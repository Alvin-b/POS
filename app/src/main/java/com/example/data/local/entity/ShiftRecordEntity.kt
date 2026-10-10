package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shift_records")
data class ShiftRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bartenderName: String,
    val stationName: String, // "Main Bar Station 1", "VIP Booth Lounge", "Rooftop Terrace"
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val totalSales: Double = 0.0,
    val totalTips: Double = 0.0,
    val cashCollected: Double = 0.0,
    val cardCollected: Double = 0.0,
    val compAmount: Double = 0.0,
    val ordersCount: Int = 0,
    val drinksPouredCount: Int = 0,
    val isShiftOpen: Boolean = true
)
