"""Informes HTML del pistoleo — Viveros Elche (diseño corporativo moderno).

Genera 3 informes independientes a partir de la capa de datos compartida
(informe_datos._load_datos):

1. build_punteo_html     — Documento de Punteo: listado correlativo único con
                            las líneas del pedido (N.L. = posición Factusol).
2. build_detalle_html    — Detalle del Pistoleo (A4 horizontal): un evento por
                            línea, con referencia/litraje/sector pedidos y servidos.
3. build_control_html    — Control de Acopio (A4 horizontal): pedido vs acopiado
                            con trazabilidad completa de sustituciones.

Convenciones transversales:
- Fechas siempre en formato dd/mm/yyyy (horas en hora local España).
- Empleados mostrados solo por su nombre de pila.
- Paleta corporativa verde/teal de Viveros Elche.
"""

import base64
import os
from datetime import datetime, timezone
from zoneinfo import ZoneInfo

from informe_datos import (
    DOC_PUNTEO_FINAL,
    DOC_PUNTEO_INICIAL,
    GGN,
    _load_datos,
    _num,
    _set,
)

_LOGO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "web", "manager", "viveros_logo.png")

_DIRECCION = [
    "PARTIDA DE ALGORÓS, POLÍGONO 1-189-A",
    "03293 ELCHE · ALICANTE",
    "Tel. 965483747 / 966635023",
]

_TZ_ESPANA = ZoneInfo("Europe/Madrid")


def _html_esc(v) -> str:
    return str(v if v is not None else "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")


def _fecha_es(v) -> str:
    """Convierte cualquier fecha a formato español dd/mm/yyyy."""
    s = str(v or "")[:10]
    try:
        return datetime.strptime(s, "%Y-%m-%d").strftime("%d/%m/%Y")
    except ValueError:
        return s


def _dt_es(dt) -> str:
    """Datetime UTC → 'dd/mm/yyyy HH:MM' en hora local de España."""
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
        return dt.astimezone(_TZ_ESPANA).strftime("%d/%m/%Y %H:%M")
    return str(dt)


def _nombre_corto(nombre) -> str:
    """Solo el nombre de pila del empleado."""
    parts = str(nombre or "").strip().split()
    return parts[0] if parts else ""


def _logo_b64() -> str:
    if not os.path.exists(_LOGO):
        return ""
    with open(_LOGO, "rb") as f:
        return base64.b64encode(f.read()).decode()


# ---------------------------------------------------------------------------
# CSS corporativo
# ---------------------------------------------------------------------------

