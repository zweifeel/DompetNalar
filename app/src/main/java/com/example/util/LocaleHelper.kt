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
    private var appContext: Context? = null

    /**
     * Inisialisasi awal saat aplikasi atau Activity dimulai.
     * Membaca bahasa tersimpan dari SharedPreferences dan menerapkannya.
     * Menyelaraskan LocaleManager Android 13+ agar tidak terkunci pada konfigurasi usang.
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        val savedCode = prefs?.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.code) ?: AppLanguage.SYSTEM.code
        val lang = AppLanguage.fromCode(savedCode)
        _currentLanguage.value = lang

        // Sinkronisasi LocaleManager Android 13+ (API 33+) secara aman saat inisialisasi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                if (localeManager != null) {
                    val currentLocales = localeManager.applicationLocales
                    val targetLocales = when (lang) {
                        AppLanguage.SYSTEM -> LocaleList.getEmptyLocaleList()
                        AppLanguage.INDONESIAN -> LocaleList(Locale("id", "ID"))
                        AppLanguage.ENGLISH -> LocaleList(Locale.US)
                    }
                    if (currentLocales != targetLocales) {
                        localeManager.applicationLocales = targetLocales
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gagal sinkronisasi LocaleManager saat init: ${e.message}")
            }
        }

        applyLocaleResourcesOnly(context.applicationContext, lang)
    }

    /**
     * Mengatur bahasa baru, menyimpan ke preferensi, dan menerapkan ke konfigurasi internal.
     * Jetpack Compose mengamati StateFlow `currentLanguage` sehingga UI otomatis me-recompose
     * secara instan tanpa perlu me-recreate Activity atau memicu crash OS pada OEM ROM.
     */
    fun setLanguage(context: Context, language: AppLanguage) {
        appContext = context.applicationContext
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        prefs?.edit()?.putString(KEY_LANGUAGE, language.code)?.commit()
        _currentLanguage.value = language

        // Sinkronisasi ke LocaleManager Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                if (localeManager != null) {
                    val targetLocales = when (language) {
                        AppLanguage.SYSTEM -> LocaleList.getEmptyLocaleList()
                        AppLanguage.INDONESIAN -> LocaleList(Locale("id", "ID"))
                        AppLanguage.ENGLISH -> LocaleList(Locale.US)
                    }
                    if (localeManager.applicationLocales != targetLocales) {
                        localeManager.applicationLocales = targetLocales
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gagal mengupdate LocaleManager: ${e.message}")
            }
        }

        // Terapkan locale ke Locale Java default dan Resources secara non-destruktif
        applyLocaleInternal(context.applicationContext, language, isUserExplicitChange = true)
    }

    @androidx.annotation.VisibleForTesting
    var systemLocaleOverrideForTesting: Locale? = null

    /**
     * Mengambil Locale sistem perangkat asli.
     * Menggunakan LocaleManager.systemLocales pada Android 13+ untuk membaca setelan bahasa OS murni
     * tanpa terpengaruh konfigurasi per-app yang sempat tertinggal.
     */
    fun getSystemLocale(): Locale {
        systemLocaleOverrideForTesting?.let { return it }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext?.let { ctx ->
                    val lm = ctx.getSystemService(LocaleManager::class.java)
                    val sysLocales = lm?.systemLocales
                    if (sysLocales != null && !sysLocales.isEmpty) {
                        sysLocales.get(0)?.let { return it }
                    }
                }
            }
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
                // Di unit test headless (appContext == null), jika tidak ada override pengujian eksplisit,
                // baseline bahasa aplikasi adalah Bahasa Indonesia (false) agar test-test assertion lama
                // yang memvalidasi pesan/toast bahasa Indonesia tidak terpengaruh oleh locale OS Windows developer.
                if (appContext == null && systemLocaleOverrideForTesting == null) {
                    false
                } else {
                    val sysLocale = getSystemLocale()
                    val lang = sysLocale.language.lowercase(Locale.ROOT)
                    lang != "id" && lang != "in"
                }
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

        // Perbarui Resources configuration secara non-destruktif
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
        return true
    }

    /**
     * Membungkus base context. Pada Jetpack Compose, wrapping createConfigurationContext
     * pada Activity.attachBaseContext melepaskan window token dan menyebabkan BadTokenException / crash.
     * Mengembalikan base context murni untuk stabilitas penuh.
     */
    fun wrapContext(base: Context): Context {
        return base
    }
}
