import pyodbc  # Librería para la conexión mediante drivers ODBC a bases de datos locales (Access).
import sqlalchemy as sa  # Toolkit SQL para gestionar la comunicación entre Python y motores de base de datos.
from google.cloud import bigquery  # SDK oficial de Google para interactuar con el almacén de datos BigQuery.
from google.cloud.exceptions import NotFound  # Excepción específica para manejar casos donde tablas no existen.
import pandas as pd  # Librería fundamental para la manipulación y análisis de datos mediante DataFrames.
import uuid  # Generador de identificadores únicos universales para crear nuevas huellas digitales.
import re  # Motor de expresiones regulares para la búsqueda y extracción de patrones de texto.
import os  # Interfaz para interactuar con el sistema operativo (rutas, variables de entorno).
import csv  # Módulo para definir reglas de escritura de archivos CSV (comillas, delimitadores).
from datetime import datetime  # Para la gestión de marcas de tiempo y fechas actuales.
from urllib.parse import quote_plus  # Para codificar caracteres especiales en la cadena de conexión ODBC.

# ==============================================================================
# 1. CONFIGURACIÓN E INFRAESTRUCTURA
# ==============================================================================

# Define la ruta local a la llave privada en formato JSON para la autenticación con Google Cloud.
os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = r'V:\DashBoard\clave_json.json'

# Diccionario centralizado de configuración para facilitar cambios de rutas o entornos.
CONFIG = {
    "project_id": "dashboard-439511",  # ID del proyecto en Google Cloud Console.
    "dataset_id": "GestionComercialVE",  # Nombre del conjunto de datos en BigQuery.
    "table_name": "LINEA_PEDIDO",  # Nombre físico de la tabla de destino.
    "table_id": "dashboard-439511.GestionComercialVE.LINEA_PEDIDO",  # Ruta completa (Project.Dataset.Table).
    "csv_path": r'V:\DashBoard\csv\LINEA_PEDIDO_SYNC.csv',  # Ubicación del archivo de intercambio temporal.
    "path_fs": f'X:\\Datos\\FS\\014{datetime.now().year}.accdb'  # Ruta dinámica a la base de datos de Factusol según el año actual.
}

# Definición del esquema maestro: garantiza que BigQuery mantenga la integridad de los tipos de datos.
SCHEMA_BQ = [
    bigquery.SchemaField("HUELLA_DIGITAL", "STRING", mode="REQUIRED", description="ID único persistente (SUMLPC)"),
    bigquery.SchemaField("SERIE_PEDIDO", "STRING", description="Serie del pedido (TIPLPC)"),
    bigquery.SchemaField("NUMERO_PEDIDO", "STRING", description="Número de documento (CODLPC)"),
    bigquery.SchemaField("POSICION_PEDIDO", "INTEGER", description="Orden correlativo de la línea (POSLPC)"),
    bigquery.SchemaField("REFERENCIA_ARTICULO", "STRING", description="Código de artículo (ARTLPC)"),
    bigquery.SchemaField("DESCRIPCION_ARTICULO", "STRING", description="Nombre del artículo filtrado de tags"),
    bigquery.SchemaField("UNIDADES", "FLOAT", description="Cantidad total (CANLPC)"),
    bigquery.SchemaField("UNIDADES_PENDIENTES", "FLOAT", description="Cantidad no servida (PENLPC)"),
    bigquery.SchemaField("CODIGO_LITRAJE", "STRING", description="Información de formato/litraje (CE1LPC)"),
    bigquery.SchemaField("CODIGO_SECTOR", "STRING", description="Información de ubicación/sector (CE2LPC)"),
    bigquery.SchemaField("PRECIO", "FLOAT", description="Precio unitario (PRELPC)"),
    bigquery.SchemaField("MARCADO", "BOOLEAN", description="Booleano (TRUE/FALSE) si detecta etiqueta de marcado"),
    bigquery.SchemaField("MARCA", "STRING", description="Color o marca extraída del tag [M:]"),
    bigquery.SchemaField("FINCA_RELEVADA", "STRING", description="Nombre de finca extraído del tag [F:]"),
    bigquery.SchemaField("SECTOR_RELEVADO", "STRING", description="Sector específico extraído del tag [S:]"),
    bigquery.SchemaField("UBICACION_EXTRA", "STRING", description="Ubicación detallada extraída del tag [UBI:]"),
    bigquery.SchemaField("PRIORIDAD", "STRING", description="Estado de urgencia determinado por tags"),
    bigquery.SchemaField("ACCION_LOGISTICA", "STRING", description="Instrucciones especiales del tag [OBS:]"),
    bigquery.SchemaField("NOTA_LINEA_PEDIDO", "STRING", description="Campo de notas extendidas (MEMLPC)"),
    bigquery.SchemaField("DESCRIPCION_SISTEMA", "STRING", description="Texto original de Factusol sin procesar"),
    bigquery.SchemaField("TOTAL_ACOPIADO", "FLOAT", description="Cantidad gestionada acumulada"),
    bigquery.SchemaField("LINEA_ACTIVA", "BOOLEAN", description="Estado lógico de existencia en el origen"),
    bigquery.SchemaField("IMPRIMIR_LINEA", "INTEGER", description="Flag de visibilidad en documentos (NIMLPC)"),
    bigquery.SchemaField("ULTIMA_SINCRONIZACION", "DATETIME", description="Marca temporal del proceso")
]

