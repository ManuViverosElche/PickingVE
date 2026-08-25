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

from informe_datos import (
    GGN,
    _load_datos,
    _num,
    _set,
)

F = "Helvetica"
FB = "Helvetica-Bold"

_LOGO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "web", "manager", "viveros_logo.png")

PAGE_W, PAGE_H = A4
MARG = 10 * mm
CONTENT_W = PAGE_W - 2 * MARG
HEADER_H = 44 * mm
ROW_H = 5.2 * mm
FOOTER_H = 26 * mm

# Mismas columnas y anchos relativos que la tabla del HTML
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

_AZUL = colors.HexColor("#4472C4")
_GRIS_BORDE = colors.HexColor("#999999")
_AMBAR_FONDO = colors.HexColor("#FFF2CC")
_AMBAR_TEXTO = colors.HexColor("#8A5D00")
_LIME = colors.HexColor("#AFCB37")
_GRIS_TEXTO = colors.HexColor("#3D565A")


def _cortar(texto: str, ancho_mm: float, font: str = F, size: float = 7) -> str:
    max_chars = max(3, int(ancho_mm / (size * 0.155 * mm)))
    return texto[:max_chars]


def _caja(c, x, y, w, h, etiqueta, valor):
    """Caja dato del centro de la cabecera (equivalente a .caja-dato del HTML)."""
    c.setStrokeColor(_GRIS_BORDE)
    c.setLineWidth(0.4)
    c.rect(x, y, w, h, stroke=1, fill=0)
    c.setFillColor(_GRIS_TEXTO)
    c.setFont(FB, 5.6)
    c.drawString(x + 1.2 * mm, y + h - 3.1 * mm, etiqueta)
    c.setFillColor(colors.black)
    c.setFont(FB, 7.2)
    v = _set(valor) or "\u2014"
    c.drawString(x + 1.2 * mm, y + 1.6 * mm, _cortar(v, w - 2.4 * mm, FB, 7.2))


def _draw_cabecera(c, o, documento, y_top, pagina=1, total_paginas=1):
    """Cabecera completa: fiscal | cajas centro | logo + tabla documento/paginacion."""
    alto = HEADER_H
    y0 = y_top - alto

    # ---- Zona izquierda: datos fiscales + cliente ----
    x = MARG
    c.setFillColor(colors.black)
    c.setFont(FB, 11)
    c.drawString(x, y_top - 6 * mm, "VIVEROS ELCHE, S.L.")
    c.setFont(F, 6.5)
    c.setFillColor(colors.black)
    c.drawString(x, y_top - 10 * mm, "CIF B03303005")
    yy = y_top - 13.5 * mm
    for linea in _DIRECCION:
        c.drawString(x, yy, linea)
        yy -= 3.4 * mm

    # Cliente: código + comercial + fiscal·dirección
    cy = yy - 3 * mm
    c.setFont(FB, 8.5)
    codigo = _set(o.get("NUMERO_CLIENTE"))
    if codigo:
        c.drawString(x, cy, codigo)
        c.setFont(FB, 8.5)
        c.drawString(x + 12 * mm, cy, _cortar(_set(o.get("N_COMERCIAL")), 58 * mm, FB, 8.5))
    else:
        c.drawString(x, cy, _cortar(_set(o.get("N_COMERCIAL")), 70 * mm, FB, 8.5))
    c.setFont(F, 6.3)
    c.setFillColor(_GRIS_TEXTO)
    cliente_dir = ", ".join(
        xx for xx in [_set(o.get("DIR_CLIENTE")), _set(o.get("CIUDAD_CLIENTE"))] if xx
    )
    fiscal_txt = _set(o.get("N_FISCAL"))
    segunda = fiscal_txt + ((" \u00b7 " + cliente_dir) if cliente_dir else "")
    c.drawString(x, cy - 3.6 * mm, _cortar(segunda, 70 * mm, F, 6.3))
    c.setFillColor(colors.black)

    # ---- Zona central: cajas de datos (grid 2 columnas x 3 filas) ----
    cx = MARG + 74 * mm
    caja_w, caja_h = 28 * mm, 10 * mm
    gap = 1.5 * mm
    filas_cajas = [
        [("Finca", o.get("FINCA_CARGA")), ("Zona", o.get("SECTOR_CARGA"))],
        [("Tractora", o.get("MATRICULA_CAMION")), ("Remolque", o.get("MATRICULA_REMOLQUE"))],
    ]
    ry = y_top - caja_h
    for par in filas_cajas:
        _caja(c, cx, ry, caja_w, caja_h, par[0][0], par[0][1])
        _caja(c, cx + caja_w + gap, ry, caja_w, caja_h, par[1][0], par[1][1])
        ry -= caja_h + gap
    ancho_span = caja_w * 2 + gap
    _caja(c, cx, ry, ancho_span, caja_h, "Marca del Pedido", o.get("MARCA_PEDIDO"))
    ry -= caja_h + gap
    _caja(c, cx, ry, ancho_span, caja_h, "Referencia del Pedido", o.get("REFERENCIA_PEDIDO"))

    # ---- Zona derecha: logo + tabla documento ----
    dx = PAGE_W - MARG - 52 * mm
    dw = 52 * mm
    if os.path.exists(_LOGO):
        try:
            logo_w = 20 * mm
            c.drawImage(
                _LOGO,
                dx + dw - logo_w,
                y_top - 16 * mm,
                width=logo_w,
                height=14 * mm,
                preserveAspectRatio=True,
                mask="auto",
            )
        except Exception:
            pass
    ty = y_top - 19 * mm
    fila_h = 5.4 * mm
    doc_filas = [
        ("Documento", documento),
        ("N\u00famero", _set(o.get("NUMERO_PEDIDO"))),
        ("Fecha Carga", _fecha_es(o.get("FECHA_CARGA"))),
        ("P\u00e1gina", f"{pagina} de {total_paginas}"),
    ]
    c.setStrokeColor(_GRIS_BORDE)
    c.setLineWidth(0.4)
    for k, v in doc_filas:
        c.rect(dx, ty - fila_h, dw * 0.36, fila_h, stroke=1, fill=0)
        c.rect(dx + dw * 0.36, ty - fila_h, dw * 0.64, fila_h, stroke=1, fill=0)
        c.setFont(FB, 6.4)
        c.setFillColor(_GRIS_TEXTO)
        c.drawString(dx + 1.2 * mm, ty - fila_h + 1.8 * mm, k)
        c.setFillColor(colors.black)
        c.setFont(FB, 7)
        c.drawString(dx + dw * 0.36 + 1.2 * mm, ty - fila_h + 1.8 * mm, _cortar(v, dw * 0.6, FB, 7))
        ty -= fila_h

    # Marco general de la cabecera
    c.setStrokeColor(colors.white)
    return y0


