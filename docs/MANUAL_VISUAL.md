# PickingVE — Manual Visual y Trazabilidad Completa

> **Versión de la app:** 2.2.2 (versionCode 32) · **Fecha:** 2026-08-24 · **Fuente:** `docs/SPECS.md` (D-01…D-167), código en `app/src/main/java/com/vivero/pickingve/`.
>
> Este documento es el **mapa vivo** de la app: qué hace, cómo fluye pantalla a pantalla, qué decisiones la moldearon (D-XX) y qué fallos tiene hoy, cada uno con su corrección propuesta.

---

## 1. Arquitectura general (offline-first)

```mermaid
flowchart LR
    subgraph UI["UI Jetpack Compose"]
        LOGIN[Login]
        MODE[Selector de modo]
        ORDERS[Pedidos]
        FAENA[Mi faena]
        GEST[Gestionar faena]
        PICK[Picking / Chequeo]
        CAM[Cámara scanner]
        CHAT[Chat pedido/línea]
        SETT[Ajustes]
        ADMIN[Admin usuarios/fincas]
    end

    subgraph VM["ViewModels (viewModelScope)"]
        LVM[LoginViewModel]
        OVM[OrderListViewModel]
        FVM[FaenaDashboardViewModel]
        GVM[GestionFaenaViewModel]
        PVC[PickingViewModel]
    end

    subgraph DATA["Capa de datos"]
        REPO[PickingRepository]
        SREPO[SettingsRepository]
        ROOM[(Room DB<br/>pickingve.db v21)]
        API[Ktor → Cloud Run<br/>/api/*]
        TG[TelegramReporter<br/>3 bots]
    end

    BG[Worker 15 min<br/>PickingSyncWorker] --> REPO
    FCM[FCM Push<br/>camión / cambios / chat] --> NOTIF[Notificaciones]

    UI --> VM --> REPO --> ROOM
    REPO --> API
    REPO --> TG
```

**Regla de oro:** la UI **solo lee Room**. La red (BigQuery vía backend Cloud Run) es asíncrona y nunca bloquea: si falla, queda pendiente y se reintenta.

---

## 2. Mapa de navegación por rol

```mermaid
flowchart TD
    START([Arranque app]) --> SES{¿Sesión activa?}
    SES -- no --> LOGIN[Pantalla Login]
    LOGIN -- OK --> ROL{Tipo de sesión}
    SES -- sí --> ROL

    ROL -- "OPERARIO" --> FAENA[Mi faena<br/>D-158/D-160/D-161]
    ROL -- "ENCARGADO modo AMBAS" --> MODE[Selector de modo<br/>Picking / Inventario / Logística]
    ROL -- "INVENTARIO" --> INV[Inventario]
    ROL -- "resto" --> ORDERS[Lista de pedidos]

    MODE --> ORDERS & INV & FAENA

    ORDERS -- "tocar pedido" --> PICK[Picking / Chequeo]
    ORDERS -- icono checklist --> FAENA
    FAENA -- "tocar línea" --> PICK
    PICK -- atrás --> BACK{¿Operario?}
    BACK -- sí --> FAENA
    BACK -- no --> ORDERS

    ORDERS -- engranaje --> SETT[Ajustes]
    SETT -- SUPERUSUARIO --> USERS[Usuarios] & FINCAS[Fincas]

    PUSH[Push FCM: pedido modificado] -.solo encargados+.-> PICK
```

- **Operarios (D-158/D-159/D-167):** entran directo a *Mi faena*, no ven pedidos ni partes; no navegan por push.
- **Modo ayuda (D-160):** desde Mi faena se puede ver temporalmente la faena de un compañero con banner y trazabilidad del ayudante.

---

## 3. Flujo maestro: sincronización offline-first

```mermaid
sequenceDiagram
    participant U as Encargado
    participant App as App (Room)
    participant W as Worker 15 min
    participant BQ as Backend Cloud Run
    participant B as BigQuery EU

    U->>App: Pulsa "Sincronizar" (o automático)
    App->>BQ: GET /pedidos (desde/hasta/estados/fincas/modificadoDesde)
    BQ->>B: Consulta pickingve/GestionComercialVE
    B-->>BQ: Pedidos + líneas activas
    BQ-->>App: JSON
    Note over App: Sync completa si: primer sync,<br/>cambio de encargado (D-76)<br/>o >12 h. Delta si no (watermark).
    App->>App: MERGE en Room<br/>(lneas desaparecidas → vigente=0, D-79)
    App->>BQ: GET /catalogo si versión cambió
    loop Cada 15 min (D-108)
        W->>App: Sube registros pendientes (POST /picking/upload)
        W->>BQ: POST /notificar (detecta pedidos modificados, D-152)
        W->>App: Reintenta cierres de línea pendientes (D-161)
    end
```

