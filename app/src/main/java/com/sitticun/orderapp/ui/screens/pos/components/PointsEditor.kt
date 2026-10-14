package com.sitticun.orderapp.ui.screens.pos.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.sitticun.orderapp.data.model.Customer
import com.sitticun.orderapp.ui.theme.*
import com.sitticun.orderapp.util.Formatters

@Composable
fun PointsEditorDialog(
    customer: Customer?,
    currentRedeemedPoints: Int,
    subtotal: Double,
    onConfirmPoints: (Int) -> Unit,
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
                        text = "กรุณาเลือกลูกค้าสมาชิกก่อนใช้แต้ม",
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

    val maxPointsAllowed = minOf(customer.pointsBalance, subtotal.toInt())
    var inputPoints by remember { mutableFloatStateOf(currentRedeemedPoints.toFloat().coerceIn(0f, maxPointsAllowed.toFloat())) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
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
                        Text(
                            text = "แลกแต้มสะสมลดราคา",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Customer Info Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceVariantColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = customer.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                            Text(text = "ยอดซื้อในบิลนี้: ${Formatters.formatCurrency(subtotal)}", fontSize = 12.sp, color = TextSecondary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "แต้มที่มี", fontSize = 11.sp, color = TextSecondary)
                            Text(
                                text = Formatters.formatPoints(customer.pointsBalance),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = PastelMintDark
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Redeemed Points Indicator
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "แต้มที่ใช้แลก",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "${inputPoints.toInt()} แต้ม",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = PastelPeach
                    )
                    Text(
                        text = "ส่วนลด: -${Formatters.formatCurrency(inputPoints.toDouble())}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusSuccess
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (maxPointsAllowed > 0) {
                    Slider(
                        value = inputPoints,
                        onValueChange = { inputPoints = it },
                        valueRange = 0f..maxPointsAllowed.toFloat(),
                        steps = if (maxPointsAllowed > 1) maxPointsAllowed - 1 else 0,
                        colors = SliderDefaults.colors(
                            thumbColor = PastelPeach,
                            activeTrackColor = PastelPeach,
                            inactiveTrackColor = BorderLight
                        )
                    )
                } else {
                    Text(
                        text = "ไม่สามารถใช้แต้มได้ (แต้มไม่พอหรือยอดบิลเป็นศูนย์)",
                        fontSize = 12.sp,
                        color = StatusDanger,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { inputPoints = 0f },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("ไม่ใช้", fontSize = 12.sp)
                    }

                    if (maxPointsAllowed >= 50) {
                        OutlinedButton(
                            onClick = { inputPoints = 50f },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("50 แต้ม", fontSize = 12.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = { inputPoints = maxPointsAllowed.toFloat() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("ใช้สูงสุด", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
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
                        onClick = { onConfirmPoints(inputPoints.toInt()) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PastelMint,
                            contentColor = Color.White
                        )
                    ) {
                        Text("ยืนยันแต้ม", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
