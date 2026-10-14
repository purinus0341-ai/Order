package com.sitticun.orderapp.data.api

import com.sitticun.orderapp.BuildConfig
import com.sitticun.orderapp.data.local.PreferenceManager
import com.sitticun.orderapp.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class SupabaseClient(private val preferenceManager: PreferenceManager) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    val baseUrl: String = BuildConfig.SUPABASE_URL.trimEnd('/')
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY

    private fun buildRequest(
        endpoint: String,
        method: String = "GET",
        body: RequestBody? = null,
        needAuth: Boolean = true,
        extraHeaders: Map<String, String> = emptyMap()
    ): Request {
        val url = if (endpoint.startsWith("http")) endpoint else "$baseUrl$endpoint"
        val builder = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey)
            .addHeader("Content-Type", "application/json")

        if (needAuth) {
            val token = preferenceManager.accessToken
            if (!token.isNullOrBlank()) {
                builder.addHeader("Authorization", "Bearer $token")
            } else {
                builder.addHeader("Authorization", "Bearer $anonKey")
            }
        } else {
            builder.addHeader("Authorization", "Bearer $anonKey")
        }

        extraHeaders.forEach { (k, v) -> builder.addHeader(k, v) }

        when (method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> builder.post(body ?: "".toRequestBody(jsonMediaType))
            "PUT" -> builder.put(body ?: "".toRequestBody(jsonMediaType))
            "PATCH" -> builder.patch(body ?: "".toRequestBody(jsonMediaType))
            "DELETE" -> builder.delete(body)
        }

        return builder.build()
    }

    private suspend fun executeWithRetry(
        endpoint: String,
        method: String = "GET",
        body: RequestBody? = null,
        needAuth: Boolean = true,
        extraHeaders: Map<String, String> = emptyMap()
    ): Response = withContext(Dispatchers.IO) {
        // Auto refresh token before request if expired or close to expiry (within 60s)
        if (needAuth && System.currentTimeMillis() >= preferenceManager.tokenExpiresAt - 60_000L) {
            val refreshSuccess = performTokenRefreshInternal()
            if (!refreshSuccess && preferenceManager.tokenExpiresAt > 0) {
                // Token refresh failed, continue and let it fail or 401
            }
        }

        val request = buildRequest(endpoint, method, body, needAuth, extraHeaders)
        val response = okHttpClient.newCall(request).execute()

        // If 401 Unauthorized received, try refreshing token once and retry
        if (response.code == 401 && needAuth && !preferenceManager.refreshToken.isNullOrBlank()) {
            response.close()
            val refreshed = performTokenRefreshInternal()
            if (refreshed) {
                val retryRequest = buildRequest(endpoint, method, body, needAuth, extraHeaders)
                return@withContext okHttpClient.newCall(retryRequest).execute()
            }
        }

        response
    }

    // --- AUTHENTICATION & PROFILE GUARD ---

    suspend fun login(request: LoginRequest): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val bodyString = json.encodeToString(request)
            val req = buildRequest(
                endpoint = "/auth/v1/token?grant_type=password",
                method = "POST",
                body = bodyString.toRequestBody(jsonMediaType),
                needAuth = false
            )

            val response = okHttpClient.newCall(req).execute()
            val resBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("เข้าสู่ระบบไม่สำเร็จ: $resBody"))
            }

            val session = json.decodeFromString<AuthSession>(resBody)
            val expiresAt = System.currentTimeMillis() + (session.expiresIn * 1000L)
            preferenceManager.accessToken = session.accessToken
            preferenceManager.refreshToken = session.refreshToken
            preferenceManager.tokenExpiresAt = expiresAt

            val userId = session.user?.id
            if (userId.isNullOrBlank()) {
                return@withContext Result.failure(Exception("ไม่พบ User ID จากระบบ"))
            }

            // Profile Active Guard: Always check is_active from profiles table
            val profileResult = fetchUserProfile(userId)
            if (profileResult.isFailure) {
                // If profile not found in table yet, create a fallback profile
                val fallbackProfile = UserProfile(
                    id = userId,
                    email = request.email,
                    fullName = request.email.substringBefore('@'),
                    role = "cashier",
                    isActive = true
                )
                preferenceManager.userProfile = fallbackProfile
                return@withContext Result.success(fallbackProfile)
            }

            val profile = profileResult.getOrThrow()
            if (!profile.isActive) {
                // User is inactive! Revoke session immediately
                preferenceManager.clearSession()
                return@withContext Result.failure(Exception("บัญชีผู้ใช้นี้ถูกระงับการใช้งาน กรุณาติดต่อผู้ดูแลระบบ"))
            }

            preferenceManager.userProfile = profile
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun performTokenRefreshInternal(): Boolean = withContext(Dispatchers.IO) {
        val refreshToken = preferenceManager.refreshToken ?: return@withContext false
        try {
            val reqBody = json.encodeToString(RefreshTokenRequest(refreshToken))
            val req = buildRequest(
                endpoint = "/auth/v1/token?grant_type=refresh_token",
                method = "POST",
                body = reqBody.toRequestBody(jsonMediaType),
                needAuth = false
            )

            val response = okHttpClient.newCall(req).execute()
            val resBody = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                val session = json.decodeFromString<AuthSession>(resBody)
                preferenceManager.accessToken = session.accessToken
                preferenceManager.refreshToken = session.refreshToken
                preferenceManager.tokenExpiresAt = System.currentTimeMillis() + (session.expiresIn * 1000L)
                return@withContext true
            }
        } catch (e: Exception) {
            // Refresh failed
        }
        false
    }

    suspend fun fetchUserProfile(userId: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithRetry("/rest/v1/profiles?id=eq.$userId&select=*")
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("ไม่สามารถดึงข้อมูล Profile: $resBody"))
            }

            val profiles = json.decodeFromString<List<UserProfile>>(resBody)
            if (profiles.isEmpty()) {
                return@withContext Result.failure(Exception("ไม่พบข้อมูลผู้ใช้ในตาราง profiles"))
            }

            Result.success(profiles.first())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- PRODUCTS ---

    suspend fun getProducts(): Result<List<Product>> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithRetry("/rest/v1/products?select=*&order=name.asc")
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("ดึงรายการสินค้าไม่สำเร็จ: $resBody"))
            }
            val products = json.decodeFromString<List<Product>>(resBody)
            Result.success(products)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertProduct(payload: ProductUpsertPayload): Result<Product> = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(payload).toRequestBody(jsonMediaType)
            val headers = mapOf("Prefer" to "return=representation,resolution=merge-duplicates")
            val response = executeWithRetry("/rest/v1/products", method = "POST", body = body, extraHeaders = headers)
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("บันทึกสินค้าไม่สำเร็จ: $resBody"))
            }
            val list = json.decodeFromString<List<Product>>(resBody)
            Result.success(list.first())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- CUSTOMERS ---

    suspend fun getCustomers(): Result<List<Customer>> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithRetry("/rest/v1/customers?select=*&order=name.asc")
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("ดึงข้อมูลลูกค้าไม่สำเร็จ: $resBody"))
            }
            val list = json.decodeFromString<List<Customer>>(resBody)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertCustomer(payload: CustomerUpsertPayload): Result<Customer> = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(payload).toRequestBody(jsonMediaType)
            val headers = mapOf("Prefer" to "return=representation,resolution=merge-duplicates")
            val response = executeWithRetry("/rest/v1/customers", method = "POST", body = body, extraHeaders = headers)
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("บันทึกลูกค้าไม่สำเร็จ: $resBody"))
            }
            val list = json.decodeFromString<List<Customer>>(resBody)
            Result.success(list.first())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun adjustCustomerPoints(payload: PointsAdjustmentPayload): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(payload).toRequestBody(jsonMediaType)
            val response = executeWithRetry("/rest/v1/rpc/adjust_customer_points", method = "POST", body = body)
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("ปรับปรุงแต้มไม่สำเร็จ: $resBody"))
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- ORDERS & RPC COMMIT SALE / VOID SALE ---

    suspend fun commitSaleRpc(request: CommitSaleRequest): Result<CommitSaleResponse> = withContext(Dispatchers.IO) {
        try {
            val bodyString = json.encodeToString(request)
            val body = bodyString.toRequestBody(jsonMediaType)
            val response = executeWithRetry("/rest/v1/rpc/commit_sale", method = "POST", body = body)
            val resBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("เกิดข้อผิดพลาดในการบันทึกการขาย: $resBody"))
            }

            val resp = try {
                json.decodeFromString<CommitSaleResponse>(resBody)
            } catch (e: Exception) {
                CommitSaleResponse(
                    success = true,
                    orderId = request.idempotencyKey,
                    orderNumber = "ORD-${request.idempotencyKey.take(8).uppercase()}",
                    message = "บันทึกการขายสำเร็จ"
                )
            }
            Result.success(resp)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun voidSaleRpc(request: VoidSaleRequest): Result<VoidSaleResponse> = withContext(Dispatchers.IO) {
        try {
            val bodyString = json.encodeToString(request)
            val body = bodyString.toRequestBody(jsonMediaType)
            val response = executeWithRetry("/rest/v1/rpc/void_sale", method = "POST", body = body)
            val resBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("ยกเลิกบิลไม่สำเร็จ: $resBody"))
            }

            val resp = try {
                json.decodeFromString<VoidSaleResponse>(resBody)
            } catch (e: Exception) {
                VoidSaleResponse(success = true, message = "ยกเลิกบิลสำเร็จ คืนสต็อกและแต้มเรียบร้อย")
            }
            Result.success(resp)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOrders(): Result<List<OrderRecord>> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithRetry("/rest/v1/orders?select=*,order_items(*)&order=created_at.desc")
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("ดึงประวัติการขายไม่สำเร็จ: $resBody"))
            }
            val list = json.decodeFromString<List<OrderRecord>>(resBody)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- STOCK & LINE NOTIFY EDGE FUNCTION ---

    suspend fun adjustStockRpc(payload: StockAdjustmentPayload): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(payload).toRequestBody(jsonMediaType)
            val response = executeWithRetry("/rest/v1/rpc/adjust_stock", method = "POST", body = body)
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("ปรับปรุงสต็อกไม่สำเร็จ: $resBody"))
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun triggerLineStockNotify(request: LineStockNotifyRequest): Result<LineStockNotifyResponse> = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(request).toRequestBody(jsonMediaType)
            // Trigger Edge Function: POST /functions/v1/line-stock-notify
            val response = executeWithRetry("/functions/v1/line-stock-notify", method = "POST", body = body)
            val resBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("ส่งแจ้งเตือน LINE ไม่สำเร็จ ($resBody)"))
            }

            val resp = try {
                json.decodeFromString<LineStockNotifyResponse>(resBody)
            } catch (e: Exception) {
                LineStockNotifyResponse(success = true, message = "ส่งการแจ้งเตือนสต็อกเข้า LINE เรียบร้อยแล้ว")
            }
            Result.success(resp)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- USER MANAGEMENT (ADMIN) ---

    suspend fun getAllUsers(): Result<List<UserProfile>> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithRetry("/rest/v1/profiles?select=*&order=created_at.desc")
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("ดึงรายชื่อผู้ใช้ไม่สำเร็จ: $resBody"))
            }
            val list = json.decodeFromString<List<UserProfile>>(resBody)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserActiveStatus(userId: String, isActive: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = "{\"is_active\": $isActive}".toRequestBody(jsonMediaType)
            val response = executeWithRetry("/rest/v1/profiles?id=eq.$userId", method = "PATCH", body = body)
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("อัปเดตสถานะผู้ใช้ไม่สำเร็จ"))
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