---

## 4. Flujo secuencial de trabajo diario (encargado)

```mermaid
flowchart TD
    A[1. Login encargado] --> B[2. Sincronizar pedidos<br/>chips fincas + días persistentes D-131]
    B --> C[3. Buscar pedido<br/>buscador D-147]
    C --> D[4. Abrir pedido → Chequeo]
    D --> E{Acopiar planta}
    E -- Pistolear EAN --> F[Confirmar asignación D-112<br/>sustitución / ampliación / aviso sector D-104]
    E -- Sin EAN: cámara pasaporte --> G[OCR C: ref+litraje+sector D-99/D-138/D-141<br/>OcrMatchDialog si faltan datos]
    E -- Manual 3 vías D-124/D-135..137 --> H[Verificar referencia → litraje/sector → cantidad]
    F & G & H --> I[Línea pintada:<br/>verde completa / gris parcial / ámbar marcada D-103<br/>secciones Pendientes → Completas D-150]
    I --> J{¿Falta stock?}
    J -- sí --> K[Cerrar línea con motivo D-161<br/>CierreLineaDialog → notifica oficina]
    J -- no --> I
    I --> L[5. Etiquetas: cola pendientes<br/>editar/restar/eliminar D-86, pasaporte mal estado D-106<br/>envío bot etiquetas D-107]
    L --> M[6. Llegada camión:<br/>muelle + OCR matrícula + fotos D-88<br/>push urgente D-163, compartir WhatsApp]
    M --> N[7. Marcar cargado / sobrante]
    N --> O[8. Cerrar parte → BigQuery picking_partes D-153<br/>sin xlsx por Telegram]
    O --> P[9. Logout]
```

### 4.1 Detalle: pistoleado EAN

```mermaid
sequenceDiagram
    participant O as Operario/Cámara
    participant PV as PickingViewModel
    participant DB as Room
    participant BQ as Backend/BigQuery

    O->>PV: EAN escaneado (debounce 2 s, D-scan)
    PV->>DB: Busca producto en catálogo local
    alt Coincide con 1 línea pendiente
        PV->>PV: SectorWarningDialog si litraje/sector difiere (D-104)
    else Coincide con varias / sustitución
        PV->>PV: Diálogo elección de línea (D-112)
    else No está en el pedido
        PV->>PV: "AMPLIACIÓN — Registrar ampliación"
    end
    PV->>DB: INSERT registro picking (offline)
    PV->>DB: pickedQty += N, refreshOrderStatus
    PV--)BQ: Upload asíncrono (manual / worker 15 min / al cerrar parte)
```

### 4.2 Detalle: desacopio

```mermaid
flowchart TD
    A[Botón Desacoplar] --> B[Diálogo confirmación<br/>cantidad editable solo venta directa '9...' D-128/D-119]
    B -->|Escaneo| C[Abre cámara directamente D-126<br/>modal UnpickScanConfirmDialog D-139]
    B -->|Desacoplar N| D[Descuenta del registro más reciente<br/>borra registro al llegar a 0 D-132]
    C --> D
    D --> E[Fix '1/29': 5 capas de reconciliación<br/>con acopiadoServidor D-132]
```

---

## 5. Flujo secuencial operario (Mi faena)

```mermaid
sequenceDiagram
    participant Op as Operario
    participant FD as FaenaDashboardViewModel
    participant DB as Room

    Op->>FD: Abre Mi faena (login directo D-158)
    FD->>DB: Líneas no CARGADAS, fecha >= hoy, agrupadas día→finca(RELEVADA)→sector
    FD-->>Op: Tarjetas con nº viajes estimados según maquinaria<br/>prioritarias arriba en rojo
    Op->>Op: Toca tarjeta → acopio manual (flujo principal, D-161)<br/>indica cuántas recogió
    Op->>FD: Botón "Cerrar línea" si falta stock (motivo prediseñado)
    FD--)BQ: POST /logistica/cierre-linea (avisa oficina, D-162)
    Op->>FD: Modo ayuda: ver faena de compañero (D-160)
    BQ--)Op: Push discrepancia "declaraste X, puntuaron Y" (canal urgente D-163)
```

