package com.vivero.pickingve.ui.picking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val lastMessage: String? = null,
    val sendingReport: Boolean = false
)

data class PendingConfirm(
    val orderId: String,
    val product: ProductEntity,
    val orderLineId: String?,
    val posicion: Int,
    val orderProductName: String,
    val originalProductId: String
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
                pendingLinePick = pick
            )
        },
        combine(
            lastMessage,
            sendingReport,
            pendingLabelCount
        ) { message, sending, labels ->
            Triple(message, sending, labels)
        }
    ) { base, (message, sending, labels) ->
        base.copy(
            lastMessage = message,
            sendingReport = sending,
            pendingLabelCount = labels
        )
    }.combine(repository.observeProducts()) { base, products ->
        base.copy(availableProducts = products)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PickingUiState())

    fun selectOrder(orderId: String) {
        selectedOrderId.value = orderId
    }

    suspend fun getNextPickingNumber(): Int {
        val orderId = selectedOrderId.value ?: return 1
        return repository.nextPickingNumber(orderId)
    }

    /** Raw EAN barcode scanned by the camera. */
    fun onBarcodeScanned(ean: String) {
        viewModelScope.launch {
            val product = repository.findProductByEan(ean)
            if (product == null) {
                lastMessage.value = "Referencia EAN no encontrada: $ean"
            } else {
                resolveProduct(product)
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
                resolveProduct(product)
            }
        }
    }

    /**
     * Assigns a scanned product to the order. If the product matches exactly one
     * line -> confirm directly. If several lines -> offer a list to pick. If no
     * line matches -> show all lines as substitution.
     */
    private suspend fun resolveProduct(product: ProductEntity) {
        val orderId = selectedOrderId.value ?: run {
            lastMessage.value = "Selecciona primero un pedido"
            return
        }
        val lines = repository.orderLinesList(orderId)
        val matches = lines.filter { line ->
            line.productId == product.reference || line.productId == product.id
        }
        when {
            matches.size == 1 -> prepareConfirm(product, matches.first())
            matches.size > 1 -> pendingLinePick.value = PendingLinePick(
                orderId = orderId,
                product = product,
                candidateLines = matches,
                isSubstitution = false
            )
            else -> pendingLinePick.value = PendingLinePick(
                orderId = orderId,
                product = product,
                candidateLines = lines,
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
        needsLabel: Boolean
    ) {
        val confirm = pendingConfirm.value ?: return
        viewModelScope.launch {
            val pickingNumber = repository.nextPickingNumber(confirm.orderId) + 1
            repository.createRecord(
                orderId = confirm.orderId,
                pickingNumber = pickingNumber,
                pickingType = pickingType,
                orderLineId = confirm.orderLineId,
                scannedEan = confirm.product.ean,
                ocrRawText = null,
                originalProductId = confirm.originalProductId,
                actualProductId = confirm.product.reference,
                liters = liters ?: confirm.product.defaultLiters,
                measure = measure ?: confirm.product.defaultMeasure,
                caliber = caliber ?: confirm.product.defaultCaliber,
                batchQty = 1,
                needsLabel = needsLabel
            )
            pendingConfirm.value = null
            lastMessage.value = "Añadido: ${confirm.product.name}"
        }
    }

    fun dismissConfirm() {
        pendingConfirm.value = null
    }

    /**
     * Marks a line as picked without scanning (plant arrived without our label).
     * The record left in Room acts as the comprobante: exact match + label yes/no.
     * References starting with "9" are venta directa and often carry no label.
     */
    fun confirmManualMark(
        line: OrderLineEntity,
        qty: Int,
        exactMatch: Boolean,
        hasLabel: Boolean
    ) {
        viewModelScope.launch {
            val pickingNumber = repository.nextPickingNumber(line.orderId) + 1
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
                needsLabel = !hasLabel
            )
            lastMessage.value = "Acopio sin escaneo: ${line.productName} x $qty" +
                (if (exactMatch) " · coincide" else " · NO coincide") +
                (if (hasLabel) " · con etiqueta" else " · sin etiqueta propia")
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
            val result = repository.sendTelegramReport(
                orderId = orderId,
                pickingNumber = pickingNumber,
                pickingType = pickingType,
                botToken = s.telegramBotToken,
                chatId = s.telegramChatId,
                employeeEmail = s.operatorEmail,
                matriculaCamion = matriculaCamion ?: s.matriculaCamion,
                matriculaRemolque = matriculaRemolque ?: s.matriculaRemolque,
                finca = finca ?: s.finca,
                zona = zona ?: s.zona,
                pesoCarga = pesoCarga ?: s.pesoCarga
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

    fun clearMessage() {
        lastMessage.value = null
    }

    /** Undo picking on a line (decrement picked quantity). */
    fun unpickLine(line: OrderLineEntity) {
        viewModelScope.launch {
            repository.unpickLine(line.orderId, line.orderLineId, line.pickedQty)
            lastMessage.value = "Desacopiado ${line.productId}"
        }
    }
}
