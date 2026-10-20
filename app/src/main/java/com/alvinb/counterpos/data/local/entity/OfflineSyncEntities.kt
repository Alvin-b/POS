package com.alvinb.counterpos.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A durable command awaiting an idempotent server acknowledgement. */
@Entity(
    tableName = "sync_outbox",
    indices = [Index(value = ["shopId", "createdAt"]), Index(value = ["blocked", "createdAt"])]
)
data class OutboxEntity(
    @PrimaryKey val operationId: String,
    val shopId: String,
    val actorUserId: String,
    val operationType: String,
    val payloadJson: String,
    val localSaleId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val lastError: String? = null,
    val blocked: Boolean = false
)

/** Last server-confirmed account/workspace snapshot; never used to authenticate a request. */
@Entity(tableName = "offline_session")
data class OfflineSessionEntity(
    @PrimaryKey val key: String = "account",
    val snapshotJson: String,
    val activeShopId: String? = null,
    val savedAt: Long = System.currentTimeMillis()
)
