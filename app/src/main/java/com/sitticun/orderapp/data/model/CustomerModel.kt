package com.sitticun.orderapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Customer(
    val id: String,
    val name: String,
    @SerialName("normalized_name") val normalizedName: String = name.lowercase().trim(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
data class CreateCustomerRpcPayload(
    @SerialName("p_id") val id: String,
    @SerialName("p_name") val name: String
)

@Serializable
data class PointBalance(
    @SerialName("customer_id") val customerId: String,
    @SerialName("product_id") val productId: Long,
    val balance: Long = 0L
)

@Serializable
data class PointLedgerEntry(
    val id: String,
    @SerialName("idempotency_key") val idempotencyKey: String = "",
    @SerialName("customer_id") val customerId: String,
    @SerialName("product_id") val productId: Long,
    val delta: Int,
    val reason: String, // 'sale', 'redeem', 'delete_sale_reversal', 'delete_redeem_reversal', 'legacy_import'
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("created_by") val createdBy: String? = null
)
