package com.sitticun.orderapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: Long,
    val name: String,
    val tag: String? = null,
    val volume: String? = null,
    val price: Double = 0.0,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class ProductUpsertPayload(
    val id: Long? = null,
    val name: String,
    val tag: String? = null,
    val volume: String? = null,
    val price: Double = 0.0,
    @SerialName("is_active") val isActive: Boolean = true
)
