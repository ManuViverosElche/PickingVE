package com.vivero.pickingve.ui.inventario

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vivero.pickingve.data.local.entities.ProductEntity
import com.vivero.pickingve.ui.theme.BrandGreen
import com.vivero.pickingve.ui.theme.MarkedBorderColor

/**
 * Pantalla de pistoleo de inventario (D-219): lista esperado vs contado del
 * sector, camara para EAN y OCR solo al pulsar el boton (D-221), conteo manual,
 * modal de planta fuera de sector (D-220) y acceso al informe (D-222).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvPantallaScreen(
    viewModel: InvViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showScanner by remember { mutableStateOf(false) }
    var desinventariarLinea by remember { mutableStateOf<InvLineaEstado?>(null) }
    var confirmarCerrarSector by remember { mutableStateOf(false) }
    var busquedaPlanta by remember { mutableStateOf("") }
    val lineasFiltradas = remember(state.lineas, busquedaPlanta) {
        if (busquedaPlanta.isBlank()) state.lineas
        else state.lineas.filter {
            it.ref.contains(busquedaPlanta, ignoreCase = true) ||
                it.litrajeDesc.contains(busquedaPlanta, ignoreCase = true) ||
                it.nombre.contains(busquedaPlanta, ignoreCase = true)
        }
    }

    BackHandler(enabled = showScanner) { showScanner = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.fincaSeleccionada?.finca.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Sector ${state.sectorSeleccionado?.descripcion.orEmpty()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    Icon(
                        if (state.gpsActivo) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                        contentDescription = if (state.gpsActivo) "GPS activo" else "GPS sin permiso",
                        tint = if (state.gpsActivo) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    IconButton(onClick = { viewModel.sincronizarAhora() }) {
                        if (state.subiendo) {
                            CircularProgressIndicator(modifier = Modifier.height(18.dp))
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Subir registros")
                        }
                    }
                    IconButton(onClick = { viewModel.abrirInforme(context) }) {
                        Icon(Icons.Default.Description, contentDescription = "Informe")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Salir")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, modifier = Modifier.navigationBarsPadding()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showScanner = true },
                            modifier = Modifier.weight(2f)
                        ) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = null)
                            Text("  Pistolear", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { viewModel.abrirSinEtiqueta() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("Sin etiqueta", maxLines = 1)
                        }
                    }
                    // D-243: controles de pie del modo lineal (se repiten en cabecera
                    // el contador; aquí la acción rápida de hueco y el fin del lineal).
                    if (state.modo == "LINEAL") {
                        Spacer(Modifier.height(8.dp))
                        if (!state.linealIniciado) {
                            Button(
                                onClick = { viewModel.iniciarLineal() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = state.gpsActivo
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Text("  Iniciar Lineal (punto GPS A)", maxLines = 1)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.registrarHueco() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Text("+1 Hueco", maxLines = 1)
                                }
                                Button(
                                    onClick = { viewModel.finalizarLineal() },
                                    modifier = Modifier.weight(1f),
                                    enabled = state.gpsActivo
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Text("Finalizar Lineal", maxLines = 1)
                                }
                            }
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
                .padding(horizontal = 16.dp)
        ) {
            // Cabecera resumen del sector
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${state.totalContado} / ${state.totalEsperado}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        if (state.fueraCount > 0) {
                            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                                Text(
                                    "${state.fueraCount} fuera de sector",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        if (state.pendientesSubir > 0) {
                            Spacer(Modifier.padding(2.dp))
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                                Text(
                                    "${state.pendientesSubir} por subir",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                    if (state.totalEsperado > 0) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = {
                                (state.totalContado.toFloat() / state.totalEsperado).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (state.sectorCerrado) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(
                                    "  Sector cerrado",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { confirmarCerrarSector = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Text("  Cerrar sector", maxLines = 1)
                        }
                    }
                }
            }
            // D-243: selector de modo de pistoleo (Estándar / Lineal por huecos).
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.modo == "ESTANDAR",
                    onClick = { viewModel.cambiarModo("ESTANDAR") },
                    label = { Text("Estándar", maxLines = 1) }
                )
                FilterChip(
                    selected = state.modo == "LINEAL",
                    onClick = { viewModel.cambiarModo("LINEAL") },
                    label = { Text("Lineal por huecos", maxLines = 1) }
                )
                Spacer(Modifier.weight(1f))
            }
            // Cabecera del lineal en curso (punto A guardado, plantas y huecos).
            if (state.modo == "LINEAL" && state.linealIniciado) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MarkedBorderColor.copy(alpha = 0.22f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = BrandGreen)
                        Text(
                            "  Lineal en curso · ${state.linealPlantas} plantas · ${state.linealHuecos} huecos",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            state.mensaje?.let {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clickable { viewModel.limpiarMensaje() }
                ) {
                    Text(it, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.error?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clickable { viewModel.limpiarMensaje() }
                ) {
                    Text(it, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = busquedaPlanta,
                onValueChange = { busquedaPlanta = it },
                placeholder = { Text("Buscar planta: referencia, litraje o nombre…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                items(lineasFiltradas, key = { it.ref + "|" + it.litraje }) { linea ->
                    InvLineaCard(linea = linea, onDesinventariar = { desinventariarLinea = linea })
                }
                if (state.lineas.isEmpty()) {
                    item {
                        Text(
                            "El ERP no espera planta en este sector. Lo que pistoles aparecerá como exceso.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                } else if (lineasFiltradas.isEmpty()) {
                    item {
                        Text(
                            "Sin coincidencias",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                }
            }
        }
    }

    // ---- Camara embebida ----
    if (showScanner) {
        Box(Modifier.fillMaxSize()) {
            InvScannerScreen(
                viewModel = viewModel,
                onClose = { showScanner = false }
            )
        }
    }

    // ---- Modal: confirmar la planta leida (EAN/OCR) antes de contarla ----
    state.pendienteConfirmar?.let { pend ->
        InvConfirmarDialog(
            pend = pend,
            onAdd = { noTieneEan -> viewModel.confirmarAdicion(noTieneEan) },
            onCancel = viewModel::cancelarAdicion
        )
    }

    // ---- Modal: desinventariar (quitar 1 planta contada) con confirmacion ----
    desinventariarLinea?.let { linea ->
        AlertDialog(
            onDismissRequest = { desinventariarLinea = null },
            title = { Text("Desinventariar") },
            text = {
                Text("¿Quitar 1 planta contada de ${linea.ref}?\nÚsalo solo si hubo un error al contar.")
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.desinventariar(linea.ref, linea.litraje)
                    desinventariarLinea = null
                }) { Text("Quitar 1") }
            },
            dismissButton = {
                TextButton(onClick = { desinventariarLinea = null }) { Text("Cancelar") }
            }
        )
    }

    // ---- Modal: cerrar sector ----
    if (confirmarCerrarSector) {
        AlertDialog(
            onDismissRequest = { confirmarCerrarSector = false },
            title = { Text("Cerrar sector") },
            text = { Text("¿Marcar este sector como terminado? El encargado lo verá como cerrado en el panel web.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.cerrarSector()
                    confirmarCerrarSector = false
                }) { Text("Cerrar sector") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarCerrarSector = false }) { Text("Cancelar") }
            }
        )
    }

    // ---- Modal: eleccion de variante tras OCR ambiguo ----
    if (state.variantesOcr.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelarVariantesOcr() },
            title = { Text("Elige la variante leída") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.variantesOcr, key = { it.producto.id }) { v ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.elegirVarianteOcr(v) }
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text("${v.producto.reference} · ${v.litrajeDesc.ifBlank { "s/ litraje" }} · ${v.sectorDesc.ifBlank { "s/ sector" }}")
                                Text(v.producto.name, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.cancelarVariantesOcr() }) { Text("Cancelar") }
            }
        )
    }

    // ---- Modal: planta sin identificar (D-240) ----
    if (state.sinEtiquetaAbierto) {
        InvSinEtiquetaDialog(
            onGuardar = { viewModel.guardarSinEtiqueta(it) },
            onCerrar = { viewModel.cerrarSinEtiqueta() }
        )
    }
}

@Composable
private fun InvLineaCard(linea: InvLineaEstado, onDesinventariar: () -> Unit) {
    // D-239: si parte de las plantas de la línea no pertenece al sector/finca
    // actual, el contenedor se resalta con borde amarillo visible.
    val cardModifier = if (linea.fueraUbicacion) {
        Modifier
            .fillMaxWidth()
            .border(2.dp, MarkedBorderColor, MaterialTheme.shapes.medium)
    } else {
        Modifier.fillMaxWidth()
    }
    Card(modifier = cardModifier) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(linea.nombre.ifBlank { linea.ref }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(
                    buildString {
                        append(linea.ref)
                        if (linea.litrajeDesc.isNotBlank()) append(" · ").append(linea.litrajeDesc)
                        if (linea.fueraUbicacion) append(" · fuera de sector")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val dif = linea.contado - linea.esperado
            val estadoColor = when {
                dif == 0 -> MaterialTheme.colorScheme.primary
                dif > 0 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.error
            }
            Text(
                "${linea.contado}/${linea.esperado}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = estadoColor
            )
            IconButton(onClick = onDesinventariar) {
                Icon(Icons.Default.Remove, contentDescription = "Desinventariar (quitar 1)")
            }
        }
    }
}

@Composable
private fun InvConfirmarDialog(
    pend: PendienteInv,
    onAdd: (noTieneEan: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    // D-241: el operario indica si la planta no lleva EAN; al confirmar se suma
    // al conteo y se encola etiqueta a sacar con el motivo "Falta etiqueta EAN".
    var noTieneEan by remember(pend.producto.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(pend.producto.name.ifBlank { pend.producto.reference }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Referencia: ${pend.producto.reference}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (pend.litrajeDesc.isNotBlank()) {
                    Text(
                        "Litraje: ${pend.litrajeDesc}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (pend.sectorDesc.isNotBlank()) {
                    Text(
                        "Sector: ${pend.sectorDesc}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (pend.eanEscaneado?.isNotBlank() == true) {
                    Text(
                        "EAN: ${pend.eanEscaneado}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = noTieneEan,
                        onCheckedChange = { noTieneEan = it }
                    )
                    Text("No tiene EAN", style = MaterialTheme.typography.bodyMedium)
                }
                if (noTieneEan) {
                    Text(
                        "Se registrará una etiqueta a sacar con el motivo «Falta etiqueta EAN».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (pend.esFuera) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "Esta planta NO es de este sector",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "La etiqueta indica sector ${pend.sectorDesc}, pero estás inventariando el sector ${pend.sectorInventariadoDesc}. Se marcará para reetiquetado.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(noTieneEan) }) {
                Text(if (pend.esFuera) "Contar aquí y marcar" else "Añadir")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancelar") }
        }
    )
}

/**
 * D-240: modal de planta sin identificar. El operario escribe una descripción o
 * hipótesis de qué planta es; se guarda como incidencia para revisión posterior
 * (sin referencia resuelta, con etiqueta a sacar por motivo "Falta etiqueta EAN").
 */
@Composable
private fun InvSinEtiquetaDialog(
    onGuardar: (descripcion: String) -> Unit,
    onCerrar: () -> Unit
) {
    var descripcion by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Planta sin identificar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "No has podido leer la etiqueta de la planta. Describe qué planta " +
                        "crees que es (hipótesis) para revisarla después:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Descripción o hipótesis") },
                    placeholder = { Text("Ej.: palmera washingtonia ~2 m, maceta 100 L…") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Se registrará como incidencia de planta sin identificar y " +
                            "aparecerá en la cola de etiquetas a sacar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onGuardar(descripcion) },
                enabled = descripcion.trim().isNotEmpty()
            ) { Text("Guardar incidencia") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } }
    )
}
