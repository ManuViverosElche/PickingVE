package com.vivero.pickingve.ui.inventario

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivero.pickingve.data.local.entities.InventoryStockEntity
import com.vivero.pickingve.data.local.entities.ProductEntity
import com.vivero.pickingve.data.remote.ApiInvFinca
import com.vivero.pickingve.data.remote.ApiInvSector
import com.vivero.pickingve.data.remote.PickingApiClient
import com.vivero.pickingve.data.repository.InventarioRepository
import com.vivero.pickingve.data.repository.PickingRepository
import com.vivero.pickingve.domain.usecase.ParsePlantPassportUseCase
import com.vivero.pickingve.domain.usecase.PassportData
import com.vivero.pickingve.scanner.OcrLine
import com.vivero.pickingve.util.Constants
import com.vivero.pickingve.util.Errores
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Linea de la pantalla de pistoleo: esperado (FactuSOL) vs contado (compartido). */
data class InvLineaEstado(
    val ref: String,
    val nombre: String,
    val litraje: String,
    val litrajeDesc: String,
    val ean: String,
    val esperado: Int,
    val contado: Int,
    val estado: String,
    /** True si parte de las plantas contadas de la linea no pertenecen al sector (D-239). */
    val fueraUbicacion: Boolean = false
)

/** Variante de producto ya resuelta (descripciones de litraje/sector). */
data class InvVariante(
    val producto: ProductEntity,
    val litrajeDesc: String,
    val sectorDesc: String
)

/** Planta pendiente de confirmacion por el operario antes de contarla. */
data class PendienteInv(
    val producto: ProductEntity,
    val eanEscaneado: String?,
    val ocrTexto: String?,
    val sinEan: Boolean,
    val litrajeDesc: String,
    val sectorDesc: String,
    val esFuera: Boolean,
    val sectorInventariadoDesc: String
)

data class InvUiState(
    val cargando: Boolean = false,
    val error: String? = null,
    val fincas: List<ApiInvFinca> = emptyList(),
    val fincaSeleccionada: ApiInvFinca? = null,
    val sectorSeleccionado: ApiInvSector? = null,
    val lineas: List<InvLineaEstado> = emptyList(),
    val totalEsperado: Int = 0,
    val totalContado: Int = 0,
    val fueraCount: Int = 0,
    val pendientesSubir: Int = 0,
    val gpsActivo: Boolean = false,
    val mensaje: String? = null,
    val pendienteConfirmar: PendienteInv? = null,
    val sinEtiquetaAbierto: Boolean = false,
    val variantesOcr: List<InvVariante> = emptyList(),
    val subiendo: Boolean = false,
    val sectorCerrado: Boolean = false,
    /** Modo de pistoleo (D-243): ESTANDAR (escaneo continuo) | LINEAL (por huecos). */
    val modo: String = "ESTANDAR",
    val linealIniciado: Boolean = false,
    val linealInicio: Pair<Double, Double>? = null,
    val linealFin: Pair<Double, Double>? = null,
    val linealSessionId: String = "",
    val linealPlantas: Int = 0,
    val linealHuecos: Int = 0
)

private data class ClaveTriada(val ref: String, val litraje: String)

