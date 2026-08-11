package com.vivero.pickingve.ui.picking

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vivero.pickingve.data.local.entities.LitrajeEntity
import com.vivero.pickingve.data.local.entities.OrderLineEntity
import com.vivero.pickingve.data.local.entities.PickingRecordEntity
import com.vivero.pickingve.ui.theme.DarkOnWarnContainer
import com.vivero.pickingve.ui.theme.DarkMarkedContainer
import com.vivero.pickingve.ui.theme.DarkOnMarkedContainer
import com.vivero.pickingve.ui.theme.DarkWarnContainer
import com.vivero.pickingve.ui.theme.LightMarkedContainer
import com.vivero.pickingve.ui.theme.LightOnMarkedContainer
import com.vivero.pickingve.ui.theme.LightOnWarnContainer
import com.vivero.pickingve.ui.theme.LightWarnContainer
import com.vivero.pickingve.util.formatInstrucciones
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickingScreen(
    viewModel: PickingViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val pendingLabels by viewModel.pendingLabels.collectAsState()
    var showScanner by remember { mutableStateOf(false) }
    var showLabels by remember { mutableStateOf(false) }
    var showSendDialog by remember { mutableStateOf(false) }
    var showOrderInfo by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var showTruckArrival by remember { mutableStateOf(false) }
    var showSobranteConfirm by remember { mutableStateOf(false) }
    var showReopenConfirm by remember { mutableStateOf(false) }
    var manualMarkLine by remember { mutableStateOf<OrderLineEntity?>(null) }
    var unpickLine by remember { mutableStateOf<OrderLineEntity?>(null) }
    var nextPickingNumber by remember { mutableStateOf(1) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = showScanner) { showScanner = false }

    val notificacionesPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificacionesPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(state.selectedOrderId) {
        nextPickingNumber = viewModel.getNextPickingNumber()
    }

    state.lastMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    val cargado = state.order?.cargado == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Pedido ${state.selectedOrderId.orEmpty()}")
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showScanner) showScanner = false else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (!cargado) {
                        if (!state.order?.matriculaCamion.isNullOrBlank()) {
                            IconButton(onClick = { showSobranteConfirm = true }) {
                                Icon(
                                    Icons.Filled.Done,
                                    contentDescription = "Camión terminado (modo sobrante)",
                                    tint = if (state.sobrante) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = { showTruckArrival = true }) {
                            Icon(
                                Icons.Filled.LocalShipping,
                                contentDescription = if (state.order?.matriculaCamion.isNullOrBlank()) {
                                    "El camión ha llegado"
                                } else {
                                    "Cambiar matrículas del camión"
                                },
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    if (state.order?.observaciones?.isNotBlank() == true) {
                        IconButton(onClick = { showOrderInfo = true }) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = "Observaciones del pedido",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!showScanner && !cargado) {
                PickingBottomBar(
                    pendingLabelCount = state.pendingLabelCount,
                    sendingReport = state.sendingReport,
                    onScan = { showScanner = true },
                    onLabels = { showLabels = true },
                    onSend = {
                        scope.launch {
                            nextPickingNumber = viewModel.getCurrentPickingNumber()
                            showSendDialog = true
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (state.sobrante) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "MODO SOBRANTE: Los escaneos restan",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            TextButton(onClick = { viewModel.setSobranteMode(false) }) {
                                Text("Salir", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
                if (state.unpickingMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "MODO DESACOPIO: Pistolea la planta que se devuelve",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            TextButton(onClick = { viewModel.setUnpickingMode(false) }) {
                                Text("Salir", color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                }
                if (cargado) {
                    CargadoSummary(
                        order = state.order,
                        lines = state.lines,
                        onReopen = { showReopenConfirm = true }
                    )
                } else if (showScanner) {
                    CameraScannerScreen(
                        viewModel = viewModel,
                        onClose = { showScanner = false }
                    )
                } else {
                    OrderHeader(
                        order = state.order
                    )
                    OrderLinesList(
                        order = state.order,
                        lines = state.lines,
                        substitutedByLine = state.substitutedByLine,
                        labelsRequestedByLine = state.labelsRequestedByLine,
                        onUnpick = { unpickLine = it },
                        onManualMark = { manualMarkLine = it }
                    )
                }
            }
        }
    )

    state.pendingConfirm?.let { pending ->
        ConfirmPickingDialog(
            pending = pending,
            line = state.lines.firstOrNull { it.orderLineId == pending.orderLineId },
            litrajes = state.litrajes,
            onConfirm = { liters, measure, caliber, needsLabel, labelReason, labelFormat ->
                viewModel.confirmPicking(
                    pickingType = "I",
                    liters = liters,
                    measure = measure,
                    caliber = caliber,
                    needsLabel = needsLabel,
                    labelReason = labelReason,
                    labelFormat = labelFormat
                )
            },
            onDismiss = viewModel::dismissConfirm
        )
    }

    state.pendingLinePick?.let { pick ->
        LinePickDialog(
            pick = pick,
            onPick = viewModel::assignToLine,
            onAmpliacion = viewModel::confirmAmpliacion,
            onDismiss = viewModel::dismissLinePick
        )
    }

    if (showSendDialog) {
        SendPickingDialog(
            nextPickingNumber = nextPickingNumber,
            defaultFinca = state.order?.fincaCarga.orEmpty(),
            defaultZona = state.order?.sectorCarga.orEmpty(),
            defaultMatriculaCamion = state.order?.matriculaCamion.orEmpty(),
            defaultMatriculaRemolque = state.order?.matriculaRemolque.orEmpty(),
            defaultMarcarCargado = state.sobrante,
            onConfirm = { pickingNumber, type, matriculaCamion, matriculaRemolque, pesoCarga, marcarCargado ->
                viewModel.sendTelegramReport(
                    pickingNumber = pickingNumber,
                    pickingType = type,
                    matriculaCamion = matriculaCamion,
                    matriculaRemolque = matriculaRemolque,
                    pesoCarga = pesoCarga,
                    finca = state.order?.fincaCarga,
                    zona = state.order?.sectorCarga
                )
                if (marcarCargado) viewModel.markOrderCargado()
                showSendDialog = false
            },
            onDismiss = { showSendDialog = false }
        )
    }

    if (showLabels) {
        LabelsDialog(
            labels = pendingLabels,
            lines = state.lines,
            history = state.labelsHistory,
            sending = state.sendingLabels,
            onSendToTelegram = viewModel::sendLabelsTelegram,
            onDismiss = { showLabels = false }
        )
    }

    unpickLine?.let { line ->
        UnpickDialog(
            line = line,
            loadRecords = { viewModel.recordsForLine(line.orderLineId) },
            onUnpickRecords = { ids ->
                viewModel.unpickRecords(line, ids)
                unpickLine = null
            },
            onEnableScanUnpick = {
                viewModel.setUnpickingMode(true, line)
                unpickLine = null
            },
            onDismiss = { unpickLine = null }
        )
    }

    if (showReopenConfirm) {
        AlertDialog(
            onDismissRequest = { showReopenConfirm = false },
            title = { Text("Reabrir pedido") },
            text = {
                Text(
                    "El pedido está marcado como CARGADO.\n\n" +
                        "¿Quieres reabrirlo para seguir pistoleando? No se borra ningún registro."
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.reopenOrder()
                    showReopenConfirm = false
                }) {
                    Text("Reabrir pedido")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReopenConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    if (showTruckArrival) {
        TruckArrivalDialog(
            defaultMatriculaCamion = state.order?.matriculaCamion.orEmpty(),
            defaultMatriculaRemolque = state.order?.matriculaRemolque.orEmpty(),
            onConfirm = { matriculaCamion, matriculaRemolque ->
                viewModel.registerTruckArrival(matriculaCamion, matriculaRemolque)
                showTruckArrival = false
            },
            onDismiss = { showTruckArrival = false }
        )
    }

    if (showSobranteConfirm) {
        AlertDialog(
            onDismissRequest = { showSobranteConfirm = false },
            title = { Text("Camión terminado") },
            text = {
                Text("¿Ha terminado la carga del camión?\n\nAl activar el modo sobrante, los escaneos pasarán a DESCONTAR lo que no se ha cargado.")
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setSobranteMode(true)
                    showSobranteConfirm = false
                }) {
                    Text("Activar modo sobrante")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSobranteConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    state.order?.let { order ->
        if (showOrderInfo) {
            AlertDialog(
                onDismissRequest = { showOrderInfo = false },
                title = { Text("Pedido ${order.orderId}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoRow("Cliente", order.customerName)
                        if (order.customerFiscal.isNotBlank() && order.customerFiscal != order.customerName) {
                            InfoRow("Fiscal", order.customerFiscal)
                        }
                        if (order.marcaPedido.isNotBlank()) InfoRow("Marca", order.marcaPedido)
                        if (order.fincaCarga.isNotBlank()) InfoRow("Finca de carga", order.fincaCarga)
                        if (order.sectorCarga.isNotBlank()) InfoRow("Sector de carga", order.sectorCarga)
                        HorizontalDivider()
                        Text(
                            "Observaciones",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            if (order.observaciones.isBlank()) "Sin observaciones"
                            else formatInstrucciones(order.observaciones),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = if (order.observaciones.isBlank()) null else FontStyle.Italic
                        )
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showChat = true }) { Text("Mensajes") }
                        TextButton(onClick = { showOrderInfo = false }) { Text("Cerrar") }
                    }
                }
            )
        }
    }

    if (showChat) {
        ChatDialog(
            pedidoId = state.selectedOrderId.orEmpty(),
            onDismiss = { showChat = false }
        )
    }

    manualMarkLine?.let { line ->
        ManualMarkDialog(
            line = line,
            litrajes = state.litrajes,
            onConfirm = { qty, labelReason, labelFormat ->
                viewModel.confirmManualMark(
                    line,
                    qty,
                    needsLabel = labelReason.isNotBlank() || labelFormat.isNotBlank(),
                    labelReason = labelReason,
                    labelFormat = labelFormat
                )
                manualMarkLine = null
            },
            onDismiss = { manualMarkLine = null }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(
            "$label: ",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic
        )
    }
}

@Composable
private fun PickingBottomBar(
    pendingLabelCount: Int,
    sendingReport: Boolean,
    onScan: () -> Unit,
    onLabels: () -> Unit,
    onSend: () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = false,
            onClick = onScan,
            icon = {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = "Pistolear")
            },
            label = { Text("Pistolear") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onLabels,
            icon = {
                Box {
                    Icon(Icons.Filled.LocalShipping, contentDescription = "Etiquetas")
                    if (pendingLabelCount > 0) {
                        Text(
                            text = pendingLabelCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.align(Alignment.TopEnd)
                        )
                    }
                }
            },
            label = { Text("Etiquetas") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onSend,
            enabled = !sendingReport,
            icon = {
                if (sendingReport) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                }
            },
            label = { Text("Enviar picking") }
        )
    }
}

@Composable
private fun OrderHeader(
    order: com.vivero.pickingve.data.local.entities.OrderEntity?
) {
    if (order == null) return
    val customer = if (order.customerFiscal.isNotBlank() && order.customerFiscal != order.customerName) {
        "${order.customerName} · ${order.customerFiscal}"
    } else {
        order.customerName
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = customer,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (order.cargado) {
                    LineBadge(
                        container = MaterialTheme.colorScheme.primary,
                        content = MaterialTheme.colorScheme.onPrimary,
                        border = null,
                        icon = { Icon(Icons.Filled.CheckCircle, null, Modifier.size(14.dp)) },
                        text = "CARGADO"
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (order.fincaCarga.isNotBlank()) {
                    Text(
                        "Finca: ${order.fincaCarga}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (order.sectorCarga.isNotBlank()) {
                    Text(
                        "Sector: ${order.sectorCarga}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (order.matriculaCamion.isNotBlank()) {
                Text(
                    "Camión: ${order.matriculaCamion}" +
                        if (order.matriculaRemolque.isNotBlank()) " · Remolque: ${order.matriculaRemolque}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (order.marcaPedido.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Text(
                        text = "Marca: ${order.marcaPedido}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CargadoSummary(
    order: com.vivero.pickingve.data.local.entities.OrderEntity?,
    lines: List<OrderLineEntity>,
    onReopen: () -> Unit
) {
    if (order == null) return
    val vigentes = lines.filter { it.vigente }
    val totalPicked = vigentes.sumOf { maxOf(it.pickedQty, it.acopiadoServidor) }
    val totalRequested = vigentes.sumOf { it.requestedQty }
    val customer = if (order.customerFiscal.isNotBlank() && order.customerFiscal != order.customerName) {
        "${order.customerName} · ${order.customerFiscal}"
    } else {
        order.customerName
    }
    val fechaCargado = order.fechaCarga?.let {
        java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(it))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        " Pedido CARGADO",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(customer, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (order.fincaCarga.isNotBlank()) {
                        Text(
                            "Finca: ${order.fincaCarga}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (order.sectorCarga.isNotBlank()) {
                        Text(
                            "Zona: ${order.sectorCarga}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (fechaCargado != null) {
                    Text(
                        "Fecha de carga: $fechaCargado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (order.matriculaCamion.isNotBlank()) {
                    Text(
                        "Camión: ${order.matriculaCamion}" +
                            if (order.matriculaRemolque.isNotBlank()) " · Remolque: ${order.matriculaRemolque}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (order.marcaPedido.isNotBlank()) {
                    Text(
                        "Marca: ${order.marcaPedido}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(
            "Total acopiado: $totalPicked / $totalRequested",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        LinearProgressIndicator(
            progress = {
                if (totalRequested == 0) 0f else totalPicked.toFloat() / totalRequested
            },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Detalle por línea",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(vigentes, key = { it.orderLineId }) { line ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                line.productName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${maxOf(line.pickedQty, line.acopiadoServidor)} / ${line.requestedQty}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            buildString {
                                append(line.productId)
                                if (line.litrajeDesc.isNotBlank()) append(" · ${line.litrajeDesc}")
                                if (line.sectorDesc.isNotBlank()) append(" · ${line.sectorDesc}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Button(
            onClick = onReopen,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reabrir pedido")
        }
    }
}

@Composable
private fun OrderLinesList(
    order: com.vivero.pickingve.data.local.entities.OrderEntity?,
    lines: List<OrderLineEntity>,
    substitutedByLine: Map<String, Int>,
    labelsRequestedByLine: Map<String, Int>,
    onUnpick: (OrderLineEntity) -> Unit,
    onManualMark: (OrderLineEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }

    val filtered = lines.filter { line ->
        query.isBlank() ||
            line.productName.contains(query, ignoreCase = true) ||
            line.productId.contains(query, ignoreCase = true) ||
            line.litrajeDesc.contains(query, ignoreCase = true) ||
            line.sectorDesc.contains(query, ignoreCase = true)
    }
    val pending = filtered.filter { it.vigente && maxOf(it.pickedQty, it.acopiadoServidor) < it.requestedQty }
    val complete = filtered.filter { it.vigente && maxOf(it.pickedQty, it.acopiadoServidor) >= it.requestedQty }
    val noVigentes = filtered.filter { !it.vigente }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Buscar por referencia, planta, litraje o sector") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    Text(
                        "Sin líneas para este filtro",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            items(pending, key = { it.orderLineId }) { line ->
                OrderLineCard(
                    order = order,
                    line = line,
                    substitutedCount = substitutedByLine[line.orderLineId] ?: 0,
                    labelsRequested = labelsRequestedByLine[line.orderLineId] ?: 0,
                    onUnpick = onUnpick,
                    onManualMark = onManualMark
                )
            }
            if (complete.isNotEmpty()) {
                item {
                    Text(
                        "Completadas",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            items(complete, key = { it.orderLineId }) { line ->
                OrderLineCard(
                    order = order,
                    line = line,
                    substitutedCount = substitutedByLine[line.orderLineId] ?: 0,
                    labelsRequested = labelsRequestedByLine[line.orderLineId] ?: 0,
                    onUnpick = onUnpick,
                    onManualMark = onManualMark
                )
            }
            if (noVigentes.isNotEmpty()) {
                item {
                    Text(
                        "Eliminadas del pedido",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            items(noVigentes, key = { it.orderLineId }) { line ->
                OrderLineCard(
                    order = order,
                    line = line,
                    substitutedCount = substitutedByLine[line.orderLineId] ?: 0,
                    labelsRequested = labelsRequestedByLine[line.orderLineId] ?: 0,
                    onUnpick = onUnpick,
                    onManualMark = onManualMark
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderLineCard(
    order: com.vivero.pickingve.data.local.entities.OrderEntity?,
    line: OrderLineEntity,
    substitutedCount: Int,
    labelsRequested: Int,
    onUnpick: (OrderLineEntity) -> Unit,
    onManualMark: (OrderLineEntity) -> Unit
) {
    val shownPicked = maxOf(line.pickedQty, line.acopiadoServidor)
    val remotePicked = (line.acopiadoServidor - line.pickedQty).coerceAtLeast(0)
    val complete = line.vigente && shownPicked >= line.requestedQty
    val overPicked = line.vigente && shownPicked > line.requestedQty
    val marcaDistinta = order?.marcaPedido?.isNotBlank() == true &&
        line.marca.isNotBlank() && line.marca != order.marcaPedido
    val markedContainer = if (isSystemInDarkTheme()) DarkMarkedContainer else LightMarkedContainer

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = line.vigente) { onManualMark(line) },
        colors = CardDefaults.cardColors(
            containerColor = when {
                !line.vigente -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                line.marcado -> markedContainer
                complete -> MaterialTheme.colorScheme.surfaceVariant
                shownPicked > 0 -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    line.productName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (line.vigente) null else TextDecoration.LineThrough,
                    modifier = Modifier.weight(1f)
                )
                if (!line.vigente) {
                    Text(
                        "ELIMINADA DEL PEDIDO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                if (complete) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Completada",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    line.productId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (line.litrajeDesc.isNotBlank()) {
                    Text(
                        "· ${line.litrajeDesc}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (line.sectorDesc.isNotBlank()) {
                    Text(
                        "· ${line.sectorDesc}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (line.marcado) {
                    val onMarkedContainer =
                        if (isSystemInDarkTheme()) DarkOnMarkedContainer else LightOnMarkedContainer
                    Text(
                        "· MARCADA",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = onMarkedContainer
                    )
                }
            }
            if (marcaDistinta) {
                val warnContainer = if (isSystemInDarkTheme()) DarkWarnContainer else LightWarnContainer
                val onWarnContainer = if (isSystemInDarkTheme()) DarkOnWarnContainer else LightOnWarnContainer
                Surface(
                    color = warnContainer,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = onWarnContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            " Marca ${line.marca} distinta a la del pedido (${order?.marcaPedido})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = onWarnContainer
                        )
                    }
                }
            }
            if (overPicked) {
                val warnContainer = if (isSystemInDarkTheme()) DarkWarnContainer else LightWarnContainer
                val onWarnContainer = if (isSystemInDarkTheme()) DarkOnWarnContainer else LightOnWarnContainer
                Surface(
                    color = warnContainer,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = onWarnContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            " Sobreacopio: ${shownPicked - line.requestedQty} más de lo pedido",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = onWarnContainer
                        )
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                if (line.pickedQty > 0) {
                    LineBadge(
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                        border = null,
                        icon = { Icon(Icons.Filled.CheckCircle, null, Modifier.size(14.dp)) },
                        text = "Pistoleada x${line.pickedQty}"
                    )
                }
                if (remotePicked > 0) {
                    LineBadge(
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                        border = null,
                        icon = { Icon(Icons.Filled.LocalShipping, null, Modifier.size(14.dp)) },
                        text = "$remotePicked de otras tabletas"
                    )
                }
                if (substitutedCount > 0) {
                    LineBadge(
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                        border = null,
                        icon = { Icon(Icons.Filled.Warning, null, Modifier.size(14.dp)) },
                        text = "$substitutedCount sustituidas"
                    )
                }
                if (labelsRequested > 0) {
                    LineBadge(
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                        border = null,
                        icon = { Icon(Icons.Filled.LocalShipping, null, Modifier.size(14.dp)) },
                        text = "Etiqueta solicitada x$labelsRequested"
                    )
                }
                if (line.prioridad.isNotBlank()) {
                    PrioBadge(prioridad = line.prioridad)
                }
                if (line.empleado.isNotBlank()) {
                    LineBadge(
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                        border = null,
                        icon = { Icon(Icons.Filled.Person, null, Modifier.size(14.dp)) },
                        text = "Empleado: ${line.empleado}"
                    )
                }
                if (line.observaciones.isNotBlank()) {
                    LineBadge(
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                        border = null,
                        icon = null,
                        text = "Nota: ${line.observaciones}"
                    )
                }
                if (line.accion.isNotBlank()) {
                    LineBadge(
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                        border = null,
                        icon = null,
                        text = "Acción: ${line.accion}"
                    )
                }
                if (line.marca.isNotBlank()) {
                    LineBadge(
                        container = MaterialTheme.colorScheme.surfaceVariant,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                        border = if (marcaDistinta) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null,
                        icon = { Icon(Icons.Filled.Label, null, Modifier.size(14.dp)) },
                        text = "Marca: ${line.marca}"
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "$shownPicked / ${line.requestedQty} · ${
                            if (line.requestedQty == 0) 0
                            else (shownPicked.toFloat() * 100 / line.requestedQty).toInt()
                        }% acopiadas",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (line.requiresMeasure) {
                    Icon(
                        Icons.Filled.Straighten,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                if (line.pickedQty > 0 && line.vigente) {
                    IconButton(onClick = { onUnpick(line) }) {
                        Icon(
                            Icons.Filled.Undo,
                            contentDescription = "Desacopiar",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = {
                    if (line.requestedQty == 0) 0f
                    else shownPicked.toFloat() / line.requestedQty
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrioBadge(prioridad: String) {
    if (esPrioridadDestacada(prioridad)) {
        val transition = rememberInfiniteTransition(label = "prio")
        val alpha by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "prioAlpha"
        )
        LineBadge(
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            border = null,
            icon = { Icon(Icons.Filled.Warning, null, Modifier.size(14.dp)) },
            text = "PRIORITARIO",
            modifier = Modifier.alpha(alpha)
        )
    }
}

private fun esPrioridadDestacada(prioridad: String): Boolean {
    val p = prioridad.uppercase()
    return p.isNotBlank() && p != "NORMAL" && p != "NO PRIORIDAD"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LineBadge(
    container: Color,
    content: Color,
    border: BorderStroke?,
    icon: (@Composable () -> Unit)?,
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = container,
        border = border,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            icon?.let {
                it()
                Spacer(modifier = Modifier.size(2.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LinePickDialog(
    pick: PendingLinePick,
    onPick: (OrderLineEntity) -> Unit,
    onAmpliacion: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = if (pick.isSubstitution) {
        "Sustitución: ${pick.product.reference}"
    } else {
        "¿A qué línea corresponde ${pick.product.reference}?"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pick.isSubstitution) {
                    Text(
                        "Esta referencia NO está en el pedido. Elige la línea a la que sustituye " +
                            "o añádela como ampliación.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pick.candidateLines, key = { it.orderLineId }) { line ->
                        Card(onClick = { onPick(line) }, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(line.productName, style = MaterialTheme.typography.titleSmall)
                                Text(line.productId, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${maxOf(line.pickedQty, line.acopiadoServidor)} / ${line.requestedQty}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (pick.isSubstitution) {
                TextButton(onClick = onAmpliacion) {
                    Text("Ampliación: referencia nueva no pedida")
                }
            }
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendPickingDialog(
    nextPickingNumber: Int,
    defaultFinca: String = "",
    defaultZona: String = "",
    defaultMatriculaCamion: String = "",
    defaultMatriculaRemolque: String = "",
    defaultMarcarCargado: Boolean = false,
    onConfirm: (Int, String, String?, String?, String?, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf(if (defaultMatriculaCamion.isBlank()) "I" else "F") }
    var matriculaCamion by remember { mutableStateOf(defaultMatriculaCamion) }
    var matriculaRemolque by remember { mutableStateOf(defaultMatriculaRemolque) }
    var pesoCarga by remember { mutableStateOf("") }
    var marcarCargado by remember { mutableStateOf(defaultMarcarCargado) }

    val isFinal = type == "F"
    val valid = (!isFinal || matriculaCamion.isNotBlank()) && marcarCargado == isFinal

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enviar picking") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "El parte incluye TODOS los registros del pedido (acumulado).",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Número de revisión: $nextPickingNumber",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = type == "I", onClick = { type = "I" })
                    Text("Inicial (parcial)")
                    RadioButton(selected = type == "F", onClick = { type = "F" })
                    Text("Final (completo)")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                InfoRow("Finca", defaultFinca.ifBlank { "—" })
                InfoRow("Zona", defaultZona.ifBlank { "—" })
                if (isFinal) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    Text(
                        "Final: datos del camión",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = matriculaCamion,
                        onValueChange = { matriculaCamion = it },
                        label = { Text("Matrícula camión") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = matriculaRemolque,
                        onValueChange = { matriculaRemolque = it },
                        label = { Text("Matrícula remolque") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pesoCarga,
                        onValueChange = { pesoCarga = it.filter(Char::isDigit) },
                        label = { Text("Peso de la carga (kg, opcional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = marcarCargado, onCheckedChange = { marcarCargado = it })
                        Text("Marcar pedido como CARGADO")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onConfirm(
                        nextPickingNumber,
                        type,
                        matriculaCamion.ifBlank { null },
                        matriculaRemolque.ifBlank { null },
                        pesoCarga.ifBlank { null },
                        marcarCargado
                    )
                }
            ) {
                Text("Enviar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun LabelsDialog(
    labels: List<PickingRecordEntity>,
    lines: List<OrderLineEntity>,
    history: List<PickingRecordEntity>,
    sending: Boolean,
    onSendToTelegram: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocalShipping, contentDescription = null)
                Text(" Etiquetas a sacar")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (labels.isEmpty()) {
                    Text("No hay plantas pendientes de etiqueta.")
                } else {
                    Text(
                        "Al enviar se manda al encargado un CSV con: referencia, planta, " +
                            "litraje, sector, EAN, cantidad, pedido, cliente, finca, sector de carga.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(labels, key = { it.recordId }) { r ->
                            val line = lines.firstOrNull { it.orderLineId == r.orderLineId }
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(r.actualProductId, style = MaterialTheme.typography.titleSmall)
                                    if (line?.productName?.isNotBlank() == true) {
                                        Text(line.productName, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (line?.litrajeDesc?.isNotBlank() == true) {
                                        Text(
                                            "Litraje: ${line.litrajeDesc}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (line?.sectorDesc?.isNotBlank() == true) {
                                        Text(
                                            "Sector: ${line.sectorDesc}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Text(
                                        "x ${r.batchQty}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
                if (history.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        "Historial de etiquetas solicitadas",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(history, key = { it.recordId }) { r ->
                            val line = lines.firstOrNull { it.orderLineId == r.orderLineId }
                            val whenSent = r.labelSentAt?.let {
                                java.text.DateFormat.getDateTimeInstance(
                                    java.text.DateFormat.SHORT,
                                    java.text.DateFormat.SHORT
                                ).format(java.util.Date(it))
                            } ?: ""
                            Text(
                                "${line?.productName ?: r.actualProductId} · x${r.batchQty}" +
                                    if (whenSent.isNotBlank()) " · enviado $whenSent" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSendToTelegram, enabled = labels.isNotEmpty() && !sending) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Enviar a Telegram")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun UnpickDialog(
    line: OrderLineEntity,
    loadRecords: suspend () -> List<PickingRecordEntity>,
    onUnpickRecords: (List<String>) -> Unit,
    onEnableScanUnpick: () -> Unit,
    onDismiss: () -> Unit
) {
    var records by remember(line.orderLineId) { mutableStateOf<List<PickingRecordEntity>>(emptyList()) }
    var selected by remember(line.orderLineId) { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember(line.orderLineId) { mutableStateOf(true) }

    LaunchedEffect(line.orderLineId) {
        loading = true
        records = loadRecords()
        loading = false
    }

    fun toggle(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }

    val checkedCount = records.count { it.recordId in selected }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Desacopiar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    line.productName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Acopiadas: ${line.pickedQty}. Marca los registros que se devuelven o usa el escaneo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else if (records.isEmpty()) {
                    Text(
                        "No hay registros individuales de esta línea.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${records.size} registros",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = {
                            selected = if (selected.size == records.size) {
                                emptySet()
                            } else {
                                records.map { it.recordId }.toSet()
                            }
                        }) {
                            Text(if (selected.size == records.size) "Quitar selección" else "Marcar todos")
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(records, key = { it.recordId }) { r ->
                            val whenPicked = java.text.DateFormat.getDateTimeInstance(
                                java.text.DateFormat.SHORT,
                                java.text.DateFormat.SHORT
                            ).format(java.util.Date(r.timestamp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { toggle(r.recordId) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = r.recordId in selected,
                                    onCheckedChange = { toggle(r.recordId) }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${r.actualProductId} · x${r.batchQty}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        buildString {
                                            append(whenPicked)
                                            if (r.measure?.isNotBlank() == true) append(" · ${r.measure}cm")
                                            if (r.caliber?.isNotBlank() == true) append(" · cal. ${r.caliber}")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = checkedCount > 0,
                onClick = {
                    onUnpickRecords(records.filter { it.recordId in selected }.map { it.recordId })
                }
            ) {
                Text("Desacopiar $checkedCount")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onEnableScanUnpick) { Text("Escaneo") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

@Composable
private fun TruckArrivalDialog(
    defaultMatriculaCamion: String,
    defaultMatriculaRemolque: String,
    onConfirm: (matriculaCamion: String, matriculaRemolque: String) -> Unit,
    onDismiss: () -> Unit
) {
    var matriculaCamion by remember { mutableStateOf(defaultMatriculaCamion) }
    var matriculaRemolque by remember { mutableStateOf(defaultMatriculaRemolque) }
    var confirmCamion by remember { mutableStateOf("") }
    val isEdit = defaultMatriculaCamion.isNotBlank()
    val doubleCheckOk = !isEdit || confirmCamion.trim() == matriculaCamion.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Cambiar matrículas" else "El camión ha llegado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Apunta la matrícula: aparecerá en el parte final.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = matriculaCamion,
                    onValueChange = { matriculaCamion = it },
                    label = { Text("Matrícula camión") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isEdit) {
                    OutlinedTextField(
                        value = confirmCamion,
                        onValueChange = { confirmCamion = it },
                        label = { Text("Repite la matrícula del camión") },
                        isError = confirmCamion.isNotBlank() && !doubleCheckOk,
                        supportingText = if (confirmCamion.isNotBlank() && !doubleCheckOk) {
                            { Text("No coincide con la matrícula escrita") }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = matriculaRemolque,
                    onValueChange = { matriculaRemolque = it },
                    label = { Text("Matrícula remolque") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = matriculaCamion.isNotBlank() && doubleCheckOk,
                onClick = { onConfirm(matriculaCamion.trim(), matriculaRemolque.trim()) }
            ) {
                Text("Registrar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun ManualMarkDialog(
    line: OrderLineEntity,
    litrajes: List<LitrajeEntity>,
    onConfirm: (qty: Int, labelReason: String, labelFormat: String) -> Unit,
    onDismiss: () -> Unit
) {
    val remaining = (line.requestedQty - line.pickedQty).coerceAtLeast(1)
    var qtyText by remember(line.orderLineId) { mutableStateOf(remaining.toString()) }
    var labelOption by remember(line.orderLineId) { mutableStateOf(1) }
    var labelFormat by remember(line.orderLineId) {
        mutableStateOf(line.litraje.ifBlank { line.litrajeDesc })
    }
    val qty = qtyText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val willOverPick = (line.pickedQty + qty) > line.requestedQty

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Acopio sin escaneo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    line.productName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Pedido: ${line.requestedQty} · Acopiadas actuales: ${line.pickedQty}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (willOverPick) {
                    Text(
                        "⚠ AVISO: Se acopiará más de lo pedido (${line.pickedQty + qty} en total para ${line.requestedQty} pedidas).",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter(Char::isDigit) },
                    label = { Text("Cantidad a acopiar") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LabelOptionSelector(
                    labelOption = labelOption,
                    labelFormat = labelFormat,
                    litrajes = litrajes,
                    onOptionChange = { labelOption = it; if (it != 3) labelFormat = "" },
                    onFormatChange = { labelFormat = it }
                )
            }
        },
        confirmButton = {
            Button(
                enabled = qty > 0 && (labelOption != 3 || labelFormat.isNotBlank()),
                onClick = {
                    onConfirm(
                        qty,
                        when (labelOption) {
                            2 -> "MACETA_ROTA"
                            3 -> "CAMBIO_FORMATO"
                            else -> ""
                        },
                        labelFormat
                    )
                }
            ) {
                Text("Marcar acopiadas")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
