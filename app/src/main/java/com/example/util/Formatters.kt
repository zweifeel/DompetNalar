package com.example.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class AppCurrency(
    val code: String,
    val symbol: String,
    val prefix: String,
    val displayName: String,
    val locale: Locale,
    val exchangeRateToIdr: Double
) {
    IDR("IDR", "Rp", "Rp ", "IDR (Rp) - Rupiah", Locale("id", "ID"), 1.0),
    USD("USD", "$", "$ ", "USD ($) - US Dollar", Locale.US, 16000.0),
    EUR("EUR", "€", "€ ", "EUR (€) - Euro", Locale.GERMANY, 17500.0),
    SGD("SGD", "S$", "S$ ", "SGD (S$) - Singapore Dollar", Locale("en", "SG"), 12000.0),
    MYR("MYR", "RM", "RM ", "MYR (RM) - Malaysian Ringgit", Locale("ms", "MY"), 3500.0)
}

object CurrencyFormatter {
    private val localeId = Locale("id", "ID")

    private val _selectedCurrency = MutableStateFlow(AppCurrency.IDR)
    val selectedCurrency: StateFlow<AppCurrency> = _selectedCurrency.asStateFlow()

    fun setCurrency(currency: AppCurrency) {
        _selectedCurrency.value = currency
    }

    fun getCurrency(): AppCurrency = _selectedCurrency.value

    fun formatRupiah(amount: Double): String {
        val curr = _selectedCurrency.value
        return try {
            val nf = NumberFormat.getNumberInstance(curr.locale).apply {
                maximumFractionDigits = if (curr == AppCurrency.IDR) 0 else 2
                minimumFractionDigits = 0
            }
            curr.prefix + nf.format(amount)
        } catch (e: Exception) {
            curr.prefix + String.format(localeId, "%,.0f", amount).replace(',', '.')
        }
    }

    fun formatCompactRupiah(amount: Double): String {
        val curr = _selectedCurrency.value
        val prefix = curr.prefix
        return when {
            amount >= 1_000_000_000 -> String.format(localeId, "%s%.1f M", prefix, amount / 1_000_000_000)
            amount >= 1_000_000 -> String.format(localeId, "%s%.1f Jt", prefix, amount / 1_000_000)
            amount >= 1_000 -> String.format(localeId, "%s%.0f Rb", prefix, amount / 1_000)
            else -> formatRupiah(amount)
        }
    }

    fun formatDate(timestamp: Long, pattern: String = "dd MMM yyyy"): String {
        val sdf = SimpleDateFormat(pattern, LocaleHelper.getActiveLocale())
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
        val sdf = SimpleDateFormat("HH:mm", LocaleHelper.getActiveLocale())
        return sdf.format(Date(timestamp))
    }
}
