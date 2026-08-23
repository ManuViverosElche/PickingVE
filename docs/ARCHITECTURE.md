# Arquitectura Técnica - PickingVE

## 1. Arquitectura General (Android Clean Architecture + MVVM)

La aplicación sigue el patrón recomendado por Google para Android moderno (Jetpack Compose, Clean Architecture, Kotlin Coroutines & Flow):

```
+-------------------------------------------------------------+
|                      UI Layer (Jetpack Compose)             |
|   Screens: PickingScreen, OrderListScreen, ScannerOverlay   |
+-------------------------------------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|                    ViewModel Layer (StateFlow)              |
|   PickingViewModel, OrderListViewModel, FaenaDashboardVM,   |
|   SettingsViewModel, LoginViewModel, AdminFincas/UsersVM    |
+-------------------------------------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|                    Domain Layer (Use Cases)                 |
|   ParsePlantPassportUseCase, MatchOcrUseCase, ScanDebouncer |
+-------------------------------------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|               Data Layer (Repository Pattern)               |
|   PickingRepository, SettingsRepository                     |
+-------------------------------------------------------------+
               /                               \
              v                                 v
+-----------------------------+   +---------------------------+
| Local Data Source (Room DB) |   | Remote Data Source        |
| - products                  |   | - BigQuery API / Proxy    |
| - orders + order_lines      |   | - Telegram Bot API        |
| - picking_records           |   +---------------------------+
| - encargados/litrajes/      |
|   sectores/chat_estado      |
+-----------------------------+
```

---

## 2. Componentes Clave

### A. Módulo de Escaneo e Imagen (CameraX + ML Kit)
- **CameraX ImageAnalysis**: Captura cuadros a 30 fps.
- **ML Kit Barcode Scanning**: Detección ultrasensible de EAN-13, EAN-8, QR y DataMatrix.
- **Debounce State Machine**:
  - Mantiene un timestamp del último código procesado (`lastScannedCode`, `lastScannedTimestamp`).
  - Ignores re-scans si `currentTime - lastScannedTimestamp < debounceThresholdMs` y `code == lastScannedCode`.
- **ML Kit Text Recognition (OCR)**:
  - Activado mediante botón manual o fallback automático cuando no hay EAN presente en la escena.
  - Procesa regiones de interés (ROI) para extraer referencias o números de partida.

### B. Motor Offline-First (Room DB & WorkManager)
- Base de datos relacional SQLite empaquetada mediante Room.
- **WorkManager**: Tareas de sincronización en segundo plano con condición `NetworkType.CONNECTED`.
- **Estrategia de Sync**:
  1. *Pull*: Descarga parcial/incremental de tablas desde BigQuery usando `updated_at`.
  2. *Push*: Envío diferido de `PickingLog` pendientes con marca de agua (`synced = false`).

### C. Integración Telegram Bot & Reporte Excel
- Generador **XlsxReportGenerator**: crea el `.xlsx` con la estructura exacta del cliente (ver `Documentacion/picking_260833_I.xlsx`) usando ZIP de XML OOXML, sin librerías pesadas tipo Apache POI.
- Servicio Ktor que realiza peticiones HTTP POST multipart/form-data a `https://api.telegram.org/bot<TOKEN>/sendDocument`.
- Adjunta el archivo .xlsx generado desde Room DB.

---

## 3. Tecnologías Seleccionadas
- **Lenguaje**: Kotlin 1.9.24 (JVM 17)
- **UI**: Jetpack Compose + Material Design 3
- **Local DB**: Room Persistence Library (esquema exportado en `app/schemas/`)
- **DI**: sin framework; singletons manuales (`AppDatabase.getDatabase`, `HttpClient` compartido en `PickingApiClient`/`TelegramReporter`), ViewModels creados en `AppNavHost`
- **Red**: Ktor Client (engine CIO) + kotlinx.serialization
- **IA/Vision**: Google ML Kit (Barcode Scanning & Text Recognition)
- **Background Jobs**: AndroidX WorkManager (`PickingSyncWorker`)
