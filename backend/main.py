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
from fastapi.responses import FileResponse, HTMLResponse, StreamingResponse
from google.cloud import bigquery, storage
from google.cloud.exceptions import NotFound
from pydantic import BaseModel, Field
from starlette.concurrency import run_in_threadpool

import punteo_report
import punteo_pdf
import punteo_html

PROJECT = os.getenv("GCP_PROJECT", "dashboard-439511")
DATASET = "GestionComercialVE"
PICKING_DATASET = "pickingve"
PICKING_TABLE = "picking_registros"
COMPENSACIONES_TABLE = "picking_compensaciones"
PICKING_VIEW = "picking_registros_v"
ENCARGADOS_TABLE = "encargados"
OPERARIOS_TABLE = "operarios"
FINCAS_TABLE = "fincas"
MAQUINARIAS_TABLE = "maquinarias"
MAQUINARIAS_FAMILIAS_TABLE = "maquinaria_familias"
REPARTO_TABLE = "reparto_faena"
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
    allow_methods=["GET", "POST"],
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
        client.get_dataset(f"{PROJECT}.{PICKING_DATASET}")
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


def _ensure_compensaciones_view() -> None:
    client.query(
        f"""
        CREATE TABLE IF NOT EXISTS `{PROJECT}.{PICKING_DATASET}.{COMPENSACIONES_TABLE}` (
            record_id STRING,
            pedido_id STRING,
            cantidad FLOAT64,
            creado_en TIMESTAMP
        )
        """
    ).result()
    client.query(
        f"""
        CREATE OR REPLACE VIEW `{PROJECT}.{PICKING_DATASET}.{PICKING_VIEW}` AS
        SELECT d.* FROM (
            SELECT p.*, ROW_NUMBER() OVER (PARTITION BY record_id ORDER BY fecha_hora DESC) AS rn
            FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_TABLE}` p
        ) d
        LEFT JOIN `{PROJECT}.{PICKING_DATASET}.{COMPENSACIONES_TABLE}` c
            ON c.record_id = d.record_id
        WHERE d.rn = 1 AND c.record_id IS NULL
        """
    ).result()


def _hash_password(usuario: str, password: str) -> str:
    return hashlib.sha256(f"{usuario}:{PASSWORD_SALT}:{password}".encode()).hexdigest()


def _ensure_encargados_table() -> None:
    dataset_ref = bigquery.Dataset(f"{PROJECT}.{PICKING_DATASET}")
    try:
        client.get_dataset(f"{PROJECT}.{PICKING_DATASET}")
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
    _ensure_column(ENCARGADOS_TABLE, "apellidos", "apellidos STRING")


def _migrar_apellidos_encargados() -> None:
    """D-69: separa nombre y apellidos en encargados existentes.

    Solo toca filas con apellidos vacío y nombre con espacios: el primer
    token queda como nombre y el resto como apellidos. Idempotente.
    """
    rows = _query(
        f"""
        SELECT id, nombre
        FROM `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}`
        WHERE (apellidos IS NULL OR apellidos = '') AND nombre LIKE '% %'
        """
    )
    for r in rows:
        partes = (r.get("nombre") or "").strip().split()
        if len(partes) < 2:
            continue
        client.query(
            f"""
            UPDATE `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}`
            SET nombre = @nombre, apellidos = @apellidos
            WHERE id = @id
            """,
            job_config=bigquery.QueryJobConfig(
                query_parameters=[
                    bigquery.ScalarQueryParameter("nombre", "STRING", partes[0]),
                    bigquery.ScalarQueryParameter("apellidos", "STRING", " ".join(partes[1:])),
                    bigquery.ScalarQueryParameter("id", "STRING", r["id"]),
                ]
            ),
        ).result()


def _ensure_operarios_table() -> None:
    dataset_ref = bigquery.Dataset(f"{PROJECT}.{PICKING_DATASET}")
    try:
        client.get_dataset(f"{PROJECT}.{PICKING_DATASET}")
    except NotFound:
        client.create_dataset(dataset_ref)
    client.query(
        f"""
        CREATE TABLE IF NOT EXISTS `{PROJECT}.{PICKING_DATASET}.{OPERARIOS_TABLE}` (
            id STRING,
            nombre STRING,
            apellidos STRING,
            email STRING,
            password_hash STRING,
            fincas_carga STRING,
            activo BOOL
        )
        """
    ).result()
    _ensure_column(OPERARIOS_TABLE, "apellidos", "apellidos STRING")
    _ensure_column(OPERARIOS_TABLE, "email", "email STRING")
    _ensure_column(OPERARIOS_TABLE, "activo", "activo BOOL")
    _ensure_column(OPERARIOS_TABLE, "maquinaria", "maquinaria STRING")


def _ensure_maquinarias_table() -> None:
    dataset_ref = bigquery.Dataset(f"{PROJECT}.{PICKING_DATASET}")
    try:
        client.get_dataset(f"{PROJECT}.{PICKING_DATASET}")
    except NotFound:
        client.create_dataset(dataset_ref)
    client.query(
        f"""
        CREATE TABLE IF NOT EXISTS `{PROJECT}.{PICKING_DATASET}.{MAQUINARIAS_TABLE}` (
            id STRING,
            nombre STRING,
            descripcion STRING,
            activo BOOL
        )
        """
    ).result()
    _ensure_column(MAQUINARIAS_TABLE, "descripcion", "descripcion STRING")
    _ensure_column(MAQUINARIAS_TABLE, "activo", "activo BOOL")
    _ensure_column(MAQUINARIAS_TABLE, "familia", "familia STRING")


def _ensure_maquinaria_familias_table() -> None:
    """D-76: familias de maquinaria (catálogo configurable en el panel)."""
    dataset_ref = bigquery.Dataset(f"{PROJECT}.{PICKING_DATASET}")
    try:
        client.get_dataset(f"{PROJECT}.{PICKING_DATASET}")
    except NotFound:
        client.create_dataset(dataset_ref)
    client.query(
        f"""
        CREATE TABLE IF NOT EXISTS `{PROJECT}.{PICKING_DATASET}.{MAQUINARIAS_FAMILIAS_TABLE}` (
            id STRING,
            nombre STRING,
            descripcion STRING,
            activo BOOL
        )
        """
    ).result()
    _ensure_column(MAQUINARIAS_FAMILIAS_TABLE, "descripcion", "descripcion STRING")
    _ensure_column(MAQUINARIAS_FAMILIAS_TABLE, "activo", "activo BOOL")


def _ensure_reparto_table() -> None:
    """D-72: reparto de faena por línea (pedido + huella) para la app futura."""
    dataset_ref = bigquery.Dataset(f"{PROJECT}.{PICKING_DATASET}")
    try:
        client.get_dataset(f"{PROJECT}.{PICKING_DATASET}")
    except NotFound:
        client.create_dataset(dataset_ref)
    client.query(
        f"""
        CREATE TABLE IF NOT EXISTS `{PROJECT}.{PICKING_DATASET}.{REPARTO_TABLE}` (
            pedido_id STRING,
            linea_huella STRING,
            operario_email STRING,
            operario_nombre STRING,
            actualizado_en TIMESTAMP
        )
        """
    ).result()


def _ensure_fincas_table() -> None:
    dataset_ref = bigquery.Dataset(f"{PROJECT}.{PICKING_DATASET}")
    try:
        client.get_dataset(f"{PROJECT}.{PICKING_DATASET}")
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
    _ensure_compensaciones_view()
    _ensure_encargados_table()
    _migrar_apellidos_encargados()
    _ensure_operarios_table()
    _ensure_fincas_table()
    _ensure_maquinarias_table()
    _ensure_maquinaria_familias_table()
    _ensure_reparto_table()
    _ensure_notificaciones_tables()
    _ensure_matriculas_table()
    _ensure_etiquetas_table()


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
    if update.get("callback_query"):
        return await run_in_threadpool(_telegram_callback, bot_token, update)
    return await run_in_threadpool(_telegram_mensaje_texto, bot_token, update)


def _telegram_callback(bot_token: str, update: dict[str, Any]) -> dict[str, Any]:
    callback = update.get("callback_query") or {}
    data = callback.get("data") or ""
    chat_id = str((callback.get("message") or {}).get("chat", {}).get("id") or "")
    message_id = (callback.get("message") or {}).get("message_id")
    callback_id = callback.get("id") or ""

    def responder(texto: str) -> None:
        try:
            _telegram_request(bot_token, "answerCallbackQuery", {"callback_query_id": callback_id, "text": texto})
        except Exception:
            pass

    def enviar(texto: str, teclado: Optional[list[list[dict[str, Any]]]] = None) -> None:
        payload: dict[str, Any] = {"chat_id": chat_id, "text": texto}
        if teclado:
            payload["reply_markup"] = {"inline_keyboard": teclado}
        _telegram_request(bot_token, "sendMessage", payload)

    if not chat_id:
        return {"ok": False}

    _guardar_oficina_chat_id(bot_token, chat_id)

    if data.startswith("check_"):
        try:
            responder("✅ Marcado como comprobado")
            if message_id:
                _telegram_request(
                    bot_token,
                    "editMessageReplyMarkup",
                    {
                        "chat_id": chat_id,
                        "message_id": message_id,
                        "reply_markup": {
                            "inline_keyboard": [[{"text": "✅ Comprobado", "callback_data": data}]]
                        },
                    },
                )
        except Exception:
            return {"ok": False}
        return {"ok": True}

    if data == "sinop":
        responder("")
        return {"ok": True}

    if data == "cancelar":
        _flujo_clear(bot_token, chat_id)
        responder("✖️ Cancelado")
        enviar("✖️ Operación cancelada.")
        return {"ok": True}

    if data in {"menu_pedido", "menu_linea"}:
        modo = "linea" if data == "menu_linea" else "pedido"
        _enviar_lista_pedidos(bot_token, chat_id, modo, 0)
        return {"ok": True}

    if data.startswith("pedidos:"):
        _, modo, offset = data.split(":")
        _enviar_lista_pedidos(bot_token, chat_id, modo, int(offset or 0))
        return {"ok": True}

    if data.startswith("pedido:"):
        _, modo, pedido = data.split(":", 2)
        if modo == "linea":
            _flujo_set(bot_token, chat_id, {"paso": "linea", "pedido": pedido})
            _enviar_lista_lineas(bot_token, chat_id, pedido, 0)
        else:
            _flujo_set(bot_token, chat_id, {"paso": "texto", "pedido": pedido})
            responder(f"Pedido {pedido} seleccionado")
            enviar(
                f"✍️ Escribe el mensaje para el pedido **{pedido}**:",
                _teclado_cancelar(),
            )
        return {"ok": True}

    if data.startswith("lineas:"):
        _, pedido, offset = data.split(":", 2)
        _enviar_lista_lineas(bot_token, chat_id, pedido, int(offset or 0))
        return {"ok": True}

    if data.startswith("linea:"):
        _, pedido, huella = data.split(":", 2)
        pos = _posicion_linea(pedido, huella)
        _flujo_set(bot_token, chat_id, {"paso": "texto", "pedido": pedido, "linea": huella})
        responder(f"Línea {pos} seleccionada")
        enviar(
            f"✍️ Escribe el mensaje para el pedido **{pedido}**, línea **{pos}**:",
            _teclado_cancelar(),
        )
        return {"ok": True}

    if data.startswith("responder:"):
        comentario_id = data.split(":", 1)[1]
        rows = _query(
            f"SELECT pedido_id, linea_huella, autor_nombre FROM `{PROJECT}.{PICKING_DATASET}.{COMENTARIOS_TABLE}` "
            f"WHERE comentario_id = {_esc(comentario_id)} LIMIT 1"
        )
        if not rows:
            responder("Mensaje no encontrado")
            return {"ok": True}
        c = rows[0]
        pedido = str(c.get("pedido_id") or "")
        linea = (c.get("linea_huella") or "") or None
        if not pedido:
            responder("Ese mensaje no tiene pedido asociado")
            return {"ok": True}
        autor = str(c.get("autor_nombre") or "encargado")
        _flujo_set(bot_token, chat_id, {"paso": "texto", "pedido": pedido, "linea": linea})
        pos = _posicion_linea(pedido, linea) if linea else None
        destino = f"pedido **{pedido}**" + (f", línea **{pos}**" if pos else "")
        responder("Respondiendo…")
        enviar(
            f"✍️ Responde a **{autor}** ({destino}):",
            _teclado_cancelar(),
        )
        return {"ok": True}

    responder("")
    return {"ok": True}


def _teclado_cancelar() -> list[list[dict[str, Any]]]:
    return [[{"text": "✖️ Cancelar", "callback_data": "cancelar"}]]


def _flujo_get(bot_token: str, chat_id: str) -> dict[str, Any]:
    rows = _query(
        f"SELECT valor_texto FROM `{PROJECT}.{PICKING_DATASET}.{NOTIFICACIONES_META_TABLE}` "
        f"WHERE clave = {_esc('flujo:' + bot_token + ':' + chat_id)}"
    )
    if not rows:
        return {}
    try:
        flujo = json.loads(rows[0]["valor_texto"] or "{}")
        return flujo if isinstance(flujo, dict) else {}
    except Exception:
        return {}


def _flujo_set(bot_token: str, chat_id: str, flujo: dict[str, Any]) -> None:
    client.query(
        f"""
        MERGE INTO `{PROJECT}.{PICKING_DATASET}.{NOTIFICACIONES_META_TABLE}` AS t
        USING (SELECT {_esc('flujo:' + bot_token + ':' + chat_id)} AS clave, {_esc(json.dumps(flujo))} AS valor_texto) AS s
        ON t.clave = s.clave
        WHEN MATCHED THEN UPDATE SET valor_texto = s.valor_texto
        WHEN NOT MATCHED THEN INSERT (clave, valor_texto) VALUES (s.clave, s.valor_texto)
        """
    ).result()


def _flujo_clear(bot_token: str, chat_id: str) -> None:
    client.query(
        f"DELETE FROM `{PROJECT}.{PICKING_DATASET}.{NOTIFICACIONES_META_TABLE}` "
        f"WHERE clave = {_esc('flujo:' + bot_token + ':' + chat_id)}"
    ).result()


def _pedidos_activos() -> list[dict[str, Any]]:
    return _query(
        f"""
        SELECT p.NUMERO_PEDIDO, p.FINCA_CARGA, p.FECHA_CARGA, c.N_COMERCIAL
        FROM `{PROJECT}.{DATASET}.PEDIDOS` p
        LEFT JOIN `{PROJECT}.{DATASET}.CLIENTE` c ON c.ID_CLIENTE = p.NUMERO_CLIENTE
        WHERE p.ESTADO_PEDIDO IN (1, 3)
          AND DATE(p.FECHA_CARGA) >= DATE(CURRENT_DATE())
        ORDER BY p.FECHA_CARGA DESC, p.NUMERO_PEDIDO DESC
        LIMIT 300
        """
    )


def _lineas_pedido(pedido: str) -> list[dict[str, Any]]:
    return _query(
        f"""
        SELECT HUELLA_DIGITAL, POSICION_PEDIDO, REFERENCIA_ARTICULO, DESCRIPCION_ARTICULO,
               DESCRIPCION_SISTEMA, CODIGO_LITRAJE, CODIGO_SECTOR
        FROM `{PROJECT}.{DATASET}.LINEA_PEDIDO`
        WHERE NUMERO_PEDIDO = {_esc(pedido)} AND LINEA_ACTIVA = TRUE
          AND UNIDADES_PENDIENTES > 0
        ORDER BY CAST(POSICION_PEDIDO AS INT64)
        """
    )


def _pedido_existe(pedido: str) -> bool:
    rows = _query(
        f"SELECT NUMERO_PEDIDO FROM `{PROJECT}.{DATASET}.PEDIDOS` "
        f"WHERE NUMERO_PEDIDO = {_esc(pedido)} LIMIT 1"
    )
    return bool(rows)


