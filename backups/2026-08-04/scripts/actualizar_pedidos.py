# =============================================================================
# ETL COMPLETO: CABECERAS DE PEDIDOS (FACTUSOL -> BIGQUERY)
# =============================================================================

import pandas as pd
import pyodbc
import csv
import os
import sys
import re
from datetime import datetime
from google.cloud import bigquery

# Configuración de salida para evitar errores de caracteres en Windows
sys.stdout.reconfigure(encoding='utf-8')

# -----------------------------------------------------------------------------
# 1. CONFIGURACIÓN DE RUTAS Y ENTORNO
# -----------------------------------------------------------------------------
current_year = datetime.now().year
db_filename = f'014{current_year}.accdb'

# Rutas de origen y destino
PATH_ACCESS = f'X:\\Datos\\FS\\{db_filename}'
OUTPUT_FILE = r"V:\\DashBoard\\csv\\PEDIDOS.csv"

# Autenticación de Google Cloud
os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = r'V:\\DashBoard\\clave_json.json'

# Parámetros BigQuery
BQ_PROJECT = "dashboard-439511"
BQ_DATASET = "GestionComercialVE"
BQ_TABLE = "PEDIDOS"

# -----------------------------------------------------------------------------
# 2. CONEXIÓN ACCESS (FACTUSOL) - TU FUNCIÓN SOLICITADA
# -----------------------------------------------------------------------------
# Definimos la cadena de conexión globalmente para que sea accesible
conn_str = (
    r"DRIVER={Microsoft Access Driver (*.mdb, *.accdb)};"
    f"DBQ={PATH_ACCESS};"
)

def obtener_conexion():
    """
    Establece y retorna el objeto de conexión a la base de datos de Factusol.
    Si falla, lanza una excepción capturable.
    """
    return pyodbc.connect(conn_str)

# -----------------------------------------------------------------------------
# 3. CONSULTA SQL CON MAPEO INTEGRADO
# -----------------------------------------------------------------------------
query_pedidos = """
SELECT
    TIPPCL AS SERIE_PEDIDO,
    CODPCL AS NUMERO_PEDIDO,
    REFPCL AS REFERENCIA_PEDIDO,
    FECPCL AS FECHA_PEDIDO,
    PENPCL AS FECHA_CARGA,
    FUMPCL AS FECHA_MODIFICACION,
    HORPCL AS FECHA_CREACION,
    USUPCL AS USUARIO_CREACION,
    USMPCL AS USUARIO_MODIFICACION,
    CLIPCL AS NUMERO_CLIENTE,
    AGEPCL AS CODIGO_AGENTE,
    ESTPCL AS ESTADO_PEDIDO,
    INCPCL AS TIENE_INCIDENCIA,
    FOPPCL AS FORMA_PAGO,
    TIVPCL AS TIPO_IVA,
    BAS2PCL AS BASE_10,
    BAS4PCL AS BASE_EXENTO,
    IIVA2PCL AS IVA_10,
    TOTPCL AS TOTAL_PEDIDO,
    PDTO2PCL AS DTO_10_PCT,
    IDTO2PCL AS DTO_10_IMP,
    PDTO4PCL AS DTO_EXENTO_PCT,
    IDTO4PCL AS DTO_EXENTO_IMP,
    PPPA2PCL AS PP_10_PCT,
    IPPA2PCL AS PP_10_IMP,
    PPPA4PCL AS PP_EXENTO_PCT,
    IPPA4PCL AS PP_EXENTO_IMP,
    IPOR2PCL AS PORTES_VENTA_10,
    IPOR4PCL AS PORTES_VENTA_EXENTO,
    TPOPCL AS SECTOR_CARGA,
    PPOPCL AS FINCA_CARGA,
    OB1PCL AS OBSERVACIONES,
    OB2PCL AS MARCA_PEDIDO,
    PRIPCL AS NOTA_PRIVADA,
    COMPCL AS NOTAS_PEDIDO,
    PRTPCL AS MODO_PORTES
FROM F_PCL
"""

