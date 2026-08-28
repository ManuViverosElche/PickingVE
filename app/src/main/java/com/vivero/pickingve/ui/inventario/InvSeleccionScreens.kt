package com.vivero.pickingve.ui.inventario

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vivero.pickingve.data.remote.ApiInvSector
import com.vivero.pickingve.ui.theme.BrandGreen
import com.vivero.pickingve.ui.theme.LightWarnContainer

/**
 * Pantallas de entrada del inventario (D-219): elegir finca y, si tiene
 * sectores, elegir el sector a inventariar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvInicioScreen(
    viewModel: InvViewModel,
    onBack: (() -> Unit)?,
    onLogout: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    var confirmarBorrado by remember { mutableStateOf(false) }
    var busquedaFinca by remember { mutableStateOf("") }
    val fincasFiltradas = remember(state.fincas, busquedaFinca) {
        if (busquedaFinca.isBlank()) state.fincas
        else state.fincas.filter { it.finca.contains(busquedaFinca, ignoreCase = true) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventario") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { confirmarBorrado = true }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Borrar registros de inventario")
                    }
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
                .padding(16.dp)
        ) {
            Text(
                "Elige la finca a inventariar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = busquedaFinca,
                onValueChange = { busquedaFinca = it },
                placeholder = { Text("Buscar finca…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            if (state.cargando) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null && state.fincas.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.cargarFincas() }) { Text("Reintentar") }
                }
            } else if (state.fincas.isEmpty()) {
                Text(
                    "No hay fincas configuradas para inventario.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (fincasFiltradas.isEmpty()) {
                Text(
                    "Sin coincidencias",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(fincasFiltradas, key = { it.finca }) { finca ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.seleccionarFinca(finca)
                                    viewModel.refrescarServidor()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(finca.finca, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    val n = finca.sectores.size
                                    Text(
                                        "Sectores: $n",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                        }
                    }
                }
            }
        }
    }

    if (confirmarBorrado) {
        AlertDialog(
            onDismissRequest = { confirmarBorrado = false },
            title = { Text("Borrar registros") },
            text = {
                Text("Se borrarán todos los pistoleos de inventario (locales y, si se pudieron subir, también en el servidor). ¿Continuar?")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.borrarRegistros()
                    confirmarBorrado = false
                }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmarBorrado = false }) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvSectorScreen(
    viewModel: InvViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val resumen by viewModel.resumenServidorSectores.collectAsState()
    val stockTodo by viewModel.stockSectores().collectAsState(initial = emptyList())

    val finca = state.fincaSeleccionada ?: return
    var busquedaSector by remember { mutableStateOf("") }
    val sectoresFiltrados = remember(finca.sectores, busquedaSector) {
        if (busquedaSector.isBlank()) finca.sectores
        else finca.sectores.filter {
            it.id.contains(busquedaSector, ignoreCase = true) ||
                it.descripcion.contains(busquedaSector, ignoreCase = true)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(finca.finca) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
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
                .padding(16.dp)
        ) {
            Text(
                "¿Qué sector vas a inventariar?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = busquedaSector,
                onValueChange = { busquedaSector = it },
                placeholder = { Text("Buscar sector…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            if (sectoresFiltrados.isEmpty()) {
                Text(
                    "Sin coincidencias",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(sectoresFiltrados, key = { it.id }) { sector ->
                    val esperado = stockTodo.filter { it.sector == sector.id }
                        .sumOf { it.stock }.toInt()
                    val r = resumen[sector.id]
                    // D-239: estados de color del sector:
                    //  - cerrado  -> verde corporativo (#025C65) con texto blanco.
                    //  - en curso (tiene inventario abierto) -> fondo amarillo y
                    //    texto en verde corporativo (se lee sobre el amarillo).
                    //  - sin empezar -> fondo neutro claro, reborde verde
                    //    corporativo y letra verde corporativo.
                    val estadoColor = when {
                        sector.cerrado -> BrandGreen
                        sector.tieneInventario -> LightWarnContainer
                        else -> null
                    }
                    val onEstadoColor = when {
                        sector.cerrado -> androidx.compose.ui.graphics.Color.White
                        sector.tieneInventario -> BrandGreen
                        else -> BrandGreen
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.seleccionarSector(sector)
                            },
                        border = if (estadoColor == null) {
                            BorderStroke(2.dp, BrandGreen)
                        } else {
                            null
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = estadoColor ?: MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(start = 4.dp)
                            ) {
                                Text(
                                    sector.descripcion,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = onEstadoColor
                                )
                                Text(
                                    "Esperado: $esperado plantas" +
                                        (r?.let { " · Contado: ${it.total}" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onEstadoColor
                                )
                            }
                            if (sector.cerrado) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.22f)
                                ) {
                                    Text(
                                        "Cerrado",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = androidx.compose.ui.graphics.Color.White
                                    )
                                }
                            } else if (sector.tieneInventario) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = BrandGreen
                                ) {
                                    Text(
                                        "En curso",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = androidx.compose.ui.graphics.Color.White
                                    )
                                }
                            }
                            if ((r?.fuera ?: 0) > 0) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        "${r!!.fuera} fuera",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
            if (state.pendientesSubir > 0) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.sincronizarAhora() }
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.height(0.dp))
                        Text(
                            "${state.pendientesSubir} registros por subir · pulsa para enviar",
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
