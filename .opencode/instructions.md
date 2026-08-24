# Instrucciones operativas - PickingVE

## REGLA DE EJECUCIÓN DE BACKEND Y RUTAS (WINDOWS)

- PROHIBIDO usar rutas relativas como `.venv\Scripts\python.exe` o asunciones de directorio.
- Toda ejecución de Python en backend debe usar la ruta absoluta explicita: `C:\Users\Usuario\Documents\Manu\Proyectos\PickingVE\backend\.venv\Scripts\python.exe`.
- PROHIBIDO usar esperas fijas con `Start-Sleep` para esperar al arranque del servidor. Para verificar que el servidor ha arrancado, implementa siempre un bucle `while` de reintentos activos comprobando el puerto (máximo 30 segundos de timeout) antes de dar por fallida la conexión.

## Pruebas locales del backend

- Arrancar: `Start-Process -FilePath "C:\Users\Usuario\Documents\Manu\Proyectos\PickingVE\backend\.venv\Scripts\python.exe" -ArgumentList "-m","uvicorn","main:app","--host","127.0.0.1","--port","8081" -WorkingDirectory "C:\Users\Usuario\Documents\Manu\Proyectos\PickingVE\backend" -PassThru -RedirectStandardOutput "<tmp>\uvicorn_out.txt" -RedirectStandardError "<tmp>\uvicorn_err.txt" -WindowStyle Hidden` con `$env:API_KEY="test-key"`.
- Esperar arranque con bucle de polling sobre `http://127.0.0.1:8081/manager?k=manager-panel-2026` (o el puerto usado) hasta HTTP 200, timeout 30 s.
- Endpoint informe Punteo: `GET /api/manager/reporte/{numero_pedido}?k=manager-panel-2026` devuelve el xlsx.
- Endpoint reporte JSON: `GET /api/manager/report/{numero_pedido}?k=manager-panel-2026`.
- Al terminar, parar el proceso: `Stop-Process -Id <pid> -Force`.

## REGLA DE DESPLIEGUE (D-156)

- El usuario prueba SIEMPRE en producción. Todo cambio en `backend/` o `backend/web/` debe desplegarse en Cloud Run con `powershell -File reportes\manager\publicar.ps1` al terminar el trabajo, y la verificación final se hace contra la URL de producción `https://pickingve-api-938422468946.europe-west1.run.app/...`. Dejar solo la versión local sin desplegar NO es válido.