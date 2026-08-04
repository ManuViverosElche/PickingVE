import pyodbc
from google.cloud import bigquery
import pandas as pd
import os
from datetime import datetime
import csv

def actualizar_tabla_linea_pedido():
    current_year = datetime.now().year
    db_filename = f'014{current_year}.accdb'
    os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = r'V:\\DashBoard\\clave_json.json'
    db_path = f'X:\\Datos\\FS\\{db_filename}'
    csv_path = r'V:\\DashBoard\\csv\\LINEA_PEDIDO.csv'
    
    conn_str = (
        r'DRIVER={Microsoft Access Driver (*.mdb, *.accdb)};'
        r'DBQ=' + db_path + ';'
    )
    conn = pyodbc.connect(conn_str)

    query = """
    SELECT 
        TIPLPC AS SERIE_PEDIDO,
        CODLPC AS NUMERO_PEDIDO,
        POSLPC AS POSICION_PEDIDO,
        ARTLPC AS REFERENCIA_ARTICULO,
        DESLPC AS DESCRIPCION_ARTICULO,
        CANLPC AS UNIDADES,
        PENLPC AS UNIDADES_PENDIENTES,
        CE1LPC AS CODIGO_LITRAJE,
        CE2LPC AS CODIGO_SECTOR,
        FIMLPC AS MARCADO,
        NIMLPC AS IMPRIMIR_LINEA
    FROM F_LPC
    ORDER BY
        TIPLPC,
        CODLPC,
        POSLPC;
    """
    df = pd.read_sql(query, conn)

    # Asegurarse de que la columna IMPRIMIR_LINEA tenga valores enteros válidos
    df["IMPRIMIR_LINEA"] = pd.to_numeric(df["IMPRIMIR_LINEA"], errors='coerce')
    df["IMPRIMIR_LINEA"] = df["IMPRIMIR_LINEA"].fillna(0).astype(int)
    
    # Conversión de columnas numéricas a números (si aplica)
    numeric_columns = ["UNIDADES", "UNIDADES_PENDIENTES"]
    for col in numeric_columns:
        df[col] = pd.to_numeric(df[col], errors='coerce')
        if df[col].isnull().any():
            print(f"Advertencia: Hay valores no numéricos en la columna {col}.")

    # Exportar a CSV asegurando que los textos están entre comillas
    df.to_csv(csv_path, index=False, encoding='utf-8', float_format='%.2f', quoting=csv.QUOTE_ALL)

    print("Exportación de datos completada con éxito.")

    # Cargar en BigQuery
    client = bigquery.Client()
    dataset_id = 'dashboard-439511.GestionComercialVE'
    table_id = f'{dataset_id}.LINEA_PEDIDO'
    
    schema = [
        bigquery.SchemaField("SERIE_PEDIDO", "STRING", mode="NULLABLE", description="Serie del pedido"),
        bigquery.SchemaField("NUMERO_PEDIDO", "STRING", mode="NULLABLE", description="Número del pedido"),
        bigquery.SchemaField("POSICION_PEDIDO", "INTEGER", mode="NULLABLE", description="Posición en el pedido"),
        bigquery.SchemaField("REFERENCIA_ARTICULO", "STRING", mode="NULLABLE", description="Referencia del artículo"),
        bigquery.SchemaField("DESCRIPCION_ARTICULO", "STRING", mode="NULLABLE", description="Descripción del artículo"),
        bigquery.SchemaField("UNIDADES", "FLOAT", mode="NULLABLE", description="Unidades solicitadas"),
        bigquery.SchemaField("UNIDADES_PENDIENTES", "FLOAT", mode="NULLABLE", description="Unidades pendientes"),
        bigquery.SchemaField("CODIGO_LITRAJE", "STRING", mode="NULLABLE", description="Código de litraje del artículo"),
        bigquery.SchemaField("CODIGO_SECTOR", "STRING", mode="NULLABLE", description="Código del sector del artículo"),
        bigquery.SchemaField("MARCADO", "INTEGER", mode="NULLABLE", description="Línea de pedido marcada 0 = NO, <> 0 MARCADO"),
        bigquery.SchemaField("IMPRIMIR_LINEA", "INTEGER", mode="NULLABLE", description="Imprimir línea 0 = SI, 1 = NO")
    ]

    # Comprobación si la tabla ya existe y configuración del trabajo
    try:
        client.get_table(table_id)
        print(f"La tabla {table_id} ya existe. Actualizando datos...")
        job_config = bigquery.LoadJobConfig(
            schema=schema,
            source_format=bigquery.SourceFormat.CSV,
            write_disposition=bigquery.WriteDisposition.WRITE_TRUNCATE,
            skip_leading_rows=1,
            max_bad_records=10,
            field_delimiter=',',
            allow_quoted_newlines=True
        )
    except Exception as e:
        print(f"La tabla {table_id} no existe. Creando nueva tabla...")
        job_config = bigquery.LoadJobConfig(
            schema=schema,
            source_format=bigquery.SourceFormat.CSV,
            write_disposition=bigquery.WriteDisposition.WRITE_TRUNCATE,
            field_delimiter=',',
            allow_quoted_newlines=True,
            skip_leading_rows=1
        )

    with open(csv_path, "rb") as source_file:
        job = client.load_table_from_file(source_file, table_id, job_config=job_config)
        job.result()

    print(f"Carga de la tabla LINEA_PEDIDO completada con éxito.")
    conn.close()

if __name__ == "__main__":
    actualizar_tabla_linea_pedido()
