package com.vivero.pickingve.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
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
    val muelleCarga: String,
    val fechaCarga: Long?,
    val marcaPedido: String,
    val observaciones: String,
    val createdAt: Long,
    val modificado: Boolean,
    val matriculaCamion: String,
    val matriculaRemolque: String,
    val cargado: Boolean,
    val sobrante: Boolean,
    val totalRequested: Int,
    val totalPicked: Int
)

data class OrderConLineas(
    @Embedded val order: OrderEntity,
    @Relation(parentColumn = "orderId", entityColumn = "orderId")
    val lineas: List<OrderLineEntity>
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
               o.fincaCarga, o.sectorCarga, o.muelleCarga, o.fechaCarga, o.marcaPedido, o.observaciones,
               o.createdAt, o.modificado, o.matriculaCamion, o.matriculaRemolque, o.cargado, o.sobrante,
               COALESCE(SUM(l.requestedQty), 0) AS totalRequested,
               COALESCE(SUM(MAX(l.pickedQty, l.acopiadoServidor)), 0) AS totalPicked
        FROM orders o
        LEFT JOIN order_lines l ON l.orderId = o.orderId
        AND l.productId NOT BETWEEN '99990' AND '99999'
        AND l.vigente = 1
        GROUP BY o.orderId, o.customerName, o.customerFiscal, o.status, o.totalLines,
                 o.fincaCarga, o.sectorCarga, o.muelleCarga, o.fechaCarga, o.marcaPedido, o.observaciones,
                 o.createdAt, o.modificado, o.matriculaCamion, o.matriculaRemolque, o.cargado, o.sobrante
        ORDER BY COALESCE(o.fechaCarga, o.createdAt) ASC
        """
    )
    fun observeOrdersWithTotals(): Flow<List<OrderWithTotals>>

    @Query(
        """
        SELECT o.orderId, o.customerName, o.customerFiscal, o.status, o.totalLines,
               o.fincaCarga, o.sectorCarga, o.muelleCarga, o.fechaCarga, o.marcaPedido, o.observaciones,
               o.createdAt, o.modificado, o.matriculaCamion, o.matriculaRemolque, o.cargado, o.sobrante,
               COALESCE(SUM(l.requestedQty), 0) AS totalRequested,
               COALESCE(SUM(MAX(l.pickedQty, l.acopiadoServidor)), 0) AS totalPicked
        FROM orders o
        LEFT JOIN order_lines l ON l.orderId = o.orderId
        AND l.productId NOT BETWEEN '99990' AND '99999'
        AND l.vigente = 1
        WHERE o.orderId LIKE '%' || :query || '%'
           OR o.customerName LIKE '%' || :query || '%'
           OR o.customerFiscal LIKE '%' || :query || '%'
           OR o.marcaPedido LIKE '%' || :query || '%'
           OR o.sectorCarga LIKE '%' || :query || '%'
           OR o.muelleCarga LIKE '%' || :query || '%'
        GROUP BY o.orderId, o.customerName, o.customerFiscal, o.status, o.totalLines,
                 o.fincaCarga, o.sectorCarga, o.muelleCarga, o.fechaCarga, o.marcaPedido, o.observaciones,
                 o.createdAt, o.modificado, o.matriculaCamion, o.matriculaRemolque, o.cargado, o.sobrante
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

    @Query("UPDATE order_lines SET acopiadoOperario = :cantidad WHERE orderLineId = :lineId")
    suspend fun updateLineAcopiadoOperario(lineId: String, cantidad: Int)

    @Query("UPDATE order_lines SET requiresMeasure = :requires WHERE orderLineId = :lineId")
    suspend fun setLineRequiresMeasure(lineId: String, requires: Boolean)

    @Query("DELETE FROM order_lines WHERE orderId = :orderId")
    suspend fun deleteLinesForOrder(orderId: String)

    @Query("DELETE FROM orders WHERE orderId = :orderId")
    suspend fun deleteOrder(orderId: String)

    @Query("DELETE FROM order_lines")
    suspend fun clearLines()

    @Query("DELETE FROM orders")
    suspend fun clearOrders()

    @Query(
        """
        UPDATE orders SET status = CASE
            WHEN (SELECT COALESCE(SUM(MAX(pickedQty, acopiadoServidor)),0) FROM order_lines WHERE orderId = :orderId AND vigente = 1) >=
                 (SELECT COALESCE(SUM(requestedQty),0) FROM order_lines WHERE orderId = :orderId AND vigente = 1)
            THEN 'COMPLETADO' ELSE 'EN_PROCESO' END
        WHERE orderId = :orderId
        """
    )
    suspend fun refreshOrderStatus(orderId: String)

    @Query("UPDATE order_lines SET vigente = 0 WHERE orderLineId IN (:lineIds)")
    suspend fun markLinesNotVigente(lineIds: List<String>)

    @Query("UPDATE orders SET modificado = 1 WHERE orderId = :orderId")
    suspend fun markOrderModificado(orderId: String)

    @Query("UPDATE orders SET modificado = 0 WHERE orderId = :orderId")
    suspend fun clearOrderModificado(orderId: String)

    @Query("SELECT * FROM orders WHERE orderId = :orderId LIMIT 1")
    suspend fun getOrder(orderId: String): OrderEntity?

    @Transaction
    @Query("SELECT * FROM orders ORDER BY COALESCE(fechaCarga, createdAt) ASC")
    fun observeOrdersConLineas(): Flow<List<OrderConLineas>>

    @Query(
        "UPDATE order_lines SET motivoCierre = :motivo, motivoCierreTexto = :texto, " +
            "cierrePendiente = :pendiente WHERE orderLineId = :lineId"
    )
    suspend fun setLineCierre(lineId: String, motivo: String, texto: String, pendiente: Boolean)

    @Query("UPDATE order_lines SET cierrePendiente = 0 WHERE orderLineId IN (:lineIds)")
    suspend fun markCierresSincronizados(lineIds: List<String>)

    @Query(
        "UPDATE order_lines SET motivoCierre = '', motivoCierreTexto = '', cierrePendiente = 0 " +
            "WHERE orderLineId = :lineId"
    )
    suspend fun clearLineCierre(lineId: String)

    @Query(
        "UPDATE order_lines SET operarioEmail = :email, operarioNombre = :nombre " +
            "WHERE orderLineId = :lineId"
    )
    suspend fun setLineOperario(lineId: String, email: String, nombre: String)

    @Query(
        "SELECT * FROM order_lines WHERE cierrePendiente = 1 AND motivoCierre != ''"
    )
    suspend fun getCierresPendientes(): List<OrderLineEntity>

    @Query("SELECT * FROM order_lines WHERE orderLineId = :lineId LIMIT 1")
    suspend fun getLine(lineId: String): OrderLineEntity?

    @Query(
        "UPDATE orders SET matriculaCamion = :matriculaCamion, matriculaRemolque = :matriculaRemolque, " +
            "matriculaRemolqueB = :matriculaRemolqueB, muelleCarga = :muelle WHERE orderId = :orderId"
    )
    suspend fun updateMatriculas(
        orderId: String,
        matriculaCamion: String,
        matriculaRemolque: String,
        matriculaRemolqueB: String,
        muelle: String
    )

    @Query(
        "UPDATE orders SET fotoMatriculaCamion = :camion, fotoMatriculaRemolqueA = :remolqueA, " +
            "fotoMatriculaRemolqueB = :remolqueB WHERE orderId = :orderId"
    )
    suspend fun updateMatriculaFotos(orderId: String, camion: String, remolqueA: String, remolqueB: String)

    @Query("UPDATE orders SET cargado = 1 WHERE orderId = :orderId")
    suspend fun setOrderCargado(orderId: String)

    @Query("UPDATE orders SET cargado = 0 WHERE orderId = :orderId")
    suspend fun setOrderNotCargado(orderId: String)

    @Query("UPDATE orders SET sobrante = :sobrante WHERE orderId = :orderId")
    suspend fun setOrderSobrante(orderId: String, sobrante: Boolean)
}
