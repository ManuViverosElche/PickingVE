package com.vivero.pickingve.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Stock esperado (FactuSOL) por sector, descargado de /api/inventario/stock (D-218). */
@Entity(
    tableName = "inventario_stock",
    primaryKeys = ["ref", "litraje", "sector"]
)
data class InventoryStockEntity(
    val ref: String,
    val litraje: String = "",
    val sector: String = "",
    val nombre: String = "",
    val ean: String = "",
    val stock: Double = 0.0
)

/**
 * Registro de pistoleo de inventario (D-219). Cada escaneo cuenta 1 planta.
 * - [sector] es el sector que se esta inventariando; [sectorEtiqueta] el que
 *   dice la etiqueta de la planta (difiere => fuera de sector, D-220).
 * - [sinEan] = conteo manual u OCR sin EAN => etiqueta para sacar en el informe.
 * - GPS one-shot por registro (D-221); null si no hubo senal.
 */
@Entity(
    tableName = "inventario_records",
    indices = [Index(value = ["finca", "sector"]), Index(value = ["eanEscaneado"])]
)
data class InventoryRecordEntity(
    @PrimaryKey val recordId: String, // UUID
    val finca: String,
    val sector: String,
    val eanEscaneado: String? = null,
    val ocrTexto: String? = null,
    val refArticulo: String = "",
    val litraje: String = "",
    val sectorEtiqueta: String = "",
    val nombrePlanta: String = "",
    val cantidad: Int = 1,
    val fueraSector: Boolean = false,
    val reetiquetar: Boolean = false,
    val sinEan: Boolean = false,
    /** Motivo de "etiqueta a sacar" (p.ej. "Falta etiqueta EAN", D-241). */
    val labelMotivo: String = "",
    /** Descripción/hipótesis del operario para plantas sin identificar (D-240). */
    val incidenciaTexto: String = "",
    /** True para huecos libres registrados en modo lineal (D-243); no cuenta como planta. */
    val esHueco: Boolean = false,
    /** Modo de pistoleo del registro: ESTANDAR | LINEAL (D-243). */
    val modoInventario: String = "ESTANDAR",
    /** Identificador de la sesión lineal (A -> B) para agrupar líneas independientes (D-244). */
    val linealSessionId: String = "",
    val latitud: Double? = null,
    val longitud: Double? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val syncedBigQuery: Boolean = false,
    val deleted: Boolean = false,
    val wasUploaded: Boolean = false,
    val empleadoEmail: String = "",
    val empleadoNombre: String = ""
)

/** Conteo local agregado por referencia+litraje para pintar la lista del sector. */
data class InvContadoLocal(
    val ref: String,
    val litraje: String,
    val contado: Int,
    val eventos: Int,
    val fuera: Int
)
