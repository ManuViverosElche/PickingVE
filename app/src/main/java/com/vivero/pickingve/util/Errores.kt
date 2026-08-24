package com.vivero.pickingve.util

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException

/**
 * Traduce excepciones técnicas a mensajes claros y accionables en castellano para la UI.
 * Los mensajes de negocio (ya redactados en castellano por el dominio) pasan intactos.
 */
object Errores {

    const val SIN_CONEXION =
        "Sin conexión a internet. Lo que hagas quedará guardado en el móvil y se enviará al recuperar la señal."

    private val PALABRAS_RED = listOf(
        "resolve", "connect", "reset", "broken pipe", "eof", "network",
        "unreachable", "refused", "host", "socket"
    )

    fun traducir(e: Throwable): String = when (e) {
        is UnknownHostException -> SIN_CONEXION
        is HttpRequestTimeoutException,
        is ConnectTimeoutException,
        is SocketTimeoutException ->
            "La conexión tardó demasiado en responder. Comprueba tu señal e inténtalo de nuevo."
        is ClientRequestException -> when (e.response.status.value) {
            401, 403 -> "Acceso denegado por el servidor. Pide al administrador que revise la configuración."
            else -> "No se pudo completar la operación (error ${e.response.status.value})."
        }
        is ServerResponseException ->
            "El servidor no responde ahora mismo. Tus datos están a salvo; inténtalo de nuevo en unos minutos."
        is SerializationException ->
            "El servidor devolvió una respuesta inesperada. Inténtalo de nuevo más tarde."
        is IOException ->
            if (PALABRAS_RED.any { e.message?.lowercase()?.contains(it) == true }) {
                SIN_CONEXION
            } else {
                "No se pudo completar la operación en el dispositivo. Inténtalo de nuevo."
            }
        else -> e.message?.takeIf { it.isNotBlank() }
            ?: "Se produjo un error inesperado. Inténtalo de nuevo."
    }
}
