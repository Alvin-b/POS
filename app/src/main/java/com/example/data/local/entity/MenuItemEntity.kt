package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "menu_items")
data class MenuItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: String, // Cocktails, Shots, Bottle Service, Beers, Seltzers, Non-Alc, VIP Packages
    val price: Double,
    val alcoholType: String, // Vodka, Tequila, Whiskey, Rum, Gin, Champagne, Beer, Non-Alc
    val stockQuantity: Int,
    val isVipExclusive: Boolean = false,
    val popularSpeedRail: Boolean = false,
    val volumeOrSize: String = "", // "Shot (1.5oz)", "Bottle (750ml)", "Pint", "Can (12oz)"
    val description: String = ""
)
