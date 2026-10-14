package com.sitticun.orderapp.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitticun.orderapp.OrderApplication
import com.sitticun.orderapp.data.model.UserProfile
import com.sitticun.orderapp.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isInactiveBlocked: Boolean = false,
    val userProfile: UserProfile? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository = OrderApplication.instance.authRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(
            email = authRepository.rememberedEmail.orEmpty(),
            userProfile = OrderApplication.instance.preferenceManager.userProfile
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email, errorMessage = null)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = null)
    }

    fun login(onSuccess: () -> Unit) {
        val current = _uiState.value
        if (current.email.isBlank() || current.password.isBlank()) {
            _uiState.value = current.copy(errorMessage = "กรุณากรอกอีเมลและรหัสผ่าน")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, isInactiveBlocked = false)
            val result = authRepository.login(current.email.trim(), current.password)
            _uiState.value = _uiState.value.copy(isLoading = false)

            if (result.isSuccess) {
                val profile = result.getOrThrow()
                // Profile Active Guard check
                if (!profile.isActive) {
                    _uiState.value = _uiState.value.copy(
                        isInactiveBlocked = true,
                        errorMessage = "บัญชีผู้ใช้นี้ถูกระงับการใช้งาน กรุณาติดต่อผู้ดูแลระบบ"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(userProfile = profile)
                    onSuccess()
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "เข้าสู่ระบบไม่สำเร็จ"
                if (err.contains("ระงับ", ignoreCase = true)) {
                    _uiState.value = _uiState.value.copy(isInactiveBlocked = true, errorMessage = err)
                } else {
                    _uiState.value = _uiState.value.copy(errorMessage = err)
                }
            }
        }
    }

    fun demoLogin(onSuccess: () -> Unit) {
        // Quick cashier demo login for testing & offline mode
        val demoProfile = UserProfile(
            userId = "demo-staff-001",
            role = "staff",
            displayName = "พนักงานประจำร้าน",
            isActive = true
        )
        OrderApplication.instance.preferenceManager.userProfile = demoProfile
        _uiState.value = _uiState.value.copy(userProfile = demoProfile)
        onSuccess()
    }

    fun dismissInactiveDialog() {
        _uiState.value = _uiState.value.copy(isInactiveBlocked = false)
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel() as T
        }
    }
}
