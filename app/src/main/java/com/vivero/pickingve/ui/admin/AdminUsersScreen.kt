package com.vivero.pickingve.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val ROLES = listOf("ENCARGADO", "SUPERUSUARIO")
private val MODOS = listOf("PICKING", "INVENTARIO", "AMBAS")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(
    viewModel: AdminUsersViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var nombre by remember { mutableStateOf("") }
    var usuario by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rol by remember { mutableStateOf("ENCARGADO") }
    var modo by remember { mutableStateOf("PICKING") }
    var email by remember { mutableStateOf("") }
    var activo by remember { mutableStateOf(true) }
    var fincasSel by remember { mutableStateOf(setOf<String>()) }

    val editando = state.editando

    LaunchedEffect(editando) {
        if (editando != null) {
            nombre = editando.nombre
            usuario = editando.usuario
            password = ""
            rol = editando.rol
            modo = editando.modo
            email = editando.email
            activo = editando.activo
            fincasSel = editando.fincasCarga
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }
    }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            nombre = ""
            usuario = ""
            password = ""
            rol = "ENCARGADO"
            modo = "PICKING"
            email = ""
            activo = true
            fincasSel = emptySet()
        }
    }
    LaunchedEffect(state.mensaje) {
        state.mensaje?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    val editing = editando != null
    val requiresPassword = !editing && (password.isBlank() || password.length < 4)
    val canSubmit = nombre.isNotBlank() && usuario.isNotBlank() && !requiresPassword &&
        email.isNotBlank() && email.contains("@")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestión de usuarios") },
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
            if (editing) {
                Text("Editando: ${editando!!.nombre}", style = MaterialTheme.typography.titleMedium)
            } else {
                Text("Nuevo usuario", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre completo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = usuario,
                onValueChange = { usuario = it },
                label = { Text("Usuario (login)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(if (editing) "Nueva contraseña (vacío = no cambia)" else "Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email (va en el parte)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Estado", style = MaterialTheme.typography.bodyMedium)
            Column {
                FilterChip(
                    selected = activo,
                    onClick = { activo = true },
                    label = { Text("Activo") },
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                FilterChip(
                    selected = !activo,
                    onClick = { activo = false },
                    label = { Text("Dado de baja") },
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Text("Rol", style = MaterialTheme.typography.bodyMedium)
            Column {
                ROLES.forEach { option ->
                    FilterChip(
                        selected = rol == option,
                        onClick = { rol = option },
                        label = { Text(option) },
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            Text("Modo de trabajo", style = MaterialTheme.typography.bodyMedium)
            Column {
                MODOS.forEach { option ->
                    FilterChip(
                        selected = modo == option,
                        onClick = { modo = option },
                        label = { Text(option) },
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            Text("Fincas de carga asignadas", style = MaterialTheme.typography.bodyMedium)
            Text(
                // D-185: la restriccion de finca solo aplica a tractores/toros;
                // el resto de maquinaria se mueve por todas las fincas.
                "Solo limita a tractores/toros. Buggy, carretilla o palet: " +
                    "no marques ninguna (podra moverse por todas).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                state.fincas.forEach { finca ->
                    FilterChip(
                        selected = finca in fincasSel,
                        onClick = {
                            fincasSel = if (finca in fincasSel) fincasSel - finca
                            else fincasSel + finca
                        },
                        label = { Text(finca) },
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.guardar(
                                nombre = nombre,
                                usuario = usuario,
                                password = password,
                                rol = rol,
                                modo = modo,
                                email = email,
                                activo = activo,
                                fincasSeleccionadas = fincasSel
                            )
                        }
                    },
                    enabled = canSubmit,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (editing) "Guardar cambios" else "Dar de alta")
                }
                if (editing) {
                    OutlinedButton(
                        onClick = { viewModel.cancelarEdicion() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar edición")
                    }
                }
            }

            HorizontalDivider()

            Text("Usuarios dados de alta", style = MaterialTheme.typography.titleMedium)
            Text(
                "Toca un usuario para editarlo (email, rol, modo, estado, fincas o contraseña).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.encargados.isEmpty()) {
                Text(
                    "Sin usuarios todavía",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.encargados.forEach { e ->
                Card(
                    onClick = { viewModel.empezarEdicion(e) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(e.nombre, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (e.activo) e.modo else "${e.modo} · BAJA",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (e.activo) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                        Text(
                            "@${e.usuario} · ${e.rol}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (e.email.isNotBlank()) {
                            Text(
                                e.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (e.fincasCarga.isNotBlank()) {
                            Text(
                                "Fincas: ${e.fincasCarga}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}