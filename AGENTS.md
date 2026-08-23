# AGENTS.md - PickingVE

## Proyecto
App Android (Kotlin, Jetpack Compose) de picking offline-first para viveros/campo.
- Compila con: `gradlew.bat assembleDebug` (Gradle 8.7, JDK del Android Studio en `C:\Program Files\Android\Android Studio\jbr`).
- APK: `apks/PickingVE-debug-<versionName>.apk` (carpeta fija `apks/` en la raíz; el build la copia automáticamente cada vez). Usar SIEMPRE el más reciente de la carpeta, no fijar versión en la documentación (evita referencias obsoletas).
- Documentación: `docs/` (SPECS, ARCHITECTURE, DATA_MODEL, BIGQUERY_STEPS, GOVERNANCE).

## Reglas
- **Offline-first**: la UI solo lee Room DB; la red (BigQuery REST, Telegram) es asíncrona.
- El reporte se genera como **.xlsx** (no CSV) replicando `Documentacion/picking_260833_I.xlsx` (XlsxReportGenerator). El CSV solo existe para exportar etiquetas pendientes.
- Secretos: `secrets.properties` (gitignored) → BuildConfig. Nunca tokens en código.
- BigQuery: **todo el proyecto está en la región EU** (`GestionComercialVE`, `pickingve`, `conector_test`). MCP local `@ergut/mcp-bigquery-server` en `opencode.json` con `--location EU` (proyecto: `dashboard-439511`). No usar datasets en US. **Ojo**: el MCP devuelve un esquema de `LINEA_PEDIDO` que NO es el de producción — verificar siempre con `bq` CLI (columnas reales: `FINCA_RELEVADA`, `SECTOR_RELEVADO`, `UBICACION_EXTRA`, `LINEA_ACTIVA`).
- Backend: `backend/main.py` en Cloud Run (proyecto `dashboard-439511` = `pickingve-api-938422468946`). NO usar la URL `pickingve-api-347521903849` (proyecto de la app, sin permiso).
- Informe Truffaut publicado (D-90): `https://pickingve-api-938422468946.europe-west1.run.app/truffaut?k=truffaut-otono-2026` — actualizar con `powershell -File reportes\truffaut\publicar.ps1` (un solo comando; detalles en SPECS.md D-90).
- No añadir comentarios salvo que se pidan. Kotlin oficial, compose/material3.
- **Documentación de decisiones**: toda decisión de producto discutida con el usuario se registra en `docs/SPECS.md` como `D-XX` (o en el archivo correspondiente de `docs/`). No implementar cambios de comportamiento sin dejar constancia de la decisión.
- **El usuario manda (D-117)**: si una pregunta queda sin respuesta clara, PARAR y no tocar nada relacionado. Nunca desplegar ni editar código sin orden explícita.
- **Truffaut tiene agente dedicado**: los cambios en `reportes/truffaut/`, `backend/web/truffaut/` y todo lo relacionado con `TRUFFAUT_*` en `backend/main.py` los hace el agente de Truffaut. Los demás agentes NO los tocan, no los "arreglan" ni despliegan el backend por ellos (el deploy con `--source backend` publica TODO el WIP sin commitear, incluido el de otros agentes).
- **Ejecución de backend (Windows)**: PROHIBIDO rutas relativas como `.venv\Scripts\python.exe` — usar SIEMPRE la ruta absoluta `C:\Users\Usuario\Documents\Manu\Proyectos\PickingVE\backend\.venv\Scripts\python.exe`. PROHIBIDO esperar el arranque con `Start-Sleep` — usar bucle `while` de polling al puerto (timeout 30 s). Detalle completo en `.opencode/instructions.md`.
- **Siempre desplegado para probar (D-156)**: el usuario prueba SIEMPRE en producción, no en local. Todo cambio en `backend/` o `backend/web/` debe desplegarse en Cloud Run (`powershell -File reportes\manager\publicar.ps1`) al terminar, y la verificación final se hace contra la URL de producción. Dejar solo la versión local sin desplegar NO es válido.

## Comandos útiles
- Build: `gradlew.bat assembleDebug` con JAVA_HOME del JBR de Android Studio.
- Instalar en dispositivo/emulador: `adb install -r apks\PickingVE-debug-<versionName>.apk` (usar el APK más reciente de `apks/`).
