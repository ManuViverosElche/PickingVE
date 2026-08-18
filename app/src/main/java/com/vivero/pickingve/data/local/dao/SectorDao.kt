package com.vivero.pickingve.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vivero.pickingve.data.local.entities.SectorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SectorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sectores: List<SectorEntity>)

    @Query("SELECT * FROM sectores ORDER BY descripcion")
    fun observeAll(): Flow<List<SectorEntity>>

    @Query("SELECT * FROM sectores ORDER BY descripcion")
    suspend fun getAll(): List<SectorEntity>
}