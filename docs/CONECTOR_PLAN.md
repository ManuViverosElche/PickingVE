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
| D-04 | Estrategia de carga **por tabla**: las tablas de movimiento (pedidos, líneas, albaranes, stock…) en incremental; las maestras (clientes, artículos…) en carga completa. La cadencia se decide por script/tabla. | 2026-08-04 |
| D-05 | Dashboards finales en **Looker Studio** (gratuito, conectado a BigQuery, acceso por rol). | 2026-08-04 |
| D-06 | Histórico: se cargan las series **`014*`** (2014–2026, operativa) y **`B14*`** (contabilidad, albaranes en B, para totales reales). | 2026-08-04 |
| D-07 | Los 22 scripts actuales quedan **congelados** en `backups/2026-08-04/` como referencia; el conector nuevo no los modifica. | 2026-08-04 |
| D-08 | El desarrollo se hace sobre **copias** de los datos; el conector nuevo solo **lee** el `.accdb` del NAS (nunca lo escribe). | 2026-08-04 |

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
- [ ] `config/` (settings + tables + .env.example)
- [ ] `core/access_client.py` — conexión ODBC de solo lectura
- [ ] `core/transform.py` + `core/bigquery_client.py`
- [ ] `core/state.py` + `core/sync.py` (watermarks, reintentos, logging)
- [ ] `scripts/sync_all.py` — CLI
- [ ] **Validación**: cargar a un dataset de pruebas con el mismo esquema que el actual
      y comparar contra los CSV existentes. Nada se escribe en `GestionComercialVE`.

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
