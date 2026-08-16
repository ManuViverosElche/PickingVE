import hashlib
import io
import json
import os
import re
import threading
import time
import urllib.request
import uuid
from datetime import date, datetime, timedelta, timezone
from typing import Any, Optional
from urllib.error import HTTPError

import google.auth
import google.auth.transport.requests
from fastapi import FastAPI, File, Form, HTTPException, Query, Header, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from google.cloud import bigquery, storage
from google.cloud.exceptions import NotFound
from pydantic import BaseModel, Field

PROJECT = os.getenv("GCP_PROJECT", "dashboard-439511")
DATASET = "GestionComercialVE"
PICKING_DATASET = "pickingve"
PICKING_TABLE = "picking_registros"
ENCARGADOS_TABLE = "encargados"
FINCAS_TABLE = "fincas"
API_KEY = os.getenv("API_KEY", "")
PASSWORD_SALT = os.getenv("PASSWORD_SALT", "pickingve-2026")
MAX_REGISTROS = 1000
TELEGRAM_API = "https://api.telegram.org"
CHAT_BUCKET = os.getenv("CHAT_BUCKET", "pickingve-chat")

client = bigquery.Client(project=PROJECT)

app = FastAPI(title="PickingVE API", version="1.3.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET"],
    allow_headers=["X-API-Key"],
)

_rate_lock = threading.Lock()
_rate_hits: dict[str, list[float]] = {}
GET_LIMIT = 120
POST_LIMIT = 30
WINDOW_SECONDS = 60

_fincas_cache: dict[str, tuple[float, Any]] = {}
FINCAS_CACHE_TTL_SECONDS = 60


def _cache_get(key: str):
    entry = _fincas_cache.get(key)
    if entry and time.time() - entry[0] < FINCAS_CACHE_TTL_SECONDS:
        return entry[1]
    return None


def _cache_set(key: str, value: Any) -> None:
    _fincas_cache[key] = (time.time(), value)


def _cache_clear() -> None:
    _fincas_cache.clear()


def _check_rate_limit(ip: str, limit: int) -> None:
    now = time.time()
    with _rate_lock:
        hits = _rate_hits.setdefault(ip, [])
        hits[:] = [t for t in hits if now - t < WINDOW_SECONDS]
        if len(hits) >= limit:
            raise HTTPException(status_code=429, detail="Demasiadas peticiones. Intenta de nuevo.")
        hits.append(now)


def _verify_key(x_api_key: Optional[str] = Header(default=None)) -> None:
    if not API_KEY or x_api_key != API_KEY:
        raise HTTPException(status_code=401, detail="API key inválida o ausente")


def _query(sql: str) -> list[dict[str, Any]]:
    return [dict(r) for r in client.query(sql).result()]


def _ensure_picking_table() -> None:
    dataset_ref = bigquery.Dataset(f"{PROJECT}.{PICKING_DATASET}")
    try:
        client.get_dataset(dataset_ref)
    except NotFound:
        client.create_dataset(dataset_ref)
    client.query(
        f"""
        CREATE TABLE IF NOT EXISTS `{PROJECT}.{PICKING_DATASET}.{PICKING_TABLE}` (
            record_id STRING,
            order_id STRING,
            picking_numero INT64,
            picking_tipo STRING,
            order_line_id STRING,
            ean_escaneado STRING,
            ocr_texto STRING,
            ref_original STRING,
            ref_servida STRING,
            sustituido BOOL,
            litros FLOAT64,
            medida STRING,
            calibre STRING,
            cantidad_partida FLOAT64,
            fecha_hora TIMESTAMP,
            empleado_email STRING,
            empleado_nombre STRING
        )
        """
    ).result()
    _ensure_column(PICKING_TABLE, "empleado_email", "empleado_email STRING")
    _ensure_column(PICKING_TABLE, "empleado_nombre", "empleado_nombre STRING")


def _hash_password(usuario: str, password: str) -> str:
    return hashlib.sha256(f"{usuario}:{PASSWORD_SALT}:{password}".encode()).hexdigest()


def _ensure_encargados_table() -> None:
    dataset_ref = bigquery.Dataset(f"{PROJECT}.{PICKING_DATASET}")
    try:
        client.get_dataset(dataset_ref)
    except NotFound:
        client.create_dataset(dataset_ref)
    client.query(
        f"""
        CREATE TABLE IF NOT EXISTS `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}` (
            id STRING,
            nombre STRING,
            usuario STRING,
            password_hash STRING,
            rol STRING,
            fincas_carga STRING,
            modo STRING,
            email STRING,
            activo BOOL
        )
        """
    ).result()
    _ensure_column(ENCARGADOS_TABLE, "modo", "modo STRING")
    _ensure_column(ENCARGADOS_TABLE, "email", "email STRING")
    _ensure_column(ENCARGADOS_TABLE, "activo", "activo BOOL")


def _ensure_fincas_table() -> None:
    dataset_ref = bigquery.Dataset(f"{PROJECT}.{PICKING_DATASET}")
    try:
        client.get_dataset(dataset_ref)
    except NotFound:
        client.create_dataset(dataset_ref)
    client.query(
        f"""
        CREATE TABLE IF NOT EXISTS `{PROJECT}.{PICKING_DATASET}.{FINCAS_TABLE}` (
            finca STRING,
            nombre STRING,
            activo BOOL
        )
        """
    ).result()
    _ensure_column(FINCAS_TABLE, "nombre", "nombre STRING")
    _ensure_column(FINCAS_TABLE, "activo", "activo BOOL")


def _fincas_automaticas() -> list[str]:
    cached = _cache_get("automaticas")
    if cached is not None:
        return cached
    rows = _query(
        f"""
        SELECT DISTINCT FINCA_CARGA AS finca
        FROM `{PROJECT}.{DATASET}.PEDIDOS`
        WHERE FINCA_CARGA IS NOT NULL AND TRIM(FINCA_CARGA) != ''
        """
    )
    resultado = [r["finca"] for r in rows]
    _cache_set("automaticas", resultado)
    return resultado


def _fincas_curadas(incluir_ocultas: bool = False) -> list[dict[str, Any]]:
    """Merged, curated finca list.

    Automatic fincas come from PEDIDOS; manual ones from the fincas table.
    Rows with activo=FALSE are hidden (kept when `incluir_ocultas`). The display
    name (`nombre`) falls back to the real `finca` value when blank.
    """
    cache_key = "curadas_incl_ocultas" if incluir_ocultas else "curadas"
    cached = _cache_get(cache_key)
    if cached is not None:
        return cached
    filas = {
        r["finca"]: r
        for r in _query(
            f"""
            SELECT finca, nombre, activo
            FROM `{PROJECT}.{PICKING_DATASET}.{FINCAS_TABLE}`
            """
        )
    }
    ocultas = {f for f, r in filas.items() if r.get("activo") is False}
    resultado: dict[str, dict[str, Any]] = {}
    for f in _fincas_automaticas():
        fila = filas.get(f)
        if f in ocultas and not incluir_ocultas:
            continue
        nombre = (fila.get("nombre") or "").strip() if fila else ""
        resultado[f] = {"finca": f, "nombre": nombre or f, "manual": False}
    for f, fila in filas.items():
        if f in ocultas and not incluir_ocultas:
            continue
        if f not in resultado:
            nombre = (fila.get("nombre") or "").strip()
            resultado[f] = {"finca": f, "nombre": nombre or f, "manual": True}
    final = list(resultado.values())
    _cache_set(cache_key, final)
    return final


def _resolver_fincas(nombres: list[str]) -> list[str]:
    """Translates display names to the real FINCA_CARGA values (as in PEDIDOS).

    Unmapped names are kept as-is. Used by /api/pedidos so renaming a finca
    does not break the sync filter.
    """
    if not nombres:
        return []
    mapa: dict[str, str] = {}
    for r in client.query(
        f"""
        SELECT finca, nombre
        FROM `{PROJECT}.{PICKING_DATASET}.{FINCAS_TABLE}`
        """
    ).result():
        real = r["finca"]
        nombre = (r.get("nombre") or "").strip()
        mapa[real.upper()] = real
        if nombre:
            mapa[nombre.upper()] = real
    return [mapa.get(n.upper(), n) for n in nombres]


