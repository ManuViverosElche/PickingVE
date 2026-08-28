package com.vivero.pickingve.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vivero.pickingve.R
import com.vivero.pickingve.ui.theme.BrandGreen
import com.vivero.pickingve.ui.theme.BrandRed

/**
 * D-192: acceso unificado — usuario (encargado) o email (operario) + contraseña.
 * Sin listas: cada vez habra mas personas y el sistema detecta el tipo solo.
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var usuario by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    if (state.success) {
        onLoginSuccess()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Image(
            painter = painterResource(R.drawable.logo_viveros),
            contentDescription = "Logo Viveros Elche",
            modifier = Modifier
                .height(130.dp)
                .width(130.dp)
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Logística Viveros Elche",
            style = MaterialTheme.typography.titleLarge,
            color = BrandGreen,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(Modifier.height(60.dp))

        OutlinedTextField(
            value = usuario,
            onValueChange = { usuario = it.trim() },
            label = { Text("Email") },
            singleLine = true,
            enabled = !state.loading,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedBorderColor = BrandGreen,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = BrandGreen,
                focusedLabelColor = BrandGreen
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            enabled = !state.loading,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedBorderColor = BrandGreen,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = BrandGreen,
                focusedLabelColor = BrandGreen
            ),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showPassword) "Ocultar contraseña" else "Mostrar contraseña",
                        tint = BrandGreen
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally)
        )

        Button(
            onClick = {
                if (showPassword) showPassword = false
                viewModel.login(usuario, password)
            },
            enabled = !state.loading && usuario.isNotBlank() && password.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandGreen,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(56.dp)
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Text(if (state.loading) "Accediendo…" else "Entrar", fontWeight = FontWeight.Bold)
        }

        // D-239: error de autenticación en Rojo Corporativo (#962622).
        state.error?.let { error ->
            Text(
                text = error,
                color = BrandRed,
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
        }

        Spacer(Modifier.weight(1.5f))
    }
}
