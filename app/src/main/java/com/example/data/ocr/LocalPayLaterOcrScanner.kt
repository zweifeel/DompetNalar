package com.example.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.example.data.model.PayLaterBillScanResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Pemindai OCR Tagihan PayLater On-Device FinSmart (Local-First ML Kit).
 *
 * 100% Berjalan di perangkat lokal tanpa internet dan tanpa Gemini API / AI Cloud:
 * 1. Menjalankan on-device Google ML Kit Text Recognition.
 * 2. Mengolah teks dengan [DeterministicPayLaterParser].
 * 3. Tidak ada data keuangan yang dikirim ke server luar.
 */
object LocalPayLaterOcrScanner {

    private const val TAG = "LocalPayLaterOcrScanner"

    /**
     * Test hook untuk injeksi hasil transkripsi teks pada unit test / Robolectric.
     */
    var textRecognizerHook: ((Bitmap) -> String)? = null

    suspend fun scanPayLaterScreenshot(
        context: Context,
        imageUri: Uri?,
        bitmap: Bitmap? = null
    ): PayLaterBillScanResult = withContext(Dispatchers.IO) {
        val loadedBitmap = bitmap ?: imageUri?.let { loadBitmapFromUri(context, it) }
            ?: throw IllegalArgumentException("Tidak dapat memuat gambar tangkapan layar dari kamera atau galeri.")

        // 1. Ekstraksi Teks Literal On-Device
        val rawText: String = if (textRecognizerHook != null) {
            textRecognizerHook!!.invoke(loadedBitmap)
        } else {
            try {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val inputImage = InputImage.fromBitmap(loadedBitmap, 0)
                val visionText = recognizer.process(inputImage).await()
                visionText.text
            } catch (e: Exception) {
                Log.w(TAG, "Gagal menjalankan ML Kit text recognition: ${e.message}")
                throw IllegalStateException("Gagal memproses gambar tagihan secara lokal: ${e.localizedMessage ?: "Format gambar tidak didukung"}")
            }
        }

        if (rawText.isBlank() || rawText.contains("TIDAK_ADA_TEKS_TERBACA", ignoreCase = true)) {
            throw IllegalStateException("Teks tagihan tidak terdeteksi pada tangkapan layar. Pastikan gambar memuat informasi tagihan yang jelas.")
        }

        // 2. Parsing Deterministik ke Domain Model
        DeterministicPayLaterParser.parse(
            rawText = rawText,
            imageUriStr = imageUri?.toString()
        )
    }

    /**
     * Memindai beberapa tangkapan layar sekaligus secara berurutan dan lokal.
     */
    suspend fun scanMultipleScreenshots(
        context: Context,
        imageUris: List<Uri>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): com.example.data.model.PayLaterBatchImportResult = withContext(Dispatchers.IO) {
        val allBills = mutableListOf<PayLaterBillScanResult>()
        val failedUris = mutableListOf<String>()

        imageUris.forEachIndexed { index, uri ->
            onProgress?.invoke(index + 1, imageUris.size)
            try {
                val loadedBitmap = loadBitmapFromUri(context, uri)
                if (loadedBitmap == null) {
                    failedUris.add(uri.toString())
                    return@forEachIndexed
                }
                val rawText = if (textRecognizerHook != null) {
                    textRecognizerHook!!.invoke(loadedBitmap)
                } else {
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    val inputImage = InputImage.fromBitmap(loadedBitmap, 0)
                    val visionText = recognizer.process(inputImage).await()
                    visionText.text
                }

                if (rawText.isNotBlank()) {
                    val parsedBills = DeterministicPayLaterParser.parseMultiBills(rawText, uri.toString())
                    allBills.addAll(parsedBills)
                } else {
                    failedUris.add(uri.toString())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gagal memproses screenshot $uri: ${e.message}")
                failedUris.add(uri.toString())
            }
        }

        com.example.data.model.PayLaterBatchImportResult(
            bills = allBills,
            failedImageUris = failedUris,
            totalImages = imageUris.size,
            processedCount = allBills.size
        )
    }

    /**
     * Preset tagihan contoh untuk demonstrasi interaktif / testing offline.
     */
    fun getSamplePayLaterBill(presetKey: String): PayLaterBillScanResult {
        return when (presetKey.lowercase()) {
            "shopee", "spaylater" -> PayLaterBillScanResult(
                providerName = "Shopee PayLater",
                productName = "SPayLater",
                currentBillAmount = 354000.0,
                totalRemainingAmount = 1416000.0,
                installmentCurrent = 2,
                installmentTotal = 6,
                dueDate = "Tgl 5 tiap bulan",
                dueDay = 5,
                billStatus = "Belum Bayar",
                interestRate = 2.95,
                rawNotes = "Cicilan SPayLater periode berjalan",
                confidenceWarnings = listOf("Contoh simulasi / demo preset offline"),
                rawText = "Contoh Tagihan SPayLater: Jatuh tempo 5 tiap bulan, Rp354.000",
                isDemoPreset = true
            )
            "dana", "danacicil" -> PayLaterBillScanResult(
                providerName = "DANA Cicil",
                productName = "Cicilan 4x",
                currentBillAmount = 125000.0,
                totalRemainingAmount = 250000.0,
                installmentCurrent = 3,
                installmentTotal = 4,
                dueDate = "Tgl 15 tiap bulan",
                dueDay = 15,
                billStatus = "Belum Bayar",
                interestRate = 0.0,
                rawNotes = "Tagihan DANA Cicil 4x per 2 minggu",
                confidenceWarnings = listOf("Contoh simulasi / demo preset offline"),
                rawText = "Contoh Tagihan DANA Cicil: Rp125.000 cicilan ke-3",
                isDemoPreset = true
            )
            "gopay", "gopaylater" -> PayLaterBillScanResult(
                providerName = "GoPayLater",
                productName = "Limit Cicilan",
                currentBillAmount = 450000.0,
                totalRemainingAmount = 450000.0,
                installmentCurrent = 1,
                installmentTotal = 1,
                dueDate = "Tgl 1 tiap bulan",
                dueDay = 1,
                billStatus = "Belum Bayar",
                interestRate = 0.0,
                rawNotes = "Tagihan GoPayLater akhir bulan",
                confidenceWarnings = listOf("Contoh simulasi / demo preset offline"),
                rawText = "Contoh Tagihan GoPayLater: Rp450.000 tempo tgl 1",
                isDemoPreset = true
            )
            else -> PayLaterBillScanResult(
                providerName = "Tagihan Lain",
                currentBillAmount = 200000.0,
                totalRemainingAmount = 200000.0,
                dueDate = "Tgl 20 tiap bulan",
                dueDay = 20,
                billStatus = "Belum Bayar",
                interestRate = 0.0,
                isDemoPreset = true
            )
        }
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (original != null) {
                rotateBitmapIfRequired(context, original, uri)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memuat bitmap dari URI $uri", e)
            null
        }
    }

    private fun rotateBitmapIfRequired(context: Context, bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(input)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            input.close()

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
                else -> bitmap
            }
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun rotateImage(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
