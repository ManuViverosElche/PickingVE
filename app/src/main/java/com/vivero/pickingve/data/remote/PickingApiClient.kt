package com.vivero.pickingve.data.remote

import com.vivero.pickingve.util.Constants
import com.vivero.pickingve.util.Constants.REST_BASE_URL
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class PickingApiClient(
    private val baseUrl: String = REST_BASE_URL
) {
    private val client = sharedHttpClient

    suspend fun compensarRegistros(registros: List<CompensaRegistro>): CompensaResponse =
        client.post("$baseUrl/picking/compensar") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(CompensaBody(registros))
        }.body<CompensaResponse>()

    companion object {
        /** Cliente HTTP compartido: crear uno por llamada filtra sockets/hilos. */
        private val sharedHttpClient: HttpClient by lazy {
            HttpClient(CIO) {
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
        }
    }

    private fun HttpRequestBuilder.auth(): HttpRequestBuilder {
        header(Constants.API_KEY_HEADER, Constants.API_KEY)
        return this
    }

    /**
     * Downloads orders from the BigQuery backend in a date range and a set of states.
     * The deployed backend supports `desde`+`hasta`+`estados=1,3` in a single call.
     * Estados: 1 = pendiente parcial, 3 = en almacen (mismo criterio que los scripts de las hojas).
     */
    suspend fun fetchPedidos(
        desde: String,
        hasta: String? = null,
        finca: String? = null,
        fincas: List<String>? = null,
        estados: List<Int> = listOf(1, 3),
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

    suspend fun notificarCambios(
    pedidosModificados: List<String> = emptyList(),
    cambiosDetalle: List<CambioLineaDetalle> = emptyList()
): ApiNotificarResponse =
        client.post("$baseUrl/notificar") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(NotificarRequest(pedidosModificados, cambiosDetalle))
        }.body()

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

    suspend fun uploadRegistros(registros: List<ApiRegistro>): ApiUploadResponse =
        client.post("$baseUrl/picking/upload") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(ApiUploadBody(registros))
        }.body<ApiUploadResponse>()

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

    suspend fun subirAdjunto(
        pedido: String,
        linea: String?,
        texto: String,
        autorEmail: String,
        autorNombre: String,
        rol: String,
        nombreArchivo: String,
        bytes: ByteArray,
        contentType: ContentType
    ): String? {
        val response = client.post("$baseUrl/comentarios/adjunto") {
            auth()
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("pedido_id", pedido)
                        if (!linea.isNullOrBlank()) append("linea_huella", linea)
                        append("autor_email", autorEmail)
                        append("autor_nombre", autorNombre)
                        append("rol", rol)
                        append("texto", texto)
                        append("archivo", bytes, Headers.build {
                            append(HttpHeaders.ContentType, contentType.toString())
                            append(HttpHeaders.ContentDisposition, "filename=\"$nombreArchivo\"")
                        })
                    }
                )
            )
        }
        return response.body<ApiAdjuntoResponse>().adjuntoUrl
    }

    suspend fun guardarMatricula(
        pedido: String,
        tipo: String,
        matricula: String,
        muelle: String,
        bytes: ByteArray? = null,
        nombreArchivo: String = "matricula.jpg"
    ): String? {
        val response = client.post("$baseUrl/pedidos/matriculas") {
            auth()
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("pedido_id", pedido)
                        append("tipo", tipo)
                        append("matricula", matricula)
                        append("muelle", muelle)
                        if (bytes != null) {
                            append("archivo", bytes, Headers.build {
                                append(HttpHeaders.ContentType, ContentType.Image.JPEG.toString())
                                append(HttpHeaders.ContentDisposition, "filename=\"$nombreArchivo\"")
                            })
                        }
                    }
                )
            )
        }
        return response.body<ApiMatriculaResponse>().fotoUrl
    }

    suspend fun fetchPerfilOperario(email: String): PerfilOperarioResponse =
        client.get("$baseUrl/perfil-operario") {
            auth()
            url.parameters.append("email", email)
        }.body<PerfilOperarioResponse>()

    /** D-184: estado actual del pedido (albaran vs borrado real). */
    suspend fun estadoPedido(numero: String): PedidoEstadoResponse =
        client.get("$baseUrl/pedido-estado") {
            auth()
            url.parameters.append("numero", numero)
        }.body<PedidoEstadoResponse>()

    /** D-190: camion compartido que contiene este pedido (precarga matriculas). */
    suspend fun camionDePedido(pedido: String): CamionDePedidoResponse =
        client.get("$baseUrl/logistica/camion-de-pedido") {
            auth()
            url.parameters.append("pedido", pedido)
        }.body<CamionDePedidoResponse>()

    /** D-190: crear camion compartido con varios pedidos y matriculas opcionales. */
    suspend fun crearCamionCompartido(
        fecha: String,
        matriculaCamion: String,
        matriculaRemolque: String,
        pedidos: List<String>,
        creadoPor: String
    ) {
        client.post("$baseUrl/logistica/camion-compartido") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(
                CamionCompartidoRequest(
                    fecha = fecha,
                    matriculaCamion = matriculaCamion,
                    matriculaRemolque = matriculaRemolque,
                    pedidos = pedidos,
                    creadoPor = creadoPor
                )
            )
        }
    }

    suspend fun notificarDiscrepancia(
        pedidoId: String,
        lineaHuella: String,
        declarado: Int,
        puntado: Int,
        mensaje: String,
        operarioEmail: String
    ) {
        client.post("$baseUrl/logistica/discrepancia") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(
                DiscrepanciaRequest(
                    pedidoId = pedidoId,
                    lineaHuella = lineaHuella,
                    declarado = declarado,
                    puntado = puntado,
                    mensaje = mensaje,
                    operarioEmail = operarioEmail
                )
            )
        }
    }

    suspend fun cerrarLinea(request: CierreLineaRequest) {
        client.post("$baseUrl/logistica/cierre-linea") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun reabrirLinea(request: ReabrirLineaRequest) {
        client.post("$baseUrl/logistica/reabrir-linea") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun loginOperario(email: String, password: String): ApiLoginOperarioResponse =
        client.post("$baseUrl/logistica/login-operario") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(LoginOperarioRequest(email, password))
        }.body<ApiLoginOperarioResponse>()

    suspend fun fetchOperariosApp(): List<ApiOperarioApp> =
        client.get("$baseUrl/logistica/operarios-app") {
            auth()
        }.body<ApiOperariosAppResponse>().operarios

    suspend fun cambiarPasswordOperario(email: String, actual: String, nueva: String) {
        client.post("$baseUrl/logistica/cambiar-password-operario") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(CambiarPasswordOperarioRequest(email, actual, nueva))
        }
    }

    suspend fun concederAyuda(lineas: List<AyudaPermisoLineaApi>, ayudanteEmail: String, concedidoPorEmail: String) {
        client.post("$baseUrl/logistica/ayuda-permiso") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(AyudaPermisoConcederRequest(lineas, ayudanteEmail, concedidoPorEmail))
        }
    }

    suspend fun revocarAyuda(lineas: List<AyudaPermisoLineaApi>, ayudanteEmail: String) {
        client.post("$baseUrl/logistica/ayuda-permiso/revocar") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(AyudaRevocarRequest(lineas, ayudanteEmail))
        }
    }

    suspend fun fetchAyudasConcedidas(ayudanteEmail: String): List<ApiAyudaPermiso> =
        client.get("$baseUrl/logistica/ayuda-permiso") {
            auth()
            url.parameters.append("ayudante_email", ayudanteEmail)
        }.body<ApiAyudaPermisosResponse>().permisos

    suspend fun guardarReparto(asignaciones: List<RepartoAsignacionApi>): RepartoGuardarResponse =
        client.post("$baseUrl/manager/reparto") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(RepartoGuardarRequest(asignaciones))
        }.body<RepartoGuardarResponse>()
}