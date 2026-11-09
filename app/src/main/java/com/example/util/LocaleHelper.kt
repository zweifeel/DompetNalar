package com.example.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Extension untuk menemukan Activity dari Context (termasuk ContextWrapper Compose).
 */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

enum class AppLanguage(
    val code: String,
    val idDisplayName: String,
    val enDisplayName: String,
    val idSubtitle: String,
    val enSubtitle: String
) {
    SYSTEM(
        code = "system",
        idDisplayName = "Ikuti Sistem HP",
        enDisplayName = "Follow System",
        idSubtitle = "Menyesuaikan dengan pengaturan bahasa perangkat Anda",
        enSubtitle = "Matches your device system language setting"
    ),
    INDONESIAN(
        code = "id",
        idDisplayName = "Bahasa Indonesia",
        enDisplayName = "Indonesian",
        idSubtitle = "Gunakan Bahasa Indonesia secara penuh",
        enSubtitle = "Use Indonesian throughout the application"
    ),
    ENGLISH(
        code = "en",
        idDisplayName = "English",
        enDisplayName = "English",
        idSubtitle = "Gunakan Bahasa Inggris untuk seluruh aplikasi",
        enSubtitle = "Use English throughout the application"
    );

    val displayName: String
        get() = if (LocaleHelper.isEnglish()) enDisplayName else idDisplayName

    val subtitle: String
        get() = if (LocaleHelper.isEnglish()) enSubtitle else idSubtitle

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: SYSTEM
        }
    }
}

object LocaleHelper {

    private const val TAG = "LocaleHelper"
    private const val PREFS_NAME = "finsmart_locale_prefs"
    private const val KEY_LANGUAGE = "app_language"

    private val _currentLanguage = MutableStateFlow(AppLanguage.SYSTEM)
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    private var prefs: SharedPreferences? = null

