package com.vivero.pickingve.ui.logistica

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivero.pickingve.data.local.entities.OrderLineEntity
import com.vivero.pickingve.ui.picking.CierreLineaDialog
import com.vivero.pickingve.ui.theme.BrandAmber
import com.vivero.pickingve.ui.theme.BrandRed
import com.vivero.pickingve.ui.theme.MarkedBorderColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaenaDashboardScreen(
    viewModel: FaenaDashboardViewModel,
    onBack: () -> Unit,
    onCambiarModo: () -> Unit = {},
    onOpenPedido: (String) -> Unit,
    onLogout: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var mostrarAyuda by remember { mutableStateOf(false) }
    var mostrarConceder by remember { mutableStateOf(false) }
    var fincaAbierta by remember { mutableStateOf<String?>(null) }
    var cambioHecho by remember { mutableStateOf(false) }
    var cambioPassGuardando by remember { mutableStateOf(false) }
    var cambioPassError by remember { mutableStateOf<String?>(null) }
    var lineaAcopio by remember { mutableStateOf<FaenaLinea?>(null) }
    var cerrandoLinea by remember { mutableStateOf<FaenaLinea?>(null) }
    var acopioGuardando by remember { mutableStateOf(false) }
    var acopioError by remember { mutableStateOf<String?>(null) }
    var cantidadAConfirmar by remember { mutableStateOf<Pair<FaenaLinea, Int>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi faena") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    // D-209: volver al selector de modo sin cerrar sesion.
                    if (!state.esOperario) {
                        IconButton(onClick = onCambiarModo) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = "Cambiar modo")
                        }
                    }
                    IconButton(onClick = { viewModel.refrescarPerfil(); mostrarConceder = true }) {
                        Icon(Icons.Filled.Groups, contentDescription = "Ayuda entre operarios")
                    }
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
        ) {
            CabeceraPerfil(state)

            if (state.ayudaDe != null) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "Modo ayuda · solo ves las líneas que ${state.ayudaDe?.nombre} te ha concedido",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.activarAyuda(null) }) {
                            Text("Salir")
                        }
                    }
                }
            }

            // D-237: filtro por finca y sector de procedencia de planta.
            if (state.fincasDisponibles.isNotEmpty()) {
                FiltroFincaRow(
                    fincas = state.fincasDisponibles,
                    seleccionada = state.fincaFiltro,
                    onSeleccion = { viewModel.filtrarPorFinca(it) }
                )
            }
            if (state.fincaFiltro != null && state.sectoresDisponibles.isNotEmpty()) {
                FiltroSectorRow(
                    sectores = state.sectoresDisponibles,
                    seleccionado = state.sectorFiltro,
                    onSeleccion = { viewModel.filtrarPorSector(it) }
                )
            }

            if (state.dias.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.Agriculture,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (state.esOperario) {
                            "No tienes líneas asignadas hoy.\nHabla con tu encargado si esperabas faena."
                        } else {
                            "No tienes faena pendiente para los próximos días"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    state.dias.forEach { dia ->
                        item(key = "dia-${dia.dia}") {
                            Text(
                                text = dia.etiqueta,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        items(dia.fincas, key = { "${dia.dia}-${it.finca}" }) { finca ->
                            val clave = "${dia.dia}-${finca.finca}"
                            FaenaFincaCard(
                                finca = finca,
                                maquinaria = state.maquinaria,
                                sectoresDesc = state.sectoresDesc,
                                expandida = fincaAbierta == clave,
                                onToggle = {
                                    fincaAbierta = if (fincaAbierta == clave) null else clave
                                },
                                onLineaClick = { linea ->
                                    if (state.esOperario) {
                                         lineaAcopio = linea
                                    } else {
                                        onOpenPedido(linea.orderId)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (mostrarAyuda) {
        AyudaDialog(
            candidatos = state.colegasDisponibles,
            actual = state.ayudaDe,
            onSeleccion = {
                viewModel.activarAyuda(it)
                mostrarAyuda = false
            },
            onDismiss = { mostrarAyuda = false }
        )
    }

    if (mostrarConceder) {
        ConcederAyudaDialog(
            state = state,
            onConceder = { lineas, email ->
                viewModel.concederAyuda(lineas, email) { mostrarConceder = false }
            },
            onVerFaenaDe = { colega ->
                viewModel.activarAyuda(colega)
                mostrarConceder = false
            },
            onDismiss = { mostrarConceder = false }
        )
    }

    if (state.debeCambiarPassword && !cambioHecho) {
        CambioPasswordObligatorioDialog(
            guardando = cambioPassGuardando,
            error = cambioPassError,
            onAceptar = { actual, nueva ->
                cambioPassGuardando = true
                cambioPassError = null
                viewModel.cambiarPassword(actual, nueva) { ok, msg ->
                    cambioPassGuardando = false
                    if (ok) {
                        cambioHecho = true
                    } else {
                        cambioPassError = msg
                    }
                }
            }
        )
    }

    lineaAcopio?.let { linea ->
        AcopioLineaDialog(
            linea = linea,
            sectoresDesc = state.sectoresDesc,
            guardando = acopioGuardando,
            error = acopioError,
            onRegistrar = { qty ->
                cantidadAConfirmar = linea to qty
            },
            onCerrarLinea = {
                cerrandoLinea = linea
                lineaAcopio = null
            },
            onDismiss = { if (!acopioGuardando) lineaAcopio = null }
        )
    }

    cantidadAConfirmar?.let { (linea, cantidad) ->
        AlertDialog(
            onDismissRequest = { if (!acopioGuardando) cantidadAConfirmar = null },
            title = { Text("Confirmar cantidad") },
            text = { Text("¿Estas seguro de que esas son las plantas que has recogido?") },
            confirmButton = {
                Button(
                    enabled = !acopioGuardando,
                    onClick = {
                        acopioGuardando = true
                        acopioError = null
                        viewModel.modificarCantidadAcopiada(linea, cantidad) { ok, msg ->
                            acopioGuardando = false
                            if (ok) {
                                cantidadAConfirmar = null
                                lineaAcopio = null
                            } else {
                                cantidadAConfirmar = null
                                acopioError = msg
                            }
                        }
                    }
                ) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(
                    enabled = !acopioGuardando,
                    onClick = { cantidadAConfirmar = null }
                ) { Text("Cancelar") }
            }
        )
    }

    cerrandoLinea?.let { linea ->
        CierreLineaDialog(
            line = linea.line,
            pendiente = linea.pendiente,
            onConfirmar = { motivo, texto ->
                viewModel.cerrarLineaFaena(linea.line, linea.pendiente, motivo, texto) { ok, _ ->
                    cerrandoLinea = null
                    if (!ok) acopioError = "No se pudo cerrar la línea"
                }
            },
            onDismiss = { cerrandoLinea = null }
        )
    }
}

@Composable
private fun CabeceraPerfil(state: FaenaUiState) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.miNombre.ifBlank { "Operario" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = buildString {
                        append(if (state.esOperario) "Operario de acopio" else "Encargado")
                        if (state.esSuperusuario) append(" · SUPERUSUARIO")
                        if (state.maquinaria.isNotBlank()) append(" · ${state.maquinaria}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Recogidas: ${state.totalRecogidas} / ${state.totalSolicitadas}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FiltroFincaRow(
    fincas: List<String>,
    seleccionada: String?,
    onSeleccion: (String?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        item(key = "todas") {
            FilterChip(
                selected = seleccionada == null,
                onClick = { onSeleccion(null) },
                label = { Text("Todas") }
            )
        }
        items(fincas, key = { it }) { finca ->
            FilterChip(
                selected = seleccionada == finca,
                onClick = { onSeleccion(finca) },
                label = { Text(finca) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FiltroSectorRow(
    sectores: List<String>,
    seleccionado: String?,
    onSeleccion: (String?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        item(key = "todos-sectores") {
            FilterChip(
                selected = seleccionado == null,
                onClick = { onSeleccion(null) },
                label = { Text("Todos los sectores") }
            )
        }
        items(sectores, key = { it }) { sector ->
            FilterChip(
                selected = seleccionado == sector,
                onClick = { onSeleccion(sector) },
                label = { Text(sector) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FaenaFincaCard(
    finca: FaenaFinca,
    maquinaria: String,
    sectoresDesc: Map<String, String>,
    expandida: Boolean,
    onToggle: () -> Unit,
    onLineaClick: (FaenaLinea) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        border = if (expandida) BorderStroke(2.dp, MarkedBorderColor) else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = finca.finca,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = buildString {
                            append("${finca.plantasPendientes} plantas pendientes")
                            append(" · ~${finca.viajesEstimados} ${if (finca.viajesEstimados == 1) "viaje" else "viajes"}")
                            if (maquinaria.isNotBlank()) append(" ($maquinaria)")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (expandida) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expandida) "Contraer" else "Desplegar"
                )
            }

            // D-237: chips resumen de pedidos con planta en esta finca.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                finca.pedidos.forEach { pedido ->
                    val ultra = FaenaDashboardViewModel.esUltraEnPedidoPublic(pedido)
                    Surface(
                        color = if (ultra) BrandRed else MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            if (ultra) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                "Pedido ${pedido.orderId}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (ultra) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = if (ultra) Color.White.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "${pedido.plantasPendientes}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (ultra) Color.White else MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = expandida) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    finca.pedidos.forEach { pedido ->
                        FaenaPedidoCard(
                            pedido = pedido,
                            sectoresDesc = sectoresDesc,
                            onLineaClick = onLineaClick
                        )
                    }
                }
            }
        }
    }
}

/** D-237: cabecera de pedido estilo picking + sus líneas, dentro de una finca de procedencia. */
@Composable
private fun FaenaPedidoCard(
    pedido: FaenaPedido,
    sectoresDesc: Map<String, String>,
    onLineaClick: (FaenaLinea) -> Unit
) {
    var mostrarDetalle by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Pedido ${pedido.orderId}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${pedido.plantasPendientes} plantas",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (pedido.clienteDisplay.isNotBlank()) {
                Text(
                    text = pedido.clienteDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (pedido.fincaCarga.isNotBlank() || pedido.sectorCarga.isNotBlank()) {
                Text(
                    text = "Carga: ${listOf(
                        pedido.fincaCarga.ifBlank { null }?.let { "finca $it" },
                        pedido.sectorCarga.ifBlank { null }?.let { "sector $it" }
                    ).filterNotNull().joinToString(" · ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (pedido.marcaPedido.isNotBlank()) {
                Text(
                    text = "Marca: ${pedido.marcaPedido}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { mostrarDetalle = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📦 Ver pedido completo (${pedido.lineas.size} líneas)")
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            val visibles = pedido.lineas
            visibles.take(3).forEach { faenaLinea ->
                FaenaLineaRow(
                    linea = faenaLinea,
                    sectoresDesc = sectoresDesc,
                    onClick = { onLineaClick(faenaLinea) }
                )
            }
            if (visibles.size > 3) {
                Text(
                    text = "... y ${visibles.size - 3} líneas más",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    if (mostrarDetalle) {
        FaenaPedidoDetailDialog(
            pedido = pedido,
            sectoresDesc = sectoresDesc,
            onLineaClick = onLineaClick,
            onDismiss = { mostrarDetalle = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FaenaPedidoDetailDialog(
    pedido: FaenaPedido,
    sectoresDesc: Map<String, String>,
    onLineaClick: (FaenaLinea) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Pedido ${pedido.orderId}") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar")
                        }
                    }
                )
                // Sticky Header (More visible order info)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Pedido ${pedido.orderId}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${pedido.plantasPendientes} plantas pendientes",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                        if (pedido.clienteDisplay.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = pedido.clienteDisplay,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (pedido.fincaCarga.isNotBlank() || pedido.sectorCarga.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Zona de acopio / Carga: ${listOf(
                                    pedido.fincaCarga.ifBlank { null }?.let { "finca $it" },
                                    pedido.sectorCarga.ifBlank { null }?.let { "sector $it" }
                                ).filterNotNull().joinToString(" · ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (pedido.marcaPedido.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Marca: ${pedido.marcaPedido}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                HorizontalDivider()
                // Scrollable lines list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val pendientes = pedido.lineas.filter { it.pendiente > 0 }
                    val completas = pedido.lineas.filter { it.pendiente <= 0 }

                    items(pendientes, key = { it.line.orderLineId }) { linea ->
                        FaenaLineaRow(
                            linea = linea,
                            sectoresDesc = sectoresDesc,
                            onClick = { onLineaClick(linea) }
                        )
                    }
                    if (completas.isNotEmpty()) {
                        item(key = "header-completadas") {
                            Text(
                                text = "Completadas",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(completas, key = { it.line.orderLineId }) { linea ->
                            FaenaLineaRow(
                                linea = linea,
                                sectoresDesc = sectoresDesc,
                                onClick = { onLineaClick(linea) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FaenaLineaRow(
    linea: FaenaLinea,
    sectoresDesc: Map<String, String>,
    onClick: () -> Unit
) {
    val ultra = FaenaDashboardViewModel.esUltra(linea)
    val completa = linea.pendiente <= 0
    val parcial = !completa && (linea.line.requestedQty - linea.pendiente) > 0
    val sectorNombre = sectoresDesc[linea.line.sectorAcopio] ?: sectoresDesc[linea.line.sector] ?: linea.line.sectorAcopio.ifBlank { linea.line.sectorDesc }
    val fincaProc = linea.line.fincaAcopio.ifBlank { linea.line.fincaArticulo }
    val marcaDistinta = linea.line.marca.isNotBlank() && linea.line.marca != linea.marcaEfectiva
    val cogidas = linea.line.acopiadoOperario
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = when {
                completa -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ultra -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = when {
            completa -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            linea.line.marcado -> BorderStroke(2.dp, MarkedBorderColor)
            else -> null
        }
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = linea.line.productName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$cogidas / ${linea.line.requestedQty} uds",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (completa) MaterialTheme.colorScheme.primary
                    else if (ultra) BrandRed else MaterialTheme.colorScheme.primary
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                if (fincaProc.isNotBlank()) {
                    Text(
                        text = "Finca: $fincaProc",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (sectorNombre.isNotBlank()) {
                    Text(
                        text = "· S: $sectorNombre",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (linea.line.litrajeDesc.isNotBlank()) {
                    Text(
                        text = "· ${linea.line.litrajeDesc}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (linea.line.ubicacion.isNotBlank()) {
                    Text(
                        text = "· ${linea.line.ubicacion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (linea.line.marcado || marcaDistinta) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    if (linea.line.marcado) {
                        Text(
                            text = "MARCADA",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MarkedBorderColor
                        )
                    }
                    if (marcaDistinta) {
                        Text(
                            text = if (linea.line.marcado) "· Marca: ${linea.line.marca}" else "Marca: ${linea.line.marca}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            if (linea.line.observaciones.isNotBlank()) {
                Text(
                    text = "📝 ${linea.line.observaciones}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (linea.line.prioridad.isNotBlank()) {
                PrioBadgeFaena(prioridad = linea.line.prioridad)
            }
            // D-237: estado de cogida TOTAL / PARCIAL.
            when {
                completa -> {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "Completada",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(4.dp))
                        Text(
                            "COGIDA (${cogidas}/${linea.line.requestedQty})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                        }
                    }
                }
                parcial -> {
                    Surface(
                        color = BrandAmber.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Text(
                            "PARCIAL (cogidas ${linea.line.requestedQty - linea.pendiente}/${linea.line.requestedQty})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandAmber,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = {
                    val total = linea.line.requestedQty.coerceAtLeast(1)
                    val hecho = (cogidas.toFloat() / total).coerceIn(0f, 1f)
                    hecho
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
        }
    }
}

/** D-233: PRIORITARIO parpadea en rojo corporativo; NO PRIORITARIO lleva otra etiqueta. */
@Composable
private fun PrioBadgeFaena(prioridad: String) {
    when (prioridad.trim().uppercase()) {
        "PRIORITARIO" -> {
            val transition = rememberInfiniteTransition(label = "prioFaena")
            val alpha by transition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                label = "prioFaenaAlpha"
            )
            Surface(
                color = BrandRed,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .alpha(alpha)
                    .padding(top = 6.dp)
            ) {
                Text(
                    "PRIORITARIO",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        "NO PRIORITARIO" -> {
            Surface(
                color = BrandAmber.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text(
                    "NO PRIORITARIO",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandAmber,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AcopioLineaDialog(
    linea: FaenaLinea,
    sectoresDesc: Map<String, String>,
    guardando: Boolean,
    error: String?,
    onRegistrar: (Int) -> Unit,
    onCerrarLinea: () -> Unit,
    onDismiss: () -> Unit
) {
    var cantidad by remember(linea) { mutableStateOf(linea.line.acopiadoOperario.toString()) }
    val qty = cantidad.toIntOrNull()
    val sectorNombre = sectoresDesc[linea.line.sectorAcopio] ?: linea.line.sectorDesc

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modificar cantidad acopiada") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = linea.line.productName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = buildString {
                        append("Pedido ${linea.orderId}")
                        append(" · ${linea.line.productId}")
                        if (linea.line.litrajeDesc.isNotBlank()) append(" · ${linea.line.litrajeDesc}")
                        if (sectorNombre.isNotBlank()) append(" · $sectorNombre")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Acopiadas ${linea.line.acopiadoOperario} de ${linea.line.requestedQty} plantas · faltan ${linea.pendiente}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                OutlinedTextField(
                    value = cantidad,
                    onValueChange = { cantidad = it.filter(Char::isDigit).take(4) },
                    label = { Text("Plantas recogidas en total") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !guardando,
                    isError = cantidad.isNotBlank() && (qty == null || qty <= 0),
                    modifier = Modifier.fillMaxWidth()
                )
                if (qty != null && qty > linea.pendiente) {
                    Text(
                        text = "⚠ Coges más de lo pendiente (quedan ${linea.pendiente})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(
                    onClick = onCerrarLinea,
                    enabled = !guardando,
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text("No encuentro más plantas · Cerrar línea", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { qty?.let(onRegistrar) },
                enabled = qty != null && qty > 0 && !guardando
            ) {
                if (guardando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (guardando) "Guardando…" else "Guardar cantidad")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !guardando) { Text("Cancelar") }
        }
    )
}

@Composable
private fun AyudaDialog(
    candidatos: List<ColegaFaena>,
    actual: ColegaFaena?,
    onSeleccion: (ColegaFaena?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modo ayuda") },
        text = {
            Column {
                Text(
                    "Solo verás las líneas que el compañero te haya concedido expresamente " +
                        "(se conceden desde este mismo diálogo, sección \"Dar ayuda\").",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                if (actual != null) {
                    TextButton(onClick = { onSeleccion(null) }) {
                        Text("Volver a mi faena")
                    }
                }
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(candidatos, key = { it.email }) { colega ->
                        FilterChip(
                            selected = actual?.email == colega.email,
                            onClick = { onSeleccion(colega) },
                            label = {
                                Text(
                                    buildString {
                                        append(colega.nombre)
                                        append(" · ")
                                        append(colega.rol)
                                        if (colega.familia.isNotBlank()) append(" · ${colega.familia}")
                                    }
                                )
                            },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
                if (candidatos.isEmpty()) {
                    Text(
                        "No hay otros usuarios dados de alta",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConcederAyudaDialog(
    state: FaenaUiState,
    onConceder: (List<Pair<String, String>>, String) -> Unit,
    onVerFaenaDe: (ColegaFaena) -> Unit,
    onDismiss: () -> Unit
) {
    var colegaSeleccionado by remember { mutableStateOf<ColegaFaena?>(null) }
    var seleccionadas by remember { mutableStateOf<Set<String>>(emptySet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ayuda entre operarios") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "1) Elige un compañero y márcale líneas de tu faena para que pueda ayudarte. " +
                        "2) O entra a ver la faena que ya te han concedido.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    state.colegasDisponibles.forEach { colega ->
                        FilterChip(
                            selected = colegaSeleccionado?.email == colega.email,
                            onClick = {
                                colegaSeleccionado = colega
                                seleccionadas = emptySet()
                            },
                            label = { Text(colega.nombre.substringBefore(' ')) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (colegaSeleccionado != null) {
                    Text(
                        "Líneas de tu faena:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    state.dias.forEach { dia ->
                        dia.fincas.forEach { finca ->
                            finca.pedidos.forEach { pedido ->
                                pedido.lineas.forEach { fl ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = fl.line.orderLineId in seleccionadas,
                                            onCheckedChange = { marcado ->
                                                seleccionadas =
                                                    if (marcado) seleccionadas + fl.line.orderLineId
                                                    else seleccionadas - fl.line.orderLineId
                                            }
                                        )
                                        Text(
                                            "${fl.line.productName} (+${fl.pendiente})",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            val colega = colegaSeleccionado ?: return@Button
                            val todas = state.dias
                                .flatMap { it.fincas }
                                .flatMap { it.pedidos }
                                .flatMap { it.lineas }
                            val lineas = seleccionadas.mapNotNull { id ->
                                todas.firstOrNull { it.line.orderLineId == id }
                                    ?.let { it.orderId to id }
                            }
                            if (lineas.isNotEmpty()) {
                                onConceder(lineas, colega.email)
                            }
                        },
                        enabled = seleccionadas.isNotEmpty(),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Text("Conceder ${seleccionadas.size} línea(s)")
                    }
                }
                if (state.ayudaDe != null) {
                    TextButton(onClick = { onVerFaenaDe(state.ayudaDe) }) {
                        Text("Ya me han concedido ayuda: ver mi modo ayuda activo")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun CambioPasswordObligatorioDialog(
    guardando: Boolean,
    error: String?,
    onAceptar: (actual: String, nueva: String) -> Unit
) {
    var actual by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var repetir by remember { mutableStateOf("") }
    var verActual by remember { mutableStateOf(false) }
    var verNueva by remember { mutableStateOf(false) }
    var verRepetir by remember { mutableStateOf(false) }
    val valida = nueva.length >= 4 && nueva == repetir

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Cambia tu contraseña") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Es tu primer acceso o sigues con la contraseña provisional. " +
                        "Elige una contraseña personal (mínimo 4 caracteres).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (error != null) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                OutlinedTextField(
                    value = actual,
                    onValueChange = { actual = it },
                    label = { Text("Contraseña actual/provisional") },
                    visualTransformation = if (verActual) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !guardando,
                    trailingIcon = {
                        IconButton(onClick = { verActual = !verActual }) {
                            Icon(
                                imageVector = if (verActual) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (verActual) "Ocultar contraseña" else "Mostrar contraseña",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
                OutlinedTextField(
                    value = nueva,
                    onValueChange = { nueva = it },
                    label = { Text("Contraseña nueva") },
                    visualTransformation = if (verNueva) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !guardando,
                    trailingIcon = {
                        IconButton(onClick = { verNueva = !verNueva }) {
                            Icon(
                                imageVector = if (verNueva) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (verNueva) "Ocultar contraseña" else "Mostrar contraseña",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
                OutlinedTextField(
                    value = repetir,
                    onValueChange = { repetir = it },
                    label = { Text("Repite la contraseña nueva") },
                    visualTransformation = if (verRepetir) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !guardando,
                    isError = repetir.isNotBlank() && repetir != nueva,
                    trailingIcon = {
                        IconButton(onClick = { verRepetir = !verRepetir }) {
                            Icon(
                                imageVector = if (verRepetir) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (verRepetir) "Ocultar contraseña" else "Mostrar contraseña",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAceptar(actual, nueva) },
                enabled = valida && actual.isNotBlank() && !guardando
            ) {
                if (guardando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (guardando) "Guardando…" else "Guardar")
            }
        }
    )
}
