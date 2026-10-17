package com.sitticun.orderapp.data.repository

import com.sitticun.orderapp.data.api.SupabaseClient
import com.sitticun.orderapp.data.local.OrderDatabaseHelper
import com.sitticun.orderapp.data.local.PreferenceManager
import com.sitticun.orderapp.data.model.*
import com.sitticun.orderapp.util.DeviceIdProvider
import com.sitticun.orderapp.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Serializable
data class PosDraftState(
    val customerId: String? = null,
    val heldBillId: String? = null,
    val paidQty: Map<String, Int> = emptyMap(),
    val freeQty: Map<String, Int> = emptyMap(),
    val pointsUsed: Map<String, Int> = emptyMap()
)

class OrderRepository(
    private val supabaseClient: SupabaseClient,
    private val dbHelper: OrderDatabaseHelper,
    private val preferenceManager: PreferenceManager,
    private val networkMonitor: NetworkMonitor,
    private val deviceIdProvider: DeviceIdProvider
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _orders = MutableStateFlow<List<OrderSummary>>(emptyList())
    val orders: StateFlow<List<OrderSummary>> = _orders.asStateFlow()

    private val _heldBills = MutableStateFlow<List<HeldBill>>(emptyList())
    val heldBills: StateFlow<List<HeldBill>> = _heldBills.asStateFlow()

    init {
        _heldBills.value = dbHelper.getAllHeldBills()
        _orders.value = dbHelper.getAllOrders()
    }

    // -------------------------------------------------------------
    // DRAFT CART PERSISTENCE
    // -------------------------------------------------------------
    fun saveDraft(draft: PosDraftState) {
        preferenceManager.draftCartJson = json.encodeToString(draft)
    }

    fun loadDraft(): PosDraftState? {
        val raw = preferenceManager.draftCartJson ?: return null
        return try {
            json.decodeFromString<PosDraftState>(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun clearDraft() {
        preferenceManager.draftCartJson = null
    }

    // -------------------------------------------------------------
    // HELD BILLS
    // -------------------------------------------------------------
    suspend fun loadHeldBills(): List<HeldBill> = withContext(Dispatchers.IO) {
        val cached = dbHelper.getAllHeldBills()
        if (cached.isNotEmpty()) {
            _heldBills.value = cached
        }

        if (networkMonitor.isOnline) {
            val remote = supabaseClient.getHeldBills()
            if (remote.isSuccess) {
                val list = remote.getOrThrow()
                dbHelper.saveHeldBills(list)
                _heldBills.value = list
                return@withContext list
            }
        }
        _heldBills.value
    }

    suspend fun holdBill(
        customer: Customer,
        note: String?,
        paidQty: Map<Long, Int>,
        pointUses: List<PointUseInput>,
        freeItems: List<FreeItemInput>,
        existingBillId: String? = null,
        expectedVersion: Long = 0L
    ): Result<HeldBill> = withContext(Dispatchers.IO) {
        val billId = existingBillId ?: ("HELD-" + UUID.randomUUID().toString())
        val payload = HeldBillPayload(
            paidQty = paidQty.mapKeys { it.key.toString() },
            pointUses = pointUses,
            freeItems = freeItems
        )
        val isoNow = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        val heldBill = HeldBill(
            id = billId,
            customerId = customer.id,
            customerName = customer.name,
            note = note,
            payload = payload,
            updatedAt = isoNow,
            version = if (expectedVersion > 0) expectedVersion + 1 else 1L
        )

        if (existingBillId != null && expectedVersion <= 0L) {
            return@withContext Result.failure(
                IllegalArgumentException("ไม่พบเวอร์ชันของบิลพัก กรุณาโหลดรายการบิลพักใหม่ก่อนบันทึก")
            )
        }

        if (networkMonitor.isOnline) {
            val remoteResult = supabaseClient.saveHeldBill(heldBill, expectedVersion)
            if (remoteResult.isFailure) {
                return@withContext Result.failure(
                    remoteResult.exceptionOrNull()
                        ?: IllegalStateException("บันทึกบิลพักขึ้นคลาวด์ไม่สำเร็จ")
                )
            }

            val updatedBill = heldBill.copy(version = remoteResult.getOrThrow())
            dbHelper.saveHeldBill(updatedBill)
            _heldBills.value = dbHelper.getAllHeldBills()
            clearDraft()
            return@withContext Result.success(updatedBill)
        }

        // Offline: preserve the local-first workflow. Online conflicts are never
        // reported as success and never overwrite the local cache.
        dbHelper.saveHeldBill(heldBill)
        _heldBills.value = dbHelper.getAllHeldBills()
        clearDraft()
        Result.success(heldBill)
    }

    suspend fun deleteHeldBill(id: String, expectedVersion: Long): Result<Unit> = withContext(Dispatchers.IO) {
        dbHelper.deleteHeldBill(id)
        _heldBills.value = dbHelper.getAllHeldBills()

        if (networkMonitor.isOnline) {
            return@withContext supabaseClient.deleteHeldBill(id, expectedVersion)
        }
        Result.success(Unit)
    }

    // -------------------------------------------------------------
    // COMMIT SALE (POS CHECKOUT)
    // -------------------------------------------------------------
    suspend fun commitSale(
        customer: Customer,
        cartItems: List<CartItem>,
        heldBillId: String? = null
    ): Result<CloudSaleResult> = withContext(Dispatchers.IO) {
        val paidItems = cartItems.filter { it.paidQty > 0 }
        val freeItems = cartItems.filter { it.freeQty > 0 }
        val pointUses = cartItems.filter { it.pointsUsed > 0 }

        if (pointUses.isNotEmpty() && freeItems.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("การใช้แต้มต้องมีของแถม"))
        }
        if (freeItems.isNotEmpty() && pointUses.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("ของแถมต้องมีการใช้แต้ม"))
        }
        if (paidItems.isEmpty() && pointUses.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("ยังไม่ได้เลือกสินค้า หรือของแถม"))
        }

        val hasRedemptions = pointUses.isNotEmpty() || freeItems.isNotEmpty()
        if (hasRedemptions && !networkMonitor.isOnline) {
            return@withContext Result.failure(IllegalStateException("การแลกแต้มหรือของแถมต้องเชื่อมต่ออินเทอร์เน็ต"))
        }

        val orderUuid = UUID.randomUUID().toString()
        val orderId = "ORD-$orderUuid"
        val deviceId = deviceIdProvider.getDeviceId()
        val idempotencyKey = "sale:$deviceId:$orderUuid"
        val isoNow = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

        val cloudItems = paidItems.map {
            OrderItemInput(
                id = "$orderId:item:${it.product.id}",
                productId = it.product.id,
                qty = it.paidQty
            )
        }
        val cloudPointUses = pointUses.map {
            PointUseInput(
                productId = it.product.id,
                points = it.pointsUsed
            )
        }
        val cloudFreeItems = freeItems.map {
            FreeItemInput(
                productId = it.product.id,
                qty = it.freeQty
            )
        }

        val saleOrder = CloudSaleOrder(
            id = orderId,
            customerId = customer.id,
            customerName = customer.name,
            heldBillId = heldBillId,
            items = cloudItems,
            pointUses = cloudPointUses,
            freeItems = cloudFreeItems
        )

        val totalPaidQty = paidItems.sumOf { it.paidQty }
        val totalAmount = paidItems.sumOf { it.paidQty * it.product.price }

        // Local cache order
        val localSummary = OrderSummary(
            id = orderId,
            customerId = customer.id,
            customerName = customer.name,
            createdAt = isoNow,
            totalQty = totalPaidQty,
            total = totalAmount,
            status = "completed",
            items = paidItems.map {
                OrderItemRecord(
                    id = "$orderId:item:${it.product.id}",
                    orderId = orderId,
                    productId = it.product.id,
                    nameSnapshot = it.product.name,
                    volumeSnapshot = it.product.volume,
                    unitPrice = it.product.price,
                    qty = it.paidQty,
                    subtotal = it.paidQty * it.product.price
                )
            },
            freeItems = freeItems.map {
                OrderFreeItemRecord(
                    id = "$orderId:free:${it.product.id}",
                    orderId = orderId,
                    productId = it.product.id,
                    qty = it.freeQty
                )
            },
            pointUses = pointUses.map {
                OrderPointUseRecord(
                    id = "$orderId:points:${it.product.id}",
                    orderId = orderId,
                    productId = it.product.id,
                    points = it.pointsUsed
                )
            }
        )

        dbHelper.saveOrder(localSummary)

        // Optimistically update local point balances
        for (item in paidItems) {
            dbHelper.updatePointBalance(customer.id, item.product.id, item.paidQty)
        }
        for (u in pointUses) {
            dbHelper.updatePointBalance(customer.id, u.product.id, -u.pointsUsed)
        }

        if (heldBillId != null) {
            dbHelper.deleteHeldBill(heldBillId)
            _heldBills.value = dbHelper.getAllHeldBills()
        }

        _orders.value = dbHelper.getAllOrders()
        clearDraft()

        if (networkMonitor.isOnline) {
            val remoteResult = supabaseClient.commitSale(saleOrder, idempotencyKey, deviceId)
            if (remoteResult.isSuccess) {
                val res = remoteResult.getOrThrow()
                // Update local balances with authoritative balances from server
                val balances = res.pointBalances.mapNotNull { (pIdStr, bal) ->
                    pIdStr.toLongOrNull()?.let { pId ->
                        PointBalance(customerId = customer.id, productId = pId, balance = bal)
                    }
                }
                if (balances.isNotEmpty()) {
                    dbHelper.savePointBalances(customer.id, balances)
                }
                return@withContext Result.success(res)
            }
        }

        // Queue in Outbox for offline sync (only valid for paid-only orders)
        val payloadJson = json.encodeToString(
            CommitSaleRpcPayload(order = saleOrder, idempotencyKey = idempotencyKey, deviceId = deviceId)
        )
        dbHelper.insertOutbox(
            actionType = "commit_sale",
            idempotencyKey = idempotencyKey,
            payloadJson = payloadJson
        )

        Result.success(
            CloudSaleResult(
                orderId = orderId,
                total = totalAmount,
                totalQty = totalPaidQty,
                idempotentReplay = false
            )
        )
    }

    // -------------------------------------------------------------
    // VOID ORDER (Admin Only)
    // -------------------------------------------------------------
    suspend fun voidOrder(orderId: String): Result<VoidOrderResult> = withContext(Dispatchers.IO) {
        val target = _orders.value.find { it.id == orderId }
            ?: return@withContext Result.failure(Exception("ไม่พบบิล"))

        if (target.isVoided) {
            return@withContext Result.failure(Exception("บิลนี้ถูกยกเลิกแล้ว"))
        }

        val idempotencyKey = "void:${target.id}"
        dbHelper.markOrderVoided(orderId)
        _orders.value = dbHelper.getAllOrders()

        if (networkMonitor.isOnline) {
            val remoteResult = supabaseClient.voidOrder(orderId, idempotencyKey)
            if (remoteResult.isSuccess) {
                val res = remoteResult.getOrThrow()
                val balances = res.pointBalances.mapNotNull { (pIdStr, bal) ->
                    pIdStr.toLongOrNull()?.let { pId ->
                        PointBalance(customerId = target.customerId, productId = pId, balance = bal)
                    }
                }
                if (balances.isNotEmpty()) {
                    dbHelper.savePointBalances(target.customerId, balances)
                }
                return@withContext Result.success(res)
            }
        }

        Result.success(
            VoidOrderResult(
                orderId = orderId,
                status = "voided",
                idempotentReplay = false
            )
        )
    }

    suspend fun loadOrders(forceRefresh: Boolean = false): List<OrderSummary> = withContext(Dispatchers.IO) {
        val cached = dbHelper.getAllOrders()
        if (cached.isNotEmpty()) {
            _orders.value = cached
        }

        if (networkMonitor.isOnline && (cached.isEmpty() || forceRefresh)) {
            val remoteRes = supabaseClient.getOrders()
            if (remoteRes.isSuccess) {
                val list = remoteRes.getOrThrow()
                dbHelper.saveOrders(list)
                _orders.value = dbHelper.getAllOrders()
                return@withContext _orders.value
            }
        }
        _orders.value
    }
}
