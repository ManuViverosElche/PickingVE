@echo off
REM Configurar variables de entorno
set GOOGLE_APPLICATION_CREDENTIALS=V:\DashBoard\clave_json.json

REM Ejecutar scripts de actualización
python "V:\DashBoard\scripts\cargar_datos.py"
REM python "V:\DashBoard\scripts\cargar_albaranes.py"
REM python "V:\DashBoard\scripts\cargar_articulos.py"
REM python "V:\Conector\DashBoard\scripts\cargar_facturas.py"
REM python "V:\Conector\DashBoard\scripts\actualizar_precios_venta.py"
REM python "V:\Conector\DashBoard\scripts\exportar15registros.py"
pause