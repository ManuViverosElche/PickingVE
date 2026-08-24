import base64
import re

MAPA = {
    "puntee_report": "informe_datos",
    "puntee_pdf": "informe_pdf",
    "punteo_html": "informe_html",
}

for nuevo in ("informe_datos", "informe_pdf", "informe_html"):
    with open(f"{nuevo}.py", "r", encoding="utf-8") as f:
        src = f.read()
    for viejo, n in MAPA.items():
        src = re.sub(rf"\b{viejo}\b", n, src)
    assert not re.search(r"^\s*(from|import)\s+puntee_", src, re.M), (
        f"{nuevo} aun importa puntee_"
    )
    with open(f"{nuevo}.py", "w", encoding="utf-8", newline="\n") as f:
        f.write(src)
print("modulos normalizados")

mods = {}
for n in ("informe_datos", "informe_pdf", "informe_html"):
    with open(f"{n}.py", "rb") as f:
        mods[n] = base64.b64encode(f.read()).decode("ascii")
assert "DOCUMENTO DE PUNTEO" in base64.b64decode(mods["informe_pdf"]).decode("utf-8")

bloques = []
for n, b in mods.items():
    lineas = "\n".join('        "' + b[i : i + 100] + '"' for i in range(0, len(b), 100))
    bloques.append(f'    "{n}": (\n{lineas}\n    ),')
nuevo = "_PUNTEO_EMBEDDED_B64 = {\n" + "\n".join(bloques) + "\n}\n"

with open("main.py", "r", encoding="utf-8") as f:
    src = f.read()
src2 = re.sub(r"_PUNTEO_EMBEDDED_B64 = \{.*?\n\}\n", lambda _: nuevo, src, count=1, flags=re.S)
assert src2 != src, "embed no sustituido"
with open("main.py", "w", encoding="utf-8", newline="\n") as f:
    f.write(src2)
print("embed regenerado")