def _draw_titulo(c, y, o):
    c.setFont(FB, 12.5)
    c.setFillColor(colors.black)
    c.drawString(MARG, y - 6 * mm, "DOCUMENTO DE PUNTEO")
    c.setFont(F, 7)
    c.setFillColor(_GRIS_TEXTO)
    meta = (
        f"Pedido {_set(o.get('NUMERO_PEDIDO'))} \u00b7 "
        f"Generado {_dt_es(datetime.now(timezone.utc))}"
    )
    c.drawRightString(PAGE_W - MARG, y - 6 * mm, meta)
    c.setFillColor(colors.black)
    return y - 10 * mm


def _draw_tabla_header(c, y):
    x = MARG
    c.setFillColor(_AZUL)
    c.setStrokeColor(_GRIS_BORDE)
    c.rect(x, y - 6 * mm, TABLE_W, 6 * mm, stroke=1, fill=1)
    c.setFillColor(colors.white)
    c.setFont(FB, 6.8)
    for name, w in _COLS:
        c.drawCentredString(x + w / 2, y - 4.2 * mm, name)
        x += w
    c.setFillColor(colors.black)
    return y - 6 * mm


def _draw_fila(c, y, num, g, susts):
    x = MARG
    c.setStrokeColor(_GRIS_BORDE)
    c.setLineWidth(0.35)
    for _, w in _COLS:
        c.rect(x, y - ROW_H, w, ROW_H, stroke=1, fill=0)
        x += w

    desc = _set(g.get("DESCRIPCION"))
    vals = {
        0: (str(num), "C", F),
        1: (_set(g.get("POSICION")), "C", F),
        2: (_set(g["ref_servida"]), "C", FB),
        3: ("*" if g.get("EQUIVALENTE") else "", "C", FB),
        4: (_cortar(desc, 53 * mm), "L", F),
        5: (_set(g["TALLA"]), "C", F),
        6: (_set(g["SECTOR"]), "C", F),
        7: (_cortar(_set(g.get("FINCA_ARTICULO")), 22 * mm), "C", F),
        8: (f"{_num(g['CANT']):,.0f}".replace(",", "."), "C", FB),
    }
    # D-178: reportlab exige str; normaliza cualquier int/float (POSICION, CANT...)
    for _k in list(vals):
        _v, _a, _f = vals[_k]
        vals[_k] = ("" if _v is None else str(_v), _a, _f)
    x = MARG
    for idx, (_, w) in enumerate(_COLS):
        v, align, font = vals[idx]
        c.setFont(font, 7)
        if align == "C":
            c.drawCentredString(x + w / 2, y - ROW_H + 1.6 * mm, v)
        else:
            c.drawString(x + 1.4 * mm, y - ROW_H + 1.6 * mm, v)
        x += w
    # Chip GGN pegado a la referencia (como .marca-ggn del HTML)
    if g.get("EQUIVALENTE"):
        ref_w = _COLS[2][1]
        chip_w = 8 * mm
        c.setFillColor(_LIME)
        c.rect(MARG + _COLS[0][1] + _COLS[1][1] + ref_w - chip_w - 1 * mm, y - ROW_H + 1.1 * mm, chip_w, 3 * mm, stroke=0, fill=1)
        c.setFillColor(colors.white)
        c.setFont(FB, 5.4)
        c.drawCentredString(
            MARG + _COLS[0][1] + _COLS[1][1] + ref_w - chip_w / 2 - 1 * mm,
            y - ROW_H + 1.9 * mm,
            "GGN",
        )
        c.setFillColor(colors.black)

    cur_y = y - ROW_H
    for s in susts:
        c.setFillColor(_AMBAR_FONDO)
        c.setStrokeColor(_GRIS_BORDE)
        c.rect(MARG, cur_y - ROW_H, TABLE_W, ROW_H, stroke=1, fill=1)
        c.setFillColor(_AMBAR_TEXTO)
        c.setFont(FB, 6.3)
        c.drawString(
            MARG + 3 * mm,
            cur_y - ROW_H + 1.5 * mm,
            _cortar(f"\u2194 Sustituci\u00f3n: {s}", TABLE_W - 6 * mm, FB, 6.3),
        )
        c.setFillColor(colors.black)
        cur_y -= ROW_H
    return cur_y


