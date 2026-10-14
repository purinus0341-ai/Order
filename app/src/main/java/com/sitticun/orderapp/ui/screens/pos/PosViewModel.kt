package com.sitticun.orderapp.ui.screens.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitticun.orderapp.OrderApplication
import com.sitticun.orderapp.data.model.*
import com.sitticun.orderapp.data.repository.CustomerRepository
import com.sitticun.orderapp.data.repository.OrderRepository
import com.sitticun.orderapp.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PosUiState(
    val searchQuery: String = "",
    val selectedCategory: String = "ทั้งหมด",
    val selectedCustomer: Customer? = null,
    val cartItems: List<CartItem> = emptyList(),
    val redeemedPoints: Int = 0,
    val isCheckingOut: Boolean = false,
    val checkoutSuccessOrder: CommitSaleResponse? = null,
    val errorMessage: String? = null,
    val showCustomerPicker: Boolean = false,
    val showFreeItemsEditor: Boolean = false,
    val showPointsEditor: Boolean = false,
    val showHeldBillsDialog: Boolean = false,
    val showPaymentDialog: Boolean = false,
    val selectedPaymentMethod: String = "CASH"
) {
    val subtotal: Double
        get() = cartItems.sumOf { it.subtotal }

    val discountAmount: Double
        get() = redeemedPoints.toDouble()

    val totalPayable: Double
        get() = (subtotal - discountAmount).coerceAtLeast(0.0)

    val earnedPoints: Int
        get() = (totalPayable / 20.0).toInt()

    val totalPaidQty: Int
        get() = cartItems.sumOf { it.paidQty }

    val totalFreeQty: Int
        get() = cartItems.sumOf { it.freeQty }

    val totalStockDeduction: Int
        get() = cartItems.sumOf { it.totalQty }
}

