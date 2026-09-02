package com.vivero.pickingve.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vivero.pickingve.data.repository.PickingRepository
import com.vivero.pickingve.data.repository.SettingsRepository
import com.vivero.pickingve.data.repository.InventarioRepository
import com.vivero.pickingve.ui.admin.AdminFincasScreen
import com.vivero.pickingve.ui.admin.AdminFincasViewModel
import com.vivero.pickingve.ui.admin.AdminUsersScreen
import com.vivero.pickingve.ui.admin.AdminUsersViewModel
import com.vivero.pickingve.ui.logistica.FaenaDashboardScreen
import com.vivero.pickingve.ui.logistica.FaenaDashboardViewModel
import com.vivero.pickingve.ui.logistica.GestionFaenaScreen
import com.vivero.pickingve.ui.logistica.GestionFaenaViewModel
import com.vivero.pickingve.ui.inventario.InvViewModel
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
import com.vivero.pickingve.ui.welcome.AcopioResumen
import com.vivero.pickingve.ui.welcome.WelcomeScreen
import com.vivero.pickingve.ui.panel.PanelHubScreen
import com.vivero.pickingve.ui.panel.HistoricoPanelScreen
import com.vivero.pickingve.ui.panel.EtiquetasPanelScreen
import com.vivero.pickingve.ui.panel.ConfigPanelScreen

