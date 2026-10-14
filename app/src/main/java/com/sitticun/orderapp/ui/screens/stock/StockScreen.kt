package com.sitticun.orderapp.ui.screens.stock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitticun.orderapp.data.model.StockSnapshot
import com.sitticun.orderapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    viewModel: StockViewModel = viewModel(factory = StockViewModel.Factory())
) {
    val state by viewModel.uiState.collectAsState()
    val snapshots by viewModel.snapshots.collectAsState()

    if (state.message != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearMessage() },
            title = { Text("สำเร็จ", fontWeight = FontWeight.Bold, color = PastelMintDark) },
            text = { Text(state.message ?: "") },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearMessage() },
                    colors = ButtonDefaults.buttonColors(containerColor = PastelMint)
                ) {
                    Text("ตกลง", color = Color.White)
                }
            }
        )
    }

    if (state.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearMessage() },
            title = { Text("ข้อผิดพลาด", fontWeight = FontWeight.Bold, color = StatusDanger) },
            text = { Text(state.errorMessage ?: "") },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearMessage() },
                    colors = ButtonDefaults.buttonColors(containerColor = PastelMint)
                ) {
                    Text("ตกลง", color = Color.White)
                }
            }
        )
    }

    Scaffold(containerColor = PastelBackground) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Count Form Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "นับสต็อกสินค้า (Stock Snapshot)",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "คำนวณยอดระบบเทียบกับยอดนับจริงครบทุกตัว",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = PastelMint,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = BorderLight, thickness = 0.8.dp)
                        Spacer(modifier = Modifier.height(14.dp))

                        // Table Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceVariantColor, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "สินค้า", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                            Text(text = "ระบบ", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
                            Text(text = "นับจริง", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                            Text(text = "ต่าง", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Product rows
                        for (item in state.comparisons) {
                            val inputVal = state.inputQuantities[item.product.id] ?: ""
                            val diff = item.diff
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.5f)) {
                                    Text(
                                        text = item.product.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = item.product.volume ?: "",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }

                                Text(
                                    text = "${item.systemQty}",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.weight(0.8f)
                                )

                                OutlinedTextField(
                                    value = inputVal,
                                    onValueChange = { viewModel.onQuantityChange(item.product.id, it) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PastelMint,
                                        unfocusedBorderColor = BorderLight
                                    )
                                )

                                Text(
                                    text = if (diff > 0) "+$diff" else "$diff",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (diff == 0) PastelMintDark else if (diff > 0) Color(0xFF2E7D32) else StatusDanger,
                                    modifier = Modifier.weight(0.7f).padding(start = 6.dp)
                                )
                            }
                            HorizontalDivider(color = BorderLight.copy(alpha = 0.5f), thickness = 0.5.dp)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Note Field
                        OutlinedTextField(
                            value = state.note,
                            onValueChange = { viewModel.onNoteChange(it) },
                            placeholder = { Text("โน้ตเพิ่มเติม เช่น นับรอบเย็น, ตรวจนับประจำเดือน") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Save Snapshot Button
                        Button(
                            onClick = { viewModel.saveSnapshot() },
                            enabled = !state.isSaving && state.comparisons.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PastelMint)
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("บันทึกสต็อก (Create Snapshot)", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Send LINE Notify Button (If a snapshot exists)
                        val targetSnapshotId = state.lastSavedSnapshotId ?: snapshots.firstOrNull()?.id
                        if (targetSnapshotId != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.sendLineNotify(targetSnapshotId) },
                                enabled = !state.isSendingLineNotify,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF06C755),
                                    contentColor = Color.White
                                )
                            ) {
                                if (state.isSendingLineNotify) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("กำลังส่งแจ้งเตือน...")
                                } else {
                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("ส่งรายงานเข้า LINE ($targetSnapshotId)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Snapshot History Header
            item {
                Text(
                    text = "ประวัติ Snapshot สต็อกย้อนหลัง",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            // History List
            if (snapshots.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(text = "ยังไม่มีประวัติการนับสต็อก", color = TextSecondary)
                    }
                }
            } else {
                items(snapshots, key = { it.id }) { snap ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = snap.id, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                Text(
                                    text = snap.capturedAt.take(16).replace('T', ' '),
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                            if (!snap.note.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = "โน้ต: ${snap.note}", fontSize = 12.sp, color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (item in snap.items) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = SurfaceVariantColor,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(text = "สินค้า #${item.productId}", fontSize = 10.sp, color = TextMuted)
                                            Text(text = "${item.qty} ชิ้น", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