# ==============================================================================
# 2. FUNCIONES DE PROCESAMIENTO LÓGICO
# ==============================================================================

def extraer_tags(texto_sucio):
    if not texto_sucio:
        return {'LIMPIA': '', 'MARCADO': False, 'MARCA': '', 'FINCA': '', 'SECTOR_R': '', 'UBI': '', 'PRIO': 'NORMAL', 'OBS': ''}
    
    texto = str(texto_sucio).replace('\n', ' ').replace('\r', ' ').strip()
    res = {'MARCA': '', 'FINCA': '', 'SECTOR_R': '', 'UBI': '', 'OBS': ''}
    
    prio = 'PRIORITARIO' if '[PRIO]' in texto.upper() else ('NO PRIORIDAD' if '[NO_PRIO]' in texto.upper() else 'NORMAL')
    marcado = True if re.search(r'\[M\]|\[M:.*?\]', texto, re.IGNORECASE) else False
    
    bloques = re.findall(r'\[(.*?)\]', texto)
    
    # Este "freno" es la clave: solo para si ve un guion seguido de un tag real (Letra(s) + Dos puntos)
    # Ignora guiones que no van seguidos de una etiqueta (como el de 6/7 - FUERA)
    freno = r'(?=\s*-\s*(?:F|S|M|UBI|OBS):|$)'

    for b in bloques:
        # SECTOR (S:): 
        # Usamos (?<![A-Z]) para asegurar que la S: no es el final de OB[S:]
        # Usamos (?:^|(?<=\s-\s)) para asegurar que empieza el bloque o sigue al separador oficial
        s_m = re.search(r'(?:^|(?<=\s-\s))(?<![A-Z])S:\s*(.*?)' + freno, b, re.IGNORECASE)
        if s_m:
            res['SECTOR_R'] = s_m.group(1).strip()
        
        # OBSERVACIONES (OBS:):
        o_m = re.search(r'(?:^|(?<=\s-\s))OBS:\s*(.*?)' + freno, b, re.IGNORECASE)
        if o_m:
            res['OBS'] = o_m.group(1).strip()

        # UBICACION EXTRA (UBI:):
        o_m = re.search(r'(?:^|(?<=\s-\s))UBI:\s*(.*?)' + freno, b, re.IGNORECASE)
        if o_m:
            res['UBI'] = o_m.group(1).strip()

        # FINCA (F:):
        f_m = re.search(r'(?:^|(?<=\s-\s))F:\s*(.*?)' + freno, b, re.IGNORECASE)
        if f_m:
            res['FINCA'] = f_m.group(1).strip()
        
        # MARCA (M:):
        m_m = re.search(r'(?:^|(?<=\s-\s))M:\s*(.*?)' + freno, b, re.IGNORECASE)
        if m_m:
            res['MARCA'] = m_m.group(1).strip()
            marcado = True

    limpia = re.sub(r'\[.*?\]', '', texto).strip()
    return {
        'LIMPIA': re.sub(r'\s+', ' ', limpia), 
        'MARCADO': marcado, 
        **res, 
        'PRIO': prio
    }

