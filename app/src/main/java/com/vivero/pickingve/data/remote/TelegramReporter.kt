package com.vivero.pickingve.data.remote

import com.vivero.pickingve.util.Constants.API_KEY
import com.vivero.pickingve.util.Constants.REST_BASE_URL
import com.vivero.pickingve.util.Constants.TELEGRAM_API_URL
import com.vivero.pickingve.util.Constants.TELEGRAM_SEND_DOCUMENT
import com.vivero.pickingve.util.Constants.TELEGRAM_SEND_MESSAGE
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import java.io.File

/**
 * Sends the generated report file (CSV / XLSX) or a text message to a Telegram
 * chat/channel using the official Bot API. Files include an inline "Marcar
 * comprobado" button; taps arrive at the backend webhook, which answers the
 * callback and flips the button to "Comprobado".
 */
class TelegramReporter(
    private val botToken: String,
    private val chatId: String
) {

    private val client = sharedHttpClient

    private companion object {
        /** Cliente HTTP compartido: crear uno por llamada filtra sockets/hilos. */
        val sharedHttpClient: HttpClient by lazy {
            HttpClient(CIO) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 30_000
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 30_000
                }
            }
        }
    }

    /** Registers (idempotent) the backend webhook that will receive button taps. */
    suspend fun ensureWebhook() {
        runCatching {
            client.submitForm(
                url = TELEGRAM_API_URL + "/bot$botToken/setWebhook",
                formParameters = Parameters.build {
                    append("url", "$REST_BASE_URL/telegram/webhook/$botToken")
                    append("secret_token", API_KEY)
                }
            )
        }
    }

    private fun checkedButton(callbackData: String): String =
        """{"inline_keyboard":[[{"text":"⬜ Marcar comprobado","callback_data":"$callbackData"}]]}"""

    suspend fun sendReport(
        file: File,
        caption: String = "Parte de picking",
        callbackData: String? = null
    ): Result<Unit> = runCatching {
        ensureWebhook()
        val url = TELEGRAM_API_URL + TELEGRAM_SEND_DOCUMENT.replace("{token}", botToken)
        val response: HttpResponse = client.submitFormWithBinaryData(
            url = url,
            formData = formData {
                append("chat_id", chatId)
                append("caption", caption)
                append("disable_notification", "false")
                if (callbackData != null) {
                    append("reply_markup", checkedButton(callbackData))
                }
                append(
                    "document",
                    file.readBytes(),
                    Headers.build {
                        append(
                            HttpHeaders.ContentType,
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        )
                        append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                    }
                )
            }
        )
        if (response.status.value !in 200..299) {
            error("Telegram API error ${response.status}: ${response.bodyAsText()}")
        }
    }

    suspend fun sendCsv(
        file: File,
        caption: String = "Etiquetas a sacar",
        callbackData: String? = null
    ): Result<Unit> = runCatching {
        ensureWebhook()
        val url = TELEGRAM_API_URL + TELEGRAM_SEND_DOCUMENT.replace("{token}", botToken)
        val response: HttpResponse = client.submitFormWithBinaryData(
            url = url,
            formData = formData {
                append("chat_id", chatId)
                append("caption", caption)
                append("disable_notification", "false")
                if (callbackData != null) {
                    append("reply_markup", checkedButton(callbackData))
                }
                append(
                    "document",
                    file.readBytes(),
                    Headers.build {
                        append(HttpHeaders.ContentType, "text/csv")
                        append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                    }
                )
            }
        )
        if (response.status.value !in 200..299) {
            error("Telegram API error ${response.status}: ${response.bodyAsText()}")
        }
    }

    suspend fun sendMessage(text: String): Result<Unit> = runCatching {
        val url = TELEGRAM_API_URL + TELEGRAM_SEND_MESSAGE.replace("{token}", botToken)
        val response: HttpResponse = client.submitForm(
            url = url,
            formParameters = Parameters.build {
                append("chat_id", chatId)
                append("text", text)
            }
        )
        if (response.status.value !in 200..299) {
            error("Telegram API error ${response.status}: ${response.bodyAsText()}")
        }
    }
}