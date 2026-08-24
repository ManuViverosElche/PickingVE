---
name: auditoria-backend
description: Skill para auditar el backend FastAPI de PickingVE (backend/main.py, informe_*.py) buscando fallos de seguridad, errores sin traducir, endpoints rotos y deuda técnica. Respeta la frontera del agente Truffaut. Activar con frases como "audita el backend", "revisa main.py", "seguridad del servidor", "mira los endpoints", "el backend falla", "revisa el API".
---

# Skill: Auditoría del Backend FastAPI (PickingVE)

## Objetivo
Auditar `backend/` desde la perspectiva de "qué puede romperse en producción": claves expuestas, excepciones crudas al cliente, queries BigQuery costosas o mal ubicadas, y endpoints muertos o duplicados. El backend es un monolito (~300 KB, ~199 funciones): mapear antes de tocar.

## Fronteras (obligatorias)
- NUNCA modificar ni desplegar: `reportes/truffaut/**`, `backend/web/truffaut/**`, lógica `TRUFFAUT_*` dentro de `main.py`. Esos hallazgos se LISTAN en el informe para su agente dedicado.
- Los cambios que se corrijan sobre la marcha deben desplegarse con la skill `despliegue-backend` (D-156).

## Pasos de Verificación Obligatorios

### 1. Mapa del monolito
- Extraer inventario de endpoints: rutas (`@app.get/post/...`), sus claves `k=` y módulos (punteo, truffaut, manager, connector...).
- Detectar duplicados, endpoints sin usar referenciados desde `backend/web/**` o la app Android (`data/remote/PickingApiClient.kt`), y endpoints huérfanos.

### 2. Seguridad (crítico)
- Claves de acceso tipo query param (`?k=truffaut-otono-2026`): listar todas las que existen en código, comprobar que no hay claves débiles/adivinables nuevas y que ninguna clave real aparece hardcodeada fuera de los sitios ya aprobados.
- Secretos: nada de tokens/API keys literales en `main.py`, `informe_*.py`, logs o respuestas JSON. Variables solo por entorno Cloud Run.
- CORS y cabeceras: exponer solo lo necesario.
- Validación de entrada: parámetros numéricos/de texto saneados antes de llegar a BigQuery SQL (riesgo de inyección en f-strings).

### 3. Errores y robustez
- Endpoints que devuelven tracebacks crudos (500 con detalle interno) → traducir a mensajes controlados.
- Timeouts y reintentos en llamadas salientes (BigQuery, Drive).
- Comprobar que toda query BigQuery usa la región correcta (todo el proyecto está en EU; nunca datasets en US).

### 4. Rendimiento y datos
- Queries sin límite de filas/fecha, SELECT * innecesarios, bucles N+1 contra BigQuery.
- Caché donde proceda (informes repetidos).

### 5. Verificación
- Tras cada corrección: prueba local con la venv absoluta + polling al puerto (reglas de `.opencode/instructions.md`) y luego deploy + verificación con la skill `despliegue-backend`.
- Nunca declarar un endpoint "arreglado" sin haberlo llamado con curl/Invoke-WebRequest y visto la respuesta real.

## Formato del Informe Final
- 🔴 **Críticos** (seguridad, datos corruptos, caídas) — con archivo:línea
- 🟡 **Deuda técnica** (monolito, duplicación, sin tests)
- 🟢 **Puntos fuertes / Estado general**
- 🚪 **Tabla de pruebas**: endpoint → escenario → resultado
- 📋 **Plan de acción** (separando lo que toca al agente Truffaut)