# ==============================================================================
# 3. EL CORAZÓN DEL SANEAMIENTO (TU OPERATIVA REAL)
# ==============================================================================
def sanear_factusol_antes_de_exportar(df_raw, conn_str, client_bq):
    """Detecta duplicados, encuentra el original en BQ y corrige Factusol."""
    conteos = df_raw['SUMLPC'].value_counts()
    huellas_duplicadas = conteos[conteos > 1].index.tolist()
    
    if not huellas_duplicadas: return

    # Consultar originales en BigQuery
    ids_q = ",".join([f"'{i}'" for i in huellas_duplicadas if i and i != "None"])
    mapeo_bq = {}
    if ids_q:
        query = f"SELECT HUELLA_DIGITAL, REFERENCIA_ARTICULO FROM `{CONFIG['table_id']}` WHERE HUELLA_DIGITAL IN ({ids_q})"
        for r in client_bq.query(query):
            h = r['HUELLA_DIGITAL']
            if h not in mapeo_bq: mapeo_bq[h] = []
            mapeo_bq[h].append(str(r['REFERENCIA_ARTICULO']))

    conn_access = pyodbc.connect(conn_str)
    cursor = conn_access.cursor()
    
    for huella in huellas_duplicadas:
        lineas = df_raw[df_raw['SUMLPC'] == huella]
        original_encontrado = False
        
        for idx, fila in lineas.iterrows():
            # Si coincide con lo que ya hay en BQ, es el original. Lo respetamos una vez.
            if huella in mapeo_bq and str(fila['ARTLPC']) in mapeo_bq[huella] and not original_encontrado:
                original_encontrado = True
                continue 
            
            # Si no es el original o ya hemos encontrado uno, es un clon -> ID nuevo
            nueva_h = f"ID-{uuid.uuid4().hex[:10].upper()}"
            cursor.execute("UPDATE F_LPC SET SUMLPC = ? WHERE TIPLPC = ? AND CODLPC = ? AND POSLPC = ?", 
                         (nueva_h, fila['TIPLPC'], fila['CODLPC'], fila['POSLPC']))
    
    conn_access.commit()
    conn_access.close()

