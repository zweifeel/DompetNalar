package com.example.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AppLanguage(val code: String, val displayName: String, val subtitle: String) {
    SYSTEM("system", "Ikuti Sistem HP (Otomatis)", "Menyesuaikan dengan pengaturan bahasa perangkat Anda"),
    INDONESIAN("id", "Bahasa Indonesia", "Gunakan Bahasa Indonesia secara penuh"),
    ENGLISH("en", "English", "Use English throughout the application")
}

object LocaleHelper {
    private val _currentLanguage = MutableStateFlow(AppLanguage.SYSTEM)
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        _currentLanguage.value = language
    }

    fun getActiveLocale(): Locale {
        return when (_currentLanguage.value) {
            AppLanguage.SYSTEM -> Locale.getDefault()
            AppLanguage.INDONESIAN -> Locale("id", "ID")
            AppLanguage.ENGLISH -> Locale("en", "US")
        }
    }
}
