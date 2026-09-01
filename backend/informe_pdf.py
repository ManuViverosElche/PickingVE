"""Genera el informe del punteo en PDF replicando EXACTAMENTE el documento que
se previsualiza en el panel (informe_html.build_punteo_html, D-152/D-173).

Misma capa de datos (informe_datos._load_datos) y mismo layout que el HTML:
cabecera fiscal + cliente + cajas finca/zona/tractora/remolque/marca/referencia
+ logo y tabla Documento/Número/Fecha; tabla N.º/N.L./Referencia/*/Descripción/
Litraje/Sector/Finca/Cant.; sustituciones resaltadas; pie GGN/GLN +
Observaciones + Verificado por + Peso de la carga.
"""

import io
import os
from datetime import datetime, timedelta, timezone

import reportlab
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.pdfgen import canvas

from informe_datos import (
    GGN,
    _load_datos,
    _num,
    _set,
)

# --- Helpers propios: autosuficiente en el contenedor ---
_DIRECCION = [
    "PARTIDA DE ALGORÓS, POLÍGONO 1-189-A",
    "03293 ELCHE · ALICANTE",
    "Tel. 965483747 / 966635023",
]


def _nombre_corto(nombre) -> str:
    parts = str(nombre or "").strip().split()
    return parts[0] if parts else ""


def _fecha_es(v) -> str:
    s = str(v or "")[:10]
    try:
        return datetime.strptime(s, "%Y-%m-%d").strftime("%d/%m/%Y")
    except ValueError:
        return s


def _offset_espana(dt_utc: datetime) -> timedelta:
    """Offset CET/CEST por regla (último domingo de marzo/octubre, 01:00 UTC),
    sin depender de tzdata del sistema."""
    year = dt_utc.year

    def ultimo_domingo(mes: int) -> datetime:
        d = datetime(year, mes, 1, 1, 0, tzinfo=timezone.utc)
        d += timedelta(days=(6 - d.weekday()) % 7)
        while d.month == mes:
            nxt = d + timedelta(days=7)
            if nxt.month != mes:
                return d
            d = nxt
        return d

    inicio_cest = ultimo_domingo(3)
    fin_cest = ultimo_domingo(10)
    return timedelta(hours=2) if inicio_cest <= dt_utc < fin_cest else timedelta(hours=1)


def _dt_es(dt) -> str:
    """Datetime UTC -> 'dd/mm/yyyy HH:MM' hora de España."""
    if not dt:
        return ""
    if isinstance(dt, str):
        try:
            dt = datetime.fromisoformat(str(dt)[:19])
        except ValueError:
            return str(dt)
    if isinstance(dt, datetime):
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        local = dt.astimezone(timezone.utc) + _offset_espana(dt.astimezone(timezone.utc))
        return local.strftime("%d/%m/%Y %H:%M")
    return str(dt)


F = "Helvetica"
FB = "Helvetica-Bold"
FI = "Helvetica-Oblique"

_LOGO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "web", "manager", "viveros_logo.png")

PAGE_W, PAGE_H = A4
MARG = 10 * mm
CONTENT_W = PAGE_W - 2 * MARG

# ---- Colores corporativos (exactos del CSS HTML) ----
_CORP_DARK = colors.HexColor("#0c3a3f")
_CORP_MID = colors.HexColor("#14555c")
_CORP_TEAL = colors.HexColor("#0e8a80")
_CORP_LIME = colors.HexColor("#35b8ac")
_CORP_BG = colors.HexColor("#eef7f5")
_CORP_LINE = colors.HexColor("#b8d8d3")
_CORP_WARN_BG = colors.HexColor("#fdf3e0")
_CORP_WARN_LINE = colors.HexColor("#e0a13c")
_CORP_TEXT = colors.HexColor("#1a2b2e")
_CORP_TEXT2 = colors.HexColor("#3d565a")

# ---- Dimensiones del HTML replicadas ----
# .pie { bottom:7mm; height:34mm } → pie_bottom = 7mm, pie_top = 41mm
# .pagina { padding-bottom:44mm } → content_limit = PAGE_H - 44mm
PIE_BOTTOM = 7 * mm
PIE_HEIGHT = 34 * mm
PIE_TOP = PIE_BOTTOM + PIE_HEIGHT  # 41mm
CONTENT_LIMIT = PAGE_H - 44 * mm  # 253mm desde abajo = contenido útil

# Cabecera: flex con 3 zonas, border-bottom 2px teal, padding-bottom 3mm
HEADER_H = 40 * mm  # Alto total de la cabecera HTML
HEADER_GAP = 4 * mm  # margin-bottom del titulo

# Tabla
ROW_H = 5.2 * mm
THEAD_H = 6 * mm  # cabecera de tabla

