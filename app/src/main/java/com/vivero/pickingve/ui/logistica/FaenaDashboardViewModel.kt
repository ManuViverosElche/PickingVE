package com.vivero.pickingve.ui.logistica

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivero.pickingve.data.local.dao.OrderConLineas
import com.vivero.pickingve.data.local.entities.EncargadoEntity
import com.vivero.pickingve.data.local.entities.OrderLineEntity
import com.vivero.pickingve.data.remote.PickingApiClient
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
import java.util.Locale
import kotlin.math.ceil

data class FaenaLinea(
    val orderId: String,
    val clienteDisplay: String,
    val marcaEfectiva: String,
    val line: OrderLineEntity,
    val pendiente: Int
)

data class FaenaSector(
    val sector: String,
    val plantasPendientes: Int,
    val lineas: List<FaenaLinea>
)

data class FaenaFinca(
    val finca: String,
    val fechaCarga: LocalDate,
    val plantasPendientes: Int,
    val viajesEstimados: Int,
    val sectores: List<FaenaSector>
)

data class FaenaDia(
    val dia: LocalDate,
    val etiqueta: String,
    val esHoy: Boolean,
    val fincas: List<FaenaFinca>
)

data class FaenaUiState(
    val dias: List<FaenaDia> = emptyList(),
    val maquinaria: String = "",
    val capacidadViaje: Int = CAPACIDAD_DEFECTO,
    val miNombre: String = "",
    val ayudaDe: EncargadoEntity? = null,
    val encargadosDisponibles: List<Pair<EncargadoEntity, Int>> = emptyList(),
    val totalPlantas: Int = 0
) {
    companion object {
        const val CAPACIDAD_DEFECTO = 300
    }
}

