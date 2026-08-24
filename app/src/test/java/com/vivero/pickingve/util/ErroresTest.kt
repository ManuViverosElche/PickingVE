package com.vivero.pickingve.util

import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErroresTest {

    @Test
    fun `sin conexion produce mensaje accionable`() {
        val resultado = Errores.traducir(UnknownHostException("Unable to resolve host api.example.com"))
        assertEquals(Errores.SIN_CONEXION, resultado)
    }

    @Test
    fun `timeout de peticion se traduce sin texto tecnico`() {
        val resultado = Errores.traducir(HttpRequestTimeoutException("https://pickingve-api", null))
        assertEquals(
            "La conexión tardó demasiado en responder. Comprueba tu señal e inténtalo de nuevo.",
            resultado
        )
    }

    @Test
    fun `timeout de socket tambien se traduce`() {
        val resultado = Errores.traducir(SocketTimeoutException("Read timed out"))
        assertEquals(
            "La conexión tardó demasiado en responder. Comprueba tu señal e inténtalo de nuevo.",
            resultado
        )
    }

    @Test
    fun `respuesta ilegible del servidor avisa sin detalles internos`() {
        val resultado = Errores.traducir(SerializationException("Unexpected JSON token at offset 42"))
        assertEquals(
            "El servidor devolvió una respuesta inesperada. Inténtalo de nuevo más tarde.",
            resultado
        )
    }

    @Test
    fun `IOException de red indica sin conexion`() {
        val resultado = Errores.traducir(IOException("Connection reset by peer"))
        assertEquals(Errores.SIN_CONEXION, resultado)
    }

    @Test
    fun `IOException local no confunde con falta de conexion`() {
        val resultado = Errores.traducir(IOException("ENOSPC: no space left on device"))
        assertEquals(
            "No se pudo completar la operación en el dispositivo. Inténtalo de nuevo.",
            resultado
        )
    }

    @Test
    fun `mensajes de negocio en castellano pasan intactos`() {
        val resultado = Errores.traducir(IllegalStateException("Configura el bot de Telegram en Ajustes"))
        assertEquals("Configura el bot de Telegram en Ajustes", resultado)
    }

    @Test
    fun `excepcion sin mensaje usa fallback generico`() {
        val resultado = Errores.traducir(RuntimeException())
        assertEquals("Se produjo un error inesperado. Inténtalo de nuevo.", resultado)
    }
}
