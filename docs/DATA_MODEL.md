# Modelo de Datos - PickingVE

## Entidades Principales (Room DB / SQLite)

### 1. `products` (Catálogo de Artículos)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | String (PK) | Código interno de producto / Referencia |
| `ean` | String (Indexed) | Código de barras EAN-13 |
| `name` | String | Descripción comercial del artículo |
| `liters` | Float? | Litraje por defecto (ej. 3.5 L) |
| `measure` | String? | Medida (ej. "30-40 cm") |
| `caliber` | String? | Calibre (ej. "C14") |
| `default_batch_qty` | Int | Cantidad por partida / maceta por defecto |
| `updated_at` | Long | Timestamp para sincronización incremental |

---

### 2. `orders` (Cabecera de Pedidos)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `order_id` | String (PK) | Número de pedido |
| `customer_name` | String | Nombre del cliente |
| `status` | String | PENDIENTE, EN_PROCESO, COMPLETADO |
| `created_at` | Long | Fecha del pedido |

---

### 3. `order_lines` (Líneas de Pedido Solicitadas)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `line_id` | String (PK) | Identificador de línea |
| `order_id` | String (FK) | ID del pedido |
| `product_id` | String (FK) | Referencia solicitada |
| `requested_qty` | Int | Cantidad total solicitada |
| `picked_qty` | Int | Cantidad servida hasta el momento |

---

### 4. `picking_records` (Registros de Lectura y Captura)
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `record_id` | String (PK) | UUID autogenerado |
| `order_id` | String (FK) | Pedido al que se asigna |
| `line_id` | String? (FK) | Línea de pedido asignada |
| `scanned_ean` | String? | Código EAN leído (si aplica) |
| `ocr_raw_text` | String? | Texto raw de la etiqueta (si fue por OCR) |
| `original_product_id` | String | Referencia original pedida |
| `actual_product_id` | String | Referencia real entregada (sustituida o igual) |
| `liters` | Float? | Litraje capturado o verificado |
| `measure` | String? | Medida capturada |
| `caliber` | String? | Calibre capturado |
| `batch_qty` | Int | Cantidad de esta partida/etiqueta |
| `timestamp` | Long | Timestamp del momento exacto del escaneo |
| `synced_bigquery` | Boolean | Estado de sync con BigQuery |
| `synced_telegram` | Boolean | Estado de envío a Telegram |

---

## Formato del Fichero Excel Exportado (Telegram)

La app genera un **Excel real (.xlsx)** (no CSV) replicando exactamente `Documentacion/picking_260833_I.xlsx`:

```
Fila 1:  ID punteo: <uuid> | Matrícula de camion:  | Matrícula de remolque:  | Finca:  | Zona:  | Peso de la carga:
Fila 2:  Correo empleado | Número de pedido | EAN Variante | Cantidad | Hora y fecha | Lote | Variedad
Fila 3+: <email>         | <pedido>         | <EAN>         | <cant>    | <serial Excel>|      |
```

- `Hora y fecha`: número de serie de Excel (época 1899-12-30, hora local) con estilo `numFmt 22` (`m/d/yy h:mm`).
- Nombre: `picking_<pedido>.<num_picking>_I.xlsx` (I = inicial) o `_F.xlsx` (F = final).
- MIME al enviar: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.

Ejemplo de fila de datos:
```
emeter79@gmail.com;260833;8316721040273;3;46220.37912037037;;Maceta Olearia 25L
```
