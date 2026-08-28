package com.vivero.pickingve.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vivero.pickingve.data.local.entities.InvContadoLocal
import com.vivero.pickingve.data.local.entities.InventoryRecordEntity
import com.vivero.pickingve.data.local.entities.InventoryStockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    // ---- Stock esperado (cache de /api/inventario/stock) ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStock(rows: List<InventoryStockEntity>)

    @Query("DELETE FROM inventario_stock")
    suspend fun clearStock()

    @Query("DELETE FROM inventario_stock WHERE sector IN (:sectores)")
    suspend fun borrarSectores(sectores: List<String>)

    @Query(
        "SELECT * FROM inventario_stock WHERE sector = :sector " +
            "ORDER BY ref, litraje"
    )
    fun observeStockPorSector(sector: String): Flow<List<InventoryStockEntity>>

    @Query("SELECT * FROM inventario_stock ORDER BY sector, ref, litraje")
    fun observeStockTodo(): Flow<List<InventoryStockEntity>>

    @Query(
        "SELECT * FROM inventario_stock WHERE sector = :sector AND (ean = :ean OR :ean = '') " +
            "ORDER BY ref LIMIT 1"
    )
    suspend fun stockPorEan(sector: String, ean: String): InventoryStockEntity?

    @Query(
        "SELECT COUNT(*) FROM inventario_stock"
    )
    suspend fun stockCount(): Int

    // ---- Registros de pistoleo ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: InventoryRecordEntity)

    @Query("SELECT * FROM inventario_records WHERE syncedBigQuery = 0")
    suspend fun pendientes(): List<InventoryRecordEntity>

    @Query("UPDATE inventario_records SET syncedBigQuery = 1 WHERE recordId IN (:ids)")
    suspend fun marcarSincronizados(ids: List<String>)

    @Query("DELETE FROM inventario_records WHERE recordId IN (:ids)")
    suspend fun borrarFisico(ids: List<String>)

    @Query(
        "UPDATE inventario_records SET deleted = 1, wasUploaded = :wasUploaded " +
            "WHERE recordId = :recordId"
    )
    suspend fun borrarLogico(recordId: String, wasUploaded: Boolean)

    @Query("SELECT wasUploaded FROM inventario_records WHERE recordId = :recordId")
    suspend fun fueSubido(recordId: String): Boolean?

    @Query(
        "SELECT refArticulo AS ref, litraje AS litraje, SUM(cantidad) AS contado, " +
            "COUNT(*) AS eventos, SUM(CASE WHEN fueraSector THEN 1 ELSE 0 END) AS fuera " +
            "FROM inventario_records " +
            "WHERE finca = :finca AND sector = :sector AND deleted = 0 AND esHueco = 0 " +
            "GROUP BY refArticulo, litraje"
    )
    fun observeContadoLocal(finca: String, sector: String): Flow<List<InvContadoLocal>>

    /** Conteo local de toda la finca (sin filtrar por sector) para fincas sin sectores. */
    @Query(
        "SELECT refArticulo AS ref, litraje AS litraje, SUM(cantidad) AS contado, " +
            "COUNT(*) AS eventos, SUM(CASE WHEN fueraSector THEN 1 ELSE 0 END) AS fuera " +
            "FROM inventario_records " +
            "WHERE finca = :finca AND deleted = 0 AND esHueco = 0 " +
            "GROUP BY refArticulo, litraje"
    )
    fun observeContadoFinca(finca: String): Flow<List<InvContadoLocal>>

    @Query(
        "SELECT COUNT(*) FROM inventario_records WHERE deleted = 0 AND syncedBigQuery = 0"
    )
    fun observePendientesSubir(): Flow<Int>

    @Query(
        "SELECT * FROM inventario_records WHERE finca = :finca AND deleted = 0 " +
            "ORDER BY timestamp DESC LIMIT 200"
    )
    fun observeUltimos(finca: String): Flow<List<InventoryRecordEntity>>

    @Query(
        "SELECT * FROM inventario_records " +
            "WHERE finca = :finca AND sector = :sector AND refArticulo = :ref " +
            "AND litraje = :litraje AND deleted = 0 " +
            "ORDER BY timestamp DESC"
    )
    suspend fun ultimosVivos(
        finca: String,
        sector: String,
        ref: String,
        litraje: String
    ): List<InventoryRecordEntity>

    @Query(
        "UPDATE inventario_records SET cantidad = cantidad - 1, syncedBigQuery = 0, " +
            "wasUploaded = 1 WHERE recordId = :recordId AND cantidad > 0"
    )
    suspend fun decrementar(recordId: String)

    /** D-243: posicion GPS interpolada de un evento del lineal (planta o hueco). */
    @Query(
        "UPDATE inventario_records SET latitud = :latitud, longitud = :longitud, " +
            "syncedBigQuery = 0 WHERE recordId = :recordId"
    )
    suspend fun asignarPosicion(recordId: String, latitud: Double, longitud: Double)

    /** D-244: obtiene todos los registros de una sesión lineal específica ordenados por tiempo. */
    @Query(
        "SELECT * FROM inventario_records WHERE linealSessionId = :sessionId AND deleted = 0 " +
            "ORDER BY timestamp ASC"
    )
    suspend fun obtenerPorSesionLineal(sessionId: String): List<InventoryRecordEntity>

    // ---- Reset (D-229): empezar de cero en las pruebas ----

    @Query("SELECT recordId FROM inventario_records WHERE syncedBigQuery = 1 AND deleted = 0")
    suspend fun idsSincronizados(): List<String>

    @Query("DELETE FROM inventario_records")
    suspend fun borrarTodosRegistros()
}
