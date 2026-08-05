import logging

import pandas as pd
from tenacity import retry, retry_if_exception_type, stop_after_attempt, wait_exponential

from . import access_client, bigquery_client, transform


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


def sync_all(client, conn, tables: list[dict], dataset: str, logger: logging.Logger, settings: dict) -> dict:
    results = {}
    for table_cfg in tables:
        name = table_cfg["name"]
        logger.info("=== Sincronizando %s ===", name)
        try:
            results[name] = sync_table(client, conn, table_cfg, dataset, logger, settings)
        except Exception as exc:
            logger.error("Fallo en %s: %s", name, exc)
            results[name] = 0
    return results
