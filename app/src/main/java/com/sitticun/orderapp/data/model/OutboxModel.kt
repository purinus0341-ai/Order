package com.sitticun.orderapp.data.model

import kotlinx.serialization.Serializable

object OutboxStatus {
    const val PENDING = "PENDING"
    const val SYNCING = "SYNCING"
    const val SYNCED = "SYNCED"
    const val FAILED = "FAILED"
}

@Serializable
data class OutboxItem(
    val id: Long = 0,
    val actionType: String,
    val idempotencyKey: String,
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = OutboxStatus.PENDING,
    val retryCount: Int = 0,
    val lastError: String? = null
)
