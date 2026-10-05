package com.example.util

import android.content.Context
import android.os.Build
import com.example.config.AppConfig

/**
 * Helper untuk mendeteksi sumber instalasi aplikasi secara dinamis dan aman.
 *
 * Membedakan antara:
 * 1. Instalasi resmi Google Play Store ("com.android.vending")
 * 2. Instalasi non-Play / sideload / internal testing (ADB, Drive, package installer lokal)
 *
 * Menggunakan PackageManager.getInstallSourceInfo pada Android 11+ (API 30+)
 * dan PackageManager.getInstallerPackageName pada versi Android di bawahnya.
 */
object InstallSourceHelper {
    const val PLAY_STORE_PACKAGE = "com.android.vending"

    fun isInstalledFromGooglePlay(context: Context): Boolean {
        // Jika flag AppConfig disetel manual untuk testing Google Play build
        if (AppConfig.IS_PLAY_STORE_BUILD) return true

        return try {
            val pm = context.packageManager
            val installerPackage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sourceInfo = pm.getInstallSourceInfo(context.packageName)
                sourceInfo.installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName)
            }
            installerPackage == PLAY_STORE_PACKAGE
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Mengembalikan nama installer package (misalnya: "com.android.vending", "com.google.android.packageinstaller", dll.)
     */
    fun getInstallerPackageName(context: Context): String? {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName)
            }
        } catch (_: Exception) {
            null
        }
    }
}
