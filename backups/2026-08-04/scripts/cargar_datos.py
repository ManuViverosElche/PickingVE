import subprocess

def ejecutar_script(script_path):
    result = subprocess.run(["python", script_path], capture_output=True, text=True)
    if result.returncode != 0:
        print(f"Error ejecutando {script_path}: {result.stderr}")
    else:
        print(f"Salida de {script_path}: {result.stdout}")

# Ruta de los scripts
ruta_scripts = "V:\\DashBoard\\scripts"

# Ejecutar scripts de actualización de tablas
ejecutar_script(f"{ruta_scripts}\\actualizar_cliente.py")
ejecutar_script(f"{ruta_scripts}\\actualizar_agente.py")
ejecutar_script(f"{ruta_scripts}\\actualizar_facturas.py")
ejecutar_script(f"{ruta_scripts}\\actualizar_albaranes.py")
ejecutar_script(f"{ruta_scripts}\\actualizar_pedidos.py")
ejecutar_script(f"{ruta_scripts}\\actualizar_linea_pedido.py")
ejecutar_script(f"{ruta_scripts}\\actualizar_articulos.py")
ejecutar_script(f"{ruta_scripts}\\actualizar_codigos_ean.py")
ejecutar_script(f"{ruta_scripts}\\actualizar_litrajes.py")
ejecutar_script(f"{ruta_scripts}\\actualizar_sectores.py")
ejecutar_script(f"{ruta_scripts}\\actualizar_formas_pago.py")
ejecutar_script(f"{ruta_scripts}\\actualizar_tarifas.py")
ejecutar_script(f"{ruta_scripts}\\actualizar_stock.py")
ejecutar_script(f"{ruta_scripts}\\actualizar_precios_venta.py")