package com.sitticun.orderapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(if (!isOnline) PastelPeachLight else PastelLavenderLight)
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
                    tint = if (!isOnline) PastelPeachDark else PastelLavenderDark,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (!isOnline) {
                        if (pendingSyncCount > 0) "โหมดออฟไลน์ (มีรายการรอซิงค์ $pendingSyncCount รายการ)"
                        else "โหมดออฟไลน์ (ทำงานได้ตามปกติ ข้อมูลจะถูกบันทึกลงเครื่อง)"
                    } else {
                        "ออนไลน์แล้ว (รอซิงค์ $pendingSyncCount รายการ)"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (!isOnline) PastelPeachDark else PastelLavenderDark
                )
            }

            if (isOnline && pendingSyncCount > 0) {
                Button(
                    onClick = onForceSync,
                    enabled = !isSyncing,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PastelLavender,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("ซิงค์เดี๋ยวนี้", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
