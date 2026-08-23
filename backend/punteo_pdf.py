"""Genera el informe del pistoleo en PDF replicando el layout de Punteo de prueba.pdf.

Misma capa de datos que punteo_report (VIVEROS ELCHE, GGN 8438002215009)
renderizada en PDF A4 vertical listo para imprimir/archivar:
cabecera con logo arriba a la derecha en cada pagina, una tabla de Punteo por
parte, sustituciones resaltadas, Detalle y Control.
"""

import io
import os

import reportlab
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

from punteo_report import (
    DOC_PUNTEO_FINAL,
    DOC_PUNTEO_INICIAL,
    GGN,
    TABLE_ROWS,
    _load_datos,
    _num,
    _set,
)

_FONT_DIR = os.path.join(os.path.dirname(reportlab.__file__), "fonts")
pdfmetrics.registerFont(TTFont("Vera", os.path.join(_FONT_DIR, "Vera.ttf")))
pdfmetrics.registerFont(TTFont("Vera-Bold", os.path.join(_FONT_DIR, "VeraBd.ttf")))
F = "Vera"
FB = "Vera-Bold"

_LOGO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "web", "manager", "logo_extracted.png")

PAGE_W, PAGE_H = A4
MARG = 10 * mm
TITLE_H = 46 * mm
TABLE_TOP = PAGE_H - MARG - TITLE_H
ROW_H = 4.8 * mm
FOOTER_TOP = 32 * mm

_COL_T1 = [
    ("SEQ", 8 * mm, "C"),
    ("REFERENCIA", 28 * mm, "C"),
    ("*", 6 * mm, "C"),
    ("DESCRIPCIÓN", 64 * mm, "L"),
    ("TALLA", 18 * mm, "C"),
    ("SECTOR", 20 * mm, "C"),
    ("FINCA", 30 * mm, "C"),
    ("CARG.", 16 * mm, "C"),
]
COL_W = sum(c[1] for c in _COL_T1)
START_X = MARG


def _draw_table_header(c):
    x = START_X
    c.setFillColor(colors.HexColor("#4472C4"))
    c.setStrokeColor(colors.HexColor("#999999"))
    c.rect(x, TABLE_TOP - 6 * mm, COL_W, 6 * mm, stroke=1, fill=1)
    c.setFillColor(colors.white)
    c.setFont(FB, 7)
    for name, w, align in _COL_T1:
        if align == "C":
            c.drawCentredString(x + w / 2, TABLE_TOP - 4.3 * mm, name)
        else:
            c.drawString(x + 1.5 * mm, TABLE_TOP - 4.3 * mm, name)
        x += w
    c.setFillColor(colors.black)


def _draw_row(c, y, seq, ref, equiv, desc, talla, sector, finca, cant, susts=None):
    x = START_X
    c.setStrokeColor(colors.HexColor("#999999"))
    for idx, (_, w, _) in enumerate(_COL_T1):
        c.rect(x, y - ROW_H, w, ROW_H, stroke=1, fill=0)
        x += w

    vals = [
        str(seq),
        _set(ref),
        "*" if equiv else "",
        _set(desc),
        _set(talla),
        _set(sector),
        _set(finca),
        str(int(_num(cant))),
    ]
    x = START_X
    for idx, (_, w, align) in enumerate(_COL_T1):
        v = vals[idx]
        if idx == 1:
            c.setFont(FB, 7)
        else:
            c.setFont(F, 7)
        if align == "C":
            c.drawCentredString(x + w / 2, y - ROW_H + 1.3 * mm, v)
        else:
            # Cortar descripcion si es muy larga
            max_chars = int(w / (1.6 * mm))
            c.drawString(x + 1.5 * mm, y - ROW_H + 1.3 * mm, v[:max_chars])
        x += w

    cur_y = y - ROW_H
    if susts:
        for s in susts:
            c.setFillColor(colors.HexColor("#FFF2CC"))
            c.rect(START_X, cur_y - ROW_H, COL_W, ROW_H, stroke=1, fill=1)
            c.setFillColor(colors.HexColor("#8A5D00"))
            c.setFont(FB, 6.5)
            c.drawString(START_X + 10 * mm, cur_y - ROW_H + 1.3 * mm, f"↔ Sustituye a: {s}")
            c.setFillColor(colors.black)
            cur_y -= ROW_H
    return cur_y


