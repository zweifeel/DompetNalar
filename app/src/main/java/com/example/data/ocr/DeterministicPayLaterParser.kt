package com.example.data.ocr

import com.example.data.ai.PayLaterAiOcrScanner
import com.example.data.model.PayLaterBillScanResult
import com.example.data.model.PayLaterSchedulePeriod
import com.example.util.InstallmentDateHelper
import com.example.util.PayLaterDeduplicator
import com.example.util.ReceiptCalculator
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

/**
 * Parser Tagihan PayLater / Pinjaman Deterministik (Local-First OCR Parsing Engine).
 *
 * Mengolah baris teks hasil On-Device OCR dari screenshot tagihan finansial:
 * - Mengidentifikasi penyedia secara independen per gambar/bagian (Shopee Pinjam, Shopee PayLater, GoPay Pinjam, dsb).
 * - Mengekstrak setiap tanggal jatuh tempo secara independen per item/periode (Defect 3).
 * - Tidak pernah menggunakan satu global dueDate atau menimpa tanggal item lain.
 * - Mengekstrak multi-tagihan dan multi-periode cicilan dari satu atau banyak screenshot (Defect 4).
 * - DILARANG KERAS MENGARANG: Menandai warning jika data tidak yakin / tidak terbaca.
 */
object DeterministicPayLaterParser {

    private val PROVIDER_PATTERNS = listOf(
        Pair("GoPay Pinjam", listOf("gopay pinjam", "gopaypinjam", "pinjam gopay", "gopay loan")),
        Pair("Shopee Pinjam", listOf("spinjam", "s pinjam", "shopee pinjam", "pinjam shopee", "shopee loan")),
        Pair("Shopee PayLater", listOf("spaylater", "s paylater", "shopee paylater", "shopee pay later", "spay later", "shopee")),
        Pair("DANA Cicil", listOf("dana cicil", "danacicil", "dana paylater", "dana")),
        Pair("GoPay PayLater", listOf("gopay paylater", "gopay pay later")),
        Pair("GoPay Later", listOf("gopay later", "gopaylater", "findaya", "gopay")),
        Pair("Akulaku", listOf("akulaku", "aku laku")),
        Pair("Kredivo", listOf("kredivo")),
        Pair("AdaKami", listOf("adakami", "ada kami")),
        Pair("Indodana", listOf("indodana", "indo dana")),
        Pair("Home Credit", listOf("home credit", "homecredit")),
        Pair("Julo", listOf("julo")),
        Pair("Easycash", listOf("easycash", "easy cash")),
        Pair("Rupiah Cepat", listOf("rupiah cepat", "rupiahcepat")),
        Pair("OVO U Card", listOf("ovo u card", "ovo paylater", "ovo")),
        Pair("Kartu Kredit BCA", listOf("bca card", "kartu kredit bca"))
    )

    private val MONTH_MAP = mapOf(
        "januari" to 1, "jan" to 1, "january" to 1,
        "februari" to 2, "feb" to 2, "february" to 2,
        "maret" to 3, "mar" to 3, "march" to 3,
        "april" to 4, "apr" to 4,
        "mei" to 5, "may" to 5,
        "juni" to 6, "jun" to 6, "june" to 6,
        "juli" to 7, "jul" to 7, "july" to 7,
        "agustus" to 8, "ags" to 8, "agu" to 8, "agt" to 8, "august" to 8, "aug" to 8,
        "september" to 9, "sep" to 9, "sept" to 9,
        "oktober" to 10, "okt" to 10, "october" to 10, "oct" to 10,
        "november" to 11, "nov" to 11,
        "desember" to 12, "des" to 12, "december" to 12, "dec" to 12
    )

