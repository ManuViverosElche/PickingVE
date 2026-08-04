import pyodbc
from google.cloud import bigquery
import pandas as pd
import os
from datetime import datetime

def actualizar_tabla_pedidos():
    current_year = datetime.now().year
    db_filename = f'014{current_year}.accdb'
    os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = r'V:\\DashBoard\\clave_json.json'
    db_path = f'X:\\Datos\\FS\\{db_filename}'
    csv_path = r'V:\\DashBoard\\csv\\PEDIDOS.csv'

    conn_str = (
        r'DRIVER={Microsoft Access Driver (*.mdb, *.accdb)};'
        r'DBQ=' + db_path + ';'
    )
    conn = pyodbc.connect(conn_str)

    query = """
    SELECT 
        TIPPCL AS SERIE_PEDIDO,
        CODPCL AS NUMERO_PEDIDO,
        REFPCL AS REFERENCIA_PEDIDO,
        FECPCL AS FECHA_PEDIDO,
        AGEPCL AS CODIGO_AGENTE,
        CLIPCL AS NUMERO_CLIENTE,
        PENPCL AS FECHA_CARGA,
        TPOPCL AS SECTOR_CARGA,
        PPOPCL AS FINCA_CARGA,
        OB1PCL AS OBSERVACIONES,
        OB2PCL AS MARCA_PEDIDO,
        ESTPCL AS ESTAD_PEDIDO
    FROM F_PCL
    ORDER BY
        TIPPCL,
        CODPCL;
    """
    df = pd.read_sql(query, conn)

    # Conversión de columnas de fecha
    df['FECHA_PEDIDO'] = pd.to_datetime(df['FECHA_PEDIDO'], errors='coerce').dt.strftime('%Y-%m-%d')
    #df['FECHA_CARGA'] = pd.to_datetime(df['FECHA_CARGA'], errors='coerce').dt.strftime('%Y-%m-%d')

    # Mostrar valores únicos en FECHA_CARGA
    print(df['FECHA_CARGA'].dropna().unique())

    # Filtrar valores que no coinciden con el formato esperado
    df_fechas_invalidas = df[df['FECHA_CARGA'].notna() & 
                            ~df['FECHA_CARGA'].str.match(r'^\d{2}/\d{2}/\d{4}$', na=False)]

    print("\nValores inválidos detectados:")
    print(df_fechas_invalidas[['FECHA_CARGA']])

    # Reemplazar valores no válidos con NaN
    df.loc[~df['FECHA_CARGA'].str.match(r'^\d{2}/\d{2}/\d{4}$', na=False), 'FECHA_CARGA'] = None

    # Convertir fechas válidas a formato YYYY-MM-DD
    df['FECHA_CARGA'] = pd.to_datetime(df['FECHA_CARGA'], format='%d/%m/%Y', errors='coerce').dt.strftime('%Y-%m-%d')

    # Contar valores nulos después de la corrección
    print(df['FECHA_CARGA'].isnull().sum(), "valores nulos después de la corrección")


    # Exportar a CSV
    df.to_csv(csv_path, index=False, encoding='utf-8', float_format='%.2f')
    print("Exportación de datos completada con éxito.")

    # Cargar en BigQuery
    client = bigquery.Client()
    dataset_id = 'dashboard-439511.GestionComercialVE'
    table_id = f'{dataset_id}.PEDIDOS'

    schema = [
        bigquery.SchemaField("SERIE_PEDIDO", "STRING", mode="NULLABLE", description="Serie del pedido"),
        bigquery.SchemaField("NUMERO_PEDIDO", "STRING", mode="NULLABLE", description="Número del pedido"),
        bigquery.SchemaField("REFERENCIA_PEDIDO", "STRING", mode="NULLABLE", description="Referencia del pedido"),
        bigquery.SchemaField("FECHA_PEDIDO", "STRING", mode="NULLABLE", description="Fecha del pedido"),
        bigquery.SchemaField("CODIGO_AGENTE", "STRING", mode="NULLABLE", description="Código del agente del pedido"),
        bigquery.SchemaField("NUMERO_CLIENTE", "STRING", mode="NULLABLE", description="Número del cliente"),
        bigquery.SchemaField("FECHA_CARGA", "STRING", mode="NULLABLE", description="Fecha de carga"),
        bigquery.SchemaField("SECTOR_CARGA", "STRING", mode="NULLABLE", description="Sector de carga"),
        bigquery.SchemaField("FINCA_CARGA", "STRING", mode="NULLABLE", description="Finca de carga"),
        bigquery.SchemaField("OBSERVACIONES", "STRING", mode="NULLABLE", description="Observaciones del pedido"),
        bigquery.SchemaField("MARCA_PEDIDO", "STRING", mode="NULLABLE", description="Marca del pedido"),
        bigquery.SchemaField("ESTADO_PEDIDO", "STRING", mode="NULLABLE", description="Estado del pedido [0 = Pendiente/1 = Pendiente Parcial/2 = Enviado/3 = Almacén]")
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

    print(f"Carga de la tabla PEDIDOS completada con éxito.")

if __name__ == "__main__":
    actualizar_tabla_pedidos()