# Columnas (mismos anchos relativos que el thead HTML)
_COLS = [
    ("N.\u00ba", 9 * mm),
    ("N.L.", 12 * mm),
    ("Referencia", 27 * mm),
    ("*", 7 * mm),
    ("Descripci\u00f3n", 55 * mm),
    ("Litraje", 19 * mm),
    ("Sector", 19 * mm),
    ("Finca", 24 * mm),
    ("Cant.", 18 * mm),
]
TABLE_W = sum(w for _, w in _COLS)


def _cortar(texto: str, ancho_mm: float, font: str = F, size: float = 7) -> str:
    max_chars = max(3, int(ancho_mm / (size * 0.155 * mm)))
    return texto[:max_chars]


def _draw_cabecera(c, o, documento, y_top, pagina=1, total_paginas=1):
    """Cabecera replicando exactamente el CSS flex del HTML:
    cabecera { display:flex; gap:5mm; border-bottom:2px solid teal; padding-bottom:3mm }
    → Zona izq (cab-fiscal 62mm) | Centro (cab-centro grid) | Der (cab-doc 52mm)"""
    y0 = y_top - HEADER_H

    # ---- Separador inferior (border-bottom 2px teal) ----
    c.setStrokeColor(_CORP_TEAL)
    c.setLineWidth(2)
    c.line(MARG, y0, PAGE_W - MARG, y0)
    c.setLineWidth(0.5)

    # ---- ZONA IZQUIERDA: cab-fiscal (62mm) ----
    fx = MARG
    fw = 62 * mm
    # Fondo .cab-fiscal: bg #eef7f5, border 1.5px corp-mid, border-radius 2mm
    c.setFillColor(_CORP_BG)
    c.setStrokeColor(_CORP_MID)
    c.setLineWidth(1.2)
    c.roundRect(fx, y0 + 3 * mm, fw, HEADER_H - 3 * mm, 2 * mm, stroke=1, fill=1)
    c.setLineWidth(0.5)

    # .empresa: 12.5pt bold, corp-dark
    c.setFillColor(_CORP_DARK)
    c.setFont(FB, 11)
    c.drawString(fx + 3 * mm, y_top - 6 * mm, "VIVEROS ELCHE, S.L.")
    # .dir: 6.5pt, #3d565a
    c.setFont(F, 6.5)
    c.setFillColor(_CORP_TEXT2)
    c.drawString(fx + 3 * mm, y_top - 10 * mm, "CIF B03303005")
    yy = y_top - 13.5 * mm
    for linea in _DIRECCION:
        c.drawString(fx + 3 * mm, yy, linea)
        yy -= 3.2 * mm

    # .cliente: border-top 1px corp-line, padding-top 1.5mm
    sep_y = yy - 1.5 * mm
    c.setStrokeColor(_CORP_LINE)
    c.setLineWidth(0.5)
    c.line(fx + 2 * mm, sep_y, fx + fw - 2 * mm, sep_y)

    cy = sep_y - 4 * mm
    codigo = _set(o.get("NUMERO_CLIENTE"))
    # .codigo: bg teal, white, rounded
    if codigo:
        cod_w = c.stringWidth(codigo, FB, 7.5) + 3 * mm
        c.setFillColor(_CORP_TEAL)
        c.roundRect(fx + 3 * mm, cy - 1 * mm, cod_w, 4 * mm, 1 * mm, stroke=0, fill=1)
        c.setFillColor(colors.white)
        c.setFont(FB, 7.5)
        c.drawString(fx + 4.5 * mm, cy, codigo)
        # .nombre: bold, corp-dark
        c.setFillColor(_CORP_DARK)
        c.setFont(FB, 7.5)
        c.drawString(fx + 3 * mm + cod_w + 2 * mm, cy, _cortar(_set(o.get("N_COMERCIAL")), fw - cod_w - 8 * mm, FB, 7.5))
    else:
        c.setFillColor(_CORP_DARK)
        c.setFont(FB, 7.5)
        c.drawString(fx + 3 * mm, cy, _cortar(_set(o.get("N_COMERCIAL")), fw - 6 * mm, FB, 7.5))
    # N_FISCAL + dirección cliente: 7.5pt, #3d565a
    c.setFont(F, 6.3)
    c.setFillColor(_CORP_TEXT2)
    cliente_dir = ", ".join(xx for xx in [_set(o.get("DIR_CLIENTE")), _set(o.get("CIUDAD_CLIENTE"))] if xx)
    fiscal_txt = _set(o.get("N_FISCAL"))
    segunda = fiscal_txt + ((" \u00b7 " + cliente_dir) if cliente_dir else "")
    c.drawString(fx + 3 * mm, cy - 3.6 * mm, _cortar(segunda, fw - 6 * mm, F, 6.3))
    c.setFillColor(colors.black)

    # ---- ZONA CENTRAL: cab-centro (grid 2 cols, gap 2mm) ----
    cx = fx + fw + 5 * mm  # gap: 5mm
    caja_w = 28 * mm
    caja_h = 10 * mm
    gap = 2 * mm
    # .caja-dato: border 1px corp-line, border-left 3px corp-teal, border-radius 1.5mm, bg white
    filas_cajas = [
        [("Finca", o.get("FINCA_CARGA")), ("Zona", o.get("SECTOR_CARGA"))],
        [("Tractora", o.get("MATRICULA_CAMION")), ("Remolque", o.get("MATRICULA_REMOLQUE"))],
    ]
    ry = y_top - caja_h - 2 * mm  # padding-top de la cabecera
    for par in filas_cajas:
        for offset, (etq, val) in enumerate(par):
            bx = cx + offset * (caja_w + gap)
            # border-left 3px teal + borde normal
            c.setStrokeColor(_CORP_LINE)
            c.setLineWidth(0.4)
            c.rect(bx, ry, caja_w, caja_h, stroke=1, fill=0)
            c.setStrokeColor(_CORP_TEAL)
            c.setLineWidth(2.5)
            c.line(bx, ry, bx, ry + caja_h)
            c.setLineWidth(0.5)
            # etiqueta: 6.5pt uppercase, corp-mid
            c.setFillColor(_CORP_MID)
            c.setFont(FB, 5.6)
            c.drawString(bx + 2 * mm, ry + caja_h - 3.2 * mm, etq.upper())
            # valor: 9.5pt bold, corp-dark
            c.setFillColor(_CORP_DARK)
            c.setFont(FB, 7.5)
            c.drawString(bx + 2 * mm, ry + 1.6 * mm, _cortar(_set(val) or "\u2014", caja_w - 4 * mm, FB, 7.5))
        ry -= caja_h + gap
    # Span 2 cols: Marca del Pedido + Referencia del Pedido
    ancho_span = caja_w * 2 + gap
    for etq, val in [("Marca del Pedido", o.get("MARCA_PEDIDO")), ("Referencia del Pedido", o.get("REFERENCIA_PEDIDO"))]:
        c.setStrokeColor(_CORP_LINE)
        c.setLineWidth(0.4)
        c.rect(cx, ry, ancho_span, caja_h, stroke=1, fill=0)
        c.setStrokeColor(_CORP_TEAL)
        c.setLineWidth(2.5)
        c.line(cx, ry, cx, ry + caja_h)
        c.setLineWidth(0.5)
        c.setFillColor(_CORP_MID)
        c.setFont(FB, 5.6)
        c.drawString(cx + 2 * mm, ry + caja_h - 3.2 * mm, etq.upper())
        c.setFillColor(_CORP_DARK)
        c.setFont(FB, 7.5)
        c.drawString(cx + 2 * mm, ry + 1.6 * mm, _cortar(_set(val) or "\u2014", ancho_span - 4 * mm, FB, 7.5))
        ry -= caja_h + gap

    # ---- ZONA DERECHA: cab-doc (52mm) → logo + tabla-doc ----
    dx = PAGE_W - MARG - 52 * mm
    dw = 52 * mm
    # Logo: .cab-logo { width:100%; max-height:22mm; object-fit:contain; margin-bottom:2mm }
    logo_h = 0
    if os.path.exists(_LOGO):
        try:
            logo_w = dw - 4 * mm
            logo_h = 16 * mm
            c.drawImage(
                _LOGO,
                dx + 2 * mm,
                y_top - logo_h - 2 * mm,
                width=logo_w,
                height=logo_h,
                preserveAspectRatio=True,
                mask="auto",
            )
        except Exception:
            pass
    # .tabla-doc: border 1px corp-mid, border-radius 1.5mm, overflow hidden
    ty = y_top - logo_h - 2 * mm - 2 * mm  # gap after logo
    fila_h = 5.4 * mm
    doc_filas = [
        ("Documento", documento),
        ("N\u00famero", _set(o.get("NUMERO_PEDIDO"))),
        ("Fecha Carga", _fecha_es(o.get("FECHA_CARGA"))),
        ("P\u00e1gina", f"{pagina} de {total_paginas}"),
    ]
    # Borde exterior
    c.setStrokeColor(_CORP_MID)
    c.setLineWidth(1)
    c.roundRect(dx, ty - len(doc_filas) * fila_h, dw, len(doc_filas) * fila_h, 1.5 * mm, stroke=1, fill=0)
    c.setLineWidth(0.5)
    for k, v in doc_filas:
        # Fila: td.k (38% bg corp-bg) + td.v (62%)
        kw = dw * 0.36
        vw = dw * 0.64
        # bg celda k
        c.setFillColor(_CORP_BG)
        c.rect(dx, ty - fila_h, kw, fila_h, stroke=0, fill=1)
        # bordes internos
        c.setStrokeColor(_CORP_LINE)
        c.setLineWidth(0.3)
        c.rect(dx, ty - fila_h, kw, fila_h, stroke=1, fill=0)
        c.rect(dx + kw, ty - fila_h, vw, fila_h, stroke=1, fill=0)
        # texto k: 6.5pt uppercase bold corp-mid
        c.setFillColor(_CORP_MID)
        c.setFont(FB, 5.6)
        c.drawString(dx + 1.5 * mm, ty - fila_h + 1.8 * mm, k.upper())
        # texto v: 7pt bold corp-dark
        c.setFillColor(_CORP_DARK)
        c.setFont(FB, 7)
        c.drawString(dx + kw + 1.5 * mm, ty - fila_h + 1.8 * mm, _cortar(v, vw - 3 * mm, FB, 7))
        ty -= fila_h

    c.setFillColor(colors.black)
    c.setStrokeColor(colors.black)
    return y0 - HEADER_GAP


