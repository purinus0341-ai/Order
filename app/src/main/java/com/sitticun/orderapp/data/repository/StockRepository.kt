package com.sitticun.orderapp.data.repository

import com.sitticun.orderapp.data.api.SupabaseClient
import com.sitticun.orderapp.data.local.OrderDatabaseHelper
import com.sitticun.orderapp.data.model.*
import com.sitticun.orderapp.util.DeviceIdProvider
import com.sitticun.orderapp.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class StockRepository(
    private val supabaseClient: SupabaseClient,
    private val dbHelper: OrderDatabaseHelper,
    private val productRepository: ProductRepository,
    private val networkMonitor: NetworkMonitor,
    private val deviceIdProvider: DeviceIdProvider
) {
    private val _snapshots = MutableStateFlow<List<StockSnapshot>>(emptyList())
    val snapshots: StateFlow<List<StockSnapshot>> = _snapshots.asStateFlow()

    private val _countSessions = MutableStateFlow<List<CountSession>>(emptyList())
    val countSessions: StateFlow<List<CountSession>> = _countSessions.asStateFlow()

    init {
        _snapshots.value = dbHelper.getAllStockSnapshots()
        _countSessions.value = dbHelper.getAllCountSessions()
    }

    suspend fun loadSnapshots(forceRefresh: Boolean = false): List<StockSnapshot> = withContext(Dispatchers.IO) {
        val cached = dbHelper.getAllStockSnapshots()
        if (cached.isNotEmpty()) {
            _snapshots.value = cached
        }

        if (networkMonitor.isOnline && (cached.isEmpty() || forceRefresh)) {
            val remoteRes = supabaseClient.getStockSnapshots()
            if (remoteRes.isSuccess) {
                val list = remoteRes.getOrThrow()
                dbHelper.saveStockSnapshots(list)
                _snapshots.value = list
                return@withContext list
            }
        }
        _snapshots.value
    }

    // Calculates system stock for all active products:
    // System Stock = (Qty in Latest Snapshot) - (Paid Qty + Free Qty sold since snapshot)
    suspend fun getStockComparisons(currentInputs: Map<Long, Int>): List<ProductStockComparison> = withContext(Dispatchers.IO) {
        val products = productRepository.loadProducts()
        val latestSnapshot = dbHelper.getLatestStockSnapshot()

        val snapshotCounts = latestSnapshot?.items?.associate { it.productId to it.qty } ?: emptyMap()
        val soldSince = dbHelper.getProductTotalSoldSince(latestSnapshot?.capturedAt)

        products.map { product ->
            val baseQty = snapshotCounts[product.id] ?: 0
            val sold = soldSince[product.id] ?: 0
            val systemQty = (baseQty - sold).coerceAtLeast(0)
            val actualQty = currentInputs[product.id] ?: systemQty

            ProductStockComparison(
                product = product,
                systemQty = systemQty,
                actualQty = actualQty
            )
        }
    }

    suspend fun createSnapshot(
        counts: Map<Long, Int>,
        note: String?
    ): Result<StockSnapshot> = withContext(Dispatchers.IO) {
        val deviceId = deviceIdProvider.getDeviceId()
        val snapshotUuid = UUID.randomUUID().toString()
        val snapshotId = "STK-$snapshotUuid"
        val idempotencyKey = "stock:$deviceId:$snapshotUuid"
        val isoNow = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

        val items = counts.map { (productId, qty) ->
            StockSnapshotItem(
                id = "STKI-${UUID.randomUUID()}",
                snapshotId = snapshotId,
                productId = productId,
                qty = qty
            )
        }

        val snapshot = StockSnapshot(
            id = snapshotId,
            capturedAt = isoNow,
            note = note?.trim()?.ifBlank { null },
            sourceDeviceId = deviceId,
            items = items
        )

        dbHelper.saveStockSnapshot(snapshot)
        _snapshots.value = dbHelper.getAllStockSnapshots()

        if (networkMonitor.isOnline) {
            val remoteRes = supabaseClient.createStockSnapshot(snapshot, idempotencyKey)
            if (remoteRes.isSuccess) {
                // Trigger line notify in background
                supabaseClient.notifyStockSnapshot(snapshotId)
                return@withContext Result.success(snapshot)
            }
        }

        Result.success(snapshot)
    }

    suspend fun sendLineStockNotification(snapshotId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!networkMonitor.isOnline) {
            return@withContext Result.failure(Exception("ไม่สามารถส่ง LINE ได้ขณะออฟไลน์"))
        }
        supabaseClient.notifyStockSnapshot(snapshotId)
    }

    // -------------------------------------------------------------
    // COUNT SESSIONS & MARKERS
    // -------------------------------------------------------------
    suspend fun loadCountSessions(): List<CountSession> = withContext(Dispatchers.IO) {
        val cached = dbHelper.getAllCountSessions()
        if (cached.isNotEmpty()) {
            _countSessions.value = cached
        }

        if (networkMonitor.isOnline) {
            val remote = supabaseClient.getCountSessions()
            if (remote.isSuccess) {
                val list = remote.getOrThrow()
                for (s in list) {
                    dbHelper.saveCountSession(s)
                }
                _countSessions.value = dbHelper.getAllCountSessions()
                return@withContext _countSessions.value
            }
        }
        _countSessions.value
    }

    suspend fun saveCountSession(
        session: CountSession,
        imageBytes: ByteArray? = null,
        mimeType: String = "image/jpeg"
    ): Result<CountSession> = withContext(Dispatchers.IO) {
        dbHelper.saveCountSession(session)
        _countSessions.value = dbHelper.getAllCountSessions()

        if (networkMonitor.isOnline) {
            if (imageBytes != null && !session.imagePath.isNullOrBlank()) {
                supabaseClient.uploadCountImage(session.imagePath, imageBytes, mimeType)
            }
            val saveSessionRes = supabaseClient.saveCountSession(session, 0L)
            if (saveSessionRes.isSuccess) {
                for (marker in session.markers) {
                    supabaseClient.addCountMarker(marker)
                }
                return@withContext Result.success(session)
            }
        }

        Result.success(session)
    }
}
