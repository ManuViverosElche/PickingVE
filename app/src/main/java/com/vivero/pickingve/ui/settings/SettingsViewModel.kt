package com.vivero.pickingve.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivero.pickingve.data.remote.PickingApiClient
import com.vivero.pickingve.data.repository.PickingRepository
import com.vivero.pickingve.data.repository.SettingsRepository
import com.vivero.pickingve.data.repository.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PasswordUiState(
    val changing: Boolean = false,
    val mensaje: String? = null,
    val error: String? = null
)

data class EmailUiState(
    val changing: Boolean = false,
    val mensaje: String? = null,
    val error: String? = null
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val repository: PickingRepository
) : ViewModel() {

    private val api = PickingApiClient()

    val settings: StateFlow<SettingsStore> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), settingsRepository.load())

    private val _passwordState = MutableStateFlow(PasswordUiState())
    val passwordState: StateFlow<PasswordUiState> = _passwordState

    private val _emailState = MutableStateFlow(EmailUiState())
    val emailState: StateFlow<EmailUiState> = _emailState

    fun currentEncargado() = repository.currentEncargado()

    fun save(
        telegramBotToken: String,
        telegramChatId: String,
        labelsBotToken: String,
        labelsChatId: String,
        operatorEmail: String
    ) {
        settingsRepository.update {
            it.copy(
                telegramBotToken = telegramBotToken,
                telegramChatId = telegramChatId,
                labelsBotToken = labelsBotToken,
                labelsChatId = labelsChatId,
                operatorEmail = operatorEmail
            )
        }
    }

    fun cambiarEmail(nuevo: String) {
        val encargado = repository.currentEncargado()
        if (encargado == null) {
            _emailState.value = EmailUiState(error = "No hay sesión iniciada")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(nuevo.trim()).matches()) {
            _emailState.value = EmailUiState(error = "Correo no válido")
            return
        }
        viewModelScope.launch {
            _emailState.value = EmailUiState(changing = true)
            try {
                api.cambiarEmail(encargado.usuario, nuevo.trim())
                repository.updateEncargadoEmail(encargado.usuario, nuevo.trim())
                _emailState.value = EmailUiState(mensaje = "Correo actualizado")
            } catch (e: Exception) {
                Log.e("PickingVE", "cambiar email failed", e)
                _emailState.value = EmailUiState(error = "Error al cambiar el correo: ${e.message}")
            }
        }
    }

    fun clearEmailMessage() {
        _emailState.value = EmailUiState()
    }

    fun cambiarPassword(actual: String, nueva: String) {
        val encargado = repository.currentEncargado()
        if (encargado == null) {
            _passwordState.value = PasswordUiState(error = "No hay sesión iniciada")
            return
        }
        if (actual.isBlank() || nueva.length < 4) {
            _passwordState.value =
                PasswordUiState(error = "La nueva contraseña debe tener al menos 4 caracteres")
            return
        }
        if (actual == nueva) {
            _passwordState.value =
                PasswordUiState(error = "La nueva contraseña debe ser distinta de la actual")
            return
        }
        viewModelScope.launch {
            _passwordState.value = PasswordUiState(changing = true)
            try {
                api.cambiarPassword(encargado.usuario, actual, nueva)
                repository.updateEncargadoPassword(encargado.usuario, nueva)
                _passwordState.value = PasswordUiState(mensaje = "Contraseña actualizada")
            } catch (e: Exception) {
                Log.e("PickingVE", "cambiar password failed", e)
                _passwordState.value = PasswordUiState(error = "Error al cambiar: ${e.message}")
            }
        }
    }

    fun clearPasswordMessage() {
        _passwordState.value = PasswordUiState()
    }
}
