import pyodbc
from google.cloud import bigquery
import pandas as pd
import os
from datetime import datetime

def actualizar_tabla_albaran():
    current_year = datetime.now().year
    db_filename = f'014{current_year}.accdb'
    os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = r'V:\\DashBoard\\clave_json.json'
    db_path = f'X:\\Datos\\FS\\{db_filename}'
    csv_path = r'V:\\DashBoard\\csv\\ALBARAN.csv'

    conn_str = (
        r'DRIVER={Microsoft Access Driver (*.mdb, *.accdb)};'
        r'DBQ=' + db_path + ';'
    )
    conn = pyodbc.connect(conn_str)

    query = """
    SELECT 
        TIPALB AS TIPO_ALBARAN,
        CODALB AS CODIGO_ALBARAN,
        FECALB AS FECHA_ALBARAN,
        ESTALB AS ESTADO,
        AGEALB AS AGENTE,
        CLIALB AS CLIENTE,
        NET2ALB AS NETO_10,
        NET4ALB AS NETO_EXENTO,
        IDTO2ALB AS DESCUENTO_10,
        IDTO4ALB AS DESCUENTO_EXENTO,
        IPPA2ALB AS PRONTO_PAGO_10,
        IPPA4ALB AS PRONTO_PAGO_EXENTO,
        IPOR2ALB AS PORTE_10,
        IPOR4ALB AS PORTE_EXENTO,
        IFIN2ALB AS FINANCIACION_10,
        IFIN2ALB AS FINANCIACION_EXENTO,
        BAS2ALB AS BASE_10,
        BAS4ALB AS BASE_EXENTO,
        IIVA2ALB AS IMPORTE_IVA,
        TOTALB AS TOTAL_ALBARAN,
        FOPALB AS FORMA_PAGO
    FROM F_ALB
    ORDER BY
        TIPALB,
        CODALB;
    """
    df = pd.read_sql(query, conn)

    df['FECHA_ALBARAN'] = pd.to_datetime(df['FECHA_ALBARAN'], errors='coerce').dt.strftime('%Y-%m-%d')

    numeric_columns = [
        "NETO_10", "NETO_EXENTO", "DESCUENTO_10", "DESCUENTO_EXENTO", 
        "PRONTO_PAGO_10", "PRONTO_PAGO_EXENTO", "PORTE_10", "PORTE_EXENTO", 
        "FINANCIACION_10", "FINANCIACION_EXENTO", "BASE_10", "BASE_EXENTO", "IMPORTE_IVA"
    ]

    for col in numeric_columns:
        df[col] = pd.to_numeric(df[col], errors='coerce')
        if df[col].isnull().any():
            print(f"Advertencia: Hay valores no numéricos en la columna {col}.")

    # Agrupación
    df["NETO"] = df["NETO_10"] + df["NETO_EXENTO"]
    df["DESCUENTO"] = df["DESCUENTO_10"] + df["DESCUENTO_EXENTO"]
    df["PRONTO_PAGO"] = df["PRONTO_PAGO_10"] + df["PRONTO_PAGO_EXENTO"]
    df["PORTE"] = df["PORTE_10"] + df["PORTE_EXENTO"]
    df["FINANCIACION"] = df["FINANCIACION_10"] + df["FINANCIACION_EXENTO"]
    df["BASE"] = df["BASE_10"] + df["BASE_EXENTO"]

    df = df[["TIPO_ALBARAN", "CODIGO_ALBARAN", "FECHA_ALBARAN", "ESTADO", "AGENTE", "CLIENTE",
              "NETO", "DESCUENTO", "PRONTO_PAGO", "PORTE", "FINANCIACION", "BASE", "IMPORTE_IVA",
              "TOTAL_ALBARAN", "FORMA_PAGO"]]

    df.to_csv(csv_path, index=False, encoding='utf-8', float_format='%.2f')
    print("Exportación de datos completada con éxito.")

    client = bigquery.Client()
    dataset_id = 'dashboard-439511.GestionComercialVE'
    table_id = f'{dataset_id}.ALBARANES'

    schema = [
        bigquery.SchemaField("TIPO_ALBARAN", "STRING", mode="NULLABLE", description="Tipo de albarán"),
        bigquery.SchemaField("CODIGO_ALBARAN", "STRING", mode="NULLABLE", description="Código del albarán"),
        bigquery.SchemaField("FECHA_ALBARAN", "STRING", mode="NULLABLE", description="Fecha del albarán"),
        bigquery.SchemaField("ESTADO", "STRING", mode="NULLABLE", description="Estado del albarán"),
        bigquery.SchemaField("AGENTE", "STRING", mode="NULLABLE", description="Código del agente"),
        bigquery.SchemaField("CLIENTE", "STRING", mode="NULLABLE", description="Código del cliente"),
        bigquery.SchemaField("NETO", "FLOAT", mode="NULLABLE", description="Importe neto del albarán"),
        bigquery.SchemaField("DESCUENTO", "FLOAT", mode="NULLABLE", description="Importe de descuento del albarán"),
        bigquery.SchemaField("PRONTO_PAGO", "FLOAT", mode="NULLABLE", description="Importe de pronto pago del albarán"),
        bigquery.SchemaField("PORTE", "FLOAT", mode="NULLABLE", description="Importe de porte del albarán"),
        bigquery.SchemaField("FINANCIACION", "FLOAT", mode="NULLABLE", description="Importe de la financiación del albarán"),
        bigquery.SchemaField("BASE", "FLOAT", mode="NULLABLE", description="Base imponible del albarán"),
        bigquery.SchemaField("IMPORTE_IVA", "FLOAT", mode="NULLABLE", description="Importe del IVA"),
        bigquery.SchemaField("TOTAL_ALBARAN", "FLOAT", mode="NULLABLE", description="Total del albarán"),
        bigquery.SchemaField("FORMA_PAGO", "STRING", mode="NULLABLE", description="Código de la forma de pago")
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

    print(f"Carga de la tabla ALBARANES completada con éxito.")

if __name__ == "__main__":
    actualizar_tabla_albaran()
