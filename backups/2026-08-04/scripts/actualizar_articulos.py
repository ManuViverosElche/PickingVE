import os
import csv
import pyodbc
import pandas as pd
from datetime import datetime
from google.cloud import bigquery
from google.api_core.exceptions import NotFound

def actualizar_tabla_articulos():
    # === CONFIGURACIÓN INICIAL ===
    current_year = datetime.now().year
    db_filename = f'014{current_year}.accdb'
    
    # Credenciales de Google
    os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = r'V:\\DashBoard\\clave_json.json'

    # Rutas de archivos
    db_path = f'X:\\Datos\\FS\\{db_filename}'
    csv_path = r'V:\\DashBoard\\csv\\ARTICULOS.csv'

    # === CONEXIÓN A ACCESS ===
    conn_str = (
        r'DRIVER={Microsoft Access Driver (*.mdb, *.accdb)};'
        rf'DBQ={db_path};'
    )

    try:
        conn = pyodbc.connect(conn_str)
    except pyodbc.Error as e:
        print(f"❌ Error al conectar con la base de datos Access: {e}")
        return

    # === LECTURA DE DATOS ===
    query = """
    SELECT 
        CODART AS ID_ARTICULO,
        EQUART AS GLOBALGAP,
        FAMART AS CODIGO_FAMILIA,
        DESART AS DESCRIPCION_ARTICULO,
        DEEART AS NOMBRE_CIENTIFICO,
        PCOART AS PRECIO_COMPRA,
        UBIART AS UBICACIONES_FINCAS,
        DELART AS DESCRIPCION_LARGA,
        DSCART AS DESCATALOGADO,
        EANART AS CODIGO_EAN,
        CP4ART AS FINCA_ARTICULO,
        DEWART AS DESCRIPCION_DETALLADA
    FROM F_ART
    ORDER BY CODART;
    """

    try:
        df = pd.read_sql(query, conn)
    except Exception as e:
        print(f"❌ Error al leer datos desde Access: {e}")
        conn.close()
        return

    # === LIMPIEZA DE DATOS ===
    finca_mapping = {
        'EXT 1': 'FERRIOL',
        'EXT 2': 'TORREGROSA',
        'EXT 3': 'GROWING GREEN',
        'EXT 4': 'VIVEROS AMOROS',
        'EXT 5': 'LINK & WIN',
        'CRE': 'CREVILLENTE',
        'CAR': 'CARREFOUR',
        'LHO': 'LA HOYA',
        'OFI': 'OFICINA'
    }

    def replace_finca_values(finca_string):
        """Reemplaza códigos cortos por nombres legibles, gestionando valores nulos."""
        if not isinstance(finca_string, str) or not finca_string.strip():
            return finca_string
        return ' '.join(finca_mapping.get(part.strip(), part.strip()) for part in finca_string.split())

    df['FINCA_ARTICULO'] = df['FINCA_ARTICULO'].apply(replace_finca_values)

    # === EXPORTACIÓN A CSV ===
    try:
        df.to_csv(
            csv_path,
            index=False,
            encoding='utf-8',
            float_format='%.2f',
            quoting=csv.QUOTE_ALL,
            lineterminator='\n',
            na_rep=''  # exporta NaN como vacío
        )
        print("Exportación de datos completada con éxito.")
    except Exception as e:
        print(f"Error al exportar CSV: {e}")
        conn.close()
        return

    # === CARGA EN BIGQUERY ===
    client = bigquery.Client()
    dataset_id = 'dashboard-439511.GestionComercialVE'
    table_id = f'{dataset_id}.ARTICULOS'

    schema = [
        bigquery.SchemaField("ID_ARTICULO", "STRING", description="Referencia del artículo"),
        bigquery.SchemaField("GLOBALGAP", "STRING", description="Artículo GlobalGAP"),
        bigquery.SchemaField("CODIGO_FAMILIA", "STRING", description="Código de la familia del artículo"),
        bigquery.SchemaField("DESCRIPCION_ARTICULO", "STRING", description="Descripción del artículo"),
        bigquery.SchemaField("NOMBRE_CIENTIFICO", "STRING", description="Nombre científico del artículo"),
        bigquery.SchemaField("PRECIO_COMPRA", "FLOAT", description="Precio de compra del artículo"),
        bigquery.SchemaField("UBICACIONES_FINCAS", "STRING", description="Ubicaciones en la finca"),
        bigquery.SchemaField("DESCRIPCION_LARGA", "STRING", description="Descripción larga del artículo"),
        bigquery.SchemaField("DESCATALOGADO", "INTEGER", description="Descatalogado (0 = NO, 1 = SI)"),
        bigquery.SchemaField("CODIGO_EAN", "STRING", description="Código EAN del artículo"),
        bigquery.SchemaField("FINCA_ARTICULO", "STRING", description="Finca del artículo"),
        bigquery.SchemaField("DESCRIPCION_DETALLADA", "STRING", description="Información adicional del artículo"),
    ]

    # Determinar si la tabla existe
    try:
        client.get_table(table_id)
        print(f"La tabla {table_id} ya existe. Reemplazando datos...")
        write_mode = bigquery.WriteDisposition.WRITE_TRUNCATE
    except NotFound:
        print(f"La tabla {table_id} no existe. Creando nueva tabla...")
        write_mode = bigquery.WriteDisposition.WRITE_APPEND

    job_config = bigquery.LoadJobConfig(
        schema=schema,
        source_format=bigquery.SourceFormat.CSV,
        field_delimiter=',',
        skip_leading_rows=1,
        allow_quoted_newlines=True,
        write_disposition=write_mode,
        max_bad_records=10
    )

    try:
        with open(csv_path, "rb") as source_file:
            job = client.load_table_from_file(source_file, table_id, job_config=job_config)
            job.result()
        print("Carga de la tabla ARTICULOS completada con éxito.")
    except Exception as e:
        print(f"Error al cargar datos en BigQuery: {e}")

    # Cerrar conexión
    conn.close()


if __name__ == "__main__":
    actualizar_tabla_articulos()
