package com.sitticun.orderapp.ui.screens.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitticun.orderapp.OrderApplication
import com.sitticun.orderapp.data.model.*
import com.sitticun.orderapp.data.repository.CustomerRepository
import com.sitticun.orderapp.data.repository.OrderRepository
import com.sitticun.orderapp.data.repository.PosDraftState
import com.sitticun.orderapp.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PosUiState(
    val searchQuery: String = "",
    val selectedCustomer: Customer? = null,
    val customerPointBalances: Map<Long, Long> = emptyMap(),
    val cartItems: List<CartItem> = emptyList(),
    val heldBillId: String? = null,
    val heldBillVersion: Long? = null,
    val isCheckingOut: Boolean = false,
    val checkoutSuccess: CloudSaleResult? = null,
    val errorMessage: String? = null,
    val showCustomerPicker: Boolean = false,
    val showPointsEditor: Boolean = false,
    val showFreeItemsEditor: Boolean = false,
    val showHeldBillsDialog: Boolean = false
) {
    val subtotal: Double
        get() = cartItems.sumOf { it.paidQty * it.product.price }

    val totalPayable: Double
        get() = subtotal

    val totalPaidQty: Int
        get() = cartItems.sumOf { it.paidQty }

    val totalFreeQty: Int
        get() = cartItems.sumOf { it.freeQty }

    val totalStockDeduction: Int
        get() = cartItems.sumOf { it.paidQty + it.freeQty }
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
        viewModelScope.launch {
            productRepository.loadProducts()
            customerRepository.loadCustomers()
            orderRepository.loadHeldBills()
            restoreDraft()
        }
    }

    private fun restoreDraft() {
        val draft = orderRepository.loadDraft() ?: return
        viewModelScope.launch {
            val allCustomers = customerRepository.customers.value
            val cust = allCustomers.find { it.id == draft.customerId }
            val allProducts = productRepository.products.value

            val restoredHeldBillVersion = draft.heldBillId?.let { id ->
                orderRepository.heldBills.value.firstOrNull { it.id == id }?.version
            }

            val restoredCart = allProducts.mapNotNull { p ->
                val paid = draft.paidQty[p.id.toString()] ?: 0
                val free = draft.freeQty[p.id.toString()] ?: 0
                val points = draft.pointsUsed[p.id.toString()] ?: 0
                if (paid > 0 || free > 0) {
                    CartItem(product = p, paidQty = paid, freeQty = free, pointsUsed = points)
                } else null
            }

            if (cust != null) {
                val balances = customerRepository.getCustomerPointBalances(cust.id).associate { it.productId to it.balance }
                _uiState.value = _uiState.value.copy(
                    selectedCustomer = cust,
                    customerPointBalances = balances,
                    cartItems = restoredCart,
                    heldBillId = draft.heldBillId,
                    heldBillVersion = restoredHeldBillVersion
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    cartItems = restoredCart,
                    heldBillId = draft.heldBillId
                )
            }
        }
    }

    private fun autoSaveDraft() {
        val state = _uiState.value
        val paidMap = state.cartItems.associate { it.product.id.toString() to it.paidQty }
        val freeMap = state.cartItems.associate { it.product.id.toString() to it.freeQty }
        val pointsMap = state.cartItems.associate { it.product.id.toString() to it.pointsUsed }

        orderRepository.saveDraft(
            PosDraftState(
                customerId = state.selectedCustomer?.id,
                heldBillId = state.heldBillId,
                paidQty = paidMap,
                freeQty = freeMap,
                pointsUsed = pointsMap
            )
        )
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    // --- CART ACTIONS ---
    fun addToCart(product: Product) {
        val current = _uiState.value.cartItems.toMutableList()
        val index = current.indexOfFirst { it.product.id == product.id }
        if (index >= 0) {
            val existing = current[index]
            current[index] = existing.copy(paidQty = existing.paidQty + 1)
        } else {
            current.add(CartItem(product = product, paidQty = 1, freeQty = 0, pointsUsed = 0))
        }
        _uiState.value = _uiState.value.copy(cartItems = current)
        autoSaveDraft()
    }

    fun updateItemPaidQty(productId: Long, delta: Int) {
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
            autoSaveDraft()
        }
    }

    fun updateItemFreeQty(productId: Long, delta: Int) {
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

    fun updatePointsAndFree(productId: Long, pointsUsed: Int, freeQty: Int) {
        val current = _uiState.value.cartItems.toMutableList()
        val index = current.indexOfFirst { it.product.id == productId }
        if (index >= 0) {
            val item = current[index]
            current[index] = item.copy(pointsUsed = pointsUsed, freeQty = freeQty)
            _uiState.value = _uiState.value.copy(cartItems = current)
            autoSaveDraft()
        }
    }

    fun removeItem(productId: Long) {
        val current = _uiState.value.cartItems.filterNot { it.product.id == productId }
        _uiState.value = _uiState.value.copy(cartItems = current)
        autoSaveDraft()
    }

    fun clearCart() {
        _uiState.value = _uiState.value.copy(
            cartItems = emptyList(),
            selectedCustomer = null,
            customerPointBalances = emptyMap(),
            heldBillId = null,
            heldBillVersion = null
        )
        orderRepository.clearDraft()
    }

    // --- CUSTOMER & POINTS ---
    fun selectCustomer(customer: Customer) {
        viewModelScope.launch {
            val balances = customerRepository.getCustomerPointBalances(customer.id).associate { it.productId to it.balance }
            _uiState.value = _uiState.value.copy(
                selectedCustomer = customer,
                customerPointBalances = balances,
                showCustomerPicker = false
            )
            autoSaveDraft()
        }
    }

    fun createCustomer(name: String) {
        viewModelScope.launch {
            val res = customerRepository.createCustomer(name)
            if (res.isSuccess) {
                selectCustomer(res.getOrThrow())
            }
        }
    }

    // --- HELD BILLS ---
    fun holdBill(note: String = "") {
        val state = _uiState.value
        val cust = state.selectedCustomer ?: return
        if (state.cartItems.isEmpty()) return

        viewModelScope.launch {
            val paidMap = state.cartItems.filter { it.paidQty > 0 }.associate { it.product.id to it.paidQty }
            val pointUses = state.cartItems.filter { it.pointsUsed > 0 }.map { PointUseInput(it.product.id, it.pointsUsed) }
            val freeItems = state.cartItems.filter { it.freeQty > 0 }.map { FreeItemInput(it.product.id, it.freeQty) }

            val result = orderRepository.holdBill(
                customer = cust,
                note = note,
                paidQty = paidMap,
                pointUses = pointUses,
                freeItems = freeItems,
                existingBillId = state.heldBillId,
                expectedVersion = state.heldBillVersion
                    ?: state.heldBillId?.let { id ->
                        orderRepository.heldBills.value.firstOrNull { it.id == id }?.version
                    }
                    ?: 0L
            )

            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message
                        ?: "บันทึกบิลพักไม่สำเร็จ กรุณาลองใหม่"
                )
                return@launch
            }

            clearCart()
        }
    }

    fun resumeHeldBill(bill: HeldBill) {
        viewModelScope.launch {
            val allCustomers = customerRepository.customers.value
            val cust = allCustomers.find { it.id == bill.customerId }
                ?: Customer(id = bill.customerId, name = bill.customerName)
            val allProducts = productRepository.products.value

            val restored = allProducts.mapNotNull { p ->
                val paid = bill.payload.paidQty[p.id.toString()] ?: 0
                val free = bill.payload.freeItems.find { it.productId == p.id }?.qty ?: 0
                val pts = bill.payload.pointUses.find { it.productId == p.id }?.points ?: 0
                if (paid > 0 || free > 0) {
                    CartItem(product = p, paidQty = paid, freeQty = free, pointsUsed = pts)
                } else null
            }

            val balances = customerRepository.getCustomerPointBalances(cust.id).associate { it.productId to it.balance }
            _uiState.value = _uiState.value.copy(
                selectedCustomer = cust,
                customerPointBalances = balances,
                cartItems = restored,
                heldBillId = bill.id,
                heldBillVersion = bill.version,
                showHeldBillsDialog = false
            )
            autoSaveDraft()
        }
    }

    fun deleteHeldBill(bill: HeldBill) {
        viewModelScope.launch {
            orderRepository.deleteHeldBill(bill.id, bill.version)
        }
    }

    // --- CONFIRM SALE ---
    fun confirmSale() {
        val state = _uiState.value
        val cust = state.selectedCustomer
        if (cust == null) {
            _uiState.value = state.copy(errorMessage = "กรุณาเลือกลูกค้าเข้าบิลก่อนคิดเงิน")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isCheckingOut = true, errorMessage = null)

            val result = orderRepository.commitSale(
                customer = cust,
                cartItems = state.cartItems,
                heldBillId = state.heldBillId
            )

            _uiState.value = _uiState.value.copy(isCheckingOut = false)

            if (result.isSuccess) {
                val resp = result.getOrThrow()
                _uiState.value = _uiState.value.copy(
                    checkoutSuccess = resp,
                    cartItems = emptyList(),
                    selectedCustomer = null,
                    customerPointBalances = emptyMap(),
                    heldBillId = null,
                    heldBillVersion = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "บันทึกการขายไม่สำเร็จ"
                )
            }
        }
    }

    fun dismissSuccessDialog() {
        _uiState.value = _uiState.value.copy(checkoutSuccess = null)
    }

    fun dismissErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // Dialog toggles
    fun toggleCustomerPicker(show: Boolean) { _uiState.value = _uiState.value.copy(showCustomerPicker = show) }
    fun togglePointsEditor(show: Boolean) { _uiState.value = _uiState.value.copy(showPointsEditor = show) }
    fun toggleFreeItemsEditor(show: Boolean) { _uiState.value = _uiState.value.copy(showFreeItemsEditor = show) }
    fun toggleHeldBillsDialog(show: Boolean) { _uiState.value = _uiState.value.copy(showHeldBillsDialog = show) }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PosViewModel() as T
        }
    }
}
