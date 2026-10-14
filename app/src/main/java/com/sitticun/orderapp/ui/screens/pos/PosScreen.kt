package com.sitticun.orderapp.ui.screens.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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

    // Categories list
    val categories = remember(products) {
        listOf("ทั้งหมด") + products.map { it.category }.distinct()
    }

    val filteredProducts = remember(products, state.searchQuery, state.selectedCategory) {
        products.filter { p ->
            val matchCategory = state.selectedCategory == "ทั้งหมด" || p.category == state.selectedCategory
            val matchQuery = state.searchQuery.isBlank() ||
                    p.name.contains(state.searchQuery, ignoreCase = true) ||
                    p.barcode.contains(state.searchQuery, ignoreCase = true) ||
                    p.sku.contains(state.searchQuery, ignoreCase = true)
            matchCategory && matchQuery && p.isActive
        }
    }

    // Dialogs
    if (state.showCustomerPicker) {
        CustomerPickerDialog(
            customers = customers,
            selectedCustomer = state.selectedCustomer,
            onSelectCustomer = { viewModel.selectCustomer(it) },
            onDismiss = { viewModel.toggleCustomerPicker(false) }
        )
    }

    if (state.showFreeItemsEditor) {
        FreeItemsEditorDialog(
            cartItems = state.cartItems,
            onUpdateFreeQty = { pid, delta -> viewModel.updateItemFreeQty(pid, delta) },
            onDismiss = { viewModel.toggleFreeItemsEditor(false) }
        )
    }

    if (state.showPointsEditor) {
        PointsEditorDialog(
            customer = state.selectedCustomer,
            currentRedeemedPoints = state.redeemedPoints,
            subtotal = state.subtotal,
            onConfirmPoints = { points -> viewModel.setRedeemedPoints(points) },
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

    // Payment Dialog
    if (state.showPaymentDialog) {
        PaymentMethodDialog(
            totalAmount = state.totalPayable,
            selectedMethod = state.selectedPaymentMethod,
            isProcessing = state.isCheckingOut,
            onSelectMethod = { viewModel.setPaymentMethod(it) },
            onConfirm = { viewModel.confirmSale() },
            onDismiss = { viewModel.dismissPaymentDialog() }
        )
    }

    // Checkout Success Dialog
    if (state.checkoutSuccessOrder != null) {
        val order = state.checkoutSuccessOrder!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissSuccessDialog() },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(40.dp)) },
            title = { Text("ชำระเงินสำเร็จ!", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column {
                    Text(text = "เลขที่บิล: ${order.orderNumber ?: order.orderId ?: "-"}", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = order.message, fontSize = 13.sp, color = TextSecondary)
                    if (order.updatedCustomerPoints != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "แต้มสะสมคงเหลือล่าสุด: ${Formatters.formatPoints(order.updatedCustomerPoints)}",
                            fontSize = 13.sp,
                            color = PastelMintDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissSuccessDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = PastelMint)
                ) {
                    Text("ตกลง / พิมพ์ใบเสร็จ", color = Color.White)
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
            // Offline Sync Banner
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
                    // Search bar
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        placeholder = { Text("ค้นหาสินค้า, บาร์โค้ด, SKU...", fontSize = 13.sp) },
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

                    // Held bills badge button
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
                            Text("บิลที่พักไว้", fontSize = 12.sp, color = TextPrimary)
                        }
                    }
                }
            }

            // Categories horizontal tabs
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    val isSelected = state.selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onCategorySelect(category) },
                        label = { Text(category, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PastelMint,
                            selectedLabelColor = Color.White,
                            containerColor = CardWhite,
                            labelColor = TextPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) PastelMint else BorderLight
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            // Products & Cart Layout
            if (isTablet) {
                // Wide Tablet 2-column view
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Box(modifier = Modifier.weight(0.65f)) {
                        ProductGrid(
                            products = filteredProducts,
                            onProductClick = { viewModel.addToCart(it) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight()
                    ) {
                        CartSheet(
                            cartItems = state.cartItems,
                            customer = state.selectedCustomer,
                            redeemedPoints = state.redeemedPoints,
                            subtotal = state.subtotal,
                            discountAmount = state.discountAmount,
                            totalPayable = state.totalPayable,
                            earnedPoints = state.earnedPoints,
                            totalStockDeduction = state.totalStockDeduction,
                            onUpdatePaidQty = { pid, delta -> viewModel.updateItemPaidQty(pid, delta) },
                            onUpdateFreeQty = { pid, delta -> viewModel.updateItemFreeQty(pid, delta) },
                            onRemoveItem = { pid -> viewModel.removeItem(pid) },
                            onOpenCustomerPicker = { viewModel.toggleCustomerPicker(true) },
                            onOpenFreeItemsEditor = { viewModel.toggleFreeItemsEditor(true) },
                            onOpenPointsEditor = { viewModel.togglePointsEditor(true) },
                            onHoldBill = { viewModel.holdBill() },
                            onClearCart = { viewModel.clearCart() },
                            onCheckout = { viewModel.openPaymentDialog() }
                        )
                    }
                }
            } else {
                // Phone view: Products on top, bottom checkout bar
                Box(modifier = Modifier.weight(1f)) {
                    ProductGrid(
                        products = filteredProducts,
                        onProductClick = { viewModel.addToCart(it) }
                    )
                }

                // Mobile Bottom Cart Strip
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
                                    text = if (state.selectedCustomer != null) "ลูกค้า: ${state.selectedCustomer!!.name}" else "แตะเพื่อดูรายละเอียดบิล",
                                    fontSize = 11.sp,
                                    color = TextSecondary
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

                // Mobile Modal Bottom Sheet
                if (isMobileCartOpen) {
                    ModalBottomSheet(
                        onDismissRequest = { isMobileCartOpen = false },
                        containerColor = CardWhite,
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    ) {
                        CartSheet(
                            cartItems = state.cartItems,
                            customer = state.selectedCustomer,
                            redeemedPoints = state.redeemedPoints,
                            subtotal = state.subtotal,
                            discountAmount = state.discountAmount,
                            totalPayable = state.totalPayable,
                            earnedPoints = state.earnedPoints,
                            totalStockDeduction = state.totalStockDeduction,
                            onUpdatePaidQty = { pid, delta -> viewModel.updateItemPaidQty(pid, delta) },
                            onUpdateFreeQty = { pid, delta -> viewModel.updateItemFreeQty(pid, delta) },
                            onRemoveItem = { pid -> viewModel.removeItem(pid) },
                            onOpenCustomerPicker = { viewModel.toggleCustomerPicker(true) },
                            onOpenFreeItemsEditor = { viewModel.toggleFreeItemsEditor(true) },
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
                                viewModel.openPaymentDialog()
                            },
                            modifier = Modifier.fillMaxHeight(0.85f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentMethodDialog(
    totalAmount: Double,
    selectedMethod: String,
    isProcessing: Boolean,
    onSelectMethod: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "ยืนยันการชำระเงิน",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "ยอดที่ต้องชำระ: ${Formatters.formatCurrency(totalAmount)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PastelPeach
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "เลือกช่องทางการชำระ:", fontSize = 13.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                val methods = listOf(
                    Triple("CASH", "เงินสด (Cash)", Icons.Default.Payments),
                    Triple("QR", "สแกน QR / พร้อมเพย์", Icons.Default.QrCode),
                    Triple("TRANSFER", "โอนเงินผ่านธนาคาร", Icons.Default.AccountBalance)
                )

                methods.forEach { (code, title, icon) ->
                    val isSelected = selectedMethod == code
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectMethod(code) }
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) PastelMintLight else SurfaceVariantColor,
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, PastelMint) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = null, tint = if (isSelected) PastelMintDark else TextSecondary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = title,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("ยกเลิก")
                    }

                    Button(
                        onClick = onConfirm,
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PastelMint,
                            contentColor = Color.White
                        )
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("เสร็จสิ้นการขาย", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