class FaenaDashboardViewModel(
    private val repository: PickingRepository
) : ViewModel() {

    private val ayudaDe = MutableStateFlow<EncargadoEntity?>(null)
    private val maquinaria = MutableStateFlow(repository.maquinariaActual())
    private val encargados = MutableStateFlow<List<EncargadoEntity>>(emptyList())

    init {
        refrescarPerfil()
    }

    fun refrescarPerfil() {
        viewModelScope.launch {
            encargados.value = repository.encargadosLocales().filter { it.activo }
            try {
                repository.refrescarPerfilOperario(PickingApiClient())
            } catch (e: Exception) {
                // Sin red se usa la maquinaria cacheada
            }
            maquinaria.value = repository.maquinariaActual()
        }
    }

    fun activarAyuda(enc: EncargadoEntity?) {
        ayudaDe.value = enc
    }

    val uiState: StateFlow<FaenaUiState> = combine(
        repository.observeOrdersConLineas(),
        ayudaDe,
        maquinaria,
        encargados
    ) { pedidos, ayuda, mq, colegas ->
        construirEstado(pedidos, ayuda, mq, colegas)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FaenaUiState())

    private fun construirEstado(
        pedidos: List<OrderConLineas>,
        ayuda: EncargadoEntity?,
        mq: String,
        colegas: List<EncargadoEntity>
    ): FaenaUiState {
        val yo = repository.currentEncargado()
        val hoy = LocalDate.now()
        val capacidad = capacidadPara(mq)
        val miEmail = yo?.email.orEmpty().trim()

        // Acumulador por (día, finca de acopio)
        data class Acum(
            val nombreFinca: String,
            val lineas: MutableList<FaenaLinea> = mutableListOf()
        )

        val porDia = mutableMapOf<LocalDate, MutableList<Pair<String, Acum>>>()
        var totalPlantas = 0

        val hayReparto = pedidos.any { p ->
            p.lineas.any { it.operarioEmail.isNotBlank() || it.empleado.isNotBlank() }
        }

        for (p in pedidos) {
            if (p.order.cargado) continue
            val fecha = p.order.fechaCarga?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            } ?: continue
            if (fecha.isBefore(hoy)) continue
            val marcaPedido = p.order.marcaPedido

            for (line in p.lineas.filter { it.vigente && it.motivoCierre.isBlank() }) {
                val asignada = when {
                    ayuda != null ->
                        line.operarioEmail.equals(ayuda.email, ignoreCase = true) ||
                            (!ayuda.usuario.isBlank() && line.empleado.isNotBlank() &&
                                line.empleado.equals(ayuda.usuario, ignoreCase = true))
                    !hayReparto -> true
                    line.operarioEmail.isNotBlank() ->
                        line.operarioEmail.equals(miEmail, ignoreCase = true)
                    line.empleado.isNotBlank() -> false
                    else -> true
                }
                if (!asignada) continue
                val pendiente =
                    (line.requestedQty - maxOf(line.pickedQty, line.acopiadoServidor))
                        .coerceAtLeast(0)
                if (pendiente <= 0) continue

                val fincaAcopio = line.fincaAcopio.ifBlank { p.order.fincaCarga }.ifBlank { "Sin finca" }
                val acumuladoresDia = porDia.getOrPut(fecha) { mutableListOf() }
                var acum = acumuladoresDia.firstOrNull { it.first == fincaAcopio }?.second
                if (acum == null) {
                    acum = Acum(nombreFinca = fincaAcopio)
                    acumuladoresDia += fincaAcopio to acum
                }
                acum.lineas += FaenaLinea(
                    orderId = p.order.orderId,
                    clienteDisplay = clienteDisplay(
                        p.order.customerFiscal,
                        p.order.customerName
                    ).principal,
                    marcaEfectiva = line.marca.ifBlank { marcaPedido },
                    line = line,
                    pendiente = pendiente
                )
                totalPlantas += pendiente
            }
        }

        val dias = porDia.entries
            .sortedBy { it.key }
            .map { (dia, acumuladores) ->
                val fincas = acumuladores.map { (_, acum) ->
                    val lineas = acum.lineas.sortedWith(
                        compareByDescending<FaenaLinea> { esUltra(it) }.thenBy { it.line.posicion }
                    )
                    val porSector = lineas.groupBy { l ->
                        l.line.sectorAcopio.ifBlank { l.line.sectorDesc }.ifBlank { "Sin sector" }
                    }
                    val sectores = porSector.entries
                        .map { (nombre, ls) -> FaenaSector(nombre, ls.sumOf { it.pendiente }, ls) }
                        .sortedWith(
                            compareByDescending<FaenaSector> { esUltraEn(it) }
                                .thenByDescending { it.plantasPendientes }
                        )
                    val plantas = lineas.sumOf { it.pendiente }
                    FaenaFinca(
                        finca = acum.nombreFinca,
                        fechaCarga = dia,
                        plantasPendientes = plantas,
                        viajesEstimados = if (plantas == 0) 0 else ceil(plantas / capacidad.toDouble()).toInt(),
                        sectores = sectores
                    )
                }.sortedByDescending { it.plantasPendientes }
                FaenaDia(
                    dia = dia,
                    etiqueta = etiquetaDia(dia, hoy),
                    esHoy = dia == hoy,
                    fincas = fincas
                )
            }

        return FaenaUiState(
            dias = dias,
            maquinaria = mq,
            capacidadViaje = capacidad,
            miNombre = yo?.nombre.orEmpty(),
            ayudaDe = ayuda,
            encargadosDisponibles = colegas
                .filter { !it.id.equals(yo?.id, ignoreCase = true) }
                .map { it to contarFaenaColega(pedidos, it, hoy) },
            totalPlantas = totalPlantas
        )
    }

    private fun contarFaenaColega(
        pedidos: List<OrderConLineas>,
        colega: EncargadoEntity,
        hoy: LocalDate
    ): Int = pedidos.sumOf { p ->
        val fecha = p.order.fechaCarga?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
        }
        if (p.order.cargado || fecha == null || fecha.isBefore(hoy)) 0
        else p.lineas.count { l ->
            l.vigente && l.motivoCierre.isBlank() &&
                (l.operarioEmail.equals(colega.email, ignoreCase = true) ||
                    (!colega.usuario.isBlank() && l.empleado.isNotBlank() &&
                        l.empleado.equals(colega.usuario, ignoreCase = true)))
        }
    }

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

        fun esUltra(l: FaenaLinea): Boolean =
            l.line.prioridad.uppercase(Locale.getDefault()).contains("PRIORITARIO")

        private fun esUltraEn(sector: FaenaSector): Boolean =
            sector.lineas.any { esUltra(it) }
    }
}

data class ClienteNombre(
    val principal: String,
    val comercial: String
)
