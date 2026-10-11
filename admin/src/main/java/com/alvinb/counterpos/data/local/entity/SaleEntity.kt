package com.alvinb.counterpos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val catalogCode: String,
    val productName: String,
    val imageAssetName: String,
    val unitName: String = "Bottle",
    val quantity: Int,
    val unitPriceKsh: Int,
    val totalKsh: Int,
    val createdAt: Long,
    val undone: Boolean = false,
    val photoOverrideUri: String? = null,
    val remoteId: String? = null
)
