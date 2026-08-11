import os
import sys
from pathlib import Path

env_path = Path(__file__).resolve().parent.parent.parent / '.env'
if env_path.exists():
    for line in env_path.read_text(encoding='utf-8').splitlines():
        if '=' in line and not line.startswith('#'):
            k, v = line.split('=', 1)
            os.environ[k.strip()] = v.strip()

import google.oauth2.credentials
from googleapiclient.discovery import build

client_id = os.getenv('GDRIVE_CLIENT_ID') or '330123701214-1i1p5530drtdugak7h46cgbog96ht225.apps.googleusercontent.com'
client_secret = os.getenv('GDRIVE_CLIENT_SECRET') or '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'
refresh_token = os.getenv('GDRIVE_REFRESH_TOKEN') or '1//04wII4Bi6zTkFCgYIARAAGAQSNwF-L9IrBfd9Lf82elHptKggsdLjiD1OBtnjiZpjXIeKLVCZNZN00MdjuE93gJC8NWteHvD3Wf4'

print(f"Usando Client ID: {client_id[:10]}...")

creds = google.oauth2.credentials.Credentials(
    token=None,
    refresh_token=refresh_token,
    client_id=client_id,
    client_secret=client_secret,
    token_uri='https://oauth2.googleapis.com/token'
)

drive_service = build('drive', 'v3', credentials=creds)
script_service = build('script', 'v1', credentials=creds)

print("\n--- BÚSQUEDA AUTOMÁTICA DE GOOGLE SHEETS ---")
sheets_results = drive_service.files().list(
    q="mimeType='application/vnd.google-apps.spreadsheet'",
    pageSize=50,
    fields="files(id, name, webViewLink)"
).execute()

sheets = sheets_results.get('files', [])
print(f"Total hojas de cálculo encontradas: {len(sheets)}")
for s in sheets:
    print(f"  * {s['name']} (ID: {s['id']})")

print("\n--- BÚSQUEDA AUTOMÁTICA DE APPS SCRIPT PROJECTS ---")
script_results = drive_service.files().list(
    q="mimeType='application/vnd.google-apps.script'",
    pageSize=50,
    fields="files(id, name, webViewLink)"
).execute()

scripts = script_results.get('files', [])
print(f"Total proyectos de Apps Script encontrados: {len(scripts)}")
for sc in scripts:
    script_id = sc['id']
    script_name = sc['name']
    print(f"\n  [SCRIPT] {script_name} (ID: {script_id})")
    try:
        content = script_service.projects().getContent(scriptId=script_id).execute()
        files = content.get('files', [])
        print(f"    -> Archivos en el proyecto: {len(files)}")
        for f in files:
            print(f"       - Archivo: {f.get('name')} ({f.get('type')})")
            source = f.get('source', '')
            lines = source.splitlines()
            print(f"         Lineas totales: {len(lines)}")
            for l in lines[:10]:
                print(f"           | {l}")
    except Exception as e:
        print(f"    -> Error al leer contenido del script: {e}")
