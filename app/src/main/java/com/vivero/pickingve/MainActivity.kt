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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        leerDeepLink(intent)
        setContent {
            PickingVETheme {
                AppNavHost(
                    repository = app.pickingRepository,
                    settingsRepository = app.settingsRepository,
                    deepLinkPedido = deepLinkPedido,
                    deepLinkLinea = deepLinkLinea,
                    onDeepLinkConsumed = {
                        deepLinkPedido = null
                        deepLinkLinea = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        leerDeepLink(intent)
    }

    private fun leerDeepLink(intent: Intent?) {
        deepLinkPedido = intent?.getStringExtra("pedido")?.takeIf { it.isNotBlank() }
        deepLinkLinea = intent?.getStringExtra("linea")?.takeIf { it.isNotBlank() }
    }
}