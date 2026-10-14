package com.sitticun.orderapp.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.sitticun.orderapp.data.local.PreferenceManager

class DeviceIdProvider(private val context: Context, private val preferenceManager: PreferenceManager) {

    @SuppressLint("HardwareIds")
    fun getDeviceId(): String {
        // Try getting android hardware ID first
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }

        if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            return "android-$androidId"
        }

        // Fallback to cached UUID in preferences
        return preferenceManager.deviceId
    }
}
