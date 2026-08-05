import sys
sys.path.insert(0, r"backend\connector")
from core.config import load_settings
from core.bigquery_client import build_client

s = load_settings()
c = build_client(s)
table = f"{s['bigquery']['project_id']}.{s['bigquery']['test_dataset']}.AGENTE"
for row in c.query(f"SELECT * FROM `{table}` ORDER BY ID_AGENTE"):
    print(dict(row))
