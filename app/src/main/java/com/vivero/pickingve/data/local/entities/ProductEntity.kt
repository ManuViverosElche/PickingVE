package com.vivero.pickingve.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [Index(value = ["ean"]), Index(value = ["reference"])]
)
data class ProductEntity(
    @PrimaryKey val id: String, // e.g. "REF-101"
    val reference: String,      // Commercial code / reference
    val ean: String?,           // EAN-13 code
    val name: String,           // Commercial name (e.g., "Olearia 25L")
    val defaultLiters: Float?,  // Default Litraje
    val defaultMeasure: String?,// Default Medida (e.g., "80-100cm")
    val defaultCaliber: String?,// Default Calibre (e.g., "C25")
    val batchQtyDefault: Int = 10, // Default batch quantity per pot label
    val updatedAt: Long = System.currentTimeMillis()
)
