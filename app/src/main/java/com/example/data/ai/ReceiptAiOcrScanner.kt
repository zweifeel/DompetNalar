package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.data.model.ReceiptItem
import com.example.data.model.ReceiptScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object ReceiptAiOcrScanner {

    fun generateRealisticReceiptFallback(imageUriStr: String? = null, presetType: Int = 0): ReceiptScanResult {
        val today = SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID")).format(Date())
        return ReceiptScanResult(
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
            rawText = "Silakan masukkan detail belanja atau gunakan AI Vision API."
        )
    }

    suspend fun scanReceiptImage(
        context: Context,
        imageUri: Uri?,
        bitmap: Bitmap? = null
    ): ReceiptScanResult = withContext(Dispatchers.IO) {
        val loadedBitmap = bitmap ?: imageUri?.let { loadBitmapFromUri(context, it) }
        val apiKey = GeminiClient.getApiKey()

        if (loadedBitmap != null && apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val ocrResult = callGeminiVisionOcr(loadedBitmap, apiKey, imageUri?.toString())
                if (ocrResult != null) {
                    return@withContext ocrResult
                }
            } catch (e: Exception) {
                // Return clean empty result on failure without fallback mock data
            }
        }

        generateRealisticReceiptFallback(imageUri?.toString(), 0)
    }

    private suspend fun callGeminiVisionOcr(
        bitmap: Bitmap,
        apiKey: String,
        imageUriStr: String?
    ): ReceiptScanResult? {
        val scaledBitmap = scaleBitmapDown(bitmap, 1280)
        val base64Image = bitmapToBase64(scaledBitmap)

        val prompt = """
            Anda adalah AI OCR Ahli Pembaca Struk/Nota FinSmart Indonesia.
            Tugas Anda adalah membaca gambar struk/nota belanja ini dan mengekstrak semua informasinya secara akurat dan detail.
            
            Ekstrak data berikut:
            1. Nama Toko/Restoran/Merchant (merchantName)
            2. Tanggal transaksi (format DD/MM/YYYY atau YYYY-MM-DD)
            3. Kategori pengeluaran yang paling cocok (misal: "Makanan & Minuman", "Belanja & Hobi", "Tagihan & Utilitas", "Kesehatan", "Transportasi & Bensin")
            4. Icon kategori (restaurant / shopping_bag / receipt / local_cafe / local_gas_station / etc.)
            5. Subtotal barang/makanan sebelum pajak & service
            6. Pajak (PPN / PB1 / Tax) dalam rupiah (0 jika tidak ada)
            7. Service charge / Biaya Layanan dalam rupiah (0 jika tidak ada)
            8. Diskon / Potongan harga dalam rupiah (0 jika tidak ada)
            9. Total bayar akhir (totalAmount)
            10. Daftar rincian barang/item (items): nama item, kuantitas (qty), harga satuan (price), dan total harga item (total).

            Kembalikan HANYA format JSON murni (tanpa tag markdown ```json) dengan format:
            {
              "merchantName": "Nama Toko / Resto",
              "date": "14/03/2026",
              "category": "Makanan & Minuman",
              "categoryIcon": "restaurant",
              "subtotal": 125000,
              "tax": 12500,
              "serviceCharge": 6250,
              "discount": 0,
              "totalAmount": 143750,
              "items": [
                {
                  "name": "Contoh Item",
                  "qty": 1,
                  "price": 50000,
                  "total": 50000
                }
              ]
            }
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
            generationConfig = GeminiGenerationConfig(
                temperature = 0.1f
            )
        )

        val response = GeminiClient.apiService.generateContent(apiKey, request)
        val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: return null

        val cleanJson = responseText
            .replace("```json", "")
            .replace("```", "")
            .trim()

        val json = JSONObject(cleanJson)
        val itemsArray = json.optJSONArray("items") ?: JSONArray()
        val itemsList = mutableListOf<ReceiptItem>()

        for (i in 0 until itemsArray.length()) {
            val itemObj = itemsArray.getJSONObject(i)
            val name = itemObj.optString("name", "Item ${i + 1}")
            val qty = itemObj.optInt("qty", 1)
            val price = itemObj.optDouble("price", 0.0)
            val total = itemObj.optDouble("total", price * qty)
            itemsList.add(
                ReceiptItem(
                    name = name,
                    qty = if (qty <= 0) 1 else qty,
                    price = price,
                    total = total
                )
            )
        }

        val merchant = json.optString("merchantName", "Struk Belanja")
        val date = json.optString("date", SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()))
        val subtotal = json.optDouble("subtotal", itemsList.sumOf { it.total })
        val tax = json.optDouble("tax", 0.0)
        val serviceCharge = json.optDouble("serviceCharge", 0.0)
        val discount = json.optDouble("discount", 0.0)
        val total = json.optDouble("totalAmount", (subtotal + tax + serviceCharge - discount).coerceAtLeast(0.0))

        return ReceiptScanResult(
            merchantName = merchant,
            date = date,
            category = json.optString("category", "Makanan & Minuman"),
            categoryIcon = json.optString("categoryIcon", "restaurant"),
            subtotal = subtotal,
            tax = tax,
            serviceCharge = serviceCharge,
            discount = discount,
            totalAmount = total,
            items = itemsList,
            imageUri = imageUriStr,
            rawText = cleanJson
        )
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        if (originalWidth <= maxDimension && originalHeight <= maxDimension) {
            return bitmap
        }
        val ratio = originalWidth.toFloat() / originalHeight.toFloat()
        val width: Int
        val height: Int
        if (originalWidth > originalHeight) {
            width = maxDimension
            height = (maxDimension / ratio).toInt()
        } else {
            height = maxDimension
            width = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
}
