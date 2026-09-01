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
    && test -s /app/web/logistica/js/modules/portal.js \
    && test -s /app/web/logistica/js/services/apiService.js \
    && test -s /app/web/logistica/js/services/dataConnector.js \
    && test -s /app/web/logistica/js/components/orderCard.js \
    && test -s /app/web/logistica/js/components/modalDetail.js \
    && test -s /app/web/logistica/js/components/subnavFaena.js \
    && test -s /app/web/logistica/js/components/labelSelector.js \
    && test -s /app/web/logistica/js/components/dashboardFaena.js \
    && test -s /app/web/logistica/js/components/navigation/sidebar.js \
    && test -s /app/web/logistica/js/components/navigation/submenu.js \
    && test -s /app/web/logistica/js/components/faena/pedidosDia.js \
    && test -s /app/web/logistica/js/components/faena/etiquetasDia.js \
    && test -s /app/web/logistica/js/components/faena/historicoCargas.js \
    && test -s /app/web/logistica/js/components/faena/repartoFaena.js \
    && test -s /app/web/logistica/js/components/faena/cargaOperarios.js \
    && test -s /app/web/logistica/js/components/inventario/partes.js \
    && test -s /app/web/logistica/js/components/inventario/resumenSector.js \
    && test -s /app/web/logistica/js/components/inventario/resumenReferencia.js \
    && test -s /app/web/logistica/js/components/inventario/inventarios.js \
    && test -s /app/web/logistica/js/components/inventario/mapa.js \
    && test -s /app/web/logistica/js/components/etiquetas/disenador.js \
    && test -s /app/web/logistica/js/components/etiquetas/impresion.js \
    && test -s /app/web/logistica/js/components/configuracion/personal.js \
    && test -s /app/web/logistica/js/components/configuracion/logistica.js \
    && test -s /app/web/logistica/js/components/configuracion/inventario.js \
    && test -s /app/web/logistica/js/components/configuracion/integraciones.js \
    && test -s /app/web/logistica/css/faena.css \
    && test -s /app/web/logistica/css/layout.css \
    && test -s /app/web/logistica/css/inventario.css \
    && test -s /app/web/logistica/css/etiquetas.css \
    && test -s /app/web/logistica/css/configuracion.css \
    && echo "FICHEROS-INFORME OK"

ENV PORT=8080
EXPOSE 8080

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8080"]
