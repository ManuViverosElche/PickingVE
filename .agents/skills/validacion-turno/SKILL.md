---
name: validacion-turno
description: Skill para validar el trabajo desarrollado por otro agente durante un turno (noche/ausencia). Compara contra el commit marca, ejecuta build+tests, prueba el APK en emulador si tocó la app, verifica producción si tocó el backend, y comprueba que registró las decisiones en SPECS.md. Activar con frases como "valida lo de esta noche", "revisa el turno", "qué hizo el agente", "valida lo desarrollado".
---

# Skill: Validación de Turno (trabajo de otro agente)

## Objetivo
Al despertar/retomar el proyecto, validar CON EVIDENCIA lo desarrollado durante el turno anterior: qué cambió, si compila, si funciona de verdad y si quedó documentado. Nada se da por bueno sin verificación real.

## Datos de contexto
- Commit marca de partida (antes del turno): ver sección "Turno nocturno" de `AGENTS.md` (se actualiza cada vez que arranca un turno). Si no está, usar el commit más antiguo que el usuario indique.
- Proyecto: `dashboard-439511`, backend `pickingve-api` (europe-west1), URL producción `https://pickingve-api-938422468946.europe-west1.run.app`.

## Pasos obligatorios (en orden)

### 1. Inventario de cambios
- `git log --oneline <marca>..HEAD` → commits del turno.
- `git diff <marca>..HEAD --stat` → ficheros tocados.
- `git status --porcelain` → WIP sin commitear (avisar si el agente dejó cosas a medias).
- Clasificar cada fichero: app / backend / panel web / truffaut / docs.
- **Frontera Truffaut**: si tocó `reportes/truffaut/**`, `backend/web/truffaut/**` o `TRUFFAUT_*` en main.py sin ser el agente de Truffaut → 🔴 alerta inmediata al usuario.

### 2. Verificación de build (bloqueante)
- App: `gradlew.bat compileDebugKotlin testDebugUnitTest lint --console=plain` (JAVA_HOME del JBR) → BUILD SUCCESSFUL obligatorio.
- Backend: `python -m py_compile backend/main.py` con la ruta absoluta de la venv (`.opencode/instructions.md`).
- Si falla: 🔴 reportar con archivo:línea; corregir solo lo trivial y obvio.

### 3. Decisiones documentadas (SPECS)
- Buscar en `docs/SPECS.md` (y `backend/docs/SPECS.md` si tocó backend) los D-XX del turno.
- Todo cambio de COMPORTAMIENTO debe tener su D-XX. Los que falten → 🟡 listado para que el usuario decida si se aceptan o se revierten.

### 4. Prueba real según ámbito
- **Si tocó la app**: `assembleDebug`, instalar el APK más reciente de `apks/` en emulador/dispositivo (skill `prueba-dispositivo-apk`), recorrer las pantallas que el agente haya modificado con screenshots verificados visualmente, y smoke test offline (modo avión) de lo crítico.
- **Si tocó el backend**: verificar contra PRODUCCIÓN los endpoints cambiados (HTTP real con curl/Invoke-WebRequest) + `gcloud run services logs read pickingve-api --region europe-west1 --project dashboard-439511 --limit=50` buscando errores nuevos. Si NO desplegó → 🔴 incumple D-156 (avisar; no desplegar sin orden del usuario).
- **Si tocó el panel web**: abrir la URL de producción con Playwright y verificar visualmente las secciones cambiadas.

### 5. Informe final
- ✅ **Validado**: lista de cambios verificados con evidencia (capturas, HTTP 200, tests).
- 🔴 **Incidencias**: con archivo:línea y cómo reproducir.
- 🟡 **Pendientes/deuda**: sin D-XX, sin tests, WIP a medias.
- 📋 **Acción recomendada**: aceptar / revertir (con `git revert <commit>`, nunca borrando) / completar.

## Reglas de oro
- Evidencia o no ha pasado: cada "funciona" necesita captura, HTTP o test detrás.
- No revertir nada sin orden explícita del usuario; proponerlo en el informe.
- No desplegar backend por cuenta propia salvo que el cambio del turno esté ya desplegado y solo haya que verificar.
