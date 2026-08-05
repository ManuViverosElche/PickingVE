import sys
sys.path.insert(0, r"backend\connector")
from core.config import load_settings
from core.bigquery_client import build_client

s = load_settings()
c = build_client(s)
PROD = f"{s['bigquery']['project_id']}.GestionComercialVE"
TEST = f"{s['bigquery']['project_id']}.{s['bigquery']['test_dataset']}"


def mismatches(table, key, cols, extra_join=""):
    cond = " AND ".join([f"t.{col} = p.{col}" for col in cols])
    sql = f"""
    SELECT COUNT(*) AS n FROM (
      SELECT t.{key} FROM `{TEST}.{table}` t
      JOIN `{PROD}.{table}` p ON t.{key} = p.{key} {extra_join}
      WHERE NOT ({cond})
    )
    """
    for r in c.query(sql):
        return r["n"]
    return None


def sample_mismatch(table, key, cols, extra_join=""):
    cond = " AND ".join([f"t.{col} = p.{col}" for col in cols])
    sel = ", ".join([f"t.{key}", *[f"t.{col} AS t_{col}" for col in cols], *[f"p.{col} AS p_{col}" for col in cols]])
    sql = f"""
    SELECT {sel} FROM `{TEST}.{table}` t
    JOIN `{PROD}.{table}` p ON t.{key} = p.{key} {extra_join}
    WHERE NOT ({cond}) LIMIT 5
    """
    for r in c.query(sql):
        print("  ", dict(r))


checks = [
    ("PEDIDOS", "NUMERO_PEDIDO", ["SERIE_PEDIDO", "NUMERO_CLIENTE", "CODIGO_AGENTE", "BASE_10", "TOTAL_PEDIDO", "FECHA_PEDIDO", "FECHA_CREACION", "ESTADO_PEDIDO"], "AND t.SERIE_PEDIDO = p.SERIE_PEDIDO"),
    ("ALBARANES", "CODIGO_ALBARAN", ["TIPO_ALBARAN", "CLIENTE", "AGENTE", "NETO", "BASE", "IMPORTE_IVA", "TOTAL_ALBARAN", "FECHA_ALBARAN"]),
]

for table, key, cols, extra_join in checks:
    try:
        n = mismatches(table, key, cols, extra_join)
        print(f"{table:12} mismatches = {n}")
        if n:
            sample_mismatch(table, key, cols, extra_join)
    except Exception as e:
        print(f"{table:12} ERROR {type(e).__name__}: {str(e)[:200]}")

print("--- LINEA_PEDIDO: por columna ---")
lp_cols = ["NUMERO_PEDIDO", "POSICION_PEDIDO", "REFERENCIA_ARTICULO", "UNIDADES", "PRIORIDAD", "DESCRIPCION_ARTICULO", "CODIGO_SECTOR"]
for col in lp_cols:
    others = [c for c in lp_cols if c != col]
    try:
        n = mismatches("LINEA_PEDIDO", "HUELLA_DIGITAL", others)
        print(f"  excluyendo {col:22} -> {n} mismatches")
    except Exception as e:
        print(f"  excluyendo {col:22} -> ERROR {str(e)[:120]}")
