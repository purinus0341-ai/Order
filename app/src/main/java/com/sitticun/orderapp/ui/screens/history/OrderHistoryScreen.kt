package com.sitticun.orderapp.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitticun.orderapp.data.model.OrderRecord
import com.sitticun.orderapp.ui.components.OrderStatusBadge
import com.sitticun.orderapp.ui.theme.*
import com.sitticun.orderapp.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderHistoryScreen(
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory())
) {
    val state by viewModel.uiState.collectAsState()
    val orders by viewModel.orders.collectAsState()

    val filteredOrders = remember(orders, state.searchQuery, state.selectedStatus) {
        orders.filter { ord ->
            val matchStatus = when (state.selectedStatus) {
                "สำเร็จ" -> ord.status.equals("COMPLETED", ignoreCase = true)
                "ยกเลิกแล้ว" -> ord.status.equals("VOIDED", ignoreCase = true)
                else -> true
            }
            val matchQuery = state.searchQuery.isBlank() ||
                    ord.orderNumber.contains(state.searchQuery, ignoreCase = true) ||
                    (ord.customerName?.contains(state.searchQuery, ignoreCase = true) == true)
            matchStatus && matchQuery
        }
    }

    if (state.showVoidDialog && state.selectedOrder != null) {
        VoidOrderDialog(
            order = state.selectedOrder!!,
            isVoiding = state.isVoiding,
            onConfirmVoid = { reason -> viewModel.voidOrder(reason) },
            onDismiss = { viewModel.dismissVoidDialog() }
        )
    }

    if (state.voidSuccessResponse != null) {
        val resp = state.voidSuccessResponse!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissSuccessDialog() },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess) },
            title = { Text("ยกเลิกบิลสำเร็จ", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(text = resp.message)
                    if (resp.restoredStockItems > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "คืนสต็อกสินค้า: ${resp.restoredStockItems} ชิ้น", fontWeight = FontWeight.SemiBold)
                    }
                    if (resp.restoredCustomerPoints > 0) {
                        Text(text = "คืนแต้มสะสมให้ลูกค้า: ${resp.restoredCustomerPoints} แต้ม", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissSuccessDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = PastelMint)
                ) {
                    Text("ตกลง", color = Color.White)
                }
            },
            containerColor = CardWhite,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(containerColor = PastelBackground) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header Search & Filter
            Surface(color = CardWhite, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        placeholder = { Text("ค้นหาเลขที่บิล, ชื่อลูกค้า...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PastelMint) },
                        trailingIcon = {
                            if (state.searchQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PastelMint,
                            unfocusedBorderColor = BorderLight
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ทั้งหมด", "สำเร็จ", "ยกเลิกแล้ว").forEach { status ->
                            val isSelected = state.selectedStatus == status
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.onStatusFilterChange(status) },
                                label = { Text(status, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PastelLavender,
                                    selectedLabelColor = Color.White,
                                    containerColor = SurfaceVariantColor,
                                    labelColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                }
            }

            // Orders list
            if (filteredOrders.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(text = "ไม่พบบันทึกการขาย", color = TextSecondary, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredOrders, key = { it.id }) { order ->
                        OrderCardItem(
                            order = order,
                            onClick = { viewModel.selectOrder(order) },
                            onVoid = { viewModel.openVoidDialog(order) }
                        )
                    }
                }
            }
        }
    }

    // Order Detail Modal Sheet
    if (state.selectedOrder != null && !state.showVoidDialog) {
        val ord = state.selectedOrder!!
        Dialog(onDismissRequest = { viewModel.selectOrder(null) }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                modifier = Modifier.fillMaxWidth().heightIn(max = 580.dp).padding(12.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = ord.orderNumber, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(text = Formatters.formatIsoDateTime(ord.createdAt), fontSize = 12.sp, color = TextSecondary)
                        }
                        OrderStatusBadge(status = ord.status)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = BorderLight, thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "ลูกค้า: ${ord.customerName ?: "ทั่วไป"} • ช่องทาง: ${ord.paymentMethod}", fontSize = 12.sp, color = TextSecondary)
                    if (!ord.deviceId.isNullOrBlank()) {
                        Text(text = "เครื่องบันทึก: ${ord.deviceId}", fontSize = 11.sp, color = TextMuted)
                    }

                    if (ord.status == "VOIDED") {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFFEBEE), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = "เหตุผลที่ยกเลิก: ${ord.voidReason ?: "-"}", fontSize = 12.sp, color = StatusDanger, fontWeight = FontWeight.SemiBold)
                                if (!ord.voidedAt.isNullOrBlank()) {
                                    Text(text = "ยกเลิกเมื่อ: ${Formatters.formatIsoDateTime(ord.voidedAt)}", fontSize = 11.sp, color = StatusDanger)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "รายการสินค้า:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(ord.items) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = item.productName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(text = "คิดเงิน ${item.paidQty} ชิ้น", fontSize = 11.sp, color = TextSecondary)
                                        if (item.freeQty > 0) {
                                            Text(text = "• ของแถม ${item.freeQty} ชิ้น", fontSize = 11.sp, color = PastelPeachDark, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text(
                                    text = Formatters.formatCurrency(item.subtotal),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = BorderLight, thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "ยอดรวมสินค้า", fontSize = 12.sp, color = TextSecondary)
                        Text(text = Formatters.formatCurrency(ord.subtotal), fontSize = 12.sp)
                    }

                    if (ord.redeemedPoints > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "ส่วนลดแต้ม (${ord.redeemedPoints} แต้ม)", fontSize = 12.sp, color = StatusSuccess)
                            Text(text = "-${Formatters.formatCurrency(ord.discountAmount)}", fontSize = 12.sp, color = StatusSuccess)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "ยอดสุทธิ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = Formatters.formatCurrency(ord.totalAmount), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PastelPeach)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.selectOrder(null) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("ปิด")
                        }

                        if (ord.status == "COMPLETED") {
                            Button(
                                onClick = { viewModel.openVoidDialog(ord) },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusDanger),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ยกเลิกบิลนี้", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderCardItem(
    order: OrderRecord,
    onClick: () -> Unit,
    onVoid: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = order.orderNumber, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                OrderStatusBadge(status = order.status)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ลูกค้า: ${order.customerName ?: "ทั่วไป"} • ${order.paymentMethod}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Text(
                    text = Formatters.formatCurrency(order.totalAmount),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PastelPeach
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Formatters.formatIsoDateTime(order.createdAt),
                    fontSize = 11.sp,
                    color = TextMuted
                )

                if (order.status == "COMPLETED") {
                    TextButton(
                        onClick = onVoid,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("ยกเลิกบิล", color = StatusDanger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
