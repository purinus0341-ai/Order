package com.sitticun.orderapp.data.repository

import com.sitticun.orderapp.data.api.SupabaseClient
import com.sitticun.orderapp.data.local.OrderDatabaseHelper
import com.sitticun.orderapp.data.model.Customer
import com.sitticun.orderapp.data.model.PointBalance
import com.sitticun.orderapp.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

class CustomerRepository(
    private val supabaseClient: SupabaseClient,
    private val dbHelper: OrderDatabaseHelper,
    private val networkMonitor: NetworkMonitor
) {
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

    suspend fun createCustomer(name: String, customId: String? = null): Result<Customer> = withContext(Dispatchers.IO) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("กรุณากรอกชื่อลูกค้า"))
        }

        val custId = customId?.takeIf { it.isNotBlank() } ?: ("CUST-" + UUID.randomUUID().toString())
        val customer = Customer(
            id = custId,
            name = trimmedName,
            normalizedName = trimmedName.lowercase()
        )

        dbHelper.saveCustomer(customer)
        _customers.value = dbHelper.getAllCustomers()

        if (networkMonitor.isOnline) {
            val res = supabaseClient.createCustomer(custId, trimmedName)
            if (res.isSuccess) {
                val created = res.getOrThrow()
                dbHelper.saveCustomer(created)
                _customers.value = dbHelper.getAllCustomers()
                return@withContext Result.success(created)
            }
        }

        Result.success(customer)
    }

    suspend fun getCustomerPointBalances(customerId: String): List<PointBalance> = withContext(Dispatchers.IO) {
        if (networkMonitor.isOnline) {
            val remote = supabaseClient.getPointBalances(customerId)
            if (remote.isSuccess) {
                val balances = remote.getOrThrow()
                dbHelper.savePointBalances(customerId, balances)
                return@withContext balances
            }
        }
        dbHelper.getPointBalancesForCustomer(customerId)
    }
}
