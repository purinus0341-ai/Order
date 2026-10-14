package com.sitticun.orderapp.ui.screens.counter

import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.font.FontWeight
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

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.onImageSelected(uri)
    }

    if (state.showSaveDialog && state.selectedProduct != null) {
        SaveCountToStockDialog(
            products = products,
            selectedProduct = state.selectedProduct!!,
            countedQty = state.totalCount,
            isSaving = state.isSaving,
            onSelectProduct = { viewModel.selectTargetProduct(it) },
            onConfirm = { isOverride -> viewModel.saveCountToStock(isOverride) },
            onDismiss = { viewModel.dismissSaveDialog() }
        )
    }

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "ระบบนับสต็อกจากรูปภาพ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = "แตะบนรูปเพื่อวางจุดมาร์กลำดับ 1, 2, 3...", fontSize = 12.sp, color = TextSecondary)
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
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Interactive Canvas Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            viewModel.addMarker(offset.x, offset.y)
                        }
                    }
            ) {
                // If user selected an image, show AsyncImage as background
                if (state.selectedImageUri != null) {
                    AsyncImage(
                        model = state.selectedImageUri,
                        contentDescription = "Stock Photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Default grid canvas background hint
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = TextMuted, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(text = "แตะปุ่ม 'เลือกรูปภาพ' ด้านบน หรือแตะบนพื้นที่ว่างเพื่อทดลองนับ", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }

                // Render Marker Dots Canvas overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val paint = Paint().apply {
                        textAlign = Paint.Align.CENTER
                        textSize = 28f
                        color = android.graphics.Color.WHITE
                        isFakeBoldText = true
                    }

                    for (marker in state.markers) {
                        val markerColor = try {
                            Color(android.graphics.Color.parseColor(marker.colorHex))
                        } catch (e: Exception) {
                            PastelMint
                        }

                        // Outer marker circle
                        drawCircle(
                            color = markerColor,
                            radius = 24f,
                            center = Offset(marker.x, marker.y)
                        )

                        // Outer white stroke
                        drawCircle(
                            color = Color.White,
                            radius = 24f,
                            center = Offset(marker.x, marker.y),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                        )

                        // Draw sequence number text inside circle
                        drawIntoCanvas { canvas ->
                            val textY = marker.y - ((paint.descent() + paint.ascent()) / 2)
                            canvas.nativeCanvas.drawText(
                                marker.seq.toString(),
                                marker.x,
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

            // Bottom Floating Marker Toolbar
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
fun SaveCountToStockDialog(
    products: List<Product>,
    selectedProduct: Product,
    countedQty: Int,
    isSaving: Boolean,
    onSelectProduct: (Product) -> Unit,
    onConfirm: (isOverride: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var isOverride by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp).padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "บันทึกยอดนับลงสต็อก",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "จำนวนจุดที่นับได้: $countedQty ชิ้น",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PastelPeach
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(text = "เลือกสินค้าที่ต้องการอัปเดต:", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))

                // Product selector
                LazyColumn(
                    modifier = Modifier.heightIn(max = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(products, key = { it.id }) { p ->
                        val isSelected = p.id == selectedProduct.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProduct(p) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) PastelMintLight else SurfaceVariantColor
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = p.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(text = "สต็อกเดิม: ${p.stockQty} ชิ้น", fontSize = 11.sp, color = TextMuted)
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = PastelMintDark)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(text = "วิธีกระทบยอดสต็อก:", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { isOverride = true },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isOverride) PastelMint else SurfaceVariantColor),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("แทนที่สต็อกเดิม ($countedQty)", color = if (isOverride) Color.White else TextPrimary, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { isOverride = false },
                        colors = ButtonDefaults.buttonColors(containerColor = if (!isOverride) PastelPeach else SurfaceVariantColor),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("บวกเพิ่ม (${selectedProduct.stockQty + countedQty})", color = if (!isOverride) Color.White else TextPrimary, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Text("ยกเลิก")
                    }

                    Button(
                        onClick = { onConfirm(isOverride) },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PastelMint),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("บันทึกสต็อก", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
