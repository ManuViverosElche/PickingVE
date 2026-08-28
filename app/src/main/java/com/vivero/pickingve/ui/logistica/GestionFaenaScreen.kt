package com.vivero.pickingve.ui.logistica

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivero.pickingve.data.remote.RepartoAsignacionApi
import com.vivero.pickingve.ui.theme.MarkedBorderColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestionFaenaScreen(
    viewModel: GestionFaenaViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var discrepanciaLinea by remember { mutableStateOf<GestionLinea?>(null) }
    var mostrarCamion by remember { mutableStateOf(false) }

    LaunchedEffect(state.mensaje) {
        state.mensaje?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMensaje()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestión de faena") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { mostrarCamion = true }) {
                        Icon(
                            Icons.Filled.LocalShipping,
                            contentDescription = "Camión compartido",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.cambiosPendientes.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${state.cambiosPendientes.size} cambios sin guardar",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Button(
                            onClick = { viewModel.guardar {} },
                            enabled = !state.guardando
                        ) {
                            Text(if (state.guardando) "Guardando…" else "Guardar")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.dias, key = { it.toString() }) { dia ->
                    FilterChip(
                        selected = dia == state.diaSeleccionado,
                        onClick = { viewModel.seleccionarDia(dia) },
                        label = { Text(dia.format(DateTimeFormatter.ofPattern("dd/MM"))) }
                    )
                }
            }
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.soloSinAsignar,
                    onClick = { viewModel.toggleFiltroSinAsignar() },
                    label = { Text("Solo sin asignar") }
                )
            }

            if (state.pedidos.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Sin líneas para este día",
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
                    state.pedidos.forEach { pedido ->
                        item(key = "p-${pedido.orderId}") {
                            Column {
                                Text(
                                    "Pedido ${pedido.orderId} · ${pedido.fincaCarga}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    pedido.cliente,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(pedido.lineas, key = { it.line.orderLineId }) { linea ->
                            GestionLineaCard(
                                linea = linea,
                                operarios = state.operarios,
                                cambioLocal =
                                    state.cambiosPendientes[linea.line.orderLineId],
                                onAsignar = { op -> viewModel.asignar(linea, op) },
                                onReabrir = { viewModel.reabrirLinea(linea) },
                                onDiscrepancia = { discrepanciaLinea = linea }
                            )
                        }
                    }
                }
            }
        }
    }

    discrepanciaLinea?.let { linea ->
        DiscrepanciaDialog(
            linea = linea,
            onEnviar = { declarado, puntado, texto ->
                viewModel.notificarDiscrepancia(linea, declarado, puntado, texto)
                discrepanciaLinea = null
            },
            onDismiss = { discrepanciaLinea = null }
        )
    }

    if (mostrarCamion) {
        CamionCompartidoDialog(
            state = state,
            onCrear = { pedidosConFecha, mc, mr ->
                viewModel.crearCamionCompartido(pedidosConFecha, mc, mr) { mostrarCamion = false }
            },
            onDismiss = { mostrarCamion = false }
        )
    }
}

/** D-190: crear camion compartido con matriculas preasignadas y N pedidos. */
@Composable
private fun CamionCompartidoDialog(
    state: GestionUiState,
    onCrear: (pedidos: List<Pair<String, String>>, mc: String, mr: String) -> Unit,
    onDismiss: () -> Unit
) {
    var mc by remember { mutableStateOf("") }
    var mr by remember { mutableStateOf("") }
    var seleccionados by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Camión compartido") },
        text = {
            Column {
                Text(
                    "Agrupa varios pedidos que cargan en el mismo camión. Si conoces las " +
                        "matrículas, se preinsertarán al encargado para que solo confirme con la foto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = mc,
                    onValueChange = { mc = it.uppercase().take(16) },
                    label = { Text("Matrícula camión (opcional)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = mr,
                    onValueChange = { mr = it.uppercase().take(16) },
                    label = { Text("Matrícula remolque (opcional)") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Text("Pedidos del día:", style = MaterialTheme.typography.labelLarge)
                val grupos = state.pedidos
                LazyColumn(modifier = Modifier.height(240.dp)) {
                    items(grupos, key = { it.orderId }) { pedido ->
                        val udsPend = pedido.lineas.sumOf { it.pendiente }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = pedido.orderId in seleccionados,
                                onCheckedChange = { marcado ->
                                    seleccionados =
                                        if (marcado) seleccionados + pedido.orderId
                                        else seleccionados - pedido.orderId
                                }
                            )
                            Text(
                                "Pedido ${pedido.orderId} · ${pedido.fincaCarga} · $udsPend uds pend.",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val fecha = state.diaSeleccionado ?: java.time.LocalDate.now()
                    onCrear(seleccionados.map { it to "$fecha" }, mc.trim(), mr.trim())
                },
                enabled = seleccionados.isNotEmpty()
            ) { Text("Crear (${seleccionados.size})") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GestionLineaCard(
    linea: GestionLinea,
    operarios: List<OperarioOption>,
    cambioLocal: RepartoAsignacionApi?,
    onAsignar: (OperarioOption?) -> Unit,
    onReabrir: () -> Unit,
    onDiscrepancia: () -> Unit
) {
    val cerrada = linea.line.motivoCierre.isNotBlank()
    val emailEfectivo = cambioLocal?.operarioEmail ?: linea.operarioEmailAsignado
    val nombreEfectivo = cambioLocal?.operarioNombre ?: linea.operarioNombreAsignado
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        border = if (cambioLocal != null) BorderStroke(2.dp, MarkedBorderColor) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${linea.line.productName} (${linea.line.productId})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(
                            "Línea ${linea.line.posicion}",
                            "${linea.pendiente} uds pendientes",
                            if (cerrada) "CERRADA · ${linea.line.motivoCierre}" else null
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (cerrada) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                FilterChip(
                    selected = emailEfectivo.isBlank(),
                    onClick = { onAsignar(null) },
                    label = { Text("Sin asignar") }
                )
                operarios.forEach { op ->
                    FilterChip(
                        selected = op.email.equals(emailEfectivo, ignoreCase = true),
                        onClick = { onAsignar(op) },
                        label = {
                            Text(op.nombre.substringBefore(' ').ifBlank { op.email })
                        }
                    )
                }
            }
            if (nombreEfectivo.isNotBlank()) {
                Text(
                    "Asignada a $nombreEfectivo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                if (cerrada) {
                    TextButton(onClick = onReabrir) { Text("Reabrir línea") }
                }
                TextButton(onClick = onDiscrepancia) { Text("Avisar diferencia") }
            }
        }
    }
}

@Composable
private fun DiscrepanciaDialog(
    linea: GestionLinea,
    onEnviar: (declarado: Int, puntado: Int, texto: String) -> Unit,
    onDismiss: () -> Unit
) {
    var declarado by remember { mutableStateOf(linea.line.acopiadoServidor.toString()) }
    var puntado by remember { mutableStateOf("") }
    var texto by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Avisar diferencia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${linea.line.productName} (${linea.line.productId})",
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = declarado,
                    onValueChange = { declarado = it.filter(Char::isDigit).take(6) },
                    label = { Text("Uds que declaró el operario") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = puntado,
                    onValueChange = { puntado = it.filter(Char::isDigit).take(6) },
                    label = { Text("Uds realmente puntuadas") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it.take(300) },
                    label = { Text("Mensaje (opcional)") },
                    singleLine = false,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onEnviar(
                        declarado.toIntOrNull() ?: 0,
                        puntado.toIntOrNull() ?: 0,
                        texto
                    )
                },
                enabled = puntado.isNotBlank()
            ) { Text("Enviar aviso") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
