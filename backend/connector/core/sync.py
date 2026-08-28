import logging

import pandas as pd
from tenacity import retry, retry_if_exception_type, stop_after_attempt, wait_exponential

from . import access_client, bigquery_client, transform

# Anio mas antiguo que interesa cargar en el historico (decision del usuario)
MIN_ANIO_HISTORICO = 2020


def build_logger(settings: dict) -> logging.Logger:
    level = getattr(logging, settings["logging"].get("level", "INFO").upper())
    handlers: list[logging.Handler] = [logging.StreamHandler()]
    log_file = settings["logging"].get("file")
    if log_file:
        handlers.append(logging.FileHandler(log_file, encoding="utf-8"))
    logging.basicConfig(level=level, format="%(asctime)s %(levelname)s %(name)s: %(message)s", handlers=handlers)
    return logging.getLogger("conector")


def extract(conn, table_cfg: dict, logger: logging.Logger) -> pd.DataFrame:
    queries = table_cfg.get("queries") or [table_cfg["query"]]
    frames = []
    for sql in queries:
        logger.info("Extrayendo %s (%s)", table_cfg["name"], table_cfg["source_table"])
        columns, rows = access_client.read_query(conn, sql)
        frames.append(pd.DataFrame.from_records(rows, columns=columns))
    if len(frames) == 1:
        return frames[0]
    return pd.concat(frames, ignore_index=True)


def transform_df(df: pd.DataFrame, table_cfg: dict, logger: logging.Logger) -> pd.DataFrame:
    df = transform.apply_transform(df, table_cfg, logger)
    return transform.finalize(df, table_cfg)


def load(client, table_cfg: dict, df: pd.DataFrame, dataset: str, logger: logging.Logger) -> int:
    project = client.project
    table_id = f"{project}.{dataset}.{table_cfg['name']}"
    if table_cfg.get("merge_key"):
        rows = bigquery_client.load_merge(client, table_cfg, df, table_id)
    elif table_cfg.get("append"):
        rows = bigquery_client.load_append(client, table_cfg, df, table_id)
    else:
        rows = bigquery_client.load_full(client, table_cfg, df, table_id)
    logger.info("Cargadas %d filas en %s", rows, table_id)
    return rows


def sync_table(client, conn, table_cfg: dict, dataset: str, logger: logging.Logger, settings: dict) -> int:
    sync_cfg = settings["sync"]
    load_with_retry = retry(
        retry=retry_if_exception_type(Exception),
        stop=stop_after_attempt(sync_cfg.get("retries", 3)),
        wait=wait_exponential(multiplier=sync_cfg.get("retry_backoff_seconds", 5)),
        reraise=True,
    )(lambda: load(client, table_cfg, df, dataset, logger))
    df = extract(conn, table_cfg, logger)
    logger.info("Transformando %s (%d filas brutas)", table_cfg["name"], len(df))
    df = transform_df(df, table_cfg, logger)
    return load_with_retry()


def filter_for_dataset(tables: list[dict], dataset: str, settings: dict) -> list[dict]:
    production = settings["bigquery"].get("production_dataset")
    if dataset != production:
        return tables
    return [t for t in tables if not t.get("only_analytics")]


def discover_history_dbs(settings: dict) -> list[tuple[int, str]]:
    """Descubre las bases historicas junto al fichero actual del NAS.

    Patron ESTRICTO sobre el nombre: 014<aaaa>.accdb (operativa) y
    B14<aaaa>.accdb (contabilidad, existe desde 2026). Excluye backups,
    copias ('Copia de...', '_Backup'), ficheros corruptos (MAL*) y otras
    series no solicitadas (001*, 015*, XD1*, FactuSOL*).
    Devuelve [(anio, ruta_absoluta)] sin la base operativa en curso.
    """
    import glob
    import os
    import re

    cur = os.path.abspath(settings["access"]["db_path"])
    folder = os.path.dirname(cur)
    patron = re.compile(r"^(?:014|B14)(\d{4})\.accdb$", re.IGNORECASE)
    found: list[tuple[int, str]] = []
    for f in glob.glob(os.path.join(folder, "*.accdb")):
        m = patron.match(os.path.basename(f))
        if not m:
            continue
        fa = os.path.abspath(f)
        if fa == cur:
            continue
        found.append((int(m.group(1)), fa))
    return sorted(set(found))


