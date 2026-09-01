@echo off
chcp 65001 >nul
title PickingVE - Actualización Automática
echo ============================================================
echo    PICKINGVE - ACTUALIZACION AUTOMATICA DESDE GITHUB
echo ============================================================
echo.

cd /d "%~dp0"

REM Buscar git (sistema o portable de PickingVE)
set "GIT=git"
where git >nul 2>&1
if %errorlevel% neq 0 (
    if exist "%LOCALAPPDATA%\PickingVE\tools\MinGit\cmd\git.exe" (
        set "GIT=%LOCALAPPDATA%\PickingVE\tools\MinGit\cmd\git.exe"
    ) else (
        echo [ERROR] No se encuentra git.exe en el sistema ni en MinGit portable.
        exit /b 1
    )
)

echo [*] Obteniendo ultimos cambios del repositorio (fetch origin)...
"%GIT%" fetch origin
if %errorlevel% neq 0 (
    echo [ERROR] Fallo al conectar con el repositorio remoto.
    exit /b %errorlevel%
)

echo [*] Sincronizando exactamente con origin/master (reset --hard)...
"%GIT%" reset --hard origin/master
if %errorlevel% neq 0 (
    echo [ERROR] Fallo al restablecer la rama local.
    exit /b %errorlevel%
)

echo [*] Actualizando rama (pull)...
"%GIT%" pull origin master
if %errorlevel% neq 0 (
    echo [ERROR] Fallo en el pull.
    exit /b %errorlevel%
)

echo.
echo ============================================================
echo   ACTUALIZACION COMPLETADA CON EXITO
echo ============================================================
exit /b 0
