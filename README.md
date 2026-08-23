# PickingVE - App de Picking Offline-First para Viveros y Campo

Aplicación Android para gestión y captura de picking en campo con soporte **Offline-First**, lectura de códigos EAN, reconocimiento OCR de etiquetas, gestión de equivalencias/sustituciones de referencia, cálculo por partidas/sumatorios, y envío automático de reporte Excel (.xlsx) vía Telegram.

---

## 📌 Documentación del Proyecto

- 📋 [Especificaciones del Sistema (SPECS.md)](docs/SPECS.md)
- 🏗️ [Arquitectura Técnica (ARCHITECTURE.md)](docs/ARCHITECTURE.md)
- 📊 [Modelo de Datos y Sincronización (DATA_MODEL.md)](docs/DATA_MODEL.md)
- 🗄️ [Integración BigQuery paso a paso (BIGQUERY_STEPS.md)](docs/BIGQUERY_STEPS.md)
- 🛡️ [Gobernanza y Buenas Prácticas (GOVERNANCE.md)](docs/GOVERNANCE.md)

---

## 🚀 Características Principales

1. **Offline-First**:
   - Cacheado local en base de datos SQLite/Room.
   - Operativa 100% fluida en zonas de campo sin cobertura.
   - Sincronización bidireccional automática o manual con Google BigQuery.

2. **Lectura Inteligente y OCR**:
   - Escaneo de código de barras EAN-13 / EAN-8 mediante CameraX + ML Kit.
   - Reconocimiento OCR inteligente de etiquetas en macetas/plantas sin EAN.
   - **Debounce Anti-Duplicados**: Retardo configurable (ej. 1.5s - 3s) para evitar capturas múltiples del mismo código sin mover la cámara.
   - Diálogo emergente de confirmación previa a la adición al picking para evitar errores.

3. **Gestión de Artículos y Partidas**:
   - Asignación de líneas de picking a pedidos.
   - Sustitución/cambio de referencia en caso de no coincidencia con la línea original.
   - Captura opcional de **Litraje**, **Medida** y **Calibre**.
   - Sumatorio automático por lotes/partidas de macetas.

4. **Exportación e Integración**:
   - Sincronización con Google BigQuery.
   - Generación y envío instantáneo a Telegram del reporte **Excel (.xlsx)** con la estructura exacta del cliente (`picking_<pedido>.<n>_I/F.xlsx`), listo para su macro de validación.
   - Bot de Telegram configurable desde la app (token en `secrets.properties`, gitignored).
