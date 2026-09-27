package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.FinSmartApp
import com.example.ui.theme.FinSmartTheme
import com.example.util.AdManager
import com.example.util.AppIconManager
import com.example.util.ThemeEngine
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Theme & Dynamic App Icon engines
        ThemeEngine.initialize(this)
        AppIconManager.initialize(this)

        // Ensure WebView cache directories exist for Chromium sub-systems
        ensureWebViewCacheDirs()

        // Initialize AdMob asynchronously
        AdManager.initialize(this)

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
