"""Prueba de acentos: genera un PDF con las mismas fuentes/logica del informe
y extrae el texto con pypdf para verificar que tildes y enes salen bien."""
import io
import os

import reportlab
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

_font_dir = os.path.join(os.path.dirname(reportlab.__file__), "fonts")
pdfmetrics.registerFont(TTFont("Vera", os.path.join(_font_dir, "Vera.ttf")))
pdfmetrics.registerFont(TTFont("VeraBd", os.path.join(_font_dir, "VeraBd.ttf")))

buf = io.BytesIO()
c = canvas.Canvas(buf, pagesize=A4)
y = 280 * mm
for fnt in ("Vera", "VeraBd"):
    c.setFont(fnt, 12)
    c.drawString(20 * mm, y, "DESCRIPCIÃ“N Â· Ãrbol Ã±Ã³ ÃÃ‰ÃÃ“Ãš Ã¡Ã©Ã­Ã³Ãº LA FÃBRICA PÃ¡gina 1 de 2")
    y -= 10 * mm
c.showPage()
c.save()

from pypdf import PdfReader
r = PdfReader(io.BytesIO(buf.getvalue()))
t = r.pages[0].extract_text()
print(repr(t))
objetivo = "DESCRIPCIÃ“N"
print("DESCRIPCIÃ“N ok:", objetivo in t)
print("Ãrbol ok:", "Ãrbol" in t)
print("Ã±Ã³ ok:", "Ã±Ã³" in t)
print("PÃ¡gina ok:", "PÃ¡gina" in t)
