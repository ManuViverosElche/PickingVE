package com.vivero.pickingve.ui.logistica

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivero.pickingve.data.local.dao.OrderConLineas
import com.vivero.pickingve.data.local.entities.OrderLineEntity
import com.vivero.pickingve.data.remote.PickingApiClient
import com.vivero.pickingve.data.remote.RepartoAsignacionApi
import com.vivero.pickingve.data.repository.PickingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class GestionLinea(
    val orderId: String,
    val pedidoDisplay: String,
    val line: OrderLineEntity,
    val operarioEmailAsignado: String,
    val operarioNombreAsignado: String,
    val pendiente: Int
)

data class GestionPedido(
    val orderId: String,
    val fecha: LocalDate,
    val fincaCarga: String,
    val cliente: String,
    val lineas: List<GestionLinea>
)

data class OperarioOption(val nombre: String, val email: String)

data class GestionUiState(
    val dias: List<LocalDate> = emptyList(),
    val diaSeleccionado: LocalDate? = null,
    val pedidos: List<GestionPedido> = emptyList(),
    val operarios: List<OperarioOption> = emptyList(),
    val cambiosPendientes: Map<String, RepartoAsignacionApi> = emptyMap(),
    val guardando: Boolean = false,
    val mensaje: String? = null,
    val soloSinAsignar: Boolean = false
)

