import sys
sys.path.insert(0, r"backend\connector")
from core.config import load_settings
from core.bigquery_client import build_client

s = load_settings()
c = build_client(s)
prod = f"{s['bigquery']['project_id']}.GestionComercialVE"
test = f"{s['bigquery']['project_id']}.{s['bigquery']['test_dataset']}"

names = ["CLIENTE", "AGENTE", "ARTICULOS", "ALBARANES", "FACTURAS", "PEDIDOS",
         "LINEA_PEDIDO", "CODIGOS_EAN", "LITRAJES", "SECTORES", "FORMAS_PAGO",
         "TARIFAS", "STOCK", "PRECIOS_VENTA"]

def count(q):
    for r in c.query(q):
        return r["n"]
    return 0


for n in names:
    try:
        pn = count(f"SELECT COUNT(*) AS n FROM `{prod}.{n}`")
    except Exception as e:
        pn = f"NO EXISTE ({type(e).__name__})"
    try:
        tn = count(f"SELECT COUNT(*) AS n FROM `{test}.{n}`")
    except Exception as e:
        tn = f"NO EXISTE ({type(e).__name__})"
    print(f"{n:14} prod={pn!s:>12}  test={tn!s:>12}  diff={('' if isinstance(pn, int) and isinstance(tn, int) else '?')}")