def _draw_punteo_header(c, o, documento, page_num, total_pages):
    # Logo
    if os.path.exists(_LOGO):
        try:
            c.drawImage(_LOGO, PAGE_W - MARG - 55 * mm, PAGE_H - MARG - 26 * mm,
                        width=55 * mm, height=26 * mm, preserveAspectRatio=True, mask='auto')
        except Exception:
            pass

    # Empresa y direccion
    c.setFont(FB, 13)
    c.drawString(MARG, PAGE_H - 14 * mm, "VIVEROS ELCHE, S.L.")
    c.setFont(F, 6.8)
    c.drawString(MARG, PAGE_H - 18 * mm, "PARTIDA DE ALGORÓS, POLÍGONO 1-189-A")
    c.drawString(MARG, PAGE_H - 21.5 * mm, "03293 ELCHE / ALICANTE")
    c.drawString(MARG, PAGE_H - 25 * mm, "965483747 / B03303005 / 966635023")

    # FINCA y ZONA
    c.setFont(FB, 7.5)
    c.setStrokeColor(colors.black)
    c.rect(PAGE_W - MARG - 110 * mm, PAGE_H - 20 * mm, 22 * mm, 5.5 * mm, stroke=1, fill=0)
    c.rect(PAGE_W - MARG - 88 * mm, PAGE_H - 20 * mm, 30 * mm, 5.5 * mm, stroke=1, fill=0)
    c.drawString(PAGE_W - MARG - 108 * mm, PAGE_H - 16.5 * mm, "FINCA")
    c.setFont(F, 7.5)
    c.drawString(PAGE_W - MARG - 86 * mm, PAGE_H - 16.5 * mm, _set(o.get("FINCA_CARGA"))[:15])

    c.setFont(FB, 7.5)
    c.rect(PAGE_W - MARG - 110 * mm, PAGE_H - 26.5 * mm, 22 * mm, 5.5 * mm, stroke=1, fill=0)
    c.rect(PAGE_W - MARG - 88 * mm, PAGE_H - 26.5 * mm, 30 * mm, 5.5 * mm, stroke=1, fill=0)
    c.drawString(PAGE_W - MARG - 108 * mm, PAGE_H - 23 * mm, "ZONA")
    c.setFont(F, 7.5)
    c.drawString(PAGE_W - MARG - 86 * mm, PAGE_H - 23 * mm, _set(o.get("SECTOR_CARGA"))[:15])

    # Cliente
    c.setFont(FB, 8)
    num_cli = str(o.get("NUMERO_CLIENTE") or "")
    nom_cli = _set(o.get("N_COMERCIAL"))
    c.drawString(MARG, PAGE_H - 33 * mm, f"{num_cli}  {nom_cli}" if num_cli else nom_cli)
    c.setFont(F, 7.5)
    c.drawString(MARG, PAGE_H - 36.5 * mm, _set(o.get("N_FISCAL")))

    # Campos
    fields = [
        ("TRACTORA", _set(o.get("MATRICULA_CAMION")), 28 * mm),
        ("REMOLQUE", _set(o.get("MATRICULA_REMOLQUE")), 28 * mm),
        ("DOCUMENTO", documento, 42 * mm),
        ("NÚMERO", _set(o.get("NUMERO_PEDIDO")), 26 * mm),
        ("PÁGINA", f"{page_num} de {total_pages}", 30 * mm),
        ("FECHA", str(o.get("FECHA_CARGA") or "")[:10], 36 * mm),
    ]
    x = MARG
    c.setFont(FB, 6.5)
    for name, _, w in fields:
        c.setFillColor(colors.HexColor("#DDEBF7"))
        c.rect(x, PAGE_H - 42 * mm, w, 4 * mm, stroke=1, fill=1)
        c.setFillColor(colors.black)
        c.drawCentredString(x + w / 2, PAGE_H - 39.2 * mm, name)
        x += w

    x = MARG
    c.setFont(F, 7.5)
    for _, val, w in fields:
        c.rect(x, PAGE_H - 48 * mm, w, 6 * mm, stroke=1, fill=0)
        c.drawCentredString(x + w / 2, PAGE_H - 44.5 * mm, val)
        x += w


def _draw_punteo_footer(c, o, empleado, peso, obs, page_num):
    c.setFont(F, 6.8)
    c.drawString(MARG, FOOTER_TOP + 18 * mm, f"* Producto certificado GlobalG.A.P. GGN {GGN}   GLN {GGN}")
    c.setFont(FB, 7)
    c.drawString(MARG, FOOTER_TOP + 12 * mm, "OBSERVACIONES:")
    c.setFont(F, 7)
    c.drawString(MARG + 30 * mm, FOOTER_TOP + 12 * mm, _set(obs))
    c.rect(MARG, FOOTER_TOP + 9.5 * mm, COL_W, 5.5 * mm, stroke=1, fill=0)

    c.setFont(F, 7)
    c.drawString(MARG, FOOTER_TOP + 3 * mm, "Verificado por:")
    c.rect(MARG + 22 * mm, FOOTER_TOP + 0.5 * mm, 45 * mm, 6 * mm, stroke=1, fill=0)
    c.setFont(F, 7.5)
    c.drawString(MARG + 24 * mm, FOOTER_TOP + 2.5 * mm, _set(empleado))

    c.setFont(F, 7)
    c.drawString(MARG + 115 * mm, FOOTER_TOP + 3 * mm, "PESO DE LA CARGA (KG)")
    c.rect(MARG + 155 * mm, FOOTER_TOP + 0.5 * mm, 30 * mm, 6 * mm, stroke=1, fill=0)
    c.setFont(F, 7.5)
    c.drawString(MARG + 158 * mm, FOOTER_TOP + 2.5 * mm, f"{_num(peso):.2f}")

    c.setFont(F, 7)
    c.drawCentredString(PAGE_W / 2, 8 * mm, f"Página {page_num}")


