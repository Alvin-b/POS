package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "club_orders")
data class ClubOrderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tabName: String, // e.g. "Table VIP 1", "Alex R. (Card #4102)", "Bar Quick Sale"
    val tableNumber: String = "", // e.g. "VIP-01", "Bar-3", "Patio-04"
    val bartenderName: String, // e.g. "Elena (Main Bar)"
    val status: String = "OPEN", // OPEN, PAID, VOIDED
    val createdAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val subtotal: Double = 0.0,
    val tax: Double = 0.0,
    val tip: Double = 0.0,
    val total: Double = 0.0,
    val paymentMethod: String? = null, // "CARD", "CASH", "VIP_COMP"
    val cardLast4: String? = null,
    val isVipTable: Boolean = false,
    val notes: String = ""
)
