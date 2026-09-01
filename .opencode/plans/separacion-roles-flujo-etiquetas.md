# Plan de Ejecución: Separación de Roles y Flujo de Etiquetas (D-274 a D-277)

**Fecha**: 01/09/2026  
**Decisiones de arquitectura**: D-274, D-275, D-276, D-277  
**Objetivo**: Separar estrictamente los flujos de operario de acopio y encargado de picking, corregir el flujo de etiquetas, y mejorar la UX del modal de motivos.

---

## Módulo 1: Backend - Separación de contadores (D-274)

### Archivos afectados
- `backend/main.py`
- `backend/informe_datos.py`
- `backend/web/logistica/js/components/faena/repartoFaena.js`

### Cambios necesarios

#### 1.1. Endpoint `/api/pedidos` y `/api/manager/orders`
- **Objetivo**: Devolver `acopiadoOperario` separado de `acopiado` (verificación).
- **SQL actual**: La subconsulta `pr` agrupa por `(order_id, order_line_id)` y calcula `ACOPIADO = SUM(IF(es_encargado, uds, 0))`.
- **SQL nueva**: 
  ```sql
  ACOPIADO_OPERARIO = SUM(IF(es_operario AND picking_type = 'I', uds, 0)),
  ACOPIADO = SUM(IF(es_encargado AND picking_type = 'F', uds, 0))
  ```
- **Respuesta JSON**: Añadir campo `acopiadoOperario` por línea.

#### 1.2. Endpoint `/api/manager/reparto`
- **Objetivo**: Mostrar `acopiadoOperario` y `acopiado` (verificación) por separado en la tabla de reparto.
- **Cambios**: La respuesta JSON debe incluir ambos contadores por línea.

#### 1.3. Panel web `repartoFaena.js`
- **Objetivo**: Mostrar dos columnas separadas: "Acopiado Op." (naranja) y "Verificado" (verde).
- **Cambios**: 
  - Columna "Acopiado Op.": muestra `acopiadoOperario` (color naranja, informativo).
  - Columna "Verificado": muestra `acopiado` (color verde, conteo real de verificación).
  - Barra de progreso: usa `acopiado` (verificación) para calcular el porcentaje.

#### 1.4. Informe de punteo (`informe_datos.py`)
- **Objetivo**: El informe de punteo debe mostrar el conteo de verificación del encargado, no el acopio del operario.
- **Cambios**: La columna "Acopiado" en el informe usa `acopiado` (verificación), no `acopiadoOperario`.

---

## Módulo 2: App Android - Room DB y UI de contadores separados (D-274)

### Archivos afectados
- `app/src/main/java/com/vivero/pickingve/data/local/entities/OrderLineEntity.kt`
- `app/src/main/java/com/vivero/pickingve/data/local/AppDatabase.kt`
- `app/src/main/java/com/vivero/pickingve/data/remote/ApiModels.kt`
- `app/src/main/java/com/vivero/pickingve/data/repository/PickingRepository.kt`
- `app/src/main/java/com/vivero/pickingve/ui/picking/PickingScreen.kt`
- `app/src/main/java/com/vivero/pickingve/ui/logistica/FaenaDashboardViewModel.kt`

### Cambios necesarios

#### 2.1. Room DB migración (v27)
- **Objetivo**: Añadir campo `acopiadoOperario` a `OrderLineEntity`.
- **Migración**: 
  ```kotlin
  db.execSQL("ALTER TABLE order_lines ADD COLUMN acopiadoOperario INTEGER NOT NULL DEFAULT 0")
  ```
- **Entity**: 
  ```kotlin
  val acopiadoOperario: Int = 0 // Acopio físico del operario de campo
  ```

#### 2.2. API Models
- **Objetivo**: `ApiOrderLine` debe incluir `acopiadoOperario`.
- **Cambios**: 
  ```kotlin
  @SerialName("acopiado_operario") val acopiadoOperario: Int = 0
  ```

#### 2.3. PickingRepository.syncFromApi()
- **Objetivo**: Mapear `acopiadoOperario` desde la API a la entidad local.
- **Cambios**: 
  ```kotlin
  acopiadoOperario = l.acopiadoOperario
  ```

#### 2.4. UI de PickingScreen.kt
- **Objetivo**: Mostrar dos contadores separados en la tarjeta de línea.
- **Cambios**:
  - Contador "Acopiado por operario": muestra `line.acopiadoOperario` (color naranja, informativo).
  - Contador "Verificado": muestra `line.pickedQty` (color verde, conteo real de verificación).
  - Barra de progreso: usa `line.pickedQty` para calcular el porcentaje.

#### 2.5. Modal de operario asignado (`OperarioAsignadoDialog`)
- **Objetivo**: Mostrar ambos contadores sin confundirlos.
- **Cambios**:
  - Texto: "El operario ${line.operarioNombre} ha acopiado ${line.acopiadoOperario} unidades (informativo)."
  - Texto: "Verificación del encargado: ${line.pickedQty} / ${line.requestedQty} unidades."

