package com.sitticun.orderapp.data.repository

import com.sitticun.orderapp.data.api.SupabaseClient
import com.sitticun.orderapp.data.local.OrderDatabaseHelper
import com.sitticun.orderapp.data.local.PreferenceManager
import com.sitticun.orderapp.data.model.*
import com.sitticun.orderapp.util.DeviceIdProvider
import com.sitticun.orderapp.util.Formatters
import com.sitticun.orderapp.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class OrderRepository(
    private val supabaseClient: SupabaseClient,
    private val dbHelper: OrderDatabaseHelper,
    private val preferenceManager: PreferenceManager,
    private val productRepository: ProductRepository,
    private val customerRepository: CustomerRepository,
    private val networkMonitor: NetworkMonitor,
    private val deviceIdProvider: DeviceIdProvider
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _orders = MutableStateFlow<List<OrderRecord>>(emptyList())
    val orders: StateFlow<List<OrderRecord>> = _orders.asStateFlow()

    private val _heldBills = MutableStateFlow<List<HeldBill>>(emptyList())
    val heldBills: StateFlow<List<HeldBill>> = _heldBills.asStateFlow()

    init {
        _heldBills.value = dbHelper.getAllHeldBills()
        _orders.value = dbHelper.getAllOrders()
    }

    // --- DRAFT CART PERSISTENCE ---

    fun saveDraftCart(items: List<CartItem>, customer: Customer?, redeemedPoints: Int) {
        val draftMap = DraftState(
            items = items,
            customer = customer,
            redeemedPoints = redeemedPoints
        )
        preferenceManager.draftCartJson = json.encodeToString(draftMap)
    }

    fun loadDraftCart(): DraftState? {
        val raw = preferenceManager.draftCartJson ?: return null
        return try {
            json.decodeFromString<DraftState>(raw)
        } catch (e: Exception) {
            null
        }
    }

    fun clearDraftCart() {
        preferenceManager.draftCartJson = null
    }

    // --- HELD BILLS ---

    fun holdCurrentBill(title: String, customer: Customer?, items: List<CartItem>, redeemedPoints: Int): HeldBill {
        val billId = "held-" + UUID.randomUUID().toString()
        val bill = HeldBill(
            id = billId,
            title = title.ifBlank { "บิล #${_heldBills.value.size + 1} (${customer?.name ?: "ทั่วไป"})" },
            customer = customer,
            items = items,
            redeemedPoints = redeemedPoints,
            createdAt = System.currentTimeMillis()
        )
        dbHelper.saveHeldBill(bill)
        _heldBills.value = dbHelper.getAllHeldBills()
        clearDraftCart()
        return bill
    }

    fun deleteHeldBill(id: String) {
        dbHelper.deleteHeldBill(id)
        _heldBills.value = dbHelper.getAllHeldBills()
    }

    fun refreshHeldBills() {
        _heldBills.value = dbHelper.getAllHeldBills()
    }

    // --- COMMIT SALE (POS CHECKOUT) ---

    suspend fun commitSale(
        customer: Customer?,
        cartItems: List<CartItem>,
        redeemedPoints: Int,
        paymentMethod: String,
        note: String? = null
    ): Result<CommitSaleResponse> = withContext(Dispatchers.IO) {
        if (cartItems.isEmpty()) {
            return@withContext Result.failure(Exception("ตะกร้าสินค้าว่างเปล่า"))
        }

        val idempotencyKey = UUID.randomUUID().toString()
        val deviceId = deviceIdProvider.getDeviceId()
        val orderNumber = Formatters.generateOrderNumber()

        // 1. Separate paid_qty and free_qty for each item
        val payloadItems = cartItems.map { item ->
            SaleItemPayload(
                productId = item.product.id,
                productName = item.product.name,
                paidQty = item.paidQty,
                freeQty = item.freeQty,
                totalQty = item.totalQty, // total_qty = paid_qty + free_qty
                unitPrice = item.product.price,
                subtotal = item.subtotal // paid_qty * unit_price
            )
        }

        // 2. Calculations
        val subtotal = cartItems.sumOf { it.subtotal }
        val discountAmount = redeemedPoints.toDouble() // 1 point = 1 Baht discount
        val totalAmount = (subtotal - discountAmount).coerceAtLeast(0.0)
        // Earn 1 point per 20 Baht of paid amount
        val earnedPoints = (totalAmount / 20.0).toInt()

        val request = CommitSaleRequest(
            idempotencyKey = idempotencyKey,
            deviceId = deviceId,
            customerId = customer?.id,
            items = payloadItems,
            subtotal = subtotal,
            redeemedPoints = redeemedPoints,
            discountAmount = discountAmount,
            earnedPoints = earnedPoints,
            totalAmount = totalAmount,
            paymentMethod = paymentMethod,
            note = note
        )

        // 3. Optimistic local updates:
        // Stock deduction based on total_qty = paid_qty + free_qty
        for (item in cartItems) {
            val stockDeduction = item.totalQty
            productRepository.updateStockLocally(item.product.id, -stockDeduction)
        }

        // Customer points update: deduct redeemed, add earned
        if (customer != null) {
            val netPointsDelta = earnedPoints - redeemedPoints
            customerRepository.updateCustomerPointsLocally(customer.id, netPointsDelta)
        }

        // Build Order Record and save to local database
        val orderRecord = OrderRecord(
            id = idempotencyKey,
            orderNumber = orderNumber,
            customerId = customer?.id,
            customerName = customer?.name ?: "ลูกค้าทั่วไป (Walk-in)",
            subtotal = subtotal,
            redeemedPoints = redeemedPoints,
            discountAmount = discountAmount,
            earnedPoints = earnedPoints,
            totalAmount = totalAmount,
            paymentMethod = paymentMethod,
            status = "COMPLETED",
            deviceId = deviceId,
            idempotencyKey = idempotencyKey,
            createdAt = Formatters.getCurrentIsoTimestamp(),
            items = payloadItems.map { p ->
                OrderItemRecord(
                    id = "item-" + UUID.randomUUID().toString(),
                    orderId = idempotencyKey,
                    productId = p.productId,
                    productName = p.productName,
                    paidQty = p.paidQty,
                    freeQty = p.freeQty,
                    totalQty = p.totalQty,
                    unitPrice = p.unitPrice,
                    subtotal = p.subtotal
                )
            }
        )

        dbHelper.saveOrder(orderRecord)
        _orders.value = dbHelper.getAllOrders()
        clearDraftCart()

        // 4. Send to Supabase RPC commit_sale if online
        if (networkMonitor.isOnline) {
            val rpcResult = supabaseClient.commitSaleRpc(request)
            if (rpcResult.isSuccess) {
                return@withContext rpcResult
            }
        }

        // 5. Offline fallback: Save into Outbox with the idempotency_key
        dbHelper.insertOutboxItem(
            actionType = OutboxActionType.COMMIT_SALE,
            idempotencyKey = idempotencyKey,
            payloadJson = json.encodeToString(request)
        )

        Result.success(
            CommitSaleResponse(
                success = true,
                orderId = idempotencyKey,
                orderNumber = orderNumber,
                message = "บันทึกการขายลงเครื่องเรียบร้อย (บันทึกเข้าคิวซิงค์ออฟไลน์)",
                updatedCustomerPoints = customer?.let { it.pointsBalance - redeemedPoints + earnedPoints }
            )
        )
    }

    // --- VOID ORDER ---

    suspend fun voidSale(orderId: String, voidReason: String): Result<VoidSaleResponse> = withContext(Dispatchers.IO) {
        if (voidReason.isBlank()) {
            return@withContext Result.failure(Exception("จำเป็นต้องระบุเหตุผลในการยกเลิกบิล"))
        }

        val allOrders = dbHelper.getAllOrders()
        val targetOrder = allOrders.find { it.id == orderId }
            ?: return@withContext Result.failure(Exception("ไม่พบบิลที่ต้องการยกเลิก"))

        if (targetOrder.status == "VOIDED") {
            return@withContext Result.failure(Exception("บิลนี้ถูกยกเลิกไปแล้ว"))
        }

        val idempotencyKey = "void-" + UUID.randomUUID().toString()
        val deviceId = deviceIdProvider.getDeviceId()

        val request = VoidSaleRequest(
            orderId = orderId,
            voidReason = voidReason.trim(),
            deviceId = deviceId,
            idempotencyKey = idempotencyKey
        )

        // 1. Optimistic Local Restoration:
        // Restore stock: paid_qty + free_qty
        for (item in targetOrder.items) {
            productRepository.updateStockLocally(item.productId, item.totalQty)
        }

        // Restore customer points: return redeemed points, revoke earned points
        if (!targetOrder.customerId.isNullOrBlank()) {
            val netRefund = targetOrder.redeemedPoints - targetOrder.earnedPoints
            customerRepository.updateCustomerPointsLocally(targetOrder.customerId, netRefund)
        }

        // Update local order status
        val nowIso = Formatters.getCurrentIsoTimestamp()
        dbHelper.updateOrderStatus(orderId, "VOIDED", voidReason.trim(), nowIso)
        _orders.value = dbHelper.getAllOrders()

        // 2. Call RPC void_sale if online
        if (networkMonitor.isOnline) {
            val remoteRes = supabaseClient.voidSaleRpc(request)
            if (remoteRes.isSuccess) {
                return@withContext remoteRes
            }
        }

        // 3. Queue in Outbox if offline
        dbHelper.insertOutboxItem(
            actionType = OutboxActionType.VOID_SALE,
            idempotencyKey = idempotencyKey,
            payloadJson = json.encodeToString(request)
        )

        Result.success(
            VoidSaleResponse(
                success = true,
                message = "ยกเลิกบิลและคืนสต็อก/แต้มเรียบร้อย (บันทึกเข้าคิวซิงค์ออฟไลน์)",
                restoredStockItems = targetOrder.items.sumOf { it.totalQty },
                restoredCustomerPoints = targetOrder.redeemedPoints
            )
        )
    }

    suspend fun loadOrders(forceRefresh: Boolean = false): List<OrderRecord> = withContext(Dispatchers.IO) {
        val cached = dbHelper.getAllOrders()
        if (cached.isNotEmpty()) {
            _orders.value = cached
        }

        if (networkMonitor.isOnline && (cached.isEmpty() || forceRefresh)) {
            val remoteRes = supabaseClient.getOrders()
            if (remoteRes.isSuccess) {
                val list = remoteRes.getOrThrow()
                for (o in list) {
                    dbHelper.saveOrder(o)
                }
                _orders.value = dbHelper.getAllOrders()
                return@withContext _orders.value
            }
        }
        _orders.value
    }
}

@kotlinx.serialization.Serializable
data class DraftState(
    val items: List<CartItem>,
    val customer: Customer? = null,
    val redeemedPoints: Int = 0
)
