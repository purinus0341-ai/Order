package com.sitticun.orderapp.data.repository

import com.sitticun.orderapp.data.api.SupabaseClient
import com.sitticun.orderapp.data.local.OrderDatabaseHelper
import com.sitticun.orderapp.data.model.*
import com.sitticun.orderapp.util.DeviceIdProvider
import com.sitticun.orderapp.util.Formatters
import com.sitticun.orderapp.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class StockRepository(
    private val supabaseClient: SupabaseClient,
    private val dbHelper: OrderDatabaseHelper,
    private val productRepository: ProductRepository,
    private val networkMonitor: NetworkMonitor,
    private val deviceIdProvider: DeviceIdProvider
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _snapshots = MutableStateFlow<List<StockSnapshot>>(emptyList())
    val snapshots: StateFlow<List<StockSnapshot>> = _snapshots.asStateFlow()

    init {
        _snapshots.value = dbHelper.getStockSnapshots()
    }

    suspend fun adjustStock(
        productId: String,
        newQty: Int,
        reason: String
    ): Result<StockSnapshot> = withContext(Dispatchers.IO) {
        val products = productRepository.products.value
        val product = products.find { it.id == productId }
            ?: return@withContext Result.failure(Exception("ไม่พบสินค้ารหัส $productId"))

        val previousQty = product.stockQty
        val deltaQty = newQty - previousQty
        val snapshotId = "snap-" + UUID.randomUUID().toString()
        val idempotencyKey = UUID.randomUUID().toString()
        val deviceId = deviceIdProvider.getDeviceId()

        val snapshot = StockSnapshot(
            id = snapshotId,
            productId = productId,
            productName = product.name,
            previousQty = previousQty,
            newQty = newQty,
            deltaQty = deltaQty,
            reason = reason,
            createdAt = Formatters.getCurrentIsoTimestamp()
        )

        // Update local database
        dbHelper.insertStockSnapshot(snapshot)
        productRepository.setStockLocally(productId, newQty)
        _snapshots.value = dbHelper.getStockSnapshots()

        val payload = StockAdjustmentPayload(
            productId = productId,
            newStockQty = newQty,
            deltaQty = deltaQty,
            reason = reason,
            idempotencyKey = idempotencyKey,
            deviceId = deviceId
        )

        if (networkMonitor.isOnline) {
            val res = supabaseClient.adjustStockRpc(payload)
            if (res.isSuccess) {
                return@withContext Result.success(snapshot)
            }
        }

        // Offline queue
        dbHelper.insertOutboxItem(
            actionType = OutboxActionType.STOCK_ADJUSTMENT,
            idempotencyKey = idempotencyKey,
            payloadJson = json.encodeToString(payload)
        )

        Result.success(snapshot)
    }

    suspend fun saveImageCount(
        productId: String,
        countedQty: Int,
        isOverride: Boolean
    ): Result<StockSnapshot> = withContext(Dispatchers.IO) {
        val products = productRepository.products.value
        val product = products.find { it.id == productId }
            ?: return@withContext Result.failure(Exception("ไม่พบสินค้าที่เลือก"))

        val newStock = if (isOverride) countedQty else (product.stockQty + countedQty)
        adjustStock(
            productId = productId,
            newQty = newStock,
            reason = if (isOverride) "นับสต็อกจากรูปภาพ (บันทึกทับ: $countedQty ชิ้น)" else "นับสต็อกจากรูปภาพ (บวกเพิ่ม: $countedQty ชิ้น)"
        )
    }

    suspend fun sendLineStockNotification(lowStockOnly: Boolean = true): Result<LineStockNotifyResponse> = withContext(Dispatchers.IO) {
        if (!networkMonitor.isOnline) {
            return@withContext Result.failure(Exception("ไม่สามารถส่ง LINE ได้ขณะออฟไลน์"))
        }

        val request = LineStockNotifyRequest(
            triggerSource = "orderapp_android_pos",
            lowStockOnly = lowStockOnly
        )

        supabaseClient.triggerLineStockNotify(request)
    }
}
