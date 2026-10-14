package com.sitticun.orderapp.ui.screens.counter

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitticun.orderapp.OrderApplication
import com.sitticun.orderapp.data.model.CountMarker
import com.sitticun.orderapp.data.model.CountSession
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
    val selectedColorHex: String = "#5B8A72",
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

    init {
        viewModelScope.launch {
            val list = productRepository.loadProducts()
            if (_uiState.value.selectedProduct == null) {
                _uiState.value = _uiState.value.copy(selectedProduct = list.firstOrNull())
            }
        }
    }

    fun onImageSelected(uri: Uri?) {
        _uiState.value = _uiState.value.copy(selectedImageUri = uri, markers = emptyList())
    }

    fun onColorChange(colorHex: String) {
        _uiState.value = _uiState.value.copy(selectedColorHex = colorHex)
    }

    fun selectTargetProduct(product: Product) {
        _uiState.value = _uiState.value.copy(selectedProduct = product)
    }

    fun addMarker(xNormalized: Float, yNormalized: Float) {
        val current = _uiState.value.markers.toMutableList()
        val nextSeq = current.size + 1
        current.add(
            MarkerDot(
                id = "CM-" + UUID.randomUUID().toString(),
                productId = _uiState.value.selectedProduct?.id,
                xNormalized = xNormalized.coerceIn(0f, 1f),
                yNormalized = yNormalized.coerceIn(0f, 1f),
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
            _uiState.value = _uiState.value.copy(showSaveDialog = true)
        }
    }

    fun dismissSaveDialog() {
        _uiState.value = _uiState.value.copy(showSaveDialog = false)
    }

    fun saveSession(note: String = "") {
        val state = _uiState.value
        val sessionId = "CNT-" + UUID.randomUUID().toString()

        val countMarkers = state.markers.map { m ->
            CountMarker(
                id = m.id,
                sessionId = sessionId,
                productId = m.productId,
                xNormalized = m.xNormalized.toDouble(),
                yNormalized = m.yNormalized.toDouble()
            )
        }

        val session = CountSession(
            id = sessionId,
            note = note.trim().ifBlank { null },
            markers = countMarkers
        )

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            val result = stockRepository.saveCountSession(session)
            _uiState.value = _uiState.value.copy(isSaving = false)

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    showSaveDialog = false,
                    message = "บันทึกข้อมูลการนับ ${state.totalCount} จุด สำเร็จ (Session ID: $sessionId)"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "บันทึกไม่สำเร็จ"
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
