package com.vivero.pickingve

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vivero.pickingve.ui.navigation.AppNavHost
import com.vivero.pickingve.ui.theme.PickingVETheme

class MainActivity : ComponentActivity() {

    private val app: PickingApplication
        get() = application as PickingApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PickingVETheme {
                AppNavHost(
                    repository = app.pickingRepository,
                    settingsRepository = app.settingsRepository
                )
            }
        }
    }
}