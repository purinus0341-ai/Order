package com.sitticun.orderapp.data.api

import com.sitticun.orderapp.BuildConfig
import com.sitticun.orderapp.data.local.PreferenceManager
import com.sitticun.orderapp.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
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
        if (needAuth && preferenceManager.tokenExpiresAt > 0 &&
            System.currentTimeMillis() >= preferenceManager.tokenExpiresAt - 60_000L
        ) {
            performTokenRefreshInternal()
        }

        val request = buildRequest(endpoint, method, body, needAuth, extraHeaders)
        val response = okHttpClient.newCall(request).execute()

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

    private suspend fun performTokenRefreshInternal(): Boolean = withContext(Dispatchers.IO) {
        val rToken = preferenceManager.refreshToken ?: return@withContext false
        try {
            val reqPayload = json.encodeToString(RefreshTokenRequest(refreshToken = rToken))
            val req = buildRequest(
                endpoint = "/auth/v1/token?grant_type=refresh_token",
                method = "POST",
                body = reqPayload.toRequestBody(jsonMediaType),
                needAuth = false
            )
            val res = okHttpClient.newCall(req).execute()
            val resBody = res.body?.string().orEmpty()

            if (res.isSuccessful) {
                val session = json.decodeFromString<AuthSession>(resBody)
                preferenceManager.accessToken = session.accessToken
                preferenceManager.refreshToken = session.refreshToken
                preferenceManager.tokenExpiresAt = System.currentTimeMillis() + (session.expiresIn * 1000L)
                return@withContext true
            }
        } catch (_: Exception) {}
        false
    }

    // -------------------------------------------------------------
    // AUTHENTICATION & PROFILES
    // -------------------------------------------------------------
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
            preferenceManager.rememberedEmail = request.email

            val userId = session.user?.id
            if (userId.isNullOrBlank()) {
                return@withContext Result.failure(Exception("ไม่พบ User ID จากระบบ"))
            }

            val profileResult = fetchUserProfile(userId)
            if (profileResult.isFailure) {
                val defaultProfile = UserProfile(
                    userId = userId,
                    role = "staff",
                    displayName = request.email.substringBefore('@'),
                    isActive = true
                )
                preferenceManager.userProfile = defaultProfile
                return@withContext Result.success(defaultProfile)
            }

            val profile = profileResult.getOrThrow()
            if (!profile.isActive) {
                preferenceManager.clearSession()
                return@withContext Result.failure(Exception("บัญชีผู้ใช้นี้ถูกปิดการใช้งาน กรุณาติดต่อผู้ดูแลระบบ"))
            }

            preferenceManager.userProfile = profile
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchUserProfile(userId: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithRetry("/rest/v1/profiles?user_id=eq.$userId&select=user_id,role,display_name,is_active")
            val resBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("ดึงข้อมูล Profile ล้มเหลว: $resBody"))
            }

            val list = json.decodeFromString<List<UserProfile>>(resBody)
            if (list.isEmpty()) {
                return@withContext Result.failure(Exception("ไม่พบข้อมูลผู้ใช้ในระบบ"))
            }
            Result.success(list.first())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // PRODUCTS
    // -------------------------------------------------------------
    suspend fun getProducts(): Result<List<Product>> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithRetry("/rest/v1/products?select=id,name,tag,volume,price,is_active&order=id.asc")
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("ดึงรายการสินค้าล้มเหลว: $resBody"))
            }
            val list = json.decodeFromString<List<Product>>(resBody)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createProduct(product: Product): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(product).toRequestBody(jsonMediaType)
            val response = executeWithRetry("/rest/v1/products", method = "POST", body = body)
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("เพิ่มสินค้าล้มเหลว: ${response.body?.string()}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProduct(product: Product): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = buildJsonObject {
                put("name", product.name)
                put("tag", product.tag)
                put("volume", product.volume)
                put("price", product.price)
                put("is_active", product.isActive)
                put("updated_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()))
            }
            val body = payload.toString().toRequestBody(jsonMediaType)
            val response = executeWithRetry("/rest/v1/products?id=eq.${product.id}", method = "PATCH", body = body)
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("แก้ไขสินค้าล้มเหลว: ${response.body?.string()}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // CUSTOMERS & POINTS
    // -------------------------------------------------------------
    suspend fun getCustomers(): Result<List<Customer>> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithRetry("/rest/v1/customers?select=id,name,normalized_name,created_at,updated_at&deleted_at=is.null&order=name.asc")
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("ดึงรายชื่อลูกค้าล้มเหลว: $resBody"))
            }
            val list = json.decodeFromString<List<Customer>>(resBody)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createCustomer(id: String, name: String): Result<Customer> = withContext(Dispatchers.IO) {
        try {
            val payload = json.encodeToString(CreateCustomerRpcPayload(id = id, name = name))
            val response = executeWithRetry(
                endpoint = "/rest/v1/rpc/create_customer",
                method = "POST",
                body = payload.toRequestBody(jsonMediaType),
                extraHeaders = mapOf("Accept" to "application/vnd.pgrst.object+json")
            )
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("สร้างลูกค้าไม่สำเร็จ: $resBody"))
            }
            val customer = json.decodeFromString<Customer>(resBody)
            Result.success(customer)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPointBalances(customerId: String): Result<List<PointBalance>> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithRetry("/rest/v1/point_balances?customer_id=eq.$customerId&select=customer_id,product_id,balance&order=product_id.asc")
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("ดึงยอดแต้มล้มเหลว: $resBody"))
            }
            val list = json.decodeFromString<List<PointBalance>>(resBody)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // SALES & ORDERS
    // -------------------------------------------------------------
    suspend fun commitSale(order: CloudSaleOrder, idempotencyKey: String, deviceId: String): Result<CloudSaleResult> = withContext(Dispatchers.IO) {
        try {
            val payload = json.encodeToString(
                CommitSaleRpcPayload(
                    order = order,
                    idempotencyKey = idempotencyKey,
                    deviceId = deviceId
                )
            )
            val response = executeWithRetry(
                endpoint = "/rest/v1/rpc/commit_sale",
                method = "POST",
                body = payload.toRequestBody(jsonMediaType)
            )
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("บันทึกการขายล้มเหลว: $resBody"))
            }
            val result = json.decodeFromString<CloudSaleResult>(resBody)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun voidOrder(orderId: String, idempotencyKey: String): Result<VoidOrderResult> = withContext(Dispatchers.IO) {
        try {
            val payload = json.encodeToString(
                VoidOrderRpcPayload(
                    orderId = orderId,
                    idempotencyKey = idempotencyKey
                )
            )
            val response = executeWithRetry(
                endpoint = "/rest/v1/rpc/void_order",
                method = "POST",
                body = payload.toRequestBody(jsonMediaType)
            )
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("ยกเลิกบิลล้มเหลว: $resBody"))
            }
            val result = json.decodeFromString<VoidOrderResult>(resBody)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOrders(): Result<List<OrderSummary>> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithRetry(
                "/rest/v1/orders?select=id,customer_id,customer_name_snapshot,created_at,total_qty,total,status,created_by,order_items(*),order_free_items(*),order_point_uses(*)&deleted_at=is.null&order=created_at.desc"
            )
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("ดึงรายการบิลล้มเหลว: $resBody"))
            }
            val list = json.decodeFromString<List<OrderSummary>>(resBody)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // HELD BILLS
    // -------------------------------------------------------------
    suspend fun getHeldBills(): Result<List<HeldBill>> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithRetry(
                "/rest/v1/held_bills?select=id,customer_id,customer_name_snapshot,note,payload,updated_at,updated_by,version&order=updated_at.desc"
            )
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("ดึงบิลพักล้มเหลว: $resBody"))
            }
            val list = json.decodeFromString<List<HeldBill>>(resBody)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveHeldBill(bill: HeldBill, expectedVersion: Long): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val body = SaveHeldBillBody(
                id = bill.id,
                customerId = bill.customerId,
                note = bill.note,
                payload = bill.payload
            )
            val payload = json.encodeToString(SaveHeldBillRpcPayload(bill = body, expectedVersion = expectedVersion))
            val response = executeWithRetry(
                endpoint = "/rest/v1/rpc/save_held_bill",
                method = "POST",
                body = payload.toRequestBody(jsonMediaType)
            )
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("บันทึกบิลพักล้มเหลว: $resBody"))
            }
            val parsed = json.decodeFromString<JsonObject>(resBody)
            val newVer = parsed["version"]?.jsonPrimitive?.longOrNull ?: (expectedVersion + 1)
            Result.success(newVer)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteHeldBill(id: String, expectedVersion: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = json.encodeToString(DeleteHeldBillRpcPayload(id = id, expectedVersion = expectedVersion))
            val response = executeWithRetry(
                endpoint = "/rest/v1/rpc/delete_held_bill",
                method = "POST",
                body = payload.toRequestBody(jsonMediaType)
            )
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("ลบบิลพักล้มเหลว: ${response.body?.string()}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // STOCK SNAPSHOTS & LINE NOTIFY
    // -------------------------------------------------------------
    suspend fun getStockSnapshots(): Result<List<StockSnapshot>> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithRetry(
                "/rest/v1/stock_snapshots?select=id,captured_at,note,source_device_id,created_by,stock_snapshot_items(id,snapshot_id,product_id,qty)&order=captured_at.desc"
            )
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("ดึงประวัติสต๊อกล้มเหลว: $resBody"))
            }

            val array = json.decodeFromString<JsonArray>(resBody)
            val snapshots = array.map { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
                val capturedAt = obj["captured_at"]?.jsonPrimitive?.content.orEmpty()
                val note = obj["note"]?.jsonPrimitive?.contentOrNull
                val deviceId = obj["source_device_id"]?.jsonPrimitive?.contentOrNull
                val createdBy = obj["created_by"]?.jsonPrimitive?.contentOrNull
                val itemsArr = obj["stock_snapshot_items"]?.jsonArray ?: JsonArray(emptyList())
                val items = itemsArr.map { itemEl ->
                    val itemObj = itemEl.jsonObject
                    StockSnapshotItem(
                        id = itemObj["id"]?.jsonPrimitive?.content.orEmpty(),
                        snapshotId = itemObj["snapshot_id"]?.jsonPrimitive?.contentOrNull ?: id,
                        productId = itemObj["product_id"]?.jsonPrimitive?.long ?: 0L,
                        qty = itemObj["qty"]?.jsonPrimitive?.int ?: 0
                    )
                }
                StockSnapshot(
                    id = id,
                    capturedAt = capturedAt,
                    note = note,
                    sourceDeviceId = deviceId,
                    createdBy = createdBy,
                    items = items
                )
            }

            Result.success(snapshots)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createStockSnapshot(snapshot: StockSnapshot, idempotencyKey: String): Result<CreateStockSnapshotResult> = withContext(Dispatchers.IO) {
        try {
            val payload = json.encodeToString(
                CreateStockSnapshotRpcPayload(
                    snapshot = snapshot,
                    idempotencyKey = idempotencyKey
                )
            )
            val response = executeWithRetry(
                endpoint = "/rest/v1/rpc/create_stock_snapshot",
                method = "POST",
                body = payload.toRequestBody(jsonMediaType)
            )
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("บันทึกสต๊อกล้มเหลว: $resBody"))
            }
            val result = json.decodeFromString<CreateStockSnapshotResult>(resBody)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun notifyStockSnapshot(snapshotId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = json.encodeToString(LineStockNotifyRequest(snapshotId = snapshotId))
            val response = executeWithRetry(
                endpoint = "/functions/v1/line-stock-notify",
                method = "POST",
                body = payload.toRequestBody(jsonMediaType)
            )
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("แจ้งเตือน LINE ล้มเหลว: $resBody"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // COUNT SESSIONS & STORAGE
    // -------------------------------------------------------------
    suspend fun uploadCountImage(path: String, bytes: ByteArray, mimeType: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val mediaType = mimeType.toMediaType()
            val body = bytes.toRequestBody(mediaType)
            val response = executeWithRetry(
                endpoint = "/storage/v1/object/count-images/$path",
                method = "POST",
                body = body
            )
            if (!response.isSuccessful && response.code != 409) { // 409: already exists is okay
                return@withContext Result.failure(Exception("อัปโหลดรูปภาพล้มเหลว: ${response.body?.string()}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCountSessions(): Result<List<CountSession>> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithRetry(
                "/rest/v1/count_sessions?select=id,created_at,created_by,source_device_id,image_path,image_width,image_height,status,note,version,count_markers(id,session_id,product_id,x_normalized,y_normalized,created_at,deleted_at)&order=created_at.desc"
            )
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("ดึงประวัติการนับล้มเหลว: $resBody"))
            }
            val array = json.decodeFromString<JsonArray>(resBody)
            val sessions = array.map { el ->
                val obj = el.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
                val markersArr = obj["count_markers"]?.jsonArray ?: JsonArray(emptyList())
                val markers = markersArr.mapNotNull { mEl ->
                    val mObj = mEl.jsonObject
                    val deletedAt = mObj["deleted_at"]?.jsonPrimitive?.contentOrNull
                    if (deletedAt != null) return@mapNotNull null
                    CountMarker(
                        id = mObj["id"]?.jsonPrimitive?.content.orEmpty(),
                        sessionId = mObj["session_id"]?.jsonPrimitive?.contentOrNull ?: id,
                        productId = mObj["product_id"]?.jsonPrimitive?.longOrNull,
                        xNormalized = mObj["x_normalized"]?.jsonPrimitive?.double ?: 0.0,
                        yNormalized = mObj["y_normalized"]?.jsonPrimitive?.double ?: 0.0,
                        createdAt = mObj["created_at"]?.jsonPrimitive?.contentOrNull
                    )
                }
                CountSession(
                    id = id,
                    createdAt = obj["created_at"]?.jsonPrimitive?.contentOrNull,
                    createdBy = obj["created_by"]?.jsonPrimitive?.contentOrNull,
                    sourceDeviceId = obj["source_device_id"]?.jsonPrimitive?.contentOrNull,
                    imagePath = obj["image_path"]?.jsonPrimitive?.contentOrNull,
                    imageWidth = obj["image_width"]?.jsonPrimitive?.intOrNull,
                    imageHeight = obj["image_height"]?.jsonPrimitive?.intOrNull,
                    status = obj["status"]?.jsonPrimitive?.content ?: "completed",
                    note = obj["note"]?.jsonPrimitive?.contentOrNull,
                    version = obj["version"]?.jsonPrimitive?.long ?: 1L,
                    markers = markers
                )
            }
            Result.success(sessions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveCountSession(session: CountSession, expectedVersion: Long): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val payload = json.encodeToString(SaveCountSessionRpcPayload(session = session, expectedVersion = expectedVersion))
            val response = executeWithRetry(
                endpoint = "/rest/v1/rpc/save_count_session",
                method = "POST",
                body = payload.toRequestBody(jsonMediaType)
            )
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("บันทึก Session นับสต๊อกล้มเหลว: $resBody"))
            }
            val parsed = json.decodeFromString<JsonObject>(resBody)
            val newVer = parsed["version"]?.jsonPrimitive?.longOrNull ?: (expectedVersion + 1)
            Result.success(newVer)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addCountMarker(marker: CountMarker): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = json.encodeToString(AddCountMarkerRpcPayload(marker = marker))
            val response = executeWithRetry(
                endpoint = "/rest/v1/rpc/add_count_marker",
                method = "POST",
                body = payload.toRequestBody(jsonMediaType)
            )
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("บันทึก Marker ล้มเหลว: ${response.body?.string()}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // ADMIN FUNCTIONS (Edge Function admin-users)
    // -------------------------------------------------------------
    suspend fun listProfilesAdmin(): Result<List<UserProfile>> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithRetry(
                "/rest/v1/profiles?select=user_id,role,display_name,is_active&order=created_at.asc"
            )
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("ดึงรายชื่อผู้ใช้ล้มเหลว: $resBody"))
            }
            val list = json.decodeFromString<List<UserProfile>>(resBody)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserActiveStatus(userId: String, isActive: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = buildJsonObject {
                put("is_active", isActive)
            }
            val response = executeWithRetry(
                endpoint = "/rest/v1/profiles?user_id=eq.$userId",
                method = "PATCH",
                body = payload.toString().toRequestBody(jsonMediaType)
            )
            if (!response.isSuccessful) {
                val adminRes = invokeAdminUsers(buildJsonObject {
                    put("action", "toggle_active")
                    put("userId", userId)
                    put("isActive", isActive)
                })
                if (adminRes.isFailure) {
                    return@withContext Result.failure(Exception("อัปเดตสถานะผู้ใช้ล้มเหลว: ${response.body?.string()}"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun invokeAdminUsers(actionPayload: JsonObject): Result<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val body = actionPayload.toString().toRequestBody(jsonMediaType)
            val response = executeWithRetry(
                endpoint = "/functions/v1/admin-users",
                method = "POST",
                body = body
            )
            val resBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("เรียกคำสั่ง Admin ล้มเหลว: $resBody"))
            }
            val obj = if (resBody.isNotBlank()) json.decodeFromString<JsonObject>(resBody) else buildJsonObject {}
            Result.success(obj)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