class GestionFaenaViewModel(
    private val repository: PickingRepository
) : ViewModel() {

    private val diaSeleccionado = MutableStateFlow<LocalDate?>(LocalDate.now())
    private val cambios = MutableStateFlow<Map<String, RepartoAsignacionApi>>(emptyMap())
    private val guardando = MutableStateFlow(false)
    private val mensaje = MutableStateFlow<String?>(null)
    private val soloSinAsignar = MutableStateFlow(false)
    private val operarios = MutableStateFlow<List<OperarioOption>>(emptyList())

    init {
        refrescarOperarios()
    }

    fun refrescarOperarios() {
        viewModelScope.launch {
            try {
                repository.syncOperarios(PickingApiClient())
            } catch (e: Exception) {
                // Offline: se usa la copia local
            }
            operarios.value = repository.operariosLocales().map {
                OperarioOption(
                    nombre = "${it.nombre} ${it.apellidos}".trim(),
                    email = it.email
                )
            }
        }
    }

    val uiState: StateFlow<GestionUiState> = combine(
        repository.observeOrdersConLineas(),
        combine(diaSeleccionado, cambios, operarios) { d, c, o -> Triple(d, c, o) },
        combine(guardando, mensaje, soloSinAsignar) { g, m, s -> Triple(g, m, s) }
    ) { pedidos, selCambiosOp, flags ->
        construirEstado(
            pedidos,
            selCambiosOp.first,
            selCambiosOp.second,
            selCambiosOp.third,
            flags
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GestionUiState())

    private fun construirEstado(
        pedidos: List<OrderConLineas>,
        dia: LocalDate?,
        cambiosMapa: Map<String, RepartoAsignacionApi>,
        listaOperarios: List<OperarioOption>,
        flags: Triple<Boolean, String?, Boolean>
    ): GestionUiState {
        val hoy = LocalDate.now()
        val (guardandoFlag, msg, filtroSinAsignar) = flags

        val conFecha = pedidos.mapNotNull { p ->
            p.order.fechaCarga?.let {
                it to Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            }
                ?.let { pair -> pair.second to p }
        }
        val dias = conFecha.map { it.first }
            .filter { !it.isBefore(hoy.minusDays(1)) }
            .distinct()
            .sorted()
        val diaEfectivo = dia ?: dias.firstOrNull()

        val pedidosDelDia = conFecha
            .filter { it.first == diaEfectivo }
            .map { (_, p) ->
                GestionPedido(
                    orderId = p.order.orderId,
                    fecha = diaEfectivo ?: hoy,
                    fincaCarga = p.order.fincaCarga,
                    cliente = FaenaDashboardViewModel.clienteDisplay(
                        p.order.customerFiscal,
                        p.order.customerName
                    ).principal,
                    lineas = p.lineas.filter { it.vigente }.map { l ->
                        val cambio = cambiosMapa[l.orderLineId]
                        GestionLinea(
                            orderId = p.order.orderId,
                            pedidoDisplay = "Pedido ${p.order.orderId}",
                            line = l,
                            operarioEmailAsignado = cambio?.operarioEmail ?: l.operarioEmail,
                            operarioNombreAsignado = cambio?.operarioNombre ?: l.operarioNombre,
                            pendiente =
                                (l.requestedQty - maxOf(l.pickedQty, l.acopiadoServidor))
                                    .coerceAtLeast(0)
                        )
                    }.filter { !filtroSinAsignar || it.operarioEmailAsignado.isBlank() }
                )
            }
            .filter { it.lineas.isNotEmpty() }

        return GestionUiState(
            dias = dias,
            diaSeleccionado = diaEfectivo,
            pedidos = pedidosDelDia,
            operarios = listaOperarios,
            cambiosPendientes = cambiosMapa,
            guardando = guardandoFlag,
            mensaje = msg,
            soloSinAsignar = filtroSinAsignar
        )
    }

    fun seleccionarDia(dia: LocalDate) {
        diaSeleccionado.value = dia
    }

    fun toggleFiltroSinAsignar() {
        soloSinAsignar.value = !soloSinAsignar.value
    }

    /** Asigna o desasigna (email vacío) una línea; se aplica al guardar. */
    fun asignar(linea: GestionLinea, operario: OperarioOption?) {
        val actual = cambios.value.toMutableMap()
        actual[linea.line.orderLineId] = RepartoAsignacionApi(
            pedidoId = linea.orderId,
            lineaHuella = linea.line.orderLineId,
            operarioNombre = operario?.nombre.orEmpty(),
            operarioEmail = operario?.email.orEmpty()
        )
        cambios.value = actual
    }

    fun descartarCambios() {
        cambios.value = emptyMap()
    }

    fun guardar(onHecho: () -> Unit) {
        if (cambios.value.isEmpty()) return
        viewModelScope.launch {
            guardando.value = true
            try {
                val r = PickingApiClient().guardarReparto(cambios.value.values.toList())
                // Aplica en Room para feedback inmediato; el servidor queda como fuente
                repository.aplicarRepartoLocal(cambios.value.values.toList())
                cambios.value = emptyMap()
                mensaje.value = "Faena guardada (${r.guardadas + r.borradas} líneas)"
                onHecho()
            } catch (e: Exception) {
                mensaje.value = "Error al guardar: ${e.message}"
            } finally {
                guardando.value = false
            }
        }
    }

    fun reabrirLinea(linea: GestionLinea) {
        viewModelScope.launch {
            try {
                PickingApiClient().reabrirLinea(
                    com.vivero.pickingve.data.remote.ReabrirLineaRequest(
                        pedidoId = linea.orderId,
                        lineaHuella = linea.line.orderLineId,
                        reabiertaPorEmail = repository.emailFaena(),
                        motivo = "Reabierta desde la app"
                    )
                )
            } catch (e: Exception) {
                // Sin red: la reapertura local queda aplicada igualmente
            }
            repository.reabrirLineaLocal(linea.line.orderLineId)
            mensaje.value = "Línea reabierta"
        }
    }

    fun notificarDiscrepancia(linea: GestionLinea, declarado: Int, puntado: Int, texto: String) {
        viewModelScope.launch {
            repository.notificarDiscrepancia(
                pedidoId = linea.orderId,
                lineaHuella = linea.line.orderLineId,
                declarado = declarado,
                puntado = puntado,
                mensaje = texto
            )
            mensaje.value = "Discrepancia enviada al operario"
        }
    }

    fun clearMensaje() {
        mensaje.value = null
    }

    /** D-190: crea un camion compartido con los pedidos marcados del dia activo. */
    fun crearCamionCompartido(
        pedidos: List<Pair<String, String>>,
        mc: String,
        mr: String,
        onHecho: () -> Unit
    ) {
        if (pedidos.isEmpty()) return
        viewModelScope.launch {
            guardando.value = true
            try {
                PickingApiClient().crearCamionCompartido(
                    fecha = (diaSeleccionado.value ?: LocalDate.now()).toString(),
                    matriculaCamion = mc,
                    matriculaRemolque = mr,
                    pedidos = pedidos.map { it.first },
                    creadoPor = repository.emailFaena()
                )
                mensaje.value = "Camión compartido creado (${pedidos.size} pedidos)"
                onHecho()
            } catch (e: Exception) {
                mensaje.value = "Error al crear el camión: ${e.message}"
            } finally {
                guardando.value = false
            }
        }
    }
}