def _draw_titulo(c, y, o):
    """Titulo con gradiente dark→mid (como .titulo-informe del HTML)."""
    bar_h = 8 * mm
    # Gradiente: de corp-dark a corp-mid
    steps = 30
    for i in range(steps):
        t = i / steps
        r = 0.047 + t * (0.078 - 0.047)
        g = 0.227 + t * (0.333 - 0.227)
        b = 0.247 + t * (0.361 - 0.247)
        c.setFillColorRGB(r, g, b)
        seg_w = CONTENT_W / steps
        c.rect(MARG + i * seg_w, y - bar_h, seg_w + 1, bar_h, stroke=0, fill=1)
    # Bordes redondeados (aproximar con rectángulo)
    c.setStrokeColor(_CORP_MID)
    c.setLineWidth(0.5)
    c.roundRect(MARG, y - bar_h, CONTENT_W, bar_h, 1.5 * mm, stroke=1, fill=0)

    # Texto: h2 11.5pt bold white + meta 7.5pt white 85% opacity
    c.setFillColor(colors.white)
    c.setFont(FB, 10)
    c.drawString(MARG + 3.5 * mm, y - 5.5 * mm, "DOCUMENTO DE PUNTEO")
    c.setFont(F, 7)
    meta = f"Pedido {_set(o.get('NUMERO_PEDIDO'))} \u00b7 Generado {_dt_es(datetime.now(timezone.utc))}"
    c.drawRightString(PAGE_W - MARG - 3.5 * mm, y - 5.5 * mm, meta)
    c.setFillColor(colors.black)
    return y - bar_h - 3 * mm


