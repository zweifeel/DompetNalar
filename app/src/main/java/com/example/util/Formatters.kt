package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Currency
import java.util.Date
import java.util.Locale

enum class AppCurrency(
    val code: String,
    val symbol: String,
    val prefix: String,
    val displayName: String,
    val locale: Locale,
    val exchangeRateToIdr: Double,
    val decimalDigits: Int = 2
) {
    IDR("IDR", "Rp", "Rp ", "IDR (Rp) - Indonesian Rupiah", Locale("id", "ID"), 1.0, 0),
    USD("USD", "$", "$ ", "USD ($) - US Dollar", Locale.US, 16000.0, 2),
    EUR("EUR", "€", "€ ", "EUR (€) - Euro", Locale.GERMANY, 17500.0, 2),
    GBP("GBP", "£", "£ ", "GBP (£) - British Pound", Locale.UK, 20000.0, 2),
    JPY("JPY", "¥", "¥ ", "JPY (¥) - Japanese Yen", Locale.JAPAN, 110.0, 0),
    SGD("SGD", "S$", "S$ ", "SGD (S$) - Singapore Dollar", Locale("en", "SG"), 12000.0, 2),
    MYR("MYR", "RM", "RM ", "MYR (RM) - Malaysian Ringgit", Locale("ms", "MY"), 3500.0, 2),
    AUD("AUD", "A$", "A$ ", "AUD (A$) - Australian Dollar", Locale("en", "AU"), 10500.0, 2),
    CAD("CAD", "C$", "C$ ", "CAD (C$) - Canadian Dollar", Locale.CANADA, 11500.0, 2),
    INR("INR", "₹", "₹ ", "INR (₹) - Indian Rupee", Locale("en", "IN"), 190.0, 2);

    companion object {
        fun fromCode(code: String): AppCurrency {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: IDR
        }

        fun fromCountryCode(countryCode: String): AppCurrency {
            return when (countryCode.uppercase(Locale.ROOT)) {
                "ID" -> IDR
                "US" -> USD
                "GB" -> GBP
                "JP" -> JPY
                "DE", "FR", "IT", "ES", "NL", "BE", "AT", "PT", "IE", "FI", "GR" -> EUR
                "SG" -> SGD
                "MY" -> MYR
                "AU" -> AUD
                "CA" -> CAD
                "IN" -> INR
                else -> {
                    try {
                        val testLocale = Locale("", countryCode)
                        val curr = Currency.getInstance(testLocale)
                        fromCode(curr.currencyCode)
                    } catch (e: Exception) {
                        IDR
                    }
                }
            }
        }
    }
}

enum class CurrencyMode {
    FOLLOW_DEVICE,
    MANUAL
}

object CurrencyFormatter {
    private const val PREFS_NAME = "finsmart_currency_prefs"
    private const val KEY_MODE = "currency_mode"
    private const val KEY_CODE = "currency_code"

    private val localeId = Locale("id", "ID")
    private var prefs: SharedPreferences? = null

    private val _currencyMode = MutableStateFlow(CurrencyMode.FOLLOW_DEVICE)
    val currencyMode: StateFlow<CurrencyMode> = _currencyMode.asStateFlow()

    private val _selectedCurrency = MutableStateFlow(AppCurrency.IDR)
    val selectedCurrency: StateFlow<AppCurrency> = _selectedCurrency.asStateFlow()
    val activeCurrency: AppCurrency get() = _selectedCurrency.value

    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        val modeStr = prefs?.getString(KEY_MODE, CurrencyMode.FOLLOW_DEVICE.name) ?: CurrencyMode.FOLLOW_DEVICE.name
        val mode = try { CurrencyMode.valueOf(modeStr) } catch (e: Exception) { CurrencyMode.FOLLOW_DEVICE }
        _currencyMode.value = mode

