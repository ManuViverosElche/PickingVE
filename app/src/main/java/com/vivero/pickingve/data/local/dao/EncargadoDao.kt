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

    /** D-201: login por email obligatorio; resuelve tambien por email. */
    @Query(
        "SELECT * FROM encargados " +
            "WHERE LOWER(usuario) = LOWER(:valor) OR LOWER(email) = LOWER(:valor) LIMIT 1"
    )
    suspend fun findByUsuarioOEmail(valor: String): EncargadoEntity?

    @Query("UPDATE encargados SET passwordHash = :hash WHERE usuario = :usuario")
    suspend fun updatePasswordHash(usuario: String, hash: String)

    @Query("UPDATE encargados SET email = :email WHERE usuario = :usuario")
    suspend fun updateEmail(usuario: String, email: String)
}