package com.vivero.pickingve.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val orderId: String, // e.g. "10045"
    val customerName: String,
    val customerFiscal: String = "",
    val status: String = "PENDIENTE", // PENDIENTE, EN_PROCESO, COMPLETADO
    val totalLines: Int = 0,
    val fincaCarga: String = "",
    val sectorCarga: String = "",
    val fechaCarga: Long? = null,   // epoch millis de FECHA_CARGA (null = sin fecha)
    val marcaPedido: String = "",
    val observaciones: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val modificado: Boolean = false, // True: el pedido cambió en el último sync (cantidades, líneas, estado)
    val matriculaCamion: String = "",
    val matriculaRemolque: String = "",
    val matriculaRemolqueB: String = "",
    val muelleCarga: String = "",
    val fotoMatriculaCamion: String = "",
    val fotoMatriculaRemolqueA: String = "",
    val fotoMatriculaRemolqueB: String = "",
    val cargado: Boolean = false, // True: se envió el parte final (control de carga)
    val sobrante: Boolean = false, // True: camión terminado -> escaneos descuentan (sobrante)
    val pickingActual: Int = 0 // Mayor picking_numero enviado a BigQuery por cualquier dispositivo
)

@Entity(
    tableName = "order_lines",
    primaryKeys = ["orderLineId"]
)
data class OrderLineEntity(
    val orderLineId: String,  // e.g. "10045-1"
    val orderId: String,      // FK to Order
    val productId: String,    // Reference requested
    val productName: String,
    val requestedQty: Int,    // Units a acopiar = UNIDADES_PENDIENTES
    val pickedQty: Int = 0,   // Quantity picked so far
    val requiresMeasure: Boolean = false, // Linea cuyas plantas hay que medir
    val posicion: Int = 0,    // Número de línea del pedido (POSICION_PEDIDO)
    val empleado: String = "", // Encargado que debe acopiarla (todavia no operativo)
    val litraje: String = "",
    val litrajeDesc: String = "",
    val sector: String = "",
    val sectorDesc: String = "",
    val marca: String = "",
    val prioridad: String = "",
    val ubicacion: String = "",
    val accion: String = "",
    val observaciones: String = "",
    val vigente: Boolean = true, // False: línea retirada del pedido (se muestra pero no se pistoolea)
    val marcado: Boolean = false, // True: línea marcada ([M] en la descripción, tabla LINEA_PEDIDO)
    val acopiadoServidor: Int = 0, // Unidades subidas a BigQuery por cualquier dispositivo (otras tabletas incluidas)
    val fincaAcopio: String = "", // FINCA_RELEVADA: finca real donde está la planta (si difiere de la teórica)
    val sectorAcopio: String = "", // SECTOR_RELEVADO: sector real donde está la planta
    val fincaArticulo: String = "", // FINCA_ARTICULO: finca de procedencia de la planta (artículo)
    val operarioEmail: String = "", // Reparto D-72: email del operario asignado a esta línea
    val operarioNombre: String = "", // Reparto D-72: nombre del operario asignado
    val motivoCierre: String = "", // Código del motivo al cerrar la línea sin completar (SIN_STOCK, ...)
    val motivoCierreTexto: String = "", // Detalle libre cuando el motivo es OTRO
    val cierrePendiente: Boolean = false // True: el cierre aún no pudo subirse al backend
)
