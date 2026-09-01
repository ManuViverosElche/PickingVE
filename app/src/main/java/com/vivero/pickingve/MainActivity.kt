package com.vivero.pickingve

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vivero.pickingve.ui.navigation.AppNavHost
import com.vivero.pickingve.ui.theme.PickingVETheme

class MainActivity : ComponentActivity() {

    private val app: PickingApplication
        get() = application as PickingApplication

    private var deepLinkPedido by mutableStateOf<String?>(null)
    private var deepLinkLinea by mutableStateOf<String?>(null)
    private var deepLinkTipo by mutableStateOf<String?>(null)
    private var deepLinkCambioTipo by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        leerDeepLink(intent)
        setContent {
            PickingVETheme {
                AppNavHost(
                    repository = app.pickingRepository,
                    settingsRepository = app.settingsRepository,
                    inventarioRepository = app.inventarioRepository,
                    deepLinkPedido = deepLinkPedido,
                    deepLinkLinea = deepLinkLinea,
                    deepLinkTipo = deepLinkTipo,
                    deepLinkCambioTipo = deepLinkCambioTipo,
                    onDeepLinkConsumed = {
                        deepLinkPedido = null
                        deepLinkLinea = null
                        deepLinkTipo = null
                        deepLinkCambioTipo = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        leerDeepLink(intent)
    }

    private fun leerDeepLink(intent: Intent?) {
        deepLinkPedido = intent?.getStringExtra("pedido")?.takeIf { it.isNotBlank() }
        deepLinkLinea = intent?.getStringExtra("linea")?.takeIf { it.isNotBlank() }
        deepLinkTipo = intent?.getStringExtra("tipo_notificacion")?.takeIf { it.isNotBlank() }
        deepLinkCambioTipo = intent?.getStringExtra("cambio_tipo")?.takeIf { it.isNotBlank() }
    }
}