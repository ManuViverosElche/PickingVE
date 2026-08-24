$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root
python -m py_compile backend/main.py
$image = "europe-west1-docker.pkg.dev/dashboard-439511/cloud-run-source-deploy/pickingve-api:latest"
gcloud builds submit backend --project dashboard-439511 --tag $image --quiet
if ($LASTEXITCODE -ne 0) { throw "Build fallido" }
gcloud run deploy pickingve-api --image $image --region europe-west1 --project dashboard-439511 --quiet
Write-Host "Panel publicado: https://pickingve-api-938422468946.europe-west1.run.app/manager?k=manager-panel-2026"
