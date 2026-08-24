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

/** Compañero disponible para modo ayuda (encargado u operario). */
data class ColegaFaena(
    val nombre: String,
    val email: String,
    val rol: String,
    val lineasPendientes: Int
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
    val totalPlantas: Int = 0
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

    init {
        refrescarPerfil()
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
            val listaEnc = repository.encargadosLocales().filter { it.activo }
            val listaOp = repository.operariosLocales()
            val todos = buildList {
                listaEnc.forEach { e ->
                    add(ColegaFaena(nombre = e.nombre, email = e.email, rol = "ENCARGADO", lineasPendientes = 0))
                }
                listaOp.forEach { o ->
                    add(
                        ColegaFaena(
                            nombre = "${o.nombre} ${o.apellidos}".trim(),
                            email = o.email,
                            rol = "OPERARIO" + if (o.maquinaria.isNotBlank()) " · ${o.maquinaria}" else "",
                            lineasPendientes = 0
                        )
                    )
                }
            }
                .filter { !it.email.equals(miEmail, ignoreCase = true) }
                .distinctBy { it.email.lowercase() }
            colegas.value = todos
        }
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
                onResultado(false, "No se pudo cambiar: ${e.message}")
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
        combine(colegas, permisosAyuda) { c, p -> c to p }
    ) { pedidos, ayuda, mq, extra ->
        construirEstado(pedidos, ayuda, mq, extra.first, extra.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FaenaUiState())

    private fun construirEstado(
        pedidos: List<OrderConLineas>,
        ayuda: ColegaFaena?,
        mq: String,
        candidatosColegas: List<ColegaFaena>,
        permisos: Set<String>
    ): FaenaUiState {
        val hoy = LocalDate.now()
        val capacidad = capacidadPara(mq)
        val miEmail = repository.emailFaena().trim()
        val esSuper = repository.esSuperusuario()
        val esOperario = repository.tipoSesion() == PickingRepository.TIPO_OPERARIO

        data class Acum(
            val nombreFinca: String,
            val lineas: MutableList<FaenaLinea> = mutableListOf()
        )

        val porDia = mutableMapOf<LocalDate, MutableList<Pair<String, Acum>>>()
        var totalPlantas = 0

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
                // D-167/D-169: cada rol ve SOLO lo asignado. El SUPERUSUARIO ve todo.
                // En modo ayuda solo entran las líneas con permiso concreto concedido.
                val visible = when {
                    ayuda != null -> asignadaAAyuda(line, ayuda) &&
                        permisos.contains(line.orderLineId)
                    esSuper -> true
                    !hayReparto -> true
                    else -> asignadaAMi
                }
                if (!visible) continue
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
                    pendiente = pendiente,
                    esAyuda = ayuda != null
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
            totalPlantas = totalPlantas
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

        private fun esUltraEn(sector: FaenaSector): Boolean =
            sector.lineas.any { esUltra(it) }
    }
}

data class ClienteNombre(
    val principal: String,
    val comercial: String
)
