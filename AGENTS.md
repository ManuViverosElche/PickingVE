# AGENTS.md - PickingVE

## Proyecto
App Android (Kotlin, Jetpack Compose) de picking offline-first para viveros/campo.
- Compila con: `gradlew.bat assembleDebug` (Gradle 8.7, JDK del Android Studio en `C:\Program Files\Android\Android Studio\jbr`).
- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Documentación: `docs/` (SPECS, ARCHITECTURE, DATA_MODEL, BIGQUERY_STEPS, GOVERNANCE).

## Reglas
- **Offline-first**: la UI solo lee Room DB; la red (BigQuery REST, Telegram) es asíncrona.
- El reporte se genera como **.xlsx** (no CSV) replicando `Documentacion/picking_260833_I.xlsx` (XlsxReportGenerator). El CSV solo existe para exportar etiquetas pendientes.
- Secretos: `secrets.properties` (gitignored) → BuildConfig. Nunca tokens en código.
- BigQuery: **todo el proyecto está en la región EU** (`GestionComercialVE`, `pickingve`, `conector_test`). MCP local `@ergut/mcp-bigquery-server` en `opencode.json` con `--location EU` (proyecto: `dashboard-439511`). No usar datasets en US.
- No añadir comentarios salvo que se pidan. Kotlin oficial, compose/material3.
- **Documentación de decisiones**: toda decisión de producto discutida con el usuario se registra en `docs/SPECS.md` como `D-XX` (o en el archivo correspondiente de `docs/`). No implementar cambios de comportamiento sin dejar constancia de la decisión.

## Comandos útiles
- Build: `gradlew.bat assembleDebug` con JAVA_HOME del JBR de Android Studio.
- Instalar en dispositivo/emulador: `adb install -r app-debug.apk`
