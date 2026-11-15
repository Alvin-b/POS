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

/** Lifecycle states for locally logged POS transactions before and after Supabase sync. */
object OfflineTransactionStatus {
    const val QUEUED = "QUEUED"
    const val SYNCING = "SYNCING"
    const val SYNCED = "SYNCED"
    const val RETRY_WAITING = "RETRY_WAITING"
    const val BLOCKED = "BLOCKED"
    const val CANCELLED = "CANCELLED"
}

/**
 * Durable local transaction log tracking every POS mutation through its offline
 * queue, WorkManager sync attempts, and final Supabase confirmation.
 */
@Entity(
    tableName = "offline_transaction_logs",
    indices = [
        Index(value = ["shopId", "createdAt"]),
        Index(value = ["shopId", "status", "createdAt"])
    ]
)
data class OfflineTransactionLogEntity(
    @PrimaryKey val operationId: String,
    val shopId: String,
    val actorUserId: String,
    val operationType: String,
    val summary: String,
    val amountKsh: Long = 0L,
    val status: String = OfflineTransactionStatus.QUEUED,
    val recordedOffline: Boolean = false,
    val attempts: Int = 0,
    val lastError: String? = null,
    val localSaleId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null
)

/** Last server-confirmed account/workspace snapshot; never used to authenticate a request. */
@Entity(tableName = "offline_session")
data class OfflineSessionEntity(
    @PrimaryKey val key: String = "account",
    val snapshotJson: String,
    val activeShopId: String? = null,
    val savedAt: Long = System.currentTimeMillis()
)