    /**
     * Parses multiple bills from a single OCR text payload if distinct bills/periods are found.
     * Guaranteed: Each bill or installment period retains its own individual dueDate and provider.
     */
    fun parseMultiBills(rawText: String, imageUriStr: String? = null): List<PayLaterBillScanResult> {
        val cleanText = rawText.trim()
        if (cleanText.isBlank() || cleanText.contains("TIDAK_ADA_TEKS_TERBACA", ignoreCase = true)) {
            return emptyList()
        }

        val lines = cleanText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val candidateBills = mutableListOf<PayLaterBillScanResult>()

        val docProvider = detectProvider(cleanText)

        // 1. Check if multi-paragraph sections exist (e.g. separate sections for different providers or distinct bills)
        val rawParagraphs = cleanText.split(Regex("""\n\s*\n""")).map { it.trim() }.filter { it.isNotBlank() }
        if (rawParagraphs.size > 1) {
            val paragraphBills = rawParagraphs.mapNotNull { pText ->
                try {
                    val p = parse(pText, imageUriStr)
                    val withDocProvider = if (p.providerName == "PayLater / Pinjaman" && docProvider.isNotBlank()) {
                        p.copy(providerName = docProvider)
                    } else p
                    if (withDocProvider.currentBillAmount > 0 || withDocProvider.totalRemainingAmount > 0) withDocProvider else null
                } catch (_: Exception) {
                    null
                }
            }
            if (paragraphBills.size > 1) {
                return paragraphBills
            }
        }

        // 1b. Check if multiple distinct bill blocks exist within single paragraph (e.g. GoPay Pinjam screenshot with separate loans)
        val billBlocks = mutableListOf<MutableList<String>>()
        var currentBlock = mutableListOf<String>()

        for (line in lines) {
            val lower = line.lowercase(Locale.ROOT)
            val isTagihanHeader = (lower.contains("tagihan") || lower.contains("pinjaman") || lower.contains("kontrak")) &&
                    (lower.contains("rp") || Regex("""\d{4,}""").containsMatchIn(line) || Regex("""\b(?:ke|periode)\s*\d+""").containsMatchIn(lower))

            if (isTagihanHeader && currentBlock.isNotEmpty()) {
                billBlocks.add(currentBlock)
                currentBlock = mutableListOf()
            }
            currentBlock.add(line)
        }
        if (currentBlock.isNotEmpty()) {
            billBlocks.add(currentBlock)
        }


        if (billBlocks.size > 1) {
            for (block in billBlocks) {
                try {
                    val blockText = block.joinToString("\n")
                    val parsed = parse(blockText, imageUriStr)
                    if (parsed.currentBillAmount > 0 || parsed.totalRemainingAmount > 0) {
                        val finalBill = if (parsed.providerName == "PayLater / Pinjaman" && docProvider.isNotBlank()) {
                            parsed.copy(providerName = docProvider)
                        } else {
                            parsed
                        }
                        candidateBills.add(finalBill)
                    }
                } catch (_: Exception) {}
            }
        }

        // If block separation found multiple valid bills with distinct amounts or dates
        if (candidateBills.size > 1) {
            return candidateBills
        }

        // 2. Check if a single bill contains multiple distinct schedule periods (e.g. Nov 12, Des 1, Jan 30)
        val singleParsed = parse(rawText, imageUriStr)
        if (singleParsed.installmentSchedule.size > 1) {
            // Create a dedicated item per scheduled period so each period's exact date is independently editable
            val multiPeriodItems = singleParsed.installmentSchedule.mapIndexed { idx, sched ->
                val pDue = sched.dueIsoDate ?: sched.dueDate
                val parsedDate = InstallmentDateHelper.parseStructuredDueDate(pDue)
                val isAmbiguous = parsedDate?.isAmbiguous ?: (sched.dueIsoDate == null)

                PayLaterBillScanResult(
                    id = UUID.randomUUID().toString(),
                    providerName = singleParsed.providerName,
                    productName = singleParsed.productName?.let { "$it (Periode ${idx + 1})" } ?: "Periode ${idx + 1}",
                    currentBillAmount = sched.amount,
                    totalRemainingAmount = singleParsed.totalRemainingAmount,
                    installmentCurrent = idx + 1,
                    installmentTotal = singleParsed.installmentSchedule.size,
                    dueDate = pDue,
                    dueDay = sched.dueDay ?: parsedDate?.day,
                    dueMonth = sched.dueMonth ?: parsedDate?.month,
                    dueYear = sched.dueYear ?: parsedDate?.year,
                    dueIsoDate = sched.dueIsoDate ?: parsedDate?.isoDate,
                    isDateAmbiguous = isAmbiguous,
                    dateConfidenceStatus = if (isAmbiguous) "NEEDS_USER_CONFIRMATION" else "VERIFIED",
                    billStatus = sched.status,
                    interestRate = singleParsed.interestRate,
                    rawNotes = singleParsed.rawNotes,
                    confidenceWarnings = if (isAmbiguous) listOf("Tanggal jatuh tempo periode ${idx + 1} memerlukan konfirmasi pengguna.") else emptyList(),
                    rawText = rawText,
                    imageUri = imageUriStr,
                    isDemoPreset = singleParsed.isDemoPreset,
                    installmentSchedule = singleParsed.installmentSchedule
                )
            }
            return multiPeriodItems
        }

        // Default: parse single comprehensive bill
        return listOf(singleParsed)
    }

