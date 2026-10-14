package com.sitticun.orderapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StockSnapshotItem(
    val id: String,
    @SerialName("snapshot_id") val snapshotId: String? = null,
    @SerialName("product_id") val productId: Long,
    val qty: Int
)

@Serializable
data class StockSnapshot(
    val id: String,
    @SerialName("captured_at") val capturedAt: String,
    val note: String? = null,
    @SerialName("source_device_id") val sourceDeviceId: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    val items: List<StockSnapshotItem> = emptyList()
)

@Serializable
data class CreateStockSnapshotRpcPayload(
    @SerialName("p_snapshot") val snapshot: StockSnapshot,
    @SerialName("p_idempotency_key") val idempotencyKey: String
)

@Serializable
data class CreateStockSnapshotResult(
    @SerialName("snapshot_id") val snapshotId: String,
    @SerialName("idempotent_replay") val idempotentReplay: Boolean = false
)

@Serializable
data class CountMarker(
    val id: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("product_id") val productId: Long? = null,
    @SerialName("x_normalized") val xNormalized: Double,
    @SerialName("y_normalized") val yNormalized: Double,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
data class CountSession(
    val id: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("source_device_id") val sourceDeviceId: String? = null,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("image_width") val imageWidth: Int? = null,
    @SerialName("image_height") val imageHeight: Int? = null,
    val status: String = "completed",
    val note: String? = null,
    val version: Long = 1L,
    val markers: List<CountMarker> = emptyList()
)

@Serializable
data class SaveCountSessionRpcPayload(
    @SerialName("p_session") val session: CountSession,
    @SerialName("p_expected_version") val expectedVersion: Long
)

@Serializable
data class AddCountMarkerRpcPayload(
    @SerialName("p_marker") val marker: CountMarker
)

@Serializable
data class LineStockNotifyRequest(
    val snapshotId: String
)

@Serializable
data class LineStockNotifyResponse(
    val ok: Boolean = true,
    val message: String? = null
)

// UI Marker dot representation on canvas
data class MarkerDot(
    val id: String,
    val productId: Long?,
    val xNormalized: Float,
    val yNormalized: Float,
    val seq: Int,
    val colorHex: String = "#5B8A72"
)

// Stock comparison model for UI StockScreen
data class ProductStockComparison(
    val product: Product,
    val systemQty: Int,
    val actualQty: Int
) {
    val diff: Int get() = actualQty - systemQty
}
