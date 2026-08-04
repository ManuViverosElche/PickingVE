import pyodbc
from google.cloud import bigquery
import pandas as pd
import os
import csv
from datetime import datetime

def actualizar_tabla_sectores():
    current_year = datetime.now().year
    db_filename = f'014{current_year}.accdb'
    os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = r'V:\\DashBoard\\clave_json.json'
    db_path = f'X:\\Datos\\FS\\{db_filename}'
    csv_path = r'V:\\DashBoard\\csv\\SECTORES.csv'

    conn_str = (
        r'DRIVER={Microsoft Access Driver (*.mdb, *.accdb)};'
        r'DBQ=' + db_path + ';'
    )
    conn = pyodbc.connect(conn_str)

    query = """
    SELECT 
        CODCE2 AS ID_SECTOR,
        DESCE2 AS DESCRIPCION_SECTOR
    FROM F_CE2
    ORDER BY
        DESCE2;
    """
    df = pd.read_sql(query, conn)

    # Exportar a CSV
    df.to_csv(csv_path, index=False, encoding='utf-8')
    print("Exportación de datos completada con éxito.")

    # Cargar en BigQuery
    client = bigquery.Client()
    dataset_id = 'dashboard-439511.GestionComercialVE'
    table_id = f'{dataset_id}.SECTORES'

    schema = [
        bigquery.SchemaField("ID_SECTOR", "STRING", mode="NULLABLE", description="Código del sector"),
        bigquery.SchemaField("DESCRIPCION_SECTOR", "STRING", mode="NULLABLE", description="Descripción del sector")
    ]

    job_config = bigquery.LoadJobConfig(
        schema=schema,
        source_format=bigquery.SourceFormat.CSV,
        write_disposition=bigquery.WriteDisposition.WRITE_TRUNCATE,
        skip_leading_rows=1
    )

    with open(csv_path, "rb") as source_file:
        job = client.load_table_from_file(source_file, table_id, job_config=job_config)
        job.result()

    print(f"Carga de la tabla SECTORES completada con éxito.")

if __name__ == "__main__":
    actualizar_tabla_sectores()
