import sys
import os

connector_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, connector_dir)

from core.config import load_settings
from core.bigquery_client import build_client

def main():
    settings = load_settings()
    client = build_client(settings)
    project_id = settings["bigquery"]["project_id"]
    
    views = {
        "mart_articulos_completo": f"""
            CREATE OR REPLACE VIEW `{project_id}.Analytics.mart_articulos_completo` AS
            SELECT 
              a.ID_ARTICULO,
              a.DESCRIPCION_ARTICULO,
              a.NOMBRE_CIENTIFICO,
              a.GLOBALGAP,
              a.DESCATALOGADO,
              a.CODIGO_EAN,
              a.FINCA_ARTICULO,
              a.CODIGO_FAMILIA,
              f.DESCRIPCION_FAMILIA,
              f.ID_SECCION,
              s.DESCRIPCION_SECCION,
              a.PRECIO_COMPRA,
              a.UBICACIONES_FINCAS,
              a.PESO
            FROM `{project_id}.Analytics.ARTICULOS` a
            LEFT JOIN `{project_id}.Analytics.FAMILIAS` f ON a.CODIGO_FAMILIA = f.ID_FAMILIA
            LEFT JOIN `{project_id}.Analytics.SECCIONES` s ON f.ID_SECCION = s.ID_SECCION
        """,
        "mart_estado_cliente": f"""
            CREATE OR REPLACE VIEW `{project_id}.Analytics.mart_estado_cliente` AS
            SELECT 
              c.ID_CLIENTE,
              c.N_FISCAL AS NOMBRE_FISCAL,
              c.N_COMERCIAL AS NOMBRE_COMERCIAL,
              c.DIRECCION,
              c.CIUDAD,
              c.PROVINCIA,
              c.TELEFONOS,
              c.EMAIL,
              c.C_AGENTE AS CODIGO_AGENTE,
              g.NOMBRE_AGENTE,
              c.F_PAGO AS CODIGO_FORMA_PAGO,
              fp.DESCRIPCION_FORMA_PAGO,
              COALESCE(v.total_pendiente, 0) AS IMPORTE_PENDIENTE,
              COALESCE(v.total_vencido, 0) AS IMPORTE_VENCIDO,
              COALESCE(v.num_vencidos, 0) AS NUM_RECIBOS_VENCIDOS
            FROM `{project_id}.Analytics.CLIENTE` c
            LEFT JOIN `{project_id}.Analytics.AGENTE` g ON c.C_AGENTE = g.ID_AGENTE
            LEFT JOIN `{project_id}.Analytics.FORMAS_PAGO` fp ON c.F_PAGO = fp.ID_FORMA_PAGO
            LEFT JOIN (
              SELECT 
                CLIENTE,
                SUM(IMPORTE) AS total_pendiente,
                SUM(IF(SAFE.PARSE_DATE('%Y-%m-%d', SUBSTR(FECHA_VENCIMIENTO, 1, 10)) < CURRENT_DATE(), IMPORTE, 0)) AS total_vencido,
                COUNTIF(SAFE.PARSE_DATE('%Y-%m-%d', SUBSTR(FECHA_VENCIMIENTO, 1, 10)) < CURRENT_DATE() AND ESTADO != 'C') AS num_vencidos
              FROM `{project_id}.Analytics.VENCIMIENTOS`
              WHERE ESTADO != 'C'
              GROUP BY CLIENTE
            ) v ON c.ID_CLIENTE = v.CLIENTE
        """,
        "mart_marcas": f"""
            CREATE OR REPLACE VIEW `{project_id}.Analytics.mart_marcas` AS
            SELECT 
              l.MARCA,
              l.SERIE_PEDIDO,
              l.NUMERO_PEDIDO,
              p.FECHA_CARGA,
              p.ESTADO_PEDIDO,
              IF(p.ESTADO_PEDIDO = 3, TRUE, FALSE) AS PEDIDO_ANULADO,
              l.REFERENCIA_ARTICULO,
              l.DESCRIPCION_ARTICULO,
              l.UNIDADES,
              l.UNIDADES_PENDIENTES,
              l.TOTAL_ACOPIADO,
              c.N_COMERCIAL AS CLIENTE_COMERCIAL,
              g.NOMBRE_AGENTE AS COMERCIAL
            FROM `{project_id}.Analytics.LINEA_PEDIDO` l
            JOIN `{project_id}.Analytics.PEDIDOS` p ON l.SERIE_PEDIDO = p.SERIE_PEDIDO AND l.NUMERO_PEDIDO = p.NUMERO_PEDIDO
            LEFT JOIN `{project_id}.Analytics.CLIENTE` c ON p.NUMERO_CLIENTE = c.ID_CLIENTE
            LEFT JOIN `{project_id}.Analytics.AGENTE` g ON p.CODIGO_AGENTE = g.ID_AGENTE
            WHERE l.MARCA IS NOT NULL AND l.MARCA != ''
        """,
        "mart_pedidos_parciales": f"""
            CREATE OR REPLACE VIEW `{project_id}.Analytics.mart_pedidos_parciales` AS
            SELECT 
              l.SERIE_PEDIDO,
              l.NUMERO_PEDIDO,
              p.FECHA_CARGA,
              p.FINCA_CARGA,
              p.SECTOR_CARGA,
              c.N_COMERCIAL AS CLIENTE_COMERCIAL,
              g.NOMBRE_AGENTE AS COMERCIAL,
              l.POSICION_PEDIDO,
              l.REFERENCIA_ARTICULO,
              l.DESCRIPCION_ARTICULO,
              ar.DESCRIPCION_FAMILIA,
              ar.DESCRIPCION_SECCION,
              l.UNIDADES AS UNIDADES_SOLICITADAS,
              l.TOTAL_ACOPIADO AS UNIDADES_ACOPIADAS,
              l.UNIDADES_PENDIENTES
            FROM `{project_id}.Analytics.LINEA_PEDIDO` l
            JOIN `{project_id}.Analytics.PEDIDOS` p ON l.SERIE_PEDIDO = p.SERIE_PEDIDO AND l.NUMERO_PEDIDO = p.NUMERO_PEDIDO
            LEFT JOIN `{project_id}.Analytics.CLIENTE` c ON p.NUMERO_CLIENTE = c.ID_CLIENTE
            LEFT JOIN `{project_id}.Analytics.AGENTE` g ON p.CODIGO_AGENTE = g.ID_AGENTE
            LEFT JOIN `{project_id}.Analytics.mart_articulos_completo` ar ON l.REFERENCIA_ARTICULO = ar.ID_ARTICULO
            WHERE l.UNIDADES_PENDIENTES > 0 AND p.ESTADO_PEDIDO != 3
        """
    }

    print("Creando marts analíticos en BigQuery (dataset Analytics)...")
    for name, sql in views.items():
        print(f"  - Creando vista {name}...")
        client.query(sql).result()
    print("¡Todos los marts creados con éxito!")

if __name__ == "__main__":
    main()
