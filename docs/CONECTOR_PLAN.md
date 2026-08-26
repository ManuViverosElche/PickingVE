# Plan del Conector Factusol → BigQuery

## Resumen ejecutivo (lenguaje sencillo)

Actualmente la empresa sube datos de Factusol (el programa de gestión del vivero) a
BigQuery (la base de datos de Google donde se montan los informes) usando **22 scripts
separados**. Cada script hace lo mismo de forma repetida: abre la base de datos Factusol
(archivo `.accdb` del NAS), extrae una tabla, la pasa a un archivo CSV y la sube a BigQuery
borrando la anterior.

**Este proyecto construye un "conector" nuevo, único y profesional** que sustituye a esos
22 scripts por uno solo, configurado con un archivo de texto (YAML). Ventajas:

- **Más seguro**: protege los datos personales de los clientes (facturación, deudas, cobros).
- **Más fiable**: si algo falla a mitad, no se pierde la información; avisa de errores.
- **Más rápido**: solo sube lo que ha cambiado (no todo siempre).
- **Más amplio**: incorpora tablas que hoy no se suben (líneas de factura, cobros,
  vencimientos, familias de artículos…) para poder hacer informes de ventas por país,
  zona, agente, deudas y cobros desde cualquier dispositivo.

### Regla de oro
El dataset `GestionComercialVE` de BigQuery y las Google Sheets que mandan la faena a los
operarios **NO se tocan**. Todo lo nuevo se construye en paralelo (dataset `Analytics`) y
cualquier cambio requiere tu consentimiento explícito.

---

## Decisiones de producto (D-XX)

