package com.alvinb.counterpos.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "butchery_config")
data class ButcheryConfigEntity(
    @PrimaryKey val shopId: String,
    val enabled: Boolean = false,
    val butcheryName: String = "",
    val beefOnlyMode: Boolean = false,
    val defaultBeefPricePerKgKsh: Int = 680,
    val paymentMethodsJson: String = "[\"M-Pesa\",\"Cash Payment\"]",
    val enabledCategoriesJson: String = "[\"Beef\",\"Goat/Mbuzi\",\"Chicken\",\"Liver/Matumbo\",\"Pork\"]",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "butchery_cuts",
    indices = [
        Index(value = ["shopId", "sortOrder"]),
        Index(value = ["shopId", "active"])
    ]
)
data class ButcheryCutEntity(
    @PrimaryKey val id: String,
    val shopId: String,
    val name: String,
    val category: String,
    val pricePerKgKsh: Int,
    val stockGrams: Int = 0,
    val iconKey: String = "beef_steak",
    val active: Boolean = true,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "butchery_sales",
    indices = [
        Index(value = ["shopId", "createdAt"]),
        Index(value = ["shopId", "undone", "createdAt"])
    ]
)
data class ButcherySaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: String,
    val cutId: String,
    val cutName: String,
    val category: String,
    val pricePerKgKsh: Int,
    val weightGrams: Int,
    val totalKsh: Int,
    val paymentMethod: String, // "mpesa" or "cash" (direct mode choice, no STK push)
    val entryMode: String,     // "weight" or "amount"
    val stockDeductedGrams: Int = 0,
    val attendantName: String? = null,
    val createdAt: Long,
    val undone: Boolean = false
)
