package com.sitticun.orderapp.ui.screens.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitticun.orderapp.OrderApplication
import com.sitticun.orderapp.data.model.Customer
import com.sitticun.orderapp.data.model.OrderSummary
import com.sitticun.orderapp.data.model.PointBalance
import com.sitticun.orderapp.data.model.Product
import com.sitticun.orderapp.data.repository.CustomerRepository
import com.sitticun.orderapp.data.repository.OrderRepository
import com.sitticun.orderapp.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CustomersUiState(
    val searchQuery: String = "",
    val selectedCustomer: Customer? = null,
    val showAddDialog: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
    val pointBalances: List<PointBalance> = emptyList(),
    val customerOrders: List<OrderSummary> = emptyList()
)

class CustomersViewModel(
    private val customerRepository: CustomerRepository = OrderApplication.instance.customerRepository,
    private val orderRepository: OrderRepository = OrderApplication.instance.orderRepository,
    private val productRepository: ProductRepository = OrderApplication.instance.productRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomersUiState())
    val uiState: StateFlow<CustomersUiState> = _uiState.asStateFlow()

    val customers = customerRepository.customers
    val products = productRepository.products

    init {
        loadData()
        viewModelScope.launch {
            orderRepository.orders.collect { allOrders ->
                val cust = _uiState.value.selectedCustomer
                if (cust != null) {
                    val orders = allOrders.filter { it.customerId == cust.id }
                    _uiState.value = _uiState.value.copy(customerOrders = orders)
                }
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            launch { customerRepository.loadCustomers() }
            launch { productRepository.loadProducts() }
            launch { orderRepository.loadOrders() }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun selectCustomer(customer: Customer?) {
        val orders = if (customer != null) {
            orderRepository.orders.value.filter { it.customerId == customer.id }
        } else emptyList()

        _uiState.value = _uiState.value.copy(
            selectedCustomer = customer,
            customerOrders = orders,
            pointBalances = emptyList()
        )

        if (customer != null) {
            viewModelScope.launch {
                val balances = customerRepository.getCustomerPointBalances(customer.id)
                _uiState.value = _uiState.value.copy(pointBalances = balances)
            }
        }
    }

    fun openAddCustomer() {
        _uiState.value = _uiState.value.copy(showAddDialog = true)
    }

    fun dismissAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    fun createCustomer(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "กรุณากรอกชื่อลูกค้า")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            val result = customerRepository.createCustomer(trimmed)
            _uiState.value = _uiState.value.copy(isSaving = false)

            if (result.isSuccess) {
                val created = result.getOrThrow()
                _uiState.value = _uiState.value.copy(
                    showAddDialog = false,
                    message = "เพิ่มลูกค้า '${created.name}' สำเร็จ"
                )
                selectCustomer(created)
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "เพิ่มลูกค้าไม่สำเร็จ"
                )
            }
        }
    }

    fun getProductName(productId: Long): String {
        return products.value.find { it.id == productId }?.name ?: "สินค้า #$productId"
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
