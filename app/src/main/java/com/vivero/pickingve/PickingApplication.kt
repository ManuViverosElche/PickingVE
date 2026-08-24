package com.vivero.pickingve

import android.app.Application
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.vivero.pickingve.data.local.AppDatabase
import com.vivero.pickingve.data.repository.PickingRepository
import com.vivero.pickingve.data.repository.SettingsRepository
import com.vivero.pickingve.worker.PickingSyncWorker
import java.util.concurrent.TimeUnit

class PickingApplication : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val pickingRepository by lazy {
        PickingRepository(
            context = this,
            productDao = database.productDao(),
            orderDao = database.orderDao(),
            pickingDao = database.pickingDao(),
            encargadoDao = database.encargadoDao(),
            litrajeDao = database.litrajeDao(),
            sectorDao = database.sectorDao(),
            chatEstadoDao = database.chatEstadoDao(),
            operarioDao = database.operarioDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        scheduleBackgroundSync()
    }

    private fun scheduleBackgroundSync() {
        val request = PeriodicWorkRequestBuilder<PickingSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "picking_sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}