    fun parse(rawText: String, imageUriStr: String? = null): PayLaterBillScanResult {
        val cleanText = rawText.trim()
        if (cleanText.isBlank() || cleanText.contains("TIDAK_ADA_TEKS_TERBACA", ignoreCase = true)) {
            throw IllegalStateException("Teks tagihan tidak terdeteksi pada tangkapan layar. Pastikan gambar memuat informasi tagihan yang jelas.")
        }

        val lines = cleanText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val lowerText = cleanText.lowercase(Locale("id", "ID"))
        val warnings = mutableListOf<String>()

        // 1. Ekstraksi Penyedia (Provider) & Platform Normalization (Defect 4)
        val detectedProvider = detectProvider(cleanText)
        val finalProviderName = if (detectedProvider.isBlank()) {
            warnings.add("Nama penyedia pinjaman tidak terdeteksi jelas pada gambar. Harap periksa dan pilih penyedia.")
            "PayLater / Pinjaman"
        } else {
            PayLaterDeduplicator.normalizePlatformName(detectedProvider)
        }

        // 2. Ekstraksi Produk
        val productName = when {
            lowerText.contains("pinjaman tunai") -> "Pinjaman Tunai"
            lowerText.contains("cicilan barang") || lowerText.contains("cicilan") -> "Cicilan Barang"
            lowerText.contains("spaylater") -> "SPayLater"
            lowerText.contains("spinjam") -> "SPinjam"
            lowerText.contains("dana cicil") -> "DANA Cicil"
            else -> null
        }

        // 3. Ekstraksi Multi-Periode Jadwal Cicilan (Defect 3)
        val extractedSchedules = extractMultiPeriodSchedules(lines)

        // 4. Ekstraksi Nominal (Tagihan Berjalan vs Total Utang)
        var currentBill = 0.0
        var totalRemaining = 0.0

        for (i in lines.indices) {
            val line = lines[i]
            val lower = line.lowercase(Locale.ROOT)

            if (lower.contains("tagihan bulan") || lower.contains("tagihan berjalan") ||
                lower.contains("tagihan 30 hari") || lower.contains("bayar sekarang") ||
                lower.contains("jumlah pembayaran") || lower.contains("tagihan saat ini") ||
                lower.contains("perlu dibayar") || lower.contains("harus dibayar") ||
                (lower.contains("tagihan") && !lower.contains("limit") && !lower.contains("sisa"))
            ) {
                val amt = extractAmountFromLineOrNext(line, lines.getOrNull(i + 1))
                if (amt > 0.0 && currentBill == 0.0) {
                    currentBill = amt
                }
            }

            if (lower.contains("total sisa") || lower.contains("sisa utang") ||
                lower.contains("sisa pinjaman") || lower.contains("total pinjaman") ||
                lower.contains("sisa tagihan") || lower.contains("limit terpakai")
            ) {
                val amt = extractAmountFromLineOrNext(line, lines.getOrNull(i + 1))
                if (amt > 0.0 && totalRemaining == 0.0) {
                    totalRemaining = amt
                }
            }
        }

        if (extractedSchedules.isNotEmpty()) {
            if (currentBill == 0.0) {
                currentBill = extractedSchedules.first().amount
            }
            if (totalRemaining == 0.0) {
                totalRemaining = extractedSchedules.sumOf { it.amount }
            }
        }

        if (currentBill == 0.0 && totalRemaining == 0.0) {
            val allAmounts = extractAllAmounts(cleanText)
            if (allAmounts.isNotEmpty()) {
                currentBill = allAmounts.first()
            }
        }

        if (totalRemaining == 0.0 && currentBill > 0.0) {
            totalRemaining = currentBill
        }

        if (currentBill == 0.0) {
            warnings.add("Nominal tagihan tidak terdeteksi otomatis. Silakan masukkan nominal secara manual.")
        }

        // 5. Ekstraksi Cicilan (misal "Cicilan 3 dari 6" atau "3/6" atau "tenor 12 bulan")
        var currentTenor: Int? = null
        var totalTenor: Int? = null

        val tenorRegex = Pattern.compile("(?i)(?:cicilan\\s+(\\d+)\\s*(?:dari|/)\\s*(\\d+)|\\b(\\d+)\\s+dari\\s+(\\d+)\\b)")
        val tenorMatcher = tenorRegex.matcher(cleanText)
        if (tenorMatcher.find()) {
            val c1 = tenorMatcher.group(1)?.toIntOrNull() ?: tenorMatcher.group(3)?.toIntOrNull()
            val t1 = tenorMatcher.group(2)?.toIntOrNull() ?: tenorMatcher.group(4)?.toIntOrNull()
            currentTenor = c1
            totalTenor = t1
        } else if (extractedSchedules.size > 1) {
            totalTenor = extractedSchedules.size
            currentTenor = 1
        } else {
            val bulanRegex = Pattern.compile("(?i)(?:tenor|periode)[:\\s]*(\\d+)\\s*bulan")
            val bulanMatcher = bulanRegex.matcher(cleanText)
            if (bulanMatcher.find()) {
                totalTenor = bulanMatcher.group(1)?.toIntOrNull()
                currentTenor = 1
            }
        }

        // 6. Ekstraksi Tanggal Jatuh Tempo Terstruktur
        var rawDueDate = ""
        val dueDateRegex = Pattern.compile("(?i)(?:jatuh\\s+tempo|bayar\\s+sebelum|due\\s+date)[:\\s]*([a-zA-Z0-9_\\s.,/-]+?)(?:\\n|$)")
        val dueMatcher = dueDateRegex.matcher(cleanText)
        if (dueMatcher.find()) {
            rawDueDate = dueMatcher.group(1)?.trim() ?: ""
        } else if (extractedSchedules.isNotEmpty()) {
            rawDueDate = extractedSchedules.first().dueIsoDate ?: extractedSchedules.first().dueDate
        } else {
            val dateLine = lines.firstOrNull { it.contains("jatuh tempo", true) || it.contains("bayar sebelum", true) }
            if (dateLine != null) {
                rawDueDate = dateLine
            }
        }

        val structuredDate = InstallmentDateHelper.parseStructuredDueDate(if (rawDueDate.isNotBlank()) rawDueDate else cleanText)
        val (normalizedDueDate, dueDay) = PayLaterAiOcrScanner.parseDueDateString(rawDueDate)

        val finalIsoDate = structuredDate?.isoDate?.takeIf { it.isNotBlank() }
        val finalYear = structuredDate?.year?.takeIf { it > 0 }
        val finalMonth = structuredDate?.month?.takeIf { it > 0 }
        val finalDay = structuredDate?.day?.takeIf { it > 0 } ?: dueDay

        val isDateAmbiguous = structuredDate?.isAmbiguous ?: (finalIsoDate == null)
        val dateConfidence = if (isDateAmbiguous) "NEEDS_USER_CONFIRMATION" else "VERIFIED"

        if (isDateAmbiguous) {
            warnings.add("Tanggal jatuh tempo memerlukan konfirmasi pengguna (format/tahun tidak pasti).")
        }

        // 7. Ekstraksi Status Tagihan
        val billStatus = when {
            lowerText.contains("jatuh tempo") && lowerText.contains("lewat") -> "Lewat Jatuh Tempo"
            lowerText.contains("belum bayar") -> "Belum Bayar"
            lowerText.contains("menunggu pembayaran") -> "Menunggu Pembayaran"
            else -> "Belum Bayar"
        }

        val effectiveDueDate = finalIsoDate ?: normalizedDueDate.ifBlank { "Tgl ${finalDay ?: 15} tiap bulan" }

        return PayLaterBillScanResult(
            providerName = finalProviderName,
            productName = productName,
            currentBillAmount = currentBill,
            totalRemainingAmount = totalRemaining,
            installmentCurrent = currentTenor,
            installmentTotal = totalTenor,
            dueDate = effectiveDueDate,
            dueDay = finalDay ?: 15,
            dueMonth = finalMonth,
            dueYear = finalYear,
            dueIsoDate = finalIsoDate,
            isDateAmbiguous = isDateAmbiguous,
            dateConfidenceStatus = dateConfidence,
            billStatus = billStatus,
            interestRate = 0.0,
            rawNotes = "Diproses via On-Device OCR DompetNalar",
            confidenceWarnings = warnings,
            rawText = cleanText,
            imageUri = imageUriStr,
            isDemoPreset = false,
            installmentSchedule = extractedSchedules
        )
    }

