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

@OptIn(ExperimentalCoroutinesApi::class)
class OrderListViewModel(
    private val repository: PickingRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val api = PickingApiClient()
    private val _syncState = MutableStateFlow(SyncUiState())
    val syncState: StateFlow<SyncUiState> = _syncState

    private val _selectedDays = MutableStateFlow(setOf(LocalDate.now()))
    val selectedDays: StateFlow<Set<LocalDate>> = _selectedDays

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
        combine(allOrders, availableDays, _selectedDays) { list, avail, days ->
            val availSet = avail.toSet()
            val effective = days.filter { it in availSet }.distinct()
            val daysToShow = if (effective.isNotEmpty()) {
                effective
            } else {
                val first = avail.firstOrNull()
                if (first == null) emptySet() else setOf(first)
            }
            list.filter { o ->
                val date = o.fechaCarga?.let {
                    java.time.Instant.ofEpochMilli(it)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                }
                date != null && !date.isBefore(today) && date in daysToShow
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleDay(date: LocalDate) {
        val current = _selectedDays.value
        _selectedDays.value = if (date in current) current - date else current + date
    }

    fun syncOrders() {
        if (_syncState.value.syncing) return
        viewModelScope.launch {
            _syncState.value = SyncUiState(syncing = true)
            try {
                val encargado = repository.currentEncargado()
                val fincas = encargado?.fincasCarga
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                val desde = LocalDate.now().toString()
                val hasta = LocalDate.now().plusDays(8).toString()
                val result = repository.syncFromApi(
                    api = api,
                    desde = desde,
                    hasta = hasta,
                    finca = if (fincas.orEmpty().size == 1) fincas!!.first() else null,
                    fincas = if ((fincas.orEmpty().size > 1)) fincas else null
                )
                _syncState.value = SyncUiState(
                    lastResult = "Sincronizado: ${result.pedidos} pedidos, " +
                        "${result.lineas} líneas, ${result.productos} artículos"
                )
            } catch (e: Exception) {
                Log.e("PickingVE", "sync failed", e)
                _syncState.value = SyncUiState(
                    lastError = "Error al sincronizar: ${e.message}"
                )
            }
        }
    }

    fun clearSyncMessage() {
        _syncState.value = _syncState.value.copy(lastResult = null, lastError = null)
    }
}
