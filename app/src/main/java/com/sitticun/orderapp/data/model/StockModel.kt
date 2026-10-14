package com.sitticun.orderapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StockSnapshot(
    val id: String,
    @SerialName("product_id") val productId: String,
    @SerialName("product_name") val productName: String,
    @SerialName("previous_qty") val previousQty: Int,
    @SerialName("new_qty") val newQty: Int,
    @SerialName("delta_qty") val deltaQty: Int,
    val reason: String, // "นับสต็อกประจำงวด", "สินค้าชำรุด", "ของแถมโปรโมชั่น", "รับสินค้าเข้า"
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class StockAdjustmentPayload(
    @SerialName("product_id") val productId: String,
    @SerialName("new_stock_qty") val newStockQty: Int,
    @SerialName("delta_qty") val deltaQty: Int,
    val reason: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("device_id") val deviceId: String
)

@Serializable
data class MarkerDot(
    val id: String,
    val x: Float,
    val y: Float,
    val seq: Int,
    val colorHex: String = "#5B8A72"
)

@Serializable
data class SaveImageCountPayload(
    @SerialName("product_id") val productId: String,
    @SerialName("counted_qty") val countedQty: Int,
    @SerialName("is_override") val isOverride: Boolean, // true = แทนที่ค่าเดิม, false = บวกเพิ่ม
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("device_id") val deviceId: String
)

@Serializable
data class LineStockNotifyRequest(
    @SerialName("trigger_source") val triggerSource: String = "manual_app",
    @SerialName("low_stock_only") val lowStockOnly: Boolean = true
)

@Serializable
data class LineStockNotifyResponse(
    val success: Boolean,
    val message: String,
    @SerialName("notified_count") val notifiedCount: Int = 0
)
