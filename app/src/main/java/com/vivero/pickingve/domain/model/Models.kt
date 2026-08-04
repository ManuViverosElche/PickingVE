package com.vivero.pickingve.domain.model

data class PickingSession(
    val orderId: String,
    val pickingNumber: Int,
    val pickingType: String, // "I" -> Inicial, "F" -> Final
    val activatedAt: Long,
    val completedAt: Long? = null
)

data class PickingLine(
    val orderLineId: String,
    val orderId: String,
    val productId: String,
    val productName: String,
    val requestedQty: Int,
    val pickedQty: Int
)

data class ScanResult(
    val ean: String?,
    val ocrText: String?,
    val timestamp: Long = System.currentTimeMillis()
)