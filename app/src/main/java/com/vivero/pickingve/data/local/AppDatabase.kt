package com.vivero.pickingve.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vivero.pickingve.data.local.dao.EncargadoDao
import com.vivero.pickingve.data.local.dao.LitrajeDao
import com.vivero.pickingve.data.local.dao.OrderDao
import com.vivero.pickingve.data.local.dao.PickingDao
import com.vivero.pickingve.data.local.dao.ProductDao
import com.vivero.pickingve.data.local.entities.EncargadoEntity
import com.vivero.pickingve.data.local.entities.LitrajeEntity
import com.vivero.pickingve.data.local.entities.OrderEntity
import com.vivero.pickingve.data.local.entities.OrderLineEntity
import com.vivero.pickingve.data.local.entities.PickingRecordEntity
import com.vivero.pickingve.data.local.entities.ProductEntity

@Database(
    entities = [
        ProductEntity::class,
        OrderEntity::class,
        OrderLineEntity::class,
        PickingRecordEntity::class,
        EncargadoEntity::class,
        LitrajeEntity::class
    ],
    version = 17,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun orderDao(): OrderDao
    abstract fun pickingDao(): PickingDao
    abstract fun encargadoDao(): EncargadoDao
    abstract fun litrajeDao(): LitrajeDao

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
                        MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}