---

## 6. Chat y notificaciones (oficina ↔ campo)

```mermaid
flowchart TD
    subgraph Oficina
        BOT1[Bot mensajes Telegram D-84/D-93<br/>menús pedido→línea, #pedido, responder D-94]
        PANEL[Panel manager web<br/>pedidos, etiquetas D-154, líneas gestionadas D-155,<br/>informe Punteo PDF D-152b, reparto]
    end
    subgraph Campo["Campo (app)"]
        CHATP[Chat pedido: burbuja gris/verde/amarilla D-96]
        CHATL[Chat por línea con datos artículo D-97]
        CHATO[Chat accesible desde lista pedidos D-109]
        FOTO[Foto → vista previa → texto → enviar D-98]
    end
    BOT1 <-->|comentarios BigQuery polling 10 s + recarga 2 s D-85| CHATP & CHATL & CHATO
    FOTO --> CHATP & CHATL
    PANEL -->|FCM pedido modificado D-152| PUSH[Notificación → abre pedido<br/>solo encargados D-167]
    CAMION[Registro matrícula] -->|FCM tipo camion_llegado canal urgente D-163| PUSH
```

Reglas clave: sin eco propio ni a SUPERUSUARIOs (D-116), filtro de fecha (solo pedidos de hoy en adelante, D-101), lías sin pendiente ocultas (D-102).

---

## 7. Trazabilidad funcional ↔ decisiones ↔ código

| Funcionalidad | Decisión(es) | Código principal |
|---|---|---|
| Líneas visibles / orden | D-01, D-03, D-102 | `backend/main.py`, `OrderDao` |
| Badge prioridad / ELIMINADA DEL PEDIDO | D-05, D-77, D-111 | `PickingScreen.kt` |
| Sync delta/completa, cambio encargado | D-76 | `PickingRepository.syncFromApi` |
| Push pedidos modificados | D-81, D-152 | `PickingSyncWorker`, `PickingFirebaseMessagingService` |
| Chat pedido/línea + badges lectura | D-82, D-85, D-96, D-97, D-100, D-115 | `ChatDialog.kt`, `ChatEstadoDao` |
| 3 bots Telegram separados | D-84 | `TelegramReporter.kt`, `backend/main.py` |
| OCR pasaporte fitosanitario | D-99, D-114, D-138, D-141, D-143, D-145 | `ParsePlantPassportUseCase.kt`, `OcrReader.kt`, `CameraScannerScreen.kt` |
| Acopio manual 3 vías | D-105, D-113, D-118, D-124, D-129, D-135–D-137 | `PickingScreen.kt` (diálogos manuales) |
| Venta directa ("9...") prellenado | D-128 | `PickingViewModel.confirmPicking` |
| Desacopio con confirmación + fix 1/29 | D-119, D-122, D-126, D-132, D-139 | `PickingScreen.kt`, `PickingRepository` |
| Cámara a pantalla completa + anti-OOM | D-125, D-130 | `CameraScannerScreen.kt` (decodeSampled) |
| Cierre de línea con motivo | D-161, D-162 | `CierreLineaDialog.kt`, `OrderDao.setLineCierre` |
| Llegada camión (muelle, OCR matrículas, fotos) | D-88, D-163 | `PickingScreen.kt`, `PickingApiClient.guardarMatricula` |
| Mi faena + reparto + ayuda | D-158, D-159, D-160 | `FaenaDashboardScreen/ViewModel`, `GestionFaena*` |
| Alerta citrícos inmovilizados | D-148 (pendiente conector) | — |
| Informe Punteo PDF GlobalGAP | D-152b enmendado | `backend/punteo_pdf.py` |
| Panel manager (etiquetas, gestionadas, enviados) | D-154, D-155, D-157 | `backend/web/manager` |
| Limpieza datos locales | D-110, D-121 | `SettingsViewModel.limpiarDatosLocales` |
| Seguridad sesión/secrets | — | `EncryptedSharedPreferences`, `BuildConfig` desde `secrets.properties` |

Versiones publicadas: 1.7.3 → 1.7.12 (D-123, D-127, D-140, D-142, D-144, D-146, D-149, D-151, D-153) → 2.1.0/2.1.1 (D-165, D-167) → **2.2.2 actual**.

