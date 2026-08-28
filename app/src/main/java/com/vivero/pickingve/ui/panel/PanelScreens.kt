package com.vivero.pickingve.ui.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vivero.pickingve.data.remote.ManagerEtiquetasPedido
import com.vivero.pickingve.data.remote.ManagerFecha
import com.vivero.pickingve.data.remote.ManagerHistoricoDetalleResponse
import com.vivero.pickingve.data.remote.ManagerHistoricoPedido
import com.vivero.pickingve.data.remote.PickingApiClient
import com.vivero.pickingve.util.Errores
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * D-213: panel de logistica para SUPERUSUARIO — paridad con las pestañas de
 * lectura del panel web (Historico de Cargas, Carga por Operario, Etiquetas a
 * Sacar) consumiendo los mismos endpoints de manager en /api.
 */

// ---------------- Historico ----------------

data class HistoricoUiState(
    val nivel: Int = 0,
    val fechas: List<ManagerFecha> = emptyList(),
    val fechaSel: String = "",
    val pedidos: List<ManagerHistoricoPedido> = emptyList(),
    val detalle: ManagerHistoricoDetalleResponse? = null,
    val detallePedido: String = "",
    val loading: Boolean = false,
    val error: String? = null
)

class PanelHistoricoViewModel : ViewModel() {
    private val api = PickingApiClient()
    private val _state = MutableStateFlow(HistoricoUiState())
    val state: StateFlow<HistoricoUiState> = _state

    init { cargarFechas() }

    fun cargarFechas() {
        _state.value = _state.value.copy(loading = true, error = null, nivel = 0)
        viewModelScope.launch {
            try {
                val fechas = api.managerHistoricoFechas()
                _state.value = _state.value.copy(fechas = fechas, loading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = Errores.traducir(e))
            }
        }
    }

    fun cargarDia(fecha: String) {
        _state.value = _state.value.copy(loading = true, error = null, nivel = 1, fechaSel = fecha)
        viewModelScope.launch {
            try {
                val dia = api.managerHistoricoDia(fecha)
                _state.value = _state.value.copy(pedidos = dia.pedidos, loading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = Errores.traducir(e))
            }
        }
    }

    fun volver() {
        if (_state.value.nivel == 1) {
            _state.value = _state.value.copy(nivel = 0, pedidos = emptyList())
        }
    }

    fun cargarDetalle(numero: String) {
        _state.value = _state.value.copy(loading = true, error = null, detallePedido = numero)
        viewModelScope.launch {
            try {
                val d = api.managerHistoricoDetalle(numero)
                _state.value = _state.value.copy(detalle = d, loading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = Errores.traducir(e))
            }
        }
    }

    fun cerrarDetalle() {
        _state.value = _state.value.copy(detalle = null, detallePedido = "")
    }

    /** D-217: descarga un informe HTML del pedido y lo entrega para abrirlo. */
    fun descargarInforme(numero: String, tipo: String, onListo: (String) -> Unit) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val ruta = when (tipo) {
                    "control" -> "informe/control/$numero"
                    "detalle" -> "informe/detalle/$numero"
                    else -> "informe/desglose/$numero"
                }
                val html = api.managerInformeHtml(ruta)
                _state.value = _state.value.copy(loading = false)
                onListo(html)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = Errores.traducir(e))
            }
        }
    }
}

// ---------------- Etiquetas a Sacar ----------------

