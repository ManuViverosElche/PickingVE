import pyodbc
from google.cloud import bigquery
import pandas as pd
import os
from datetime import datetime
import csv
import re

def actualizar_tabla_cliente():
    # Obtener el año vigente
    current_year = datetime.now().year
    db_filename = f'014{current_year}.accdb'

    # Configurar la variable de entorno para las credenciales de Google
    os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = r'V:\\DashBoard\\clave_json.json'

    # Ruta de la base de datos Access
    db_path = f'X:\\Datos\\FS\\{db_filename}'
    # Ruta de exportación
    csv_path = r'V:\\DashBoard\\csv\\CLIENTE.csv'

    # Conexión a la base de datos Access
    conn_str = (
        r'DRIVER={Microsoft Access Driver (*.mdb, *.accdb)};'
        r'DBQ=' + db_path + ';'
    )
    conn = pyodbc.connect(conn_str)

    # Leer los datos de Access
    query = """
    SELECT 
        CODCLI AS ID_CLIENTE,
        NOFCLI AS N_FISCAL,
        NOCCLI AS N_COMERCIAL,
        DOMCLI AS DIRECCION,
        POBCLI AS CIUDAD,
        PROCLI AS PROVINCIA,
        CPOCLI AS CP,
        PAICLI AS PAIS,
        TELCLI AS TELEFONOS,
        EMACLI AS EMAIL,
        AGECLI AS C_AGENTE,
        TIVCLI AS T_IVA,
        FPACLI AS F_PAGO
    FROM F_CLI
    WHERE ESTCLI <> 3
    ORDER BY
        CODCLI;
    """
    df = pd.read_sql(query, conn)

    # 🧹 Limpiar saltos de línea y espacios "invisibles"
    df = df.replace({r'[\r\n]+': ' '}, regex=True)  # quita intros
    #df = df.applymap(lambda x: x.strip() if isinstance(x, str) else x)  # trim
    for col in df.select_dtypes(include='object'):
        df[col] = df[col].map(lambda x: x.strip() if isinstance(x, str) else x)


    # Guardar los datos en un CSV seguro para BigQuery
    df.to_csv(
        csv_path,
        index=False,
        encoding="utf-8",
        quoting=csv.QUOTE_ALL,
        lineterminator="\n"
    )

    print("Exportación de datos completada con éxito (con limpieza de intros).")

    # Cargar los datos en BigQuery desde el archivo CSV
    client = bigquery.Client()
    dataset_id = 'dashboard-439511.GestionComercialVE'
    table_id = f'{dataset_id}.CLIENTE'

    # Definir el esquema de la tabla
    schema = [
        bigquery.SchemaField("ID_CLIENTE", "STRING", mode="NULLABLE", description="Id del cliente"),
        bigquery.SchemaField("N_FISCAL", "STRING", mode="NULLABLE", description="Nombre fiscal del cliente"),
        bigquery.SchemaField("N_COMERCIAL", "STRING", mode="NULLABLE", description="Nombre comercial del cliente"),
        bigquery.SchemaField("DIRECCION", "STRING", mode="NULLABLE", description="Dirección del cliente"),
        bigquery.SchemaField("CIUDAD", "STRING", mode="NULLABLE", description="Ciudad del cliente"),
        bigquery.SchemaField("PROVINCIA", "STRING", mode="NULLABLE", description="Provincia del cliente"),
        bigquery.SchemaField("CP", "STRING", mode="NULLABLE", description="Código Postal del cliente"),
        bigquery.SchemaField("PAIS", "STRING", mode="NULLABLE", description="País del cliente"),
        bigquery.SchemaField("TELEFONOS", "STRING", mode="NULLABLE", description="Número de teléfono del cliente"),
        bigquery.SchemaField("EMAIL", "STRING", mode="NULLABLE", description="E-mail del cliente"),
        bigquery.SchemaField("C_AGENTE", "STRING", mode="NULLABLE", description="Código del agente asignado al cliente"),
        bigquery.SchemaField("T_IVA", "STRING", mode="NULLABLE", description="Tipo de IVA del cliente"),
        bigquery.SchemaField("F_PAGO", "STRING", mode="NULLABLE", description="Forma de pago del cliente")
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
    print(f"Carga de la tabla CLIENTE completada con éxito.")
    conn.close()


# Ejecutar la función
if __name__ == "__main__":
    actualizar_tabla_cliente()