package com.vivero.pickingve.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sectores")
data class SectorEntity(
    @PrimaryKey val id: String,
    val descripcion: String
)