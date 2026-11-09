package com.example.config

import com.example.BuildConfig

/**
 * Dual-Mode Architecture Configuration (Bab 13)
 *
 * - IS_PLAY_STORE_BUILD:
 *   Sourced explicitly and immutably from BuildConfig.IS_PLAY_STORE_BUILD.
 *   - false on direct APK sideload / internal development builds.
 */
object AppConfig {
    val IS_PLAY_STORE_BUILD: Boolean = BuildConfig.IS_PLAY_STORE_BUILD

    const val APP_NAME = "DompetNalar"
    val APP_VERSION_NAME: String = BuildConfig.VERSION_NAME
    val APP_VERSION_CODE: Int = BuildConfig.VERSION_CODE
    const val PRIVACY_POLICY_VERSION = "1.0.0"
    const val APP_AUTHOR = "SleepyKoala"
    const val COPYRIGHT_YEAR = "2026"


    const val MONTHLY_PRICE_IDR = 19_000.0
    const val YEARLY_PRICE_IDR = 149_000.0

    const val SUPPORT_EMAIL = "lunachroc01@gmail.com"

    const val DEFAULT_METADATA_URL = "https://drive.google.com/uc?export=download&id=YOUR_METADATA_FILE_ID"

    // AdMob Configurations (Bab 9 & 13)
    val ADMOB_APP_ID: String = BuildConfig.ADMOB_APP_ID
    val ADMOB_BANNER_AD_UNIT_ID: String = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
    val ADMOB_REWARDED_AD_UNIT_ID: String = BuildConfig.ADMOB_REWARDED_AD_UNIT_ID
    val ADMOB_INTERSTITIAL_AD_UNIT_ID: String = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID

    // Google Identity & OAuth Configurations (Real Google Sign-In & Drive Cloud Sync)
    const val GOOGLE_SIGN_IN_WEB_CLIENT_ID = "1029968996578-kd1pohf7nhge6mmm3m3uagr83e40tti6.apps.googleusercontent.com"
    const val GOOGLE_DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
}