| Código | Decisión | Fecha |
|---|---|---|
| D-01 | El dataset `GestionComercialVE` es **producción operativa** (faena de operarios vía Apps Script). No se cambian nombres de tablas/campos ni funcionalidad. | 2026-08-04 |
| D-02 | Todo lo nuevo se construye en un **dataset `Analytics`** separado, alimentado por el conector nuevo. | 2026-08-04 |
| D-03 | El conector corre en **dual**: desarrollo en este PC y despliegue final en el PC-servidor. | 2026-08-04 |
| D-04 | Estrategia de carga: **full reload (TRUNCATE) en las 14 tablas** + **MERGE solo en LINEA_PEDIDO** (preserva `TOTAL_ACOPIADO`/`LINEA_ACTIVA`). Revisado el 2026-08-05: volúmenes pequeños (máx 33 K filas, reload completo ≈ 50 s), `WRITE_TRUNCATE` es atómico por tabla, ningún proceso externo escribe en `GestionComercialVE` (la app escribe en el dataset `pickingve`), el incremental no detecta borrados en Factusol y solo 5/14 tablas tienen marca FUM (F_CLI/F_ART/F_ALB/F_FAC/F_PCL). | 2026-08-04 |
| D-05 | Dashboards finales en **Looker Studio** (gratuito, conectado a BigQuery, acceso por rol). | 2026-08-04 |
| D-06 | Histórico: se cargan las series **`014*`** (2014–2026, operativa) y **`B14*`** (contabilidad, albaranes en B, para totales reales). | 2026-08-04 |
| D-07 | Los 22 scripts actuales quedan **congelados** en `backups/2026-08-04/` como referencia; el conector nuevo no los modifica. | 2026-08-04 |
| D-08 | El desarrollo se hace sobre **copias** de los datos; el conector nuevo solo **lee** el `.accdb` del NAS (nunca lo escribe). | 2026-08-04 |
| D-09 | El entorno local del conector usa **Python 3.11 (32-bit)** en `backend/connector/.venv`: el driver ODBC de Access solo existe en 32-bit en este PC y pandas 2.1+ dejó de publicar ruedas win32. Dependencias instaladas con `--only-binary :all:` (pandas 2.0.3). | 2026-08-04 |
| D-10 | Carga nativa a BigQuery por **JSONL** (`NEWLINE_DELIMITED_JSON` vía `load_table_from_file`) en vez de `load_table_from_dataframe`: pyarrow no publica ruedas win32 para Python 32-bit y `load_table_from_dataframe` lo requiere. Se escribe un fichero temporal UTF-8 (`force_ascii=False`, `date_format=iso`) y se sube con esquema explícito; `finalize` convierte NaN/NaT reales a `NULL`. Sin pyarrow en dependencias. | 2026-08-05 |
| D-11 | La validación (sin tocar `GestionComercialVE`) se hace contra el dataset **`conector_test`** en región **EU**: `create_dataset` no acepta `location` como kwarg, se crea con `bigquery.Dataset(ref)` + `ds.location = "EU"`. El MCP bigquery lleva `--location EU` en `opencode.json` (requiere reiniciar opencode). | 2026-08-05 |
| D-12 | **Validación 1:1 superada** comparando `conector_test` vs `GestionComercialVE` (solo lectura): CLIENTE, ALBARANES y los totales de `sumar_documento` coinciden al 100%; PEDIDOS 7/872 y LINEA_PEDIDO 125/10671 difieren solo por **drift real de Factusol** (pedidos editados, sectores re-asignados, posiciones re-numeradas con la misma huella `ID-…`). | 2026-08-05 |
| D-13 | **Fase 2 ejecutada**: el conector pasa de 14 a **21 tablas** (nuevas: SECCIONES/F_SEC, FAMILIAS/F_FAM con ID_SECCION, VENCIMIENTOS/F_REC, COBROS/F_COB, OBRAS/F_OBR direcciones descarga, LINEA_FACTURA/F_LFA, LINEA_ALBARAN/F_LAL; ARTICULOS ampliada con AUTORIZACION=CP5ART y FECHA_MODIFICACION=FUMART). Sincronizadas en `GestionComercialVE`, `Analytics` y validadas antes en `conector_test`. | 2026-08-25 |
| D-14 | Dataset nuevo **`Analytics`** creado en región EU con los marts analíticos (`mart_articulos_completo`, `mart_estado_cliente`, `mart_marcas`, `mart_pedidos_parciales`). Regla del usuario: **informes SIEMPRE con descripciones**, nunca códigos (los joins maestro se resuelven dentro de las vistas). `GestionComercialVE` sigue siendo operativa pero ya no alimenta Sheets (desaparecen); la nueva web logística puede leer de cualquiera de los dos datasets. | 2026-08-25 |
| D-15 | **Alertas de morosidad sin límite de días**: al fijarse CUALQUIER `FECHA_CARGA` futura se evalúa el estado del cliente (vencido >0 €) para dar margen de maniobra a transferencias. Canales: banner rojo en Panel Comercial `/comercial` + Telegram (`TELEGRAM_MESSAGES_BOT_TOKEN`). Panel Comercial móvil-first nuevo en `backend/web/comercial/` (NO toca manager ni truffaut), endpoints `/api/comercial/*`. | 2026-08-25 |
| D-16 | **Automatización Windows**: `run_sync.ps1` (wrapper con logs en `backend/connector/logs/`) + **instalador autocontenido `instalar_pickingve.bat`** (un solo archivo: clona repo, crea .venv, instala deps, escribe settings.local.yaml, registra tareas Task Scheduler "PickingVE-Sync-Produccion" cada 30 min 08:00–21:00 y "PickingVE-Sync-Analytics" diaria 05:00, crea acceso directo de escritorio al menú `sincronizar_menu.bat` y lanza sync de prueba). Cadencia elegida por el usuario: pedidos muy vivos (~30 min), albaranes 2-3 h (cubierto por el ciclo de 30 min), facturas/maestros diarios, noche sin sync. | 2026-08-25 |
| D-17 | **Reparto de tablas por dataset** (corrección del usuario): en `GestionComercialVE` (producción) solo lo operativo + maestros con filtro secciones —16 tablas—; las financieras y analíticas (**VENCIMIENTOS, COBROS, LINEA_FACTURA, LINEA_ALBARAN, DIRECCIONES_DESCARGA**) viven SOLO en `Analytics` mediante el flag `only_analytics` de tables.yaml (respetado por sync completo y por `--table`). `F_OBR` se nombra **DIRECCIONES_DESCARGA** (no OBRAS). Tablas mal creadas antes eliminadas. | 2026-08-25 |

---

## Arquitectura objetivo

