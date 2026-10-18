package com.sitticun.orderapp.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitticun.orderapp.OrderApplication
import com.sitticun.orderapp.data.model.OrderSummary
import com.sitticun.orderapp.data.model.VoidOrderResult
import com.sitticun.orderapp.data.repository.AuthRepository
import com.sitticun.orderapp.data.repository.OrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val searchQuery: String = "",
    val selectedStatus: String = "ทั้งหมด", // "ทั้งหมด", "completed", "voided"
    val selectedOrder: OrderSummary? = null,
    val showVoidDialog: Boolean = false,
    val isVoiding: Boolean = false,
    val isRefreshing: Boolean = false,
    val voidSuccessResponse: VoidOrderResult? = null,
    val errorMessage: String? = null
)

class HistoryViewModel(
    private val orderRepository: OrderRepository = OrderApplication.instance.orderRepository,
    private val authRepository: AuthRepository = OrderApplication.instance.authRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    val orders = orderRepository.orders
    val currentUser = authRepository.currentUser

    val isAdmin: Boolean
        get() = currentUser.value?.isAdmin == true

    init {
        loadOrders(forceRefresh = true)
    }

    fun loadOrders(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            orderRepository.loadOrders(forceRefresh = forceRefresh)
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun onStatusFilterChange(status: String) {
        _uiState.value = _uiState.value.copy(selectedStatus = status)
    }

    fun selectOrder(order: OrderSummary?) {
        _uiState.value = _uiState.value.copy(selectedOrder = order)
    }

    fun openVoidDialog(order: OrderSummary) {
        if (!isAdmin) {
            _uiState.value = _uiState.value.copy(errorMessage = "เฉพาะผู้ดูแลระบบ (Admin) เท่านั้นที่สามารถยกเลิกบิลได้")
            return
        }
        _uiState.value = _uiState.value.copy(showVoidDialog = true, selectedOrder = order)
    }

    fun dismissVoidDialog() {
        _uiState.value = _uiState.value.copy(showVoidDialog = false)
    }

    fun voidOrder() {
        val order = _uiState.value.selectedOrder ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isVoiding = true, errorMessage = null)
            val result = orderRepository.voidOrder(order.id)
            _uiState.value = _uiState.value.copy(isVoiding = false)

            if (result.isSuccess) {
                val resp = result.getOrThrow()
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

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel() as T
        }
    }
}
