package com.vivero.pickingve.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vivero.pickingve.data.repository.PickingRepository
import com.vivero.pickingve.data.repository.SettingsRepository
import com.vivero.pickingve.ui.admin.AdminFincasScreen
import com.vivero.pickingve.ui.admin.AdminFincasViewModel
import com.vivero.pickingve.ui.admin.AdminUsersScreen
import com.vivero.pickingve.ui.admin.AdminUsersViewModel
import com.vivero.pickingve.ui.login.LoginScreen
import com.vivero.pickingve.ui.login.LoginViewModel
import com.vivero.pickingve.ui.mode.InventarioScreen
import com.vivero.pickingve.ui.mode.ModeSelectScreen
import com.vivero.pickingve.ui.orders.OrderListScreen
import com.vivero.pickingve.ui.orders.OrderListViewModel
import com.vivero.pickingve.ui.picking.PickingScreen
import com.vivero.pickingve.ui.picking.PickingViewModel
import com.vivero.pickingve.ui.settings.SettingsScreen
import com.vivero.pickingve.ui.settings.SettingsViewModel

@Composable
fun AppNavHost(
    repository: PickingRepository,
    settingsRepository: SettingsRepository
) {

    val loginViewModel: LoginViewModel = viewModel { LoginViewModel(repository) }
    val orderListViewModel: OrderListViewModel =
        viewModel { OrderListViewModel(repository, settingsRepository) }
    val pickingViewModel: PickingViewModel =
        viewModel { PickingViewModel(repository, settingsRepository) }
    val settingsViewModel: SettingsViewModel =
        viewModel { SettingsViewModel(settingsRepository, repository) }
    val adminViewModel: AdminUsersViewModel =
        viewModel { AdminUsersViewModel() }
    val adminFincasViewModel: AdminFincasViewModel =
        viewModel { AdminFincasViewModel() }

    var loggedIn by remember { mutableStateOf(repository.currentEncargado() != null) }
    var screen by remember {
        mutableStateOf(initialScreen(repository.currentEncargado()?.modo))
    }

    if (!loggedIn) {
        LoginScreen(
            viewModel = loginViewModel,
            onLoginSuccess = {
                loggedIn = true
                screen = initialScreen(repository.currentEncargado()?.modo)
            }
        )
        return
    }

    when (screen) {
        AppScreen.ORDERS -> OrderListScreen(
            viewModel = orderListViewModel,
            onOrderSelected = { orderId ->
                pickingViewModel.selectOrder(orderId)
                screen = AppScreen.PICKING
            },
            onOpenSettings = { screen = AppScreen.SETTINGS },
            onLogout = {
                repository.logout()
                loggedIn = false
            }
        )

        AppScreen.PICKING -> PickingScreen(
            viewModel = pickingViewModel,
            onBack = { screen = AppScreen.ORDERS }
        )

        AppScreen.SETTINGS -> SettingsScreen(
            viewModel = settingsViewModel,
            isSuperUser = repository.currentEncargado()?.rol == "SUPERUSUARIO",
            onBack = { screen = AppScreen.ORDERS },
            onOpenUsers = { screen = AppScreen.USERS },
            onOpenFincas = { screen = AppScreen.FINCAS }
        )

        AppScreen.USERS -> AdminUsersScreen(
            viewModel = adminViewModel,
            onBack = { screen = AppScreen.SETTINGS }
        )

        AppScreen.FINCAS -> AdminFincasScreen(
            viewModel = adminFincasViewModel,
            onBack = { screen = AppScreen.SETTINGS }
        )

        AppScreen.MODE -> ModeSelectScreen(
            encargadoNombre = repository.currentEncargado()?.let {
                it.usuario.ifBlank { it.nombre }
            } ?: "",
            onPicking = { screen = AppScreen.ORDERS },
            onInventario = { screen = AppScreen.INVENTARIO },
            onLogout = {
                repository.logout()
                loggedIn = false
            }
        )

        AppScreen.INVENTARIO -> InventarioScreen(
            onBack = if (repository.currentEncargado()?.modo == "AMBAS") {
                { screen = AppScreen.MODE }
            } else {
                null
            }
        )
    }
}

private fun initialScreen(modo: String?): AppScreen = when (modo) {
    "AMBAS" -> AppScreen.MODE
    "INVENTARIO" -> AppScreen.INVENTARIO
    else -> AppScreen.ORDERS
}

private enum class AppScreen { ORDERS, PICKING, SETTINGS, USERS, FINCAS, MODE, INVENTARIO }
