package com.sitticun.orderapp.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    private val currencyFormat = DecimalFormat("฿#,##0.00")
    private val integerFormat = DecimalFormat("#,##0")
    private val thaiDateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("th", "TH"))
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    fun formatCurrency(amount: Double): String {
        return currencyFormat.format(amount)
    }

    fun formatNumber(number: Int): String {
        return integerFormat.format(number)
    }

    fun formatPoints(points: Int): String {
        return "${integerFormat.format(points)} แต้ม"
    }

    fun formatDateTime(timestampMillis: Long): String {
        return thaiDateFormat.format(Date(timestampMillis))
    }

    fun formatIsoDateTime(isoString: String?): String {
        if (isoString.isNullOrBlank()) return "-"
        return try {
            val date = isoDateFormat.parse(isoString) ?: return isoString
            thaiDateFormat.format(date)
        } catch (e: Exception) {
            isoString
        }
    }

    fun getCurrentIsoTimestamp(): String {
        return isoDateFormat.format(Date())
    }

    fun generateOrderNumber(): String {
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val randomPart = (1000..9999).random()
        return "ORD-$datePart-$randomPart"
    }
}
