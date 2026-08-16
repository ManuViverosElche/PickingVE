package com.vivero.pickingve.data.repository

import android.content.Context
import com.vivero.pickingve.data.local.dao.EncargadoDao
import com.vivero.pickingve.data.local.dao.LitrajeDao
import com.vivero.pickingve.data.local.dao.OrderDao
import com.vivero.pickingve.data.local.dao.PickingDao
import com.vivero.pickingve.data.local.dao.ProductDao
import com.vivero.pickingve.data.local.entities.EncargadoEntity
import com.vivero.pickingve.data.local.entities.LitrajeEntity
import com.vivero.pickingve.data.local.entities.OrderEntity
import com.vivero.pickingve.data.local.entities.OrderLineEntity
import com.vivero.pickingve.data.local.entities.PickingRecordEntity
import com.vivero.pickingve.data.local.entities.ProductEntity
import com.vivero.pickingve.data.remote.ApiEncargado
import com.vivero.pickingve.data.remote.ApiRegistro
import com.vivero.pickingve.data.remote.ApiUploadResponse
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
    private val encargadoDao: EncargadoDao,
    private val litrajeDao: LitrajeDao
) {

    private val prefs = context.getSharedPreferences("pickingve_flags", Context.MODE_PRIVATE)

    private var firstSyncDone: Boolean
        get() = prefs.getBoolean(KEY_FIRST_SYNC, false)
        set(value) {
            prefs.edit().putBoolean(KEY_FIRST_SYNC, value).apply()
        }

    private var catalogVersion: String
        get() = prefs.getString(KEY_CATALOG_VERSION, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_CATALOG_VERSION, value).apply()
        }

    private var lastPedidosSyncAt: Long
        get() = prefs.getLong(KEY_LAST_PEDIDOS_SYNC, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_PEDIDOS_SYNC, value).apply()
        }

    private var lastFullSyncAt: Long
        get() = prefs.getLong(KEY_LAST_FULL_SYNC, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_FULL_SYNC, value).apply()
        }

    private var lastSyncEncargadoId: String?
        get() = prefs.getString(KEY_LAST_SYNC_ENCARGADO, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_SYNC_ENCARGADO, value).apply()
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
        if (!enc.activo) return null
        if (enc.passwordHash == hashPassword(usuario.trim(), password)) {
            setCurrentEncargado(enc)
            return enc
        }
        return null
    }

    /**
     * Updates the local (Room) password hash after a successful remote change,
     * so offline login keeps working with the new password.
     */
    suspend fun updateEncargadoPassword(usuario: String, password: String) {
        encargadoDao.updatePasswordHash(usuario, hashPassword(usuario, password))
    }

    suspend fun updateEncargadoEmail(usuario: String, email: String) {
        encargadoDao.updateEmail(usuario, email)
        val fresh = encargadoDao.findByUsuario(usuario) ?: return
        setCurrentEncargado(fresh)
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
                    modo = enc.modo,
                    email = enc.email,
                    activo = enc.activo
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
            .putString(KEY_ENCARGADO_EMAIL, enc.email)
            .putBoolean(KEY_ENCARGADO_ACTIVO, enc.activo)
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
            modo = prefs.getString(KEY_ENCARGADO_MODO, "PICKING") ?: "PICKING",
            email = prefs.getString(KEY_ENCARGADO_EMAIL, "") ?: "",
            activo = prefs.getBoolean(KEY_ENCARGADO_ACTIVO, true)
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
            .remove(KEY_ENCARGADO_EMAIL)
            .remove(KEY_ENCARGADO_ACTIVO)
            .remove(KEY_LAST_SYNC_ENCARGADO)
            .apply()
    }

    /**
     * Copies the freshest row of the logged-in user (Room table, refreshed from
     * the backend at login/sync) into the session prefs, so finca assignments
     * edited from the admin screen apply without re-logging in.
     */
    suspend fun refreshCurrentEncargadoFromLocal() {
        val usuario = prefs.getString(KEY_ENCARGADO_USUARIO, null) ?: return
        val fresh = encargadoDao.findByUsuario(usuario) ?: return
        setCurrentEncargado(fresh)
    }

    fun selectedFincas(): Set<String> = prefs.getString(KEY_SELECTED_FINCAS, "")
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet() ?: emptySet()

    fun saveSelectedFincas(fincas: Set<String>) {
        prefs.edit()
            .putString(KEY_SELECTED_FINCAS, fincas.sorted().joinToString(","))
            .apply()
    }

    private fun ApiEncargado.toEntity() = EncargadoEntity(
        id = id,
        nombre = nombre,
        usuario = usuario,
        passwordHash = passwordHash,
        rol = rol,
        fincasCarga = fincasCarga,
        modo = modo,
        email = email,
        activo = activo
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

    suspend fun registerTruckArrival(
        orderId: String,
        matriculaCamion: String,
        matriculaRemolque: String,
        matriculaRemolqueB: String = "",
        muelle: String = "",
        fotos: Map<String, ByteArray> = emptyMap()
    ) {
        orderDao.updateMatriculas(orderId, matriculaCamion, matriculaRemolque, matriculaRemolqueB, muelle)
        val subir = matriculaCamion.isNotBlank() || matriculaRemolque.isNotBlank() ||
            matriculaRemolqueB.isNotBlank() || muelle.isNotBlank() || fotos.isNotEmpty()
        if (!subir) return
        try {
            val api = PickingApiClient()
            val tipos = listOf("CAMION", "REMOLQUE_A", "REMOLQUE_B")
            val urls = mutableMapOf<String, String>()
            for (tipo in tipos) {
                val matricula = when (tipo) {
                    "CAMION" -> matriculaCamion
                    "REMOLQUE_A" -> matriculaRemolque
                    else -> matriculaRemolqueB
                }
                val foto = fotos[tipo]
                if (tipo != "CAMION" && matricula.isBlank() && foto == null) continue
                val muelleTipo = if (tipo == "CAMION") muelle else ""
                val url = api.guardarMatricula(
                    pedido = orderId,
                    tipo = tipo,
                    matricula = matricula,
                    muelle = muelleTipo,
                    bytes = foto,
                    nombreArchivo = "matricula_${tipo}.jpg"
                )
                if (!url.isNullOrBlank()) urls[tipo] = url
            }
            if (urls.isNotEmpty()) {
                orderDao.updateMatriculaFotos(
                    orderId,
                    camion = urls["CAMION"] ?: "",
                    remolqueA = urls["REMOLQUE_A"] ?: "",
                    remolqueB = urls["REMOLQUE_B"] ?: ""
                )
            }
        } catch (e: Exception) {
            // La matrícula queda guardada en Room aunque la subida a BigQuery falle
        }
    }

    suspend fun markOrderSobrante(orderId: String, on: Boolean) = orderDao.setOrderSobrante(orderId, on)

    suspend fun markOrderCargado(orderId: String) {
        orderDao.setOrderCargado(orderId)
        orderDao.setOrderSobrante(orderId, false)
    }

    fun observeLabelsRequestedByLine(orderId: String) =
        pickingDao.observeLabelsRequestedByLine(orderId)

    fun observeSubstitutedByLine(orderId: String) =
        pickingDao.observeSubstitutedByLine(orderId)

    fun observeLabelsHistory(orderId: String) = pickingDao.observeLabelsHistory(orderId)

    /** Resta una etiqueta pendiente; si el registro quedaba en 1, lo limpia (sale de la cola). */
    suspend fun decrementPendingLabel(recordId: String) {
        if (pickingDao.decrementLabelQty(recordId) == 0) {
            pickingDao.clearLabel(recordId)
        }
    }

    /** Quita la etiqueta pendiente de un registro sin tocar el acopio. */
    suspend fun removePendingLabel(recordId: String) = pickingDao.clearLabel(recordId)

    // ---- Picking ----
    suspend fun insertPickingRecord(record: PickingRecordEntity) {
        pickingDao.insert(record)
        if (record.orderLineId != null) {
            orderDao.addLinePickedQty(record.orderLineId, record.batchQty)
        }
        orderDao.refreshOrderStatus(record.orderId)
    }

    /** Decrements the picked quantity of a line and adjusts its real records. */
    suspend fun unpickLine(orderId: String, lineId: String, qty: Int) {
        val line = orderDao.getLinesForOrder(orderId).firstOrNull { it.orderLineId == lineId }
            ?: return
        val decrement = qty.coerceAtMost(line.pickedQty)
        if (decrement > 0) {
            orderDao.addLinePickedQty(lineId, -decrement)
            var remaining = decrement
            pickingDao.getRecordsForLine(lineId)
                .filter { it.batchQty > 0 }
                .sortedByDescending { it.timestamp }
                .forEach { record ->
                    if (remaining <= 0) return@forEach
                    val take = minOf(record.batchQty, remaining)
                    remaining -= take
                    if (record.batchQty - take <= 0) {
                        pickingDao.deleteRecord(record.recordId)
                    } else {
                        pickingDao.decrementBatchQty(record.recordId, take)
                    }
                }
            orderDao.refreshOrderStatus(orderId)
        }
    }

    suspend fun unpickSobrante(orderId: String, productRef: String): Boolean {
        val record = pickingDao.getRecordsForOrder(orderId)
            .filter { it.batchQty > 0 && it.actualProductId == productRef }
            .sortedByDescending { it.timestamp }
            .firstOrNull() ?: return false
        if (record.batchQty > 1) {
            pickingDao.decrementBatchQty(record.recordId, 1)
        } else {
            pickingDao.deleteRecord(record.recordId)
        }
        if (record.orderLineId != null) {
            orderDao.addLinePickedQty(record.orderLineId, -1)
        }
        orderDao.refreshOrderStatus(orderId)
        return true
    }

    fun observePendingBigQuery(): Flow<List<PickingRecordEntity>> =
        pickingDao.observePendingBigQuery()

    suspend fun recordsForOrder(orderId: String): List<PickingRecordEntity> =
        pickingDao.getRecordsForOrder(orderId)

    /**
     * Generates the XLSX report for an order and sends it to Telegram.
     * Filename format: picking_<orderId>.<revision>_<I|F>.xlsx; for the final
     * part the revision is omitted: picking_<orderId>_F.xlsx
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
        pesoCarga: String = "",
        labelsBotToken: String = "",
        labelsChatId: String = ""
    ): Result<String> {
        if (botToken.isBlank() || chatId.isBlank()) {
            return Result.failure(IllegalStateException("Configura el bot de Telegram en Ajustes"))
        }
        val records = pickingDao.getRecordsForOrder(orderId)
        if (records.isEmpty()) {
            return Result.failure(IllegalStateException("No hay registros que enviar"))
        }
        val lineByRecordId = orderDao.getLinesForOrder(orderId).associateBy { it.orderLineId }
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val fileName = if (pickingType == "F") {
            "picking_${orderId}_F.xlsx"
        } else {
            "picking_${orderId}.${pickingNumber}_I.xlsx"
        }
        val file = File(dir, fileName)

        val rows = records.map { record ->
            val eanServido = record.scannedEan
                ?: resolveEanForCombination(record, lineByRecordId)
            val line = record.orderLineId?.let { lineByRecordId[it] }
            val eanPedido = if (line != null) resolveEanForOrdered(line) else ""
            val refPedida = if (eanPedido.isNotBlank() && eanPedido != eanServido) eanPedido else ""
            XlsxReportGenerator.ReportRow(
                eanVariante = eanServido,
                cantidad = record.batchQty,
                timestamp = record.timestamp,
                medida = record.measure.orEmpty(),
                calibre = record.caliber.orEmpty(),
                refPedida = refPedida,
                tipo = if (record.orderLineId == null) "AMPLIACIÓN" else ""
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
            muelle = orderDao.getOrder(orderId)?.muelleCarga.orEmpty(),
            employeeEmail = employeeEmail,
            orderNumber = orderId,
            rows = rows
        )

        val reporter = TelegramReporter(botToken, chatId)
        val order = orderDao.getOrder(orderId)
        val encargado = currentEncargado()
        val caption = buildString {
            appendLine("Parte de picking $fileName")
            appendLine("$orderId - ${order?.customerName.orEmpty()}")
            appendLine("$finca - $zona - ${fechaCargaDisplay(order?.fechaCarga)}")
            append("${encargado?.nombre.orEmpty()} - $employeeEmail")
        }
        val result = reporter.sendReport(
            file,
            caption = caption,
            callbackData = "check_${orderId}_$pickingNumber"
        ).map { fileName }
        if (result.isSuccess) {
            val labelsCsv = writePendingLabelsCsv(orderId)
            if (labelsCsv != null) {
                val labelsReporter = TelegramReporter(
                    labelsBotToken.ifBlank { botToken },
                    labelsChatId.ifBlank { chatId }
                )
                labelsReporter.sendCsv(
                    labelsCsv,
                    caption = "Etiquetas a imprimir (pedido $orderId)",
                    callbackData = "check_labels_$orderId"
                ).onSuccess {
                    pickingDao.markLabelsSent(
                        pickingDao.getPendingLabelsForOrder(orderId).map { it.recordId },
                        System.currentTimeMillis()
                    )
                }
            }
        }
        return result
    }

    private fun fechaCargaDisplay(millis: Long?): String {
        if (millis == null) return ""
        return java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }

    private fun recordIdForOrder(orderId: String): String =
        UUID.nameUUIDFromBytes("punteo-$orderId".toByteArray()).toString()

    /**
     * Resolves the real EAN of a plant that has a label but was not scanned:
     * picks the catalog EAN matching the combination (reference + litraje + sector)
     * of its order line. Returns "" when no EAN can be resolved (a reference is
     * never used as EAN: it would break the client macro).
     */
    private suspend fun resolveEanForCombination(
        record: PickingRecordEntity,
        lineByRecordId: Map<String, OrderLineEntity>
    ): String {
        val line = record.orderLineId?.let { lineByRecordId[it] }
        return resolveEanFor(record.actualProductId, line)
    }

    private suspend fun resolveEanForOrdered(line: OrderLineEntity): String {
        return resolveEanFor(line.productId, line)
    }

    private suspend fun resolveEanFor(reference: String, line: OrderLineEntity?): String {
        val candidates = productDao.findEansByReference(reference)
        if (candidates.isEmpty()) return ""
        return candidates
            .maxByOrNull { candidate ->
                var score = 0
                if (line != null) {
                    if (candidate.litraje.isNotBlank() && candidate.litraje == line.litraje) score += 2
                    if (candidate.sector.isNotBlank() && candidate.sector == line.sector) score += 1
                }
                score
            }
            ?.ean
            .orEmpty()
    }

    /**
     * Writes the labels CSV (semicolon, UTF-8 with BOM) with the pending labels
     * to print, aggregated per combination (reference + litraje + sector + motivo).
     * Columns: Referencia, Planta, Litraje, Sector, EAN, Cantidad, Pedido,
     * Cliente, Finca carga, Sector carga, Solicitadas por, Motivo, Formato.
     */
    suspend fun writePendingLabelsCsv(orderId: String): File? {
        val labels = pickingDao.getPendingLabelsForOrder(orderId)
        if (labels.isEmpty()) return null
        val lineByRecordId = orderDao.getLinesForOrder(orderId).associateBy { it.orderLineId }
        val order = orderDao.getOrder(orderId)

        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.append(
            "Referencia;Planta;Litraje;Sector;EAN;Cantidad;Pedido;Cliente;Finca carga;Sector carga;Solicitadas por;Motivo;Formato\n"
        )
        labels
            .groupBy { record ->
                val line = record.orderLineId?.let { lineByRecordId[it] }
                listOf(
                    record.actualProductId,
                    line?.litrajeDesc.orEmpty(),
                    line?.sectorDesc.orEmpty(),
                    record.labelReason.orEmpty(),
                    record.labelFormat.orEmpty()
                )
            }
            .toSortedMap(compareBy({ it[0] }, { it[1] }, { it[2] }, { it[3] }, { it[4] }))
            .forEach { (key, records) ->
                val line = records.first().orderLineId?.let { lineByRecordId[it] }
                val ean = records.firstNotNullOfOrNull { it.scannedEan }
                    ?: resolveEanForCombination(records.first(), lineByRecordId)
                sb.append(csvEscape(key[0])).append(';')
                sb.append(csvEscape(line?.productName.orEmpty())).append(';')
                sb.append(csvEscape(key[1])).append(';')
                sb.append(csvEscape(key[2])).append(';')
                sb.append(csvEscape(ean)).append(';')
                sb.append(records.sumOf { it.batchQty }).append(';')
                sb.append(csvEscape(orderId)).append(';')
                sb.append(csvEscape(order?.customerName.orEmpty())).append(';')
                sb.append(csvEscape(order?.fincaCarga.orEmpty())).append(';')
                sb.append(csvEscape(order?.sectorCarga.orEmpty())).append(';')
                sb.append(csvEscape(currentEncargado()?.nombre.orEmpty())).append(';')
                sb.append(csvEscape(motivoLabel(key[3], key[4]))).append(';')
                sb.append(csvEscape(if (key[3] == "CAMBIO_FORMATO") key[4] else "")).append('\n')
            }

        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "etiquetas_pendientes_$orderId.csv")
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    private fun motivoLabel(reason: String, format: String): String = when (reason) {
        "MACETA_ROTA" -> "Rotura de maceta"
        "CAMBIO_FORMATO" -> "Cambio de formato a $format"
        else -> ""
    }

    /**
     * Sends the pending labels to Telegram (message + CSV) and marks them as
     * sent so they move to the labels history. Returns the CSV filename sent.
     */
    suspend fun sendLabelsTelegram(
        orderId: String,
        botToken: String,
        chatId: String
    ): Result<String> {
        if (botToken.isBlank() || chatId.isBlank()) {
            return Result.failure(IllegalStateException("Configura el bot de Telegram en Ajustes"))
        }
        val labels = pickingDao.getPendingLabelsForOrder(orderId)
        if (labels.isEmpty()) {
            return Result.failure(IllegalStateException("No hay etiquetas pendientes que enviar"))
        }
        val file = writePendingLabelsCsv(orderId) ?: return Result.failure(
            IllegalStateException("No hay etiquetas pendientes que enviar")
        )
        val reporter = TelegramReporter(botToken, chatId)
        return runCatching {
            val order = orderDao.getOrder(orderId)
            reporter.sendMessage(
                "Etiquetas a imprimir - Pedido $orderId" +
                    if (order != null) " (${order.customerName})" else "" +
                    ": ${labels.sumOf { it.batchQty }} etiquetas. Adjunto el CSV."
            ).getOrThrow()
            reporter.sendCsv(
                file,
                caption = "Etiquetas a imprimir (pedido $orderId)",
                callbackData = "check_labels_$orderId"
            ).getOrThrow()
            pickingDao.markLabelsSent(
                labels.map { it.recordId },
                System.currentTimeMillis()
            )
            file.name
        }
    }

    private fun csvEscape(value: String): String =
        if (value.contains(';') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    suspend fun markSyncedBigQuery(ids: List<String>) = pickingDao.markSyncedBigQuery(ids)
    suspend fun clearOrderModificado(orderId: String) = orderDao.clearOrderModificado(orderId)

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
        needsLabel: Boolean = false,
        labelReason: String = "",
        labelFormat: String = ""
    ): PickingRecordEntity {
        val existing = pickingDao.findMatchingRecord(
            orderId, orderLineId, scannedEan, actualProductId, measure, caliber
        )
        if (existing != null) {
            if (needsLabel) {
                pickingDao.markLabelRequested(existing.recordId, labelReason, labelFormat)
            }
            pickingDao.incrementBatchQty(existing.recordId, batchQty)
            orderDao.addLinePickedQty(existing.orderLineId.orEmpty(), batchQty)
            orderDao.refreshOrderStatus(orderId)
            return existing.copy(
                batchQty = existing.batchQty + batchQty,
                needsLabel = existing.needsLabel || needsLabel,
                labelReason = if (needsLabel) labelReason else existing.labelReason,
                labelFormat = if (needsLabel) labelFormat else existing.labelFormat
            )
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
            labelReason = labelReason,
            labelFormat = labelFormat,
            timestamp = System.currentTimeMillis(),
            empleadoEmail = currentEncargado()?.email.orEmpty(),
            empleadoNombre = currentEncargado()?.nombre.orEmpty()
        )
        insertPickingRecord(record)
        return record
    }

    /**
     * Downloads catalog (ARTICULOS + CODIGOS_EAN + LITRAJES) and the orders in a
     * date range from the BigQuery backend, storing them into Room.
     *
     * Catalog is downloaded only when the backend version changed (or the DB is
     * empty). Orders are pulled as a delta since the last sync (`modificadoDesde`),
     * or as a full window on first sync / after FULL_SYNC_MAX_AGE_MS.
     */
    suspend fun syncFromApi(
        api: PickingApiClient,
        desde: String,
        hasta: String? = null,
        finca: String? = null,
        fincas: List<String>? = null,
        estados: List<Int> = listOf(2, 3)
    ): SyncResult {
        val hasProducts = productDao.count() > 0
        val serverVersion = try {
            api.fetchCatalogoVersion()
        } catch (e: Exception) {
            ""
        }
        val productos = if (hasProducts && serverVersion.isNotBlank() && serverVersion == catalogVersion) {
            0
        } else {
            downloadCatalog(api, serverVersion)
        }

        val now = System.currentTimeMillis()
        val encargadoChanged = currentEncargado()?.id != lastSyncEncargadoId
        val needFull = lastFullSyncAt == 0L || (now - lastFullSyncAt) > FULL_SYNC_MAX_AGE_MS || encargadoChanged
        val pedidos = api.fetchPedidos(
            desde = desde,
            hasta = hasta,
            finca = finca,
            fincas = fincas,
            estados = estados,
            modificadoDesde = if (needFull) null else utcDateTime(lastPedidosSyncAt)
        )
        val modifiedOrderIds = mutableSetOf<String>()
        val orders = pedidos.map { p ->
            val prevOrder = orderDao.getOrder(p.numero)
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
                observaciones = p.observaciones,
                pickingActual = p.pickingActual,
                modificado = prevOrder?.modificado ?: false
            )
        }
        val descripciones = productDao.getAll().associate { it.id to it.name }
        val lines = pedidos.flatMap { p ->
            val existing = orderDao.getLinesForOrder(p.numero)
                .associateBy { it.orderLineId }
            val serverLineIds = mutableSetOf<String>()
            p.lineas.map { l ->
                val lineId = l.huella ?: "${p.numero}-${l.posicion ?: 0}"
                serverLineIds += lineId
                val prev = existing[lineId]
                val requested = (l.pendientes ?: 0.0).toInt()
                if (prev != null && (prev.requestedQty != requested ||
                        prev.productId != l.referencia)
                ) {
                    modifiedOrderIds += p.numero
                }
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
                    observaciones = l.observaciones,
                    marcado = l.marcado,
                    acopiadoServidor = l.acopiado,
                    vigente = true
                )
            }.also { _ ->
                val removedIds = existing.keys - serverLineIds
                if (removedIds.isNotEmpty()) {
                    orderDao.markLinesNotVigente(removedIds.toList())
                    modifiedOrderIds += p.numero
                }
            }
        }
        orderDao.upsertOrders(orders)
        orderDao.upsertLines(lines)
        modifiedOrderIds.forEach { orderDao.markOrderModificado(it) }
        orders.forEach { orderDao.refreshOrderStatus(it.orderId) }
        deleteDemoData()
        lastPedidosSyncAt = now
        if (needFull) lastFullSyncAt = now
        lastSyncEncargadoId = currentEncargado()?.id
        firstSyncDone = true
        return SyncResult(productos = productos, pedidos = orders.size, lineas = lines.size)
    }

    /**
     * Downloads the full catalog and stores the server version. Used by the
     * background worker; returns the number of products stored.
     */
    private suspend fun downloadCatalog(api: PickingApiClient, serverVersion: String): Int {
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
                batchQtyDefault = 10, updatedAt = now,
                litraje = e.litraje ?: "", sector = e.sector ?: ""
            )
        }
        productDao.upsert(products)
        litrajeDao.upsertAll(
            catalogo.litrajes.map { LitrajeEntity(id = it.id, descripcion = it.descripcion) }
        )
        if (serverVersion.isNotBlank()) catalogVersion = serverVersion
        return products.size
    }

    /**
     * Refreshes the catalog only when the backend version changed (or the DB is
     * empty). Returns true when a download happened. Safe to call from the
     * background worker.
     */
    suspend fun syncCatalogIfChanged(api: PickingApiClient): Boolean {
        val version = try {
            api.fetchCatalogoVersion()
        } catch (e: Exception) {
            return false
        }
        if (version.isBlank()) return false
        if (productDao.count() > 0 && version == catalogVersion) return false
        downloadCatalog(api, version)
        return true
    }

    private fun utcDateTime(epochMillis: Long): String =
        java.time.LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(epochMillis),
            java.time.ZoneOffset.UTC
        ).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))

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

    /** Uploads all not-yet-synced picking records to the backend with exponential backoff and explicit ack. */
    suspend fun uploadPendingRegistros(api: PickingApiClient): Int {
        val pending = pickingDao.observePendingBigQuery().first()
        if (pending.isEmpty()) return 0
        val registros = pending.map { r ->
            val qty = if (r.deleted) -r.batchQty.toDouble() else r.batchQty.toDouble()
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
                cantidadPartida = qty,
                fechaHora = Instant.ofEpochMilli(r.timestamp).toString(),
                empleadoEmail = r.empleadoEmail,
                empleadoNombre = r.empleadoNombre
            )
        }

        var attempt = 0
        val maxAttempts = 3
        var response: ApiUploadResponse? = null

        while (attempt < maxAttempts) {
            try {
                response = api.uploadRegistros(registros)
                break
            } catch (e: Exception) {
                attempt++
                if (attempt >= maxAttempts) throw e
                kotlinx.coroutines.delay(1000L * (1L shl (attempt - 1)))
            }
        }

        val accepted = response?.acceptedIds.orEmpty()
        if (accepted.isNotEmpty()) {
            pickingDao.markSyncedBigQuery(accepted)
            pending.filter { it.deleted && it.recordId in accepted }.forEach {
                pickingDao.deleteRecord(it.recordId) // physical cleanup after sync
            }
        }

        return response?.ok ?: 0
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

    suspend fun nextPickingNumber(orderId: String): Int {
        val local = pickingDao.maxPickingNumber(orderId)
        val servidor = orderDao.getOrder(orderId)?.pickingActual ?: 0
        val max = maxOf(local, servidor)
        return if (max > 1000) pickingDao.getRecordsForOrder(orderId).size + 1 else max + 1
    }

    suspend fun currentPickingNumber(orderId: String): Int {
        val local = pickingDao.maxPickingNumber(orderId)
        val servidor = orderDao.getOrder(orderId)?.pickingActual ?: 0
        val max = maxOf(local, servidor)
        return if (max > 1000) pickingDao.getRecordsForOrder(orderId).size else max
    }

    fun observePendingLabels(orderId: String): Flow<List<PickingRecordEntity>> =
        pickingDao.observePendingLabels(orderId)

    fun observeLitrajes(): Flow<List<LitrajeEntity>> = litrajeDao.observeAll()

    suspend fun litrajesList(): List<LitrajeEntity> = litrajeDao.getAll()

    suspend fun recordsForLine(lineId: String): List<PickingRecordEntity> =
        pickingDao.getRecordsForLine(lineId)

    suspend fun markOrderNotCargado(orderId: String) = orderDao.setOrderNotCargado(orderId)

    /**
     * Desacopio por selección: borra los registros elegidos y descuenta su
     * cantidad de la línea asociada.
     */
    suspend fun unpickRecordsByLine(orderId: String, recordIds: List<String>) {
        val records = pickingDao.getRecordsForOrder(orderId).filter { it.recordId in recordIds }
        if (records.isEmpty()) return
        pickingDao.deleteRecordsByIds(recordIds)
        val linesById = orderDao.getLinesForOrder(orderId).associateBy { it.orderLineId }
        records.groupBy { it.orderLineId }.forEach { (lineId, group) ->
            if (lineId != null) {
                val line = linesById[lineId]
                if (line != null) {
                    orderDao.updateLinePickedQty(
                        lineId,
                        maxOf(0, line.pickedQty - group.sumOf { it.batchQty })
                    )
                }
            }
        }
        orderDao.refreshOrderStatus(orderId)
    }

    /**
     * Desacopio por escaneo: el EAN se escanea contra una línea concreta y se
     * descuenta 1 unidad del último registro coincidente de esa línea.
     */
    suspend fun unpickLineByScan(orderId: String, lineId: String): Boolean {
        val records = pickingDao.getRecordsForOrder(orderId)
            .filter { it.orderLineId == lineId }
            .sortedByDescending { it.timestamp }
        val record = records.firstOrNull()
            ?: return false
        if (record.batchQty > 1) {
            pickingDao.decrementBatchQty(record.recordId, 1)
        } else {
            pickingDao.deleteRecord(record.recordId)
        }
        val line = orderDao.getLinesForOrder(orderId).firstOrNull { it.orderLineId == lineId }
            ?: return false
        orderDao.updateLinePickedQty(lineId, maxOf(0, line.pickedQty - 1))
        orderDao.refreshOrderStatus(orderId)
        return true
    }

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
        const val KEY_CATALOG_VERSION = "catalog_version"
        const val KEY_LAST_PEDIDOS_SYNC = "last_pedidos_sync_at"
        const val KEY_LAST_FULL_SYNC = "last_full_sync_at"
        const val KEY_LAST_SYNC_ENCARGADO = "last_sync_encargado"
        const val FULL_SYNC_MAX_AGE_MS = 12L * 60 * 60 * 1000
        const val DEMO_ORDER_ID = "10045"
        const val KEY_ENCARGADO_ID = "encargado_id"
        const val KEY_ENCARGADO_NOMBRE = "encargado_nombre"
        const val KEY_ENCARGADO_USUARIO = "encargado_usuario"
        const val KEY_ENCARGADO_ROL = "encargado_rol"
        const val KEY_ENCARGADO_FINCAS = "encargado_fincas"
        const val KEY_ENCARGADO_MODO = "encargado_modo"
        const val KEY_ENCARGADO_EMAIL = "encargado_email"
        const val KEY_ENCARGADO_ACTIVO = "encargado_activo"
        const val KEY_SELECTED_FINCAS = "selected_fincas"
    }
}