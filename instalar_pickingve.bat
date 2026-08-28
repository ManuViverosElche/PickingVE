<# :
@echo off
title PickingVE - Instalador Servidor v3
echo.
echo  INSTALADOR PICKINGVE v3 - esta ventana NO se cierra sola hasta el final.
powershell -NoProfile -ExecutionPolicy Bypass -Command "$raw=[IO.File]::ReadAllText('%~f0'); $parts=$raw -split '(?m)^#>[ \t]*\r?\n',2; if($parts.Count -lt 2){ Write-Host 'Cabecera corrupta'; exit 1 }; & { Invoke-Expression $parts[1] }"
set "EC=%errorlevel%"
echo.
echo ============================================================
if "%EC%"=="0" (echo   INSTALACION FINALIZADA CORRECTAMENTE) else (echo   INSTALACION CON ERRORES - codigo %EC%)
echo   Log guardado en el ESCRITORIO: pickingve_instalador.log
echo ============================================================
echo.
pause
exit /b %EC%
#>

# ============================================================
#  INSTALADOR PICKINGVE v3 - Conector Factusol -> BigQuery
#  - No requiere admin ni tienda Windows (Git portatil propio)
#  - Fuerza TLS 1.2 (imprescindible en Windows 10 viejos)
#  - LOG SIEMPRE en el Escritorio: pickingve_instalador.log
# ============================================================

$ErrorActionPreference = 'Continue'
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$RepoUrl      = "https://github.com/ManuViverosElche/PickingVE.git"
$DefaultDb    = "X:\Datos\FS\0142026.accdb"
$DefaultCreds = "V:\DashBoard\clave_json.json"
$ToolsDir     = Join-Path $env:LOCALAPPDATA "PickingVE\tools"
$Desktop      = [Environment]::GetFolderPath("Desktop")
$LogPath      = Join-Path $Desktop "pickingve_instalador.log"

if (Test-Path $LogPath) { Remove-Item $LogPath -Force -ErrorAction SilentlyContinue }
Start-Transcript -Path $LogPath | Out-Null

function Step($t){ Write-Host "`n=== $t ===" -ForegroundColor Cyan }
function Ok($m){ Write-Host "  [OK]   $m" -ForegroundColor Green }
function Warn($m){ Write-Host "  [AVISO]$m" -ForegroundColor Yellow }
function Fail($m){ Write-Host "  [ERROR] $m" -ForegroundColor Red }

Write-Host @"

  ============================================================
     INSTALADOR PICKINGVE v3 - Factusol a BigQuery
     Todo queda registrado en: $LogPath
  ============================================================
"@ -ForegroundColor White

