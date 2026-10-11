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
    version = 5,
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

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "counterpos.db"
            ).addMigrations(MIGRATION_3_4, MIGRATION_4_5).fallbackToDestructiveMigration(dropAllTables = true).build().also { instance = it }
        }
    }
}
