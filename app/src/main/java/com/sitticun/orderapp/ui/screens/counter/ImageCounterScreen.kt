package com.sitticun.orderapp.ui.screens.counter

import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sitticun.orderapp.ui.components.IosNavigationHeader
import com.sitticun.orderapp.ui.theme.*

@Composable
fun ImageCounterScreen(
    viewModel: ImageCounterViewModel = viewModel(factory = ImageCounterViewModel.Factory())
) {
    val state by viewModel.uiState.collectAsState()
    val products by viewModel.products.collectAsState()

    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.onImageSelected(uri)
    }

    if (state.showSaveDialog) {
        SaveCountSessionDialog(
            countedQty = state.totalCount,
            isSaving = state.isSaving,
            onConfirm = { note -> viewModel.saveSession(note) },
            onDismiss = { viewModel.dismissSaveDialog() }
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
            title = "นับสต็อกจากรูป",
            subtitle = "แตะบนภาพเพื่อวางจุดมาร์กพิกัดลำดับอัตโนมัติ",
            trailingContent = {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = IosBlue,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { galleryLauncher.launch("image/*") }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(
                            text = if (state.selectedImageUri != null) "เปลี่ยนรูป" else "เลือกรูปภาพ",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            },
            bottomContent = {
                if (products.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(products) { p ->
                            val isSelected = p.id == state.selectedProduct?.id
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isSelected) IosBlue else IosSystemGray5,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { viewModel.selectTargetProduct(p) }
                            ) {
                                Text(
                                    text = p.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else TextPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(canvasSize) {
                        detectTapGestures { offset ->
                            val normX = if (canvasSize.width > 0) offset.x / canvasSize.width else 0.5f
                            val normY = if (canvasSize.height > 0) offset.y / canvasSize.height else 0.5f
                            viewModel.addMarker(normX, normY)
                        }
                    }
            ) {
                if (state.selectedImageUri != null) {
                    AsyncImage(
                        model = state.selectedImageUri,
                        contentDescription = "Stock Photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(IosSystemGray5),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = IosSystemGray, modifier = Modifier.size(34.dp))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "แตะ 'เลือกรูปภาพ' ด้านบน หรือแตะบนพื้นที่เพื่อวางจุดมาร์ก",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Render Canvas Overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val paint = Paint().apply {
                        textAlign = Paint.Align.CENTER
                        textSize = 28f
                        color = android.graphics.Color.WHITE
                        isFakeBoldText = true
                    }

                    val w = size.width
                    val h = size.height

                    for (marker in state.markers) {
                        val posX = marker.xNormalized * w
                        val posY = marker.yNormalized * h

                        val markerColor = try {
                            Color(android.graphics.Color.parseColor(marker.colorHex))
                        } catch (e: Exception) {
                            IosBlue
                        }

                        drawCircle(
                            color = markerColor,
                            radius = 24f,
                            center = Offset(posX, posY)
                        )

                        drawCircle(
                            color = Color.White,
                            radius = 24f,
                            center = Offset(posX, posY),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                        )

                        drawIntoCanvas { canvas ->
                            val textY = posY - ((paint.descent() + paint.ascent()) / 2)
                            canvas.nativeCanvas.drawText(
                                marker.seq.toString(),
                                posX,
                                textY,
                                paint
                            )
                        }
                    }
                }
            }

            // Message Toast
            if (state.message != null || state.errorMessage != null) {
                val isErr = state.errorMessage != null
                Surface(
                    color = if (isErr) IosRedLight else IosGreenLight,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp, start = 20.dp, end = 20.dp)
                ) {
                    Text(
                        text = (state.message ?: state.errorMessage)!!,
                        color = if (isErr) StatusDanger else IosGreenDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                    )
                }
            }

            // Marker Toolbar
            MarkerToolbar(
                totalCount = state.totalCount,
                selectedColorHex = state.selectedColorHex,
                onColorSelected = { viewModel.onColorChange(it) },
                onUndo = { viewModel.undo() },
                onClearAll = { viewModel.clearAll() },
                onSaveCount = { viewModel.openSaveDialog() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
fun SaveCountSessionDialog(
    countedQty: Int,
    isSaving: Boolean,
    onConfirm: (note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CardWhite,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "บันทึกการนับ (Count Session)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "จำนวนจุดที่มาร์กไว้ทั้งหมด: $countedQty จุด",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = IosBlue
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("โน้ตเพิ่มเติม (ถ้ามี)") },
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
                        onClick = { onConfirm(note) },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("บันทึก Cloud", fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
