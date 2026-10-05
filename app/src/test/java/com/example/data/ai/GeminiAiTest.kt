package com.example.data.ai

import com.example.config.AppConfig
import com.example.data.model.ReceiptScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.POST

class GeminiAiTest {

    @Test
    fun testDefaultGeminiModelIsFlashLite() {
        // Inspect the GeminiApiService Retrofit annotation to guarantee gemini-3.5-flash-lite
        val method = GeminiApiService::class.java.getMethod(
            "generateContent",
            String::class.java,
            GeminiGenerateRequest::class.java,
            kotlin.coroutines.Continuation::class.java
        )
        val postAnnotation = method.getAnnotation(POST::class.java)
        assertNotNull("generateContent harus memiliki anotasi @POST", postAnnotation)
        assertEquals(
            "Endpoint default harus mengarah ke v1beta/models/gemini-3.5-flash-lite:generateContent",
            "v1beta/models/gemini-3.5-flash-lite:generateContent",
            postAnnotation!!.value
        )
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
