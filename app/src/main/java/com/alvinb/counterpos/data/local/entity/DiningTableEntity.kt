package com.alvinb.counterpos.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dining_tables",
    indices = [Index(value = ["name"], unique = true)]
)
data class DiningTableEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long
)
