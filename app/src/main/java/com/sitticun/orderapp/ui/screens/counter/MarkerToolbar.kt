package com.sitticun.orderapp.ui.screens.counter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
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
fun MarkerToolbar(
    totalCount: Int,
    selectedColorHex: String,
    onColorSelected: (String) -> Unit,
    onUndo: () -> Unit,
    onClearAll: () -> Unit,
    onSaveCount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorPalette = listOf(
        "#5B8A72", // Mint
        "#E07A5F", // Peach
        "#6B7FD7", // Lavender
        "#D9534F", // Coral Red
        "#F1A208"  // Amber
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = CardWhite,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Count and Color picker row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Count Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PastelMintLight
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "นับได้: ",
                            fontSize = 12.sp,
                            color = PastelMintDark
                        )
                        Text(
                            text = "$totalCount จุด",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PastelMintDark
                        )
                    }
                }

                // Color Palette Dots
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    colorPalette.forEach { hex ->
                        val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                        val color = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { onColorSelected(hex) }
                                .then(
                                    if (isSelected) Modifier.border(2.5.dp, TextPrimary, CircleShape)
                                    else Modifier
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = BorderLight, thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Actions: Undo, Clear, Save
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onUndo,
                    enabled = totalCount > 0,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ย้อนกลับ", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onClearAll,
                    enabled = totalCount > 0,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDanger)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ล้างจุด", fontSize = 12.sp)
                }

                Button(
                    onClick = onSaveCount,
                    enabled = totalCount > 0,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1.3f),
                    colors = ButtonDefaults.buttonColors(containerColor = PastelMint, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("บันทึกสต็อก", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
