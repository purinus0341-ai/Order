package com.sitticun.orderapp.data.local

import android.content.Context
import android.content.SharedPreferences
import com.sitticun.orderapp.data.model.UserProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    var tokenExpiresAt: Long
        get() = prefs.getLong(KEY_TOKEN_EXPIRES_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_TOKEN_EXPIRES_AT, value).apply()

    var userProfile: UserProfile?
        get() {
            val raw = prefs.getString(KEY_USER_PROFILE, null) ?: return null
            return try {
                json.decodeFromString<UserProfile>(raw)
            } catch (e: Exception) {
                null
            }
        }
        set(value) {
            val raw = if (value != null) json.encodeToString(value) else null
            prefs.edit().putString(KEY_USER_PROFILE, raw).apply()
        }

    val isProfileActive: Boolean
        get() = userProfile?.isActive ?: false

    val isLoggedIn: Boolean
        get() = !accessToken.isNullOrBlank() && isProfileActive

    val deviceId: String
        get() {
            var id = prefs.getString(KEY_DEVICE_ID, null)
            if (id.isNullOrBlank()) {
                id = "android-" + UUID.randomUUID().toString()
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            return id
        }

    var draftCartJson: String?
        get() = prefs.getString(KEY_DRAFT_CART, null)
        set(value) = prefs.edit().putString(KEY_DRAFT_CART, value).apply()

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRES_AT)
            .remove(KEY_USER_PROFILE)
            .apply()
    }

    companion object {
        private const val PREF_NAME = "order_app_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
        private const val KEY_USER_PROFILE = "user_profile"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DRAFT_CART = "draft_cart"
    }
}
