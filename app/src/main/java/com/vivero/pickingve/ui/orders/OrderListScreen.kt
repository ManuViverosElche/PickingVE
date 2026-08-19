package com.vivero.pickingve.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vivero.pickingve.data.local.dao.OrderWithTotals
import com.vivero.pickingve.ui.picking.ChatDialog
import com.vivero.pickingve.util.formatInstrucciones
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderListScreen(
    viewModel: OrderListViewModel,
    onOrderSelected: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val orders by viewModel.orders.collectAsState()
    val availableDays by viewModel.availableDays.collectAsState()
    val selectedDays by viewModel.selectedDays.collectAsState()
    val assignedFincas by viewModel.assignedFincas.collectAsState()
    val selectedFincas by viewModel.selectedFincas.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()
    val pendingUploadCount by viewModel.pendingUploadCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val syncStarted = remember { mutableStateOf(false) }
    var infoOrder by remember { mutableStateOf<OrderWithTotals?>(null) }
    var chatOrderId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!syncStarted.value) {
            syncStarted.value = true
            viewModel.syncOrders()
        }
    }

    LaunchedEffect(syncState.lastResult, syncState.lastError) {
        val message = syncState.lastResult ?: syncState.lastError
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearSyncMessage()
        }
    }

    LaunchedEffect(uploadState.lastResult, uploadState.lastError) {
        val message = uploadState.lastResult ?: uploadState.lastError
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearUploadMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pedidos") },
                actions = {
                    if (syncState.syncing) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        }
                    } else {
                        IconButton(onClick = { viewModel.syncOrders() }) {
                            Icon(Icons.Filled.Sync, contentDescription = "Sincronizar")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Salir")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (assignedFincas.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(assignedFincas, key = { it }) { finca ->
                        FilterChip(
                            selected = finca in selectedFincas,
                            onClick = { viewModel.toggleFinca(finca) },
                            label = { Text(finca) }
                        )
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableDays, key = { it.toString() }) { day ->
                    FilterChip(
                        selected = day in selectedDays,
                        onClick = { viewModel.toggleDay(day) },
                        label = { Text(dayChipLabel(day)) }
                    )
                }
            }

            if (pendingUploadCount > 0 || uploadState.uploading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (uploadState.uploading) "Subiendo pendientes..." else "$pendingUploadCount registros por subir",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!uploadState.uploading) {
                        TextButton(onClick = { viewModel.uploadNow() }) {
                            Text("Subir ahora")
                        }
                    }
                }
            }

            if (orders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedDays.size == 1 && LocalDate.now() in selectedDays) {
                            "No hay pedidos de carga para hoy.\nToca otro día para ver más pedidos."
                        } else {
                            "No hay pedidos para los días seleccionados.\nSincroniza (↻) o toca otro día."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val grouped = orders.groupBy { it.fechaCarga?.let(::dayLabel) ?: "Sin fecha" }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    grouped.forEach { (day, dayOrders) ->
                        item(key = "day-$day") {
                            Text(
                                text = day,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                            )
                        }
                        items(dayOrders, key = { it.orderId }) { order ->
                            OrderCard(
                                order = order,
                                onClick = { onOrderSelected(order.orderId) },
                                onInfo = { infoOrder = order },
                                onChat = { chatOrderId = order.orderId }
                            )
                        }
                    }
                }
            }
        }
    }

    infoOrder?.let { order ->
        OrderInfoDialog(order = order, onDismiss = { infoOrder = null })
    }
    chatOrderId?.let { id ->
        ChatDialog(pedidoId = id, onDismiss = { chatOrderId = null })
    }
}

private fun dayChipLabel(date: LocalDate): String {
    return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}

private fun dayLabel(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return when (date) {
        LocalDate.now() -> "Hoy · ${date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"
        LocalDate.now().plusDays(1) -> "Mañana · ${date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy", Locale("es")))
    }
}

@Composable
private fun OrderCard(order: OrderWithTotals, onClick: () -> Unit, onInfo: () -> Unit, onChat: () -> Unit) {
    val progress = if (order.totalRequested == 0) 0f
    else order.totalPicked.toFloat() / order.totalRequested
    val pct = (progress * 100).toInt()

    val displayCustomer = if (order.customerFiscal.isNotBlank() && order.customerFiscal != order.customerName) {
        "${order.customerName} · ${order.customerFiscal}"
    } else {
        order.customerName
    }

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pedido ${order.orderId}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (order.modificado) {
                        Text(
                            text = "MODIFICADO · revisa las líneas",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = displayCustomer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (order.marcaPedido.isNotBlank()) {
                        Text(
                            text = "Marca: ${order.marcaPedido}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (order.observaciones.isNotBlank()) {
                    IconButton(onClick = onInfo) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = "Ver observaciones",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                IconButton(onClick = onChat) {
                    Icon(
                        Icons.Filled.ChatBubbleOutline,
                        contentDescription = "Mensajes del pedido",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = " Acopio ${order.totalPicked} / ${order.totalRequested} plantas ($pct%)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (order.fincaCarga.isNotBlank() || order.sectorCarga.isNotBlank()) {
                Text(
                    text = listOf(
                        order.fincaCarga.ifBlank { null }?.let { "Finca de carga: $it" },
                        order.sectorCarga.ifBlank { null }?.let { "Sector de carga: $it" }
                    ).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (order.cargado) {
                Text(
                    text = "✓ CARGADO",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun OrderInfoDialog(order: OrderWithTotals, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pedido ${order.orderId}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoRow(label = "Cliente", value = order.customerName)
                if (order.customerFiscal.isNotBlank() && order.customerFiscal != order.customerName) {
                    InfoRow(label = "Fiscal", value = order.customerFiscal)
                }
                if (order.marcaPedido.isNotBlank()) {
                    InfoRow(label = "Marca", value = order.marcaPedido)
                }
                if (order.fincaCarga.isNotBlank()) {
                    InfoRow(label = "Finca de carga", value = order.fincaCarga)
                }
                if (order.sectorCarga.isNotBlank()) {
                    InfoRow(label = "Sector de carga", value = order.sectorCarga)
                }
                if (order.observaciones.isNotBlank()) {
                    Text(
                        text = "Observaciones",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatInstrucciones(order.observaciones),
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.weight(1f)
        )
    }
}
