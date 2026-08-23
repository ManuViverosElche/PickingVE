package com.vivero.pickingve.ui.logistica

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.vivero.pickingve.data.local.entities.EncargadoEntity
import com.vivero.pickingve.ui.theme.MarkedBorderColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaenaDashboardScreen(
    viewModel: FaenaDashboardViewModel,
    onBack: () -> Unit,
    onOpenPedido: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var mostrarAyuda by remember { mutableStateOf(false) }
    var fincaAbierta by remember { mutableStateOf<String?>(null) }

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
                    IconButton(onClick = { viewModel.refrescarPerfil(); mostrarAyuda = true }) {
                        Icon(Icons.Filled.Groups, contentDescription = "Modo ayuda")
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
                            "Estás viendo la faena de ${state.ayudaDe?.nombre}",
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
                        "No tienes faena pendiente para los próximos días",
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
                                expandida = fincaAbierta == clave,
                                onToggle = {
                                    fincaAbierta = if (fincaAbierta == clave) null else clave
                                },
                                onOpenPedido = onOpenPedido
                            )
                        }
                    }
                }
            }
        }
    }

    if (mostrarAyuda) {
        AyudaDialog(
            candidatos = state.encargadosDisponibles,
            actual = state.ayudaDe,
            onSeleccion = {
                viewModel.activarAyuda(it)
                mostrarAyuda = false
            },
            onDismiss = { mostrarAyuda = false }
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
                    text = if (state.maquinaria.isBlank()) {
                        "Maquinaria sin configurar · viaje estimado ${state.capacidadViaje} plantas"
                    } else {
                        "${state.maquinaria} · ~${state.capacidadViaje} plantas por viaje"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.totalPlantas > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${state.totalPlantas} plantas",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FaenaFincaCard(
    finca: FaenaFinca,
    maquinaria: String,
    expandida: Boolean,
    onToggle: () -> Unit,
    onOpenPedido: (String) -> Unit
) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
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

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                finca.sectores.forEach { sector ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            if (sector.lineas.any { l ->
                                    FaenaDashboardViewModel.esUltra(l)
                                }
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(" ", style = MaterialTheme.typography.labelMedium)
                            }
                            Text(
                                "${sector.sector} · ${sector.plantasPendientes}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = expandida) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                    finca.sectores.forEach { sector ->
                        Text(
                            text = "Sector ${sector.sector}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                        sector.lineas.forEach { faenaLinea ->
                            FaenaLineaRow(
                                linea = faenaLinea,
                                onClick = { onOpenPedido(faenaLinea.orderId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FaenaLineaRow(linea: FaenaLinea, onClick: () -> Unit) {
    val ultra = FaenaDashboardViewModel.esUltra(linea)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (ultra) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (linea.line.marcado) BorderStroke(2.dp, MarkedBorderColor) else null
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = linea.clienteDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Pedido ${linea.orderId}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${linea.line.productName} (${linea.line.productId})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull(
                            linea.line.litrajeDesc.takeIf { it.isNotBlank() },
                            linea.marcaEfectiva.takeIf { it.isNotBlank() }?.let { "Marca $it" },
                            if (linea.line.marcado) "MARCADA" else null
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${linea.pendiente} uds",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (ultra) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            LinearProgressIndicator(
                progress = {
                    val total = linea.line.requestedQty.coerceAtLeast(1)
                    (linea.pendiente.toFloat() / total).coerceIn(0f, 1f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun AyudaDialog(
    candidatos: List<Pair<EncargadoEntity, Int>>,
    actual: EncargadoEntity?,
    onSeleccion: (EncargadoEntity?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modo ayuda") },
        text = {
            Column {
                Text(
                    "Elige a qué compañero vas a ayudar. Verás su faena y lo que acopies se registrará como ayuda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                if (actual != null) {
                    TextButton(onClick = { onSeleccion(null) }) {
                        Text("Volver a mi faena")
                    }
                }
                candidatos.forEach { (enc, lineas) ->
                    FilterChip(
                        selected = actual?.id == enc.id,
                        onClick = { onSeleccion(enc) },
                        label = {
                            Text("${enc.nombre} ($lineas líneas)")
                        },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                if (candidatos.isEmpty()) {
                    Text(
                        "No hay otros compañeros con faena asignada",
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