def _formato_pedido(r: dict[str, Any]) -> str:
    fecha = r.get("FECHA_CARGA")
    fecha_txt = ""
    if fecha is not None:
        try:
            fecha_txt = " · " + fecha.strftime("%d/%m")
        except Exception:
            fecha_txt = ""
    cliente = str(r.get("N_COMERCIAL") or "").strip()
    finca = str(r.get("FINCA_CARGA") or "").strip()
    return f"{r.get('NUMERO_PEDIDO')} · {cliente or 's/cliente'} · {finca or 's/finca'}{fecha_txt}"


def _formato_linea(r: dict[str, Any]) -> str:
    pos = str(r.get("POSICION_PEDIDO") or "")
    ref = str(r.get("REFERENCIA_ARTICULO") or "").strip()
    desc = str(r.get("DESCRIPCION_ARTICULO") or "").strip() or str(r.get("DESCRIPCION_SISTEMA") or "").strip()
    litraje = str(r.get("CODIGO_LITRAJE") or "").strip()
    sector = str(r.get("CODIGO_SECTOR") or "").strip()
    partes = [p for p in [f"L{pos}", ref, desc, litraje, sector] if p]
    return " · ".join(partes)


def _enviar_lista_pedidos(bot_token: str, chat_id: str, modo: str, offset: int) -> None:
    pedidos = _pedidos_activos()
    pagina = pedidos[offset:offset + 10]
    if not pagina:
        _telegram_request(
            bot_token, "sendMessage",
            {"chat_id": chat_id, "text": "No hay pedidos activos en este momento."},
        )
        return
    teclado: list[list[dict[str, Any]]] = []
    for p in pagina:
        teclado.append([
            {
                "text": _formato_pedido(p),
                "callback_data": f"pedido:{modo}:{p.get('NUMERO_PEDIDO')}",
            }
        ])
    total_paginas = (len(pedidos) + 9) // 10
    nav: list[dict[str, Any]] = []
    if offset > 0:
        nav.append({"text": "◀️", "callback_data": f"pedidos:{modo}:{max(0, offset - 10)}"})
    nav.append({"text": f"{offset // 10 + 1}/{total_paginas}", "callback_data": "sinop"})
    if offset + 10 < len(pedidos):
        nav.append({"text": "▶️", "callback_data": f"pedidos:{modo}:{offset + 10}"})
    teclado.append(nav)
    teclado.append([{"text": "✖️ Cancelar", "callback_data": "cancelar"}])
    titulo = "📦 Mensaje al pedido: elige un pedido activo:" if modo == "pedido" \
        else "📋 Mensaje a línea: elige primero el pedido:"
    _telegram_request(
        bot_token, "sendMessage",
        {"chat_id": chat_id, "text": titulo, "reply_markup": {"inline_keyboard": teclado}},
    )


def _enviar_lista_lineas(bot_token: str, chat_id: str, pedido: str, offset: int) -> None:
    lineas = _lineas_pedido(pedido)
    pagina = lineas[offset:offset + 12]
    if not pagina:
        _telegram_request(
            bot_token, "sendMessage",
            {"chat_id": chat_id, "text": f"El pedido {pedido} no tiene líneas activas."},
        )
        return
    teclado: list[list[dict[str, Any]]] = []
    for l in pagina:
        teclado.append([
            {
                "text": _formato_linea(l),
                "callback_data": f"linea:{pedido}:{l.get('HUELLA_DIGITAL')}",
            }
        ])
    total_paginas = (len(lineas) + 11) // 12
    nav: list[dict[str, Any]] = []
    if offset > 0:
        nav.append({"text": "◀️", "callback_data": f"lineas:{pedido}:{max(0, offset - 12)}"})
    nav.append({"text": f"{offset // 12 + 1}/{total_paginas}", "callback_data": "sinop"})
    if offset + 12 < len(lineas):
        nav.append({"text": "▶️", "callback_data": f"lineas:{pedido}:{offset + 12}"})
    teclado.append(nav)
    teclado.append([{"text": "✖️ Cancelar", "callback_data": "cancelar"}])
    _telegram_request(
        bot_token, "sendMessage",
        {
            "chat_id": chat_id,
            "text": f"📋 Líneas del pedido {pedido} (elige una):",
            "reply_markup": {"inline_keyboard": teclado},
        },
    )


# ---------------- Notificaciones push (FCM) y chat oficina <-> encargados ----------------

FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
FCM_URL = f"https://fcm.googleapis.com/v1/projects/{PROJECT}/messages:send"
FCM_TOKENS_TABLE = "fcm_tokens"
COMENTARIOS_TABLE = "comentarios"
NOTIFICACIONES_META_TABLE = "notificaciones_meta"
MATRICULAS_TABLE = "matriculas_pedido"
ETIQUETAS_TABLE = "etiquetas"

_fcm_token_cache: dict[str, tuple[float, str]] = {}


def _esc(value: Any) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, (int, float)):
        return str(value)
    s = str(value).replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
    return "'" + s + "'"


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


