package com.vivero.pickingve.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vivero.pickingve.data.local.dao.ChatEstadoDao
import com.vivero.pickingve.data.local.dao.EncargadoDao
import com.vivero.pickingve.data.local.dao.LitrajeDao
import com.vivero.pickingve.data.local.dao.OperarioDao
import com.vivero.pickingve.data.local.dao.OrderDao
import com.vivero.pickingve.data.local.dao.PickingDao
import com.vivero.pickingve.data.local.dao.ProductDao
import com.vivero.pickingve.data.local.dao.SectorDao
import com.vivero.pickingve.data.local.entities.ChatEstadoEntity
import com.vivero.pickingve.data.local.entities.EncargadoEntity
import com.vivero.pickingve.data.local.entities.LitrajeEntity
import com.vivero.pickingve.data.local.entities.OperarioEntity
import com.vivero.pickingve.data.local.entities.OrderEntity
import com.vivero.pickingve.data.local.entities.OrderLineEntity
import com.vivero.pickingve.data.local.entities.PickingRecordEntity
import com.vivero.pickingve.data.local.entities.ProductEntity
import com.vivero.pickingve.data.local.entities.SectorEntity
import com.vivero.pickingve.data.local.entities.InventoryRecordEntity
import com.vivero.pickingve.data.local.entities.InventoryStockEntity
import com.vivero.pickingve.data.local.dao.InventoryDao

