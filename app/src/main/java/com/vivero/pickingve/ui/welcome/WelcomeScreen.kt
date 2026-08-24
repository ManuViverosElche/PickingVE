package com.vivero.pickingve.ui.welcome

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vivero.pickingve.data.repository.PickingRepository
import com.vivero.pickingve.ui.orders.OrderListViewModel
import com.vivero.pickingve.ui.theme.BrandGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

data class TiempoHoy(val temp: String, val desc: String)

/**
 * D-193: pantalla de bienvenida — saludo, tiempo de hoy, resumen de la faena
 * segun el rol y sincronizacion visible ("Actualizando el dia..."). El boton
 * EMPEZAR EL DIA aparece tras un minimo de 3 s y cuando el sync termina.
 */
@Composable
fun WelcomeScreen(
    repository: PickingRepository,
    orderListViewModel: OrderListViewModel,
    resumenFaena: String?,
    onEmpezar: () -> Unit
) {
    val syncState by orderListViewModel.syncState.collectAsState()
    val orders by orderListViewModel.orders.collectAsState()
    var segundos by remember { mutableStateOf(0) }
    var tiempo by remember { mutableStateOf<TiempoHoy?>(null) }

    val esOperario = repository.tipoSesion() == PickingRepository.TIPO_OPERARIO
    val syncTerminado = !syncState.syncing
    val puedeEntrar = segundos >= 3 && syncTerminado

    LaunchedEffect(Unit) {
        orderListViewModel.syncOrders()
        while (segundos < 3) { delay(250); segundos += 1 }
    }
    LaunchedEffect(Unit) {
        val t = withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=38.2622&longitude=-0.7063&current_weather=true")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000; conn.readTimeout = 5000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val cw = Json.parseToJsonElement(body).jsonObject["current_weather"]!!.jsonObject
                val temp = cw["temperature"]!!.jsonPrimitive.content
                val code = cw["weathercode"]!!.jsonPrimitive.content.toInt()
                TiempoHoy("${temp}°C", descripcionTiempo(code))
            }.getOrNull()
        }
        if (t != null) tiempo = t
    }

    val hoy = remember { java.time.LocalDate.now() }
    val pedidosHoy = orders.filter {
        it.fechaCarga?.let { ms -> java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate() } == hoy
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("PickingVE", style = MaterialTheme.typography.headlineMedium, color = BrandGreen)
        Text(
            "Buenos días${if (esOperario) ", operario" else ""}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp)
        )

        // --- Tiempo de hoy ---
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🌤️", style = MaterialTheme.typography.headlineSmall)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(tiempo?.let { "${it.temp} · ${it.desc}" } ?: "Elche", style = MaterialTheme.typography.titleMedium)
                    Text(
                        hoy.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", java.util.Locale("es"))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- Resumen de faena segun rol ---
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (esOperario) Icons.Filled.Agriculture else Icons.Filled.Inventory2, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        if (esOperario) "Tu faena de hoy" else "Cargas de hoy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                when {
                    esOperario -> Text(
                        resumenFaena ?: "Calculando…",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    else -> Text(
                        if (pedidosHoy.isEmpty()) "Sin cargas registradas hoy"
                        else "${pedidosHoy.size} pedido(s) con carga hoy",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // --- Estado de sincronizacion (interactivo, nunca parece colgado) ---
        val transicion = rememberInfiniteTransition(label = "sync")
        val pulso by transicion.animateFloat(
            initialValue = 0.4f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "syncAlpha"
        )
        if (!puedeEntrar) {
            Text(
                if (!syncTerminado) "Actualizando el día…" else "Preparando…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.alpha(pulso)
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        } else {
            Button(
                onClick = onEmpezar,
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("EMPEZAR EL DÍA", fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.weight(1f))
    }
}

private fun descripcionTiempo(code: Int): String = when (code) {
    0 -> "Despejado"; 1, 2 -> "Parcialmente nublado"; 3 -> "Nublado"
    in 45..48 -> "Niebla"
    in 51..67, in 80..82 -> "Lluvia"
    in 71..77, in 85..86 -> "Nieve"
    in 95..99 -> "Tormenta"
    else -> "—"
}
