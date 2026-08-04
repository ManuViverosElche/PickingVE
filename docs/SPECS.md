# Especificaciones del Sistema - PickingVE

## 1. Visión General
El objetivo de **PickingVE** es dotar a los operarios de vivero/campo de una herramienta Android ultrarrápida, precisa y con funcionamiento autónomo sin cobertura, para preparar pedidos (picking), verificar referencias, capturar medidas y enviar partes de trabajo en CSV directamente a Telegram y BigQuery.

---

## 2. Requerimientos Funcionales (RF)

### RF-01: Conexión y Sincronización con BigQuery
- Descarga de catálogo de artículos, clientes y pedidos activos hacia la base de datos local (Room DB).
- Subida de registros de picking acumulados cuando la conexión a Internet esté disponible.

### RF-02: Escaneo EAN y OCR de Etiquetas
- **Lector EAN**: Escaneo con visor de cámara en tiempo real.
- **Debounce / Anti-Repetición**: Retardo temporal (configurable, p. ej. 2000 ms) tras leer un código de barras para evitar lecturas sucesivas no deseadas sobre la misma maceta.
- **OCR de Respaldo**: En caso de etiquetas dañadas o sin EAN, captura de texto (nombre, proveedor, pasaporte fitosanitario) mediante OCR (Google ML Kit Text Recognition) para búsqueda difusa en la base de datos.
- **Confirmación con Ventana Emergente**: Al detectar un artículo, mostrar un diálogo flotante con la información del producto (Ref, Nombre, Formato, Litraje, Cantidad por Partida) para que el operario confirme antes de agregar al picking.

### RF-03: Asignación y Sustitución de Referencias
- Asignación de la lectura a una línea de pedido específica.
- Si el artículo leído no coincide exactamente con la referencia solicitada en el pedido, permitir la **Sustitución de Referencia** registrando el cambio, la causa y la equivalencia aprobada.

### RF-04: Atributos Especiales de Planta/Maceta
- Soporte para captura de:
  - **Litraje** (L)
  - **Medida** (cm / m)
  - **Calibre** (mm / cm)
- Validación de campos obligatorios en función de la categoría del producto.

### RF-05: Sumatorio por Partida
- Las lecturas no son necesariamente unitarias.
- Cada etiqueta leída representa un lote/partida (ej. lote de 10, 25 o 50 unidades).
- Permite la edición o confirmación de la cantidad de la partida antes del sumatorio final por línea.

### RF-06: Exportación Excel (.xlsx) vía Telegram
- Generación de un fichero **Excel real (.xlsx)** (no CSV) que replica exactamente la estructura del fichero de referencia `picking_260833_I.xlsx` (ver `Documentacion/`), de modo que la macro de validación del cliente lo lea sin cambios.
- Estructura:
  - **Fila 1 (metadatos)**: `ID punteo: <uuid>` | `Matrícula de camion: ` | `Matrícula de remolque: ` | `Finca: ` | `Zona: ` | `Peso de la carga: `
  - **Fila 2 (cabeceras)**: `Correo empleado` | `Número de pedido` | `EAN Variante` | `Cantidad` | `Hora y fecha` | `Lote` | `Variedad`
  - **Filas 3+ (datos)**: una fila por etiqueta/partida. `Hora y fecha` se escribe como número de serie de Excel con formato `m/d/yy h:mm` (numFmt 22), igual que el original.
- Nombre de archivo obligatorio: `picking_<numero_pedido>.<numero_de_picking>_I|F.xlsx`
  - `I` = picking inicial (al llegar la planta), `F` = picking final (comprobación de sobrante).
- Envío automático del fichero adjunto mediante Telegram Bot API (`sendDocument`) al completar o pausar el picking.

---

## 3. Requerimientos No Funcionales (RNF)

- **RNF-01: Performance Offline-First**: Tiempo de respuesta en escaneo e inserción local < 100ms.
- **RNF-02: Usabilidad en Campo**: Interfaz con alto contraste, botones amplios aptos para guantes de trabajo, retroalimentación táctil (vibración) y sonora (beep al escanear).
- **RNF-03: Tolerancia a Fallos**: Si la app se cierra inesperadamente, el borrador de picking debe conservarse intacto.
- **RNF-04: Gestión de Batería**: Optimización de consumo de cámara y GPS en jornadas de campo largas.

