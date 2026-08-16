package com.vivero.pickingve.ui.picking

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.vivero.pickingve.data.local.AppDatabase
import com.vivero.pickingve.data.local.entities.OrderLineEntity
import com.vivero.pickingve.data.remote.ApiComentario
import com.vivero.pickingve.data.remote.PickingApiClient
import com.vivero.pickingve.data.repository.SettingsRepository
import io.ktor.http.ContentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

@Composable
fun ChatDialog(
    pedidoId: String,
    linea: String? = null,
    lineaInfo: OrderLineEntity? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val api = remember { PickingApiClient() }
    val settings = remember { SettingsRepository(context.applicationContext).settings.value }
    val scope = rememberCoroutineScope()
    var mensajes by remember { mutableStateOf<List<ApiComentario>>(emptyList()) }
    var texto by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var subiendoFoto by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    suspend fun cargar() {
        try {
            mensajes = api.fetchComentarios(pedidoId, linea)
            if (mensajes.isNotEmpty()) {
                listState.scrollToItem(mensajes.lastIndex)
            }
        } catch (e: Exception) {
            error = "No se pudieron cargar los mensajes"
        }
    }

    LaunchedEffect(pedidoId, linea) {
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
                    linea = linea,
                    texto = cuerpo,
                    autorEmail = settings.operatorEmail.ifBlank { "app@pickingve" },
                    autorNombre = settings.operatorName.ifBlank { "Encargado" },
                    rol = rol
                )
                error = null
                cargar()
                delay(2_000)
                cargar()
            } catch (e: Exception) {
                error = "No se pudo enviar el mensaje"
                texto = cuerpo
            } finally {
                enviando = false
            }
        }
    }

    var fotoPendiente by remember { mutableStateOf<Uri?>(null) }
    val tomarFoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { }
    var fotoEnviando by remember { mutableStateOf(false) }

    fun lanzarCamara() {
        if (enviando || subiendoFoto) return
        val archivo = File.createTempFile("chat_", ".jpg", context.cacheDir)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            archivo
        )
        fotoPendiente = uri
        tomarFoto.launch(uri)
    }

    fun confirmarFoto() {
        val uri = fotoPendiente ?: return
        if (subiendoFoto || fotoEnviando) return
        fotoEnviando = true
        scope.launch {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("No se pudo leer la foto")
                val rol = AppDatabase.getDatabase(context).encargadoDao().getAll()
                    .firstOrNull { it.email == settings.operatorEmail }?.rol ?: "ENCARGADO"
                val nombre = "foto_${System.currentTimeMillis()}.jpg"
                api.subirAdjunto(
                    pedido = pedidoId,
                    linea = linea,
                    texto = texto.trim(),
                    autorEmail = settings.operatorEmail.ifBlank { "app@pickingve" },
                    autorNombre = settings.operatorName.ifBlank { "Encargado" },
                    rol = rol,
                    nombreArchivo = nombre,
                    bytes = bytes,
                    contentType = ContentType.Image.JPEG
                )
                texto = ""
                fotoPendiente = null
                error = null
                cargar()
                delay(2_000)
                cargar()
            } catch (e: Exception) {
                error = "No se pudo enviar la foto"
            } finally {
                fotoEnviando = false
            }
        }
    }

    val pendiente = lineaInfo?.let {
        it.requestedQty - maxOf(it.pickedQty, it.acopiadoServidor)
    }

    val titulo = if (linea == null) {
        "Mensajes · Pedido $pedidoId"
    } else {
        "Línea ${lineaInfo?.posicion ?: "?"} · ${lineaInfo?.productName.orEmpty()}"
    }
    val subtitulo = if (linea == null) null else {
        listOfNotNull(
            lineaInfo?.productId,
            lineaInfo?.litrajeDesc?.takeIf { it.isNotBlank() },
            lineaInfo?.sectorDesc?.takeIf { it.isNotBlank() },
            pendiente?.let { "Pendiente $it" }
        ).joinToString(" · ")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(titulo, style = MaterialTheme.typography.titleMedium)
                if (subtitulo != null) {
                    Text(
                        subtitulo,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
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
                val foto = fotoPendiente
                if (foto != null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = foto,
                            contentDescription = "Vista previa de la foto",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = texto,
                                onValueChange = { texto = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Añade un texto a la foto…") },
                                maxLines = 3,
                                enabled = !subiendoFoto && !fotoEnviando
                            )
                            IconButton(
                                onClick = { confirmarFoto() },
                                enabled = !enviando && !subiendoFoto && !fotoEnviando
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar foto")
                            }
                            IconButton(
                                onClick = { fotoPendiente = null },
                                enabled = !fotoEnviando
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancelar foto")
                            }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = texto,
                            onValueChange = { texto = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(if (subiendoFoto) "Subiendo foto…" else "Escribe un mensaje…") },
                            maxLines = 3,
                            enabled = !subiendoFoto
                        )
                        IconButton(
                            onClick = { lanzarCamara() },
                            enabled = !enviando && !subiendoFoto
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = "Enviar foto")
                        }
                        IconButton(
                            onClick = { enviar() },
                            enabled = texto.isNotBlank() && !enviando && !subiendoFoto
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                        }
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
    Row(
        horizontalArrangement = if (esMio) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            color = if (esMio) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (esMio) 14.dp else 4.dp,
                bottomEnd = if (esMio) 4.dp else 14.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!esMio && m.autorNombre.isNotBlank()) {
                    Text(
                        m.autorNombre,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (m.adjuntoUrl.isNotBlank()) {
                    AsyncImage(
                        model = m.adjuntoUrl,
                        contentDescription = "Foto adjunta",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    )
                }
                if (m.texto.isNotBlank()) {
                    Text(
                        m.texto,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    formatearFecha(m.creadoEn),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

private fun formatearFecha(iso: String): String = try {
    val instante = Instant.parse(iso)
    java.time.ZonedDateTime.ofInstant(instante, java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
} catch (e: Exception) {
    ""
}