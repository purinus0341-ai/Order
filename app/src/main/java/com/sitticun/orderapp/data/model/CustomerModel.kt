package com.sitticun.orderapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Customer(
    val id: String,
    val name: String,
    val phone: String = "",
    val email: String? = null,
    @SerialName("points_balance") val pointsBalance: Int = 0,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CustomerUpsertPayload(
    val id: String? = null,
    val name: String,
    val phone: String = "",
    val email: String? = null,
    @SerialName("points_balance") val pointsBalance: Int = 0,
    val notes: String? = null
)

@Serializable
data class PointsAdjustmentPayload(
    @SerialName("customer_id") val customerId: String,
    @SerialName("points_change") val pointsChange: Int,
    val reason: String, // บังคับระบุเหตุผล
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("device_id") val deviceId: String
)
