package com.sitticun.orderapp.ui.screens.admin

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitticun.orderapp.BuildConfig
import com.sitticun.orderapp.OrderApplication
import com.sitticun.orderapp.ui.components.IosAppIconBadge
import com.sitticun.orderapp.ui.components.IosNavigationHeader
import com.sitticun.orderapp.ui.components.IosSegmentedControl
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

    val tabTitles = listOf("ซิงค์", "สินค้า", "ผู้ใช้งาน", "เกี่ยวกับ")

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
            title = "ตั้งค่าและผู้ดูแล",
            subtitle = "จัดการระบบ ซิงค์ออฟไลน์ และตรวจสอบสถานะ",
            trailingContent = {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = IosRedLight,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            OrderApplication.instance.authRepository.logout()
                            onLogout()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, tint = StatusDanger, modifier = Modifier.size(15.dp))
                        Text("ออกจากระบบ", color = StatusDanger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            bottomContent = {
                IosSegmentedControl(
                    items = tabTitles,
                    selectedIndex = state.selectedTab,
                    onItemSelected = { viewModel.selectTab(it) }
                )
            }
        )

        Box(modifier = Modifier.fillMaxSize()) {
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
                    color = if (isErr) IosRedLight else IosGreenLight,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(
                        text = (state.message ?: state.errorMessage)!!,
                        color = if (isErr) StatusDanger else IosGreenDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        IosAppIconBadge(
            icon = Icons.Default.Storefront,
            size = 72.dp,
            iconSize = 36.dp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "OrderApp POS",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "เวอร์ชัน ${BuildConfig.APP_VERSION_NAME} (${BuildConfig.APP_VERSION_CODE})",
            fontSize = 13.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // iOS Settings-style Grouped Info Table
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = CardWhite,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                IosSettingsInfoRow(label = "ชื่อแอปพลิเคชัน", value = "OrderApp POS")
                HorizontalDivider(color = IosSeparatorLight, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                IosSettingsInfoRow(label = "Package ID", value = "com.sitticun.orderapp")
                HorizontalDivider(color = IosSeparatorLight, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                IosSettingsInfoRow(label = "เวอร์ชันปัจจุบัน", value = "${BuildConfig.APP_VERSION_NAME} (Build ${BuildConfig.APP_VERSION_CODE})", valueColor = IosBlue)
                HorizontalDivider(color = IosSeparatorLight, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                IosSettingsInfoRow(label = "Supabase Cloud", value = BuildConfig.SUPABASE_URL.take(26) + "...")
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = onCheckUpdate,
            enabled = !isChecking,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
            shape = RoundedCornerShape(14.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            if (isChecking) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("กำลังตรวจสอบอัปเดต...")
            } else {
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("รายการอัปเดตซอฟต์แวร์ (GitHub Releases)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }

        if (updateInfo != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (updateInfo.hasUpdate) IosOrangeLight else IosGreenLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = if (updateInfo.hasUpdate) "พบเวอร์ชันใหม่: ${updateInfo.latestVersion}" else "ซอฟต์แวร์ของคุณเป็นเวอร์ชันล่าสุดแล้ว (${updateInfo.currentVersion})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (updateInfo.hasUpdate) IosOrangeDark else IosGreenDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = updateInfo.releaseNotes, fontSize = 12.sp, color = TextSecondary)

                    if (updateInfo.hasUpdate && !updateInfo.downloadUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onDownloadApk(updateInfo.downloadUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = IosOrange),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ดาวน์โหลดและติดตั้ง APK", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IosSettingsInfoRow(
    label: String,
    value: String,
    valueColor: Color = TextSecondary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Normal, color = valueColor)
    }
}
