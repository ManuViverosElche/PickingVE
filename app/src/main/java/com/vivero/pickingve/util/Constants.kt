package com.vivero.pickingve.util

object Constants {
    const val DEFAULT_DEBOUNCE_MS: Long = 2000L
    const val DATABASE_NAME = "pickingve.db"

    // Telegram
    const val TELEGRAM_API_URL = "https://api.telegram.org"
    const val TELEGRAM_SEND_DOCUMENT = "/bot{token}/sendDocument"

    // BigQuery backend proxy (Cloud Run)
    const val REST_BASE_URL = "https://pickingve-api-938422468946.europe-west1.run.app/api"
    const val REST_PEDIDOS = "/pedidos"
    const val REST_CATALOGO = "/catalogo"
    const val REST_UPLOAD_PICKING = "/picking/upload"
    const val API_KEY_HEADER = "X-API-Key"

    // Shared secret for the backend (injected via BuildConfig from secrets.properties)
    val API_KEY: String = com.vivero.pickingve.BuildConfig.API_KEY
}