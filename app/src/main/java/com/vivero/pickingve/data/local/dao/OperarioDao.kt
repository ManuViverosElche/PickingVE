package com.vivero.pickingve.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vivero.pickingve.data.local.entities.OperarioEntity

@Dao
interface OperarioDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(operarios: List<OperarioEntity>)

    @Query("SELECT * FROM operarios WHERE activo = 1 ORDER BY nombre")
    suspend fun getAllActivos(): List<OperarioEntity>

    @Query("SELECT * FROM operarios WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun findByEmail(email: String): OperarioEntity?

    @Query("UPDATE operarios SET passwordHash = :hash, debeCambiarPassword = 0 WHERE LOWER(email) = LOWER(:email)")
    suspend fun updatePasswordHash(email: String, hash: String)

    @Query("DELETE FROM operarios")
    suspend fun clear()
}
