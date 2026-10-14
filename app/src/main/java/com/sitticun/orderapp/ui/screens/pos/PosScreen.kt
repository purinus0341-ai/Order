package com.sitticun.orderapp.ui.screens.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitticun.orderapp.OrderApplication
import com.sitticun.orderapp.ui.components.OfflineBanner
import com.sitticun.orderapp.ui.screens.pos.components.*
import com.sitticun.orderapp.ui.theme.*
import com.sitticun.orderapp.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    viewModel: PosViewModel = viewModel(factory = PosViewModel.Factory())
) {
    val state by viewModel.uiState.collectAsState()
    val products by viewModel.products.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val heldBills by viewModel.heldBills.collectAsState()

    val outboxItems by OrderApplication.instance.outboxRepository.outboxItems.collectAsState()
    val isSyncing by OrderApplication.instance.outboxRepository.isSyncing.collectAsState()
    val isOnlineFlow by OrderApplication.instance.networkMonitor.isOnlineFlow.collectAsState(initial = true)

    val pendingCount = remember(outboxItems) { outboxItems.count { it.status == "PENDING" || it.status == "FAILED" } }
    var isMobileCartOpen by remember { mutableStateOf(false) }

    val filteredProducts = remember(products, state.searchQuery) {
        products.filter { p ->
            val matchQuery = state.searchQuery.isBlank() ||
                    p.name.contains(state.searchQuery, ignoreCase = true) ||
                    (p.tag != null && p.tag.contains(state.searchQuery, ignoreCase = true))
            matchQuery && p.isActive
        }
    }

    // Dialogs
    if (state.showCustomerPicker) {
        CustomerPickerDialog(
            customers = customers,
            selectedCustomer = state.selectedCustomer,
            onSelectCustomer = { viewModel.selectCustomer(it) },
            onCreateCustomer = { viewModel.createCustomer(it) },
            onDismiss = { viewModel.toggleCustomerPicker(false) }
        )
    }

    if (state.showPointsEditor) {
        PointsEditorDialog(
            customer = state.selectedCustomer,
            cartItems = state.cartItems,
            pointBalances = state.customerPointBalances,
            onUpdatePointsAndFree = { pid, points, free -> viewModel.updatePointsAndFree(pid, points, free) },
            onDismiss = { viewModel.togglePointsEditor(false) }
        )
    }

    if (state.showHeldBillsDialog) {
        HeldBillsDialog(
            heldBills = heldBills,
            onResumeBill = { viewModel.resumeHeldBill(it) },
            onDeleteBill = { viewModel.deleteHeldBill(it) },
            onDismiss = { viewModel.toggleHeldBillsDialog(false) }
        )
    }

    // Error Alert Dialog
    if (state.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissErrorMessage() },
            title = { Text("ข้อผิดพลาด", fontWeight = FontWeight.Bold, color = StatusDanger) },
            text = { Text(state.errorMessage ?: "") },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissErrorMessage() },
                    colors = ButtonDefaults.buttonColors(containerColor = PastelMint)
                ) {
                    Text("ตกลง", color = Color.White)
                }
            }
        )
    }

    // Checkout Success Dialog
    if (state.checkoutSuccess != null) {
        val result = state.checkoutSuccess!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissSuccessDialog() },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(40.dp)) },
            title = { Text("บันทึกการขายสำเร็จ!", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column {
                    Text(text = "รหัสบิล: ${result.orderId}", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "ยอดรวม: ${Formatters.formatCurrency(result.total)} (คิดเงิน ${result.totalQty} ชิ้น)", fontSize = 13.sp, color = TextSecondary)
                    if (result.pointBalances.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "ยอดแต้มสะสมล่าสุด:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PastelMintDark)
                        for ((pid, bal) in result.pointBalances) {
                            val pName = products.find { it.id.toString() == pid }?.name ?: "สินค้า $pid"
                            Text(text = "• $pName: $bal แต้ม", fontSize = 12.sp, color = TextSecondary)
                        }
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

    // Main POS Layout
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(PastelBackground)
    ) {
        val isTablet = maxWidth >= 760.dp

        Column(modifier = Modifier.fillMaxSize()) {
            OfflineBanner(
                isOnline = isOnlineFlow,
                pendingSyncCount = pendingCount,
                isSyncing = isSyncing,
                onForceSync = {
                    OrderApplication.instance.outboxRepository.refreshOutbox()
                }
            )

            // Header Toolbar
            Surface(
                color = CardWhite,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        placeholder = { Text("ค้นหาสินค้า...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PastelMint) },
                        trailingIcon = {
                            if (state.searchQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "ล้าง", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PastelMint,
                            unfocusedBorderColor = BorderLight
                        )
                    )

                    BadgedBox(
                        badge = {
                            if (heldBills.isNotEmpty()) {
                                Badge(containerColor = PastelPeach) {
                                    Text("${heldBills.size}")
                                }
                            }
                        }
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.toggleHeldBillsDialog(true) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.PauseCircle, contentDescription = null, tint = PastelLavender, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("บิลพัก", fontSize = 12.sp, color = TextPrimary)
                        }
                    }
                }
            }

            // Products & Cart
            if (isTablet) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Box(modifier = Modifier.weight(0.62f)) {
                        ProductGrid(
                            products = filteredProducts,
                            onProductClick = { viewModel.addToCart(it) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(0.38f)
                            .fillMaxHeight()
                    ) {
                        CartSheet(
                            cartItems = state.cartItems,
                            customer = state.selectedCustomer,
                            pointBalances = state.customerPointBalances,
                            subtotal = state.subtotal,
                            totalPayable = state.totalPayable,
                            totalStockDeduction = state.totalStockDeduction,
                            onUpdatePaidQty = { pid, delta -> viewModel.updateItemPaidQty(pid, delta) },
                            onUpdateFreeQty = { pid, delta -> viewModel.updateItemFreeQty(pid, delta) },
                            onRemoveItem = { pid -> viewModel.removeItem(pid) },
                            onOpenCustomerPicker = { viewModel.toggleCustomerPicker(true) },
                            onOpenPointsEditor = { viewModel.togglePointsEditor(true) },
                            onHoldBill = { viewModel.holdBill() },
                            onClearCart = { viewModel.clearCart() },
                            onCheckout = { viewModel.confirmSale() }
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    ProductGrid(
                        products = filteredProducts,
                        onProductClick = { viewModel.addToCart(it) }
                    )
                }

                // Mobile Bottom Strip
                Surface(
                    color = CardWhite,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isMobileCartOpen = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(PastelMint),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "${state.cartItems.sumOf { it.totalQty }} รายการ (คิดเงิน ${state.totalPaidQty} ชิ้น)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (state.selectedCustomer != null) "ลูกค้า: ${state.selectedCustomer!!.name}" else "แตะเพื่อระบุลูกค้า & ดูบิล",
                                    fontSize = 11.sp,
                                    color = if (state.selectedCustomer != null) TextSecondary else PastelPeachDark
                                )
                            }
                        }

                        Button(
                            onClick = { isMobileCartOpen = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PastelPeach),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(Formatters.formatCurrency(state.totalPayable), fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                if (isMobileCartOpen) {
                    ModalBottomSheet(
                        onDismissRequest = { isMobileCartOpen = false },
                        containerColor = CardWhite,
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    ) {
                        CartSheet(
                            cartItems = state.cartItems,
                            customer = state.selectedCustomer,
                            pointBalances = state.customerPointBalances,
                            subtotal = state.subtotal,
                            totalPayable = state.totalPayable,
                            totalStockDeduction = state.totalStockDeduction,
                            onUpdatePaidQty = { pid, delta -> viewModel.updateItemPaidQty(pid, delta) },
                            onUpdateFreeQty = { pid, delta -> viewModel.updateItemFreeQty(pid, delta) },
                            onRemoveItem = { pid -> viewModel.removeItem(pid) },
                            onOpenCustomerPicker = { viewModel.toggleCustomerPicker(true) },
                            onOpenPointsEditor = { viewModel.togglePointsEditor(true) },
                            onHoldBill = {
                                viewModel.holdBill()
                                isMobileCartOpen = false
                            },
                            onClearCart = {
                                viewModel.clearCart()
                                isMobileCartOpen = false
                            },
                            onCheckout = {
                                isMobileCartOpen = false
                                viewModel.confirmSale()
                            },
                            modifier = Modifier.fillMaxHeight(0.85f)
                        )
                    }
                }
            }
        }
    }
}
