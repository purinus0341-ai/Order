package com.sitticun.orderapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitticun.orderapp.ui.theme.*

@Composable
fun StockBadge(stockQty: Int, minAlert: Int, modifier: Modifier = Modifier) {
    val (bgColor, textColor, label) = when {
        stockQty <= 0 -> Triple(Color(0xFFFFEBEA), StatusDanger, "หมด")
        stockQty <= minAlert -> Triple(Color(0xFFFFF3E0), StatusWarning, "ใกล้หมด ($stockQty)")
        else -> Triple(PastelMintLight, PastelMintDark, "$stockQty ชิ้น")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

@Composable
fun OrderStatusBadge(status: String, modifier: Modifier = Modifier) {
    val isCompleted = status.equals("COMPLETED", ignoreCase = true)
    val bgColor = if (isCompleted) PastelMintLight else Color(0xFFFFEBEA)
    val textColor = if (isCompleted) PastelMintDark else StatusDanger
    val text = if (isCompleted) "สำเร็จ" else "ยกเลิกบิลแล้ว"

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
fun FreeBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(PastelPeachLight)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "ของแถม",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = PastelPeachDark
        )
    }
}
