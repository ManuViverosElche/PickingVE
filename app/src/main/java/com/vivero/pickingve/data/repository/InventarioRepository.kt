package com.vivero.pickingve.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.vivero.pickingve.data.local.dao.InventoryDao
import com.vivero.pickingve.data.local.dao.LitrajeDao
import com.vivero.pickingve.data.local.dao.ProductDao
import com.vivero.pickingve.data.local.dao.SectorDao
import com.vivero.pickingve.data.local.entities.InventoryRecordEntity
import com.vivero.pickingve.data.local.entities.InventoryStockEntity
import com.vivero.pickingve.data.remote.ApiInvFinca
import com.vivero.pickingve.data.remote.ApiInvRegistro
import com.vivero.pickingve.data.remote.PickingApiClient
import com.vivero.pickingve.data.local.entities.ProductEntity
import com.vivero.pickingve.util.GpsFix
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID

/**
 * Repositorio del modulo de inventario (D-218/D-219).
 * Offline-first: la UI solo lee Room; la subida a BigQuery es diferida
 * (best-effort tras cada registro y worker periodico), igual que picking.
 */
class InventarioRepository(
    private val context: Context,
    private val inventoryDao: InventoryDao,
    private val productDao: ProductDao,
    private val litrajeDao: LitrajeDao,
    private val sectorDao: SectorDao,
    private val pickingRepository: PickingRepository
) {

    private val uploadMutex = Mutex()

    companion object {
        private const val PREFS = "pickingve_inv"
        private const val KEY_STOCK_VERSION = "inv_stock_version"
        private const val KEY_STOCK_FINCA = "inv_stock_finca"
        private const val UPLOAD_CHUNK_SIZE = 100
        private const val UPLOAD_MAX_ATTEMPTS = 3
    }

    /** Posicion one-shot para el registro actual (D-221); null sin permiso o sin fix. */
    fun posicionActual(): Pair<Double, Double>? = GpsFix.ultimaPosicion(context)

    fun hayPermisoGps(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    // ---- Fincas / sectores ----

    suspend fun fincas(api: PickingApiClient): List<ApiInvFinca> =
        api.inventarioFincas().fincas

    // ---- Stock esperado (cache local) ----

    /**
     * Descarga el stock esperado de la finca si cambio la version en el
     * servidor (huella __TABLES__ de STOCK/SECTORES/ARTICULOS/CODIGOS_EAN).
     * Reemplaza las filas de los sectores de ESTA finca sin tocar las cacheadas
     * de otras fincas.
     */
    suspend fun syncStock(api: PickingApiClient, finca: String, forzar: Boolean = false): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fincaCacheada = prefs.getString(KEY_STOCK_FINCA, null)
        var remota = ""
        if (!forzar && fincaCacheada == finca) {
            try {
                remota = api.inventarioStockVersion().version
                if (remota.isNotBlank() && prefs.getString(KEY_STOCK_VERSION, "") == remota &&
                    inventoryDao.stockCount() > 0
                ) {
                    return false
                }
            } catch (_: Exception) {
                // Sin version (offline) seguimos con la cache local.
                return false
            }
        }
        // La cache de esperado es de UNA finca: al cambiar de finca (o la primera
        // vez) se vacia para no mezclar referencias de la finca anterior.
        if (fincaCacheada == null || fincaCacheada != finca) {
            inventoryDao.clearStock()
        }
        val respuesta = api.inventarioStock(finca)
        if (respuesta.filas.isNotEmpty()) {
            val sectores = respuesta.filas.map { it.sector }.distinct()
            inventoryDao.borrarSectores(sectores)
            inventoryDao.upsertStock(
                respuesta.filas.map {
                    InventoryStockEntity(
                        ref = it.ref,
                        litraje = it.litraje,
                        sector = it.sector,
                        nombre = it.nombre,
                        ean = it.ean,
                        stock = it.stock
                    )
                }
            )
        }
        prefs.edit().putString(KEY_STOCK_FINCA, finca).apply()
        if (remota.isNotBlank()) {
            prefs.edit().putString(KEY_STOCK_VERSION, remota).apply()
        }
        return true
    }

    fun observeStockPorSector(sector: String): Flow<List<InventoryStockEntity>> =
        inventoryDao.observeStockPorSector(sector)

    fun observeStockTodo(): Flow<List<InventoryStockEntity>> = inventoryDao.observeStockTodo()

    suspend fun stockCount(): Int = inventoryDao.stockCount()

    // ---- Conteos ----

    fun observeContadoLocal(finca: String, sector: String) =
        inventoryDao.observeContadoLocal(finca, sector)

    fun observeContadoFinca(finca: String) = inventoryDao.observeContadoFinca(finca)

    fun observePendientesSubir() = inventoryDao.observePendientesSubir()

    suspend fun resolverEan(ean: String): ProductEntity? = productDao.findByEan(ean)

    suspend fun variantesDeReferencia(reference: String): List<ProductEntity> =
        productDao.findEansByReference(reference)

    suspend fun buscarProductos(query: String): List<ProductEntity> = productDao.search(query)

    suspend fun litrajes() = litrajeDao.getAll()

    suspend fun sectores() = sectorDao.getAll()

    /** Crea un registro de conteo y dispara subida best-effort. */
    suspend fun registrar(
        finca: String,
        sector: String,
        producto: ProductEntity?,
        eanEscaneado: String?,
        ocrTexto: String?,
        fueraSector: Boolean,
        reetiquetar: Boolean,
        sinEan: Boolean,
        labelMotivo: String = "",
        modoInventario: String = "ESTANDAR",
        linealSessionId: String = "",
        latitud: Double?,
        longitud: Double?
    ): InventoryRecordEntity {
        val record = InventoryRecordEntity(
            recordId = UUID.randomUUID().toString(),
            finca = finca,
            sector = sector,
            eanEscaneado = eanEscaneado,
            ocrTexto = ocrTexto,
            refArticulo = producto?.reference ?: "",
            litraje = producto?.litraje ?: "",
            sectorEtiqueta = producto?.sector ?: "",
            nombrePlanta = producto?.name ?: "",
            cantidad = 1,
            fueraSector = fueraSector,
            reetiquetar = reetiquetar,
            sinEan = sinEan,
            labelMotivo = labelMotivo,
            modoInventario = modoInventario,
            linealSessionId = linealSessionId,
            latitud = latitud,
            longitud = longitud,
            empleadoEmail = pickingRepository.emailFaena(),
            empleadoNombre = pickingRepository.nombreFaena()
        )
        inventoryDao.insert(record)
        return record
    }

    /**
     * D-240: planta sin identificar. El operario escribe una descripción/hipótesis
     * de qué planta es; se guarda como incidencia (`incidenciaTexto`) con
     * `sin_ean=true` (alimenta la cola de etiquetas a sacar con el motivo) y sin
     * referencia resuelta, para revisión posterior.
     */
    suspend fun registrarIncidencia(
        finca: String,
        sector: String,
        descripcion: String,
        modoInventario: String = "ESTANDAR",
        linealSessionId: String = "",
        latitud: Double?,
        longitud: Double?
    ): InventoryRecordEntity {
        val record = InventoryRecordEntity(
            recordId = UUID.randomUUID().toString(),
            finca = finca,
            sector = sector,
            eanEscaneado = null,
            ocrTexto = descripcion.take(500),
            refArticulo = "",
            litraje = "",
            sectorEtiqueta = "",
            nombrePlanta = descripcion.take(256),
            cantidad = 1,
            fueraSector = false,
            reetiquetar = false,
            sinEan = true,
            labelMotivo = "Falta etiqueta EAN",
            incidenciaTexto = descripcion.take(500),
            modoInventario = modoInventario,
            linealSessionId = linealSessionId,
            latitud = latitud,
            longitud = longitud,
            empleadoEmail = pickingRepository.emailFaena(),
            empleadoNombre = pickingRepository.nombreFaena()
        )
        inventoryDao.insert(record)
        return record
    }

    /** D-243: registra un hueco libre del modo lineal (es_hueco=true, no cuenta como planta). */
    suspend fun registrarHueco(
        finca: String,
        sector: String,
        modoInventario: String,
        linealSessionId: String = "",
        latitud: Double?,
        longitud: Double?
    ): InventoryRecordEntity {
        val record = InventoryRecordEntity(
            recordId = UUID.randomUUID().toString(),
            finca = finca,
            sector = sector,
            eanEscaneado = null,
            ocrTexto = null,
            refArticulo = "",
            litraje = "",
            sectorEtiqueta = "",
            nombrePlanta = "Hueco libre",
            cantidad = 1,
            fueraSector = false,
            reetiquetar = false,
            sinEan = false,
            labelMotivo = "",
            incidenciaTexto = "",
            esHueco = true,
            modoInventario = modoInventario,
            linealSessionId = linealSessionId,
            latitud = latitud,
            longitud = longitud,
            empleadoEmail = pickingRepository.emailFaena(),
            empleadoNombre = pickingRepository.nombreFaena()
        )
        inventoryDao.insert(record)
        return record
    }

    /** D-243: asigna posiciones interpoladas a los eventos del lineal (plantas y huecos). */
    suspend fun asignarPosicionesLineal(
        recordIds: List<String>,
        posA: Pair<Double, Double>,
        posB: Pair<Double, Double>
    ) {
        val n = recordIds.size
        if (n == 0) return
        recordIds.forEachIndexed { i, recordId ->
            val t = (i + 1).toDouble() / (n + 1).toDouble()
            val lat = posA.first + (posB.first - posA.first) * t
            val lon = posA.second + (posB.second - posA.second) * t
            inventoryDao.asignarPosicion(recordId, lat, lon)
        }
    }

    /** D-244: asigna posiciones interpoladas a todos los eventos de una sesión lineal específica. */
    suspend fun asignarPosicionesLinealPorSesion(
        linealSessionId: String,
        posA: Pair<Double, Double>,
        posB: Pair<Double, Double>
    ) {
        val records = inventoryDao.obtenerPorSesionLineal(linealSessionId)
        val n = records.size
        if (n == 0) return
        records.forEachIndexed { i, record ->
            val t = (i + 1).toDouble() / (n + 1).toDouble()
            val lat = posA.first + (posB.first - posA.first) * t
            val lon = posA.second + (posB.second - posA.second) * t
            inventoryDao.asignarPosicion(record.recordId, lat, lon)
        }
    }

    /**
     * Resta N plantas (1 por pulsacion) del ultimo registro vivo de esa triada.
     * Si llega a 0 se borra; si ya estaba subido se compensa en BigQuery al subir.
     */
    suspend fun restar(finca: String, sector: String, ref: String, litraje: String): Boolean {
        val ultimo = inventoryDao.ultimosVivos(finca, sector, ref, litraje).firstOrNull()
            ?: return false
        if (ultimo.cantidad <= 1) {
            inventoryDao.borrarLogico(ultimo.recordId, ultimo.syncedBigQuery)
        } else {
            inventoryDao.decrementar(ultimo.recordId)
        }
        return true
    }

    /**
     * D-229: borra todos los pistoleos de inventario para empezar de cero en las
     * pruebas. Borra en el servidor (endpoint /inventario/borrar, best-effort) y
     * vacía la tabla local completa (registros y compensaciones pendientes).
     */
    suspend fun borrarRegistros(api: PickingApiClient) {
        try {
            api.inventarioBorrar()
        } catch (_: Exception) {
            // Best-effort: si el borrado del servidor falla, al menos se limpia local.
        }
        inventoryDao.borrarTodosRegistros()
    }

    suspend fun uploadPendientes(api: PickingApiClient): Int = uploadMutex.withLock {
        val pendientes = inventoryDao.pendientes()
        if (pendientes.isEmpty()) return 0

        val aCompensar = pendientes.filter { it.deleted && it.wasUploaded }
        val aEnviar = mutableListOf<ApiInvRegistro>()
        val purgar = mutableListOf<String>()

        pendientes.forEach { r ->
            when {
                r.deleted && !r.wasUploaded -> purgar.add(r.recordId)
                !r.deleted -> aEnviar.add(
                    ApiInvRegistro(
                        recordId = r.recordId,
                        finca = r.finca,
                        sector = r.sector,
                        eanEscaneado = r.eanEscaneado.orEmpty(),
                        ocrTexto = r.ocrTexto?.take(500).orEmpty(),
                        refArticulo = r.refArticulo,
                        litraje = r.litraje,
                        sectorEtiqueta = r.sectorEtiqueta,
                        nombrePlanta = r.nombrePlanta.take(256),
                        cantidad = r.cantidad,
                        fueraSector = r.fueraSector,
                        reetiquetar = r.reetiquetar,
                        sinEan = r.sinEan,
                        labelMotivo = r.labelMotivo,
                        incidenciaTexto = r.incidenciaTexto,
                        esHueco = r.esHueco,
                        modoInventario = r.modoInventario,
                        linealSessionId = r.linealSessionId,
                        latitud = r.latitud,
                        longitud = r.longitud,
                        fechaHora = Instant.ofEpochMilli(r.timestamp).toString(),
                        empleadoEmail = r.empleadoEmail,
                        empleadoNombre = r.empleadoNombre
                    )
                )
            }
        }
        inventoryDao.borrarFisico(purgar)

        var compensados = 0
        if (aCompensar.isNotEmpty()) {
            var intento = 0
            while (true) {
                try {
                    api.inventarioCompensar(aCompensar.map { it.recordId })
                    break
                } catch (e: ClientRequestException) {
                    if (e.response.status.value == 404) break
                    intento++
                    if (intento >= UPLOAD_MAX_ATTEMPTS) throw e
                    kotlinx.coroutines.delay(1000L * (1L shl (intento - 1)))
                } catch (e: Exception) {
                    intento++
                    if (intento >= UPLOAD_MAX_ATTEMPTS) throw e
                    kotlinx.coroutines.delay(1000L * (1L shl (intento - 1)))
                }
            }
            inventoryDao.borrarFisico(aCompensar.map { it.recordId })
            compensados += aCompensar.size
        }

        var subidos = 0
        aEnviar.chunked(UPLOAD_CHUNK_SIZE).forEach { chunk ->
            var intento = 0
            while (intento < UPLOAD_MAX_ATTEMPTS) {
                try {
                    val resp = api.inventarioUpload(chunk)
                    val ids = resp.acceptedIds.ifEmpty { chunk.map { it.recordId } }
                    inventoryDao.marcarSincronizados(ids)
                    subidos += ids.size
                    break
                } catch (e: Exception) {
                    intento++
                    if (intento >= UPLOAD_MAX_ATTEMPTS) throw e
                    kotlinx.coroutines.delay(1000L * (1L shl (intento - 1)))
                }
            }
        }
        return subidos + compensados
    }
}
