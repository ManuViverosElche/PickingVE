import os
from google.oauth2 import service_account
from googleapiclient.discovery import build

key_path = r"V:\DashBoard\clave_json.json"
scopes = [
    'https://www.googleapis.com/auth/spreadsheets',
    'https://www.googleapis.com/auth/drive',
    'https://www.googleapis.com/auth/script.projects'
]

creds = service_account.Credentials.from_service_account_file(key_path, scopes=scopes)
script_service = build('script', 'v1', credentials=creds)

script_id = '15L26pwVBwzgfLwstinCb0Mv_GgDJYWhf94NTpIkH9Fc7W9cJMs1BHCLg' # Pedidos - Borisa
try:
    content = script_service.projects().getContent(scriptId=script_id).execute()
    print("SUCCESS with Service Account! Archivos:", len(content.get('files', [])))
    for f in content.get('files', []):
        print(" -", f.get('name'))
except Exception as e:
    print("Error with Service Account:", e)
