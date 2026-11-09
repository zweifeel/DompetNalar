package com.example.data.chatbot.parser

import java.util.Locale
import java.util.regex.Pattern

/**
 * Normalizer deterministik untuk ekstraksi nominal mata uang (Rupiah & Internasional).
 *
 * Mendukung format:
 * - Standar: 5000, 50000, 250000
 * - Pemisah ribuan titik/koma: 5.000, 50.000, 1.500.000, 1,500,000, $50.00, €45.50
 * - Satuan singkatan: 5k, 50k, 50 k, 50rb, 50 rb, 50 ribu, 50 thousand
 * - Satuan jutaan: 1 juta, 1.5 juta, 1,5 juta, 1jt, 2.5jt, 1 million, 2.5m
 * - Simbol awalan/akhiran: Rp50.000, Rp 50.000,-, $50, USD 50, €50, £50, ¥5000, 50 dollars
 *
 * Menolak secara tegas:
 * - Nominal <= 0 (negatif / nol)
 * - Nilai NaN atau tak terhingga (infinite)
 * - Nominal melampaui batas kewajaran transaksi (> 100 Miliar)
 */
object AmountNormalizer {

    const val MAX_REASONABLE_AMOUNT = 100_000_000_000.0

    data class ExtractedAmount(
        val value: Double,
        val rawMatchedText: String,
        val startIndex: Int,
        val endIndex: Int,
        val detectedCurrencyCode: String? = null
    )