def _renombrar_en_encargados(anterior: str, nuevo: str) -> None:
    """Propagates a finca rename to every encargado whose fincas_carga lists it."""
    if not nuevo or not anterior or anterior.upper() == nuevo.upper():
        return
    filas = client.query(
        f"""
        SELECT id, fincas_carga
        FROM `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}`
        """
    ).result()
    pendientes = []
    for r in filas:
        actual = (r.get("fincas_carga") or "").strip()
        if not actual:
            continue
        partes = [p.strip() for p in actual.split(",")]
        if any(p.upper() == anterior.upper() for p in partes):
            nuevas = [nuevo if p.upper() == anterior.upper() else p for p in partes]
            pendientes.append((r["id"], ", ".join(nuevas)))
    for enc_id, nueva_lista in pendientes:
        client.query(
            f"""
            UPDATE `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}`
            SET fincas_carga = @fincas
            WHERE id = @id
            """,
            job_config=bigquery.QueryJobConfig(
                query_parameters=[
                    bigquery.ScalarQueryParameter("fincas", "STRING", nueva_lista),
                    bigquery.ScalarQueryParameter("id", "STRING", enc_id),
                ]
            ),
        ).result()


def _ensure_column(table: str, column: str, ddl: str) -> None:
    found = [
        r for r in client.query(
            f"""
            SELECT column_name
            FROM `{PROJECT}.{PICKING_DATASET}.INFORMATION_SCHEMA.COLUMNS`
            WHERE table_name = '{table}' AND column_name = '{column}'
            """
        ).result()
    ]
    if not found:
        client.query(
            f"ALTER TABLE `{PROJECT}.{PICKING_DATASET}.{table}` ADD COLUMN {ddl}"
        ).result()


@app.on_event("startup")
def _startup() -> None:
    _ensure_picking_table()
    _ensure_encargados_table()
    _ensure_fincas_table()
    _ensure_notificaciones_tables()
    _ensure_matriculas_table()


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


def _telegram_request(bot_token: str, method: str, payload: dict[str, Any]) -> dict[str, Any]:
    req = urllib.request.Request(
        f"{TELEGRAM_API}/bot{bot_token}/{method}",
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode())


@app.post("/api/telegram/webhook/{bot_token}")
async def telegram_webhook(
    bot_token: str,
    request: Request,
    x_telegram_bot_api_secret_token: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    if not API_KEY or x_telegram_bot_api_secret_token != API_KEY:
        raise HTTPException(status_code=401, detail="Secret token inválido o ausente")
    update = await request.json()
    callback = update.get("callback_query")
    if not callback:
        return _telegram_mensaje_texto(bot_token, update)
    data = callback.get("data") or ""
    if not data.startswith("check_"):
        return {"ok": True}
    message = callback.get("message") or {}
    chat_id = (message.get("chat") or {}).get("id")
    message_id = message.get("message_id")
    callback_id = callback.get("id")
    try:
        _telegram_request(
            bot_token,
            "answerCallbackQuery",
            {
                "callback_query_id": callback_id,
                "text": "✅ Marcado como comprobado",
            },
        )
        if chat_id and message_id:
            _telegram_request(
                bot_token,
                "editMessageReplyMarkup",
                {
                    "chat_id": chat_id,
                    "message_id": message_id,
                    "reply_markup": {
                        "inline_keyboard": [
                            [
                                {
                                    "text": "✅ Comprobado",
                                    "callback_data": data,
                                }
                            ]
                        ]
                    },
                },
            )
    except Exception:
        return {"ok": False}
    return {"ok": True}


# ---------------- Notificaciones push (FCM) y chat oficina <-> encargados ----------------

FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
FCM_URL = f"https://fcm.googleapis.com/v1/projects/{PROJECT}/messages:send"
FCM_TOKENS_TABLE = "fcm_tokens"
COMENTARIOS_TABLE = "comentarios"
NOTIFICACIONES_META_TABLE = "notificaciones_meta"
MATRICULAS_TABLE = "matriculas_pedido"

_fcm_token_cache: dict[str, tuple[float, str]] = {}


def _esc(value: Any) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "\\'").replace("\n", "\\n") + "'"


def _ensure_notificaciones_tables() -> None:
    client.query(
        f"""
        CREATE TABLE IF NOT EXISTS `{PROJECT}.{PICKING_DATASET}.{FCM_TOKENS_TABLE}` (
            encargado_email STRING NOT NULL,
            token STRING NOT NULL,
            plataforma STRING,
            fecha_actualizacion TIMESTAMP
        );
        CREATE TABLE IF NOT EXISTS `{PROJECT}.{PICKING_DATASET}.{COMENTARIOS_TABLE}` (
            comentario_id STRING NOT NULL,
            pedido_id STRING NOT NULL,
            linea_huella STRING,
            autor_email STRING,
            autor_nombre STRING,
            rol STRING,
            canal STRING,
            texto STRING,
            adjunto_url STRING,
            creado_en TIMESTAMP
        );
        CREATE TABLE IF NOT EXISTS `{PROJECT}.{PICKING_DATASET}.{NOTIFICACIONES_META_TABLE}` (
            clave STRING NOT NULL,
            valor TIMESTAMP,
            valor_texto STRING
        );
        """
    ).result()
    _ensure_column(NOTIFICACIONES_META_TABLE, "valor_texto", "valor_texto STRING")
    _ensure_column(COMENTARIOS_TABLE, "adjunto_url", "adjunto_url STRING")


def _ensure_matriculas_table() -> None:
    client.query(
        f"""
        CREATE TABLE IF NOT EXISTS `{PROJECT}.{PICKING_DATASET}.{MATRICULAS_TABLE}` (
            pedido_id STRING NOT NULL,
            tipo STRING NOT NULL,
            matricula STRING,
            muelle STRING,
            foto_url STRING,
            creado_en TIMESTAMP
        )
        """
    ).result()


def _fcm_access_token() -> str:
    cached = _fcm_token_cache.get("token")
    if cached and cached[0] > time.time() + 60:
        return cached[1]
    creds, _ = google.auth.default(scopes=[FCM_SCOPE])
    creds.refresh(google.auth.transport.requests.Request())
    _fcm_token_cache["token"] = (creds.expiry.timestamp(), creds.token)
    return creds.token


