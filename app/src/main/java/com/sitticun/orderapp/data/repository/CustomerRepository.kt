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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class CustomerRepository(
    private val supabaseClient: SupabaseClient,
    private val dbHelper: OrderDatabaseHelper,
    private val networkMonitor: NetworkMonitor,
    private val deviceIdProvider: DeviceIdProvider
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _customers = MutableStateFlow<List<Customer>>(emptyList())
    val customers: StateFlow<List<Customer>> = _customers.asStateFlow()

    suspend fun loadCustomers(forceRefresh: Boolean = false): List<Customer> = withContext(Dispatchers.IO) {
        val cached = dbHelper.getAllCustomers()
        if (cached.isNotEmpty()) {
            _customers.value = cached
        }

        if (networkMonitor.isOnline && (cached.isEmpty() || forceRefresh)) {
            val remoteRes = supabaseClient.getCustomers()
            if (remoteRes.isSuccess) {
                val list = remoteRes.getOrThrow()
                dbHelper.saveCustomers(list)
                _customers.value = list
                return@withContext list
            }
        }
        _customers.value
    }

    suspend fun saveCustomer(payload: CustomerUpsertPayload): Result<Customer> = withContext(Dispatchers.IO) {
        val custId = payload.id ?: ("cust-" + UUID.randomUUID().toString())
        val customer = Customer(
            id = custId,
            name = payload.name,
            phone = payload.phone,
            email = payload.email,
            pointsBalance = payload.pointsBalance,
            notes = payload.notes
        )

        dbHelper.saveCustomers(listOf(customer))
        _customers.value = dbHelper.getAllCustomers()

        if (networkMonitor.isOnline) {
            val res = supabaseClient.upsertCustomer(payload.copy(id = custId))
            if (res.isSuccess) {
                return@withContext res
            }
        }

        dbHelper.insertOutboxItem(
            actionType = OutboxActionType.CUSTOMER_UPSERT,
            idempotencyKey = "upsert-cust-$custId",
            payloadJson = json.encodeToString(payload.copy(id = custId))
        )

        Result.success(customer)
    }

    suspend fun adjustPoints(customerId: String, pointsChange: Int, reason: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val idempotencyKey = UUID.randomUUID().toString()
        val deviceId = deviceIdProvider.getDeviceId()

        val payload = PointsAdjustmentPayload(
            customerId = customerId,
            pointsChange = pointsChange,
            reason = reason,
            idempotencyKey = idempotencyKey,
            deviceId = deviceId
        )

        // Optimistically update local database
        dbHelper.updateCustomerPointsLocal(customerId, pointsChange)
        _customers.value = dbHelper.getAllCustomers()

        if (networkMonitor.isOnline) {
            val res = supabaseClient.adjustCustomerPoints(payload)
            if (res.isSuccess) {
                return@withContext res
            }
        }

        // Queue in Outbox if offline or failed
        dbHelper.insertOutboxItem(
            actionType = OutboxActionType.CUSTOMER_POINTS,
            idempotencyKey = idempotencyKey,
            payloadJson = json.encodeToString(payload)
        )

        Result.success(true)
    }

    fun updateCustomerPointsLocally(customerId: String, deltaPoints: Int) {
        dbHelper.updateCustomerPointsLocal(customerId, deltaPoints)
        _customers.value = dbHelper.getAllCustomers()
    }
}
