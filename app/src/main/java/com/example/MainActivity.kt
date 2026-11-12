package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.FinSmartApp
import com.example.ui.theme.FinSmartTheme
import com.example.util.AdManager
import com.example.util.AppIconManager
import com.example.util.LocaleHelper
import com.example.util.ThemeEngine
import java.io.File

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Locale configuration
        LocaleHelper.initialize(this)
        
        // Initialize Theme & Dynamic App Icon engines
        ThemeEngine.initialize(this)
        AppIconManager.initialize(this)
        com.example.util.CurrencyFormatter.initialize(this)

        // Ensure WebView cache directories exist for Chromium sub-systems
        ensureWebViewCacheDirs()

        // Initialize AdMob and UMP Consent asynchronously
        com.example.util.ConsentManager.gatherConsent(this) { consentResult ->
            if (consentResult.canRequestAds) {
                AdManager.initialize(this)
            }
        }

        enableEdgeToEdge()
        setContent {
            FinSmartTheme {
                FinSmartApp()
            }
        }
    }

    private fun ensureWebViewCacheDirs() {
        try {
            val webViewCacheDir = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache")
            File(webViewCacheDir, "js").mkdirs()
            File(webViewCacheDir, "wasm").mkdirs()
        } catch (_: Exception) {
            // Non-fatal directory initialization
        }
    }
}
