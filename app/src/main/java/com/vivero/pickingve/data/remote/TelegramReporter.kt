package com.vivero.pickingve.data.remote

import com.vivero.pickingve.util.Constants.TELEGRAM_API_URL
import com.vivero.pickingve.util.Constants.TELEGRAM_SEND_DOCUMENT
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import java.io.File

/**
 * Sends the generated report file (CSV / XLSX) to a Telegram chat/channel
 * using the official Bot API sendDocument method.
 */
class TelegramReporter(
    private val botToken: String,
    private val chatId: String
) {

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }

    suspend fun sendReport(file: File, caption: String = "Parte de picking"): Result<Unit> = runCatching {
        val url = TELEGRAM_API_URL + TELEGRAM_SEND_DOCUMENT.replace("{token}", botToken)
        val response: HttpResponse = client.submitFormWithBinaryData(
            url = url,
            formData = formData {
                append("chat_id", chatId)
                append("caption", caption)
                append("disable_notification", "false")
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
}