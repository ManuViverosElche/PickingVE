package com.vivero.pickingve.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vivero.pickingve.PickingApplication
import com.vivero.pickingve.data.remote.PickingApiClient

/**
 * Periodic background sync: uploads pending picking records (small) and
 * refreshes the catalog only when the backend version changed (tiny check).
 * Uses NetworkType.CONNECTED so it runs as soon as coverage returns.
 */
class PickingSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as PickingApplication
            val api = PickingApiClient()
            app.pickingRepository.uploadPendingRegistros(api)
            app.pickingRepository.reintentarCierresPendientes(api)
            app.pickingRepository.syncCatalogIfChanged(api)
            app.pickingRepository.syncEncargados(api)
            try {
                api.notificarCambios()
            } catch (e: Exception) {
                Log.e("PickingVE", "notificar cambios fallo", e)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