def _draw_detalle_table(c, rows, start_y):
    headers = ["Parte", "Tipo", "Fecha / Hora", "Empleado", "Pos", "Ref. Pedida", "Ref. Servida",
               "Cant", "Sust.", "EAN", "OCR / Pasaporte", "L.", "Med.", "Cal.", "Talla", "Sec."]
    widths = [9, 8, 26, 28, 8, 20, 20, 10, 8, 20, 35, 8, 8, 8, 12, 12]
    total_w = sum(widths) * mm
    x0 = MARG
    row_h = 4.2 * mm
    c.setFillColor(colors.HexColor("#4472C4"))
    c.setStrokeColor(colors.HexColor("#999999"))
    c.rect(x0, start_y, total_w, row_h, stroke=1, fill=1)
    c.setFillColor(colors.white)
    c.setFont(FB, 5.5)
    x = x0
    for name, w in zip(headers, widths):
        c.drawCentredString(x + w * mm / 2, start_y + 1.2 * mm, name)
        x += w * mm
    c.setFillColor(colors.black)
    y = start_y - row_h
    for r in rows:
        vals = [
            str(r.get("picking_numero") or ""), str(r.get("picking_tipo") or ""),
            str(r.get("fecha_hora") or "").replace("T", " ")[:19],
            str(r.get("empleado_nombre") or "")[:15], str(r.get("POSICION") or ""),
            str(r.get("ref_original") or ""), str(r.get("ref_servida") or ""),
            str(int(_num(r.get("cantidad_partida")))), "S" if r.get("sustituido") else "",
            str(r.get("ean_escaneado") or ""), str(r.get("ocr_texto") or "")[:25],
            str(int(_num(r.get("litros")))), str(r.get("medida") or ""), str(r.get("calibre") or ""),
            str(r.get("TALLA") or "")[:8], str(r.get("SECTOR") or "")[:8],
        ]
        x = x0
        c.setFont(F, 5.5)
        for v, w in zip(vals, widths):
            c.rect(x, y, w * mm, row_h, stroke=1, fill=0)
            c.drawString(x + 0.8 * mm, y + 1.1 * mm, v)
            x += w * mm
        y -= row_h
    return y


def _draw_control_table(c, rows, sin_localizar, start_y):
    headers = ["Pos", "Ref. Pedida", "Descripción", "Talla", "Sector", "Pedido",
               "Pend.", "Pistol.", "Dif.", "Sust.", "Partes"]
    widths = [8, 22, 56, 16, 14, 12, 12, 14, 12, 10, 14]
    total_w = sum(widths) * mm
    x0 = MARG
    row_h = 4.2 * mm
    c.setFillColor(colors.HexColor("#4472C4"))
    c.setStrokeColor(colors.HexColor("#999999"))
    c.rect(x0, start_y, total_w, row_h, stroke=1, fill=1)
    c.setFillColor(colors.white)
    c.setFont(FB, 5.5)
    x = x0
    for name, w in zip(headers, widths):
        c.drawCentredString(x + w * mm / 2, start_y + 1.2 * mm, name)
        x += w * mm
    c.setFillColor(colors.black)
    y = start_y - row_h
    c.setFont(F, 5.5)
    for l in rows:
        pend = _num(l.get("UNIDADES_PENDIENTES"))
        pist = _num(l.get("ACOPIADO"))
        dif = pend - max(pist, 0)
        vals = [
            str(l.get("POSICION_PEDIDO") or ""), str(l.get("REFERENCIA_ARTICULO") or ""),
            str(l.get("DESCRIPCION_ARTICULO") or "")[:35], str(l.get("TALLA") or ""),
            str(l.get("SECTOR") or ""), str(int(_num(l.get("UNIDADES")))), str(int(pend)),
            str(int(pist)), str(int(dif)), "S" if l.get("SUSTITUIDO") else "", str(l.get("PARTES") or ""),
        ]
        x = x0
        for v, w in zip(vals, widths):
            c.rect(x, y, w * mm, row_h, stroke=1, fill=0)
            c.drawString(x + 0.8 * mm, y + 1.1 * mm, v)
            x += w * mm
        y -= row_h
    if sin_localizar:
        y -= row_h
        c.setFont(FB, 6)
        c.setFillColor(colors.HexColor("#DDEBF7"))
        c.rect(x0, y, total_w, row_h, stroke=1, fill=1)
        c.setFillColor(colors.black)
        c.drawString(x0 + 2 * mm, y + 1.1 * mm,
                     "REFERENCIAS SERVIDAS NO LOCALIZADAS EN EL CATÁLOGO 2026 (revisar)")
        y -= row_h
        for s in sin_localizar:
            c.rect(x0, y, total_w, row_h, stroke=1, fill=0)
            c.drawString(x0 + 2 * mm, y + 1.1 * mm, _set(s.get("ref_servida")))
            c.drawString(x0 + 50 * mm, y + 1.1 * mm, _set(s.get("ean_escaneado")))
            y -= row_h
    return y


