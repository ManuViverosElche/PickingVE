package com.vivero.pickingve.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "encargados")
data class EncargadoEntity(
    @PrimaryKey val id: String,
    val nombre: String,
    val usuario: String,
    val passwordHash: String,
    val rol: String = "ENCARGADO",
    val fincasCarga: String = "",
    val modo: String = "PICKING",
    val email: String = "",
    val activo: Boolean = true
)