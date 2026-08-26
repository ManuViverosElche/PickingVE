import argparse
import sys
from pathlib import Path

CONNECTOR_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(CONNECTOR_ROOT))

from core.access_client import open_connection
from core.bigquery_client import build_client
from core.config import load_settings, load_tables
from core.sync import build_logger, extract, filter_for_dataset, sync_all, sync_table, transform_df


def main() -> int:
    parser = argparse.ArgumentParser(description="Conector Factusol -> BigQuery")
    parser.add_argument("--table", help="Sincronizar solo una tabla (por defecto todas)")
    parser.add_argument("--dataset", help="Dataset de BigQuery destino (por defecto conector_test)")
    parser.add_argument("--dry-run", action="store_true", help="Solo extraer y transformar, sin cargar")
    parser.add_argument("--limit", type=int, help="Limitar el numero de filas por tabla (pruebas)")
    args = parser.parse_args()

    settings = load_settings()
    logger = build_logger(settings)
    dataset = args.dataset or settings["bigquery"]["test_dataset"]

    tables = filter_for_dataset(load_tables(), dataset, settings)
    if args.table:
        tables = [t for t in tables if t["name"] == args.table]
        if not tables:
            logger.error("Tabla no encontrada en %s (¿solo Analytics?): %s", dataset, args.table)
            return 1

    client = build_client(settings)

    with open_connection(settings) as conn:
        if args.dry_run:
            for table_cfg in tables:
                df = extract(conn, table_cfg, logger)
                df = transform_df(df, table_cfg, logger)
                logger.info("[dry-run] %s: %d filas, %d columnas", table_cfg["name"], len(df), len(df.columns))
            return 0

        if args.table:
            df = extract(conn, tables[0], logger)
            if args.limit:
                df = df.head(args.limit)
            df = transform_df(df, tables[0], logger)
            sync_table(client, conn, tables[0], dataset, logger, settings)
            return 0

        sync_all(client, conn, tables, dataset, logger, settings)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
