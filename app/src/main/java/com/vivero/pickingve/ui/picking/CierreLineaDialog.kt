package com.vivero.pickingve.ui.picking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.vivero.pickingve.data.local.entities.OrderLineEntity

data class MotivoCierre(val codigo: String, val etiqueta: String)

val MOTIVOS_CIERRE = listOf(
    MotivoCierre("SIN_STOCK", "No hay planta suficiente en campo"),
    MotivoCierre("PLANTA_DANADA", "Planta dañada o en mal estado"),
    MotivoCierre("CALIBRE_NO_COMERCIAL", "Calibre/tamaño no comercial"),
    MotivoCierre("NO_ENCONTRADA", "No se ha encontrado la referencia"),
    MotivoCierre("CLIMATOLOGIA", "Daños por climatología"),
    MotivoCierre("OTRO", "Otro motivo (especificar)")
)

@Composable
fun CierreLineaDialog(
    line: OrderLineEntity,
    pendiente: Int,
    onConfirmar: (motivo: String, texto: String) -> Unit,
    onDismiss: () -> Unit
) {
    var motivo by remember { mutableStateOf(MOTIVOS_CIERRE.first().codigo) }
    var texto by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cerrar línea") },
        text = {
            Column {
                Text(
                    text = "${line.productName} (${line.productId})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Faltan $pendiente unidades. Indica el motivo; la oficina lo recibirá al momento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(MOTIVOS_CIERRE, key = { it.codigo }) { m ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            RadioButton(
                                selected = motivo == m.codigo,
                                onClick = { motivo = m.codigo }
                            )
                            Text(m.etiqueta, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (motivo == "OTRO") {
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { texto = it },
                        label = { Text("Describe el motivo") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val detalle = if (motivo == "OTRO") texto.trim() else ""
                    onConfirmar(motivo, detalle)
                },
                enabled = motivo != "OTRO" || texto.isNotBlank()
            ) {
                Text("Cerrar línea")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