def _draw_tabla_header(c, y):
    """Cabecera de tabla: bg corp-dark, texto white, border-bottom 2px teal."""
    x = MARG
    c.setFillColor(_CORP_DARK)
    c.rect(x, y - THEAD_H, TABLE_W, THEAD_H, stroke=0, fill=1)
    # border-bottom teal
    c.setStrokeColor(_CORP_TEAL)
    c.setLineWidth(2)
    c.line(x, y - THEAD_H, x + TABLE_W, y - THEAD_H)
    c.setLineWidth(0.5)
    # Texto: white, 6.8pt bold uppercase
    c.setFillColor(colors.white)
    c.setFont(FB, 6)
    for name, w in _COLS:
        c.drawCentredString(x + w / 2, y - THEAD_H + 1.8 * mm, name)
        x += w
    c.setFillColor(colors.black)
    return y - THEAD_H


def _draw_fila(c, y, num, g, susts, zebra=False):
    """Fila de datos replicando el CSS del HTML:
    - Zebra: bg #eef7f5 en filas pares
    - Borde inferior: 1px corp-line
    - nl: center, bold, teal
    - ref: bold, corp-dark
    - GGN: chip verde debajo de la referencia
    - sust: bg warn-bg, border-left 3px warn-line"""
    has_ggn = bool(g.get("EQUIVALENTE"))
    # Fila principal: más alta si hay GGN (para que el badge quepa debajo de la ref)
    badge_extra = 3.5 * mm if has_ggn else 0
    row_h = ROW_H + badge_extra

    if zebra:
        c.setFillColor(_CORP_BG)
        c.rect(MARG, y - row_h, TABLE_W, row_h, stroke=0, fill=1)

    # Bordes inferiores de cada celda
    c.setStrokeColor(_CORP_LINE)
    c.setLineWidth(0.35)
    c.line(MARG, y - row_h, MARG + TABLE_W, y - row_h)
    # Bordes laterales de cada columna
    x = MARG
    for _, w in _COLS:
        c.line(x, y, x, y - row_h)
        x += w
    c.line(x, y, x, y - row_h)  # borde derecho

    # Valores de celda
    desc = _set(g.get("DESCRIPCION"))
    medida_txt = _set(g.get("MEDIDA_TXT"))
    sufijo = f" \u00b7 {medida_txt}" if medida_txt else ""
    ancho_suf = c.stringWidth(sufijo, FI, 7) if sufijo else 0
    desc_col_w = _COLS[4][1]
    disp_pt = desc_col_w - 1.4 * mm - ancho_suf
    desc_final = desc[: max(3, int(disp_pt / (7 * 0.155 * mm)))]
    vals = {
        0: (str(num), "C", F, 7),
        1: (str(g.get("POSICION") or ""), "C", F, 7),
        2: (str(g["ref_servida"] or ""), "C", FB, 7),
        3: ("*" if has_ggn else "", "C", FB, 7),
        4: (desc_final, "L", F, 7),
        5: (_set(g["TALLA"]), "C", F, 7),
        6: (_set(g["SECTOR"]), "C", F, 7),
        7: (_cortar(_set(g.get("FINCA_ARTICULO")), 22 * mm), "C", F, 7),
        8: (f"{_num(g['CANT']):,.0f}".replace(",", "."), "C", FB, 7),
    }
    for _k in list(vals):
        _v, _a, _f, _s = vals[_k]
        vals[_k] = ("" if _v is None else str(_v), _a, _f, _s)

    x = MARG
    for idx, (_, w) in enumerate(_COLS):
        v, align, font, size = vals[idx]
        # nl: teal, ref: dark, estrella: teal
        if idx == 0:
            c.setFillColor(_CORP_TEAL)
        elif idx == 2:
            c.setFillColor(_CORP_DARK)
        elif idx == 3:
            c.setFillColor(_CORP_TEAL)
        else:
            c.setFillColor(colors.black)
        c.setFont(font, size)
        # Cuando hay GGN, subir el texto de la ref para hacer sitio al chip debajo
        if has_ggn and idx == 2:
            baseline = y - ROW_H + 3 * mm
        else:
            baseline = y - row_h + 1.6 * mm
        if align == "C":
            c.drawCentredString(x + w / 2, baseline, v)
        else:
            c.drawString(x + 1.4 * mm, baseline, v)
        x += w

    # Medida en cursiva
    if sufijo:
        ancho_desc = c.stringWidth(desc_final, F, 7)
        x_desc = MARG + sum(w for _, w in _COLS[:4]) + 1.4 * mm
        c.setFont(FI, 7)
        c.setFillColor(_CORP_TEXT2)
        c.drawString(x_desc + ancho_desc, y - row_h + 1.6 * mm, sufijo)
        c.setFillColor(colors.black)

    # Chip GGN debajo de la referencia (replicando .marca-ggn del HTML)
    if has_ggn:
        ref_x = MARG + _COLS[0][1] + _COLS[1][1]
        ref_w = _COLS[2][1]
        chip_w = 10 * mm
        chip_h = 2.5 * mm
        chip_x = ref_x + (ref_w - chip_w) / 2
        # El chip va CENTRADO en la zona badge_extra (la franja inferior de la fila).
        # badge_extra = 3.5mm, chip_h = 2.5mm → margen = 0.5mm arriba y abajo.
        # chip_bottom = y - row_h + 0.5mm
        chip_y = y - row_h + 0.5 * mm
        c.setFillColor(_CORP_TEAL)
        c.roundRect(chip_x, chip_y, chip_w, chip_h, 1 * mm, stroke=0, fill=1)
        c.setFillColor(colors.white)
        c.setFont(FB, 5.5)
        c.drawCentredString(chip_x + chip_w / 2, chip_y + 0.6 * mm, "GGN")
        c.setFillColor(colors.black)

    cur_y = y - row_h
    for s in susts:
        # Fila sustitución: bg warn-bg, border-left 3px warn-line
        c.setFillColor(_CORP_WARN_BG)
        c.rect(MARG, cur_y - row_h, TABLE_W, row_h, stroke=0, fill=1)
        c.setStrokeColor(_CORP_LINE)
        c.setLineWidth(0.35)
        c.rect(MARG, cur_y - row_h, TABLE_W, row_h, stroke=1, fill=0)
        # border-left warn-line
        c.setStrokeColor(_CORP_WARN_LINE)
        c.setLineWidth(2.5)
        c.line(MARG, cur_y, MARG, cur_y - row_h)
        c.setLineWidth(0.5)
        # Texto sustitución
        c.setFillColor(_CORP_WARN_LINE)
        c.setFont(FB, 6.3)
        c.drawString(
            MARG + 3 * mm,
            cur_y - row_h + 1.5 * mm,
            _cortar(f"\u2194 Sustituci\u00f3n: {s}", TABLE_W - 6 * mm, FB, 6.3),
        )
        c.setFillColor(colors.black)
        cur_y -= row_h
    return cur_y


