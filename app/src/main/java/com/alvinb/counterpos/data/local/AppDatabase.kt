package com.alvinb.counterpos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.alvinb.counterpos.data.local.dao.CounterPosDao
import com.alvinb.counterpos.data.local.entity.ProductEntity
import com.alvinb.counterpos.data.local.entity.SaleEntity

@Database(
    entities = [ProductEntity::class, SaleEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun counterPosDao(): CounterPosDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "counterpos.db"
            ).fallbackToDestructiveMigration(dropAllTables = true).build().also { instance = it }
        }
    }
}