def _ensure_etiquetas_table() -> None:
    client.query(
        f"""
        CREATE TABLE IF NOT EXISTS `{PROJECT}.{PICKING_DATASET}.{ETIQUETAS_TABLE}` (
            pedido_id STRING NOT NULL,
            order_line_id STRING,
            referencia STRING NOT NULL,
            litraje STRING,
            sector STRING,
            cantidad FLOAT64,
            motivo STRING,
            estado STRING NOT NULL,
            creado_en TIMESTAMP,
            actualizado_en TIMESTAMP,
            actualizado_por STRING
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


def _es_superusuario(email: str) -> bool:
    rows = _query(
        f"SELECT 1 FROM `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}` "
        f"WHERE email = {_esc(email)} AND rol = 'SUPERUSUARIO' LIMIT 1"
    )
    return bool(rows)


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


def _guardar_oficina_chat_id(bot_token: str, chat_id: str) -> None:
    if not bot_token or not chat_id:
        return
    try:
        client.query(
            f"""
            MERGE INTO `{PROJECT}.{PICKING_DATASET}.{NOTIFICACIONES_META_TABLE}` AS t
            USING (SELECT {_esc('oficina_chat_id:' + bot_token)} AS clave, {_esc(chat_id)} AS valor_texto) AS s
            ON t.clave = s.clave
            WHEN MATCHED THEN UPDATE SET valor_texto = s.valor_texto
            WHEN NOT MATCHED THEN INSERT (clave, valor_texto) VALUES (s.clave, s.valor_texto)
            """
        ).result()
    except Exception:
        pass


def _registrar_comandos_bot(bot_token: str) -> None:
    if not bot_token:
        return
    try:
        comandos = [
            {"command": "start", "description": "Menú principal de pedidos"},
            {"command": "pedido", "description": "Escribir a un pedido (/pedido 260766)"},
            {"command": "linea", "description": "Escribir a una línea (/linea 260766 1)"},
            {"command": "cancelar", "description": "Cancelar selección actual"},
        ]
        _telegram_request(bot_token, "setMyCommands", {"commands": comandos})
    except Exception:
        pass


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
    linea = (linea or "").strip() or None
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
        lineas.append(f"👤 Cliente: {n_comercial}")
        if n_fiscal and n_fiscal.upper() != n_comercial.upper():
            lineas.append(f"🏢 Fiscal: {n_fiscal}")
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
    if linea:
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
        pedido_id, linea_huella or None, autor_email,
        autor_nombre or autor_email, rol, "app", texto.strip(), url,
    )
    return {"ok": True, "adjunto_url": url}


@app.get("/api/comentarios/recientes")
def comentarios_recientes(
    request: Request,
    dias: int = Query(default=14, ge=1, le=60),
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    """D-73: actividad de chat por pedido/línea para marcar "sin leer" en el panel.

    Devuelve el último mensaje ajeno a oficina (rol APP/ENCARGADO) por
    pedido+línea en los últimos N días. El panel compara con su marca local
    de última lectura (localStorage) para hacer parpadear el botón Mensajes.
    """
    _verify_manager_key(k, x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", GET_LIMIT)
    rows = [
        dict(r)
        for r in client.query(
            f"""
            SELECT pedido_id, COALESCE(linea_huella, '') AS linea_huella,
                   COUNT(*) AS total,
                   FORMAT_TIMESTAMP('%Y-%m-%dT%H:%M:%E6SZ', MAX(creado_en)) AS ultimo_mensaje
            FROM `{PROJECT}.{PICKING_DATASET}.{COMENTARIOS_TABLE}`
            WHERE creado_en > TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL @dias DAY)
              AND rol NOT IN ('OFICINA', 'ADMIN')
            GROUP BY pedido_id, linea_huella
            """,
            job_config=bigquery.QueryJobConfig(
                query_parameters=[bigquery.ScalarQueryParameter("dias", "INT64", dias)]
            ),
        ).result()
    ]
    return {"recientes": rows}
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

    # D-15X: primera matrícula de CAMIÓN registrada = camión en muelle.
    # Aviso ultra prioritario a los encargados de la finca del pedido.
    if tipo == "CAMION" and matricula_limpia:
        try:
            previas = _query(
                f"SELECT 1 FROM `{PROJECT}.{PICKING_DATASET}.{MATRICULAS_TABLE}` "
                f"WHERE pedido_id = {_esc(pedido_id)} AND tipo = 'CAMION' "
                f"AND matricula != '' AND creado_en < CURRENT_TIMESTAMP() - INTERVAL 1 MINUTE LIMIT 1"
            )
            ya_aviso = bool(previas)
            if not ya_aviso:
                finca = _finca_pedido(pedido_id)
                muelle_txt = f" en {muelle_limpio}" if muelle_limpio else ""
                cuerpo = (
                    f"🚚 El camión {matricula_limpia}{muelle_txt} está en el cargadero "
                    f"del pedido {pedido_id}: prioridad máxima para las líneas pendientes."
                )
                for email in _encargados_finca(finca):
                    _enviar_fcm(
                        email,
                        f"Camión en muelle · Pedido {pedido_id}",
                        cuerpo[:300],
                        {"tipo": "camion_llegado", "pedido": pedido_id},
                    )
                bot_token = os.getenv("TELEGRAM_MESSAGES_BOT_TOKEN", "") or os.getenv("TELEGRAM_BOT_TOKEN", "")
                chat_id = _oficina_chat_id(bot_token) if bot_token else None
                if bot_token and chat_id:
                    _telegram_request(
                        bot_token,
                        "sendMessage",
                        {"chat_id": chat_id, "text": cuerpo},
                    )
        except Exception:
            pass
    return {"ok": True, "foto_url": foto_url}


def _telegram_mensaje_texto(bot_token: str, update: dict[str, Any]) -> dict[str, Any]:
    message = update.get("message") or {}
    text = (message.get("text") or "").strip()
    if not text:
        return {"ok": True}
    chat_id = str((message.get("chat") or {}).get("id") or "")
    from_user = message.get("from") or {}
    nombre = f"{(from_user.get('first_name') or '')} {(from_user.get('last_name') or '')}".strip() or "Oficina"
    if not chat_id:
        return {"ok": True}

    _guardar_oficina_chat_id(bot_token, chat_id)

    def responder_chat(mensaje: str, teclado: Optional[list[list[dict[str, Any]]]] = None) -> None:
        payload: dict[str, Any] = {"chat_id": chat_id, "text": mensaje}
        if teclado:
            payload["reply_markup"] = {"inline_keyboard": teclado}
        try:
            _telegram_request(bot_token, "sendMessage", payload)
        except Exception:
            pass

    # --- Comandos del menú ---
    if text.startswith("/"):
        cmd = text.split()[0].lower()
        resto = text[len(cmd):].strip()
        if cmd == "/start":
            _registrar_comandos_bot(bot_token)
            _enviar_lista_pedidos(bot_token, chat_id, "pedido", 0)
            return {"ok": True}
        if cmd == "/cancelar":
            _flujo_clear(bot_token, chat_id)
            responder_chat("✖️ Selección cancelada.")
            return {"ok": True}
        if cmd == "/pedido":
            if resto:
                pedido = resto.split()[0]
                if not _pedido_existe(pedido):
                    responder_chat(f"No existe el pedido {pedido}.")
                    return {"ok": True}
                _flujo_set(bot_token, chat_id, {"paso": "texto", "pedido": pedido})
                responder_chat(
                    f"✍️ Escribe el mensaje para el pedido **{pedido}**:",
                    _teclado_cancelar(),
                )
            else:
                _enviar_lista_pedidos(bot_token, chat_id, "pedido", 0)
            return {"ok": True}
        if cmd == "/linea":
            partes = resto.split()
            if len(partes) >= 1 and partes[0]:
                pedido = partes[0]
                if not _pedido_existe(pedido):
                    responder_chat(f"No existe el pedido {pedido}.")
                    return {"ok": True}
                if len(partes) >= 2 and partes[1]:
                    rows = _query(
                        f"SELECT HUELLA_DIGITAL FROM `{PROJECT}.{DATASET}.LINEA_PEDIDO` "
                        f"WHERE NUMERO_PEDIDO = {_esc(pedido)} AND POSICION_PEDIDO = CAST({_esc(partes[1])} AS INT64) "
                        f"AND LINEA_ACTIVA = TRUE LIMIT 1"
                    )
                    if not rows:
                        responder_chat(f"No existe la línea {partes[1]} del pedido {pedido}.")
                        return {"ok": True}
                    _flujo_set(bot_token, chat_id, {"paso": "texto", "pedido": pedido, "linea": rows[0]["HUELLA_DIGITAL"]})
                    responder_chat(
                        f"✍️ Escribe el mensaje para el pedido **{pedido}**, línea **{partes[1]}**:",
                        _teclado_cancelar(),
                    )
                else:
                    _flujo_set(bot_token, chat_id, {"paso": "linea", "pedido": pedido})
                    _enviar_lista_lineas(bot_token, chat_id, pedido, 0)
            else:
                _enviar_lista_pedidos(bot_token, chat_id, "linea", 0)
            return {"ok": True}
        responder_chat(
            "Comandos disponibles:\n"
            "/start — menú de pedidos\n"
            "/pedido 260766 — escribir al pedido\n"
            "/linea 260766 1 — escribir a una línea\n"
            "/cancelar — cancelar la selección"
        )
        return {"ok": True}

    def publicar(pedido: str, linea: Optional[str], cuerpo: str) -> None:
        _insertar_comentario(pedido, linea, "oficina@telegram", nombre, "SUPERUSUARIO", "telegram", cuerpo)
        if pedido:
            finca = _finca_pedido(pedido)
            destinos = _encargados_finca(finca)
        else:
            finca = ""
            destinos = [r["email"] for r in _query(
                f"SELECT email FROM `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}`"
            )]
        for email in destinos:
            try:
                _enviar_fcm(
                    email,
                    f"Mensaje de la oficina · Pedido {pedido}" if pedido else "Aviso de la oficina",
                    cuerpo,
                    {"tipo": "comentario", "pedido": pedido, "linea": linea or "", "canal": "telegram"},
                )
            except Exception:
                pass

    # --- Responder nativo de Telegram: reply_to_message sobre un mensaje del bot ---
    reply = message.get("reply_to_message") or {}
    reply_text = f"{(reply.get('text') or '')} {(reply.get('caption') or '')}"
    pm = re.search(r"Pedido\s+(\d+)", reply_text) if reply_text else None
    if pm:
        flujo = _flujo_get(bot_token, chat_id)
        if flujo.get("paso") != "texto":
            pedido_reply = pm.group(1)
            lm = re.search(r"Línea\s+(\d+)", reply_text)
            huella = None
            if lm:
                rows = _query(
                    f"SELECT HUELLA_DIGITAL FROM `{PROJECT}.{DATASET}.LINEA_PEDIDO` "
                    f"WHERE NUMERO_PEDIDO = {_esc(pedido_reply)} AND POSICION_PEDIDO = CAST({_esc(lm.group(1))} AS INT64) "
                    f"AND LINEA_ACTIVA = TRUE LIMIT 1"
                )
                if rows:
                    huella = rows[0]["HUELLA_DIGITAL"]
            publicar(pedido_reply, huella, text)
            pos = _posicion_linea(pedido_reply, huella) if huella else None
            destino = f"pedido {pedido_reply}" + (f", línea {pos}" if pos else "")
            responder_chat(f"✅ Mensaje enviado a los encargados del {destino}.")
            return {"ok": True}

    # --- Flujo de menú pendiente (esperando texto) ---
    flujo = _flujo_get(bot_token, chat_id)
    if flujo.get("paso") == "texto":
        pedido = str(flujo.get("pedido") or "")
        linea = (flujo.get("linea") or "") or None
        _flujo_clear(bot_token, chat_id)
        publicar(pedido, linea, text)
        if pedido:
            pos = _posicion_linea(pedido, linea) if linea else None
            destino = f"pedido {pedido}" + (f", línea {pos}" if pos else "")
            responder_chat(f"✅ Mensaje enviado a los encargados del {destino}.")
        else:
            responder_chat("✅ Mensaje enviado a todos los encargados.")
        return {"ok": True}

    # --- Atajo directo: #pedido texto ---
    m = re.match(r"^\s*#(\d+)\b\s*(.*)$", text, re.S)
    if m:
        pedido = m.group(1)
        cuerpo = (m.group(2) or "").strip() or "(sin texto)"
        if not _pedido_existe(pedido):
            responder_chat(
                f"⚠️ El pedido **#{pedido}** no existe. Revisa el número.",
                _teclado_principal(),
            )
            return {"ok": True}
        publicar(pedido, None, f"#{pedido} {cuerpo}")
        responder_chat(f"✅ Mensaje registrado y enviado a los encargados del pedido {pedido}.")
        return {"ok": True}

    # --- Sin # ni flujo: menú principal ---
    responder_chat(
        "¿Qué quieres enviar a los encargados?",
        _teclado_principal(),
    )
    return {"ok": True}


def _teclado_principal() -> list[list[dict[str, Any]]]:
    return [
        [{"text": "?? Mensaje al pedido", "callback_data": "menu_pedido"}],
        [{"text": "?? Mensaje a línea de pedido", "callback_data": "menu_linea"}],
        [{"text": "?? Cancelar", "callback_data": "cancelar"}],
    ]


class CambioLineaDetalle(BaseModel):
    pedido: str
    linea: str = ""
    tipo: str  # "nueva", "borrada", "cantidad"
    descripcion: str = ""


class NotificarRequest(BaseModel):
    pedidos_modificados: list[str] = Field(default_factory=list)
    cambios_detalle: list[CambioLineaDetalle] = Field(default_factory=list)


@app.post("/api/notificar")
def notificar_cambios(
    request: Request,
    body: NotificarRequest,
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
    pedidos_notificados = set()

    # 1) Pedidos con detalle de cambios (nueva API)
    for cambio in body.cambios_detalle:
        pedido = cambio.pedido
        if pedido in pedidos_notificados:
            continue
        finca = _finca_pedido(pedido)
        for email in _encargados_finca(finca):
            if _enviar_fcm(
                email,
                f"Pedido {pedido} modificado",
                cambio.descripcion or "Revisa las líneas en la app",
                {"tipo": "pedido_modificado", "pedido": pedido, "linea": cambio.linea, "cambio_tipo": cambio.tipo},
            ):
                enviadas += 1
        pedidos_notificados.add(pedido)

    # 2) Pedidos pasados sin detalle (compatibilidad: solo IDs)
    for pedido in body.pedidos_modificados:
        if pedido in pedidos_notificados:
            continue
        finca = _finca_pedido(pedido)
        for email in _encargados_finca(finca):
            if _enviar_fcm(
                email,
                f"Pedido {pedido} modificado",
                "Revisa las líneas en la app",
                {"tipo": "pedido_modificado", "pedido": pedido, "linea": ""},
            ):
                enviadas += 1
        pedidos_notificados.add(pedido)

    # 3) Pedidos modificados en BigQuery desde el último chequeo (compatibilidad)
    pedidos_bq = _query(
        f"SELECT NUMERO_PEDIDO, FINCA_CARGA FROM `{PROJECT}.{DATASET}.PEDIDOS` "
        f"WHERE FECHA_MODIFICACION > DATETIME({_esc(wm_str)}) AND ESTADO_PEDIDO IN (1, 3) "
        f"AND DATE(FECHA_CARGA) >= DATE(CURRENT_DATE())"
    )
    for r in pedidos_bq:
        pedido = str(r.get("NUMERO_PEDIDO") or "")
        if pedido in pedidos_notificados:
            continue
        finca = r.get("FINCA_CARGA") or ""
        for email in _encargados_finca(finca):
            if _enviar_fcm(
                email,
                f"Pedido {pedido} modificado",
                "Revisa las líneas en la app",
                {"tipo": "pedido_modificado", "pedido": pedido, "linea": ""},
            ):
                enviadas += 1
        pedidos_notificados.add(pedido)

    comentarios = _query(
        f"SELECT comentario_id, pedido_id, linea_huella, autor_email, autor_nombre, rol, canal, texto, adjunto_url, creado_en "
        f"FROM `{PROJECT}.{PICKING_DATASET}.{COMENTARIOS_TABLE}` "
        f"WHERE creado_en > TIMESTAMP({_esc(wm_str)}) "
        f"AND (canal <> 'telegram' OR creado_en < TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 3 MINUTE))"
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
            autor_email = str(c.get("autor_email") or "")
            ofis = _query(
                f"SELECT email FROM `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}` "
                f"WHERE rol = 'SUPERUSUARIO'"
            )
            for ofi in ofis:
                if ofi["email"] == autor_email:
                    continue
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
                            {"chat_id": chat_id, "text": cuerpo,
                             "reply_markup": {"inline_keyboard": [[
                                 {"text": "↩️ Responder", "callback_data": f"responder:{c.get('comentario_id')}"}
                             ]]}},
                        )
                    else:
                        _telegram_request(
                            bot_token,
                            "sendMessage",
                            {"chat_id": chat_id, "text": cuerpo,
                             "reply_markup": {"inline_keyboard": [[
                                 {"text": "↩️ Responder", "callback_data": f"responder:{c.get('comentario_id')}"}
                             ]]}},
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
    return {"ok": True, "pedidos_modificados": len(pedidos_notificados), "comentarios_nuevos": len(comentarios), "notificaciones_enviadas": enviadas}


@app.get("/api/encargados")
def lista_encargados(
    request: Request,
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_manager_key(k, x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", GET_LIMIT)
    rows = _query(
        f"""
        SELECT id, nombre, apellidos, usuario, password_hash, rol, fincas_carga, modo, email, activo
        FROM `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}`
        ORDER BY nombre
        """
    )
    return {
        "encargados": [
            {
                **r,
                "email": r.get("email") or "",
                "apellidos": r.get("apellidos") or "",
                "activo": r.get("activo") is not False,
            }
            for r in rows
        ]
    }


@app.get("/api/operarios")
def lista_operarios(
    request: Request,
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_manager_key(k, x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", GET_LIMIT)
    _ensure_operarios_table()
    rows = _query(
        f"""
        SELECT id, nombre, apellidos, email, password_hash, fincas_carga, maquinaria, activo
        FROM `{PROJECT}.{PICKING_DATASET}.{OPERARIOS_TABLE}`
        ORDER BY nombre
        """
    )
    return {
        "operarios": [
            {
                **r,
                "email": r.get("email") or "",
                "apellidos": r.get("apellidos") or "",
                "maquinaria": r.get("maquinaria") or "",
                "activo": r.get("activo") is not False,
            }
            for r in rows
        ]
    }


class OperarioRequest(BaseModel):
    id: Optional[str] = None
    nombre: str
    apellidos: str = ""
    email: str
    password: Optional[str] = None
    fincas_carga: str = ""
    maquinaria: str = ""
    activo: bool = True


@app.post("/api/operarios")
def crear_operario(
    req: OperarioRequest,
    request: Request,
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_manager_key(k, x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    _ensure_operarios_table()
    op_id = req.id or uuid.uuid4().hex
    pwd_hash = _hash_password(req.email, req.password) if req.password else None

    existing = _query(f"SELECT password_hash FROM `{PROJECT}.{PICKING_DATASET}.{OPERARIOS_TABLE}` WHERE email = {_esc(req.email)}")
    if not pwd_hash and existing:
        pwd_hash = existing[0].get("password_hash")
    elif not pwd_hash:
        pwd_hash = _hash_password(req.email, "1234")

    client.query(
        f"""
        MERGE `{PROJECT}.{PICKING_DATASET}.{OPERARIOS_TABLE}` T
        USING (SELECT {_esc(op_id)} AS id, {_esc(req.nombre)} AS nombre, {_esc(req.apellidos)} AS apellidos,
                      {_esc(req.email)} AS email, {_esc(pwd_hash)} AS password_hash, {_esc(req.fincas_carga)} AS fincas_carga,
                      {_esc(req.maquinaria)} AS maquinaria, {str(req.activo).upper()} AS activo) S
        ON T.email = S.email
        WHEN MATCHED THEN UPDATE SET nombre = S.nombre, apellidos = S.apellidos, password_hash = COALESCE(S.password_hash, T.password_hash), fincas_carga = S.fincas_carga, maquinaria = S.maquinaria, activo = S.activo
        WHEN NOT MATCHED THEN INSERT (id, nombre, apellidos, email, password_hash, fincas_carga, maquinaria, activo) VALUES (S.id, S.nombre, S.apellidos, S.email, S.password_hash, S.fincas_carga, S.maquinaria, S.activo)
        """
    ).result()
    return {"ok": True}


@app.post("/api/operarios/eliminar")
def eliminar_operario(
    req: dict[str, str],
    request: Request,
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_manager_key(k, x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    email = req.get("email")
    if not email:
        raise HTTPException(400, "Email obligatorio")
    client.query(f"DELETE FROM `{PROJECT}.{PICKING_DATASET}.{OPERARIOS_TABLE}` WHERE email = {_esc(email)}").result()
    return {"ok": True}


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
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_manager_key(k, x_api_key)
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
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_manager_key(k, x_api_key)
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
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_manager_key(k, x_api_key)
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
    return {"ok": 1}


class MaquinariaBody(BaseModel):
    id: Optional[str] = None
    nombre: str = Field(min_length=1, max_length=128)
    descripcion: str = Field(default="", max_length=256)
    familia: str = Field(default="", max_length=128)
    activo: bool = True


@app.get("/api/manager/maquinarias")
def lista_maquinarias(
    request: Request,
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_manager_key(k, x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", GET_LIMIT)
    _ensure_maquinarias_table()
    rows = _query(
        f"""
        SELECT id, nombre, descripcion, familia, activo
        FROM `{PROJECT}.{PICKING_DATASET}.{MAQUINARIAS_TABLE}`
        ORDER BY nombre
        """
    )
    return {
        "maquinarias": [
            {
                **r,
                "descripcion": r.get("descripcion") or "",
                "familia": r.get("familia") or "",
                "activo": r.get("activo") is not False,
            }
            for r in rows
        ]
    }


@app.post("/api/manager/maquinarias")
def guardar_maquinaria(
    request: Request,
    body: MaquinariaBody,
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_manager_key(k, x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    _ensure_maquinarias_table()
    mq_id = body.id or ""
    if not mq_id:
        # D-77: alta idempotente — si ya existe una maquinaria con el mismo
        # nombre (doble clic incluido), se actualiza esa fila en vez de duplicar.
        existentes = _query(
            f"""
            SELECT id FROM `{PROJECT}.{PICKING_DATASET}.{MAQUINARIAS_TABLE}`
            WHERE LOWER(TRIM(nombre)) = LOWER(TRIM({_esc(body.nombre)}))
            ORDER BY id LIMIT 1
            """
        )
        mq_id = existentes[0]["id"] if existentes else f"MQ-{uuid.uuid4().hex[:8].upper()}"
    client.query(
        f"""
        MERGE `{PROJECT}.{PICKING_DATASET}.{MAQUINARIAS_TABLE}` T
        USING (SELECT {_esc(mq_id)} AS id, {_esc(body.nombre.strip())} AS nombre,
                      {_esc(body.descripcion)} AS descripcion, {_esc((body.familia or '').strip())} AS familia,
                      {str(body.activo).upper()} AS activo) S
        ON T.id = S.id
        WHEN MATCHED THEN UPDATE SET nombre = S.nombre, descripcion = S.descripcion, familia = S.familia, activo = S.activo
        WHEN NOT MATCHED THEN INSERT (id, nombre, descripcion, familia, activo) VALUES (S.id, S.nombre, S.descripcion, S.familia, S.activo)
        """
    ).result()
    return {"ok": True}


# ===== D-76: Familias de maquinaria =====


@app.get("/api/manager/maquinarias-familias")
def lista_maquinarias_familias(
    request: Request,
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_manager_key(k, x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", GET_LIMIT)
    _ensure_maquinaria_familias_table()
    rows = _query(
        f"""
        SELECT id, nombre, descripcion, activo
        FROM `{PROJECT}.{PICKING_DATASET}.{MAQUINARIAS_FAMILIAS_TABLE}`
        ORDER BY nombre
        """
    )
    return {
        "familias": [
            {**r, "descripcion": r.get("descripcion") or "", "activo": r.get("activo") is not False}
            for r in rows
        ]
    }


@app.post("/api/manager/maquinarias-familias")
def guardar_maquinaria_familia(
    request: Request,
    body: MaquinariaBody,
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_manager_key(k, x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    _ensure_maquinaria_familias_table()
    fam_id = body.id or ""
    if not fam_id:
        # D-77: alta idempotente — si ya existe una familia con el mismo
        # nombre (doble clic incluido), se actualiza esa fila en vez de duplicar.
        existentes = _query(
            f"""
            SELECT id FROM `{PROJECT}.{PICKING_DATASET}.{MAQUINARIAS_FAMILIAS_TABLE}`
            WHERE LOWER(TRIM(nombre)) = LOWER(TRIM({_esc(body.nombre)}))
            ORDER BY id LIMIT 1
            """
        )
        fam_id = existentes[0]["id"] if existentes else f"MF-{uuid.uuid4().hex[:8].upper()}"
    client.query(
        f"""
        MERGE `{PROJECT}.{PICKING_DATASET}.{MAQUINARIAS_FAMILIAS_TABLE}` T
        USING (SELECT {_esc(fam_id)} AS id, {_esc(body.nombre.strip())} AS nombre,
                      {_esc(body.descripcion)} AS descripcion, {str(body.activo).upper()} AS activo) S
        ON T.id = S.id
        WHEN MATCHED THEN UPDATE SET nombre = S.nombre, descripcion = S.descripcion, activo = S.activo
        WHEN NOT MATCHED THEN INSERT (id, nombre, descripcion, activo) VALUES (S.id, S.nombre, S.descripcion, S.activo)
        """
    ).result()
    return {"ok": True}


@app.post("/api/manager/maquinarias-familias/eliminar")
def eliminar_maquinaria_familia(
    request: Request,
    body: dict[str, str],
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_manager_key(k, x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    fam_id = (body.get("id") or "").strip()
    if not fam_id:
        raise HTTPException(400, "Id de familia obligatorio")
    client.query(
        f"DELETE FROM `{PROJECT}.{PICKING_DATASET}.{MAQUINARIAS_FAMILIAS_TABLE}` WHERE id = {_esc(fam_id)}"
    ).result()
    return {"ok": True}


@app.post("/api/manager/maquinarias/eliminar")
def eliminar_maquinaria(
    request: Request,
    body: dict[str, str],
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_manager_key(k, x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    mq_id = (body.get("id") or "").strip()
    if not mq_id:
        raise HTTPException(400, "Id de maquinaria obligatorio")
    client.query(
        f"DELETE FROM `{PROJECT}.{PICKING_DATASET}.{MAQUINARIAS_TABLE}` WHERE id = {_esc(mq_id)}"
    ).result()
    return {"ok": True}


@app.get("/api/manager/reparto")
def lista_reparto(
    request: Request,
    fecha: Optional[str] = Query(None, description="Fecha de carga (YYYY-MM-DD)"),
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    """D-72: asignaciones de faena guardadas para los pedidos de una fecha."""
    _verify_manager_key(k, x_api_key)
    target_date = date.today()
    if fecha and fecha not in ("null", "undefined", ""):
        try:
            target_date = date.fromisoformat(fecha)
        except ValueError:
            pass
    rows = [
        dict(r)
        for r in client.query(
            f"""
            SELECT rf.pedido_id, rf.linea_huella, rf.operario_email, rf.operario_nombre,
                   FORMAT_TIMESTAMP('%Y-%m-%dT%H:%M:%E6SZ', rf.actualizado_en) AS actualizado_en
            FROM `{PROJECT}.{PICKING_DATASET}.{REPARTO_TABLE}` rf
            JOIN `{PROJECT}.{DATASET}.PEDIDOS` p ON p.NUMERO_PEDIDO = rf.pedido_id
            WHERE DATE(p.FECHA_CARGA) = @fecha
            """,
            job_config=bigquery.QueryJobConfig(
                query_parameters=[bigquery.ScalarQueryParameter("fecha", "DATE", target_date.isoformat())]
            ),
        ).result()
    ]
    return {"asignaciones": rows}


class RepartoAsignacion(BaseModel):
    pedido_id: str
    linea_huella: str
    operario_nombre: str = ""
    operario_email: str = ""


class RepartoBody(BaseModel):
    asignaciones: list[RepartoAsignacion]


@app.post("/api/manager/reparto")
def guardar_reparto(
    request: Request,
    body: RepartoBody,
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    """D-72: guarda el reparto de faena.

    La app Android (cuando exista el módulo de faena) leerá esta tabla con
    GET /api/manager/reparto?fecha=... o un endpoint dedicado por encargado.
    Clave lógica: (pedido_id, linea_huella). Operario vacío = desasignar.
    """
    _verify_manager_key(k, x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    _ensure_reparto_table()
    emails = {
        r["nombre"]: r["email"]
        for r in _query(
            f"SELECT nombre, email FROM `{PROJECT}.{PICKING_DATASET}.{OPERARIOS_TABLE}`"
        )
    }
    guardadas, borradas = 0, 0
    for a in body.asignaciones:
        huella = (a.linea_huella or "").strip()
        pedido = (a.pedido_id or "").strip()
        if not pedido or not huella:
            continue
        op_nombre = (a.operario_nombre or "").strip()
        if not op_nombre:
            client.query(
                f"DELETE FROM `{PROJECT}.{PICKING_DATASET}.{REPARTO_TABLE}` "
                f"WHERE pedido_id = {_esc(pedido)} AND linea_huella = {_esc(huella)}"
            ).result()
            borradas += 1
            continue
        op_email = a.operario_email or emails.get(op_nombre, "")
        client.query(
            f"""
            MERGE `{PROJECT}.{PICKING_DATASET}.{REPARTO_TABLE}` T
            USING (
                SELECT {_esc(pedido)} AS pedido_id, {_esc(huella)} AS linea_huella,
                       {_esc(op_email)} AS operario_email, {_esc(op_nombre)} AS operario_nombre,
                       CURRENT_TIMESTAMP() AS actualizado_en
            ) S
            ON T.pedido_id = S.pedido_id AND T.linea_huella = S.linea_huella
            WHEN MATCHED THEN UPDATE SET operario_email = S.operario_email, operario_nombre = S.operario_nombre, actualizado_en = S.actualizado_en
            WHEN NOT MATCHED THEN INSERT (pedido_id, linea_huella, operario_email, operario_nombre, actualizado_en)
                VALUES (S.pedido_id, S.linea_huella, S.operario_email, S.operario_nombre, S.actualizado_en)
            """
        ).result()
        guardadas += 1
    return {"ok": True, "guardadas": guardadas, "borradas": borradas}


# ---------------- D-15X Logística: cierre de línea, discrepancias y perfil operario ----------------

CIERRES_TABLE = "cierres_linea"

MOTIVOS_CIERRE_ETIQUETAS = {
    "SIN_STOCK": "No hay planta suficiente en campo",
    "PLANTA_DANADA": "Planta dañada o en mal estado",
    "CALIBRE_NO_COMERCIAL": "Calibre/tamaño no comercial",
    "NO_ENCONTRADA": "No se ha encontrado la referencia",
    "CLIMATOLOGIA": "Daños por climatología",
    "OTRO": "Otro motivo",
}


def _ensure_cierres_table() -> None:
    client.query(
        f"""
        CREATE TABLE IF NOT EXISTS `{PROJECT}.{PICKING_DATASET}.{CIERRES_TABLE}` (
            id STRING NOT NULL,
            pedido_id STRING NOT NULL,
            linea_huella STRING NOT NULL,
            cantidad_faltante INT64,
            motivo STRING,
            motivo_texto STRING,
            operario_email STRING,
            operario_nombre STRING,
            creado_en TIMESTAMP
        )
        """
    ).result()


class CierreLineaBody(BaseModel):
    pedido_id: str
    linea_huella: str
    cantidad_faltante: int = 0
    motivo: str = Field(min_length=3, max_length=48)
    motivo_texto: str = Field(default="", max_length=500)
    operario_email: str = ""
    operario_nombre: str = ""


@app.post("/api/logistica/cierre-linea")
def cerrar_linea(
    req: CierreLineaBody,
    request: Request,
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    """El operario cierra una línea sin completarla; la oficina recibe el motivo."""
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    if req.motivo not in MOTIVOS_CIERRE_ETIQUETAS:
        raise HTTPException(status_code=422, detail="Motivo no válido")
    _ensure_cierres_table()
    client.query(
        f"INSERT INTO `{PROJECT}.{PICKING_DATASET}.{CIERRES_TABLE}` "
        f"(id, pedido_id, linea_huella, cantidad_faltante, motivo, motivo_texto, operario_email, operario_nombre, creado_en) "
        f"VALUES ({_esc(uuid.uuid4())}, {_esc(req.pedido_id)}, {_esc(req.linea_huella)}, {int(req.cantidad_faltante)}, "
        f"{_esc(req.motivo)}, {_esc(req.motivo_texto)}, {_esc(req.operario_email)}, {_esc(req.operario_nombre)}, CURRENT_TIMESTAMP())"
    ).result()

    etiqueta = MOTIVOS_CIERRE_ETIQUETAS[req.motivo]
    detalle = req.motivo_texto.strip()
    pos = _posicion_linea(req.pedido_id, req.linea_huella)
    ref = f"Pedido {req.pedido_id}" + (f" · Línea {pos}" if pos else "")
    cuerpo = (
        f"✖️ Línea cerrada por {req.operario_nombre or 'un operario'} ({ref}): "
        f"faltan {req.cantidad_faltante} uds — {etiqueta}"
        + (f": {detalle}" if detalle else "")
    )
    try:
        ofis = _query(
            f"SELECT email FROM `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}` WHERE rol = 'SUPERUSUARIO'"
        )
        for ofi in ofis:
            _enviar_fcm(
                ofi["email"],
                "Línea cerrada en campo",
                cuerpo[:300],
                {"tipo": "cierre_linea", "pedido": req.pedido_id, "linea": req.linea_huella},
            )
    except Exception:
        pass
    bot_token = os.getenv("TELEGRAM_MESSAGES_BOT_TOKEN", "") or os.getenv("TELEGRAM_BOT_TOKEN", "")
    chat_id = _oficina_chat_id(bot_token) if bot_token else None
    if bot_token and chat_id:
        try:
            contexto = _contexto_pedido(req.pedido_id)
            texto = f"{cuerpo}\n———\n{contexto}" if contexto else cuerpo
            _telegram_request(
                bot_token,
                "sendMessage",
                {"chat_id": chat_id, "text": texto},
            )
        except Exception:
            pass
    return {"ok": True}


class DiscrepanciaBody(BaseModel):
    pedido_id: str
    linea_huella: str
    declarado: int = 0
    puntado: int = 0
    mensaje: str = Field(default="", max_length=500)
    operario_email: str = ""


@app.post("/api/logistica/discrepancia")
def notificar_discrepancia(
    req: DiscrepanciaBody,
    request: Request,
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    """El encargado punta menos unidades de las que declaró el operario: se le pide justificación."""
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    if not req.operario_email:
        raise HTTPException(status_code=400, detail="operario_email obligatorio")
    pos = _posicion_linea(req.pedido_id, req.linea_huella)
    cuerpo = (
        f"Falta planta en {req.pedido_id}" +
        (f" línea {pos}" if pos else "") +
        f": declaraste {req.declarado} uds y se han puntuado {req.puntado} uds."
    )
    if req.mensaje.strip():
        cuerpo += f" {req.mensaje.strip()}"
    _enviar_fcm(
        req.operario_email,
        "Falta planta: indica el motivo",
        cuerpo[:300],
        {
            "tipo": "discrepancia",
            "pedido": req.pedido_id,
            "linea": req.linea_huella,
            "declarado": str(req.declarado),
            "puntado": str(req.puntado),
        },
    )
    return {"ok": True}


@app.get("/api/perfil-operario")
def perfil_operario(
    request: Request,
    email: str = Query(..., description="Email del operario"),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    """Perfil ligero para la app de logística (maquinaria y fincas)."""
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", GET_LIMIT)
    try:
        _ensure_operarios_table()
        rows = client.query(
            f"""
            SELECT email, nombre, maquinaria, fincas_carga
            FROM `{PROJECT}.{PICKING_DATASET}.{OPERARIOS_TABLE}`
            WHERE LOWER(email) = LOWER({_esc(email)}) LIMIT 1
            """
        ).result()
    except Exception:
        rows = []
    r = next(iter(rows), None)
    return {
        "email": (r.get("email") or email) if r else email,
        "nombre": (r.get("nombre") or "") if r else "",
        "maquinaria": (r.get("maquinaria") or "") if r else "",
        "fincas_carga": (r.get("fincas_carga") or "") if r else "",
    }


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


class EncargadoGestionBody(BaseModel):
    nombre: str = Field(min_length=1, max_length=128)
    apellidos: str = Field(default="", max_length=128)
    email: str = Field(min_length=3, max_length=128)
    password: str = Field(default="", max_length=128)
    fincas_carga: str = Field(default="", max_length=256)
    rol: str = Field(default="ENCARGADO", max_length=32)
    activo: bool = True


@app.post("/api/encargados/gestion")
def gestionar_encargado(
    request: Request,
    body: EncargadoGestionBody,
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_manager_key(k, x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    _ensure_encargados_table()
    usuario = body.email.split("@")[0].lower()
    enc_id = f"ID-{uuid.uuid4().hex[:10].upper()}"
    pwd_hash = _hash_password(usuario, body.password) if body.password else ""
    existing = _query(
        f"SELECT id FROM `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}` WHERE LOWER(email) = {_esc(body.email.lower())} OR LOWER(usuario) = {_esc(usuario)}"
    )
    if existing:
        enc_id = existing[0]["id"]
        if not body.password:
            pwd_hash = ""
    client.query(
        f"""
        MERGE `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}` T
        USING (SELECT {_esc(enc_id)} AS id, {_esc(body.nombre)} AS nombre, {_esc(body.apellidos)} AS apellidos,
                      {_esc(usuario)} AS usuario,
                      {_esc(pwd_hash)} AS password_hash, {_esc(body.rol)} AS rol,
                      {_esc(body.fincas_carga)} AS fincas_carga, 'PICKING' AS modo,
                      {_esc(body.email)} AS email, {str(body.activo).upper()} AS activo) S
        ON T.id = S.id
        WHEN MATCHED THEN UPDATE SET
            nombre = S.nombre,
            apellidos = S.apellidos,
            email = S.email,
            password_hash = IF(S.password_hash = '', T.password_hash, S.password_hash),
            fincas_carga = S.fincas_carga,
            activo = S.activo
        WHEN NOT MATCHED THEN INSERT (id, nombre, apellidos, usuario, password_hash, rol, fincas_carga, modo, email, activo)
            VALUES (S.id, S.nombre, S.apellidos, S.usuario, S.password_hash, S.rol, S.fincas_carga, S.modo, S.email, S.activo)
        """
    ).result()
    return {"ok": True}


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
        INNER JOIN `{PROJECT}.{DATASET}.LINEA_PEDIDO` l
            ON l.SERIE_PEDIDO = p.SERIE_PEDIDO AND l.NUMERO_PEDIDO = p.NUMERO_PEDIDO
            AND COALESCE(l.IMPRIMIR_LINEA, 0) = 0
            AND COALESCE(l.LINEA_ACTIVA, TRUE) = TRUE
            AND COALESCE(l.UNIDADES_PENDIENTES, 0) > 0
        LEFT JOIN `{PROJECT}.{DATASET}.LITRAJES` lt ON lt.ID_LITRAJE = l.CODIGO_LITRAJE
        LEFT JOIN `{PROJECT}.{DATASET}.SECTORES` st ON st.ID_SECTOR = l.CODIGO_SECTOR
        LEFT JOIN (
            SELECT order_id, order_line_id, SUM(cantidad_partida) AS ACOPIADO
            FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_VIEW}`
            GROUP BY order_id, order_line_id
        ) pr ON pr.order_id = p.NUMERO_PEDIDO AND pr.order_line_id = l.HUELLA_DIGITAL
        LEFT JOIN (
            SELECT order_id, MAX(picking_numero) AS ULTIMO_PARTE
            FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_VIEW}`
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
        if desde is not None:
            where.append(
                "(p.FECHA_MODIFICACION >= @modificadoDesde OR DATE(p.FECHA_CARGA) >= @desde)"
            )
        else:
            where.append(
                "(p.FECHA_MODIFICACION >= @modificadoDesde OR DATE(p.FECHA_CARGA) = @fecha)"
            )
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
        serie = r.get("SERIE_PEDIDO") or ""
        numero = r.get("NUMERO_PEDIDO") or ""
        key = f"{serie}_{numero}"
        p = pedidos.get(key)
        if p is None:
            p = pedidos[key] = {
                "serie": serie,
                "numero": numero,
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
                    "operarioEmail": r.get("_OPERARIO_EMAIL") or "",
                    "operarioNombre": r.get("_OPERARIO_NOMBRE") or "",
                    "ubicacion": r.get("UBICACION_EXTRA") or "",
                    "prioridad": r.get("PRIORIDAD") or "",
                    "accion": r.get("ACCION_LOGISTICA") or "",
                    "observaciones": r.get("NOTA_LINEA_PEDIDO") or "",
                }
            )
    # D-15X: reparto de faena (D-72) adjuntado por lotes para no romper la
    # consulta principal si la tabla aún no existe.
    try:
        _ensure_reparto_table()
        claves = list(pedidos.keys())
        for i in range(0, len(claves), 200):
            lote = [k.split("_")[-1] for k in claves[i:i + 200]]
            asignaciones = [
                dict(r)
                for r in client.query(
                    f"""
                    SELECT pedido_id, linea_huella, operario_email, operario_nombre
                    FROM `{PROJECT}.{PICKING_DATASET}.{REPARTO_TABLE}`
                    WHERE pedido_id IN UNNEST(@pedidos)
                    """,
                    job_config=bigquery.QueryJobConfig(
                        query_parameters=[
                            bigquery.ArrayQueryParameter("pedidos", "STRING", lote)
                        ]
                    ),
                ).result()
            ]
            por_numero = {k.split("_")[-1]: pedidos[k] for k in claves}
            for a in asignaciones:
                p = por_numero.get(str(a.get("pedido_id") or ""))
                if p is None:
                    continue
                for linea in p["lineas"]:
                    if linea.get("huella") == a.get("linea_huella"):
                        linea["operarioEmail"] = (a.get("operario_email") or "").strip()
                        linea["operarioNombre"] = (a.get("operario_nombre") or "").strip()
    except Exception:
        pass
    return {
        "desde": desde.isoformat() if desde else None,
        "fecha": fecha.isoformat() if fecha else None,
        "pedidos": list(pedidos.values()),
    }


TRUFFAUT_WEB_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "web", "truffaut")
TRUFFAUT_WEB_TOKEN = "truffaut-otono-2026"
TRUFFAUT_STORES = {
    "001CHE": "Chennevières-sur-Marne",
    "004NAN": "Truffaut - Nantes",
    "008BAI": "Truffaut - Baillet",
    "009VIL": "Truffaut - Villeparisis",
    "1026NICE PETRUCCIOLI": "Nicot Jardinage-Truffaut (Lorient)",
    "011PLA": "Truffaut - Plaisir",
    "012HER": "Truffaut - Herblay",
    "013SER": "Truffaut - Servon",
    "014LVB": "Truffaut - La Ville du Bois",
    "019AMI": "Truffaut - Amiens",
    "020TOB": "Truffaut - Balma (Toulouse)",
    "024ORL": "Truffaut - Orléans",
    "031PAU": "Truffaut - Pau-Lons",
    "033CHM": "Truffaut - Châtenay-Malabry",
    "035PGS": "Truffaut - Paris Grand Stade",
    "036NIM": "Truffaut - Nîmes",
    "040IVR": "Truffaut - Ivry",
    "045CAB": "Truffaut - Cabriès",
    "047MON": "Truffaut - Montpellier",
    "050AUB": "Truffaut - Aubagne",
    "051MER": "Truffaut - Mérignac",
    "052ROS": "Truffaut - Rosny",
    "053GRI": "Truffaut - Grigny",
    "073FQX": "Truffaut - Fourqueux",
    "075TPM": "Truffaut - Tours Madelaine",
    "076BRS-BOULOGNE": "Truffaut - Boulogne (Mitry-Mory)",
    "085MTL": "Truffaut - Montélimar",
    "086ADP": "Truffaut - Althen-des-Paluds",
    "1026NIC": "Nicot Jardinage-Truffaut (Lorient)",
}
TRUFFAUT_SIN_30 = {"260857"}
TRUFFAUT_BASE = {
    "11008-BO-25": 16.0,
    "11008-BO-26": 16.0,
    "90048": 16.0,
    "11311-MA-26": 11.0,
    "90017": 26.5,
    "80040-24-25": 26.5,
    "80043-24-26": 26.5,
    "80044-24-27": 26.5,
    "94713": 37.0,
    "94772": 54.8,
    "11192-BO-25": 42.0,
}
TRUFFAUT_ROUTES_A = [
    {
        "num": 1,
        "title": "Camión 1 · Mediterráneo (Montpellier, Montélimar & Provenza)",
        "corridor": "Hérault / Valle del Ródano / Provenza",
        "highway": "AP-7 / A9 / A7 / A54 / A50",
        "totalPal": 35,
        "stops": [
            {"pedido": "260857", "store": "Truffaut - Montpellier", "addr": "77 Rue Hélène Boucher - ZAC Fréjorgues Ouest", "cp": "34130", "city": "Mauguio (Montpellier)", "pal": 2, "legKm": 861, "cumKm": 861, "lat": 43.583, "lng": 4.003},
            {"pedido": "260866", "store": "Truffaut - Montélimar", "addr": "Rue Louis Charpenne", "cp": "26200", "city": "Montélimar", "pal": 7, "legKm": 152, "cumKm": 1013, "lat": 44.5582, "lng": 4.7509},
            {"pedido": "260867", "store": "Truffaut - Althen-des-Paluds", "addr": "Route de la Roque", "cp": "84210", "city": "Althen-des-Paluds (Avignon)", "pal": 8, "legKm": 83, "cumKm": 1096, "lat": 44.0049, "lng": 4.9585},
            {"pedido": "260856", "store": "Truffaut - Cabriès", "addr": "ZAC Grande Campagne - Plan de Campagne", "cp": "13480", "city": "Cabriès (Marsella Norte)", "pal": 8, "legKm": 100, "cumKm": 1196, "lat": 43.4414, "lng": 5.3796},
            {"pedido": "260859", "store": "Truffaut - Aubagne", "addr": "CD2 Route de Gémenos", "cp": "13400", "city": "Aubagne (Marsella Este)", "pal": 10, "legKm": 36, "cumKm": 1232, "lat": 43.2927, "lng": 5.5683}
        ],
        "totalKm": 1232,
    },
    {
        "num": 2,
        "title": "Camión 2 · Nîmes & Montpellier",
        "corridor": "Languedoc",
        "highway": "AP-7 / A9",
        "totalPal": 34,
        "stops": [
            {"pedido": "260854", "store": "Truffaut - Nîmes", "addr": "ZAC Mas des Abeilles, Rue Michel Debré", "cp": "30000", "city": "Nîmes", "pal": 24, "legKm": 900, "cumKm": 900, "lat": 43.8374, "lng": 4.3601},
            {"pedido": "260857", "store": "Truffaut - Montpellier", "addr": "77 Rue Hélène Boucher - ZAC Fréjorgues Ouest", "cp": "34130", "city": "Mauguio (Montpellier)", "pal": 10, "legKm": 50, "cumKm": 950, "lat": 43.583, "lng": 4.003}
        ],
        "totalKm": 950,
    },
    {
        "num": 3,
        "title": "Camión 3 · ESPECIAL URBANO París (Hayon / Plataforma)",
        "corridor": "Île-de-France (tiendas urbanas sin muelle)",
        "highway": "A10 / Périphérique / A86 / A3 / N3",
        "totalPal": 33,
        "special": "Camión imprescindible con plataforma elevadora (Hayon)",
        "stops": [
            {"pedido": "260845", "store": "Truffaut - Plaisir", "addr": "RN12 Z.A. Sainte-Apolline", "cp": "78380", "city": "Plaisir", "pal": 5, "legKm": 1528, "cumKm": 1528, "lat": 48.8114, "lng": 1.9465},
            {"pedido": "260848", "store": "Truffaut - La Ville du Bois", "addr": "RN20", "cp": "91620", "city": "La Ville du Bois", "pal": 7, "legKm": 41, "cumKm": 1569, "lat": 48.6608, "lng": 2.2701},
            {"pedido": "260855", "store": "Truffaut - Ivry", "addr": "5 Rue François Mitterrand", "cp": "94200", "city": "Ivry-sur-Seine", "pal": 4, "legKm": 23, "cumKm": 1592, "lat": 48.8137, "lng": 2.385},
            {"pedido": "260861", "store": "Truffaut - Rosny", "addr": "CC Domus - 16, Rue de Lisbonne", "cp": "93110", "city": "Rosny-sous-Bois", "pal": 4, "legKm": 14, "cumKm": 1606, "lat": 48.8727, "lng": 2.485},
            {"pedido": "260853", "store": "Truffaut - Paris Grand Stade", "addr": "2 Rue Jesse Owens", "cp": "93200", "city": "Saint-Denis (Paris)", "pal": 4, "legKm": 14, "cumKm": 1620, "lat": 48.9245, "lng": 2.3601},
            {"pedido": "260843", "store": "Truffaut - Villeparisis", "addr": "RN 3 Route de Villevaude", "cp": "77270", "city": "Villeparisis", "pal": 5, "legKm": 23, "cumKm": 1643, "lat": 48.9428, "lng": 2.6133},
            {"pedido": "260865", "store": "Truffaut - Boulogne (Mitry-Mory)", "addr": "Transit via Breewel 10 Rue Mercier", "cp": "77290", "city": "Mitry-Mory (Boulogne)", "pal": 4, "legKm": 6, "cumKm": 1649, "lat": 48.985, "lng": 2.6164}
        ],
        "totalKm": 1649,
    },
    {
        "num": 4,
        "title": "Camión 4 · Corona París & Picardía",
        "corridor": "Loire / Île-de-France Oeste, Sur & Norte / Picardía",
        "highway": "A10 / N104 / A13 / A115 / A16",
        "totalPal": 33,
        "stops": [
            {"pedido": "260864", "store": "Truffaut - Tours Madelaine", "addr": "CC Ma Petite Madelaine - 213-215 Av du Grand Sud", "cp": "37170", "city": "Chambray-lès-Tours (Tours)", "pal": 4, "legKm": 1262, "cumKm": 1262, "lat": 47.3375, "lng": 0.7025},
            {"pedido": "260882", "store": "Truffaut - Orléans", "addr": "Route de Saint Cyr en Val", "cp": "45650", "city": "Saint-Jean-le-Blanc (Orléans)", "pal": 4, "legKm": 122, "cumKm": 1384, "lat": 47.8923, "lng": 1.914},
            {"pedido": "260863", "store": "Truffaut - Fourqueux", "addr": "ZA du Pince-Loup", "cp": "78112", "city": "Saint-Germain-en-Laye (Fourqueux)", "pal": 4, "legKm": 134, "cumKm": 1518, "lat": 48.8863, "lng": 2.0649},
            {"pedido": "260852", "store": "Truffaut - Châtenay-Malabry", "addr": "72 Avenue Roger Salengro", "cp": "92290", "city": "Châtenay-Malabry", "pal": 4, "legKm": 34, "cumKm": 1552, "lat": 48.7651, "lng": 2.2783},
            {"pedido": "260862", "store": "Truffaut - Grigny", "addr": "RN 7 ZI La Plaine Basse - Rue Ferdinand de Lesseps", "cp": "91350", "city": "Grigny", "pal": 3, "legKm": 20, "cumKm": 1572, "lat": 48.6539, "lng": 2.3852},
            {"pedido": "260847", "store": "Truffaut - Servon", "addr": "3, Rue Georges - RN 19", "cp": "77170", "city": "Servon", "pal": 3, "legKm": 33, "cumKm": 1605, "lat": 48.7178, "lng": 2.5875},
            {"pedido": "260846", "store": "Truffaut - Herblay", "addr": "La Patte d'Oie 270 Bd du Havre", "cp": "95220", "city": "Pierrelaye (Herblay)", "pal": 4, "legKm": 62, "cumKm": 1667, "lat": 49.012, "lng": 2.154},
            {"pedido": "260842", "store": "Truffaut - Baillet", "addr": "Rue de Paris", "cp": "95560", "city": "Baillet-en-France", "pal": 4, "legKm": 23, "cumKm": 1690, "lat": 49.0723, "lng": 2.2996},
            {"pedido": "260849", "store": "Truffaut - Amiens", "addr": "3, Passage du Rayon Vert", "cp": "80330", "city": "Longueau (Amiens)", "pal": 3, "legKm": 116, "cumKm": 1806, "lat": 49.8615, "lng": 2.4265}
        ],
        "totalKm": 1806,
    },
    {
        "num": 5,
        "title": "Camión 5 · Suroeste & Bretaña",
        "corridor": "Aquitania / Pirineos / Atlántico / Bretaña",
        "highway": "A-23 / AP-8 / A64 / A62 / A10 / N165",
        "totalPal": 33,
        "stops": [
            {"pedido": "260851", "store": "Truffaut - Pau-Lons", "addr": "ZAC du Mail 1-7, Rue Robert Schuman", "cp": "64140", "city": "Lons (Pau)", "pal": 4, "legKm": 691, "cumKm": 691, "lat": 43.3206, "lng": -0.4109},
            {"pedido": "260850", "store": "Truffaut - Balma (Toulouse)", "addr": "Route de Lavaur", "cp": "31130", "city": "Balma (Toulouse)", "pal": 9, "legKm": 207, "cumKm": 898, "lat": 43.6108, "lng": 1.4991},
            {"pedido": "260860", "store": "Truffaut - Mérignac", "addr": "7, Rue Hipparque - Domaine de Pelus", "cp": "33700", "city": "Mérignac (Burdeos)", "pal": 7, "legKm": 252, "cumKm": 1150, "lat": 44.835, "lng": -0.6331},
            {"pedido": "260841", "store": "Truffaut - Nantes", "addr": "258 Route de Vannes", "cp": "44700", "city": "Orvault (Nantes)", "pal": 7, "legKm": 365, "cumKm": 1515, "lat": 47.2709, "lng": -1.6239},
            {"pedido": "260844", "store": "Nicot Jardinage-Truffaut (Lorient)", "addr": "ZAC Kerulve-Rue du Verger", "cp": "56100", "city": "Lorient", "pal": 6, "legKm": 162, "cumKm": 1677, "lat": 47.7483, "lng": -3.3701}
        ],
        "totalKm": 1677,
    },
]

TRUFFAUT_ROUTES_B = TRUFFAUT_ROUTES_A

_ROUTE_GEO_CACHE: dict[Any, dict[str, Any]] = {}
_ROUTE_GEO_MIN_KM = 1.5


def _haversine_km(a: tuple[float, float], b: tuple[float, float]) -> float:
    import math

    lat1, lon1 = a
    lat2, lon2 = b
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 6371.0 * 2 * math.asin(math.sqrt(h))


def _road_route(origin: dict, stops: list[dict]) -> Optional[dict[str, Any]]:
    key = (origin["lat"], origin["lng"], tuple((s["lat"], s["lng"]) for s in stops))
    cached = _ROUTE_GEO_CACHE.get(key)
    if cached:
        return cached
    waypoints = ";".join(
        [f"{origin['lng']},{origin['lat']}"]
        + [f"{s['lng']},{s['lat']}" for s in stops]
    )
    url = (
        "https://router.project-osrm.org/route/v1/driving/"
        + waypoints
        + "?overview=full&geometries=geojson&steps=false&annotations=false"
    )
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "pickingve-route/1.0"})
        with urllib.request.urlopen(req, timeout=90) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        if data.get("code") != "Ok":
            return None
        route = data["routes"][0]
        legs_km = [round(leg["distance"] / 1000) for leg in route["legs"]]
        if not legs_km:
            return None
        geo: list[list[float]] = []
        prev: Optional[tuple[float, float]] = None
        for lon, lat in route["geometry"]["coordinates"]:
            p = (float(lat), float(lon))
            if prev is None or _haversine_km(prev, p) >= _ROUTE_GEO_MIN_KM:
                geo.append([p[0], p[1]])
                prev = p
        last = route["geometry"]["coordinates"][-1]
        geo[-1] = [float(last[1]), float(last[0])]
        out = {"geo": geo, "legs_km": legs_km}
        _ROUTE_GEO_CACHE[key] = out
        return out
    except Exception:
        return None


def _apply_road_route(route: dict, origin: dict) -> None:
    road = _road_route(origin, route["stops"])
    if road is None:
        return
    route["geo"] = road["geo"]
    cum = 0
    for i, stop in enumerate(route["stops"]):
        cum += road["legs_km"][i]
        stop["legKm"] = road["legs_km"][i]
        stop["cumKm"] = cum
    route["totalKm"] = cum


@app.get("/api/truffaut/reporte")
def get_truffaut_reporte(
    request: Request,
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    if not API_KEY or (x_api_key != API_KEY and k != TRUFFAUT_WEB_TOKEN):
        raise HTTPException(status_code=401, detail="API key inválida o ausente")
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
          AND CAST(p.ESTADO_PEDIDO AS INT64) IN (0, 3)
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
            SELECT lp.NUMERO_PEDIDO AS n, lp.POSICION_PEDIDO AS p, lp.REFERENCIA_ARTICULO AS r,
                   lp.DESCRIPCION_ARTICULO AS d, CAST(lp.UNIDADES AS INT64) AS u,
                   lp.CODIGO_LITRAJE AS lc, lt.DESCRIPCION_LITRAJE AS l, lp.PRECIO AS pr,
                   lp.UBICACION_EXTRA AS ubi, lp.FINCA_RELEVADA AS f, lp.SECTOR_RELEVADO AS s,
                   lp.LINEA_ACTIVA AS act
            FROM `{PROJECT}.{DATASET}.LINEA_PEDIDO` lp
            LEFT JOIN `{PROJECT}.{DATASET}.LITRAJES` lt ON lt.ID_LITRAJE = lp.CODIGO_LITRAJE
            WHERE lp.NUMERO_PEDIDO IN ({inlist})
              AND lp.LINEA_ACTIVA = TRUE
            ORDER BY lp.NUMERO_PEDIDO, lp.POSICION_PEDIDO
        """)
        by_n = {o["n"]: o for o in orders}
        for r in lines:
            o = by_n.get(r["n"])
            if not o:
                continue
            if r["r"] == "99998" and r["lc"] == "EUR" and r["act"]:
                o["pal"] += int(r["u"] or 0)
            o["lin"].append({
                "p": int(r["p"] or 0),
                "r": r["r"] or "",
                "d": r["d"] or "",
                "u": int(r["u"] or 0),
                "l": r["l"] or "",
                "pr": float(r["pr"] or 0),
                "b": TRUFFAUT_BASE.get(r["r"]),
                "ubi": r["ubi"] or "",
                "f": r["f"] or "",
                "s": r["s"] or "",
            })
            b = TRUFFAUT_BASE.get(r["r"])
            o["lin"][-1]["sub"] = round((float(r["pr"] or 0) / b - 1) * 100, 1) if b else None
        for o in orders:
            o["lin"].sort(key=lambda x: x["p"])
            if o["no30"]:
                o["planta"] = o["tot"]
                o["transp"] = 0.0
            else:
                planta = 0.0
                for L in o["lin"]:
                    if L["b"]:
                        planta += L["u"] * L["b"]
                    else:
                        planta += L["u"] * L["pr"] / 1.30
                o["planta"] = round(planta, 2)
                o["transp"] = round(o["tot"] - planta, 2)
            o["transp_pct"] = round(o["transp"] / o["tot"] * 100, 1) if o["tot"] else 0.0

    order_pal_map = {o["n"]: o["pal"] for o in orders}
    def dynamic_routes(routes_list):
        import copy
        res = copy.deepcopy(routes_list)
        by_pedido = {}
        for r in res:
            for stop in r["stops"]:
                by_pedido.setdefault(stop["pedido"], []).append(stop)
        for pedido, stops in by_pedido.items():
            live = order_pal_map.get(pedido)
            if live is None:
                continue
            if len(stops) == 1:
                stops[0]["pal"] = live
            else:
                static_sum = sum(s["pal"] for s in stops)
                assigned = 0
                for i, s in enumerate(stops):
                    if i == len(stops) - 1:
                        s["pal"] = live - assigned
                    else:
                        s["pal"] = round(live * s["pal"] / static_sum)
                        assigned += s["pal"]
        for r in res:
            r["totalPal"] = sum(s["pal"] for s in r["stops"])
        return res

    payload = {
        "generated": date.today().isoformat(),
        "origin": {
            "name": "Viveros Elche - La Fábrica",
            "addr": "CV-845, km 3.5, 03680 Aspe, Alicante, España",
            "lat": 38.3453,
            "lng": -0.7681
        },
        "orders": orders,
        "routes_a": dynamic_routes(TRUFFAUT_ROUTES_A),
        "routes_b": dynamic_routes(TRUFFAUT_ROUTES_B)
    }
    for route in payload["routes_a"]:
        _apply_road_route(route, payload["origin"])
    for route in payload["routes_b"]:
        _apply_road_route(route, payload["origin"])
    _cache_set("truffaut_reporte", payload)
    return payload


