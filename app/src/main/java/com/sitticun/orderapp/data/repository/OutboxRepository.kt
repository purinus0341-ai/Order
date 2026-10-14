package com.sitticun.orderapp.data.repository

import com.sitticun.orderapp.data.api.SupabaseClient
import com.sitticun.orderapp.data.local.OrderDatabaseHelper
import com.sitticun.orderapp.data.model.*
import com.sitticun.orderapp.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class OutboxRepository(
    private val supabaseClient: SupabaseClient,
    private val dbHelper: OrderDatabaseHelper,
    private val networkMonitor: NetworkMonitor
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _outboxItems = MutableStateFlow<List<OutboxItem>>(emptyList())
    val outboxItems: StateFlow<List<OutboxItem>> = _outboxItems.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        refreshOutbox()
    }

    fun refreshOutbox() {
        _outboxItems.value = dbHelper.getAllOutboxItems()
    }

    suspend fun syncPendingItems(): Int = withContext(Dispatchers.IO) {
        if (!networkMonitor.isOnline) return@withContext 0
        if (_isSyncing.value) return@withContext 0

        _isSyncing.value = true
        var successCount = 0

        try {
            val pending = dbHelper.getPendingOutboxItems()
            for (item in pending) {
                dbHelper.updateOutboxStatus(item.id, OutboxStatus.SYNCING)

                val result = processSingleOutboxItem(item)
                if (result.isSuccess) {
                    dbHelper.updateOutboxStatus(item.id, OutboxStatus.SYNCED)
                    successCount++
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Sync error"
                    dbHelper.updateOutboxStatus(item.id, OutboxStatus.FAILED, err)
                }
            }
        } finally {
            _isSyncing.value = false
            refreshOutbox()
        }

        successCount
    }

    private suspend fun processSingleOutboxItem(item: OutboxItem): Result<Unit> {
        return try {
            when (item.actionType) {
                OutboxActionType.COMMIT_SALE -> {
                    val req = json.decodeFromString<CommitSaleRequest>(item.payloadJson)
                    val res = supabaseClient.commitSaleRpc(req)
                    if (res.isSuccess) Result.success(Unit) else Result.failure(res.exceptionOrNull() ?: Exception("RPC commit_sale failed"))
                }
                OutboxActionType.VOID_SALE -> {
                    val req = json.decodeFromString<VoidSaleRequest>(item.payloadJson)
                    val res = supabaseClient.voidSaleRpc(req)
                    if (res.isSuccess) Result.success(Unit) else Result.failure(res.exceptionOrNull() ?: Exception("RPC void_sale failed"))
                }
                OutboxActionType.STOCK_ADJUSTMENT -> {
                    val req = json.decodeFromString<StockAdjustmentPayload>(item.payloadJson)
                    val res = supabaseClient.adjustStockRpc(req)
                    if (res.isSuccess) Result.success(Unit) else Result.failure(res.exceptionOrNull() ?: Exception("RPC adjust_stock failed"))
                }
                OutboxActionType.CUSTOMER_POINTS -> {
                    val req = json.decodeFromString<PointsAdjustmentPayload>(item.payloadJson)
                    val res = supabaseClient.adjustCustomerPoints(req)
                    if (res.isSuccess) Result.success(Unit) else Result.failure(res.exceptionOrNull() ?: Exception("RPC adjust_points failed"))
                }
                OutboxActionType.PRODUCT_UPSERT -> {
                    val req = json.decodeFromString<ProductUpsertPayload>(item.payloadJson)
                    val res = supabaseClient.upsertProduct(req)
                    if (res.isSuccess) Result.success(Unit) else Result.failure(res.exceptionOrNull() ?: Exception("Upsert product failed"))
                }
                OutboxActionType.CUSTOMER_UPSERT -> {
                    val req = json.decodeFromString<CustomerUpsertPayload>(item.payloadJson)
                    val res = supabaseClient.upsertCustomer(req)
                    if (res.isSuccess) Result.success(Unit) else Result.failure(res.exceptionOrNull() ?: Exception("Upsert customer failed"))
                }
                else -> Result.failure(Exception("Unknown action type ${item.actionType}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun clearCompleted() {
        dbHelper.clearSyncedOutboxItems()
        refreshOutbox()
    }

    fun clearAll() {
        dbHelper.clearAllOutboxItems()
        refreshOutbox()
    }
}
