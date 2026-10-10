package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "order_items",
    foreignKeys = [
        ForeignKey(
            entity = ClubOrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["orderId"])]
)
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderId: Long,
    val menuItemId: Long,
    val itemName: String,
    val itemCategory: String,
    val unitPrice: Double,
    val quantity: Int = 1,
    val modifiers: String = "", // e.g. "Double (+4.00), Rocks, Lime"
    val addedAt: Long = System.currentTimeMillis()
)
