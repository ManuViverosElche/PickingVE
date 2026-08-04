import pyodbc
from google.cloud import bigquery
import pandas as pd
import os
from datetime import datetime

def actualizar_tabla_agente():
    # Obtener el año vigente
    current_year = datetime.now().year
    db_filename = f'014{current_year}.accdb'

    # Configurar la variable de entorno para las credenciales de Google
    os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = r'V:\\DashBoard\\clave_json.json'

    # Ruta de la base de datos Access
    db_path = f'X:\\Datos\\FS\\{db_filename}'
    # Ruta de exportación
    csv_path = r'V:\\DashBoard\\csv\\AGENTE.csv'

    # Conexión a la base de datos Access
    conn_str = (
        r'DRIVER={Microsoft Access Driver (*.mdb, *.accdb)};'
        r'DBQ=' + db_path + ';'
    )
    conn = pyodbc.connect(conn_str)

    # Leer los datos de Access
    query = """
    SELECT 
        CODAGE AS ID_AGENTE,
        EMAAGE AS EMAIL_AGENTE,
        NOMAGE AS NOMBRE_AGENTE
    FROM F_AGE
    ORDER BY
        CODAGE;
    """
    df = pd.read_sql(query, conn)

    # Guardar los datos en un CSV
    df.to_csv(csv_path, index=False)

    print("Exportación de datos completada con éxito.")

    # Cargar los datos en BigQuery desde el archivo CSV
    client = bigquery.Client()
    dataset_id = 'dashboard-439511.GestionComercialVE'
    table_id = f'{dataset_id}.AGENTE'

    # Definir el esquema de la tabla
    schema = [
        bigquery.SchemaField("ID_AGENTE", "STRING", mode="NULLABLE", description="Código del agente"),
        bigquery.SchemaField("EMAIL_AGENTE", "STRING", mode="NULLABLE", description="E-mail del agente"),
        bigquery.SchemaField("NOMBRE_AGENTE", "STRING", mode="NULLABLE", description="Nombre del agente")
    ]

    try:
        client.get_table(table_id)
        print(f"La tabla {table_id} ya existe. Actualizando datos...")
        job_config = bigquery.LoadJobConfig(
            schema=schema,
            source_format=bigquery.SourceFormat.CSV,
            write_disposition=bigquery.WriteDisposition.WRITE_TRUNCATE,
        )
    except:
        print(f"La tabla {table_id} no existe. Creándola...")
        table = bigquery.Table(table_id, schema=schema)
        client.create_table(table)
        job_config = bigquery.LoadJobConfig(
            schema=schema,
            source_format=bigquery.SourceFormat.CSV,
            write_disposition=bigquery.WriteDisposition.WRITE_APPEND,
        )

    with open(csv_path, "rb") as source_file:
        job = client.load_table_from_file(source_file, table_id, job_config=job_config)

    job.result()
    print(f"Carga de la tabla AGENTE completada con éxito.")
    conn.close()

# Ejecutar la función
if __name__ == "__main__":
    actualizar_tabla_agente()
