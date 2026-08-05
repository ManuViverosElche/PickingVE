import sys
sys.path.insert(0, r"backend\connector")
from core.config import load_settings
from core.access_client import open_connection

s = load_settings()
with open_connection(s) as conn:
    cur = conn.cursor()

    tabs = ["F_CLI", "F_AGE", "F_ART", "F_ALB", "F_FAC", "F_PCL", "F_LPC",
            "F_EAC", "F_CE1", "F_CE2", "F_FPA", "F_LTA", "F_LTC", "F_STC"]
    for t in tabs:
        try:
            cols = cur.columns(t).fetchall()
            datecols = [c.column_name for c in cols
                        if c.type_name in ("DATETIME", "DATE", "SMALLDATETIME", "TIMESTAMP")]
            print(f"{t:8} {len(cols):3} cols  fecha={datecols}")
        except Exception as e:
            print(f"{t:8} ERROR {type(e).__name__}: {str(e)[:80]}")