class PosViewModel(
    private val productRepository: ProductRepository = OrderApplication.instance.productRepository,
    private val customerRepository: CustomerRepository = OrderApplication.instance.customerRepository,
    private val orderRepository: OrderRepository = OrderApplication.instance.orderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PosUiState())
    val uiState: StateFlow<PosUiState> = _uiState.asStateFlow()

    val products = productRepository.products
    val customers = customerRepository.customers
    val heldBills = orderRepository.heldBills

    init {
        // Load initial products & customers
        viewModelScope.launch {
            productRepository.loadProducts()
            customerRepository.loadCustomers()
            restoreDraftCart()
        }
    }

    private fun restoreDraftCart() {
        val draft = orderRepository.loadDraftCart() ?: return
        _uiState.value = _uiState.value.copy(
            cartItems = draft.items,
            selectedCustomer = draft.customer,
            redeemedPoints = draft.redeemedPoints
        )
    }

    private fun autoSaveDraft() {
        val current = _uiState.value
        orderRepository.saveDraftCart(
            items = current.cartItems,
            customer = current.selectedCustomer,
            redeemedPoints = current.redeemedPoints
        )
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun onCategorySelect(category: String) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    // --- CART ITEM ACTIONS ---

    fun addToCart(product: Product) {
        val current = _uiState.value.cartItems.toMutableList()
        val index = current.indexOfFirst { it.product.id == product.id }
        if (index >= 0) {
            val existing = current[index]
            current[index] = existing.copy(paidQty = existing.paidQty + 1)
        } else {
            current.add(CartItem(product = product, paidQty = 1, freeQty = 0))
        }
        _uiState.value = _uiState.value.copy(cartItems = current)
        validatePointsCap()
        autoSaveDraft()
    }

    fun updateItemPaidQty(productId: String, delta: Int) {
        val current = _uiState.value.cartItems.toMutableList()
        val index = current.indexOfFirst { it.product.id == productId }
        if (index >= 0) {
            val item = current[index]
            val newPaid = item.paidQty + delta
            if (newPaid <= 0 && item.freeQty <= 0) {
                current.removeAt(index)
            } else {
                current[index] = item.copy(paidQty = newPaid.coerceAtLeast(0))
            }
            _uiState.value = _uiState.value.copy(cartItems = current)
            validatePointsCap()
            autoSaveDraft()
        }
    }

    fun updateItemFreeQty(productId: String, delta: Int) {
        val current = _uiState.value.cartItems.toMutableList()
        val index = current.indexOfFirst { it.product.id == productId }
        if (index >= 0) {
            val item = current[index]
            val newFree = (item.freeQty + delta).coerceAtLeast(0)
            if (item.paidQty <= 0 && newFree <= 0) {
                current.removeAt(index)
            } else {
                current[index] = item.copy(freeQty = newFree)
            }
            _uiState.value = _uiState.value.copy(cartItems = current)
            autoSaveDraft()
        }
    }

    fun removeItem(productId: String) {
        val current = _uiState.value.cartItems.filterNot { it.product.id == productId }
        _uiState.value = _uiState.value.copy(cartItems = current)
        validatePointsCap()
        autoSaveDraft()
    }

    fun clearCart() {
        _uiState.value = _uiState.value.copy(
            cartItems = emptyList(),
            selectedCustomer = null,
            redeemedPoints = 0
        )
        orderRepository.clearDraftCart()
    }

    // --- CUSTOMER & POINTS ---

    fun selectCustomer(customer: Customer?) {
        _uiState.value = _uiState.value.copy(
            selectedCustomer = customer,
            showCustomerPicker = false,
            redeemedPoints = 0 // Reset points on customer change
        )
        autoSaveDraft()
    }

    fun setRedeemedPoints(points: Int) {
        val maxPoints = (_uiState.value.selectedCustomer?.pointsBalance ?: 0)
        val maxDiscount = _uiState.value.subtotal.toInt()
        val allowed = points.coerceIn(0, minOf(maxPoints, maxDiscount))
        _uiState.value = _uiState.value.copy(redeemedPoints = allowed, showPointsEditor = false)
        autoSaveDraft()
    }

    private fun validatePointsCap() {
        val currentPoints = _uiState.value.redeemedPoints
        val maxAllowed = minOf(
            _uiState.value.selectedCustomer?.pointsBalance ?: 0,
            _uiState.value.subtotal.toInt()
        )
        if (currentPoints > maxAllowed) {
            _uiState.value = _uiState.value.copy(redeemedPoints = maxAllowed.coerceAtLeast(0))
        }
    }

    // --- HELD BILLS ---

    fun holdBill(titleNote: String = "") {
        val state = _uiState.value
        if (state.cartItems.isEmpty()) return

        orderRepository.holdCurrentBill(
            title = titleNote,
            customer = state.selectedCustomer,
            items = state.cartItems,
            redeemedPoints = state.redeemedPoints
        )

        clearCart()
    }

    fun resumeHeldBill(bill: HeldBill) {
        _uiState.value = _uiState.value.copy(
            cartItems = bill.items,
            selectedCustomer = bill.customer,
            redeemedPoints = bill.redeemedPoints,
            showHeldBillsDialog = false
        )
        orderRepository.deleteHeldBill(bill.id)
        autoSaveDraft()
    }

    fun deleteHeldBill(billId: String) {
        orderRepository.deleteHeldBill(billId)
    }

    // --- CHECKOUT ---

    fun openPaymentDialog() {
        if (_uiState.value.cartItems.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(showPaymentDialog = true)
        }
    }

    fun setPaymentMethod(method: String) {
        _uiState.value = _uiState.value.copy(selectedPaymentMethod = method)
    }

    fun confirmSale() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(isCheckingOut = true, errorMessage = null)

            val result = orderRepository.commitSale(
                customer = state.selectedCustomer,
                cartItems = state.cartItems,
                redeemedPoints = state.redeemedPoints,
                paymentMethod = state.selectedPaymentMethod
            )

            _uiState.value = _uiState.value.copy(isCheckingOut = false)

            if (result.isSuccess) {
                val resp = result.getOrThrow()
                _uiState.value = _uiState.value.copy(
                    checkoutSuccessOrder = resp,
                    showPaymentDialog = false,
                    cartItems = emptyList(),
                    selectedCustomer = null,
                    redeemedPoints = 0
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "ชำระเงินไม่สำเร็จ"
                )
            }
        }
    }

    fun dismissSuccessDialog() {
        _uiState.value = _uiState.value.copy(checkoutSuccessOrder = null)
    }

    // --- DIALOG TOGGLES ---
    fun toggleCustomerPicker(show: Boolean) { _uiState.value = _uiState.value.copy(showCustomerPicker = show) }
    fun toggleFreeItemsEditor(show: Boolean) { _uiState.value = _uiState.value.copy(showFreeItemsEditor = show) }
    fun togglePointsEditor(show: Boolean) { _uiState.value = _uiState.value.copy(showPointsEditor = show) }
    fun toggleHeldBillsDialog(show: Boolean) { _uiState.value = _uiState.value.copy(showHeldBillsDialog = show) }
    fun dismissPaymentDialog() { _uiState.value = _uiState.value.copy(showPaymentDialog = false) }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PosViewModel() as T
        }
    }
}