#### 2.6. FaenaDashboardViewModel.kt
- **Objetivo**: El dashboard de faena debe mostrar el conteo de verificación del encargado, no el acopio del operario.
- **Cambios**: 
  - `totalRecogidas` usa `line.pickedQty` (verificación), no `line.acopiadoServidor`.
  - La barra de progreso usa `line.pickedQty`.

---

## Módulo 3: App Android - Flujo de etiquetas corregido (D-275)

### Archivos afectados
- `app/src/main/java/com/vivero/pickingve/data/repository/PickingRepository.kt`
- `app/src/main/java/com/vivero/pickingve/ui/picking/PickingViewModel.kt`
- `app/src/main/java/com/vivero/pickingve/ui/picking/ConfirmPickingDialog.kt`

### Cambios necesarios

#### 3.1. Eliminar el registro espejo `-1`
- **Objetivo**: Cuando `needsLabel = true`, la planta DEBE contar como acopiada.
- **Código actual** (líneas 1000-1011 de `PickingRepository.kt`):
  ```kotlin
  if (needsLabel && labelReason != "CAMBIO_FORMATO" && orderLineId != null) {
      val espejo = record.copy(
          recordId = UUID.randomUUID().toString(),
          batchQty = -1,
          needsLabel = false
      )
      insertPickingRecord(espejo)
      orderDao.addLinePickedQty(orderLineId, -1)
  }
  ```
- **Código nuevo**: Eliminar este bloque completo. La planta se registra normalmente con `batchQty = 1` y `needsLabel = true`, pero el conteo de la línea NO se resta.

#### 3.2. Mensaje de éxito en PickingViewModel
- **Objetivo**: Mostrar dos mensajes separados: acopio + etiqueta solicitada.
- **Código actual**: 
  ```kotlin
  lastMessage.value = "Acopiada x${record.batchQty} · ${product.name}"
  ```
- **Código nuevo**: 
  ```kotlin
  val labelMsg = if (record.needsLabel) " · 🏷️ Etiqueta: ${motivoEtiqueta(record.labelReason)}" else ""
  lastMessage.value = "✅ Acopiada x${record.batchQty} · ${product.name}$labelMsg"
  ```

#### 3.3. CSV de etiquetas pendientes
- **Objetivo**: Listar TODOS los registros con `needsLabel = true`, sin filtrar por `batchQty > 0`.
- **Código actual**: La consulta de etiquetas pendientes filtra por `batchQty > 0`.
- **Código nuevo**: Eliminar el filtro `batchQty > 0` en la consulta de etiquetas pendientes.

#### 3.4. Panel web "Etiquetas a sacar"
- **Objetivo**: Mostrar la lista completa de registros con `needsLabel = true`.
- **Cambios**: El endpoint `/api/manager/etiquetas/dia` debe devolver todos los registros con `needsLabel = true`, sin filtrar por `batchQty`.

---

## Módulo 4: App Android - Modal de motivos scrolleable + nueva opción (D-276, D-277)

### Archivos afectados
- `app/src/main/java/com/vivero/pickingve/ui/picking/ConfirmPickingDialog.kt`

### Cambios necesarios

#### 4.1. Modal scrolleable (D-276)
- **Objetivo**: El modal de motivos debe ser scrolleable para que todas las opciones sean visibles.
- **Código actual**: 
  ```kotlin
  LabelOptionSelector(
      labelOption = labelOption,
      labelFormat = labelFormat,
      litrajes = litrajes,
      mostrarNoEtiqueta = false,
      onOptionChange = { labelOption = it; if (it != 3) labelFormat = "" },
      onFormatChange = { labelFormat = it }
  )
  ```
- **Código nuevo**: 
  ```kotlin
  Column(
      modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 400.dp)
          .verticalScroll(rememberScrollState())
  ) {
      LabelOptionSelector(
          labelOption = labelOption,
          labelFormat = labelFormat,
          litrajes = litrajes,
          mostrarNoEtiqueta = true, // D-277: mostrar opción "No tiene etiqueta"
          onOptionChange = { labelOption = it; if (it != 3) labelFormat = "" },
          onFormatChange = { labelFormat = it }
      )
  }
  ```

#### 4.2. Nueva opción "No tiene etiqueta" (D-277)
- **Objetivo**: Añadir la opción "No tiene etiqueta" al modal de confirmación.
- **Código actual** (línea 249): 
  ```kotlin
  mostrarNoEtiqueta = false
  ```
- **Código nuevo**: 
  ```kotlin
  mostrarNoEtiqueta = true
  ```

#### 4.3. Lógica del motivo "SIN_ETIQUETA"
- **Objetivo**: Cuando el usuario selecciona `labelOption == 1`, el motivo se guarda como `labelReason = "SIN_ETIQUETA"`.
- **Código actual** (líneas 265-270):
  ```kotlin
  when (labelOption) {
      2 -> "MACETA_ROTA"
      3 -> "CAMBIO_FORMATO"
      4 -> "PASAPORTE_MAL_ESTADO"
      else -> ""
  }
  ```
- **Código nuevo**:
  ```kotlin
  when (labelOption) {
      1 -> "SIN_ETIQUETA"
      2 -> "MACETA_ROTA"
      3 -> "CAMBIO_FORMATO"
      4 -> "PASAPORTE_MAL_ESTADO"
      else -> ""
  }
  ```