        if (mode == CurrencyMode.FOLLOW_DEVICE) {
            val deviceCurrency = resolveDeviceCurrency()
            _selectedCurrency.value = deviceCurrency
        } else {
            val savedCode = prefs?.getString(KEY_CODE, AppCurrency.IDR.code) ?: AppCurrency.IDR.code
            _selectedCurrency.value = AppCurrency.fromCode(savedCode)
        }
    }

    fun setMode(context: Context, mode: CurrencyMode) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        prefs?.edit()?.putString(KEY_MODE, mode.name)?.commit()
        _currencyMode.value = mode

        if (mode == CurrencyMode.FOLLOW_DEVICE) {
            _selectedCurrency.value = resolveDeviceCurrency()
        }
    }

    fun setCurrency(currency: AppCurrency, context: Context? = null) {
        _selectedCurrency.value = currency
        _currencyMode.value = CurrencyMode.MANUAL
        if (context != null) {
            if (prefs == null) {
                prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
            prefs?.edit()
                ?.putString(KEY_MODE, CurrencyMode.MANUAL.name)
                ?.putString(KEY_CODE, currency.code)
                ?.commit()
        }
    }

    fun getCurrency(): AppCurrency = _selectedCurrency.value

    fun resolveDeviceCurrency(): AppCurrency {
        val sysCountry = Locale.getDefault().country
        return if (sysCountry.isNotBlank()) {
            AppCurrency.fromCountryCode(sysCountry)
        } else {
            AppCurrency.IDR
        }
    }

    /**
     * Memformat nominal keuangan mengikuti mata uang spesifik (ISO-4217) atau mata uang aktif jika null.
     * Mencegah distorsi format pada tampilan portofolio multi-mata uang (DEC-PROD-03).
     */
    fun formatCurrency(amount: Double, currencyCode: String?): String {
        if (!currencyCode.isNullOrBlank()) {
            val matched = AppCurrency.entries.firstOrNull { it.code.equals(currencyCode, ignoreCase = true) }
            if (matched != null) {
                return try {
                    val nf = NumberFormat.getNumberInstance(matched.locale).apply {
                        maximumFractionDigits = matched.decimalDigits
                        minimumFractionDigits = matched.decimalDigits
                    }
                    matched.prefix + nf.format(amount)
                } catch (e: Exception) {
                    matched.prefix + String.format(localeId, "%,.0f", amount).replace(',', '.')
                }
            } else {
                // Fallback untuk kode ISO 4217 generik lainnya
                return try {
                    val curr = Currency.getInstance(currencyCode.uppercase(Locale.ROOT))
                    val nf = NumberFormat.getNumberInstance(LocaleHelper.getActiveLocale()).apply {
                        maximumFractionDigits = curr.defaultFractionDigits.coerceAtLeast(0)
                        minimumFractionDigits = curr.defaultFractionDigits.coerceAtLeast(0)
                    }
                    "${curr.symbol} " + nf.format(amount)
                } catch (e: Exception) {
                    "$currencyCode " + String.format(localeId, "%,.2f", amount)
                }
            }
        }
        val curr = _selectedCurrency.value
        return try {
            val nf = NumberFormat.getNumberInstance(curr.locale).apply {
                maximumFractionDigits = curr.decimalDigits
                minimumFractionDigits = curr.decimalDigits
            }
            curr.prefix + nf.format(amount)
        } catch (e: Exception) {
            curr.prefix + String.format(localeId, "%,.0f", amount).replace(',', '.')
        }
    }

    /**
     * Memformat nominal keuangan mengikuti mata uang dan locale aktif.
     */
    fun formatCurrency(amount: Double): String = formatCurrency(amount, null)

    /**
     * Alias resmi yang tetap kompatibel dengan panggilan lama [formatRupiah].
     */
    fun formatRupiah(amount: Double): String = formatCurrency(amount)

    fun formatCompactCurrency(amount: Double, currencyCode: String? = null): String {
        val curr = if (!currencyCode.isNullOrBlank()) {
            AppCurrency.fromCode(currencyCode)
        } else {
            _selectedCurrency.value
        }
        val prefix = curr.prefix
        val isEn = LocaleHelper.isEnglish()
        val billionSuffix = if (isEn) "B" else "M"
        val millionSuffix = if (isEn) "M" else "Jt"
        val thousandSuffix = if (isEn) "K" else "Rb"
        val activeLocale = curr.locale
        return when {
            amount >= 1_000_000_000 -> String.format(activeLocale, "%s%.1f %s", prefix, amount / 1_000_000_000, billionSuffix)
            amount >= 1_000_000 -> String.format(activeLocale, "%s%.1f %s", prefix, amount / 1_000_000, millionSuffix)
            amount >= 1_000 -> String.format(activeLocale, "%s%.0f %s", prefix, amount / 1_000, thousandSuffix)
            else -> formatCurrency(amount, curr.code)
        }
    }

    fun formatCompactRupiah(amount: Double): String = formatCompactCurrency(amount)

    fun formatDate(timestamp: Long, pattern: String = "dd MMM yyyy"): String {
        val sdf = SimpleDateFormat(pattern, LocaleHelper.getActiveLocale())
        return sdf.format(Date(timestamp))
    }

    fun formatRelativeDate(timestamp: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestamp }

        val isSameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

        if (isSameDay) return if (LocaleHelper.isEnglish()) "Today" else "Hari Ini"

        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = yesterday.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

        if (isYesterday) return if (LocaleHelper.isEnglish()) "Yesterday" else "Kemarin"

        return formatDate(timestamp, "dd MMM yyyy")
    }

    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", LocaleHelper.getActiveLocale())
        return sdf.format(Date(timestamp))
    }

    fun clearForTest() {
        prefs = null
        _currencyMode.value = CurrencyMode.FOLLOW_DEVICE
        _selectedCurrency.value = AppCurrency.IDR
    }
}
