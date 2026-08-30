FROM python:3.12-slim
# cache-buster v5: 2026-08-24T11:20Z

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Verificacion dura: los tres modulos del informe deben estar fisicamente
RUN ls -la /app \
    && test -s /app/informe_datos.py \
    && test -s /app/informe_pdf.py \
    && test -s /app/informe_html.py \
    && test -s /app/inventario_pdf.py \
    && test -d /app/web/inventario \
    && test -d /app/web/logistica \
    && test -s /app/web/logistica/index.html \
    && test -s /app/web/logistica/js/modules/faena.js \
    && test -s /app/web/logistica/js/modules/inventario.js \
    && test -s /app/web/logistica/js/modules/impresion.js \
    && test -s /app/web/logistica/js/modules/designer.js \
    && test -s /app/web/logistica/js/services/apiService.js \
    && test -s /app/web/logistica/js/components/orderCard.js \
    && test -s /app/web/logistica/js/components/modalDetail.js \
    && test -s /app/web/logistica/js/components/subnavFaena.js \
    && test -s /app/web/logistica/js/components/labelSelector.js \
    && echo "FICHEROS-INFORME OK"

ENV PORT=8080
EXPOSE 8080

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8080"]
