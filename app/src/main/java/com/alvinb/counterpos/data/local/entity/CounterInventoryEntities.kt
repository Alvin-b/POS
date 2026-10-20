package com.alvinb.counterpos.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "service_counters", indices = [Index(value = ["shopId", "name"], unique = true)])
data class ServiceCounterEntity(
    @PrimaryKey val id: String,
    val shopId: String,
    val name: String,
    val active: Boolean = true
)

@Entity(
    tableName = "counter_stock",
    primaryKeys = ["counterId", "catalogCode"],
    indices = [Index(value = ["shopId", "counterId"])]
)
data class CounterStockEntity(
    val shopId: String,
    val counterId: String,
    val catalogCode: String,
    val productId: String,
    val quantity: Int
)
