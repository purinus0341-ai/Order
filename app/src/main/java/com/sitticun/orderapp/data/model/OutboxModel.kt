package com.sitticun.orderapp.data.model

import kotlinx.serialization.Serializable

object OutboxActionType {
    const val COMMIT_SALE = "COMMIT_SALE"
    const val VOID_SALE = "VOID_SALE"
    const val STOCK_ADJUSTMENT = "STOCK_ADJUSTMENT"
    const val CUSTOMER_POINTS = "CUSTOMER_POINTS"
    const val PRODUCT_UPSERT = "PRODUCT_UPSERT"
    const val CUSTOMER_UPSERT = "CUSTOMER_UPSERT"
}

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
