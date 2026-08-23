# Integración con Google BigQuery — Guía Paso a Paso

## Objetivo
Que la app Android de picking (offline-first) lea el catálogo y los pedidos desde la **base real** del negocio y devuelva los registros de picking. En esta base ya viven los pedidos que se mandan a los operarios.

**Base real (verificada):**
- Proyecto GCP: `dashboard-439511`
- Dataset: `GestionComercialVE`
- Conexión de desarrollo: MCP local `@ergut/mcp-bigquery-server` (ADC de gcloud) en `opencode.json`.

---

## Mapa tabla real → modelo de la app

| BigQuery (GestionComercialVE) | App Android (Room) | Uso |
|---|---|---|
| `PEDIDOS` | `OrderEntity` | Pedidos. Clave `SERIE_PEDIDO` + `NUMERO_PEDIDO` |
| `LINEA_PEDIDO` | `OrderLineEntity` | Partidas del pedido. `POSICION_PEDIDO` ordena; `UNIDADES`/`UNIDADES_PENDIENTES` |
| `ARTICULOS` | `ProductEntity` | Catálogo: `ID_ARTICULO`, `DESCRIPCION_ARTICULO`, `CODIGO_EAN`, `FINCA_ARTICULO`, `DESCATALOGADO` |
| `CODIGOS_EAN` | `ProductEntity.eans` | EAN real por combinación `REFERENCIA_ARTICULO` + `CODIGO_LITRAJE` + `CODIGO_SECTOR` |
| `LITRAJES` | — | `ID_LITRAJE` → `DESCRIPCION_LITRAJE` (formato/litraje) |

---

## Consultas reales (para el backend REST)

### Catálogo de artículos + EANs
```sql
SELECT
  a.ID_ARTICULO,
  a.DESCRIPCION_ARTICULO,
  a.FINCA_ARTICULO,
  COALESCE(a.CODIGO_EAN, '') AS EAN_DEFAULT
FROM `dashboard-439511.GestionComercialVE.ARTICULOS` a
WHERE a.DESCATALOGADO = 0;
```

### Tabla EAN → litraje/sector (para resolver escaneo)
```sql
SELECT REFERENCIA_ARTICULO, CODIGO_EAN, CODIGO_LITRAJE, CODIGO_SECTOR
FROM `dashboard-439511.GestionComercialVE.CODIGOS_EAN`
WHERE CODIGO_EAN IS NOT NULL;
```

### Pedidos por fecha de carga y finca (faena del día)
```sql
SELECT SERIE_PEDIDO, NUMERO_PEDIDO, NUMERO_CLIENTE, ESTADO_PEDIDO,
       FECHA_CARGA, SECTOR_CARGA, FINCA_CARGA
FROM `dashboard-439511.GestionComercialVE.PEDIDOS`
WHERE FECHA_CARGA = DATE(...)           -- día seleccionado
  AND FINCA_CARGA = @finca             -- finca del operario (BORISA, LA FÁBRICA…)
ORDER BY NUMERO_PEDIDO;
```

### Partidas de un pedido (con tags logísticos)
```sql
SELECT HUELLA_DIGITAL, SERIE_PEDIDO, NUMERO_PEDIDO, POSICION_PEDIDO,
       REFERENCIA_ARTICULO, DESCRIPCION_ARTICULO, UNIDADES, UNIDADES_PENDIENTES,
       CODIGO_LITRAJE, CODIGO_SECTOR, MARCA, FINCA_RELEVADA, SECTOR_RELEVADO,
       UBICACION_EXTRA, PRIORIDAD, ACCION_LOGISTICA, LINEA_ACTIVA
FROM `dashboard-439511.GestionComercialVE.LINEA_PEDIDO`
WHERE SERIE_PEDIDO = @serie AND NUMERO_PEDIDO = @numero
  AND LINEA_ACTIVA = TRUE
ORDER BY POSICION_PEDIDO;
```

> Nota: `SERIE_PEDIDO` es `STRING` (los pedidos tipo picking usan la serie `1`). `LINEA_PEDIDO.HUELLA_DIGITAL` es la PK (`REQUIRED`). `CODIGOS_EAN` permite que una referencia tenga varios EAN según litraje/sector.

---

## Arquitectura (Opción A — RECOMENDADA)

```
Android app ──HTTPS──▶ API REST (Cloud Run) ──SDK BigQuery──▶ BigQuery (dashboard-439511)
```

- La service account de GCP vive solo en el backend; nunca en el APK.
- Endpoints reales del backend (ver `backend/main.py`): `GET /api/pedidos`, `GET /api/catalogo` (artículos+EAN+litrajes+sectores), `GET /api/catalogo/version`, `POST /api/picking/upload`, `POST /api/picking/compensar`, entre otros.

## Paso de conexión de la app
1. Cambiar en `app/src/main/java/com/vivero/pickingve/util/Constants.kt`:
   ```kotlin
   const val REST_BASE_URL = "https://tu-backend.run.app/api"
   ```
2. La app descarga catálogo/partidas a Room cuando hay red (WorkManager) y sube `picking_registros` al terminar la sesión.

## Documentación oficial
- BigQuery API: https://cloud.google.com/bigquery/docs/reference/libraries
- Cloud Run: https://cloud.google.com/run/docs/quickstarts/build-and-deploy
- Guía de credenciales: https://cloud.google.com/docs/authentication/production