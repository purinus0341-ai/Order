package com.sitticun.orderapp.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitticun.orderapp.OrderApplication
import com.sitticun.orderapp.data.model.OrderRecord
import com.sitticun.orderapp.data.model.VoidSaleResponse
import com.sitticun.orderapp.data.repository.OrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val searchQuery: String = "",
    val selectedStatus: String = "ทั้งหมด", // "ทั้งหมด", "COMPLETED", "VOIDED"
    val selectedOrder: OrderRecord? = null,
    val showVoidDialog: Boolean = false,
    val isVoiding: Boolean = false,
    val voidSuccessResponse: VoidSaleResponse? = null,
    val errorMessage: String? = null
)

class HistoryViewModel(
    private val orderRepository: OrderRepository = OrderApplication.instance.orderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    val orders = orderRepository.orders

    init {
        loadOrders()
    }

    fun loadOrders() {
        viewModelScope.launch {
            orderRepository.loadOrders()
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun onStatusFilterChange(status: String) {
        _uiState.value = _uiState.value.copy(selectedStatus = status)
    }

    fun selectOrder(order: OrderRecord?) {
        _uiState.value = _uiState.value.copy(selectedOrder = order)
    }

    fun openVoidDialog(order: OrderRecord) {
        _uiState.value = _uiState.value.copy(showVoidDialog = true, selectedOrder = order)
    }

    fun dismissVoidDialog() {
        _uiState.value = _uiState.value.copy(showVoidDialog = false)
    }

    fun voidOrder(voidReason: String) {
        val order = _uiState.value.selectedOrder ?: return
        if (voidReason.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "จำเป็นต้องระบุเหตุผลในการยกเลิกบิล")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isVoiding = true, errorMessage = null)
            val result = orderRepository.voidSale(order.id, voidReason.trim())
            _uiState.value = _uiState.value.copy(isVoiding = false)

            if (result.isSuccess) {
                val resp = result.getOrThrow()
                // Update selected order in state
                val updated = orderRepository.orders.value.find { it.id == order.id }
                _uiState.value = _uiState.value.copy(
                    showVoidDialog = false,
                    selectedOrder = updated,
                    voidSuccessResponse = resp
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "ยกเลิกบิลไม่สำเร็จ"
                )
            }
        }
    }

    fun dismissSuccessDialog() {
        _uiState.value = _uiState.value.copy(voidSuccessResponse = null)
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel() as T
        }
    }
}
