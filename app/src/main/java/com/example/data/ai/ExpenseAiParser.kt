package com.example.data.ai

import com.example.data.model.CategoryItem
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
import java.util.regex.Pattern

object ExpenseAiParser {

    suspend fun parseExpenseMessage(
        userMessage: String,
        availableWallets: List<Wallet>,
        availableCategories: List<CategoryItem>
    ): ParsedTransactionResult = withContext(Dispatchers.IO) {
        val apiKey = GeminiClient.getApiKey()

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val geminiResult = callGeminiApi(userMessage, availableWallets, availableCategories, apiKey)
                if (geminiResult != null) {
                    return@withContext geminiResult
                }
            } catch (e: Exception) {
                // Fallback to local offline parser if network fails
            }
        }

        // Offline / Fast NLP Local Parser Fallback
        parseLocally(userMessage, availableWallets, availableCategories)
    }

    private suspend fun callGeminiApi(
        userMessage: String,
        wallets: List<Wallet>,
        categories: List<CategoryItem>,
        apiKey: String
    ): ParsedTransactionResult? {
        val walletNames = wallets.joinToString(", ") { it.name }
        val categoryNames = categories.joinToString(", ") { "${it.name} (${it.type.name})" }

        val prompt = """
            Anda adalah asisten AI keuangan FinSmart Indonesia.
            Tugas Anda adalah mengekstrak data transaksi dari kalimat input pengguna berikut:
            "$userMessage"
            
            Daftar dompet tersedia: [$walletNames]
            Daftar kategori tersedia: [$categoryNames]
            
            Kembalikan HANYA JSON murni (tanpa markdown backtick ```json) dengan format:
            {
              "title": "Judul singkat transaksi (contoh: Beli Nasi Padang)",
              "amount": 35000,
              "type": "EXPENSE" atau "INCOME" atau "TRANSFER",
              "category": "Nama kategori yang paling cocok",
              "categoryIcon": "restaurant / commute / shopping_bag / receipt / payments / etc.",
              "suggestedWalletName": "Nama dompet sumber yang paling cocok",
              "suggestedTargetWalletName": "Nama dompet tujuan (hanya jika type TRANSFER, selain itu null)",
              "note": "Catatan tambahan jika ada",
              "tags": "#tag1 #tag2",
              "aiExplanation": "Kalimat penjelasan ramah 1 kalimat dalam bahasa Indonesia"
            }
        """.trimIndent()

        val request = GeminiGenerateRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
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
        return ParsedTransactionResult(
            title = json.optString("title", userMessage),
            amount = json.optDouble("amount", 0.0),
            type = json.optString("type", "EXPENSE"),
            category = json.optString("category", "Lain-lain"),
            categoryIcon = json.optString("categoryIcon", "category"),
            suggestedWalletName = json.optString("suggestedWalletName", wallets.firstOrNull()?.name ?: "Dompet Utama"),
            suggestedTargetWalletName = if (json.isNull("suggestedTargetWalletName")) null else json.optString("suggestedTargetWalletName"),
            note = json.optString("note", ""),
            tags = json.optString("tags", ""),
            aiExplanation = json.optString("aiExplanation", "Transaksi berhasil diekstrak oleh Gemini AI.")
        )
    }

    private fun parseLocally(
        input: String,
        wallets: List<Wallet>,
        categories: List<CategoryItem>
    ): ParsedTransactionResult {
        val lower = input.lowercase(Locale("id", "ID"))

        // 1. Determine Type
        val isTransfer = lower.contains("transfer") || lower.contains("kirim uang") || lower.contains("topup") || lower.contains("top up")
        val isIncome = !isTransfer && (lower.contains("gaji") || lower.contains("bonus") || lower.contains("dapat") ||
                lower.contains("terima") || lower.contains("masuk") || lower.contains("omset") || lower.contains("penjualan") || lower.contains("cair"))
        val type = when {
            isTransfer -> "TRANSFER"
            isIncome -> "INCOME"
            else -> "EXPENSE"
        }

        // 2. Extract Amount
        var amount = 0.0

        // Regex for "50rb", "50k", "50.000", "50 ribu", "1.5 juta", "2jt", "100000"
        val jutaPattern = Pattern.compile("(\\d+([.,]\\d+)?)\\s*(juta|jt)")
        val jutaMatcher = jutaPattern.matcher(lower)
        if (jutaMatcher.find()) {
            val numStr = jutaMatcher.group(1)?.replace(",", ".") ?: "1"
            amount = (numStr.toDoubleOrNull() ?: 1.0) * 1_000_000.0
        } else {
            val ribuPattern = Pattern.compile("(\\d+([.,]\\d+)?)\\s*(ribu|rb|k)")
            val ribuMatcher = ribuPattern.matcher(lower)
            if (ribuMatcher.find()) {
                val numStr = ribuMatcher.group(1)?.replace(",", ".") ?: "1"
                amount = (numStr.toDoubleOrNull() ?: 1.0) * 1_000.0
            } else {
                val plainNumPattern = Pattern.compile("(?:rp\\.?\\s*)?(\\d{1,3}(?:\\.\\d{3})+|\\d+)")
                val numMatcher = plainNumPattern.matcher(lower)
                if (numMatcher.find()) {
                    val rawNum = numMatcher.group(1)?.replace(".", "") ?: "0"
                    amount = rawNum.toDoubleOrNull() ?: 0.0
                }
            }
        }

        // 3. Match Wallet
        val matchedSourceWallet = wallets.firstOrNull { w ->
            lower.contains(w.name.lowercase(Locale.ROOT)) ||
                    (w.iconName.isNotBlank() && lower.contains(w.iconName.lowercase(Locale.ROOT))) ||
                    (lower.contains("gopay") && w.name.contains("gopay", true)) ||
                    (lower.contains("ovo") && w.name.contains("ovo", true)) ||
                    (lower.contains("dana") && w.name.contains("dana", true)) ||
                    (lower.contains("shopee") && w.name.contains("shopee", true)) ||
                    (lower.contains("bca") && w.name.contains("bca", true)) ||
                    (lower.contains("bri") && w.name.contains("bri", true)) ||
                    (lower.contains("mandiri") && w.name.contains("mandiri", true)) ||
                    (lower.contains("tunai") || lower.contains("cash")) && w.type == com.example.data.model.WalletType.CASH
        } ?: wallets.firstOrNull()

        val matchedTargetWallet = if (isTransfer) {
            wallets.firstOrNull { w ->
                w.id != matchedSourceWallet?.id && (
                        lower.contains("ke ${w.name.lowercase(Locale.ROOT)}") ||
                                (w.iconName.isNotBlank() && lower.contains("ke ${w.iconName.lowercase(Locale.ROOT)}")) ||
                                (lower.contains("ke gopay") && w.name.contains("gopay", true)) ||
                                (lower.contains("ke dana") && w.name.contains("dana", true)) ||
                                (lower.contains("ke ovo") && w.name.contains("ovo", true))
                        )
            } ?: wallets.filter { it.id != matchedSourceWallet?.id }.firstOrNull()
        } else null

        // 4. Match Category
        val matchedCategory = when {
            isTransfer -> "Transfer Saldo"
            lower.contains("makan") || lower.contains("minum") || lower.contains("kopi") || lower.contains("bakso") || lower.contains("nasi") || lower.contains("sate") || lower.contains("resto") -> "Makanan & Minuman"
            lower.contains("bensin") || lower.contains("pertalite") || lower.contains("pertamax") || lower.contains("parkir") || lower.contains("gojek") || lower.contains("grab") || lower.contains("tol") || lower.contains("angkot") -> "Transportasi & Bensin"
            lower.contains("belanja") || lower.contains("beli") || lower.contains("baju") || lower.contains("sepatu") || lower.contains("kaos") || lower.contains("indomaret") || lower.contains("alfamart") -> "Belanja & Hobi"
            lower.contains("listrik") || lower.contains("pln") || lower.contains("wifi") || lower.contains("pulsa") || lower.contains("paket data") || lower.contains("air") || lower.contains("pdam") -> "Tagihan & Utilitas"
            lower.contains("obat") || lower.contains("dokter") || lower.contains("klinik") || lower.contains("apotek") || lower.contains("vitamin") -> "Kesehatan"
            lower.contains("gaji") -> "Gaji Pokok"
            lower.contains("bonus") || lower.contains("thr") -> "Bonus & Tunjangan"
            lower.contains("investasi") || lower.contains("saham") || lower.contains("reksadana") || lower.contains("crypto") -> "Investasi & Dividen"
            lower.contains("omset") || lower.contains("jual") || lower.contains("toko") -> "Penjualan / Omset"
            else -> if (type == "INCOME") "Pemasukan Lain" else "Lain-lain"
        }

        // 5. Category Icon
        val catIcon = when (matchedCategory) {
            "Makanan & Minuman" -> "restaurant"
            "Transportasi & Bensin" -> "commute"
            "Belanja & Hobi" -> "shopping_bag"
            "Tagihan & Utilitas" -> "receipt"
            "Kesehatan" -> "medical_services"
            "Gaji Pokok" -> "payments"
            "Bonus & Tunjangan" -> "card_giftcard"
            "Investasi & Dividen" -> "trending_up"
            "Penjualan / Omset" -> "store"
            "Transfer Saldo" -> "swap_horiz"
            else -> "category"
        }

        // 6. Generate Title
        val title = input.replace(Regex("(?i)(pake|pakai|via|dari|ke|dengan)\\s+\\w+"), "").trim().capitalize(Locale.ROOT)

        val explanation = when (type) {
            "INCOME" -> "AI mencatat pemasukan '$title' senilai ${amount.toLong()} ke ${matchedSourceWallet?.name}."
            "TRANSFER" -> "AI menyiapkan transfer ${amount.toLong()} dari ${matchedSourceWallet?.name} ke ${matchedTargetWallet?.name}."
            else -> "AI mendeteksi pengeluaran '$title' sebesar ${amount.toLong()} via ${matchedSourceWallet?.name}."
        }

        return ParsedTransactionResult(
            title = if (title.isBlank()) matchedCategory else title,
            amount = amount,
            type = type,
            category = matchedCategory,
            categoryIcon = catIcon,
            suggestedWalletName = matchedSourceWallet?.name ?: "Dompet Utama",
            suggestedTargetWalletName = matchedTargetWallet?.name,
            note = "Dicatat otomatis via AI Gemini Input",
            tags = "#" + matchedCategory.split(" ").first().lowercase(),
            aiExplanation = explanation
        )
    }
}
