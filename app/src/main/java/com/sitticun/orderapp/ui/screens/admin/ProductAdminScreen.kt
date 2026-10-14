package com.sitticun.orderapp.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
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
import com.sitticun.orderapp.data.model.ProductUpsertPayload
import com.sitticun.orderapp.ui.theme.*
import com.sitticun.orderapp.util.Formatters

@Composable
fun ProductAdminScreen(
    products: List<Product>,
    showDialog: Boolean,
    productToEdit: Product?,
    isSaving: Boolean,
    onOpenAdd: () -> Unit,
    onOpenEdit: (Product) -> Unit,
    onDismissDialog: () -> Unit,
    onSaveProduct: (ProductUpsertPayload) -> Unit,
    onToggleActive: (Product) -> Unit
) {
    if (showDialog) {
        ProductEditDialog(
            product = productToEdit,
            isSaving = isSaving,
            onSave = onSaveProduct,
            onDismiss = onDismissDialog
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "รายการสินค้าในระบบ (${products.size} รายการ)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextPrimary
            )

            Button(
                onClick = onOpenAdd,
                colors = ButtonDefaults.buttonColors(containerColor = PastelMint),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("เพิ่มสินค้า", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(products, key = { it.id }) { prod ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = prod.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                if (!prod.isActive) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFFEBEE)) {
                                        Text("ปิดการขาย", color = StatusDanger, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!prod.tag.isNullOrBlank()) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = PastelMintLight) {
                                        Text(text = "แท็ก: ${prod.tag}", fontSize = 11.sp, color = PastelMintDark, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                                if (!prod.volume.isNullOrBlank()) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = PastelLavenderLight) {
                                        Text(text = "ขนาด: ${prod.volume}", fontSize = 11.sp, color = PastelLavenderDark, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ราคาขาย: ${Formatters.formatCurrency(prod.price)} • ID: ${prod.id}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PastelPeach
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Switch(
                                checked = prod.isActive,
                                onCheckedChange = { onToggleActive(prod) },
                                colors = SwitchDefaults.colors(checkedThumbColor = PastelMint, checkedTrackColor = PastelMintLight)
                            )

                            IconButton(onClick = { onOpenEdit(prod) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "แก้ไข", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductEditDialog(
    product: Product?,
    isSaving: Boolean,
    onSave: (ProductUpsertPayload) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(product?.name ?: "") }
    var tag by remember { mutableStateOf(product?.tag ?: "") }
    var volume by remember { mutableStateOf(product?.volume ?: "") }
    var priceText by remember { mutableStateOf(product?.price?.toString() ?: "15.0") }
    var isActive by remember { mutableStateOf(product?.isActive ?: true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (product == null) "เพิ่มสินค้าใหม่" else "แก้ไขข้อมูลสินค้า",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ชื่อสินค้า *") },
                    placeholder = { Text("เช่น น้ำถัง 18.9 ลิตร") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tag,
                        onValueChange = { tag = it },
                        label = { Text("แท็กสินค้า (Tag)") },
                        placeholder = { Text("18.9L") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = volume,
                        onValueChange = { volume = it },
                        label = { Text("ขนาด / ปริมาตร") },
                        placeholder = { Text("18.9 ลิตร") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("ราคาขาย (฿) *") },
                    placeholder = { Text("15.0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("เปิดขายในระบบ POS (is_active)", fontSize = 13.sp)
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = PastelMint, checkedTrackColor = PastelMintLight)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Text("ยกเลิก")
                    }

                    Button(
                        onClick = {
                            val price = priceText.toDoubleOrNull() ?: 0.0
                            val payload = ProductUpsertPayload(
                                id = product?.id,
                                name = name.trim(),
                                tag = tag.trim().ifBlank { null },
                                volume = volume.trim().ifBlank { null },
                                price = price,
                                isActive = isActive
                            )
                            onSave(payload)
                        },
                        enabled = name.isNotBlank() && !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = PastelMint),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("บันทึกสินค้า", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
