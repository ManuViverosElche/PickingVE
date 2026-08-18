package com.vivero.pickingve.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "picking_records")
data class PickingRecordEntity(
    @PrimaryKey val recordId: String, // UUID
    val orderId: String,               // Order Number (e.g. 10045)
    val pickingNumber: Int,            // Picking session number (e.g. 1, 2)
    val pickingType: String,           // "I" (Inicial) or "F" (Final)
    val orderLineId: String?,          // Assigned Order Line ID
    val scannedEan: String?,           // Scanned EAN barcode if present
    val ocrRawText: String?,           // OCR captured text if scanned via OCR
    val originalProductId: String,     // Original requested reference
    val actualProductId: String,       // Actual reference delivered (substituted or same)
    val isSubstituted: Boolean = false,// Flag if reference was changed
    val liters: Float?,                // Litraje
    val measure: String?,              // Medida
    val caliber: String?,              // Calibre
    val batchQty: Int,                 // Quantity in this batch/partida
    val needsLabel: Boolean = false,   // Planta llego sin etiqueta -> hay que sacar etiqueta
    val labelReason: String = "",      // Motivo de la etiqueta: "" | MACETA_ROTA | CAMBIO_FORMATO
    val labelFormat: String = "",      // Formato destino si labelReason == CAMBIO_FORMATO (descripcion LITRAJES)
    val labelSent: Boolean = false,    // Las etiquetas de este registro ya se solicitaron por Telegram
    val labelSentAt: Long? = null,     // Cuando se solicitaron (historial)
    val timestamp: Long = System.currentTimeMillis(),
    val syncedBigQuery: Boolean = false,
    val syncedTelegram: Boolean = false,
    val empleadoEmail: String = "",
    val empleadoNombre: String = "",
    val deleted: Boolean = false,
    val wasUploaded: Boolean = false
)
