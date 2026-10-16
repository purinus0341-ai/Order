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
        stockQty <= 0 -> Triple(IosRedLight, StatusDanger, "หมด")
        stockQty <= minAlert -> Triple(IosOrangeLight, StatusWarning, "ใกล้หมด ($stockQty)")
        else -> Triple(IosGreenLight, IosGreenDark, "$stockQty ชิ้น")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 2.dp)
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
    val bgColor = if (isCompleted) IosGreenLight else IosRedLight
    val textColor = if (isCompleted) IosGreenDark else StatusDanger
    val text = if (isCompleted) "สำเร็จ" else "ยกเลิกแล้ว"

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

@Composable
fun FreeBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(IosOrangeLight)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = "ของแถม",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = IosOrangeDark
        )
    }
}