# ==============================================================================
# 4. SINCRONIZACIÓN (BIGQUERY)
# ==============================================================================
def sincronizar_bigquery(df, client):
    """Sube el CSV y ejecuta el MERGE con MARCADO como Booleano."""
    print("Fase 2: Sincronizando con BigQuery...")

    # --- AÑADIR ESTE BLOQUE AQUÍ ---
    try:
        client.get_table(CONFIG['table_id'])
    except NotFound:
        # Si no existe, la creamos usando el SCHEMA_BQ que tienes arriba
        table = bigquery.Table(CONFIG['table_id'], schema=SCHEMA_BQ)
        client.create_table(table)
        print(f"Tabla {CONFIG['table_name']} creada.")
        
        # IMPORTANTE: Esperar a que la tabla sea visible antes de seguir
        import time
        time.sleep(5)

    # 1. Forzamos que la columna MARCADO sea booleana real
    df['MARCADO'] = df['MARCADO'].astype(bool)

    # Limpieza de seguridad para el CSV
    df = df.replace(r'\n', ' ', regex=True).replace(r'\r', ' ', regex=True)
    df.to_csv(CONFIG['csv_path'], index=False, encoding='utf-8', quoting=csv.QUOTE_ALL, lineterminator='\n')

    temp_table = f"{CONFIG['table_id']}_TEMP"
    job_config = bigquery.LoadJobConfig(
        source_format="CSV",
        skip_leading_rows=1,
        write_disposition="WRITE_TRUNCATE",
        allow_quoted_newlines=True,
        schema=[
            bigquery.SchemaField(c, "BOOLEAN") if c == "MARCADO" else bigquery.SchemaField(c, "STRING") 
            for c in df.columns
        ]
    )

    with open(CONFIG['csv_path'], "rb") as f:
        client.load_table_from_file(f, temp_table, job_config=job_config).result()

    # Sentencia MERGE optimizada con limpieza de líneas vacías
    sql = f"""
        MERGE `{CONFIG['table_id']}` T 
        USING `{temp_table}` S 
        ON T.HUELLA_DIGITAL = S.HUELLA_DIGITAL
        
        WHEN MATCHED THEN 
            UPDATE SET 
                T.SERIE_PEDIDO = S.SERIE_PEDIDO, 
                T.NUMERO_PEDIDO = S.NUMERO_PEDIDO, 
                T.POSICION_PEDIDO = CAST(S.POSICION_PEDIDO AS INT64),
                T.REFERENCIA_ARTICULO = S.REFERENCIA_ARTICULO, 
                T.UNIDADES = CAST(S.UNIDADES AS FLOAT64), 
                T.PRECIO = CAST(S.PRECIO AS FLOAT64),
                T.DESCRIPCION_ARTICULO = S.DESCRIPCION_ARTICULO,
                T.MARCADO = S.MARCADO,
                T.MARCA = S.MARCA,
                T.FINCA_RELEVADA = S.FINCA_RELEVADA,
                T.SECTOR_RELEVADO = S.SECTOR_RELEVADO,
                T.UBICACION_EXTRA = S.UBICACION_EXTRA,
                T.PRIORIDAD = S.PRIORIDAD,
                T.ACCION_LOGISTICA = S.ACCION_LOGISTICA,
                T.DESCRIPCION_SISTEMA = S.DESCRIPCION_SISTEMA,
                T.LINEA_ACTIVA = TRUE,
                T.UNIDADES_PENDIENTES = CAST(S.UNIDADES_PENDIENTES AS FLOAT64),
                T.IMPRIMIR_LINEA = CAST(S.IMPRIMIR_LINEA AS INT64),
                T.ULTIMA_SINCRONIZACION = CAST(S.ULTIMA_SINCRONIZACION AS DATETIME)
        
        WHEN NOT MATCHED THEN 
            INSERT (HUELLA_DIGITAL, SERIE_PEDIDO, NUMERO_PEDIDO, POSICION_PEDIDO, REFERENCIA_ARTICULO, DESCRIPCION_ARTICULO, UNIDADES, UNIDADES_PENDIENTES, CODIGO_LITRAJE, CODIGO_SECTOR, PRECIO, MARCADO, MARCA, FINCA_RELEVADA, SECTOR_RELEVADO, UBICACION_EXTRA, PRIORIDAD, ACCION_LOGISTICA, NOTA_LINEA_PEDIDO, DESCRIPCION_SISTEMA, TOTAL_ACOPIADO, LINEA_ACTIVA, IMPRIMIR_LINEA, ULTIMA_SINCRONIZACION)
            VALUES (S.HUELLA_DIGITAL, S.SERIE_PEDIDO, S.NUMERO_PEDIDO, CAST(S.POSICION_PEDIDO AS INT64), S.REFERENCIA_ARTICULO, S.DESCRIPCION_ARTICULO, CAST(S.UNIDADES AS FLOAT64), CAST(S.UNIDADES_PENDIENTES AS FLOAT64), S.CODIGO_LITRAJE, S.CODIGO_SECTOR, CAST(S.PRECIO AS FLOAT64), S.MARCADO, S.MARCA, S.FINCA_RELEVADA, S.SECTOR_RELEVADO, S.UBICACION_EXTRA, S.PRIORIDAD, S.ACCION_LOGISTICA, S.NOTA_LINEA_PEDIDO, S.DESCRIPCION_SISTEMA, 0.0, TRUE, CAST(S.IMPRIMIR_LINEA AS INT64), CAST(S.ULTIMA_SINCRONIZACION AS DATETIME))
        
        /* LÓGICA DE LIMPIEZA INTELIGENTE */
        WHEN NOT MATCHED BY SOURCE AND T.TOTAL_ACOPIADO > 0 THEN 
            UPDATE SET T.LINEA_ACTIVA = FALSE
            
        WHEN NOT MATCHED BY SOURCE AND T.TOTAL_ACOPIADO <= 0 THEN 
            DELETE
    """
    client.query(sql).result()
    client.delete_table(temp_table)

