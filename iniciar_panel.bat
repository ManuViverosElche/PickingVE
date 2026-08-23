@echo off
TITLE PickingVE - Panel Operativo y Gestor de Informes
color 0A
cls
echo ========================================================
echo   PICKINGVE - INICIANDO PANEL OPERATIVO Y GESTOR
echo ========================================================
echo.
echo [1/3] Preparando entorno...
cd /d "%~dp0backend"

echo.
echo [2/3] Arrancando servidor de pedidos (Puerto 8080)...
start /b python main.py

echo.
echo [3/3] Esperando a que el servidor responda en el puerto 8080...
powershell -Command "while (-not (Test-NetConnection -ComputerName localhost -Port 8080).TcpTestSucceeded) { Start-Sleep -Milliseconds 500 }"

echo Servidor listo. Abriendo panel en el navegador...
start http://localhost:8080/manager?k=manager-panel-2026

echo.
echo ========================================================
echo   ¡SISTEMA LISTO PARA TRABAJAR!
echo   - Panel operativo abierto en el navegador.
echo   - No cierres esta ventana mientras estés trabajando.
echo ========================================================
echo.
pause
