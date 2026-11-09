package com.example.util

import android.content.Context
import android.content.SharedPreferences

/**
 * OnboardingManager
 *
 * Mengelola status penyelesaian dialog selamat datang / onboarding pertama kali aplikasi diinstal.
 *
 * Ketentuan:
 * - Menggunakan SharedPreferences lokal privat ("finsmart_onboarding_prefs").
 * - Zero-cost FOSS: Tidak mengirim data apa pun ke internet atau server cloud.
 * - Bersih dari database Room: Tidak membebani skema Room SQLite.
 */
object OnboardingManager {

    const val PREFS_NAME = "finsmart_onboarding_prefs"
    const val KEY_ONBOARDING_COMPLETED = "has_completed_first_run_onboarding"
    const val KEY_ONBOARDING_TIMESTAMP = "onboarding_completed_timestamp"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Memeriksa apakah pengguna baru sudah menyelesaikan pengaturan awal instalasi (bahasa & mata uang).
     */
    fun isOnboardingCompleted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    /**
     * Menandai bahwa proses onboarding instalasi telah diselesaikan.
     */
    fun completeOnboarding(context: Context) {
        getPrefs(context).edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, true)
            .putLong(KEY_ONBOARDING_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    /**
     * Mengambil waktu penyelesaian onboarding dalam epoch milliseconds.
     */
    fun getCompletedTimestamp(context: Context): Long {
        return getPrefs(context).getLong(KEY_ONBOARDING_TIMESTAMP, 0L)
    }

    /**
     * Digunakan untuk isolasi unit test otomatis.
     */
    fun resetForTesting(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
