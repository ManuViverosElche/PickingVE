@echo off
chcp 65001 >nul
title PickingVE - Panel Sincronizacion Factusol
setlocal enabledelayedexpansion

rem Raiz del proyecto = dos niveles por encima de este script (backend\connector\scripts)
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%\..\..") do set "ROOT=%%~fI"

set "PY=%ROOT%\backend\connector\.venv\Scripts\python.exe"
set "SYNC=%ROOT%\backend\connector\scripts\sync_all.py"
if not exist "%PY%" set "PY=python"

:menu
cls
echo ============================================================
echo    PANEL DE SINCRONIZACION FACTUSOL - PICKINGVE
echo ============================================================
echo    Proyecto: %ROOT%
echo ------------------------------------------------------------
echo   [1] Sincronizar TODO Produccion (GestionComercialVE)
echo   [2] Sincronizar TODO Analytics
echo   [3] Solo PEDIDOS + LINEA_PEDIDO  (acopio urgente)
echo   [4] Solo CLIENTE / ARTICULOS / STOCK
echo   [5] Solo VENCIMIENTOS + COBROS   (morosidad)
echo   [6] Ver ultimo log de ejecucion
echo   [7] Salir
echo ------------------------------------------------------------
set /p OP="Elige una opcion (1-7): "

if "%OP%"=="1" goto full_prod
if "%OP%"=="2" goto full_ana
if "%OP%"=="3" goto pedidos
if "%OP%"=="4" goto maestros
if "%OP%"=="5" goto morosidad
if "%OP%"=="6" goto verlog
if "%OP%"=="7" exit /b 0
goto menu

:full_prod
echo.
echo === Sincronizando GestionComercialVE (21 tablas) ===
"%PY%" "%SYNC%" --dataset GestionComercialVE
goto fin

:full_ana
echo.
echo === Sincronizando Analytics (21 tablas) ===
"%PY%" "%SYNC%" --dataset Analytics
goto fin

:pedidos
echo.
echo === PEDIDOS ===
"%PY%" "%SYNC%" --table PEDIDOS --dataset GestionComercialVE
echo === LINEA_PEDIDO ===
"%PY%" "%SYNC%" --table LINEA_PEDIDO --dataset GestionComercialVE
goto fin

:maestros
echo.
echo === CLIENTE ===
"%PY%" "%SYNC%" --table CLIENTE --dataset GestionComercialVE
echo === ARTICULOS ===
"%PY%" "%SYNC%" --table ARTICULOS --dataset GestionComercialVE
echo === STOCK ===
"%PY%" "%SYNC%" --table STOCK --dataset GestionComercialVE
goto fin

:morosidad
echo.
echo === VENCIMIENTOS (Analytics) ===
"%PY%" "%SYNC%" --table VENCIMIENTOS --dataset Analytics
echo === COBROS (Analytics) ===
"%PY%" "%SYNC%" --table COBROS --dataset Analytics
goto fin

:verlog
echo.
set "LASTLOG="
for /f "delims=" %%F in ('dir /b /o-d "%ROOT%\backend\connector\logs\sync_*.log" 2^>nul') do (
    if not defined LASTLOG set "LASTLOG=%ROOT%\backend\connector\logs\%%F"
)
if not defined LASTLOG (
    echo No hay logs todavia.
) else (
    echo Mostrando: !LASTLOG!
    echo ------------------------------------------------------------
    type "!LASTLOG!"
)

:fin
echo.
echo ============================================================
pause
goto menu