    /**
     * Ekstraksi jadwal multi-periode yang mendukung:
     * - "November 12 November", "Desember 1 Desember", "Januari 30 Januari"
     * - "12 November", "1 Desember", "30 Januari"
     * - "Jatuh tempo 12 Nov 2026", "Jatuh tempo 1 Des 2026", "Jatuh tempo 30 Jan 2027"
     * - Format urutan kronologis maupun tidak kronologis
     */
    fun extractMultiPeriodSchedules(lines: List<String>): List<PayLaterSchedulePeriod> {
        val scheduleList = mutableListOf<PayLaterSchedulePeriod>()
        val seenDates = mutableSetOf<String>()
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)

        val monthNames = "(?:januari|january|jan|februari|february|feb|maret|march|mar|april|apr|mei|may|juni|june|jun|juli|july|jul|agustus|august|agu|aug|september|sep|oktober|october|okt|oct|november|nov|desember|december|des|dec)"
        val datePattern = Regex(
            """(?i)\b(?:(\d{1,2})\s+($monthNames)(?:\s+(\d{4}))?|($monthNames)\s+(\d{1,2})(?:\s+(\d{4}))?|(\d{4})-(\d{1,2})-(\d{1,2})|(\d{1,2})[-/](\d{1,2})(?:[-/](\d{4}))?)\b"""
        )

