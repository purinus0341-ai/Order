package com.sitticun.orderapp.data.repository

import com.sitticun.orderapp.data.api.SupabaseClient
import com.sitticun.orderapp.data.local.PreferenceManager
import com.sitticun.orderapp.data.model.LoginRequest
import com.sitticun.orderapp.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepository(
    private val supabaseClient: SupabaseClient,
    private val preferenceManager: PreferenceManager
) {
    private val _currentUser = MutableStateFlow<UserProfile?>(preferenceManager.userProfile)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    val isLoggedIn: Boolean
        get() = preferenceManager.isLoggedIn

    suspend fun login(email: String, pass: String): Result<UserProfile> {
        val res = supabaseClient.login(LoginRequest(email, pass))
        if (res.isSuccess) {
            val profile = res.getOrThrow()
            _currentUser.value = profile
        }
        return res
    }

    suspend fun verifyProfileActiveGuard(): Boolean {
        val user = preferenceManager.userProfile ?: return false
        val res = supabaseClient.fetchUserProfile(user.id)
        if (res.isSuccess) {
            val freshProfile = res.getOrThrow()
            preferenceManager.userProfile = freshProfile
            _currentUser.value = freshProfile
            if (!freshProfile.isActive) {
                logout()
                return false
            }
            return true
        }
        // If network error, check cached profile state
        return user.isActive
    }

    fun logout() {
        preferenceManager.clearSession()
        _currentUser.value = null
    }
}
