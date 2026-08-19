package com.vivero.pickingve.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.vivero.pickingve.data.local.dao.OrderWithTotals
import com.vivero.pickingve.data.remote.PickingApiClient
import com.vivero.pickingve.data.repository.PickingRepository
import com.vivero.pickingve.data.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SyncUiState(
    val syncing: Boolean = false,
    val lastResult: String? = null,
    val lastError: String? = null
)

data class UploadUiState(
    val uploading: Boolean = false,
    val lastResult: String? = null,
    val lastError: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class OrderListViewModel(
    private val repository: PickingRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val api = PickingApiClient()
    private val _syncState = MutableStateFlow(SyncUiState())
    val syncState: StateFlow<SyncUiState> = _syncState

    private val _uploadState = MutableStateFlow(UploadUiState())
    val uploadState: StateFlow<UploadUiState> = _uploadState

    val pendingUploadCount: StateFlow<Int> = repository
        .observePendingBigQuery()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _selectedDays = MutableStateFlow(initialSelectedDays())
    val selectedDays: StateFlow<Set<LocalDate>> = _selectedDays

    private val _assignedFincas = MutableStateFlow(parseFincas(repository.currentEncargado()?.fincasCarga))
    val assignedFincas: StateFlow<List<String>> = _assignedFincas

    private val _selectedFincas = MutableStateFlow(initialSelectedFincas())
    val selectedFincas: StateFlow<Set<String>> = _selectedFincas

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val allOrders: StateFlow<List<OrderWithTotals>> = repository
        .observeOrdersWithTotals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val today = LocalDate.now()

    val availableDays: StateFlow<List<LocalDate>> = allOrders
        .map { orders ->
            orders.mapNotNull { o ->
                o.fechaCarga?.let {
                    java.time.Instant.ofEpochMilli(it)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                }
            }
            .filter { !it.isBefore(today) }
            .distinct()
            .sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val orders: StateFlow<List<OrderWithTotals>> =
        combine(
            combine(allOrders, _searchQuery) { list, query -> list to query },
            availableDays, _selectedDays, _assignedFincas, _selectedFincas
        ) { base, avail, days, fincas, sel ->
            val (list, query) = base
            val availSet = avail.toSet()
            val effective = days.filter { it in availSet }.distinct()
            val daysToShow = if (effective.isNotEmpty()) {
                effective
            } else {
                val first = avail.firstOrNull()
                if (first == null) emptySet() else setOf(first)
            }
            val fincaFilterActive = fincas.isNotEmpty() && sel.isNotEmpty()
            val q = query.trim()
            list.filter { o ->
                val date = o.fechaCarga?.let {
                    java.time.Instant.ofEpochMilli(it)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                }
                val dateOk = if (q.isNotEmpty()) {
                    true
                } else {
                    date != null && !date.isBefore(today) && date in daysToShow
                }
                val fincaOk = q.isNotEmpty() || !fincaFilterActive || sel.any { it.equals(o.fincaCarga, ignoreCase = true) }
                val queryOk = q.isEmpty() || listOf(
                    o.orderId, o.customerName, o.customerFiscal, o.marcaPedido,
                    o.fincaCarga, o.sectorCarga, o.muelleCarga, o.observaciones
                ).any { it.contains(q, ignoreCase = true) }
                dateOk && fincaOk && queryOk
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleDay(date: LocalDate) {
        val current = _selectedDays.value
        val next = if (date in current) current - date else current + date
        _selectedDays.value = next
        repository.saveSelectedDays(next.map { it.toString() }.toSet())
    }

    fun toggleFinca(finca: String) {
        val current = _selectedFincas.value
        val next = if (finca in current) current - finca else current + finca
        _selectedFincas.value = next
        repository.saveSelectedFincas(next)
    }

    fun syncOrders() {
        if (_syncState.value.syncing) return
        viewModelScope.launch {
            _syncState.value = SyncUiState(syncing = true)
            try {
                repository.syncEncargados(api)
                repository.refreshCurrentEncargadoFromLocal()
                val fincas = parseFincas(repository.currentEncargado()?.fincasCarga)
                _assignedFincas.value = fincas
                reconcileSelection(fincas)
                val selected = _selectedFincas.value
                val desde = LocalDate.now().toString()
                val hasta = LocalDate.now().plusDays(8).toString()
                val result = repository.syncFromApi(
                    api = api,
                    desde = desde,
                    hasta = hasta,
                    finca = if (selected.size == 1) selected.first() else null,
                    fincas = if (selected.size > 1) selected.toList() else null
                )
                _syncState.value = SyncUiState(
                    lastResult = "Sincronizado: ${result.pedidos} pedidos, " +
                        "${result.lineas} líneas, " +
                        if (result.productos > 0) "${result.productos} artículos"
                        else "catálogo sin cambios"
                )
            } catch (e: Exception) {
                Log.e("PickingVE", "sync failed", e)
                _syncState.value = SyncUiState(
                    lastError = "Error al sincronizar: ${e.message}"
                )
            }
        }
    }

    private fun initialSelectedFincas(): Set<String> {
        val saved = repository.selectedFincas()
        val assigned = _assignedFincas.value
        val valid = saved.intersect(assigned.toSet())
        return if (valid.isNotEmpty()) valid else assigned.toSet()
    }

    private fun initialSelectedDays(): Set<LocalDate> {
        val hoy = LocalDate.now()
        val saved = repository.selectedDays()
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .filter { !it.isBefore(hoy) }
            .toSet()
        return if (saved.isNotEmpty()) saved else setOf(hoy)
    }

    private fun reconcileSelection(assigned: List<String>) {
        val assignedSet = assigned.toSet()
        val valid = _selectedFincas.value.intersect(assignedSet)
        val next = if (valid.isNotEmpty()) valid else assignedSet
        _selectedFincas.value = next
        repository.saveSelectedFincas(next)
    }

    private fun parseFincas(raw: String?): List<String> = raw
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

    fun clearSyncMessage() {
        _syncState.value = _syncState.value.copy(lastResult = null, lastError = null)
    }

    fun uploadNow() {
        if (_uploadState.value.uploading) return
        viewModelScope.launch {
            _uploadState.value = UploadUiState(uploading = true)
            try {
                val count = repository.uploadPendingRegistros(api)
                _uploadState.value = UploadUiState(
                    lastResult = if (count > 0) "Subidos $count registros" else "Nada pendiente de subir"
                )
            } catch (e: Exception) {
                Log.e("PickingVE", "upload failed", e)
                _uploadState.value = UploadUiState(
                    lastError = "Error al subir: ${e.message}"
                )
            }
        }
    }

    fun clearUploadMessage() {
        _uploadState.value = _uploadState.value.copy(lastResult = null, lastError = null)
    }
}
