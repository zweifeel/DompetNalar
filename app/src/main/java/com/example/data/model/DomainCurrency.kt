package com.example.data.model

import java.util.Currency
import java.util.Locale

class UnknownCurrencyException(message: String) : IllegalArgumentException(message)
class UnsupportedCurrencyScaleException(message: String) : IllegalArgumentException(message)

/**
 * Domain currency representation strictly compliant with ISO 4217.
 * Immutable and validated against standard Java Currency library.
 */
data class DomainCurrency(
    val code: String,
    val decimalDigits: Int,
    val symbol: String
) {
    companion object {
        private val ISO_CURRENCIES by lazy {
            Currency.getAvailableCurrencies().associateBy { it.currencyCode }
        }

        fun fromCode(code: String): DomainCurrency {
            val normalized = code.trim().uppercase(Locale.ROOT)
            val javaCurr = ISO_CURRENCIES[normalized]
                ?: throw UnknownCurrencyException("Unsupported or invalid ISO 4217 currency code: $code")

            val digits = javaCurr.defaultFractionDigits
            if (digits < 0) {
                throw UnsupportedCurrencyScaleException(
                    "Currency '$normalized' has non-standard fraction digits ($digits) and is not supported."
                )
            }

            return DomainCurrency(
                code = javaCurr.currencyCode,
                decimalDigits = digits,
                symbol = javaCurr.symbol
            )
        }
    }
}
