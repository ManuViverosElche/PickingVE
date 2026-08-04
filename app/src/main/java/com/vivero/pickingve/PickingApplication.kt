package com.vivero.pickingve

import android.app.Application
import com.vivero.pickingve.data.local.AppDatabase
import com.vivero.pickingve.data.repository.PickingRepository
import com.vivero.pickingve.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PickingApplication : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val pickingRepository by lazy {
        PickingRepository(
            context = this,
            productDao = database.productDao(),
            orderDao = database.orderDao(),
            pickingDao = database.pickingDao(),
            encargadoDao = database.encargadoDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
    }
}