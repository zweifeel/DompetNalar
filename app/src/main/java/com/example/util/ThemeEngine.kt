package com.example.util

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

enum class AppThemeMode(val title: String, val subtitle: String) {
    SYSTEM("Ikuti Sistem HP (Default)", "Mengikuti mode terang/gelap bawaan pengaturan HP"),
    AUTO_TIME("Otomatis Sesuai Waktu", "Mode Gelap saat malam (18.00 - 06.00), Terang saat siang"),
    LIGHT("Mode Terang (Light Mode)", "Tampilan cerah dengan kontras tinggi"),
    DARK("Mode Gelap (Dark Mode)", "Tampilan gelap elegan dan hemat daya")
}

enum class AppAccentColor(
    val displayName: String,
    val primary: Color,
    val primaryLight: Color,
    val primaryDark: Color,
    val sampleHex: String
) {
    OCEAN_BLUE(
        displayName = "Ocean Blue (Bawaan)",
        primary = Color(0xFF0B57D0),
        primaryLight = Color(0xFF3B82F6),
        primaryDark = Color(0xFF1D4ED8),
        sampleHex = "#0B57D0"
    ),
    EMERALD_GREEN(
        displayName = "Emerald Green",
        primary = Color(0xFF00897B),
        primaryLight = Color(0xFF10B981),
        primaryDark = Color(0xFF004D40),
        sampleHex = "#00897B"
    ),
    SUNSET_GOLD(
        displayName = "Sunset Gold",
        primary = Color(0xFFD97706),
        primaryLight = Color(0xFFF59E0B),
        primaryDark = Color(0xFFB45309),
        sampleHex = "#D97706"
    ),
    CYBER_PURPLE(
        displayName = "Cyber Purple",
        primary = Color(0xFF8E24AA),
        primaryLight = Color(0xFFAB47BC),
        primaryDark = Color(0xFF6A1B9A),
        sampleHex = "#8E24AA"
    ),
    CRIMSON_RED(
        displayName = "Crimson Red",
        primary = Color(0xFFE53935),
        primaryLight = Color(0xFFEF4444),
        primaryDark = Color(0xFFC62828),
        sampleHex = "#E53935"
    )
}

object ThemeEngine {
    private const val TAG = "ThemeEngine"
    private const val PREFS_NAME = "finsmart_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ACCENT_COLOR = "accent_color"
    private const val KEY_WALLPAPER_URI = "wallpaper_uri"
    private const val KEY_WALLPAPER_OPACITY = "wallpaper_opacity"

    private val _currentThemeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val currentThemeMode: StateFlow<AppThemeMode> = _currentThemeMode.asStateFlow()

    private val _currentAccentColor = MutableStateFlow(AppAccentColor.OCEAN_BLUE)
    val currentAccentColor: StateFlow<AppAccentColor> = _currentAccentColor.asStateFlow()

    private val _customWallpaperUri = MutableStateFlow<String?>(null)
    val customWallpaperUri: StateFlow<String?> = _customWallpaperUri.asStateFlow()

    private val _customWallpaperOpacity = MutableStateFlow(0.35f)
    val customWallpaperOpacity: StateFlow<Float> = _customWallpaperOpacity.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val savedMode = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
        _currentThemeMode.value = try {
            AppThemeMode.valueOf(savedMode ?: AppThemeMode.SYSTEM.name)
        } catch (_: Exception) {
            AppThemeMode.SYSTEM
        }

        val savedAccent = prefs.getString(KEY_ACCENT_COLOR, AppAccentColor.OCEAN_BLUE.name)
        _currentAccentColor.value = try {
            AppAccentColor.valueOf(savedAccent ?: AppAccentColor.OCEAN_BLUE.name)
        } catch (_: Exception) {
            AppAccentColor.OCEAN_BLUE
        }

        _customWallpaperUri.value = prefs.getString(KEY_WALLPAPER_URI, null)
        _customWallpaperOpacity.value = prefs.getFloat(KEY_WALLPAPER_OPACITY, 0.35f)
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        _currentThemeMode.value = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        Log.d(TAG, "Theme mode set to: $mode")
    }

    fun setAccentColor(context: Context, accent: AppAccentColor) {
        _currentAccentColor.value = accent
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACCENT_COLOR, accent.name).apply()
        Log.d(TAG, "Accent color set to: $accent")
    }

    fun setCustomWallpaper(context: Context, uriString: String?, opacity: Float = 0.35f) {
        _customWallpaperUri.value = uriString
        _customWallpaperOpacity.value = opacity.coerceIn(0.05f, 0.95f)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_WALLPAPER_URI, uriString)
            .putFloat(KEY_WALLPAPER_OPACITY, _customWallpaperOpacity.value)
            .apply()
        Log.d(TAG, "Custom wallpaper updated: $uriString (opacity: $opacity)")
    }

    fun clearCustomWallpaper(context: Context) {
        setCustomWallpaper(context, null, 0.35f)
    }

    fun isNightTime(): Boolean {
        return try {
            val now = LocalTime.now()
            val hour = now.hour
            hour >= 18 || hour < 6
        } catch (_: Exception) {
            false
        }
    }

    fun isDarkThemeActive(themeMode: AppThemeMode, systemIsDark: Boolean): Boolean {
        return when (themeMode) {
            AppThemeMode.SYSTEM -> systemIsDark
            AppThemeMode.AUTO_TIME -> isNightTime()
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK -> true
        }
    }
}
