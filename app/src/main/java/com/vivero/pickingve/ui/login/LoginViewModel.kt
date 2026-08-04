package com.vivero.pickingve.ui.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivero.pickingve.data.local.entities.EncargadoEntity
import com.vivero.pickingve.data.remote.PickingApiClient
import com.vivero.pickingve.data.repository.PickingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val loading: Boolean = false,
    val encargados: List<EncargadoEntity> = emptyList(),
    val error: String? = null,
    val success: Boolean = false
)

class LoginViewModel(
    private val repository: PickingRepository
) : ViewModel() {

    private val api = PickingApiClient()
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

    init {
        loadEncargados()
    }

    fun loadEncargados() {
        viewModelScope.launch {
            val locales = repository.encargadosLocales()
            if (locales.isNotEmpty()) {
                _state.value = LoginUiState(encargados = locales)
                return@launch
            }
            _state.value = LoginUiState(loading = true)
            try {
                repository.syncEncargados(api)
                _state.value = LoginUiState(encargados = repository.encargadosLocales())
            } catch (e: Exception) {
                Log.e("PickingVE", "sync encargados failed", e)
                _state.value = LoginUiState(
                    error = "Sin conexión y sin encargados descargados"
                )
            }
        }
    }

    fun login(usuario: String, password: String) {
        if (password.isBlank()) {
            _state.value = _state.value.copy(error = "Introduce la contraseña")
            return
        }
        viewModelScope.launch {
            val local = repository.loginEncargadoLocal(usuario, password)
            if (local != null) {
                _state.value = LoginUiState(success = true)
            } else {
                val remoto = repository.loginEncargadoRemoto(api, usuario, password)
                if (remoto) {
                    _state.value = LoginUiState(success = true)
                } else {
                    _state.value = _state.value.copy(error = "Usuario o contraseña incorrectos")
                }
            }
        }
    }
}