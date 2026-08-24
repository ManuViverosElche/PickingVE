import base64

code = (
    "import os\n"
    "print('FILES:', sorted(os.listdir('/app')))\n"
    "src = open('/app/punteo_pdf.py').read()\n"
    "print('PDF_LEN:', len(src))\n"
    "print('HAS_NUEVO:', ('DOCUMENTO' in src and '_COLS' in src))\n"
)
with open("probe_payload.b64", "w") as f:
    f.write(base64.b64encode(code.encode()).decode())
print("payload listo")
