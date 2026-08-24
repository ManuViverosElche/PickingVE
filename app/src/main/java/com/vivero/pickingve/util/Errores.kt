package com.vivero.pickingve.util

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import java.io.IOException
import java.net.SocketException
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

    const val TIMEOUT =
        "La conexión tardó demasiado en responder. Comprueba tu señal e inténtalo de nuevo."

    private val PALABRAS_RED = listOf(
        "resolve", "connect", "reset", "broken pipe", "eof", "network",
        "unreachable", "refused", "host", "socket"
    )

    fun traducir(e: Throwable): String = when {
        esErrorTimeout(e) -> TIMEOUT
        esErrorDeRed(e) -> SIN_CONEXION
        e is ClientRequestException -> when (e.response.status.value) {
            401, 403 -> "Acceso denegado por el servidor. Pide al administrador que revise la configuración."
            else -> "No se pudo completar la operación (error ${e.response.status.value})."
        }
        e is ServerResponseException ->
            "El servidor no responde ahora mismo. Tus datos están a salvo; inténtalo de nuevo en unos minutos."
        e is SerializationException ->
            "El servidor devolvió una respuesta inesperada. Inténtalo de nuevo más tarde."
        e is IOException ->
            "No se pudo completar la operación en el dispositivo. Inténtalo de nuevo."
        else -> e.message?.takeIf { it.isNotBlank() }
            ?: "Se produjo un error inesperado. Inténtalo de nuevo."
    }

    /** true solo cuando el fallo es claramente de conectividad (nunca de credenciales). */
    fun esErrorDeRed(e: Throwable): Boolean = enCadena(e) {
        when (it) {
            is UnknownHostException,
            is java.nio.channels.UnresolvedAddressException,
            is HttpRequestTimeoutException,
            is ConnectTimeoutException,
            is SocketTimeoutException -> true
            is SocketException -> true
            is IOException -> PALABRAS_RED.any { w -> it.message?.lowercase()?.contains(w) == true }
            else -> false
        }
    }

    private fun esErrorTimeout(e: Throwable): Boolean = enCadena(e) {
        it is HttpRequestTimeoutException || it is ConnectTimeoutException || it is SocketTimeoutException
    }

    private fun enCadena(e: Throwable, predicado: (Throwable) -> Boolean): Boolean =
        generateSequence(e as Throwable?) { it.cause }.take(6).any(predicado)
}