def _css() -> str:
    return """
@page { size: A4 portrait; margin: 0; }
@page apaisada { size: A4 landscape; margin: 0; }
* { box-sizing: border-box; margin: 0; padding: 0; }
/* D-177: imprimir con los sombreados/fondos tal cual se ven en pantalla */
* { -webkit-print-color-adjust: exact !important; print-color-adjust: exact !important; }
:root {
  --corp-dark: #0c3a3f;
  --corp-mid: #14555c;
  --corp-teal: #0e8a80;
  --corp-lime: #35b8ac;
  --corp-bg: #eef7f5;
  --corp-line: #b8d8d3;
  --corp-warn-bg: #fdf3e0;
  --corp-warn-line: #e0a13c;
}
body {
  font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;
  color: #1a2b2e; background: #fff; font-size: 9pt; line-height: 1.4;
}
/* D-200: pagina fisica EXACTA (height fija + overflow hidden): el contenido
   nunca puede crecer ni pisar el pie; el navegador no re-pagina porque
   @page margin es 0 y cada .pagina ocupa justo una hoja. */
.pagina { width: 210mm; height: 297mm; padding: 7mm 9mm 34mm; page-break-after: always; position: relative; overflow: hidden; }
.pagina.apaisada { width: 297mm; height: 210mm; padding: 6mm 8mm 10mm; page: apaisada; }
.pagina:last-child { page-break-after: auto; }
@media print { .no-print { display: none; } body { background: #fff; } }

/* ---- Cabecera ---- */
.cabecera { display: flex; gap: 5mm; align-items: stretch; border-bottom: 2px solid var(--corp-teal); padding-bottom: 3mm; margin-bottom: 4mm; }
.cab-fiscal {
  flex: 0 0 62mm; border: 1.5px solid var(--corp-mid); border-radius: 2mm;
  padding: 2.5mm 3mm; font-size: 8pt; background: var(--corp-bg);
}
.cab-fiscal .empresa { font-size: 12.5pt; font-weight: 700; color: var(--corp-dark); letter-spacing: -0.2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cab-fiscal .dir { color: #3d565a; margin-top: 0.5mm; }
.cab-fiscal .cliente { margin-top: 2mm; border-top: 1px solid var(--corp-line); padding-top: 1.5mm; }
.cab-fiscal .cliente .codigo { display: inline-block; background: var(--corp-teal); color: #fff; border-radius: 1mm; padding: 0.3mm 1.6mm; font-weight: 700; font-size: 8.5pt; }
.cab-fiscal .cliente .nombre { font-weight: 700; color: var(--corp-dark); }
.cab-centro { flex: 1 1 auto; display: grid; grid-template-columns: 1fr 1fr; gap: 2mm; align-content: start; }
.caja-dato { border: 1px solid var(--corp-line); border-left: 3px solid var(--corp-teal); border-radius: 1.5mm; padding: 1.4mm 2mm; background: #fff; min-width: 0; }
.caja-dato b { display: block; font-size: 6.5pt; text-transform: uppercase; letter-spacing: 0.6px; color: var(--corp-mid); white-space: nowrap; }
.caja-dato span { font-size: 9.5pt; font-weight: 700; color: var(--corp-dark); display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cab-doc { flex: 0 0 52mm; display: flex; flex-direction: column; align-items: stretch; }
.cab-logo { width: 100%; max-height: 22mm; object-fit: contain; margin-bottom: 2mm; }
.tabla-doc { width: 100%; border-collapse: collapse; font-size: 7.5pt; border: 1px solid var(--corp-mid); border-radius: 1.5mm; overflow: hidden; }
.tabla-doc td { border: 1px solid var(--corp-line); padding: 1mm 1.6mm; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 0; }
.tabla-doc td.k { background: var(--corp-bg); color: var(--corp-mid); font-weight: 600; text-transform: uppercase; font-size: 6.5pt; width: 38%; }
.tabla-doc td.v { font-weight: 700; color: var(--corp-dark); }

/* ---- Titulo de seccion ---- */
.titulo-informe {
  display: flex; justify-content: space-between; align-items: center;
  background: linear-gradient(90deg, var(--corp-dark), var(--corp-mid));
  color: #fff; border-radius: 1.5mm; padding: 2mm 3.5mm; margin-bottom: 3mm;
}
.titulo-informe h2 { font-size: 11.5pt; font-weight: 700; letter-spacing: 0.3px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.titulo-informe .meta { font-size: 7.5pt; opacity: 0.85; white-space: nowrap; }

/* ---- Tablas de datos ---- */
table.datos { width: 100%; border-collapse: collapse; font-size: 8pt; table-layout: fixed; }
table.datos th, table.datos td { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
table.datos thead th {
  background: var(--corp-dark); color: #fff; font-weight: 600; text-transform: uppercase;
  font-size: 6.8pt; letter-spacing: 0.5px; padding: 1.8mm 1.2mm; text-align: center;
  border-bottom: 2px solid var(--corp-teal);
}
table.datos tbody td { border-bottom: 1px solid var(--corp-line); padding: 1.4mm 1.2mm; vertical-align: middle; }
table.datos tbody tr:nth-child(even) td { background: var(--corp-bg); }
table.datos td.c { text-align: center; }
table.datos td.nl { text-align: center; font-weight: 700; color: var(--corp-teal); }
table.datos td.ref { font-weight: 700; color: var(--corp-dark); }
table.datos tr.ggn .marca-ggn { display: inline-block; background: var(--corp-lime); color: #fff; border-radius: 1mm; font-size: 6pt; font-weight: 700; padding: 0.2mm 1.2mm; margin-left: 1mm; vertical-align: middle; }
table.datos td.estrella { text-align: center; font-weight: 700; color: var(--corp-teal); }
table.datos tr.sust td { background: var(--corp-warn-bg) !important; border-left: 3px solid var(--corp-warn-line); }
.sust-tag { color: #a06a10; font-weight: 700; }
.medida-desc { color: #3d565a; font-style: italic; }

/* ---- Tablas apaisadas (detalle/control) ---- */
table.apaisada { width: 100%; border-collapse: collapse; font-size: 6.8pt; table-layout: fixed; }
table.apaisada thead th {
  background: var(--corp-dark); color: #fff; font-weight: 600; text-transform: uppercase;
  font-size: 6pt; letter-spacing: 0.4px; padding: 1.4mm 0.8mm; text-align: center;
  border-bottom: 2px solid var(--corp-teal);
}
table.apaisada tbody td { border-bottom: 1px solid var(--corp-line); padding: 1mm 0.8mm; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
table.apaisada tbody tr:nth-child(even) td { background: var(--corp-bg); }
table.apaisada td.c { text-align: center; }
table.apaisada tr.sust td { background: var(--corp-warn-bg) !important; }
.cambio { color: #a06a10; font-weight: 700; }

/* ---- Pie ---- */
.pie { position: absolute; bottom: 7mm; left: 9mm; right: 9mm; height: 24mm; overflow: hidden; font-size: 7.5pt; }
.pagina.apaisada .pie { display: none; }
.pie .ggn-line { color: var(--corp-mid); margin-bottom: 1.5mm; }
.pie .obs-box { border: 1px solid var(--corp-line); border-radius: 1.5mm; padding: 1.6mm 2.2mm; min-height: 9mm; background: var(--corp-bg); margin-bottom: 2mm; overflow: hidden; }
.pie .obs-box br + *, .pie .obs-box { max-height: 12mm; }
.pie .obs-box b { color: var(--corp-mid); text-transform: uppercase; font-size: 6.5pt; letter-spacing: 0.5px; }
.pie .fila-final { display: flex; justify-content: space-between; align-items: flex-end; gap: 6mm; }
.firma-box { flex: 1; border: 1px solid var(--corp-line); border-radius: 1.5mm; padding: 1.6mm 2.5mm 3mm; }
.firma-box b { color: var(--corp-mid); text-transform: uppercase; font-size: 6.5pt; letter-spacing: 0.5px; }
.firma-box .linea { display: block; border-bottom: 1px solid #7fa8a2; min-height: 6mm; font-weight: 700; color: var(--corp-dark); text-align: center; overflow: hidden; white-space: nowrap; }
.peso-box { flex: 0 0 60mm; border: 1.5px solid var(--corp-mid); border-radius: 1.5mm; padding: 1.6mm 2.5mm 3mm; }
.peso-box b { color: var(--corp-mid); text-transform: uppercase; font-size: 6.5pt; letter-spacing: 0.5px; }
.peso-box .valor { float: right; border: 1px solid var(--corp-mid); border-radius: 1mm; min-width: 24mm; text-align: center; font-weight: 700; padding: 0.4mm 2mm; background: #fff; }
"""


