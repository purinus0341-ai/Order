package com.sitticun.orderapp.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitticun.orderapp.OrderApplication
import com.sitticun.orderapp.data.api.SupabaseClient
import com.sitticun.orderapp.data.model.Product
import com.sitticun.orderapp.data.model.ProductUpsertPayload
import com.sitticun.orderapp.data.model.UserProfile
import com.sitticun.orderapp.data.repository.OutboxRepository
import com.sitticun.orderapp.data.repository.ProductRepository
import com.sitticun.orderapp.data.repository.UpdateInfo
import com.sitticun.orderapp.data.repository.UpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminUiState(
    val selectedTab: Int = 0, // 0 = Sync Diagnostics, 1 = Product Admin, 2 = User Admin, 3 = App Version
    val usersList: List<UserProfile> = emptyList(),
    val isLoadingUsers: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val isCheckingUpdate: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
    val productToEdit: Product? = null,
    val showProductDialog: Boolean = false,
    val isSavingProduct: Boolean = false
)

class AdminViewModel(
    private val outboxRepository: OutboxRepository = OrderApplication.instance.outboxRepository,
    private val productRepository: ProductRepository = OrderApplication.instance.productRepository,
    private val supabaseClient: SupabaseClient = OrderApplication.instance.supabaseClient,
    private val updateRepository: UpdateRepository = OrderApplication.instance.updateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    val outboxItems = outboxRepository.outboxItems
    val isSyncing = outboxRepository.isSyncing
    val products = productRepository.products

    init {
        loadUsers()
        checkAppUpdate()
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
    }

    // --- OUTBOX DIAGNOSTICS ---

    fun forceSync() {
        viewModelScope.launch {
            val count = outboxRepository.syncPendingItems()
            _uiState.value = _uiState.value.copy(
                message = if (count > 0) "ซิงค์ข้อมูลสำเร็จ $count รายการ" else "ไม่มีรายการที่ต้องซิงค์ หรือระบบยังออฟไลน์"
            )
        }
    }

    fun clearCompletedOutbox() {
        outboxRepository.clearCompleted()
        _uiState.value = _uiState.value.copy(message = "ล้างประวัติรายการที่ซิงค์สำเร็จแล้ว")
    }

    fun resetOutbox() {
        outboxRepository.clearAll()
        _uiState.value = _uiState.value.copy(message = "ล้างคิวงานออฟไลน์ทั้งหมดเรียบร้อย")
    }

    // --- USER ADMIN ---

    fun loadUsers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingUsers = true)
            val result = supabaseClient.getAllUsers()
            _uiState.value = _uiState.value.copy(isLoadingUsers = false)

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(usersList = result.getOrThrow())
            } else {
                // Fallback to local user
                val localUser = OrderApplication.instance.preferenceManager.userProfile
                if (localUser != null) {
                    _uiState.value = _uiState.value.copy(usersList = listOf(localUser))
                }
            }
        }
    }

    fun toggleUserActive(user: UserProfile) {
        val newStatus = !user.isActive
        viewModelScope.launch {
            val res = supabaseClient.updateUserActiveStatus(user.id, newStatus)
            if (res.isSuccess) {
                val updated = _uiState.value.usersList.map {
                    if (it.id == user.id) it.copy(isActive = newStatus) else it
                }
                _uiState.value = _uiState.value.copy(
                    usersList = updated,
                    message = "อัปเดตสถานะผู้ใช้ ${user.fullName ?: user.email} เป็น ${if (newStatus) "เปิดใช้งาน" else "ระงับสิทธิ์"} แล้ว"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = res.exceptionOrNull()?.message ?: "อัปเดตสถานะผู้ใช้ไม่สำเร็จ"
                )
            }
        }
    }

    // --- PRODUCT ADMIN ---

    fun openAddProduct() {
        _uiState.value = _uiState.value.copy(showProductDialog = true, productToEdit = null)
    }

    fun openEditProduct(product: Product) {
        _uiState.value = _uiState.value.copy(showProductDialog = true, productToEdit = product)
    }

    fun dismissProductDialog() {
        _uiState.value = _uiState.value.copy(showProductDialog = false, productToEdit = null)
    }

    fun saveProduct(payload: ProductUpsertPayload) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingProduct = true, errorMessage = null)
            val result = productRepository.saveProduct(payload)
            _uiState.value = _uiState.value.copy(isSavingProduct = false)

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    showProductDialog = false,
                    productToEdit = null,
                    message = "บันทึกข้อมูลสินค้าเรียบร้อยแล้ว"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "บันทึกสินค้าไม่สำเร็จ"
                )
            }
        }
    }

    fun toggleProductActive(product: Product) {
        val payload = ProductUpsertPayload(
            id = product.id,
            name = product.name,
            sku = product.sku,
            barcode = product.barcode,
            category = product.category,
            price = product.price,
            cost = product.cost,
            stockQty = product.stockQty,
            minStockAlert = product.minStockAlert,
            imageUrl = product.imageUrl,
            isActive = !product.isActive
        )
        saveProduct(payload)
    }

    // --- APP UPDATE CHECKER ---

    fun checkAppUpdate() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingUpdate = true)
            val res = updateRepository.checkUpdate()
            _uiState.value = _uiState.value.copy(isCheckingUpdate = false)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(updateInfo = res.getOrThrow())
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, errorMessage = null)
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AdminViewModel() as T
        }
    }
}