```
Factusol .accdb (NAS: X:\Datos\FS\014{anio}.accdb, B14…)
        │  ODBC Microsoft Access Driver (*.mdb, *.accdb) — solo lectura
        ▼
┌──────────────────────────────────────────────────────┐
│  Conector Python unificado (backend/connector)       │
│  - config/tables.yaml  (mapeo declarativo)           │
│  - core/access_client.py (ODBC)                      │
│  - core/transform.py   (limpieza)                    │
│  - core/bigquery_client.py (carga nativa API)        │
│  - core/state.py       (watermarks / estado)         │
│  - core/sync.py        (orquestación + reintentos)   │
│  - scripts/sync_all.py (CLI)                         │
└──────────────────────────────────────────────────────┘
        │  BigQuery API (sin CSV intermedio)
        ▼
BigQuery: dashboard-439511 (región EU)
  ├── GestionComercialVE  ← NO SE TOCA (producción operativa)
  └── Analytics           ← NUEVO: tablas ampliadas + histórico + marts
        │
        ▼
Looker Studio: dashboards por rol (ventas, cobros, deudas, stock)
```

## Estructura del proyecto

```
backend/connector/
├── config/
│   ├── settings.yaml          # rutas NAS, dataset, regiones (plantilla, sin secretos)
│   ├── settings.local.yaml    # copia local real (gitignored)
│   ├── tables.yaml            # mapeo de tablas (actual + ampliación)
│   └── .env.example           # variables de entorno de ejemplo
├── core/
│   ├── __init__.py
│   ├── access_client.py       # conexión ODBC al .accdb
│   ├── bigquery_client.py     # carga nativa a BigQuery
│   ├── transform.py           # limpieza y tipado de datos
│   ├── state.py               # control de watermark (qué se ha subido)
│   └── sync.py                # orquestación de sincronización
├── scripts/
│   ├── sync_all.py            # CLI principal (--table, --incremental, --full)
│   └── init_historical.py     # carga histórica 0142014..2026 + B14
├── requirements.txt
└── README.md
```

## Fases

### Fase 0 — Fundación (completada)
- [x] Snapshot de seguridad del sistema actual → `backups/2026-08-04/`
- [x] Repo Git + GitHub (`origin/master`)
- [x] Estructura `backend/connector/`
- [ ] `docs/CONECTOR_PLAN.md` (este documento)

### Fase 1 — Conector core (en curso)
- [x] `config/` (settings + tables + .env.example)
- [x] `core/access_client.py` — conexión ODBC de solo lectura
- [x] `core/transform.py` + `core/bigquery_client.py` (carga JSONL, MERGE por huella)
- [x] `core/sync.py` (reintentos tenacity, logging) — sin `state.py` (no hay watermark fiable; recarga + MERGE)
- [x] `scripts/sync_all.py` — CLI (`--table`, `--dataset`, `--dry-run`, `--limit`)
- [x] **Validación**: 14 tablas cargadas en `conector_test` (región EU) y comparadas 1:1
      contra `GestionComercialVE` (solo lectura). Coincidencia total salvo drift real de
      Factusol (D-12). Scripts de validación en `backend/connector/scripts/`
      (`compare_counts.py`, `compare_values.py`, `schema_check.py`, `verify_agent.py`).
- [ ] Definir cadencia de sincronización (D-04) y despliegue en el PC-servidor (D-03)

### Fase 2 — Dataset Analytics + histórico
- Tablas maestras ampliadas y nuevas tablas de movimiento (LFA, LAL, COB, REC, OBR, FAM…)
- Carga histórica `0142014` → `0142026` + `B14`

### Fase 3 — Modelo en estrella (marts)
- `dim_*` y `fact_*` para ventas, entregas, cobros y vencimientos.

### Fase 4 — Looker Studio
- Dashboards por rol (IAM).

### Fase 5 — Automatización y monitorización
- Task Scheduler, alertas Telegram, Data Quality Scan.

### Fase 6 — Skills/MCPs
- Fix región EU del MCP BigQuery; MCP de acceso a Access para inspección en desarrollo.

---

## Seguridad (tratamos datos personales de clientes)

1. **Nunca** se commitean credenciales: `clave_json.json`, `.env`, `secrets.properties`
   están en `.gitignore`.
2. El conector **solo lee** el `.accdb`; jamás escribe sobre la base de Factusol.
3. Los datos personales (clientes, facturación) solo viajan a BigQuery con el project-id
   `dashboard-439511` de la empresa; el acceso se controla con IAM por roles.
4. El dataset `Analytics` no se hace público nunca; Looker Studio se comparte por usuario
   con permisos de solo lectura.
5. Logs: no se registran campos sensibles completos (nunca NIF completos en texto plano
   en logs de debug si puede evitarse).
6. Se revisa el código con detalle antes de tocar cualquier tabla que consume la app.