def _draw_pie(c, obs, empleado_txt, peso):
    """Pie replicando EXACTAMENTE el CSS del HTML:
    .pie { position:absolute; bottom:7mm; left:9mm; right:9mm; height:34mm }
    .ggn-line → .obs-box (border, bg, rounded) → .fila-final (flex: firma + peso)"""
    # Posición del pie (bottom-up): PIE_BOTTOM = 7mm
    y0 = PIE_BOTTOM

    # ---- Línea separadora superior (de .pie border-top implícito) ----
    c.setStrokeColor(_CORP_LINE)
    c.setLineWidth(0.5)
    c.line(MARG, y0 + PIE_HEIGHT, PAGE_W - MARG, y0 + PIE_HEIGHT)

    # ---- GGN line ----
    c.setFont(F, 6.6)
    c.setFillColor(_CORP_MID)
    c.drawString(MARG, y0 + PIE_HEIGHT - 5 * mm,
                 f"* Producto certificado GlobalG.A.P.   GGN {GGN}  \u00b7  GLN {GGN}")

    # ---- Observaciones (.obs-box) ----
    obs_top = y0 + PIE_HEIGHT - 7 * mm
    obs_h = 12 * mm
    obs_w = CONTENT_W * 0.62
    # border 1px corp-line, border-radius 1.5mm, bg corp-bg
    c.setFillColor(_CORP_BG)
    c.setStrokeColor(_CORP_LINE)
    c.setLineWidth(0.5)
    c.roundRect(MARG, obs_top - obs_h, obs_w, obs_h, 1.5 * mm, stroke=1, fill=1)
    # Titulo: 6.5pt uppercase bold corp-mid
    c.setFillColor(_CORP_MID)
    c.setFont(FB, 5.6)
    c.drawString(MARG + 2 * mm, obs_top - 3.2 * mm, "OBSERVACIONES")
    # Contenido
    c.setFont(F, 6.6)
    c.setFillColor(colors.black)
    obs_txt = _set(obs) or "\u2014"
    max_chars = int((obs_w - 4 * mm) / (6.6 * 0.155 * mm))
    lineas_obs = [obs_txt[i:i + max_chars] for i in range(0, len(obs_txt), max_chars)][:2]
    oy = obs_top - 6.5 * mm
    for ln in lineas_obs:
        c.drawString(MARG + 2 * mm, oy, ln)
        oy -= 3 * mm

    # ---- Fila final: Verificado por + Peso (.fila-final: flex) ----
    fila_y = y0 + 1 * mm
    fila_h = 12 * mm

    # -- Firma box (.firma-box) --
    firma_x = MARG
    firma_w = CONTENT_W - obs_w - 4 * mm  # el resto del ancho
    # border 1px corp-line, border-radius 1.5mm
    c.setStrokeColor(_CORP_LINE)
    c.setLineWidth(0.5)
    c.roundRect(firma_x, fila_y, firma_w, fila_h, 1.5 * mm, stroke=1, fill=0)
    # Titulo: 6.5pt uppercase bold corp-mid
    c.setFillColor(_CORP_MID)
    c.setFont(FB, 5.6)
    c.drawString(firma_x + 2.5 * mm, fila_y + fila_h - 3.2 * mm, "VERIFICADO POR")
    # Nombre: bold, corp-dark, centrado
    c.setFillColor(_CORP_DARK)
    c.setFont(FB, 7.5)
    nombre = _nombre_corto(empleado_txt)
    c.drawCentredString(firma_x + firma_w / 2, fila_y + 2 * mm, nombre)
    # Línea bajo el nombre
    c.setStrokeColor(colors.HexColor("#7fa8a2"))
    c.setLineWidth(0.5)
    line_y = fila_y + 4.5 * mm
    c.line(firma_x + 4 * mm, line_y, firma_x + firma_w - 4 * mm, line_y)

    # -- Peso box (.peso-box) --
    peso_x = firma_x + firma_w + 4 * mm
    peso_w = PAGE_W - MARG - peso_x
    # border 1.5px corp-mid, border-radius 1.5mm
    c.setStrokeColor(_CORP_MID)
    c.setLineWidth(1.2)
    c.roundRect(peso_x, fila_y, peso_w, fila_h, 1.5 * mm, stroke=1, fill=0)
    c.setLineWidth(0.5)
    # Titulo
    c.setFillColor(_CORP_MID)
    c.setFont(FB, 5.6)
    c.drawString(peso_x + 2.5 * mm, fila_y + fila_h - 3.2 * mm, "PESO DE LA CARGA (KG)")
    # Valor: bold, border corp-mid, bg white, centrado en caja
    c.setFillColor(colors.black)
    c.setFont(FB, 8)
    peso_txt = f"{_num(peso):,.2f}".replace(",", "@").replace(".", ",").replace("@", ".")
    # Mini caja para el valor
    val_w = c.stringWidth(peso_txt, FB, 8) + 4 * mm
    val_x = peso_x + peso_w - val_w - 2 * mm
    val_y = fila_y + 1.5 * mm
    c.setFillColor(colors.white)
    c.setStrokeColor(_CORP_MID)
    c.setLineWidth(0.5)
    c.roundRect(val_x, val_y, val_w, 5 * mm, 1 * mm, stroke=1, fill=1)
    c.setFillColor(colors.black)
    c.setFont(FB, 8)
    c.drawCentredString(val_x + val_w / 2, val_y + 1.2 * mm, peso_txt)

    c.setFillColor(colors.black)
    c.setStrokeColor(colors.black)


