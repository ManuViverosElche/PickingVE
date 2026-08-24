---
name: despliegue-backend
description: Skill para desplegar y verificar el backend PickingVE en Cloud Run de forma segura. Evita publicar WIP ajeno por accidente y garantiza verificación contra producción (D-156). Activar con frases como "despliega", "publica", "súbelo al servidor", "pásalo a producción", "sube los cambios del backend", "publicar.ps1", "deploy" o tras cualquier cambio en backend/.
---

# Skill: Despliegue Seguro del Backend (Cloud Run)

## Objetivo
Desplegar el backend sin sorpresas: nunca publicar trabajo sin commitear de otros agentes, nunca dar un deploy por bueno sin verificarlo contra producción. Elimina los "palos de ciego" típicos: subir WIP ajeno, deploy roto no detectado, URL equivocada.

## Datos fijos del proyecto
- Servicio Cloud Run: `pickingve-api`, región `europe-west1`, proyecto `dashboard-439511`.
- URL de producción (la ÚNICA válida): `https://pickingve-api-938422468946.europe-west1.run.app`
- PROHIBIDO usar `pickingve-api-347521903849...` (proyecto de la app, sin permisos).
- Script único de publicación: `powershell -File reportes\manager\publicar.ps1` (hace py_compile → gcloud builds submit → gcloud run deploy).
- Panel manager: `https://pickingve-api-938422468946.europe-west1.run.app/manager?k=manager-panel-2026`

## Pasos obligatorios (en orden, sin saltarse ninguno)

### 1. PRECHECK: ¿qué va a publicar el deploy? (crítico)
El deploy publica TODO el working directory sin commitear, incluido código de otros agentes. ANTES de ejecutar nada:
- `git status --porcelain` y `git diff --stat`: listar archivos modificados sin commit.
- Si aparecen ficheros FUERA de tu tarea, PARAR y avisar al usuario, especialmente:
  - `reportes/truffaut/**` o `backend/web/truffaut/**` o cambios `TRUFFAUT_*` dentro de `main.py` → dominio del agente de Truffaut. NO desplegarlos tú.
  - Cualquier otro módulo ajeno a tu cambio.
- Solo continuar cuando el usuario confirme explícitamente que quiere publicar ese estado completo.

### 2. Compilación previa local
- `python -m py_compile backend/main.py` (ya lo hace publicar.ps1, pero falla antes y más claro).
- Si hay tests aplicables al módulo tocado, ejecutarlos antes.

### 3. Desplegar
- `powershell -File reportes\manager\publicar.ps1`
- Si falla el build: `gcloud builds list --project dashboard-439511 --limit 3` y `gcloud builds log <ID> --project dashboard-439511` para ver el error real. NO reintentar a ciegas.

### 4. Verificación contra producción (obligatoria, D-156)
Nada queda "terminado" hasta pasar esto:
- Polling HTTP sobre producción (bucle while, NUNCA `Start-Sleep` fijo):
  `GET https://pickingve-api-938422468946.europe-west1.run.app/manager?k=manager-panel-2026` → esperar HTTP 200 (timeout 60 s; el arranque puede tardar).
- Ejercitar el endpoint concreto que hayas cambiado contra producción (con su clave `k=`).
- Revisar logs de arranque por errores nuevos:
  `gcloud run services logs read pickingve-api --region europe-west1 --project dashboard-439511 --limit=50`
- Comparar con logs anteriores solo si aparece ERROR/Traceback nuevo.

### 5. Prueba local (opcional, para depurar antes de publicar)
- Python SIEMPRE con ruta absoluta: `C:\Users\Usuario\Documents\Manu\Proyectos\PickingVE\backend\.venv\Scripts\python.exe`.
- Arrancar uvicorn en puerto 8081 con `$env:API_KEY="test-key"` (detalle exacto en `.opencode/instructions.md`).
- Esperar arranque con bucle de polling al puerto (timeout 30 s), nunca espera fija.
- Al terminar: `Stop-Process -Id <pid> -Force`.

## Reglas de oro
- Un deploy = una verificación completa. Nunca decir "desplegado" sin haber hecho el paso 4.
- Si algo falla en producción, primero logs (`gcloud run services logs read`), después hipótesis.
- No editar `reportes/truffaut/`, `backend/web/truffaut/` ni lógica `TRUFFAUT_*` bajo ninguna circunstancia.
