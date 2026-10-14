package com.sitticun.orderapp.ui.screens.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitticun.orderapp.OrderApplication
import com.sitticun.orderapp.data.model.Customer
import com.sitticun.orderapp.data.model.CustomerUpsertPayload
import com.sitticun.orderapp.data.model.OrderRecord
import com.sitticun.orderapp.data.repository.CustomerRepository
import com.sitticun.orderapp.data.repository.OrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CustomersUiState(
    val searchQuery: String = "",
    val selectedCustomer: Customer? = null,
    val showUpsertDialog: Boolean = false,
    val customerToEdit: Customer? = null,
    val showPointsDialog: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
    val customerOrders: List<OrderRecord> = emptyList()
)

class CustomersViewModel(
    private val customerRepository: CustomerRepository = OrderApplication.instance.customerRepository,
    private val orderRepository: OrderRepository = OrderApplication.instance.orderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomersUiState())
    val uiState: StateFlow<CustomersUiState> = _uiState.asStateFlow()

    val customers = customerRepository.customers

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            customerRepository.loadCustomers()
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun selectCustomer(customer: Customer?) {
        val orders = if (customer != null) {
            orderRepository.orders.value.filter { it.customerId == customer.id }
        } else emptyList()
        _uiState.value = _uiState.value.copy(selectedCustomer = customer, customerOrders = orders)
    }

    fun openAddCustomer() {
        _uiState.value = _uiState.value.copy(showUpsertDialog = true, customerToEdit = null)
    }

    fun openEditCustomer(customer: Customer) {
        _uiState.value = _uiState.value.copy(showUpsertDialog = true, customerToEdit = customer)
    }

    fun dismissUpsertDialog() {
        _uiState.value = _uiState.value.copy(showUpsertDialog = false, customerToEdit = null)
    }

    fun saveCustomer(name: String, phone: String, email: String?, notes: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            val currentEdit = _uiState.value.customerToEdit

            val payload = CustomerUpsertPayload(
                id = currentEdit?.id,
                name = name.trim(),
                phone = phone.trim(),
                email = email?.trim()?.ifBlank { null },
                pointsBalance = currentEdit?.pointsBalance ?: 0,
                notes = notes?.trim()?.ifBlank { null }
            )

            val result = customerRepository.saveCustomer(payload)
            _uiState.value = _uiState.value.copy(isSaving = false)

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    showUpsertDialog = false,
                    customerToEdit = null,
                    message = "บันทึกข้อมูลลูกค้าสำเร็จ"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "บันทึกข้อมูลลูกค้าไม่สำเร็จ"
                )
            }
        }
    }

    fun openAdjustPoints(customer: Customer) {
        _uiState.value = _uiState.value.copy(showPointsDialog = true, selectedCustomer = customer)
    }

    fun dismissPointsDialog() {
        _uiState.value = _uiState.value.copy(showPointsDialog = false)
    }

    fun adjustPoints(pointsChange: Int, reason: String) {
        val cust = _uiState.value.selectedCustomer ?: return
        if (reason.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "จำเป็นต้องระบุเหตุผลในการปรับปรุงแต้ม")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            val result = customerRepository.adjustPoints(cust.id, pointsChange, reason.trim())
            _uiState.value = _uiState.value.copy(isSaving = false)

            if (result.isSuccess) {
                // Refresh local selected customer
                val updated = customerRepository.customers.value.find { it.id == cust.id }
                _uiState.value = _uiState.value.copy(
                    selectedCustomer = updated,
                    showPointsDialog = false,
                    message = "ปรับยอดแต้มสำเร็จ"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "ปรับแต้มไม่สำเร็จ"
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
            return CustomersViewModel() as T
        }
    }
}
