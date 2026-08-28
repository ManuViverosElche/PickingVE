"""Informe PDF del analisis de inventario (D-222).

Estilo corporativo alineado con informe_pdf.py (corp-dark #0c3a3f, bordes
#b8d8d3, zebra #eef7f5, fuera de sector ambar #fdf3e0). Paginacion propia:
cabecera compacta + tablas Resumen / Lineas / Fuera de sector / Etiquetas.
"""
import io
import os

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.pdfgen import canvas as rl_canvas

F = "Helvetica"
FB = "Helvetica-Bold"

_LOGO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "web", "manager", "viveros_logo.png")

PAGE_W, PAGE_H = A4
MARG = 10 * mm

_DARK = colors.HexColor("#0c3a3f")
_BORDE = colors.HexColor("#b8d8d3")
_ZEBRA = colors.HexColor("#eef7f5")
_AMBAR_BG = colors.HexColor("#fdf3e0")
_AMBAR_TX = colors.HexColor("#a06a10")
_GRIS_TX = colors.HexColor("#3D565A")
_ROJO = colors.HexColor("#962622")
_VERDE = colors.HexColor("#2e7d32")


def _fmt(v) -> str:
    try:
        n = float(v)
    except (TypeError, ValueError):
        return "0"
    if abs(n - round(n)) < 0.05:
        return str(int(round(n)))
    return f"{n:.1f}".replace(".", ",")


def _cortar(texto: str, ancho_mm: float, size: float = 6.8) -> str:
    max_chars = max(3, int(ancho_mm / (size * 0.155 * mm)))
    return str(texto or "")[:max_chars]


class _Pdf:
    def __init__(self, datos: dict):
        self.datos = datos
        self.buf = io.BytesIO()
        self.c = rl_canvas.Canvas(self.buf, pagesize=A4)
        self.pagina = 0
        self.y = PAGE_H - MARG

    def cabecera(self) -> float:
        c = self.c
        y = PAGE_H - MARG
        c.setFillColor(colors.black)
        c.setFont(FB, 12)
        c.drawString(MARG, y - 5 * mm, "VIVEROS ELCHE, S.L.")
        c.setFont(FB, 9)
        c.setFillColor(_DARK)
        c.drawString(MARG, y - 11 * mm, "INVENTARIO \u00b7 AN\u00c1LISIS ESPERADO vs CONTADO")
        c.setFont(F, 7)
        c.setFillColor(_GRIS_TX)
        d = self.datos
        generado = str(d.get("generado", "")).replace("T", " ")[:16]
        ambito = str(d.get("finca", ""))
        if d.get("sector"):
            ambito += f" \u00b7 Sector {d.get('sector')}"
        c.drawString(MARG, y - 16 * mm, f"{ambito}   \u00b7   Generado: {generado}")
        if os.path.exists(_LOGO):
            try:
                c.drawImage(_LOGO, PAGE_W - MARG - 22 * mm, y - 15 * mm,
                            width=22 * mm, height=13 * mm, preserveAspectRatio=True, mask="auto")
            except Exception:
                pass
        c.setStrokeColor(_BORDE)
        c.setLineWidth(0.6)
        c.line(MARG, y - 19 * mm, PAGE_W - MARG, y - 19 * mm)
        return y - 23 * mm

    def pie(self) -> None:
        c = self.c
        c.setStrokeColor(_BORDE)
        c.setLineWidth(0.4)
        c.line(MARG, MARG + 6 * mm, PAGE_W - MARG, MARG + 6 * mm)
        c.setFont(F, 6.5)
        c.setFillColor(_GRIS_TX)
        c.drawString(MARG, MARG + 2 * mm, "Viveros Elche, S.L. \u00b7 Inventario")
        c.drawRightString(PAGE_W - MARG, MARG + 2 * mm, f"P\u00e1gina {self.pagina}")
        c.showPage()

    def estado_color(self, estado: str):
        if estado == "EXCESO":
            return _AMBAR_TX
        if estado == "FALTA":
            return _ROJO
        return _VERDE

    def tabla(
        self,
        titulo: str,
        cols,
        filas,
        estilo_fila=None,
        nota: str | None = None,
    ) -> None:
        """cols: [(texto, ancho_mm)]; filas: [[celda]]; celda=str|num|(texto,color)."""
        c = self.c
        table_w = sum(w for _, w in cols)

        def fila_cabecera_tabla():
            y2 = self.y
            c.setFillColor(_DARK)
            c.rect(MARG, y2 - 6 * mm, table_w, 6 * mm, stroke=0, fill=1)
            c.setFillColor(colors.white)
            c.setFont(FB, 7.2)
            x = MARG
            for texto, w in cols:
                c.drawString(x + 1 * mm, y2 - 4.2 * mm, texto)
                x += w
            self.y = y2 - 6 * mm

        # Titulo de seccion
        if self.y - 12 * mm < MARG + 9 * mm:
            self.pie()
            self.pagina += 1
            self.y = self.cabecera()
        c.setFillColor(_DARK)
        c.setFont(FB, 8.6)
        c.drawString(MARG, self.y - 4.5 * mm, titulo)
        self.y -= 7 * mm
        fila_cabecera_tabla()

        rh = 5.0 * mm
        for i, fila in enumerate(filas):
            if self.y - rh < MARG + 9 * mm:
                self.pie()
                self.pagina += 1
                self.y = self.cabecera()
                fila_cabecera_tabla()
            y = self.y
            fondo = None
            if estilo_fila is not None:
                fondo = estilo_fila(i, fila)
            elif i % 2 == 1:
                fondo = _ZEBRA
            if fondo is not None:
                c.setFillColor(fondo)
                c.rect(MARG, y - rh, table_w, rh, stroke=0, fill=1)
            c.setStrokeColor(_BORDE)
            c.setLineWidth(0.25)
            c.rect(MARG, y - rh, table_w, rh, stroke=1, fill=0)
            x = MARG
            for j, valor in enumerate(fila):
                _, w = cols[j]
                txt = valor if isinstance(valor, str) else _fmt(valor)
                color_extra = None
                if isinstance(valor, tuple):
                    txt, color_extra = valor
                if color_extra is not None:
                    c.setFillColor(color_extra)
                else:
                    c.setFillColor(colors.black)
                c.setFont(F, 6.8)
                c.drawString(x + 1 * mm, y - rh + 1.5 * mm, _cortar(txt, w - 1.6 * mm))
                x += w
            self.y -= rh
        if nota:
            if self.y - 5 * mm < MARG + 9 * mm:
                self.pie()
                self.pagina += 1
                self.y = self.cabecera()
            c.setFont(F, 6.3)
            c.setFillColor(_GRIS_TX)
            c.drawString(MARG, self.y - 3.5 * mm, nota)
            self.y -= 6 * mm


