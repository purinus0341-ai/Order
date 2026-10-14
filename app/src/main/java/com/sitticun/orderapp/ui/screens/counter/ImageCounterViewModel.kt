package com.sitticun.orderapp.ui.screens.counter

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitticun.orderapp.OrderApplication
import com.sitticun.orderapp.data.model.MarkerDot
import com.sitticun.orderapp.data.model.Product
import com.sitticun.orderapp.data.repository.ProductRepository
import com.sitticun.orderapp.data.repository.StockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ImageCounterUiState(
    val selectedImageUri: Uri? = null,
    val markers: List<MarkerDot> = emptyList(),
    val selectedColorHex: String = "#5B8A72", // Default Pastel Mint
    val selectedProduct: Product? = null,
    val showSaveDialog: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
) {
    val totalCount: Int
        get() = markers.size
}

class ImageCounterViewModel(
    private val productRepository: ProductRepository = OrderApplication.instance.productRepository,
    private val stockRepository: StockRepository = OrderApplication.instance.stockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageCounterUiState())
    val uiState: StateFlow<ImageCounterUiState> = _uiState.asStateFlow()

    val products = productRepository.products

    fun onImageSelected(uri: Uri?) {
        _uiState.value = _uiState.value.copy(selectedImageUri = uri, markers = emptyList())
    }

    fun onColorChange(colorHex: String) {
        _uiState.value = _uiState.value.copy(selectedColorHex = colorHex)
    }

    fun addMarker(x: Float, y: Float) {
        val current = _uiState.value.markers.toMutableList()
        val nextSeq = current.size + 1
        current.add(
            MarkerDot(
                id = UUID.randomUUID().toString(),
                x = x,
                y = y,
                seq = nextSeq,
                colorHex = _uiState.value.selectedColorHex
            )
        )
        _uiState.value = _uiState.value.copy(markers = current)
    }

    fun undo() {
        val current = _uiState.value.markers
        if (current.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(markers = current.dropLast(1))
        }
    }

    fun clearAll() {
        _uiState.value = _uiState.value.copy(markers = emptyList())
    }

    fun openSaveDialog() {
        if (_uiState.value.markers.isNotEmpty()) {
            val defaultProd = products.value.firstOrNull()
            _uiState.value = _uiState.value.copy(showSaveDialog = true, selectedProduct = defaultProd)
        }
    }

    fun dismissSaveDialog() {
        _uiState.value = _uiState.value.copy(showSaveDialog = false)
    }

    fun selectTargetProduct(product: Product) {
        _uiState.value = _uiState.value.copy(selectedProduct = product)
    }

    fun saveCountToStock(isOverride: Boolean) {
        val prod = _uiState.value.selectedProduct ?: return
        val count = _uiState.value.totalCount

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            val result = stockRepository.saveImageCount(prod.id, count, isOverride)
            _uiState.value = _uiState.value.copy(isSaving = false)

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    showSaveDialog = false,
                    message = "บันทึกยอดนับ $count ชิ้น เข้าสต็อก '${prod.name}' เรียบร้อยแล้ว"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "บันทึกสต็อกไม่สำเร็จ"
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
            return ImageCounterViewModel() as T
        }
    }
}
