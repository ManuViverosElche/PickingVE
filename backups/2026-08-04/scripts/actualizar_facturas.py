import pyodbc
from google.cloud import bigquery
import pandas as pd
import os
from datetime import datetime

def actualizar_tabla_factura():
    current_year = datetime.now().year
    db_filename = f'014{current_year}.accdb'
    os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = r'V:\\DashBoard\\clave_json.json'
    db_path = f'X:\\Datos\\FS\\{db_filename}'
    csv_path = r'V:\\DashBoard\\csv\\FACTURA.csv'

    conn_str = (
        r'DRIVER={Microsoft Access Driver (*.mdb, *.accdb)};'
        r'DBQ=' + db_path + ';'
    )
    conn = pyodbc.connect(conn_str)

    query = """
    SELECT 
        TIPFAC AS TIPO_FACTURA,
        CODFAC AS CODIGO_FACTURA,
        FECFAC AS FECHA_FACTURA,
        ESTFAC AS ESTADO,
        AGEFAC AS AGENTE,
        CLIFAC AS CLIENTE,
        NET2FAC AS NETO_10,
        NET4FAC AS NETO_EXENTO,
        IDTO2FAC AS DESCUENTO_10,
        IDTO4FAC AS DESCUENTO_EXENTO,
        IPPA2FAC AS PRONTO_PAGO_10,
        IPPA4FAC AS PRONTO_PAGO_EXENTO,
        IPOR2FAC AS PORTE_10,
        IPOR4FAC AS PORTE_EXENTO,
        IFIN2FAC AS FINANCIACION_10,
        IFIN2FAC AS FINANCIACION_EXENTO,
        BAS2FAC AS BASE_10,
        BAS4FAC AS BASE_EXENTO,
        IIVA2FAC AS IMPORTE_IVA,
        TOTFAC AS TOTAL_FACTURA,
        FOPFAC AS FORMA_PAGO
    FROM F_FAC
    ORDER BY
        TIPFAC,
        CODFAC;
    """
    df = pd.read_sql(query, conn)

    df['FECHA_FACTURA'] = pd.to_datetime(df['FECHA_FACTURA'], errors='coerce').dt.strftime('%Y-%m-%d')

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

    df = df[["TIPO_FACTURA", "CODIGO_FACTURA", "FECHA_FACTURA", "ESTADO", "AGENTE", "CLIENTE",
              "NETO", "DESCUENTO", "PRONTO_PAGO", "PORTE", "FINANCIACION", "BASE", "IMPORTE_IVA",
              "TOTAL_FACTURA", "FORMA_PAGO"]]

    df.to_csv(csv_path, index=False, encoding='utf-8', float_format='%.2f')
    print("Exportación de datos completada con éxito.")

    client = bigquery.Client()
    dataset_id = 'dashboard-439511.GestionComercialVE'
    table_id = f'{dataset_id}.FACTURAS'

    schema = [
        bigquery.SchemaField("TIPO_FACTURA", "STRING", mode="NULLABLE", description="Tipo de factura"),
        bigquery.SchemaField("CODIGO_FACTURA", "STRING", mode="NULLABLE", description="Código de la factura"),
        bigquery.SchemaField("FECHA_FACTURA", "STRING", mode="NULLABLE", description="Fecha de la factura"),
        bigquery.SchemaField("ESTADO", "STRING", mode="NULLABLE", description="Estado de la factura"),
        bigquery.SchemaField("AGENTE", "STRING", mode="NULLABLE", description="Código del agente"),
        bigquery.SchemaField("CLIENTE", "STRING", mode="NULLABLE", description="Código del cliente"),
        bigquery.SchemaField("NETO", "FLOAT", mode="NULLABLE", description="Importe neto de la factura"),
        bigquery.SchemaField("DESCUENTO", "FLOAT", mode="NULLABLE", description="Importe de descuento de la factura"),
        bigquery.SchemaField("PRONTO_PAGO", "FLOAT", mode="NULLABLE", description="Importe de pronto pago de la factura"),
        bigquery.SchemaField("PORTE", "FLOAT", mode="NULLABLE", description="Importe de porte de la factura"),
        bigquery.SchemaField("FINANCIACION", "FLOAT", mode="NULLABLE", description="Importe de la financiacion de la factura"),
        bigquery.SchemaField("BASE", "FLOAT", mode="NULLABLE", description="Base imponible de la factura"),
        bigquery.SchemaField("IMPORTE_IVA", "FLOAT", mode="NULLABLE", description="Importe del IVA"),
        bigquery.SchemaField("TOTAL_FACTURA", "FLOAT", mode="NULLABLE", description="Total de la factura"),
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
            max_bad_records=10  # Permitir algunos errores
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
        job.result()  # Espera a que se complete la carga

    print(f"Carga de la tabla FACTURAS completada con éxito.")

if __name__ == "__main__":
    actualizar_tabla_factura()
