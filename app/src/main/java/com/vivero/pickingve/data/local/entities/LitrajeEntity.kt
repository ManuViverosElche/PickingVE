package com.vivero.pickingve.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "litrajes")
data class LitrajeEntity(
    @PrimaryKey val id: String,
    val descripcion: String
)
