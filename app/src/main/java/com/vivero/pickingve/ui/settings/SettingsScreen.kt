package com.vivero.pickingve.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    isSuperUser: Boolean = false,
    onOpenUsers: () -> Unit = {}
) {
    val settings by viewModel.settings.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var token by remember(settings.telegramBotToken) { mutableStateOf(settings.telegramBotToken) }
    var chatId by remember(settings.telegramChatId) { mutableStateOf(settings.telegramChatId) }
    var email by remember(settings.operatorEmail) { mutableStateOf(settings.operatorEmail) }

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
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo empleado (fila Correo empleado)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    viewModel.save(
                        telegramBotToken = token.trim(),
                        telegramChatId = chatId.trim(),
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

            if (isSuperUser) {
                HorizontalDivider()
                Text("Administración", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = onOpenUsers,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Gestión de usuarios")
                }
            }
        }
    }
}