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
    val createdAt: Long = System.currentTimeMillis()
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
    val observaciones: String = ""
)
