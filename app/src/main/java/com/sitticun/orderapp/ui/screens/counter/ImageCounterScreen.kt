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
import com.sitticun.orderapp.data.model.Product
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

    Scaffold(
        containerColor = PastelBackground,
        topBar = {
            Surface(color = CardWhite, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "นับสต็อกจากรูปภาพ (Image Counter)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(text = "แตะเพื่อวางจุดมาร์กพิกัด Normalized (0-1)", fontSize = 11.sp, color = TextSecondary)
                        }

                        Button(
                            onClick = { galleryLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = PastelLavender),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (state.selectedImageUri != null) "เปลี่ยนรูป" else "เลือกรูปภาพ", fontSize = 12.sp)
                        }
                    }

                    // Product Selector Chips
                    if (products.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(products) { p ->
                                val isSelected = p.id == state.selectedProduct?.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectTargetProduct(p) },
                                    label = { Text(p.name, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PastelMint,
                                        selectedLabelColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
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
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = TextMuted, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(text = "แตะปุ่ม 'เลือกรูปภาพ' ด้านบน หรือแตะบนพื้นที่เพื่อวางจุด", color = TextSecondary, fontSize = 13.sp)
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
                            PastelMint
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
                    color = if (isErr) Color(0xFFFFEBEE) else PastelMintLight,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp, start = 20.dp, end = 20.dp)
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
                    .padding(14.dp)
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
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
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
                    color = PastelPeach
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("โน้ตเพิ่มเติม (ถ้ามี)") },
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
                        onClick = { onConfirm(note) },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PastelMint),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("บันทึก Cloud", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
