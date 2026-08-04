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
ejecutar_script(f"{ruta_scripts}\\actualizar_albaranes.py")
ejecutar_script(f"{ruta_scripts}\\actualizar_formas_pago.py")
