package com.sitticun.orderapp.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitticun.orderapp.data.model.OutboxItem
import com.sitticun.orderapp.data.model.OutboxStatus
import com.sitticun.orderapp.ui.theme.*
import com.sitticun.orderapp.util.Formatters

@Composable
fun SyncDiagnosticsScreen(
    outboxItems: List<OutboxItem>,
    isSyncing: Boolean,
    isOnline: Boolean,
    onForceSync: () -> Unit,
    onClearCompleted: () -> Unit,
    onResetOutbox: () -> Unit
) {
    val pendingCount = outboxItems.count { it.status == OutboxStatus.PENDING }
    val syncedCount = outboxItems.count { it.status == OutboxStatus.SYNCED }
    val failedCount = outboxItems.count { it.status == OutboxStatus.FAILED }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        // Status Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isOnline) StatusSuccess else StatusDanger)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isOnline) "สถานะเครือข่าย: ออนไลน์ (Online)" else "สถานะเครือข่าย: ออฟไลน์ (Offline)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = PastelMint, strokeWidth = 2.dp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox(label = "รอซิงค์", count = pendingCount, color = StatusWarning, modifier = Modifier.weight(1f))
                    StatBox(label = "สำเร็จแล้ว", count = syncedCount, color = StatusSuccess, modifier = Modifier.weight(1f))
                    StatBox(label = "ติดขัด / ล้มเหลว", count = failedCount, color = StatusDanger, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onForceSync,
                        enabled = !isSyncing && isOnline,
                        colors = ButtonDefaults.buttonColors(containerColor = PastelMint),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("สั่งซิงค์เดี๋ยวนี้", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onClearCompleted,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ล้างที่สำเร็จ", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onResetOutbox,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDanger),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ล้างทั้งหมด", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "รายการงานในคิว Outbox (${outboxItems.size} รายการ)",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (outboxItems.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = "คิวว่างเปล่า ทุกรายการซิงค์เรียบร้อย", color = TextSecondary, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(outboxItems, key = { it.id }) { item ->
                    OutboxItemCard(item = item)
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SurfaceVariantColor,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, fontSize = 11.sp, color = TextSecondary)
            Text(text = "$count", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun OutboxItemCard(item: OutboxItem) {
    val (statusColor, statusBg, statusText) = when (item.status) {
        OutboxStatus.SYNCED -> Triple(StatusSuccess, PastelMintLight, "ซิงค์สำเร็จ")
        OutboxStatus.SYNCING -> Triple(PastelLavender, PastelLavenderLight, "กำลังส่ง...")
        OutboxStatus.FAILED -> Triple(StatusDanger, Color(0xFFFFEBEE), "ผิดพลาด (${item.retryCount} ครั้ง)")
        else -> Triple(StatusWarning, Color(0xFFFFF3E0), "รอซิงค์")
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.actionType,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextPrimary
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusBg
                ) {
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Idempotency Key: ${item.idempotencyKey}", fontSize = 10.sp, color = TextMuted)
            Text(text = "สร้างเมื่อ: ${Formatters.formatDateTime(item.createdAt)}", fontSize = 10.sp, color = TextMuted)

            if (!item.lastError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ข้อผิดพลาด: ${item.lastError}",
                    fontSize = 11.sp,
                    color = StatusDanger,
                    maxLines = 2
                )
            }
        }
    }
}