def build_punteo_pdf(
    bq_client,
    project: str,
    dataset: str,
    picking_dataset: str,
    picking_table: str,
    matriculas_table: str,
    numero_pedido: str,
) -> bytes:
    """Devuelve los bytes del PDF del informe Punteo del pedido (A4 vertical)."""
    datos = _load_datos(
        bq_client, project, dataset, picking_dataset, picking_table,
        matriculas_table, numero_pedido,
    )
    o = datos["o"]
    partes = datos["partes"]
    filas = datos["filas"]
    detalle = datos["detalle"]
    control = datos["control"]
    sin_localizar = datos["sin_localizar"]
    sust_map = datos["sust_map"]
    obs = _set(o.get("OBSERVACIONES"))
    peso = 0.0  # pendiente de capturar en el cierre de parte (D-153)

    buf = io.BytesIO()
    c = canvas.Canvas(buf, pagesize=(PAGE_W, PAGE_H))
    c.setTitle(f"Informe Punteo - Pedido {numero_pedido}")

    page_counter = 1

    # --- Paginas Punteo (una por parte) --------------------------------------
    for parte in partes:
        pnum = parte["picking_numero"]
        ptype = parte["picking_tipo"]
        documento = DOC_PUNTEO_INICIAL if ptype == "I" else DOC_PUNTEO_FINAL
        empleado = _set(parte.get("EMPLEADO"))
        rows = [f for f in filas if f["picking_numero"] == pnum and f["picking_tipo"] == ptype]
        if not rows:
            continue
        total_pages = max(1, (len(rows) + TABLE_ROWS - 1) // TABLE_ROWS)
        for page_idx in range(total_pages):
            _draw_punteo_header(c, o, documento, page_idx + 1, total_pages)
            _draw_table_header(c)
            chunk = rows[page_idx * TABLE_ROWS: (page_idx + 1) * TABLE_ROWS]
            y = TABLE_TOP - 6 * mm
            for i, r in enumerate(chunk):
                susts = sust_map.get((r.get("picking_numero"), r.get("picking_tipo"), r.get("ref_servida")), [])
                y = _draw_row(c, y, page_idx * TABLE_ROWS + i + 1, r.get("ref_servida"),
                              r.get("EQUIVALENTE"), r.get("DESCRIPCION"), r.get("TALLA"),
                              r.get("SECTOR"), r.get("FINCA_ARTICULO"), r.get("CANT"), susts=susts)
            _draw_punteo_footer(c, o, empleado, peso, obs, page_counter)
            page_counter += 1
            c.showPage()

    # --- Detalle -----------------------------------------------------------
    c.setFont(FB, 11)
    c.drawString(MARG, PAGE_H - 16 * mm, f"DETALLE DEL PISTOLEO - PEDIDO {numero_pedido}")
    per_page = int((PAGE_H - MARG - 28 * mm) / (4.2 * mm))
    for i in range(0, max(1, len(detalle)), per_page):
        _draw_detalle_table(c, detalle[i:i + per_page], PAGE_H - 24 * mm)
        c.setFont(F, 7)
        c.drawCentredString(PAGE_W / 2, 8 * mm, f"Página {page_counter}")
        page_counter += 1
        c.showPage()

    # --- Control ------------------------------------------------------------
    c.setFont(FB, 11)
    c.drawString(MARG, PAGE_H - 16 * mm, f"CONTROL DE ACOPIO - PEDIDO {numero_pedido}")
    per_page_c = int((PAGE_H - MARG - 28 * mm) / (4.2 * mm))
    for i in range(0, max(1, len(control)), per_page_c):
        _draw_control_table(c, control[i:i + per_page_c], sin_localizar if i == 0 else [],
                            PAGE_H - 24 * mm)
        c.setFont(F, 7)
        c.drawCentredString(PAGE_W / 2, 8 * mm, f"Página {page_counter}")
        page_counter += 1
        c.showPage()

    c.save()
    return buf.getvalue()
