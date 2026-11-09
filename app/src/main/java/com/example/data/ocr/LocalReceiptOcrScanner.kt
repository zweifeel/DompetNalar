package com.example.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.example.data.model.ReceiptItem
import com.example.data.model.ReceiptScanResult
import com.example.data.model.TaxTreatment
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pemindai OCR Nota On-Device FinSmart (Local-First ML Kit Text Recognition).
 *
 * 100% Berjalan di perangkat lokal tanpa koneksi internet dan tanpa Gemini API / AI Cloud:
 * 1. Menjalankan on-device Google ML Kit Text Recognition via Play Services.
 * 2. Mengolah teks yang terbaca dengan [DeterministicReceiptParser].
 * 3. Tidak pernah mengunggah data atau gambar nota pengguna ke internet.
 */
object LocalReceiptOcrScanner {

    private const val TAG = "LocalReceiptOcrScanner"

    /**
     * Test hook untuk injeksi hasil transkripsi teks pada unit test / Robolectric
     * tanpa memerlukan Google Play Services runtime.
     */
    var textRecognizerHook: ((Bitmap) -> String)? = null

    suspend fun scanReceiptImage(
        context: Context,
        imageUri: Uri?,
        bitmap: Bitmap? = null
    ): ReceiptScanResult = withContext(Dispatchers.IO) {
        val loadedBitmap = bitmap ?: imageUri?.let { loadBitmapFromUri(context, it) }
            ?: throw IllegalArgumentException("Tidak dapat memuat gambar nota dari kamera atau galeri.")

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
                throw IllegalStateException("Gagal memproses gambar nota secara lokal: ${e.localizedMessage ?: "Format gambar tidak didukung"}")
            }
        }

        if (rawText.isBlank() || rawText.contains("TIDAK_ADA_TEKS_TERBACA", ignoreCase = true)) {
            throw IllegalStateException("Teks nota tidak terdeteksi atau tidak terbaca pada foto. Pastikan foto cukup terang dan fokus pada nota.")
        }

        // 2. Parsing Deterministik ke Domain Model
        DeterministicReceiptParser.parse(
            rawText = rawText,
            imageUriStr = imageUri?.toString()
        )
    }

    /**
     * Fallback preset realistis untuk mode simulasi / demo.
     */
    fun generateRealisticReceiptFallback(imageUriStr: String? = null, presetType: Int = 1): ReceiptScanResult {
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        return when (presetType) {
            1 -> ReceiptScanResult(
                merchantName = "Indomaret Mojopuro Gede",
                date = today,
                category = "Belanja & Hobi",
                categoryIcon = "shopping_bag",
                subtotal = 10200.0,
                tax = 1011.0,
                serviceCharge = 0.0,
                discount = 500.0,
                totalAmount = 9700.0,
                taxTreatment = TaxTreatment.INCLUSIVE,
                items = listOf(
                    ReceiptItem(name = "GAGA 100 JALAPENO 75", qty = 1, price = 3700.0, total = 3700.0),
                    ReceiptItem(name = "INDOMIE TORI KARA 89", qty = 1, price = 6500.0, total = 6500.0)
                ),
                imageUri = imageUriStr,
                rawText = "Contoh Struk Indomaret Mojopuro Gede"
            )
            2 -> ReceiptScanResult(
                merchantName = "Momoyo Dukun Gresik",
                date = today,
                category = "Makanan & Minuman",
                categoryIcon = "restaurant",
                subtotal = 36000.0,
                tax = 0.0,
                serviceCharge = 0.0,
                discount = 0.0,
                totalAmount = 36000.0,
                taxTreatment = TaxTreatment.EXCLUSIVE,
                items = listOf(
                    ReceiptItem(name = "Matcha - Fiesta Sundae", qty = 1, price = 16000.0, total = 16000.0),
                    ReceiptItem(name = "Roasted Oolong Milk Tea", qty = 1, price = 20000.0, total = 20000.0)
                ),
                imageUri = imageUriStr,
                rawText = "Contoh Struk Momoyo Dukun Gresik"
            )
            else -> ReceiptScanResult(
                merchantName = "Struk Belanja",
                date = today,
                category = "Makanan & Minuman",
                categoryIcon = "restaurant",
                subtotal = 0.0,
                tax = 0.0,
                serviceCharge = 0.0,
                discount = 0.0,
                totalAmount = 0.0,
                taxTreatment = TaxTreatment.UNKNOWN,
                items = emptyList(),
                imageUri = imageUriStr,
                rawText = "Preset Kosong"
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
