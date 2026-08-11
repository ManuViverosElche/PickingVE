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
        mostrarNotificacion(title, body)
    }

    private fun mostrarNotificacion(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val channelId = "pickingve_notificaciones"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Notificaciones PickingVE",
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(1, notification)
        } catch (e: SecurityException) {
            Log.e("PickingVE", "Permiso de notificaciones denegado", e)
        }
    }
}
