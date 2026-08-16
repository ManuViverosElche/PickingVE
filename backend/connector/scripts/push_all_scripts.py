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

def push_project(name, script_id):
    project_dir = backup_root / name
    if not project_dir.exists():
        print(f"Directorio local no encontrado para {name}: {project_dir}")
        return False
    
    files_to_push = []
    for fpath in project_dir.glob('*'):
        if fpath.name == 'README.md':
            continue
        
        ext = fpath.suffix.lower()
        fname = fpath.stem
        
        ftype = 'SERVER_JS'
        if ext == '.json':
            ftype = 'JSON'
        elif ext == '.html':
            ftype = 'HTML'
            
        source = fpath.read_text(encoding='utf-8')
        files_to_push.append({
            'name': fname,
            'type': ftype,
            'source': source
        })
        
    try:
        body = {'files': files_to_push}
        script_service.projects().updateContent(scriptId=script_id, body=body).execute()
        print(f"[OK] Sincronizado {name} ({len(files_to_push)} archivos) -> Google Apps Script")
        return True
    except Exception as e:
        print(f"[ERROR] Al sincronizar {name}: {e}")
        return False

if __name__ == '__main__':
    target = sys.argv[1] if len(sys.argv) > 1 else 'all'
    if target == 'all':
        print("Sincronizando todos los proyectos locales a Google Apps Script...")
        for name, script_id in SCRIPTS_MAP.items():
            push_project(name, script_id)
    else:
        if target in SCRIPTS_MAP:
            push_project(target, SCRIPTS_MAP[target])
        else:
            print(f"Proyecto '{target}' no encontrado en el mapa. Opciones: 'all', o nombre exacto.")
