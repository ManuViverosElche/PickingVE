package com.vivero.pickingve.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vivero.pickingve.data.local.entities.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(products: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: ProductEntity)

    @Query("SELECT * FROM products WHERE ean = :ean OR reference = :ean LIMIT 1")
    suspend fun findByEan(ean: String): ProductEntity?

    @Query("SELECT * FROM products WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%' OR LOWER(reference) LIKE '%' || LOWER(:query) || '%' ORDER BY name LIMIT 15")
    suspend fun search(query: String): List<ProductEntity>

    @Query("SELECT * FROM products ORDER BY reference LIMIT 200")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE reference = :reference AND ean IS NOT NULL AND ean != ''")
    suspend fun findEansByReference(reference: String): List<ProductEntity>

    @Query("SELECT * FROM products WHERE updatedAt > :timestamp")
    suspend fun getUpdatedSince(timestamp: Long): List<ProductEntity>

    @Query("SELECT * FROM products")
    suspend fun getAll(): List<ProductEntity>

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int

    @Query("DELETE FROM products WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}