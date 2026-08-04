package com.vivero.pickingve.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.vivero.pickingve.data.local.entities.OrderEntity
import com.vivero.pickingve.data.local.entities.OrderLineEntity
import kotlinx.coroutines.flow.Flow

data class OrderWithTotals(
    val orderId: String,
    val customerName: String,
    val customerFiscal: String,
    val status: String,
    val totalLines: Int,
    val fincaCarga: String,
    val sectorCarga: String,
    val fechaCarga: Long?,
    val marcaPedido: String,
    val observaciones: String,
    val createdAt: Long,
    val totalRequested: Int,
    val totalPicked: Int
)

@Dao
interface OrderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrders(orders: List<OrderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLines(lines: List<OrderLineEntity>)

    @Query("SELECT * FROM orders ORDER BY createdAt DESC")
    fun observeActiveOrders(): Flow<List<OrderEntity>>

    @Query(
        """
        SELECT o.orderId, o.customerName, o.customerFiscal, o.status, o.totalLines,
               o.fincaCarga, o.sectorCarga, o.fechaCarga, o.marcaPedido, o.observaciones,
               o.createdAt,
               COALESCE(SUM(l.requestedQty), 0) AS totalRequested,
               COALESCE(SUM(l.pickedQty), 0) AS totalPicked
        FROM orders o
        LEFT JOIN order_lines l ON l.orderId = o.orderId
        AND l.productId NOT BETWEEN '99990' AND '99999'
        GROUP BY o.orderId, o.customerName, o.customerFiscal, o.status, o.totalLines,
                 o.fincaCarga, o.sectorCarga, o.fechaCarga, o.marcaPedido, o.observaciones,
                 o.createdAt
        ORDER BY COALESCE(o.fechaCarga, o.createdAt) ASC
        """
    )
    fun observeOrdersWithTotals(): Flow<List<OrderWithTotals>>

    @Query(
        """
        SELECT o.orderId, o.customerName, o.customerFiscal, o.status, o.totalLines,
               o.fincaCarga, o.sectorCarga, o.fechaCarga, o.marcaPedido, o.observaciones,
               o.createdAt,
               COALESCE(SUM(l.requestedQty), 0) AS totalRequested,
               COALESCE(SUM(l.pickedQty), 0) AS totalPicked
        FROM orders o
        LEFT JOIN order_lines l ON l.orderId = o.orderId
        AND l.productId NOT BETWEEN '99990' AND '99999'
        WHERE o.orderId LIKE '%' || :query || '%'
           OR o.customerName LIKE '%' || :query || '%'
           OR o.customerFiscal LIKE '%' || :query || '%'
           OR o.marcaPedido LIKE '%' || :query || '%'
        GROUP BY o.orderId, o.customerName, o.customerFiscal, o.status, o.totalLines,
                 o.fincaCarga, o.sectorCarga, o.fechaCarga, o.marcaPedido, o.observaciones,
                 o.createdAt
        ORDER BY COALESCE(o.fechaCarga, o.createdAt) ASC
        """
    )
    fun searchOrders(query: String): Flow<List<OrderWithTotals>>

    @Query("SELECT * FROM order_lines WHERE orderId = :orderId ORDER BY posicion ASC, orderLineId")
    fun observeLinesForOrder(orderId: String): Flow<List<OrderLineEntity>>

    @Query("SELECT * FROM order_lines WHERE orderId = :orderId ORDER BY posicion ASC, orderLineId")
    suspend fun getLinesForOrder(orderId: String): List<OrderLineEntity>

    @Query("UPDATE order_lines SET pickedQty = :pickedQty WHERE orderLineId = :lineId")
    suspend fun updateLinePickedQty(lineId: String, pickedQty: Int)

    @Query("UPDATE order_lines SET pickedQty = pickedQty + :qty WHERE orderLineId = :lineId")
    suspend fun addLinePickedQty(lineId: String, qty: Int)

    @Query("UPDATE order_lines SET requiresMeasure = :requires WHERE orderLineId = :lineId")
    suspend fun setLineRequiresMeasure(lineId: String, requires: Boolean)

    @Query("DELETE FROM order_lines WHERE orderId = :orderId")
    suspend fun deleteLinesForOrder(orderId: String)

    @Query("DELETE FROM orders WHERE orderId = :orderId")
    suspend fun deleteOrder(orderId: String)

    @Query(
        """
        UPDATE orders SET status = CASE
            WHEN (SELECT COALESCE(SUM(pickedQty),0) FROM order_lines WHERE orderId = :orderId) >=
                 (SELECT COALESCE(SUM(requestedQty),0) FROM order_lines WHERE orderId = :orderId)
            THEN 'COMPLETADO' ELSE 'EN_PROCESO' END
        WHERE orderId = :orderId
        """
    )
    suspend fun refreshOrderStatus(orderId: String)

    @Query("SELECT * FROM orders WHERE orderId = :orderId LIMIT 1")
    suspend fun getOrder(orderId: String): OrderEntity?
}
