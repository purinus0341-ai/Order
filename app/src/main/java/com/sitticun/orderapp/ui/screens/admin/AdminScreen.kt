package com.sitticun.orderapp.ui.screens.admin

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitticun.orderapp.BuildConfig
import com.sitticun.orderapp.OrderApplication
import com.sitticun.orderapp.ui.theme.*

@Composable
fun AdminScreen(
    onLogout: () -> Unit,
    viewModel: AdminViewModel = viewModel(factory = AdminViewModel.Factory())
) {
    val state by viewModel.uiState.collectAsState()
    val outboxItems by viewModel.outboxItems.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val products by viewModel.products.collectAsState()
    val isOnlineFlow by OrderApplication.instance.networkMonitor.isOnlineFlow.collectAsState(initial = true)
    val context = LocalContext.current

    val tabs = listOf(
        Pair("ซิงค์ออฟไลน์", Icons.Default.Sync),
        Pair("สินค้า", Icons.Default.Inventory2),
        Pair("ผู้ใช้งาน", Icons.Default.People),
        Pair("เวอร์ชัน & ข้อมูล", Icons.Default.Info)
    )

    if (state.message != null || state.errorMessage != null) {
        val isErr = state.errorMessage != null
        LaunchedEffect(state.message, state.errorMessage) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = PastelBackground,
        topBar = {
            Surface(color = CardWhite, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ผู้ดูแลระบบและการซิงค์ (Admin & Diagnostics)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        TextButton(
                            onClick = {
                                OrderApplication.instance.authRepository.logout()
                                onLogout()
                            }
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, tint = StatusDanger, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ออกจากระบบ", color = StatusDanger, fontSize = 12.sp)
                        }
                    }

                    TabRow(
                        selectedTabIndex = state.selectedTab,
                        containerColor = CardWhite,
                        contentColor = PastelMint
                    ) {
                        tabs.forEachIndexed { index, (title, icon) ->
                            Tab(
                                selected = state.selectedTab == index,
                                onClick = { viewModel.selectTab(index) },
                                text = { Text(title, fontSize = 12.sp, fontWeight = if (state.selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                selectedContentColor = PastelMint,
                                unselectedContentColor = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (state.selectedTab) {
                0 -> SyncDiagnosticsScreen(
                    outboxItems = outboxItems,
                    isSyncing = isSyncing,
                    isOnline = isOnlineFlow,
                    onForceSync = { viewModel.forceSync() },
                    onClearCompleted = { viewModel.clearCompletedOutbox() },
                    onResetOutbox = { viewModel.resetOutbox() }
                )
                1 -> ProductAdminScreen(
                    products = products,
                    showDialog = state.showProductDialog,
                    productToEdit = state.productToEdit,
                    isSaving = state.isSavingProduct,
                    onOpenAdd = { viewModel.openAddProduct() },
                    onOpenEdit = { viewModel.openEditProduct(it) },
                    onDismissDialog = { viewModel.dismissProductDialog() },
                    onSaveProduct = { viewModel.saveProduct(it) },
                    onToggleActive = { viewModel.toggleProductActive(it) }
                )
                2 -> UserAdminScreen(
                    users = state.usersList,
                    isLoading = state.isLoadingUsers,
                    onToggleActive = { viewModel.toggleUserActive(it) },
                    onRefresh = { viewModel.loadUsers() }
                )
                3 -> AboutAppScreen(
                    updateInfo = state.updateInfo,
                    isChecking = state.isCheckingUpdate,
                    onCheckUpdate = { viewModel.checkAppUpdate() },
                    onDownloadApk = { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                )
            }

            // Toast feedback
            if (state.message != null || state.errorMessage != null) {
                val isErr = state.errorMessage != null
                Surface(
                    color = if (isErr) Color(0xFFFFEBEE) else PastelMintLight,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(
                        text = (state.message ?: state.errorMessage)!!,
                        color = if (isErr) StatusDanger else PastelMintDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AboutAppScreen(
    updateInfo: com.sitticun.orderapp.data.repository.UpdateInfo?,
    isChecking: Boolean,
    onCheckUpdate: () -> Unit,
    onDownloadApk: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "ข้อมูลเวอร์ชันและระบบ (App Info)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "ชื่อแอปพลิเคชัน:", fontSize = 13.sp, color = TextSecondary)
                    Text(text = "OrderApp POS", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Package Name:", fontSize = 13.sp, color = TextSecondary)
                    Text(text = "com.sitticun.orderapp", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "เวอร์ชันแอปปัจจุบัน:", fontSize = 13.sp, color = TextSecondary)
                    Text(text = "${BuildConfig.APP_VERSION_NAME} (Build ${BuildConfig.APP_VERSION_CODE})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PastelMint)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Supabase URL:", fontSize = 13.sp, color = TextSecondary)
                    Text(text = BuildConfig.SUPABASE_URL.take(24) + "...", fontSize = 12.sp, color = TextMuted)
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = BorderLight, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onCheckUpdate,
                    enabled = !isChecking,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PastelLavender),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("กำลังตรวจสอบอัปเดต...")
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("เช็กอัปเดตเวอร์ชัน APK จาก GitHub Releases", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                if (updateInfo != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (updateInfo.hasUpdate) PastelPeachLight else PastelMintLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (updateInfo.hasUpdate) "พบเวอร์ชันใหม่: ${updateInfo.latestVersion}" else "คุณกำลังใช้งานเวอร์ชันล่าสุด (${updateInfo.currentVersion})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (updateInfo.hasUpdate) PastelPeachDark else PastelMintDark
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = updateInfo.releaseNotes, fontSize = 12.sp, color = TextSecondary)

                            if (updateInfo.hasUpdate && !updateInfo.downloadUrl.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { onDownloadApk(updateInfo.downloadUrl) },
                                    colors = ButtonDefaults.buttonColors(containerColor = PastelPeach),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ดาวน์โหลดไฟล์ APK", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
