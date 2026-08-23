# Gobernanza del Proyecto, Estándares y Buenas Prácticas

## 1. Principios de Desarrollo
- **Offline-First Architecture**: La UI interactúa **únicamente** con la base de datos local (Room). La capa de red trabaja asíncronamente en segundo plano.
- **Single Source of Truth (SSOT)**: Room DB es la única fuente de verdad para el estado activo de la app.
- **Fail-Safe Scanning**: Las capturas de código de barras no pueden perderse por caídas de red o fallos de batería. Se persisten inmediatamente antes de actualizar la UI.

## 2. Convenciones de Código
- **Kotlin Standard**: Seguir las [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- **Naming Conventions**:
  - Clases: `PascalCase` (ej. `PickingRepositoryImpl`).
  - Funciones y Variables: `camelCase` (ej. `processBarcodeScan`).
  - Constantes: `UPPER_SNAKE_CASE` (ej. `DEFAULT_SCAN_DEBOUNCE_MS = 2000L`).
  - Layouts / Composables: `PascalCase` para componentes `@Composable`.

## 3. Seguridad y Configuración
- **Secretos**: No guardar nunca tokens de Telegram, credenciales de GCP/BigQuery ni claves de API en control de versiones.
- El token de Telegram vive en `secrets.properties` (**gitignored**) y se inyecta como `BuildConfig.DEFAULT_TELEGRAM_BOT_TOKEN`; la app permite sobrescribirlo desde Ajustes.
- La service account de BigQuery solo reside en el backend REST intermedio (nunca en el APK).

## 4. Control de Calidad y Pruebas
- **Unit Tests**:
  - Cobertura de ViewModels, UseCases y algoritmos de Debounce/Matching OCR.
- **UI & Integration Tests**:
  - Test de flujo de picking con base de datos en memoria (Room InMemoryDatabase).

## 5. Control de Versiones (Git Workflow)
- **Ramas** (realidad actual del repositorio):
  - `master`: única rama; código de producción estable.
  - Los cambios se commitean directamente en `master` (proyecto unipersonal + agentes). Si en el futuro se añade trabajo paralelo, crear `feature/*` antes de activar `develop`.
- **Mensajes de Commit**:
  - `feat: ...` para nuevas características.
  - `fix: ...` para corrección de errores.
  - `docs: ...` para actualización de especificaciones o gobernanza.
