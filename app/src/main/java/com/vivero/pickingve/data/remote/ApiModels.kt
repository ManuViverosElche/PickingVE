package com.vivero.pickingve.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiPedidosResponse(
    val fecha: String? = null,
    val pedidos: List<ApiPedido> = emptyList()
)

@Serializable
data class ApiNotificarResponse(
    val ok: Boolean = false,
    val pedidosModificados: Int = 0,
    val comentariosNuevos: Int = 0,
    val notificacionesEnviadas: Int = 0
)

@Serializable
data class CambioLineaDetalle(
    @SerialName("pedido") val pedido: String,
    @SerialName("linea") val linea: String = "",
    @SerialName("tipo") val tipo: String,
    @SerialName("descripcion") val descripcion: String
)

@Serializable
data class NotificarRequest(
    @SerialName("pedidos_modificados") val pedidosModificados: List<String> = emptyList(),
    @SerialName("cambios_detalle") val cambiosDetalle: List<CambioLineaDetalle> = emptyList()
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
    val acopiadoOperario: Int = 0,
    val acopiado: Int = 0,
    val empleado: String = "",
    val litraje: String = "",
    val litrajeDesc: String = "",
    val sector: String = "",
    val sectorDesc: String = "",
    val marca: String = "",
    val fincaRelevada: String = "",
    val sectorRelevado: String = "",
    val fincaArticulo: String = "",
    val operarioEmail: String = "",
    val operarioNombre: String = "",
    val ubicacion: String = "",
    val prioridad: String = "",
    val accion: String = "",
    val observaciones: String = ""
)

@Serializable
data class ApiCatalogo(
    val articulos: List<ApiArticulo> = emptyList(),
    val eans: List<ApiEan> = emptyList(),
    val litrajes: List<ApiLitraje> = emptyList(),
    val sectores: List<ApiSector> = emptyList()
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
data class ApiSector(
    @SerialName("ID_SECTOR") val id: String = "",
    @SerialName("DESCRIPCION_SECTOR") val descripcion: String = ""
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
    @SerialName("empleado_nombre") val empleadoNombre: String = "",
    @SerialName("needs_label") val needsLabel: Boolean = false,
    @SerialName("label_reason") val labelReason: String = ""
)

@Serializable
data class ApiUploadBody(val registros: List<ApiRegistro>)

@Serializable
data class ApiUploadResponse(
    val ok: Int = 0,
    val duplicados: Int = 0,
    @SerialName("accepted_ids") val acceptedIds: List<String> = emptyList()
)

@Serializable
data class ApiParteBody(
    @SerialName("parte_id") val parteId: String,
    @SerialName("pedido_id") val pedidoId: String,
    @SerialName("picking_tipo") val pickingTipo: String,
    @SerialName("picking_numero") val pickingNumero: Int,
    @SerialName("matricula_camion") val matriculaCamion: String = "",
    @SerialName("matricula_remolque") val matriculaRemolque: String = "",
    val peso: Double? = null,
    val finca: String = "",
    val zona: String = "",
    @SerialName("empleado_email") val empleadoEmail: String = "",
    @SerialName("empleado_nombre") val empleadoNombre: String = "",
    val fecha: String = ""
)

@Serializable
data class CompensaRegistro(
    @SerialName("record_id") val recordId: String,
    @SerialName("pedido_id") val pedidoId: String,
    val cantidad: Double
)

@Serializable
data class CompensaBody(val registros: List<CompensaRegistro>)

@Serializable
data class CompensaResponse(val ok: Int = 0)

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
    @SerialName("adjunto_url") val adjuntoUrl: String = "",
    @SerialName("creado_en") val creadoEn: String = ""
)

@Serializable
data class ApiComentariosResponse(val comentarios: List<ApiComentario> = emptyList())

@Serializable
data class ApiMatriculaResponse(
    val ok: Boolean = false,
    @SerialName("foto_url") val fotoUrl: String = ""
)

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

@Serializable
data class ApiAdjuntoResponse(
    val ok: Boolean = true,
    @SerialName("adjunto_url") val adjuntoUrl: String? = null
)

