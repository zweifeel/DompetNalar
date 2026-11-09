package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.config.AppConfig

/**
 * PolicyManager
 *
 * Mengelola status pengakuan dan persetujuan Kebijakan Privasi & Transparansi
 * secara terpisah dari versi aplikasi (Fase C).
 *
 * Ketentuan:
 * - Menggunakan SharedPreferences lokal privat ("finsmart_policy_prefs").
 * - Tidak menyimpan ke database transaksi Room.
 * - Tidak mengirim data persetujuan ke Firestore (zero-cost & privacy-preserving).
 * - Tidak mengikat status kebijakan semata-mata ke email pengguna.
 * - Jika versi kebijakan berubah secara material (AppConfig.PRIVACY_POLICY_VERSION),
 *   aplikasi akan menampilkan kembali layar kebijakan pada pembukaan pertama.
 */
object PolicyManager {

    const val PREFS_NAME = "finsmart_policy_prefs"
    const val KEY_ACKNOWLEDGED_VERSION = "acknowledged_policy_version"
    const val KEY_ACKNOWLEDGED_TIMESTAMP = "acknowledged_policy_timestamp"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Memeriksa apakah versi kebijakan aktif telah dibaca dan dikonfirmasi pengguna.
     */
    fun isPolicyAcknowledged(
        context: Context,
        requiredVersion: String = AppConfig.PRIVACY_POLICY_VERSION
    ): Boolean {
        val acknowledgedVersion = getPrefs(context).getString(KEY_ACKNOWLEDGED_VERSION, null)
        return acknowledgedVersion != null && acknowledgedVersion == requiredVersion
    }

    /**
     * Menyimpan konfirmasi bahwa pengguna telah membaca dan memahami kebijakan privasi versi tertentu.
     */
    fun acknowledgePolicy(
        context: Context,
        version: String = AppConfig.PRIVACY_POLICY_VERSION
    ) {
        getPrefs(context).edit()
            .putString(KEY_ACKNOWLEDGED_VERSION, version)
            .putLong(KEY_ACKNOWLEDGED_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    /**
     * Mengambil versi kebijakan yang terakhir dikonfirmasi pengguna.
     */
    fun getAcknowledgedVersion(context: Context): String? {
        return getPrefs(context).getString(KEY_ACKNOWLEDGED_VERSION, null)
    }

    /**
     * Mengambil waktu konfirmasi kebijakan dalam epoch milliseconds.
     */
    fun getAcknowledgedTimestamp(context: Context): Long {
        return getPrefs(context).getLong(KEY_ACKNOWLEDGED_TIMESTAMP, 0L)
    }

    /**
     * Untuk pengujian unit & isolasi test.
     */
    fun resetPolicyForTesting(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
