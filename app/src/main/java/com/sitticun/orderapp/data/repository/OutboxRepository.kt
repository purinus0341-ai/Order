package com.sitticun.orderapp.data.repository

import com.sitticun.orderapp.data.api.SupabaseClient
import com.sitticun.orderapp.data.local.OrderDatabaseHelper
import com.sitticun.orderapp.data.local.OutboxTask
import com.sitticun.orderapp.data.model.CommitSaleRpcPayload
import com.sitticun.orderapp.data.model.OutboxItem
import com.sitticun.orderapp.data.model.OutboxStatus
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
        val tasks = dbHelper.getAllOutbox()
        _outboxItems.value = tasks.map {
            OutboxItem(
                id = it.id,
                actionType = it.actionType,
                idempotencyKey = it.idempotencyKey,
                payloadJson = it.payloadJson,
                createdAt = it.createdAt,
                status = it.status,
                retryCount = it.retryCount,
                lastError = it.lastError
            )
        }
    }

    suspend fun syncPendingItems(): Int = withContext(Dispatchers.IO) {
        if (!networkMonitor.isOnline) return@withContext 0
        if (_isSyncing.value) return@withContext 0

        _isSyncing.value = true
        var successCount = 0

        try {
            val pending = dbHelper.getPendingOutbox()
            for (task in pending) {
                dbHelper.updateOutboxStatus(task.id, OutboxStatus.SYNCING)

                val result = processSingleTask(task)
                if (result.isSuccess) {
                    dbHelper.updateOutboxStatus(task.id, OutboxStatus.SYNCED)
                    successCount++
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Sync error"
                    dbHelper.incrementOutboxRetry(task.id, err)
                }
            }
        } finally {
            _isSyncing.value = false
            refreshOutbox()
        }

        successCount
    }

    private suspend fun processSingleTask(task: OutboxTask): Result<Unit> {
        return try {
            when (task.actionType) {
                "commit_sale" -> {
                    val req = json.decodeFromString<CommitSaleRpcPayload>(task.payloadJson)
                    val res = supabaseClient.commitSale(req.order, req.idempotencyKey, req.deviceId ?: "")
                    if (res.isSuccess) Result.success(Unit) else Result.failure(res.exceptionOrNull() ?: Exception("RPC commit_sale failed"))
                }
                else -> Result.failure(Exception("Unknown action type: ${task.actionType}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteItem(id: Long) {
        dbHelper.deleteOutbox(id)
        refreshOutbox()
    }

    fun clearSynced() {
        val items = dbHelper.getAllOutbox().filter { it.status == OutboxStatus.SYNCED }
        for (item in items) {
            dbHelper.deleteOutbox(item.id)
        }
        refreshOutbox()
    }

    fun clearCompleted() {
        clearSynced()
    }

    fun clearAll() {
        val items = dbHelper.getAllOutbox()
        for (item in items) {
            dbHelper.deleteOutbox(item.id)
        }
        refreshOutbox()
    }
}
