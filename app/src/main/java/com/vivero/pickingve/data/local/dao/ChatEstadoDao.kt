package com.vivero.pickingve.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.vivero.pickingve.data.local.entities.ChatEstadoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatEstadoDao {

    @Query(
        """
        INSERT INTO chat_estado (hilo_id, ultimo_creado_en, sin_leer)
        VALUES (:hiloId, :ultimoCreadoEn, :sinLeer)
        ON CONFLICT(hilo_id) DO UPDATE SET
            ultimo_creado_en = :ultimoCreadoEn,
            sin_leer = :sinLeer
        """
    )
    suspend fun upsert(hiloId: String, ultimoCreadoEn: String, sinLeer: Int)

    @Query("SELECT * FROM chat_estado WHERE hilo_id = :hiloId LIMIT 1")
    suspend fun get(hiloId: String): ChatEstadoEntity?

    @Query("UPDATE chat_estado SET sin_leer = 0 WHERE hilo_id = :hiloId")
    suspend fun marcarLeido(hiloId: String)

    @Query("SELECT * FROM chat_estado")
    fun observeAll(): Flow<List<ChatEstadoEntity>>
}