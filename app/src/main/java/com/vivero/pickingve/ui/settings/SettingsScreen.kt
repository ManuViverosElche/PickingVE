package com.vivero.pickingve.ui.settings

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    isSuperUser: Boolean = false,
    onOpenUsers: () -> Unit = {},
    onOpenFincas: () -> Unit = {}
) {
    val settings by viewModel.settings.collectAsState()
    val passwordState by viewModel.passwordState.collectAsState()
    val emailState by viewModel.emailState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val encargado = viewModel.currentEncargado()

    var token by remember(settings.telegramBotToken) { mutableStateOf(settings.telegramBotToken) }
    var chatId by remember(settings.telegramChatId) { mutableStateOf(settings.telegramChatId) }
    var labelsToken by remember(settings.labelsBotToken) { mutableStateOf(settings.labelsBotToken) }
    var labelsChatId by remember(settings.labelsChatId) { mutableStateOf(settings.labelsChatId) }
    var email by remember(settings.operatorEmail) { mutableStateOf(settings.operatorEmail) }
    var passwordActual by remember { mutableStateOf("") }
    var passwordNueva by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var editingEmail by remember { mutableStateOf(false) }
    var newEmail by remember { mutableStateOf("") }

    LaunchedEffect(passwordState.mensaje, passwordState.error) {
        val message = passwordState.mensaje ?: passwordState.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearPasswordMessage()
            passwordActual = ""
            passwordNueva = ""
            passwordConfirm = ""
        }
    }

    LaunchedEffect(emailState.mensaje, emailState.error) {
        val message = emailState.mensaje ?: emailState.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearEmailMessage()
            editingEmail = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Cuenta", style = MaterialTheme.typography.titleMedium)
            if (encargado != null) {
                Text(
                    "${encargado.nombre} (${encargado.usuario})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        "Correo: ${encargado.email.ifBlank { "—" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        newEmail = encargado.email
                        editingEmail = true
                    }) {
                        Text("Editar")
                    }
                }
                if (editingEmail) {
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        label = { Text("Nuevo correo") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.cambiarEmail(newEmail) },
                            enabled = !emailState.changing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (emailState.changing) "Guardando..." else "Guardar correo")
                        }
                        TextButton(onClick = { editingEmail = false }) { Text("Cancelar") }
                    }
                }
            }
            Text(
                "Correo que se pone en la fila 'Correo empleado' del parte.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo alternativo (parte)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    viewModel.save(
                        telegramBotToken = token.trim(),
                        telegramChatId = chatId.trim(),
                        labelsBotToken = labelsToken.trim(),
                        labelsChatId = labelsChatId.trim(),
                        operatorEmail = email.trim()
                    )
                    scope.launch {
                        snackbarHostState.showSnackbar("Ajustes guardados")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar")
            }
            Text(
                "Cómo obtener el Chat ID: envía un mensaje al bot y consulta " +
                    "https://api.telegram.org/bot<TOKEN>/getUpdates",
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider()
            Text("Bot de etiquetas", style = MaterialTheme.typography.titleMedium)
            Text(
                "Las etiquetas (maceta rota, cambio de formato) se piden a este bot. " +
                    "Si se deja vacío, se usa el bot del parte.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = labelsToken,
                onValueChange = { labelsToken = it },
                label = { Text("Token del bot de etiquetas") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = labelsChatId,
                onValueChange = { labelsChatId = it },
                label = { Text("Chat ID del bot de etiquetas") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()
            Text("Contraseña", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = passwordActual,
                onValueChange = { passwordActual = it },
                label = { Text("Contraseña actual") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = passwordNueva,
                onValueChange = { passwordNueva = it },
                label = { Text("Nueva contraseña (mín. 4 caracteres)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = passwordConfirm,
                onValueChange = { passwordConfirm = it },
                label = { Text("Confirmar nueva contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    if (passwordNueva != passwordConfirm) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Las contraseñas no coinciden")
                        }
                    } else {
                        viewModel.cambiarPassword(passwordActual, passwordNueva)
                    }
                },
                enabled = !passwordState.changing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (passwordState.changing) "Cambiando..." else "Cambiar contraseña")
            }

            if (isSuperUser) {
                HorizontalDivider()
                Text("Administración", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = onOpenUsers,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Gestión de usuarios")
                }
                Button(
                    onClick = onOpenFincas,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Gestión de fincas")
                }
            }
        }
    }
}