$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root
python -m py_compile backend/main.py
gcloud run deploy pickingve-api --source backend --region europe-west1 --project dashboard-439511 --quiet
Write-Host "Panel publicado: https://pickingve-api-938422468946.europe-west1.run.app/manager?k=manager-panel-2026"