#### 4.4. CSV de etiquetas: descripción legible
- **Objetivo**: El motivo "SIN_ETIQUETA" se muestra como "No tiene etiqueta" en el CSV.
- **Código actual**: El CSV muestra el código crudo del motivo.
- **Código nuevo**: 
  ```kotlin
  val motivoDesc = when (record.labelReason) {
      "SIN_ETIQUETA" -> "No tiene etiqueta"
      "MACETA_ROTA" -> "Maceta rota"
      "CAMBIO_FORMATO" -> "Cambio de formato a ${record.labelFormat}"
      "PASAPORTE_MAL_ESTADO" -> "Pasaporte en mal estado"
      else -> record.labelReason
  }
  ```

---

## Módulo 5: Panel web - Actualización de contadores y etiquetas (D-274, D-275)

### Archivos afectados
- `backend/web/logistica/js/components/faena/repartoFaena.js`
- `backend/web/logistica/js/components/faena/etiquetasDia.js`

### Cambios necesarios

#### 5.1. Reparto de faena: dos columnas separadas
- **Objetivo**: Mostrar "Acopiado Op." (naranja) y "Verificado" (verde) por separado.
- **Cambios**:
  - Columna "Acopiado Op.": muestra `acopiadoOperario` (color naranja, informativo).
  - Columna "Verificado": muestra `acopiado` (color verde, conteo real de verificación).
  - Barra de progreso: usa `acopiado` (verificación) para calcular el porcentaje.

#### 5.2. Etiquetas a sacar: lista completa
- **Objetivo**: Mostrar todos los registros con `needsLabel = true`, sin filtrar por `batchQty > 0`.
- **Cambios**: La tabla de etiquetas muestra todos los registros con `needsLabel = true`, incluyendo la referencia, litraje, sector, cantidad y motivo.

---

## Orden de ejecución

1. **Módulo 1 (Backend)**: Separación de contadores en endpoints y panel web.
2. **Módulo 2 (App Android)**: Room DB migración y UI de contadores separados.
3. **Módulo 3 (App Android)**: Flujo de etiquetas corregido (eliminar registro espejo -1).
4. **Módulo 4 (App Android)**: Modal de motivos scrolleable + nueva opción "No tiene etiqueta".
5. **Módulo 5 (Panel web)**: Actualización de contadores y etiquetas.

---

## Verificación

### Backend
- [ ] Endpoint `/api/pedidos` devuelve `acopiadoOperario` separado de `acopiado`.
- [ ] Endpoint `/api/manager/orders` devuelve `acopiadoOperario` separado de `acopiado`.
- [ ] Endpoint `/api/manager/reparto` devuelve ambos contadores por línea.
- [ ] Endpoint `/api/manager/etiquetas/dia` devuelve todos los registros con `needsLabel = true`.
- [ ] Informe de punteo usa `acopiado` (verificación), no `acopiadoOperario`.

### App Android
- [ ] Room DB migración v27 añade campo `acopiadoOperario`.
- [ ] UI de PickingScreen muestra dos contadores separados.
- [ ] Modal de operario asignado muestra ambos contadores sin confundirlos.
- [ ] Flujo de etiquetas: la planta físicamente acopiada cuenta (no hay registro espejo -1).
- [ ] CSV de etiquetas lista todos los registros con `needsLabel = true`.
- [ ] Modal de motivos es scrolleable.
- [ ] Modal de motivos incluye opción "No tiene etiqueta".
- [ ] CSV de etiquetas muestra descripción legible del motivo.

### Panel web
- [ ] Reparto de faena muestra dos columnas separadas: "Acopiado Op." y "Verificado".
- [ ] Etiquetas a sacar muestra la lista completa de registros con `needsLabel = true`.

---

## Notas técnicas

- **Room DB versión**: La migración v27 debe ser idempotente (verificar si la columna ya existe antes de añadirla).
- **Compatibilidad hacia atrás**: Los registros antiguos con `pickingType = "I"` y `needsLabel = true` deben seguir mostrándose correctamente en el CSV de etiquetas.
- **Performance**: La consulta de `acopiadoOperario` en el backend debe usar índices en `(order_id, order_line_id, picking_type)` para evitar escaneos completos de la tabla `picking_registros_v`.
- **Testing**: Se debe verificar el flujo completo con un caso práctico:
  1. Jesús (operario de acopio) escanea 10 plantas con motivo "No tiene etiqueta".
  2. La app registra 10 plantas acopiadas (`batchQty = 10`, `needsLabel = true`, `labelReason = "SIN_ETIQUETA"`).
  3. El conteo de acopiadas por operario muestra 10 (informativo).
  4. El CSV de etiquetas lista 10 etiquetas a sacar con motivo "No tiene etiqueta".
  5. Laura (encargada de picking) verifica las 10 plantas escaneándolas.
  6. La app registra 10 plantas verificadas (`batchQty = 10`, `pickingType = "F"`).
  7. El conteo de verificación del encargado muestra 10 / 10 (verde).
  8. No hay sobreacopio (los contadores están separados).
