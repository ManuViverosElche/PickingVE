package com.vivero.pickingve.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vivero.pickingve.data.local.entities.PickingRecordEntity
import kotlinx.coroutines.flow.Flow

data class LabelsRequestedByLine(val orderLineId: String?, val cnt: Int)

@Dao
interface PickingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: PickingRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<PickingRecordEntity>)

    @Query("SELECT * FROM picking_records WHERE syncedBigQuery = 0 ORDER BY timestamp")
    fun observePendingBigQuery(): Flow<List<PickingRecordEntity>>

    @Query("SELECT * FROM picking_records WHERE orderId = :orderId ORDER BY timestamp")
    suspend fun getRecordsForOrder(orderId: String): List<PickingRecordEntity>

    @Query("SELECT * FROM picking_records WHERE orderId = :orderId ORDER BY timestamp")
    fun observeRecordsForOrder(orderId: String): Flow<List<PickingRecordEntity>>

    @Query("SELECT * FROM picking_records WHERE orderLineId = :lineId ORDER BY timestamp DESC")
    suspend fun getRecordsForLine(lineId: String): List<PickingRecordEntity>

    @Query(
        """
        SELECT * FROM picking_records
        WHERE orderId = :orderId AND needsLabel = 1 AND labelSent = 0 AND deleted = 0
        ORDER BY timestamp
        """
    )
    fun observePendingLabels(orderId: String): Flow<List<PickingRecordEntity>>

    @Query(
        """
        SELECT * FROM picking_records
        WHERE orderId = :orderId AND needsLabel = 1 AND labelSent = 0 AND deleted = 0
        ORDER BY timestamp
        """
    )
    suspend fun getPendingLabelsForOrder(orderId: String): List<PickingRecordEntity>

    @Query(
        """
        SELECT * FROM picking_records
        WHERE orderId = :orderId AND needsLabel = 1 AND labelSent = 1
        ORDER BY labelSentAt DESC, timestamp
        """
    )
    fun observeLabelsHistory(orderId: String): Flow<List<PickingRecordEntity>>

    @Query(
        """
        SELECT orderLineId, COUNT(*) AS cnt FROM picking_records
        WHERE orderId = :orderId AND needsLabel = 1 AND labelSent = 1
        GROUP BY orderLineId
        """
    )
    fun observeLabelsRequestedByLine(orderId: String): Flow<List<LabelsRequestedByLine>>

    @Query(
        """
        SELECT orderLineId, SUM(batchQty) AS cnt FROM picking_records
        WHERE orderId = :orderId AND isSubstituted = 1 AND orderLineId IS NOT NULL
        GROUP BY orderLineId
        """
    )
    fun observeSubstitutedByLine(orderId: String): Flow<List<LabelsRequestedByLine>>

    @Query(
        "UPDATE picking_records SET labelSent = 1, labelSentAt = :at WHERE recordId IN (:ids)"
    )
    suspend fun markLabelsSent(ids: List<String>, at: Long)

    @Query("UPDATE picking_records SET syncedBigQuery = 1, wasUploaded = 1 WHERE recordId IN (:ids)")
    suspend fun markSyncedBigQuery(ids: List<String>)

    @Query("DELETE FROM picking_records WHERE recordId = :recordId")
    suspend fun deleteRecordPhysical(recordId: String)

    @Query(
        """
        SELECT orderLineId, COALESCE(SUM(batchQty), 0) AS cnt FROM picking_records
        WHERE orderId = :orderId AND deleted = 1 AND syncedBigQuery = 0
        GROUP BY orderLineId
        """
    )
    fun observeCompensacionesPendientes(orderId: String): Flow<List<LabelsRequestedByLine>>

    @Query(
        """
        SELECT orderLineId, COALESCE(SUM(batchQty), 0) AS cnt FROM picking_records
        WHERE orderId = :orderId AND deleted = 1 AND syncedBigQuery = 0
        GROUP BY orderLineId
        """
    )
    suspend fun getCompensacionesPendientes(orderId: String): List<LabelsRequestedByLine>

    @Query(
        "SELECT COALESCE(MAX(pickingNumber), 0) FROM picking_records WHERE orderId = :orderId"
    )
    suspend fun maxPickingNumber(orderId: String): Int

    @Query(
        """
        SELECT * FROM picking_records
        WHERE orderId = :orderId
          AND ((:orderLineId IS NULL AND orderLineId IS NULL)
               OR orderLineId = :orderLineId)
          AND (scannedEan = :ean
               OR (scannedEan IS NULL AND :ean IS NULL AND actualProductId = :actualProductId))
          AND (measure = :measure OR (measure IS NULL AND :measure IS NULL))
          AND (caliber = :caliber OR (caliber IS NULL AND :caliber IS NULL))
        LIMIT 1
        """
    )
    suspend fun findMatchingRecord(
        orderId: String,
        orderLineId: String?,
        ean: String?,
        actualProductId: String,
        measure: String?,
        caliber: String?
    ): PickingRecordEntity?

    @Query(
        """
        UPDATE picking_records
        SET batchQty = batchQty + :addQty, syncedBigQuery = 0
        WHERE recordId = :recordId
        """
    )
    suspend fun incrementBatchQty(recordId: String, addQty: Int)

    @Query("UPDATE picking_records SET batchQty = batchQty - :qty, syncedBigQuery = 0 WHERE recordId = :recordId")
    suspend fun decrementBatchQty(recordId: String, qty: Int)

    @Query("UPDATE picking_records SET needsLabel = 1, syncedBigQuery = 0 WHERE recordId = :recordId")
    suspend fun markNeedsLabel(recordId: String)

    @Query(
        """
        UPDATE picking_records
        SET needsLabel = 1, labelReason = :labelReason, labelFormat = :labelFormat, syncedBigQuery = 0
        WHERE recordId = :recordId
        """
    )
    suspend fun markLabelRequested(recordId: String, labelReason: String, labelFormat: String)

    @Query("UPDATE picking_records SET deleted = 1, syncedBigQuery = 0 WHERE recordId = :recordId")
    suspend fun deleteRecord(recordId: String)

    @Query("UPDATE picking_records SET deleted = 1, syncedBigQuery = 0 WHERE recordId IN (:recordIds)")
    suspend fun deleteRecordsByIds(recordIds: List<String>)

    @Query(
        "UPDATE picking_records SET batchQty = batchQty - 1, syncedBigQuery = 0 " +
            "WHERE recordId = :recordId AND batchQty > 1"
    )
    suspend fun decrementLabelQty(recordId: String): Int

    @Query(
        "UPDATE picking_records SET needsLabel = 0, labelReason = '', labelFormat = '', syncedBigQuery = 0 " +
            "WHERE recordId = :recordId"
    )
    suspend fun clearLabel(recordId: String)

    @Query("DELETE FROM picking_records")
    suspend fun clearAll()
}
