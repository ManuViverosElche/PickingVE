package com.vivero.pickingve.ui.picking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun ConfirmPickingDialog(
    pending: PendingConfirm,
    requiresMeasure: Boolean,
    onConfirm: (liters: Float?, measure: String?, caliber: String?, needsLabel: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val product = pending.product
    var liters by remember { mutableStateOf(product.defaultLiters?.toString().orEmpty()) }
    var measure by remember { mutableStateOf(product.defaultMeasure.orEmpty()) }
    var caliber by remember { mutableStateOf(product.defaultCaliber.orEmpty()) }
    var needsLabel by remember { mutableStateOf(false) }
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
                if (pending.originalProductId != product.reference) {
                    Text(
                        text = "\u26A0 Sustituci\u00f3n: ${pending.originalProductId} \u2192 ${product.reference}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                OutlinedTextField(
                    value = liters,
                    onValueChange = { liters = it },
                    label = { Text("Litraje (L)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (requiresMeasure) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = needsLabel,
                        onCheckedChange = { needsLabel = it }
                    )
                    Text("La planta ha llegado sin etiqueta: hay que sacar etiqueta")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = measureValid,
                onClick = {
                    onConfirm(
                        liters.toFloatOrNull(),
                        measure.ifBlank { null },
                        caliber.ifBlank { null },
                        needsLabel
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
