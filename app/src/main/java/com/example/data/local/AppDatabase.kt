package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.ClubPosDao
import com.example.data.local.entity.ClubOrderEntity
import com.example.data.local.entity.MenuItemEntity
import com.example.data.local.entity.OrderItemEntity
import com.example.data.local.entity.ShiftRecordEntity

@Database(
    entities = [
        MenuItemEntity::class,
        ClubOrderEntity::class,
        OrderItemEntity::class,
        ShiftRecordEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun clubPosDao(): ClubPosDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pulse_club_pos.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
