package com.sitticun.orderapp.ui.screens.pos.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stars
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
import com.sitticun.orderapp.data.model.CartItem
import com.sitticun.orderapp.data.model.Customer
import com.sitticun.orderapp.data.model.PointBalance
import com.sitticun.orderapp.ui.theme.*

@Composable
fun PointsEditorDialog(
    customer: Customer?,
    cartItems: List<CartItem>,
    pointBalances: Map<Long, Long>,
    onUpdatePointsAndFree: (productId: Long, pointsUsed: Int, freeQty: Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (customer == null) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "กรุณาเลือกลูกค้าสมาชิกก่อนแลกแต้ม",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = PastelMint)
                    ) {
                        Text("เข้าใจแล้ว", color = Color.White)
                    }
                }
            }
        }
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PastelPeachLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Stars, contentDescription = null, tint = PastelPeach, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "แต้มสะสม & แลกของแถม",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "ใช้แต้มเพื่อแลกของแถมตามหมวดสินค้า",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Customer info
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceVariantColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "ลูกค้า: ${customer.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = BorderLight, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(8.dp))

                if (cartItems.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("ยังไม่มีสินค้าในตะกร้า", color = TextSecondary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(cartItems, key = { it.product.id }) { item ->
                            val balance = pointBalances[item.product.id] ?: 0L
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceVariantColor)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.product.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = TextPrimary
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = PastelMintLight
                                        ) {
                                            Text(
                                                text = "มี $balance แต้ม",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PastelMintDark,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Stepper for points used
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "แต้มที่ใช้: ${item.pointsUsed} แต้ม", fontSize = 13.sp, color = TextPrimary)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    val newPoints = (item.pointsUsed - 1).coerceAtLeast(0)
                                                    // Also adjust freeQty accordingly if 0 points
                                                    val newFree = if (newPoints == 0) 0 else item.freeQty
                                                    onUpdatePointsAndFree(item.product.id, newPoints, newFree)
                                                },
                                                enabled = item.pointsUsed > 0,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = null, tint = if (item.pointsUsed > 0) PastelPeach else TextMuted)
                                            }
                                            Text(
                                                text = "${item.pointsUsed}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp)
                                            )
                                            IconButton(
                                                onClick = {
                                                    if (item.pointsUsed < balance) {
                                                        val newPoints = item.pointsUsed + 1
                                                        val newFree = if (item.freeQty == 0) 1 else item.freeQty
                                                        onUpdatePointsAndFree(item.product.id, newPoints, newFree)
                                                    }
                                                },
                                                enabled = item.pointsUsed < balance,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, tint = if (item.pointsUsed < balance) PastelMint else TextMuted)
                                            }
                                        }
                                    }

                                    // Stepper for free items
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "ของแถม: ${item.freeQty} ขวด", fontSize = 13.sp, color = PastelPeachDark, fontWeight = FontWeight.Medium)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    val newFree = (item.freeQty - 1).coerceAtLeast(0)
                                                    val newPoints = if (newFree == 0) 0 else item.pointsUsed
                                                    onUpdatePointsAndFree(item.product.id, newPoints, newFree)
                                                },
                                                enabled = item.freeQty > 0,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = null, tint = if (item.freeQty > 0) PastelPeach else TextMuted)
                                            }
                                            Text(
                                                text = "${item.freeQty}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp)
                                            )
                                            IconButton(
                                                onClick = {
                                                    val newFree = item.freeQty + 1
                                                    val newPoints = if (item.pointsUsed == 0 && balance > 0) 1 else item.pointsUsed
                                                    onUpdatePointsAndFree(item.product.id, newPoints, newFree)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, tint = PastelMint)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PastelMint, contentColor = Color.White)
                ) {
                    Text("ตกลง", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
