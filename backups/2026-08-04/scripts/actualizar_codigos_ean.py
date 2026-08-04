import pyodbc
from google.cloud import bigquery
import pandas as pd
import os
from datetime import datetime

def actualizar_tabla_codigos_barras():
    current_year = datetime.now().year
    db_filename = f'014{current_year}.accdb'
    os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = r'V:\\DashBoard\\clave_json.json'
    db_path = f'X:\\Datos\\FS\\{db_filename}'
    csv_path = r'V:\\DashBoard\\csv\\CODIGOS_EAN.csv'

    conn_str = (
        r'DRIVER={Microsoft Access Driver (*.mdb, *.accdb)};'
        r'DBQ=' + db_path + ';'
    )
    conn = pyodbc.connect(conn_str)

    query = """
    SELECT 
        ARTEAC AS REFERENCIA_ARTICULO,
        EANEAC AS CODIGO_EAN,
        CE1EAC AS CODIGO_LITRAJE,
        CE2EAC AS CODIGO_SECTOR
    FROM F_EAC
    ORDER BY
        ARTEAC,
        CE1EAC;
    """
    df = pd.read_sql(query, conn)

    # Exportar a CSV
    df.to_csv(csv_path, index=False, encoding='utf-8')
    print("Exportación de datos completada con éxito.")

    # Cargar en BigQuery
    client = bigquery.Client()
    dataset_id = 'dashboard-439511.GestionComercialVE'
    table_id = f'{dataset_id}.CODIGOS_EAN'

    schema = [
        bigquery.SchemaField("REFERENCIA_ARTICULO", "STRING", mode="NULLABLE", description="Referencia del artículo"),
        bigquery.SchemaField("CODIGO_EAN", "STRING", mode="NULLABLE", description="Código EAN de la combinación"),
        bigquery.SchemaField("CODIGO_LITRAJE", "STRING", mode="NULLABLE", description="Código del litraje del artículo"),
        bigquery.SchemaField("CODIGO_SECTOR", "STRING", mode="NULLABLE", description="Código del sector del artículo")
    ]

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
    except:
        print(f"La tabla {table_id} no existe. Creando nueva tabla...")
        job_config = bigquery.LoadJobConfig(
            schema=schema,
            source_format=bigquery.SourceFormat.CSV,
            write_disposition=bigquery.WriteDisposition.WRITE_TRUNCATE,
            skip_leading_rows=1
        )

    with open(csv_path, "rb") as source_file:
        job = client.load_table_from_file(source_file, table_id, job_config=job_config)
        job.result()

    print(f"Carga de la tabla CODIGOS_EAN completada con éxito.")

if __name__ == "__main__":
    actualizar_tabla_codigos_barras()