# ---------------------------------------------------------------------------
# Cabecera y pie comunes
# ---------------------------------------------------------------------------

def _cabecera(o, documento: str, pagina: int = 1, total_paginas: int = 1) -> str:
    logo = _logo_b64()
    logo_html = f'<img class="cab-logo" src="data:image/png;base64,{logo}">' if logo else '<div style="height:20mm;"></div>'
    dir_html = "".join(f"<div>{_html_esc(l)}</div>" for l in _DIRECCION)
    cliente_dir = ", ".join(x for x in [_set(o.get("DIR_CLIENTE")), _set(o.get("CIUDAD_CLIENTE"))] if x)
    return f"""
<div class="cabecera">
  <div class="cab-fiscal">
    <div class="empresa">VIVEROS ELCHE, S.L.</div>
    <div class="dir">CIF B03303005<br>{dir_html}</div>
    <div class="cliente">
      <span class="codigo">{_html_esc(str(o.get('NUMERO_CLIENTE') or ''))}</span>
      <span class="nombre"> {_html_esc(_set(o.get('N_COMERCIAL')))}</span>
      <div style="font-size:7.5pt; color:#3d565a;">{_html_esc(_set(o.get('N_FISCAL')))}{(' · ' + cliente_dir) if cliente_dir else ''}</div>
    </div>
  </div>
  <div class="cab-centro">
    <div class="caja-dato"><b>Finca</b><span>{_html_esc(_set(o.get('FINCA_CARGA')))}</span></div>
    <div class="caja-dato"><b>Zona</b><span>{_html_esc(_set(o.get('SECTOR_CARGA')))}</span></div>
    <div class="caja-dato"><b>Tractora</b><span>{_html_esc(_set(o.get('MATRICULA_CAMION')) or '—')}</span></div>
    <div class="caja-dato"><b>Remolque</b><span>{_html_esc(_set(o.get('MATRICULA_REMOLQUE')) or '—')}</span></div>
    <div class="caja-dato" style="grid-column: span 2;"><b>Marca del Pedido</b><span>{_html_esc(_set(o.get('MARCA_PEDIDO')) or '—')}</span></div>
    <div class="caja-dato" style="grid-column: span 2;"><b>Referencia del Pedido</b><span>{_html_esc(_set(o.get('REFERENCIA_PEDIDO')) or '—')}</span></div>
  </div>
  <div class="cab-doc">
    {logo_html}
    <table class="tabla-doc">
      <tr><td class="k">Documento</td><td class="v">{_html_esc(documento)}</td></tr>
      <tr><td class="k">Número</td><td class="v">{_html_esc(str(o.get('NUMERO_PEDIDO') or ''))}</td></tr>
      <tr><td class="k">Fecha Carga</td><td class="v">{_html_esc(_fecha_es(o.get('FECHA_CARGA')))}</td></tr>
      <tr><td class="k">Página</td><td class="v">{pagina} de {total_paginas}</td></tr>
    </table>
  </div>
</div>
"""


def _pie(obs, empleado, peso) -> str:
    return f"""
<div class="pie">
  <div class="ggn-line">* Producto certificado GlobalG.A.P. &nbsp;GGN {GGN} &nbsp;·&nbsp; GLN {GGN}</div>
  <div class="obs-box"><b>Observaciones</b><br>{_html_esc(_set(obs)) or '—'}</div>
  <div class="fila-final">
    <div class="firma-box"><b>Verificado por</b><span class="linea">{_html_esc(_nombre_corto(empleado))}</span></div>
    <div class="peso-box"><b>Peso de la carga (KG)</b><span class="valor">{_num(peso):,.2f}</span></div>
  </div>
</div>
"""


