package com.vivero.pickingve.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vivero.pickingve.data.local.entities.EncargadoEntity
import com.vivero.pickingve.ui.theme.BrandGreen

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedUsuario by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }

    if (state.success) {
        onLoginSuccess()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "PickingVE",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Acceso de encargados y operarios",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 40.dp))
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "Encargados",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(state.encargados, key = { "E-${it.id}" }) { enc ->
                    UsuarioRow(
                        titulo = enc.nombre,
                        subtitulo = if (enc.rol.isBlank()) "ENCARGADO" else enc.rol,
                        selected = selectedUsuario == LoginViewModel.PREFIJO_ENCARGADO + enc.usuario,
                        onSelect = { selectedUsuario = LoginViewModel.PREFIJO_ENCARGADO + enc.usuario }
                    )
                }
                item {
                    Text(
                        text = "Operarios de acopio",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(state.operarios, key = { "O-${it.id}" }) { op ->
                    UsuarioRow(
                        titulo = "${op.nombre} ${op.apellidos}".trim(),
                        subtitulo = "OPERARIO" + if (op.maquinaria.isNotBlank()) " · ${op.maquinaria}" else "",
                        selected = selectedUsuario == LoginViewModel.PREFIJO_OPERARIO + op.email,
                        onSelect = { selectedUsuario = LoginViewModel.PREFIJO_OPERARIO + op.email }
                    )
                }
                if (state.encargados.isEmpty() && state.operarios.isEmpty()) {
                    item {
                        Text(
                            text = state.error ?: "No hay usuarios descargados",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            var showPassword by remember { mutableStateOf(false) }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("ContraseÃ±a") },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                enabled = selectedUsuario != null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandGreen,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = BrandGreen,
                    focusedLabelColor = BrandGreen
                ),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (showPassword) "Ocultar contraseÃ±a" else "Mostrar contraseÃ±a",
                            tint = if (selectedUsuario != null) BrandGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (state.error != null) {
                Text(
                    text = state.error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Button(
                onClick = {
                    if (selectedUsuario != null) {
                        viewModel.login(selectedUsuario!!, password)
                    }
                },
                enabled = selectedUsuario != null && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandGreen,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text("Entrar")
            }
        }
    }
}

@Composable
private fun UsuarioRow(
    titulo: String,
    subtitulo: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = subtitulo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}