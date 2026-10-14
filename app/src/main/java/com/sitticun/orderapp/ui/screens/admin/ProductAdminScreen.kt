package com.sitticun.orderapp.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.sitticun.orderapp.ui.components.StockBadge
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
                text = "จัดการสินค้าในระบบ (${products.size} รายการ)",
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
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = prod.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                if (!prod.isActive) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFFEBEE)) {
                                        Text("ปิดการขาย", color = StatusDanger, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "ราคา: ${Formatters.formatCurrency(prod.price)} • ทุน: ${Formatters.formatCurrency(prod.cost)} • สต็อก: ${prod.stockQty} ชิ้น",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = "SKU: ${prod.sku.ifBlank { "-" }} • บาร์โค้ด: ${prod.barcode.ifBlank { "-" }} • หมวด: ${prod.category}",
                                fontSize = 11.sp,
                                color = TextMuted
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
    var sku by remember { mutableStateOf(product?.sku ?: "") }
    var barcode by remember { mutableStateOf(product?.barcode ?: "") }
    var category by remember { mutableStateOf(product?.category ?: "ทั่วไป") }
    var priceText by remember { mutableStateOf(product?.price?.toString() ?: "0.0") }
    var costText by remember { mutableStateOf(product?.cost?.toString() ?: "0.0") }
    var stockText by remember { mutableStateOf(product?.stockQty?.toString() ?: "10") }
    var alertText by remember { mutableStateOf(product?.minStockAlert?.toString() ?: "5") }
    var isActive by remember { mutableStateOf(product?.isActive ?: true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp).padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (product == null) "เพิ่มสินค้าใหม่" else "แก้ไขข้อมูลสินค้า",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("ชื่อสินค้า *") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = priceText,
                                onValueChange = { priceText = it },
                                label = { Text("ราคาขาย (฿) *") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = costText,
                                onValueChange = { costText = it },
                                label = { Text("ต้นทุน (฿)") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = stockText,
                                onValueChange = { stockText = it.filter { c -> c.isDigit() } },
                                label = { Text("จำนวนสต็อก") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = alertText,
                                onValueChange = { alertText = it.filter { c -> c.isDigit() } },
                                label = { Text("เตือนเมื่อต่ำกว่า") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = barcode,
                                onValueChange = { barcode = it },
                                label = { Text("บาร์โค้ด") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = sku,
                                onValueChange = { sku = it },
                                label = { Text("SKU") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("หมวดหมู่สินค้า") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    item {
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
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Text("ยกเลิก")
                    }

                    Button(
                        onClick = {
                            val price = priceText.toDoubleOrNull() ?: 0.0
                            val cost = costText.toDoubleOrNull() ?: 0.0
                            val stock = stockText.toIntOrNull() ?: 0
                            val alert = alertText.toIntOrNull() ?: 5
                            val payload = ProductUpsertPayload(
                                id = product?.id,
                                name = name.trim(),
                                sku = sku.trim(),
                                barcode = barcode.trim(),
                                category = category.trim(),
                                price = price,
                                cost = cost,
                                stockQty = stock,
                                minStockAlert = alert,
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
