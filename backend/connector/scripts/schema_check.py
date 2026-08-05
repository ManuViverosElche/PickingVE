import sys
sys.path.insert(0, r"backend\connector")
from core.config import load_settings
from core.bigquery_client import build_client

s = load_settings()
c = build_client(s)
TEST = f"{s['bigquery']['project_id']}.{s['bigquery']['test_dataset']}"
for t in ["PEDIDOS", "ALBARANES"]:
    tbl = c.get_table(f"{TEST}.{t}")
    print(t, "->", ", ".join(f.name for f in tbl.schema))
