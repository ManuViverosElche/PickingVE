package com.vivero.pickingve.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vivero.pickingve.data.remote.ApiFinca

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminFincasScreen(
    viewModel: AdminFincasViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var nuevaFinca by remember { mutableStateOf("") }
    var fincaEditando by remember { mutableStateOf<ApiFinca?>(null) }
    var nombreEditando by remember { mutableStateOf("") }
    var fincaEliminando by remember { mutableStateOf<ApiFinca?>(null) }

    LaunchedEffect(state.mensaje) {
        state.mensaje?.let { snackbarHostState.showSnackbar(it) }
        viewModel.clearMessages()
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
        viewModel.clearMessages()
    }

    fincaEditando?.let { finca ->
        AlertDialog(
            onDismissRequest = { fincaEditando = null },
            title = { Text("Renombrar finca") },
            text = {
                OutlinedTextField(
                    value = nombreEditando,
                    onValueChange = { nombreEditando = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renombrar(finca, nombreEditando)
                        fincaEditando = null
                    },
                    enabled = nombreEditando.isNotBlank()
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { fincaEditando = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    fincaEliminando?.let { finca ->
        AlertDialog(
            onDismissRequest = { fincaEliminando = null },
            title = { Text("Eliminar finca ${finca.finca}") },
            text = {
                Text(
                    "Se borra definitivamente. Si la finca vuelve a aparecer en pedidos " +
                        "nuevos, se volverá a importar automáticamente."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.eliminar(finca.finca)
                        fincaEliminando = null
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { fincaEliminando = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestión de fincas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Las fincas detectadas en pedidos y las dadas de alta manualmente se asignan " +
                    "a cada empleado desde Gestión de usuarios. Puedes renombrarlas, ocultarlas " +
                    "(el interruptor oculta/muestra: las ocultas no se ofrecen al empleado, pero sus " +
                    "pedidos siguen sincronizándose) o eliminarlas definitivamente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = nuevaFinca,
                    onValueChange = { nuevaFinca = it },
                    label = { Text("Nueva finca") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        viewModel.crear(nuevaFinca)
                        nuevaFinca = ""
                    },
                    enabled = nuevaFinca.isNotBlank()
                ) {
                    Text("Añadir")
                }
            }

            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            } else if (state.fincas.isEmpty()) {
                Text(
                    "Sin fincas todavía",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.fincas.forEach { finca ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(finca.nombre, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (finca.oculto) "Oculta" else if (finca.manual) "Manual" else "Automática (de pedidos)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    fincaEditando = finca
                                    nombreEditando = finca.nombre
                                }
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "Renombrar ${finca.finca}")
                            }
                            Switch(
                                checked = !finca.oculto,
                                onCheckedChange = { checked ->
                                    viewModel.cambiarOcultacion(finca, ocultar = !checked)
                                },
                                enabled = finca.finca !in state.cambiando,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(onClick = { fincaEliminando = finca }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Eliminar ${finca.finca}",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