def _doc_html(title: str, body_paginas: str) -> str:
    return f"""<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<title>{_html_esc(title)}</title>
<style>{_css()}</style>
</head>
<body>
{body_paginas}
</body>
</html>"""


def _trocear_por_filas(bloques: list[tuple[str, int]], primera: int, siguientes: int) -> list[list[str]]:
    """Trocea bloques (html, filas_equivalentes) en páginas estilo ERP.

    La primera página gasta menos presupuesto (cabecera + título grandes) que
    las siguientes. Un bloque nunca se parte: si no cabe entero, pasa a la
    siguiente página.
    """
    paginas: list[list[str]] = []
    actual: list[str] = []
    libres = float(primera)
    for html_b, peso in bloques:
        if actual and libres < peso:
            paginas.append(actual)
            actual = []
            libres = float(siguientes)
        actual.append(html_b)
        libres -= peso
    if actual or not paginas:
        paginas.append(actual)
    return paginas


# Presupuesto calibrado CON MEDICION REAL en navegador (Playwright): fila
# media ~7.3mm con font 8pt/padding 1.4mm. Hueco vertical util ~169mm (p1) y
# ~183mm (pn); apaisada ~141mm con filas ~4.9mm.
_FILAS_P1_VERTICAL = 22
_FILAS_PN_VERTICAL = 24
_FILAS_P1_APAISADA = 22
_FILAS_PN_APAISADA = 25


def _paginar_informe(
    bloques: list[tuple[str, int]],
    cabecera_fn,
    pie_html: str,
    titulo_fn,
    thead: str,
    apaisada: bool,
) -> str:
    """Genera el body con N páginas .pagina, cada una con cabecera completa,
    título (cont. a partir de la 2ª), thead repetido y pie propio."""
    p1 = _FILAS_P1_APAISADA if apaisada else _FILAS_P1_VERTICAL
    pn = _FILAS_PN_APAISADA if apaisada else _FILAS_PN_VERTICAL
    trozos = _trocear_por_filas(bloques, p1, pn)
    total = len(trozos)
    clase = "pagina apaisada" if apaisada else "pagina"
    out = []
    for num_pag, trozo in enumerate(trozos, start=1):
        cuerpo = "".join(trozo)
        out.append(f"""
<div class="{clase}">
  {cabecera_fn(num_pag, total)}
  <div class="titulo-informe">
    {titulo_fn(num_pag)}
  </div>
  <table class="{'apaisada' if apaisada else 'datos'}">
    {thead}
    <tbody>{cuerpo}</tbody>
  </table>
  {pie_html}
</div>
""")
    return "".join(out)


# ---------------------------------------------------------------------------
# Informe 1 — Documento de Punteo (listado correlativo único)
# ---------------------------------------------------------------------------

def _agrupar_filas_pedido(filas: list) -> list:
    """Agrupa todas las partidas pistoleadas del pedido en un listado correlativo
    único por línea original (POSICION) + referencia servida + litraje + sector
    + medida fisica (D-201): lotes de distinta medida salen como lineas separadas."""
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
        g["partes"].add(f"{r.get('picking_tipo')}{r.get('picking_numero')}")
    return sorted(agrupado.values(), key=lambda g: (g["POSICION"], g["ref_servida"], g["TALLA"], g["SECTOR"], g["MEDIDA_TXT"]))


