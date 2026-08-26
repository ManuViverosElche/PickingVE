import sys
import os

# Add connector root to path
connector_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, connector_dir)

from core.access_client import open_connection, list_tables, read_query

def main():
    print("=== INSPECCION DE TABLAS FACTUSOL (.accdb) ===")
    with open_connection() as conn:
        tables = list_tables(conn)
        print(f"Total tablas F_* encontradas: {len(tables)}")
        
        target_tables = ["F_CLI", "F_ART", "F_SEC", "F_FAM", "F_REC", "F_COB", "F_OBR", "F_LFA", "F_LAL", "F_ALB", "F_FAC", "F_PCL", "F_LPC"]
        
        for t in tables:
            if any(t.upper() == target.upper() for target in target_tables):
                print(f"\n--- TABLA: {t} ---")
                try:
                    cols, rows = read_query(conn, f"SELECT TOP 1 * FROM {t}")
                    print(f"Columnas ({len(cols)}):")
                    for c in cols:
                        print(f"  - {c}")
                except Exception as e:
                    print(f"  Error leyendo tabla {t}: {e}")

if __name__ == "__main__":
    main()