@OptIn(FlowPreview::class)
class InvViewModel(
    private val pickingRepository: PickingRepository,
    private val inventarioRepository: InventarioRepository
) : ViewModel() {

    private val api = PickingApiClient()

    private val _uiState = MutableStateFlow(InvUiState())
    val uiState: StateFlow<InvUiState> = _uiState

    /** Par (finca, sector) activo; null mientras se elige finca/sector. */
    private val ambito = MutableStateFlow<Pair<String, String>?>(null)

    /** Progreso compartido del servidor por triada (ref+litraje). */
    private val servidor = MutableStateFlow<Map<ClaveTriada, Int>>(emptyMap())

    /** Totales compartidos por sector (para la pantalla de eleccion de sector). */
    data class ResumenSector(val total: Int, val fuera: Int)
    private val resumenServidor = MutableStateFlow<Map<String, ResumenSector>>(emptyMap())
    val resumenServidorSectores: StateFlow<Map<String, ResumenSector>> = resumenServidor

    private val fueraPorSector = MutableStateFlow<Int>(0)

    /** Código -> descripción de litraje y sector (para no mostrar nunca códigos). */
    private val litrajeDescMap = MutableStateFlow<Map<String, String>>(emptyMap())
    private val sectorDescMap = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        cargarFincas()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(gpsActivo = inventarioRepository.hayPermisoGps())
        }
        viewModelScope.launch {
            runCatching { inventarioRepository.litrajes().associate { it.id to it.descripcion } }
                .onSuccess { litrajeDescMap.value = it }
        }
        viewModelScope.launch {
            runCatching { inventarioRepository.sectores().associate { it.id to it.descripcion } }
                .onSuccess { sectorDescMap.value = it }
        }
        viewModelScope.launch {
            ambito
                .flatMapLatest { par ->
                    if (par == null) {
                        kotlinx.coroutines.flow.combine(
                            kotlinx.coroutines.flow.flowOf(emptyList<com.vivero.pickingve.data.local.entities.InventoryStockEntity>()),
                            kotlinx.coroutines.flow.flowOf(emptyList<com.vivero.pickingve.data.local.entities.InvContadoLocal>())
                        ) { s, c -> Triple(s, c, null as Pair<String, String>?) }
                    } else if (par.second.isBlank()) {
                        // D-231: finca sin sectores -> inventariar la finca completa.
                        combine(
                            inventarioRepository.observeStockTodo(),
                            inventarioRepository.observeContadoFinca(par.first).debounce(150)
                        ) { s, c -> Triple(s, c, par) }
                    } else {
                        combine(
                            inventarioRepository.observeStockPorSector(par.second),
                            inventarioRepository.observeContadoLocal(par.first, par.second)
                                .debounce(150)
                        ) { s, c -> Triple(s, c, par) }
                    }
                }
                .combine(servidor) { triple, srv -> Triple(triple.first, triple.second, srv) }
                .combine(litrajeDescMap) { triple, lMap ->
                    construirLineas(triple.first, triple.second, triple.third, lMap)
                }
                .collect { lineas ->
                    _uiState.update {
                        it.copy(
                            lineas = lineas,
                            totalEsperado = lineas.sumOf { l -> l.esperado },
                            totalContado = lineas.sumOf { l -> l.contado }
                        )
                    }
                }
        }
        viewModelScope.launch {
            inventarioRepository.observePendientesSubir().collect { n ->
                _uiState.update { st -> st.copy(pendientesSubir = n) }
            }
        }
    }

    private fun construirLineas(
        stock: List<InventoryStockEntity>,
        contado: List<com.vivero.pickingve.data.local.entities.InvContadoLocal>,
        srv: Map<ClaveTriada, Int>,
        litrajeMap: Map<String, String>
    ): List<InvLineaEstado> {
        // D-240 (enmienda D-231): unificar por referencia + DESCRIPCION de litraje.
        // El mismo contenedor puede llegar con codigos distintos ("100" y "100L"
        // describen ambos "100L"), mientras "60" (60-70L) y "60L" (60L) son
        // contenedores distintos y deben seguir separados. Ademas el esperado se
        // SUMA: STOCK repite filas por la dimension poda/contenedor (D-219).
        fun canon(raw: String): String {
            val d = litrajeMap[raw]?.trim().orEmpty()
            return d.ifBlank { raw }
        }
        val esperadoPor = HashMap<ClaveTriada, Int>()
        val contadoPor = HashMap<ClaveTriada, Int>()
        val fueraPor = HashMap<ClaveTriada, Int>()
        val stockRaw = HashMap<ClaveTriada, String>()
        val contadoRaw = HashMap<ClaveTriada, String>()
        val nombres = HashMap<String, Pair<String, String>>()
        stock.forEach { s ->
            val key = ClaveTriada(s.ref, canon(s.litraje))
            esperadoPor[key] = (esperadoPor[key] ?: 0) + s.stock.toInt()
            stockRaw.putIfAbsent(key, s.litraje)
            nombres.putIfAbsent(s.ref, s.nombre to s.ean)
        }
        contado.forEach { c ->
            val key = ClaveTriada(c.ref, canon(c.litraje))
            contadoPor[key] = (contadoPor[key] ?: 0) + c.contado
            fueraPor[key] = (fueraPor[key] ?: 0) + c.fuera
            contadoRaw.putIfAbsent(key, c.litraje)
        }
        val remotoPor = HashMap<ClaveTriada, Int>()
        srv.forEach { (t, v) -> remotoPor[ClaveTriada(t.ref, canon(t.litraje))] = v }

        val claves = linkedSetOf<ClaveTriada>()
        esperadoPor.keys.forEach { claves.add(it) }
        contadoPor.keys.forEach { claves.add(it) }
        remotoPor.keys.forEach { claves.add(it) }
        return claves.sortedWith(compareBy({ it.ref }, { it.litraje })).map { clave ->
            val esperado = esperadoPor[clave] ?: 0
            val local = contadoPor[clave] ?: 0
            val remoto = remotoPor[clave] ?: 0
            val contadoTotal = maxOf(local, remoto)
            val dif = contadoTotal - esperado
            InvLineaEstado(
                ref = clave.ref,
                nombre = nombres[clave.ref]?.first.orEmpty(),
                litraje = stockRaw[clave] ?: contadoRaw[clave] ?: "",
                litrajeDesc = clave.litraje,
                ean = nombres[clave.ref]?.second.orEmpty(),
                esperado = esperado,
                contado = contadoTotal,
                fueraUbicacion = (fueraPor[clave] ?: 0) > 0,
                estado = if (dif == 0) "OK" else if (dif > 0) "EXCESO" else "FALTA"
            )
        }
    }

    // ---- Seleccion finca / sector ----

    fun cargarFincas() {
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, error = null) }
            try {
                val fincas = inventarioRepository.fincas(api)
                _uiState.update { it.copy(cargando = false, fincas = fincas) }
            } catch (e: Exception) {
                _uiState.update { it.copy(cargando = false, error = Errores.traducir(e)) }
            }
        }
    }

    fun seleccionarFinca(finca: ApiInvFinca) {
        if (finca.sectores.isEmpty()) {
            // D-231: finca sin sectores -> directo al pistoleo de la finca completa
            // (se fija el "sector" pseudo inmediatamente para no pasar por la lista de sectores).
            ambito.value = finca.finca to ""
            _uiState.update {
                it.copy(
                    cargando = true,
                    fincaSeleccionada = finca,
                    error = null,
                    sectorSeleccionado = ApiInvSector(id = "", descripcion = "Toda la finca"),
                    sectorCerrado = false
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    cargando = true,
                    fincaSeleccionada = finca,
                    error = null,
                    sectorSeleccionado = null,
                    sectorCerrado = false
                )
            }
        }
        viewModelScope.launch {
            try {
                inventarioRepository.syncStock(api, finca.finca)
                _uiState.update { it.copy(cargando = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(cargando = false, error = Errores.traducir(e)) }
            }
        }
    }

    fun volverAFincas() {
        ambito.value = null
        servidor.value = emptyMap()
        fueraPorSector.value = 0
        _uiState.update { it.copy(sectorSeleccionado = null, fincaSeleccionada = null) }
    }

    fun volverASeleccionSector() {
        val finca = _uiState.value.fincaSeleccionada
        if (finca != null && finca.sectores.isEmpty()) {
            volverAFincas()
            return
        }
        ambito.value = null
        servidor.value = emptyMap()
        _uiState.update { it.copy(sectorSeleccionado = null) }
        refrescarServidor()
    }

    fun seleccionarSector(sector: ApiInvSector) {
        val finca = _uiState.value.fincaSeleccionada ?: return
        ambito.value = finca.finca to sector.id
        _uiState.update { it.copy(sectorSeleccionado = sector) }
        refrescarServidor()
    }

    fun refrescarServidor() {
        val finca = _uiState.value.fincaSeleccionada?.finca ?: return
        viewModelScope.launch {
            try {
                val prog = api.inventarioProgreso(finca)
                val sectorActivo = _uiState.value.sectorSeleccionado?.id
                val map = HashMap<ClaveTriada, Int>()
                var fuera = 0
                prog.lineas.forEach { l ->
                    if (sectorActivo.isNullOrBlank() || l.sector == sectorActivo) {
                        map[ClaveTriada(l.ref, l.litraje)] = l.contado.toInt()
                        fuera += l.fuera
                    }
                }
                servidor.value = map
                fueraPorSector.value = fuera
                resumenServidor.value = prog.resumen.associate {
                    it.sector to ResumenSector(it.total.toInt(), it.fuera)
                }
                _uiState.update { it.copy(fueraCount = fuera) }
            } catch (_: Exception) {
            }
        }
    }

    // ---- Escaneo EAN ----

    fun onBarcodeScanned(ean: String) {
        viewModelScope.launch {
            if (_uiState.value.pendienteConfirmar != null) return@launch
            try {
                val producto = inventarioRepository.resolverEan(ean)
                if (producto == null) {
                    mensaje("El EAN $ean no está en el catálogo")
                    return@launch
                }
                solicitarConfirmacion(producto, eanEscaneado = ean, ocrTexto = null, sinEan = false)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = Errores.traducir(e)) }
            }
        }
    }

    /** OCR capturado por la camara: parsea el pasaporte con los catalogos locales. */
    fun onOcrCapturado(texto: String, lineas: List<OcrLine>) {
        viewModelScope.launch {
            if (_uiState.value.pendienteConfirmar != null) return@launch
            try {
                val lit = inventarioRepository.litrajes()
                val sec = inventarioRepository.sectores()
                val datos = ParsePlantPassportUseCase().parse(texto, lineas, lit, sec)
                if (datos == null) {
                    mensaje("No se pudo leer la etiqueta")
                } else {
                    onPasaporteLeido(datos, texto)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = Errores.traducir(e)) }
            }
        }
    }

    fun onPasaporteLeido(datos: PassportData, textoCrudo: String) {
        viewModelScope.launch {
            try {
                val variantes = inventarioRepository.variantesDeReferencia(datos.referencia)
                if (variantes.isEmpty()) {
                    mensaje("C: ${datos.referencia} no está en el catálogo")
                    return@launch
                }
                val candidatas = filtrarVariantes(variantes, datos.litraje, datos.sector)
                if (candidatas.size == 1) {
                    solicitarConfirmacion(candidatas.first(), eanEscaneado = null, ocrTexto = textoCrudo, sinEan = true)
                } else {
                    _uiState.update {
                        it.copy(
                            variantesOcr = (candidatas.ifEmpty { variantes }).map { p ->
                                InvVariante(p, litrajeDesc(p.litraje), sectorDesc(p.sector))
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = Errores.traducir(e)) }
            }
        }
    }

    fun elegirVarianteOcr(variante: InvVariante) {
        _uiState.update { it.copy(variantesOcr = emptyList()) }
        solicitarConfirmacion(variante.producto, eanEscaneado = null, ocrTexto = null, sinEan = true)
    }

    fun cancelarVariantesOcr() {
        _uiState.update { it.copy(variantesOcr = emptyList()) }
    }

    private fun filtrarVariantes(
        variantes: List<ProductEntity>,
        litraje: String?,
        sector: String?
    ): List<ProductEntity> {
        var lista = variantes
        if (!litraje.isNullOrBlank()) {
            val porLitraje = lista.filter { it.litraje.equals(litraje.trim(), ignoreCase = true) }
            if (porLitraje.isNotEmpty()) lista = porLitraje
        }
        if (!sector.isNullOrBlank()) {
            val porSector = lista.filter { it.sector.equals(sector.trim(), ignoreCase = true) }
            if (porSector.isNotEmpty()) lista = porSector
        }
        return lista
    }

    /** Prepara la confirmacion del operario antes de contar la planta (nunca se suma en caliente). */
    private fun solicitarConfirmacion(
        producto: ProductEntity,
        eanEscaneado: String?,
        ocrTexto: String?,
        sinEan: Boolean
    ) {
        val par = ambito.value ?: return
        val sectorEtiqueta = producto.sector.trim()
        // D-231: en finca completa (sector vacio) no hay "fuera de sector".
        val esFuera = par.second.isNotBlank() && sectorEtiqueta.isNotBlank() && sectorEtiqueta != par.second
        _uiState.update {
            it.copy(
                variantesOcr = emptyList(),
                pendienteConfirmar = PendienteInv(
                    producto = producto,
                    eanEscaneado = eanEscaneado,
                    ocrTexto = ocrTexto,
                    sinEan = sinEan,
                    litrajeDesc = litrajeDesc(producto.litraje),
                    sectorDesc = sectorDesc(producto.sector),
                    esFuera = esFuera,
                    sectorInventariadoDesc = it.sectorSeleccionado?.descripcion.orEmpty()
                )
            )
        }
    }

    /**
     * El operario confirma la lectura: se cuenta la planta y se guardan las
     * coordenadas GPS. [noTieneEan] true -> ademas se encola la etiqueta a
     * sacar con motivo "Falta etiqueta EAN" (D-241).
     */
    fun confirmarAdicion(noTieneEan: Boolean) {
        val pend = _uiState.value.pendienteConfirmar ?: return
        val par = ambito.value ?: return
        viewModelScope.launch {
            try {
                val st = _uiState.value
                val pos = inventarioRepository.posicionActual()
                val sinEan = pend.sinEan || pend.eanEscaneado.isNullOrBlank() || noTieneEan
                inventarioRepository.registrar(
                    finca = par.first,
                    sector = par.second,
                    producto = pend.producto,
                    eanEscaneado = pend.eanEscaneado,
                    ocrTexto = pend.ocrTexto,
                    fueraSector = pend.esFuera,
                    reetiquetar = pend.esFuera,
                    sinEan = sinEan,
                    labelMotivo = if (sinEan) "Falta etiqueta EAN" else "",
                    modoInventario = st.modo,
                    linealSessionId = if (st.modo == "LINEAL" && st.linealIniciado) st.linealSessionId else "",
                    latitud = pos?.first,
                    longitud = pos?.second
                )
                if (st.modo == "LINEAL" && st.linealIniciado) {
                    _uiState.update { it.copy(linealPlantas = it.linealPlantas + 1) }
                }
                _uiState.update { it.copy(pendienteConfirmar = null) }
                val etiquetaMsg = if (sinEan) " · etiqueta para sacar" else ""
                val fueraMsg = if (pend.esFuera) " · marcada para reetiquetado" else ""
                mensaje("+1 ${pend.producto.reference} (${pend.litrajeDesc})$etiquetaMsg$fueraMsg")
                refrescarServidor()
                if (st.modo != "LINEAL" || !st.linealIniciado) subirBestEffort()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = Errores.traducir(e)) }
            }
        }
    }

    fun cancelarAdicion() {
        _uiState.update { it.copy(pendienteConfirmar = null) }
    }

    // ---- Planta sin identificar (D-240) ----

    fun abrirSinEtiqueta() {
        _uiState.update { it.copy(sinEtiquetaAbierto = true) }
    }

    fun cerrarSinEtiqueta() {
        _uiState.update { it.copy(sinEtiquetaAbierto = false) }
    }

    /** Guarda una incidencia de planta sin identificar con la hipótesis del operario. */
    fun guardarSinEtiqueta(descripcion: String) {
        val par = ambito.value ?: return
        if (descripcion.isBlank()) {
            mensaje("Escribe una descripción o hipótesis de qué planta es")
            return
        }
        viewModelScope.launch {
            try {
                val st = _uiState.value
                val pos = inventarioRepository.posicionActual()
                inventarioRepository.registrarIncidencia(
                    finca = par.first,
                    sector = par.second,
                    descripcion = descripcion.trim(),
                    modoInventario = st.modo,
                    linealSessionId = if (st.modo == "LINEAL" && st.linealIniciado) st.linealSessionId else "",
                    latitud = pos?.first,
                    longitud = pos?.second
                )
                if (st.modo == "LINEAL" && st.linealIniciado) {
                    _uiState.update { it.copy(linealPlantas = it.linealPlantas + 1) }
                }
                cerrarSinEtiqueta()
                mensaje("Incidencia registrada: planta sin identificar")
                refrescarServidor()
                if (st.modo != "LINEAL" || !st.linealIniciado) subirBestEffort()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = Errores.traducir(e)) }
            }
        }
    }

    // ---- Modo lineal por huecos (D-243) ----

    fun cambiarModo(modo: String) {
        if (modo == _uiState.value.modo) return
        // Al cambiar de modo se descarta cualquier lineal a medio hacer.
        _uiState.update {
            it.copy(
                modo = modo,
                linealIniciado = false,
                linealInicio = null,
                linealFin = null,
                linealSessionId = "",
                linealPlantas = 0,
                linealHuecos = 0
            )
        }
        mensaje(if (modo == "LINEAL") "Modo Lineal: inicia el lineal para registrar plantas y huecos" else "Modo Estándar")
    }

    /** Guarda el punto GPS A y abre la sesión del lineal. */
    fun iniciarLineal() {
        viewModelScope.launch {
            val pos = inventarioRepository.posicionActual()
            if (pos == null) {
                mensaje("Activa el GPS para guardar el punto A del lineal")
                return@launch
            }
            val sessionId = java.util.UUID.randomUUID().toString()
            _uiState.update {
                it.copy(
                    linealIniciado = true,
                    linealInicio = pos,
                    linealFin = null,
                    linealSessionId = sessionId,
                    linealPlantas = 0,
                    linealHuecos = 0
                )
            }
            mensaje("Lineal iniciado · punto A guardado")
        }
    }

    /** Registra un espacio vacío (hueco) del lineal; no cuenta como planta. */
    fun registrarHueco() {
        val st = _uiState.value
        if (!st.linealIniciado) return
        val par = ambito.value ?: return
        viewModelScope.launch {
            try {
                val pos = inventarioRepository.posicionActual()
                inventarioRepository.registrarHueco(
                    finca = par.first,
                    sector = par.second,
                    modoInventario = st.modo,
                    linealSessionId = if (st.modo == "LINEAL" && st.linealIniciado) st.linealSessionId else "",
                    latitud = pos?.first,
                    longitud = pos?.second
                )
                _uiState.update { it.copy(linealHuecos = it.linealHuecos + 1) }
                mensaje("+1 Hueco registrado")
            } catch (e: Exception) {
                _uiState.update { it.copy(error = Errores.traducir(e)) }
            }
        }
    }

    /**
     * Guarda el punto GPS B e interpola las posiciones de plantas y huecos
     * registrados entre A y B (D-243). Recién entonces se sube al servidor.
     */
    fun finalizarLineal() {
        val st = _uiState.value
        val inicio = st.linealInicio ?: run {
            mensaje("El lineal no está iniciado")
            return
        }
        val sessionId = st.linealSessionId
        viewModelScope.launch {
            val fin = inventarioRepository.posicionActual()
            if (fin == null) {
                mensaje("Activa el GPS para guardar el punto B del lineal")
                return@launch
            }
            if (sessionId.isNotBlank()) {
                inventarioRepository.asignarPosicionesLinealPorSesion(sessionId, inicio, fin)
            }
            _uiState.update {
                it.copy(
                    linealIniciado = false,
                    linealInicio = null,
                    linealFin = fin,
                    linealSessionId = "",
                    linealPlantas = 0,
                    linealHuecos = 0
                )
            }
            mensaje("Lineal finalizado · posiciones interpoladas")
            subirBestEffort()
        }
    }

    /** D-229: desinventariar (quitar 1 planta contada de esa variante) tras confirmacion. */
    fun desinventariar(ref: String, litraje: String) {
        val par = ambito.value ?: return
        viewModelScope.launch {
            try {
                val ok = inventarioRepository.restar(par.first, par.second, ref, litraje)
                if (!ok) mensaje("No hay conteos que descontar")
                refrescarServidor()
                subirBestEffort()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = Errores.traducir(e)) }
            }
        }
    }

    /** D-229: marca el sector como cerrado (local + servidor best-effort). */
    fun cerrarSector() {
        val finca = _uiState.value.fincaSeleccionada?.finca ?: return
        val sector = _uiState.value.sectorSeleccionado?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(sectorCerrado = true) }
            mensaje("Sector marcado como cerrado")
            runCatching { api.inventarioCerrar(finca, sector) }
        }
    }

    // ---- Sync / informe ----

    fun sincronizarAhora() {
        viewModelScope.launch {
            _uiState.update { it.copy(subiendo = true) }
            try {
                inventarioRepository.uploadPendientes(api)
                refrescarServidor()
                mensaje("Sincronizado")
            } catch (e: Exception) {
                _uiState.update { it.copy(error = Errores.traducir(e)) }
            } finally {
                _uiState.update { it.copy(subiendo = false) }
            }
        }
    }

    private fun subirBestEffort() {
        viewModelScope.launch {
            try {
                inventarioRepository.uploadPendientes(api)
            } catch (_: Exception) {
            }
        }
    }

    /** D-229: borra todos los pistoleos de inventario (local y servidor si se pudo) para empezar de cero. */
    fun borrarRegistros() {
        viewModelScope.launch {
            try {
                inventarioRepository.borrarRegistros(api)
                servidor.value = emptyMap()
                fueraPorSector.value = 0
                resumenServidor.value = emptyMap()
                _uiState.update { it.copy(fueraCount = 0, pendienteConfirmar = null, variantesOcr = emptyList()) }
                mensaje("Registros de inventario borrados")
                refrescarServidor()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = Errores.traducir(e)) }
            }
        }
    }

    fun abrirInforme(context: Context) {
        val url = Constants.REST_BASE_URL.removeSuffix("/api") +
            "/inventario?k=${Constants.INVENTARIO_WEB_TOKEN}"
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    fun limpiarMensaje() {
        _uiState.update { it.copy(mensaje = null, error = null) }
    }

    /** Stock esperado cacheado de toda la finca (totales por sector). */
    fun stockSectores() = inventarioRepository.observeStockTodo()

    /** Descripción legible de un código de litraje (premisa: nunca mostrar códigos). */
    fun litrajeDesc(code: String): String = litrajeDescMap.value[code] ?: code

    /** Descripción legible de un código de sector (premisa: nunca mostrar códigos). */
    fun sectorDesc(code: String): String = sectorDescMap.value[code] ?: code

    private fun mensaje(texto: String) {
        _uiState.update { it.copy(mensaje = texto) }
    }
}
