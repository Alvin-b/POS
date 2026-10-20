package com.alvinb.counterpos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.alvinb.counterpos.data.local.dao.CounterPosDao
import com.alvinb.counterpos.data.local.entity.DiningTableEntity
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.local.entity.TableOrderEntity
@Database(
    entities = [ProductEntity::class, SaleEntity::class, DiningTableEntity::class, TableOrderEntity::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun counterPosDao(): CounterPosDao

    companion object {
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS dining_tables (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_dining_tables_name ON dining_tables(name)")
                db.execSQL("CREATE TABLE IF NOT EXISTS table_orders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tableId TEXT NOT NULL, saleId INTEGER NOT NULL, catalogCode TEXT NOT NULL, productName TEXT NOT NULL, unitName TEXT NOT NULL, quantity INTEGER NOT NULL, unitPriceKsh INTEGER NOT NULL, totalKsh INTEGER NOT NULL, servedAt INTEGER NOT NULL, FOREIGN KEY(tableId) REFERENCES dining_tables(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_table_orders_tableId_servedAt ON table_orders(tableId, servedAt)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_table_orders_saleId ON table_orders(saleId)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN remoteId TEXT")
                db.execSQL("ALTER TABLE products ADD COLUMN averageCostKsh INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sales ADD COLUMN remoteId TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sales ADD COLUMN counterId TEXT")
                db.execSQL("ALTER TABLE sales ADD COLUMN counterName TEXT")
                db.execSQL("ALTER TABLE sales ADD COLUMN waiterName TEXT")
                db.execSQL("ALTER TABLE sales ADD COLUMN tableId TEXT")
                db.execSQL("ALTER TABLE sales ADD COLUMN tableName TEXT")
                db.execSQL("ALTER TABLE table_orders ADD COLUMN counterId TEXT")
                db.execSQL("ALTER TABLE table_orders ADD COLUMN counterName TEXT")
                db.execSQL("ALTER TABLE table_orders ADD COLUMN waiterName TEXT")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // The bundled catalog previously shipped with fabricated opening stock.
                // Only clear those bundled rows when they have no remote business ID;
                // genuine server-owned inventory is retained and re-synced from Supabase.
                db.execSQL(
                    "UPDATE products SET stockQuantity = 0, inMyStock = 0, sellingPriceKsh = 0 " +
                        "WHERE remoteId IS NULL AND catalogCode IN (" +
                        "'6001000000011','6001000000028','6001000000035','6001000000042','6001000000059'," +
                        "'6001000000066','6001000000073','6001000000080','6001000000097','6001000000103'," +
                        "'6001000000110','6001000000127','6001000000134','6001000000141','6001000000158'," +
                        "'6001000000165','6001000000172','6001000000189','6001000000196')"
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop only the local starter rows created by the former demo seed.
                // User-created tables and their bills are preserved.
                db.execSQL("DELETE FROM table_orders WHERE tableId IN ('table_1','table_2','table_3','table_4','table_5')")
                db.execSQL("DELETE FROM dining_tables WHERE id IN ('table_1','table_2','table_3','table_4','table_5')")

                // Older app builds attributed sales to a made-up waiter and/or local
                // placeholder counter. Keep the sale itself; clear only false attribution.
                db.execSQL("UPDATE sales SET waiterName = NULL WHERE lower(trim(coalesce(waiterName, ''))) = 'amina'")
                db.execSQL("UPDATE sales SET counterId = NULL, counterName = NULL WHERE counterId = 'counter_1'")
                db.execSQL("UPDATE table_orders SET waiterName = NULL WHERE lower(trim(coalesce(waiterName, ''))) = 'amina'")
                db.execSQL("UPDATE table_orders SET counterId = NULL, counterName = NULL WHERE counterId = 'counter_1'")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "counterpos.db"
            ).addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).fallbackToDestructiveMigration(dropAllTables = true).build().also { instance = it }
        }
    }
}
