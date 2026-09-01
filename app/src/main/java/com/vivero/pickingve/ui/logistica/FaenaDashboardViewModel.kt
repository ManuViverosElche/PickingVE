package com.vivero.pickingve.ui.logistica

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivero.pickingve.data.local.dao.OrderConLineas
import com.vivero.pickingve.data.local.entities.EncargadoEntity
import com.vivero.pickingve.data.local.entities.OrderLineEntity
import com.vivero.pickingve.data.remote.PickingApiClient
import com.vivero.pickingve.data.repository.PickingRepository
import com.vivero.pickingve.util.Errores
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.ceil

data class FaenaLinea(
    val orderId: String,
    val clienteDisplay: String,
    val marcaEfectiva: String,
    val line: OrderLineEntity,
    val pendiente: Int,
    val esAyuda: Boolean = false
)

/** D-237: pedido agrupado dentro de una finca de procedencia, con datos de pedido estilo picking. */
data class FaenaPedido(
    val orderId: String,
    val clienteDisplay: String,
    val marcaPedido: String,
    val fincaCarga: String,
    val sectorCarga: String,
    val plantasPendientes: Int,
    val lineas: List<FaenaLinea>
)

data class FaenaFinca(
    val finca: String,
    val fechaCarga: LocalDate,
    val plantasPendientes: Int,
    val viajesEstimados: Int,
    val pedidos: List<FaenaPedido>
)

data class FaenaDia(
    val dia: LocalDate,
    val etiqueta: String,
    val esHoy: Boolean,
    val fincas: List<FaenaFinca>
)

/** Compañero disponible para modo ayuda (encargado u operario). */
data class ColegaFaena(
    val nombre: String,
    val email: String,
    val rol: String,
    val familia: String = "",
    val lineasPendientes: Int = 0
)

data class FaenaUiState(
    val dias: List<FaenaDia> = emptyList(),
    val maquinaria: String = "",
    val capacidadViaje: Int = CAPACIDAD_DEFECTO,
    val miNombre: String = "",
    val esOperario: Boolean = false,
    val esSuperusuario: Boolean = false,
    val debeCambiarPassword: Boolean = false,
    val ayudaDe: ColegaFaena? = null,
    val colegasDisponibles: List<ColegaFaena> = emptyList(),
    val totalPlantas: Int = 0,
    val totalSolicitadas: Int = 0,
    val totalRecogidas: Int = 0,
    /** Catálogo sector -> descripción, para mostrar NOMBRES nunca códigos. */
    val sectoresDesc: Map<String, String> = emptyMap(),
    /** D-237: fincas de procedencia de planta disponibles para filtrar. */
    val fincasDisponibles: List<String> = emptyList(),
    val fincaFiltro: String? = null,
    val sectoresDisponibles: List<String> = emptyList(),
    val sectorFiltro: String? = null
) {
    companion object {
        const val CAPACIDAD_DEFECTO = 300
    }
}

