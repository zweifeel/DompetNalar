package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.media.ExifInterface
import com.example.data.model.PayLaterBillScanResult
import com.example.util.ReceiptCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Calendar

/**
 * AI OCR Scanner specialized for Indonesian PayLater and loan/pinjaman online
 * bill screenshots (e.g., Shopee PayLater, DANA Cicil, GoPayLater, Akulaku, AdaKami).
 */
object PayLaterAiOcrScanner {

    private const val TAG = "PayLaterAiOcrScanner"

    /**
     * Provides realistic sample presets for demonstration, testing, or offline fallback.
     */
    fun getSamplePayLaterBill(type: String = "shopee", imageUriStr: String? = null): PayLaterBillScanResult {
        return when (type.lowercase()) {
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
                imageUri = imageUriStr,
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
                imageUri = imageUriStr,
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
                imageUri = imageUriStr,
                isDemoPreset = true
            )
            else -> PayLaterBillScanResult(
                providerName = "Pinjaman Online",
                productName = null,
                currentBillAmount = 500000.0,
                totalRemainingAmount = 1500000.0,
                installmentCurrent = 1,
                installmentTotal = 3,
                dueDate = "Tgl 20 tiap bulan",
                dueDay = 20,
                billStatus = "Belum Bayar",
                interestRate = 0.0,
                rawNotes = "Contoh tagihan pinjaman",
                confidenceWarnings = listOf("Data contoh demo preset offline. Harap periksa dan sesuaikan sebelum menyimpan."),
                rawText = "Contoh Tagihan Pinjol",
                imageUri = imageUriStr,
                isDemoPreset = true
            )
        }
    }

    /**
     * Scans a screenshot URI with Gemini Vision AI.
     */
    suspend fun scanPayLaterScreenshot(
        context: Context,
        imageUri: Uri?,
        bitmap: Bitmap? = null
    ): PayLaterBillScanResult = withContext(Dispatchers.IO) {
        val loadedBitmap = bitmap ?: imageUri?.let { loadBitmapFromUri(context, it) }
            ?: throw IllegalArgumentException("Tidak dapat memuat gambar screenshot tagihan.")

        val apiKey = GeminiClient.getApiKey().trim()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "API Key Gemini belum disetel. Melemparkan pengecualian aman.")
            throw IllegalStateException("API Key Gemini belum disetel di Secrets Panel. Tambahkan GEMINI_API_KEY untuk memproses screenshot secara otomatis.")
        }

        try {
            val scanResult = callGeminiVisionScan(loadedBitmap, apiKey, imageUri?.toString())
            scanResult ?: throw IllegalStateException("Gemini AI tidak mengembalikan data tagihan yang valid.")
        } catch (e: HttpException) {
            val code = e.code()
            Log.e(TAG, "HTTP Exception $code saat memanggil Gemini Vision untuk tagihan PayLater", e)
            when (code) {
                400 -> throw IllegalStateException("Format screenshot atau payload tagihan tidak valid (HTTP 400).")
                401, 403 -> throw IllegalStateException("Autentikasi ditolak (HTTP $code). Periksa API Key di Secrets.")
                404 -> throw IllegalStateException("Model AI tidak tersedia (HTTP 404). Periksa konfigurasi model.")
                429 -> throw IllegalStateException("Batas kuota API tercapai (HTTP 429). Mohon coba beberapa saat lagi.")
                500, 502, 503, 504 -> throw IllegalStateException("Server AI sedang mengalami kendala sementara (HTTP $code). Silakan coba lagi nanti.")
                else -> throw IllegalStateException("Kendala layanan AI (HTTP $code). Silakan coba lagi.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saat memproses screenshot PayLater", e)
            val msg = e.message ?: ""
            if (msg.contains("503") || msg.contains("Server Busy")) {
                throw IllegalStateException("Server AI sedang sibuk (HTTP 503). Silakan coba lagi nanti.")
            }
            throw e
        }
    }

    private suspend fun callGeminiVisionScan(
        bitmap: Bitmap,
        apiKey: String,
        imageUriStr: String?
    ): PayLaterBillScanResult? {
        val base64Image = compressAndEncodeBitmap(bitmap)

        val prompt = """
            Anda adalah AI FinSmart Indonesia spesialis ekstraksi tagihan pinjaman online dan PayLater.
            Tugas Anda: teliti gambar screenshot aplikasi pinjaman/paylater ini (misal: Shopee PayLater, DANA Cicil, GoPayLater, GoPay Pinjam, Akulaku, AdaKami, Kredivo, Indodana, dsb) dan ekstrak seluruh informasinya ke format JSON murni.

            FORMAT JSON WAJIB:
            {
              "nama_penyedia": "Nama Penyedia / Layanan (misal: Shopee PayLater, DANA Cicil, GoPayLater, Akulaku, AdaKami)",
              "nama_produk": "Nama produk spesifik (misal: SPayLater, Pinjaman Tunai, Cicilan 6 Bulan, null jika tidak ada)",
              "tagihan_bulan_ini": 0.0,
              "total_sisa_utang": 0.0,
              "cicilan_ke": null,
              "total_cicilan": null,
              "tanggal_jatuh_tempo": "Teks tanggal jatuh tempo (misal: '5 Nov 2026' atau '15 tiap bulan' atau '2026-10-25')",
              "status_tagihan": "Status teks jika tertera (misal: 'Belum Bayar', 'Jatuh Tempo', 'Menunggu Pembayaran', null jika tidak ada)",
              "estimasi_bunga_persen": 0.0,
              "catatan_tambahan": "Ringkasan tagihan / nomor referensi ringkas jika ada"
            }

            ATURAN PENTING:
            1. Pembedaan Jumlah:
               - 'tagihan_bulan_ini': Nominal yang HARUS DIBAYAR SEKARANG atau tagihan periode berjalan saat ini.
               - 'total_sisa_utang': Total keseluruhan sisa pinjaman/utang atau total sisa limit terpakai jika tertera di layar.
               - JANGAN menyamakan keduanya jika screenshot membedakannya. Jika hanya ada satu nominal tagihan, isi pada 'tagihan_bulan_ini'.
            2. Cicilan:
               - Jika tertera 'Cicilan 3 dari 6' atau '3/6', isi cicilan_ke: 3, total_cicilan: 6.
               - Jika tidak ada pembagian cicilan bertahap, isi null.
            3. Tanggal Jatuh Tempo:
               - Cari kata 'Jatuh Tempo', 'Bayar sebelum', 'Due date', atau tanggal pembayaran.
               - JANGAN mengarang tanggal jika tidak tertera di layar.
            4. Status:
               - JANGAN mengubah status menjadi 'Lunas' hanya karena ada instruksi pembayaran atau tombol bayar.
            5. Akurasi & Keamanan:
               - JANGAN mengarang data, angka, atau tanggal yang tidak terbaca.
               - Nilai angka harus berupa angka Double murni tanpa 'Rp', titik, atau koma.
               - HINDARI menuliskan nomor akun bank atau nomor KTP lengkap demi privasi.
            6. KEMBALIKAN HANYA TEKS JSON MURNI TANPA MARKDOWN (DILARANG menggunakan ```json atau ```).
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

        val response = GeminiClient.executeGenerateWithRetry(
            apiKey = apiKey,
            request = request,
            modelName = "gemini-3.5-flash-lite",
            maxRetries = 3
        )

        val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Gemini AI tidak memberikan respon teks.")

        val cleanText = responseText.replace("```json", "").replace("```", "").trim()
        val start = cleanText.indexOf('{')
        val end = cleanText.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) {
            throw IllegalStateException("Respons AI tidak memuat format JSON yang valid: $cleanText")
        }

        val json = JSONObject(cleanText.substring(start, end + 1))
        return parseScanResultFromJson(json, cleanText, imageUriStr)
    }

    /**
     * Parses the JSON payload safely into a [PayLaterBillScanResult].
     */
    fun parseScanResultFromJson(
        json: JSONObject,
        rawText: String? = null,
        imageUriStr: String? = null
    ): PayLaterBillScanResult {
        val providerName = json.optString("nama_penyedia", "").trim()
        val productName = json.optString("nama_produk", "").trim().ifBlank { null }

        val currentBillAmount = ReceiptCalculator.parseAmountSafe(
            json.opt("tagihan_bulan_ini") ?: json.opt("nominal_tagihan") ?: json.opt("jumlah_tagihan")
        )

        val totalRemainingAmount = ReceiptCalculator.parseAmountSafe(
            json.opt("total_sisa_utang") ?: json.opt("total_pinjaman") ?: json.opt("sisa_pokok")
        )

        val installmentCurrent = if (json.has("cicilan_ke") && !json.isNull("cicilan_ke")) {
            json.optInt("cicilan_ke", -1).takeIf { it > 0 }
        } else null

        val installmentTotal = if (json.has("total_cicilan") && !json.isNull("total_cicilan")) {
            json.optInt("total_cicilan", -1).takeIf { it > 0 }
        } else null

        val rawDueDate = json.optString("tanggal_jatuh_tempo", "").trim()
        val (normalizedDueDate, dueDay) = parseDueDateString(rawDueDate)

        val billStatus = json.optString("status_tagihan", "").trim().ifBlank { null }
        val interestRate = if (json.has("estimasi_bunga_persen")) {
            ReceiptCalculator.parseAmountSafe(json.opt("estimasi_bunga_persen"))
        } else null

        val rawNotes = json.optString("catatan_tambahan", "").trim().ifBlank { null }

        // Compute confidence warnings
        val warnings = mutableListOf<String>()
        if (providerName.isBlank()) {
            warnings.add("Nama penyedia pinjaman tidak terdeteksi jelas. Harap masukkan nama penyedia.")
        }
        if (currentBillAmount <= 0.0 && totalRemainingAmount <= 0.0) {
            warnings.add("Nominal tagihan belum terbaca. Harap periksa dan isi nominal secara manual.")
        }
        if (normalizedDueDate.isBlank()) {
            warnings.add("Tanggal jatuh tempo tidak terbaca. Harap tentukan tanggal jatuh tempo.")
        }

        return PayLaterBillScanResult(
            providerName = providerName.ifBlank { "Tagihan PayLater" },
            productName = productName,
            currentBillAmount = currentBillAmount,
            totalRemainingAmount = totalRemainingAmount,
            installmentCurrent = installmentCurrent,
            installmentTotal = installmentTotal,
            dueDate = normalizedDueDate.ifBlank { "15 tiap bulan" },
            dueDay = dueDay,
            billStatus = billStatus,
            interestRate = interestRate,
            rawNotes = rawNotes,
            confidenceWarnings = warnings,
            rawText = rawText,
            imageUri = imageUriStr
        )
    }

    /**
     * Normalizes due date string into an Indonesian format compatible with [NotificationDataHelper].
     * Returns Pair(normalizedDueDateString, dayOfMonthInt)
     */
    fun parseDueDateString(raw: String?): Pair<String, Int?> {
        if (raw.isNullOrBlank()) return Pair("", null)

        val trimmed = raw.trim()

        // Match explicit day like "Tgl 5 tiap bulan" or "Tgl 5"
        val dayMatch = Regex("(?i)(?:tgl|tanggal)\\s*([0-9]{1,2})").find(trimmed)
        if (dayMatch != null) {
            val day = dayMatch.groupValues[1].toIntOrNull()
            if (day != null && day in 1..31) {
                return Pair("Tgl $day tiap bulan", day)
            }
        }

        // Match standalone day number in strings like "5 tiap bulan"
        val tiapBulanMatch = Regex("([0-9]{1,2})\\s*(?:tiap\\s*bulan)", RegexOption.IGNORE_CASE).find(trimmed)
        if (tiapBulanMatch != null) {
            val day = tiapBulanMatch.groupValues[1].toIntOrNull()
            if (day != null && day in 1..31) {
                return Pair("Tgl $day tiap bulan", day)
            }
        }

        // Match date formats like "05-11-2026", "05/11/2026", "2026-11-05"
        val isoMatch = Regex("([0-9]{4})[-/]([0-9]{1,2})[-/]([0-9]{1,2})").find(trimmed)
        if (isoMatch != null) {
            val day = isoMatch.groupValues[3].toIntOrNull()
            if (day != null && day in 1..31) {
                return Pair("Tgl $day tiap bulan", day)
            }
        }

        val dmyMatch = Regex("([0-9]{1,2})[-/]([0-9]{1,2})[-/]([0-9]{2,4})").find(trimmed)
        if (dmyMatch != null) {
            val day = dmyMatch.groupValues[1].toIntOrNull()
            if (day != null && day in 1..31) {
                return Pair("Tgl $day tiap bulan", day)
            }
        }

        // Match Indonesian month names like "5 Nov", "15 Oktober 2026", "20 November"
        val monthNamesRegex = Regex(
            "([0-9]{1,2})\\s*(?:jan(?:uari)?|feb(?:ruari)?|mar(?:et)?|apr(?:il)?|mei|jun(?:i)?|jul(?:i)?|agu(?:stus)?|sep(?:tember)?|okt(?:ober)?|nov(?:ember)?|des(?:ember)?)",
            RegexOption.IGNORE_CASE
        )
        val monthMatch = monthNamesRegex.find(trimmed)
        if (monthMatch != null) {
            val day = monthMatch.groupValues[1].toIntOrNull()
            if (day != null && day in 1..31) {
                return Pair("Tgl $day tiap bulan", day)
            }
        }

        // Extract first valid standalone 1-31 number
        val singleDigitMatch = Regex("\\b([1-9]|[12][0-9]|3[01])\\b").find(trimmed)
        if (singleDigitMatch != null) {
            val day = singleDigitMatch.value.toIntOrNull()
            if (day != null && day in 1..31) {
                return Pair("Tgl $day tiap bulan", day)
            }
        }

        return Pair(trimmed, null)
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOptions)
            }

            var inSampleSize = 1
            val maxDim = 1200
            while (boundsOptions.outWidth / inSampleSize > maxDim || boundsOptions.outHeight / inSampleSize > maxDim) {
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
            if (scaled != bitmap && !scaled.isRecycled) {
                scaled.recycle()
            }
        }
    }
}
