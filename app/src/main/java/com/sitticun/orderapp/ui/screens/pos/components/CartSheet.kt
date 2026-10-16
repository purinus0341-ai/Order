package com.sitticun.orderapp.ui.screens.pos.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.sitticun.orderapp.data.model.CartItem
import com.sitticun.orderapp.data.model.Customer
import com.sitticun.orderapp.ui.components.FreeBadge
import com.sitticun.orderapp.ui.theme.*
import com.sitticun.orderapp.util.Formatters

@Composable
fun CartSheet(
    cartItems: List<CartItem>,
    customer: Customer?,
    pointBalances: Map<Long, Long>,
    subtotal: Double,
    totalPayable: Double,
    totalStockDeduction: Int,
    onUpdatePaidQty: (productId: Long, delta: Int) -> Unit,
    onUpdateFreeQty: (productId: Long, delta: Int) -> Unit,
    onRemoveItem: (productId: Long) -> Unit,
    onOpenCustomerPicker: () -> Unit,
    onOpenPointsEditor: () -> Unit,
    onHoldBill: () -> Unit,
    onClearCart: () -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = IosGroupedBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Customer & Action Bar (iOS Grouped Header)
            Surface(
                color = CardWhite,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Customer Pill (Mandatory)
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (customer != null) IosBlueLight else IosOrangeLight,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onOpenCustomerPicker)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (customer != null) IosBlue else IosOrangeDark,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = customer?.name ?: "เลือกลูกค้า (จำเป็น)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (customer != null) IosBlue else IosOrangeDark
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = if (customer != null) IosBlue else IosOrangeDark,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        // iOS Circular Tinted Action Buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IosActionCircleButton(
                                icon = Icons.Default.Stars,
                                tint = IosOrange,
                                bgColor = IosOrangeLight,
                                enabled = cartItems.isNotEmpty() && customer != null,
                                onClick = onOpenPointsEditor
                            )

                            IosActionCircleButton(
                                icon = Icons.Default.PauseCircle,
                                tint = IosIndigo,
                                bgColor = IosIndigoLight,
                                enabled = cartItems.isNotEmpty() && customer != null,
                                onClick = onHoldBill
                            )

                            IosActionCircleButton(
                                icon = Icons.Default.DeleteOutline,
                                tint = StatusDanger,
                                bgColor = IosRedLight,
                                enabled = cartItems.isNotEmpty(),
                                onClick = onClearCart
                            )
                        }
                    }
                    HorizontalDivider(color = IosSeparator.copy(alpha = 0.45f), thickness = 0.5.dp)
                }
            }

            // Items List
            if (cartItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(IosSystemGray5),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingBag,
                                contentDescription = null,
                                tint = IosSystemGray,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "ตะกร้าว่างเปล่า",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "แตะเลือกรายการสินค้าเพื่อเริ่มต้นขาย",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cartItems, key = { it.product.id }) { item ->
                        val balance = pointBalances[item.product.id] ?: 0L
                        CartItemRow(
                            item = item,
                            balance = balance,
                            onUpdatePaid = { delta -> onUpdatePaidQty(item.product.id, delta) },
                            onUpdateFree = { delta -> onUpdateFreeQty(item.product.id, delta) },
                            onRemove = { onRemoveItem(item.product.id) }
                        )
                    }
                }
            }

            // Apple Pay-inspired Summary & Checkout Footer
            Surface(
                color = CardWhite,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    // Paid summary
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "สินค้าคิดเงิน (${cartItems.sumOf { it.paidQty }} ชิ้น)",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = Formatters.formatCurrency(subtotal),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }

                    // Free items summary
                    val totalFree = cartItems.sumOf { it.freeQty }
                    if (totalFree > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FreeBadge()
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ของแถมแลกแต้ม ($totalFree ชิ้น)",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            Text(
                                text = "-${cartItems.sumOf { it.pointsUsed }} แต้ม",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IosOrangeDark
                            )
                        }
                    }

                    // Earned points preview
                    if (customer != null && cartItems.any { it.paidQty > 0 }) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "แต้มสะสมที่จะได้รับ",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                            Text(
                                text = "+${cartItems.sumOf { it.paidQty }} แต้ม",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusSuccess
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = IosSeparatorLight, thickness = 0.6.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Net Total Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ยอดชำระสุทธิ",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "ตัดสต็อกรวม $totalStockDeduction ชิ้น",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        Text(
                            text = Formatters.formatCurrency(totalPayable),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = IosBlue,
                            letterSpacing = (-0.5).sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Apple Pay Style Primary Checkout Button
                    Button(
                        onClick = onCheckout,
                        enabled = cartItems.isNotEmpty() && customer != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IosBlue,
                            contentColor = Color.White,
                            disabledContainerColor = IosSystemGray5,
                            disabledContentColor = IosSystemGray
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (customer == null) "เลือกลูกค้าก่อนชำระเงิน" else "ยืนยันการขาย • ${Formatters.formatCurrency(totalPayable)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IosActionCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    bgColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (enabled) bgColor else IosSystemGray6)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else IosSystemGray2,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun CartItemRow(
    item: CartItem,
    balance: Long,
    onUpdatePaid: (Int) -> Unit,
    onUpdateFree: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardWhite,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.product.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(IosSystemGray6)
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "ลบ",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Paid quantity iOS capsule stepper & price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = IosGroupedBackground
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onUpdatePaid(-1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "ลด", tint = IosBlue, modifier = Modifier.size(15.dp))
                        }
                        Text(
                            text = "${item.paidQty}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onUpdatePaid(1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "เพิ่ม", tint = IosBlue, modifier = Modifier.size(15.dp))
                        }
                    }
                }

                Text(
                    text = Formatters.formatCurrency(item.subtotal),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            // Free item indicator if any
            if (item.freeQty > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FreeBadge()
                        Text(
                            text = "แถม ${item.freeQty} ชิ้น (ใช้ ${item.pointsUsed} แต้ม)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = IosOrangeDark
                        )
                    }
                    Text(text = "ฟรี", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IosOrangeDark)
                }
            }
        }
    }
}