---

## 4. Decisiones de Producto (ronda 05/08)

Decisiones tomadas junto al usuario; quedan registradas para que el comportamiento sea estable:

- **D-01 Líneas visibles**: Solo se muestran las líneas de pedido con `IMPRIMIR_LINEA` activado. En los datos actuales `0 = visible`, `1 = no visible`. El filtro se aplica en el backend (`/api/pedidos`): `COALESCE(l.IMPRIMIR_LINEA, 0) = 0`.
- **D-02 Unidades a acopiar**: La cantidad objetivo por línea es **`UNIDADES_PENDIENTES`**, nunca `UNIDADES`. El campo `unidades` queda fuera del cálculo (solo se conserva en el API).
- **D-03 Orden de líneas**: Las líneas se ordenan por número de línea del pedido (`POSICION_PEDIDO`, columna Room `posicion`), igual que aparecen en las hojas de papel. Antes se ordenaban por `orderLineId` (huella digital = hash, sin orden real).
- **D-04 Ubicación auxiliar**: La `UBICACION_EXTRA` **no se muestra** en las tarjetas de línea: comprobar la planta no es lo mismo que acopiarla, y la ubicación auxiliar no es necesaria para el acopio. La prioridad sí es relevante.
- **D-05 Badge de prioridad**: Solo se muestra badge cuando la línea es **PRIORITARIO**. El caso "PRIORIDAD NORMAL" ya no se pinta (era ruido).
- **D-06 Colores**: Se usan los colores corporativos antes que otros: verde `#025C65` (marca/empleado) y rojo `#962622` (prioritario). Cuando el rojo ya está usado (PRIORITARIO), la advertencia de "marca distinta" pasa a un ámbar `#B26A00` acorde con la paleta. Notas → contenedor terciario, acción logística → contenedor secundario (ambos de la misma familia que el verde).
- **D-07 Empleado por línea**: Campo `EMPLEADO` preparado en la UI junto a la línea (badge verde con persona). **Todavía no operativo**: la tabla `LINEA_PEDIDO` de BigQuery no tiene la columna; se rellenará cuando exista. No se añade a la BD aún.
- **D-08 Back de cámara**: Pulsar atrás (gesto o botón del sistema) o la flecha "Volver" con la cámara abierta **cierra la cámara y vuelve al pedido**, no al listado. Solo desde el pedido (cámara cerrada) se vuelve al listado.
- **D-09 BD local**: `OrderLineEntity` gana `posicion` (INTEGER, nº de línea) y `empleado` (TEXT, reservado). Room v6 con `MIGRATION_5_6`; los datos existentes se conservan.
- **D-10 Marca dentro del pedido**: El chip de marca del header usa fondo verde corporativo (`primary` = `#025C65`) con texto blanco (`onPrimary`).
- **D-11 Carrusel de fechas**: Los chips de día son un carrusel desplazable horizontal (LazyRow) y muestran **solo la fecha** en formato `dd/mm/yyyy` (sin "Hoy"/"Mañana"). Los títulos de sección del listado conservan el día relativo + fecha.
- **D-12 % de carga en el listado**: El porcentaje va en la misma línea que "Acopio X / Y plantas (Z%)". La barra de progreso queda sola, a ancho completo, ahorrando altura.
- **D-13 Acopio sin escaneo (planta sin etiqueta)**: Cada línea pendiente tiene el botón **"Sin etiqueta"** que abre un comprobante: ¿es exactamente esta planta? (sí/no), ¿lleva etiqueta? (sí/no) y cantidad. Al confirmar se crea un registro de picking normal (con `needsLabel` si no lleva etiqueta → aparece en "Etiquetas a sacar") y la línea suma las acopiadas. Las referencias que empiezan por **9** (venta directa, no son nuestras) por defecto marcan "No lleva etiqueta" y muestran el aviso de que puede llevar solo la del vendedor.
- **D-14 Login**: El login es obligatorio al entrar si no hay sesión y la sesión es persistente (SharedPreferences). Al salir (icono Salir) vuelve a pedir login.
- **D-15 Futuro**: Se añadirá más adelante un selector de modo **Picking / Inventario** en la entrada de la app (todavía sin desarrollar).