    /**
     * Mengekstrak nominal dari kalimat input pengguna.
     * Mengembalikan [ExtractedAmount] atau null jika tidak ada nominal valid yang terdeteksi.
     */
    fun extractAmount(input: String): ExtractedAmount? {
        val cleanInput = input.trim()
        if (cleanInput.isBlank()) return null
        val lower = cleanInput.lowercase(Locale.ROOT)

        // 0. Tolak nilai negatif secara aman (misal "-50000" atau "minus 50rb")
        val negativePattern = Pattern.compile("(?i)(?:^|\\s)(?:-|minus\\s+)(?:rp\\.?\\s*|[$€£¥₹]\\s*|usd\\s*)?\\d+")
        if (negativePattern.matcher(lower).find()) {
            return null
        }

        // Deteksi kode mata uang eksplisit jika disebutkan
        val detectedCurrCode = when {
            lower.contains("usd") || lower.contains("dollar") || lower.contains("$") -> "USD"
            lower.contains("eur") || lower.contains("euro") || lower.contains("€") -> "EUR"
            lower.contains("gbp") || lower.contains("pound") || lower.contains("£") -> "GBP"
            lower.contains("jpy") || lower.contains("yen") || lower.contains("¥") -> "JPY"
            lower.contains("sgd") || lower.contains("s$") -> "SGD"
            lower.contains("myr") || lower.contains("ringgit") || lower.contains("rm") -> "MYR"
            lower.contains("rp") || lower.contains("rupiah") || lower.contains("idr") -> "IDR"
            else -> null
        }

        // 1. Prioritas 1: Format Jutaan / Millions ("1.5 juta", "1,5 jt", "2jt", "10 juta", "2 million", "2.5m")
        val jutaRegex = Pattern.compile("(?i)(?:rp\\.?\\s*|[$€£¥₹]\\s*|usd\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(?:juta|jt|million|m\\b)\\b")
        val jutaMatcher = jutaRegex.matcher(lower)
        if (jutaMatcher.find()) {
            val numStr = jutaMatcher.group(1)?.replace(",", ".") ?: "1"
            val multiplier = 1_000_000.0
            val parsedNum = numStr.toDoubleOrNull()
            if (parsedNum != null && parsedNum > 0.0) {
                val finalVal = parsedNum * multiplier
                if (finalVal.isFinite() && finalVal <= MAX_REASONABLE_AMOUNT) {
                    return ExtractedAmount(
                        value = finalVal,
                        rawMatchedText = jutaMatcher.group(0) ?: "",
                        startIndex = jutaMatcher.start(),
                        endIndex = jutaMatcher.end(),
                        detectedCurrencyCode = detectedCurrCode
                    )
                }
            }
        }

        // 2. Prioritas 2: Format Ribuan Singkatan ("50k", "50rb", "50 ribu", "2.5k", "100 rb", "50 thousand")
        val ribuRegex = Pattern.compile("(?i)(?:rp\\.?\\s*|[$€£¥₹]\\s*|usd\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(?:ribu|rb|k|thousand)\\b")
        val ribuMatcher = ribuRegex.matcher(lower)
        if (ribuMatcher.find()) {
            val numStr = ribuMatcher.group(1)?.replace(",", ".") ?: "1"
            val multiplier = 1_000.0
            val parsedNum = numStr.toDoubleOrNull()
            if (parsedNum != null && parsedNum > 0.0) {
                val finalVal = parsedNum * multiplier
                if (finalVal.isFinite() && finalVal <= MAX_REASONABLE_AMOUNT) {
                    return ExtractedAmount(
                        value = finalVal,
                        rawMatchedText = ribuMatcher.group(0) ?: "",
                        startIndex = ribuMatcher.start(),
                        endIndex = ribuMatcher.end(),
                        detectedCurrencyCode = detectedCurrCode
                    )
                }
            }
        }

        // 3. Prioritas 3: Format Rupiah Standar dengan pemisah ribuan titik ("Rp 50.000", "50.000,-", "1.500.000")
        val dottedRupiahRegex = Pattern.compile("(?i)(?:rp\\.?\\s*)?(\\d{1,3}(?:\\.\\d{3})+)(?:\\s*,\\s*-)?\\b")
        val dottedMatcher = dottedRupiahRegex.matcher(lower)
        if (dottedMatcher.find()) {
            val numDigits = dottedMatcher.group(1)?.replace(".", "") ?: ""
            val parsedVal = numDigits.toDoubleOrNull()
            if (parsedVal != null && parsedVal > 0.0 && parsedVal.isFinite() && parsedVal <= MAX_REASONABLE_AMOUNT) {
                return ExtractedAmount(
                    value = parsedVal,
                    rawMatchedText = dottedMatcher.group(0) ?: "",
                    startIndex = dottedMatcher.start(),
                    endIndex = dottedMatcher.end(),
                    detectedCurrencyCode = detectedCurrCode ?: "IDR"
                )
            }
        }

        // 4. Prioritas 4: Format Internasional dengan koma ribuan ("$1,500.00", "50,000", "1,200.50")
        val internationalRegex = Pattern.compile("(?i)(?:[$€£¥₹]\\s*|usd\\s*|eur\\s*|gbp\\s*)?(\\d{1,3}(?:,\\d{3})+(?:\\.\\d{1,2})?)\\b")
        val intlMatcher = internationalRegex.matcher(lower)
        if (intlMatcher.find()) {
            val numDigits = intlMatcher.group(1)?.replace(",", "") ?: ""
            val parsedVal = numDigits.toDoubleOrNull()
            if (parsedVal != null && parsedVal > 0.0 && parsedVal.isFinite() && parsedVal <= MAX_REASONABLE_AMOUNT) {
                return ExtractedAmount(
                    value = parsedVal,
                    rawMatchedText = intlMatcher.group(0) ?: "",
                    startIndex = intlMatcher.start(),
                    endIndex = intlMatcher.end(),
                    detectedCurrencyCode = detectedCurrCode
                )
            }
        }

        // 5. Prioritas 5: Format Desimal Internasional ("$50.00", "€25.50", "15.75 dollars")
        val decimalRegex = Pattern.compile("(?i)(?:[$€£¥₹]\\s*|usd\\s*|eur\\s*)(\\d+\\.\\d{1,2})\\b")
        val decimalMatcher = decimalRegex.matcher(lower)
        if (decimalMatcher.find()) {
            val parsedVal = decimalMatcher.group(1)?.toDoubleOrNull()
            if (parsedVal != null && parsedVal > 0.0 && parsedVal.isFinite() && parsedVal <= MAX_REASONABLE_AMOUNT) {
                return ExtractedAmount(
                    value = parsedVal,
                    rawMatchedText = decimalMatcher.group(0) ?: "",
                    startIndex = decimalMatcher.start(),
                    endIndex = decimalMatcher.end(),
                    detectedCurrencyCode = detectedCurrCode
                )
            }
        }

        // 6. Prioritas 6: Angka polos dengan prefix simbol / kode mata uang ("$ 50", "USD 100", "Rp 5000")
        val prefixedPlainRegex = Pattern.compile("(?i)(?:rp\\.?\\s*|[$€£¥₹]\\s*|usd\\s*|eur\\s*|gbp\\s*|jpy\\s*|sgd\\s*|myr\\s*)(\\d{1,9})\\b")
        val prefMatcher = prefixedPlainRegex.matcher(lower)
        if (prefMatcher.find()) {
            val parsedVal = prefMatcher.group(1)?.toDoubleOrNull()
            if (parsedVal != null && parsedVal > 0.0 && parsedVal.isFinite() && parsedVal <= MAX_REASONABLE_AMOUNT) {
                return ExtractedAmount(
                    value = parsedVal,
                    rawMatchedText = prefMatcher.group(0) ?: "",
                    startIndex = prefMatcher.start(),
                    endIndex = prefMatcher.end(),
                    detectedCurrencyCode = detectedCurrCode
                )
            }
        }

        // 7. Prioritas 7: Angka polos umum (misal "makan 25000", "bensin 30000")
        val plainRegex = Pattern.compile("\\b(\\d{3,9})\\b")
        val plainMatcher = plainRegex.matcher(lower)
        if (plainMatcher.find()) {
            val parsedVal = plainMatcher.group(1)?.toDoubleOrNull()
            if (parsedVal != null && parsedVal > 0.0 && parsedVal.isFinite() && parsedVal <= MAX_REASONABLE_AMOUNT) {
                return ExtractedAmount(
                    value = parsedVal,
                    rawMatchedText = plainMatcher.group(0) ?: "",
                    startIndex = plainMatcher.start(),
                    endIndex = plainMatcher.end(),
                    detectedCurrencyCode = detectedCurrCode
                )
            }
        }

        return null
    }
}
