package com.sitticun.orderapp.ui.screens.stock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sitticun.orderapp.data.model.Product
import com.sitticun.orderapp.ui.theme.*

@Composable
fun StockAdjustmentDialog(
    product: Product,
    isAdjusting: Boolean,
    onConfirm: (newQty: Int, reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var newQtyText by remember { mutableStateOf(product.stockQty.toString()) }
    var reason by remember { mutableStateOf("นับสต็อกประจำงวด (Cycle Count)") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val reasons = listOf(
        "นับสต็อกประจำงวด (Cycle Count)",
        "รับสินค้าเข้าคลัง (Restock)",
        "สินค้าชำรุด / แตกหัก",
        "สินค้าหมดอายุ",
        "ของแถม / ส่งเสริมการขาย",
        "ปรับปรุงยอดผิดพลาด"
    )

    val newQty = newQtyText.toIntOrNull() ?: product.stockQty
    val delta = newQty - product.stockQty

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Inventory,
                        contentDescription = null,
                        tint = PastelMint,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "ปรับปรุงยอดสต็อก (Snapshot)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceVariantColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(text = "สต็อกปัจจุบัน: ${product.stockQty} ชิ้น", fontSize = 12.sp, color = TextSecondary)
                            Text(text = "แจ้งเตือนเมื่อต่ำกว่า: ${product.minStockAlert}", fontSize = 12.sp, color = TextMuted)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = newQtyText,
                    onValueChange = {
                        newQtyText = it.filter { c -> c.isDigit() }
                        errorText = null
                    },
                    label = { Text("ยอดสต็อกใหม่ที่นับได้") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (delta >= 0) "ส่วนต่าง: +$delta ชิ้น (เพิ่มขึ้น)" else "ส่วนต่าง: $delta ชิ้น (ลดลง)",
                    fontSize = 12.sp,
                    color = if (delta >= 0) PastelMintDark else StatusDanger,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(text = "เลือกเหตุผลในการปรับปรุง:", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(reasons) { r ->
                        val isSelected = reason == r
                        FilterChip(
                            selected = isSelected,
                            onClick = { reason = r },
                            label = { Text(r, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PastelMint,
                                selectedLabelColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("เหตุผลที่ระบุ (บันทึกลง Snapshot)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                if (errorText != null) {
                    Text(text = errorText!!, color = StatusDanger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Text("ยกเลิก")
                    }

                    Button(
                        onClick = {
                            val target = newQtyText.toIntOrNull()
                            if (target == null || target < 0) {
                                errorText = "กรุณาระบุยอดสต็อกที่ถูกต้อง"
                                return@Button
                            }
                            if (reason.isBlank()) {
                                errorText = "จำเป็นต้องระบุเหตุผล"
                                return@Button
                            }
                            onConfirm(target, reason)
                        },
                        enabled = !isAdjusting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PastelMint),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isAdjusting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("บันทึกปรับสต็อก", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
