package com.sitticun.orderapp.ui.screens.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitticun.orderapp.OrderApplication
import com.sitticun.orderapp.ui.components.IosNavigationHeader
import com.sitticun.orderapp.ui.components.IosSearchBar
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

    val pendingCount = remember(outboxItems) {
        outboxItems.count {
            it.status == com.sitticun.orderapp.data.model.OutboxStatus.PENDING ||
                it.status == com.sitticun.orderapp.data.model.OutboxStatus.FAILED
        }
    }
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
                    colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("ตกลง", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = CardWhite,
            shape = RoundedCornerShape(18.dp)
        )
    }

    // Checkout Success Dialog
    if (state.checkoutSuccess != null) {
        val result = state.checkoutSuccess!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissSuccessDialog() },
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(IosGreenLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = StatusSuccess,
                        modifier = Modifier.size(30.dp)
                    )
                }
            },
            title = { Text("บันทึกการขายสำเร็จ", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "รหัสบิล: ${result.orderId}", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(
                        text = "ยอดรวม: ${Formatters.formatCurrency(result.total)} (${result.totalQty} ชิ้น)",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                    if (result.pointBalances.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = IosGroupedBackground,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "แต้มสะสมคงเหลือล่าสุด",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = IosBlue
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                for ((pid, bal) in result.pointBalances) {
                                    val pName = products.find { it.id.toString() == pid }?.name ?: "สินค้า $pid"
                                    Text(text = "• $pName: $bal แต้ม", fontSize = 12.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissSuccessDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("เสร็จสิ้น", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = CardWhite,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Main POS Layout
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(IosGroupedBackground)
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

            // iOS Style Navigation Header with Search Bar & Held Bills Capsule
            IosNavigationHeader(
                title = "ขายสินค้า",
                subtitle = if (state.selectedCustomer != null) "ลูกค้า: ${state.selectedCustomer!!.name}" else "เลือกรายการสินค้าเข้าตะกร้า",
                trailingContent = {
                    // Held Bills iOS Tinted Pill Button
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (heldBills.isNotEmpty()) IosIndigoLight else IosSystemGray6,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { viewModel.toggleHeldBillsDialog(true) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PauseCircle,
                                contentDescription = null,
                                tint = if (heldBills.isNotEmpty()) IosIndigo else TextSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "บิลพัก",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (heldBills.isNotEmpty()) IosIndigo else TextPrimary
                            )
                            if (heldBills.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(IosIndigo),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${heldBills.size}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                },
                bottomContent = {
                    IosSearchBar(
                        query = state.searchQuery,
                        onQueryChange = { viewModel.onSearchQueryChange(it) },
                        placeholder = "ค้นหาชื่อสินค้า หรือแท็ก...",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )

            // Products & Cart Split / Mobile View
            if (isTablet) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Box(modifier = Modifier.weight(0.60f)) {
                        ProductGrid(
                            products = filteredProducts,
                            onProductClick = { viewModel.addToCart(it) }
                        )
                    }

                    VerticalDivider(color = IosSeparator.copy(alpha = 0.45f), thickness = 0.5.dp)

                    Box(
                        modifier = Modifier
                            .weight(0.40f)
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

                    // Floating iOS Apple Store-style Checkout Bar at Bottom
                    val totalItems = state.cartItems.sumOf { it.totalQty }
                    Surface(
                        color = CardWhite,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(18.dp),
                                ambientColor = Color.Black.copy(alpha = 0.14f),
                                spotColor = Color.Black.copy(alpha = 0.14f)
                            )
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { isMobileCartOpen = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(if (totalItems > 0) IosBlue else IosSystemGray5),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingBag,
                                        contentDescription = null,
                                        tint = if (totalItems > 0) Color.White else IosSystemGray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (totalItems > 0) "$totalItems รายการในตะกร้า (คิดเงิน ${state.totalPaidQty})" else "ตะกร้ายังว่างอยู่",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        letterSpacing = (-0.15).sp
                                    )
                                    Text(
                                        text = if (state.selectedCustomer != null) "ลูกค้า: ${state.selectedCustomer!!.name}" else "แตะเพื่อเลือกลูกค้าและชำระเงิน",
                                        fontSize = 12.sp,
                                        color = if (state.selectedCustomer != null) IosBlue else TextSecondary
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (totalItems > 0) IosBlue else IosSystemGray5
                            ) {
                                Text(
                                    text = Formatters.formatCurrency(state.totalPayable),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (totalItems > 0) Color.White else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                if (isMobileCartOpen) {
                    ModalBottomSheet(
                        onDismissRequest = { isMobileCartOpen = false },
                        containerColor = IosGroupedBackground,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
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
                            modifier = Modifier.fillMaxHeight(0.88f)
                        )
                    }
                }
            }
        }
    }
}