def build_punteo_html(bq_client, project, dataset, picking_dataset, picking_table, matriculas_table, numero_pedido) -> str:
    """Documento de Punteo: UN solo listado correlativo con todas las líneas del pedido.

    Cada fila conserva el N.L. original de Factusol (POSICION_PEDIDO), de modo que si
    una línea se desglosa en varias referencias al pistolear, todas comparten N.L.
    """
    datos = _load_datos(bq_client, project, dataset, picking_dataset, picking_table, matriculas_table, numero_pedido)
    o = datos["o"]
    filas = datos["filas"]
    sust_map = datos["sust_map"]
    obs = o.get("OBSERVACIONES")
    peso = 0.0

    empleados = sorted({_nombre_corto(d.get("empleado_nombre")) for d in datos["detalle"] if d.get("empleado_nombre") and not d.get("es_operario")})
    empleado_txt = ", ".join(empleados) or "Pendiente de pistoleo"
    partes_set = {(d.get("picking_tipo"), d.get("picking_numero")) for d in datos["detalle"]}
    doc_label = "Punteo Final" if any(t == "F" for t, _ in partes_set) else ("Punteo Inicial" if partes_set else "Punteo (en curso)")

    filas_agrupadas = _agrupar_filas_pedido(filas)

    bloques: list[tuple[str, int]] = []
    for i, g in enumerate(filas_agrupadas, start=1):
        clase_ggn = "ggn" if g.get("EQUIVALENTE") else ""
        marca_ggn = '<span class="marca-ggn">GGN</span>' if g.get("EQUIVALENTE") else ""
        estrella = "*" if g.get("EQUIVALENTE") else ""
        susts = []
        for d in datos["detalle"]:
            if not d.get("sustituido"):
                continue
            if (str(d.get("ref_servida") or ""), str(d.get("LITRAJE_SERVIDA") or ""), str(d.get("SECTOR_SERVIDA") or "")) != (
                g["ref_servida"], g["TALLA"], g["SECTOR"]
            ):
                continue
            susts.append(
                f"{d.get('ref_original') or '—'} ({_set(d.get('LITRAJE_PEDIDA'))} · {_set(d.get('SECTOR_PEDIDA'))})"
                f" → {d.get('ref_servida') or '—'} ({_set(g['TALLA'])} · {_set(g['SECTOR'])})"
            )
        bloque_html = f"""
<tr class="{clase_ggn}">
  <td class="nl">{i}</td>
  <td class="c">{_html_esc(g['POSICION'])}</td>
  <td class="c ref">{_html_esc(g['ref_servida'])}{marca_ggn}</td>
  <td class="estrella">{estrella}</td>
  <td>{_html_esc(_set(g.get("DESCRIPCION")))}{f' <i class="medida-desc">· {_html_esc(g["MEDIDA_TXT"])}</i>' if g.get("MEDIDA_TXT") else ''}</td>
  <td class="c">{_html_esc(g['TALLA'])}</td>
  <td class="c">{_html_esc(g['SECTOR'])}</td>
  <td class="c">{_html_esc(_set(g.get("FINCA_ARTICULO")))}</td>
  <td class="c" style="font-weight:700;">{_num(g['CANT']):,.0f}</td>
</tr>"""
        for s in susts:
            bloque_html += f'<tr class="sust"><td colspan="9"><span class="sust-tag">↔ Sustitución:</span> {_html_esc(s)}</td></tr>'
        bloques.append((bloque_html, 1 + len(susts)))

    if not bloques:
        bloques.append((
            '<tr><td colspan="9" class="c" style="padding:8mm; color:#5a7578;">Sin pistoleo registrado todavía para este pedido.</td></tr>',
            1,
        ))

    def cabecera_fn(pag: int, total: int) -> str:
        return _cabecera(o, doc_label, pag, total)

    def titulo_fn(pag: int) -> str:
        meta = f"Pedido {_html_esc(str(o.get('NUMERO_PEDIDO') or ''))} · Generado {_dt_es(datetime.now(timezone.utc))}"
        if pag == 1:
            return f'<h2>DOCUMENTO DE PUNTEO</h2>\n    <div class="meta">{meta}</div>'
        return f'<h2>DOCUMENTO DE PUNTEO · Pedido {_html_esc(str(o.get("NUMERO_PEDIDO") or ""))} (cont.)</h2>\n    <div class="meta">{meta}</div>'

    thead = """<thead><tr>
      <th style="width:8mm;">N.º</th>
      <th style="width:12mm;">N.L.</th>
      <th style="width:24mm;">Referencia</th>
      <th style="width:8mm;">*</th>
      <th style="text-align:left;">Descripción</th>
      <th style="width:20mm;">Litraje</th>
      <th style="width:20mm;">Sector</th>
      <th style="width:26mm;">Finca</th>
      <th style="width:16mm;">Cant.</th>
    </tr></thead>"""

    body = _paginar_informe(bloques, cabecera_fn, _pie(obs, empleado_txt, peso), titulo_fn, thead, apaisada=False)
    return _doc_html(f"Punteo - Pedido {numero_pedido}", body)


# ---------------------------------------------------------------------------
# Informe 2 — Detalle del Pistoleo (A4 horizontal)
# ---------------------------------------------------------------------------

