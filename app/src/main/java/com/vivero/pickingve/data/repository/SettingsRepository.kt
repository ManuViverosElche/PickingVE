package com.vivero.pickingve.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.vivero.pickingve.util.Constants.DEFAULT_DEBOUNCE_MS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsStore(
    val phoneId: String = "",
    val operatorName: String = "",
    val operatorEmail: String = "",
    val telegramBotToken: String = "",
    val telegramChatId: String = "",
    val labelsBotToken: String = "",
    val labelsChatId: String = "",
    val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    val vibrateOnScan: Boolean = true,
    val beepOnScan: Boolean = true,
    val matriculaCamion: String = "",
    val matriculaRemolque: String = "",
    val finca: String = "",
    val zona: String = "",
    val pesoCarga: String = ""
)

class SettingsRepository(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "pickingve_secure_settings",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("pickingve_settings", Context.MODE_PRIVATE)
        }
    }

    private val _settings = MutableStateFlow(load())
    val settings = _settings.asStateFlow()

    fun load(): SettingsStore = SettingsStore(
        telegramBotToken = prefs.getString(KEY_TOKEN, "")
            ?.takeIf { it.isNotEmpty() }
            ?: com.vivero.pickingve.BuildConfig.DEFAULT_TELEGRAM_BOT_TOKEN,
        telegramChatId = prefs.getString(KEY_CHAT_ID, "")
            ?.takeIf { it.isNotEmpty() }
            ?: com.vivero.pickingve.BuildConfig.DEFAULT_TELEGRAM_CHAT_ID,
        labelsBotToken = prefs.getString(KEY_LABELS_TOKEN, "")
            ?.takeIf { it.isNotEmpty() }
            ?: com.vivero.pickingve.BuildConfig.DEFAULT_LABELS_BOT_TOKEN,
        labelsChatId = prefs.getString(KEY_LABELS_CHAT_ID, "")
            ?.takeIf { it.isNotEmpty() }
            ?: com.vivero.pickingve.BuildConfig.DEFAULT_LABELS_CHAT_ID,
        phoneId = prefs.getString(KEY_PHONE_ID, "").orEmpty(),
        operatorName = prefs.getString(KEY_OPERATOR, "").orEmpty(),
        operatorEmail = prefs.getString(KEY_EMAIL, "").orEmpty(),
        debounceMs = prefs.getLong(KEY_DEBOUNCE, DEFAULT_DEBOUNCE_MS),
        vibrateOnScan = prefs.getBoolean(KEY_VIBRATE, true),
        beepOnScan = prefs.getBoolean(KEY_BEEP, true),
        matriculaCamion = prefs.getString(KEY_MAT_CAMION, "").orEmpty(),
        matriculaRemolque = prefs.getString(KEY_MAT_REMOLQUE, "").orEmpty(),
        finca = prefs.getString(KEY_FINCA, "").orEmpty(),
        zona = prefs.getString(KEY_ZONA, "").orEmpty(),
        pesoCarga = prefs.getString(KEY_PESO, "").orEmpty()
    )

    fun update(block: (SettingsStore) -> SettingsStore) {
        val next = block(_settings.value)
        prefs.edit()
            .putString(KEY_TOKEN, next.telegramBotToken)
            .putString(KEY_CHAT_ID, next.telegramChatId)
            .putString(KEY_LABELS_TOKEN, next.labelsBotToken)
            .putString(KEY_LABELS_CHAT_ID, next.labelsChatId)
            .putString(KEY_PHONE_ID, next.phoneId)
            .putString(KEY_OPERATOR, next.operatorName)
            .putString(KEY_EMAIL, next.operatorEmail)
            .putLong(KEY_DEBOUNCE, next.debounceMs)
            .putBoolean(KEY_VIBRATE, next.vibrateOnScan)
            .putBoolean(KEY_BEEP, next.beepOnScan)
            .putString(KEY_MAT_CAMION, next.matriculaCamion)
            .putString(KEY_MAT_REMOLQUE, next.matriculaRemolque)
            .putString(KEY_FINCA, next.finca)
            .putString(KEY_ZONA, next.zona)
            .putString(KEY_PESO, next.pesoCarga)
            .apply()
        _settings.value = next
    }

    companion object {
        private const val KEY_TOKEN = "telegram_bot_token"
        private const val KEY_CHAT_ID = "telegram_chat_id"
        private const val KEY_LABELS_TOKEN = "labels_telegram_bot_token"
        private const val KEY_LABELS_CHAT_ID = "labels_telegram_chat_id"
        private const val KEY_PHONE_ID = "phone_id"
        private const val KEY_OPERATOR = "operator_name"
        private const val KEY_EMAIL = "operator_email"
        private const val KEY_DEBOUNCE = "scan_debounce_ms"
        private const val KEY_VIBRATE = "vibrate_on_scan"
        private const val KEY_BEEP = "beep_on_scan"
        private const val KEY_MAT_CAMION = "matricula_camion"
        private const val KEY_MAT_REMOLQUE = "matricula_remolque"
        private const val KEY_FINCA = "finca"
        private const val KEY_ZONA = "zona"
        private const val KEY_PESO = "peso_carga"
    }
}