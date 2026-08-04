# Snapshot de seguridad - 2026-08-04

## Qué es esto
Copia de seguridad del sistema de sincronización **actual** Factusol → BigQuery que vive en
Google Drive (`G:\Mi unidad\DashBoard`). Se tomó **antes** de construir el conector nuevo,
para que en cualquier momento se pueda restaurar o consultar el sistema que estaba en
producción.

## Qué contiene
| Elemento | Descripción |
|---|---|
| `scripts/` (22 .py) | Scripts ETL actuales (pyodbc → pandas → CSV → BigQuery, `WRITE_TRUNCATE`) |
| `cargar_datos.py` | Orquestador: ejecuta todos los scripts secuencialmente |
| `ejecutar_scripts.bat` / `actualizar_pedidos.bat` | Lanzadores con credenciales GCP |
| `Apps Script/` (6 .gs) | Dashboards y faena de operarios en Google Sheets |
| `INVENTARIO_CSV.txt` | Listado de los CSV intermedios con nº de líneas |

## Qué NO contiene (a propósito, seguridad)
- **No** se copiaron las claves JSON de service account (`clave_json.json`, `clave_stock.json`).
  Siguen solo en Google Drive.
- **No** se copiaron las bases de datos `.accdb` (quedan en el NAS, `X:\Datos\FS\`).
- Los scripts solo referencian la ruta de la credencial (`V:\DashBoard\clave_json.json`),
  nunca su contenido.

## Credenciales / rutas que asumían los scripts viejos
- BD Factusol: `X:\Datos\FS\014{anio}.accdb` (NAS)
- Credencial GCP: `V:\DashBoard\clave_json.json` (Google Drive montado como unidad V:)
- CSV intermedios: `V:\DashBoard\csv\*.csv`

## Series históricas detectadas en el NAS
- `014*` (2014–2026): base operativa principal (pedidos, clientes, artículos…)
- `B14*` (2026): contabilidad — solo albaranes en "B" (para totales reales)
- `001*`, `015*`, `XD1*`, backups: no entran en el histórico por ahora