@Database(
    entities = [
        ProductEntity::class,
        OrderEntity::class,
        OrderLineEntity::class,
        PickingRecordEntity::class,
        EncargadoEntity::class,
        LitrajeEntity::class,
        SectorEntity::class,
        ChatEstadoEntity::class,
        OperarioEntity::class,
        InventoryStockEntity::class,
        InventoryRecordEntity::class
    ],
    version = 29,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun orderDao(): OrderDao
    abstract fun pickingDao(): PickingDao
    abstract fun encargadoDao(): EncargadoDao
    abstract fun litrajeDao(): LitrajeDao
    abstract fun sectorDao(): SectorDao
    abstract fun chatEstadoDao(): ChatEstadoDao
    abstract fun operarioDao(): OperarioDao
    abstract fun inventoryDao(): InventoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE order_lines ADD COLUMN requiresMeasure INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE picking_records ADD COLUMN needsLabel INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE orders ADD COLUMN customerFiscal TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE orders ADD COLUMN fincaCarga TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE orders ADD COLUMN sectorCarga TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE orders ADD COLUMN fechaCarga INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE orders ADD COLUMN marcaPedido TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE order_lines ADD COLUMN litraje TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE order_lines ADD COLUMN litrajeDesc TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE order_lines ADD COLUMN sector TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE order_lines ADD COLUMN sectorDesc TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE order_lines ADD COLUMN marca TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS encargados (
                        id TEXT NOT NULL,
                        nombre TEXT NOT NULL,
                        usuario TEXT NOT NULL,
                        passwordHash TEXT NOT NULL,
                        rol TEXT NOT NULL,
                        fincasCarga TEXT NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE orders ADD COLUMN observaciones TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE order_lines ADD COLUMN prioridad TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE order_lines ADD COLUMN ubicacion TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE order_lines ADD COLUMN accion TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE order_lines ADD COLUMN observaciones TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE order_lines ADD COLUMN posicion INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE order_lines ADD COLUMN empleado TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE encargados ADD COLUMN modo TEXT NOT NULL DEFAULT 'PICKING'"
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE products ADD COLUMN litraje TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE products ADD COLUMN sector TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE encargados ADD COLUMN email TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE encargados ADD COLUMN activo INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE picking_records ADD COLUMN empleadoEmail TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE picking_records ADD COLUMN empleadoNombre TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE order_lines ADD COLUMN vigente INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE orders ADD COLUMN modificado INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE picking_records ADD COLUMN labelSent INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE picking_records ADD COLUMN labelSentAt INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE orders ADD COLUMN matriculaCamion TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE orders ADD COLUMN matriculaRemolque TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE orders ADD COLUMN cargado INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE orders ADD COLUMN sobrante INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE picking_records ADD COLUMN labelReason TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE picking_records ADD COLUMN labelFormat TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS litrajes (
                        id TEXT NOT NULL PRIMARY KEY,
                        descripcion TEXT NOT NULL
                    )
                    """
                )
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE order_lines ADD COLUMN marcado INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE orders ADD COLUMN pickingActual INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE order_lines ADD COLUMN acopiadoServidor INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE picking_records ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN matriculaRemolqueB TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE orders ADD COLUMN muelleCarga TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE orders ADD COLUMN fotoMatriculaCamion TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE orders ADD COLUMN fotoMatriculaRemolqueA TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE orders ADD COLUMN fotoMatriculaRemolqueB TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_estado (
                        hilo_id TEXT NOT NULL PRIMARY KEY,
                        ultimo_creado_en TEXT NOT NULL,
                        sin_leer INTEGER NOT NULL
                    )
                    """
                )
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sectores (
                        id TEXT NOT NULL PRIMARY KEY,
                        descripcion TEXT NOT NULL
                    )
                    """
                )
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE picking_records ADD COLUMN wasUploaded INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE order_lines ADD COLUMN fincaAcopio TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE order_lines ADD COLUMN sectorAcopio TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE order_lines ADD COLUMN operarioEmail TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE order_lines ADD COLUMN operarioNombre TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE order_lines ADD COLUMN motivoCierre TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE order_lines ADD COLUMN motivoCierreTexto TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "ALTER TABLE order_lines ADD COLUMN cierrePendiente INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS operarios (
                        id TEXT NOT NULL PRIMARY KEY,
                        nombre TEXT NOT NULL,
                        apellidos TEXT NOT NULL DEFAULT '',
                        email TEXT NOT NULL,
                        passwordHash TEXT NOT NULL,
                        maquinaria TEXT NOT NULL DEFAULT '',
                        fincasCarga TEXT NOT NULL DEFAULT '',
                        activo INTEGER NOT NULL DEFAULT 1,
                        debeCambiarPassword INTEGER NOT NULL DEFAULT 1
                    )
                    """
                )
            }
        }

        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // D-196: añade columna modo (ACOPIO/INVENTARIO/AMBAS). La entidad
                // OperarioEntity ya la exige; los DB en v22 sin ella crasheaban
                // en verificación de Room incluso tras borrar datos por el
                // fallback sin migracion. Migración idempotente: si ya existe
                // (instalación fresca v22 con modo) no hace nada.
                var hasModo = false
                db.query("PRAGMA table_info(operarios)").use { c ->
                    val nameIdx = c.getColumnIndex("name")
                    while (c.moveToNext()) {
                        if (c.getString(nameIdx) == "modo") { hasModo = true; break }
                    }
                }
                if (!hasModo) {
                    db.execSQL("ALTER TABLE operarios ADD COLUMN modo TEXT NOT NULL DEFAULT 'ACOPIO'")
                }
            }
        }

        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS inventario_stock (
                        ref TEXT NOT NULL,
                        litraje TEXT NOT NULL,
                        sector TEXT NOT NULL,
                        nombre TEXT NOT NULL,
                        ean TEXT NOT NULL,
                        stock REAL NOT NULL,
                        PRIMARY KEY(ref, litraje, sector)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS inventario_records (
                        recordId TEXT NOT NULL PRIMARY KEY,
                        finca TEXT NOT NULL,
                        sector TEXT NOT NULL,
                        eanEscaneado TEXT,
                        ocrTexto TEXT,
                        refArticulo TEXT NOT NULL,
                        litraje TEXT NOT NULL,
                        sectorEtiqueta TEXT NOT NULL,
                        nombrePlanta TEXT NOT NULL,
                        cantidad INTEGER NOT NULL,
                        fueraSector INTEGER NOT NULL,
                        reetiquetar INTEGER NOT NULL,
                        sinEan INTEGER NOT NULL,
                        latitud REAL,
                        longitud REAL,
                        timestamp INTEGER NOT NULL,
                        syncedBigQuery INTEGER NOT NULL,
                        deleted INTEGER NOT NULL,
                        wasUploaded INTEGER NOT NULL,
                        empleadoEmail TEXT NOT NULL,
                        empleadoNombre TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_inventario_records_finca_sector " +
                        "ON inventario_records (finca, sector)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_inventario_records_eanEscaneado " +
                        "ON inventario_records (eanEscaneado)"
                )
            }
        }

        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE order_lines ADD COLUMN fincaArticulo TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // D-240/D-214/D-187: campos de incidencia, motivo de etiqueta,
                // huecos y modo de pistoleo en los registros de inventario.
                db.execSQL(
                    "ALTER TABLE inventario_records ADD COLUMN labelMotivo TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE inventario_records ADD COLUMN incidenciaTexto TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE inventario_records ADD COLUMN esHueco INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE inventario_records ADD COLUMN modoInventario TEXT NOT NULL DEFAULT 'ESTANDAR'"
                )
            }
        }

        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // D-186: ID de sesión lineal (A -> B) para agrupar líneas independientes.
                db.execSQL(
                    "ALTER TABLE inventario_records ADD COLUMN linealSessionId TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // D-232: Separación de contadores - acopio de operario vs verificación de encargado
                db.execSQL(
                    "ALTER TABLE order_lines ADD COLUMN acopiadoOperario INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // D-274: flag tieneCamion en orders (sincronizado desde backend)
                db.execSQL(
                    "ALTER TABLE orders ADD COLUMN tieneCamion INTEGER NOT NULL DEFAULT 0"
                )
                // D-276: cantidad solicitada anterior en order_lines (cambio del sistema)
                db.execSQL(
                    "ALTER TABLE order_lines ADD COLUMN requestedQtyAnterior INTEGER"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pickingve.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                        MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                        MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                        MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
                        MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25,
                        MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29
                    )
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}