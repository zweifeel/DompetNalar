package com.example.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object CurrencyFormatter {
    private val localeId = Locale("id", "ID")
    private val currencyFormat = NumberFormat.getCurrencyInstance(localeId).apply {
        maximumFractionDigits = 0
    }

    fun formatRupiah(amount: Double): String {
        return try {
            val formatted = currencyFormat.format(amount)
            // Replace standard "Rp" spacing if needed
            formatted.replace("Rp", "Rp ")
        } catch (e: Exception) {
            "Rp " + String.format(localeId, "%,.0f", amount).replace(',', '.')
        }
    }

    fun formatCompactRupiah(amount: Double): String {
        return when {
            amount >= 1_000_000_000 -> String.format(localeId, "Rp %.1f M", amount / 1_000_000_000)
            amount >= 1_000_000 -> String.format(localeId, "Rp %.1f Jt", amount / 1_000_000)
            amount >= 1_000 -> String.format(localeId, "Rp %.0f Rb", amount / 1_000)
            else -> formatRupiah(amount)
        }
    }

    fun formatDate(timestamp: Long, pattern: String = "dd MMM yyyy"): String {
        val sdf = SimpleDateFormat(pattern, localeId)
        return sdf.format(Date(timestamp))
    }

    fun formatRelativeDate(timestamp: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestamp }

        val isSameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

        if (isSameDay) return "Hari Ini"

        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = yesterday.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

        if (isYesterday) return "Kemarin"

        return formatDate(timestamp, "dd MMM yyyy")
    }

    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", localeId)
        return sdf.format(Date(timestamp))
    }
}
