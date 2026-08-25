# AGENTS.md - PickingVE

## Router de Skills (activación automática)
Antes de empezar CUALQUIER tarea, comprueba si el pedido del usuario encaja aquí e invoca la skill correspondiente (herramienta `skill`). Si dudas entre dos, carga AMBAS. El usuario no nombrará las skills: tú decides por él.

| Si el usuario pide algo como... | Skill |
|---|---|
| "audita el código", "revisa calidad", "comprueba fugas/seguridad Android" | `auditoria-android` |
| "prueba la app como usuario", "mira si va fluida", "comprueba mensajes/errores" | `auditoria-experiencia-usuario` |
| "despliega", "publica", "súbelo al servidor", "pásalo a producción" | `despliegue-backend` |
| "revisa el backend", "main.py", "seguridad del servidor", "endpoints" | `auditoria-backend` |
| "sincroniza", "reconcilia", "los datos no cuadran", "BigQuery/Access" | `conector-bigquery` |
| "instala/prueba el APK en el móvil", "haz un smoke test real" | `prueba-dispositivo-apk` |
| "valida lo desarrollado", "revisa el turno nocturno", "qué hizo el agente" | `validacion-turno` |
| "diseña una pantalla", "mejora la UI", "estilo Material" | `mobile-android-design` |

## Turno nocturno (agente en marcha)
- **Marca de partida**: commit `2b7b416` (hotfix auditoría UX, D-193/D-194). Todo lo posterior a esa marca es del turno.
- Reglas del turno: commits **por ficheros propios** (PROHIBIDO `git add -A` — ya mezcló WIP ajeno en v2.2.6); NO tocar `backend/web/truffaut/**`, `reportes/truffaut/**`, lógica `TRUFFAUT_*` ni `backend/web/manager/index.html` (WIP ajeno); decisiones → D-XX en `docs/SPECS.md`; app → build+tests verdes antes de cerrar; backend → deploy + verificación con `despliegue-backend` (D-156).
- **Si el turno falla o se queda a medias**: NO bloquearse. El WIP queda visible con `git status --porcelain`; quien retome por la mañana ejecuta la skill `validacion-turno` (detecta WIP, valida contra `2b7b416`, y propone completar/revertir). Revertir = `git revert <commit>`, nunca borrar a mano. Ningún fallo de un agente bloquea al siguiente: el repo es la fuente de verdad.

Regla general de calidad: tras cambios significativos en la app → `auditoria-experiencia-usuario`; tras cambios en `backend/` → deploy + verificación con `despliegue-backend` (D-156).

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
