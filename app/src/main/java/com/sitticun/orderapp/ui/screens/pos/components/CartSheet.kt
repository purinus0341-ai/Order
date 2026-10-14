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
    redeemedPoints: Int,
    subtotal: Double,
    discountAmount: Double,
    totalPayable: Double,
    earnedPoints: Int,
    totalStockDeduction: Int,
    onUpdatePaidQty: (productId: String, delta: Int) -> Unit,
    onUpdateFreeQty: (productId: String, delta: Int) -> Unit,
    onRemoveItem: (productId: String) -> Unit,
    onOpenCustomerPicker: () -> Unit,
    onOpenFreeItemsEditor: () -> Unit,
    onOpenPointsEditor: () -> Unit,
    onHoldBill: () -> Unit,
    onClearCart: () -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = CardWhite
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Customer & Action Bar
            Surface(
                color = SurfaceVariantColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Customer Pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = CardWhite,
                        modifier = Modifier.clickable(onClick = onOpenCustomerPicker)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = if (customer != null) PastelLavender else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = customer?.name ?: "เลือกลูกค้า",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (customer != null) TextPrimary else TextSecondary
                            )
                            if (customer != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "(${customer.pointsBalance} แต้ม)",
                                    fontSize = 11.sp,
                                    color = PastelPeachDark,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Hold Bill & Clear Cart Actions
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onOpenFreeItemsEditor,
                            enabled = cartItems.isNotEmpty(),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = "ของแถม", tint = PastelPeach)
                        }

                        IconButton(
                            onClick = onHoldBill,
                            enabled = cartItems.isNotEmpty(),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.PauseCircle, contentDescription = "พักบิล", tint = PastelLavender)
                        }

                        IconButton(
                            onClick = onClearCart,
                            enabled = cartItems.isNotEmpty(),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "ล้างตะกร้า", tint = StatusDanger)
                        }
                    }
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
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ตะกร้าว่างเปล่า",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "แตะเลือกสินค้าจากฝั่งซ้ายเพื่อเริ่มขาย",
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
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cartItems, key = { it.product.id }) { item ->
                        CartItemRow(
                            item = item,
                            onUpdatePaid = { delta -> onUpdatePaidQty(item.product.id, delta) },
                            onUpdateFree = { delta -> onUpdateFreeQty(item.product.id, delta) },
                            onRemove = { onRemoveItem(item.product.id) }
                        )
                    }
                }
            }

            Divider(color = BorderLight, thickness = 1.dp)

            // Calculation Breakdown Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PastelBackground)
                    .padding(14.dp)
            ) {
                // Paid & Free Summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ยอดรวมสินค้า (${cartItems.sumOf { it.paidQty }} ชิ้น)",
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

                if (cartItems.any { it.freeQty > 0 }) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FreeBadge()
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ของแถม (${cartItems.sumOf { it.freeQty }} ชิ้น)",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = "฿0.00",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PastelPeachDark
                        )
                    }
                }

                // Points discount
                if (customer != null || redeemedPoints > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenPointsEditor),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Stars, contentDescription = null, tint = PastelPeach, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (redeemedPoints > 0) "ส่วนลดแต้มสะสม ($redeemedPoints แต้ม)" else "ใช้แต้มสะสมลดราคา",
                                fontSize = 12.sp,
                                color = if (redeemedPoints > 0) StatusSuccess else PastelLavender,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = if (redeemedPoints > 0) "-${Formatters.formatCurrency(discountAmount)}" else "เลือกแต้ม >",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (redeemedPoints > 0) StatusSuccess else PastelLavender
                        )
                    }
                }

                // Earned points preview
                if (customer != null && earnedPoints > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "แต้มที่จะได้รับเพิ่ม",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        Text(
                            text = "+$earnedPoints แต้ม",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PastelMintDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = BorderLight, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // Net Total Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ยอดสุทธิ",
                            fontSize = 15.sp,
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
                        color = PastelPeach
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Checkout Button
                Button(
                    onClick = onCheckout,
                    enabled = cartItems.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PastelMint,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ชำระเงิน (${Formatters.formatCurrency(totalPayable)})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CartItemRow(
    item: CartItem,
    onUpdatePaid: (Int) -> Unit,
    onUpdateFree: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, BorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Item Title & Remove
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.product.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "ลบ", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Paid Qty & Free Qty Steppers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Paid quantity stepper
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "คิดเงิน:", fontSize = 11.sp, color = TextSecondary)
                    IconButton(onClick = { onUpdatePaid(-1) }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Remove, contentDescription = "ลด", tint = PastelMint, modifier = Modifier.size(14.dp))
                    }
                    Text(
                        text = "${item.paidQty}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    IconButton(onClick = { onUpdatePaid(1) }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "เพิ่ม", tint = PastelMint, modifier = Modifier.size(14.dp))
                    }
                }

                // Line Total
                Text(
                    text = Formatters.formatCurrency(item.subtotal),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            // Free items row if any
            if (item.freeQty > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FreeBadge()
                        IconButton(onClick = { onUpdateFree(-1) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Remove, contentDescription = "ลดของแถม", tint = PastelPeach, modifier = Modifier.size(12.dp))
                        }
                        Text(
                            text = "${item.freeQty}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = PastelPeachDark
                        )
                        IconButton(onClick = { onUpdateFree(1) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "เพิ่มของแถม", tint = PastelPeach, modifier = Modifier.size(12.dp))
                        }
                    }
                    Text(text = "ฟรี", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PastelPeachDark)
                }
            }
        }
    }
}
