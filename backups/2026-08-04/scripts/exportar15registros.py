import pyodbc
import os
import pandas as pd
from datetime import datetime

# RUTA A TU ACCESS (copia de trabajo)
current_year = datetime.now().year
db_filename = f'014{current_year}.accdb'
ACCESS_PATH = f'X:\\{db_filename}'
# CARPETA SALIDA
OUTPUT_DIR = r'V:\\DashBoard\\Base de Datos'
# NÚMERO DE REGISTROS POR TABLA
TOP_N = 15

os.makedirs(OUTPUT_DIR, exist_ok=True)

# CADENA DE CONEXIÓN (Access 2007+ 64 bits; si es .mdb cambia Provider)
conn_str = (
    r"Driver={Microsoft Access Driver (*.mdb, *.accdb)};"
    fr"Dbq={ACCESS_PATH};"
)

cnx = pyodbc.connect(conn_str)
cursor = cnx.cursor()

# 1) Listar tablas de usuario
tables = []
for row in cursor.tables(tableType="TABLE"):
    name = row.table_name
    # Saltar tablas de sistema
    if not name.startswith("MSys") and not name.startswith("~"):
        tables.append(name)

print("Tablas encontradas:", len(tables))

# 2) Exportar TOP N de cada tabla a Excel
for name in tables:
    try:
        sql = f"SELECT TOP {TOP_N} * FROM [{name}]"
        df = pd.read_sql(sql, cnx)
        out_path = os.path.join(OUTPUT_DIR, f"{name}_TOP{TOP_N}.xlsx")
        df.to_excel(out_path, index=False)
        print("OK:", name)
    except Exception as e:
        print("ERROR en", name, "->", e)

cursor.close()
cnx.close()
print("Hecho.")