data class EtiquetasUiState(
    val fecha: String = LocalDate.now().toString(),
    val estado: String = "todos",
    val pedidos: List<ManagerEtiquetasPedido> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class PanelEtiquetasViewModel : ViewModel() {
    private val api = PickingApiClient()
    private val _state = MutableStateFlow(EtiquetasUiState())
    val state: StateFlow<EtiquetasUiState> = _state

    init { cargar() }

    fun cargar(fecha: String = _state.value.fecha, estado: String = _state.value.estado) {
        _state.value = _state.value.copy(loading = true, error = null, fecha = fecha, estado = estado)
        viewModelScope.launch {
            try {
                val r = api.managerEtiquetasDia(fecha, estado)
                _state.value = _state.value.copy(pedidos = r.pedidos, loading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = Errores.traducir(e))
            }
        }
    }

    /** D-217: informe HTML de etiquetas del dia. */
    fun descargarInformeDia(onListo: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val html = api.managerInformeHtml(
                    "etiquetas/dia/informe?fecha=${_state.value.fecha}"
                )
                onListo(html)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = Errores.traducir(e))
            }
        }
    }
}

// ---------------- Hub ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelHubScreen(
    onBack: () -> Unit,
    onOpenHistorico: () -> Unit,
    onOpenEtiquetas: () -> Unit,
    onOpenConfig: () -> Unit = {},
    onOpenGestion: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Panel de logística") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Salir")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Todo lo del panel web, en el móvil",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PanelCard(
                titulo = "Histórico de cargas",
                descripcion = "Cargas y envíos por fecha, detalle y carga por operario",
                icono = { Icon(Icons.Filled.LocalShipping, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = onOpenHistorico
            )
            PanelCard(
                titulo = "Etiquetas a sacar",
                descripcion = "Triada referencia · litraje · sector de los pedidos del día",
                icono = { Icon(Icons.Filled.Inventory2, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = onOpenEtiquetas
            )
            PanelCard(
                titulo = "Maquinarias y familias",
                descripcion = "Configuración de tipos de maquinaria y sus familias",
                icono = { Icon(Icons.Filled.Agriculture, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = onOpenConfig
            )
            PanelCard(
                titulo = "Gestión de faena",
                descripcion = "Reparto de líneas y seguimiento de la faena por finca y operario",
                icono = { Icon(Icons.Filled.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = onOpenGestion
            )
        }
    }
}

@Composable
private fun PanelCard(
    titulo: String,
    descripcion: String,
    icono: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(18.dp)
        ) {
            icono()
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------- Historico ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricoPanelScreen(
    viewModel: PanelHistoricoViewModel = viewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.nivel == 0) "Histórico de cargas" else "Cargas · ${state.fechaSel}") },
                navigationIcon = {
                    IconButton(onClick = { if (state.nivel == 1) viewModel.volver() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            }
            state.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
                Button(onClick = viewModel::cargarFechas, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Reintentar")
                }
            }
            when (state.nivel) {
                0 -> LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    if (state.fechas.isEmpty() && !state.loading && state.error == null) {
                        item {
                            Text(
                                "Todavía no hay cargas registradas. Cuando registres la " +
                                    "llegada de un camión o envíes un parte final, el histórico " +
                                    "aparecerá aquí.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    items(state.fechas, key = { it.fecha }) { f ->
                        Card(
                            onClick = { viewModel.cargarDia(f.fecha) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(formatearFechaLarga(f.fecha), fontWeight = FontWeight.Bold)
                                    Text(
                                        "${f.total} pedido(s)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                AssistChip(onClick = {}, enabled = false, label = { Text("${f.enviados} enviados") })
                                Spacer(Modifier.height(0.dp))
                                AssistChip(onClick = {}, enabled = false, label = { Text("${f.cargados} cargados") })
                            }
                        }
                    }
                }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(state.pedidos, key = { it.numero }) { p ->
                        Card(
                            onClick = { viewModel.cargarDetalle(p.numero) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Pedido ${p.numero}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    EstadoChip(p.estado)
                                }
                                Text(
                                    p.cliente,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    listOf(p.finca, p.sector).filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Acopio ${p.totalPistoleado} plantas · ${p.totalEventos} pistoleos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.detalle?.let { detalle ->
        val context = androidx.compose.ui.platform.LocalContext.current
        ModalBottomSheet(onDismissRequest = viewModel::cerrarDetalle) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // D-217: matriculas registradas del pedido.
                if (detalle.matriculas.isNotEmpty()) {
                    Text(
                        "Matrículas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    detalle.matriculas.forEach { m ->
                        Text(
                            "${m.tipo.orEmpty()}: ${m.matricula.orEmpty()}" +
                                (m.muelle?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                // D-217: informes HTML del pedido (paridad con el panel web).
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    TextButton(onClick = {
                        viewModel.descargarInforme(detalle.pedido, "control") { html ->
                            abrirHtml(context, html, "informe_control.html")
                        }
                    }) { Text("Control") }
                    TextButton(onClick = {
                        viewModel.descargarInforme(detalle.pedido, "detalle") { html ->
                            abrirHtml(context, html, "informe_detalle.html")
                        }
                    }) { Text("Detalle") }
                    TextButton(onClick = {
                        viewModel.descargarInforme(detalle.pedido, "desglose") { html ->
                            abrirHtml(context, html, "informe_desglose.html")
                        }
                    }) { Text("Desglose") }
                }
                CargaPorOperarioContenido(detalle)
            }
        }
    }
}

@Composable
private fun EstadoChip(estado: String) {
    val (texto, color) = when (estado) {
        "enviado" -> "ENVIADO" to MaterialTheme.colorScheme.primary
        "cargado" -> "CARGADO" to MaterialTheme.colorScheme.tertiary
        else -> "PENDIENTE" to MaterialTheme.colorScheme.error
    }
    AssistChip(onClick = {}, enabled = false, label = { Text(texto, color = color) })
}

/** D-213: "Carga por Operario" — eventos de pistoleo agrupados por empleado. */
@Composable
private fun CargaPorOperarioContenido(detalle: ManagerHistoricoDetalleResponse) {
    val porOperario = remember(detalle) {
        detalle.registros
            .groupBy { it.empleado.ifBlank { "Sin nombre" } }
            .map { (empleado, regs) ->
                Triple(
                    empleado,
                    regs.sumOf { it.cantidad },
                    regs.maxOfOrNull { it.fechaHora } ?: ""
                )
            }
            .sortedByDescending { it.second }
    }
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            "Carga por operario · Pedido ${detalle.pedido}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        if (porOperario.isEmpty()) {
            Text(
                "Sin pistoleos registrados.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            porOperario.forEach { (empleado, plantas, ultimo) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(empleado, fontWeight = FontWeight.Bold)
                        Text(
                            "$plantas plantas · último: ${ultimo.take(16)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------- Etiquetas a Sacar ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EtiquetasPanelScreen(
    viewModel: PanelEtiquetasViewModel = viewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var fechaTexto by remember(state.fecha) { mutableStateOf(state.fecha) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Etiquetas a sacar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        val context = androidx.compose.ui.platform.LocalContext.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = fechaTexto,
                    onValueChange = { fechaTexto = it },
                    label = { Text("Fecha (AAAA-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.cargar(fechaTexto.trim()) }) {
                    Text("Ver")
                }
            }
            // D-218: filtro de estado + informe del dia (paridad con la web).
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val opciones = listOf(
                    "todos" to "Todos", "activos" to "Activos", "pendientes" to "Pendientes",
                    "cargados" to "Cargados", "enviados" to "Enviados"
                )
                items(opciones.size, key = { opciones[it].first }) { i ->
                    val (valor, etiqueta) = opciones[i]
                    androidx.compose.material3.FilterChip(
                        selected = state.estado == valor,
                        onClick = { viewModel.cargar(estado = valor) },
                        label = { Text(etiqueta) }
                    )
                }
            }
            TextButton(onClick = {
                viewModel.descargarInformeDia { html ->
                    abrirHtml(context, html, "etiquetas_dia.html")
                }
            }) { Text("Informe del día (HTML)") }
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                items(state.pedidos, key = { it.pedido }) { p ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "Pedido ${p.pedido}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                listOf(p.cliente, p.finca).filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            p.etiquetas.forEach { e ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${e.referencia} — ${e.descripcion}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            listOf(e.litraje, e.sector, e.motivo)
                                                .filter { it.isNotBlank() }
                                                .joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        "×${e.cantidad.toInt()}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.pedidos.isEmpty() && !state.loading && state.error == null) {
                    item {
                        Text(
                            "Sin etiquetas pendientes para esta fecha.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------------- Configuracion: maquinarias y familias (D-216) ----------------

data class ConfigUiState(
    val maquinarias: List<com.vivero.pickingve.data.remote.MaquinariaItem> = emptyList(),
    val familias: List<com.vivero.pickingve.data.remote.FamiliaItem> = emptyList(),
    val loading: Boolean = false,
    val guardando: Boolean = false,
    val error: String? = null,
    val mensaje: String? = null
)

class PanelConfigViewModel : ViewModel() {
    private val api = PickingApiClient()
    private val _state = MutableStateFlow(ConfigUiState())
    val state: StateFlow<ConfigUiState> = _state

    init { cargar() }

    fun cargar() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val mq = api.managerMaquinarias()
                val fam = api.managerFamilias()
                _state.value = _state.value.copy(maquinarias = mq, familias = fam, loading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = Errores.traducir(e))
            }
        }
    }

    fun guardarMaquinaria(id: String, nombre: String, descripcion: String, familia: String) {
        if (nombre.isBlank()) return
        _state.value = _state.value.copy(guardando = true, error = null)
        viewModelScope.launch {
            try {
                api.managerGuardarMaquinaria(
                    com.vivero.pickingve.data.remote.MaquinariaBody(
                        id = id, nombre = nombre.trim(), descripcion = descripcion.trim(), familia = familia.trim()
                    )
                )
                _state.value = _state.value.copy(guardando = false, mensaje = "Maquinaria guardada")
                cargar()
            } catch (e: Exception) {
                _state.value = _state.value.copy(guardando = false, error = Errores.traducir(e))
            }
        }
    }

    fun eliminarMaquinaria(id: String) {
        viewModelScope.launch {
            try {
                api.managerEliminarMaquinaria(id)
                _state.value = _state.value.copy(mensaje = "Maquinaria eliminada")
                cargar()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = Errores.traducir(e))
            }
        }
    }

    fun guardarFamilia(id: String, nombre: String, descripcion: String) {
        if (nombre.isBlank()) return
        _state.value = _state.value.copy(guardando = true, error = null)
        viewModelScope.launch {
            try {
                api.managerGuardarFamilia(
                    com.vivero.pickingve.data.remote.MaquinariaBody(
                        id = id, nombre = nombre.trim(), descripcion = descripcion.trim()
                    )
                )
                _state.value = _state.value.copy(guardando = false, mensaje = "Familia guardada")
                cargar()
            } catch (e: Exception) {
                _state.value = _state.value.copy(guardando = false, error = Errores.traducir(e))
            }
        }
    }

    fun eliminarFamilia(id: String) {
        viewModelScope.launch {
            try {
                api.managerEliminarFamilia(id)
                _state.value = _state.value.copy(mensaje = "Familia eliminada")
                cargar()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = Errores.traducir(e))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigPanelScreen(
    viewModel: PanelConfigViewModel = viewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editMaquinaria by remember { mutableStateOf<com.vivero.pickingve.data.remote.MaquinariaItem?>(null) }
    var nuevaMaquinaria by remember { mutableStateOf(false) }
    var editFamilia by remember { mutableStateOf<com.vivero.pickingve.data.remote.FamiliaItem?>(null) }
    var nuevaFamilia by remember { mutableStateOf(false) }
    var confirmarBorrado by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Maquinarias y familias") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.loading) CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            state.mensaje?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Maquinarias",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { nuevaMaquinaria = true }) { Text("+ Nueva") }
                    }
                }
                items(state.maquinarias, key = { it.id }) { mq ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (mq.activo) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mq.nombre, fontWeight = FontWeight.Bold)
                                Text(
                                    listOf(mq.descripcion, mq.familia).filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { editMaquinaria = mq }) { Text("Editar") }
                            TextButton(onClick = { confirmarBorrado = "maquinaria" to mq.id }) {
                                Text("Borrar", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Familias",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { nuevaFamilia = true }) { Text("+ Nueva") }
                    }
                }
                items(state.familias, key = { it.id }) { fam ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(fam.nombre, fontWeight = FontWeight.Bold)
                                if (fam.descripcion.isNotBlank()) {
                                    Text(
                                        fam.descripcion,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TextButton(onClick = { editFamilia = fam }) { Text("Editar") }
                            TextButton(onClick = { confirmarBorrado = "familia" to fam.id }) {
                                Text("Borrar", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (nuevaMaquinaria || editMaquinaria != null) {
        val mq = editMaquinaria
        EditorDialog(
            titulo = if (mq == null) "Nueva maquinaria" else "Editar maquinaria",
            nombreInicial = mq?.nombre.orEmpty(),
            descripcionInicial = mq?.descripcion.orEmpty(),
            extraInicial = mq?.familia.orEmpty(),
            etiquetaExtra = "Familia",
            guardando = state.guardando,
            onGuardar = { nombre, desc, extra ->
                viewModel.guardarMaquinaria(mq?.id.orEmpty(), nombre, desc, extra)
                nuevaMaquinaria = false
                editMaquinaria = null
            },
            onDismiss = { nuevaMaquinaria = false; editMaquinaria = null }
        )
    }
    if (nuevaFamilia || editFamilia != null) {
        val fam = editFamilia
        EditorDialog(
            titulo = if (fam == null) "Nueva familia" else "Editar familia",
            nombreInicial = fam?.nombre.orEmpty(),
            descripcionInicial = fam?.descripcion.orEmpty(),
            extraInicial = "",
            etiquetaExtra = null,
            guardando = state.guardando,
            onGuardar = { nombre, desc, _ ->
                viewModel.guardarFamilia(fam?.id.orEmpty(), nombre, desc)
                nuevaFamilia = false
                editFamilia = null
            },
            onDismiss = { nuevaFamilia = false; editFamilia = null }
        )
    }
    confirmarBorrado?.let { (tipo, id) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmarBorrado = null },
            title = { Text("¿Borrar?") },
            text = { Text("Esta acción elimina el registro definitivamente.") },
            confirmButton = {
                TextButton(onClick = {
                    if (tipo == "maquinaria") viewModel.eliminarMaquinaria(id)
                    else viewModel.eliminarFamilia(id)
                    confirmarBorrado = null
                }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmarBorrado = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun EditorDialog(
    titulo: String,
    nombreInicial: String,
    descripcionInicial: String,
    extraInicial: String,
    etiquetaExtra: String?,
    guardando: Boolean,
    onGuardar: (nombre: String, descripcion: String, extra: String) -> Unit,
    onDismiss: () -> Unit
) {
    var nombre by remember { mutableStateOf(nombreInicial) }
    var descripcion by remember { mutableStateOf(descripcionInicial) }
    var extra by remember { mutableStateOf(extraInicial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Descripción") }
                )
                if (etiquetaExtra != null) {
                    OutlinedTextField(
                        value = extra,
                        onValueChange = { extra = it },
                        label = { Text(etiquetaExtra) },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onGuardar(nombre, descripcion, extra) },
                enabled = nombre.isNotBlank() && !guardando
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun formatearFechaLarga(iso: String): String = try {
    java.time.LocalDate.parse(iso)
        .format(java.time.format.DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy", java.util.Locale("es")))
} catch (e: Exception) {
    iso
}

/** D-217: guarda HTML en cache y lo abre con el navegador (FileProvider). */
fun abrirHtml(context: android.content.Context, html: String, nombre: String) {
    try {
        val f = java.io.File(context.cacheDir, nombre)
        f.writeText(html)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", f
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/html")
            addFlags(
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
