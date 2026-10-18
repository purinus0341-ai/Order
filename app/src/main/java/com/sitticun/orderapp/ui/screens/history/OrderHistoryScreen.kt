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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitticun.orderapp.data.model.OrderSummary
import com.sitticun.orderapp.ui.components.IosNavigationHeader
import com.sitticun.orderapp.ui.components.IosSearchBar
import com.sitticun.orderapp.ui.components.IosSegmentedControl
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
    val currentUser by viewModel.currentUser.collectAsState()
    val isAdmin = currentUser?.isAdmin == true

    val statusOptions = listOf("ทั้งหมด", "สำเร็จ", "ยกเลิกแล้ว")
    val selectedStatusIdx = statusOptions.indexOf(state.selectedStatus).coerceAtLeast(0)

    val filteredOrders = remember(orders, state.searchQuery, state.selectedStatus) {
        orders.filter { ord ->
            val matchStatus = when (state.selectedStatus) {
                "สำเร็จ" -> !ord.isVoided
                "ยกเลิกแล้ว" -> ord.isVoided
                else -> true
            }
            val matchQuery = state.searchQuery.isBlank() ||
                    ord.id.contains(state.searchQuery, ignoreCase = true) ||
                    ord.customerName.contains(state.searchQuery, ignoreCase = true)
            matchStatus && matchQuery
        }
    }

    if (state.showVoidDialog && state.selectedOrder != null) {
        VoidOrderDialog(
            order = state.selectedOrder!!,
            isVoiding = state.isVoiding,
            onConfirmVoid = { viewModel.voidOrder() },
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "รหัสบิล: ${resp.orderId}")
                    Text(text = "สถานะ: ${resp.status}")
                    Text(
                        text = "ระบบได้ทำการคืนแต้มสะสมและคืนสต็อกสินค้าอัตโนมัติแล้ว",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissSuccessDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("ตกลง", color = Color.White)
                }
            },
            containerColor = CardWhite,
            shape = RoundedCornerShape(18.dp)
        )
    }

    if (state.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearErrorMessage() },
            icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StatusDanger) },
            title = { Text("เกิดข้อผิดพลาด", fontWeight = FontWeight.Bold) },
            text = { Text(state.errorMessage ?: "") },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearErrorMessage() },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDanger),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("ตกลง", color = Color.White)
                }
            },
            containerColor = CardWhite,
            shape = RoundedCornerShape(18.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosGroupedBackground)
    ) {
        IosNavigationHeader(
            title = "ประวัติการขาย",
            subtitle = "รายการบิลทั้งหมด ${filteredOrders.size} รายการ",
            bottomContent = {
                IosSearchBar(
                    query = state.searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    placeholder = "ค้นหาเลขที่บิล หรือชื่อลูกค้า...",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                IosSegmentedControl(
                    items = statusOptions,
                    selectedIndex = selectedStatusIdx,
                    onItemSelected = { idx -> viewModel.onStatusFilterChange(statusOptions[idx]) }
                )
            }
        )

        if (filteredOrders.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = "ไม่พบบันทึกการขาย", color = TextSecondary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredOrders, key = { it.id }) { order ->
                    OrderCardItem(
                        order = order,
                        canVoid = isAdmin && !order.isVoided,
                        onClick = { viewModel.selectOrder(order) },
                        onVoid = { viewModel.openVoidDialog(order) }
                    )
                }
            }
        }
    }

    // Order Detail Modal Sheet
    if (state.selectedOrder != null && !state.showVoidDialog) {
        val ord = state.selectedOrder!!
        Dialog(onDismissRequest = { viewModel.selectOrder(null) }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = CardWhite,
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp).padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "บิล #${ord.id.take(8)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = Formatters.formatIsoDateTime(ord.createdAt),
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        OrderStatusBadge(status = if (ord.isVoided) "VOIDED" else "COMPLETED")
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = IosSeparatorLight, thickness = 0.6.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "ลูกค้า: ${ord.customerName.ifBlank { "ไม่ระบุ" }}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    if (!ord.createdBy.isNullOrBlank()) {
                        Text(text = "ผู้บันทึก: ${ord.createdBy}", fontSize = 11.sp, color = TextMuted)
                    }

                    if (ord.isVoided) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = IosRedLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "บิลนี้ถูกยกเลิกแล้ว (Voided) และปรับคืนสต็อก/แต้มแล้ว",
                                fontSize = 12.sp,
                                color = StatusDanger,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "รายการสินค้า (${ord.totalQty} ชิ้น):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ord.items) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${item.nameSnapshot} ${item.volumeSnapshot ?: ""}".trim(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${item.qty} ชิ้น x ${Formatters.formatCurrency(item.unitPrice)}",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                                Text(
                                    text = Formatters.formatCurrency(item.subtotal),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
                        }

                        if (ord.freeItems.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "ของแถมที่แลกรับ:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = IosOrangeDark
                                )
                            }
                            items(ord.freeItems) { free ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "รหัสสินค้า ${free.productId}",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = "${free.qty} ชิ้น (ฟรี)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IosOrangeDark
                                    )
                                }
                            }
                        }

                        if (ord.pointUses.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "แต้มสะสมที่ใช้แลก:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StatusSuccess
                                )
                            }
                            items(ord.pointUses) { pUse ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "แต้มสินค้า ${pUse.productId}",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = "-${pUse.points} แต้ม",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusSuccess
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = IosSeparatorLight, thickness = 0.6.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "ยอดรวมสุทธิ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            text = Formatters.formatCurrency(ord.total),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = IosBlue
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.selectOrder(null) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("ปิด", color = TextSecondary)
                        }

                        if (!ord.isVoided && isAdmin) {
                            Button(
                                onClick = { viewModel.openVoidDialog(ord) },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusDanger),
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ยกเลิกบิลนี้", color = Color.White, fontWeight = FontWeight.SemiBold)
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
    order: OrderSummary,
    canVoid: Boolean,
    onClick: () -> Unit,
    onVoid: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardWhite,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "บิล #${order.id.take(8)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
                OrderStatusBadge(status = if (order.isVoided) "VOIDED" else "COMPLETED")
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ลูกค้า: ${order.customerName.ifBlank { "ไม่ระบุ" }} • ${order.totalQty} ชิ้น",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Text(
                    text = Formatters.formatCurrency(order.total),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = IosBlue
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

                if (canVoid) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = IosRedLight,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onVoid)
                    ) {
                        Text(
                            text = "ยกเลิกบิล",
                            color = StatusDanger,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