def build_detalle_html(bq_client, project, dataset, picking_dataset, picking_table, matriculas_table, numero_pedido) -> str:
    """Detalle exhaustivo del pistoleo en A4 horizontal: un evento por línea."""
    datos = _load_datos(bq_client, project, dataset, picking_dataset, picking_table, matriculas_table, numero_pedido)
    o = datos["o"]
    detalle = datos["detalle"]

    filas_lista: list[str] = []
    for r in detalle:
        ref_ped = _set(r.get("REF_LINEA"))
        lit_ped = _set(r.get("LITRAJE_PEDIDA"))
        sec_ped = _set(r.get("SECTOR_PEDIDA"))
        ref_ser = _set(r.get("ref_servida"))
        lit_ser = _set(r.get("LITRAJE_SERVIDA") or r.get("TALLA"))
        sec_ser = _set(r.get("SECTOR_SERVIDA") or r.get("SECTOR"))
        cambio = bool(r.get("sustituido")) or (ref_ped and ref_ser and ref_ped != ref_ser) or (lit_ped and lit_ser and lit_ped != lit_ser) or (sec_ped and sec_ser and sec_ped != sec_ser)
        cls = ' class="sust"' if cambio else ""
        marca = ' <span class="cambio">⟲</span>' if cambio else ""
        filas_lista.append(f"""
<tr{cls}>
  <td class="c">{_html_esc(_set(r.get('picking_tipo')))}{_html_esc(_set(r.get('picking_numero')))}</td>
  <td class="c">{_html_esc(_dt_es(r.get('fecha_hora')))}</td>
  <td class="c">{_html_esc(_nombre_corto(r.get('empleado_nombre')))}</td>
  <td class="c">{_html_esc(r.get('POSICION') or '')}</td>
  <td class="c">{_html_esc(ref_ped)}</td>
  <td class="c">{_html_esc(lit_ped)}</td>
  <td class="c">{_html_esc(sec_ped)}</td>
  <td class="c" style="font-weight:700;">{_html_esc(ref_ser)}</td>
  <td class="c">{_html_esc(lit_ser)}</td>
  <td class="c">{_html_esc(sec_ser)}</td>
  <td class="c" style="font-weight:700;">{_num(r.get('cantidad_partida')):,.0f}</td>
  <td class="c">{'SÍ' + marca if cambio else ''}</td>
  <td class="c">{_html_esc(_set(r.get('ean_escaneado')))}</td>
  <td title="{_html_esc(_set(r.get('ocr_texto')))}">{_html_esc((_set(r.get('ocr_texto')) or '')[:28])}</td>
  <td class="c">{_html_esc(_set(r.get('calibre')))}</td>
</tr>""")

    if not detalle:
        filas_lista = ['<tr><td colspan="15" class="c" style="padding:8mm;">Sin eventos de pistoleo registrados todavía.</td></tr>']

    bloques = [(b, 1) for b in filas_lista]

    def cabecera_fn(pag: int, total: int) -> str:
        return _cabecera(o, 'Detalle del Pistoleo', pag, total)

    meta_base = f"{len(detalle)} eventos · Generado {_dt_es(datetime.now(timezone.utc))} (hora España)"
    def titulo_fn(pag: int) -> str:
        h2 = f"DETALLE DEL PISTOLEO — PEDIDO {_html_esc(str(o.get('NUMERO_PEDIDO') or ''))}"
        if pag > 1:
            h2 += " (cont.)"
        return f'<h2>{h2}</h2>\n    <div class="meta">{meta_base}</div>'

    thead = """<colgroup>
      <col style="width:5%"><col style="width:9%"><col style="width:7%"><col style="width:4%">
      <col style="width:8%"><col style="width:7%"><col style="width:8%">
      <col style="width:8%"><col style="width:7%"><col style="width:8%">
      <col style="width:5%"><col style="width:5%"><col style="width:11%"><col style="width:11%"><col style="width:6%">
    </colgroup>
    <thead><tr>
      <th>Parte</th><th>Fecha / Hora (ES)</th><th>Empleado</th><th>N.L.</th>
      <th>Ref. Pedida</th><th>Litraje Ped.</th><th>Sector Ped.</th>
      <th>Ref. Servida</th><th>Litraje Serv.</th><th>Sector Serv.</th>
      <th>Cant.</th><th>Cambio</th><th>EAN</th><th>OCR / Pasaporte</th><th>Calibre</th>
    </tr></thead>"""

    body = _paginar_informe(bloques, cabecera_fn, "", titulo_fn, thead, apaisada=True)
    return _doc_html(f"Detalle Pistoleo - Pedido {numero_pedido}", body)


# ---------------------------------------------------------------------------
# Informe 3 — Control de Acopio (A4 horizontal)
# ---------------------------------------------------------------------------

def _susts_por_linea(detalle: list) -> dict[int, list[str]]:
    """Mapa posición de línea → descripciones de sustitución completas."""
    mapa: dict[int, list[str]] = {}
    for d in detalle:
        if not d.get("sustituido"):
            continue
        pos = int(_num(d.get("POSICION")))
        mapa.setdefault(pos, []).append(
            f"{d.get('ref_original') or '—'} ({_set(d.get('LITRAJE_PEDIDA'))} · {_set(d.get('SECTOR_PEDIDA'))})"
            f" → {d.get('ref_servida') or '—'} ({_set(d.get('LITRAJE_SERVIDA') or d.get('TALLA'))} · {_set(d.get('SECTOR_SERVIDA') or d.get('SECTOR'))})"
        )
    return mapa


