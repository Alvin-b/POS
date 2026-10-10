package com.alvinb.counterpos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val catalogCode: String,
    val verifiedBarcode: String?,
    val name: String,
    val brand: String,
    val size: String,
    val category: String,
    val suggestedPriceKsh: Int,
    val sellingPriceKsh: Int,
    val stockQuantity: Int,
    val inMyStock: Boolean,
    val lowStockThreshold: Int,
    val imageAssetName: String,
    val photoOverrideUri: String? = null,
    val productSourceUrl: String? = null,
    val imageSourceUrl: String? = null,
    val verificationNote: String = ""
)