@Serializable
data class CierreLineaRequest(
    @SerialName("pedido_id") val pedidoId: String,
    @SerialName("linea_huella") val lineaHuella: String,
    @SerialName("cantidad_faltante") val cantidadFaltante: Int,
    val motivo: String,
    @SerialName("motivo_texto") val motivoTexto: String = "",
    @SerialName("operario_email") val operarioEmail: String = "",
    @SerialName("operario_nombre") val operarioNombre: String = ""
)

@Serializable
data class PerfilOperarioResponse(
    val email: String = "",
    val nombre: String = "",
    val maquinaria: String = "",
    @SerialName("fincas_carga") val fincasCarga: String = ""
)

@Serializable
data class PedidoEstadoResponse(
    val numero: String = "",
    val estado: Int = 0,
    /** True cuando el pedido esta ALBARANEADO (pendientes a 0 por factura). */
    val albaran: Boolean = false
)

// ---- D-190: camion compartido entre varios pedidos ----

@Serializable
data class CamionCompartidoRequest(
    val fecha: String,
    @SerialName("matricula_camion") val matriculaCamion: String = "",
    @SerialName("matricula_remolque") val matriculaRemolque: String = "",
    val pedidos: List<String>,
    @SerialName("creado_por") val creadoPor: String = ""
)

@Serializable
data class CamionDePedidoResponse(
    val encontrado: Boolean = false,
    @SerialName("matricula_camion") val matriculaCamion: String = "",
    @SerialName("matricula_remolque") val matriculaRemolque: String = "",
    val pedidos: List<String> = emptyList()
)

@Serializable
data class DiscrepanciaRequest(
    @SerialName("pedido_id") val pedidoId: String,
    @SerialName("linea_huella") val lineaHuella: String,
    val declarado: Int,
    val puntado: Int,
    val mensaje: String = "",
    @SerialName("operario_email") val operarioEmail: String
)

// ---- D-166 Login de operarios ----

@Serializable
data class LoginOperarioRequest(
    val email: String,
    val password: String
)

@Serializable
data class ApiLoginOperarioResponse(
    val id: String = "",
    val nombre: String = "",
    val apellidos: String = "",
    val email: String = "",
    val maquinaria: String = "",
    @SerialName("fincas_carga") val fincasCarga: String = "",
    @SerialName("password_provisional") val passwordProvisional: Boolean = true,
    val modo: String = "ACOPIO"
)

@Serializable
data class ApiOperarioApp(
    val id: String = "",
    val nombre: String = "",
    val apellidos: String = "",
    val email: String = "",
    @SerialName("password_hash") val passwordHash: String = "",
    val maquinaria: String = "",
    @SerialName("fincas_carga") val fincasCarga: String = "",
    val activo: Boolean = true,
    @SerialName("password_provisional") val passwordProvisional: Boolean = true,
    val modo: String = "ACOPIO"
)

@Serializable
data class ApiOperariosAppResponse(val operarios: List<ApiOperarioApp> = emptyList())

@Serializable
data class CambiarPasswordOperarioRequest(
    val email: String,
    @SerialName("password_actual") val passwordActual: String,
    @SerialName("password_nueva") val passwordNueva: String
)

// ---- D-169 Ayuda por líneas concretas ----

@Serializable
data class AyudaPermisoLineaApi(
    @SerialName("pedido_id") val pedidoId: String,
    @SerialName("linea_huella") val lineaHuella: String
)

@Serializable
data class AyudaPermisoConcederRequest(
    val lineas: List<AyudaPermisoLineaApi>,
    @SerialName("ayudante_email") val ayudanteEmail: String,
    @SerialName("concedido_por_email") val concedidoPorEmail: String = ""
)

@Serializable
data class AyudaRevocarRequest(
    val lineas: List<AyudaPermisoLineaApi>,
    @SerialName("ayudante_email") val ayudanteEmail: String
)

@Serializable
data class ApiAyudaPermisosResponse(
    val permisos: List<ApiAyudaPermiso> = emptyList()
)

@Serializable
data class ApiAyudaPermiso(
    @SerialName("pedido_id") val pedidoId: String = "",
    @SerialName("linea_huella") val lineaHuella: String = ""
)

// ---- D-171 Reabrir línea cerrada ----

@Serializable
data class ReabrirLineaRequest(
    @SerialName("pedido_id") val pedidoId: String,
    @SerialName("linea_huella") val lineaHuella: String,
    @SerialName("reabierta_por_email") val reabiertaPorEmail: String = "",
    val motivo: String = ""
)