def _draw_pie(c, obs, empleado_txt, peso):
    y0 = MARG + FOOTER_H
    c.setStrokeColor(_GRIS_BORDE)
    c.line(MARG, y0 + FOOTER_H - 2 * mm, PAGE_W - MARG, y0 + FOOTER_H - 2 * mm)

    c.setFont(F, 6.6)
    c.drawString(MARG, y0 + FOOTER_H - 5.4 * mm, f"* Producto certificado GlobalG.A.P.   GGN {GGN}  \u00b7  GLN {GGN}")

    # Observaciones
    box_h = 12 * mm
    box_w = CONTENT_W * 0.62
    c.rect(MARG, y0 + 4 * mm, box_w, box_h, stroke=1, fill=0)
    c.setFont(FB, 6.4)
    c.drawString(MARG + 1.5 * mm, y0 + 4 * mm + box_h - 3.4 * mm, "Observaciones")
    c.setFont(F, 6.6)
    obs_txt = _set(obs) or "\u2014"
    max_chars = int((box_w - 3 * mm) / (6.6 * 0.155 * mm))
    lineas_obs = [obs_txt[i:i + max_chars] for i in range(0, len(obs_txt), max_chars)][:3]
    oy = y0 + 4 * mm + box_h - 6.6 * mm
    for ln in lineas_obs:
        c.drawString(MARG + 1.5 * mm, oy, ln)
        oy -= 3 * mm

    # Verificado por + Peso
    fx = MARG + box_w + 4 * mm
    fw = PAGE_W - MARG - fx
    c.setFont(FB, 6.8)
    c.drawString(fx, y0 + 4 * mm + box_h - 3.4 * mm, "Verificado por")
    c.setFont(F, 7.5)
    c.drawString(fx + 24 * mm, y0 + 4 * mm + box_h - 3.6 * mm, _nombre_corto(empleado_txt))
    c.setStrokeColor(colors.black)
    c.line(fx + 22 * mm, y0 + 4 * mm + box_h - 4.6 * mm, PAGE_W - MARG, y0 + 4 * mm + box_h - 4.6 * mm)
    c.setFont(FB, 6.8)
    c.drawString(fx, y0 + 4 * mm + box_h - 9.4 * mm, "Peso de la carga (KG)")
    c.setFont(FB, 8)
    c.drawString(fx + 34 * mm, y0 + 4 * mm + box_h - 9.8 * mm, f"{_num(peso):,.2f}".replace(",", "@").replace(".", ",").replace("@", "."))


def _agrupar_filas_pedido(filas: list) -> list:
    """Listado correlativo único por (POSICION, ref_servida, TALLA, SECTOR),
    igual que el HTML del panel."""
    agrupado: dict[tuple, dict] = {}
    for r in filas:
        key = (
            int(_num(r.get("POSICION"))),
            str(r.get("ref_servida") or ""),
            str(r.get("TALLA") or ""),
            str(r.get("SECTOR") or ""),
        )
        g = agrupado.get(key)
        if g is None:
            g = agrupado[key] = {
                "POSICION": key[0],
                "ref_servida": key[1],
                "TALLA": key[2],
                "SECTOR": key[3],
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
        key=lambda g: (g["POSICION"], g["ref_servida"], g["TALLA"], g["SECTOR"]),
    )


