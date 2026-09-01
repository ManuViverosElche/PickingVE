package com.vivero.pickingve.ui.welcome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import com.vivero.pickingve.ui.logistica.FaenaDashboardViewModel
import com.vivero.pickingve.R
import com.vivero.pickingve.data.repository.PickingRepository
import com.vivero.pickingve.ui.orders.OrderListViewModel
import com.vivero.pickingve.ui.theme.BrandGreen
import com.vivero.pickingve.util.GpsFix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

data class TramoTiempo(
    val nombre: String,
    val desc: String,
    val tempMin: String,
    val tempMax: String
)

data class TiempoHoy(
    val localidad: String = "Elche",
    val tempActual: String = "—",
    val desc: String = "—",
    val maxDia: String = "—",
    val minDia: String = "—",
    val descDia: String = "—",
    val tramos: List<TramoTiempo> = emptyList()
)

/** Resumen de acopio pendiente agrupado por finca (para el operario de acopio). */
data class AcopioResumen(val finca: String, val plantas: Int)

/**
 * D-193: pantalla de bienvenida — saludo, tiempo de hoy, resumen de la faena
 * segun el rol y sincronizacion visible ("Actualizando el dia..."). El boton
 * EMPEZAR EL DIA aparece tras un minimo de 3 s y cuando el sync termina.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    repository: PickingRepository,
    orderListViewModel: OrderListViewModel,
    faenaViewModel: FaenaDashboardViewModel,
    acopioPorFinca: List<AcopioResumen>,
    onEmpezar: () -> Unit,
    onLogout: () -> Unit
) {
    val syncState by orderListViewModel.syncState.collectAsStateWithLifecycle()
    val orders by orderListViewModel.orders.collectAsStateWithLifecycle()
    var segundos by remember { mutableStateOf(0) }
    var tiempo by remember { mutableStateOf<TiempoHoy?>(null) }
    var resumenExpandido by remember { mutableStateOf(false) }

    var esOperario by remember { mutableStateOf(repository.tipoSesion() == PickingRepository.TIPO_OPERARIO) }
    val nombreOperario = remember { repository.nombreFaena() }
    val syncTerminado = !syncState.syncing
    val puedeEntrar = segundos >= 3 && syncTerminado
    val horaActual = remember { java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) }
    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
        if (!esOperario) {
            esOperario = repository.encargadoEsOperarioActivo()
        }
    }

    LaunchedEffect(Unit) {
        orderListViewModel.syncOrders()
        faenaViewModel.refrescarPerfil()
        while (segundos < 3) { delay(250); segundos += 1 }
    }
    LaunchedEffect(Unit) {
        val ctx = context
        val t = withContext(Dispatchers.IO) {
            val pos = obtenerPosicion(ctx)
            val localidad = reverseGeocode(pos.first, pos.second)
            cargarTiempo(pos.first, pos.second)?.copy(localidad = localidad)
        }
        if (t != null) tiempo = t
    }

    val hoy = remember { java.time.LocalDate.now() }
    val pedidosHoy = orders.filter {
        it.fechaCarga?.let { ms -> java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate() } == hoy
    }
    val totalAcopio = acopioPorFinca.sumOf { it.plantas }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bienvenido") },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Salir")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Image(
                painter = painterResource(R.drawable.logo_viveros_sin_palmera),
                contentDescription = "Logo Viveros Elche",
                modifier = Modifier
                    .height(110.dp)
                    .width(110.dp)
            )
            Text(
                if (nombreOperario.isNotBlank()) "Buenos días, ${nombreOperario.substringBefore(' ').trim()}" else "Buenos días",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp)
            )

            // --- Tiempo de hoy (localidad geolocalizada + previsión por tramos) ---
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🌤️", style = MaterialTheme.typography.headlineSmall)
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                tiempo?.localidad ?: "Elche",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "$horaActual · " + hoy.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", java.util.Locale("es"))),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            tiempo?.tempActual ?: "—",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    tiempo?.let { t ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Día: ${t.descDia} · máx ${t.maxDia} / mín ${t.minDia}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            t.tramos.forEach { tramo ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        tramo.nombre,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "${tramo.tempMin} → ${tramo.tempMax}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        tramo.desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
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
                            if (esOperario) "Acopio de hoy" else "Cargas de hoy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    when {
                        esOperario -> {
                            if (acopioPorFinca.isEmpty()) {
                                Text(
                                    "Sin acopio pendiente",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { resumenExpandido = !resumenExpandido },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "$totalAcopio plantas pendientes en ${acopioPorFinca.size} finca(s)",
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            if (resumenExpandido) "Toca para ocultar el detalle por finca"
                                            else "Toca para ver el detalle por finca",
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.alpha(0.7f)
                                        )
                                    }
                                    Icon(
                                        if (resumenExpandido) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = if (resumenExpandido) "Ocultar detalle" else "Ver detalle por finca",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                AnimatedVisibility(visible = resumenExpandido) {
                                    Column {
                                        Spacer(Modifier.height(6.dp))
                                        acopioPorFinca.forEach { (finca, plantas) ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp)
                                            ) {
                                                Text(
                                                    finca,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    "$plantas plantas",
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun obtenerPosicion(context: android.content.Context): Pair<Double, Double> {
    return GpsFix.ultimaPosicion(context) ?: (38.2622 to -0.7063) // Elche por defecto
}

private fun reverseGeocode(lat: Double, lon: Double): String = runCatching {
    val url = URL("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=es")
    val conn = url.openConnection() as HttpURLConnection
    conn.connectTimeout = 4000; conn.readTimeout = 4000
    val body = conn.inputStream.bufferedReader().use { it.readText() }
    val obj = Json.parseToJsonElement(body).jsonObject
    val locality = obj["locality"]?.jsonPrimitive?.content.orEmpty().trim()
    val city = obj["city"]?.jsonPrimitive?.content.orEmpty().trim()
    locality.ifBlank { city }.ifBlank { "Elche" }
}.getOrDefault("Elche")

private fun cargarTiempo(lat: Double, lon: Double): TiempoHoy? = runCatching {
    val url = URL(
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current_weather=true&daily=temperature_2m_max,temperature_2m_min,weathercode" +
            "&hourly=temperature_2m,weathercode&timezone=auto&forecast_days=1"
    )
    val conn = url.openConnection() as HttpURLConnection
    conn.connectTimeout = 5000; conn.readTimeout = 5000
    val body = conn.inputStream.bufferedReader().use { it.readText() }
    val root = Json.parseToJsonElement(body).jsonObject
    val cw = root["current_weather"]?.jsonObject
    val temp = cw?.get("temperature")?.jsonPrimitive?.content ?: "—"
    val code = cw?.get("weathercode")?.jsonPrimitive?.content?.toIntOrNull() ?: 0

    val daily = root["daily"]?.jsonObject
    val dMax = daily?.get("temperature_2m_max")?.jsonArray?.getOrNull(0)?.jsonPrimitive?.content ?: "—"
    val dMin = daily?.get("temperature_2m_min")?.jsonArray?.getOrNull(0)?.jsonPrimitive?.content ?: "—"
    val dCode = daily?.get("weathercode")?.jsonArray?.getOrNull(0)?.jsonPrimitive?.content?.toIntOrNull() ?: code

    val hourly = root["hourly"]?.jsonObject
    val times = hourly?.get("time")?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
    val temps = hourly?.get("temperature_2m")?.jsonArray?.map { it.jsonPrimitive.content.toDoubleOrNull() } ?: emptyList()
    val codes = hourly?.get("weathercode")?.jsonArray?.map { it.jsonPrimitive.content.toIntOrNull() } ?: emptyList()

    TiempoHoy(
        tempActual = "${temp}°C",
        desc = descripcionTiempo(code),
        maxDia = dMax,
        minDia = dMin,
        descDia = descripcionTiempo(dCode),
        tramos = calcularTramos(times, temps, codes)
    )
}.getOrNull()

private fun calcularTramos(
    times: List<String>,
    temps: List<Double?>,
    codes: List<Int?>
): List<TramoTiempo> {
    data class Punto(val hora: Int, val temp: Double, val code: Int)
    val datos = times.mapIndexedNotNull { i, t ->
        val hora = t.substringAfterLast('T').substringBefore(':').toIntOrNull() ?: return@mapIndexedNotNull null
        val temp = temps.getOrNull(i) ?: return@mapIndexedNotNull null
        Punto(hora, temp, codes.getOrNull(i) ?: 0)
    }
    return listOf("Mañana" to 6..11, "Tarde" to 12..17, "Noche" to 18..23).mapNotNull { (nombre, rango) ->
        val enTramo = datos.filter { it.hora in rango }
        if (enTramo.isEmpty()) return@mapNotNull null
        val min = enTramo.minOf { it.temp }.toInt()
        val max = enTramo.maxOf { it.temp }.toInt()
        val codeDominante = enTramo.groupBy { it.code }.maxByOrNull { it.value.size }?.key ?: 0
        TramoTiempo(nombre, descripcionTiempo(codeDominante), "${min}°", "${max}°")
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