@app.get("/truffaut")
def truffaut_web(k: Optional[str] = Query(default=None)):
    if k != TRUFFAUT_WEB_TOKEN:
        raise HTTPException(404, "Not found")
    return FileResponse(os.path.join(TRUFFAUT_WEB_DIR, "index.html"))


@app.get("/truffaut/rutas")
def truffaut_web_rutas(k: Optional[str] = Query(default=None)):
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
    sectores = _query(f"SELECT ID_SECTOR, DESCRIPCION_SECTOR FROM `{PROJECT}.{DATASET}.SECTORES`")
    return {
        "articulos": articulos,
        "eans": eans,
        "litrajes": litrajes,
        "sectores": sectores,
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
    if nuevos:
        errors = client.insert_rows_json(
            f"{PROJECT}.{PICKING_DATASET}.{PICKING_TABLE}",
            [r.model_dump() for r in nuevos],
        )
        if errors:
            raise HTTPException(status_code=500, detail=str(errors[:5]))
    # Los duplicados también se confirman: ya están (o estarán) en la tabla y
    # el cliente debe marcarlos como sincronizados para no reenviarlos siempre.
    return {
        "ok": len(nuevos),
        "duplicados": len(pending_ids) - len(nuevos),
        "accepted_ids": pending_ids,
    }


class CompensaRegistro(BaseModel):
    record_id: str = Field(min_length=8, max_length=64)
    pedido_id: str = Field(min_length=1, max_length=32)
    cantidad: float


class CompensaBody(BaseModel):
    registros: list[CompensaRegistro] = Field(default_factory=list)


@app.post("/api/picking/compensar")
def compensar(
    request: Request,
    body: CompensaBody,
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    """Registra borrados lógicos de registros ya subidos (desacopio posterior
    a la subida). Idempotente por record_id; las lecturas usan la vista
    `picking_registros_v`, que excluye estos record_id."""
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", POST_LIMIT)
    if not body.registros:
        return {"ok": 0}
    if len(body.registros) > MAX_REGISTROS:
        raise HTTPException(status_code=400, detail="Demasiados registros")
    values = ", ".join(
        f"({_esc(r.record_id)}, {_esc(r.pedido_id)}, {float(r.cantidad)})"
        for r in body.registros
    )
    client.query(
        f"""
        MERGE `{PROJECT}.{PICKING_DATASET}.{COMPENSACIONES_TABLE}` T
        USING (SELECT * FROM UNNEST([STRUCT<record_id STRING, pedido_id STRING, cantidad FLOAT64>{values}])) S
        ON T.record_id = S.record_id
        WHEN NOT MATCHED THEN
          INSERT (record_id, pedido_id, cantidad, creado_en)
          VALUES (S.record_id, S.pedido_id, S.cantidad, CURRENT_TIMESTAMP())
        """
    ).result()
    return {"ok": len(body.registros)}


MANAGER_WEB_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "web", "manager")
MANAGER_WEB_TOKEN = "manager-panel-2026"


@app.get("/manager")
def manager_web(k: Optional[str] = Query(default=None)):
    if k != MANAGER_WEB_TOKEN:
        raise HTTPException(404, "Not found")
    return FileResponse(os.path.join(MANAGER_WEB_DIR, "index.html"))


def _verify_manager_key(
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> None:
    if k != MANAGER_WEB_TOKEN and x_api_key != API_KEY:
        raise HTTPException(status_code=401, detail="API key inválida o ausente")


@app.get("/api/manager/orders")
def manager_orders(
    fecha: Optional[str] = Query(None, description="Fecha de carga (YYYY-MM-DD)"),
    estado: Optional[str] = Query(None, description="Filtro de estado: activos, pendientes, cargados, enviados, todos"),
    incluirEnviados: bool = Query(False, description="Incluir pedidos ya enviados/cargados"),
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
):
    _verify_manager_key(k, x_api_key)
    target_date = date.today()
    if fecha and fecha not in ("null", "undefined", ""):
        try:
            target_date = date.fromisoformat(fecha)
        except ValueError:
            pass
    
    st_filter = (estado or "").strip().lower()
    if not st_filter:
        # D-70: por defecto se muestran TODOS los estados del día: un pedido
        # acopiado al completo o cargado no desaparece del listado.
        st_filter = "todos"

    filtro_sql = ""
    if st_filter in ("pendientes", "pendiente"):
        filtro_sql = " AND pf.order_id IS NULL AND m.pedido_id IS NULL"
    elif st_filter in ("cargados", "cargado"):
        filtro_sql = " AND pf.order_id IS NULL AND m.pedido_id IS NOT NULL"
    elif st_filter in ("enviados", "enviado"):
        filtro_sql = " AND pf.order_id IS NOT NULL"
    elif st_filter == "activos":
        filtro_sql = " AND pf.order_id IS NULL"
    elif st_filter == "acopiados":
        filtro_sql = " AND tot.TOTAL_ACOPIADO > 0"

    sql = f"""
        SELECT p.SERIE_PEDIDO, p.NUMERO_PEDIDO, p.NUMERO_CLIENTE, p.ESTADO_PEDIDO,
               p.FECHA_CARGA, p.SECTOR_CARGA, p.FINCA_CARGA, p.NOTAS_PEDIDO,
               p.MARCA_PEDIDO, p.REFERENCIA_PEDIDO, p.CODIGO_AGENTE,
               COALESCE(c.N_COMERCIAL, '') AS CLIENTE,
               COALESCE(c.N_FISCAL, '') AS CLIENTE_FISCAL,
               COALESCE(c.DIRECCION, '') AS DIRECCION_DESCARGA,
                COALESCE(ag.NOMBRE_AGENTE, '') AS AGENTE,
                l.HUELLA_DIGITAL, l.POSICION_PEDIDO, l.REFERENCIA_ARTICULO,
                l.DESCRIPCION_ARTICULO, l.UNIDADES_PENDIENTES,
                l.CODIGO_LITRAJE, l.CODIGO_SECTOR, l.UBICACION_EXTRA,
                l.FINCA_RELEVADA, l.SECTOR_RELEVADO, l.MARCADO, l.MARCA,
                l.PRIORIDAD, l.ACCION_LOGISTICA, l.NOTA_LINEA_PEDIDO,
                COALESCE(lt.DESCRIPCION_LITRAJE, l.CODIGO_LITRAJE, '') AS LITRAJE_DESC,
                COALESCE(st.DESCRIPCION_SECTOR, l.CODIGO_SECTOR, '') AS SECTOR_DESC,
                COALESCE(pr.ACOPIADO, 0) AS ACOPIADO,
                COALESCE(pr.OPERARIOS, '') AS OPERARIOS,
                COALESCE(pr.DETALLE_OPS, '') AS DETALLE_OPS,
                CASE WHEN m.pedido_id IS NOT NULL THEN TRUE ELSE FALSE END AS CARGADO,
                CASE WHEN pf.order_id IS NOT NULL THEN TRUE ELSE FALSE END AS TIENE_PARTE_FINAL,
                COALESCE(tot.TOTAL_ACOPIADO, 0) AS TOTAL_ACOPIADO,
                COALESCE(rf.operario_nombre, '') AS OPERARIO_ASIGNADO
         FROM `{PROJECT}.{DATASET}.PEDIDOS` p
         LEFT JOIN `{PROJECT}.{DATASET}.CLIENTE` c ON c.ID_CLIENTE = p.NUMERO_CLIENTE
         LEFT JOIN `{PROJECT}.{DATASET}.AGENTE` ag ON ag.ID_AGENTE = p.CODIGO_AGENTE
         INNER JOIN `{PROJECT}.{DATASET}.LINEA_PEDIDO` l
             ON l.SERIE_PEDIDO = p.SERIE_PEDIDO AND l.NUMERO_PEDIDO = p.NUMERO_PEDIDO
             AND COALESCE(l.IMPRIMIR_LINEA, 0) = 0
             AND COALESCE(l.LINEA_ACTIVA, TRUE) = TRUE
         LEFT JOIN `{PROJECT}.{DATASET}.LITRAJES` lt ON lt.ID_LITRAJE = l.CODIGO_LITRAJE
         LEFT JOIN `{PROJECT}.{DATASET}.SECTORES` st ON st.ID_SECTOR = l.CODIGO_SECTOR
        LEFT JOIN (
            SELECT order_id, order_line_id,
                   SUM(cantidad) AS ACOPIADO,
                   STRING_AGG(empleado, ', ') AS OPERARIOS,
                   STRING_AGG(detalle, ', ') AS DETALLE_OPS
            FROM (
                SELECT order_id, order_line_id,
                       COALESCE(NULLIF(TRIM(empleado_nombre), ''), 'Desconocido') AS empleado,
                       CONCAT(COALESCE(NULLIF(TRIM(empleado_nombre), ''), 'Desconocido'), ':', CAST(SUM(cantidad_partida) AS INT64)) AS detalle,
                       SUM(cantidad_partida) AS cantidad
                FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_VIEW}`
                GROUP BY order_id, order_line_id, empleado_nombre
            )
            GROUP BY order_id, order_line_id
        ) pr ON pr.order_id = p.NUMERO_PEDIDO AND pr.order_line_id = l.HUELLA_DIGITAL
        LEFT JOIN (
            SELECT order_id, SUM(cantidad_partida) AS TOTAL_ACOPIADO
            FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_VIEW}`
            GROUP BY order_id
        ) tot ON tot.order_id = p.NUMERO_PEDIDO
        LEFT JOIN `{PROJECT}.{PICKING_DATASET}.{MATRICULAS_TABLE}` m
            ON m.pedido_id = p.NUMERO_PEDIDO AND m.tipo = 'CAMION'
        LEFT JOIN (
            SELECT DISTINCT order_id FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_TABLE}` WHERE picking_tipo = 'F'
        ) pf ON pf.order_id = p.NUMERO_PEDIDO
        LEFT JOIN `{PROJECT}.{PICKING_DATASET}.{REPARTO_TABLE}` rf
            ON rf.pedido_id = p.NUMERO_PEDIDO AND rf.linea_huella = l.HUELLA_DIGITAL
        WHERE DATE(p.FECHA_CARGA) = @fecha{filtro_sql}
        ORDER BY p.NUMERO_PEDIDO DESC, l.POSICION_PEDIDO
    """
    params = [bigquery.ScalarQueryParameter("fecha", "DATE", target_date.isoformat())]
    rows = [dict(r) for r in client.query(sql, job_config=bigquery.QueryJobConfig(query_parameters=params)).result()]

    pedidos: dict[str, dict[str, Any]] = {}
    for r in rows:
        key = f"{r.get('SERIE_PEDIDO') or ''}_{r.get('NUMERO_PEDIDO') or ''}"
        p = pedidos.get(key)
        if p is None:
            cargado = bool(r.get("CARGADO"))
            tiene_final = bool(r.get("TIENE_PARTE_FINAL"))
            total_acopiado = int(r.get("TOTAL_ACOPIADO") or 0)
            
            # 4 Estados:
            # 1. sin_acopiar: total_acopiado == 0 y not cargado y not tiene_final
            # 2. en_proceso: total_acopiado > 0 y not cargado y not tiene_final
            # 3. camion_asignado: cargado (camión registrado) y not tiene_final
            # 4. enviado / cargado final: tiene_final
            if tiene_final:
                estado_calc = "enviado"
            elif cargado:
                estado_calc = "camion_asignado"
            elif total_acopiado > 0:
                estado_calc = "en_proceso"
            else:
                estado_calc = "sin_acopiar"

            p = pedidos[key] = {
                "serie": r.get('SERIE_PEDIDO') or '',
                "numero": r.get('NUMERO_PEDIDO') or '',
                "cliente": r.get("CLIENTE") or "",
                "clienteFiscal": r.get("CLIENTE_FISCAL") or "",
                "referenciaPedido": r.get("REFERENCIA_PEDIDO") or "",
                "direccionDescarga": r.get("DIRECCION_DESCARGA") or "",
                "agente": r.get("AGENTE") or "",
                "marcaPedido": r.get("MARCA_PEDIDO") or "",
                "notasPedido": r.get("NOTAS_PEDIDO") or "",
                "fechaCarga": str(r.get("FECHA_CARGA")) if r.get("FECHA_CARGA") else None,
                "sector": r.get("SECTOR_CARGA") or "",
                "finca": r.get("FINCA_CARGA") or "",
                "cargado": cargado,
                "tieneParteFinal": tiene_final,
                "estado": estado_calc,
                "estadoFactusol": r.get("ESTADO_PEDIDO"),
                "lineas": [],
            }
        if r.get("HUELLA_DIGITAL") is not None:
            ref = str(r.get("REFERENCIA_ARTICULO") or "")
            p["lineas"].append(
                {
                    "huellaDigital": r.get("HUELLA_DIGITAL"),
                    "posicion": r.get("POSICION_PEDIDO"),
                    "referencia": ref,
                    "descripcion": r.get("DESCRIPCION_ARTICULO") or "",
                    "litraje": r.get("LITRAJE_DESC") or r.get("CODIGO_LITRAJE") or "",
                    "sector": r.get("SECTOR_DESC") or r.get("CODIGO_SECTOR") or r.get("SECTOR_RELEVADO") or "",
                    "ubicacionExtra": r.get("UBICACION_EXTRA") or "",
                    "fincaLinea": r.get("FINCA_RELEVADA") or p["finca"],
                    "prioritario": str(r.get("PRIORIDAD") or "").upper() == "PRIORITARIO",
                    "marcado": bool(r.get("MARCADO")),
                    "marca": r.get("MARCA") or "",
                    "observaciones": r.get("NOTA_LINEA_PEDIDO") or r.get("ACCION_LOGISTICA") or "",
                    "pendientes": r.get("UNIDADES_PENDIENTES") or 0,
                    "acopiado": int(r.get("ACOPIADO") or 0),
                    "operarios": r.get("OPERARIOS") or "",
                    "detalleOperarios": r.get("DETALLE_OPS") or "",
                    "operarioAsignado": r.get("OPERARIO_ASIGNADO") or "",
                }
            )
    return {"fecha": target_date.isoformat(), "pedidos": list(pedidos.values())}


@app.get("/api/manager/fechas")
def manager_fechas(
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_manager_key(k, x_api_key)
    rows = _query(f"""
        SELECT DATE(p.FECHA_CARGA) AS FECHA,
               COUNT(DISTINCT p.NUMERO_PEDIDO) AS TOTAL,
               COUNT(DISTINCT IF(pf.order_id IS NULL AND m.pedido_id IS NULL, p.NUMERO_PEDIDO, NULL)) AS PENDIENTES,
               COUNT(DISTINCT IF(pf.order_id IS NULL AND m.pedido_id IS NOT NULL, p.NUMERO_PEDIDO, NULL)) AS CARGADOS,
               COUNT(DISTINCT IF(pf.order_id IS NOT NULL, p.NUMERO_PEDIDO, NULL)) AS ENVIADOS
        FROM `{PROJECT}.{DATASET}.PEDIDOS` p
        LEFT JOIN `{PROJECT}.{PICKING_DATASET}.{MATRICULAS_TABLE}` m
            ON m.pedido_id = p.NUMERO_PEDIDO AND m.tipo = 'CAMION'
        LEFT JOIN (
            SELECT DISTINCT order_id FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_TABLE}` WHERE picking_tipo = 'F'
        ) pf ON pf.order_id = p.NUMERO_PEDIDO
        WHERE p.FECHA_CARGA IS NOT NULL
        GROUP BY FECHA
        HAVING PENDIENTES > 0 OR DATE(FECHA) >= CURRENT_DATE()
        ORDER BY FECHA
    """)
    return {
        "fechas": [
            {
                "fecha": str(r["FECHA"]),
                "pedidos": int(r["TOTAL"] or 0),
                "pendientes": int(r["PENDIENTES"] or 0),
                "cargados": int(r["CARGADOS"] or 0),
                "enviados": int(r["ENVIADOS"] or 0),
            }
            for r in rows
        ]
    }


@app.get("/api/manager/report/{numero_pedido}")
def manager_report(
    numero_pedido: str,
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
):
    _verify_manager_key(k, x_api_key)
    # Get order info, lines, picking records, and check citrus immobilization (AUTORIZACION / CP5ART)
    order_sql = f"""
        SELECT p.SERIE_PEDIDO, p.NUMERO_PEDIDO, p.FINCA_CARGA, p.SECTOR_CARGA, p.FECHA_CARGA,
               COALESCE(c.N_COMERCIAL, '') AS CLIENTE,
               m.matricula AS MATRICULA_CAMION,
               CASE WHEN m.pedido_id IS NOT NULL THEN TRUE ELSE FALSE END AS CARGADO
        FROM `{PROJECT}.{DATASET}.PEDIDOS` p
        LEFT JOIN `{PROJECT}.{DATASET}.CLIENTE` c ON c.ID_CLIENTE = p.NUMERO_CLIENTE
        LEFT JOIN `{PROJECT}.{PICKING_DATASET}.{MATRICULAS_TABLE}` m ON m.pedido_id = p.NUMERO_PEDIDO AND m.tipo = 'CAMION'
        WHERE p.NUMERO_PEDIDO = @pedido
        LIMIT 1
    """
    params = [bigquery.ScalarQueryParameter("pedido", "STRING", numero_pedido)]
    order_res = list(client.query(order_sql, job_config=bigquery.QueryJobConfig(query_parameters=params)).result())
    if not order_res:
        raise HTTPException(status_code=404, detail="Pedido no encontrado")
    o = order_res[0]

    autorizacion_col = ""
    try:
        col_rows = _query(
            f"SELECT column_name FROM `{PROJECT}.{DATASET}.INFORMATION_SCHEMA.COLUMNS` "
            f"WHERE table_name = 'ARTICULOS' AND column_name = 'AUTORIZACION'"
        )
        if col_rows:
            autorizacion_col = ", a.AUTORIZACION"
    except Exception:
        autorizacion_col = ""

    lines_sql = f"""
        SELECT l.HUELLA_DIGITAL, l.POSICION_PEDIDO, l.REFERENCIA_ARTICULO, l.DESCRIPCION_ARTICULO,
               l.UNIDADES AS PEDIDO,
               l.UNIDADES_PENDIENTES{autorizacion_col},
               COALESCE(pr.ACOPIADO, 0) AS ACOPIADO,
               COALESCE(pr.SUSTITUIDO, FALSE) AS SUSTITUIDO
        FROM `{PROJECT}.{DATASET}.LINEA_PEDIDO` l
        LEFT JOIN `{PROJECT}.{DATASET}.ARTICULOS` a ON a.ID_ARTICULO = l.REFERENCIA_ARTICULO
        LEFT JOIN (
            SELECT order_id, order_line_id, SUM(cantidad_partida) AS ACOPIADO,
                   MAX(CAST(sustituido AS INT64)) > 0 AS SUSTITUIDO
            FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_VIEW}`
            WHERE order_id = @pedido
            GROUP BY order_id, order_line_id
        ) pr ON pr.order_line_id = l.HUELLA_DIGITAL
        WHERE l.NUMERO_PEDIDO = @pedido AND COALESCE(l.IMPRIMIR_LINEA, 0) = 0
        ORDER BY l.POSICION_PEDIDO
    """
    line_rows = [dict(r) for r in client.query(lines_sql, job_config=bigquery.QueryJobConfig(query_parameters=params)).result()]

    alertas_citricos = []
    lineas_res = []
    for lr in line_rows:
        ref = str(lr.get("REFERENCIA_ARTICULO") or "")
        desc = str(lr.get("DESCRIPCION_ARTICULO") or "")
        auth = str(lr.get("AUTORIZACION") or "")
        
        # Check if citrus and immobilized (CP5ART contains "NO")
        is_citrus = "CITRIC" in desc.upper()
        if "NO" in auth.upper() and is_citrus:
            alertas_citricos.append({
                "referencia": ref,
                "descripcion": desc,
                "autorizacion": auth
            })

        lineas_res.append({
            "posicion": lr.get("POSICION_PEDIDO"),
            "referencia": ref,
            "descripcion": desc,
            "pedido": lr.get("PEDIDO") or 0,
            "pendientes": lr.get("UNIDADES_PENDIENTES") or 0,
            "acopiado": int(lr.get("ACOPIADO") or 0),
            "sustituido": bool(lr.get("SUSTITUIDO")),
        })

    return {
        "numero": numero_pedido,
        "cliente": o.get("CLIENTE"),
        "finca": o.get("FINCA_CARGA"),
        "sector": o.get("SECTOR_CARGA"),
        "fechaCarga": str(o.get("FECHA_CARGA")) if o.get("FECHA_CARGA") else None,
        "matriculaCamion": o.get("MATRICULA_CAMION"),
        "cargado": bool(o.get("CARGADO")),
        "alertasCitricos": alertas_citricos,
        "lineas": lineas_res
    }


@app.get("/api/manager/reporte/{numero_pedido}")
def manager_reporte(
    numero_pedido: str,
    formato: str = Query("html", description="Formato del informe: html (default) o pdf"),
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
):
    """Informe Punteo del pedido en HTML o PDF replicando el layout de Punteo de prueba.pdf (D-152, D-158)."""
    _verify_manager_key(k, x_api_key)
    fmt = (formato or "html").lower()
    if fmt == "pdf":
        try:
            data = punteo_pdf.build_punteo_pdf(
                client, PROJECT, DATASET, PICKING_DATASET, PICKING_VIEW, MATRICULAS_TABLE,
                numero_pedido,
            )
        except ValueError as e:
            raise HTTPException(status_code=404, detail=str(e))
        filename = f"picking_{numero_pedido}_Punteo.pdf"
        return StreamingResponse(
            io.BytesIO(data),
            media_type="application/pdf",
            headers={"Content-Disposition": f'attachment; filename="{filename}"'},
        )
    else:
        try:
            html = punteo_html.build_punteo_html(
                client, PROJECT, DATASET, PICKING_DATASET, PICKING_VIEW, MATRICULAS_TABLE,
                numero_pedido,
            )
        except ValueError as e:
            raise HTTPException(status_code=404, detail=str(e))
        return HTMLResponse(content=html)


@app.get("/api/manager/informe/desglose/{numero_pedido}")
def manager_informe_desglose(
    numero_pedido: str,
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
):
    """Informe HTML desglosado (detalle exhaustivo del pistoleo) del pedido (D-158, D-160)."""
    _verify_manager_key(k, x_api_key)
    try:
        html = punteo_html.build_desglose_html(
            client, PROJECT, DATASET, PICKING_DATASET, PICKING_VIEW, MATRICULAS_TABLE,
            numero_pedido,
        )
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))
    return HTMLResponse(content=html)


@app.get("/api/manager/informe/detalle/{numero_pedido}")
def manager_informe_detalle(
    numero_pedido: str,
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
):
    """Informe HTML del Detalle del Pistoleo (A4 horizontal, un evento por línea)."""
    _verify_manager_key(k, x_api_key)
    try:
        html = punteo_html.build_detalle_html(
            client, PROJECT, DATASET, PICKING_DATASET, PICKING_VIEW, MATRICULAS_TABLE,
            numero_pedido,
        )
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))
    return HTMLResponse(content=html)