def _alto_tras_cabecera(y_top: float) -> float:
    """Y donde empieza el titulo tras dibujar la cabecera completa."""
    return y_top - HEADER_H


def build_punteo_pdf(
    bq_client,
    project,
    dataset,
    picking_dataset,
    picking_table,
    matriculas_table,
    numero_pedido,
) -> bytes:
    """PDF del DOCUMENTO DE PUNTEO id\u00e9ntico al HTML previsualizado en el panel."""
    datos = _load_datos(
        bq_client, project, dataset, picking_dataset, picking_table, matriculas_table, numero_pedido
    )
    o = datos["o"]
    filas = datos["filas"]
    obs = o.get("OBSERVACIONES")
    peso = 0.0

    empleados = sorted(
        {_nombre_corto(d.get("empleado_nombre")) for d in datos["detalle"] if d.get("empleado_nombre")}
    )
    empleado_txt = ", ".join(empleados) or "Pendiente de pistoleo"
    partes_set = {(d.get("picking_tipo"), d.get("picking_numero")) for d in datos["detalle"]}
    doc_label = (
        "Punteo Final" if any(t == "F" for t, _ in partes_set)
        else ("Punteo Inicial" if partes_set else "Punteo (en curso)")
    )

    filas_agrupadas = _agrupar_filas_pedido(filas)

    def susts_de(g):
        guion = "\u2014"
        flecha = "\u2194"
        punto = "\u00b7"
        flecha_txt = " \u2192 "
        out = []
        for d in datos["detalle"]:
            if not d.get("sustituido"):
                continue
            if (
                str(d.get("ref_servida") or ""),
                str(d.get("LITRAJE_SERVIDA") or ""),
                str(d.get("SECTOR_SERVIDA") or ""),
            ) != (g["ref_servida"], g["TALLA"], g["SECTOR"]):
                continue
            orig = d.get("ref_original") or guion
            lit_p = _set(d.get("LITRAJE_PEDIDA"))
            sec_p = _set(d.get("SECTOR_PEDIDA"))
            out.append(
                f"{orig} ({lit_p} {punto} {sec_p})"
                f"{flecha_txt}{d.get('ref_servida') or guion} ({_set(g['TALLA'])} {punto} {_set(g['SECTOR'])})"
            )
        return out

    buf = io.BytesIO()
    c = canvas.Canvas(buf, pagesize=A4)
    c.setTitle(f"Punteo - Pedido {numero_pedido}")

    # D-196: tope real del pie = linea separadora en MARG + 2*FOOTER_H - 2mm
    # (60mm desde el borde inferior). Las filas deben quedar por encima con
    # 3mm de holgura; antes (MARG+FOOTER_H+14) una pagina llena las metia
    # 10mm dentro del pie, tachando la ultima fila con la linea separadora.
    limite_inferior = MARG + 2 * FOOTER_H - 2 * mm + 3 * mm

    # ---- D-178: PLANIFICACION de paginas ANTES de dibujar ----
    # Cada bloque = fila de linea + sus subfilas de sustitucion. La primera
    # pagina gasta cabecera completa + titulo + cabecera de tabla; las
    # siguientes repiten la cabecera completa (logo, documento y PAGINA X de Y
    # en todas las hojas) pero sin el titulo grande.
    bloques = []
    for i, g in enumerate(filas_agrupadas, start=1):
        s = susts_de(g)
        bloques.append((i, g, s, ROW_H * (1 + len(s))))

    def capacidad(es_primera: bool) -> float:
        y0 = PAGE_H - MARG
        if es_primera:
            y = _alto_tras_cabecera(y0)
            y -= 10 * mm  # titulo
            return y - 6 * mm - limite_inferior  # header tabla
        # Páginas 2+ también llevan cabecera completa (HEADER_H) + continuación
        return _alto_tras_cabecera(y0) - 9 * mm - 6 * mm - limite_inferior

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
            c.setFont(FB, 9)
            c.drawString(
                MARG,
                y - 6 * mm,
                f"DOCUMENTO DE PUNTEO \u00b7 Pedido {_set(o.get('NUMERO_PEDIDO'))} (cont.)",
            )
            y -= 9 * mm
        y = _draw_tabla_header(c, y)

        if not plan and num_pag == 1:
            c.setFont(F, 7.5)
            c.setFillColor(_GRIS_TEXTO)
            c.drawCentredString(
                PAGE_W / 2,
                y - 20 * mm,
                "Sin pistoleo registrado todav\u00eda para este pedido.",
            )
            c.setFillColor(colors.black)

        for i, g, s, _alto in plan:
            y = _draw_fila(c, y, i, g, s)

        _draw_pie(c, obs, empleado_txt, peso)
        if num_pag != total_paginas or not filas_agrupadas:
            pass
        c.showPage()

    c.save()
    return buf.getvalue()
