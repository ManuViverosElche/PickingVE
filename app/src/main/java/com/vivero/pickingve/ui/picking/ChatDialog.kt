package com.vivero.pickingve.ui.picking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vivero.pickingve.data.local.AppDatabase
import com.vivero.pickingve.data.remote.ApiComentario
import com.vivero.pickingve.data.remote.PickingApiClient
import com.vivero.pickingve.data.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatDialog(
    pedidoId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val api = remember { PickingApiClient() }
    val settings = remember { SettingsRepository(context.applicationContext).settings.value }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var mensajes by remember { mutableStateOf<List<ApiComentario>>(emptyList()) }
    var texto by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    suspend fun cargar() {
        try {
            mensajes = api.fetchComentarios(pedidoId)
            if (mensajes.isNotEmpty()) {
                listState.scrollToItem(mensajes.lastIndex)
            }
        } catch (e: Exception) {
            error = "No se pudieron cargar los mensajes"
        }
    }

    LaunchedEffect(pedidoId) {
        cargar()
        while (true) {
            delay(10_000)
            cargar()
        }
    }

    fun enviar() {
        if (texto.isBlank() || enviando) return
        enviando = true
        val cuerpo = texto.trim()
        texto = ""
        scope.launch {
            try {
                val rol = AppDatabase.getDatabase(context).encargadoDao().getAll()
                    .firstOrNull { it.email == settings.operatorEmail }?.rol ?: "ENCARGADO"
                api.crearComentario(
                    pedido = pedidoId,
                    linea = null,
                    texto = cuerpo,
                    autorEmail = settings.operatorEmail.ifBlank { "app@pickingve" },
                    autorNombre = settings.operatorName.ifBlank { "Encargado" },
                    rol = rol
                )
                cargar()
                error = null
            } catch (e: Exception) {
                error = "No se pudo enviar el mensaje"
                texto = cuerpo
            } finally {
                enviando = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mensajes · Pedido $pedidoId") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (mensajes.isEmpty()) {
                        item {
                            Text(
                                "Sin mensajes todavía. Escribe a la oficina si necesitas algo.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    items(mensajes, key = { it.id }) { m ->
                        MensajeBurbuja(m, esMio = m.autorEmail == settings.operatorEmail)
                    }
                }
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { texto = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Escribe un mensaje…") },
                        maxLines = 3
                    )
                    IconButton(
                        onClick = { enviar() },
                        enabled = texto.isNotBlank() && !enviando
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun MensajeBurbuja(m: ApiComentario, esMio: Boolean) {
    Surface(
        color = if (esMio) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    m.autorNombre.ifBlank { m.autorEmail },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = if (esMio) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatearFecha(m.creadoEn),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                m.texto,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.widthIn(max = 300.dp)
            )
        }
    }
}

private fun formatearFecha(iso: String): String = try {
    val instante = java.time.Instant.parse(iso)
    java.time.ZonedDateTime.ofInstant(instante, java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"))
} catch (e: Exception) {
    ""
}
