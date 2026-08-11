"""Reparto de acopios tras un desglose de linea en Factusol.

Situacion: una linea del pedido (la "madre") se ha desglosado en varias
lineas ("hijas") y se ha eliminado del pedido. Los registros de acopio
(pickingve.picking_registros) siguen apuntando a la huella de la madre,
asi que las hijas aparecen sin acopiar en la app.

Este script MUEVE (no copia) los registros de acopio de la madre a las
hijas, con las cantidades que la oficina indique, y actualiza
ref_original / ref_servida con la referencia de cada hija (la madre ya no
existe en el pedido de Factusol). Lo que no se reparta se queda en la
madre como historico. Ademas actualiza FECHA_MODIFICACION del pedido para
que la app lo vuelva a sincronizar y muestre el cambio.

USO (siempre en dry-run primero):
    python repartir_acopio.py --pedido 260858 --madre ID-436E3195FF ^
        --reparto "ID-B9EFE7336B:8,ID-71969709B7:8,ID-778C87C7D2:3,ID-F99CDA5A13:2"
    python repartir_acopio.py --pedido 260858 --madre ID-436E3195FF ^
        --reparto "ID-B9EFE7336B:8,ID-71969709B7:8,ID-778C87C7D2:3,ID-F99CDA5A13:2" --aplicar

Para localizar huellas:
    python repartir_acopio.py --pedido 260858
"""
import argparse
import os
import sys
import uuid

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

from core.config import load_settings  # noqa: E402
from core.bigquery_client import build_client  # noqa: E402

REG_DATASET = "pickingve"
LPC_DATASET = "GestionComercialVE"


