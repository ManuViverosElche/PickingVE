import pyodbc
from google.cloud import bigquery
import pandas as pd
import os
import csv
from datetime import datetime

def actualizar_tabla_precios_venta():
    current_year = datetime.now().year
    db_filename = f'014{current_year}.accdb'
    os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = r'V:\\DashBoard\\clave_json.json'
    db_path = f'X:\\Datos\\FS\\{db_filename}'
    csv_path = r'V:\\DashBoard\\csv\\PRECIOS_VENTA.csv'

    # Conexión a Access
    conn_str = (
        r'DRIVER={Microsoft Access Driver (*.mdb, *.accdb)};'
        r'DBQ=' + db_path + ';'
    )
    conn = pyodbc.connect(conn_str)

    # Función para leer con cursor
    def fetch_query(query):
        cursor = conn.cursor()
        cursor.execute(query)
        rows = cursor.fetchall()
        columns = [column[0] for column in cursor.description]
        df = pd.DataFrame.from_records(rows, columns=columns)
        return df

    # Consulta para artículos simples
    query_lta = """
    SELECT 
        TARLTA AS CODIGO_TARIFA,
        ARTLTA AS REFERENCIA_ARTICULO,
        PRELTA AS PRECIO_VENTA,
        NULL AS CODIGO_LITRAJE,
        NULL AS CODIGO_SECTOR,
        'NO' AS ES_COMBINACION
    FROM F_LTA
    ORDER BY TARLTA, ARTLTA;
    """

    # Consulta para combinaciones
    query_ltc = """
    SELECT 
        TARLTC AS CODIGO_TARIFA,
        ARTLTC AS REFERENCIA_ARTICULO,
        PRELTC AS PRECIO_VENTA,
        CE1LTC AS CODIGO_LITRAJE,
        CE2LTC AS CODIGO_SECTOR,
        'SÍ' AS ES_COMBINACION
    FROM F_LTC
    ORDER BY TARLTC, ARTLTC;
    """

    # Leer datos
    df_lta = fetch_query(query_lta)
    df_ltc = fetch_query(query_ltc)

    #print("Filas LTA:", len(df_lta))
    #print("Filas LTC:", len(df_ltc))

    # Concatenar
    df_precios = pd.concat([df_lta, df_ltc], ignore_index=True)

    #print("Filas totales:", len(df_precios))

    # Reemplazar NaN por 0 para precios
    df_precios['PRECIO_VENTA'] = df_precios['PRECIO_VENTA'].fillna(0)

    # Exportar a CSV seguro
    df_precios.to_csv(
        csv_path,
        index=False,
        encoding='utf-8',
        quoting=csv.QUOTE_ALL,
        na_rep='0'
    )
    print("Exportación de datos completada con éxito.")

    # Cargar en BigQuery
    client = bigquery.Client()
    dataset_id = 'dashboard-439511.GestionComercialVE'
    table_id = f'{dataset_id}.PRECIOS_VENTA'

    schema = [
        bigquery.SchemaField("CODIGO_TARIFA", "STRING", mode="NULLABLE", description="Código de la tarifa"),
        bigquery.SchemaField("REFERENCIA_ARTICULO", "STRING", mode="NULLABLE", description="Referencia del artículo"),
        bigquery.SchemaField("PRECIO_VENTA", "FLOAT", mode="NULLABLE", description="Precio de venta"),
        bigquery.SchemaField("CODIGO_LITRAJE", "STRING", mode="NULLABLE", description="Código del litraje"),
        bigquery.SchemaField("CODIGO_SECTOR", "STRING", mode="NULLABLE", description="Código del sector"),
        bigquery.SchemaField("ES_COMBINACION", "STRING", mode="NULLABLE", description="Indica si es combinación o no")
    ]

    try:
        client.get_table(table_id)
        print(f"La tabla {table_id} ya existe. Actualizando datos...")
        job_config = bigquery.LoadJobConfig(
            schema=schema,
            source_format=bigquery.SourceFormat.CSV,
            skip_leading_rows=1,  # 👈 Ignorar la fila de cabecera
            write_disposition=bigquery.WriteDisposition.WRITE_TRUNCATE,
        )
    except:
        print(f"La tabla {table_id} no existe. Creándola...")
        table = bigquery.Table(table_id, schema=schema)
        client.create_table(table)
        job_config = bigquery.LoadJobConfig(
            schema=schema,
            source_format=bigquery.SourceFormat.CSV,
            skip_leading_rows=1,  # 👈 También aquí
            write_disposition=bigquery.WriteDisposition.WRITE_APPEND,
        )


    with open(csv_path, "rb") as source_file:
        job = client.load_table_from_file(source_file, table_id, job_config=job_config)
        job.result()

    print("Carga de la tabla PRECIOS_VENTA completada con éxito.")
    conn.close()

if __name__ == "__main__":
    actualizar_tabla_precios_venta()
