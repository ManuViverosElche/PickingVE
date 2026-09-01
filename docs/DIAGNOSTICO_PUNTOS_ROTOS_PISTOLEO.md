# Diagnóstico de Puntos Rotos — Auditoría Estática PickingVE

> **Modo**: Solo lectura (READ-ONLY). No se ha modificado ningún archivo de código.
> **Fecha**: 2026-09-01
> **Alcance**: ViewModels, DAOs Room, scanner, pantallas Compose del módulo de pistoleo/picking.

---

## 1. Auditoría de Estado y ViewModels (UI State & Event Flow)

### 1.1 PickingViewModel — `uiState` con 7 niveles de `combine` anidados

| Campo | Valor |
|---|---|
| **Archivo** | `ui/picking/PickingViewModel.kt` |
| **Líneas** | 209–265 |
| **Severidad** | Media (rendimiento / recomposición) |

**Descripción**: El `uiState` final se construye encadenando 7 llamadas `.combine()` sucesivas. Cada emisión de cualquier flow interno dispara la recomposición completa del árbol `PickingScreen`. Con pedidos de +50 líneas, esto genera recomposiciones frecuentes y costosas.

**Causa raíz**: No hay `distinctUntilChanged()` entre las combinaciones; emisiones duplicadas de flows intermedios propagan recomposiciones innecesarias.

---

### 1.2 `forceEanScanQtyOne` — Variable `var` que se fuga entre escaneos

| Campo | Valor |
|---|---|
| **Archivo** | `ui/picking/PickingViewModel.kt` |
| **Línea** | 348 (decl.), 336 (set), 857 (reset), 916 (dismiss sin reset) |
| **Severidad** | **Alta** (lógica corrupta) |

**Comportamiento erróneo**: `forceEanScanQtyOne` se pone a `true` al escanear EAN de referencia "9". Se resetea en `prepareConfirm()` (línea 857) pero **NO** en `dismissConfirm()` (línea 916). Si el usuario escanea una ref "9" y cancela el diálogo, el siguiente escaneo hereda `isLabel = true` y marca `needsLabel` en el registro sin que el operario lo indicara.

**Causa raíz**: `dismissConfirm()` no incluye `forceEanScanQtyOne = false`. Flag mutable fuera del flujo reactivo con limpieza incompleta.

---

### 1.3 `ultimoEscaneoFueEan` — Flag no reactivo con fuga potencial

| Campo | Valor |
|---|---|
| **Archivo** | `ui/picking/PickingViewModel.kt` |
| **Línea** | 372 (decl.), 333 (set), 859 (reset en prepareConfirm) |
| **Severidad** | Media |

Si el usuario escanea un EAN y la app entra en `resolveProduct()` → `pendingLinePick` (múltiples líneas candidatas), y el usuario navega fuera sin elegir línea ni dismissar, el flag queda a `true`. El campo `scannedEan` del registro puede quedar `null` cuando el escaneo fue realmente por EAN, rompiendo la trazabilidad en BigQuery.

---

### 1.4 FaenaDashboardViewModel — `construirEstado()` sin `flowOn(Dispatchers.Default)`

| Campo | Valor |
|---|---|
| **Archivo** | `ui/logistica/FaenaDashboardViewModel.kt` |
| **Líneas** | 359–532 |
| **Severidad** | Media |

Itera todos los pedidos y líneas en el hilo principal en cada emisión. Falta `.flowOn(Dispatchers.Default)`.

---

### 1.5 InvViewModel — `debounce(150)` en contado local retrasa la UI

| Campo | Valor |
|---|---|
| **Archivo** | `ui/inventario/InvViewModel.kt` |
| **Líneas** | 146, 152 |
| **Severidad** | Baja |

El debounce aplicado al flow de lectura (no al de escritura) añade 150ms de latencia tras cada confirmación, pudiendo superar el RNF-01 (<100ms).

---

## 2. Captura y Procesamiento de Escaneo

### 2.1 ScanDebouncer — Memoria de un solo valor

