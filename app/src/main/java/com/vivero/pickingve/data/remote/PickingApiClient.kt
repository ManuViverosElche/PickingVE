package com.vivero.pickingve.data.remote

import com.vivero.pickingve.util.Constants
import com.vivero.pickingve.util.Constants.REST_BASE_URL
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class PickingApiClient(
    private val baseUrl: String = REST_BASE_URL
) {
    private val client = HttpClient(CIO) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }

    private fun HttpRequestBuilder.auth(): HttpRequestBuilder {
        header(Constants.API_KEY_HEADER, Constants.API_KEY)
        return this
    }

    /**
     * Downloads orders from the BigQuery backend in a date range and a set of states.
     * The deployed backend supports `desde`+`hasta`+`estados=2,3` in a single call.
     */
    suspend fun fetchPedidos(
        desde: String,
        hasta: String? = null,
        finca: String? = null,
        fincas: List<String>? = null,
        estados: List<Int> = listOf(2, 3),
        modificadoDesde: String? = null
    ): List<ApiPedido> {
        val result = client.get("$baseUrl/pedidos") {
            auth()
            url.parameters.append("desde", desde)
            if (!hasta.isNullOrBlank()) url.parameters.append("hasta", hasta)
            if (finca != null) url.parameters.append("finca", finca)
            if (!fincas.isNullOrEmpty()) url.parameters.append("fincas", fincas.joinToString(","))
            url.parameters.append("estados", estados.joinToString(","))
            if (!modificadoDesde.isNullOrBlank()) {
                url.parameters.append("modificadoDesde", modificadoDesde)
            }
        }.body<ApiPedidosResponse>().pedidos
        return result
    }

    suspend fun fetchIngresos(): List<ApiEncargado> =
        client.get("$baseUrl/encargados") {
            auth()
        }.body<ApiEncargadosResponse>().encargados

    suspend fun fetchFincas(): List<String> =
        client.get("$baseUrl/fincas") {
            auth()
        }.body<ApiFincasResponse>().fincas

    suspend fun fetchFincasGestion(): List<ApiFinca> =
        client.get("$baseUrl/fincas/gestion") {
            auth()
        }.body<ApiFincaGestionResponse>().fincas

    suspend fun crearFinca(finca: String, nombre: String? = null, activo: Boolean = true) {
        client.post("$baseUrl/fincas") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(CrearFincaRequest(finca = finca, nombre = nombre, activo = activo))
        }
    }

    suspend fun eliminarFinca(finca: String) {
        client.post("$baseUrl/fincas/eliminar") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(EliminarFincaRequest(finca))
        }
    }

    suspend fun cambiarPassword(usuario: String, passwordActual: String, passwordNueva: String) {
        client.post("$baseUrl/encargados/cambiar-password") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(CambiarPasswordRequest(usuario, passwordActual, passwordNueva))
        }
    }

    suspend fun cambiarEmail(usuario: String, email: String) {
        client.post("$baseUrl/encargados/cambiar-email") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(CambiarEmailRequest(usuario, email))
        }
    }

    suspend fun crearEncargado(request: CrearEncargadoRequest) {
        client.post("$baseUrl/encargados") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun loginEncargado(usuario: String, password: String): ApiEncargado =
        client.post("$baseUrl/encargados/login") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(usuario, password))
        }.body<ApiEncargado>()

    suspend fun fetchCatalogo(): ApiCatalogo =
        client.get("$baseUrl/catalogo") {
            auth()
        }.body<ApiCatalogo>()

    suspend fun fetchCatalogoVersion(): String =
        client.get("$baseUrl/catalogo/version") {
            auth()
        }.body<ApiCatalogoVersion>().version

    suspend fun uploadRegistros(registros: List<ApiRegistro>): Int =
        client.post("$baseUrl/picking/upload") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(ApiUploadBody(registros))
        }.body<ApiUploadResponse>().ok

    suspend fun registrarFcmToken(email: String, token: String, plataforma: String = "android") {
        client.post("$baseUrl/fcm-token") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(FcmTokenRequest(email = email, token = token, plataforma = plataforma))
        }
    }

    suspend fun fetchComentarios(pedido: String, linea: String? = null): List<ApiComentario> =
        client.get("$baseUrl/comentarios") {
            auth()
            url.parameters.append("pedido", pedido)
            if (!linea.isNullOrBlank()) url.parameters.append("linea", linea)
        }.body<ApiComentariosResponse>().comentarios

    suspend fun crearComentario(
        pedido: String,
        linea: String?,
        texto: String,
        autorEmail: String,
        autorNombre: String,
        rol: String
    ) {
        client.post("$baseUrl/comentarios") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(
                ComentarioRequest(
                    pedidoId = pedido,
                    lineaHuella = linea,
                    texto = texto,
                    autorEmail = autorEmail,
                    autorNombre = autorNombre,
                    rol = rol,
                    canal = "app"
                )
            )
        }
    }
}