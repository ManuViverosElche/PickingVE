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
        estados: List<Int> = listOf(2, 3)
    ): List<ApiPedido> {
        val result = client.get("$baseUrl/pedidos") {
            auth()
            url.parameters.append("desde", desde)
            if (!hasta.isNullOrBlank()) url.parameters.append("hasta", hasta)
            if (finca != null) url.parameters.append("finca", finca)
            if (!fincas.isNullOrEmpty()) url.parameters.append("fincas", fincas.joinToString(","))
            url.parameters.append("estados", estados.joinToString(","))
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

    suspend fun uploadRegistros(registros: List<ApiRegistro>): Int =
        client.post("$baseUrl/picking/upload") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(ApiUploadBody(registros))
        }.body<ApiUploadResponse>().ok
}