// ---- D-172 Gestión de faena desde la app (reparto del panel) ----

@Serializable
data class RepartoAsignacionApi(
    @SerialName("pedido_id") val pedidoId: String,
    @SerialName("linea_huella") val lineaHuella: String,
    @SerialName("operario_nombre") val operarioNombre: String = "",
    @SerialName("operario_email") val operarioEmail: String = ""
)

@Serializable
data class RepartoGuardarRequest(
    val asignaciones: List<RepartoAsignacionApi> = emptyList()
)

@Serializable
data class RepartoGuardarResponse(
    val ok: Boolean = false,
    val guardadas: Int = 0,
    val borradas: Int = 0
)
// ---- D-213: panel manager (superusuario) ----

@kotlinx.serialization.Serializable
data class ManagerFecha(
    val fecha: String = "",
    val pedidos: Int = 0,
    val pendientes: Int = 0,
    val cargados: Int = 0,
    val enviados: Int = 0,
    val total: Int = 0
)

@kotlinx.serialization.Serializable
data class ManagerFechasResponse(val fechas: List<ManagerFecha> = emptyList())

@kotlinx.serialization.Serializable
data class ManagerHistoricoPedido(
    val serie: String = "",
    val numero: String = "",
    val cliente: String = "",
    val fechaCarga: String? = null,
    val sector: String = "",
    val finca: String = "",
    val estado: String = "",
    val cargado: Boolean = false,
    val tieneParteFinal: Boolean = false,
    val totalPistoleado: Int = 0,
    val totalEventos: Int = 0
)

@kotlinx.serialization.Serializable
data class ManagerHistoricoDiaResponse(
    val fecha: String = "",
    val pedidos: List<ManagerHistoricoPedido> = emptyList()
)

@kotlinx.serialization.Serializable
data class ManagerEtiqueta(
    val referencia: String = "",
    val litraje: String = "",
    val sector: String = "",
    val descripcion: String = "",
    val cantidad: Double = 0.0,
    val motivo: String = "",
    val estado: String = ""
)

@kotlinx.serialization.Serializable
data class ManagerEtiquetasPedido(
    val pedido: String = "",
    val cliente: String = "",
    val finca: String = "",
    val etiquetas: List<ManagerEtiqueta> = emptyList()
)

@kotlinx.serialization.Serializable
data class ManagerEtiquetasResponse(val pedidos: List<ManagerEtiquetasPedido> = emptyList())

@kotlinx.serialization.Serializable
data class ManagerRegistro(
    val parte: String = "",
    val fechaHora: String = "",
    val empleado: String = "",
    val refServida: String = "",
    val cantidad: Double = 0.0
)

@kotlinx.serialization.Serializable
data class ManagerHistoricoDetalleResponse(
    val pedido: String = "",
    val matriculas: List<MatriculaItem> = emptyList(),
    val registros: List<ManagerRegistro> = emptyList()
)

// ---- D-216: configuracion de maquinarias y familias (panel) ----

@kotlinx.serialization.Serializable
data class MaquinariaItem(
    val id: String = "",
    val nombre: String = "",
    val descripcion: String = "",
    val familia: String = "",
    val activo: Boolean = true
)

@kotlinx.serialization.Serializable
data class MaquinariasResponse(val maquinarias: List<MaquinariaItem> = emptyList())

@kotlinx.serialization.Serializable
data class FamiliaItem(
    val id: String = "",
    val nombre: String = "",
    val descripcion: String = "",
    val activo: Boolean = true
)

@kotlinx.serialization.Serializable
data class FamiliasResponse(val familias: List<FamiliaItem> = emptyList())

@kotlinx.serialization.Serializable
data class MaquinariaBody(
    val id: String = "",
    val nombre: String = "",
    val descripcion: String = "",
    val familia: String = "",
    val activo: Boolean = true
)

@kotlinx.serialization.Serializable
data class OkResponse(val ok: Boolean = false)

@kotlinx.serialization.Serializable
data class MatriculaItem(
    val tipo: String? = null,
    val matricula: String? = null,
    val muelle: String? = null,
    val fotoUrl: String? = null,
    val creadoEn: String? = null
)

