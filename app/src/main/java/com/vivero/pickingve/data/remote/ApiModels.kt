package com.vivero.pickingve.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiPedidosResponse(
    val fecha: String? = null,
    val pedidos: List<ApiPedido> = emptyList()
)

@Serializable
data class ApiPedido(
    val serie: String = "",
    val numero: String = "",
    val cliente: String = "",
    val clienteFiscal: String = "",
    val estado: Int? = null,
    val fechaCarga: String? = null,
    val sector: String = "",
    val finca: String = "",
    val marcaPedido: String = "",
    val observaciones: String = "",
    val pickingActual: Int = 0,
    val lineas: List<ApiLinea> = emptyList()
)

@Serializable
data class ApiLinea(
    val huella: String? = null,
    val posicion: Int? = null,
    val referencia: String = "",
    val descripcion: String = "",
    val unidades: Double? = null,
    val pendientes: Double? = null,
    val imprimirLinea: Int = 0,
    val marcado: Boolean = false,
    val acopiado: Int = 0,
    val empleado: String = "",
    val litraje: String = "",
    val litrajeDesc: String = "",
    val sector: String = "",
    val sectorDesc: String = "",
    val marca: String = "",
    val fincaRelevada: String = "",
    val sectorRelevado: String = "",
    val ubicacion: String = "",
    val prioridad: String = "",
    val accion: String = "",
    val observaciones: String = ""
)

@Serializable
data class ApiCatalogo(
    val articulos: List<ApiArticulo> = emptyList(),
    val eans: List<ApiEan> = emptyList(),
    val litrajes: List<ApiLitraje> = emptyList()
)

@Serializable
data class ApiCatalogoVersion(
    val version: String = ""
)

@Serializable
data class ApiArticulo(
    @SerialName("ID_ARTICULO") val id: String = "",
    @SerialName("DESCRIPCION_ARTICULO") val descripcion: String = "",
    @SerialName("CODIGO_EAN") val ean: String? = null,
    @SerialName("FINCA_ARTICULO") val finca: String = ""
)

@Serializable
data class ApiEan(
    @SerialName("REFERENCIA_ARTICULO") val referencia: String = "",
    @SerialName("CODIGO_EAN") val ean: String = "",
    @SerialName("CODIGO_LITRAJE") val litraje: String? = null,
    @SerialName("CODIGO_SECTOR") val sector: String? = null
)

@Serializable
data class ApiLitraje(
    @SerialName("ID_LITRAJE") val id: String = "",
    @SerialName("DESCRIPCION_LITRAJE") val descripcion: String = ""
)

@Serializable
data class ApiRegistro(
    @SerialName("record_id") val recordId: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("picking_numero") val pickingNumero: Int,
    @SerialName("picking_tipo") val pickingTipo: String,
    @SerialName("order_line_id") val orderLineId: String = "",
    @SerialName("ean_escaneado") val eanEscaneado: String = "",
    @SerialName("ocr_texto") val ocrTexto: String = "",
    @SerialName("ref_original") val refOriginal: String = "",
    @SerialName("ref_servida") val refServida: String = "",
    @SerialName("sustituido") val sustituido: Boolean = false,
    @SerialName("litros") val litros: Double? = null,
    @SerialName("medida") val medida: String = "",
    @SerialName("calibre") val calibre: String = "",
    @SerialName("cantidad_partida") val cantidadPartida: Double = 0.0,
    @SerialName("fecha_hora") val fechaHora: String,
    @SerialName("empleado_email") val empleadoEmail: String = "",
    @SerialName("empleado_nombre") val empleadoNombre: String = ""
)

@Serializable
data class ApiUploadBody(val registros: List<ApiRegistro>)

@Serializable
data class ApiUploadResponse(val ok: Int = 0)

@Serializable
data class ApiEncargado(
    val id: String = "",
    val nombre: String = "",
    val usuario: String = "",
    val rol: String = "",
    @SerialName("password_hash") val passwordHash: String = "",
    @SerialName("fincas_carga") val fincasCarga: String = "",
    val modo: String = "PICKING",
    val email: String = "",
    val activo: Boolean = true
)

@Serializable
data class ApiEncargadosResponse(val encargados: List<ApiEncargado> = emptyList())

@Serializable
data class ApiFincasResponse(val fincas: List<String> = emptyList())

@Serializable
data class ApiFinca(
    val finca: String = "",
    val nombre: String = "",
    val manual: Boolean = false,
    val oculto: Boolean = false
)

@Serializable
data class ApiFincaGestionResponse(val fincas: List<ApiFinca> = emptyList())

@Serializable
data class CrearFincaRequest(
    val finca: String,
    val nombre: String? = null,
    val activo: Boolean = true
)

@Serializable
data class EliminarFincaRequest(val finca: String)

@Serializable
data class ApiOkResponse(val ok: Int = 0)

@Serializable
data class CambiarPasswordRequest(
    val usuario: String,
    @SerialName("password_actual") val passwordActual: String,
    @SerialName("password_nueva") val passwordNueva: String
)

@Serializable
data class CambiarEmailRequest(
    val usuario: String,
    val email: String
)

@Serializable
data class CrearEncargadoRequest(
    val id: String,
    val nombre: String,
    val usuario: String,
    val password: String,
    val rol: String,
    @SerialName("fincas_carga") val fincasCarga: String,
    val modo: String,
    val email: String,
    val activo: Boolean = true
)

@Serializable
data class LoginRequest(
    val usuario: String = "",
    val password: String = ""
)

@Serializable
data class FcmTokenRequest(
    val email: String,
    val token: String,
    val plataforma: String = "android"
)

@Serializable
data class ApiComentario(
    @SerialName("comentario_id") val id: String = "",
    @SerialName("pedido_id") val pedido: String = "",
    @SerialName("linea_huella") val linea: String? = null,
    @SerialName("autor_email") val autorEmail: String = "",
    @SerialName("autor_nombre") val autorNombre: String = "",
    val rol: String = "",
    val canal: String = "",
    val texto: String = "",
    @SerialName("creado_en") val creadoEn: String = ""
)

@Serializable
data class ApiComentariosResponse(val comentarios: List<ApiComentario> = emptyList())

@Serializable
data class ComentarioRequest(
    @SerialName("pedido_id") val pedidoId: String,
    @SerialName("linea_huella") val lineaHuella: String? = null,
    val texto: String,
    @SerialName("autor_email") val autorEmail: String,
    @SerialName("autor_nombre") val autorNombre: String = "",
    val rol: String = "ENCARGADO",
    val canal: String = "app"
)