---

## 8. Auditoría técnica (2026-08-24)

Compilación: ✅ `assembleDebug` OK · Tests unitarios: ✅ 8/8 (`XlsxReportGeneratorTest`, `MatchOcrUseCaseTest`, `ScanDebouncerTest`) · Lint: ❌ **1 error, 73 warnings**.

### 🔴 Errores críticos / bloqueantes

| # | Hallazgo | Ubicación | Corrección |
|---|---|---|---|
| R1 | **Lint falla el build de release-check**: uso de API experimental CameraX sin anotación (`UnsafeOptInUsageError`) | `ui/picking/CameraScannerScreen.kt:216` | Añadir `@androidx.camera.core.ExperimentalGetImage` o `@OptIn(ExperimentalGetImage::class)` en la función que llama `setAnalyzer`. Es una línea; desbloquea `lintDebug` (hoy aborta). |
| R2 | **Secreto compartido embebido en APK**: `API_KEY` viaja en `BuildConfig`; cualquier persona con el APK puede extraerla y llamar al backend (única barrera `X-API-Key`). | `app/build.gradle.kts:45-49`, `Constants.kt:20` | Riesgo aceptable para flota interna controlada, pero conviene: (a) limitar en backend por IP/rango o por token rotable por dispositivo, o (b) auth mutua TLS / token efímero emitido por login. No es fix de una línea: decidir con el usuario antes de tocar. |

### 🟡 Advertencias y deuda técnica

| # | Hallazgo | Ubicación | Corrección |
|---|---|---|---|
| Y1 | Alcances no estructurados (`CoroutineScope(...).launch`) sin cancelación | `CameraScannerScreen.kt:335` (runOcr), `fcm/PickingFirebaseMessagingService.kt:27` | En el servicio es patrón aceptable (ciclo corto); en `runOcr` conviene pasar el `scope` de la pantalla o usar `rememberCoroutineScope` para cancelar si se cierra la cámara a mitad del OCR. |
| Y2 | `targetSdk = 34` (hay 35/36); compatibilidad forzada | `app/build.gradle.kts:26` | Subir a 35 probando en tableta real (permiso POST_NOTIFICATIONS ya está gestionado). |
| Y3 | Dependencias antiguas (AGP 8.4.1, Compose BOM 2024.05.00, Room 2.6.1, Camerax 1.3.3, security-crypto alpha06…) | `gradle/libs.versions.toml` | Actualización planificada por bloques (Room y security-crypto primero: pasar `security-crypto` de alpha06 a estable 1.1.0). No hacer todas a la vez. |
| Y4 | Orientación fijada portrait + falta `dataExtractionRules` (deprecado en Android 12+) | `AndroidManifest.xml:17,26` | Para tablets de campo el portrait es intencional (documentarlo o suprimir warning); añadir `dataExtractionRules` manteniendo `allowBackup=false`. |
| Y5 | Checks SDK obsoletos y recursos sin usar | `PickingFirebaseMessagingService.kt:83`, `mipmap-anydpi-v26`, `logo_viveros.png` | Limpiar: minSdk=26 hace innecesario el check; fusionar recursos y borrar PNG no usado. |
| Y6 | `mutableStateOf(0/Int)` en vez de `mutableIntStateOf` (3 sitios, boxing innecesario) | `ConfirmPickingDialog.kt:64`, `PickingScreen.kt:172,2312` | Cambiar a `mutableIntStateOf`. Trivial. |
| Y7 | `LazyColumn` sin `key` en selector de motivos de cierre | `CierreLineaDialog.kt:65` | Lista estática corta: riesgo nulo; añadir `key = { it }` por consistencia. |
| Y8 | `Modifier` no es primer parámetro opcional en composable | `PickingScreen.kt:747` | Reordenar firma. Cosmético. |
| Y9 | Dependencias fuera del catálogo TOML | `app/build.gradle.kts:155,160,161` | Moverlas a `libs.versions.toml`. |
| Y10 | Release sin minify/R8 (`isMinifyEnabled = false`) | `app/build.gradle.kts:64` | Se distribuye APK debug; si algún día se publica release, activar R8 y revisar reglas proguard de kotlinx-serialization/Ktor. |
| Y11 | Cobertura de tests baja: solo 8 tests unitarios, nada de ViewModels/repositorio | `app/src/test` | Añadir tests de `MatchOcrUseCase` extendidos (litrajes palabra, geometría D-141) y de parseo de pasaporte con casos reales de campo (D-145). |

