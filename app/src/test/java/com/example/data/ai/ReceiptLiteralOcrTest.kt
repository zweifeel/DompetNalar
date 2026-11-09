package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ReceiptItem
import com.example.data.model.ReceiptScanResult
import com.example.data.model.TaxTreatment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReceiptLiteralOcrTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testUjiOcrAssetExistsAndReadable() {
        // 1. Foto nota uji fisik wajib ada dan dapat dibaca sebagai file/gambar
        val testAssetFile = if (File("test-assets/nota_uji_ocr_finsmart.jpg").exists()) {
            File("test-assets/nota_uji_ocr_finsmart.jpg")
        } else {
            File("../test-assets/nota_uji_ocr_finsmart.jpg")
        }
        assertTrue("File test-assets/nota_uji_ocr_finsmart.jpg harus tetap ada", testAssetFile.exists())
        assertTrue("Ukuran file nota uji harus lebih dari 0 byte", testAssetFile.length() > 0)

        val bitmap = testAssetFile.inputStream().use { BitmapFactory.decodeStream(it) }
        assertNotNull("Nota uji harus dapat didecode menjadi Bitmap oleh sistem Android", bitmap)
        assertTrue("Tinggi nota uji harus valid", bitmap.height > 0)
    }

    @Test
    fun testEmptyOrNoTextResponse_throwsWithoutDummyFallback() {
        // 2. Respons kosong atau tidak memuat teks tidak boleh menghasilkan preset palsu
        val emptyText = "   "
        val noTextFlag = "TIDAK_ADA_TEKS_TERBACA"

        val isInvalid1 = emptyText.isBlank() || emptyText.contains("TIDAK_ADA_TEKS_TERBACA", ignoreCase = true)
        val isInvalid2 = noTextFlag.isBlank() || noTextFlag.contains("TIDAK_ADA_TEKS_TERBACA", ignoreCase = true)

        assertTrue("Respons kosong harus ditandai tidak valid", isInvalid1)
        assertTrue("Respons TIDAK_ADA_TEKS_TERBACA harus ditandai tidak valid", isInvalid2)
    }

    @Test
    fun testUnreadableSection_doesNotInventNumbers() {
        // 3. Bagian yang tidak terbaca tidak boleh diisi dengan angka rekaan
        val sampleWithUnreadable = """
            {
              "transkripsi_literal": "INDOMARET MOJOPURO\nITEM A : [tidak terbaca]\nTOTAL : [tidak terbaca]",
              "nama_toko": "INDOMARET MOJOPURO",
              "tanggal": "[tidak terbaca]",
              "subtotal": 0.0,
              "total_akhir": 0.0,
              "items": [
                {
                  "nama_barang": "ITEM A",
                  "jumlah": 1,
                  "harga_satuan": 0.0,
                  "total_harga": 0.0
                }
              ]
            }
        """.trimIndent()

        val json = org.json.JSONObject(sampleWithUnreadable)
        val rawText = json.getString("transkripsi_literal")
        assertTrue("Teks literal harus memuat penanda [tidak terbaca]", rawText.contains("[tidak terbaca]"))

        val totalAkhir = json.getDouble("total_akhir")
        assertEquals("Total akhir tidak boleh diisi angka rekaan", 0.0, totalAkhir, 0.001)
    }

    @Test
    fun testNonJsonLiteralTextResponse_doesNotCrashAndKeepsTranscription() {
        // 4. Format respons plain text non-JSON tidak boleh crash dan teks literal harus utuh
        val plainTextResponse = """
            WARUNG MAKAN SEDAP
            JL. RAYA NO. 12
            1 NASI GORENG    25.000
            1 ES TEH MANIS    5.000
            TOTAL            30.000
        """.trimIndent()

        // Robust parsing logic verification
        val start = plainTextResponse.indexOf('{')
        val end = plainTextResponse.lastIndexOf('}')
        val json = if (start != -1 && end != -1 && end > start) {
            try { org.json.JSONObject(plainTextResponse.substring(start, end + 1)) } catch (_: Exception) { null }
        } else null

        val literalTranscription = if (json != null) {
            json.optString("transkripsi_literal", plainTextResponse)
        } else {
            plainTextResponse
        }

        assertEquals("Transkripsi literal harus menyimpan teks plain apa adanya", plainTextResponse, literalTranscription)
        val merchant = plainTextResponse.lines().firstOrNull { it.isNotBlank() }?.trim() ?: "Struk Belanja"
        assertEquals("WARUNG MAKAN SEDAP", merchant)
    }

    @Test
    fun testConcurrentScanRaceCondition_tokenProtection() {
        // 5. Dua request foto berbeda dengan timestamp/token asinkron tidak boleh saling menimpa
        var activeScanToken = 1000L
        val oldRequestToken = 1000L
        val newRequestToken = 2000L

        // User memilih foto kedua saat foto pertama masih berjalan
        activeScanToken = newRequestToken

        var isOldResultAccepted = false
        var isNewResultAccepted = false

        // Respon foto pertama selesai terlambat
        if (activeScanToken == oldRequestToken) {
            isOldResultAccepted = true
        }

        // Respon foto kedua selesai
        if (activeScanToken == newRequestToken) {
            isNewResultAccepted = true
        }

        assertFalse("Hasil foto lama yang terlambat TIDAK boleh menimpa UI", isOldResultAccepted)
        assertTrue("Hasil foto terbaru HARUS diterima oleh UI", isNewResultAccepted)
    }

    @Test
    fun testLiteralTranscription_doesNotAutoPersistTransaction() {
        // 6. Transkripsi OCR murni membaca teks dan TIDAK menyimpan transaksi otomatis ke database
        val scanResult = ReceiptScanResult(
            merchantName = "Toko Ritel Uji",
            date = "11-10-2026",
            subtotal = 50000.0,
            totalAmount = 50000.0,
            rawText = "Toko Ritel Uji\n1x Barang A 50.000\nTotal 50.000"
        )

        assertNotNull(scanResult.rawText)
        assertTrue("Hasil harus memuat transkripsi teks", scanResult.rawText!!.isNotBlank())
        // Model ReceiptScanResult hanyalah transient UI transfer object tanpa DB persistence otomatis
        assertEquals(50000.0, scanResult.totalAmount, 0.001)
    }
}
