package com.vivero.pickingve.data.repository

import android.content.Context
import com.vivero.pickingve.data.local.dao.EncargadoDao
import com.vivero.pickingve.data.local.dao.OrderDao
import com.vivero.pickingve.data.local.dao.PickingDao
import com.vivero.pickingve.data.local.dao.ProductDao
import com.vivero.pickingve.data.local.entities.EncargadoEntity
import com.vivero.pickingve.data.local.entities.OrderEntity
import com.vivero.pickingve.data.local.entities.OrderLineEntity
import com.vivero.pickingve.data.local.entities.PickingRecordEntity
import com.vivero.pickingve.data.local.entities.ProductEntity
import com.vivero.pickingve.data.remote.ApiEncargado
import com.vivero.pickingve.data.remote.ApiRegistro
import com.vivero.pickingve.data.remote.PickingApiClient
import com.vivero.pickingve.data.remote.XlsxReportGenerator
import com.vivero.pickingve.data.remote.TelegramReporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

data class SyncResult(
    val productos: Int,
    val pedidos: Int,
    val lineas: Int
)

class PickingRepository(
    private val context: Context,
    private val productDao: ProductDao,
    private val orderDao: OrderDao,
    private val pickingDao: PickingDao,
    private val encargadoDao: EncargadoDao
) {

    private val prefs = context.getSharedPreferences("pickingve_flags", Context.MODE_PRIVATE)

    private var firstSyncDone: Boolean
        get() = prefs.getBoolean(KEY_FIRST_SYNC, false)
        set(value) {
            prefs.edit().putBoolean(KEY_FIRST_SYNC, value).apply()
        }

    // ---- Encargados ----
    suspend fun syncEncargados(api: PickingApiClient) {
        val encargados = api.fetchIngresos()
        encargadoDao.clear()
        encargadoDao.upsert(encargados.map { it.toEntity() })
    }

    suspend fun encargadosLocales(): List<EncargadoEntity> = encargadoDao.getAll()

    fun hashPassword(usuario: String, password: String): String {
        val raw = "$usuario:pickingve-2026:$password"
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    suspend fun loginEncargadoLocal(usuario: String, password: String): EncargadoEntity? {
        val enc = encargadoDao.findByUsuario(usuario.trim()) ?: return null
        if (enc.passwordHash == hashPassword(usuario.trim(), password)) {
            setCurrentEncargado(enc)
            return enc
        }
        return null
    }

    suspend fun loginEncargadoRemoto(api: PickingApiClient, usuario: String, password: String): Boolean {
        return try {
            val enc = api.loginEncargado(usuario.trim(), password)
            setCurrentEncargado(
                EncargadoEntity(
                    id = enc.id,
                    nombre = enc.nombre,
                    usuario = enc.usuario,
                    passwordHash = hashPassword(enc.usuario, password),
                    rol = enc.rol,
                    fincasCarga = enc.fincasCarga,
                    modo = enc.modo
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    fun setCurrentEncargado(enc: EncargadoEntity) {
        prefs.edit()
            .putString(KEY_ENCARGADO_ID, enc.id)
            .putString(KEY_ENCARGADO_NOMBRE, enc.nombre)
            .putString(KEY_ENCARGADO_USUARIO, enc.usuario)
            .putString(KEY_ENCARGADO_ROL, enc.rol)
            .putString(KEY_ENCARGADO_FINCAS, enc.fincasCarga)
            .putString(KEY_ENCARGADO_MODO, enc.modo)
            .apply()
    }

    fun currentEncargado(): EncargadoEntity? {
        val id = prefs.getString(KEY_ENCARGADO_ID, null) ?: return null
        return EncargadoEntity(
            id = id,
            nombre = prefs.getString(KEY_ENCARGADO_NOMBRE, "") ?: "",
            usuario = prefs.getString(KEY_ENCARGADO_USUARIO, "") ?: "",
            passwordHash = "",
            rol = prefs.getString(KEY_ENCARGADO_ROL, "ENCARGADO") ?: "ENCARGADO",
            fincasCarga = prefs.getString(KEY_ENCARGADO_FINCAS, "") ?: "",
            modo = prefs.getString(KEY_ENCARGADO_MODO, "PICKING") ?: "PICKING"
        )
    }

    fun logout() {
        prefs.edit()
            .remove(KEY_ENCARGADO_ID)
            .remove(KEY_ENCARGADO_NOMBRE)
            .remove(KEY_ENCARGADO_USUARIO)
            .remove(KEY_ENCARGADO_ROL)
            .remove(KEY_ENCARGADO_FINCAS)
            .remove(KEY_ENCARGADO_MODO)
            .apply()
    }

    private fun ApiEncargado.toEntity() = EncargadoEntity(
        id = id,
        nombre = nombre,
        usuario = usuario,
        passwordHash = passwordHash,
        rol = rol,
        fincasCarga = fincasCarga,
        modo = modo
    )

    // ---- Products ----
    suspend fun upsertProducts(products: List<ProductEntity>) = productDao.upsert(products)
    suspend fun findProductByEan(ean: String): ProductEntity? = productDao.findByEan(ean)
    suspend fun searchProducts(query: String): List<ProductEntity> = productDao.search(query)
    fun observeProducts(): Flow<List<ProductEntity>> = productDao.observeAll()
    suspend fun productCount(): Int = productDao.count()

    // ---- Orders ----
    suspend fun upsertOrders(orders: List<OrderEntity>, lines: List<OrderLineEntity>) {
        orderDao.upsertOrders(orders)
        orderDao.upsertLines(lines)
    }

    fun observeOrders(): Flow<List<OrderEntity>> = orderDao.observeActiveOrders()
    fun observeOrdersWithTotals() = orderDao.observeOrdersWithTotals()
    fun searchOrders(query: String) = orderDao.searchOrders(query)
    fun observeOrderLines(orderId: String): Flow<List<OrderLineEntity>> =
        orderDao.observeLinesForOrder(orderId)

    suspend fun orderLinesList(orderId: String): List<OrderLineEntity> =
        orderDao.getLinesForOrder(orderId)

    // ---- Picking ----
    suspend fun insertPickingRecord(record: PickingRecordEntity) {
        pickingDao.insert(record)
        if (record.orderLineId != null) {
            orderDao.addLinePickedQty(record.orderLineId, record.batchQty)
        }
        orderDao.refreshOrderStatus(record.orderId)
    }

    /** Decrements the picked quantity of a line (undo picking). */
    suspend fun unpickLine(orderId: String, lineId: String, qty: Int) {
        val line = orderDao.getLinesForOrder(orderId).firstOrNull { it.orderLineId == lineId }
            ?: return
        val decrement = qty.coerceAtMost(line.pickedQty)
        if (decrement > 0) {
            orderDao.addLinePickedQty(lineId, -decrement)
            orderDao.refreshOrderStatus(orderId)
        }
    }

    fun observePendingBigQuery(): Flow<List<PickingRecordEntity>> =
        pickingDao.observePendingBigQuery()

    fun observePendingTelegram(): Flow<List<PickingRecordEntity>> =
        pickingDao.observePendingTelegram()

    suspend fun recordsForOrder(orderId: String): List<PickingRecordEntity> =
        pickingDao.getRecordsForOrder(orderId)

    /**
     * Generates the XLSX report for an order and sends it to Telegram.
     * Filename format: picking_<orderId>.<pickingNumber>_<I|F>.xlsx
     * Returns the result with the filename sent.
     */
    suspend fun sendTelegramReport(
        orderId: String,
        pickingNumber: Int,
        pickingType: String,
        botToken: String,
        chatId: String,
        employeeEmail: String,
        matriculaCamion: String = "",
        matriculaRemolque: String = "",
        finca: String = "",
        zona: String = "",
        pesoCarga: String = ""
    ): Result<String> {
        if (botToken.isBlank() || chatId.isBlank()) {
            return Result.failure(IllegalStateException("Configura el bot de Telegram en Ajustes"))
        }
        val records = pickingDao.getRecordsForOrder(orderId)
        if (records.isEmpty()) {
            return Result.failure(IllegalStateException("No hay registros que enviar"))
        }
        val productNames = productDao.getAll().associate { it.reference to it.name }
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val fileName = buildString {
            append("picking_").append(orderId)
            append(".").append(pickingNumber)
            append("_").append(pickingType)
            append(".xlsx")
        }
        val file = File(dir, fileName)

        val rows = records.map { record ->
            XlsxReportGenerator.ReportRow(
                eanVariante = record.scannedEan ?: record.actualProductId,
                cantidad = record.batchQty,
                timestamp = record.timestamp,
                lote = record.caliber ?: record.measure.orEmpty(),
                variedad = productNames[record.actualProductId].orEmpty()
            )
        }
        XlsxReportGenerator.generate(
            file = file,
            idPunteo = recordIdForOrder(orderId),
            matriculaCamion = matriculaCamion,
            matriculaRemolque = matriculaRemolque,
            finca = finca,
            zona = zona,
            pesoCarga = pesoCarga,
            employeeEmail = employeeEmail,
            orderNumber = orderId,
            rows = rows
        )

        val reporter = TelegramReporter(botToken, chatId)
        return reporter.sendReport(file, caption = "Parte de picking $fileName")
            .map { fileName }
    }

    private fun recordIdForOrder(orderId: String): String =
        UUID.nameUUIDFromBytes("punteo-$orderId".toByteArray()).toString()

    suspend fun markSyncedBigQuery(ids: List<String>) = pickingDao.markSyncedBigQuery(ids)
    suspend fun markSyncedTelegram(ids: List<String>) = pickingDao.markSyncedTelegram(ids)

    suspend fun createRecord(
        orderId: String,
        pickingNumber: Int,
        pickingType: String,
        orderLineId: String?,
        scannedEan: String?,
        ocrRawText: String?,
        originalProductId: String,
        actualProductId: String,
        liters: Float?,
        measure: String?,
        caliber: String?,
        batchQty: Int,
        needsLabel: Boolean = false
    ): PickingRecordEntity {
        val existing = pickingDao.findMatchingUnsynced(orderId, actualProductId, measure, caliber)
        if (existing != null) {
            pickingDao.incrementBatchQty(existing.recordId, batchQty)
            orderDao.addLinePickedQty(existing.orderLineId.orEmpty(), batchQty)
            orderDao.refreshOrderStatus(orderId)
            return existing.copy(batchQty = existing.batchQty + batchQty)
        }
        val record = PickingRecordEntity(
            recordId = UUID.randomUUID().toString(),
            orderId = orderId,
            pickingNumber = pickingNumber,
            pickingType = pickingType,
            orderLineId = orderLineId,
            scannedEan = scannedEan,
            ocrRawText = ocrRawText,
            originalProductId = originalProductId,
            actualProductId = actualProductId,
            isSubstituted = originalProductId != actualProductId,
            liters = liters,
            measure = measure,
            caliber = caliber,
            batchQty = batchQty,
            needsLabel = needsLabel,
            timestamp = System.currentTimeMillis()
        )
        insertPickingRecord(record)
        return record
    }

    /**
     * Downloads catalog (ARTICULOS + CODIGOS_EAN + LITRAJES) and the orders in a
     * date range from the BigQuery backend, storing them into Room.
     */
    suspend fun syncFromApi(
        api: PickingApiClient,
        desde: String,
        hasta: String? = null,
        finca: String? = null,
        fincas: List<String>? = null,
        estados: List<Int> = listOf(2, 3)
    ): SyncResult {
        val catalogo = api.fetchCatalogo()
        val descripciones = catalogo.articulos.associate { it.id to it.descripcion }

        val products = mutableListOf<ProductEntity>()
        val now = System.currentTimeMillis()
        catalogo.articulos.forEach { a ->
            products += ProductEntity(
                id = a.id, reference = a.id, ean = a.ean,
                name = a.descripcion.ifBlank { a.id },
                defaultLiters = null, defaultMeasure = null, defaultCaliber = null,
                batchQtyDefault = 10, updatedAt = now
            )
        }
        catalogo.eans.forEach { e ->
            products += ProductEntity(
                id = "EAN-${e.ean}", reference = e.referencia, ean = e.ean,
                name = descripciones[e.referencia] ?: e.referencia,
                defaultLiters = null, defaultMeasure = null, defaultCaliber = null,
                batchQtyDefault = 10, updatedAt = now
            )
        }
        productDao.upsert(products)

        val pedidos = api.fetchPedidos(
            desde = desde,
            hasta = hasta,
            finca = finca,
            fincas = fincas,
            estados = estados
        )
        val orders = pedidos.map { p ->
            OrderEntity(
                orderId = p.numero,
                customerName = p.cliente.ifBlank { "Pedido ${p.numero}" },
                customerFiscal = p.clienteFiscal,
                status = estadoLabel(p.estado),
                totalLines = p.lineas.size,
                fincaCarga = p.finca,
                sectorCarga = p.sector,
                fechaCarga = p.fechaCarga?.let(::parseCargaMillis),
                marcaPedido = p.marcaPedido,
                observaciones = p.observaciones
            )
        }
        val lines = pedidos.flatMap { p ->
            val existing = orderDao.getLinesForOrder(p.numero)
                .associateBy { it.orderLineId }
            p.lineas.map { l ->
                val lineId = l.huella ?: "${p.numero}-${l.posicion ?: 0}"
                val prev = existing[lineId]
                val requested = (l.pendientes ?: 0.0)
                    .toInt().coerceAtLeast(1)
                OrderLineEntity(
                    orderLineId = lineId,
                    orderId = p.numero,
                    productId = l.referencia,
                    productName = l.descripcion.ifBlank { l.referencia },
                    requestedQty = requested,
                    pickedQty = prev?.pickedQty ?: 0,
                    requiresMeasure = prev?.requiresMeasure
                        ?: requiresMeasureByDescription(l.referencia, descripciones),
                    posicion = l.posicion ?: 0,
                    empleado = l.empleado,
                    litraje = l.litraje,
                    litrajeDesc = l.litrajeDesc,
                    sector = l.sector,
                    sectorDesc = l.sectorDesc,
                    marca = l.marca,
                    prioridad = l.prioridad,
                    ubicacion = l.ubicacion,
                    accion = l.accion,
                    observaciones = l.observaciones
                )
            }
        }
        orderDao.upsertOrders(orders)
        orderDao.upsertLines(lines)
        deleteDemoData()
        firstSyncDone = true
        return SyncResult(productos = products.size, pedidos = orders.size, lineas = lines.size)
    }

    /**
     * A line requires measuring when the article's description says the price is
     * per cm/metre (e.g. "BRAHEA ARMATA (precio por cm)"). Measured always in cm.
     */
    private fun requiresMeasureByDescription(
        referencia: String,
        descripciones: Map<String, String>
    ): Boolean {
        val desc = descripciones[referencia].orEmpty().lowercase()
        return desc.contains("precio por") && (desc.contains("cm") || desc.contains("metro") || desc.contains("m"))
    }

    private fun parseCargaMillis(fechaCarga: String): Long? {
        return try {
            val clean = fechaCarga.substringBefore(" ")
            java.time.LocalDate.parse(clean)
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    /** Uploads all not-yet-synced picking records to the backend. Returns count uploaded. */
    suspend fun uploadPendingRegistros(api: PickingApiClient): Int {
        val pending = pickingDao.observePendingBigQuery().first()
        if (pending.isEmpty()) return 0
        val registros = pending.map { r ->
            ApiRegistro(
                recordId = r.recordId,
                orderId = r.orderId,
                pickingNumero = r.pickingNumber,
                pickingTipo = r.pickingType,
                orderLineId = r.orderLineId.orEmpty(),
                eanEscaneado = r.scannedEan.orEmpty(),
                ocrTexto = r.ocrRawText.orEmpty(),
                refOriginal = r.originalProductId,
                refServida = r.actualProductId,
                sustituido = r.isSubstituted,
                litros = r.liters?.toDouble(),
                medida = r.measure.orEmpty(),
                calibre = r.caliber.orEmpty(),
                cantidadPartida = r.batchQty.toDouble(),
                fechaHora = Instant.ofEpochMilli(r.timestamp).toString()
            )
        }
        val ok = api.uploadRegistros(registros)
        pickingDao.markSyncedBigQuery(pending.map { it.recordId })
        return ok
    }

    private fun estadoLabel(estado: Int?): String = when (estado) {
        3 -> "EN ALMACEN"
        2 -> "ENVIADO"
        1 -> "PENDIENTE PARCIAL"
        else -> "PENDIENTE"
    }

    suspend fun allProducts(): List<ProductEntity> = productDao.getAll()

    suspend fun setLineRequiresMeasure(lineId: String, requires: Boolean) =
        orderDao.setLineRequiresMeasure(lineId, requires)

    suspend fun pendingLabelsForOrder(orderId: String): List<PickingRecordEntity> =
        pickingDao.getPendingLabelsForOrder(orderId)

    suspend fun nextPickingNumber(orderId: String): Int =
        pickingDao.maxPickingNumber(orderId) + 1

    fun observePendingLabels(orderId: String): Flow<List<PickingRecordEntity>> =
        pickingDao.observePendingLabels(orderId)

    private fun demoProducts(): List<String> =
        listOf("MAC-25L-A", "MAC-3L-RO", "ARB-15L-PIT")

    private suspend fun deleteDemoData() {
        orderDao.deleteLinesForOrder(DEMO_ORDER_ID)
        orderDao.deleteOrder(DEMO_ORDER_ID)
        productDao.deleteByIds(demoProducts())
    }

    /** Seeds demo data when DB is empty so the app works immediately offline. */
    suspend fun seedSampleDataIfEmpty() {
        if (firstSyncDone) return
        if (productDao.count() > 0) return

        val products = listOf(
            ProductEntity(
                id = "MAC-25L-A", reference = "MAC-25L-A", ean = "8412345678901",
                name = "Maceta Olearia 25L", defaultLiters = 25f,
                defaultMeasure = "80-100cm", defaultCaliber = "C25", batchQtyDefault = 50
            ),
            ProductEntity(
                id = "MAC-3L-RO", reference = "MAC-3L-RO", ean = "8412345678902",
                name = "Maceta Rosal 3L", defaultLiters = 3f,
                defaultMeasure = "20-30cm", defaultCaliber = "C14", batchQtyDefault = 100
            ),
            ProductEntity(
                id = "ARB-15L-PIT", reference = "ARB-15L-PIT", ean = null,
                name = "Arbol Pitosporo 15L", defaultLiters = 15f,
                defaultMeasure = "60-80cm", defaultCaliber = "C18", batchQtyDefault = 25
            )
        )
        productDao.upsert(products)

        val order = OrderEntity(
            orderId = "10045",
            customerName = "Viveros del Este SL",
            status = "PENDIENTE",
            totalLines = 3
        )
        val lines = listOf(
            OrderLineEntity("10045-1", "10045", "MAC-25L-A", "Maceta Olearia 25L", 150),
            OrderLineEntity("10045-2", "10045", "MAC-3L-RO", "Maceta Rosal 3L", 400),
            OrderLineEntity("10045-3", "10045", "ARB-15L-PIT", "Arbol Pitosporo 15L", 75)
        )
        orderDao.upsertOrders(listOf(order))
        orderDao.upsertLines(lines)
    }

    private companion object {
        const val KEY_FIRST_SYNC = "first_sync_done"
        const val DEMO_ORDER_ID = "10045"
        const val KEY_ENCARGADO_ID = "encargado_id"
        const val KEY_ENCARGADO_NOMBRE = "encargado_nombre"
        const val KEY_ENCARGADO_USUARIO = "encargado_usuario"
        const val KEY_ENCARGADO_ROL = "encargado_rol"
        const val KEY_ENCARGADO_FINCAS = "encargado_fincas"
        const val KEY_ENCARGADO_MODO = "encargado_modo"
    }
}