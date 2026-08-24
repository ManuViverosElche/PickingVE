import sys
sys.path.insert(0, 'backend')
from google.cloud import bigquery
client = bigquery.Client(project='dashboard-439511')
q = client.query("""
SELECT NUMERO_PEDIDO, POSICION_PEDIDO, REFERENCIA_ARTICULO, UNIDADES, UNIDADES_PENDIENTES 
FROM `dashboard-439511.GestionComercialVE.LINEA_PEDIDO` 
WHERE CAST(NUMERO_PEDIDO AS STRING) = '260842' 
AND IMPRIMIR_LINEA = 0 
LIMIT 10
""")
print([dict(r) for r in q.result()])