"""Verifica que el driver ODBC de Access y la base Factusol son accesibles.

Uso: python check_access.py
Lee config/settings.local.yaml y prueba una conexion REAL de solo lectura.
Diagnostica el fallo tipico: driver instalado en 32 bits pero Python 64-bit
(o viceversa), dando la solucion exacta.
"""
import sys
from pathlib import Path

CONNECTOR_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(CONNECTOR_ROOT))

import pyodbc

from core.access_client import build_connection_string
from core.config import load_settings


def main() -> int:
    settings = load_settings()
    driver = settings["access"]["driver"]
    db_path = settings["access"]["db_path"]

    print(f"Python : {sys.version}")
    print(f"Driver buscado : {driver}")

    drivers = pyodbc.drivers()
    access_drivers = [d for d in drivers if "Access" in d]
    print(f"Drivers Access visibles ({len(access_drivers)}):")
    for d in access_drivers:
        print(f"  - {d}")

    if not access_drivers:
        print("\n[ERROR] Ningun driver ODBC de Access visible para este Python.")
        print("Solucion: instala 'Microsoft Access Database Engine 2016 Redistributable'")
        print("en LA MISMA arquitectura que este Python.")
        return 1

    if driver not in drivers:
        print(f"\n[ERROR] El driver configurado no esta visible para este Python.")
        print("Causa tipica: driver de 32 bits + Python de 64 bits (o al contrario).")
        print("Solucion: instala el 'Access Database Engine' que coincida con la")
        print("arquitectura del Python del .venv, o usa /quiet para forzar la otra.")
        return 1

    print(f"\nProbando conexion a: {db_path}")
    try:
        with pyodbc.connect(build_connection_string(settings)) as conn:
            cur = conn.cursor()
            cur.execute("SELECT COUNT(*) FROM F_ART")
            n = cur.fetchone()[0]
            print(f"[OK] Conexion correcta. F_ART tiene {n} articulos.")
            return 0
    except Exception as exc:
        print(f"\n[ERROR] No se pudo abrir la base: {exc}")
        if "not find" in str(exc) or "0x80004005" in str(exc):
            print("Pistas: ¿esta conectada la unidad de red (X:)? ¿la ruta es correcta?")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