def build_control_html(bq_client, project, dataset, picking_dataset, picking_table, matriculas_table, numero_pedido) -> str:
    """Control de Acopio en A4 horizontal: pedido vs acopiado con trazabilidad de cambios."""
    datos = _load_datos(bq_client, project, dataset, picking_dataset, picking_table, matriculas_table, numero_pedido)
    o = datos["o"]
    control = datos["control"]
    sin_localizar = datos["sin_localizar"]
    sust_por_linea = _susts_por_linea(datos["detalle"])

    filas_lista: list[tuple[str, int]] = []
    total_ped = total_acop = 0.0
    for l in control:
        ped = _num(l.get("UNIDADES"))
        pend = _num(l.get("UNIDADES_PENDIENTES"))
        acop = _num(l.get("ACOPIADO"))
        dif = pend - max(acop, 0)
        total_ped += ped
        total_acop += acop
        pos = int(_num(l.get("POSICION_PEDIDO")))
        susts = sust_por_linea.get(pos, [])
        estado = "✔ Completo" if dif <= 0 and ped > 0 else ("● Parcial" if acop > 0 else "○ Sin acopiar")
        color_estado = "#0e8a80" if dif <= 0 and ped > 0 else ("#e0a13c" if acop > 0 else "#8fb0ac")
        bloque_html = f"""
<tr>
  <td class="c" style="font-weight:700; color:#0e8a80;">{pos}</td>
  <td class="c" style="font-weight:700;">{_html_esc(l.get('REFERENCIA_ARTICULO') or '')}</td>
  <td>{_html_esc(l.get('DESCRIPCION_ARTICULO') or '')}</td>
  <td class="c">{_html_esc(l.get('TALLA') or '')}</td>
  <td class="c">{_html_esc(l.get('SECTOR') or '')}</td>
  <td class="c">{ped:,.0f}</td>
  <td class="c">{pend:,.0f}</td>
  <td class="c" style="font-weight:700; color:{color_estado};">{acop:,.0f}</td>
  <td class="c">{dif:,.0f}</td>
  <td class="c">{'S' if l.get('SUSTITUIDO') else ''}</td>
  <td class="c" style="font-weight:600; color:{color_estado};">{estado}</td>
  <td class="c">{_html_esc(l.get('PARTES') or '')}</td>
</tr>"""
        peso_bloque = 1
        for s in susts:
            bloque_html += f'<tr class="sust"><td colspan="12"><span class="sust-tag">↔ Cambio de artículo:</span> {_html_esc(s)}</td></tr>'
            peso_bloque += 1
        filas_lista.append((bloque_html, peso_bloque))

    if sin_localizar:
        extra = '<tr class="sust"><td colspan="12"><b>REFERENCIAS SERVIDAS NO LOCALIZADAS EN EL CATÁLOGO (revisar)</b></td></tr>'
        for s in sin_localizar:
            extra += f'<tr class="sust"><td class="c">{_html_esc(s.get("ref_servida"))}</td><td colspan="11">EAN: {_html_esc(s.get("ean_escaneado"))}</td></tr>'
        filas_lista.append((extra, len(sin_localizar) + 1))

    bloques = filas_lista or [('<tr><td colspan="12" class="c" style="padding:8mm;">Este pedido no tiene líneas activas.</td></tr>', 1)]

    def cabecera_fn(pag: int, total: int) -> str:
        return _cabecera(o, 'Control de Acopio', pag, total)

    meta_base = f"Total pedido {total_ped:,.0f} uds · Acopiado {total_acop:,.0f} uds · Generado {_dt_es(datetime.now(timezone.utc))}"
    def titulo_fn(pag: int) -> str:
        h2 = f"CONTROL DE ACOPIO — PEDIDO {_html_esc(str(o.get('NUMERO_PEDIDO') or ''))}"
        if pag > 1:
            h2 += " (cont.)"
        return f'<h2>{h2}</h2>\n    <div class="meta">{meta_base}</div>'

    thead = """<colgroup>
      <col style="width:4%"><col style="width:8%"><col style="width:auto"><col style="width:7%"><col style="width:8%">
      <col style="width:5.5%"><col style="width:6.5%"><col style="width:6.5%"><col style="width:5.5%">
      <col style="width:4%"><col style="width:8%"><col style="width:9%">
    </colgroup>
    <thead><tr>
      <th>N.L.</th><th>Ref. Pedida</th><th style="text-align:left;">Descripción</th><th>Litraje</th><th>Sector</th>
      <th>Pedido</th><th>Pendiente</th><th>Pistoleado</th><th>Difer.</th><th>Sust.</th><th>Estado</th><th>Partes</th>
    </tr></thead>"""

    body = _paginar_informe(bloques, cabecera_fn, "", titulo_fn, thead, apaisada=True)
    return _doc_html(f"Control Acopio - Pedido {numero_pedido}", body)


# ---------------------------------------------------------------------------
# Compatibilidad: desglose = detalle + control
# ---------------------------------------------------------------------------

