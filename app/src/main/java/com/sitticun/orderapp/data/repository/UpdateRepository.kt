package com.sitticun.orderapp.data.repository

import com.sitticun.orderapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val name: String,
    val size: Long,
    @SerialName("browser_download_url") val downloadUrl: String
)

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val releaseNotes: String,
    val downloadUrl: String?
)

class UpdateRepository {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkUpdate(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/purinus0341-ai/Order/releases/latest")
                .header("User-Agent", "OrderApp-Android")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string().orEmpty()

            if (!resp.isSuccessful) {
                return@withContext Result.success(
                    UpdateInfo(
                        hasUpdate = false,
                        latestVersion = BuildConfig.APP_VERSION_NAME,
                        currentVersion = BuildConfig.APP_VERSION_NAME,
                        releaseNotes = "คุณกำลังใช้เวอร์ชันล่าสุดแล้ว",
                        downloadUrl = null
                    )
                )
            }

            val release = json.decodeFromString<GitHubRelease>(body)
            val latestTag = release.tagName.removePrefix("v").trim()
            val current = BuildConfig.APP_VERSION_NAME.trim()

            val apkAsset = release.assets.find { it.name.endsWith(".apk", ignoreCase = true) }
            val hasUpdate = isNewerVersion(latestTag, current)

            Result.success(
                UpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersion = release.tagName,
                    currentVersion = current,
                    releaseNotes = if (hasUpdate) (release.body ?: "ไม่มีรายละเอียดเพิ่มเติม") else "คุณกำลังใช้งานเวอร์ชันล่าสุดแล้ว",
                    downloadUrl = apkAsset?.downloadUrl
                )
            )
        } catch (e: Exception) {
            Result.success(
                UpdateInfo(
                    hasUpdate = false,
                    latestVersion = BuildConfig.APP_VERSION_NAME,
                    currentVersion = BuildConfig.APP_VERSION_NAME,
                    releaseNotes = "ไม่สามารถเชื่อมต่อเซิร์ฟเวอร์อัปเดตได้ (${e.localizedMessage})",
                    downloadUrl = null
                )
            )
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val cleanLatest = latest.removePrefix("v").trim()
        val cleanCurrent = current.removePrefix("v").trim()
        if (cleanLatest.isEmpty() || cleanCurrent.isEmpty()) return false
        if (cleanLatest.equals(cleanCurrent, ignoreCase = true)) return false

        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
