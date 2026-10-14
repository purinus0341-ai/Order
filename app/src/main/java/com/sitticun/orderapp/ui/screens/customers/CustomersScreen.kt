package com.sitticun.orderapp.ui.screens.customers

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitticun.orderapp.data.model.Customer
import com.sitticun.orderapp.ui.components.OrderStatusBadge
import com.sitticun.orderapp.ui.theme.*
import com.sitticun.orderapp.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    viewModel: CustomersViewModel = viewModel(factory = CustomersViewModel.Factory())
) {
    val state by viewModel.uiState.collectAsState()
    val customers by viewModel.customers.collectAsState()

    val filteredCustomers = remember(customers, state.searchQuery) {
        if (state.searchQuery.isBlank()) customers
        else {
            customers.filter {
                it.name.contains(state.searchQuery, ignoreCase = true) ||
                        it.phone.contains(state.searchQuery, ignoreCase = true)
            }
        }
    }

    // Add / Edit Customer Dialog
    if (state.showUpsertDialog) {
        CustomerUpsertDialog(
            customer = state.customerToEdit,
            isSaving = state.isSaving,
            onSave = { name, phone, email, notes -> viewModel.saveCustomer(name, phone, email, notes) },
            onDismiss = { viewModel.dismissUpsertDialog() }
        )
    }

    // Adjust Points Dialog
    if (state.showPointsDialog && state.selectedCustomer != null) {
        AdjustPointsDialog(
            customer = state.selectedCustomer!!,
            isSaving = state.isSaving,
            onConfirm = { points, reason -> viewModel.adjustPoints(points, reason) },
            onDismiss = { viewModel.dismissPointsDialog() }
        )
    }

    // Toast-like Snackbar/Alert for messages
    if (state.message != null || state.errorMessage != null) {
        val isErr = state.errorMessage != null
        LaunchedEffect(state.message, state.errorMessage) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddCustomer() },
                containerColor = PastelMint,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "เพิ่มลูกค้า")
            }
        },
        containerColor = PastelBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header Search Bar
            Surface(
                color = CardWhite,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        placeholder = { Text("ค้นหาชื่อลูกค้า หรือ เบอร์โทรศัพท์...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PastelMint) },
                        trailingIcon = {
                            if (state.searchQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PastelMint,
                            unfocusedBorderColor = BorderLight
                        )
                    )

                    Button(
                        onClick = { viewModel.openAddCustomer() },
                        colors = ButtonDefaults.buttonColors(containerColor = PastelMint),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("เพิ่มลูกค้า", fontSize = 13.sp)
                    }
                }
            }

            if (state.message != null) {
                Surface(
                    color = PastelMintLight,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = state.message!!,
                        color = PastelMintDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (state.errorMessage != null) {
                Surface(
                    color = Color(0xFFFFEBEE),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = state.errorMessage!!,
                        color = StatusDanger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Customer List and Detail split
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                val isWide = maxWidth >= 760.dp

                if (isWide) {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(modifier = Modifier.weight(0.55f)) {
                            CustomerList(
                                customers = filteredCustomers,
                                selectedCustomerId = state.selectedCustomer?.id,
                                onSelect = { viewModel.selectCustomer(it) },
                                onEdit = { viewModel.openEditCustomer(it) },
                                onAdjustPoints = { viewModel.openAdjustPoints(it) }
                            )
                        }

                        Box(modifier = Modifier.weight(0.45f)) {
                            CustomerDetailPane(
                                customer = state.selectedCustomer,
                                orders = state.customerOrders,
                                onAdjustPoints = { state.selectedCustomer?.let { viewModel.openAdjustPoints(it) } }
                            )
                        }
                    }
                } else {
                    CustomerList(
                        customers = filteredCustomers,
                        selectedCustomerId = state.selectedCustomer?.id,
                        onSelect = { viewModel.selectCustomer(it) },
                        onEdit = { viewModel.openEditCustomer(it) },
                        onAdjustPoints = { viewModel.openAdjustPoints(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerList(
    customers: List<Customer>,
    selectedCustomerId: String?,
    onSelect: (Customer) -> Unit,
    onEdit: (Customer) -> Unit,
    onAdjustPoints: (Customer) -> Unit
) {
    if (customers.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "ไม่พบข้อมูลลูกค้า", color = TextSecondary, fontSize = 14.sp)
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(customers, key = { it.id }) { customer ->
                val isSelected = customer.id == selectedCustomerId
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) PastelMintLight else CardWhite),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, PastelMint)
                    else androidx.compose.foundation.BorderStroke(0.8.dp, BorderLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(customer) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(PastelMint),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = customer.name.take(1),
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = customer.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = customer.phone.ifBlank { "ไม่มีเบอร์โทร" },
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                if (!customer.notes.isNullOrBlank()) {
                                    Text(
                                        text = customer.notes,
                                        fontSize = 11.sp,
                                        color = TextMuted,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Points Badge & Actions
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PastelPeachLight,
                                modifier = Modifier.clickable { onAdjustPoints(customer) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Stars, contentDescription = null, tint = PastelPeach, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = Formatters.formatPoints(customer.pointsBalance),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PastelPeachDark
                                    )
                                }
                            }

                            IconButton(onClick = { onEdit(customer) }, modifier = Modifier.size(32.dp)) {
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
private fun CustomerDetailPane(
    customer: Customer?,
    orders: List<com.sitticun.orderapp.data.model.OrderRecord>,
    onAdjustPoints: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        modifier = Modifier.fillMaxSize()
    ) {
        if (customer == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "เลือกข้อมูลลูกค้าเพื่อดูประวัติการซื้อ", color = TextSecondary, fontSize = 14.sp)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = customer.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = customer.phone, fontSize = 13.sp, color = TextSecondary)
                    }

                    Button(
                        onClick = onAdjustPoints,
                        colors = ButtonDefaults.buttonColors(containerColor = PastelPeach),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("ปรับยอดแต้ม", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = BorderLight, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(14.dp))

                Text(text = "ประวัติการสั่งซื้อ (${orders.size} ครั้ง)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))

                if (orders.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(text = "ยังไม่มีประวัติการซื้อ", fontSize = 13.sp, color = TextMuted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(orders, key = { it.id }) { ord ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceVariantColor)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = ord.orderNumber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(text = Formatters.formatIsoDateTime(ord.createdAt), fontSize = 11.sp, color = TextMuted)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = Formatters.formatCurrency(ord.totalAmount),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = PastelPeach
                                        )
                                        OrderStatusBadge(status = ord.status)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerUpsertDialog(
    customer: Customer?,
    isSaving: Boolean,
    onSave: (name: String, phone: String, email: String?, notes: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(customer?.name ?: "") }
    var phone by remember { mutableStateOf(customer?.phone ?: "") }
    var email by remember { mutableStateOf(customer?.email ?: "") }
    var notes by remember { mutableStateOf(customer?.notes ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (customer == null) "เพิ่มสมาชิกใหม่" else "แก้ไขข้อมูลสมาชิก",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ชื่อ-นามสกุล *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("เบอร์โทรศัพท์") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("อีเมล (ถ้ามี)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("บันทึกเพิ่มเติม") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Text("ยกเลิก")
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) onSave(name, phone, email, notes)
                        },
                        enabled = name.isNotBlank() && !isSaving,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PastelMint),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("บันทึก", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AdjustPointsDialog(
    customer: Customer,
    isSaving: Boolean,
    onConfirm: (pointsChange: Int, reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var isAddition by remember { mutableStateOf(true) }
    var pointsText by remember { mutableStateOf("50") }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "ปรับปรุงแต้มสะสม", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "ลูกค้า: ${customer.name} (คงเหลือ: ${customer.pointsBalance} แต้ม)", fontSize = 13.sp, color = TextSecondary)

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle Add vs Deduct
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { isAddition = true },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isAddition) PastelMint else SurfaceVariantColor),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("เพิ่มแต้ม (+)", color = if (isAddition) Color.White else TextPrimary)
                    }

                    Button(
                        onClick = { isAddition = false },
                        colors = ButtonDefaults.buttonColors(containerColor = if (!isAddition) PastelPeach else SurfaceVariantColor),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("หักแต้ม (-)", color = if (!isAddition) Color.White else TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = pointsText,
                    onValueChange = { pointsText = it.filter { char -> char.isDigit() } },
                    label = { Text("จำนวนแต้ม") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                        error = null
                    },
                    label = { Text("ระบุเหตุผล (บังคับ) *") },
                    placeholder = { Text("เช่น ของขวัญวันเกิด, ชดเชยสินค้า...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    isError = error != null
                )

                if (error != null) {
                    Text(text = error!!, color = StatusDanger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Text("ยกเลิก")
                    }

                    Button(
                        onClick = {
                            val pts = pointsText.toIntOrNull() ?: 0
                            if (pts <= 0) {
                                error = "กรุณาระบุจำนวนแต้มที่ถูกต้อง"
                                return@Button
                            }
                            if (reason.isBlank()) {
                                error = "จำเป็นต้องระบุเหตุผลในการปรับปรุงแต้ม"
                                return@Button
                            }
                            val change = if (isAddition) pts else -pts
                            onConfirm(change, reason)
                        },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isAddition) PastelMint else PastelPeach)
                    ) {
                        if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("ยืนยัน", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