// ---- D-218: Inventario ----

@kotlinx.serialization.Serializable
data class ApiInvSector(
    val id: String = "",
    @SerialName("descripcion") val descripcion: String = "",
    @SerialName("cerrado") val cerrado: Boolean = false,
    @SerialName("tieneInventario") val tieneInventario: Boolean = false
)

@kotlinx.serialization.Serializable
data class ApiInvFinca(
    val finca: String = "",
    val prefijo: String = "",
    val sectores: List<ApiInvSector> = emptyList()
)

@kotlinx.serialization.Serializable
data class ApiInvFincasResponse(val fincas: List<ApiInvFinca> = emptyList())

@kotlinx.serialization.Serializable
data class ApiInvStockItem(
    val sector: String = "",
    val ref: String = "",
    val litraje: String = "",
    val nombre: String = "",
    val ean: String = "",
    val stock: Double = 0.0
)

@kotlinx.serialization.Serializable
data class ApiInvStockResponse(
    val finca: String = "",
    val filas: List<ApiInvStockItem> = emptyList()
)

@kotlinx.serialization.Serializable
data class ApiInvVersion(val version: String = "")

@kotlinx.serialization.Serializable
data class ApiInvRegistro(
    @SerialName("record_id") val recordId: String,
    @SerialName("finca") val finca: String,
    @SerialName("sector") val sector: String,
    @SerialName("ean_escaneado") val eanEscaneado: String = "",
    @SerialName("ocr_texto") val ocrTexto: String = "",
    @SerialName("ref_articulo") val refArticulo: String = "",
    @SerialName("litraje") val litraje: String = "",
    @SerialName("sector_etiqueta") val sectorEtiqueta: String = "",
    @SerialName("nombre_planta") val nombrePlanta: String = "",
    @SerialName("cantidad") val cantidad: Int = 1,
    @SerialName("fuera_sector") val fueraSector: Boolean = false,
    @SerialName("reetiquetar") val reetiquetar: Boolean = false,
    @SerialName("sin_ean") val sinEan: Boolean = false,
    @SerialName("label_motivo") val labelMotivo: String = "",
    @SerialName("incidencia_texto") val incidenciaTexto: String = "",
    @SerialName("es_hueco") val esHueco: Boolean = false,
    @SerialName("modo_inventario") val modoInventario: String = "",
    @SerialName("lineal_session_id") val linealSessionId: String = "",
    @SerialName("latitud") val latitud: Double? = null,
    @SerialName("longitud") val longitud: Double? = null,
    @SerialName("fecha_hora") val fechaHora: String,
    @SerialName("empleado_email") val empleadoEmail: String = "",
    @SerialName("empleado_nombre") val empleadoNombre: String = ""
)

@kotlinx.serialization.Serializable
data class ApiInvUploadBody(val registros: List<ApiInvRegistro>)

@kotlinx.serialization.Serializable
data class ApiInvUploadResponse(
    val ok: Int = 0,
    val duplicados: Int = 0,
    @SerialName("accepted_ids") val acceptedIds: List<String> = emptyList()
)

@kotlinx.serialization.Serializable
data class ApiInvCompensaBody(
    @SerialName("record_ids") val recordIds: List<String>
)

@kotlinx.serialization.Serializable
data class ApiInvBorrarBody(
    val finca: String? = null,
    val sector: String? = null,
    val desde: String? = null
)

@kotlinx.serialization.Serializable
data class ApiInvCerrarBody(
    val finca: String,
    val sector: String
)

@kotlinx.serialization.Serializable
data class ApiInvProgresoLinea(
    val sector: String = "",
    val ref: String = "",
    val litraje: String = "",
    val contado: Double = 0.0,
    val eventos: Int = 0,
    val fuera: Int = 0,
    val reetiquetar: Int = 0
)

@kotlinx.serialization.Serializable
data class ApiInvProgresoResumen(
    val sector: String = "",
    val total: Double = 0.0,
    val fuera: Int = 0
)

@kotlinx.serialization.Serializable
data class ApiInvProgresoResponse(
    val finca: String = "",
    val lineas: List<ApiInvProgresoLinea> = emptyList(),
    val resumen: List<ApiInvProgresoResumen> = emptyList()
)