| Campo | Valor |
|---|---|
| **Archivo** | `domain/usecase/ScanDebouncer.kt` |
| **Líneas** | 24–35 |
| **Severidad** | Media |

Solo recuerda `lastKey`. Si se escanea A → B → A en < `debounceMs`, el segundo A se acepta porque `lastKey` es B. Permite duplicados con códigos alternados.

---

### 2.2 BarcodeAnalyzer — `imageProxy` no se cierra si `InputImage.fromMediaImage()` lanza

| Campo | Valor |
|---|---|
| **Archivo** | `scanner/BarcodeAnalyzer.kt` |
| **Líneas** | 25–61 |
| **Severidad** | **Alta** (fuga de memoria / camera freeze) |

```kotlin
// Línea 32: Si esto lanza, imageProxy NUNCA se cierra
val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
scanner.process(image)
    .addOnCompleteListener { imageProxy.close() } // Solo si process() no lanzó
```

**Causa raíz**: Falta `try { ... } finally { imageProxy.close() }` alrededor de todo el procesamiento. Los `imageProxy` no cerrados agotan el buffer de la cámara, causando freeze del visor.

---

### 2.3 CameraScannerScreen — Callback `onCodigoLeido` stale tras recomposición

| Campo | Valor |
|---|---|
| **Archivo** | `ui/picking/CameraScannerScreen.kt` |
| **Líneas** | 186–191, 218 |
| **Severidad** | Media |

`onCodigoLeido` se define como función local y se pasa como `::onCodigoLeido` al `BarcodeAnalyzer` dentro del `factory` del `AndroidView`. El `factory` se ejecuta una sola vez; tras recomposición (cambio de modo EAN/Pasaporte/Ambos), la closure capturada es stale y el filtro `if (modo == CameraModo.PASAPORTE) return` puede no funcionar.

**Causa raíz**: Falta `rememberUpdatedState` para el callback o re-bindar el analyzer en cada recomposición.

---

### 2.4 ProductDao.findByEan — Resultado no determinista

| Campo | Valor |
|---|---|
| **Archivo** | `data/local/dao/ProductDao.kt` |
| **Línea** | 19 |
| **Severidad** | Media |

```sql
SELECT * FROM products WHERE ean = :ean OR reference = :ean LIMIT 1
```

Sin `ORDER BY`, si un producto tiene `reference = "X"` y otro tiene `ean = "X"`, el resultado es indeterminado.

**Fix**: `ORDER BY CASE WHEN ean = :ean THEN 0 ELSE 1 END LIMIT 1`.

---

### 2.5 `onBarcodeScanned` — Lectura de `uiState.value` dentro de coroutine asíncrona

| Campo | Valor |
|---|---|
| **Archivo** | `ui/picking/PickingViewModel.kt` |
| **Líneas** | 327–346 |
| **Severidad** | Media |

`uiState.value` (línea 338) se lee para decidir el modo activo. Si el usuario cambia de modo mientras se procesa el escaneo, la decisión se toma con datos obsoletos, pudiendo enrutar un acopio normal a `unpickByScanProduct()`.

---

## 3. Sincronización Offline-First y Persistencia Room

### 3.1 PickingDao.observeCompensacionesPendientes — Sin verificación de vigencia de línea

| Campo | Valor |
|---|---|
| **Archivo** | `data/local/dao/PickingDao.kt` |
| **Líneas** | 89–96 |
| **Severidad** | Media |

Las compensaciones se calculan por `orderLineId` del registro borrado, sin verificar si la línea sigue vigente o si el registro fue re-creado con otra línea.

---

### 3.2 PickingDao.findMatchingRecord — Sin distinguir `pickingNumber`

| Campo | Valor |
|---|---|
| **Archivo** | `data/local/dao/PickingDao.kt` |
| **Líneas** | 107–132 |
| **Severidad** | **Alta** |

Busca registro para fusionar por `orderId + orderLineId + ean/productId + measure + caliber`, pero NO filtra por `pickingNumber`. Tras enviar un parte, si se re-escanea la misma planta, se incrementa el registro antiguo (ya reportado), inflando el `batchQty` del siguiente parte.

