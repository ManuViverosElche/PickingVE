import os
import sys
from pathlib import Path

env_path = Path(__file__).resolve().parent.parent.parent.parent / '.env'
if env_path.exists():
    for line in env_path.read_text(encoding='utf-8').splitlines():
        if '=' in line and not line.startswith('#'):
            k, v = line.split('=', 1)
            os.environ[k.strip()] = v.strip()

import google.oauth2.credentials
from googleapiclient.discovery import build

client_id = os.getenv('GDRIVE_CLIENT_ID')
client_secret = os.getenv('GDRIVE_CLIENT_SECRET')
refresh_token = os.getenv('GDRIVE_REFRESH_TOKEN')

if not client_id or not client_secret or not refresh_token:
    raise ValueError("Faltan variables de entorno GDRIVE_CLIENT_ID, GDRIVE_CLIENT_SECRET o GDRIVE_REFRESH_TOKEN en el archivo .env o en el entorno.")

creds = google.oauth2.credentials.Credentials(
    token=None,
    refresh_token=refresh_token,
    client_id=client_id,
    client_secret=client_secret,
    token_uri='https://oauth2.googleapis.com/token'
)

script_service = build('script', 'v1', credentials=creds)

SCRIPTS_MAP = {
    "Pedidos_Borisa": "15L26pwVBwzgfLwstinCb0Mv_GgDJYWhf94NTpIkH9Fc7W9cJMs1BHCLg",
    "Pedidos_La_Fabrica": "1fvg9BOyTZNrik9DUm8ThLfdT2CbLvDP5apYitvOMGsKjGDNPyAtf7YHf",
    "Pedidos_Diao": "1Y1qGTm9CKn7hA86VXs1JiBArlvYoeBbBj9BhPo-QvI3OyVuT-D2GqWyQ",
    "Pedidos_Antonio": "1Tni2rkPJ_a9-YGgGdh7og7rdRH2A7qotNkA74iSTZvzgQBacApBms78D",
    "Pedidos_Don_Angel": "1h8xdGG4DoHKeMRQcS1O72z6Z5d0kZr8II2VgtXoyeBt66QPsrtS0xKXc",
    "Pedidos_Abderrahmane": "1auYmvcu9T24F-mAG0APLiZnXyfNtVIE6ubLEx0CwbAOfNbHgXjtZdVkD",
    "Pedidos_Angelillo": "1Ughk1BWn5CuN8S9-nOKxhdKC-rCPzZJM-sEVHeipuStuXC5rJY7Lp--X",
    "Pedidos_Jesus": "1Y9S6eaM0y8gQcoaVxlZhxmejkbatbS2eqwFqKyUZvnodCLcn7DyyvsyO",
    "Pedidos_Fede": "1GbF5UVH5w9Wz1OsXl8bBjoas6KTT75EqwY--XaLNX913Sj4U5tmT1RR9",
    "Pedidos_Resto_Fincas": "1Qxxkin0bVYbBjEeIUJMvBUbOGir8Vro8J9hgre3VAruGdX-dKKLkDGvo",
    "Pedidos_Bara": "1Nw-jva5b-saHh3x5jhnc0yL3f6Ib-1r9WGMzjexM7Ntg0KxweNb4Bz7R",
    "Pedidos_Carlos": "1XODmMUoQlfhuaC-J8h5N_VJu6PkO1TvLMMbm0zGHtxYTD7mQxLN9kXO1",
    "Libreria_Pedidos_Viveros_Elche": "1iM_5w2iHTidxU_wCpyZrs_7E5u90Yzlee0xfFvAN2NJvULzhTuaMCxhL",
    "Calendario_Viveros_Elche": "1pMXkK0d8XC41wrwL6Zy7x7mG3HCqZxpqvLCK8BOB7qG-QKvlcjwjjfhR"
}

backup_root = Path(__file__).resolve().parent.parent.parent.parent / 'docs' / 'apps_scripts'
backup_root.mkdir(parents=True, exist_ok=True)

readme_lines = ["# Apps Script Backups & Catalog\n\nCatálogo de proyectos de Google Apps Script vinculados a PickingVE.\n\n## Proyectos respaldados:\n"]

for name, script_id in SCRIPTS_MAP.items():
    print(f"Respaldando {name} (ID: {script_id})...")
    project_dir = backup_root / name
    project_dir.mkdir(parents=True, exist_ok=True)
    
    readme_lines.append(f"- **{name}** (`{script_id}`)")
    
    try:
        content = script_service.projects().getContent(scriptId=script_id).execute()
        files = content.get('files', [])
        for f in files:
            fname = f.get('name', 'unknown')
            ftype = f.get('type', 'TEXT')
            source = f.get('source', '')
            
            ext = '.gs'
            if ftype == 'JSON':
                ext = '.json'
            elif ftype == 'HTML':
                ext = '.html'
                
            out_file = project_dir / f"{fname}{ext}"
            out_file.write_text(source, encoding='utf-8')
        print(f"  -> Guardados {len(files)} archivos.")
    except Exception as e:
        print(f"  -> Error: {e}")
        readme_lines.append(f"  - *Error al sincronizar: {e}*")

readme_path = backup_root / 'README.md'
readme_path.write_text("\n".join(readme_lines), encoding='utf-8')
print(f"\nRespaldo completo en {backup_root}")
