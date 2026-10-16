package com.sitticun.orderapp.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitticun.orderapp.ui.components.IosAppIconBadge
import com.sitticun.orderapp.ui.components.IosIconBox
import com.sitticun.orderapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory())
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    if (state.isInactiveBlocked) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissInactiveDialog() },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = StatusDanger) },
            title = { Text("ระงับการเข้าถึง", fontWeight = FontWeight.Bold, color = StatusDanger) },
            text = { Text("บัญชีผู้ใช้นี้มีสถานะไม่ใช้งาน (is_active = false) ถูกระงับการใช้งานตามกฎความปลอดภัย กรุณาติดต่อผู้ดูแลระบบเพื่อเปิดสิทธิ์") },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissInactiveDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("ตกลง", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = CardWhite,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IosGroupedBackground)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // iOS Squircle App Icon Header
            IosAppIconBadge(
                icon = Icons.Default.Storefront,
                size = 78.dp,
                iconSize = 40.dp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "OrderApp POS",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "ระบบขายหน้าร้านและบริหารจัดการสต็อก",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            if (state.errorMessage != null && !state.isInactiveBlocked) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    color = IosRedLight
                ) {
                    Text(
                        text = state.errorMessage!!,
                        color = StatusDanger,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // iOS Inset Grouped Form Card (Email + Password)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardWhite,
                shadowElevation = 0.5.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    IosFormRowField(
                        label = "อีเมล",
                        value = state.email,
                        onValueChange = { viewModel.onEmailChange(it) },
                        placeholder = "name@company.com",
                        icon = Icons.Default.Email,
                        iconBg = IosBlue,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        )
                    )

                    HorizontalDivider(
                        color = IosSeparatorLight,
                        thickness = 0.6.dp,
                        modifier = Modifier.padding(start = 54.dp)
                    )

                    IosFormRowField(
                        label = "รหัสผ่าน",
                        value = state.password,
                        onValueChange = { viewModel.onPasswordChange(it) },
                        placeholder = "กรอกรหัสผ่าน",
                        icon = Icons.Default.Lock,
                        iconBg = IosIndigo,
                        isPassword = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                viewModel.login(onLoginSuccess)
                            }
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Primary iOS Filled Action Button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.login(onLoginSuccess)
                },
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = IosBlue,
                    contentColor = Color.White,
                    disabledContainerColor = IosBlue.copy(alpha = 0.5f)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.2.dp
                    )
                } else {
                    Text(
                        text = "เข้าสู่ระบบ",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary iOS Tinted Pill Button (Quick Demo Mode)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = IosBlueLight,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { viewModel.demoLogin(onLoginSuccess) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "ทดลองใช้งานทันที (โหมดแคชเชียร์)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = IosBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun IosFormRowField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IosIconBox(
            icon = icon,
            backgroundColor = iconBg,
            size = 28.dp,
            iconSize = 16.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.width(72.dp)
        )

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 15.sp,
                    color = IosSystemGray
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Normal
                ),
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                cursorBrush = SolidColor(IosBlue),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