@app.get("/api/manager/informe/control/{numero_pedido}")
def manager_informe_control(
    numero_pedido: str,
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
):
    """Informe HTML del Control de Acopio (A4 horizontal con trazabilidad de sustituciones)."""
    _verify_manager_key(k, x_api_key)
    try:
        html = punteo_html.build_control_html(
            client, PROJECT, DATASET, PICKING_DATASET, PICKING_VIEW, MATRICULAS_TABLE,
            numero_pedido,
        )
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))
    return HTMLResponse(content=html)


@app.get("/api/manager/etiquetas/dia")
def manager_etiquetas_dia(
    fecha: Optional[date] = Query(None, description="Fecha de carga (YYYY-MM-DD)"),
    estado: Optional[str] = Query(None, description="Filtro de estado"),
    incluirEnviados: bool = Query(False, description="Incluir pedidos ya enviados/cargados"),
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
):
    """Etiquetas a sacar de los pedidos de la fecha: triada referencia+litraje+sector."""
    _verify_manager_key(k, x_api_key)
    target_date = fecha or date.today()

    st_filter = (estado or "").strip().lower()
    if not st_filter:
        st_filter = "todos" if incluirEnviados else "activos"

    filtro_activos = ""
    if st_filter in ("pendientes", "pendiente"):
        filtro_activos = f" AND NOT EXISTS (SELECT 1 FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_TABLE}` pf WHERE pf.order_id = p.NUMERO_PEDIDO AND pf.picking_tipo = 'F') AND NOT EXISTS (SELECT 1 FROM `{PROJECT}.{PICKING_DATASET}.{MATRICULAS_TABLE}` m WHERE m.pedido_id = p.NUMERO_PEDIDO AND m.tipo = 'CAMION')"
    elif st_filter in ("cargados", "cargado"):
        filtro_activos = f" AND NOT EXISTS (SELECT 1 FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_TABLE}` pf WHERE pf.order_id = p.NUMERO_PEDIDO AND pf.picking_tipo = 'F') AND EXISTS (SELECT 1 FROM `{PROJECT}.{PICKING_DATASET}.{MATRICULAS_TABLE}` m WHERE m.pedido_id = p.NUMERO_PEDIDO AND m.tipo = 'CAMION')"
    elif st_filter in ("enviados", "enviado"):
        filtro_activos = f" AND EXISTS (SELECT 1 FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_TABLE}` pf WHERE pf.order_id = p.NUMERO_PEDIDO AND pf.picking_tipo = 'F')"
    elif st_filter == "activos":
        filtro_activos = f" AND NOT EXISTS (SELECT 1 FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_TABLE}` pf WHERE pf.order_id = p.NUMERO_PEDIDO AND pf.picking_tipo = 'F')"

    params = [bigquery.ScalarQueryParameter("fecha", "DATE", target_date.isoformat())]

    labels_sql = f"""
        WITH lbl AS (
            SELECT r.order_id,
                   r.ref_servida AS referencia,
                   COALESCE(lit.DESCRIPCION_LITRAJE, l.CODIGO_LITRAJE, '') AS litraje,
                   COALESCE(sec.DESCRIPCION_SECTOR, l.CODIGO_SECTOR, '') AS sector,
                   ANY_VALUE(a.DESCRIPCION_ARTICULO) AS descripcion,
                   ANY_VALUE(r.order_line_id) AS order_line_id,
                   SUM(r.cantidad_partida) AS cantidad,
                   LOGICAL_OR(r.ocr_texto IS NOT NULL AND r.ocr_texto != '') AS ocr_presente
            FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_VIEW}` r
            LEFT JOIN `{PROJECT}.{DATASET}.LINEA_PEDIDO` l ON l.HUELLA_DIGITAL = r.order_line_id
            LEFT JOIN `{PROJECT}.{DATASET}.LITRAJES` lit ON lit.ID_LITRAJE = l.CODIGO_LITRAJE
            LEFT JOIN `{PROJECT}.{DATASET}.SECTORES` sec ON sec.ID_SECTOR = l.CODIGO_SECTOR
            LEFT JOIN `{PROJECT}.{DATASET}.ARTICULOS` a ON a.ID_ARTICULO = r.ref_servida
            WHERE (r.ean_escaneado IS NULL OR r.ean_escaneado = '')
            GROUP BY r.order_id, r.ref_servida,
                     COALESCE(lit.DESCRIPCION_LITRAJE, l.CODIGO_LITRAJE, ''),
                     COALESCE(sec.DESCRIPCION_SECTOR, l.CODIGO_SECTOR, '')
            HAVING SUM(r.cantidad_partida) > 0
        )
        SELECT p.NUMERO_PEDIDO, COALESCE(c.N_COMERCIAL, '') AS CLIENTE, p.FINCA_CARGA, p.ESTADO_PEDIDO,
               lbl.referencia, lbl.litraje, lbl.sector, lbl.descripcion, lbl.cantidad,
               lbl.ocr_presente, lbl.order_line_id
        FROM `{PROJECT}.{DATASET}.PEDIDOS` p
        LEFT JOIN `{PROJECT}.{DATASET}.CLIENTE` c ON c.ID_CLIENTE = p.NUMERO_CLIENTE
        INNER JOIN lbl ON lbl.order_id = p.NUMERO_PEDIDO
        WHERE DATE(p.FECHA_CARGA) = @fecha{filtro_activos}
        ORDER BY p.NUMERO_PEDIDO DESC, lbl.referencia, lbl.litraje, lbl.sector
    """
    labels = [dict(r) for r in client.query(labels_sql, job_config=bigquery.QueryJobConfig(query_parameters=params)).result()]

    pedidos_map: dict[str, dict[str, Any]] = {}
    for l in labels:
        ped = str(l.get("NUMERO_PEDIDO") or "")
        p = pedidos_map.get(ped)
        if p is None:
            p = pedidos_map[ped] = {
                "pedido": ped,
                "cliente": l.get("CLIENTE") or "",
                "finca": l.get("FINCA_CARGA") or "",
                "etiquetas": [],
            }
        ref = str(l.get("referencia") or "")
        litraje = str(l.get("litraje") or "")
        sector = str(l.get("sector") or "")
        p["etiquetas"].append({
            "referencia": ref,
            "litraje": litraje,
            "sector": sector,
            "descripcion": l.get("descripcion") or "",
            "cantidad": float(l.get("cantidad") or 0),
            "motivo": "Venta directa - etiqueta del vendedor" if ref.startswith("9") else (
                "Etiqueta ilegible (OCR)" if l.get("ocr_presente") else "Planta sin etiqueta"
            ),
            "order_line_id": l.get("order_line_id") or "",
        })

    if pedidos_map:
        estados_sql = f"""
            SELECT pedido_id, referencia, litraje, sector, estado, actualizado_por
            FROM `{PROJECT}.{PICKING_DATASET}.{ETIQUETAS_TABLE}`
            WHERE pedido_id IN UNNEST(@pedidos)
        """
        estados_rows = [dict(r) for r in client.query(
            estados_sql,
            job_config=bigquery.QueryJobConfig(
                query_parameters=[bigquery.ArrayQueryParameter("pedidos", "STRING", list(pedidos_map.keys()))]
            ),
        ).result()]
    else:
        estados_rows = []
    estados_map = {}
    for e in estados_rows:
        estados_map[(e["pedido_id"], e["referencia"], e["litraje"] or "", e["sector"] or "")] = e

    result_pedidos = []
    for ped, p in pedidos_map.items():
        resumen = {"pendiente": 0, "impresa": 0, "encolada": 0}
        for et in p["etiquetas"]:
            key = (ped, et["referencia"], et["litraje"], et["sector"])
            st = estados_map.get(key)
            estado = st["estado"] if st else "pendiente"
            et["estado"] = estado
            et["actualizadoPor"] = st.get("actualizado_por") if st else None
            resumen[estado] = resumen.get(estado, 0) + 1
        p["resumen"] = resumen
        result_pedidos.append(p)

    return {"fecha": target_date.isoformat(), "pedidos": result_pedidos}


