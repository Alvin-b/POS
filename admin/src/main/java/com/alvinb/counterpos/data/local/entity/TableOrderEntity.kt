package com.alvinb.counterpos.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "table_orders",
    foreignKeys = [
        ForeignKey(
            entity = DiningTableEntity::class,
            parentColumns = ["id"],
            childColumns = ["tableId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tableId", "servedAt"]), Index(value = ["saleId"], unique = true)]
)
data class TableOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableId: String,
    val saleId: Long,
    val catalogCode: String,
    val productName: String,
    val unitName: String,
    val quantity: Int,
    val unitPriceKsh: Int,
    val totalKsh: Int,
    val servedAt: Long
)
