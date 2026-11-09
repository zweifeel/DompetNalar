package com.example.util

import android.content.res.Resources
import android.os.Build
import java.util.Currency
import java.util.Locale

/**
 * Resolves the device's system regional ISO 3166 country code and ISO 4217 currency code
 * directly from framework system resources (Resources.getSystem()), ensuring complete
 * independence from in-app language overrides or Locale.setDefault() mutations.
 */
object DeviceRegionResolver {

    /**
     * Resolves the device's system regional ISO 3166 2-letter country code.
     * Guaranteed independent of in-app language overrides, Locale.setDefault(), or ContextWrapper.
     *
     * @return 2-letter uppercase ISO country code, or null if language-only, missing, or invalid.
     */
    fun resolveDeviceCountry(): String? {
        return try {
            val systemLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val locales = Resources.getSystem().configuration.locales
                if (!locales.isEmpty) locales.get(0) else null
            } else {
                @Suppress("DEPRECATION")
                Resources.getSystem().configuration.locale
            }

            val country = systemLocale?.country?.trim()?.uppercase(Locale.ROOT)
            if (!country.isNullOrBlank() && country.length == 2) country else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Maps the resolved system country to an official ISO 4217 currency code.
     * Rejects language-only, missing, invalid, or pseudo-currencies.
     */
    fun resolveDeviceCurrencyCode(): String? {
        val country = resolveDeviceCountry() ?: return null
        return try {
            val currency = Currency.getInstance(Locale("", country))
            if (currency.defaultFractionDigits >= 0) currency.currencyCode else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolves the currency code for a given ISO 3166-1 alpha-2 country code.
     * Returns null if invalid, null, unsupported, or unresolvable.
     * ZERO arbitrary IDR or USD fallback.
     */
    fun resolveCurrencyForCountry(countryCode: String?): String? {
        val country = countryCode?.trim()?.uppercase(Locale.ROOT)
        if (country.isNullOrBlank() || country.length != 2) return null
        return try {
            val currency = Currency.getInstance(Locale("", country))
            if (currency.defaultFractionDigits >= 0) currency.currencyCode else null
        } catch (e: Exception) {
            null
        }
    }
}
