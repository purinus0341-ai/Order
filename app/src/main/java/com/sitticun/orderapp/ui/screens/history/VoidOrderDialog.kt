package com.sitticun.orderapp.ui.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sitticun.orderapp.data.model.OrderRecord
import com.sitticun.orderapp.ui.theme.*
import com.sitticun.orderapp.util.Formatters

@Composable
fun VoidOrderDialog(
    order: OrderRecord,
    isVoiding: Boolean,
    onConfirmVoid: (voidReason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val totalItems = order.items.sumOf { it.totalQty }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = StatusDanger,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "ยกเลิกบิลการขาย (Void Order)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusDanger
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFEBEE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "เลขที่บิล: ${order.orderNumber}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "ยอดเงิน: ${Formatters.formatCurrency(order.totalAmount)}", fontSize = 13.sp)
                        Text(
                            text = "ระบบจะคืนสต็อกสินค้าทั้งหมด $totalItems ชิ้น (รวมของแถม) และคืนแต้มสะสมของลูกค้ากลับสู่ระบบอัตโนมัติ",
                            fontSize = 12.sp,
                            color = StatusDanger,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                        errorText = null
                    },
                    label = { Text("เหตุผลในการยกเลิก (บังคับกรอก) *") },
                    placeholder = { Text("เช่น คืนสินค้า, ลูกค้าเปลี่ยนใจ, สแกนสินค้าผิด...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    isError = errorText != null
                )

                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = StatusDanger,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("ไม่ยกเลิก")
                    }

                    Button(
                        onClick = {
                            if (reason.isBlank()) {
                                errorText = "จำเป็นต้องระบุเหตุผลในการยกเลิกบิล"
                                return@Button
                            }
                            onConfirmVoid(reason)
                        },
                        enabled = !isVoiding,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDanger),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isVoiding) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("ยืนยันยกเลิกบิล", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
