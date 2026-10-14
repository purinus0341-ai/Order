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
import com.sitticun.orderapp.data.model.OrderSummary
import com.sitticun.orderapp.data.model.PointBalance
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
                it.name.contains(state.searchQuery, ignoreCase = true)
            }
        }
    }

    // Add Customer Dialog
    if (state.showAddDialog) {
        AddCustomerDialog(
            isSaving = state.isSaving,
            onSave = { name -> viewModel.createCustomer(name) },
            onDismiss = { viewModel.dismissAddDialog() }
        )
    }

    // Snackbar / Alert for messages
    if (state.message != null || state.errorMessage != null) {
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
                        placeholder = { Text("ค้นหาชื่อลูกค้า...", fontSize = 13.sp) },
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
                        Box(modifier = Modifier.weight(0.5f)) {
                            CustomerList(
                                customers = filteredCustomers,
                                selectedCustomerId = state.selectedCustomer?.id,
                                onSelect = { viewModel.selectCustomer(it) }
                            )
                        }

                        Box(modifier = Modifier.weight(0.5f)) {
                            CustomerDetailPane(
                                customer = state.selectedCustomer,
                                pointBalances = state.pointBalances,
                                orders = state.customerOrders,
                                getProductName = { viewModel.getProductName(it) }
                            )
                        }
                    }
                } else {
                    CustomerList(
                        customers = filteredCustomers,
                        selectedCustomerId = state.selectedCustomer?.id,
                        onSelect = { viewModel.selectCustomer(it) }
                    )
                }
            }
        }
    }

    // Modal detail for narrow screens
    if (state.selectedCustomer != null && androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 760) {
        val cust = state.selectedCustomer!!
        Dialog(onDismissRequest = { viewModel.selectCustomer(null) }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 580.dp)
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    CustomerDetailPane(
                        customer = cust,
                        pointBalances = state.pointBalances,
                        orders = state.customerOrders,
                        getProductName = { viewModel.getProductName(it) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.selectCustomer(null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("ปิด")
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerList(
    customers: List<Customer>,
    selectedCustomerId: String?,
    onSelect: (Customer) -> Unit
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
                                    text = "ID: ${customer.id.take(12)}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerDetailPane(
    customer: Customer?,
    pointBalances: List<PointBalance>,
    orders: List<OrderSummary>,
    getProductName: (Long) -> String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        modifier = Modifier.fillMaxSize()
    ) {
        if (customer == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "เลือกข้อมูลลูกค้าเพื่อดูแต้มสะสมและประวัติ", color = TextSecondary, fontSize = 14.sp)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = customer.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = "รหัสสมาชิก: ${customer.id}", fontSize = 11.sp, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BorderLight, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Point Balances per product
                Text(
                    text = "แต้มสะสมแยกตามสินค้า (1 ชิ้นที่ซื้อ = 1 แต้ม)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (pointBalances.isEmpty()) {
                    Text(
                        text = "ยังไม่มีแต้มสะสม",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pointBalances.forEach { pb ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = PastelPeachLight,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = getProductName(pb.productId),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Stars,
                                            contentDescription = null,
                                            tint = PastelPeach,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${pb.balance}",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PastelPeachDark
                                        )
                                        Text(
                                            text = " แต้ม",
                                            fontSize = 11.sp,
                                            color = PastelPeachDark
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BorderLight, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "ประวัติการสั่งซื้อ (${orders.size} ครั้ง)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextPrimary
                )
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
                                        Text(text = "บิล #${ord.id.take(8)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(text = Formatters.formatIsoDateTime(ord.createdAt), fontSize = 11.sp, color = TextMuted)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = Formatters.formatCurrency(ord.total),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = PastelPeach
                                        )
                                        OrderStatusBadge(status = if (ord.isVoided) "VOIDED" else "COMPLETED")
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
fun AddCustomerDialog(
    isSaving: Boolean,
    onSave: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "เพิ่มสมาชิกลูกค้าใหม่",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ชื่อลูกค้า *") },
                    placeholder = { Text("เช่น สมชาย หรือ ร้านค้า A") },
                    singleLine = true,
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
                            if (name.isNotBlank()) onSave(name)
                        },
                        enabled = name.isNotBlank() && !isSaving,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PastelMint),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("บันทึก", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
