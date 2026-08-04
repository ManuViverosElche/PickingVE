import hashlib
import os
import time
import threading
from datetime import date
from typing import Any, Optional

from fastapi import FastAPI, HTTPException, Query, Header, Request
from google.cloud import bigquery
from google.cloud.exceptions import NotFound
from pydantic import BaseModel, Field

PROJECT = os.getenv("GCP_PROJECT", "dashboard-439511")
DATASET = "GestionComercialVE"
PICKING_DATASET = "pickingve"
PICKING_TABLE = "picking_registros"
ENCARGADOS_TABLE = "encargados"
API_KEY = os.getenv("API_KEY", "")
PASSWORD_SALT = os.getenv("PASSWORD_SALT", "pickingve-2026")
MAX_REGISTROS = 1000

client = bigquery.Client(project=PROJECT)

app = FastAPI(title="PickingVE API", version="1.1.0")

_rate_lock = threading.Lock()
_rate_hits: dict[str, list[float]] = {}
GET_LIMIT = 120
POST_LIMIT = 30
WINDOW_SECONDS = 60


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
            fecha_hora TIMESTAMP
        )
        """
    ).result()


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
            modo STRING
        )
        """
    ).result()
    _ensure_column(ENCARGADOS_TABLE, "modo", "modo STRING")


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


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/api/encargados")
def lista_encargados(
    request: Request,
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", GET_LIMIT)
    rows = _query(
        f"""
        SELECT id, nombre, usuario, password_hash, rol, fincas_carga, modo
        FROM `{PROJECT}.{PICKING_DATASET}.{ENCARGADOS_TABLE}`
        ORDER BY nombre
        """
    )
    return {"encargados": rows}


@app.get("/api/fincas")
def lista_fincas(
    request: Request,
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", GET_LIMIT)
    rows = _query(
        f"""
        SELECT DISTINCT FINCA_CARGA AS finca
        FROM `{PROJECT}.{DATASET}.PEDIDOS`
        WHERE FINCA_CARGA IS NOT NULL AND TRIM(FINCA_CARGA) != ''
        ORDER BY finca
        """
    )
    return {"fincas": [r["finca"] for r in rows]}


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
                      @fincas_carga AS fincas_carga, @modo AS modo) S
        ON T.id = S.id
        WHEN MATCHED THEN UPDATE SET
            nombre = S.nombre,
            usuario = S.usuario,
            password_hash = IF(@password = '', T.password_hash, S.password_hash),
            rol = S.rol,
            fincas_carga = S.fincas_carga,
            modo = S.modo
        WHEN NOT MATCHED THEN INSERT (id, nombre, usuario, password_hash, rol, fincas_carga, modo)
            VALUES (S.id, S.nombre, S.usuario, S.password_hash, S.rol, S.fincas_carga, S.modo)
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
        SELECT id, nombre, usuario, password_hash, rol, fincas_carga, modo
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
    return {
        "id": e["id"],
        "nombre": e["nombre"],
        "usuario": e["usuario"],
        "rol": e["rol"],
        "fincas_carga": e["fincas_carga"],
        "modo": e["modo"],
    }


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
    x_api_key: Optional[str] = Header(default=None),
) -> dict[str, Any]:
    _verify_key(x_api_key)
    _check_rate_limit(request.client.host if request.client else "unknown", GET_LIMIT)
    if desde is None and fecha is None:
        raise HTTPException(status_code=400, detail="Indica 'desde' o 'fecha'")
    sql = f"""
        SELECT p.SERIE_PEDIDO, p.NUMERO_PEDIDO, p.NUMERO_CLIENTE, p.ESTADO_PEDIDO,
               p.FECHA_CARGA, p.SECTOR_CARGA, p.FINCA_CARGA, p.OBSERVACIONES,
               p.MARCA_PEDIDO,
               COALESCE(c.N_COMERCIAL, '') AS CLIENTE,
               COALESCE(c.N_FISCAL, '') AS CLIENTE_FISCAL,
               l.HUELLA_DIGITAL, l.POSICION_PEDIDO, l.REFERENCIA_ARTICULO,
               l.DESCRIPCION_ARTICULO, l.UNIDADES, l.UNIDADES_PENDIENTES,
               l.CODIGO_LITRAJE, l.CODIGO_SECTOR, l.MARCA, l.FINCA_RELEVADA,
               l.SECTOR_RELEVADO, l.UBICACION_EXTRA, l.PRIORIDAD, l.ACCION_LOGISTICA,
               l.NOTA_LINEA_PEDIDO, l.IMPRIMIR_LINEA,
               lt.DESCRIPCION_LITRAJE, st.DESCRIPCION_SECTOR
        FROM `{PROJECT}.{DATASET}.PEDIDOS` p
        LEFT JOIN `{PROJECT}.{DATASET}.CLIENTE` c ON c.ID_CLIENTE = p.NUMERO_CLIENTE
        LEFT JOIN `{PROJECT}.{DATASET}.LINEA_PEDIDO` l
            ON l.SERIE_PEDIDO = p.SERIE_PEDIDO AND l.NUMERO_PEDIDO = p.NUMERO_PEDIDO
            AND COALESCE(l.IMPRIMIR_LINEA, 0) = 0
        LEFT JOIN `{PROJECT}.{DATASET}.LITRAJES` lt ON lt.ID_LITRAJE = l.CODIGO_LITRAJE
        LEFT JOIN `{PROJECT}.{DATASET}.SECTORES` st ON st.ID_SECTOR = l.CODIGO_SECTOR
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
        lista = [f.strip().upper() for f in fincas.split(",") if f.strip()]
        where.append("p.FINCA_CARGA IN UNNEST(@fincas)")
        params.append(bigquery.ArrayQueryParameter("fincas", "STRING", lista))
    elif finca:
        where.append("p.FINCA_CARGA = @finca")
        params.append(bigquery.ScalarQueryParameter("finca", "STRING", finca))
    if estados:
        lista_estados = [int(e.strip()) for e in estados.split(",") if e.strip()]
        where.append("p.ESTADO_PEDIDO IN UNNEST(@estados)")
        params.append(bigquery.ArrayQueryParameter("estados", "INT64", lista_estados))
    elif estado is not None:
        where.append("p.ESTADO_PEDIDO = @estado")
        params.append(bigquery.ScalarQueryParameter("estado", "INT64", estado))
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
                "observaciones": r.get("OBSERVACIONES") or "",
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
    cantidad_partida: float = Field(default=0, ge=0, le=1_000_000)
    fecha_hora: str = Field(min_length=1, max_length=64)


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
        return {"ok": 0, "duplicados": 0}

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
        return {"ok": 0, "duplicados": len(body.registros)}

    errors = client.insert_rows_json(
        f"{PROJECT}.{PICKING_DATASET}.{PICKING_TABLE}",
        [r.model_dump() for r in nuevos],
    )
    if errors:
        raise HTTPException(status_code=500, detail=str(errors[:5]))
    return {"ok": len(nuevos), "duplicados": len(pending_ids) - len(nuevos)}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", "8080")))