@app.get("/api/manager/etiquetas/{numero_pedido}")
def manager_etiquetas(
    numero_pedido: str,
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
):
    """Etiquetas a sacar del pedido: registros acopiados sin EAN, con motivo y estado."""
    _verify_manager_key(k, x_api_key)
    params = [bigquery.ScalarQueryParameter("pedido", "STRING", numero_pedido)]

    labels_sql = f"""
        SELECT r.ref_servida AS referencia,
               ANY_VALUE(a.DESCRIPCION_ARTICULO) AS descripcion,
               ANY_VALUE(COALESCE(lit.DESCRIPCION_LITRAJE, l.CODIGO_LITRAJE, '')) AS litraje,
               ANY_VALUE(COALESCE(sec.DESCRIPCION_SECTOR, l.CODIGO_SECTOR, '')) AS sector,
               ANY_VALUE(r.order_line_id) AS order_line_id,
               SUM(r.cantidad_partida) AS cantidad,
               LOGICAL_OR(r.ocr_texto IS NOT NULL AND r.ocr_texto != '') AS ocr_presente
        FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_VIEW}` r
        LEFT JOIN `{PROJECT}.{DATASET}.LINEA_PEDIDO` l ON l.HUELLA_DIGITAL = r.order_line_id
        LEFT JOIN `{PROJECT}.{DATASET}.LITRAJES` lit ON lit.ID_LITRAJE = l.CODIGO_LITRAJE
        LEFT JOIN `{PROJECT}.{DATASET}.SECTORES` sec ON sec.ID_SECTOR = l.CODIGO_SECTOR
        LEFT JOIN `{PROJECT}.{DATASET}.ARTICULOS` a ON a.ID_ARTICULO = r.ref_servida
        WHERE r.order_id = @pedido
          AND (r.ean_escaneado IS NULL OR r.ean_escaneado = '')
        GROUP BY r.ref_servida,
                 COALESCE(lit.DESCRIPCION_LITRAJE, l.CODIGO_LITRAJE, ''),
                 COALESCE(sec.DESCRIPCION_SECTOR, l.CODIGO_SECTOR, '')
        HAVING SUM(r.cantidad_partida) > 0
        ORDER BY r.ref_servida
    """
    labels = [dict(r) for r in client.query(labels_sql, job_config=bigquery.QueryJobConfig(query_parameters=params)).result()]

    estados_sql = f"""
        SELECT referencia, litraje, sector, estado, actualizado_por
        FROM `{PROJECT}.{PICKING_DATASET}.{ETIQUETAS_TABLE}`
        WHERE pedido_id = @pedido
    """
    estados = [dict(r) for r in client.query(estados_sql, job_config=bigquery.QueryJobConfig(query_parameters=params)).result()]
    estados_map = {(e["referencia"], e["litraje"] or "", e["sector"] or ""): e for e in estados}

    resumen = {"pendiente": 0, "impresa": 0, "encolada": 0}
    etiquetas = []
    for l in labels:
        ref = str(l.get("referencia") or "")
        litraje = str(l.get("litraje") or "")
        sector = str(l.get("sector") or "")
        key = (ref, litraje, sector)
        st = estados_map.get(key)
        estado = st["estado"] if st else "pendiente"
        motivo = "Venta directa - etiqueta del vendedor" if ref.startswith("9") else (
            "Etiqueta ilegible (OCR)" if l.get("ocr_presente") else "Planta sin etiqueta"
        )
        resumen[estado] = resumen.get(estado, 0) + 1
        etiquetas.append({
            "referencia": ref,
            "descripcion": l.get("descripcion") or "",
            "litraje": litraje,
            "sector": sector,
            "cantidad": float(l.get("cantidad") or 0),
            "motivo": motivo,
            "estado": estado,
            "actualizadoPor": st.get("actualizado_por") if st else None,
        })

    return {"pedido": numero_pedido, "etiquetas": etiquetas, "resumen": resumen}


