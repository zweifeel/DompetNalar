package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.data.model.ReceiptScanResult
import com.example.data.ocr.LocalReceiptOcrScanner

/**
 * Adaptor Pemindai Nota FinSmart (100% Local-First).
 *
 * Meneruskan panggilan OCR ke [LocalReceiptOcrScanner] on-device
 * tanpa memerlukan Gemini API Key atau koneksi internet.
 */
object ReceiptAiOcrScanner {

    fun generateRealisticReceiptFallback(imageUriStr: String? = null, presetType: Int = 1): ReceiptScanResult {
        return LocalReceiptOcrScanner.generateRealisticReceiptFallback(imageUriStr, presetType)
    }

    suspend fun scanReceiptImage(
        context: Context,
        imageUri: Uri?,
        bitmap: Bitmap? = null
    ): ReceiptScanResult {
        return LocalReceiptOcrScanner.scanReceiptImage(context, imageUri, bitmap)
    }
}