def build_inventario_pdf(datos: dict) -> bytes:
    p = _Pdf(datos)
    p.pagina = 1
    p.y = p.cabecera()

    resumen = datos.get("resumen") or []
    if resumen:
        filas = []
        for r in resumen:
            dif = int(r.get("dif") or 0)
            color = _VERDE if dif == 0 else (_AMBAR_TX if dif > 0 else _ROJO)
            filas.append([
                str(r.get("sector", "")),
                str(r.get("sectorDesc", "")),
                _fmt(r.get("esperado")),
                _fmt(r.get("contado")),
                (_fmt(dif), color),
            ])
        p.tabla(
            "Resumen por sector",
            [("Sector", 18 * mm), ("Descripci\u00f3n", 62 * mm), ("Esperado", 24 * mm),
             ("Contado", 24 * mm), ("Dif.", 20 * mm)],
            filas,
        )

    lineas = datos.get("lineas") or []
    filas_lineas = []
    for f in lineas:
        col = p.estado_color(str(f.get("estado", "")))
        dif = float(f.get("dif") or 0)
        filas_lineas.append([
            str(f.get("sector", "")),
            str(f.get("ref", "")),
            str(f.get("nombre", "")),
            str(f.get("litraje", "")),
            _fmt(f.get("esperado")),
            _fmt(f.get("contado")),
            (_fmt(dif), col),
            (str(f.get("estado", "")), col),
        ])
    if filas_lineas:
        p.tabla(
            f"Referencias ({len(filas_lineas)})",
            [
                ("Sector", 14 * mm),
                ("Ref.", 26 * mm),
                ("Planta", 54 * mm),
                ("Litraje", 16 * mm),
                ("Esp.", 17 * mm),
                ("Cont.", 17 * mm),
                ("Dif.", 15 * mm),
                ("Estado", 16 * mm),
            ],
            filas_lineas,
            estilo_fila=lambda i, fila: (
                _AMBAR_BG if (fila[-1][0] == "EXCESO" or fila[-1][0] == "FALTA")
                else (_ZEBRA if i % 2 else None)
            ),
            nota="Esp. = stock FactuSOL \u00b7 Cont. = plantas pistoleadas en ese sector.",
        )

    fuera = datos.get("fueraSector") or []
    if fuera:
        filas_fuera = []
        for r in fuera:
            filas_fuera.append([
                str(r.get("fechaHora", ""))[:16],
                str(r.get("ref", "")),
                str(r.get("nombre", "")),
                str(r.get("litraje", "") or "\u2014"),
                str(r.get("sectorEtiquetaDesc", "") or "\u2014"),
                str(r.get("sectorDesc", "") or r.get("sector", "") or "\u2014"),
                _fmt(r.get("cantidad")),
                str(r.get("empleado", "")),
            ])
        p.tabla(
            f"Plantas FUERA DE SECTOR \u2014 reetiquetar ({len(fuera)})",
            [("Fecha", 22 * mm), ("Ref.", 22 * mm), ("Planta", 32 * mm), ("Litraje", 16 * mm), ("Su sector", 18 * mm),
             ("Encontrada en", 18 * mm), ("Cant.", 12 * mm), ("Empleado", 24 * mm)],
            filas_fuera,
            estilo_fila=lambda i, _f: (_AMBAR_BG if i % 2 == 0 else None),
        )

    etiquetas = datos.get("etiquetas") or []
    if etiquetas:
        filas_et = []
        for r in etiquetas:
            motivo = "Reetiquetado" if r.get("reetiquetar") else ("OCR sin EAN" if r.get("sinEan") else "Conteo manual")
            filas_et.append([
                str(r.get("ref", "")),
                str(r.get("litraje", "")),
                str(r.get("sectorEtiquetaDesc", "") or r.get("sectorDesc", "") or r.get("sector", "")),
                motivo,
                _fmt(r.get("cantidad")),
                str(r.get("fechaHora", ""))[:16],
            ])
        p.tabla(
            f"Etiquetas a sacar ({len(etiquetas)})",
            [("Ref.", 30 * mm), ("Litraje", 22 * mm), ("Sector", 20 * mm),
             ("Motivo", 40 * mm), ("Cant.", 14 * mm), ("Fecha", 26 * mm)],
            filas_et,
            estilo_fila=lambda i, _f: (_ZEBRA if i % 2 else None),
        )
    elif not lineas:
        p.tabla(
            "Sin datos",
            [("Informaci\u00f3n", 150 * mm)],
            [["No hay conteos para este \u00e1mbito."]],
        )

    p.pie()
    p.c.save()
    return bytes(p.buf.getvalue())