def esc(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "TRUE" if v else "FALSE"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("'", "\\'") + "'"


def q(client, sql):
    rows = []
    for r in client.query(sql):
        rows.append(dict(r))
    return rows


def main():
    ap = argparse.ArgumentParser(description="Reparto de acopios tras desglose de linea")
    ap.add_argument("--pedido", required=True, help="Numero de pedido, p.ej. 260858")
    ap.add_argument("--madre", help="Huella de la linea desglosada (sin ella solo lista lineas)")
    ap.add_argument("--reparto", help="Lista huella:cantidad separada por comas, p.ej. ID-X:8,ID-Y:3")
    ap.add_argument("--aplicar", action="store_true", help="Aplica los cambios (por defecto es dry-run)")
    args = ap.parse_args()

    s = load_settings()
    project = s["bigquery"]["project_id"]
    client = build_client(s)
    reg = f"{project}.{REG_DATASET}.picking_registros"
    lpc = f"{project}.{LPC_DATASET}.LINEA_PEDIDO"
    pedidos = f"{project}.{LPC_DATASET}.PEDIDOS"

    lineas = q(client, f"""
        SELECT HUELLA_DIGITAL AS huella, REFERENCIA_ARTICULO AS ref,
               DESCRIPCION_ARTICULO AS descr, LINEA_ACTIVA AS activa
        FROM `{lpc}` WHERE NUMERO_PEDIDO = {esc(args.pedido)}
    """)
    acopios = {r["order_line_id"]: float(r["tot"]) for r in q(client, f"""
        SELECT order_line_id, SUM(cantidad_partida) AS tot
        FROM `{reg}` WHERE order_id = {esc(args.pedido)}
        GROUP BY order_line_id
    """)}

    if not args.madre:
        print(f"Lineas del pedido {args.pedido} ({len(lineas)}):")
        for l in lineas:
            estado = "activa" if l["activa"] else "INACTIVA"
            tot = acopios.get(l["huella"], 0)
            print(f"  {l['huella']:24} ref={l['ref']:>12} acopio={tot:>8g} [{estado}]  {l['descr'] or ''}")
        return

    if not args.reparto:
        ap.error("--reparto es obligatorio cuando se indica --madre")

    hijos = []
    for par in args.reparto.split(","):
        huella, _, cant = par.strip().partition(":")
        if not huella or not cant:
            ap.error(f"Formato de reparto invalido: {par!r} (esperado HUELLA:cantidad)")
        hijos.append((huella, float(cant)))

    registros = q(client, f"""
        SELECT record_id, picking_numero, picking_tipo, ean_escaneado, ocr_texto,
               ref_original, ref_servida, sustituido, litros, medida, calibre,
               cantidad_partida, fecha_hora, empleado_email, empleado_nombre
        FROM `{reg}`
        WHERE order_id = {esc(args.pedido)} AND order_line_id = {esc(args.madre)}
        ORDER BY fecha_hora
    """)
    total_madre = sum(float(r["cantidad_partida"]) for r in registros)
    total_reparto = sum(c for _, c in hijos)

    if not registros:
        print(f"La madre {args.madre} no tiene registros de acopio en {args.pedido}. Nada que repartir.")
        return
    if total_reparto > total_madre + 1e-9:
        print(f"ERROR: el reparto ({total_reparto:g}) supera lo acopiado ({total_madre:g}).")
        sys.exit(1)

    refs = {l["huella"]: l["ref"] for l in lineas}
    for huella, _ in hijos:
        if huella not in refs:
            print(f"AVISO: la huella {huella} no existe en LINEA_PEDIDO del pedido {args.pedido}.")
            print("  Comprueba las huellas con: python repartir_acopio.py --pedido " + args.pedido)
            sys.exit(1)

    sobrante = total_madre - total_reparto
    print(f"Pedido {args.pedido} | madre {args.madre} | acopiado {total_madre:g}")
    print(f"Reparto ({total_reparto:g}):")
    for huella, c in hijos:
        print(f"  -> {huella}  {c:g} uds  (ref {refs[huella]})")
    if sobrante > 1e-9:
        print(f"  -> queda en la madre (historico): {sobrante:g} uds")
    else:
        print("  -> la madre queda a 0 (la eliminara el proximo ETL)")

    if not args.aplicar:
        print("\n(dry-run: no se ha tocado nada. Anade --aplicar para ejecutar)")
        return

    respuesta = input("Confirmar reparto? (repartir): ")
    if respuesta.strip().lower() != "repartir":
        print("Cancelado.")
        return

    # Construccion de las filas nuevas (hijas) y restos de la madre
    cola = [float(r["cantidad_partida"]) for r in registros]
    idx = 0
    nuevas = []
    restos = []
    for huella, cant in hijos:
        pendiente = cant
        while pendiente > 1e-9:
            r = registros[idx]
            disponible = cola[idx]
            mover = min(pendiente, disponible)
            nuevas.append((huella, r, mover))
            cola[idx] = disponible - mover
            pendiente -= mover
            if cola[idx] <= 1e-9:
                idx += 1
    for i, r in enumerate(registros):
        if cola[i] > 1e-9:
            restos.append((r, cola[i]))

    inserts = []
    for huella, r, mover in nuevas:
        inserts.append(
            f"({esc(str(uuid.uuid4()))}, {esc(args.pedido)}, {esc(r['picking_numero'])}, "
            f"{esc(r['picking_tipo'])}, {esc(huella)}, {esc(r['ean_escaneado'])}, "
            f"{esc(r['ocr_texto'])}, {esc(refs[huella])}, {esc(refs[huella])}, FALSE, "
            f"{esc(r['litros'])}, {esc(r['medida'])}, {esc(r['calibre'])}, {esc(mover)}, "
            f"TIMESTAMP({esc(str(r['fecha_hora']))}), {esc(r['empleado_email'])}, "
            f"{esc(r['empleado_nombre'])})"
        )
    restos_sql = []
    for r, cant in restos:
        restos_sql.append(
            f"({esc(str(uuid.uuid4()))}, {esc(args.pedido)}, {esc(r['picking_numero'])}, "
            f"{esc(r['picking_tipo'])}, {esc(args.madre)}, {esc(r['ean_escaneado'])}, "
            f"{esc(r['ocr_texto'])}, {esc(r['ref_original'])}, {esc(r['ref_servida'])}, "
            f"{esc(r['sustituido'])}, {esc(r['litros'])}, {esc(r['medida'])}, "
            f"{esc(r['calibre'])}, {esc(cant)}, TIMESTAMP({esc(str(r['fecha_hora']))}), "
            f"{esc(r['empleado_email'])}, {esc(r['empleado_nombre'])})"
        )

    afectadas = {args.madre}
    afectadas.update(h for h, _ in hijos)
    update_totals = "\n".join(
        f"UPDATE `{lpc}` SET TOTAL_ACOPIADO = (SELECT IFNULL(SUM(cantidad_partida), 0) "
        f"FROM `{reg}` WHERE order_line_id = {esc(h)} AND order_id = {esc(args.pedido)}) "
        f"WHERE HUELLA_DIGITAL = {esc(h)};"
        for h in afectadas
    )

    script = "BEGIN TRANSACTION;\n"
    script += f"DELETE FROM `{reg}` WHERE order_id = {esc(args.pedido)} AND order_line_id = {esc(args.madre)};\n"
    script += f"INSERT INTO `{reg}` (record_id, order_id, picking_numero, picking_tipo, order_line_id, ean_escaneado, ocr_texto, ref_original, ref_servida, sustituido, litros, medida, calibre, cantidad_partida, fecha_hora, empleado_email, empleado_nombre) VALUES\n"
    script += ",\n".join(restos_sql + inserts) + ";\n"
    script += update_totals + "\n"
    script += (f"UPDATE `{pedidos}` SET FECHA_MODIFICACION = CURRENT_DATETIME() "
               f"WHERE NUMERO_PEDIDO = {esc(args.pedido)};\n")
    script += "COMMIT TRANSACTION;"

    client.query(script).result()
    print(f"Reparto aplicado: {len(nuevas)} registros movidos, {len(restos)} restos en la madre.")
    print("Sincroniza la app para que las hijas muestren el acopio.")


if __name__ == "__main__":
    main()
