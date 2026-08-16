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
    onConfirm: (
        liters: Float?,
        measure: String?,
        caliber: String?,
        needsLabel: Boolean,
        labelReason: String,
        labelFormat: String
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
                    text = "Referencia: ${product.reference}\n" +
                        "Línea: ${pending.orderId}-${pending.posicion}\n" +
                        pending.orderProductName,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (line != null) {
                    val remaining = line.requestedQty - line.pickedQty
                    if (remaining < 0) {
                        Text(
                            text = "⚠ AVISO: Línea ya completada (${line.pickedQty}/${line.requestedQty}). Se acopiará MÁS de lo pedido (sobreacopio: ${-remaining}).",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    } else if (remaining == 0) {
                        Text(
                            text = "⚠ AVISO: Línea ya completada (${line.pickedQty}/${line.requestedQty}). Al añadir se superará lo pedido.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Quedan $remaining de ${line.requestedQty} unidades en la línea",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (pending.isAmpliacion) {
                    Text(
                        text = "\u26A0 Ampliaci\u00f3n: referencia nueva no pedida",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (pending.originalProductId != product.reference && !pending.isAmpliacion) {
                    Text(
                        text = "\u26A0 Sustituci\u00f3n: ${pending.originalProductId} \u2192 ${product.reference}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
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
                    onConfirm(
                        liters,
                        measure.ifBlank { null },
                        caliber.ifBlank { null },
                        labelOption == 2 || labelOption == 3,
                        when (labelOption) {
                            2 -> "MACETA_ROTA"
                            3 -> "CAMBIO_FORMATO"
                            else -> ""
                        },
                        labelFormat
                    )
                }
            ) {
                Text("A\u00f1adir l\u00ednea")
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

private fun parseLitraje(value: String): Float? =
    Regex("""\d+(?:[.,]\d+)?""").find(value)?.value?.replace(',', '.')?.toFloatOrNull()