# ==============================================================================
# 5. MAIN
# ==============================================================================
def main():
    client = bigquery.Client()
    conn_str = f'DRIVER={{Microsoft Access Driver (*.mdb, *.accdb)}};DBQ={CONFIG["path_fs"]};'
    engine = sa.create_engine(f"access+pyodbc:///?odbc_connect={quote_plus(conn_str)}")
    
    # 1. Analizar duplicados
    with engine.connect() as conn:
        df_raw = pd.read_sql("SELECT TIPLPC, CODLPC, POSLPC, ARTLPC, SUMLPC FROM F_LPC", conn)
    
    # 2. Sanear Factusol físicamente
    sanear_factusol_antes_de_exportar(df_raw, conn_str, client)
    
    # 3. Leer datos ya limpios y procesar
    with engine.connect() as conn:
        df_limpio = pd.read_sql("SELECT * FROM F_LPC", conn)
    
    registros = []
    fecha_sincro = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    for _, f in df_limpio.iterrows():
        t = extraer_tags(f['DESLPC'])
        registros.append({
            'HUELLA_DIGITAL': str(f['SUMLPC']),
            'SERIE_PEDIDO': str(f['TIPLPC']),
            'NUMERO_PEDIDO': str(f['CODLPC']),
            'POSICION_PEDIDO': int(f['POSLPC']),
            'REFERENCIA_ARTICULO': str(f['ARTLPC']),
            'DESCRIPCION_ARTICULO': t['LIMPIA'],
            'UNIDADES': float(f['CANLPC']),
            'UNIDADES_PENDIENTES': float(f['PENLPC'] if f['PENLPC'] else 0),
            'CODIGO_LITRAJE': str(f['CE1LPC']),
            'CODIGO_SECTOR': str(f['CE2LPC']),
            'PRECIO': float(f['PRELPC']),
            'MARCADO': bool(t['MARCADO']),
            'MARCA': t['MARCA'],
            'FINCA_RELEVADA': t['FINCA'],
            'SECTOR_RELEVADO': t['SECTOR_R'],
            'UBICACION_EXTRA': t['UBI'],
            'PRIORIDAD': t['PRIO'],
            'ACCION_LOGISTICA': t['OBS'],
            'NOTA_LINEA_PEDIDO': str(f['MEMLPC'])[:500] if f['MEMLPC'] else "", 'DESCRIPCION_SISTEMA': str(f['DESLPC']),
            'TOTAL_ACOPIADO': 0.0, 
            'LINEA_ACTIVA': True, 
            'IMPRIMIR_LINEA': int(f['NIMLPC'] if f['NIMLPC'] else 0),
            'ULTIMA_SINCRONIZACION': fecha_sincro
        })

    sincronizar_bigquery(pd.DataFrame(registros), client)
    print("Todo listo. A dormir.")

if __name__ == "__main__":
    main()