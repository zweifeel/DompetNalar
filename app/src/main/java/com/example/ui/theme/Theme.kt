package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.util.AppAccentColor
import com.example.util.AppThemeMode
import com.example.util.ThemeEngine

fun buildDarkColorScheme(accent: AppAccentColor) = darkColorScheme(
    primary = accent.primaryLight,
    onPrimary = Color.White,
    primaryContainer = accent.primaryDark,
    onPrimaryContainer = Color.White,
    secondary = accent.primary,
    onSecondary = Color.White,
    tertiary = FinSmartTertiary,
    background = BgDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = SlateBorder
)

fun buildLightColorScheme(accent: AppAccentColor) = lightColorScheme(
    primary = accent.primary,
    onPrimary = Color.White,
    primaryContainer = accent.primaryLight.copy(alpha = 0.2f),
    onPrimaryContainer = accent.primaryDark,
    secondary = accent.primaryLight,
    onSecondary = Color.White,
    tertiary = FinSmartTertiary,
    background = BgLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFE2E8F0)
)

@Composable
fun FinSmartTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val themeMode by ThemeEngine.currentThemeMode.collectAsState()
    val accentColor by ThemeEngine.currentAccentColor.collectAsState()

    val isDark = ThemeEngine.isDarkThemeActive(themeMode, systemDark)

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> buildDarkColorScheme(accentColor)
        else -> buildLightColorScheme(accentColor)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