def _enviar_fcm(email: str, titulo: str, cuerpo: str, data: dict[str, Any]) -> bool:
    rows = _query(
        f"SELECT token FROM `{PROJECT}.{PICKING_DATASET}.{FCM_TOKENS_TABLE}` "
        f"WHERE encargado_email = {_esc(email)}"
    )
    if not rows:
        return False
    payload = {
        "message": {
            "token": rows[0]["token"],
            "notification": {"title": titulo, "body": cuerpo},
            "data": {k: str(v) for k, v in data.items()},
            "android": {"priority": "high"},
        }
    }
    req = urllib.request.Request(
        FCM_URL,
        data=json.dumps(payload).encode(),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {_fcm_access_token()}",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status == 200
    except (HTTPError, OSError):
        return False


def _encargados_finca(finca: str) -> list[str]:
    if not finca:
        return []
    objetivo = finca.strip().upper()
    rows = _query(
        f"SELECT email, fincas_carga FROM `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}`"
    )
    return [
        r["email"]
        for r in rows
        if any(p.strip().upper() == objetivo for p in (r.get("fincas_carga") or "").split(","))
    ]


def _finca_pedido(pedido: str) -> str:
    rows = _query(
        f"SELECT FINCA_CARGA FROM `{PROJECT}.{DATASET}.PEDIDOS` "
        f"WHERE NUMERO_PEDIDO = {_esc(pedido)} LIMIT 1"
    )
    return (rows[0].get("FINCA_CARGA") or "") if rows else ""


def _oficina_chat_id(bot_token: str) -> Optional[str]:
    rows = _query(
        f"SELECT valor_texto FROM `{PROJECT}.{PICKING_DATASET}.{NOTIFICACIONES_META_TABLE}` "
        f"WHERE clave = {_esc('oficina_chat_id:' + bot_token)}"
    )
    return rows[0]["valor_texto"] if rows else None


def _insertar_comentario(
    pedido: str,
    linea: Optional[str],
    email: str,
    nombre: str,
    rol: str,
    canal: str,
    texto: str,
    adjunto_url: Optional[str] = None,
) -> None:
    client.query(
        f"INSERT INTO `{PROJECT}.{PICKING_DATASET}.{COMENTARIOS_TABLE}` "
        f"(comentario_id, pedido_id, linea_huella, autor_email, autor_nombre, rol, canal, texto, adjunto_url, creado_en) "
        f"VALUES ({_esc(str(uuid.uuid4()))}, {_esc(pedido)}, {_esc(linea)}, {_esc(email)}, "
        f"{_esc(nombre)}, {_esc(rol)}, {_esc(canal)}, {_esc(texto)}, {_esc(adjunto_url)}, CURRENT_TIMESTAMP())"
    ).result()


def _posicion_linea(pedido: str, huella: Optional[str]) -> Optional[int]:
    if not huella:
        return None
    rows = _query(
        f"SELECT POSICION_PEDIDO FROM `{PROJECT}.{DATASET}.LINEA_PEDIDO` "
        f"WHERE NUMERO_PEDIDO = {_esc(pedido)} AND HUELLA_DIGITAL = {_esc(huella)} LIMIT 1"
    )
    if not rows:
        return None
    pos = rows[0].get("POSICION_PEDIDO")
    return int(pos) if pos is not None else None


_DIAS_ES = ["lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"]


def _muelle_pedido(pedido: str) -> str:
    try:
        rows = _query(
            f"SELECT muelle FROM `{PROJECT}.{PICKING_DATASET}.matriculas_pedido` "
            f"WHERE pedido_id = {_esc(pedido)} AND muelle IS NOT NULL AND muelle != '' "
            f"ORDER BY creado_en DESC LIMIT 1"
        )
    except Exception:
        return ""
    return (rows[0].get("muelle") or "").strip() if rows else ""


def _contexto_pedido(pedido: str) -> str:
    """Bloque de contexto del pedido para los avisos a la oficina: fecha de carga
    con día de la semana, finca/sector/muelle, cliente (y fiscal si difiere) y comercial."""
    if not pedido:
        return ""
    rows = _query(
        f"""
        SELECT p.FINCA_CARGA, p.SECTOR_CARGA, p.FECHA_CARGA, p.NUMERO_CLIENTE,
               c.N_FISCAL, c.N_COMERCIAL, a.NOMBRE_AGENTE
        FROM `{PROJECT}.{DATASET}.PEDIDOS` p
        LEFT JOIN `{PROJECT}.{DATASET}.CLIENTE` c ON c.ID_CLIENTE = p.NUMERO_CLIENTE
        LEFT JOIN `{PROJECT}.{DATASET}.AGENTE` a ON a.ID_AGENTE = p.CODIGO_AGENTE
        WHERE p.NUMERO_PEDIDO = {_esc(pedido)} LIMIT 1
        """
    )
    if not rows:
        return ""
    r = rows[0]
    lineas: list[str] = []
    fecha = r.get("FECHA_CARGA")
    if fecha is not None:
        dia = _DIAS_ES[fecha.weekday()]
        lineas.append(f"📅 {fecha.strftime('%d/%m/%Y')} · {dia}")
    partes = [p for p in [
        (r.get("FINCA_CARGA") or "").strip(),
        (r.get("SECTOR_CARGA") or "").strip(),
        _muelle_pedido(pedido),
    ] if p]
    if partes:
        lineas.append("📍 " + " · ".join(partes))
    n_comercial = (r.get("N_COMERCIAL") or "").strip()
    n_fiscal = (r.get("N_FISCAL") or "").strip()
    if n_comercial:
        cliente = f"👤 Cliente: {n_comercial}"
        if n_fiscal and n_fiscal.upper() != n_comercial.upper():
            cliente += f" · 🏢 Fiscal: {n_fiscal}"
        lineas.append(cliente)
    comercial = (r.get("NOMBRE_AGENTE") or "").strip()
    if comercial:
        lineas.append(f"🤝 Comercial: {comercial}")
    return "\n".join(lineas)


class FcmTokenRequest(BaseModel):
    email: str
    token: str
    plataforma: str = "android"


@app.post("/api/fcm-token")
def registrar_fcm_token(
    req: FcmTokenRequest,
    request: Request,
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    if not req.email or not req.token:
        raise HTTPException(status_code=400, detail="email y token son obligatorios")
    client.query(
        f"DELETE FROM `{PROJECT}.{PICKING_DATASET}.{FCM_TOKENS_TABLE}` "
        f"WHERE encargado_email = {_esc(req.email)} AND token != {_esc(req.token)}"
    ).result()
    client.query(
        f"""
        MERGE INTO `{PROJECT}.{PICKING_DATASET}.{FCM_TOKENS_TABLE}` AS t
        USING (SELECT {_esc(req.email)} AS email, {_esc(req.token)} AS token, {_esc(req.plataforma)} AS plataforma) AS s
        ON t.token = s.token
        WHEN MATCHED THEN UPDATE SET encargado_email = s.email, plataforma = s.plataforma, fecha_actualizacion = CURRENT_TIMESTAMP()
        WHEN NOT MATCHED THEN INSERT (encargado_email, token, plataforma, fecha_actualizacion)
            VALUES (s.email, s.token, s.plataforma, CURRENT_TIMESTAMP())
        """
    ).result()
    return {"ok": True}


class ComentarioRequest(BaseModel):
    pedido_id: str
    linea_huella: Optional[str] = None
    texto: str
    autor_email: str
    autor_nombre: str = ""
    rol: str = "ENCARGADO"
    canal: str = "app"


@app.get("/api/comentarios")
def lista_comentarios(
    pedido: str = Query(...),
    linea: Optional[str] = Query(default=None),
    desde: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    where = [f"pedido_id = {_esc(pedido)}"]
    if linea is not None:
        where.append(f"linea_huella = {_esc(linea)}")
    if desde:
        where.append(f"creado_en > TIMESTAMP({_esc(desde)})")
    rows = _query(
        f"SELECT comentario_id, pedido_id, linea_huella, autor_email, autor_nombre, rol, canal, texto, adjunto_url, "
        f"FORMAT_TIMESTAMP('%Y-%m-%dT%H:%M:%E6SZ', creado_en) AS creado_en "
        f"FROM `{PROJECT}.{PICKING_DATASET}.{COMENTARIOS_TABLE}` "
        f"WHERE " + " AND ".join(where) + " ORDER BY creado_en"
    )
    return {"comentarios": rows}


@app.post("/api/comentarios")
def crear_comentario(
    req: ComentarioRequest,
    request: Request,
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    texto = req.texto.strip()
    if not texto:
        raise HTTPException(status_code=400, detail="El comentario no puede estar vacío")
    _insertar_comentario(
        req.pedido_id, req.linea_huella, req.autor_email,
        req.autor_nombre or req.autor_email, req.rol, req.canal, texto,
    )
    return {"ok": True}


@app.post("/api/comentarios/adjunto")
def crear_comentario_adjunto(
    request: Request,
    pedido_id: str = Form(...),
    linea_huella: Optional[str] = Form(default=None),
    autor_email: str = Form(...),
    autor_nombre: Optional[str] = Form(default=None),
    rol: str = Form(default="ENCARGADO"),
    texto: str = Form(default=""),
    archivo: UploadFile = File(...),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    datos = archivo.file.read()
    if len(datos) > 10 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="La foto supera los 10 MB")
    ext = (archivo.filename or "").rsplit(".", 1)[-1].lower() or "jpg"
    if ext not in {"jpg", "jpeg", "png", "webp"}:
        raise HTTPException(status_code=415, detail="Formato no permitido")
    nombre_archivo = f"chat/{datetime.now(timezone.utc).strftime('%Y%m%d')}/{uuid.uuid4().hex}.{ext}"
    bucket = storage.Client(project=PROJECT).bucket(CHAT_BUCKET)
    bucket.blob(nombre_archivo).upload_from_string(
        datos,
        content_type="image/jpeg" if ext in {"jpg", "jpeg"} else f"image/{ext}",
    )
    url = f"https://storage.googleapis.com/{CHAT_BUCKET}/{nombre_archivo}"
    _insertar_comentario(
        pedido_id, linea_huella, autor_email,
        autor_nombre or autor_email, rol, "app", texto.strip(), url,
    )
    return {"ok": True, "adjunto_url": url}


@app.get("/api/pedidos/matriculas")
def lista_matriculas(
    pedido: str = Query(...),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    rows = _query(
        f"SELECT pedido_id, tipo, matricula, muelle, foto_url, "
        f"FORMAT_TIMESTAMP('%Y-%m-%dT%H:%M:%E6SZ', creado_en) AS creado_en "
        f"FROM `{PROJECT}.{PICKING_DATASET}.{MATRICULAS_TABLE}` "
        f"WHERE pedido_id = {_esc(pedido)} ORDER BY creado_en"
    )
    return {"matriculas": rows}


@app.post("/api/pedidos/matriculas")
def guardar_matricula(
    request: Request,
    pedido_id: str = Form(...),
    tipo: str = Form(...),
    matricula: str = Form(default=""),
    muelle: str = Form(default=""),
    archivo: Optional[UploadFile] = File(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    if tipo not in {"CAMION", "REMOLQUE_A", "REMOLQUE_B"}:
        raise HTTPException(status_code=422, detail="Tipo no válido")
    foto_url = ""
    if archivo is not None:
        datos = archivo.file.read()
        if len(datos) > 10 * 1024 * 1024:
            raise HTTPException(status_code=413, detail="La foto supera los 10 MB")
        ext = (archivo.filename or "").rsplit(".", 1)[-1].lower() or "jpg"
        if ext not in {"jpg", "jpeg", "png", "webp"}:
            raise HTTPException(status_code=415, detail="Formato no permitido")
        nombre_archivo = f"matriculas/{pedido_id}/{tipo}_{uuid.uuid4().hex}.{ext}"
        bucket = storage.Client(project=PROJECT).bucket(CHAT_BUCKET)
        bucket.blob(nombre_archivo).upload_from_string(
            datos,
            content_type="image/jpeg" if ext in {"jpg", "jpeg"} else f"image/{ext}",
        )
        foto_url = f"https://storage.googleapis.com/{CHAT_BUCKET}/{nombre_archivo}"
    matricula_limpia = matricula.strip().upper()
    muelle_limpio = muelle.strip()
    client.query(
        f"""
        MERGE `{PROJECT}.{PICKING_DATASET}.{MATRICULAS_TABLE}` T
        USING (SELECT {_esc(pedido_id)} AS pedido_id, {_esc(tipo)} AS tipo) S
        ON T.pedido_id = S.pedido_id AND T.tipo = S.tipo
        WHEN MATCHED THEN
            UPDATE SET matricula = {_esc(matricula_limpia)}, muelle = {_esc(muelle_limpio)},
                       foto_url = CASE WHEN {_esc(foto_url)} != '' THEN {_esc(foto_url)} ELSE T.foto_url END,
                       creado_en = CURRENT_TIMESTAMP()
        WHEN NOT MATCHED THEN
            INSERT (pedido_id, tipo, matricula, muelle, foto_url, creado_en)
            VALUES ({_esc(pedido_id)}, {_esc(tipo)}, {_esc(matricula_limpia)},
                    {_esc(muelle_limpio)}, {_esc(foto_url)}, CURRENT_TIMESTAMP())
        """
    ).result()
    return {"ok": True, "foto_url": foto_url}


def _telegram_mensaje_texto(bot_token: str, update: dict[str, Any]) -> dict[str, Any]:
    message = update.get("message") or {}
    text = (message.get("text") or "").strip()
    if not text:
        return {"ok": True}
    chat_id = (message.get("chat") or {}).get("id")
    from_user = message.get("from") or {}
    nombre = f"{(from_user.get('first_name') or '')} {(from_user.get('last_name') or '')}".strip() or "Oficina"
    if chat_id:
        client.query(
            f"""
            MERGE INTO `{PROJECT}.{PICKING_DATASET}.{NOTIFICACIONES_META_TABLE}` AS t
            USING (SELECT {_esc('oficina_chat_id:' + bot_token)} AS clave, {_esc(str(chat_id))} AS valor_texto) AS s
            ON t.clave = s.clave
            WHEN MATCHED THEN UPDATE SET valor_texto = s.valor_texto
            WHEN NOT MATCHED THEN INSERT (clave, valor_texto) VALUES (s.clave, s.valor_texto)
            """
        ).result()
    m = re.match(r"^\s*#(\d+)\b\s*(.*)$", text, re.S)
    pedido = m.group(1) if m else ""
    cuerpo = (m.group(2) if m else text).strip() or "(sin texto)"
    _insertar_comentario(pedido, None, "oficina@telegram", nombre, "SUPERUSUARIO", "telegram", f"{pedido and ('#' + pedido + ' ') or ''}{cuerpo}")
    if pedido:
        finca = _finca_pedido(pedido)
        destinos = _encargados_finca(finca)
    else:
        finca = ""
        destinos = [r["email"] for r in _query(
            f"SELECT email FROM `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}`"
        )]
    for email in destinos:
        _enviar_fcm(
            email,
            f"Mensaje de la oficina · Pedido {pedido}" if pedido else "Aviso de la oficina",
            cuerpo,
            {"tipo": "comentario", "pedido": pedido, "linea": "", "canal": "telegram"},
        )
    try:
        _telegram_request(bot_token, "sendMessage", {"chat_id": chat_id, "text": "📨 Mensaje registrado y enviado a los encargados."})
    except Exception:
        pass
    return {"ok": True}


@app.post("/api/notificar")
def notificar_cambios(
    request: Request,
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    ahora = datetime.now(timezone.utc)
    filas = _query(
        f"SELECT valor FROM `{PROJECT}.{PICKING_DATASET}.{NOTIFICACIONES_META_TABLE}` "
        f"WHERE clave = 'ultimo_chequeo'"
    )
    wm = filas[0]["valor"] if filas else ahora - timedelta(minutes=15)
    wm_str = wm.strftime("%Y-%m-%d %H:%M:%S")
    enviadas = 0

    pedidos = _query(
        f"SELECT NUMERO_PEDIDO, FINCA_CARGA FROM `{PROJECT}.{DATASET}.PEDIDOS` "
        f"WHERE FECHA_MODIFICACION > DATETIME({_esc(wm_str)}) AND ESTADO_PEDIDO IN (2, 3)"
    )
    for r in pedidos:
        pedido = str(r.get("NUMERO_PEDIDO") or "")
        finca = r.get("FINCA_CARGA") or ""
        for email in _encargados_finca(finca):
            if _enviar_fcm(
                email,
                f"Pedido {pedido} modificado",
                "Revisa las líneas en la app",
                {"tipo": "pedido_modificado", "pedido": pedido, "linea": ""},
            ):
                enviadas += 1

    comentarios = _query(
        f"SELECT pedido_id, linea_huella, autor_nombre, rol, canal, texto, adjunto_url "
        f"FROM `{PROJECT}.{PICKING_DATASET}.{COMENTARIOS_TABLE}` "
        f"WHERE creado_en > TIMESTAMP({_esc(wm_str)})"
    )
    for c in comentarios:
        pedido = str(c.get("pedido_id") or "")
        texto = str(c.get("texto") or "")
        if c.get("canal") == "telegram":
            finca = _finca_pedido(pedido) if pedido else ""
            destinos = _encargados_finca(finca) if finca else [r["email"] for r in _query(
                f"SELECT email FROM `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}`"
            )]
            for email in destinos:
                if _enviar_fcm(
                    email,
                    f"Mensaje de la oficina · Pedido {pedido}" if pedido else "Aviso de la oficina",
                    texto[:200],
                    {"tipo": "comentario", "pedido": pedido, "linea": str(c.get("linea_huella") or ""), "canal": "telegram"},
                ):
                    enviadas += 1
        else:
            nombre = str(c.get("autor_nombre") or "Encargado")
            ofis = _query(
                f"SELECT email FROM `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}` "
                f"WHERE rol = 'SUPERUSUARIO'"
            )
            for ofi in ofis:
                if _enviar_fcm(
                    ofi["email"],
                    f"{nombre} · Pedido {pedido}" if pedido else f"{nombre}",
                    texto[:200],
                    {"tipo": "comentario", "pedido": pedido, "linea": str(c.get("linea_huella") or ""), "canal": "app"},
                ):
                    enviadas += 1
            bot_token = os.getenv("TELEGRAM_MESSAGES_BOT_TOKEN", "") or os.getenv("TELEGRAM_BOT_TOKEN", "")
            chat_id = _oficina_chat_id(bot_token) if bot_token else None
            if chat_id and bot_token:
                pos = _posicion_linea(pedido, c.get("linea_huella"))
                ref = f"Pedido {pedido}" + (f" · Línea {pos}" if pos else "")
                adjunto = c.get("adjunto_url") or ""
                contexto = _contexto_pedido(pedido)
                cuerpo = f"💬 {nombre} ({ref}): {texto}"
                if contexto:
                    cuerpo = f"💬 {nombre} ({ref})\n{contexto}\n———\n{texto}"
                try:
                    if adjunto:
                        _telegram_request(
                            bot_token,
                            "sendPhoto",
                            {"chat_id": chat_id, "photo": adjunto,
                             "caption": f"{nombre} ({ref})"},
                        )
                        _telegram_request(
                            bot_token,
                            "sendMessage",
                            {"chat_id": chat_id, "text": cuerpo},
                        )
                    else:
                        _telegram_request(
                            bot_token,
                            "sendMessage",
                            {"chat_id": chat_id, "text": cuerpo},
                        )
                except Exception:
                    pass

    client.query(
        f"""
        MERGE INTO `{PROJECT}.{PICKING_DATASET}.{NOTIFICACIONES_META_TABLE}` AS t
        USING (SELECT 'ultimo_chequeo' AS clave, TIMESTAMP({_esc(ahora.strftime('%Y-%m-%d %H:%M:%S'))}) AS valor) AS s
        ON t.clave = s.clave
        WHEN MATCHED THEN UPDATE SET valor = s.valor
        WHEN NOT MATCHED THEN INSERT (clave, valor) VALUES (s.clave, s.valor)
        """
    ).result()
    return {"ok": True, "pedidos_modificados": len(pedidos), "comentarios_nuevos": len(comentarios), "notificaciones_enviadas": enviadas}


@app.get("/api/encargados")
def lista_encargados(
    request: Request,
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", GET_LIMIT)
    rows = _query(
        f"""
        SELECT id, nombre, usuario, password_hash, rol, fincas_carga, modo, email, activo
        FROM `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}`
        ORDER BY nombre
        """
    )
    return {
        "encargados": [
            {**r, "email": r.get("email") or "", "activo": r.get("activo") is not False}
            for r in rows
        ]
    }


@app.get("/api/fincas")
def lista_fincas(
    request: Request,
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", GET_LIMIT)
    fincas = sorted(
        (f["nombre"] for f in _fincas_curadas()),
        key=lambda n: n.upper(),
    )
    return {"fincas": fincas}


@app.get("/api/fincas/gestion")
def lista_fincas_gestion(
    request: Request,
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", GET_LIMIT)
    filas = {
        r["finca"]: r
        for r in _query(
            f"""
            SELECT finca, nombre, activo
            FROM `{PROJECT}.{PICKING_DATASET}.{FINCAS_TABLE}`
            """
        )
    }
    fincas = _fincas_curadas(incluir_ocultas=True)
    ocultas = {f for f, r in filas.items() if r.get("activo") is False}
    return {
        "fincas": [
            {
                "finca": f["finca"],
                "nombre": f["nombre"],
                "manual": f["manual"],
                "oculto": f["finca"] in ocultas,
            }
            for f in fincas
        ]
    }


class FincaBody(BaseModel):
    finca: str = Field(min_length=1, max_length=64)
    nombre: Optional[str] = Field(default=None, max_length=128)
    activo: bool = True


class FincaDeleteBody(BaseModel):
    finca: str = Field(min_length=1, max_length=64)


@app.post("/api/fincas")
def crear_finca(
    request: Request,
    body: FincaBody,
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    _ensure_fincas_table()
    _cache_clear()
    finca = body.finca.strip().upper()
    if not finca:
        raise HTTPException(status_code=400, detail="Indica el nombre de la finca")
    nombre = (body.nombre or "").strip() or None
    if nombre and nombre.upper() == finca.upper():
        nombre = None
    previa = client.query(
        f"""
        SELECT nombre
        FROM `{PROJECT}.{PICKING_DATASET}.{FINCAS_TABLE}`
        WHERE finca = @finca
        """,
        job_config=bigquery.QueryJobConfig(
            query_parameters=[bigquery.ScalarQueryParameter("finca", "STRING", finca)]
        ),
    ).result()
    previa_nombre = next((r.get("nombre") for r in previa), None)
    client.query(
        f"""
        MERGE `{PROJECT}.{PICKING_DATASET}.{FINCAS_TABLE}` T
        USING (SELECT @finca AS finca, @nombre AS nombre, @activo AS activo) S
        ON T.finca = S.finca
        WHEN MATCHED THEN UPDATE SET nombre = S.nombre, activo = S.activo
        WHEN NOT MATCHED THEN INSERT (finca, nombre, activo) VALUES (S.finca, S.nombre, S.activo)
        """,
        job_config=bigquery.QueryJobConfig(
            query_parameters=[
                bigquery.ScalarQueryParameter("finca", "STRING", finca),
                bigquery.ScalarQueryParameter("nombre", "STRING", nombre),
                bigquery.ScalarQueryParameter("activo", "BOOL", body.activo),
            ]
        ),
    ).result()
    if nombre and previa_nombre is None:
        _renombrar_en_encargados(finca, nombre)
    elif nombre and previa_nombre and previa_nombre.upper() != nombre.upper():
        _renombrar_en_encargados(previa_nombre, nombre)
    return {"ok": 1}


@app.post("/api/fincas/eliminar")
def eliminar_finca(
    request: Request,
    body: FincaDeleteBody,
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    _ensure_fincas_table()
    _cache_clear()
    finca = body.finca.strip().upper()
    if not finca:
        raise HTTPException(status_code=400, detail="Indica el nombre de la finca")
    automaticas = {f.upper(): f for f in _fincas_automaticas()}
    real = automaticas.get(finca)
    if real is not None:
        client.query(
            f"""
            MERGE `{PROJECT}.{PICKING_DATASET}.{FINCAS_TABLE}` T
            USING (SELECT @finca AS finca, FALSE AS activo) S
            ON T.finca = S.finca
            WHEN MATCHED THEN UPDATE SET activo = S.activo
            WHEN NOT MATCHED THEN INSERT (finca, nombre, activo)
                VALUES (S.finca, NULL, S.activo)
            """,
            job_config=bigquery.QueryJobConfig(
                query_parameters=[bigquery.ScalarQueryParameter("finca", "STRING", real)]
            ),
        ).result()
        return {"ok": 1, "ocultada": True}
    client.query(
        f"""
        DELETE FROM `{PROJECT}.{PICKING_DATASET}.{FINCAS_TABLE}`
        WHERE finca = @finca
        """,
        job_config=bigquery.QueryJobConfig(
            query_parameters=[bigquery.ScalarQueryParameter("finca", "STRING", finca)]
        ),
    ).result()
    return {"ok": 1}


class LoginBody(BaseModel):
    usuario: str = Field(min_length=1, max_length=64)
    password: str = Field(min_length=1, max_length=128)


class EncargadoBody(BaseModel):
    id: str = Field(min_length=1, max_length=64)
    nombre: str = Field(min_length=1, max_length=128)
    usuario: str = Field(min_length=1, max_length=64)
    password: str = Field(default="", max_length=128)
    rol: str = Field(default="ENCARGADO", max_length=32)
    fincas_carga: str = Field(default="", max_length=256)
    modo: str = Field(default="PICKING", max_length=16)
    email: str = Field(default="", max_length=128)
    activo: bool = True


@app.post("/api/encargados")
def crear_encargado(
    request: Request,
    body: EncargadoBody,
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    _ensure_encargados_table()
    exists = [
        r for r in client.query(
            f"""
            SELECT id
            FROM `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}`
            WHERE id = @id
            """,
            job_config=bigquery.QueryJobConfig(
                query_parameters=[bigquery.ScalarQueryParameter("id", "STRING", body.id)]
            ),
        ).result()
    ]
    if not exists and not body.password:
        raise HTTPException(status_code=400, detail="La contraseña es obligatoria para nuevos usuarios")
    client.query(
        f"""
        MERGE `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}` T
        USING (SELECT @id AS id, @nombre AS nombre, @usuario AS usuario,
                      @password_hash AS password_hash, @rol AS rol,
                      @fincas_carga AS fincas_carga, @modo AS modo,
                      @email AS email, @activo AS activo) S
        ON T.id = S.id
        WHEN MATCHED THEN UPDATE SET
            nombre = S.nombre,
            usuario = S.usuario,
            password_hash = IF(@password = '', T.password_hash, S.password_hash),
            rol = S.rol,
            fincas_carga = S.fincas_carga,
            modo = S.modo,
            email = S.email,
            activo = S.activo
        WHEN NOT MATCHED THEN INSERT (id, nombre, usuario, password_hash, rol, fincas_carga, modo, email, activo)
            VALUES (S.id, S.nombre, S.usuario, S.password_hash, S.rol, S.fincas_carga, S.modo, S.email, S.activo)
        """,
        job_config=bigquery.QueryJobConfig(
            query_parameters=[
                bigquery.ScalarQueryParameter("id", "STRING", body.id),
                bigquery.ScalarQueryParameter("nombre", "STRING", body.nombre),
                bigquery.ScalarQueryParameter("usuario", "STRING", body.usuario),
                bigquery.ScalarQueryParameter("password", "STRING", body.password),
                bigquery.ScalarQueryParameter(
                    "password_hash",
                    "STRING",
                    _hash_password(body.usuario, body.password) if body.password else "",
                ),
                bigquery.ScalarQueryParameter("rol", "STRING", body.rol),
                bigquery.ScalarQueryParameter("fincas_carga", "STRING", body.fincas_carga),
                bigquery.ScalarQueryParameter("modo", "STRING", body.modo),
                bigquery.ScalarQueryParameter("email", "STRING", body.email),
                bigquery.ScalarQueryParameter("activo", "BOOL", body.activo),
            ]
        ),
    ).result()
    return {"ok": 1}


@app.post("/api/encargados/login")
def login_encargado(
    request: Request,
    body: LoginBody,
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    _ensure_encargados_table()
    enc = [dict(r) for r in client.query(
        f"""
        SELECT id, nombre, usuario, password_hash, rol, fincas_carga, modo, email, activo
        FROM `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}`
        WHERE usuario = @usuario
        """,
        job_config=bigquery.QueryJobConfig(
            query_parameters=[bigquery.ScalarQueryParameter("usuario", "STRING", body.usuario)]
        ),
    ).result()]

    if not enc:
        raise HTTPException(status_code=404, detail="Encargado no encontrado")
    e = dict(enc[0])
    if e["password_hash"] != _hash_password(body.usuario, body.password):
        raise HTTPException(status_code=401, detail="Contraseña incorrecta")
    if e.get("activo") is False:
        raise HTTPException(status_code=403, detail="Usuario dado de baja")
    return {
        "id": e["id"],
        "nombre": e["nombre"],
        "usuario": e["usuario"],
        "rol": e["rol"],
        "fincas_carga": e["fincas_carga"],
        "modo": e["modo"],
        "email": e.get("email") or "",
        "activo": e.get("activo") is not False,
    }


class CambiarPasswordBody(BaseModel):
    usuario: str = Field(min_length=1, max_length=64)
    password_actual: str = Field(min_length=1, max_length=128)
    password_nueva: str = Field(min_length=4, max_length=128)


class CambiarEmailBody(BaseModel):
    usuario: str = Field(min_length=1, max_length=64)
    email: str = Field(min_length=1, max_length=200)


@app.post("/api/encargados/cambiar-email")
def cambiar_email(
    request: Request,
    body: CambiarEmailBody,
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    _ensure_encargados_table()
    _ensure_column(ENCARGADOS_TABLE, "email", "email STRING")
    enc = [dict(r) for r in client.query(
        f"""
        SELECT id
        FROM `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}`
        WHERE usuario = @usuario
        """,
        job_config=bigquery.QueryJobConfig(
            query_parameters=[bigquery.ScalarQueryParameter("usuario", "STRING", body.usuario)]
        ),
    ).result()]
    if not enc:
        raise HTTPException(status_code=404, detail="Encargado no encontrado")
    client.query(
        f"""
        UPDATE `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}`
        SET email = @email
        WHERE id = @id
        """,
        job_config=bigquery.QueryJobConfig(
            query_parameters=[
                bigquery.ScalarQueryParameter("email", "STRING", body.email.strip()),
                bigquery.ScalarQueryParameter("id", "STRING", enc[0]["id"]),
            ]
        ),
    ).result()
    return {"ok": 1}


@app.post("/api/encargados/cambiar-password")
def cambiar_password(
    request: Request,
    body: CambiarPasswordBody,
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    _ensure_encargados_table()
    enc = [dict(r) for r in client.query(
        f"""
        SELECT id, password_hash
        FROM `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}`
        WHERE usuario = @usuario
        """,
        job_config=bigquery.QueryJobConfig(
            query_parameters=[bigquery.ScalarQueryParameter("usuario", "STRING", body.usuario)]
        ),
    ).result()]
    if not enc:
        raise HTTPException(status_code=404, detail="Encargado no encontrado")
    if enc[0]["password_hash"] != _hash_password(body.usuario, body.password_actual):
        raise HTTPException(status_code=401, detail="Contraseña actual incorrecta")
    client.query(
        f"""
        UPDATE `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}`
        SET password_hash = @password_hash
        WHERE id = @id
        """,
        job_config=bigquery.QueryJobConfig(
            query_parameters=[
                bigquery.ScalarQueryParameter("password_hash", "STRING", _hash_password(body.usuario, body.password_nueva)),
                bigquery.ScalarQueryParameter("id", "STRING", enc[0]["id"]),
            ]
        ),
    ).result()
    return {"ok": 1}


@app.get("/api/pedidos")
def pedidos(
    request: Request,
    desde: Optional[date] = Query(None, description="Pedidos con FECHA_CARGA >= desde (YYYY-MM-DD)"),
    fecha: Optional[date] = Query(None, description="Dia de carga exacto (YYYY-MM-DD)"),
    hasta: Optional[date] = Query(None, description="Pedidos con FECHA_CARGA <= hasta (YYYY-MM-DD)"),
    finca: Optional[str] = Query(None, description="Finca de carga unica (BORISA, LA FABRICA...)"),
    fincas: Optional[str] = Query(None, description="Fincas de carga separadas por coma (BORISA,LA FABRICA)"),
    estados: Optional[str] = Query(None, description="Estados a cargar, separados por coma (2,3)"),
    estado: Optional[int] = Query(None, description="Filtro por ESTADO_PEDIDO unico (3 = EN ALMACEN)"),
    modificadoDesde: Optional[datetime] = Query(None, description="Solo pedidos con FECHA_MODIFICACION >= (ISO datetime)"),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", GET_LIMIT)
    if desde is None and fecha is None:
        raise HTTPException(status_code=400, detail="Indica 'desde' o 'fecha'")
    sql = f"""
        SELECT p.SERIE_PEDIDO, p.NUMERO_PEDIDO, p.NUMERO_CLIENTE, p.ESTADO_PEDIDO,
               p.FECHA_CARGA, p.SECTOR_CARGA, p.FINCA_CARGA, p.NOTAS_PEDIDO,
               p.MARCA_PEDIDO,
               COALESCE(c.N_COMERCIAL, '') AS CLIENTE,
               COALESCE(c.N_FISCAL, '') AS CLIENTE_FISCAL,
               l.HUELLA_DIGITAL, l.POSICION_PEDIDO, l.REFERENCIA_ARTICULO,
               l.DESCRIPCION_ARTICULO, l.UNIDADES, l.UNIDADES_PENDIENTES,
               l.CODIGO_LITRAJE, l.CODIGO_SECTOR, l.MARCA, l.FINCA_RELEVADA,
               l.SECTOR_RELEVADO, l.UBICACION_EXTRA, l.PRIORIDAD, l.ACCION_LOGISTICA,
               l.NOTA_LINEA_PEDIDO, l.IMPRIMIR_LINEA, l.MARCADO,
               lt.DESCRIPCION_LITRAJE, st.DESCRIPCION_SECTOR,
               COALESCE(pr.ACOPIADO, 0) AS ACOPIADO,
               COALESCE(pn.ULTIMO_PARTE, 0) AS ULTIMO_PARTE
        FROM `{PROJECT}.{DATASET}.PEDIDOS` p
        LEFT JOIN `{PROJECT}.{DATASET}.CLIENTE` c ON c.ID_CLIENTE = p.NUMERO_CLIENTE
        LEFT JOIN `{PROJECT}.{DATASET}.LINEA_PEDIDO` l
            ON l.SERIE_PEDIDO = p.SERIE_PEDIDO AND l.NUMERO_PEDIDO = p.NUMERO_PEDIDO
            AND COALESCE(l.IMPRIMIR_LINEA, 0) = 0
            AND COALESCE(l.LINEA_ACTIVA, TRUE) = TRUE
        LEFT JOIN `{PROJECT}.{DATASET}.LITRAJES` lt ON lt.ID_LITRAJE = l.CODIGO_LITRAJE
        LEFT JOIN `{PROJECT}.{DATASET}.SECTORES` st ON st.ID_SECTOR = l.CODIGO_SECTOR
        LEFT JOIN (
            SELECT order_id, order_line_id, SUM(cantidad_partida) AS ACOPIADO
            FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_TABLE}`
            GROUP BY order_id, order_line_id
        ) pr ON pr.order_id = p.NUMERO_PEDIDO AND pr.order_line_id = l.HUELLA_DIGITAL
        LEFT JOIN (
            SELECT order_id, MAX(picking_numero) AS ULTIMO_PARTE
            FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_TABLE}`
            GROUP BY order_id
        ) pn ON pn.order_id = p.NUMERO_PEDIDO
    """
    where = []
    params = []
    if desde is not None:
        where.append("DATE(p.FECHA_CARGA) >= @desde")
        params.append(bigquery.ScalarQueryParameter("desde", "DATE", desde.isoformat()))
    else:
        where.append("DATE(p.FECHA_CARGA) = @fecha")
        params.append(bigquery.ScalarQueryParameter("fecha", "DATE", fecha.isoformat()))
    if hasta is not None:
        where.append("DATE(p.FECHA_CARGA) <= @hasta")
        params.append(bigquery.ScalarQueryParameter("hasta", "DATE", hasta.isoformat()))
    if fincas:
        lista = [f.upper() for f in _resolver_fincas([f.strip() for f in fincas.split(",") if f.strip()])]
        where.append("UPPER(p.FINCA_CARGA) IN UNNEST(@fincas)")
        params.append(bigquery.ArrayQueryParameter("fincas", "STRING", lista))
    elif finca:
        lista = [f.upper() for f in _resolver_fincas([finca])]
        where.append("UPPER(p.FINCA_CARGA) = @finca")
        params.append(bigquery.ScalarQueryParameter("finca", "STRING", lista[0] if lista else finca.upper()))
    if estados:
        lista_estados = [int(e.strip()) for e in estados.split(",") if e.strip()]
        where.append("p.ESTADO_PEDIDO IN UNNEST(@estados)")
        params.append(bigquery.ArrayQueryParameter("estados", "INT64", lista_estados))
    elif estado is not None:
        where.append("p.ESTADO_PEDIDO = @estado")
        params.append(bigquery.ScalarQueryParameter("estado", "INT64", estado))
    if modificadoDesde is not None:
        where.append("p.FECHA_MODIFICACION >= @modificadoDesde")
        params.append(
            bigquery.ScalarQueryParameter(
                "modificadoDesde", "DATETIME", modificadoDesde.isoformat()
            )
        )
    if where:
        sql += " WHERE " + " AND ".join(where)
    sql += " ORDER BY p.NUMERO_PEDIDO DESC, l.POSICION_PEDIDO"

    rows = [dict(r) for r in client.query(sql, job_config=bigquery.QueryJobConfig(query_parameters=params)).result()]

    pedidos: dict[str, dict[str, Any]] = {}
    for r in rows:
        key = (r.get("SERIE_PEDIDO") or "", r.get("NUMERO_PEDIDO") or "")
        p = pedidos.get(key)
        if p is None:
            p = pedidos[key] = {
                "serie": key[0],
                "numero": key[1],
                "cliente": r.get("CLIENTE") or "",
                "clienteFiscal": r.get("CLIENTE_FISCAL") or "",
                "estado": r.get("ESTADO_PEDIDO"),
                "fechaCarga": str(r.get("FECHA_CARGA")) if r.get("FECHA_CARGA") else None,
                "sector": r.get("SECTOR_CARGA") or "",
                "finca": r.get("FINCA_CARGA") or "",
                "marcaPedido": r.get("MARCA_PEDIDO") or "",
                "observaciones": r.get("NOTAS_PEDIDO") or r.get("OBSERVACIONES") or "",
                "pickingActual": int(r.get("ULTIMO_PARTE") or 0),
                "lineas": [],
            }
        if r.get("HUELLA_DIGITAL") is not None:
            p["lineas"].append(
                {
                    "huella": r.get("HUELLA_DIGITAL"),
                    "posicion": r.get("POSICION_PEDIDO"),
                    "referencia": r.get("REFERENCIA_ARTICULO") or "",
                    "descripcion": r.get("DESCRIPCION_ARTICULO") or "",
                    "unidades": r.get("UNIDADES"),
                    "pendientes": r.get("UNIDADES_PENDIENTES"),
                    "imprimirLinea": r.get("IMPRIMIR_LINEA") or 0,
                    "marcado": bool(r.get("MARCADO")),
                    "acopiado": int(r.get("ACOPIADO") or 0),
                    "litraje": r.get("CODIGO_LITRAJE") or "",
                    "litrajeDesc": r.get("DESCRIPCION_LITRAJE") or "",
                    "sector": r.get("CODIGO_SECTOR") or "",
                    "sectorDesc": r.get("DESCRIPCION_SECTOR") or "",
                    "marca": r.get("MARCA") or "",
                    "fincaRelevada": r.get("FINCA_RELEVADA") or "",
                    "sectorRelevado": r.get("SECTOR_RELEVADO") or "",
                    "ubicacion": r.get("UBICACION_EXTRA") or "",
                    "prioridad": r.get("PRIORIDAD") or "",
                    "accion": r.get("ACCION_LOGISTICA") or "",
                    "observaciones": r.get("NOTA_LINEA_PEDIDO") or "",
                }
            )
    return {
        "desde": desde.isoformat() if desde else None,
        "fecha": fecha.isoformat() if fecha else None,
        "pedidos": list(pedidos.values()),
    }


TRUFFAUT_STORES = {
    "001CHE": "Chennevières-sur-Marne",
    "004NAN": "Nantes",
    "008BAI": "Baillet-en-France",
    "009VIL": "Villeparisis",
    "1026NICE PETRUCCIOLI": "Nice",
    "011PLA": "Plaisir",
    "012HER": "Herblay",
    "013SER": "Servon",
    "014LVB": "La Ville du Bois",
    "019AMI": "Amiens",
    "020TOB": "Toulouse-Blagnac",
    "024ORL": "Orléans",
    "031PAU": "Pau",
    "033CHM": "Chambourcy",
    "035PGS": "La Queue-en-Brie",
    "036NIM": "Nîmes",
    "040IVR": "Ivry-sur-Seine",
    "045CAB": "Cabriès",
    "047MON": "Montpellier",
    "050AUB": "Aubagne",
    "051MER": "Mérignac",
    "052ROS": "Rosny-Sous-Bois",
    "053GRI": "Grisy-Suisnes",
    "073FQX": "La Crau",
    "075TPM": "Toulon",
    "076BRS-BOULOGNE": "Boulogne-Billancourt",
    "085MTL": "Montélimar",
    "086ADP": "Saint-Ouen-L'Aumône",
}
TRUFFAUT_SIN_30 = {"260857"}
TRUFFAUT_WEB_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "web", "truffaut")
TRUFFAUT_WEB_TOKEN = "truffaut-otono-2026"


@app.get("/api/truffaut/reporte")
def get_truffaut_reporte(
    request: Request,
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", GET_LIMIT)
    cached = _cache_get("truffaut_reporte")
    if cached is not None:
        return cached
    rows = _query(f"""
        SELECT p.NUMERO_PEDIDO AS n, p.REFERENCIA_PEDIDO AS ref, p.NUMERO_CLIENTE AS cli,
               c.N_COMERCIAL AS ncom, p.FECHA_PEDIDO AS fp, p.FECHA_CARGA AS fc,
               p.MODO_PORTES AS mp, CAST(p.TOTAL_PEDIDO AS FLOAT64) AS tot
        FROM `{PROJECT}.{DATASET}.PEDIDOS` p
        LEFT JOIN `{PROJECT}.{DATASET}.CLIENTE` c ON c.ID_CLIENTE = p.NUMERO_CLIENTE
        WHERE (p.REFERENCIA_PEDIDO LIKE 'TRUFFAUT OTOÑO' OR p.REFERENCIA_PEDIDO LIKE '%/D30')
          AND p.NUMERO_CLIENTE != '34999'
          AND CAST(p.TOTAL_PEDIDO AS FLOAT64) > 0
          AND CAST(p.ESTADO_PEDIDO AS INT64) = 0
        ORDER BY p.NUMERO_PEDIDO
    """)
    orders = []
    nums = []
    for r in rows:
        nums.append(r["n"])
        orders.append({
            "n": r["n"],
            "ref": r["ref"] or "",
            "cli": r["cli"] or "",
            "ncom": r["ncom"] or "",
            "store": TRUFFAUT_STORES.get(r["ncom"], r["ncom"] or ""),
            "fp": r["fp"].strftime("%d/%m/%Y") if r["fp"] else None,
            "fc": r["fc"].strftime("%d/%m/%Y") if r["fc"] else None,
            "mp": int(r["mp"] or 0),
            "tot": float(r["tot"] or 0),
            "pal": 0,
            "no30": r["n"] in TRUFFAUT_SIN_30,
            "lin": [],
        })
    if nums:
        inlist = ",".join("'" + n + "'" for n in nums)
        lines = _query(f"""
            SELECT NUMERO_PEDIDO AS n, POSICION_PEDIDO AS p, REFERENCIA_ARTICULO AS r,
                   DESCRIPCION_ARTICULO AS d, CAST(UNIDADES AS INT64) AS u,
                   CODIGO_LITRAJE AS l, PRECIO AS pr, UBICACION_EXTRA AS ubi,
                   FINCA_RELEVADA AS f, SECTOR_RELEVADO AS s, LINEA_ACTIVA AS act
            FROM `{PROJECT}.{DATASET}.LINEA_PEDIDO`
            WHERE NUMERO_PEDIDO IN ({inlist})
              AND LINEA_ACTIVA = TRUE
            ORDER BY NUMERO_PEDIDO, POSICION_PEDIDO
        """)
        by_n = {o["n"]: o for o in orders}
        for r in lines:
            o = by_n.get(r["n"])
            if not o:
                continue
            if r["r"] == "99998" and r["l"] == "EUR" and r["act"]:
                o["pal"] += int(r["u"] or 0)
            o["lin"].append({
                "p": int(r["p"] or 0),
                "r": r["r"] or "",
                "d": r["d"] or "",
                "u": int(r["u"] or 0),
                "l": r["l"] or "",
                "pr": float(r["pr"] or 0),
                "ubi": r["ubi"] or "",
                "f": r["f"] or "",
                "s": r["s"] or "",
            })
        for o in orders:
            o["lin"].sort(key=lambda x: x["p"])
    payload = {"generated": date.today().isoformat(), "orders": orders}
    _cache_set("truffaut_reporte", payload)
    return payload


@app.get("/truffaut")
def truffaut_web(k: Optional[str] = Query(default=None)):
    if k != TRUFFAUT_WEB_TOKEN:
        raise HTTPException(404, "Not found")
    return FileResponse(os.path.join(TRUFFAUT_WEB_DIR, "index.html"))


@app.get("/truffaut/data.json")
def truffaut_web_data(k: Optional[str] = Query(default=None)):
    if k != TRUFFAUT_WEB_TOKEN:
        raise HTTPException(404, "Not found")
    return FileResponse(os.path.join(TRUFFAUT_WEB_DIR, "data.json"))


@app.get("/api/catalogo")
def catalogo(
    request: Request,
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", GET_LIMIT)
    articulos = _query(
        f"""
        SELECT ID_ARTICULO, DESCRIPCION_ARTICULO, CODIGO_EAN, FINCA_ARTICULO
        FROM `{PROJECT}.{DATASET}.ARTICULOS`
        WHERE DESCATALOGADO = 0
        """
    )
    eans = _query(
        f"""
        SELECT REFERENCIA_ARTICULO, CODIGO_EAN, CODIGO_LITRAJE, CODIGO_SECTOR
        FROM `{PROJECT}.{DATASET}.CODIGOS_EAN`
        WHERE CODIGO_EAN IS NOT NULL
        """
    )
    litrajes = _query(f"SELECT ID_LITRAJE, DESCRIPCION_LITRAJE FROM `{PROJECT}.{DATASET}.LITRAJES`")
    return {
        "articulos": articulos,
        "eans": eans,
        "litrajes": litrajes,
    }


@app.get("/api/catalogo/version")
def catalogo_version(
    request: Request,
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, str]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", GET_LIMIT)
    rows = _query(
        f"""
        SELECT table_id, last_modified_time
        FROM `{PROJECT}.{DATASET}.__TABLES__`
        WHERE table_id IN ('ARTICULOS', 'CODIGOS_EAN', 'LITRAJES')
        """
    )
    fingerprint = "\n".join(
        f"{r['table_id']}:{r['last_modified_time']}"
        for r in sorted(rows, key=lambda r: r['table_id'])
    )
    version = hashlib.sha256(fingerprint.encode()).hexdigest()
    return {"version": version}


class RegistroPicking(BaseModel):
    record_id: str = Field(min_length=1, max_length=64)
    order_id: str = Field(min_length=1, max_length=64)
    picking_numero: int = Field(ge=1, le=50)
    picking_tipo: str = Field(pattern="^[IF]$")
    order_line_id: str = Field(default="", max_length=64)
    ean_escaneado: str = Field(default="", max_length=32)
    ocr_texto: str = Field(default="", max_length=500)
    ref_original: str = Field(default="", max_length=64)
    ref_servida: str = Field(default="", max_length=64)
    sustituido: bool = False
    litros: Optional[float] = Field(default=None, ge=0, le=10000)
    medida: str = Field(default="", max_length=64)
    calibre: str = Field(default="", max_length=64)
    cantidad_partida: float = Field(default=0, ge=-1_000_000, le=1_000_000)
    fecha_hora: str = Field(min_length=1, max_length=64)
    empleado_email: str = Field(default="", max_length=128)
    empleado_nombre: str = Field(default="", max_length=128)


class UploadBody(BaseModel):
    registros: list[RegistroPicking] = Field(max_length=MAX_REGISTROS)


@app.post("/api/picking/upload")
def upload(
    request: Request,
    body: UploadBody,
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    _ensure_picking_table()
    if not body.registros:
        return {"ok": 0, "duplicados": 0, "accepted_ids": []}

    pending_ids = [r.record_id for r in body.registros]
    existing = client.query(
        f"""
        SELECT record_id FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_TABLE}`
        WHERE record_id IN UNNEST(@ids)
        """,
        job_config=bigquery.QueryJobConfig(
            query_parameters=[bigquery.ArrayQueryParameter("ids", "STRING", pending_ids)]
        ),
    ).result()
    existing_ids = {row["record_id"] for row in existing}

    nuevos = [r for r in body.registros if r.record_id not in existing_ids]
    if not nuevos:
        return {"ok": 0, "duplicados": len(body.registros), "accepted_ids": []}

    errors = client.insert_rows_json(
        f"{PROJECT}.{PICKING_DATASET}.{PICKING_TABLE}",
        [r.model_dump() for r in nuevos],
    )
    if errors:
        raise HTTPException(status_code=500, detail=str(errors[:5]))
    return {"ok": len(nuevos), "duplicados": len(pending_ids) - len(nuevos), "accepted_ids": [r.record_id for r in nuevos]}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", "8080")))
