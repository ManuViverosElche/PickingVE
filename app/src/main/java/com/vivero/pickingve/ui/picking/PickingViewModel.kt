package com.vivero.pickingve.ui.picking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivero.pickingve.data.local.entities.LitrajeEntity
import com.vivero.pickingve.data.local.entities.OrderLineEntity
import com.vivero.pickingve.data.local.entities.PickingRecordEntity
import com.vivero.pickingve.data.local.entities.ProductEntity
import com.vivero.pickingve.data.remote.PickingApiClient
import com.vivero.pickingve.data.repository.PickingRepository
import com.vivero.pickingve.data.repository.SettingsRepository
import com.vivero.pickingve.domain.usecase.MatchOcrUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PickingUiState(
    val selectedOrderId: String? = null,
    val order: com.vivero.pickingve.data.local.entities.OrderEntity? = null,
    val lines: List<OrderLineEntity> = emptyList(),
    val pendingConfirm: PendingConfirm? = null,
    val pendingLinePick: PendingLinePick? = null,
    val availableProducts: List<ProductEntity> = emptyList(),
    val pendingLabelCount: Int = 0,
    val labelsHistory: List<PickingRecordEntity> = emptyList(),
    val labelsRequestedByLine: Map<String, Int> = emptyMap(),
    val substitutedByLine: Map<String, Int> = emptyMap(),
    val litrajes: List<LitrajeEntity> = emptyList(),
    val lastMessage: String? = null,
    val sendingReport: Boolean = false,
    val sendingLabels: Boolean = false,
    val sobrante: Boolean = false,
    val unpickingMode: Boolean = false,
    val selectedRecordIds: Set<String> = emptySet()
)

data class PendingConfirm(
    val orderId: String,
    val product: ProductEntity,
    val orderLineId: String?,
    val posicion: Int,
    val orderProductName: String,
    val originalProductId: String,
    val isAmpliacion: Boolean = false
)