@app.post("/api/manager/etiquetas/estado")
async def manager_etiquetas_estado(
    request: Request,
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
):
    """Marca el estado de una etiqueta: pendiente -> impresa -> encolada."""
    _verify_manager_key(k, x_api_key)
    body = await request.json()
    pedido = str(body.get("pedido") or "")
    ref = str(body.get("referencia") or "")
    litraje = str(body.get("litraje") or "")
    sector = str(body.get("sector") or "")
    estado = str(body.get("estado") or "pendiente")
    por = str(body.get("por") or "")
    linea = str(body.get("order_line_id") or "")
    if estado not in ("pendiente", "impresa", "encolada"):
        raise HTTPException(status_code=400, detail="Estado inválido")

    sql = f"""
        MERGE `{PROJECT}.{PICKING_DATASET}.{ETIQUETAS_TABLE}` T
        USING (SELECT @pedido AS pedido_id, @linea AS order_line_id, @ref AS referencia,
                      @litraje AS litraje, @sector AS sector) S
        ON T.pedido_id = S.pedido_id AND T.referencia = S.referencia
           AND T.litraje = S.litraje AND T.sector = S.sector
        WHEN MATCHED THEN UPDATE SET
            estado = @estado, actualizado_en = CURRENT_TIMESTAMP(), actualizado_por = @por
        WHEN NOT MATCHED THEN INSERT
            (pedido_id, order_line_id, referencia, litraje, sector, estado, creado_en, actualizado_en, actualizado_por)
            VALUES (@pedido, @linea, @ref, @litraje, @sector, @estado, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), @por)
    """
    def _merge_estado() -> None:
        client.query(sql, job_config=bigquery.QueryJobConfig(
            query_parameters=[
                bigquery.ScalarQueryParameter("pedido", "STRING", pedido),
                bigquery.ScalarQueryParameter("linea", "STRING", linea),
                bigquery.ScalarQueryParameter("ref", "STRING", ref),
                bigquery.ScalarQueryParameter("litraje", "STRING", litraje),
                bigquery.ScalarQueryParameter("sector", "STRING", sector),
                bigquery.ScalarQueryParameter("estado", "STRING", estado),
                bigquery.ScalarQueryParameter("por", "STRING", por),
            ]
        )).result()

    await run_in_threadpool(_merge_estado)
    return {"ok": True, "pedido": pedido, "referencia": ref, "estado": estado}


