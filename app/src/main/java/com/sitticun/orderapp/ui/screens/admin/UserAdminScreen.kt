package com.sitticun.orderapp.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitticun.orderapp.data.model.UserProfile
import com.sitticun.orderapp.ui.theme.*

@Composable
fun UserAdminScreen(
    users: List<UserProfile>,
    isLoading: Boolean,
    onToggleActive: (UserProfile) -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        // Guard Info Alert
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = PastelLavenderLight,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = PastelLavenderDark)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "ระบบตรวจสอบสิทธิ์ Profile Active Guard",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = PastelLavenderDark
                    )
                    Text(
                        text = "หากปิดสถานะ (is_active = false) ผู้ใช้จะถูกระงับการเข้าสู่ระบบและเตะออกจากแอปพลิเคชันทันที",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "รายชื่อผู้ใช้ทั้งหมด (${users.size} คน)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextPrimary
            )

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = PastelMint, strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onRefresh) {
                    Text("รีเฟรช", fontSize = 12.sp, color = PastelMint)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (users.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = "ไม่พบข้อมูลผู้ใช้ในระบบ", color = TextSecondary, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(users, key = { it.id }) { user ->
                    UserCard(user = user, onToggle = { onToggleActive(user) })
                }
            }
        }
    }
}

@Composable
private fun UserCard(
    user: UserProfile,
    onToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
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
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (user.role == "admin") PastelPeach else PastelMint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (user.role == "admin") Icons.Default.AdminPanelSettings else Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = user.fullName ?: user.email ?: "ผู้ใช้งาน",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (user.role == "admin") PastelPeachLight else PastelMintLight
                        ) {
                            Text(
                                text = user.role.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (user.role == "admin") PastelPeachDark else PastelMintDark,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = user.email ?: "-",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Text(
                        text = if (user.isActive) "สถานะ: ใช้งานปกติ (Active)" else "สถานะ: ถูกระงับการใช้งาน (Inactive)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (user.isActive) StatusSuccess else StatusDanger
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "is_active", fontSize = 10.sp, color = TextMuted)
                Switch(
                    checked = user.isActive,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PastelMint,
                        checkedTrackColor = PastelMintLight,
                        uncheckedThumbColor = StatusDanger,
                        uncheckedTrackColor = Color(0xFFFFEBEE)
                    )
                )
            }
        }
    }
}