def _agrupar_filas_pedido(filas: list) -> list:
    """Listado correlativo único por (POSICION, ref_servida, TALLA, SECTOR,
    MEDIDA_TXT), igual que el HTML del panel. D-201: la medida separa lotes."""
    agrupado: dict[tuple, dict] = {}
    for r in filas:
        key = (
            int(_num(r.get("POSICION"))),
            str(r.get("ref_servida") or ""),
            str(r.get("TALLA") or ""),
            str(r.get("SECTOR") or ""),
            str(r.get("MEDIDA_TXT") or ""),
        )
        g = agrupado.get(key)
        if g is None:
            g = agrupado[key] = {
                "POSICION": key[0],
                "ref_servida": key[1],
                "TALLA": key[2],
                "SECTOR": key[3],
                "MEDIDA_TXT": key[4],
                "DESCRIPCION": r.get("DESCRIPCION"),
                "EQUIVALENTE": r.get("EQUIVALENTE"),
                "FINCA_ARTICULO": r.get("FINCA_ARTICULO"),
                "CANT": 0.0,
                "SUSTITUCIONES": 0,
                "partes": set(),
            }
        g["CANT"] += _num(r.get("CANT"))
        g["SUSTITUCIONES"] += int(r.get("SUSTITUCIONES") or 0)
    return sorted(
        agrupado.values(),
        key=lambda g: (g["POSICION"], g["ref_servida"], g["TALLA"], g["SECTOR"], g["MEDIDA_TXT"]),
    )