# -----------------------------------------------------------------------------
# 4. ESQUEMA DE BIGQUERY
# -----------------------------------------------------------------------------
PEDIDOS_SCHEMA = [
    bigquery.SchemaField("SERIE_PEDIDO", "STRING", description="Serie del pedido"),
    bigquery.SchemaField("NUMERO_PEDIDO", "STRING", description="Número de pedido"),
    bigquery.SchemaField("REFERENCIA_PEDIDO", "STRING", description="Referencia cliente"),
    bigquery.SchemaField("NUMERO_CLIENTE", "STRING", description="ID Cliente"),
    bigquery.SchemaField("CODIGO_AGENTE", "STRING", description="ID Agente"),
    bigquery.SchemaField("USUARIO_CREACION", "STRING", description="Usuario alta"),
    bigquery.SchemaField("USUARIO_MODIFICACION", "STRING", description="Usuario modif"),
    bigquery.SchemaField("FECHA_PEDIDO", "DATETIME", description="Fecha pedido"),
    bigquery.SchemaField("FECHA_CARGA", "DATETIME", description="Fecha carga"),
    bigquery.SchemaField("FECHA_MODIFICACION", "DATETIME", description="Fecha modif"),
    bigquery.SchemaField("FECHA_CREACION", "DATETIME", description="Fecha registro"),
    bigquery.SchemaField("ESTADO_PEDIDO", "INT64", description="Estado numérico"),
    bigquery.SchemaField("TIENE_INCIDENCIA", "INT64", description="Incidencia (0/1)"),
    bigquery.SchemaField("FORMA_PAGO", "STRING", description="Forma pago"),
    bigquery.SchemaField("TIPO_IVA", "STRING", description="Tipo IVA"),
    bigquery.SchemaField("BASE_10", "NUMERIC", description="Base 10%"),
    bigquery.SchemaField("BASE_EXENTO", "NUMERIC", description="Base exenta"),
    bigquery.SchemaField("IVA_10", "NUMERIC", description="IVA 10%"),
    bigquery.SchemaField("TOTAL_PEDIDO", "NUMERIC", description="Total pedido"),
    bigquery.SchemaField("DTO_10_PCT", "NUMERIC", description="% Dto 10%"),
    bigquery.SchemaField("DTO_10_IMP", "NUMERIC", description="Imp Dto 10%"),
    bigquery.SchemaField("DTO_EXENTO_PCT", "NUMERIC", description="% Dto exento"),
    bigquery.SchemaField("DTO_EXENTO_IMP", "NUMERIC", description="Imp Dto exento"),
    bigquery.SchemaField("PP_10_PCT", "NUMERIC", description="% PP 10%"),
    bigquery.SchemaField("PP_10_IMP", "NUMERIC", description="Imp PP 10%"),
    bigquery.SchemaField("PP_EXENTO_PCT", "NUMERIC", description="% PP exento"),
    bigquery.SchemaField("PP_EXENTO_IMP", "NUMERIC", description="Imp PP exento"),
    bigquery.SchemaField("PORTES_VENTA_10", "NUMERIC", description="Portes 10%"),
    bigquery.SchemaField("PORTES_VENTA_EXENTO", "NUMERIC", description="Portes exento"),
    bigquery.SchemaField("SECTOR_CARGA", "STRING", description="Sector logística"),
    bigquery.SchemaField("FINCA_CARGA", "STRING", description="Finca logística"),
    bigquery.SchemaField("OBSERVACIONES", "STRING", description="Notas preparación"),
    bigquery.SchemaField("MARCA_PEDIDO", "STRING", description="Marca pedido"),
    bigquery.SchemaField("MODO_PORTES", "INT64", description="0-Debido / 1-Pagado"),
    bigquery.SchemaField("COSTE_TRANSPORTE_COMPRA", "NUMERIC", description="Coste real camión (Nota)"),
    bigquery.SchemaField("TRANSPORTE_INCLUIDO_30", "NUMERIC", description="30% calculado del total"),
    bigquery.SchemaField("MARGEN_LOGISTICO_NETO", "NUMERIC", description="Rendimiento transporte"),
    bigquery.SchemaField("REPERCUSION_TRANSPORTE_PCT", "NUMERIC", description="% coste s/ total"),
    bigquery.SchemaField("NOTAS_PEDIDO", "STRING", description="Notas del pedido"),
    bigquery.SchemaField("NOTA_PRIVADA", "STRING", description="Nota privada completa")
]

# -----------------------------------------------------------------------------
# 5. LÓGICA DE LIMPIEZA Y CÁLCULOS ANALÍTICOS
# -----------------------------------------------------------------------------

def extraer_coste_nota(texto):
    if not texto: return 0.0
    match = re.search(r'(?:transporte:?|precio:?)\s*([\d.,]+)', texto.lower())
    if match:
        num_str = match.group(1).replace('.', '').replace(',', '.')
        try: return float(num_str)
        except: return 0.0
    return 0.0

