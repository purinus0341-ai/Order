package com.sitticun.orderapp.ui.screens.stock

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitticun.orderapp.data.model.Product
import com.sitticun.orderapp.ui.components.StockBadge
import com.sitticun.orderapp.ui.theme.*
import com.sitticun.orderapp.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    viewModel: StockViewModel = viewModel(factory = StockViewModel.Factory())
) {
    val state by viewModel.uiState.collectAsState()
    val products by viewModel.products.collectAsState()

    val lowStockCount = remember(products) { products.count { it.isLowStock } }
    val outOfStockCount = remember(products) { products.count { it.isOutOfStock } }

    val filteredProducts = remember(products, state.searchQuery, state.filterMode) {
        products.filter { p ->
            val matchFilter = when (state.filterMode) {
                "สินค้าใกล้หมด" -> p.isLowStock
                "สินค้าหมด" -> p.isOutOfStock
                else -> true
            }
            val matchQuery = state.searchQuery.isBlank() ||
                    p.name.contains(state.searchQuery, ignoreCase = true) ||
                    p.barcode.contains(state.searchQuery, ignoreCase = true) ||
                    p.sku.contains(state.searchQuery, ignoreCase = true)
            matchFilter && matchQuery
        }
    }

    if (state.showAdjustDialog && state.selectedProduct != null) {
        StockAdjustmentDialog(
            product = state.selectedProduct!!,
            isAdjusting = state.isAdjusting,
            onConfirm = { newQty, reason -> viewModel.adjustStock(newQty, reason) },
            onDismiss = { viewModel.dismissAdjustDialog() }
        )
    }

    if (state.lineNotifyResult != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLineDialog() },
            icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = PastelMint, modifier = Modifier.size(36.dp)) },
            title = { Text("การแจ้งเตือน LINE", fontWeight = FontWeight.Bold) },
            text = { Text(state.lineNotifyResult!!) },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissLineDialog() },
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
            // Summary Cards Row
            Surface(color = CardWhite, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Total items card
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceVariantColor)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = "สินค้าทั้งหมด", fontSize = 11.sp, color = TextSecondary)
                                Text(text = "${products.size} รายการ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }

                        // Low stock card
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = "ใกล้หมด", fontSize = 11.sp, color = StatusWarning)
                                Text(text = "$lowStockCount รายการ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = StatusWarning)
                            }
                        }

                        // Out of stock card
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = "สินค้าหมด", fontSize = 11.sp, color = StatusDanger)
                                Text(text = "$outOfStockCount รายการ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = StatusDanger)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // LINE Notify Trigger Button
                    Button(
                        onClick = { viewModel.triggerLineNotify() },
                        enabled = !state.isSendingLineNotify,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF06C755), // LINE Green
                            contentColor = Color.White
                        )
                    ) {
                        if (state.isSendingLineNotify) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("กำลังส่งแจ้งเตือน...")
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ส่งแจ้งเตือนสินค้าใกล้หมดเข้า LINE (Edge Function)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search and filter
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        placeholder = { Text("ค้นหาสินค้า, SKU, บาร์โค้ด...", fontSize = 13.sp) },
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
                        listOf("ทั้งหมด", "สินค้าใกล้หมด", "สินค้าหมด").forEach { mode ->
                            val isSelected = state.filterMode == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.onFilterModeChange(mode) },
                                label = { Text(mode, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PastelMint,
                                    selectedLabelColor = Color.White
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                }
            }

            // Products list
            if (filteredProducts.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(text = "ไม่พบรายการสินค้า", color = TextSecondary, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        StockItemCard(
                            product = product,
                            onAdjust = { viewModel.openAdjustDialog(product) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StockItemCard(
    product: Product,
    onAdjust: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = product.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    StockBadge(stockQty = product.stockQty, minAlert = product.minStockAlert)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "หมวด: ${product.category}", fontSize = 12.sp, color = TextSecondary)
                    if (product.barcode.isNotBlank()) {
                        Text(text = "บาร์โค้ด: ${product.barcode}", fontSize = 12.sp, color = TextMuted)
                    }
                }
                Text(
                    text = "ราคา: ${Formatters.formatCurrency(product.price)} (เตือนเมื่อต่ำกว่า ${product.minStockAlert})",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Button(
                onClick = onAdjust,
                colors = ButtonDefaults.buttonColors(containerColor = PastelMintLight, contentColor = PastelMintDark),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("ปรับยอด", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
