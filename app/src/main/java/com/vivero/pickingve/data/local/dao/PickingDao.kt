package com.vivero.pickingve.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vivero.pickingve.data.local.entities.PickingRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PickingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: PickingRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<PickingRecordEntity>)

    @Query("SELECT * FROM picking_records WHERE syncedBigQuery = 0 ORDER BY timestamp")
    fun observePendingBigQuery(): Flow<List<PickingRecordEntity>>

    @Query("SELECT * FROM picking_records WHERE syncedTelegram = 0 ORDER BY timestamp")
    fun observePendingTelegram(): Flow<List<PickingRecordEntity>>

    @Query("SELECT * FROM picking_records WHERE orderId = :orderId ORDER BY timestamp")
    suspend fun getRecordsForOrder(orderId: String): List<PickingRecordEntity>

    @Query(
        """
        SELECT COUNT(*) FROM picking_records
        WHERE orderId = :orderId AND syncedTelegram = 0
        """
    )
    suspend fun countPendingTelegramForOrder(orderId: String): Int

    @Query("UPDATE picking_records SET syncedBigQuery = 1 WHERE recordId IN (:ids)")
    suspend fun markSyncedBigQuery(ids: List<String>)

    @Query("UPDATE picking_records SET syncedTelegram = 1 WHERE recordId IN (:ids)")
    suspend fun markSyncedTelegram(ids: List<String>)

    @Query(
        "SELECT COALESCE(MAX(pickingNumber), 0) FROM picking_records WHERE orderId = :orderId"
    )
    suspend fun maxPickingNumber(orderId: String): Int

    @Query(
        """
        SELECT * FROM picking_records
        WHERE orderId = :orderId AND needsLabel = 1 AND syncedTelegram = 0
        ORDER BY timestamp
        """
    )
    fun observePendingLabels(orderId: String): Flow<List<PickingRecordEntity>>

    @Query(
        """
        SELECT * FROM picking_records
        WHERE orderId = :orderId AND needsLabel = 1 AND syncedTelegram = 0
        ORDER BY timestamp
        """
    )
    suspend fun getPendingLabelsForOrder(orderId: String): List<PickingRecordEntity>
    @Query(
        """
        SELECT * FROM picking_records
        WHERE orderId = :orderId AND actualProductId = :actualProductId
            AND (measure = :measure OR (measure IS NULL AND :measure IS NULL))
            AND (caliber = :caliber OR (caliber IS NULL AND :caliber IS NULL))
            AND syncedBigQuery = 0
        LIMIT 1
        """
    )
    suspend fun findMatchingUnsynced(
        orderId: String,
        actualProductId: String,
        measure: String?,
        caliber: String?
    ): PickingRecordEntity?

    @Query("UPDATE picking_records SET batchQty = batchQty + :addQty WHERE recordId = :recordId")
    suspend fun incrementBatchQty(recordId: String, addQty: Int)
}