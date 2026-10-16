package com.sitticun.orderapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitticun.orderapp.ui.theme.*

@Composable
fun OfflineBanner(
    isOnline: Boolean,
    pendingSyncCount: Int,
    isSyncing: Boolean,
    onForceSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOnline || pendingSyncCount > 0) {
        Surface(
            color = if (!isOnline) IosOrangeLight else IosBlueLight,
            modifier = modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (!isOnline) Icons.Default.CloudOff else Icons.Default.Sync,
                            contentDescription = null,
                            tint = if (!isOnline) IosOrangeDark else IosBlueDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (!isOnline) {
                                if (pendingSyncCount > 0) "ออฟไลน์ • รอซิงค์ $pendingSyncCount รายการ"
                                else "โหมดออฟไลน์ • บันทึกข้อมูลลงเครื่องอัตโนมัติ"
                            } else {
                                "ออนไลน์แล้ว • รอซิงค์ $pendingSyncCount รายการ"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (!isOnline) IosOrangeDark else IosBlueDark
                        )
                    }

                    if (isOnline && pendingSyncCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = IosBlue,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(enabled = !isSyncing) { onForceSync() }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = Color.White,
                                        strokeWidth = 1.8.dp
                                    )
                                } else {
                                    Text(
                                        text = "ซิงค์ทันที",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = IosSeparator.copy(alpha = 0.35f), thickness = 0.5.dp)
            }
        }
    }
}