data class PendingLinePick(
    val orderId: String,
    val product: ProductEntity,
    val candidateLines: List<OrderLineEntity>,
    val isSubstitution: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
class PickingViewModel(
    private val repository: PickingRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val selectedOrderId = MutableStateFlow<String?>(null)
    private val pendingConfirm = MutableStateFlow<PendingConfirm?>(null)
    private val pendingLinePick = MutableStateFlow<PendingLinePick?>(null)
    private val lastMessage = MutableStateFlow<String?>(null)
    private val sendingReport = MutableStateFlow(false)
    private val sendingLabels = MutableStateFlow(false)
    private val unpickingMode = MutableStateFlow(false)
    private val selectedRecordIds = MutableStateFlow<Set<String>>(emptySet())
    private var unpickTargetLine: OrderLineEntity? = null

    private val lines: StateFlow<List<OrderLineEntity>> = selectedOrderId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.observeOrderLines(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val order: StateFlow<com.vivero.pickingve.data.local.entities.OrderEntity?> = selectedOrderId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repository.observeOrders().map { list -> list.firstOrNull { it.orderId == id } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val pendingLabelCount: StateFlow<Int> = selectedOrderId
        .flatMapLatest { id ->
            if (id == null) flowOf(0)
            else repository.observePendingLabels(id).map { it.size }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingLabels: StateFlow<List<PickingRecordEntity>> = selectedOrderId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.observePendingLabels(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val labelsHistory: StateFlow<List<PickingRecordEntity>> = selectedOrderId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.observeLabelsHistory(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val labelsRequestedByLine: StateFlow<Map<String, Int>> = selectedOrderId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyMap())
            else repository.observeLabelsRequestedByLine(id)
                .map { rows -> rows.mapNotNull { it.orderLineId?.let { id2 -> id2 to it.cnt } }.toMap() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val substitutedByLine: StateFlow<Map<String, Int>> = selectedOrderId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyMap())
            else repository.observeSubstitutedByLine(id)
                .map { rows -> rows.mapNotNull { it.orderLineId?.let { id2 -> id2 to it.cnt } }.toMap() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val litrajes: StateFlow<List<LitrajeEntity>> = repository.observeLitrajes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val unpickState: StateFlow<Pair<Boolean, Set<String>>> = combine(
        unpickingMode,
        selectedRecordIds
    ) { mode, selected -> mode to selected }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false to emptySet())

    val uiState: StateFlow<PickingUiState> = combine(
        combine(
            selectedOrderId,
            order,
            lines,
            pendingConfirm,
            pendingLinePick
        ) { orderId, order, orderLines, confirm, pick ->
            PickingUiState(
                selectedOrderId = orderId,
                order = order,
                lines = orderLines,
                pendingConfirm = confirm,
                pendingLinePick = pick,
                sobrante = order?.sobrante == true
            )
        },
        unpickState,
        combine(
            lastMessage,
            sendingReport,
            sendingLabels
        ) { message, sending, sendingLabels ->
            Triple(message, sending, sendingLabels)
        },
        combine(
            pendingLabelCount,
            labelsHistory,
            labelsRequestedByLine,
            substitutedByLine,
            litrajes
        ) { labels, history, requested, substituted, litrajes ->
            Quint(labels, history, requested, substituted, litrajes)
        }
    ) { base, unpick, flags, extra ->
        base.copy(
            unpickingMode = unpick.first,
            selectedRecordIds = unpick.second,
            lastMessage = flags.first,
            sendingReport = flags.second,
            sendingLabels = flags.third,
            pendingLabelCount = extra.labelCount,
            labelsHistory = extra.history,
            labelsRequestedByLine = extra.requested,
            substitutedByLine = extra.substituted,
            litrajes = extra.litrajes
        )
    }.combine(repository.observeProducts()) { base, products ->
        base.copy(availableProducts = products)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PickingUiState())

    private data class Quint(
        val labelCount: Int,
        val history: List<PickingRecordEntity>,
        val requested: Map<String, Int>,
        val substituted: Map<String, Int>,
        val litrajes: List<LitrajeEntity>
    )

    fun selectOrder(orderId: String) {
        selectedOrderId.value = orderId
        viewModelScope.launch { repository.clearOrderModificado(orderId) }
    }

    suspend fun getNextPickingNumber(): Int {
        val orderId = selectedOrderId.value ?: return 1
        return repository.nextPickingNumber(orderId)
    }

    suspend fun getCurrentPickingNumber(): Int {
        val orderId = selectedOrderId.value ?: return 1
        return repository.currentPickingNumber(orderId)
    }

    /** Raw EAN barcode scanned by the camera. */
    fun onBarcodeScanned(ean: String) {
        viewModelScope.launch {
            val product = repository.findProductByEan(ean)
            if (product == null) {
                lastMessage.value = "Referencia EAN no encontrada: $ean"
            } else {
                val currentState = uiState.value
                when {
                    currentState.unpickingMode -> unpickByScanProduct(product)
                    currentState.sobrante -> unpickSobranteByScan(product)
                    else -> resolveProduct(product)
                }
            }
        }
    }

    /** Raw text captured via OCR fallback (label without EAN). */
    fun onOcrText(text: String) {
        viewModelScope.launch {
            val (product, score) = MatchOcrUseCase().bestMatch(text, repository.allProducts())
            if (product == null) {
                lastMessage.value = "Sin coincidencia clara (score $score) para: ${text.take(60)}"
            } else {
                val currentState = uiState.value
                when {
                    currentState.unpickingMode -> unpickByScanProduct(product)
                    currentState.sobrante -> unpickSobranteByScan(product)
                    else -> resolveProduct(product)
                }
            }
        }
    }

    private suspend fun unpickByScanProduct(product: ProductEntity) {
        val orderId = selectedOrderId.value ?: return
        val target = unpickTargetLine
            ?: repository.orderLinesList(orderId).firstOrNull { line ->
                line.vigente && (line.productId == product.reference || line.productId == product.id)
            }
        if (target == null) {
            lastMessage.value = "No hay una línea de ${product.name} en el pedido"
            return
        }
        val ok = repository.unpickLineByScan(orderId, target.orderLineId)
        lastMessage.value = if (ok) {
            "Desacopiado ${target.productId} x 1 (escaneo)"
        } else {
            "No hay unidades acopiadas de ${target.productId}"
        }
    }

    private suspend fun unpickSobranteByScan(product: ProductEntity) {
        val orderId = selectedOrderId.value ?: run {
            lastMessage.value = "Selecciona primero un pedido"
            return
        }
        val success = repository.unpickSobrante(orderId, product.reference)
        if (success) {
            lastMessage.value = "Sobrante devuelto: ${product.name}"
        } else {
            lastMessage.value = "No hay sobrante acopiado de ${product.name}"
        }
    }

    /**
     * Assigns a scanned product to the order. If the product matches exactly one
     * line -> confirm directly. If several lines -> offer a list to pick. If no
     * line matches -> offer the lines as substitution plus the ampliacion option.
     */
    private suspend fun resolveProduct(product: ProductEntity) {
        val orderId = selectedOrderId.value ?: run {
            lastMessage.value = "Selecciona primero un pedido"
            return
        }
        val lines = repository.orderLinesList(orderId)
        val vigenteLines = lines.filter { it.vigente }
        val pendingLines = vigenteLines.filter { (it.requestedQty - maxOf(it.pickedQty, it.acopiadoServidor)) > 0 }

        val pendingMatches = pendingLines.filter { line ->
            line.productId == product.reference || line.productId == product.id
        }
        if (pendingMatches.size == 1) {
            prepareConfirm(product, pendingMatches.first())
            return
        }
        if (pendingMatches.size > 1) {
            pendingLinePick.value = PendingLinePick(
                orderId = orderId,
                product = product,
                candidateLines = pendingMatches,
                isSubstitution = false
            )
            return
        }

        val allMatches = vigenteLines.filter { line ->
            line.productId == product.reference || line.productId == product.id
        }
        when {
            allMatches.size == 1 -> prepareConfirm(product, allMatches.first())
            allMatches.size > 1 -> pendingLinePick.value = PendingLinePick(
                orderId = orderId,
                product = product,
                candidateLines = allMatches,
                isSubstitution = false
            )
            else -> pendingLinePick.value = PendingLinePick(
                orderId = orderId,
                product = product,
                candidateLines = pendingLines,
                isSubstitution = true
            )
        }
    }

    /** User picked which line this scanned batch belongs to. */
    fun assignToLine(line: OrderLineEntity) {
        val pick = pendingLinePick.value ?: return
        pendingLinePick.value = null
        prepareConfirm(pick.product, line)
    }

    /** The scanned product is a new reference not requested in the order (ampliacion). */
    fun confirmAmpliacion() {
        val pick = pendingLinePick.value ?: return
        if (!pick.isSubstitution) return
        pendingLinePick.value = null
        pendingConfirm.value = PendingConfirm(
            orderId = pick.orderId,
            product = pick.product,
            orderLineId = null,
            posicion = 0,
            orderProductName = pick.product.name,
            originalProductId = pick.product.reference,
            isAmpliacion = true
        )
    }

    fun dismissLinePick() {
        pendingLinePick.value = null
    }

    private fun prepareConfirm(product: ProductEntity, line: OrderLineEntity) {
        pendingConfirm.value = PendingConfirm(
            orderId = line.orderId,
            product = product,
            orderLineId = line.orderLineId,
            posicion = line.posicion,
            orderProductName = line.productName,
            originalProductId = line.productId
        )
    }

    fun confirmPicking(
        pickingType: String,
        liters: Float?,
        measure: String?,
        caliber: String?,
        needsLabel: Boolean,
        labelReason: String = "",
        labelFormat: String = ""
    ) {
        val confirm = pendingConfirm.value ?: return
        viewModelScope.launch {
            val pickingNumber = repository.nextPickingNumber(confirm.orderId)
            repository.createRecord(
                orderId = confirm.orderId,
                pickingNumber = pickingNumber,
                pickingType = pickingType,
                orderLineId = confirm.orderLineId,
                scannedEan = confirm.product.ean,
                ocrRawText = null,
                originalProductId = if (confirm.isAmpliacion) confirm.product.reference
                else confirm.originalProductId,
                actualProductId = confirm.product.reference,
                liters = liters ?: confirm.product.defaultLiters,
                measure = measure ?: confirm.product.defaultMeasure,
                caliber = caliber ?: confirm.product.defaultCaliber,
                batchQty = 1,
                needsLabel = needsLabel,
                labelReason = labelReason,
                labelFormat = labelFormat
            )
            pendingConfirm.value = null
            lastMessage.value = if (confirm.isAmpliacion) {
                "Ampliación: ${confirm.product.name}"
            } else {
                "Añadido: ${confirm.product.name}"
            }
        }
    }

    fun dismissConfirm() {
        pendingConfirm.value = null
    }

    /**
     * Marks a line as picked without scanning. The checkbox marks the maceta
     * rota case: a replacement label must be printed (needsLabel -> labels queue).
     */
    fun confirmManualMark(
        line: OrderLineEntity,
        qty: Int,
        needsLabel: Boolean,
        labelReason: String = "",
        labelFormat: String = ""
    ) {
        viewModelScope.launch {
            val pickingNumber = repository.nextPickingNumber(line.orderId)
            repository.createRecord(
                orderId = line.orderId,
                pickingNumber = pickingNumber,
                pickingType = "I",
                orderLineId = line.orderLineId,
                scannedEan = null,
                ocrRawText = null,
                originalProductId = line.productId,
                actualProductId = line.productId,
                liters = null,
                measure = null,
                caliber = null,
                batchQty = qty,
                needsLabel = needsLabel,
                labelReason = labelReason,
                labelFormat = labelFormat
            )
            lastMessage.value = "Acopio sin escaneo: ${line.productName} x $qty" +
                if (needsLabel) " · cambio de maceta: etiqueta a sacar" else ""
        }
    }

    fun showOcrError() {
        lastMessage.value = "No se pudo leer la etiqueta. Inténtalo de nuevo."
    }

    /** Generates the XLSX report for the current order and sends it to Telegram. */
    fun sendTelegramReport(
        pickingNumber: Int,
        pickingType: String,
        matriculaCamion: String? = null,
        matriculaRemolque: String? = null,
        pesoCarga: String? = null,
        finca: String? = null,
        zona: String? = null
    ) {
        val orderId = selectedOrderId.value ?: return
        viewModelScope.launch {
            sendingReport.value = true
            val s = settingsRepository.load()
            val employeeEmail = repository.currentEncargado()?.email
                ?.takeIf { it.isNotBlank() }
                ?: s.operatorEmail
            val result = repository.sendTelegramReport(
                orderId = orderId,
                pickingNumber = pickingNumber,
                pickingType = pickingType,
                botToken = s.telegramBotToken,
                chatId = s.telegramChatId,
                employeeEmail = employeeEmail,
                matriculaCamion = matriculaCamion ?: s.matriculaCamion,
                matriculaRemolque = matriculaRemolque ?: s.matriculaRemolque,
                finca = finca ?: s.finca,
                zona = zona ?: s.zona,
                pesoCarga = pesoCarga ?: s.pesoCarga,
                labelsBotToken = s.labelsBotToken,
                labelsChatId = s.labelsChatId
            )
            sendingReport.value = false
            lastMessage.value = result.fold(
                onSuccess = {
                    val uploaded = try {
                        repository.uploadPendingRegistros(PickingApiClient())
                    } catch (e: Exception) {
                        0
                    }
                    if (uploaded > 0) "Excel enviado: $it · $uploaded registros subidos a BigQuery"
                    else "Excel enviado: $it"
                },
                onFailure = { "Error: ${it.message}" }
            )
        }
    }

    /** Sends the pending labels of the current order to Telegram and moves them to history. */
    fun sendLabelsTelegram() {
        val orderId = selectedOrderId.value ?: return
        viewModelScope.launch {
            sendingLabels.value = true
            val s = settingsRepository.load()
            val result = repository.sendLabelsTelegram(
                orderId = orderId,
                botToken = s.labelsBotToken,
                chatId = s.labelsChatId
            )
            sendingLabels.value = false
            lastMessage.value = result.fold(
                onSuccess = { "Etiquetas enviadas: $it" },
                onFailure = { "Error: ${it.message}" }
            )
        }
    }

    /** Registers the arrival of the truck and stores its license plates in the order. */
    fun registerTruckArrival(matriculaCamion: String, matriculaRemolque: String) {
        val orderId = selectedOrderId.value ?: return
        viewModelScope.launch {
            repository.registerTruckArrival(orderId, matriculaCamion, matriculaRemolque)
            lastMessage.value = "Camión registrado: $matriculaCamion"
        }
    }

    fun setSobranteMode(on: Boolean) {
        val orderId = selectedOrderId.value ?: return
        viewModelScope.launch {
            repository.markOrderSobrante(orderId, on)
            lastMessage.value = if (on) "Modo sobrante activado (los escaneos descuentan)" else "Modo sobrante desactivado"
        }
    }

    /** Marks the order as CARGADO after the final report is sent. */
    fun markOrderCargado() {
        val orderId = selectedOrderId.value ?: return
        viewModelScope.launch {
            repository.markOrderCargado(orderId)
            lastMessage.value = "Pedido marcado como CARGADO"
        }
    }

    /** Reopens a loaded (CARGADO) order with a simple confirmation. */
    fun reopenOrder() {
        val orderId = selectedOrderId.value ?: return
        viewModelScope.launch {
            repository.markOrderNotCargado(orderId)
            lastMessage.value = "Pedido reabierto"
        }
    }

    fun setUnpickingMode(on: Boolean, targetLine: OrderLineEntity? = null) {
        unpickingMode.value = on
        unpickTargetLine = if (on) targetLine else null
        if (!on) selectedRecordIds.value = emptySet()
    }

    fun toggleRecordSelection(recordId: String) {
        val current = selectedRecordIds.value
        selectedRecordIds.value = if (recordId in current) current - recordId else current + recordId
    }

    /** Desacopio por selección: borra los registros marcados. */
    fun unpickSelectedRecords() {
        val orderId = selectedOrderId.value ?: return
        val ids = selectedRecordIds.value.toList()
        if (ids.isEmpty()) {
            lastMessage.value = "Selecciona al menos un registro"
            return
        }
        viewModelScope.launch {
            repository.unpickRecordsByLine(orderId, ids)
            setUnpickingMode(false)
            lastMessage.value = "Desacopiados ${ids.size} registros"
        }
    }

    /** Desacopio por selección desde el diálogo de una línea concreta. */
    fun unpickRecords(line: OrderLineEntity, recordIds: List<String>) {
        viewModelScope.launch {
            repository.unpickRecordsByLine(line.orderId, recordIds)
            lastMessage.value = "Desacopiados ${recordIds.size} registros de ${line.productId}"
        }
    }

    /** Desacopio por escaneo: resta 1 unidad de la línea correspondiente. */
    fun unpickScanned(line: OrderLineEntity) {
        viewModelScope.launch {
            val ok = repository.unpickLineByScan(line.orderId, line.orderLineId)
            lastMessage.value = if (ok) {
                "Desacopiado ${line.productId} x 1 (escaneo)"
            } else {
                "No hay unidades acopiadas de ${line.productId}"
            }
        }
    }

    /** Registros reales de una línea (para el diálogo de desacopio). */
    suspend fun recordsForLine(lineId: String): List<PickingRecordEntity> =
        repository.recordsForLine(lineId)

    fun clearMessage() {
        lastMessage.value = null
    }

    /** Exports the pending labels of the current order as a CSV file (null if none). */
    suspend fun pendingLabelsCsvFile(orderId: String): java.io.File? =
        repository.writePendingLabelsCsv(orderId)

    /** Undo picking on a line (decrement picked quantity and real records). */
    fun unpickLine(line: OrderLineEntity, qty: Int) {
        viewModelScope.launch {
            repository.unpickLine(line.orderId, line.orderLineId, qty)
            lastMessage.value = "Desacopiado ${line.productId} x $qty"
        }
    }
}
