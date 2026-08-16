package com.vivero.pickingve.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_estado")
data class ChatEstadoEntity(
    @PrimaryKey @ColumnInfo(name = "hilo_id") val hiloId: String,
    @ColumnInfo(name = "ultimo_creado_en") val ultimoCreadoEn: String,
    @ColumnInfo(name = "sin_leer") val sinLeer: Int
)