package com.example.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Dual-Mode Architecture Configuration (Bab 13)
 *
 * - IS_PLAY_STORE_BUILD = false (Default for GitHub / Internal APK build):
 *   Uses manual DANA / E-Wallet payment method + Master Dev Approval flow,
 *   and Google Drive direct APK updates.
 *
 * - IS_PLAY_STORE_BUILD = true (Google Play Store release):
 *   Automatically switches to Google Play Billing API and Google Play In-App Updates SDK,
 *   hiding manual DANA transfer instructions.
 */
object AppConfig {
    var IS_PLAY_STORE_BUILD: Boolean by mutableStateOf(false)

    const val APP_NAME = "FinSmart"
    const val APP_VERSION_NAME = "1.0.3"
    const val APP_VERSION_CODE = 3
    const val APP_AUTHOR = "Lukas"
    const val COPYRIGHT_YEAR = "2026"

    const val DANA_PAYMENT_NUMBER = "085155076566"
    const val DANA_PAYMENT_NAME = "Lukas"

    const val MONTHLY_PRICE_IDR = 19_000.0
    const val YEARLY_PRICE_IDR = 149_000.0

    const val SUPPORT_EMAIL = "lunachroc07@gmail.com"

    const val DEFAULT_METADATA_URL = "https://drive.google.com/uc?export=download&id=YOUR_METADATA_FILE_ID"

    // AdMob Configurations (Bab 9 & 13)
    const val ADMOB_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val ADMOB_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val ADMOB_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    const val ADMOB_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
}