    /**
     * Inisialisasi awal saat aplikasi atau Activity dimulai.
     * Membaca bahasa tersimpan dari SharedPreferences dan menerapkannya.
     * Tidak memutasi LocaleManager agar tidak memicu restart/recreate loop saat startup.
     */
    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        val savedCode = prefs?.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.code) ?: AppLanguage.SYSTEM.code
        val lang = AppLanguage.fromCode(savedCode)
        _currentLanguage.value = lang
        applyLocaleResourcesOnly(context.applicationContext, lang)
    }

    /**
     * Mengatur bahasa baru, menyimpan ke preferensi, menerapkan ke konfigurasi Android,
     * dan me-recreate Activity jika diperlukan (pada API < 33 secara aman).
     */
    fun setLanguage(context: Context, language: AppLanguage) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        prefs?.edit()?.putString(KEY_LANGUAGE, language.code)?.commit()
        _currentLanguage.value = language

        // Terapkan ke konfigurasi Application dan Context lokal
        val localeManagerApplied = applyLocaleInternal(context.applicationContext, language, isUserExplicitChange = true)

        val activity = context.findActivity() ?: (if (context is Activity) context else null)

        // Pada Android 13+ (API 33+), jika LocaleManager mengubah applicationLocales,
        // sistem secara otomatis merestart Activity.
        // Hanya lakukan recreate manual jika LocaleManager tidak mengubah konfigurasi atau gagal,
        // dan jalankan melalui Handler Looper utama agar transisi Compose event loop selesai bersih.
        if (!localeManagerApplied) {
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            activity.recreate()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Gagal me-recreate activity: ${e.message}")
                    }
                }
            }
        }
    }

    @androidx.annotation.VisibleForTesting
    var systemLocaleOverrideForTesting: Locale? = null

    /**
     * Mengambil Locale sistem perangkat asli.
     */
    fun getSystemLocale(): Locale {
        systemLocaleOverrideForTesting?.let { return it }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Resources.getSystem().configuration.locales.get(0) ?: Locale.getDefault()
            } else {
                @Suppress("DEPRECATION")
                Resources.getSystem().configuration.locale ?: Locale.getDefault()
            }
        } catch (_: Throwable) {
            Locale.getDefault()
        }
    }

    /**
     * Memeriksa apakah mode bahasa aktif adalah Bahasa Inggris (atau bahasa internasional).
     * Pada mode SYSTEM:
     * - Jika bahasa perangkat adalah Bahasa Indonesia ("id" atau legacy "in"), gunakan Bahasa Indonesia (false).
     * - Jika bahasa perangkat adalah bahasa internasional lainnya (Inggris, Spanyol, Prancis, dsb.),
     *   gunakan Bahasa Inggris (true) sebagai kamus teks antarmuka standar global.
     */
    fun isEnglish(): Boolean {
        return when (_currentLanguage.value) {
            AppLanguage.ENGLISH -> true
            AppLanguage.INDONESIAN -> false
            AppLanguage.SYSTEM -> {
                val sysLocale = getSystemLocale()
                val lang = sysLocale.language.lowercase(Locale.ROOT)
                lang != "id" && lang != "in"
            }
        }
    }

    /**
     * Mengambil Locale Java aktif untuk pemformatan tanggal, angka, dan mata uang.
     * Pada mode SYSTEM, mengembalikan Locale sistem perangkat aktual secara murni.
     */
    fun getActiveLocale(): Locale {
        return when (_currentLanguage.value) {
            AppLanguage.SYSTEM -> getSystemLocale()
            AppLanguage.INDONESIAN -> Locale("id", "ID")
            AppLanguage.ENGLISH -> Locale.US
        }
    }

    /**
     * Menerapkan konfigurasi locale ke sistem Android dan Resources.
     */
    fun applyLocale(context: Context, language: AppLanguage) {
        applyLocaleInternal(context, language, isUserExplicitChange = false)
    }

    private fun applyLocaleResourcesOnly(context: Context, language: AppLanguage) {
        applyLocaleInternal(context, language, isUserExplicitChange = false)
    }

    private fun applyLocaleInternal(context: Context, language: AppLanguage, isUserExplicitChange: Boolean): Boolean {
        val locale = when (language) {
            AppLanguage.SYSTEM -> getSystemLocale()
            AppLanguage.INDONESIAN -> Locale("id", "ID")
            AppLanguage.ENGLISH -> Locale.US
        }

        Locale.setDefault(locale)

        // Hanya modifikasi Resources.updateConfiguration jika versi Android < 33 (Tiramisu).
        // Pada Android 13+ (API 33+), updateConfiguration deprecated dan merusak konfigurasi sistem serta token window Compose.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            try {
                val res = context.resources
                val config = Configuration(res.configuration)
                config.setLocale(locale)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    config.setLocales(LocaleList(locale))
                }
                @Suppress("DEPRECATION")
                res.updateConfiguration(config, res.displayMetrics)
            } catch (e: Exception) {
                Log.w(TAG, "Gagal mengupdate Resources configuration: ${e.message}")
            }
        }

        // Dukungan Android 13 (API level 33) Per-App Language Preferences
        // HANYA mutasi LocaleManager saat dipanggil dari perubahan eksplisit pengguna (setLanguage).
        // Jangan mutasi saat initialize() agar tidak memicu configuration change / recreate cascade loop.
        var localeManagerHandled = false
        if (isUserExplicitChange && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                if (localeManager != null) {
                    val targetLocaleList = when (language) {
                        AppLanguage.SYSTEM -> LocaleList.getEmptyLocaleList()
                        AppLanguage.INDONESIAN -> LocaleList(Locale("id", "ID"))
                        AppLanguage.ENGLISH -> LocaleList(Locale.US)
                    }
                    val currentLocales = localeManager.applicationLocales
                    if (currentLocales != targetLocaleList) {
                        localeManager.applicationLocales = targetLocaleList
                        localeManagerHandled = true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gagal mengupdate LocaleManager: ${e.message}")
            }
        }
        return localeManagerHandled
    }

    /**
     * Membungkus base context agar Activity langsung mewarisi konfigurasi locale yang benar pada API < 33.
     * Pada Android 13+ (API 33+), pembungkusan createConfigurationContext di attachBaseContext DILARANG
     * karena melepaskan Activity Window Token yang menyebabkan crash Jetpack Compose saat Activity direstart.
     */
    fun wrapContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return base
        }
        val locale = getActiveLocale()
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        }
        return base.createConfigurationContext(config)
    }
}
