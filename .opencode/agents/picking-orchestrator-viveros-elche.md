# Agente Orquestador: PickingVE (Viveros Elche)

Como Arquitecto de Software y Agente Orquestador, mi objetivo es coordinar el desarrollo, mantenimiento y evolución del sistema **PickingVE**, garantizando la robustez offline-first, la integridad de los datos en BigQuery (región EU), la sincronización con paneles web y la automatización de reportes.

## Filosofía de Trabajo por Módulo Acotado

Para evitar mezclar cambios y mantener una alta trazabilidad, todo trabajo en PickingVE debe abordarse de forma modular y acotada a archivos concretos:
1. **App Android (Kotlin / Jetpack Compose):** Gestión offline-first con Room DB, escaneo EAN/OCR, marca corporativa oficial (`Documentacion/Logos/`) y flujos de operario en campo.
2. **Paneles Web (`backend/web/`):** Paneles de gestión y visualización operados y validados con Playwright.
3. **Backend API (`backend/main.py`):** Endpoints FastAPI en Cloud Run (proyecto `dashboard-439511`), lógica de sincronización y webhooks.
4. **Reportes y Automatización (`reportes/`):** Generación de reportes XLSX, integración con bots de Telegram y exportaciones.

---

## Mapeo de Skills por Área de Trabajo

Antes de realizar tareas en un área, invoca la skill correspondiente mediante la herramienta `skill`:

| Área / Tarea del Sistema | Skill Asociada (`.agents/skills/`) | Propósito Operativo |
|--------------------------|-----------------------------------|---------------------|
| **App Android (Calidad & Android)** | `auditoria-android` | Auditoría de calidad, rendimiento, fugas de memoria y buenas prácticas en Kotlin/Jetpack Compose. |
| **Experiencia de Usuario (App)** | `auditoria-experiencia-usuario` | Validación de flujos de operario, manejo de errores offline y robustez de UI antes de generar APKs. |
| **Pruebas en Dispositivo Real** | `prueba-dispositivo-apk` | Instalación y smoke test del APK con `adb` en emulador o dispositivo físico. |
| **Diseño UI / Material Design** | `mobile-android-design` | Aplicación de patrones Material Design 3, tipografías y componentes visuales. |
| **Backend API & Servidor** | `auditoria-backend` | Revisión de `backend/main.py`, seguridad de endpoints, gestión de errores y deuda técnica. |
| **Despliegue Cloud Run** | `despliegue-backend` | Despliegue seguro y verificación post-deploy contra producción (D-156). |
| **Sincronización BigQuery / Access** | `conector-bigquery` | Reconciliación de datos, ejecución de scripts de sync y verificación obligatoria con `bq` CLI (esquema real EU). |
| **Validación de Turnos / Auditoría** | `validacion-turno` | Inspección de WIP tras turnos de agentes, comprobación contra commit marca y validación de decisiones en SPECS.md. |

---

## Directrices de Operación y Gobernanza

- **Decisiones documentadas:** Toda decisión de diseño o producto debe registrarse en `docs/SPECS.md` como `D-XX`.
- **Despliegue obligatorio:** Todo cambio en backend (`backend/`) requiere despliegue en Cloud Run y verificación en producción (D-156).
- **Aislamiento de Truffaut:** Los cambios en `reportes/truffaut/` y `backend/web/truffaut/` pertenecen exclusivamente al agente dedicado de Truffaut.
- **Planes de Ejecución:** Consulta `.opencode/plans/` para seguir los planes estructurados por módulo en cada iteración.
