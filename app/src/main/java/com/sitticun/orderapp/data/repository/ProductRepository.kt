package com.sitticun.orderapp.data.repository

import com.sitticun.orderapp.data.api.SupabaseClient
import com.sitticun.orderapp.data.local.OrderDatabaseHelper
import com.sitticun.orderapp.data.model.OutboxActionType
import com.sitticun.orderapp.data.model.Product
import com.sitticun.orderapp.data.model.ProductUpsertPayload
import com.sitticun.orderapp.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class ProductRepository(
    private val supabaseClient: SupabaseClient,
    private val dbHelper: OrderDatabaseHelper,
    private val networkMonitor: NetworkMonitor
) {
    private val json = Json { ignoreUnknownKeys = true }
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
                _products.value = remoteList
                return@withContext remoteList
            }
        }
        _products.value
    }

    suspend fun saveProduct(payload: ProductUpsertPayload): Result<Product> = withContext(Dispatchers.IO) {
        val productId = payload.id ?: ("prod-" + UUID.randomUUID().toString())
        val product = Product(
            id = productId,
            name = payload.name,
            sku = payload.sku,
            barcode = payload.barcode,
            category = payload.category,
            price = payload.price,
            cost = payload.cost,
            stockQty = payload.stockQty,
            minStockAlert = payload.minStockAlert,
            imageUrl = payload.imageUrl,
            isActive = payload.isActive
        )

        // Save locally first
        dbHelper.saveProducts(listOf(product))
        _products.value = dbHelper.getAllProducts()

        if (networkMonitor.isOnline) {
            val remoteRes = supabaseClient.upsertProduct(payload.copy(id = productId))
            if (remoteRes.isSuccess) {
                return@withContext remoteRes
            }
        }

        // If offline or error, queue to Outbox
        dbHelper.insertOutboxItem(
            actionType = OutboxActionType.PRODUCT_UPSERT,
            idempotencyKey = "upsert-prod-$productId",
            payloadJson = json.encodeToString(payload.copy(id = productId))
        )

        Result.success(product)
    }

    fun updateStockLocally(productId: String, delta: Int) {
        dbHelper.updateProductStockLocal(productId, delta)
        _products.value = dbHelper.getAllProducts()
    }

    fun setStockLocally(productId: String, newQty: Int) {
        dbHelper.setProductStockDirect(productId, newQty)
        _products.value = dbHelper.getAllProducts()
    }
}
