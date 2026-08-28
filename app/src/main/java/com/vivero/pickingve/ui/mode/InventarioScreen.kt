package com.vivero.pickingve.ui.mode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivero.pickingve.ui.inventario.InvInicioScreen
import com.vivero.pickingve.ui.inventario.InvPantallaScreen
import com.vivero.pickingve.ui.inventario.InvSectorScreen
import com.vivero.pickingve.ui.inventario.InvViewModel

/**
 * Punto de entrada del modulo de inventario (D-219): encadena la selección de
 * finca y sector con la pantalla de pistoleo, según el estado del ViewModel.
 */
@Composable
fun InventarioScreen(
    viewModel: InvViewModel,
    onBack: (() -> Unit)?,
    onLogout: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when {
        state.sectorSeleccionado != null -> InvPantallaScreen(
            viewModel = viewModel,
            onBack = { viewModel.volverASeleccionSector() },
            onLogout = onLogout
        )
        state.fincaSeleccionada != null -> InvSectorScreen(
            viewModel = viewModel,
            onBack = { viewModel.volverAFincas() },
            onLogout = onLogout
        )
        else -> InvInicioScreen(
            viewModel = viewModel,
            onBack = onBack,
            onLogout = onLogout
        )
    }
}
