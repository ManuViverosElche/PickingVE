package com.vivero.pickingve.ui.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivero.pickingve.data.remote.ApiEncargado
import com.vivero.pickingve.data.remote.CrearEncargadoRequest
import com.vivero.pickingve.data.remote.PickingApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AdminUiState(
    val loading: Boolean = false,
    val fincas: List<String> = emptyList(),
    val encargados: List<ApiEncargado> = emptyList(),
    val editando: ApiEncargado? = null,
    val mensaje: String? = null,
    val error: String? = null,
    val saved: Boolean = false
)

class AdminUsersViewModel(
    private val api: PickingApiClient = PickingApiClient()
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUiState(loading = true))
    val state: StateFlow<AdminUiState> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = AdminUiState(loading = true)
            try {
                val fincas = api.fetchFincas()
                val encargados = api.fetchIngresos()
                _state.value = AdminUiState(loading = false, fincas = fincas, encargados = encargados)
            } catch (e: Exception) {
                Log.e("PickingVE", "admin load failed", e)
                _state.value = AdminUiState(
                    loading = false,
                    error = "No se pudo cargar el listado: ${e.message}"
                )
            }
        }
    }

    fun empezarEdicion(enc: ApiEncargado) {
        _state.value = _state.value.copy(editando = enc, mensaje = null, error = null)
    }

    fun cancelarEdicion() {
        _state.value = _state.value.copy(editando = null)
    }

    fun guardar(
        nombre: String,
        usuario: String,
        password: String,
        rol: String,
        modo: String,
        fincasSeleccionadas: Set<String>
    ) {
        val editando = _state.value.editando
        if (editando == null && password.length < 4) {
            _state.value = _state.value.copy(error = "La contraseña debe tener al menos 4 caracteres")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(mensaje = null, error = null, saved = false)
            try {
                val id = editando?.id ?: "E${System.currentTimeMillis()}"
                api.crearEncargado(
                    CrearEncargadoRequest(
                        id = id,
                        nombre = nombre.trim(),
                        usuario = usuario.trim(),
                        password = password,
                        rol = rol,
                        fincasCarga = fincasSeleccionadas.sorted().joinToString(", "),
                        modo = modo
                    )
                )
                val who = usuario.trim()
                _state.value = _state.value.copy(
                    editando = null,
                    saved = true,
                    mensaje = if (editando != null) {
                        "Usuario $who actualizado"
                    } else {
                        "Usuario $who dado de alta"
                    }
                )
                load()
            } catch (e: Exception) {
                Log.e("PickingVE", "guardar encargado failed", e)
                _state.value = _state.value.copy(error = "Error al guardar: ${e.message}")
            }
        }
    }
}