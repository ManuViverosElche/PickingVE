package com.vivero.pickingve.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vivero.pickingve.MainActivity
import com.vivero.pickingve.R
import com.vivero.pickingve.data.remote.PickingApiClient
import com.vivero.pickingve.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PickingFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val email = SettingsRepository(applicationContext).settings.value.operatorEmail
        if (email.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    PickingApiClient().registrarFcmToken(email, token)
                } catch (e: Exception) {
                    Log.e("PickingVE", "Registro token FCM fallido", e)
                }
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        val title = remoteMessage.notification?.title
            ?: data["title"]
            ?: "PickingVE"
        val body = remoteMessage.notification?.body
            ?: data["body"]
            ?: ""
        val tipo = data["tipo"] ?: "pedido_modificado"
        val linea = data["linea"]?.takeIf { it.isNotBlank() }
        val cambioTipo = data["cambio_tipo"]?.takeIf { it.isNotBlank() }
        mostrarNotificacion(
            title,
            body,
            pedido = data["pedido"]?.takeIf { it.isNotBlank() },
            linea = linea,
            tipo = tipo,
            cambioTipo = cambioTipo
        )
    }

    private fun mostrarNotificacion(
        title: String,
        body: String,
        pedido: String?,
        linea: String?,
        tipo: String,
        cambioTipo: String?
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!pedido.isNullOrBlank()) putExtra("pedido", pedido)
            if (!linea.isNullOrBlank()) putExtra("linea", linea)
            putExtra("tipo_notificacion", tipo)
            if (!cambioTipo.isNullOrBlank()) putExtra("cambio_tipo", cambioTipo)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // D-15X: camión en muelle y discrepancias usan un canal de urgencia
        // (vibración larga, prioridad máxima) para que el operario lo vea al instante.
        val urgente = tipo == "camion_llegado" || tipo == "discrepancia"
        val channelId = if (urgente) "pickingve_urgente" else "pickingve_notificaciones"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (urgente) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "Avisos urgentes (camión en muelle)",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 400, 250, 400, 250, 400)
                    }
                )
            } else {
                manager.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "Notificaciones PickingVE",
                        NotificationManager.IMPORTANCE_HIGH
                    )
                )
            }
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(
                if (urgente) 2 else 1,
                notification
            )
        } catch (e: SecurityException) {
            Log.e("PickingVE", "Permiso de notificaciones denegado", e)
        }
    }
}
