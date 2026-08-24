package com.vivero.pickingve.ui.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivero.pickingve.data.remote.ApiFinca
import com.vivero.pickingve.data.remote.PickingApiClient
import com.vivero.pickingve.util.Errores
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AdminFincasUiState(
    val loading: Boolean = false,
    val fincas: List<ApiFinca> = emptyList(),
    val cambiando: Set<String> = emptySet(),
    val mensaje: String? = null,
    val error: String? = null
)

class AdminFincasViewModel(
    private val api: PickingApiClient = PickingApiClient()
) : ViewModel() {

    private val _state = MutableStateFlow(AdminFincasUiState(loading = true))
    val state: StateFlow<AdminFincasUiState> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            try {
                val fincas = api.fetchFincasGestion()
                _state.value = AdminFincasUiState(loading = false, fincas = fincas)
            } catch (e: Exception) {
                Log.e("PickingVE", "admin fincas load failed", e)
                _state.value = AdminFincasUiState(
                    loading = false,
                    error = "No se pudo cargar el listado: ${Errores.traducir(e)}"
                )
            }
        }
    }

    fun crear(nombre: String) {
        val finca = nombre.trim()
        if (finca.isEmpty()) {
            _state.value = _state.value.copy(error = "Indica el nombre de la finca")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(mensaje = null, error = null)
            try {
                api.crearFinca(finca)
                _state.value = _state.value.copy(mensaje = "Finca $finca dada de alta")
                load()
            } catch (e: Exception) {
                Log.e("PickingVE", "crear finca failed", e)
                _state.value = _state.value.copy(error = "Error al guardar: ${Errores.traducir(e)}")
            }
        }
    }

    fun renombrar(finca: ApiFinca, nuevoNombre: String) {
        val nombre = nuevoNombre.trim()
        if (nombre.isEmpty()) {
            _state.value = _state.value.copy(error = "El nombre no puede quedar vacío")
            return
        }
        if (nombre.equals(finca.finca, ignoreCase = true)) {
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(mensaje = null, error = null)
            try {
                api.crearFinca(
                    finca = finca.finca,
                    nombre = nombre,
                    activo = !finca.oculto
                )
                _state.value = _state.value.copy(mensaje = "Nombre actualizado")
                load()
            } catch (e: Exception) {
                Log.e("PickingVE", "renombrar finca failed", e)
                _state.value = _state.value.copy(error = "Error al guardar: ${Errores.traducir(e)}")
            }
        }
    }

    fun cambiarOcultacion(finca: ApiFinca, ocultar: Boolean) {
        if (finca.finca in _state.value.cambiando) return
        _state.value = _state.value.copy(
            mensaje = null,
            error = null,
            fincas = _state.value.fincas.map { if (it.finca == finca.finca) it.copy(oculto = ocultar) else it },
            cambiando = _state.value.cambiando + finca.finca
        )
        viewModelScope.launch {
            try {
                api.crearFinca(
                    finca = finca.finca,
                    nombre = finca.nombre,
                    activo = !ocultar
                )
                _state.value = _state.value.copy(
                    cambiando = _state.value.cambiando - finca.finca,
                    mensaje = if (ocultar) "Finca ocultada" else "Finca visible"
                )
            } catch (e: Exception) {
                Log.e("PickingVE", "cambiar ocultacion finca failed", e)
                _state.value = _state.value.copy(
                    fincas = _state.value.fincas.map { if (it.finca == finca.finca) finca else it },
                    cambiando = _state.value.cambiando - finca.finca,
                    error = "Error al guardar: ${Errores.traducir(e)}"
                )
            }
        }
    }

    fun eliminar(finca: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(mensaje = null, error = null)
            try {
                api.eliminarFinca(finca)
                _state.value = _state.value.copy(mensaje = "Finca $finca eliminada")
                load()
            } catch (e: Exception) {
                Log.e("PickingVE", "eliminar finca failed", e)
                _state.value = _state.value.copy(error = "Error al eliminar: ${Errores.traducir(e)}")
            }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(mensaje = null, error = null)
    }
}