        for (i in lines.indices) {
            val line = lines[i]
            val lowerLine = line.lowercase(Locale.ROOT)

            val match = datePattern.find(line)
            if (match != null) {
                var day: Int? = null
                var month: Int? = null
                var year: Int? = null
                var rawMatched = match.value

                if (match.groupValues[1].isNotEmpty() && match.groupValues[2].isNotEmpty()) {
                    // Day Month [Year] e.g. "12 November", "1 Desember 2026"
                    day = match.groupValues[1].toIntOrNull()
                    month = MONTH_MAP[match.groupValues[2].lowercase(Locale.ROOT)]
                    year = match.groupValues[3].toIntOrNull()
                } else if (match.groupValues[4].isNotEmpty() && match.groupValues[5].isNotEmpty()) {
                    // Month Day [Year] e.g. "November 12"
                    month = MONTH_MAP[match.groupValues[4].lowercase(Locale.ROOT)]
                    day = match.groupValues[5].toIntOrNull()
                    year = match.groupValues[6].toIntOrNull()
                } else if (match.groupValues[7].isNotEmpty()) {
                    // ISO YYYY-MM-DD
                    year = match.groupValues[7].toIntOrNull()
                    month = match.groupValues[8].toIntOrNull()
                    day = match.groupValues[9].toIntOrNull()
                } else if (match.groupValues[10].isNotEmpty() && match.groupValues[11].isNotEmpty()) {
                    // DD/MM[/YYYY]
                    day = match.groupValues[10].toIntOrNull()
                    month = match.groupValues[11].toIntOrNull()
                    year = match.groupValues[12].toIntOrNull()
                }

                if (day != null && month != null && month in 1..12 && day in 1..31) {
                    val key = "$day-$month"
                    if (!seenDates.contains(key)) {
                        seenDates.add(key)

                        // Extract amount in current line, preceding line, or next line
                        val amounts = extractAllAmounts(line)
                        val lineAmount = if (amounts.isNotEmpty()) {
                            amounts.first()
                        } else {
                            val nextLine = lines.getOrNull(i + 1)
                            val prevLine = if (i > 0) lines.getOrNull(i - 1) else null
                            (if (nextLine != null) extractAllAmounts(nextLine).firstOrNull() else null)
                                ?: (if (prevLine != null) extractAllAmounts(prevLine).firstOrNull() else null)
                                ?: 0.0
                        }

                        // Determine year sequentially if not explicitly provided
                        val resolvedYear = year ?: run {
                            // If schedule already has an item, check if month wrapped around to next year
                            val prevMonth = scheduleList.lastOrNull()?.dueMonth
                            val prevYear = scheduleList.lastOrNull()?.dueYear ?: currentYear
                            if (prevMonth != null && month < prevMonth) {
                                prevYear + 1
                            } else {
                                prevYear
                            }
                        }

                        val isValidCalendar = InstallmentDateHelper.isValidDate(resolvedYear, month, day)
                        val isoDateStr = if (isValidCalendar) String.format(Locale.ROOT, "%04d-%02d-%02d", resolvedYear, month, day) else null
                        val status = if (line.contains("lunas", true) || line.contains("selesai", true)) "Lunas" else "Belum Bayar"

                        scheduleList.add(
                            PayLaterSchedulePeriod(
                                periodIndex = scheduleList.size + 1,
                                dueDate = rawMatched,
                                amount = lineAmount,
                                status = status,
                                dueIsoDate = isoDateStr,
                                dueYear = resolvedYear,
                                dueMonth = month,
                                dueDay = day,
                                isPaid = status == "Lunas"
                            )
                        )
                    }
                }
            }
        }
        return scheduleList
    }

    /**
     * Mendeteksi nama penyedia secara deterministik dari teks tanpa menebak.
     */
    fun detectProvider(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        var matchedProvider = ""
        var maxKeywordLength = 0

        for ((providerName, keywords) in PROVIDER_PATTERNS) {
            for (kw in keywords) {
                if (lower.contains(kw) && kw.length > maxKeywordLength) {
                    matchedProvider = providerName
                    maxKeywordLength = kw.length
                }
            }
        }
        return matchedProvider
    }

    private fun extractAmountFromLineOrNext(line: String, nextLine: String?): Double {
        val inLine = extractAllAmounts(line).firstOrNull()
        if (inLine != null && inLine > 0.0) return inLine
        if (nextLine != null) {
            val inNext = extractAllAmounts(nextLine).firstOrNull()
            if (inNext != null && inNext > 0.0) return inNext
        }
        return 0.0
    }

    fun extractAllAmounts(text: String): List<Double> {
        val regex = Pattern.compile("(?i)(?:rp\\.?\\s*)?(\\d{1,3}(?:[.,]\\d{3})+|\\d{4,9})\\b")
        val matcher = regex.matcher(text)
        val list = mutableListOf<Double>()
        while (matcher.find()) {
            val rawMatched = matcher.group(0) ?: ""
            val amt = ReceiptCalculator.parseAmountSafe(rawMatched)
            if (amt in 2020.0..2035.0 && !rawMatched.lowercase(Locale.ROOT).contains("rp")) {
                continue
            }
            if (amt >= 1000.0) {
                list.add(amt)
            }
        }
        return list
    }
}
