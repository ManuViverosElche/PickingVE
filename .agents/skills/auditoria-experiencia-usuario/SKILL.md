---
name: auditoria-experiencia-usuario
description: Skill para auditar PickingVE desde la perspectiva de un usuario REAL (operario/encargado en campo): entradas inválidas, sin conexión, flujos interrumpidos, configuración ausente, mensajes crípticos. Activar con frases como "audita", "prueba la app como usuario", "mira si va fluida", "comprueba mensajes/errores", "la app falla", o siempre antes de generar un APK distribuible y tras cambios significativos.
---

# Skill: Auditoría de Experiencia de Usuario (PickingVE)

## Objetivo
Auditar la app Android PickingVE desde la perspectiva de un usuario REAL que "trabaja mal": entradas inválidas, sin conexión, flujos interrumpidos, configuración ausente. Garantizar que la experiencia sea fluida, intuitiva, sin fallos visibles ni mensajes técnicos crípticos. Ejecutar esta skill SIN que el usuario tenga que volver a dar instrucciones.

## Contexto del proyecto (obligatorio respetar)
- **Offline-first**: la UI solo lee Room; toda llamada de red (backend Cloud Run, Telegram) es asíncrona y tolerante a fallo.
- **Roles**: OPERARIO, ENCARGADO, SUPERUSUARIO; modos AMBAS/INVENTARIO. La pantalla inicial depende del rol (`initialScreen` en `ui/navigation/AppNavHost.kt`). Los cambios de comportamiento por rol deben tener decisión registrada en `docs/SPECS.md` (D-XX).
- **Secretos**: `secrets.properties` (gitignored) → BuildConfig. Tokens de Telegram introducidos por cada usuario van CIFRADOS en `EncryptedSharedPreferences`.
- **APK**: `assembleDebug` copia automáticamente a `apks/PickingVE-debug-<versionName>.apk`. Auditar SIEMPRE contra el más reciente de la carpeta.

## Pasos de Verificación Obligatorios

### 1. Compilación y pruebas base
- `.\gradlew.bat compileDebugKotlin testDebugUnitTest lint --console=plain` → BUILD SUCCESSFUL obligatorio (JAVA_HOME del JBR de Android Studio: `C:\Program Files\Android\Android Studio\jbr`).
- Confirmar que `assembleDebug` genera y copia el APK más reciente a `apks/`.
- Si falla algo: corregir ANTES de continuar con el resto de la auditoría.

### 2. Puertas traseras de red y credenciales (crítico)
- Buscar en `data/repository/PickingRepository.kt`, `SettingsRepository.kt`, `data/remote/PickingApiClient.kt` y `TelegramReporter.kt` cualquier `catch` que propague excepciones crudas hasta la UI (`Result.failure(e)` sin traducir).
- VERIFICAR que toda excepción que llega a un ViewModel se traduce a mensaje claro y accionable en castellano (sin stack traces, sin texto técnico tipo "HTTP 500", "timeout", "NullPointerException").
- Simular mentalmente: sin internet, timeout, backend caído (Cloud Run 503), rate-limit, token de Telegram inválido/caducado, sesión expirada, pedido inexistente en el backend. Cada caso debe producir mensaje útil o degradación silenciosa correcta (offline-first: guardar local y sincronizar después, nunca perder datos).
- Verificar que los tests unitarios existentes (`XlsxReportGeneratorTest`, `MatchOcrUseCaseTest`, `ParsePlantPassportUseCaseTest`, `ScanDebouncerTest`) siguen pasando y cubren casos límite.

### 3. Validación de entradas del usuario
- Campos numéricos (cantidades de picking, litraje, códigos manuales): filtrado de caracteres en tiempo real + `isError` visual + texto de ayuda.
- Diálogos críticos (`ConfirmPickingDialog`, `CierreLineaDialog`, chat): nunca aceptar envío con datos inválidos silenciosamente; validar en dominio (`domain/usecase/`) y reflejar error en UI.
- Teclado correcto por campo (Number/Decimal para cantidades, Text para usuarios, Email si procede).
- Escáner/OCR: `ScanDebouncer` evita lecturas duplicadas; código ilegible produce feedback claro, no bloqueo de cámara (`scanner/OcrReader.kt`, `BarcodeAnalyzer.kt`, `ui/picking/CameraScannerScreen.kt`).

