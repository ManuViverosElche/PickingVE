package com.vivero.pickingve.ui.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivero.pickingve.data.local.entities.EncargadoEntity
import com.vivero.pickingve.data.local.entities.OperarioEntity
import com.vivero.pickingve.data.remote.PickingApiClient
import com.vivero.pickingve.data.repository.PickingRepository
import com.vivero.pickingve.util.Errores
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

    /**
     * D-192: login unificado — el usuario escribe SU USUARIO (encargado) o su
     * EMAIL (operario) y la app detecta el tipo probando ambas tablas.
     */
    fun login(seleccion: String, password: String) {
        if (password.isBlank()) {
            _state.value = _state.value.copy(error = "Introduce la contraseña")
            return
        }
        viewModelScope.launch {
            val esOperario = seleccion.startsWith(PREFIJO_OPERARIO)
            val clave = when {
                seleccion.startsWith(PREFIJO_OPERARIO) -> seleccion.removePrefix(PREFIJO_OPERARIO)
                seleccion.startsWith(PREFIJO_ENCARGADO) -> seleccion.removePrefix(PREFIJO_ENCARGADO)
                else -> seleccion
            }
            // D-192: si el texto parece email, probar primero como operario
            val orden: List<Int> = if (esOperario || clave.contains("@")) listOf(1, 0) else listOf(0, 1)
            var errorRed: String? = null
            for (tipo in orden) {
                if (tipo == 1) {
                    val local = repository.loginOperarioLocal(clave, password)
                    if (local != null) { registrarTokenPush(); _state.value = LoginUiState(success = true); return@launch }
                    val remoto = try {
                        repository.loginOperarioRemoto(api, clave, password)
                    } catch (e: Exception) {
                        Log.e("PickingVE", "loginOperarioRemoto excepcion: ${e.javaClass.name}: ${e.message}", e)
                        errorRed = Errores.traducir(e)
                        null
                    }
                    if (remoto != null) { registrarTokenPush(); _state.value = LoginUiState(success = true); return@launch }
                } else {
                    val local = repository.loginEncargadoLocal(clave, password)
                    if (local != null) { registrarTokenPush(); _state.value = LoginUiState(success = true); return@launch }
                    val remoto = try {
                        repository.loginEncargadoRemoto(api, clave, password)
                    } catch (e: Exception) {
                        Log.e("PickingVE", "loginEncargadoRemoto excepcion: ${e.javaClass.name}: ${e.message}", e)
                        errorRed = Errores.traducir(e)
                        false
                    }
                    if (remoto) { registrarTokenPush(); _state.value = LoginUiState(success = true); return@launch }
                }
            }
            _state.value = _state.value.copy(error = errorRed ?: "Usuario o contraseña incorrectos")
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
