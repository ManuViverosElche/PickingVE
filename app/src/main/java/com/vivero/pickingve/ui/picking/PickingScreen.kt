package com.vivero.pickingve.ui.picking

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.vivero.pickingve.data.local.entities.ChatEstadoEntity
import com.vivero.pickingve.data.local.entities.LitrajeEntity
import com.vivero.pickingve.data.local.entities.OrderLineEntity
import com.vivero.pickingve.data.local.entities.PickingRecordEntity
import com.vivero.pickingve.data.local.entities.ProductEntity
import com.vivero.pickingve.data.local.entities.SectorEntity
import com.vivero.pickingve.domain.usecase.ParsePlantPassportUseCase
import com.vivero.pickingve.scanner.OcrReader
import com.vivero.pickingve.ui.theme.DarkOnWarnContainer
import com.vivero.pickingve.ui.theme.DarkPickedContainer
import com.vivero.pickingve.ui.theme.DarkOnPickedContainer
import com.vivero.pickingve.ui.theme.DarkWarnContainer
import com.vivero.pickingve.ui.theme.LightPickedContainer
import com.vivero.pickingve.ui.theme.LightOnPickedContainer
import com.vivero.pickingve.ui.theme.MarkedBorderColor
import com.vivero.pickingve.ui.theme.LightOnWarnContainer
import com.vivero.pickingve.ui.theme.LightWarnContainer
import com.vivero.pickingve.util.formatInstrucciones
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickingScreen(
    viewModel: PickingViewModel,
    onBack: () -> Unit,
    deepLinkLinea: String? = null,
    deepLinkTipo: String? = null,
    deepLinkCambioTipo: String? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val pendingLabels by viewModel.pendingLabels.collectAsState()
    val chatEstados by viewModel.chatEstados.collectAsState()
    val context = LocalContext.current
    var showScanner by remember { mutableStateOf(false) }
    var scannerModo by remember { mutableStateOf<CameraModo?>(null) }
    var showLabels by remember { mutableStateOf(false) }
    var showSendDialog by remember { mutableStateOf(false) }
    var showOrderInfo by remember { mutableStateOf(false) }
    var chatLinea by remember { mutableStateOf<String?>(null) }
    var deepLinkConsumed by remember { mutableStateOf(false) }
    var highlightedLineId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(deepLinkLinea, deepLinkTipo, deepLinkCambioTipo) {
        if (!deepLinkConsumed && deepLinkLinea != null) {
            when (deepLinkTipo) {
                "comentario" -> {
                    chatLinea = deepLinkLinea
                }
                "pedido_modificado" -> {
                    highlightedLineId = deepLinkLinea
                    // Auto-quitar el resaltado después de 8 segundos
                    kotlinx.coroutines.delay(8000)
                    highlightedLineId = null
                }
            }
            deepLinkConsumed = true
            onDeepLinkConsumed()
        }
    }
    var showTruckArrival by remember { mutableStateOf(false) }
    var showSobranteConfirm by remember { mutableStateOf(false) }
    var showReopenConfirm by remember { mutableStateOf(false) }
    var manualMarkLine by remember { mutableStateOf<OrderLineEntity?>(null) }
    var unpickLine by remember { mutableStateOf<OrderLineEntity?>(null) }
    var cerrarLineaDialog by remember { mutableStateOf<OrderLineEntity?>(null) }
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
                    IconButton(onClick = {
                        chatLinea = null
                        viewModel.marcarChatLeido("")
                    }) {
                        Text(
                            "💬",
                            style = MaterialTheme.typography.titleMedium,
                            color = chatColor(chatEstados, "")
                        )
                    }
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
                    onScan = {
                        scannerModo = null
                        showScanner = true
                    },
                    onManualScan = {
                        scannerModo = CameraModo.PASAPORTE
                        showScanner = true
                    },
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
                        compensaciones = state.compensacionesPorLinea,
                        onReopen = { showReopenConfirm = true }
                    )
                } else if (showScanner) {
                    CameraScannerScreen(
                        viewModel = viewModel,
                        onClose = { showScanner = false },
                        modoInicial = scannerModo ?: CameraModo.AMBOS
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
                        chatEstados = chatEstados,
                        compensaciones = state.compensacionesPorLinea,
                        onUnpick = { unpickLine = it },
                        onManualMark = { manualMarkLine = it },
                        onOpenChat = { chatLinea = it.orderLineId },
                        onCerrarLinea = { cerrarLineaDialog = it },
                        highlightedLineId = highlightedLineId
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
            compensaciones = state.compensacionesPorLinea,
            onConfirm = { liters, measure, caliber, needsLabel, labelReason, labelFormat, qty ->
                viewModel.confirmPicking(
                    pickingType = "I",
                    liters = liters,
                    measure = measure,
                    caliber = caliber,
                    needsLabel = needsLabel,
                    labelReason = labelReason,
                    labelFormat = labelFormat,
                    qty = qty
                )
            },
            onDismiss = viewModel::dismissConfirm
        )
    }

    state.pendingLinePick?.let { pick ->
        LinePickDialog(
            pick = pick,
            compensaciones = state.compensacionesPorLinea,
            onPick = viewModel::assignToLine,
            onAmpliacion = viewModel::confirmAmpliacion,
            onDismiss = viewModel::dismissLinePick
        )
    }

    state.pendingSectorWarning?.let { warning ->
        SectorWarningDialog(
            warning = warning,
            onConfirm = viewModel::confirmSectorWarning,
            onChangeLine = viewModel::sectorWarningToLinePick,
            onDismiss = viewModel::dismissSectorWarning
        )
    }

    state.pendingOcrMatch?.let { pending ->
        OcrMatchDialog(
            pending = pending,
            productos = state.availableProducts,
            litrajes = state.litrajes,
            sectores = state.sectores,
            onConfirm = viewModel::confirmOcrMatch,
            onDismiss = viewModel::dismissOcrMatch
        )
    }

    state.pendingUnpickScan?.let { pending ->
        UnpickScanConfirmDialog(
            pending = pending,
            compensaciones = state.compensacionesPorLinea,
            onConfirm = viewModel::confirmUnpickScan,
            onDismiss = viewModel::cancelUnpickScan
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
            onDecrement = viewModel::decrementPendingLabel,
            onRemove = viewModel::removePendingLabel,
            onDismiss = { showLabels = false }
        )
    }

    unpickLine?.let { line ->
        UnpickConfirmDialog(
            line = line,
            compensaciones = state.compensacionesPorLinea,
            onConfirmUnpick = { qty ->
                viewModel.unpickLine(line, qty)
                unpickLine = null
            },
            onEnableScanUnpick = {
                viewModel.setUnpickingMode(true, line)
                unpickLine = null
                scannerModo = null
                showScanner = true
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
            fincaCarga = state.order?.fincaCarga.orEmpty(),
            defaultMatriculaCamion = state.order?.matriculaCamion.orEmpty(),
            defaultMatriculaRemolque = state.order?.matriculaRemolque.orEmpty(),
            defaultMatriculaRemolqueB = state.order?.matriculaRemolqueB.orEmpty(),
            defaultMuelle = state.order?.muelleCarga.orEmpty(),
            onConfirm = { matriculaCamion, matriculaRemolque, matriculaRemolqueB, muelle, fotos, fotoCompartir ->
                viewModel.registerTruckArrival(
                    matriculaCamion,
                    matriculaRemolque,
                    matriculaRemolqueB,
                    muelle,
                    fotos
                )
                showTruckArrival = false
                fotoCompartir?.let { uri ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri("foto_camion", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Compartir foto del camión"))
                }
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
                        TextButton(onClick = {
                            showOrderInfo = false
                            chatLinea = ""
                        }) { Text("Mensajes") }
                        TextButton(onClick = { showOrderInfo = false }) { Text("Cerrar") }
                    }
                }
            )
        }
    }

    chatLinea?.let { linea ->
        ChatDialog(
            pedidoId = state.selectedOrderId.orEmpty(),
            linea = linea.ifBlank { null },
            lineaInfo = if (linea.isBlank()) null else state.lines.firstOrNull { it.orderLineId == linea },
            onDismiss = {
                viewModel.marcarChatLeido(linea)
                chatLinea = null
            }
        )
    }

    manualMarkLine?.let { line ->
        ManualMarkDialog(
            line = line,
            productos = state.availableProducts,
            litrajesAll = state.litrajes,
            sectoresAll = state.sectores,
            compensaciones = state.compensacionesPorLinea,
            onDirecto = {
                viewModel.marcarDirecto(line)
                manualMarkLine = null
            },
            onVariant = { litrajeDesc, sectorDesc ->
                viewModel.marcarVariant(line, litrajeDesc, sectorDesc)
                manualMarkLine = null
            },
            onConfirm = { referencia, litrajeDesc, sectorDesc ->
                viewModel.marcarDesdeEtiqueta(referencia, litrajeDesc, sectorDesc)
                manualMarkLine = null
            },
            onDismiss = { manualMarkLine = null }
        )
    }

    cerrarLineaDialog?.let { line ->
        val pendienteCierre = (line.requestedQty - maxOf(
            line.pickedQty,
            line.acopiadoServidor
        )).coerceAtLeast(0)
        CierreLineaDialog(
            line = line,
            pendiente = pendienteCierre,
            onConfirmar = { motivo, texto ->
                viewModel.cerrarLinea(line, motivo, texto)
                cerrarLineaDialog = null
            },
            onDismiss = { cerrarLineaDialog = null }
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
    onManualScan: () -> Unit,
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
            onClick = onManualScan,
            icon = {
                Icon(Icons.Filled.DocumentScanner, contentDescription = "Etiqueta sin EAN")
            },
            label = { Text("Sin EAN") }
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

/**
 * Nombre de cliente normalizado (D-15X): fiscal - comercial, con el comercial
 * en cursiva. Si solo hay uno, se muestra ese sin duplicar.
 */
@Composable
private fun ClienteNombreTexto(
    fiscal: String,
    comercial: String,
    style: androidx.compose.ui.text.TextStyle,
    fontWeight: FontWeight? = null,
    maxLines: Int = 2,
    modifier: Modifier = Modifier
) {
    val distintos = fiscal.isNotBlank() && comercial.isNotBlank() &&
        !fiscal.equals(comercial, ignoreCase = true)
    val texto = when {
        distintos -> "$fiscal - $comercial"
        comercial.isNotBlank() -> comercial
        else -> fiscal
    }
    Text(
        text = texto,
        style = style,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

private fun motivoCierreEtiqueta(codigo: String): String =
    com.vivero.pickingve.ui.picking.MOTIVOS_CIERRE
        .firstOrNull { it.codigo == codigo }?.etiqueta ?: codigo.replace('_', ' ')

@Composable
private fun OrderHeader(
    order: com.vivero.pickingve.data.local.entities.OrderEntity?
) {
    if (order == null) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClienteNombreTexto(
                    fiscal = order.customerFiscal,
                    comercial = order.customerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
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
    compensaciones: Map<String, Int>,
    onReopen: () -> Unit
) {
    if (order == null) return
    val vigentes = lines.filter { it.vigente && it.requestedQty > 0 }
    val totalPicked = vigentes.sumOf {
        maxOf(it.pickedQty, (it.acopiadoServidor - (compensaciones[it.orderLineId] ?: 0)).coerceAtLeast(0))
    }
    val totalRequested = vigentes.sumOf { it.requestedQty }
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
                ClienteNombreTexto(
                    fiscal = order.customerFiscal,
                    comercial = order.customerName,
                    style = MaterialTheme.typography.bodyMedium
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
                                "${maxOf(line.pickedQty, (line.acopiadoServidor - (compensaciones[line.orderLineId] ?: 0)).coerceAtLeast(0))} / ${line.requestedQty}",
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
private fun chatColor(estados: List<ChatEstadoEntity>, hilo: String): Color {
    val estado = estados.firstOrNull { it.hiloId == hilo }
        ?: return MaterialTheme.colorScheme.onSurfaceVariant
    return if (estado.sinLeer > 0) Color(0xFFF9A825) else MaterialTheme.colorScheme.primary
}

@Composable
private fun OrderLinesList(
    order: com.vivero.pickingve.data.local.entities.OrderEntity?,
    lines: List<OrderLineEntity>,
    substitutedByLine: Map<String, Int>,
    labelsRequestedByLine: Map<String, Int>,
    chatEstados: List<ChatEstadoEntity>,
    compensaciones: Map<String, Int>,
    onUnpick: (OrderLineEntity) -> Unit,
    onManualMark: (OrderLineEntity) -> Unit,
    onOpenChat: (OrderLineEntity) -> Unit,
    onCerrarLinea: (OrderLineEntity) -> Unit,
    highlightedLineId: String? = null
) {
    var query by remember { mutableStateOf("") }

    val filtered = lines.filter { line ->
        line.vigente && line.requestedQty > 0 && (
            query.isBlank() ||
                line.productName.contains(query, ignoreCase = true) ||
                line.productId.contains(query, ignoreCase = true) ||
                line.litrajeDesc.contains(query, ignoreCase = true) ||
                line.sectorDesc.contains(query, ignoreCase = true)
            )
    }

    val shownPicked: (OrderLineEntity) -> Int = { line ->
        val compensado = compensaciones[line.orderLineId] ?: 0
        maxOf(line.pickedQty, (line.acopiadoServidor - compensado).coerceAtLeast(0))
    }
    val isComplete: (OrderLineEntity) -> Boolean = { line ->
        line.vigente && line.requestedQty > 0 && shownPicked(line) >= line.requestedQty
    }
    val pending = filtered.filter { !isComplete(it) && it.motivoCierre.isBlank() }.sortedBy { it.posicion }
    val completed = filtered.filter { isComplete(it) && it.motivoCierre.isBlank() }.sortedBy { it.posicion }
    val cerradas = filtered.filter { it.motivoCierre.isNotBlank() }.sortedBy { it.posicion }

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
                    chatEstados = chatEstados,
                    compensaciones = compensaciones,
                    shownPickedOverride = null,
                    onUnpick = onUnpick,
                    onManualMark = onManualMark,
                    onOpenChat = onOpenChat,
                    onCerrarLinea = onCerrarLinea,
                    isHighlighted = highlightedLineId == line.orderLineId
                )
            }
            if (completed.isNotEmpty()) {
                item(key = "lineas-completas") {
                    Text(
                        "Líneas completas",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                items(completed, key = { it.orderLineId }) { line ->
                    OrderLineCard(
                        order = order,
                        line = line,
                        substitutedCount = substitutedByLine[line.orderLineId] ?: 0,
                        labelsRequested = labelsRequestedByLine[line.orderLineId] ?: 0,
                        chatEstados = chatEstados,
                        compensaciones = compensaciones,
                        shownPickedOverride = null,
                        onUnpick = onUnpick,
                        onManualMark = onManualMark,
                        onOpenChat = onOpenChat,
                        onCerrarLinea = onCerrarLinea,
                        isHighlighted = highlightedLineId == line.orderLineId
                    )
                }
            }
            if (cerradas.isNotEmpty()) {
                item(key = "lineas-cerradas") {
                    Text(
                        "Líneas cerradas",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                items(cerradas, key = { it.orderLineId }) { line ->
                    OrderLineCard(
                        order = order,
                        line = line,
                        substitutedCount = substitutedByLine[line.orderLineId] ?: 0,
                        labelsRequested = labelsRequestedByLine[line.orderLineId] ?: 0,
                        chatEstados = chatEstados,
                        compensaciones = compensaciones,
                        shownPickedOverride = null,
                        onUnpick = onUnpick,
                        onManualMark = onManualMark,
                        onOpenChat = onOpenChat,
                        onCerrarLinea = onCerrarLinea,
                        isHighlighted = highlightedLineId == line.orderLineId
                    )
                }
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
    chatEstados: List<ChatEstadoEntity>,
    compensaciones: Map<String, Int>,
    shownPickedOverride: Int?,
    onUnpick: (OrderLineEntity) -> Unit,
    onManualMark: (OrderLineEntity) -> Unit,
    onOpenChat: (OrderLineEntity) -> Unit,
    onCerrarLinea: (OrderLineEntity) -> Unit,
    isHighlighted: Boolean = false
) {
    val compensado = compensaciones[line.orderLineId] ?: 0
    val shownPicked = shownPickedOverride ?: maxOf(
        line.pickedQty,
        (line.acopiadoServidor - compensado).coerceAtLeast(0)
    )
    val remotePicked = (line.acopiadoServidor - compensado - line.pickedQty).coerceAtLeast(0)
    val complete = line.vigente && line.requestedQty > 0 && shownPicked >= line.requestedQty
    val overPicked = line.vigente && shownPicked > line.requestedQty
    val cerrada = line.motivoCierre.isNotBlank()
    // D-15X: la marca de la línea manda; si está vacía, hereda la del pedido.
    val marcaEfectiva = line.marca.ifBlank { order?.marcaPedido.orEmpty() }
    val marcaDistinta = order?.marcaPedido?.isNotBlank() == true &&
        line.marca.isNotBlank() && line.marca != order.marcaPedido
    val pickedContainer = if (isSystemInDarkTheme()) DarkPickedContainer else LightPickedContainer
    val highlightColor = if (isSystemInDarkTheme()) Color(0xFFFFD54F) else Color(0xFFF9A825) // Amber

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onManualMark(line) },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isHighlighted -> highlightColor.copy(alpha = 0.3f)
                complete -> pickedContainer
                shownPicked > 0 -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = when {
            isHighlighted -> BorderStroke(3.dp, highlightColor)
            line.marcado -> BorderStroke(2.dp, MarkedBorderColor)
            else -> null
        }
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
                if (line.vigente) {
                    IconButton(onClick = { onOpenChat(line) }) {
                        Text(
                            "💬",
                            style = MaterialTheme.typography.titleMedium,
                            color = chatColor(chatEstados, line.orderLineId)
                        )
                    }
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
                    Text(
                        "· MARCADA",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MarkedBorderColor
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
                val pendiente = (line.requestedQty - shownPicked).coerceAtLeast(0)
                if (pendiente > 0) {
                    LineBadge(
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                        border = null,
                        icon = { Icon(Icons.Filled.CheckCircle, null, Modifier.size(14.dp)) },
                        text = "Pendiente $pendiente"
                    )
                }
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
                        icon = { Icon(Icons.AutoMirrored.Filled.Label, null, Modifier.size(14.dp)) },
                        text = "Marca: ${line.marca}"
                    )
                } else if (marcaEfectiva.isNotBlank()) {
                    // D-15X: sin marca de línea se hereda la del pedido (se indica en el badge)
                    LineBadge(
                        container = MaterialTheme.colorScheme.surfaceVariant,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                        border = null,
                        icon = { Icon(Icons.AutoMirrored.Filled.Label, null, Modifier.size(14.dp)) },
                        text = "Marca: $marcaEfectiva (pedido)"
                    )
                }
                if (line.fincaAcopio.isNotBlank() &&
                    !line.fincaAcopio.equals(order?.fincaCarga.orEmpty(), ignoreCase = true)
                ) {
                    LineBadge(
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                        border = null,
                        icon = { Icon(Icons.Filled.LocalShipping, null, Modifier.size(14.dp)) },
                        text = "Recogida: ${line.fincaAcopio}" +
                            if (line.sectorAcopio.isNotBlank()) " · ${line.sectorAcopio}" else ""
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
                    if (cerrada) {
                        Text(
                            text = "CERRADA · " + motivoCierreEtiqueta(line.motivoCierre) +
                                if (line.motivoCierreTexto.isNotBlank()) ": ${line.motivoCierreTexto}" else "",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (line.requiresMeasure) {
                    Icon(
                        Icons.Filled.Straighten,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                if (!cerrada && line.vigente && !complete && line.requestedQty > shownPicked) {
                    TextButton(onClick = { onCerrarLinea(line) }) {
                        Text("Cerrar línea")
                    }
                }
                if (line.pickedQty > 0 && line.vigente) {
                    IconButton(onClick = { onUnpick(line) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
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
    compensaciones: Map<String, Int>,
    onPick: (OrderLineEntity) -> Unit,
    onAmpliacion: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = if (pick.isSubstitution) {
        "Sustitución"
    } else {
        "¿A qué línea corresponde?"
    }
    val product = pick.product
    val productAttrs = listOfNotNull(
        product.litraje.takeIf { it.isNotBlank() }?.let { "Litraje: $it" },
        product.sector.takeIf { it.isNotBlank() }?.let { "Sector: $it" }
    ).joinToString(" · ")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            "Leída: ${product.reference}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            product.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (productAttrs.isNotBlank()) {
                            Text(
                                productAttrs,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
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
                                if (line.litrajeDesc.isNotBlank() || line.sectorDesc.isNotBlank()) {
                                    Text(
                                        text = buildString {
                                            if (line.litrajeDesc.isNotBlank()) append("Litraje: ${line.litrajeDesc}")
                                            if (line.sectorDesc.isNotBlank()) {
                                                if (line.litrajeDesc.isNotBlank()) append(" · ")
                                                append("Sector: ${line.sectorDesc}")
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    "${maxOf(line.pickedQty, (line.acopiadoServidor - (compensaciones[line.orderLineId] ?: 0)).coerceAtLeast(0))} / ${line.requestedQty}",
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

@Composable
private fun SectorWarningDialog(
    warning: SectorWarning,
    onConfirm: () -> Unit,
    onChangeLine: () -> Unit,
    onDismiss: () -> Unit
) {
    val product = warning.product
    val line = warning.line
    val etiquetaAttrs = listOfNotNull(
        product.litraje.takeIf { it.isNotBlank() }?.let { "litraje $it" },
        product.sector.takeIf { it.isNotBlank() }?.let { "sector $it" }
    ).joinToString(" · ")
    val lineaAttrs = listOfNotNull(
        line.litrajeDesc.takeIf { it.isNotBlank() },
        line.sectorDesc.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚠ La etiqueta no coincide con la línea") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Etiqueta escaneada: ${product.reference} · ${product.name}" +
                        (if (etiquetaAttrs.isNotBlank()) " · $etiquetaAttrs" else ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Línea del pedido: ${line.productName}" +
                        (if (lineaAttrs.isNotBlank()) " · $lineaAttrs" else ""),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "El litraje o sector de la etiqueta no coincide con el de la línea. " +
                        "Se registrará en esta línea.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Añadir a esta línea") }
        },
        dismissButton = {
            TextButton(onClick = onChangeLine) { Text("Elegir otra línea") }
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
    onDecrement: (String) -> Unit,
    onRemove: (String) -> Unit,
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
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
                                    IconButton(onClick = { onDecrement(r.recordId) }) {
                                        Icon(Icons.Filled.Remove, contentDescription = "Quitar una etiqueta")
                                    }
                                    IconButton(onClick = { onRemove(r.recordId) }) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Eliminar etiqueta")
                                    }
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
private fun UnpickConfirmDialog(
    line: OrderLineEntity,
    compensaciones: Map<String, Int>,
    onConfirmUnpick: (qty: Int) -> Unit,
    onEnableScanUnpick: () -> Unit,
    onDismiss: () -> Unit
) {
    val ventaDirecta = line.productId.startsWith("9")
    val shownPicked = maxOf(line.pickedQty, (line.acopiadoServidor - (compensaciones[line.orderLineId] ?: 0)).coerceAtLeast(0))
    var qtyText by remember(line.orderLineId) { mutableStateOf(if (ventaDirecta) shownPicked.coerceAtLeast(1).toString() else "1") }
    val qty = if (ventaDirecta) (qtyText.toIntOrNull()?.coerceAtLeast(1) ?: 1) else 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Desacoplar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    line.productName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${line.productId}" +
                        (if (line.litrajeDesc.isNotBlank()) " · ${line.litrajeDesc}" else "") +
                        (if (line.sectorDesc.isNotBlank()) " · ${line.sectorDesc}" else "") +
                        " · Acopiadas: $shownPicked · Pedido: ${line.requestedQty}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Se desacoplará $qty de la línea ${line.posicion}. La cantidad vuelve a pendiente y el registro se elimina.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
                if (ventaDirecta) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.filter(Char::isDigit) },
                        label = { Text("Cantidad a desacopiar") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        "Planta propia: se desacopia de 1 en 1.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "También puedes pistolear la planta que se devuelve con el botón Escaneo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirmUnpick(qty) }) {
                Text("Desacoplar $qty")
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TruckArrivalDialog(
    fincaCarga: String,
    defaultMatriculaCamion: String,
    defaultMatriculaRemolque: String,
    defaultMatriculaRemolqueB: String,
    defaultMuelle: String,
    onConfirm: (
        matriculaCamion: String,
        matriculaRemolque: String,
        matriculaRemolqueB: String,
        muelle: String,
        fotos: Map<String, ByteArray>,
        fotoCompartir: Uri?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var matriculaCamion by remember { mutableStateOf(defaultMatriculaCamion) }
    var matriculaRemolque by remember { mutableStateOf(defaultMatriculaRemolque) }
    var matriculaRemolqueB by remember { mutableStateOf(defaultMatriculaRemolqueB) }
    var muelle by remember { mutableStateOf(defaultMuelle) }
    var confirmCamion by remember { mutableStateOf("") }
    var escaneando by remember { mutableStateOf(false) }
    var fotoCamion by remember { mutableStateOf<Uri?>(null) }
    var fotoRemolqueA by remember { mutableStateOf<Uri?>(null) }
    var fotoRemolqueB by remember { mutableStateOf<Uri?>(null) }
    var fotoTarget by remember { mutableStateOf<String?>(null) }
    val isEdit = defaultMatriculaCamion.isNotBlank()
    val doubleCheckOk = !isEdit || confirmCamion.trim() == matriculaCamion.trim()

    val sugerenciasMuelle = when {
        fincaCarga.contains("FABRICA", ignoreCase = true) -> (1..8).map { "Muelle $it" }
        fincaCarga.contains("BORISA", ignoreCase = true) -> (1..4).map { "Muelle $it" } + "Explanada"
        else -> emptyList()
    }

    fun uriFoto(tipo: String): Uri? = when (tipo) {
        "CAMION" -> fotoCamion
        "REMOLQUE_A" -> fotoRemolqueA
        else -> fotoRemolqueB
    }

    fun setFoto(tipo: String, uri: Uri?) {
        when (tipo) {
            "CAMION" -> fotoCamion = uri
            "REMOLQUE_A" -> fotoRemolqueA = uri
            else -> fotoRemolqueB = uri
        }
    }

    val tomarFoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val target = fotoTarget ?: return@rememberLauncherForActivityResult
        if (ok) {
            setFoto(target, uriFoto(target))
            if (target == "CAMION") {
                scope.launch {
                    escaneando = true
                    try {
                        val bmp = context.contentResolver.openInputStream(uriFoto("CAMION")!!)?.use {
                            BitmapFactory.decodeStream(it)
                        }
                        if (bmp != null) {
                            val texto = OcrReader.readText(bmp)
                            val matriculaRegex = Regex(
                                """(?i)\b(?:[A-Z]{2}\d{4}[A-Z]{2}|\d{4}[A-Z]{3}|[A-Z]\d{4}[A-Z]{2})\b"""
                            )
                            val encontradas = matriculaRegex.findAll(texto.orEmpty())
                                .map { it.value.uppercase() }.toList()
                            if (matriculaCamion.isBlank() && encontradas.isNotEmpty()) {
                                matriculaCamion = encontradas[0]
                            }
                            if (matriculaRemolque.isBlank() && encontradas.size > 1) {
                                matriculaRemolque = encontradas[1]
                            }
                            if (matriculaRemolqueB.isBlank() && encontradas.size > 2) {
                                matriculaRemolqueB = encontradas[2]
                            }
                        }
                    } catch (e: Exception) {
                        // Sin OCR si la foto no se puede leer
                    } finally {
                        escaneando = false
                    }
                }
            }
        }
    }

    fun lanzarCamara(tipo: String) {
        val archivo = File.createTempFile("matricula_${tipo}_", ".jpg", context.cacheDir)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            archivo
        )
        fotoTarget = tipo
        setFoto(tipo, uri)
        tomarFoto.launch(uri)
    }

    fun confirmar() {
        val fotos = buildMap {
            fotoCamion?.let { uriFoto("CAMION") }?.let { u ->
                context.contentResolver.openInputStream(u)?.use { put("CAMION", it.readBytes()) }
            }
            fotoRemolqueA?.let { u ->
                context.contentResolver.openInputStream(u)?.use { put("REMOLQUE_A", it.readBytes()) }
            }
            fotoRemolqueB?.let { u ->
                context.contentResolver.openInputStream(u)?.use { put("REMOLQUE_B", it.readBytes()) }
            }
        }
        onConfirm(
            matriculaCamion.trim(),
            matriculaRemolque.trim(),
            matriculaRemolqueB.trim(),
            muelle.trim(),
            fotos,
            fotoCamion
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Cambiar matrículas" else "El camión ha llegado") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Apunta la matrícula: aparecerá en el parte final.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = matriculaCamion,
                    onValueChange = { matriculaCamion = it },
                    label = { Text("Matrícula camión") },
                    trailingIcon = {
                        if (escaneando) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        } else {
                            IconButton(onClick = { lanzarCamara("CAMION") }) {
                                Icon(Icons.Filled.QrCodeScanner, contentDescription = "Escanear matrícula del camión")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                fotoCamion?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = "Foto del camión",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                    )
                }
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
                    label = { Text("Matrícula remolque 1") },
                    trailingIcon = {
                        if (escaneando) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        } else {
                            IconButton(onClick = { lanzarCamara("REMOLQUE_A") }) {
                                Icon(Icons.Filled.QrCodeScanner, contentDescription = "Escanear matrícula del remolque")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                fotoRemolqueA?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = "Foto del remolque 1",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                    )
                }
                OutlinedTextField(
                    value = matriculaRemolqueB,
                    onValueChange = { matriculaRemolqueB = it },
                    label = { Text("Matrícula remolque 2") },
                    trailingIcon = {
                        if (escaneando) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        } else {
                            IconButton(onClick = { lanzarCamara("REMOLQUE_B") }) {
                                Icon(Icons.Filled.QrCodeScanner, contentDescription = "Escanear matrícula del remolque 2")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                fotoRemolqueB?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = "Foto del remolque 2",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                    )
                }
                OutlinedTextField(
                    value = muelle,
                    onValueChange = { muelle = it },
                    label = { Text("Muelle de carga") },
                    placeholder = { Text(if (sugerenciasMuelle.isEmpty()) "Opcional" else "Elige o escribe") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (sugerenciasMuelle.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sugerenciasMuelle.forEach { sugerencia ->
                            FilterChip(
                                onClick = { muelle = sugerencia },
                                label = { Text(sugerencia) },
                                selected = muelle == sugerencia
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { lanzarCamara("CAMION") },
                        enabled = !escaneando
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                        Text("Foto camión", modifier = Modifier.padding(start = 4.dp))
                    }
                    OutlinedButton(
                        onClick = { lanzarCamara("REMOLQUE_A") },
                        enabled = !escaneando
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                        Text("Remolque 1", modifier = Modifier.padding(start = 4.dp))
                    }
                    OutlinedButton(
                        onClick = { lanzarCamara("REMOLQUE_B") },
                        enabled = !escaneando
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                        Text("Remolque 2", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = matriculaCamion.isNotBlank() && doubleCheckOk && !escaneando,
                onClick = { confirmar() }
            ) {
                Text("Registrar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ManualMarkDialog(
    line: OrderLineEntity,
    productos: List<ProductEntity>,
    litrajesAll: List<LitrajeEntity>,
    sectoresAll: List<SectorEntity>,
    compensaciones: Map<String, Int>,
    onDirecto: () -> Unit,
    onVariant: (litrajeDesc: String?, sectorDesc: String?) -> Unit,
    onConfirm: (referencia: String, litrajeDesc: String?, sectorDesc: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var paso by remember(line.orderLineId) { mutableStateOf(0) }
    var referencia by remember(line.orderLineId) { mutableStateOf("") }
    var litrajeVariant by remember(line.orderLineId) { mutableStateOf(line.litrajeDesc) }
    var sectorVariant by remember(line.orderLineId) { mutableStateOf(line.sectorDesc) }
    var litrajeEtiqueta by remember(line.orderLineId) { mutableStateOf("") }
    var sectorEtiqueta by remember(line.orderLineId) { mutableStateOf("") }

    val parser = remember { ParsePlantPassportUseCase() }
    val productosRef = remember(line.productId, productos) {
        parser.buscarPorReferencia(line.productId, productos)
    }
    val litrajes = remember(productosRef, litrajesAll) {
        litrajeOptionsDe(productosRef, litrajesAll, line)
    }
    val sectores = remember(productosRef, sectoresAll) {
        sectorOptionsDe(productosRef, sectoresAll, line)
    }

    LaunchedEffect(litrajes) {
        if (litrajes.isNotEmpty() && litrajes.none { it.descripcion == litrajeVariant }) litrajeVariant = ""
    }
    LaunchedEffect(sectores) {
        if (sectores.isNotEmpty() && sectores.none { it.descripcion == sectorVariant }) sectorVariant = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (paso) {
                    0 -> "Acopio manual"
                    1 -> "Otro litraje/sector"
                    else -> "Otra referencia (C:)"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Línea ${line.posicion} · ${line.productName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${line.productId}" +
                        (if (line.litrajeDesc.isNotBlank()) " · ${line.litrajeDesc}" else "") +
                        (if (line.sectorDesc.isNotBlank()) " · ${line.sectorDesc}" else "") +
                        " · Pedido: ${line.requestedQty} · Acopiadas: ${maxOf(line.pickedQty, (line.acopiadoServidor - (compensaciones[line.orderLineId] ?: 0)).coerceAtLeast(0))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                when (paso) {
                    0 -> {
                        Text(
                            "1. Elige cómo acopiar",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = onDirecto,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Acopiar ${line.productId}") }
                        Text(
                            "Se acopiará 1 planta de la línea.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { paso = 1 },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Es la misma referencia pero otro litraje/sector") }
                        OutlinedButton(
                            onClick = { paso = 2 },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("La planta tiene otra referencia") }
                    }
                    1 -> {
                        Text(
                            "2. El litraje o sector es diferente del de la línea",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (litrajes.isEmpty() && sectores.isEmpty()) {
                            Text(
                                "Esta referencia no tiene otros litrajes ni sectores en el catálogo.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (litrajes.isNotEmpty()) {
                            SimpleDropdown(
                                label = "Litraje",
                                items = litrajes.map { it.id to it.descripcion },
                                selected = litrajeVariant,
                                onSelected = { litrajeVariant = it }
                            )
                        }
                        if (sectores.isNotEmpty()) {
                            SimpleDropdown(
                                label = "Sector",
                                items = sectores.map { it.id to it.descripcion },
                                selected = sectorVariant,
                                onSelected = { sectorVariant = it }
                            )
                        }
                        Text(
                            "Se gestionará igual que un EAN que no está en el pedido: podrás acopiarlo en esta línea, elegir otra línea o añadirlo como referencia nueva.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        Text(
                            "2. Escribe la referencia de la etiqueta (C:)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        ReferenciaVariantePicker(
                            referencia = referencia,
                            onReferenciaChange = { referencia = it.uppercase() },
                            litrajeDesc = litrajeEtiqueta,
                            onLitrajeChange = { litrajeEtiqueta = it },
                            sectorDesc = sectorEtiqueta,
                            onSectorChange = { sectorEtiqueta = it },
                            productos = productos,
                            litrajesAll = litrajesAll,
                            sectoresAll = sectoresAll
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (paso) {
                0 -> {}
                1 -> Button(
                    enabled = litrajeVariant.isNotBlank() || sectorVariant.isNotBlank(),
                    onClick = {
                        onVariant(litrajeVariant.ifBlank { null }, sectorVariant.ifBlank { null })
                    }
                ) { Text("Marcar acopiada") }
                else -> Button(
                    enabled = referencia.trim().length >= 2,
                    onClick = {
                        onConfirm(referencia, litrajeEtiqueta.ifBlank { null }, sectorEtiqueta.ifBlank { null })
                    }
                ) { Text("Marcar acopiada") }
            }
        },
        dismissButton = {
            if (paso == 0) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            } else {
                TextButton(onClick = { paso = 0 }) { Text("Atrás") }
            }
        }
    )
}

/** Desplegable simple sin buscador: todos los valores ordenados, de menor a mayor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleDropdown(
    label: String,
    items: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val mostrar = items.firstOrNull { it.second == selected }?.let { (id, desc) ->
        if (desc.isBlank() || desc == id) id else "$id · $desc"
    } ?: selected
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = mostrar,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { (id, desc) ->
                DropdownMenuItem(
                    text = { Text(if (desc.isBlank() || desc == id) id else "$id · $desc") },
                    onClick = {
                        onSelected(desc)
                        expanded = false
                    }
                )
            }
            if (items.isEmpty()) {
                Text(
                    "Sin opciones",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/** Referencia (C:) con sugerencias del catálogo + litraje/sector de esa referencia. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReferenciaVariantePicker(
    referencia: String,
    onReferenciaChange: (String) -> Unit,
    litrajeDesc: String,
    onLitrajeChange: (String) -> Unit,
    sectorDesc: String,
    onSectorChange: (String) -> Unit,
    productos: List<ProductEntity>,
    litrajesAll: List<LitrajeEntity>,
    sectoresAll: List<SectorEntity>
) {
    val parser = remember { ParsePlantPassportUseCase() }
    val productosRef = remember(referencia, productos) {
        if (referencia.isBlank()) emptyList()
        else parser.buscarPorReferencia(referencia, productos)
    }
    val litrajes = remember(productosRef, litrajesAll) {
        if (productosRef.isEmpty()) litrajesAll
        else litrajeOptionsDe(productosRef, litrajesAll, null)
    }
    val sectores = remember(productosRef, sectoresAll) {
        if (productosRef.isEmpty()) sectoresAll
        else sectorOptionsDe(productosRef, sectoresAll, null)
    }
    val sugerencias = remember(referencia, productos, productosRef) {
        if (referencia.length < 3 || productosRef.isNotEmpty()) emptyList()
        else {
            val norm = parser.normalizarRef(referencia)
            productos.map { it.reference }.distinct()
                .filter { parser.normalizarRef(it).startsWith(norm) }
                .sorted()
                .take(15)
        }
    }

    LaunchedEffect(productosRef, litrajes) {
        if (litrajes.isNotEmpty() && litrajes.none { it.descripcion == litrajeDesc }) onLitrajeChange("")
    }
    LaunchedEffect(productosRef, sectores) {
        if (sectores.isNotEmpty() && sectores.none { it.descripcion == sectorDesc }) onSectorChange("")
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = referencia,
            onValueChange = onReferenciaChange,
            label = { Text("Referencia (C:)") },
            placeholder = { Text("Ej.: 11125-SU-24") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (sugerencias.isNotEmpty()) {
            Text(
                "Sugerencias:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sugerencias.forEach { sugerencia ->
                    FilterChip(
                        selected = false,
                        onClick = { onReferenciaChange(sugerencia) },
                        label = { Text(sugerencia) }
                    )
                }
            }
        }
        if (referencia.trim().length >= 2 && productosRef.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "La referencia no está en el catálogo: se tratará como sustitución o ampliación.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
        if (litrajes.isNotEmpty()) {
            SimpleDropdown(
                label = "Litraje",
                items = litrajes.map { it.id to it.descripcion },
                selected = litrajeDesc,
                onSelected = onLitrajeChange
            )
        }
        if (sectores.isNotEmpty()) {
            SimpleDropdown(
                label = "Sector",
                items = sectores.map { it.id to it.descripcion },
                selected = sectorDesc,
                onSelected = onSectorChange
            )
        }
    }
}

/** Litrajes del catálogo para una referencia, ordenados de menor a mayor (T.. y sin número al final). */
private fun litrajeOptionsDe(
    productosRef: List<ProductEntity>,
    litrajesAll: List<LitrajeEntity>,
    line: OrderLineEntity?
): List<LitrajeEntity> {
    val ids = productosRef.mapNotNull { it.litraje.takeIf(String::isNotBlank) }.distinct()
    var opciones = litrajesAll.filter { it.id in ids }
    val faltantes = ids.filter { id -> opciones.none { it.id == id } }
        .map { LitrajeEntity(id = it, descripcion = it) }
    opciones = opciones + faltantes
    if (opciones.isEmpty() && line != null && line.litrajeDesc.isNotBlank()) {
        opciones = listOf(LitrajeEntity(id = line.litraje, descripcion = line.litrajeDesc))
    }
    return opciones.sortedWith(compareBy({ litrajeNumero(it.id) }, { it.id }))
}

private fun litrajeNumero(id: String): Double {
    val norm = id.replace(" ", "").replace("L", "", ignoreCase = true)
    Regex("""^(\d+(?:[.,]\d+)?)""").find(norm)?.let {
        return it.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 1_000_000.0
    }
    return if (norm.startsWith("T", ignoreCase = true)) 1_000_000.0 else 1_000_001.0
}

/** Sectores del catálogo para una referencia, ordenados por código. */
private fun sectorOptionsDe(
    productosRef: List<ProductEntity>,
    sectoresAll: List<SectorEntity>,
    line: OrderLineEntity?
): List<SectorEntity> {
    val ids = productosRef.mapNotNull { it.sector.takeIf(String::isNotBlank) }.distinct()
    var opciones = sectoresAll.filter { it.id in ids }
    val faltantes = ids.filter { id -> opciones.none { it.id == id } }
        .map { SectorEntity(id = it, descripcion = it) }
    opciones = opciones + faltantes
    if (opciones.isEmpty() && line != null && line.sectorDesc.isNotBlank()) {
        opciones = listOf(SectorEntity(id = line.sector, descripcion = line.sectorDesc))
    }
    return opciones.sortedBy { it.id }
}

/** El pasaporte sin EAN no se pudo desambiguar solo: el encargado confirma la referencia. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun OcrMatchDialog(
    pending: PendingOcrMatch,
    productos: List<ProductEntity>,
    litrajes: List<LitrajeEntity>,
    sectores: List<SectorEntity>,
    onConfirm: (referencia: String, litrajeDesc: String?, sectorDesc: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var referencia by remember(pending.ocrText) { mutableStateOf(pending.referencia) }
    var litrajeDesc by remember(pending.ocrText) { mutableStateOf(pending.litrajeDesc.orEmpty()) }
    var sectorDesc by remember(pending.ocrText) { mutableStateOf(pending.sectorDesc.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Referencia de la etiqueta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "El pasaporte no tiene EAN. Confirma la referencia leída y elige litraje/sector si aparecen:",
                    style = MaterialTheme.typography.bodyMedium
                )
                ReferenciaVariantePicker(
                    referencia = referencia,
                    onReferenciaChange = { referencia = it.uppercase() },
                    litrajeDesc = litrajeDesc,
                    onLitrajeChange = { litrajeDesc = it },
                    sectorDesc = sectorDesc,
                    onSectorChange = { sectorDesc = it },
                    productos = productos,
                    litrajesAll = litrajes,
                    sectoresAll = sectores
                )
            }
        },
        confirmButton = {
            Button(
                enabled = referencia.trim().length >= 2,
                onClick = { onConfirm(referencia, litrajeDesc.ifBlank { null }, sectorDesc.ifBlank { null }) }
            ) { Text("Marcar acopiada") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/** Confirmación de desacopio por escaneo: igual que al acopiar, se verifica antes. */
@Composable
private fun UnpickScanConfirmDialog(
    pending: PendingUnpickScan,
    compensaciones: Map<String, Int>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val product = pending.product
    val line = pending.line
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Desacopiar por escaneo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    product.reference +
                        (product.litraje.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") +
                        (product.sector.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                Text(
                    "Línea ${line.posicion} · ${line.productId}" +
                        (if (line.litrajeDesc.isNotBlank()) " · ${line.litrajeDesc}" else "") +
                        (if (line.sectorDesc.isNotBlank()) " · ${line.sectorDesc}" else "") +
                        " · Pedido: ${line.requestedQty} · Acopiadas: ${maxOf(line.pickedQty, (line.acopiadoServidor - (compensaciones[line.orderLineId] ?: 0)).coerceAtLeast(0))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Se desacopiará 1 unidad de la planta escaneada.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Desacoplar 1") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
