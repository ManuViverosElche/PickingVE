package com.vivero.pickingve.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vivero.pickingve.data.local.dao.EncargadoDao
import com.vivero.pickingve.data.local.dao.OrderDao
import com.vivero.pickingve.data.local.dao.PickingDao
import com.vivero.pickingve.data.local.dao.ProductDao
import com.vivero.pickingve.data.local.entities.EncargadoEntity
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
        EncargadoEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun orderDao(): OrderDao
    abstract fun pickingDao(): PickingDao
    abstract fun encargadoDao(): EncargadoDao

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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pickingve.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6, MIGRATION_6_7
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}