$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root
node reportes/truffaut/build.js
Copy-Item reportes/truffaut/index.html backend/web/truffaut/index.html -Force
Copy-Item reportes/truffaut/data.json backend/web/truffaut/data.json -Force
python -m py_compile backend/main.py
gcloud run deploy pickingve-api --source backend --region europe-west1 --project dashboard-439511 --quiet
Write-Host "Informe publicado: https://pickingve-api-938422468946.europe-west1.run.app/truffaut?k=truffaut-otono-2026"