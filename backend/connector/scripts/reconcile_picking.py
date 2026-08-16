import os
import sys
from pathlib import Path

# Cargar .env si existe
env_path = Path(__file__).resolve().parent.parent.parent.parent / '.env'
if env_path.exists():
    for line in env_path.read_text(encoding='utf-8').splitlines():
        if '=' in line and not line.startswith('#'):
            k, v = line.split('=', 1)
            os.environ[k.strip()] = v.strip()

from google.cloud import bigquery
import uuid
from datetime import datetime, timezone

PROJECT = os.getenv("GCP_PROJECT", "dashboard-439511")
DATASET = "GestionComercialVE"
PICKING_DATASET = "pickingve"
PICKING_TABLE = "picking_registros"

client = bigquery.Client(project=PROJECT, location="EU")

def audit_picking():
    print("=== AUDITORÍA DE RECONCILIACIÓN DE PICKING (BigQuery EU) ===")
    
    # 1. Agrupar acopiado en picking_registros por order_id y order_line_id
    query_reg = f"""
        SELECT order_id, order_line_id, SUM(cantidad_partida) AS total_acopiado_reg
        FROM `{PROJECT}.{PICKING_DATASET}.{PICKING_TABLE}`
        WHERE order_line_id IS NOT NULL AND order_line_id != ''
        GROUP BY order_id, order_line_id
    """
    print("Ejecutando consulta de registros de picking...")
    reg_rows = { (row["order_id"], row["order_line_id"]): row["total_acopiado_reg"] for row in client.query(query_reg).result() }
    
    # 2. Consultar LINEA_PEDIDO de GestionComercialVE
    query_lp = f"""
        SELECT CONCAT(SERIE_PEDIDO, '-', NUMERO_PEDIDO) AS order_id, 
               HUELLA_DIGITAL AS order_line_id, 
               TOTAL_ACOPIADO AS total_acopiado_erp,
               UNIDADES_PENDIENTES
        FROM `{PROJECT}.{DATASET}.LINEA_PEDIDO`
        WHERE LINEA_ACTIVA = TRUE
    """
    print("Ejecutando consulta de LINEA_PEDIDO...")
    lp_rows = client.query(query_lp).result()
    
    discrepancies = 0
    for lp in lp_rows:
        oid = lp["order_id"]
        lid = lp["order_line_id"]
        erp_val = lp["total_acopiado_erp"] or 0.0
        reg_val = reg_rows.get((oid, lid), 0.0)
        
        if abs(erp_val - reg_val) > 0.001:
            discrepancies += 1
            print(f"[DISCREPANCIA] Pedido {oid}, Línea {lid}: ERP Total Acopiado = {erp_val}, Picking Registros SUM = {reg_val}")
            
    print(f"\nAuditoría finalizada. Total de discrepancias encontradas: {discrepancies}")

if __name__ == "__main__":
    audit_picking()
