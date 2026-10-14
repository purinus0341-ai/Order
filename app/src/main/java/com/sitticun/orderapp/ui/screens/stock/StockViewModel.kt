package com.sitticun.orderapp.ui.screens.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitticun.orderapp.OrderApplication
import com.sitticun.orderapp.data.model.Product
import com.sitticun.orderapp.data.model.StockSnapshot
import com.sitticun.orderapp.data.repository.ProductRepository
import com.sitticun.orderapp.data.repository.StockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StockUiState(
    val searchQuery: String = "",
    val filterMode: String = "ทั้งหมด", // "ทั้งหมด", "สินค้าใกล้หมด", "สินค้าหมด"
    val showAdjustDialog: Boolean = false,
    val selectedProduct: Product? = null,
    val isAdjusting: Boolean = false,
    val isSendingLineNotify: Boolean = false,
    val lineNotifyResult: String? = null,
    val message: String? = null,
    val errorMessage: String? = null
)

class StockViewModel(
    private val productRepository: ProductRepository = OrderApplication.instance.productRepository,
    private val stockRepository: StockRepository = OrderApplication.instance.stockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StockUiState())
    val uiState: StateFlow<StockUiState> = _uiState.asStateFlow()

    val products = productRepository.products
    val snapshots = stockRepository.snapshots

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            productRepository.loadProducts()
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun onFilterModeChange(mode: String) {
        _uiState.value = _uiState.value.copy(filterMode = mode)
    }

    fun openAdjustDialog(product: Product) {
        _uiState.value = _uiState.value.copy(showAdjustDialog = true, selectedProduct = product)
    }

    fun dismissAdjustDialog() {
        _uiState.value = _uiState.value.copy(showAdjustDialog = false, selectedProduct = null)
    }

    fun adjustStock(newQty: Int, reason: String) {
        val prod = _uiState.value.selectedProduct ?: return
        if (reason.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "จำเป็นต้องระบุเหตุผลในการปรับสต็อก")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAdjusting = true, errorMessage = null)
            val result = stockRepository.adjustStock(prod.id, newQty, reason.trim())
            _uiState.value = _uiState.value.copy(isAdjusting = false)

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    showAdjustDialog = false,
                    selectedProduct = null,
                    message = "บันทึกการปรับยอดสต็อกสำเร็จ (บันทึก Snapshot เรียบร้อย)"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "ปรับสต็อกไม่สำเร็จ"
                )
            }
        }
    }

    fun triggerLineNotify() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSendingLineNotify = true, errorMessage = null)
            val result = stockRepository.sendLineStockNotification(lowStockOnly = true)
            _uiState.value = _uiState.value.copy(isSendingLineNotify = false)

            if (result.isSuccess) {
                val resp = result.getOrThrow()
                _uiState.value = _uiState.value.copy(
                    lineNotifyResult = "แจ้งเตือนผ่าน LINE เรียบร้อย: ${resp.message}"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    lineNotifyResult = "ส่งแจ้งเตือน LINE ไม่สำเร็จ: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun dismissLineDialog() {
        _uiState.value = _uiState.value.copy(lineNotifyResult = null)
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
