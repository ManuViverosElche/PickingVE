# =============================================================================
# SCRIPT DE AUTOMATIZACION DE SINCRONIZACION FACTUSOL -> BIGQUERY
# PickingVE - Ejecutable para Programador de Tareas de Windows (Task Scheduler)
# =============================================================================

param(
    [string]$Dataset = "GestionComercialVE",
    [string]$Table = ""
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ConnectorDir = Split-Path -Parent $ScriptDir
$VenvPython = Join-Path $ConnectorDir ".venv\Scripts\python.exe"
$LogDir = Join-Path $ConnectorDir "logs"

if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Path $LogDir | Out-Null
}

$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$LogFile = Join-Path $LogDir "sync_$Timestamp.log"

"=== INICIO SINCRONIZACION FACTUSOL ($Dataset) - $(Get-Date) ===" | Out-File -FilePath $LogFile -Encoding utf8

$SyncScript = Join-Path $ScriptDir "sync_all.py"
$ProcessArgs = @($SyncScript, "--dataset", $Dataset)
if ($Table -ne "") {
    $ProcessArgs += "--table"
    $ProcessArgs += $Table
}

& $VenvPython @ProcessArgs *>&1 | Out-File -FilePath $LogFile -Append -Encoding utf8
$ExitCode = $LASTEXITCODE

if ($ExitCode -eq 0) {
    "=== SINCRONIZACION EXITOSA - $(Get-Date) ===" | Out-File -FilePath $LogFile -Append -Encoding utf8
    Write-Host "Sincronización completada con éxito. Log: $LogFile"
} else {
    "=== ERROR EN SINCRONIZACION (Exit Code: $ExitCode) - $(Get-Date) ===" | Out-File -FilePath $LogFile -Append -Encoding utf8
    Write-Error "Error en sincronización. Código: $ExitCode. Log: $LogFile"
    exit $ExitCode
}
