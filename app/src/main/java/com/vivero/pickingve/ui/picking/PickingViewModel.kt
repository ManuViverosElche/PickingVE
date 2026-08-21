package com.vivero.pickingve.ui.picking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivero.pickingve.data.local.entities.ChatEstadoEntity
import com.vivero.pickingve.data.local.entities.LitrajeEntity
import com.vivero.pickingve.data.local.entities.OrderLineEntity
import com.vivero.pickingve.data.local.entities.PickingRecordEntity
import com.vivero.pickingve.data.local.entities.ProductEntity
import com.vivero.pickingve.data.local.entities.SectorEntity
import com.vivero.pickingve.data.remote.PickingApiClient
import com.vivero.pickingve.data.repository.PickingRepository
import com.vivero.pickingve.data.repository.SettingsRepository
import com.vivero.pickingve.domain.usecase.ParsePlantPassportUseCase
import com.vivero.pickingve.domain.usecase.PassportData
import com.vivero.pickingve.scanner.OcrLine
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
    val pendingSectorWarning: SectorWarning? = null,
    val availableProducts: List<ProductEntity> = emptyList(),
    val pendingLabelCount: Int = 0,
    val labelsHistory: List<PickingRecordEntity> = emptyList(),
    val labelsRequestedByLine: Map<String, Int> = emptyMap(),
    val substitutedByLine: Map<String, Int> = emptyMap(),
    val litrajes: List<LitrajeEntity> = emptyList(),
    val sectores: List<SectorEntity> = emptyList(),
    val compensacionesPorLinea: Map<String, Int> = emptyMap(),
    val lastMessage: String? = null,
    val sendingReport: Boolean = false,
    val sendingLabels: Boolean = false,
    val sobrante: Boolean = false,
    val unpickingMode: Boolean = false,
    val pendingOcrMatch: PendingOcrMatch? = null,
    val pendingUnpickScan: PendingUnpickScan? = null,
    val selectedRecordIds: Set<String> = emptySet()
)

data class PendingConfirm(
    val orderId: String,
    val product: ProductEntity,
    val orderLineId: String?,
    val posicion: Int,
    val orderProductName: String,
    val originalProductId: String,
    val isAmpliacion: Boolean = false,
    val ocrText: String? = null,
    val isLabel: Boolean = false
)

data class PendingLinePick(
    val orderId: String,
    val product: ProductEntity,
    val candidateLines: List<OrderLineEntity>,
    val isSubstitution: Boolean
)

data class SectorWarning(
    val product: ProductEntity,
    val line: OrderLineEntity
)

data class PendingOcrMatch(
    val referencia: String,
    val litrajeDesc: String?,
    val sectorDesc: String?,
    val ocrText: String
)

data class PendingUnpickScan(
    val product: ProductEntity,
    val line: OrderLineEntity
)

