package com.vivero.pickingve.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vivero.pickingve.data.local.entities.EncargadoEntity

@Dao
interface EncargadoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(encargados: List<EncargadoEntity>)

    @Query("DELETE FROM encargados")
    suspend fun clear()

    @Query("SELECT * FROM encargados")
    suspend fun getAll(): List<EncargadoEntity>

    @Query("SELECT * FROM encargados WHERE usuario = :usuario LIMIT 1")
    suspend fun findByUsuario(usuario: String): EncargadoEntity?
}