import pyodbc
from google.cloud import bigquery
import pandas as pd
import os
from datetime import datetime

def actualizar_tabla_forma_pago():
    # Configurar la variable de entorno para las credenciales de Google
    os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = r'V:\\DashBoard\\clave_json.json'
    
    # Ruta de la base de datos Access
    current_year = datetime.now().year
    db_filename = f'014{current_year}.accdb'
    db_path = f'X:\\Datos\\FS\\{db_filename}'
    
    # Ruta de exportación
    csv_path = r'V:\\DashBoard\\csv\\FORMA_PAGO.csv'

    # Conexión a la base de datos Access
    conn_str = (
        r'DRIVER={Microsoft Access Driver (*.mdb, *.accdb)};'
        r'DBQ=' + db_path + ';'
    )
    conn = pyodbc.connect(conn_str)

    # Leer los datos de la tabla F_FPA
    query = """
    SELECT 
        CODFPA AS ID_FORMA_PAGO,
        DESFPA AS DESCRIPCION_FORMA_PAGO,
        VENFPA AS NUMERO_VENCIMIENTOS,
        PROFPA AS VENCIMIENTOS_PROPORCIONALES,
        DIA1FPA AS DIAS_VENCIMIENTO_1,
        DIA2FPA AS DIAS_VENCIMIENTO_2,
        DIA3FPA AS DIAS_VENCIMIENTO_3,
        DIA4FPA AS DIAS_VENCIMIENTO_4,
        DIA5FPA AS DIAS_VENCIMIENTO_5,
        DIA6FPA AS DIAS_VENCIMIENTO_6,
        PRO1FPA AS PROPORCION_PAGO_1,
        PRO2FPA AS PROPORCION_PAGO_2,
        PRO3FPA AS PROPORCION_PAGO_3,
        PRO4FPA AS PROPORCION_PAGO_4,
        PRO5FPA AS PROPORCION_PAGO_5,
        PRO6FPA AS PROPORCION_PAGO_6,
        MESFPA AS TIPO_VENCIMIENTO,
        AUDFPA AS AJUSTAR_ULTIMO_DIA,
        CCOFPA AS CONTRAPARTIDA_COBROS,
        CPAFPA AS CONTRAPARTIDA_PAGOS,
        CFEFPA AS CODIGO_FACTURA_E
    FROM F_FPA
    ORDER BY
        CODFPA;
    """
    df = pd.read_sql(query, conn)

    # Imprimir el DataFrame para verificar las columnas
    #print("Columnas en el DataFrame:", df.columns.tolist())
    #print("Datos del DataFrame:\n", df.head())  # Muestra los primeros registros para verificar

    # Asegurarse de que las columnas numéricas están en el formato correcto
    numeric_columns = [
        "NUMERO_VENCIMIENTOS", "VENCIMIENTOS_PROPORCIONALES", 
        "DIAS_VENCIMIENTO_1", "DIAS_VENCIMIENTO_2", 
        "DIAS_VENCIMIENTO_3", "DIAS_VENCIMIENTO_4", 
        "DIAS_VENCIMIENTO_5", "DIAS_VENCIMIENTO_6", 
        "PROPORCION_PAGO_1", "PROPORCION_PAGO_2", 
        "PROPORCION_PAGO_3", "PROPORCION_PAGO_4", 
        "PROPORCION_PAGO_5", "PROPORCION_PAGO_6"
    ]

    for col in numeric_columns:
        if col in df.columns:  # Verificar si la columna existe
            df[col] = pd.to_numeric(df[col], errors='coerce').fillna(0)
        else:
            print(f"Advertencia: la columna '{col}' no existe en el DataFrame.")

    # Guardar los datos en un CSV
    df.to_csv(csv_path, index=False, encoding='utf-8', float_format='%.6f')
    print("Exportación de datos completada con éxito.")

    # Cargar los datos en BigQuery
    client = bigquery.Client()
    dataset_id = 'dashboard-439511.GestionComercialVE'
    table_id = f'{dataset_id}.FORMAS_PAGO'

    # Definir el esquema de la tabla
    schema = [
        bigquery.SchemaField("ID_FORMA_PAGO", "STRING", mode="NULLABLE", description="Código de la forma de pago"),
        bigquery.SchemaField("DESCRIPCION_FORMA_PAGO", "STRING", mode="NULLABLE", description="Descripción de la forma de pago"),
        bigquery.SchemaField("NUMERO_VENCIMIENTOS", "INTEGER", mode="NULLABLE", description="Número de vencimientos"),
        bigquery.SchemaField("VENCIMIENTOS_PROPORCIONALES", "INTEGER", mode="NULLABLE", description="Vencimientos proporcionales"),
        bigquery.SchemaField("DIAS_VENCIMIENTO_1", "INTEGER", mode="NULLABLE", description="Días de vencimiento 1"),
        bigquery.SchemaField("DIAS_VENCIMIENTO_2", "INTEGER", mode="NULLABLE", description="Días de vencimiento 2"),
        bigquery.SchemaField("DIAS_VENCIMIENTO_3", "INTEGER", mode="NULLABLE", description="Días de vencimiento 3"),
        bigquery.SchemaField("DIAS_VENCIMIENTO_4", "INTEGER", mode="NULLABLE", description="Días de vencimiento 4"),
        bigquery.SchemaField("DIAS_VENCIMIENTO_5", "INTEGER", mode="NULLABLE", description="Días de vencimiento 5"),
        bigquery.SchemaField("DIAS_VENCIMIENTO_6", "INTEGER", mode="NULLABLE", description="Días de vencimiento 6"),
        bigquery.SchemaField("PROPORCION_PAGO_1", "FLOAT", mode="NULLABLE", description="Proporción de pago 1"),
        bigquery.SchemaField("PROPORCION_PAGO_2", "FLOAT", mode="NULLABLE", description="Proporción de pago 2"),
        bigquery.SchemaField("PROPORCION_PAGO_3", "FLOAT", mode="NULLABLE", description="Proporción de pago 3"),
        bigquery.SchemaField("PROPORCION_PAGO_4", "FLOAT", mode="NULLABLE", description="Proporción de pago 4"),
        bigquery.SchemaField("PROPORCION_PAGO_5", "FLOAT", mode="NULLABLE", description="Proporción de pago 5"),
        bigquery.SchemaField("PROPORCION_PAGO_6", "FLOAT", mode="NULLABLE", description="Proporción de pago 6"),
        bigquery.SchemaField("TIPO_VENCIMIENTO", "STRING", mode="NULLABLE", description="Forma de pago por días o por meses"),
        bigquery.SchemaField("AJUSTAR_ULTIMO_DIA", "BOOLEAN", mode="NULLABLE", description="Ajustar al último día del mes"),
        bigquery.SchemaField("CONTRAPARTIDA_COBROS", "STRING", mode="NULLABLE", description="Contrapartida por defecto para cobros"),
        bigquery.SchemaField("CONTRAPARTIDA_PAGOS", "STRING", mode="NULLABLE", description="Contrapartida por defecto para pagos"),
        bigquery.SchemaField("CODIGO_FACTURA_E", "STRING", mode="NULLABLE", description="Código de la forma de pago en el estándar Factura-e"),
    ]

    # Crear o actualizar la tabla en BigQuery
    try:
        client.get_table(table_id)
        print(f"La tabla {table_id} ya existe. Actualizando datos...")
        job_config = bigquery.LoadJobConfig(
            schema=schema,
            source_format=bigquery.SourceFormat.CSV,
            write_disposition=bigquery.WriteDisposition.WRITE_TRUNCATE,
            skip_leading_rows=1,  # Opción para omitir la fila de encabezado
        )
    except:
        print(f"La tabla {table_id} no existe. Creándola...")
        table = bigquery.Table(table_id, schema=schema)
        client.create_table(table)
        job_config = bigquery.LoadJobConfig(
            schema=schema,
            source_format=bigquery.SourceFormat.CSV,
            write_disposition=bigquery.WriteDisposition.WRITE_APPEND,
            skip_leading_rows=1,
        )

    with open(csv_path, "rb") as source_file:
        job = client.load_table_from_file(source_file, table_id, job_config=job_config)

    job.result()
    print(f"Carga de la tabla FORMAS_PAGO completada con éxito.")
    conn.close()

# Ejecutar la función
if __name__ == "__main__":
    actualizar_tabla_forma_pago()
