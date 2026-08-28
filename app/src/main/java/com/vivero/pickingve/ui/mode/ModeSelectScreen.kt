package com.vivero.pickingve.ui.mode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSelectScreen(
    encargadoNombre: String,
    mostrarFaena: Boolean = true,
    mostrarPanel: Boolean = false,
    onPanel: () -> Unit = {},
    onPicking: () -> Unit,
    onInventario: () -> Unit,
    onLogistica: () -> Unit = {},
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bienvenido, $encargadoNombre") },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Salir")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Selecciona el modo de trabajo", style = MaterialTheme.typography.titleMedium)
            // D-208: "Mi faena" oculta para SUPERUSUARIO sin rol de operario activo.
            if (mostrarFaena) {
                Button(
                    onClick = onLogistica,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Mi faena (logística)")
                }
            }
            // D-213: panel de logistica (paridad con el panel web) para superusuarios.
            if (mostrarPanel) {
                Button(
                    onClick = onPanel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Panel de logística")
                }
            }
            OutlinedButton(
                onClick = onPicking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Picking")
            }
            OutlinedButton(
                onClick = onInventario,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Inventario")
            }
        }
    }
}