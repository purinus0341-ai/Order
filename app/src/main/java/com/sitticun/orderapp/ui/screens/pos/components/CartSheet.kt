package com.sitticun.orderapp.ui.screens.pos.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
                    // Customer Pill (Mandatory)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (customer != null) PastelMintLight else PastelPeachLight,
                        modifier = Modifier.clickable(onClick = onOpenCustomerPicker)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = if (customer != null) PastelMintDark else PastelPeachDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = customer?.name ?: "เลือกลูกค้า (จำเป็น)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (customer != null) TextPrimary else PastelPeachDark
                            )
                        }
                    }

                    // Actions
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onOpenPointsEditor,
                            enabled = cartItems.isNotEmpty() && customer != null,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Stars, contentDescription = "แต้ม & ของแถม", tint = PastelPeach)
                        }

                        IconButton(
                            onClick = onHoldBill,
                            enabled = cartItems.isNotEmpty() && customer != null,
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
                            text = "แตะเลือกสินค้าจากด้านบนเพื่อเริ่มขาย",
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

            HorizontalDivider(color = BorderLight, thickness = 1.dp)

            // Calculation Breakdown Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PastelBackground)
                    .padding(14.dp)
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
                        horizontalArrangement = Arrangement.SpaceBetween
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
                            text = "ใช้ ${cartItems.sumOf { it.pointsUsed }} แต้ม",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PastelPeachDark
                        )
                    }
                }

                // Earned points preview (1 paid qty = 1 point per product)
                if (customer != null && cartItems.any { it.paidQty > 0 }) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "แต้มที่จะได้รับ (1 ชิ้น = 1 แต้ม)",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        Text(
                            text = "+${cartItems.sumOf { it.paidQty }} แต้ม",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PastelMintDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = BorderLight, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // Net Total Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ยอดรวมสุทธิ",
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
                    enabled = cartItems.isNotEmpty() && customer != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PastelMint,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (customer == null) "กรุณาเลือกลูกค้าก่อนคิดเงิน" else "บันทึกการขาย (${Formatters.formatCurrency(totalPayable)})",
                        fontSize = 15.sp,
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
    balance: Long,
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

            // Paid quantity stepper & price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "ซื้อ:", fontSize = 11.sp, color = TextSecondary)
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

                Text(
                    text = Formatters.formatCurrency(item.subtotal),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            // Free item indicator if any
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
                        Text(
                            text = "ของแถม ${item.freeQty} ขวด (ใช้ ${item.pointsUsed} แต้ม)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = PastelPeachDark
                        )
                    }
                    Text(text = "ฟรี", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PastelPeachDark)
                }
            }
        }
    }
}