def build_punteo_pdf(
    bq_client,
    project,
    dataset,
    picking_dataset,
    picking_table,
    matriculas_table,
    partes_table,
    numero_pedido,
) -> bytes:
    """PDF del DOCUMENTO DE PUNTEO idéntico al HTML previsualizado en el panel."""
    datos = _load_datos(
        bq_client, project, dataset, picking_dataset, picking_table, matriculas_table, partes_table, numero_pedido
    )
    o = datos["o"]
    filas = datos["filas"]
    obs = o.get("OBSERVACIONES")
    peso = datos.get("peso", 0.0)

    empleados = sorted(
        {_nombre_corto(d.get("empleado_nombre")) for d in datos["detalle"] if d.get("empleado_nombre") and not d.get("es_operario")}
    )
    empleado_txt = ", ".join(empleados) or "Pendiente de pistoleo"
    partes_set = {(d.get("picking_tipo"), d.get("picking_numero")) for d in datos["detalle"]}
    doc_label = (
        "Punteo Final" if any(t == "F" for t, _ in partes_set)
        else ("Punteo Inicial" if partes_set else "Punteo (en curso)")
    )

    filas_agrupadas = _agrupar_filas_pedido(filas)

    # Pre-computar sustituciones agrupadas por (POSICION, ref_original, ref_servida, medida).
    sust_agrupadas: dict[tuple, dict] = {}
    for d in datos["detalle"]:
        if not d.get("sustituido"):
            continue
        pos = int(_num(d.get("POSICION")))
        ref_orig = str(d.get("ref_original") or "")
        ref_serv = str(d.get("ref_servida") or "")
        med = str(d.get("medida") or "")
        key = (pos, ref_orig, ref_serv, med)
        sa = sust_agrupadas.get(key)
        if sa is None:
            sa = sust_agrupadas[key] = {
                "POSICION": pos,
                "ref_original": ref_orig,
                "ref_servida": ref_serv,
                "medida": med,
                "litraje_pedida": str(d.get("LITRAJE_PEDIDA") or ""),
                "sector_pedida": str(d.get("SECTOR_PEDIDA") or ""),
                "litraje_servida": str(d.get("LITRAJE_SERVIDA") or ""),
                "sector_servida": str(d.get("SECTOR_SERVIDA") or ""),
                "cantidad": 0.0,
            }
        sa["cantidad"] += _num(d.get("cantidad_partida"))

    def susts_de(g):
        guion = "\u2014"
        flecha = "\u2194"
        punto = "\u00b7"
        flecha_txt = " \u2192 "
        med_txt = str(g.get("MEDIDA_TXT") or "")
        out = []
        for key_s, sa in sust_agrupadas.items():
            if sa["POSICION"] != g["POSICION"] or sa["ref_servida"] != g["ref_servida"]:
                continue
            med_sust = sa["medida"]
            if med_sust and med_txt and med_sust not in med_txt:
                continue
            orig = sa["ref_original"] or guion
            lit_p = _set(sa["litraje_pedida"])
            sec_p = _set(sa["sector_pedida"])
            out.append(
                f"{orig} ({lit_p} {punto} {sec_p})"
                f"{flecha_txt}{sa['ref_servida'] or guion} ({_set(g['TALLA'])} {punto} {_set(g['SECTOR'])})"
                f"  \u00d7{sa['cantidad']:,.0f}"
            )
        return out

    buf = io.BytesIO()
    c = canvas.Canvas(buf, pagesize=A4)
    c.setTitle(f"Punteo - Pedido {numero_pedido}")

    # Límite inferior: contenido debe quedar por encima del pie
    # pie_top = PIE_BOTTOM + PIE_HEIGHT = 41mm desde abajo
    limite_inferior = PIE_TOP + 3 * mm  # 44mm desde abajo

    # ---- Planificación de páginas ----
    bloques = []
    for i, g in enumerate(filas_agrupadas, start=1):
        s = susts_de(g)
        ggn_h = 3.5 * mm if g.get("EQUIVALENTE") else 0
        bloques.append((i, g, s, (ROW_H + ggn_h) + ROW_H * len(s)))

    def capacidad(es_primera: bool) -> float:
        y0 = PAGE_H - MARG
        y = y0 - HEADER_H - HEADER_GAP  # tras cabecera
        if es_primera:
            y -= 8 * mm + 3 * mm  # titulo bar + gap
        else:
            y -= 9 * mm  # titulo continuacion
        y -= THEAD_H  # cabecera tabla
        return y - limite_inferior

    paginas_plan: list[list[tuple]] = [[]]
    libres = [capacidad(True)]
    for bloque in bloques:
        if libres[-1] < bloque[3]:
            paginas_plan.append([])
            libres.append(capacidad(False))
        paginas_plan[-1].append(bloque)
        libres[-1] -= bloque[3]
    total_paginas = max(1, len(paginas_plan))

    for num_pag, plan in enumerate(paginas_plan, start=1):
        y_top = PAGE_H - MARG
        y = _draw_cabecera(c, o, doc_label, y_top, num_pag, total_paginas)
        if num_pag == 1:
            y = _draw_titulo(c, y, o)
        else:
            # Titulo de continuación
            bar_h = 7 * mm
            c.setFillColor(_CORP_DARK)
            c.rect(MARG, y - bar_h, CONTENT_W, bar_h, stroke=0, fill=1)
            c.setStrokeColor(_CORP_MID)
            c.setLineWidth(0.5)
            c.roundRect(MARG, y - bar_h, CONTENT_W, bar_h, 1.5 * mm, stroke=1, fill=0)
            c.setFillColor(colors.white)
            c.setFont(FB, 8)
            c.drawString(
                MARG + 3.5 * mm,
                y - 5 * mm,
                f"DOCUMENTO DE PUNTEO \u00b7 Pedido {_set(o.get('NUMERO_PEDIDO'))} (cont.)",
            )
            y -= bar_h + 3 * mm
        y = _draw_tabla_header(c, y)

        if not plan and num_pag == 1:
            c.setFont(F, 7.5)
            c.setFillColor(_CORP_TEXT2)
            c.drawCentredString(
                PAGE_W / 2,
                y - 20 * mm,
                "Sin pistoleo registrado todav\u00eda para este pedido.",
            )
            c.setFillColor(colors.black)

        for fila_idx, (i, g, s, _alto) in enumerate(plan):
            y = _draw_fila(c, y, i, g, s, zebra=(fila_idx % 2 == 1))

        _draw_pie(c, obs, empleado_txt, peso)
        c.showPage()

    c.save()
    return buf.getvalue()
