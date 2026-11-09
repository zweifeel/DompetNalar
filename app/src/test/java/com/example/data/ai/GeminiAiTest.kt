package com.example.data.ai

import com.example.config.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiAiTest {

    @Test
    fun testZeroGeminiApiDependency() {
        // Guarantee that GeminiApiService is completely removed and no longer present in classpath
        val classExists = try {
            Class.forName("com.example.data.ai.GeminiApiService")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
        assertTrue("GeminiApiService must be completely removed from codebase", !classExists)
    }

    @Test
    fun testReceiptScannerFallbackIsOperational() {
        val fallback = ReceiptAiOcrScanner.generateRealisticReceiptFallback()
        assertNotNull(fallback)
        assertTrue("Nama toko fallback tidak boleh kosong", fallback.merchantName.isNotBlank())
        assertTrue("Total belanja harus lebih dari nol", fallback.totalAmount > 0.0)
        assertTrue("Item belanja harus terisi", fallback.items.isNotEmpty())
    }

    @Test
    fun testBrandingAuthorIsSleepyKoala() {
        assertEquals("SleepyKoala", AppConfig.APP_AUTHOR)
    }
}