### 4. Estados de la interfaz (nunca colgados ni vacíos sin explicación)
- Toda pantalla con datos asíncronos (OrderList, FaenaDashboard, GestionFaena, Picking, Settings, Admin*) debe manejar: CARGANDO (spinner breve), VACÍO (mensaje amable + acción sugerida, ej. "Baja el catálogo desde Ajustes"), ERROR (mensaje + reintento si procede).
- Prohibido el estado "cargando infinito": el primer evento del Flow de Room (aunque sea lista vacía/null) debe cerrar el spinner.
- Estados de sincronización visibles y honestos: `syncState`, `uploadState`, `pendingUploadCount` en `OrderListScreen`; si hay subidas pendientes, el usuario debe verlo y entender qué pasará al recuperar conexión.
- Listas grandes siempre con `key = { it.id }` estable en LazyColumn/LazyGrid.
- Recolección de Flows siempre con `collectAsStateWithLifecycle()`.

### 5. Navegación y fluidez
- Navegación por enum `AppScreen` en `AppNavHost`: verificar que TODA pantalla de segundo nivel (FAENA, GESTION_FAENA, PICKING, SETTINGS, USERS, FINCAS, INVENTARIO) tiene botón atrás funcional (`onBack`) que devuelve al sitio correcto según rol (OPERARIO en PICKING → FAENA, no ORDERS).
- El botón atrás del sistema no debe cerrar la app inesperadamente desde pantallas internas ni dejar estado inconsistente.
- Deep links/push (FCM → `deepLinkPedido/deepLinkLinea`): se consumen una sola vez (`onDeepLinkConsumed`), respetan restricción de rol (D-167: operarios no navegan al pedido por push) y no rompen si llegan con app cerrada o en pantalla incorrecta.
- Logout desde cualquier punto deja la app en login sin datos de sesión residual visibles.

### 6. Seguridad y configuración (multiusuario)
- NINGÚN secreto hardcodeado. Claves solo vía `secrets.properties` → BuildConfig; verificar que `secrets.properties` está gitignored y no hay tokens nuevos en código fuente.
- Tokens de Telegram/API por usuario guardados SOLO en `EncryptedSharedPreferences` (Keystore AES256), nunca en preferencias planas ni Room sin cifrar.
- Pantalla de Ajustes: campos de token ocultos (password visual), validación de formato antes de guardar, y prueba de conectividad opcional que informe si el token es inválido.
- Funcionalidad restringida por rol (usuarios/fincas solo SUPERUSUARIO, partes según rol) realmente bloqueada también en ViewModel, no solo oculta en UI.

### 7. Recursos multimedia, cámara y memoria
- Cámara del escáner: liberada correctamente al salir de la pantalla (`DisposableEffect`/`onDispose`); permiso de cámara denegado produce mensaje claro con ruta a Ajustes, no pantalla negra.
- OCR/análisis de frames detenidos cuando la pantalla no es visible (batería).
- WorkManager (`worker/PickingSyncWorker.kt`): reintento con backoff, no duplica subidas tras reinicio de proceso, y falla de forma silenciosa pero recuperable.

### 8. Multiusuario y datos compartidos
- La configuración (tokens, fincas asignadas, modo) es POR USUARIO: cambiar de sesión no debe heredar configuración del anterior ni filtrar datos entre operarios.
- Al limpiar datos locales desde Ajustes, pedir confirmación explícita e informar qué se pierde (etiquetas pendientes, caché).

## Formato del Informe Final (obligatorio)
- 🔴 **Errores Críticos / Bloqueantes** (con archivo:línea)
- 🟡 **Advertencias y Deuda Técnica**
- 🟢 **Puntos Fuertes y Estado General**
- 🚪 **Puertas traseras comprobadas** (tabla: escenario → resultado)
- 📋 **Plan de Acción Recomendado**

## Reglas de oro
- TODO en castellano.
- Corregir sobre la marcha lo que sea razonable; lo que requiera decisión de producto se registra como `D-XX` en `docs/SPECS.md` antes de tocar comportamiento.
- Nunca declarar "OK" sin haberlo compilado/testeado (`BUILD SUCCESSFUL` real).
- Si un hallazgo afecta al backend (`backend/main.py`), NO desplegarlo aquí: anotarlo en el informe para su agente correspondiente.
