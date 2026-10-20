package com.alvinb.counterpos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.alvinb.counterpos.data.local.dao.CounterPosDao
import com.alvinb.counterpos.data.local.entity.CounterStockEntity
import com.alvinb.counterpos.data.local.entity.DiningTableEntity
import com.alvinb.counterpos.data.local.entity.OfflineSessionEntity
import com.alvinb.counterpos.data.local.entity.OutboxEntity
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity
import com.alvinb.counterpos.data.local.entity.ServiceCounterEntity
import com.alvinb.counterpos.data.local.entity.TableOrderEntity

@Database(
    entities = [
        ProductEntity::class,
        SaleEntity::class,
        DiningTableEntity::class,
        TableOrderEntity::class,
        OutboxEntity::class,
        OfflineSessionEntity::class,
        ServiceCounterEntity::class,
        CounterStockEntity::class
    ],
    version = 9,
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
                ensureColumn(db, "sales", "sourceCounterId", "TEXT")
                // Drop only local starter rows created by the former demo seed. User-created
                // tables and their bills are preserved.
                db.execSQL("DELETE FROM table_orders WHERE tableId IN ('table_1','table_2','table_3','table_4','table_5')")
                db.execSQL("DELETE FROM dining_tables WHERE id IN ('table_1','table_2','table_3','table_4','table_5')")

                // Older builds attributed sales to a made-up waiter and/or placeholder counter.
                // Keep the sale itself and clear only false attribution.
                db.execSQL("UPDATE sales SET waiterName = NULL WHERE lower(trim(coalesce(waiterName, ''))) = 'amina'")
                db.execSQL("UPDATE sales SET counterId = NULL, counterName = NULL WHERE counterId = 'counter_1'")
                db.execSQL("UPDATE table_orders SET waiterName = NULL WHERE lower(trim(coalesce(waiterName, ''))) = 'amina'")
                db.execSQL("UPDATE table_orders SET counterId = NULL, counterName = NULL WHERE counterId = 'counter_1'")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Published upstream v8 databases do not yet have these offline tables.
                // IF NOT EXISTS also supports earlier local builds that created them sooner.
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_outbox (operationId TEXT NOT NULL, shopId TEXT NOT NULL, actorUserId TEXT NOT NULL, operationType TEXT NOT NULL, payloadJson TEXT NOT NULL, localSaleId INTEGER, createdAt INTEGER NOT NULL, attempts INTEGER NOT NULL, lastError TEXT, blocked INTEGER NOT NULL, PRIMARY KEY(operationId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_shopId_createdAt ON sync_outbox(shopId, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_blocked_createdAt ON sync_outbox(blocked, createdAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS offline_session (key TEXT NOT NULL, snapshotJson TEXT NOT NULL, activeShopId TEXT, savedAt INTEGER NOT NULL, PRIMARY KEY(key))")
                db.execSQL("CREATE TABLE IF NOT EXISTS service_counters (id TEXT NOT NULL PRIMARY KEY, shopId TEXT NOT NULL, name TEXT NOT NULL, active INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_service_counters_shopId_name ON service_counters(shopId, name)")
                db.execSQL("CREATE TABLE IF NOT EXISTS counter_stock (shopId TEXT NOT NULL, counterId TEXT NOT NULL, catalogCode TEXT NOT NULL, productId TEXT NOT NULL, quantity INTEGER NOT NULL, PRIMARY KEY(counterId, catalogCode))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_counter_stock_shopId_counterId ON counter_stock(shopId, counterId)")

                // Support both published v8 and older local builds whose schema migration
                // history reached the same version using different intermediate steps.
                ensureColumn(db, "sales", "counterId", "TEXT")
                ensureColumn(db, "sales", "counterName", "TEXT")
                ensureColumn(db, "sales", "waiterName", "TEXT")
                ensureColumn(db, "sales", "tableId", "TEXT")
                ensureColumn(db, "sales", "tableName", "TEXT")
                ensureColumn(db, "sales", "sourceCounterId", "TEXT")
                ensureColumn(db, "table_orders", "counterId", "TEXT")
                ensureColumn(db, "table_orders", "counterName", "TEXT")
                ensureColumn(db, "table_orders", "waiterName", "TEXT")
                ensureColumn(db, "dining_tables", "remoteId", "TEXT")
            }
        }

        private fun ensureColumn(db: SupportSQLiteDatabase, table: String, column: String, declaration: String) {
            val cursor = db.query("PRAGMA table_info($table)")
            val exists = try {
                val nameIndex = cursor.getColumnIndex("name")
                var found = false
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == column) found = true
                }
                found
            } finally {
                cursor.close()
            }
            if (!exists) db.execSQL("ALTER TABLE $table ADD COLUMN $column $declaration")
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "counterpos.db"
            ).addMigrations(
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9
            ).fallbackToDestructiveMigration(dropAllTables = true)
                .build().also { instance = it }
        }
    }
}
