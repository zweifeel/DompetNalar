package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.data.model.PayLaterBillScanResult
import com.example.data.ocr.DeterministicPayLaterParser
import com.example.data.ocr.LocalPayLaterOcrScanner
import com.example.util.InstallmentDateHelper
import com.example.util.PayLaterDeduplicator
import com.example.util.ReceiptCalculator
import org.json.JSONObject

/**
 * Adaptor Pemindai Tagihan PayLater FinSmart (100% Local-First).
 *
 * Meneruskan pemindaian tangkapan layar ke [LocalPayLaterOcrScanner] on-device
 * tanpa memerlukan Gemini API Key atau koneksi internet.
 */
object PayLaterAiOcrScanner {

    fun getSamplePayLaterBill(presetKey: String): PayLaterBillScanResult {
        return LocalPayLaterOcrScanner.getSamplePayLaterBill(presetKey)
    }

    suspend fun scanPayLaterScreenshot(
        context: Context,
        imageUri: Uri?,
        bitmap: Bitmap? = null
    ): PayLaterBillScanResult {
        return LocalPayLaterOcrScanner.scanPayLaterScreenshot(context, imageUri, bitmap)
    }

    suspend fun scanMultipleScreenshots(
        context: Context,
        imageUris: List<Uri>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): com.example.data.model.PayLaterBatchImportResult {
        return LocalPayLaterOcrScanner.scanMultipleScreenshots(context, imageUris, onProgress)
    }

    /**
     * Mem-parsing JSON hasil ekstraksi terstruktur menjadi [PayLaterBillScanResult].
     * Digunakan untuk utilitas parser, pengetesan unit test, dan deserialisasi payload.
     */
    fun parseScanResultFromJson(
        json: JSONObject,
        rawText: String? = null,
        imageUriStr: String? = null
    ): PayLaterBillScanResult {
        val rawProvider = json.optString("nama_penyedia", "").trim()
        val providerName = if (rawProvider.isNotBlank()) PayLaterDeduplicator.normalizePlatformName(rawProvider) else ""
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
        val structuredDate = InstallmentDateHelper.parseStructuredDueDate(rawDueDate)
        val (normalizedDueDate, dueDay) = parseDueDateString(rawDueDate)

        val finalIsoDate = structuredDate?.isoDate?.takeIf { it.isNotBlank() }
        val finalYear = structuredDate?.year?.takeIf { it > 0 }
        val finalMonth = structuredDate?.month?.takeIf { it > 0 }
        val finalDay = structuredDate?.day?.takeIf { it > 0 } ?: dueDay

        val isDateAmbiguous = structuredDate?.isAmbiguous ?: (finalIsoDate == null)
        val dateConfidence = if (isDateAmbiguous) "NEEDS_USER_CONFIRMATION" else "VERIFIED"

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
        if (normalizedDueDate.isBlank() && finalIsoDate == null) {
            warnings.add("Tanggal jatuh tempo tidak terbaca. Harap tentukan tanggal jatuh tempo.")
        } else if (isDateAmbiguous) {
            warnings.add("Tanggal jatuh tempo memerlukan konfirmasi pengguna (format/tahun tidak pasti).")
        }

        val effectiveDueDate = finalIsoDate ?: normalizedDueDate

        return PayLaterBillScanResult(
            providerName = providerName,
            productName = productName,
            currentBillAmount = currentBillAmount,
            totalRemainingAmount = totalRemainingAmount,
            installmentCurrent = installmentCurrent,
            installmentTotal = installmentTotal,
            dueDate = effectiveDueDate,
            dueDay = finalDay,
            dueMonth = finalMonth,
            dueYear = finalYear,
            dueIsoDate = finalIsoDate,
            isDateAmbiguous = isDateAmbiguous,
            dateConfidenceStatus = dateConfidence,
            billStatus = billStatus,
            interestRate = interestRate,
            rawNotes = rawNotes,
            confidenceWarnings = warnings,
            rawText = rawText,
            imageUri = imageUriStr
        )
    }

    /**
     * Menormalkan string tanggal jatuh tempo ke format standar Indonesia atau ISO kalender.
     * Mengembalikan Pair(normalizedDueDateString, dayOfMonthInt)
     * Tidak membuang informasi bulan/tahun jika tanggal kalender lengkap terdeteksi.
     */
    fun parseDueDateString(raw: String?): Pair<String, Int?> {
        if (raw.isNullOrBlank()) return Pair("", null)

        val trimmed = raw.trim()

        // 1. Check if complete structured date exists (e.g. "7 Des 2026", "2026-12-07", "07/12/2026")
        val structured = InstallmentDateHelper.parseStructuredDueDate(trimmed)
        if (structured != null && !structured.isAmbiguous && structured.isoDate.isNotBlank()) {
            return Pair(structured.isoDate, structured.day)
        }

        // 2. Explicit day like "Tgl 5 tiap bulan" or "Tgl 5"
        val dayMatch = Regex("(?i)(?:tgl|tanggal)\\s*([0-9]{1,2})").find(trimmed)
        if (dayMatch != null) {
            val day = dayMatch.groupValues[1].toIntOrNull()
            if (day != null && day in 1..31) {
                return Pair("Tgl $day tiap bulan", day)
            }
        }

        // 3. Standalone day number in strings like "5 tiap bulan"
        val tiapBulanMatch = Regex("([0-9]{1,2})\\s*(?:tiap\\s*bulan)", RegexOption.IGNORE_CASE).find(trimmed)
        if (tiapBulanMatch != null) {
            val day = tiapBulanMatch.groupValues[1].toIntOrNull()
            if (day != null && day in 1..31) {
                return Pair("Tgl $day tiap bulan", day)
            }
        }

        // 4. Ambiguous structured date (e.g. "7 Des", "Due 07/12", "November 2026")
        if (structured != null && structured.isAmbiguous) {
            val dayVal = if (structured.day > 0) structured.day else null
            return Pair(trimmed, dayVal)
        }

        // 5. Fallback: extract first plausible integer 1..31
        val anyIntMatch = Regex("\\b([1-9]|[12][0-9]|3[01])\\b").find(trimmed)
        if (anyIntMatch != null) {
            val day = anyIntMatch.groupValues[1].toIntOrNull()
            if (day != null && day in 1..31) {
                return Pair("Tgl $day tiap bulan", day)
            }
        }

        return Pair(trimmed, null)
    }

    fun parseStructuredDueDate(raw: String?): InstallmentDateHelper.StructuredDueDateResult? {
        return InstallmentDateHelper.parseStructuredDueDate(raw)
    }
}