$global:ExitCode = 0
try {

    # ---------- utilidades ----------
    function Find-Git {
        $c = Get-Command git -ErrorAction SilentlyContinue
        if ($c) { return $c.Source }
        foreach ($p in @("C:\Program Files\Git\bin\git.exe","C:\Program Files (x86)\Git\bin\git.exe")) {
            if (Test-Path $p) { return $p }
        }
        $portable = Join-Path $ToolsDir "MinGit\cmd\git.exe"
        if (Test-Path $portable) { return $portable }
        return $null
    }

    function Ensure-Git {
        $g = Find-Git
        if ($g) { return $g }
        Step "(extra) Descargando Git portatil (~40 MB, sin permisos de admin)"
        New-Item -ItemType Directory -Force -Path (Join-Path $ToolsDir "MinGit") | Out-Null
        $zip = Join-Path $ToolsDir "MinGit.zip"
        $url = "https://github.com/git-for-windows/git/releases/download/v2.47.1.windows.1/MinGit-2.47.1-64-bit.zip"
        Write-Host "  Descargando $url"
        [Net.WebClient]::new().DownloadFile($url, $zip)
        Write-Host "  Extrayendo..."
        Expand-Archive -LiteralPath $zip -DestinationPath (Join-Path $ToolsDir "MinGit") -Force
        Remove-Item $zip -Force -ErrorAction SilentlyContinue
        $g = Find-Git
        if ($g) { Ok ("Git portatil listo: " + $g) }
        return $g
    }

    function Find-Python {
        foreach ($name in @("python","py")) {
            $c = Get-Command $name -ErrorAction SilentlyContinue
            if ($c) { return $c.Source }
        }
        foreach ($v in @(3,4)) {
            foreach ($bits in @("64","32")) {
                $p = "$env:LOCALAPPDATA\Programs\Python\Python3$v$bits\python.exe"
                if (Test-Path $p) { return $p }
            }
        }
        return $null
    }

    function Ensure-Python {
        $p = Find-Python
        if ($p) { return $p }
        Step "(extra) Descargando instalador de Python 3.12 (~26 MB, por usuario)"
        $exe = Join-Path $ToolsDir "python312.exe"
        New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null
        $url = "https://www.python.org/ftp/python/3.12.8/python-3.12.8-amd64.exe"
        Write-Host "  Descargando $url"
        [Net.WebClient]::new().DownloadFile($url, $exe)
        Write-Host "  Instalando silenciosamente (solo para este usuario)..."
        Start-Process -FilePath $exe -ArgumentList "/quiet InstallAllUsers=0 PrependPath=1 Include_test=0" -Wait
        $env:Path = "$env:Path;$env:LOCALAPPDATA\Programs\Python\Python312;$env:LOCALAPPDATA\Programs\Python\Python312\Scripts"
        $p = Find-Python
        if ($p) { Ok ("Python instalado: " + $p) }
        return $p
    }

    # ---------- 1. Carpeta ----------
    Step "1/9 Carpeta de instalacion"
    $defRoot = "C:\PickingVE"
    $r = Read-Host "  Carpeta destino [$defRoot]"
    if ([string]::IsNullOrWhiteSpace($r)) { $r = $defRoot }
    $Root = $r.TrimEnd('\')
    Write-Host "  Destino: $Root"
    if ((Test-Path $Root) -and -not (Test-Path "$Root\.git")) {
        Warn " La carpeta existe sin repositorio (¿intento anterior interrumpido?)."
        $del = Read-Host "  ¿Borrarla e instalar limpia? [S/n]"
        if ($del -notmatch '^[nN]') { Remove-Item -LiteralPath $Root -Recurse -Force }
    }

    # ---------- prueba de conexion ----------
    Step "2/9 Prueba de conexion a internet"
    try {
        $resp = [Net.WebRequest]::Create("https://github.com")
        $resp.Timeout = 8000
        $r2 = $resp.GetResponse()
        $r2.Close()
        Ok "Internet accesible (github.com)"
    } catch {
        Fail "NO HAY ACCESO a github.com desde este PC: $($_.Exception.Message)"
        throw "Sin internet no se puede instalar. Conecta la red y vuelve a ejecutar."
    }

    # ---------- 3. Clonar o actualizar ----------
    Step "3/9 Descargando codigo del proyecto"
    $gitExe = Ensure-Git
    if (-not $gitExe) { throw "No se pudo conseguir Git (ni sistema, ni portatil)." }
    Ok ("Usando Git: " + $gitExe)

    if (Test-Path "$Root\.git") {
        & $gitExe -C $Root pull --ff-only
        if ($LASTEXITCODE -ne 0) { Warn " pull devolvio $LASTEXITCODE; se continua con el codigo existente." }
        else { Ok "Repositorio actualizado" }
    } else {
        New-Item -ItemType Directory -Force -Path $Root | Out-Null
        Write-Host "  Si GitHub pide credenciales -> Usuario: tu usuario | Contrasena: Personal Access Token"
        & $gitExe clone $RepoUrl $Root
        if ($LASTEXITCODE -ne 0) {
            throw "Fallo el CLONE (codigo $LASTEXITCODE). Motivo tipico: repositorio privado sin credenciales validas."
        }
        Ok "Repositorio clonado en $Root"
    }
    if (-not (Test-Path (Join-Path $Root "backend\connector\requirements.txt"))) {
        throw "El repositorio se descargo pero falta backend\connector\requirements.txt (descarga incompleta)."
    }

    # ---------- 4. Python ----------
    Step "4/9 Preparando Python"
    $connectorDir = Join-Path $Root "backend\connector"
    $venvPy = Join-Path $connectorDir ".venv\Scripts\python.exe"
    if (Test-Path $venvPy) {
        Ok "Entorno virtual existente reutilizado"
    } else {
        $pyExe = Ensure-Python
        if (-not $pyExe) { throw "No se pudo conseguir Python (ni sistema, ni instalacion automatica)." }
        & $pyExe -m venv (Join-Path $connectorDir ".venv")
        if (-not (Test-Path $venvPy)) { throw "No se pudo crear el entorno virtual .venv" }
        Ok "Entorno virtual creado (.venv)"
    }

    # ---------- 5. Dependencias ----------
    Step "5/9 Instalando dependencias (pandas, bigquery, pyodbc...)"
    & $venvPy -m pip install --disable-pip-version-check --quiet --only-binary :all: -r (Join-Path $connectorDir "requirements.txt")
    if ($LASTEXITCODE -ne 0) {
        Warn " reintento sin restriccion de binarios..."
        & $venvPy -m pip install --disable-pip-version-check --quiet -r (Join-Path $connectorDir "requirements.txt")
        if ($LASTEXITCODE -ne 0) { throw "pip fallo (codigo $LASTEXITCODE). Revisa el log." }
    }
    Ok "Dependencias instaladas"

    # ---------- 5b. Verificar driver Access ----------
    Step "5b/9 Verificando acceso a Factusol (driver ODBC)"
    & $venvPy -m pip install --disable-pip-version-check --quiet pyodbc | Out-Null
    & $venvPy (Join-Path $connectorDir "scripts\check_access.py")
    if ($LASTEXITCODE -ne 0) {
        Warn " El test de Access fallo. La instalacion CONTINUA (podras arreglarlo luego),"
        Warn " pero las sincronizaciones no funcionaran hasta resolverlo. Ver log de arriba."
    }

    # ---------- 6. Configuracion local ----------
    Step "6/9 Configuracion local (Factusol y Google)"
    $db = Read-Host "  Ruta base Factusol (.accdb) [$DefaultDb]"
    if ([string]::IsNullOrWhiteSpace($db)) { $db = $DefaultDb }
    $creds = Read-Host "  Ruta clave JSON de BigQuery [$DefaultCreds]"
    if ([string]::IsNullOrWhiteSpace($creds)) { $creds = $DefaultCreds }
    if (-not (Test-Path $db))    { Warn " Ruta Factusol no accesible ahora (se guarda igualmente)." }
    if (-not (Test-Path $creds)) { Warn " JSON de credenciales no encontrado; copialo antes de la primera sync." }

    $cfgDir  = Join-Path $connectorDir "config"
    $cfgFile = Join-Path $cfgDir "settings.local.yaml"
    New-Item -ItemType Directory -Force -Path $cfgDir | Out-Null
    if (Test-Path $cfgFile) { Copy-Item $cfgFile "$cfgFile.bak" -Force }
    @"
access:
  driver: "Microsoft Access Driver (*.mdb, *.accdb)"
  db_path: "$($db.Replace('\','\\'))"
  read_only: true

bigquery:
  project_id: "dashboard-439511"
  production_dataset: "GestionComercialVE"
  test_dataset: "conector_test"
  analytics_dataset: "Analytics"
  location: "EU"
  credentials_path: "$($creds.Replace('\','\\'))"
"@ | Out-File -FilePath $cfgFile -Encoding utf8
    Ok "settings.local.yaml escrito"

    # ---------- 7. Tareas programadas ----------
    Step "7/9 Programando sincronizacion automatica"
    $runSync = Join-Path $connectorDir "scripts\run_sync.ps1"
    $action  = "-NoProfile -ExecutionPolicy Bypass -File `"$runSync`""
    $user    = "$env:USERDOMAIN\$env:USERNAME"
    $hoy     = Get-Date -Format "yyyy-MM-dd"

    function New-SyncTaskXml($name, $startHour, $intervalMin, $durHours, $dataset){
        # schtasks + XML: metodo universal (Register-ScheduledTask falla en
        # algunos Windows 10 al setear RepetitionInterval)
        $rep = ""
        if ($intervalMin -gt 0) {
            $rep = "<Repetition><Interval>PT$($intervalMin)M</Interval><Duration>PT$($durHours)H</Duration><StopAtDurationEnd>true</StopAtDurationEnd></Repetition>"
        }
        $xml = @"
<?xml version="1.0" encoding="UTF-16"?>
<Task version="1.2" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
  <Triggers>
    <CalendarTrigger>
      <StartBoundary>$($hoy)T$($startHour):00:00</StartBoundary>
      $rep
      <ScheduleByDay><DaysInterval>1</DaysInterval></ScheduleByDay>
    </CalendarTrigger>
  </Triggers>
  <Principals>
    <Principal id="Author"><UserId>$user</UserId><LogonType>InteractiveToken</LogonType></Principal>
  </Principals>
  <Settings>
    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>
    <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>
    <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>
    <StartWhenAvailable>true</StartWhenAvailable>
    <ExecutionTimeLimit>PT2H</ExecutionTimeLimit>
  </Settings>
  <Actions Context="Author">
    <Exec><Command>powershell.exe</Command><Arguments>$action -Dataset $dataset</Arguments><WorkingDirectory>$Root</WorkingDirectory></Exec>
  </Actions>
</Task>
"@
        $xmlFile = Join-Path $env:TEMP "$name.xml"
        [IO.File]::WriteAllText($xmlFile, $xml, (New-Object Text.UnicodeEncoding))
        schtasks /Create /F /TN $name /XML $xmlFile | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "schtasks devolvio $LASTEXITCODE para $name" }
    }

    try {
        New-SyncTaskXml "PickingVE-Sync-Produccion" 8 30 13 "GestionComercialVE"
        Ok "Produccion -> GestionComercialVE cada 30 min (08:00-21:00)"
        New-SyncTaskXml "PickingVE-Sync-Analytics" 5 0 0 "Analytics"
        Ok "Analytics -> diario a las 05:00"
    } catch {
        Fail ("No se pudieron crear las tareas automaticas: " + $_.Exception.Message)
        Warn " Podras crearlas a mano; no bloquea la instalacion ni el menu manual."
    }

    # ---------- 8. Acceso directo ----------
    Step "8/9 Acceso directo en el Escritorio"
    $menuBat = Join-Path $connectorDir "scripts\sincronizar_menu.bat"
    try {
        $lnk = Join-Path $Desktop "Sincronizar Factusol.lnk"
        $ws = New-Object -ComObject WScript.Shell
        $sc = $ws.CreateShortcut($lnk)
        $sc.TargetPath = $menuBat
        $sc.WorkingDirectory = (Split-Path $menuBat)
        $sc.Description = "Sincronizar Factusol con BigQuery (PickingVE)"
        $sc.IconLocation = "%SystemRoot%\System32\shell32.dll,13"
        $sc.Save()
        Ok ("Creado: " + $lnk)
    } catch {
        Warn (" No se pudo crear el acceso directo: " + $_.Exception.Message)
    }

    # ---------- 9. Prueba inicial ----------
    Step "9/9 Prueba inicial de sincronizacion"
    $t = Read-Host "  ¿Ejecutar sync completa de prueba ahora? [S/n]"
    if ($t -notmatch '^[nN]') {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $runSync -Dataset GestionComercialVE
        if ($LASTEXITCODE -eq 0) { Ok "Prueba completada con exito" }
        else { Warn " La prueba devolvio $LASTEXITCODE; revisa backend\connector\logs\" }
    }

    Write-Host @"

  ============================================================
     RESUMEN - INSTALACION COMPLETADA
     Proyecto ............ $Root
     Sync automatica ..... Produccion cada 30 min / Analytics diaria
     Manual escritorio ... 'Sincronizar Factusol'
     Logs sync ........... backend\connector\logs\
  ============================================================
"@ -ForegroundColor Green

} catch {
    $global:ExitCode = 1
    Write-Host "`n  ==========================================" -ForegroundColor Red
    Write-Host "   LA INSTALACION SE DETUVO EN ESTE PUNTO:" -ForegroundColor Red
    Write-Host "   $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "   Detalle tecnico completo en el log:" -ForegroundColor Red
    Write-Host "   $LogPath" -ForegroundColor Red
    Write-Host "  ==========================================`n" -ForegroundColor Red
}

Stop-Transcript | Out-Null
exit $global:ExitCode
