package com.sitticun.orderapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CartItem(
    val product: Product,
    val paidQty: Int = 1,
    val freeQty: Int = 0
) {
    val totalQty: Int
        get() = paidQty + freeQty

    val subtotal: Double
        get() = paidQty * product.price
}

@Serializable
data class SaleItemPayload(
    @SerialName("product_id") val productId: String,
    @SerialName("product_name") val productName: String,
    @SerialName("paid_qty") val paidQty: Int,
    @SerialName("free_qty") val freeQty: Int,
    @SerialName("total_qty") val totalQty: Int,
    @SerialName("unit_price") val unitPrice: Double,
    @SerialName("subtotal") val subtotal: Double
)

@Serializable
data class CommitSaleRequest(
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("items") val items: List<SaleItemPayload>,
    @SerialName("subtotal") val subtotal: Double,
    @SerialName("redeemed_points") val redeemedPoints: Int = 0,
    @SerialName("discount_amount") val discountAmount: Double = 0.0,
    @SerialName("earned_points") val earnedPoints: Int = 0,
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("payment_method") val paymentMethod: String = "CASH", // "CASH", "QR", "TRANSFER"
    @SerialName("note") val note: String? = null
)

@Serializable
data class CommitSaleResponse(
    val success: Boolean = true,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("order_number") val orderNumber: String? = null,
    val message: String = "",
    @SerialName("updated_customer_points") val updatedCustomerPoints: Int? = null
)

@Serializable
data class OrderRecord(
    val id: String,
    @SerialName("order_number") val orderNumber: String,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("customer_name") val customerName: String? = null,
    val subtotal: Double,
    @SerialName("redeemed_points") val redeemedPoints: Int = 0,
    @SerialName("discount_amount") val discountAmount: Double = 0.0,
    @SerialName("earned_points") val earnedPoints: Int = 0,
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("payment_method") val paymentMethod: String = "CASH",
    val status: String = "COMPLETED", // "COMPLETED", "VOIDED"
    @SerialName("void_reason") val voidReason: String? = null,
    @SerialName("voided_at") val voidedAt: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("idempotency_key") val idempotencyKey: String? = null,
    @SerialName("created_at") val createdAt: String,
    val items: List<OrderItemRecord> = emptyList()
)

@Serializable
data class OrderItemRecord(
    val id: String = "",
    @SerialName("order_id") val orderId: String = "",
    @SerialName("product_id") val productId: String,
    @SerialName("product_name") val productName: String,
    @SerialName("paid_qty") val paidQty: Int,
    @SerialName("free_qty") val freeQty: Int,
    @SerialName("total_qty") val totalQty: Int,
    @SerialName("unit_price") val unitPrice: Double,
    val subtotal: Double
)

@Serializable
data class VoidSaleRequest(
    @SerialName("order_id") val orderId: String,
    @SerialName("void_reason") val voidReason: String, // บังคับระบุเหตุผลในการยกเลิกบิล
    @SerialName("device_id") val deviceId: String,
    @SerialName("idempotency_key") val idempotencyKey: String
)

@Serializable
data class VoidSaleResponse(
    val success: Boolean = true,
    val message: String = "",
    @SerialName("restored_stock_items") val restoredStockItems: Int = 0,
    @SerialName("restored_customer_points") val restoredCustomerPoints: Int = 0
)

@Serializable
data class HeldBill(
    val id: String,
    val title: String,
    val customer: Customer? = null,
    val items: List<CartItem>,
    val redeemedPoints: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    val totalAmount: Double
        get() {
            val sub = items.sumOf { it.subtotal }
            return (sub - redeemedPoints).coerceAtLeast(0.0)
        }
    val totalItemsCount: Int
        get() = items.sumOf { it.totalQty }
}
