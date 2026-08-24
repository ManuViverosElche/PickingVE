package com.vivero.pickingve.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.vivero.pickingve.ui.logistica.FaenaDashboardScreen
import com.vivero.pickingve.ui.logistica.FaenaDashboardViewModel
import com.vivero.pickingve.ui.logistica.GestionFaenaScreen
import com.vivero.pickingve.ui.logistica.GestionFaenaViewModel
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
    settingsRepository: SettingsRepository,
    deepLinkPedido: String? = null,
    deepLinkLinea: String? = null,
    deepLinkTipo: String? = null,
    deepLinkCambioTipo: String? = null,
    onDeepLinkConsumed: () -> Unit = {}
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
    val faenaViewModel: FaenaDashboardViewModel =
        viewModel { FaenaDashboardViewModel(repository) }
    val gestionFaenaViewModel: GestionFaenaViewModel =
        viewModel { GestionFaenaViewModel(repository) }

    var loggedIn by remember { mutableStateOf(repository.tipoSesion().isNotBlank()) }
    var screen by remember {
        mutableStateOf(initialScreen(repository))
    }
    val deepPedido = deepLinkPedido?.takeIf { it.isNotBlank() }
    val deepLinea = deepLinkLinea?.takeIf { it.isNotBlank() }
    val deepTipo = deepLinkTipo?.takeIf { it.isNotBlank() }
    val deepCambioTipo = deepLinkCambioTipo?.takeIf { it.isNotBlank() }

    LaunchedEffect(loggedIn, deepPedido, deepTipo, deepCambioTipo) {
        // D-167: los operarios no navegan al pedido por push; solo encargados+
        if (loggedIn && deepPedido != null &&
            repository.tipoSesion() != PickingRepository.TIPO_OPERARIO
        ) {
            pickingViewModel.selectOrder(deepPedido)
            screen = AppScreen.PICKING
        }
    }

    if (!loggedIn) {
        LoginScreen(
            viewModel = loginViewModel,
            onLoginSuccess = {
                loggedIn = true
                screen = initialScreen(repository)
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
            onOpenFaena = { screen = AppScreen.FAENA },
            onLogout = {
                repository.logout()
                loggedIn = false
            }
        )

        AppScreen.FAENA -> FaenaDashboardScreen(
            viewModel = faenaViewModel,
            onBack = { screen = AppScreen.ORDERS },
            onOpenPedido = { orderId ->
                pickingViewModel.selectOrder(orderId)
                screen = AppScreen.PICKING
            },
            onOpenGestion = { screen = AppScreen.GESTION_FAENA },
            onLogout = {
                repository.logout()
                loggedIn = false
            }
        )

        AppScreen.GESTION_FAENA -> GestionFaenaScreen(
            viewModel = gestionFaenaViewModel,
            onBack = { screen = AppScreen.FAENA }
        )

        AppScreen.PICKING -> PickingScreen(
            viewModel = pickingViewModel,
            onBack = {
                screen = if (repository.tipoSesion() == PickingRepository.TIPO_OPERARIO) {
                    AppScreen.FAENA
                } else {
                    AppScreen.ORDERS
                }
            },
            deepLinkLinea = if (screen == AppScreen.PICKING && deepPedido != null) deepLinea else null,
            deepLinkTipo = if (screen == AppScreen.PICKING && deepPedido != null) deepTipo else null,
            deepLinkCambioTipo = if (screen == AppScreen.PICKING && deepPedido != null) deepCambioTipo else null,
            onDeepLinkConsumed = onDeepLinkConsumed
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
            onLogistica = { screen = AppScreen.FAENA },
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

/**
 * Pantalla inicial según el rol de la sesión (D-166/D-167):
 * - OPERARIO: directo a Mi faena (no ve pedidos ni partes).
 * - ENCARGADO/SUPERUSUARIO modo AMBAS: selector.
 * - INVENTARIO: su pantalla.
 * - Resto: Pedidos.
 */
private fun initialScreen(repository: PickingRepository): AppScreen {
    if (repository.tipoSesion().isBlank()) return AppScreen.ORDERS
    return when {
        repository.tipoSesion() == PickingRepository.TIPO_OPERARIO -> AppScreen.FAENA
        else -> when (repository.currentEncargado()?.modo) {
            "AMBAS" -> AppScreen.MODE
            "INVENTARIO" -> AppScreen.INVENTARIO
            else -> AppScreen.ORDERS
        }
    }
}

private enum class AppScreen { ORDERS, FAENA, GESTION_FAENA, PICKING, SETTINGS, USERS, FINCAS, MODE, INVENTARIO }