class FaenaDashboardViewModel(
    private val repository: PickingRepository
) : ViewModel() {

    private val ayudaDe = MutableStateFlow<ColegaFaena?>(null)
    private val maquinaria = MutableStateFlow(repository.maquinariaActual())
    private val colegas = MutableStateFlow<List<ColegaFaena>>(emptyList())
    private val permisosAyuda = MutableStateFlow<Set<String>>(emptySet())
    private val sectores = MutableStateFlow<Map<String, String>>(emptyMap())
    private val fincaFiltro = MutableStateFlow<String?>(null)
    private val sectorFiltro = MutableStateFlow<String?>(null)

    fun filtrarPorFinca(finca: String?) {
        fincaFiltro.value = finca
        sectorFiltro.value = null
    }

    fun filtrarPorSector(sector: String?) {
        sectorFiltro.value = sector
    }

    init {
        refrescarPerfil()
        cargarSectores()
    }

    private fun cargarSectores() {
        viewModelScope.launch {
            sectores.value = repository.sectoresList()
                .associate { it.id to it.descripcion }
                .filterValues { it.isNotBlank() }
        }
    }

    fun refrescarPerfil() {
        viewModelScope.launch {
            try {
                repository.syncEncargados(PickingApiClient())
            } catch (e: Exception) {
                // Offline: se usa la copia local
            }
            try {
                repository.syncOperarios(PickingApiClient())
            } catch (e: Exception) {
                // Offline: se usa la copia local
            }
            if (repository.tipoSesion() == PickingRepository.TIPO_OPERARIO) {
                repository.currentOperario()?.let { op ->
                    maquinaria.value = op.maquinaria
                }
            } else {
                try {
                    repository.refrescarPerfilOperario(PickingApiClient())
                } catch (e: Exception) {
                    // Sin red se usa la cacheada
                }
                maquinaria.value = repository.maquinariaActual()
            }
            refrescarColegas()
            recargarPermisosAyuda()
        }
    }

    private fun refrescarColegas() {
        val miEmail = repository.emailFaena().trim()
        viewModelScope.launch {
            val listaOp = repository.operariosLocales()

            // D-235: modo ayuda SOLO entre operarios que comparten ALGUNA familia
            // de maquinaria con el logueado. Un operario puede llevar VARIAS
            // maquinarias (lista separada por comas), por lo que tiene varias
            // familias y le salen más colegas. Los encargados no aparecen (no
            // acopian planta).
            val famMap = runCatching {
                PickingApiClient().managerMaquinarias()
                    .filter { it.activo }
                    .associate { normalizarMaquinaria(it.nombre) to it.familia.trim() }
                    .filterValues { it.isNotBlank() }
            }.getOrDefault(emptyMap())
            val miMaquina = when (repository.tipoSesion()) {
                PickingRepository.TIPO_OPERARIO -> repository.currentOperario()?.maquinaria.orEmpty()
                else -> repository.maquinariaActual()
            }
            val miFamilias = familiasDe(famMap, miMaquina)

            val todos = buildList {
                listaOp.forEach { o ->
                    val fams = familiasDe(famMap, o.maquinaria)
                    // Sin catálogo de familias (sin red) ambos conjuntos están vacíos
                    // y se muestran todos (degradación natural para no bloquear el
                    // modo ayuda). Con catálogo disponible, solo si comparten familia.
                    val comparteFamilia =
                        if (miFamilias.isEmpty() && fams.isEmpty()) true
                        else fams.any { it in miFamilias }
                    if (comparteFamilia) {
                        add(
                            ColegaFaena(
                                nombre = "${o.nombre} ${o.apellidos}".trim(),
                                email = o.email,
                                rol = "OPERARIO" + if (o.maquinaria.isNotBlank()) " · ${o.maquinaria}" else "",
                                familia = fams.sorted().joinToString(" · ")
                            )
                        )
                    }
                }
            }
                .filter { !it.email.equals(miEmail, ignoreCase = true) }
                .distinctBy { it.email.lowercase() }
            colegas.value = todos
        }
    }

    private fun normalizarMaquinaria(s: String): String =
        s.lowercase(Locale.getDefault())
            .replace(Regex("[áàäâ]"), "a")
            .replace(Regex("[éèëê]"), "e")
            .replace(Regex("[íìïî]"), "i")
            .replace(Regex("[óòöô]"), "o")
            .replace(Regex("[úùüû]"), "u")
            .replace("ñ", "n")
            .trim()

    private fun familiaDe(famMap: Map<String, String>, maquinaria: String): String? {
        if (maquinaria.isBlank()) return null
        val n = normalizarMaquinaria(maquinaria)
        famMap[n]?.let { return it }
        return famMap.entries
            .firstOrNull { n.contains(it.key) }
            ?.value
    }

    /** D-235: familias de maquinaria del operario (maquinaria puede ser varias, separadas por coma). */
    private fun familiasDe(famMap: Map<String, String>, maquinaria: String): Set<String> {
        if (maquinaria.isBlank()) return emptySet()
        return maquinaria.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { familiaDe(famMap, it) }
            .toSet()
    }

    fun activarAyuda(colega: ColegaFaena?) {
        ayudaDe.value = colega
        recargarPermisosAyuda()
    }

    /** D-169: el ayudante solo ve las líneas que le fueron concedidas. */
    private fun recargarPermisosAyuda() {
        viewModelScope.launch {
            permisosAyuda.value = try {
                PickingApiClient()
                    .fetchAyudasConcedidas(repository.emailFaena())
                    .map { it.lineaHuella }
                    .toSet()
            } catch (e: Exception) {
                emptySet()
            }
        }
    }

    fun cambiarPassword(actual: String, nueva: String, onResultado: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val email = repository.currentOperario()?.email.orEmpty()
            if (email.isBlank()) {
                onResultado(false, "Sesión de operario no encontrada")
                return@launch
            }
            try {
                repository.cambiarPasswordOperario(PickingApiClient(), email, actual, nueva)
                sesPrefsCambioHecho()
                onResultado(true, "Contraseña actualizada")
            } catch (e: Exception) {
                onResultado(false, "No se pudo cambiar: ${Errores.traducir(e)}")
            }
        }
    }

    private suspend fun sesPrefsCambioHecho() {
        // Refresca la sesión para limpiar el aviso de cambio obligatorio
        repository.refreshCurrentEncargadoFromLocal()
        val op = repository.operariosLocales().firstOrNull {
            it.email.equals(repository.emailFaena(), ignoreCase = true)
        }
        if (op != null && repository.tipoSesion() == PickingRepository.TIPO_OPERARIO) {
            repository.setCurrentOperario(op.copy(debeCambiarPassword = false))
        }
    }

    /**
     * D-233: acopio directo desde "Mi faena". El operario dice cuántas plantas
     * ha cogido de la línea y se registra como acopio normal (offline-first).
     */
    fun acopiarCantidad(linea: FaenaLinea, cantidad: Int, onResultado: (Boolean, String) -> Unit) {
        if (cantidad <= 0) {
            onResultado(false, "Indica cuántas plantas has cogido")
            return
        }
        viewModelScope.launch {
            try {
                val numero = repository.nextPickingNumber(linea.orderId)
                repository.createRecord(
                    orderId = linea.orderId,
                    pickingNumber = numero,
                    pickingType = "I",
                    orderLineId = linea.line.orderLineId,
                    scannedEan = null,
                    ocrRawText = null,
                    originalProductId = linea.line.productId,
                    actualProductId = linea.line.productId,
                    liters = null,
                    measure = null,
                    caliber = null,
                    batchQty = cantidad
                )
                subirPendientesBestEffort()
                onResultado(true, "Acopiadas $cantidad · ${linea.line.productName}")
            } catch (e: Exception) {
                onResultado(false, "No se pudo registrar el acopio: ${Errores.traducir(e)}")
            }
        }
    }

    fun modificarCantidadAcopiada(linea: FaenaLinea, cantidad: Int, onResultado: (Boolean, String) -> Unit) {
        if (cantidad !in 0..linea.line.requestedQty) {
            onResultado(false, "La cantidad debe estar entre 0 y ${linea.line.requestedQty}")
            return
        }
        viewModelScope.launch {
            try {
                repository.modificarCantidadAcopiada(linea.line, cantidad)
                subirPendientesBestEffort()
                onResultado(true, "Cantidad actualizada")
            } catch (e: Exception) {
                onResultado(false, "No se pudo actualizar la cantidad: ${Errores.traducir(e)}")
            }
        }
    }

    /** D-233: cerrar la línea desde "Mi faena" (no hay más planta que acopiar). */
    fun cerrarLineaFaena(
        line: OrderLineEntity,
        cantidadFaltante: Int,
        motivo: String,
        texto: String,
        onResultado: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.cerrarLinea(line, motivo, texto, cantidadFaltante)
                onResultado(true, "Línea cerrada")
            } catch (e: Exception) {
                onResultado(false, "No se pudo cerrar la línea: ${Errores.traducir(e)}")
            }
        }
    }

    private suspend fun subirPendientesBestEffort() {
        try {
            repository.uploadPendingRegistros(PickingApiClient())
        } catch (e: Exception) {
            // Sin red: la compensación se sube en el siguiente ciclo
        }
    }

    /** Concede al colega las líneas seleccionadas de MI faena visible (D-169). */
    fun concederAyuda(lineas: List<Pair<String, String>>, ayudanteEmail: String, onListo: () -> Unit) {
        viewModelScope.launch {
            try {
                PickingApiClient().concederAyuda(
                    lineas.map { (pedido, linea) ->
                        com.vivero.pickingve.data.remote.AyudaPermisoLineaApi(pedidoId = pedido, lineaHuella = linea)
                    },
                    ayudanteEmail,
                    repository.emailFaena()
                )
            } catch (e: Exception) {
                // Sin red: se reintenta desde el panel o más tarde
            }
            onListo()
        }
    }

    val uiState: StateFlow<FaenaUiState> = combine(
        repository.observeOrdersConLineas(),
        ayudaDe,
        maquinaria,
        combine(sectores, fincaFiltro, sectorFiltro) { s, f, sec -> Triple(s, f, sec) },
        combine(colegas, permisosAyuda) { c, p -> c to p }
    ) { pedidos, ayuda, mq, filtros, extra ->
        construirEstado(pedidos, ayuda, mq, filtros.first, filtros.second, filtros.third, extra.first, extra.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FaenaUiState())

    private fun construirEstado(
        pedidos: List<OrderConLineas>,
        ayuda: ColegaFaena?,
        mq: String,
        sectoresMap: Map<String, String>,
        filtroFinca: String?,
        filtroSector: String?,
        candidatosColegas: List<ColegaFaena>,
        permisos: Set<String>
    ): FaenaUiState {
        val hoy = LocalDate.now()
        val capacidad = capacidadPara(mq)
        val miEmail = repository.emailFaena().trim()
        val esSuper = repository.esSuperusuario()
        val esOperario = repository.tipoSesion() == PickingRepository.TIPO_OPERARIO

        data class AcumFinca(
            val nombreFinca: String,
            val porPedido: MutableMap<String, MutableList<FaenaLinea>> = mutableMapOf()
        )

        val porDia = mutableMapOf<LocalDate, MutableList<AcumFinca>>()
        var totalPlantas = 0
        var totalSolicitadas = 0
        var totalRecogidas = 0

        val hayReparto = pedidos.any { p ->
            p.lineas.any { it.operarioEmail.isNotBlank() }
        }

        for (p in pedidos) {
            if (p.order.cargado) continue
            val fecha = p.order.fechaCarga?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            } ?: continue
            if (fecha.isBefore(hoy)) continue
            val marcaPedido = p.order.marcaPedido

            for (line in p.lineas.filter { it.vigente && it.motivoCierre.isBlank() }) {
                val asignadaAMi = line.operarioEmail.isNotBlank() &&
                    line.operarioEmail.equals(miEmail, ignoreCase = true)
                val visible = when {
                    ayuda != null -> asignadaAAyuda(line, ayuda) &&
                        permisos.contains(line.orderLineId)
                    esSuper -> true
                    !hayReparto -> true
                    else -> asignadaAMi
                }
                if (!visible) continue

                totalSolicitadas += line.requestedQty
                // D-274: Separación de contadores según el rol
                // Operario: cuenta su propio acopio (acopiadoOperario)
                // Encargado: cuenta su propia verificación (pickedQty)
                val recogido = if (esOperario) line.acopiadoOperario else line.pickedQty
                totalRecogidas += recogido

                val pendiente =
                    (line.requestedQty - recogido)
                        .coerceAtLeast(0)
                if (pendiente < 0) continue

                val fincaProc = line.fincaAcopio
                    .ifBlank { line.fincaArticulo }
                    .ifBlank { p.order.fincaCarga }
                    .ifBlank { "Sin finca" }
                if (filtroFinca != null && !filtroFinca.equals(fincaProc, ignoreCase = true)) continue

                val sectorProc = line.sectorAcopio.ifBlank { line.sectorDesc }
                if (filtroSector != null && !filtroSector.equals(sectorProc, ignoreCase = true)) continue

                val diaAcum = porDia.getOrPut(fecha) { mutableListOf() }
                val acumFinca = diaAcum.firstOrNull { it.nombreFinca.equals(fincaProc, ignoreCase = true) }
                    ?: AcumFinca(nombreFinca = fincaProc).also { diaAcum += it }
                acumFinca.porPedido.getOrPut(p.order.orderId) { mutableListOf() } += FaenaLinea(
                    orderId = p.order.orderId,
                    clienteDisplay = clienteDisplay(
                        p.order.customerFiscal,
                        p.order.customerName
                    ).principal,
                    marcaEfectiva = line.marca.ifBlank { marcaPedido },
                    line = line,
                    pendiente = pendiente,
                    esAyuda = ayuda != null
                )
                totalPlantas += pendiente
            }
        }

        val dias = porDia.entries
            .sortedBy { it.key }
            .map { (dia, acumuladores) ->
                val fincas = acumuladores.mapNotNull { acum ->
                    val pedidosFinca = acum.porPedido.entries.map { (orderId, lineas) ->
                        val lineasOrd = lineas.sortedWith(
                            compareByDescending<FaenaLinea> { esUltra(it) }.thenBy { it.line.posicion }
                        )
                        val pedidoRef = pedidos.firstOrNull { it.order.orderId == orderId }?.order
                        FaenaPedido(
                            orderId = orderId,
                            clienteDisplay = lineasOrd.firstOrNull()?.clienteDisplay.orEmpty(),
                            marcaPedido = pedidoRef?.marcaPedido.orEmpty(),
                            fincaCarga = pedidoRef?.fincaCarga.orEmpty(),
                            sectorCarga = pedidoRef?.sectorCarga.orEmpty(),
                            plantasPendientes = lineasOrd.sumOf { it.pendiente },
                            lineas = lineasOrd
                        )
                    }.sortedWith(
                        compareByDescending<FaenaPedido> { esUltraEnPedido(it) }
                            .thenByDescending { it.plantasPendientes }
                            .thenBy { it.orderId }
                    )
                    val plantas = acum.porPedido.values.flatten().sumOf { it.pendiente }
                    FaenaFinca(
                        finca = acum.nombreFinca,
                        fechaCarga = dia,
                        plantasPendientes = plantas,
                        viajesEstimados = if (plantas == 0) 0 else ceil(plantas / capacidad.toDouble()).toInt(),
                        pedidos = pedidosFinca
                    )
                }.sortedByDescending { it.plantasPendientes }
                FaenaDia(
                    dia = dia,
                    etiqueta = etiquetaDia(dia, hoy),
                    esHoy = dia == hoy,
                    fincas = fincas
                )
            }

        val fincasDisponibles = dias.flatMap { it.fincas.map { f -> f.finca } }
            .distinct()
            .sorted()

        val sectoresDisponibles = dias.flatMap { it.fincas }
            .filter { filtroFinca == null || it.finca.equals(filtroFinca, ignoreCase = true) }
            .flatMap { f -> f.pedidos.flatMap { p -> p.lineas } }
            .map { l -> l.line.sectorAcopio.ifBlank { l.line.sectorDesc } }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        val debeCambiarPass = repository.currentOperario()?.debeCambiarPassword == true

        return FaenaUiState(
            dias = dias,
            maquinaria = mq,
            capacidadViaje = capacidad,
            miNombre = repository.nombreFaena(),
            esOperario = esOperario,
            esSuperusuario = esSuper,
            debeCambiarPassword = debeCambiarPass,
            ayudaDe = ayuda,
            colegasDisponibles = candidatosColegas,
            totalPlantas = totalPlantas,
            totalSolicitadas = totalSolicitadas,
            totalRecogidas = totalRecogidas,
            sectoresDesc = sectoresMap,
            fincasDisponibles = fincasDisponibles,
            fincaFiltro = filtroFinca,
            sectoresDisponibles = sectoresDisponibles,
            sectorFiltro = filtroSector
        )
    }

    private fun asignadaAAyuda(line: OrderLineEntity, ayuda: ColegaFaena): Boolean =
        line.operarioEmail.isNotBlank() && line.operarioEmail.equals(ayuda.email, ignoreCase = true)

    companion object {
        fun capacidadPara(maquinaria: String): Int {
            val m = maquinaria.uppercase(Locale.getDefault())
            return when {
                m.contains("TRACTOR") || m.contains("REMOLQUE") -> 500
                m.contains("PALET") || m.contains("TRANS") -> 300
                m.contains("BUGGY") || m.contains("QUAD") || m.contains("CARRO") -> 250
                m.contains("CARRETILLA") || m.contains("MANO") -> 100
                else -> FaenaUiState.CAPACIDAD_DEFECTO
            }
        }

        fun etiquetaDia(dia: LocalDate, hoy: LocalDate): String {
            val fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
            return when (dia) {
                hoy -> "Hoy · ${dia.format(fmt)}"
                hoy.plusDays(1) -> "Mañana · ${dia.format(fmt)}"
                else -> dia.format(
                    java.time.format.DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy", Locale("es"))
                )
            }
        }

        /** Nombre mostrado: fiscal - comercial (el comercial va en cursiva en la UI). */
        fun clienteDisplay(fiscal: String, comercial: String): ClienteNombre = when {
            fiscal.isNotBlank() && comercial.isNotBlank() &&
                !fiscal.equals(comercial, ignoreCase = true) ->
                ClienteNombre(principal = fiscal, comercial = comercial)
            comercial.isNotBlank() -> ClienteNombre(principal = comercial, comercial = "")
            else -> ClienteNombre(principal = fiscal, comercial = "")
        }

        /** D-187: solo el valor EXACTO PRIORITARIO es ultra. "NO PRIORITARIO" no. */
        fun esUltra(l: FaenaLinea): Boolean =
            l.line.prioridad.trim().uppercase(Locale.getDefault()) == "PRIORITARIO"

        private fun esUltraEnPedido(pedido: FaenaPedido): Boolean =
            pedido.lineas.any { esUltra(it) }

        /** D-237: true si el pedido tiene alguna línea PRIORITARIA (para chips de la finca). */
        fun esUltraEnPedidoPublic(pedido: FaenaPedido): Boolean = esUltraEnPedido(pedido)
    }
}

data class ClienteNombre(
    val principal: String,
    val comercial: String
)
