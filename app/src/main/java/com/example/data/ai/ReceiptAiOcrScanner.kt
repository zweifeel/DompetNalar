package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.data.model.ReceiptItem
import com.example.data.model.ReceiptScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReceiptAiOcrScanner {
    private const val TAG = "ReceiptAiOcrScanner"

    /**
     * Fallback realistic presets matching user receipt samples:
     * Preset 1: Indomaret Mojopuro Gede
     * Preset 2: Momoyo Dukun Gresik
     */
    fun generateRealisticReceiptFallback(imageUriStr: String? = null, presetType: Int = 1): ReceiptScanResult {
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        return when (presetType) {
            1 -> ReceiptScanResult(
                merchantName = "Indomaret Mojopuro Gede",
                date = today,
                category = "Belanja & Kebutuhan",
                categoryIcon = "shopping_bag",
                subtotal = 10200.0,
                tax = 1011.0,
                serviceCharge = 0.0,
                discount = 500.0,
                totalAmount = 9700.0,
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
                items = emptyList(),
                imageUri = imageUriStr,
                rawText = "Silakan masukkan detail belanja atau gunakan contoh."
            )
        }
    }

    suspend fun scanReceiptImage(
        context: Context,
        imageUri: Uri?,
        bitmap: Bitmap? = null
    ): ReceiptScanResult = withContext(Dispatchers.IO) {
        val loadedBitmap = bitmap ?: imageUri?.let { loadBitmapFromUri(context, it) }
            ?: throw IllegalArgumentException("Tidak dapat memuat gambar nota dari kamera atau galeri.")

        val apiKey = GeminiClient.getApiKey().trim()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "API Key Gemini kosong atau default. Menggunakan preset realistis.")
            throw IllegalStateException("API Key Gemini belum disetel di Secrets Panel. Tambahkan GEMINI_API_KEY atau gunakan contoh nota.")
        }

        try {
            val ocrResult = callGeminiVisionOcr(loadedBitmap, apiKey, imageUri?.toString())
            ocrResult ?: throw IllegalStateException("Gemini AI tidak mengembalikan data nota yang valid.")
        } catch (e: HttpException) {
            Log.e(TAG, "HTTP Exception ${e.code()} saat memanggil Gemini Vision OCR", e)
            if (e.code() == 503) {
                throw IllegalStateException("Server AI sedang sibuk (HTTP 503). Silakan coba beberapa saat lagi atau gunakan fitur contoh.")
            } else if (e.code() == 429) {
                throw IllegalStateException("Batas kuota API tercapai (HTTP 429). Mohon tunggu beberapa saat.")
            } else {
                throw IllegalStateException("Kendala server AI (HTTP ${e.code()}). Silakan coba lagi.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saat memanggil Gemini Vision OCR", e)
            val msg = e.message ?: ""
            if (msg.contains("503") || msg.contains("Server Busy")) {
                throw IllegalStateException("Server AI sedang sibuk (HTTP 503). Silakan coba beberapa saat lagi atau gunakan fitur contoh.")
            }
            throw e
        }
    }

    private suspend fun callGeminiVisionOcr(
        bitmap: Bitmap,
        apiKey: String,
        imageUriStr: String?
    ): ReceiptScanResult? {
        // Step 1: Compress & resize image to max 1024px with JPEG 75% quality & recycle intermediate bitmap
        val base64Image = compressAndEncodeBitmap(bitmap)

        // Step 2: System prompt tailored for Indonesian receipts
        val prompt = """
            Anda adalah AI OCR FinSmart Indonesia. Tugas Anda: baca gambar struk/nota belanja fisik ini secara teliti dan ekstrak seluruh datanya ke format JSON murni.
            
            FORMAT JSON WAJIB:
            {
              "nama_toko": "Nama Toko / Resto / Merchant",
              "tanggal": "DD-MM-YYYY",
              "subtotal": 0.0,
              "pajak_ppn": 0.0,
              "total_akhir": 0.0,
              "items": [
                {
                  "nama_barang": "Nama Produk / Makanan",
                  "jumlah": 1,
                  "harga_satuan": 0.0,
                  "total_harga": 0.0
                }
              ]
            }

            Petunjuk Ekstraksi Struk Indonesia:
            1. nama_toko: Cari nama brand, cabang, atau header toko di bagian atas struk (contoh: 'Indomaret Mojopuro Gede', 'Momoyo Dukun Gresik').
            2. tanggal: Formatkan selalu menjadi DD-MM-YYYY yang DINAMIS mengikuti tanggal transaksi yang tercetak pada struk fisik.
               - Contoh format struk lokal: '28.09.26' -> '28-09-2026', '2026-09-27' -> '27-09-2026', '14/03/2026' -> '14-03-2026'.
               - Jika tahun tercetak 2 digit (misal: 28.09.26), konversi ke 4 digit (2026).
            3. items:
               - nama_barang: Nama barang/makanan yang dibeli.
               - jumlah: Angka kuantitas (integer, minimal 1).
               - harga_satuan: Harga satuan per item (Double/Number murni tanpa 'Rp', titik, atau koma).
               - total_harga: Total harga item (jumlah * harga_satuan).
            4. subtotal: Total harga barang sebelum pajak dan diskon.
            5. pajak_ppn: Nilai PPN / PB1 / Tax jika tertera pada struk (contoh: PPN= 1.011 -> 1011.0). Isi 0.0 jika tidak ada.
            6. total_akhir: Nominal akhir yang wajib dibayar (TOTAL BELANJA / Total Harga / Nominal Pembayaran).
            7. Semua nilai nominal harus berupa angka Double/Number murni tanpa 'Rp', titik, atau koma.
            8. KEMBALIKAN HANYA TEKS JSON VALID TANPA MARKDOWN TAG (DILARANG menggunakan ```json atau ```).
        """.trimIndent()

        val request = GeminiGenerateRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt),
                        GeminiPart(
                            inlineData = GeminiInlineData(
                                mimeType = "image/jpeg",
                                data = base64Image
                            )
                        )
                    )
                )
            ),
            generationConfig = GeminiGenerationConfig(temperature = 0.1f)
        )

        // Step 3: API call focusing on stable gemini-1.5-flash with Exponential Backoff retry
        val response = GeminiClient.executeGenerateWithRetry(apiKey, request, modelName = "gemini-1.5-flash", maxRetries = 3)
        val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Gemini AI tidak memberikan respon teks.")

        // Step 4: Robust JSON extraction & safe parsing
        val cleanText = responseText.replace("```json", "").replace("```", "").trim()
        val start = cleanText.indexOf('{')
        val end = cleanText.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) {
            throw IllegalStateException("Respons AI tidak memuat format JSON yang valid: $cleanText")
        }

        val json = JSONObject(cleanText.substring(start, end + 1))

        fun parseDoubleSafe(obj: JSONObject, vararg keys: String, defaultVal: Double = 0.0): Double {
            for (key in keys) {
                val raw = obj.opt(key) ?: continue
                if (raw is Number) return raw.toDouble()
                val str = raw.toString().replace("Rp", "", ignoreCase = true).trim()
                val clean = str.replace(".", "").replace(",", ".").filter { it.isDigit() || it == '.' }
                val parsed = clean.toDoubleOrNull()
                if (parsed != null) return parsed
            }
            return defaultVal
        }

        val itemsArray = json.optJSONArray("items") ?: JSONArray()
        val itemsList = mutableListOf<ReceiptItem>()

        for (i in 0 until itemsArray.length()) {
            val itemObj = itemsArray.optJSONObject(i) ?: continue
            val name = (itemObj.optString("nama_barang").ifBlank { itemObj.optString("name") }).ifBlank { "Item ${i + 1}" }
            val qty = itemObj.optInt("jumlah", itemObj.optInt("qty", 1)).coerceAtLeast(1)
            val price = parseDoubleSafe(itemObj, "harga_satuan", "price", defaultVal = 0.0)
            val total = parseDoubleSafe(itemObj, "total_harga", "total", defaultVal = price * qty)
            itemsList.add(
                ReceiptItem(
                    name = name,
                    qty = qty,
                    price = price,
                    total = if (total > 0.0) total else (price * qty)
                )
            )
        }

        val merchant = (json.optString("nama_toko").ifBlank { json.optString("merchantName") }).ifBlank { "Struk Belanja" }
        val rawDate = (json.optString("tanggal").ifBlank { json.optString("date") }).trim()
        val date = normalizeDate(rawDate)

        val subtotal = parseDoubleSafe(json, "subtotal", defaultVal = itemsList.sumOf { it.total })
        val tax = parseDoubleSafe(json, "pajak_ppn", "tax", defaultVal = 0.0)
        val rawTotal = parseDoubleSafe(json, "total_akhir", "totalAmount", defaultVal = 0.0)
        val finalTotal = if (rawTotal > 0.0) rawTotal else (subtotal + tax).coerceAtLeast(0.0)

        return ReceiptScanResult(
            merchantName = merchant,
            date = date,
            category = if (merchant.contains("Indomaret", ignoreCase = true) || merchant.contains("Alfa", ignoreCase = true)) "Belanja & Kebutuhan" else "Makanan & Minuman",
            categoryIcon = if (merchant.contains("Indomaret", ignoreCase = true) || merchant.contains("Alfa", ignoreCase = true)) "shopping_bag" else "restaurant",
            subtotal = subtotal,
            tax = tax,
            totalAmount = finalTotal,
            items = itemsList,
            imageUri = imageUriStr,
            rawText = cleanText
        )
    }

    private fun normalizeDate(rawDate: String): String {
        if (rawDate.isBlank()) {
            return SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        }
        val cleaned = rawDate.replace('/', '-').replace('.', '-').trim()
        val parts = cleaned.split('-')
        if (parts.size == 3) {
            // YYYY-MM-DD -> DD-MM-YYYY
            if (parts[0].length == 4) {
                val y = parts[0]
                val m = parts[1].padStart(2, '0')
                val d = parts[2].padStart(2, '0')
                return "$d-$m-$y"
            }
            // DD-MM-YY -> DD-MM-YYYY
            val d = parts[0].padStart(2, '0')
            val m = parts[1].padStart(2, '0')
            var y = parts[2]
            if (y.length == 2) {
                y = "20$y"
            }
            return "$d-$m-$y"
        }
        return rawDate
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            var inSampleSize = 1
            while ((options.outWidth / inSampleSize) > 1024 || (options.outHeight / inSampleSize) > 1024) {
                inSampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            var decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    val degrees = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                    if (degrees != 0f) {
                        val matrix = Matrix().apply { postRotate(degrees) }
                        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                        if (rotated != decoded) {
                            decoded.recycle()
                        }
                        decoded = rotated
                    }
                }
            } catch (_: Exception) {}
            decoded
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int = 1024): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        if (originalWidth <= maxDimension && originalHeight <= maxDimension) return bitmap
        val ratio = originalWidth.toFloat() / originalHeight.toFloat()
        val width = if (originalWidth > originalHeight) maxDimension else (maxDimension * ratio).toInt()
        val height = if (originalWidth > originalHeight) (maxDimension / ratio).toInt() else maxDimension
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun compressAndEncodeBitmap(bitmap: Bitmap): String {
        val scaled = scaleBitmapDown(bitmap, 1024)
        val stream = ByteArrayOutputStream()
        try {
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, stream)
            val byteArray = stream.toByteArray()
            return Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } finally {
            stream.close()
            // Clean up intermediate bitmap to prevent OutOfMemoryError
            if (scaled != bitmap && !scaled.isRecycled) {
                scaled.recycle()
            }
        }
    }
}