---

### 3.3 OrderDao.observeOrdersWithTotals — `totalPicked` ignora compensaciones locales

| Campo | Valor |
|---|---|
| **Archivo** | `data/local/dao/OrderDao.kt` |
| **Líneas** | 54–71 |
| **Severidad** | Media |

`COALESCE(SUM(MAX(l.pickedQty, l.acopiadoServidor)), 0)` no descuenta compensaciones pendientes. La tarjeta del pedido muestra % de carga superior al real tras desacopios.

---

### 3.4 PickingRepository.syncEncargados — `clear()` + `upsert()` no atómico

| Campo | Valor |
|---|---|
| **Archivo** | `data/repository/PickingRepository.kt` |
| **Líneas** | 156–160 |
| **Severidad** | Baja |

Si el proceso muere entre `clear()` y `upsert()`, la tabla queda vacía. Falta `@Transaction`.

---

### 3.5 InvViewModel — Flash de datos antiguos al cambiar de finca

| Campo | Valor |
|---|---|
| **Archivo** | `ui/inventario/InvViewModel.kt` |
| **Línea** | 251 (`seleccionarFinca`) |
| **Severidad** | Media |

`seleccionarFinca()` no resetea `servidor.value = emptyMap()` antes de la sync. Los datos de la finca anterior se muestran brevemente.

---

## 4. Resumen de Hallazgos

| # | Severidad | Hallazgo | Archivo:Función |
|---|---|---|---|
| 2.2 | **Alta** | `imageProxy` no se cierra en fallo | `BarcodeAnalyzer.kt:analyze()` |
| 3.2 | **Alta** | `findMatchingRecord` sin `pickingNumber` | `PickingDao.kt:107` |
| 1.2 | **Alta** | `forceEanScanQtyOne` leak en dismiss | `PickingViewModel.kt:916` |
| 2.3 | Media | Callback stale en cámara | `CameraScannerScreen.kt:218` |
| 2.4 | Media | `findByEan` sin ORDER BY | `ProductDao.kt:19` |
| 1.1 | Media | 7 niveles de combine anidados | `PickingViewModel.kt:209` |
| 1.3 | Media | `ultimoEscaneoFueEan` leak | `PickingViewModel.kt:372` |
| 1.4 | Media | `construirEstado()` en hilo principal | `FaenaDashboardViewModel.kt:359` |
| 2.1 | Media | ScanDebouncer un solo valor | `ScanDebouncer.kt:24` |
| 2.5 | Media | `uiState.value` stale en coroutine | `PickingViewModel.kt:338` |
| 3.1 | Media | Compensaciones sin vigencia | `PickingDao.kt:89` |
| 3.3 | Media | `totalPicked` ignora compensaciones | `OrderDao.kt:54` |
| 3.5 | Media | Flash datos antiguos en inventario | `InvViewModel.kt:251` |
| 3.4 | Baja | sync encargados no atómica | `PickingRepository.kt:156` |
| 1.5 | Baja | debounce(150) en lectura | `InvViewModel.kt:146` |

---

## 5. Recomendaciones Prioritarias

1. **ImageProxy leak (2.2)**: Envolver todo el `analyze()` en `try { ... } finally { imageProxy.close() }`.
2. **forceEanScanQtyOne leak (1.2)**: Añadir `forceEanScanQtyOne = false` en `dismissConfirm()`.
3. **Callback stale cámara (2.3)**: Usar `rememberUpdatedState` para el callback o re-bindar el analyzer en cada recomposición.
4. **ProductDao ambigüedad (2.4)**: Añadir `ORDER BY CASE WHEN ean = :ean THEN 0 ELSE 1 END`.
5. **Transacción sync encargados (3.4)**: Crear método `@Transaction` en el DAO que haga `clear()` + `upsert()` atómicamente.
6. **FlowOn para FaenaDashboard (1.4)**: Añadir `.flowOn(Dispatchers.Default)` al pipeline de `uiState`.