@Composable
fun AppNavHost(
    repository: PickingRepository,
    settingsRepository: SettingsRepository,
    inventarioRepository: InventarioRepository,
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
    val invViewModel: InvViewModel =
        viewModel { InvViewModel(repository, inventarioRepository) }

    // D-182: rememberSaveable en lugar de remember para que la pantalla activa
    // sobreviva a la recreación de la Activity (segundo plano / presión de memoria)
    // y no vuelva a la bienvenida ("Buenos días").
    var loggedIn by rememberSaveable { mutableStateOf(repository.tipoSesion().isNotBlank()) }
    var screen by rememberSaveable {
        mutableStateOf(if (repository.tipoSesion().isNotBlank()) AppScreen.WELCOME else AppScreen.ORDERS)
    }
    val deepPedido = deepLinkPedido?.takeIf { it.isNotBlank() }
    val deepLinea = deepLinkLinea?.takeIf { it.isNotBlank() }
    val deepTipo = deepLinkTipo?.takeIf { it.isNotBlank() }
    val deepCambioTipo = deepLinkCambioTipo?.takeIf { it.isNotBlank() }

    // D-191: cierre de sesión único. Resetea el LoginViewModel porque sin eso
    // `state.success` seguía a true tras un login y LoginScreen volvía a entrar
    // solo ("recarga la pantalla pero no cierra sesión").
    val logout = {
        repository.logout()
        loginViewModel.reset()
        loggedIn = false
    }

    LaunchedEffect(loggedIn, deepPedido, deepTipo, deepCambioTipo) {
        // D-176: los operarios no navegan al pedido por push; solo encargados+.
        // D-273: EXCEPCIÓN - las discrepancias (tipo=discrepancia) y los comentarios
        // dirigidos al operario viajan a "Mi faena" con la línea resaltada y el chat
        // abierto, para que el operario responda sin perderse entre 200 líneas.
        if (loggedIn && deepPedido != null) {
            if (repository.tipoSesion() != PickingRepository.TIPO_OPERARIO) {
                pickingViewModel.selectOrder(deepPedido)
                screen = AppScreen.PICKING
            } else if (deepTipo == "discrepancia" || deepTipo == "comentario") {
                screen = AppScreen.FAENA
            }
        }
    }

    if (!loggedIn) {
        LoginScreen(
            viewModel = loginViewModel,
            onLoginSuccess = {
                loggedIn = true
                screen = AppScreen.WELCOME
            }
        )
        return
    }

    val faenaState by faenaViewModel.uiState.collectAsState()
    val acopioPorFinca = faenaState.dias
        .flatMap { it.fincas }
        .groupBy { it.finca }
        .map { (finca, lista) -> AcopioResumen(finca, lista.sumOf { it.plantasPendientes }) }
        .sortedByDescending { it.plantas }

    when (screen) {
        AppScreen.WELCOME -> WelcomeScreen(
            repository = repository,
            orderListViewModel = orderListViewModel,
            faenaViewModel = faenaViewModel,
            acopioPorFinca = acopioPorFinca,
            onEmpezar = {
                screen = when {
                    repository.tipoSesion() == PickingRepository.TIPO_OPERARIO -> AppScreen.FAENA
                    else -> initialScreen(repository).let { if (it == AppScreen.ORDERS && repository.currentEncargado()?.modo == "AMBAS") AppScreen.MODE else it }
                }
            },
            onLogout = logout
        )
        AppScreen.ORDERS -> {
            val backDest = if (repository.currentEncargado()?.rol == "SUPERUSUARIO" || repository.currentEncargado()?.modo == "AMBAS") AppScreen.MODE else AppScreen.WELCOME
            androidx.activity.compose.BackHandler {
                screen = backDest
            }
            OrderListScreen(
                viewModel = orderListViewModel,
                mostrarFaena = repository.currentEncargado()?.rol == "SUPERUSUARIO",
                onOrderSelected = { orderId ->
                    pickingViewModel.selectOrder(orderId)
                    screen = AppScreen.PICKING
                },
                onOpenSettings = { screen = AppScreen.SETTINGS },
                onOpenFaena = { screen = AppScreen.FAENA },
                onBack = { screen = backDest },
                onLogout = logout
            )
        }

        AppScreen.FAENA -> {
            androidx.activity.compose.BackHandler { screen = AppScreen.WELCOME }
            FaenaDashboardScreen(
                viewModel = faenaViewModel,
                onBack = { screen = AppScreen.WELCOME },
                onOpenPedido = { orderId ->
                    pickingViewModel.selectOrder(orderId)
                    screen = AppScreen.PICKING
                },
                deepLinkLinea = if (screen == AppScreen.FAENA && deepLinea != null) deepLinea else null,
                deepLinkTipo = if (screen == AppScreen.FAENA && deepTipo != null) deepTipo else null,
                onDeepLinkConsumed = onDeepLinkConsumed,
                onLogout = logout
            )
        }

        AppScreen.GESTION_FAENA -> GestionFaenaScreen(
            viewModel = gestionFaenaViewModel,
            onBack = { screen = AppScreen.PANEL }
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
            mostrarFaena = repository.currentEncargado()?.rol == "SUPERUSUARIO",
            mostrarPanel = repository.currentEncargado()?.rol == "SUPERUSUARIO",
            onPanel = { screen = AppScreen.PANEL },
            onPicking = { screen = AppScreen.ORDERS },
            onInventario = { screen = AppScreen.INVENTARIO },
            onLogistica = { screen = AppScreen.FAENA },
            onLogout = logout
        )

        AppScreen.PANEL -> {
            var panelSub by rememberSaveable { mutableStateOf("HUB") }
            androidx.activity.compose.BackHandler {
                if (panelSub != "HUB") panelSub = "HUB"
                else screen = AppScreen.MODE
            }
            when (panelSub) {
                "HUB" -> PanelHubScreen(
                    onBack = { screen = AppScreen.MODE },
                    onOpenHistorico = { panelSub = "HISTORICO" },
                    onOpenEtiquetas = { panelSub = "ETIQUETAS" },
                    onOpenConfig = { panelSub = "CONFIG" },
                    onOpenGestion = { screen = AppScreen.GESTION_FAENA },
                    onLogout = logout
                )
                "HISTORICO" -> HistoricoPanelScreen(
                    onBack = { panelSub = "HUB" }
                )
                "ETIQUETAS" -> EtiquetasPanelScreen(
                    onBack = { panelSub = "HUB" }
                )
                "CONFIG" -> ConfigPanelScreen(
                    onBack = { panelSub = "HUB" }
                )
            }
        }

        AppScreen.INVENTARIO -> InventarioScreen(
            viewModel = invViewModel,
            onBack = if (repository.currentEncargado()?.modo == "AMBAS") {
                { screen = AppScreen.MODE }
            } else {
                null
            },
            onLogout = logout
        )
    }
}

/**
 * Pantalla inicial según el rol de la sesión (D-175/D-176):
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

private enum class AppScreen { WELCOME, ORDERS, FAENA, GESTION_FAENA, PICKING, SETTINGS, USERS, FINCAS, MODE, INVENTARIO, PANEL }
