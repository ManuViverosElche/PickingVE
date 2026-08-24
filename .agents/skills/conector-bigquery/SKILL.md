---
name: conector-bigquery
description: Skill para sincronizar y verificar datos Access→BigQuery con backend/connector. Orquesta los scripts de sync/auditoría y obliga a verificar SIEMPRE el esquema real con bq CLI (el MCP de BigQuery devuelve esquema obsoleto). Activar con frases como "sincroniza", "ejecuta el conector", "reconcilia", "los datos no cuadran", "comprueba BigQuery/Access", "los conteos no dan".
---

# Skill: Conector Access → BigQuery

## Objetivo
Ejecutar y verificar la sincronización de datos entre Access (vía Drive) y BigQuery SIN palos de ciego: cada sync se cierra con comprobación de conteos y esquema contra la realidad, no contra lo que diga el MCP.

## Datos fijos
- Proyecto GCP: `dashboard-439511`. TODOS los datasets están en región **EU** (`GestionComercialVE`, `pickingve`, `conector_test`). Nunca usar datasets en US.
- Código: `backend/connector/` — core (`access_client.py`, `bigquery_client.py`, `sync.py`, `transform.py`), config (`settings.yaml`, `tables.yaml`) y scripts en `backend/connector/scripts/`.
- Python del conector: preferir `backend\connector\.venv\Scripts\python.exe`; si no existe, `backend\.venv\Scripts\python.exe`. SIEMPRE ruta absoluta.
- **El MCP bigquery devuelve un esquema de LINEA_PEDIDO que NO es el de producción.** Columnas reales verificadas: `FINCA_RELEVADA`, `SECTOR_RELEVADO`, `UBICACION_EXTRA`, `LINEA_ACTIVA`.

## Pasos obligatorios

### 1. Estado actual ANTES de tocar nada
- Esquema real: `bq show --schema --project_id=dashboard-439511 <dataset>.<tabla>` (bq CLI 2.1.33 instalado). NUNCA fiarse del esquema que lista el MCP.
- Conteos actuales: `bq query --location=EU --use_legacy_sql=false --project_id=dashboard-439511 "SELECT COUNT(*) FROM \`dataset.tabla\`"`.

### 2. Ejecutar sync/auditoría
- Script principal: `scripts/sync_all.py`. Auditorías: `compare_counts.py` (conteos Access vs BQ), `reconcile_picking.py`, `schema_check.py`, `compare_values.py`.
- Si un script pide credenciales/env, mirar primero `connector/config/.env.example` y pedir al usuario lo que falte; nunca inventar rutas de service accounts.
- Ejecutar UN script cada vez y leer su salida completa antes del siguiente.

### 3. Verificación posterior (obligatoria)
- Repetir conteos BQ y comparar con Access: una sync no está OK hasta que `compare_counts.py` cuadre o las diferencias estén explicadas.
- Spot-check de columnas sensibles (`FINCA_RELEVADA`, `SECTOR_RELEVADO`, `LINEA_ACTIVA`) con una query de muestra.
- Si algo difiere: NO re-sincronizar a ciegas; diagnosticar con `lp_mismatch_detail.py` o logs del script.

### 4. Reglas de oro
- Todo en EU. Toda verificación de esquema por bq CLI.
- No borrar/trunear tablas sin orden explícita del usuario (los scripts de push pueden ser destructivos).
- Registrar en el informe final: qué script, qué tablas tocadas, diferencias antes/después.
