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
import com.sitticun.orderapp.ui.components.IosNavigationHeader
import com.sitticun.orderapp.ui.components.IosSearchBar
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

    if (state.showAddDialog) {
        AddCustomerDialog(
            isSaving = state.isSaving,
            onSave = { name -> viewModel.createCustomer(name) },
            onDismiss = { viewModel.dismissAddDialog() }
        )
    }

    if (state.message != null || state.errorMessage != null) {
        LaunchedEffect(state.message, state.errorMessage) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosGroupedBackground)
    ) {
        IosNavigationHeader(
            title = "สมาชิกลูกค้า",
            subtitle = "จัดการรายชื่อลูกค้าและแต้มสะสม (${customers.size} คน)",
            trailingContent = {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = IosBlue,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { viewModel.openAddCustomer() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("เพิ่มลูกค้า", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            },
            bottomContent = {
                IosSearchBar(
                    query = state.searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    placeholder = "ค้นหาชื่อลูกค้า...",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )

        if (state.message != null) {
            Surface(
                color = IosGreenLight,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = state.message!!,
                    color = IosGreenDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        if (state.errorMessage != null) {
            Surface(
                color = IosRedLight,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = state.errorMessage!!,
                    color = StatusDanger,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        // Customer List and Detail split
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val isWide = maxWidth >= 760.dp

            if (isWide) {
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.weight(0.48f)) {
                        CustomerList(
                            customers = filteredCustomers,
                            selectedCustomerId = state.selectedCustomer?.id,
                            onSelect = { viewModel.selectCustomer(it) }
                        )
                    }

                    Box(modifier = Modifier.weight(0.52f)) {
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

    // Modal detail for narrow screens
    if (state.selectedCustomer != null && androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 760) {
        val cust = state.selectedCustomer!!
        Dialog(onDismissRequest = { viewModel.selectCustomer(null) }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = CardWhite,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 580.dp)
                    .padding(6.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        CustomerDetailPane(
                            customer = cust,
                            pointBalances = state.pointBalances,
                            orders = state.customerOrders,
                            getProductName = { viewModel.getProductName(it) }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.selectCustomer(null) },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IosBlue)
                    ) {
                        Text("เสร็จสิ้น", fontWeight = FontWeight.SemiBold)
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
        // iOS Contacts-style Inset Grouped List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(customers, key = { it.id }) { customer ->
                val isSelected = customer.id == selectedCustomerId
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) IosBlueLight else CardWhite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(customer) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) IosBlue else IosIndigo),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = customer.name.take(1),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = customer.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) IosBlueDark else TextPrimary
                                )
                                Text(
                                    text = "รหัสสมาชิก: ${customer.id.take(12)}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = IosSystemGray2,
                            modifier = Modifier.size(20.dp)
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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardWhite,
        modifier = Modifier.fillMaxSize()
    ) {
        if (customer == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "เลือกรายชื่อลูกค้าเพื่อดูแต้มสะสมและประวัติ", color = TextSecondary, fontSize = 14.sp)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = customer.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = "รหัสสมาชิก: ${customer.id}", fontSize = 12.sp, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = IosSeparatorLight, thickness = 0.6.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "แต้มสะสมแยกตามสินค้า",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (pointBalances.isEmpty()) {
                    Text(
                        text = "ยังไม่มีแต้มสะสม",
                        fontSize = 13.sp,
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
                                shape = RoundedCornerShape(12.dp),
                                color = IosOrangeLight,
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
                                            tint = IosOrange,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${pb.balance}",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = IosOrangeDark
                                        )
                                        Text(
                                            text = " แต้ม",
                                            fontSize = 11.sp,
                                            color = IosOrangeDark
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = IosSeparatorLight, thickness = 0.6.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "ประวัติการสั่งซื้อ (${orders.size} รายการ)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = TextSecondary
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
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = IosGroupedBackground
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = "บิล #${ord.id.take(8)}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                                        Text(text = Formatters.formatIsoDateTime(ord.createdAt), fontSize = 11.sp, color = TextMuted)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = Formatters.formatCurrency(ord.total),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = IosBlue
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
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
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CardWhite,
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
                    placeholder = { Text("เช่น คุณสมชาย หรือ ร้านค้า A") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IosBlue,
                        unfocusedBorderColor = IosSeparatorLight
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("ยกเลิก", color = TextSecondary)
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) onSave(name)
                        },
                        enabled = name.isNotBlank() && !isSaving,
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("บันทึก", fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }
    }
}
