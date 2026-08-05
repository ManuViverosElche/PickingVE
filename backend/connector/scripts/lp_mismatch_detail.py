import sys
sys.path.insert(0, r"backend\connector")
from core.config import load_settings
from core.bigquery_client import build_client

s = load_settings()
c = build_client(s)
PROD = f"{s['bigquery']['project_id']}.GestionComercialVE"
TEST = f"{s['bigquery']['project_id']}.{s['bigquery']['test_dataset']}"

sql = """
SELECT t.NUMERO_PEDIDO, t.POSICION_PEDIDO, t.REFERENCIA_ARTICULO,
       t.CODIGO_SECTOR AS t_sector, p.CODIGO_SECTOR AS p_sector,
       t.POSICION_PEDIDO AS t_pos, p.POSICION_PEDIDO AS p_pos,
       t.UNIDADES AS t_unid, p.UNIDADES AS p_unid,
       t.HUELLA_DIGITAL AS t_huella, p.HUELLA_DIGITAL AS p_huella
FROM `%s`.LINEA_PEDIDO t
JOIN `%s`.LINEA_PEDIDO p
  ON t.HUELLA_DIGITAL = p.HUELLA_DIGITAL
WHERE NOT (t.CODIGO_SECTOR = p.CODIGO_SECTOR AND t.POSICION_PEDIDO = p.POSICION_PEDIDO
       AND t.UNIDADES = p.UNIDADES AND t.REFERENCIA_ARTICULO = p.REFERENCIA_ARTICULO)
LIMIT 10
""" % (TEST, PROD)

for r in c.query(sql):
    print(dict(r))