def limpiar_datos(df):
    # --- PROCESAMIENTO DE FECHA_CARGA (Texto manual dd/mm/yyyy) ---
    df['FECHA_CARGA'] = df['FECHA_CARGA'].astype(str).str.strip()
    # Validación estricta del formato que tú escribes manualmente
    mask_carga = df['FECHA_CARGA'].str.match(r'^\d{2}/\d{2}/\d{4}$', na=False)
    df.loc[~mask_carga, 'FECHA_CARGA'] = None
    
    # Convertimos a datetime para poder operar, pero sin perder el rastro
    df['FECHA_CARGA'] = pd.to_datetime(df['FECHA_CARGA'], format='%d/%m/%Y', errors='coerce')

    # --- OTRAS FECHAS ---
    df['FECHA_PEDIDO'] = pd.to_datetime(df['FECHA_PEDIDO'], errors='coerce')
    df['FECHA_MODIFICACION'] = pd.to_datetime(df['FECHA_MODIFICACION'], errors='coerce')
    df['FECHA_CREACION_RAW'] = pd.to_datetime(df['FECHA_CREACION'], errors='coerce')
    
    # Reconstruir FECHA_CREACION con la hora real
    df['FECHA_CREACION'] = df.apply(
        lambda x: pd.Timestamp.combine(x['FECHA_PEDIDO'].date(), x['FECHA_CREACION_RAW'].time()) 
        if pd.notnull(x['FECHA_PEDIDO']) and pd.notnull(x['FECHA_CREACION_RAW']) else x['FECHA_PEDIDO'], axis=1
    )

    # --- CÁLCULOS ANALÍTICOS (Precisión total antes de redondear) ---
    df['COSTE_TRANSPORTE_COMPRA'] = df['NOTA_PRIVADA'].apply(extraer_coste_nota)
    
    # Corregido: MODO_PORTES == 0 para el cálculo del 30%
    df['TRANSPORTE_INCLUIDO_30'] = df.apply(
        lambda x: (x['TOTAL_PEDIDO'] * 0.30) if x['MODO_PORTES'] == 0 else (x['PORTES_VENTA_10'] + x['PORTES_VENTA_EXENTO']), 
        axis=1
    )
    
    df['MARGEN_LOGISTICO_NETO'] = df['TRANSPORTE_INCLUIDO_30'] - df['COSTE_TRANSPORTE_COMPRA']
    
    # Repercusión calculada con valores brutos
    df['REPERCUSION_TRANSPORTE_PCT'] = df.apply(
        lambda x: (x['COSTE_TRANSPORTE_COMPRA'] / x['TOTAL_PEDIDO'] * 100) if x['TOTAL_PEDIDO'] > 0 else 0, 
        axis=1
    )

    # --- FORMATEO FINAL ---
    # 1. Redondeo de números (Despues de los cálculos)
    num_cols = [f.name for f in PEDIDOS_SCHEMA if f.field_type in ["NUMERIC", "INT64"]]
    for col in num_cols:
        df[col] = pd.to_numeric(df[col], errors="coerce").fillna(0).round(2)

    # 2. ELIMINAR HORAS DEFINITIVAMENTE (Forzando a String YYYY-MM-DD)
    # Al usar .astype(str).str[:10] cortamos cualquier T00:00:00 que intente añadir Pandas
    df['FECHA_PEDIDO'] = df['FECHA_PEDIDO'].dt.strftime('%Y-%m-%d').astype(str).str[:10]
    df['FECHA_CARGA'] = df['FECHA_CARGA'].dt.strftime('%Y-%m-%d').astype(str).str[:10]
    
    # Estas mantienen la hora
    df['FECHA_MODIFICACION'] = df['FECHA_MODIFICACION'].dt.strftime("%Y-%m-%d %H:%M:%S")
    df['FECHA_CREACION'] = df['FECHA_CREACION'].dt.strftime("%Y-%m-%d %H:%M:%S")

    # 3. Limpieza de textos y reemplazo de nulos de Pandas para el CSV
    df = df.replace(['NaT', 'nan', 'None', 'NaN'], None)
    
    txt_cols = [f.name for f in PEDIDOS_SCHEMA if f.field_type == "STRING"]
    for col in txt_cols:
        df[col] = df[col].fillna("").astype(str).str.replace(r'[\n\r"]', ' ', regex=True).str.strip()

    return df[[f.name for f in PEDIDOS_SCHEMA]]

# -----------------------------------------------------------------------------
# 6. FUNCIÓN PRINCIPAL (EJECUCIÓN)
# -----------------------------------------------------------------------------

def actualizar_tabla_pedidos():
    print(f"[{datetime.now()}] --- INICIANDO PROCESO ---")
    
    try:
        # LLAMADA A TU FUNCIÓN DE CONEXIÓN
        print(f"Abriendo conexion con: {PATH_ACCESS}")
        conn = obtener_conexion()
        
        print("Extrayendo datos de Factusol...")
        df_raw = pd.read_sql(query_pedidos, conn)
        conn.close() # Cerramos conexión después de extraer
        
        print(f"Procesando {len(df_raw)} registros...")
        df_final = limpiar_datos(df_raw)
        
        # Guardado en CSV
        os.makedirs(os.path.dirname(OUTPUT_FILE), exist_ok=True)
        df_final.to_csv(OUTPUT_FILE, index=False, encoding="utf-8", quoting=csv.QUOTE_NONNUMERIC)
        
        # Carga a BigQuery
        print("Sincronizando con BigQuery...")
        client = bigquery.Client()
        table_id = f"{BQ_PROJECT}.{BQ_DATASET}.{BQ_TABLE}"
        job_config = bigquery.LoadJobConfig(
            schema=PEDIDOS_SCHEMA,
            source_format=bigquery.SourceFormat.CSV,
            skip_leading_rows=1,
            write_disposition="WRITE_TRUNCATE"
        )

        with open(OUTPUT_FILE, "rb") as f:
            job = client.load_table_from_file(f, table_id, job_config=job_config)
        
        job.result()
        print(f"[{datetime.now()}] --- PROCESO COMPLETADO CON EXITO ---")

    except Exception as e:
        print(f"!!! ERROR DETECTADO: {str(e)}")

if __name__ == "__main__":
    actualizar_tabla_pedidos()