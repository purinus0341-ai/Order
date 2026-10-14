package com.sitticun.orderapp.data.repository

import com.sitticun.orderapp.data.api.SupabaseClient
import com.sitticun.orderapp.data.local.OrderDatabaseHelper
import com.sitticun.orderapp.data.model.Product
import com.sitticun.orderapp.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class ProductRepository(
    private val supabaseClient: SupabaseClient,
    private val dbHelper: OrderDatabaseHelper,
    private val networkMonitor: NetworkMonitor
) {
    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    suspend fun loadProducts(forceRefresh: Boolean = false): List<Product> = withContext(Dispatchers.IO) {
        val cached = dbHelper.getAllProducts()
        if (cached.isNotEmpty()) {
            _products.value = cached
        }

        if (networkMonitor.isOnline && (cached.isEmpty() || forceRefresh)) {
            val remoteResult = supabaseClient.getProducts()
            if (remoteResult.isSuccess) {
                val remoteList = remoteResult.getOrThrow()
                dbHelper.saveProducts(remoteList)
                _products.value = remoteList.filter { it.isActive }
                return@withContext _products.value
            }
        }
        _products.value
    }

    suspend fun loadProductsAdmin(): List<Product> = withContext(Dispatchers.IO) {
        if (networkMonitor.isOnline) {
            val remoteResult = supabaseClient.getProducts()
            if (remoteResult.isSuccess) {
                val remoteList = remoteResult.getOrThrow()
                dbHelper.saveProducts(remoteList)
                return@withContext remoteList
            }
        }
        dbHelper.getAllProductsAdmin()
    }

    suspend fun createProduct(product: Product): Result<Unit> = withContext(Dispatchers.IO) {
        dbHelper.saveProducts(listOf(product))
        if (networkMonitor.isOnline) {
            val res = supabaseClient.createProduct(product)
            if (res.isSuccess) {
                loadProducts(true)
            }
            return@withContext res
        }
        loadProducts()
        Result.success(Unit)
    }

    suspend fun updateProduct(product: Product): Result<Unit> = withContext(Dispatchers.IO) {
        dbHelper.saveProducts(listOf(product))
        if (networkMonitor.isOnline) {
            val res = supabaseClient.updateProduct(product)
            if (res.isSuccess) {
                loadProducts(true)
            }
            return@withContext res
        }
        loadProducts()
        Result.success(Unit)
    }
}
