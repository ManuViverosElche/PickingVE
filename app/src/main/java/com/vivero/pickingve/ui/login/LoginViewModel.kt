package com.vivero.pickingve.ui.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivero.pickingve.data.local.entities.EncargadoEntity
import com.vivero.pickingve.data.local.entities.OperarioEntity
import com.vivero.pickingve.data.remote.PickingApiClient
import com.vivero.pickingve.data.repository.PickingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class LoginUiState(
    val loading: Boolean = false,
    val encargados: List<EncargadoEntity> = emptyList(),
    val operarios: List<OperarioEntity> = emptyList(),
    val error: String? = null,
    val success: Boolean = false
)

class LoginViewModel(
    private val repository: PickingRepository
) : ViewModel() {

    companion object {
        const val PREFIJO_ENCARGADO = "E:"
        const val PREFIJO_OPERARIO = "O:"
    }

    private val api = PickingApiClient()
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

    init {
        cargarUsuarios()
    }

    fun cargarUsuarios() {
        viewModelScope.launch {
            _state.value = LoginUiState(
                encargados = repository.encargadosLocales().filter { it.activo },
                operarios = repository.operariosLocales()
            )
            try {
                repository.syncEncargados(api)
            } catch (e: Exception) {
                Log.e("PickingVE", "sync encargados failed", e)
            }
            try {
                repository.syncOperarios(api)
            } catch (e: Exception) {
                Log.e("PickingVE", "sync operarios failed", e)
            }
            val encargados = repository.encargadosLocales().filter { it.activo }
            val operarios = repository.operariosLocales()
            _state.value = _state.value.copy(
                encargados = encargados,
                operarios = operarios,
                loading = false,
                error = if (encargados.isEmpty() && operarios.isEmpty()) {
                    "Sin conexión y sin usuarios descargados"
                } else {
                    null
                }
            )
        }
    }

    /** `seleccion` viene con prefijo "E:<usuario>" o "O:<email>". */
    fun login(seleccion: String, password: String) {
        if (password.isBlank()) {
            _state.value = _state.value.copy(error = "Introduce la contraseña")
            return
        }
        viewModelScope.launch {
            when {
                seleccion.startsWith(PREFIJO_OPERARIO) -> {
                    val email = seleccion.removePrefix(PREFIJO_OPERARIO)
                    val local = repository.loginOperarioLocal(email, password)
                    if (local != null) {
                        registrarTokenPush()
                        _state.value = LoginUiState(success = true)
                    } else {
                        val remoto = repository.loginOperarioRemoto(api, email, password)
                        if (remoto != null) {
                            registrarTokenPush()
                            _state.value = LoginUiState(success = true)
                        } else {
                            _state.value = _state.value.copy(
                                error = "Usuario o contraseña incorrectos"
                            )
                        }
                    }
                }
                else -> {
                    val usuario = seleccion.removePrefix(PREFIJO_ENCARGADO)
                    val local = repository.loginEncargadoLocal(usuario, password)
                    if (local != null) {
                        registrarTokenPush()
                        _state.value = LoginUiState(success = true)
                    } else {
                        val remoto = repository.loginEncargadoRemoto(api, usuario, password)
                        if (remoto) {
                            registrarTokenPush()
                            _state.value = LoginUiState(success = true)
                        } else {
                            _state.value = _state.value.copy(
                                error = "Usuario o contraseña incorrectos"
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun registrarTokenPush() {
        try {
            val email = repository.currentEncargado()?.email
                ?: repository.currentOperario()?.email
                ?: return
            if (email.isBlank()) return
            val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
            api.registrarFcmToken(email, token)
        } catch (e: Exception) {
            Log.e("PickingVE", "Registro token FCM fallido", e)
        }
    }
}
