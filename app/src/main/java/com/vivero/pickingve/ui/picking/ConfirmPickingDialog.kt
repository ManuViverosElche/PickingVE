package com.vivero.pickingve.ui.picking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vivero.pickingve.data.local.entities.LitrajeEntity
import com.vivero.pickingve.data.local.entities.OrderLineEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmPickingDialog(
    pending: PendingConfirm,
    line: OrderLineEntity?,
    litrajes: List<LitrajeEntity>,
    compensaciones: Map<String, Int>,
    onConfirm: (
        liters: Float?,
        measure: String?,
        caliber: String?,
        needsLabel: Boolean,
        labelReason: String,
        labelFormat: String,
        qty: Int
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val product = pending.product
    val requiresMeasure = line?.requiresMeasure == true
    val liters = remember(pending) { resolveLitraje(line, product) }
    val litrajeInfo = listOfNotNull(
        line?.litrajeDesc?.ifBlank { null }
    ).joinToString(" · ")
    var measure by remember { mutableStateOf(product.defaultMeasure.orEmpty()) }
    var caliber by remember { mutableStateOf(product.defaultCaliber.orEmpty()) }
    var labelOption by remember { mutableStateOf(0) }
    var labelFormat by remember { mutableStateOf("") }
    val ventaDirecta = product.reference.startsWith("9")
    val shownPicked = remember(line, compensaciones) {
        if (line == null) 0
        else maxOf(line.pickedQty, line.acopiadoServidor - (compensaciones[line.orderLineId] ?: 0))
    }
    val defaultQty = if (ventaDirecta && line != null) {
        (line.requestedQty - shownPicked).coerceAtLeast(1)
    } else 1
    // Para etiquetas siempre usamos cantidad 1 (se acumulan al volver a pedir)
    val labelQty = 1
    var qtyText by remember(pending, line) { mutableStateOf(defaultQty.toString()) }
    val qty = if (ventaDirecta) (qtyText.toIntOrNull()?.coerceAtLeast(1) ?: 1) else 1
    val measureValid = !requiresMeasure || measure.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmar artículo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Referencia escaneada: ${product.reference}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (pending.isAmpliacion) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(
                                "AMPLIACIÓN · No está en el pedido",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "Esta referencia no existe en el pedido ${pending.orderId}. Se añadirá como referencia nueva.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(
                                "Se añadirá a la línea ${pending.posicion}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "${pending.orderProductName} · ${pending.originalProductId}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (pending.originalProductId != product.reference) {
                                Text(
                                    "Sustitución: el pedido pide ${pending.originalProductId}, esta planta es ${product.reference}.",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            if (line != null) {
                                val remaining = line.requestedQty - shownPicked
                                if (remaining < 0) {
                                    Text(
                                        "⚠ Línea ya completada (${shownPicked}/${line.requestedQty}). Se acopiará MÁS de lo pedido (sobreacopio: ${-remaining}).",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                } else if (remaining == 0) {
                                    Text(
                                        "⚠ Línea ya completada (${shownPicked}/${line.requestedQty}). Al añadir se superará lo pedido.",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                } else {
                                    Text(
                                        "Pendiente: $remaining unidades de ${line.requestedQty}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
                if (product.ean?.isNotBlank() == true) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Etiqueta EAN: ${product.ean}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                if (litrajeInfo.isNotBlank()) {
                    Text(
                        text = "Litraje: $litrajeInfo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (!pending.ocrText.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(
                                "Etiqueta",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = pending.ocrText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                if (ventaDirecta) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.filter(Char::isDigit) },
                        label = { Text("Cantidad a acopiar") },
                        supportingText = {
                            Text("Venta directa: valida la cantidad antes de continuar")
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (ventaDirecta && line != null && (shownPicked + qty) > line.requestedQty) {
                    Text(
                        "⚠ AVISO: Se acopiará más de lo pedido (${shownPicked + qty} en total para ${line.requestedQty} pedidas).",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
                if (requiresMeasure) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    OutlinedTextField(
                        value = measure,
                        onValueChange = { measure = it },
                        label = { Text("Medida (cm)* obligatoria") },
                        isError = measure.isBlank(),
                        supportingText = if (measure.isBlank()) {
                            { Text("Introduce la medida") }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = caliber,
                        onValueChange = { caliber = it },
                        label = { Text("Calibre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Straighten,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text(
                            "Esta planta se vende por medida: introduce la altura en cent\u00edmetros",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                LabelOptionSelector(
                    labelOption = labelOption,
                    labelFormat = labelFormat,
                    litrajes = litrajes,
                    mostrarNoEtiqueta = false,
                    onOptionChange = { labelOption = it; if (it != 3) labelFormat = "" },
                    onFormatChange = { labelFormat = it }
                )
            }
        },
        confirmButton = {
            Button(
                enabled = measureValid && (labelOption != 3 || labelFormat.isNotBlank()),
                onClick = {
                    val isLabel = labelOption == 2 || labelOption == 3 || labelOption == 4
                    onConfirm(
                        liters,
                        measure.ifBlank { null },
                        caliber.ifBlank { null },
                        isLabel,
                        when (labelOption) {
                            2 -> "MACETA_ROTA"
                            3 -> "CAMBIO_FORMATO"
                            4 -> "PASAPORTE_MAL_ESTADO"
                            else -> ""
                        },
                        labelFormat,
                        if (isLabel) labelQty else qty
                    )
                }
            ) {
                Text(
                    if (pending.isAmpliacion) "Registrar ampliación"
                    else "Añadir a línea ${pending.posicion}"
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
internal fun LabelOptionSelector(
    labelOption: Int,
    labelFormat: String,
    litrajes: List<LitrajeEntity>,
    onOptionChange: (Int) -> Unit,
    onFormatChange: (String) -> Unit,
    mostrarNoEtiqueta: Boolean = true
) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = labelOption == 0, onClick = { onOptionChange(0) })
        Text("Ninguna (sin incidencia)")
    }
    if (mostrarNoEtiqueta) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = labelOption == 1, onClick = { onOptionChange(1) })
            Text("No lleva etiqueta: hay que sacar etiquetas")
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = labelOption == 2, onClick = { onOptionChange(2) })
        Text("Maceta rota")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = labelOption == 3, onClick = { onOptionChange(3) })
        Text("Cambio de maceta a otro formato")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = labelOption == 4, onClick = { onOptionChange(4) })
        Text("Pasaporte en mal estado")
    }
    if (labelOption == 3) {
        LitrajeSearchField(
            litrajes = litrajes,
            selected = labelFormat,
            onSelected = onFormatChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LitrajeSearchField(
    litrajes: List<LitrajeEntity>,
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(selected) }
    val filtered = remember(query, litrajes) {
        if (query.isBlank()) litrajes
        else litrajes.filter {
            it.id.contains(query, ignoreCase = true) ||
                it.descripcion.contains(query, ignoreCase = true)
        }
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Formato (LITRAJES)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Column {
                filtered.forEach { litraje ->
                    DropdownMenuItem(
                        text = {
                            Text(litraje.descripcion)
                        },
                        onClick = {
                            onSelected(litraje.descripcion)
                            query = litraje.descripcion
                            expanded = false
                        }
                    )
                }
                if (filtered.isEmpty()) {
                    Text(
                        "Sin coincidencias",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

/** Litraje auto-rellenado: línea primero (código o descripción), luego el valor por defecto del producto. */
internal fun resolveLitraje(line: OrderLineEntity?, product: com.vivero.pickingve.data.local.entities.ProductEntity): Float? {
    if (line != null) {
        parseLitraje(line.litraje)?.let { return it }
        parseLitraje(line.litrajeDesc)?.let { return it }
    }
    return product.defaultLiters
}

internal fun parseLitraje(value: String): Float? =
    Regex("""\d+(?:[.,]\d+)?""").find(value)?.value?.replace(',', '.')?.toFloatOrNull()
