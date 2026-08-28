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
     * D-233: al cerrar sesión, limpia el estado del login. Sin esto, `success`
     * seguía a true tras un login y LoginScreen volvía a entrar solo (el cierre
     * de sesión "recargaba" la pantalla pero no salía).
     */
    fun reset() {
        _state.value = LoginUiState()
        cargarUsuarios()
    }

    /**
     * D-192/D-201: login unificado por email — la app detecta el tipo probando
     * ambas tablas. D-200: feedback inmediato (loading real) y registro FCM
     * fuera del camino critico para no ralentizar el acceso.
     */
    fun login(seleccion: String, password: String) {
        if (password.isBlank()) {
            _state.value = _state.value.copy(error = "Introduce la contraseña")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val esOperario = seleccion.startsWith(PREFIJO_OPERARIO)
                val clave = when {
                    seleccion.startsWith(PREFIJO_OPERARIO) -> seleccion.removePrefix(PREFIJO_OPERARIO)
                    seleccion.startsWith(PREFIJO_ENCARGADO) -> seleccion.removePrefix(PREFIJO_ENCARGADO)
                    else -> seleccion
                }
                // D-192: si el texto parece email, probar primero como operario
                val orden: List<Int> =
                    if (esOperario || clave.contains("@")) listOf(1, 0) else listOf(0, 1)
                var errorRed: String? = null
                bucle@ for (tipo in orden) {
                    if (tipo == 1) {
                        val local = repository.loginOperarioLocal(clave, password)
                        if (local != null) { exito(); return@launch }
                        val remoto = try {
                            repository.loginOperarioRemoto(api, clave, password)
                        } catch (e: Exception) {
                            Log.e("PickingVE", "loginOperarioRemoto excepcion: ${e.javaClass.name}: ${e.message}", e)
                            errorRed = mensajeLogin(e)
                            break@bucle
                        }
                        if (remoto != null) { exito(); return@launch }
                    } else {
                        val local = repository.loginEncargadoLocal(clave, password)
                        if (local != null) { exito(); return@launch }
                        val remoto = try {
                            repository.loginEncargadoRemoto(api, clave, password)
                        } catch (e: Exception) {
                            Log.e("PickingVE", "loginEncargadoRemoto excepcion: ${e.javaClass.name}: ${e.message}", e)
                            errorRed = mensajeLogin(e)
                            break@bucle
                        }
                        if (remoto) { exito(); return@launch }
                    }
                }
                _state.value = _state.value.copy(error = errorRed ?: "Usuario o contraseña incorrectos")
            } finally {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    private fun exito() {
        // D-200: el token push se registra en segundo plano; no bloquea entrar.
        viewModelScope.launch { registrarTokenPush() }
        _state.value = LoginUiState(success = true)
    }

    private fun mensajeLogin(e: Exception): String =
        if (Errores.esErrorDeRed(e)) Errores.SIN_CONEXION_LOGIN else Errores.traducir(e)

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
