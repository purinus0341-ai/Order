package com.sitticun.orderapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: String,
    val name: String,
    val sku: String = "",
    val barcode: String = "",
    val category: String = "ทั่วไป",
    val price: Double = 0.0,
    val cost: Double = 0.0,
    @SerialName("stock_qty") val stockQty: Int = 0,
    @SerialName("min_stock_alert") val minStockAlert: Int = 5,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null
) {
    val isLowStock: Boolean
        get() = stockQty in 1..minStockAlert

    val isOutOfStock: Boolean
        get() = stockQty <= 0
}

@Serializable
data class ProductUpsertPayload(
    val id: String? = null,
    val name: String,
    val sku: String = "",
    val barcode: String = "",
    val category: String = "ทั่วไป",
    val price: Double = 0.0,
    val cost: Double = 0.0,
    @SerialName("stock_qty") val stockQty: Int = 0,
    @SerialName("min_stock_alert") val minStockAlert: Int = 5,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean = true
)
