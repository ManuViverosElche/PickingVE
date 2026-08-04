# AGENTS.md - PickingVE

## Proyecto
App Android (Kotlin, Jetpack Compose) de picking offline-first para viveros/campo.
- Compila con: `gradlew.bat assembleDebug` (Gradle 8.7, JDK del Android Studio en `C:\Program Files\Android\Android Studio\jbr`).
- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Documentación: `docs/` (SPECS, ARCHITECTURE, DATA_MODEL, BIGQUERY_STEPS, GOVERNANCE).

## Reglas
- **Offline-first**: la UI solo lee Room DB; la red (BigQuery REST, Telegram) es asíncrona.
- El reporte se genera como **.xlsx** (no CSV) replicando `Documentacion/picking_260833_I.xlsx` (XlsxReportGenerator).
- Secretos: `secrets.properties` (gitignored) → BuildConfig. Nunca tokens en código.
- BigQuery: MCP local `@ergut/mcp-bigquery-server` en `opencode.json` (proyecto: `dashboard-439511`, dataset `GestionComercialVE`).
- No añadir comentarios salvo que se pidan. Kotlin oficial, compose/material3.

## Comandos útiles
- Build: `gradlew.bat assembleDebug` con JAVA_HOME del JBR de Android Studio.
- Instalar en dispositivo/emulador: `adb install -r app-debug.apk`