def sync_historical(client, tables: list[dict], dataset: str, logger: logging.Logger, settings: dict) -> dict:
    """Historico multi-anio con estrategia del usuario (D-18):

      - Anios CERRADOS (<= actual-2): se cargan UNA sola vez; un registro de
        control (HIST_CONTROL) evita repetir trabajo que nunca cambia.
      - Anio ANTERIOR y ACTUAL (+ su base B14): se REFRESCAN en cada ejecucion,
        borrando antes sus filas (DELETE WHERE ANIO) para que sea idempotente.

    Las tablas destino son HIST_<tabla> en el dataset indicado, con columna ANIO.
    """
    results: dict[str, int] = {}
    hist_tables = [t for t in tables if t.get("hist")]
    if not hist_tables:
        logger.error("--historical: ninguna tabla marcada con hist: true")
        return results

    dbs = discover_history_dbs(settings)
    if not dbs:
        logger.warning("--historical: no se han encontrado ficheros 014*/B14* validos junto a %s",
                       settings["access"]["db_path"])
        return results

    from datetime import datetime

    anio_actual = datetime.now().year
    limite_cerrados = anio_actual - 2  # <= limite => cerrado, carga unica

    control_id = f"{client.project}.{dataset}.HIST_CONTROL"
    client.query(
        f"CREATE TABLE IF NOT EXISTS `{control_id}` ("
        "db_file STRING, anio INT64, filas INT64, cargado_en TIMESTAMP)"
    ).result()

    ya_cargados = {
        r["db_file"]
        for r in client.query(f"SELECT db_file FROM `{control_id}`").result()
    }

    total_filas = 0
    for anio, path in sorted(dbs):
        if anio < MIN_ANIO_HISTORICO:
            logger.info("=== %s: anterior a %d, SE OMITE ===", path, MIN_ANIO_HISTORICO)
            continue
        es_reciente = anio >= anio_actual - 1
        etiqueta = "RECIENTE" if es_reciente else "CERRADO"
        if not es_reciente and path.lower() in ya_cargados:
            logger.info("=== %s %s: ya cargada antes, SE OMITE ===", etiqueta, path)
            continue
        logger.info("=== Historico %d (%s) %s ===", anio, etiqueta, path)

        s_year = {**settings, "access": {**settings["access"], "db_path": path}}
        filas_fichero = 0
        try:
            with access_client.open_connection(s_year) as conn:
                for base_cfg in hist_tables:
                    schema = list(base_cfg["schema"])
                    if not any(f["name"] == "ANIO" for f in schema):
                        schema.append({"name": "ANIO", "type": "INTEGER", "mode": "NULLABLE",
                                       "description": "Año de la base de origen"})
                    cfg = {
                        **base_cfg,
                        "name": f"HIST_{base_cfg['name']}",
                        "merge_key": None,
                        "append": True,
                        "schema": schema,
                    }
                    table_id = f"{client.project}.{dataset}.{cfg['name']}"
                    # Idempotencia: borra las filas de este anio antes de reinsertar
                    try:
                        client.query(f"DELETE FROM `{table_id}` WHERE ANIO = {anio}").result()
                    except Exception:
                        pass  # tabla aun no existe
                    try:
                        df = extract(conn, cfg, logger)
                    except Exception as exc:
                        logger.warning("  %s ausente o ilegible en %s: %s", base_cfg["name"], path, exc)
                        continue
                    df["ANIO"] = anio
                    df = transform_df(df, cfg, logger)
                    filas = load(client, cfg, df, dataset, logger)
                    results[cfg["name"]] = results.get(cfg["name"], 0) + filas
                    filas_fichero += filas
        except Exception as exc:
            logger.error("No se pudo procesar %s: %s", path, exc)
            continue

        client.query(
            f"DELETE FROM `{control_id}` WHERE db_file = @f"
            .replace("@f", f"'{path.lower().replace(chr(39), chr(39)*2)}'")
        ).result()
        client.query(
            f"INSERT INTO `{control_id}` (db_file, anio, filas, cargado_en) "
            f"VALUES ('{path.lower().replace(chr(39), chr(39)*2)}', {anio}, {filas_fichero}, CURRENT_TIMESTAMP())"
        ).result()
        total_filas += filas_fichero
        logger.info("  -> %d filas registradas en HIST_CONTROL", filas_fichero)

    logger.info("Historico completado: %d filas nuevas/refrescadas", total_filas)
    return results


def sync_all(client, conn, tables: list[dict], dataset: str, logger: logging.Logger, settings: dict) -> dict:
    results = {}
    tables = filter_for_dataset(tables, dataset, settings)
    for table_cfg in tables:
        name = table_cfg["name"]
        logger.info("=== Sincronizando %s ===", name)
        try:
            results[name] = sync_table(client, conn, table_cfg, dataset, logger, settings)
        except Exception as exc:
            logger.error("Fallo en %s: %s", name, exc)
            results[name] = 0
    return results
