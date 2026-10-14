package com.sitticun.orderapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrderItemInput(
    val id: String,
    @SerialName("product_id") val productId: Long,
    val qty: Int
)

@Serializable
data class PointUseInput(
    @SerialName("product_id") val productId: Long,
    val points: Int
)

@Serializable
data class FreeItemInput(
    @SerialName("product_id") val productId: Long,
    val qty: Int
)

@Serializable
data class CloudSaleOrder(
    val id: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("held_bill_id") val heldBillId: String? = null,
    val items: List<OrderItemInput> = emptyList(),
    @SerialName("point_uses") val pointUses: List<PointUseInput> = emptyList(),
    @SerialName("free_items") val freeItems: List<FreeItemInput> = emptyList()
)

@Serializable
data class CommitSaleRpcPayload(
    @SerialName("p_order") val order: CloudSaleOrder,
    @SerialName("p_idempotency_key") val idempotencyKey: String,
    @SerialName("p_device_id") val deviceId: String? = null
)

@Serializable
data class CloudSaleResult(
    @SerialName("order_id") val orderId: String,
    val total: Double,
    @SerialName("total_qty") val totalQty: Int,
    @SerialName("point_balances") val pointBalances: Map<String, Long> = emptyMap(),
    @SerialName("idempotent_replay") val idempotentReplay: Boolean = false
)

@Serializable
data class VoidOrderRpcPayload(
    @SerialName("p_order_id") val orderId: String,
    @SerialName("p_idempotency_key") val idempotencyKey: String
)

@Serializable
data class VoidOrderResult(
    @SerialName("order_id") val orderId: String,
    val status: String,
    @SerialName("point_balances") val pointBalances: Map<String, Long> = emptyMap(),
    @SerialName("idempotent_replay") val idempotentReplay: Boolean = false
)

@Serializable
data class OrderSummary(
    val id: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("customer_name_snapshot") val customerName: String = "",
    @SerialName("created_at") val createdAt: String,
    @SerialName("total_qty") val totalQty: Int,
    val total: Double,
    val status: String = "completed", // "completed", "voided"
    @SerialName("created_by") val createdBy: String? = null,
    val items: List<OrderItemRecord> = emptyList(),
    val freeItems: List<OrderFreeItemRecord> = emptyList(),
    val pointUses: List<OrderPointUseRecord> = emptyList()
) {
    val isVoided: Boolean
        get() = status.equals("voided", ignoreCase = true)
}

@Serializable
data class OrderItemRecord(
    val id: String = "",
    @SerialName("order_id") val orderId: String = "",
    @SerialName("product_id") val productId: Long,
    @SerialName("name_snapshot") val nameSnapshot: String,
    @SerialName("volume_snapshot") val volumeSnapshot: String? = null,
    @SerialName("unit_price") val unitPrice: Double,
    val qty: Int,
    val subtotal: Double
)

@Serializable
data class OrderFreeItemRecord(
    val id: String = "",
    @SerialName("order_id") val orderId: String = "",
    @SerialName("product_id") val productId: Long,
    val qty: Int
)

@Serializable
data class OrderPointUseRecord(
    val id: String = "",
    @SerialName("order_id") val orderId: String = "",
    @SerialName("product_id") val productId: Long,
    val points: Int
)

@Serializable
data class HeldBillPayload(
    @SerialName("paid_qty") val paidQty: Map<String, Int> = emptyMap(),
    @SerialName("point_uses") val pointUses: List<PointUseInput> = emptyList(),
    @SerialName("free_items") val freeItems: List<FreeItemInput> = emptyList()
)

@Serializable
data class HeldBill(
    val id: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("customer_name_snapshot") val customerName: String = "",
    val note: String? = null,
    val payload: HeldBillPayload,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("updated_by") val updatedBy: String? = null,
    val version: Long = 1L
)

@Serializable
data class SaveHeldBillBody(
    val id: String,
    @SerialName("customer_id") val customerId: String,
    val note: String? = null,
    val payload: HeldBillPayload
)

@Serializable
data class SaveHeldBillRpcPayload(
    @SerialName("p_bill") val bill: SaveHeldBillBody,
    @SerialName("p_expected_version") val expectedVersion: Long
)

@Serializable
data class DeleteHeldBillRpcPayload(
    @SerialName("p_id") val id: String,
    @SerialName("p_expected_version") val expectedVersion: Long
)

// UI POS cart item representation
data class CartItem(
    val product: Product,
    val paidQty: Int = 0,
    val freeQty: Int = 0,
    val pointsUsed: Int = 0
) {
    val totalQty: Int get() = paidQty + freeQty
    val subtotal: Double get() = paidQty * product.price
}