@app.get("/api/manager/etiquetas/dia/informe")
def manager_etiquetas_dia_informe(
    fecha: Optional[date] = Query(None, description="Fecha de carga (YYYY-MM-DD)"),
    estado: Optional[str] = Query(None, description="Filtro de estado"),
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
):
    """Informe HTML imprimible de etiquetas a sacar del día (D-158)."""
    _verify_manager_key(k, x_api_key)
    data = manager_etiquetas_dia(
        fecha=fecha,
        estado=estado,
        incluirEnviados=True if (estado or "").lower() == "todos" else False,
        k=k,
        x_api_key=x_api_key,
    )
    html = punteo_html.build_etiquetas_html(data.get("pedidos", []), data.get("fecha", ""))
    return HTMLResponse(content=html)


@app.get("/api/manager/historico")
def manager_historico(
    fecha: Optional[date] = Query(None, description="Filtrar por fecha específica"),
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
):
    """Pestaña Histórico: pedidos cargados y enviados de todas las fechas (D-160)."""
    _verify_manager_key(k, x_api_key)
    if fecha is None:
        sql = f"""
            SELECT DATE(p.FECHA_CARGA) AS FECHA,
                   COUNT(DISTINCT p.NUMERO_PEDIDO) AS TOTAL,
                   COUNT(DISTINCT IF(pf.order_id IS NOT NULL, p.NUMERO_PEDIDO, NULL)) AS ENVIADOS,
                   COUNT(DISTINCT IF(m.pedido_id IS NOT NULL AND pf.order_id IS NULL, p.NUMERO_PEDIDO, NULL)) AS CARGADOS
            FROM `{PROJECT}.{DATASET}.PEDIDOS` p
            LEFT JOIN `{PROJECT}.{PICKING_DATASET}.{MATRICULAS_TABLE}` m
                ON m.pedido_id = p.NUMERO_PEDIDO AND m.tipo = 'CAMION'
            LEFT JOIN (
                SELECT DISTINCT order_id FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_TABLE}` WHERE picking_tipo = 'F'
            ) pf ON pf.order_id = p.NUMERO_PEDIDO
            WHERE m.pedido_id IS NOT NULL OR pf.order_id IS NOT NULL
            GROUP BY FECHA
            ORDER BY FECHA DESC
        """
        rows = [dict(r) for r in client.query(sql).result()]
        return {
            "fechas": [
                {
                    "fecha": str(r["FECHA"]),
                    "total": int(r["TOTAL"] or 0),
                    "cargados": int(r["CARGADOS"] or 0),
                    "enviados": int(r["ENVIADOS"] or 0),
                }
                for r in rows
            ]
        }
    else:
        sql = f"""
            SELECT p.SERIE_PEDIDO, p.NUMERO_PEDIDO, p.FECHA_CARGA, p.SECTOR_CARGA, p.FINCA_CARGA,
                   COALESCE(c.N_COMERCIAL, '') AS CLIENTE,
                   CASE WHEN m.pedido_id IS NOT NULL THEN TRUE ELSE FALSE END AS CARGADO,
                   CASE WHEN pf.order_id IS NOT NULL THEN TRUE ELSE FALSE END AS TIENE_PARTE_FINAL,
                   COALESCE(pr.TOTAL_PIS, 0) AS TOTAL_PISTOLEADO,
                   COALESCE(pr.TOTAL_EVENTOS, 0) AS TOTAL_EVENTOS
            FROM `{PROJECT}.{DATASET}.PEDIDOS` p
            LEFT JOIN `{PROJECT}.{DATASET}.CLIENTE` c ON c.ID_CLIENTE = p.NUMERO_CLIENTE
            LEFT JOIN `{PROJECT}.{PICKING_DATASET}.{MATRICULAS_TABLE}` m
                ON m.pedido_id = p.NUMERO_PEDIDO AND m.tipo = 'CAMION'
            LEFT JOIN (
                SELECT DISTINCT order_id FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_TABLE}` WHERE picking_tipo = 'F'
            ) pf ON pf.order_id = p.NUMERO_PEDIDO
            LEFT JOIN (
                SELECT order_id, SUM(cantidad_partida) AS TOTAL_PIS, COUNT(*) AS TOTAL_EVENTOS
                FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_VIEW}`
                GROUP BY order_id
            ) pr ON pr.order_id = p.NUMERO_PEDIDO
            WHERE DATE(p.FECHA_CARGA) = @fecha AND (m.pedido_id IS NOT NULL OR pf.order_id IS NOT NULL)
            ORDER BY p.NUMERO_PEDIDO DESC
        """
        params = [bigquery.ScalarQueryParameter("fecha", "DATE", fecha.isoformat())]
        rows = [dict(r) for r in client.query(sql, job_config=bigquery.QueryJobConfig(query_parameters=params)).result()]
        pedidos = []
        for r in rows:
            cargado = bool(r.get("CARGADO"))
            tiene_final = bool(r.get("TIENE_PARTE_FINAL"))
            st = "enviado" if tiene_final else ("cargado" if cargado else "pendiente")
            pedidos.append({
                "serie": r.get("SERIE_PEDIDO") or "",
                "numero": r.get("NUMERO_PEDIDO") or "",
                "cliente": r.get("CLIENTE") or "",
                "fechaCarga": str(r.get("FECHA_CARGA")) if r.get("FECHA_CARGA") else None,
                "sector": r.get("SECTOR_CARGA") or "",
                "finca": r.get("FINCA_CARGA") or "",
                "estado": st,
                "cargado": cargado,
                "tieneParteFinal": tiene_final,
                "totalPistoleado": int(r.get("TOTAL_PISTOLEADO") or 0),
                "totalEventos": int(r.get("TOTAL_EVENTOS") or 0),
            })
        return {"fecha": fecha.isoformat(), "pedidos": pedidos}


@app.get("/api/manager/historico/{numero_pedido}")
def manager_historico_detalle(
    numero_pedido: str,
    k: Optional[str] = Query(default=None),
    x_api_key: Optional[str] = Header(default=None),
):
    """Detalle completo del pedido en histórico: matrículas, eventos de pistoleo y etiquetas (D-160)."""
    _verify_manager_key(k, x_api_key)
    params = [bigquery.ScalarQueryParameter("pedido", "STRING", numero_pedido)]
    jc = bigquery.QueryJobConfig(query_parameters=params)

    # 1. Matrículas
    mat_sql = f"""
        SELECT tipo, matricula, muelle, foto_url, creado_en
        FROM `{PROJECT}.{PICKING_DATASET}.{MATRICULAS_TABLE}`
        WHERE pedido_id = @pedido
        ORDER BY creado_en DESC
    """
    matriculas = [
        {
            "tipo": r.get("tipo"),
            "matricula": r.get("matricula"),
            "muelle": r.get("muelle"),
            "fotoUrl": r.get("foto_url"),
            "creadoEn": str(r.get("creado_en")) if r.get("creado_en") else None,
        }
        for r in client.query(mat_sql, job_config=jc).result()
    ]

    # 2. Eventos de pistoleo
    reg_sql = f"""
        SELECT picking_numero, picking_tipo, fecha_hora, empleado_nombre,
               ean_escaneado, ocr_texto, ref_original, ref_servida,
               sustituido, cantidad_partida, litros, medida, calibre
        FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_VIEW}`
        WHERE order_id = @pedido
        ORDER BY fecha_hora
    """
    registros = [
        {
            "parte": f"{r.get('picking_tipo')}{r.get('picking_numero')}",
            "fechaHora": str(r.get("fecha_hora")).replace("T", " ")[:19] if r.get("fecha_hora") else "",
            "empleado": r.get("empleado_nombre") or "",
            "ean": r.get("ean_escaneado") or "",
            "ocr": r.get("ocr_texto") or "",
            "refOriginal": r.get("ref_original") or "",
            "refServida": r.get("ref_servida") or "",
            "sustituido": bool(r.get("sustituido")),
            "cantidad": float(r.get("cantidad_partida") or 0),
            "litros": float(r.get("litros") or 0),
            "medida": r.get("medida") or "",
            "calibre": r.get("calibre") or "",
        }
        for r in client.query(reg_sql, job_config=jc).result()
    ]

    # 3. Etiquetas
    etiquetas_data = manager_etiquetas(numero_pedido=numero_pedido, k=k, x_api_key=x_api_key)

    return {
        "pedido": numero_pedido,
        "matriculas": matriculas,
        "registros": registros,
        "etiquetas": etiquetas_data.get("etiquetas", []),
        "resumenEtiquetas": etiquetas_data.get("resumen", {}),
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", "8080")))

