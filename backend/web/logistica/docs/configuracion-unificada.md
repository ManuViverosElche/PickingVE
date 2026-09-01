# Unificación de Configuración — Portal /logistica

**Decisión:** D-265 (Portal Logístico unificado /logistica).

Este documento registra qué configuraciones existían en los paneles beta
`/manager` y `/inventario`, qué duplicidades se eliminaron en el portal
unificado y dónde quedó cada ajuste.

---

## Duplicidades eliminadas

### 1. "Operarios" (Acopio) y "Operarios (Inventario)" en /manager

- **Original:** `/manager` muestra 3 tablas separadas en Configuración:
  - `Encargados` (fetch `/api/encargados`)
  - `Operarios (Acopio)` (fetch `/api/operarios`, todos los modos)
  - `Operarios (Inventario)` (misma lista `/api/operarios` filtrada por
    `modo = INVENTARIO | AMBAS`)
- **Duplicidad detectada:** La tabla "Operarios (Inventario)" era un **filtro
  sobre la misma fuente** que "Operarios (Acopio)" (mismo endpoint, mismos
  campos de creación/edición con `openPersonModal`).
- **Solución en /logistica:** Se mantiene UNA sola fuente de datos
  (`fetchOperarios()`) y **una sola pantalla de Personal y operarios**
  (`configuracion/personal.js`) con tres vistas por filtro:
  - Encargados → tabla (fetch `/api/encargados`)
  - Operarios (Acopio) → tabla (fetch `/api/operarios`)
  - Operarios (Inventario) → tabla (fetch `/api/operarios` **filtrada por modo**)
- El alta/edición usa el mismo modal profesional (`openPersonModal`) con el
  selector de modo único: `ACOPIO | INVENTARIO | AMBAS` (para operarios) o
  `PICKING | INVENTARIO | AMBAS` (para encargados). **No hay dos formularios
  para el mismo criterio.**

### 2. "Fincas" en /manager y "Fincas (propias)" en /inventario

- **Original:**
  - `/manager` → Configuración → `Fincas` (fetch `/api/fincas/gestion`;
    alta/edición/ocultar vía modal; incluye fincas ajenas y propias).
  - `/inventario` → Configuración → `Fincas (propias)` (fetch
    `/api/inventario/fincas/config`; checkbox "Inventariar" sobre las propias).
- **Duplicidad parcial:** Ambas gestionan el estado de fincas, pero con
  **criterios distintos** (visibilidad de carga vs inventariabilidad).
- **Solución en /logistica:**
  - `configuracion/logistica.js` → **Fincas de logística** (visibilidad,
    tipo manual/auto, propia/ajena) — comportamiento completo de /manager.
  - `configuracion/inventario.js` → **Fincas (propias) inventariables** con
    checkbox — comportamiento completo de /inventario.
- **No se duplica:** la gestión de visibilidad vive solo en
  `config/logistica`; la de inventariabilidad solo en `config/inventario`.

### 3. "Operarios Autorizados para Inventario" vs "Operarios de Inventario"

- **Original:**
  - `/manager` → `Operarios (Inventario)` (lista editable de `/api/operarios`
    con modo inventario).
  - `/inventario` → `Operarios Autorizados para Inventario` (lista de
    `/api/inventario/operarios`, solo lectura).
- **Duplicidad:** ambas mostraban el mismo colectivo (operarios con modo
  inventario).
- **Solución en /logistica:** Se conservan ambas vistas porque cada una
  pertenece a su submenú funcional (Personal / Configuración de inventario),
  pero **la fuente es la misma** y el alta/edición solo existe en Personal
  (`config/personal`); la pantalla de inventario queda como lista de
  consulta/read-only igual que `/inventario`.

### 4. "Faena a Inventariar (por finca y sector)" — sin duplicidad

- **Original:** solo existía en `/inventario` (Configuración). No tenía
  equivalente en `/manager`.
- **Solución en /logistica:** se conserva íntegra en
  `config/inventario` → `Faena a Inventariar`, con `guardarFaenaGlobal()`
  (POST `/api/inventario/faena/bulk`).

### 5. Maquinaria y Familias — sin duplicidad

- **Original:** solo en `/manager` (Configuración).
- **Solución en /logistica:** `config/logistica` → `Maquinaria` y
  `Familias de Maquinaria`, con CRUD completo (alta/edición/eliminar).

---

## Tabla final: dónde quedó cada ajuste

| Criterio configurado | /manager | /inventario | Portal /logistica |
|---|---|---|---|
| Alta/edición encargados | `openPersonModal('encargado')` | — | `config/personal` → Encargados |
| Alta/edición operarios acopio | `openPersonModal('operario')` | — | `config/personal` → Operarios (Acopio) |
| Alta/edición operarios inventario | `openPersonModal('operarioInv')` | — | `config/personal` → Operarios (Inventario) |
| Fincas visibilidad/tipo | `openFincaModal` | — | `config/logistica` → Fincas |
| Maquinaria | `openMaquinariaModal` | — | `config/logistica` → Maquinaria |
| Familias maquinaria | `openFamiliaModal` | — | `config/logistica` → Familias |
| Fincas inventariables | — | checkbox `chkFincaInv` | `config/inventario` → Fincas (propias) |
| Faena a inventariar | — | checklists `chkFaena` | `config/inventario` → Faena a Inventariar |
| Operarios autorizados inventario | — | lista read-only | `config/inventario` → Operarios autorizados |

---

## Notas de conservación

- Los paneles `/manager` y `/inventario` **no se han modificado** (solo lectura
  durante la migración; AGENTS.md los marca como WIP ajeno).
- La única modificación fuera de `/logistica` fue ampliar
  `_verify_inventario_key` en `backend/main.py` para aceptar el token del
  portal unificado `logistica-2026` (autorización; no cambia la lógica de
  negocio).
- No se han simplificado funciones: cada submenú del portal replica el
  comportamiento completo del panel original que sustituye.