# Plan Modular: Paneles Web de Gestión (`plan-web-panels.md`)

## Objetivo
Desarrollar, actualizar y verificar los paneles web de gestión (`backend/web/manager/`, etc.) asegurando su integración con la API de Cloud Run y una experiencia de usuario robusta.

## Módulos y Ficheros Clave
- `backend/web/manager/`: Panel de gestión principal (HTML/JS/CSS).
- `reportes/manager/publicar.ps1`: Script de despliegue automatizado a Cloud Run (D-156).
- Servidor MCP de Playwright para pruebas e inspección automatizada.

---

## Fases de Ejecución

### Fase 1: Revisión y Desarrollo de Vistas Web
1. Inspeccionar componentes estáticos e interactivos en `backend/web/manager/`.
2. Asegurar la comunicación correcta con los endpoints de la API en Cloud Run (`pickingve-api-938422468946.europe-west1.run.app`).
3. Respetar estrictamente la prohibición de tocar el submódulo Truffaut (`backend/web/truffaut/`).

### Fase 2: Automatización y Testing con Playwright
1. Configurar y ejecutar pruebas de humo visuales y funcionales sobre las interfaces web utilizando el servidor MCP de Playwright.
2. Comprobar tiempos de carga, respuesta de formularios y visualización de tablas de pedidos/stock.

### Fase 3: Despliegue en Producción (D-156)
1. Ejecutar el script de publicación oficial (`powershell -File reportes\manager\publicar.ps1`).
2. Validar el funcionamiento directamente contra la URL de producción (nunca dejar solo versión local).
