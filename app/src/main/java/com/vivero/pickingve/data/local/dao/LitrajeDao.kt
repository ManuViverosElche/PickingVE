package com.vivero.pickingve.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vivero.pickingve.data.local.entities.LitrajeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LitrajeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(litrajes: List<LitrajeEntity>)

    @Query("SELECT * FROM litrajes ORDER BY descripcion")
    fun observeAll(): Flow<List<LitrajeEntity>>

    @Query("SELECT * FROM litrajes ORDER BY descripcion")
    suspend fun getAll(): List<LitrajeEntity>
}
