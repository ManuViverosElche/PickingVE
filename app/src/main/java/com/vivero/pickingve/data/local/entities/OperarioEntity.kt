package com.vivero.pickingve.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "operarios")
data class OperarioEntity(
    @PrimaryKey val id: String,
    val nombre: String,
    val apellidos: String = "",
    val email: String,
    val passwordHash: String,
    val maquinaria: String = "",
    val fincasCarga: String = "",
    val activo: Boolean = true,
    val debeCambiarPassword: Boolean = true
)
