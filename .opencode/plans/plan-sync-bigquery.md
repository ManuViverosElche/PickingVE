# Plan Modular: Sincronización Backend y BigQuery (`plan-sync-bigquery.md`)

## Objetivo
Gestionar los flujos de sincronización de datos entre las fuentes locales (Access) y Google BigQuery, asegurando el cumplimiento de la región EU y la integridad del esquema real.

## Módulos y Ficheros Clave
- `backend/connector/`: Scripts de sincronización, extracción y carga de datos.
- `backend/main.py`: Endpoints de API que interactúan con BigQuery.
- Documentación de referencia en `docs/BIGQUERY_STEPS.md`.

---

## Fases de Ejecución

### Fase 1: Verificación de Entorno y Región
1. Asegurar que todas las consultas y operaciones en BigQuery apuntan estrictamente a la **región EU** (proyectos `dashboard-439511`, datasets `GestionComercialVE`, `pickingve`, `conector_test`).
2. Utilizar el MCP local `@ergut/mcp-bigquery-server` configurado en `opencode.json` con `--location EU`.

### Fase 2: Validación de Esquema Real con `bq` CLI
1. **Regla crítica:** El MCP puede devolver esquemas de `LINEA_PEDIDO` desactualizados. Verificar siempre las columnas reales mediante `bq` CLI (`FINCA_RELEVADA`, `SECTOR_RELEVADO`, `UBICACION_EXTRA`, `LINEA_ACTIVA`).
2. Invocar la skill `conector-bigquery` ante cualquier discrepancia de conteos o reconciliación de datos.

### Fase 3: Ejecución y Pruebas de Sincronización
1. Ejecutar scripts de conector en `backend/connector/` con rutas absolutas de Python (`C:\Users\Usuario\Documents\Manu\Proyectos\PickingVE\backend\.venv\Scripts\python.exe`).
2. Monitorear logs de ejecución y asegurar consistencia transaccional.
