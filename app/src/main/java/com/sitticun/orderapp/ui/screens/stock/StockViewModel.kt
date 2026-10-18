package com.sitticun.orderapp.ui.screens.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitticun.orderapp.OrderApplication
import com.sitticun.orderapp.data.model.ProductStockComparison
import com.sitticun.orderapp.data.model.StockSnapshot
import com.sitticun.orderapp.data.repository.OrderRepository
import com.sitticun.orderapp.data.repository.ProductRepository
import com.sitticun.orderapp.data.repository.StockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StockUiState(
    val comparisons: List<ProductStockComparison> = emptyList(),
    val inputQuantities: Map<Long, String> = emptyMap(),
    val note: String = "",
    val isSaving: Boolean = false,
    val isSendingLineNotify: Boolean = false,
    val lastSavedSnapshotId: String? = null,
    val message: String? = null,
    val errorMessage: String? = null
)

class StockViewModel(
    private val productRepository: ProductRepository = OrderApplication.instance.productRepository,
    private val stockRepository: StockRepository = OrderApplication.instance.stockRepository,
    private val orderRepository: OrderRepository = OrderApplication.instance.orderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StockUiState())
    val uiState: StateFlow<StockUiState> = _uiState.asStateFlow()

    val snapshots = stockRepository.snapshots

    init {
        loadData()
    }

    fun loadData(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            productRepository.loadProducts(forceRefresh = forceRefresh)
            orderRepository.loadOrders(forceRefresh = forceRefresh)
            stockRepository.loadSnapshots(forceRefresh = forceRefresh)
            refreshComparisons()
        }
    }

    private suspend fun refreshComparisons() {
        val parsedInputs = _uiState.value.inputQuantities.mapNotNull { (pid, str) ->
            str.toIntOrNull()?.let { pid to it }
        }.toMap()

        val list = stockRepository.getStockComparisons(parsedInputs)
        val initialInputs = _uiState.value.inputQuantities.toMutableMap()
        for (item in list) {
            if (!initialInputs.containsKey(item.product.id)) {
                initialInputs[item.product.id] = item.systemQty.toString()
            }
        }
        _uiState.value = _uiState.value.copy(
            comparisons = list,
            inputQuantities = initialInputs
        )
    }

    fun onQuantityChange(productId: Long, text: String) {
        val filtered = text.filter { it.isDigit() }
        val updated = _uiState.value.inputQuantities.toMutableMap()
        updated[productId] = filtered
        _uiState.value = _uiState.value.copy(inputQuantities = updated)

        viewModelScope.launch {
            refreshComparisons()
        }
    }

    fun onNoteChange(text: String) {
        _uiState.value = _uiState.value.copy(note = text)
    }

    fun saveSnapshot() {
        val state = _uiState.value
        val counts = mutableMapOf<Long, Int>()

        for (item in state.comparisons) {
            val text = state.inputQuantities[item.product.id] ?: ""
            val qty = text.toIntOrNull()
            if (qty == null || qty < 0) {
                _uiState.value = state.copy(errorMessage = "กรุณากรอกจำนวนของ ${item.product.name} ให้ถูกต้อง")
                return
            }
            counts[item.product.id] = qty
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, errorMessage = null)
            val result = stockRepository.createSnapshot(counts, state.note)
            _uiState.value = _uiState.value.copy(isSaving = false)

            if (result.isSuccess) {
                val saved = result.getOrThrow()
                _uiState.value = _uiState.value.copy(
                    lastSavedSnapshotId = saved.id,
                    message = "บันทึกสต็อกเรียบร้อยแล้ว (Snapshot: ${saved.id})",
                    note = ""
                )
                refreshComparisons()
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "บันทึกสต็อกไม่สำเร็จ"
                )
            }
        }
    }

    fun sendLineNotify(snapshotId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSendingLineNotify = true, errorMessage = null)
            val res = stockRepository.sendLineStockNotification(snapshotId)
            _uiState.value = _uiState.value.copy(isSendingLineNotify = false)

            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    message = "ส่งรายงานสต็อกเข้า LINE เรียบร้อยแล้ว"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "ส่งแจ้งเตือน LINE ไม่สำเร็จ: ${res.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, errorMessage = null)
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StockViewModel() as T
        }
    }
}