@OptIn(ExperimentalCoroutinesApi::class)
class PickingViewModel(
    private val repository: PickingRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val selectedOrderId = MutableStateFlow<String?>(null)
    private val pendingConfirm = MutableStateFlow<PendingConfirm?>(null)
    private val pendingLinePick = MutableStateFlow<PendingLinePick?>(null)
    private val pendingSectorWarning = MutableStateFlow<SectorWarning?>(null)
    private val pendingOcrMatch = MutableStateFlow<PendingOcrMatch?>(null)
    private val pendingUnpickScan = MutableStateFlow<PendingUnpickScan?>(null)
    private val lastMessage = MutableStateFlow<String?>(null)
    private val sendingReport = MutableStateFlow(false)
    private val sendingLabels = MutableStateFlow(false)
    private val unpickingMode = MutableStateFlow(false)
    private val selectedRecordIds = MutableStateFlow<Set<String>>(emptySet())
    private var unpickTargetLine: OrderLineEntity? = null
    private var lastOcrText: String? = null

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

    private val compensacionesPorLinea: StateFlow<Map<String, Int>> = selectedOrderId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyMap())
            else repository.observeCompensacionesPendientes(id)
                .map { rows -> rows.mapNotNull { it.orderLineId?.let { id2 -> id2 to it.cnt } }.toMap() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val litrajes: StateFlow<List<LitrajeEntity>> = repository.observeLitrajes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val sectores: StateFlow<List<SectorEntity>> = repository.observeSectores()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val unpickState: StateFlow<Pair<Boolean, Set<String>>> = combine(
        unpickingMode,
        selectedRecordIds
    ) { mode, selected -> mode to selected }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false to emptySet())

    private val pendingFlags: StateFlow<Quad<PendingConfirm?, PendingLinePick?, SectorWarning?, PendingUnpickScan?>> = combine(
        pendingConfirm,
        pendingLinePick,
        pendingSectorWarning,
        pendingUnpickScan
    ) { confirm, pick, sectorWarning, unpickScan ->
        Quad(confirm, pick, sectorWarning, unpickScan)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Quad(null, null, null, null))

    val uiState: StateFlow<PickingUiState> = combine(
        combine(
            selectedOrderId,
            order,
            lines,
            pendingFlags
        ) { orderId, order, orderLines, pending ->
            PickingUiState(
                selectedOrderId = orderId,
                order = order,
                lines = orderLines,
                pendingConfirm = pending.first,
                pendingLinePick = pending.second,
                pendingSectorWarning = pending.third,
                pendingUnpickScan = pending.fourth,
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
    }.combine(repository.observeSectores()) { base, sectores ->
        base.copy(sectores = sectores)
    }.combine(compensacionesPorLinea) { base, compensaciones ->
        base.copy(compensacionesPorLinea = compensaciones)
    }.combine(pendingOcrMatch) { base, ocrMatch ->
        base.copy(pendingOcrMatch = ocrMatch)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PickingUiState())

    private data class Quad<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    private data class Quint(
        val labelCount: Int,
        val history: List<PickingRecordEntity>,
        val requested: Map<String, Int>,
        val substituted: Map<String, Int>,
        val litrajes: List<LitrajeEntity>
    )

    fun selectOrder(orderId: String) {
        selectedOrderId.value = orderId
        setUnpickingMode(false)
        pendingConfirm.value = null
        pendingLinePick.value = null
        pendingSectorWarning.value = null
        pendingOcrMatch.value = null
        pendingUnpickScan.value = null
        viewModelScope.launch { repository.clearOrderModificado(orderId) }
        actualizarChatEstados()
    }

    val chatEstados: StateFlow<List<ChatEstadoEntity>> = repository.observeChatEstados()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun actualizarChatEstados() {
        val pedidoId = selectedOrderId.value ?: return
        viewModelScope.launch {
            try {
                val comentarios = PickingApiClient().fetchComentarios(pedidoId)
                repository.actualizarChatEstados(comentarios)
            } catch (e: Exception) {
                // Sin red: se mantiene el último estado conocido
            }
        }
    }

    fun marcarChatLeido(hiloId: String) {
        viewModelScope.launch { repository.marcarChatLeido(hiloId) }
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
                // Para referencias "9" escaneadas por EAN, forzar cantidad 1
                if (product.reference.startsWith("9")) {
                    forceEanScanQtyOne = true
                }
                val currentState = uiState.value
                when {
                    currentState.unpickingMode -> unpickByScanProduct(product)
                    currentState.sobrante -> unpickSobranteByScan(product)
                    else -> resolveProduct(product)
                }
            }
        }
    }

    private var forceEanScanQtyOne = false

    /** Raw text captured via OCR fallback (plant passport label without EAN). */
    fun onOcrText(text: String, lines: List<OcrLine> = emptyList()) {
        viewModelScope.launch {
            val parser = ParsePlantPassportUseCase()
            val litrajes = repository.litrajesList()
            val sectores = repository.sectoresList()
            val passport = parser.parse(text, lines, litrajes, sectores)
            if (passport == null) {
                val recorte = text.trim().replace('\n', ' ').take(140)
                lastMessage.value = if (recorte.isBlank()) {
                    "No se detectó la referencia C: en la etiqueta"
                } else {
                    "No se detectó la referencia C: en la etiqueta. Texto: $recorte"
                }
                return@launch
            }
            var catalogo = repository.allProducts()
            if (catalogo.isEmpty()) {
                try {
                    repository.syncCatalogIfChanged(PickingApiClient())
                    catalogo = repository.allProducts()
                } catch (e: Exception) {
                    // Sin red: seguir con el catálogo vacío; el modal permite sustitución/ampliación
                }
            }
            val candidatos = parser.buscarPorReferencia(passport.referencia, catalogo)
            if (candidatos.isEmpty()) {
                lastOcrText = text
                pendingOcrMatch.value = PendingOcrMatch(
                    referencia = passport.referencia,
                    litrajeDesc = passport.litrajeDesc,
                    sectorDesc = passport.sectorDesc,
                    ocrText = text
                )
                return@launch
            }
            if (candidatos.size == 1) {
                lastOcrText = text
                rutaProducto(candidatos.first())
                return@launch
            }
            val litraje = passport.litrajeDesc?.let { parser.resolveLitraje(it, litrajes) }
            val sector = passport.sectorDesc?.let { parser.resolveSector(it, sectores) }
            val filtrados = candidatos.filter {
                (litraje == null || it.litraje.equals(litraje, ignoreCase = true)) &&
                    (sector == null || it.sector.equals(sector, ignoreCase = true))
            }
            if (filtrados.size == 1) {
                lastOcrText = text
                rutaProducto(filtrados.first())
                return@launch
            }
            lastOcrText = text
            pendingOcrMatch.value = PendingOcrMatch(
                referencia = passport.referencia,
                litrajeDesc = passport.litrajeDesc,
                sectorDesc = passport.sectorDesc,
                ocrText = text
            )
        }
    }

    /** El encargado aceptó la referencia leída (posiblemente con litraje/sector corregidos). */
    fun confirmOcrMatch(referencia: String, litrajeDesc: String?, sectorDesc: String?) {
        val pending = pendingOcrMatch.value ?: return
        val ref = referencia.trim().replaceFirst(Regex("^[cC]\\s*[:.]?\\s*"), "")
        if (ref.length < 2) {
            lastMessage.value = "Escribe la referencia C: de la etiqueta"
            return
        }
        viewModelScope.launch {
            val parser = ParsePlantPassportUseCase()
            val catalogo = repository.allProducts()
            val candidatos = parser.buscarPorReferencia(ref, catalogo)
            val litraje = litrajeDesc?.takeIf { it.isNotBlank() }?.let {
                parser.resolveLitraje(it, repository.litrajesList())
            }
            val sector = sectorDesc?.takeIf { it.isNotBlank() }?.let {
                parser.resolveSector(it, repository.sectoresList())
            }
            if (candidatos.isEmpty()) {
                // La referencia no está en el catálogo: se trata como sustitución o ampliación.
                pendingOcrMatch.value = null
                lastOcrText = pending.ocrText
                rutaProducto(
                    ProductEntity(
                        id = ref,
                        reference = ref,
                        name = ref,
                        ean = null,
                        defaultLiters = null,
                        defaultMeasure = null,
                        defaultCaliber = null,
                        litraje = litraje.orEmpty(),
                        sector = sector.orEmpty()
                    )
                )
                return@launch
            }
            val filtrados = if (candidatos.size == 1) candidatos
            else candidatos.filter {
                (litraje == null || it.litraje.equals(litraje, ignoreCase = true)) &&
                    (sector == null || it.sector.equals(sector, ignoreCase = true))
            }
            val eligioAmbos = litrajeDesc?.isNotBlank() == true && sectorDesc?.isNotBlank() == true
            if (filtrados.size != 1) {
                if (eligioAmbos) {
                    val descLitraje = litrajeDesc!!.trim()
                    val descSector = sectorDesc!!.trim()
                    val mejor = when {
                        filtrados.isNotEmpty() -> filtrados.first()
                        else -> candidatos.firstOrNull {
                            (litraje != null && it.litraje.equals(litraje, ignoreCase = true)) ||
                                it.litraje.equals(descLitraje, ignoreCase = true)
                        } ?: candidatos.firstOrNull {
                            (sector != null && it.sector.equals(sector, ignoreCase = true)) ||
                                it.sector.equals(descSector, ignoreCase = true)
                        }
                    }
                    if (mejor != null) {
                        pendingOcrMatch.value = null
                        lastOcrText = pending.ocrText
                        rutaProducto(mejor)
                        return@launch
                    }
                }
                pendingOcrMatch.value = pending.copy(
                    referencia = ref,
                    litrajeDesc = litrajeDesc?.takeIf { it.isNotBlank() },
                    sectorDesc = sectorDesc?.takeIf { it.isNotBlank() }
                )
                lastMessage.value = when {
                    candidatos.isEmpty() -> "Referencia C: $ref no encontrada en el catálogo"
                    filtrados.isEmpty() -> "Ninguna variante de C: $ref coincide con el litraje y sector elegidos"
                    else -> "Varios productos coinciden con C: $ref — elige litraje y sector"
                }
                return@launch
            }
            pendingOcrMatch.value = null
            lastOcrText = pending.ocrText
            rutaProducto(filtrados.first())
        }
    }

    fun dismissOcrMatch() {
        pendingOcrMatch.value = null
    }

    /** Ruta según el modo activo: desacopio, sobrante o acopio normal. */
    private suspend fun rutaProducto(product: ProductEntity) {
        val currentState = uiState.value
        when {
            currentState.unpickingMode -> unpickByScanProduct(product)
            currentState.sobrante -> unpickSobranteByScan(product)
            else -> resolveProduct(product)
        }
    }

    /** Acopio manual desde la etiqueta (pasaporte): referencia C: + litraje + sector. */
    fun marcarDesdeEtiqueta(referencia: String, litrajeDesc: String?, sectorDesc: String?) {
        val ref = referencia.trim().replaceFirst(Regex("^[cC]\\s*[:.]?\\s*"), "")
        if (ref.length < 2) {
            lastMessage.value = "Escribe la referencia C: de la etiqueta"
            return
        }
        lastOcrText = buildString {
            append("C: $ref")
            if (!litrajeDesc.isNullOrBlank()) append(" $litrajeDesc")
            if (!sectorDesc.isNullOrBlank()) append(" $sectorDesc")
        }
        viewModelScope.launch {
            val catalogo = repository.allProducts()
            val parser = ParsePlantPassportUseCase()
            val passport = PassportData(ref, litrajeDesc = litrajeDesc, sectorDesc = sectorDesc)
            val producto = parser.bestMatch(
                passport,
                catalogo,
                repository.litrajesList(),
                repository.sectoresList()
            ) ?: parser.buscarPorReferencia(ref, catalogo).firstOrNull()
            if (producto == null) {
                val leido = "C: $ref" +
                    (litrajeDesc?.let { " · Litraje $it" } ?: "") +
                    (sectorDesc?.let { " · Sector $it" } ?: "")
                lastMessage.value = "No encontrado en el catálogo ($leido)"
                return@launch
            }
            val currentState = uiState.value
            when {
                currentState.unpickingMode -> unpickByScanProduct(producto)
                currentState.sobrante -> unpickSobranteByScan(producto)
                else -> resolveProduct(producto)
            }
        }
    }

    /** Acopio manual: misma referencia y mismo litraje/sector que la línea → confirmación directa. */
    fun marcarDirecto(line: OrderLineEntity) {
        lastOcrText = null
        viewModelScope.launch {
            val catalogo = repository.allProducts()
            val parser = ParsePlantPassportUseCase()
            val producto = parser.buscarPorReferencia(line.productId, catalogo).firstOrNull {
                (line.litraje.isBlank() || it.litraje.isBlank() || it.litraje == line.litraje) &&
                    (line.sector.isBlank() || it.sector.isBlank() || it.sector == line.sector)
            } ?: parser.buscarPorReferencia(line.productId, catalogo).firstOrNull()
            if (producto == null) {
                lastMessage.value = "No se encontró la referencia ${line.productId} en el catálogo"
                return@launch
            }
            resolveProduct(producto)
        }
    }

    /** Acopio manual: misma referencia que la línea pero con otro litraje y/o sector de la etiqueta. */
    fun marcarVariant(line: OrderLineEntity, litrajeDesc: String?, sectorDesc: String?) {
        val mismoLitraje = litrajeDesc.isNullOrBlank() ||
            line.litrajeDesc.isBlank() ||
            litrajeDesc.trim().equals(line.litrajeDesc.trim(), ignoreCase = true)
        val mismoSector = sectorDesc.isNullOrBlank() ||
            line.sectorDesc.isBlank() ||
            sectorDesc.trim().equals(line.sectorDesc.trim(), ignoreCase = true)
        if (mismoLitraje && mismoSector) {
            marcarDirecto(line)
            return
        }
        lastOcrText = "C: ${line.productId}" +
            (litrajeDesc?.takeIf { it.isNotBlank() }?.let { " $it" } ?: "") +
            (sectorDesc?.takeIf { it.isNotBlank() }?.let { " $it" } ?: "")
        viewModelScope.launch {
            val catalogo = repository.allProducts()
            val parser = ParsePlantPassportUseCase()
            val passport = PassportData(line.productId, litrajeDesc = litrajeDesc, sectorDesc = sectorDesc)
            val variante = parser.bestMatch(
                passport,
                catalogo,
                repository.litrajesList(),
                repository.sectoresList()
            ) ?: parser.buscarPorReferencia(line.productId, catalogo).firstOrNull()
            if (variante == null) {
                lastMessage.value = "No encontrado en el catálogo (C: ${line.productId})"
                return@launch
            }
            if (coincideAtributos(variante, line)) {
                pendingSectorWarning.value = SectorWarning(variante, line)
            } else {
                resolveProduct(variante)
            }
        }
    }

    private suspend fun unpickByScanProduct(product: ProductEntity) {
        val orderId = selectedOrderId.value ?: return
        val lines = repository.orderLinesList(orderId)
        val target = lines.firstOrNull { line ->
            line.vigente && (line.productId == product.reference || line.productId == product.id)
        } ?: unpickTargetLine?.takeIf { it.vigente }
        if (target == null) {
            lastMessage.value = "No hay una línea de ${product.name} en el pedido"
            return
        }
        pendingUnpickScan.value = PendingUnpickScan(product, target)
    }

    /** El encargado confirmó el desacopio por escaneo en el modal. */
    fun confirmUnpickScan() {
        val pending = pendingUnpickScan.value ?: return
        viewModelScope.launch {
            pendingUnpickScan.value = null
            val orderId = selectedOrderId.value
            val target = if (orderId == null) pending.line
            else repository.orderLinesList(orderId)
                .firstOrNull { it.orderLineId == pending.line.orderLineId }
                ?.takeIf { it.vigente }
                ?: pending.line
            val ok = repository.unpickLineByScan(target.orderId, target.orderLineId)
            if (ok) subirPendientesBestEffort()
            lastMessage.value = if (ok) {
                "Desacopiado ${target.productId} x 1 (escaneo)"
            } else {
                "No hay unidades acopiadas de ${target.productId}"
            }
        }
    }

    fun cancelUnpickScan() {
        pendingUnpickScan.value = null
    }

    private suspend fun unpickSobranteByScan(product: ProductEntity) {
        val orderId = selectedOrderId.value ?: run {
            lastMessage.value = "Selecciona primero un pedido"
            return
        }
        val success = repository.unpickSobrante(orderId, product.reference)
        if (success) subirPendientesBestEffort()
        if (success) {
            lastMessage.value = "Sobrante devuelto: ${product.name}"
        } else {
            lastMessage.value = "No hay sobrante acopiado de ${product.name}"
        }
    }

    private suspend fun subirPendientesBestEffort() {
        try {
            repository.uploadPendingRegistros(PickingApiClient())
        } catch (e: Exception) {
            // Sin red: la compensación se sube en el siguiente ciclo
        }
    }

    /** Cuánto muestra la UI como acopiado, descontando compensaciones pendientes de subir. */
    private suspend fun shownPicked(line: OrderLineEntity, comps: Map<String, Int>): Int {
        val compensado = comps[line.orderLineId] ?: 0
        return maxOf(line.pickedQty, line.acopiadoServidor - compensado)
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
        val comps = repository.compensacionesPendientes(orderId)
            .mapNotNull { it.orderLineId?.let { l -> l to it.cnt } }.toMap()
        val vigenteLines = lines.filter { it.vigente }
        val pendingLines = vigenteLines.filter {
            (it.requestedQty - shownPicked(it, comps)) > 0
        }

        val pendingMatches = pendingLines.filter { line ->
            line.productId == product.reference || line.productId == product.id
        }
        if (pendingMatches.size == 1) {
            val line = pendingMatches.first()
            if (coincideAtributos(product, line)) {
                prepareConfirm(product, line)
            } else {
                pendingSectorWarning.value = SectorWarning(product, line)
            }
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
            isAmpliacion = true,
            ocrText = lastOcrText,
            isLabel = false
        )
    }

    fun dismissLinePick() {
        pendingLinePick.value = null
    }

    /**
     * True when the scanned variant matches the line's requested attributes.
     * A blank attribute on either side is not considered a mismatch.
     */
    private fun coincideAtributos(product: ProductEntity, line: OrderLineEntity): Boolean {
        val litrajeOk = line.litraje.isBlank() || product.litraje.isBlank() || line.litraje == product.litraje
        val sectorOk = line.sector.isBlank() || product.sector.isBlank() || line.sector == product.sector
        return litrajeOk && sectorOk
    }

    /** The user accepted the warning: register the scan against the warned line. */
    fun confirmSectorWarning() {
        val warning = pendingSectorWarning.value ?: return
        pendingSectorWarning.value = null
        prepareConfirm(warning.product, warning.line)
    }

    /** The user wants to pick another line instead of the warned one. */
    fun sectorWarningToLinePick() {
        val warning = pendingSectorWarning.value ?: return
        pendingSectorWarning.value = null
        val orderId = selectedOrderId.value ?: return
        viewModelScope.launch {
            val lines = repository.orderLinesList(orderId)
            val comps = repository.compensacionesPendientes(orderId)
                .mapNotNull { it.orderLineId?.let { l -> l to it.cnt } }.toMap()
            val vigenteLines = lines.filter { it.vigente }
            val pendingLines = vigenteLines.filter {
                (it.requestedQty - shownPicked(it, comps)) > 0
            }
            pendingLinePick.value = PendingLinePick(
                orderId = orderId,
                product = warning.product,
                candidateLines = pendingLines,
                isSubstitution = true
            )
        }
    }

    fun dismissSectorWarning() {
        pendingSectorWarning.value = null
    }

    private fun prepareConfirm(product: ProductEntity, line: OrderLineEntity) {
        val isLabel = forceEanScanQtyOne
        forceEanScanQtyOne = false
        pendingConfirm.value = PendingConfirm(
            orderId = line.orderId,
            product = product,
            orderLineId = line.orderLineId,
            posicion = line.posicion,
            orderProductName = line.productName,
            originalProductId = line.productId,
            ocrText = lastOcrText,
            isLabel = isLabel
        )
    }

    fun confirmPicking(
        pickingType: String,
        liters: Float?,
        measure: String?,
        caliber: String?,
        needsLabel: Boolean,
        labelReason: String = "",
        labelFormat: String = "",
        qty: Int = 1
    ) {
        val confirm = pendingConfirm.value ?: return
        val batchQty = if (confirm.isLabel) 1 else qty
        pendingConfirm.value = null
        lastOcrText = null
        viewModelScope.launch {
            val pickingNumber = repository.nextPickingNumber(confirm.orderId)
            repository.createRecord(
                orderId = confirm.orderId,
                pickingNumber = pickingNumber,
                pickingType = pickingType,
                orderLineId = confirm.orderLineId,
                scannedEan = confirm.product.ean,
                ocrRawText = confirm.ocrText,
                originalProductId = if (confirm.isAmpliacion) confirm.product.reference
                else confirm.originalProductId,
                actualProductId = confirm.product.reference,
                liters = liters ?: confirm.product.defaultLiters,
                measure = measure ?: confirm.product.defaultMeasure,
                caliber = caliber ?: confirm.product.defaultCaliber,
                batchQty = batchQty,
                needsLabel = needsLabel,
                labelReason = labelReason,
                labelFormat = labelFormat
            )
            pendingConfirm.value = null
            lastMessage.value = if (confirm.isAmpliacion) {
                "Ampliación: ${confirm.product.name}"
            } else {
                "Añadido: ${confirm.product.name}" + if (batchQty > 1) " x$batchQty" else ""
            }
        }
    }

    fun dismissConfirm() {
        pendingConfirm.value = null
        lastOcrText = null
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

    /** Resta una etiqueta pendiente de la cola (si era la última, la elimina). */
    fun decrementPendingLabel(recordId: String) {
        viewModelScope.launch {
            repository.decrementPendingLabel(recordId)
        }
    }

    /** Elimina la etiqueta pendiente de un registro sin desacopiar la planta. */
    fun removePendingLabel(recordId: String) {
        viewModelScope.launch {
            repository.removePendingLabel(recordId)
        }
    }

    /** Registers the arrival of the truck and stores its license plates in the order. */
    fun registerTruckArrival(
        matriculaCamion: String,
        matriculaRemolque: String,
        matriculaRemolqueB: String = "",
        muelle: String = "",
        fotos: Map<String, ByteArray> = emptyMap()
    ) {
        val orderId = selectedOrderId.value ?: return
        viewModelScope.launch {
            repository.registerTruckArrival(
                orderId,
                matriculaCamion,
                matriculaRemolque,
                matriculaRemolqueB,
                muelle,
                fotos
            )
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
            subirPendientesBestEffort()
            lastMessage.value = "Desacopiado ${line.productId} x $qty"
        }
    }
}