def build_desglose_html(bq_client, project, dataset, picking_dataset, picking_table, matriculas_table, numero_pedido) -> str:
    """Informe desglosado: Detalle del Pistoleo + Control de Acopio (ambos apaisados)."""
    pag_det = build_detalle_html(bq_client, project, dataset, picking_dataset, picking_table, matriculas_table, numero_pedido)
    pag_ctrl = build_control_html(bq_client, project, dataset, picking_dataset, picking_table, matriculas_table, numero_pedido)
    body_det = pag_det.split("<body>", 1)[1].split("</body>", 1)[0]
    body_ctrl = pag_ctrl.split("<body>", 1)[1].split("</body>", 1)[0]
    css = pag_det.split("<style>", 1)[1].split("</style>", 1)[0]
    return f"""<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<title>Desglose Pistoleo - Pedido {_html_esc(numero_pedido)}</title>
<style>{css}</style>
</head>
<body>
{body_det}
{body_ctrl}
</body>
</html>"""


# ---------------------------------------------------------------------------
# Etiquetas a Sacar (mismo estilo corporativo)
# ---------------------------------------------------------------------------

def _pagina_cabecera_etiquetas(fecha: str, page_num: int, total_pages: int) -> str:
    logo = _logo_b64()
    logo_html = f'<img class="cab-logo" src="data:image/png;base64,{logo}">' if logo else ""
    return f"""
<div class="cabecera">
  <div class="cab-fiscal">
    <div class="empresa">VIVEROS ELCHE, S.L.</div>
    <div class="dir">{"".join(f"<div>{_html_esc(l)}</div>" for l in _DIRECCION)}</div>
  </div>
  <div style="flex:1;"></div>
  <div class="cab-doc">
    {logo_html}
    <table class="tabla-doc">
      <tr><td class="k">Documento</td><td class="v">Etiquetas a Sacar</td></tr>
      <tr><td class="k">Fecha</td><td class="v">{_html_esc(_fecha_es(fecha))}</td></tr>
      <tr><td class="k">Página</td><td class="v">{page_num} de {total_pages}</td></tr>
    </table>
  </div>
</div>
"""


def build_etiquetas_html(pedidos: list, fecha: str) -> str:
    """HTML imprimible del listado de etiquetas a sacar del día."""
    paginas: list[list] = []
    actual: list | None = None
    for p in pedidos:
        if actual is None or len(actual) >= 10:
            actual = []
            paginas.append(actual)
        actual.append(p)
    if not paginas:
        paginas = [[]]

    pages_html = []
    total_pages = len(paginas)
    for idx, pedidos_pag in enumerate(paginas, start=1):
        html = _pagina_cabecera_etiquetas(fecha, idx, total_pages)
        html += f"""
  <div class="titulo-informe">
    <h2>ETIQUETAS A SACAR — {_html_esc(_fecha_es(fecha))}</h2>
    <div class="meta">Listado del día · Página {idx} de {total_pages}</div>
  </div>"""
        if not pedidos_pag or not any(p.get("etiquetas") for p in pedidos_pag):
            html += '<div style="padding:20px; text-align:center; color:#5a7578;">No hay etiquetas a sacar en esta fecha.</div>'
        else:
            for p in pedidos_pag:
                pend = p.get("resumen", {}).get("pendiente", 0)
                managed = p.get("resumen", {}).get("impresa", 0) + p.get("resumen", {}).get("encolada", 0)
                estado = "TODAS GESTIONADAS" if pend == 0 else (f"{pend} PENDIENTES" if managed == 0 else f"PARCIAL ({pend} pend.)")
                html += f"""
<table class="datos" style="margin-bottom:4mm;">
  <thead><tr><th colspan="6" style="text-align:left; font-size:8pt;">
    PEDIDO #{_html_esc(p.get('pedido'))} — {_html_esc(p.get('cliente'))} — {_html_esc(p.get('finca') or 'SIN FINCA')}
    <span style="float:right;">[{_html_esc(estado)}]</span>
  </th></tr>
  <tr>
    <th style="width:26mm;">Referencia</th><th style="width:16mm;">Litraje</th><th style="width:18mm;">Sector</th>
    <th style="width:13mm;">Cant.</th><th style="text-align:left;">Motivo</th><th style="width:20mm;">Estado</th>
  </tr></thead>
  <tbody>"""
                for e in p.get("etiquetas", []):
                    html += f"""
    <tr>
      <td class="c ref">{_html_esc(e.get('referencia'))}</td>
      <td class="c">{_html_esc(e.get('litraje') or '—')}</td>
      <td class="c">{_html_esc(e.get('sector') or '—')}</td>
      <td class="c" style="font-weight:700;">{_num(e.get('cantidad')):.0f}</td>
      <td>{_html_esc(e.get('motivo'))}</td>
      <td class="c">{_html_esc(e.get('estado'))}</td>
    </tr>"""
                html += "</tbody></table>"
        pages_html.append(f'<div class="pagina">{html}</div>')

    return _doc_html(f"Etiquetas a Sacar — {_fecha_es(fecha)}", "".join(pages_html))