### 🟢 Puntos fuertes y estado general

- **Offline-first bien implementado:** la UI solo lee Room; DAOs 100% `suspend`/`Flow` (Room gestiona su hilo); red con Ktor CIO en sus propios hilos; OCR decodifica en `Dispatchers.Default` con `inSampleSize` (fix OOM D-130).
- **Ciclo de vida correcto:** todos los estados usan `collectAsStateWithLifecycle()` (9 pantallas); executor de cámara liberado en `onDispose` (`CameraScannerScreen.kt:72-76`); Flows expuestos con `stateIn(viewModelScope, WhileSubscribed(5000))`.
- **Seguridad razonable:** sesión y settings en `EncryptedSharedPreferences` (AES256-SIV/GCM) con fallback controlado (`SettingsRepository.kt:36-44`, `PickingRepository.kt:77-85`); secretos fuera del código (`secrets.properties` gitignored → BuildConfig); manifest limpio: sin cleartext, `FileProvider` y servicio FCM no exportados, permisos mínimos.
- **Manejo de errores robusto:** ~65 puntos try/catch/runCatching; helper central `launchCatching` en `PickingViewModel` ("nunca crashea"); errores de red convertidos en mensaje de UI.
- **Listas eficientes:** todas las listas grandes llevan `key = { ... }` estable.
- **Estado:** app compilando, tests verdes, 172 decisiones documentadas en SPECS.md. El único bloqueante formal es el error de lint R1.

### 📋 Plan de acción recomendado (por orden)

1. **R1 (5 min):** anotar `@OptIn(ExperimentalGetImage::class)` en `BarcodeAnalyzer`/uso en `CameraScannerScreen.kt:216` → `lintDebug` vuelve a verde.
2. **Y6 + Y7 + Y8 + Y9 (30 min):** limpieza mecánica de lint warnings triviales.
3. **Y4 (15 min):** añadir `dataExtractionRules.xml` (mantener backup desactivado).
4. **Y1 (30 min):** estructurar el scope de `runOcr`.
5. **Y11:** tests del parser de pasaporte con casos reales (protege el flujo más frágil en campo).
6. **Y3/Y2:** ventanas de upgrade planificadas (Room → 2.8.x requiere validar migraciones; targetSdk 35 con prueba en tablet).
7. **R2:** decisión de producto sobre endurecer auth del backend (requiere tu OK, D-117).

---

## 9. Fallos conocidos históricos y cómo se resolvieron (lecciones)

| Síntoma en campo | Causa raíz | Fix aplicado | Ref |
|---|---|---|---|
| Caída al capturar etiqueta sin EAN (12 MP) | Bitmap completo en memoria | Captura a archivo + `inSampleSize` ≤1600 px | D-130 |
| "1/29" imposible de desacopiar | Registro borrado localmente aún no subido; snapshot remoto decía 1 | Reconciliación en 5 capas con `acopiadoServidor` | D-132 |
| Chat de pedido siempre vacío | `chatLinea = ""` vs `null` en filtro backend | Normalización `"" → null` | D-115 |
| Pedidos no bajaban al cambiar encargado | Watermark global de sync delta | Flag `last_sync_encargado` → sync completa | D-76 |
| OCR "no encontrado en el catálogo" | Comparación exacta con guiones/espacios; litraje-palabra no detectado | `normalizarRef`, parser geométrico por altura de fuente | D-138, D-141 |
| "Varios productos coinciden con C:" tras elegir litraje | Resolución ignoraba la elección del usuario | Fix de resolución en flujo OCR | D-145 |
| Filtro de días se reseteaba a hoy | Sin persistencia | Preferencias serializadas ISO | D-131 |
| Eco de notificaciones al autor/SUPERUSUARIOs | Difusión sin exclusiones | Excluir autor (app) y SUPERUSUARIOs (TG) | D-116 |
| Desplegable sin los 6 litrajes de la referencia | Buscador pre-filtraba por valor elegido | Dropdown simple con TODAS las variantes | D-136 |

---

*Cualquier cambio de comportamiento nuevo debe registrarse como nueva decisión D-XX en `docs/SPECS.md` antes de implementarse.*
