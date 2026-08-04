import pyodbc
from google.cloud import bigquery
import pandas as pd
import os
import csv
from datetime import datetime

def actualizar_tabla_stock_combinaciones():
    current_year = datetime.now().year
    db_filename = f'014{current_year}.accdb'
    os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = r'V:\\DashBoard\\clave_json.json'
    db_path = f'X:\\Datos\\FS\\{db_filename}'
    csv_path = r'V:\\DashBoard\\csv\\STOCK.csv'
    
    conn_str = (
        r'DRIVER={Microsoft Access Driver (*.mdb, *.accdb)};'
        r'DBQ=' + db_path + ';'
    )
    conn = pyodbc.connect(conn_str)

    # Consulta para extraer los datos de la tabla F_STC
    query = """
    SELECT 
        ARTSTC AS REFERENCIA_ARTICULO,
        CE1STC AS CODIGO_LITRAJE,
        CE2STC AS CODIGO_SECTOR,
        ACTSTC AS STOCK_ACTUAL,
        DISSTC AS DISPONIBLE_ACTUAL
    FROM F_STC
    ORDER BY ARTSTC;
    """
    
    df = pd.read_sql(query, conn)

    # Asegurarse de que los campos numéricos están en el formato adecuado
    numeric_columns = ["STOCK_ACTUAL", "DISPONIBLE_ACTUAL"]
    for col in numeric_columns:
        df[col] = pd.to_numeric(df[col], errors='coerce')  # Convierte no numéricos a NaN
        df[col] = df[col].fillna(0)  # Rellena NaN con 0

    # Exportar a CSV con citas para evitar problemas de formato
    df.to_csv(csv_path, index=False, encoding='utf-8', float_format='%.2f', quoting=csv.QUOTE_ALL)
    print("Exportación de datos completada con éxito.")

    # Configuración de BigQuery
    client = bigquery.Client()
    dataset_id = 'dashboard-439511.GestionComercialVE'
    table_id = f'{dataset_id}.STOCK'
    
    schema = [
        bigquery.SchemaField("REFERENCIA_ARTICULO", "STRING", mode="NULLABLE", description="Referencia del artículo"),
        bigquery.SchemaField("CODIGO_LITRAJE", "STRING", mode="NULLABLE", description="Código de litraje del artículo"),
        bigquery.SchemaField("CODIGO_SECTOR", "STRING", mode="NULLABLE", description="Código del sector del artículo"),
        bigquery.SchemaField("STOCK_ACTUAL", "FLOAT", mode="NULLABLE", description="Stock actual del artículo"),
        bigquery.SchemaField("DISPONIBLE_ACTUAL", "FLOAT", mode="NULLABLE", description="Disponible actual del artículo")
    ]

    # Configuración del trabajo de carga en BigQuery
    try:
        client.get_table(table_id)
        print(f"La tabla {table_id} ya existe. Actualizando datos...")
        job_config = bigquery.LoadJobConfig(
            schema=schema,
            source_format=bigquery.SourceFormat.CSV,
            write_disposition=bigquery.WriteDisposition.WRITE_TRUNCATE,
            skip_leading_rows=1,
            max_bad_records=10
        )
    except Exception as e:
        print(f"La tabla {table_id} no existe. Creando nueva tabla...")
        job_config = bigquery.LoadJobConfig(
            schema=schema,
            source_format=bigquery.SourceFormat.CSV,
            write_disposition=bigquery.WriteDisposition.WRITE_TRUNCATE,
            skip_leading_rows=1
        )

    # Cargar los datos en BigQuery
    with open(csv_path, "rb") as source_file:
        job = client.load_table_from_file(source_file, table_id, job_config=job_config)
        job.result()

    print(f"Carga de la tabla STOCK completada con éxito.")

if __name__ == "__main__":
    actualizar_tabla_stock_combinaciones()
