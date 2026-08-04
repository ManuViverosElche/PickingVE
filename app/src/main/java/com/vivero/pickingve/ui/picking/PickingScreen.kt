package com.vivero.pickingve.ui.picking

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vivero.pickingve.data.local.entities.OrderLineEntity
import com.vivero.pickingve.data.local.entities.PickingRecordEntity
import com.vivero.pickingve.ui.theme.DarkOnWarnContainer
import com.vivero.pickingve.ui.theme.DarkWarnContainer
import com.vivero.pickingve.ui.theme.LightOnWarnContainer
import com.vivero.pickingve.ui.theme.LightWarnContainer
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
    var manualMarkLine by remember { mutableStateOf<OrderLineEntity?>(null) }
    var nextPickingNumber by remember { mutableStateOf(1) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = showScanner) { showScanner = false }

    LaunchedEffect(state.selectedOrderId) {
        nextPickingNumber = viewModel.getNextPickingNumber()
    }

    state.lastMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

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
            if (!showScanner) {
                PickingBottomBar(
                    pendingLabelCount = state.pendingLabelCount,
                    sendingReport = state.sendingReport,
                    onScan = { showScanner = true },
                    onLabels = { showLabels = true },
                    onSend = {
                        scope.launch {
                            nextPickingNumber = viewModel.getNextPickingNumber()
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
                if (showScanner) {
                    CameraScannerScreen(
                        viewModel = viewModel,
                        onClose = { showScanner = false }
                    )
                } else {
                    OrderHeader(order = state.order)
                    OrderLinesList(
                        order = state.order,
                        lines = state.lines,
                        onUnpick = viewModel::unpickLine,
                        onManualMark = { manualMarkLine = it }
                    )
                }
            }
        }
    )

    state.pendingConfirm?.let { pending ->
        val line = state.lines.firstOrNull { it.orderLineId == pending.orderLineId }
        ConfirmPickingDialog(
            pending = pending,
            requiresMeasure = line?.requiresMeasure == true,
            onConfirm = { liters, measure, caliber, needsLabel ->
                viewModel.confirmPicking(
                    pickingType = "I",
                    liters = liters,
                    measure = measure,
                    caliber = caliber,
                    needsLabel = needsLabel
                )
            },
            onDismiss = viewModel::dismissConfirm
        )
    }

    state.pendingLinePick?.let { pick ->
        LinePickDialog(
            pick = pick,
            onPick = viewModel::assignToLine,
            onDismiss = viewModel::dismissLinePick
        )
    }

    if (showSendDialog) {
        SendPickingDialog(
            nextPickingNumber = nextPickingNumber,
            defaultFinca = state.order?.fincaCarga.orEmpty(),
            defaultZona = state.order?.sectorCarga.orEmpty(),
            onConfirm = { pickingNumber, type, matriculaCamion, matriculaRemolque, pesoCarga, finca, zona ->
                viewModel.sendTelegramReport(
                    pickingNumber = pickingNumber,
                    pickingType = type,
                    matriculaCamion = matriculaCamion,
                    matriculaRemolque = matriculaRemolque,
                    pesoCarga = pesoCarga,
                    finca = finca,
                    zona = zona
                )
                showSendDialog = false
            },
            onDismiss = { showSendDialog = false }
        )
    }

    if (showLabels) {
        LabelsDialog(
            labels = pendingLabels,
            lines = state.lines,
            onDismiss = { showLabels = false }
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
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            order.observaciones.ifBlank { "Sin observaciones" },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showOrderInfo = false }) { Text("Cerrar") }
                }
            )
        }
    }

    manualMarkLine?.let { line ->
        ManualMarkDialog(
            line = line,
            onConfirm = { qty, exactMatch, hasLabel ->
                viewModel.confirmManualMark(line, qty, exactMatch, hasLabel)
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
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
private fun OrderHeader(order: com.vivero.pickingve.data.local.entities.OrderEntity?) {
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
            Text(
                text = customer,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
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
private fun OrderLinesList(
    order: com.vivero.pickingve.data.local.entities.OrderEntity?,
    lines: List<OrderLineEntity>,
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
    val pending = filtered.filter { it.pickedQty < it.requestedQty }
    val complete = filtered.filter { it.pickedQty >= it.requestedQty }

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
    onUnpick: (OrderLineEntity) -> Unit,
    onManualMark: (OrderLineEntity) -> Unit
) {
    val complete = line.pickedQty >= line.requestedQty
    val marcaDistinta = order?.marcaPedido?.isNotBlank() == true &&
        line.marca.isNotBlank() && line.marca != order.marcaPedido

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                complete -> MaterialTheme.colorScheme.surfaceVariant
                line.pickedQty > 0 -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
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
                    modifier = Modifier.weight(1f)
                )
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
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
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${line.pickedQty} / ${line.requestedQty} · ${
                            if (line.requestedQty == 0) 0
                            else (line.pickedQty.toFloat() * 100 / line.requestedQty).toInt()
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
                if (!complete && line.pickedQty < line.requestedQty) {
                    TextButton(
                        onClick = { onManualMark(line) },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            "Sin etiqueta",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (line.pickedQty > 0) {
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
                    else line.pickedQty.toFloat() / line.requestedQty
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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pick.candidateLines, key = { it.orderLineId }) { line ->
                    Card(onClick = { onPick(line) }, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(line.productName, style = MaterialTheme.typography.titleSmall)
                            Text(line.productId, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${line.pickedQty} / ${line.requestedQty}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
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
    onConfirm: (Int, String, String?, String?, String?, String?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var pickingNumber by remember { mutableStateOf(nextPickingNumber.toString()) }
    var type by remember { mutableStateOf("I") }
    var truckPresent by remember { mutableStateOf(true) }
    var matriculaCamion by remember { mutableStateOf("") }
    var matriculaRemolque by remember { mutableStateOf("") }
    var pesoCarga by remember { mutableStateOf("") }
    var finca by remember { mutableStateOf(defaultFinca) }
    var zona by remember { mutableStateOf(defaultZona) }

    val isFinal = type == "F"
    val valid = pickingNumber.toIntOrNull() != null &&
        (!isFinal || (matriculaCamion.isNotBlank() && pesoCarga.isNotBlank() && truckPresent))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enviar picking") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pickingNumber,
                    onValueChange = { pickingNumber = it.filter(Char::isDigit) },
                    label = { Text("Número de picking") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = type == "I", onClick = { type = "I" })
                    Text("Inicial (parcial)")
                    RadioButton(selected = type == "F", onClick = { type = "F" })
                    Text("Final (completo)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = truckPresent, onCheckedChange = { truckPresent = it })
                    Text(if (truckPresent) "El camión está presente" else "El camión NO está presente")
                }
                if (!truckPresent) {
                    Text(
                        "El camión no está: se enviará como picking parcial y los " +
                            "datos del camión se piden en el último parcial (final)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = finca,
                    onValueChange = { finca = it },
                    label = { Text("Finca") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = zona,
                    onValueChange = { zona = it },
                    label = { Text("Zona") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isFinal) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "Final: datos obligatorios del camión",
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
                        onValueChange = { pesoCarga = it },
                        label = { Text("Peso de la carga") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onConfirm(
                        pickingNumber.toIntOrNull() ?: 1,
                        type,
                        matriculaCamion.ifBlank { null },
                        matriculaRemolque.ifBlank { null },
                        pesoCarga.ifBlank { null },
                        finca.ifBlank { null },
                        zona.ifBlank { null }
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
            if (labels.isEmpty()) {
                Text("No hay plantas pendientes de etiqueta.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(labels, key = { it.recordId }) { r ->
                        val line = lines.firstOrNull { it.orderLineId == r.orderLineId }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(r.actualProductId, style = MaterialTheme.typography.titleSmall)
                                if (r.liters != null) {
                                    Text("Litraje: ${r.liters} L", style = MaterialTheme.typography.bodySmall)
                                }
                                if (line?.sector?.isNotBlank() == true) {
                                    Text("Sector: ${line.sector}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("x ${r.batchQty}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun ManualMarkDialog(
    line: OrderLineEntity,
    onConfirm: (qty: Int, exactMatch: Boolean, hasLabel: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val remaining = (line.requestedQty - line.pickedQty).coerceAtLeast(1)
    var qtyText by remember(line.orderLineId) { mutableStateOf(remaining.toString()) }
    var exactMatch by remember(line.orderLineId) { mutableStateOf(true) }
    var hasLabel by remember(line.orderLineId) {
        mutableStateOf(!line.productId.startsWith("9"))
    }
    val qty = qtyText.toIntOrNull()?.coerceIn(1, remaining) ?: remaining

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
                    line.productId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (line.productId.startsWith("9")) {
                    Text(
                        "Venta directa: probablemente no lleve nuestra etiqueta " +
                            "(sí la del vendedor).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = exactMatch, onCheckedChange = { exactMatch = it })
                    Text("Es exactamente esta planta", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "¿Lleva etiqueta?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = hasLabel, onClick = { hasLabel = true })
                    Text("Sí, lleva etiqueta", style = MaterialTheme.typography.bodyMedium)
                    RadioButton(selected = !hasLabel, onClick = { hasLabel = false })
                    Text("No lleva etiqueta", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter(Char::isDigit) },
                    label = { Text("Cantidad a acopiar (máx. $remaining)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = qty > 0,
                onClick = { onConfirm(qty, exactMatch, hasLabel) }
            ) {
                Text("Marcar acopiadas")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
