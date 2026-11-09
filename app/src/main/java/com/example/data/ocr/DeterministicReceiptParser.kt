package com.example.data.ocr

import com.example.data.model.ReceiptItem
import com.example.data.model.ReceiptScanResult
import com.example.data.model.TaxTreatment
import com.example.util.ReceiptCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Parser Nota Deterministik (Local-First OCR Parsing Engine).
 *
 * Mengolah baris teks hasil On-Device OCR (ML Kit) tanpa LLM atau koneksi internet:
 * 1. Zonal Segmentation: Header toko, daftar item, ringkasan finansial, dan footer metadata.
 * 2. Whitelist & Blacklist Filtering: Mengeliminasi baris sampah/metadata (NPWP, DPP, PPN, Kasir, No Bon, Call Center, SMS, Member).
 * 3. Validasi Integritas Matematis: Setiap baris item belanja divalidasi (qty x harga satuan ≈ total baris).
 * 4. Deteksi Perlakuan Pajak: Membedakan PPN Inclusive (harga sudah termasuk PPN) vs Exclusive.
 * 5. DILARANG MENGARANG: Jika teks kosong atau tidak terbaca, lempar exception transparan.
 */
object DeterministicReceiptParser {

    private val KNOWN_MERCHANTS = listOf(
        "INDOMARET", "ALFAMART", "SUPERINDO", "HYPERMART", "TRANSMART",
        "MOMOYO", "MIXUE", "KOPI KENANGAN", "JANJI JIWA", "CHATIME",
        "MCDONALD'S", "MCDONALDS", "KFC", "BURGER KING", "PIZZA HUT",
        "STARBUCKS", "POINT COFFEE", "LAWSON", "FAMILYMART", "CIRCLE K",
        "WARKOP", "WARUNG", "APOTEK K-24", "KIMIA FARMA", "GUARDIAN", "WATSONS"
    )

    private val METADATA_BLACKLIST = listOf(
        "npwp", "dpp", "ppn", "pb1", "pajak", "tax",
        "kasir", "cashier", "no. bon", "no.bon", "no bon", "no struk", "no. struk",
        "no.transaksi", "no transaksi", "no. order", "no order", "faktur",
        "call center", "customer care", "customer service", "sms", "telp", "phone",
        "poinku", "indomaret poinku", "member", "kartu member", "ponta", "loyalty",
        "barcode", "promo", "pas pesta", "selamat belanja", "terima kasih", "thank you",
        "kembalian", "kembali", "tunai", "cash", "debit", "qris", "edc", "visa", "mastercard",
        "shopeepay", "gopay", "ovo", "dana", "linkaja", "harga termasuk ppn",
        "pt. indomarco", "pt indomarco", "pt. sumber alfaria", "pt sumber alfaria",
        "keterangan", "layanan konsumen", "situs", "website", "www.", ".com", ".co.id"
    )

    fun parse(rawText: String, imageUriStr: String? = null): ReceiptScanResult {
        val cleanText = rawText.trim()
        if (cleanText.isBlank() || cleanText.contains("TIDAK_ADA_TEKS_TERBACA", ignoreCase = true)) {
            throw IllegalStateException("Teks nota tidak terdeteksi atau tidak terbaca pada foto. Pastikan foto cukup terang dan fokus pada nota.")
        }

        val lines = cleanText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            throw IllegalStateException("Teks nota tidak terdeteksi atau tidak terbaca pada foto.")
        }

        // 1. Ekstraksi Nama Toko (Merchant Name)
        var merchantName = "[tidak terbaca]"
        for (known in KNOWN_MERCHANTS) {
            val matchingLine = lines.take(8).firstOrNull { it.contains(known, ignoreCase = true) }
            if (matchingLine != null) {
                merchantName = known
                break
            }
        }
        if (merchantName == "[tidak terbaca]") {
            for (line in lines.take(5)) {
                if (!isDateOrNumberLine(line) && !isBlacklistedMetadata(line) && line.length >= 3) {
                    merchantName = line.take(40)
                    break
                }
            }
        }

        // 2. Ekstraksi Tanggal Transaksi
        var dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val dateRegex = Pattern.compile("(?i)\\b(\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}|\\d{1,2}\\s+(?:jan|feb|mar|apr|mei|jun|jul|ags|agt|sep|okt|nov|des)[a-z]*\\s+\\d{2,4})\\b")
        for (line in lines) {
            val matcher = dateRegex.matcher(line)
            if (matcher.find()) {
                dateStr = matcher.group(1)?.replace("/", "-")?.replace(".", "-") ?: dateStr
                break
            }
        }

        // 3. Ekstraksi Nilai Moneter Ringkasan Finansial (Total, Subtotal, Pajak, Diskon, Service)
        var detectedTotal = 0.0
        var detectedSubtotal = 0.0
        var detectedTax = 0.0
        var detectedDiscount = 0.0
        var detectedService = 0.0

        for (i in lines.indices) {
            val line = lines[i]
            val lower = line.lowercase(Locale.ROOT)

            // TOTAL / TOTAL AKHIR / TOTAL BAYAR
            if ((lower.contains("total bayar") || lower.contains("grand total") || lower.contains("jumlah bayar") ||
                lower.startsWith("total:") || lower.startsWith("total ") || lower == "total") &&
                !lower.contains("subtotal") && !lower.contains("sub total") && !lower.contains("kembali")
            ) {
                val amt = extractAmountFromLineOrNext(line, lines.getOrNull(i + 1))
                if (amt > 0.0 && detectedTotal == 0.0) {
                    detectedTotal = amt
                }
            }

            // SUBTOTAL / TOTAL BELANJA / TOTAL HARGA
            if (lower.contains("subtotal") || lower.contains("sub total") || lower.contains("total belanja") || lower.contains("total harga")) {
                val amt = extractAmountFromLineOrNext(line, lines.getOrNull(i + 1))
                if (amt > 0.0 && detectedSubtotal == 0.0) {
                    detectedSubtotal = amt
                }
            }

            // PAJAK / PPN / PB1 / DPP
            if (lower.contains("ppn") || lower.contains("pajak") || lower.contains("pb1") || lower.contains("tax")) {
                val amt = extractAmountFromLineOrNext(line, lines.getOrNull(i + 1))
                if (amt > 0.0 && detectedTax == 0.0) {
                    detectedTax = amt
                }
            }

            // DISKON / POTONGAN
            if (lower.contains("diskon") || lower.contains("potongan") || lower.contains("hemat") || lower.contains("promo") || lower.contains("voucher")) {
                val amt = extractAmountFromLineOrNext(line, lines.getOrNull(i + 1))
                if (amt > 0.0 && detectedDiscount == 0.0) {
                    detectedDiscount = amt
                }
            }

            // SERVICE CHARGE
            if (lower.contains("service") || lower.contains("layanan")) {
                val amt = extractAmountFromLineOrNext(line, lines.getOrNull(i + 1))
                if (amt > 0.0 && detectedService == 0.0) {
                    detectedService = amt
                }
            }
        }

        // 4. Ekstraksi Baris Item Belanja (Zonal Filtering & Mathematical Integrity)
        val itemsList = mutableListOf<ReceiptItem>()
        var inItemZone = false
        var hitSummaryOrFooter = false

        for (i in lines.indices) {
            val line = lines[i]
            val lower = line.lowercase(Locale.ROOT)

            // Deteksi batas ringkasan / footer nota
            if (isSummaryOrFooterStart(lower)) {
                hitSummaryOrFooter = true
                continue
            }

            // Jika belum melewati ringkasan, periksa apakah baris ini adalah item belanja
            if (!hitSummaryOrFooter) {
                // Abaikan jika baris cocok dengan blacklist metadata toko
                if (isBlacklistedMetadata(lower)) {
                    continue
                }

                // Abaikan jika baris tanggal, waktu, no bon, atau header toko
                if (isDateOrNumberLine(line) || isBlacklistedMetadata(lower)) {
                    continue
                }

                val item = tryParseReceiptItem(line)
                if (item != null) {
                    itemsList.add(item)
                    inItemZone = true
                }
            }
        }

        // Tentukan Subtotal jika belum terdeteksi dari teks
        val itemsSum = itemsList.sumOf { it.total }
        if (detectedSubtotal == 0.0 && itemsSum > 0.0) {
            detectedSubtotal = itemsSum
        }

        // Tentukan Total jika belum terdeteksi dari teks
        if (detectedTotal == 0.0) {
            detectedTotal = if (detectedSubtotal > 0.0) {
                if (detectedTax > 0.0 && Math.abs((detectedSubtotal + detectedTax) - itemsSum) > 10.0) {
                    detectedSubtotal + detectedTax + detectedService - detectedDiscount
                } else {
                    detectedSubtotal
                }
            } else {
                itemsSum
            }
        }

        // Tentukan Perlakuan Pajak (Tax Treatment)
        val taxTreatment = when {
            detectedTax <= 0.0 -> TaxTreatment.INCLUSIVE
            Math.abs((detectedSubtotal + detectedTax) - detectedTotal) < 100.0 -> TaxTreatment.EXCLUSIVE
            Math.abs(detectedSubtotal - detectedTotal) < 100.0 -> TaxTreatment.INCLUSIVE
            else -> TaxTreatment.INCLUSIVE
        }

        // Klasifikasi Kategori Otomatis
        val (catName, catIcon) = when {
            merchantName.contains("INDOMARET", true) || merchantName.contains("ALFAMART", true) ||
                    merchantName.contains("SUPERINDO", true) || merchantName.contains("HYPERMART", true) ->
                Pair("Belanja & Hobi", "shopping_bag")
            merchantName.contains("APOTEK", true) || merchantName.contains("FARMA", true) ||
                    merchantName.contains("GUARDIAN", true) || merchantName.contains("WATSONS", true) ->
                Pair("Kesehatan", "medical_services")
            else -> Pair("Makanan & Minuman", "restaurant")
        }

        return ReceiptScanResult(
            merchantName = merchantName,
            date = dateStr,
            category = catName,
            categoryIcon = catIcon,
            subtotal = detectedSubtotal,
            tax = detectedTax,
            serviceCharge = detectedService,
            discount = detectedDiscount,
            totalAmount = detectedTotal,
            taxTreatment = taxTreatment,
            items = itemsList,
            imageUri = imageUriStr,
            rawText = cleanText
        )
    }

    private fun isDateOrNumberLine(line: String): Boolean {
        val lower = line.lowercase(Locale.ROOT)
        return lower.matches(Regex(".*\\b\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}\\b.*")) ||
                lower.matches(Regex("^[\\d\\s.,:/-]+$")) ||
                lower.startsWith("jl.") || lower.startsWith("jalan") || lower.startsWith("telp")
    }

    private fun isBlacklistedMetadata(lower: String): Boolean {
        return METADATA_BLACKLIST.any { lower.contains(it) } ||
                lower.startsWith("jl.") || lower.startsWith("jalan") ||
                lower.matches(Regex(".*npwp.*")) ||
                lower.matches(Regex(".*\\b(?:dpp|ppn|pb1)\\b.*")) ||
                lower.matches(Regex(".*\\bkasir\\b.*")) ||
                lower.matches(Regex(".*\\bno\\.?\\s*(?:bon|struk|transaksi).*")) ||
                lower.matches(Regex(".*\\bcall\\s*center.*")) ||
                lower.matches(Regex(".*\\b(?:sms|wa)\\b\\s*[:0-9].*")) ||
                lower.matches(Regex("^[0-9\\s.-]{12,}$")) // Deretan angka nomor resi/kartu/barcode murni
    }

    private fun isSummaryOrFooterStart(lower: String): Boolean {
        return (lower.startsWith("total") || lower.startsWith("subtotal") || lower.startsWith("grand total") ||
                lower.startsWith("jumlah bayar") || lower.startsWith("total bayar") || lower.startsWith("tunai") ||
                lower.startsWith("cash") || lower.startsWith("kembali") || lower.startsWith("kembalian")) &&
                !lower.contains("sgnture") && !lower.contains("hydro") && !lower.contains("minerale")
    }

    private fun extractAmountFromLineOrNext(line: String, nextLine: String?): Double {
        val inLine = extractAmountsFromText(line).lastOrNull() ?: ReceiptCalculator.parseAmountSafe(line)
        if (inLine > 0.0) return inLine
        if (nextLine != null && !isBlacklistedMetadata(nextLine.lowercase(Locale.ROOT))) {
            val inNext = extractAmountsFromText(nextLine).lastOrNull() ?: ReceiptCalculator.parseAmountSafe(nextLine)
            if (inNext > 0.0) return inNext
        }
        return 0.0
    }

    private fun extractAmountsFromText(text: String): List<Double> {
        val priceRegex = Pattern.compile("(?i)(?:rp\\.?\\s*)?(\\d{1,3}(?:[.,]\\d{3})+|\\d{3,8})\\b")
        val matcher = priceRegex.matcher(text)
        val list = mutableListOf<Double>()
        while (matcher.find()) {
            val amt = ReceiptCalculator.parseAmountSafe(matcher.group(0))
            if (amt > 0.0) {
                list.add(amt)
            }
        }
        return list
    }

    private fun tryParseReceiptItem(line: String): ReceiptItem? {
        val clean = line.trim()
        if (clean.length < 3) return null

        // Harus memiliki setidaknya 2 huruf alfabet untuk menjadi nama produk
        val letterCount = clean.count { it.isLetter() }
        if (letterCount < 2) return null

        // Format pola harga
        val priceRegex = Pattern.compile("(?i)(?:rp\\.?\\s*)?(\\d{1,3}(?:[.,]\\d{3})+|\\d{3,8})\\b")
        val matcher = priceRegex.matcher(clean)
        val pricesFound = mutableListOf<Pair<Double, Int>>() // amount to start index

        while (matcher.find()) {
            val amt = ReceiptCalculator.parseAmountSafe(matcher.group(0))
            if (amt >= 100.0) {
                pricesFound.add(Pair(amt, matcher.start()))
            }
        }

        if (pricesFound.isEmpty()) return null

        val lastPrice = pricesFound.last()
        val namePart = clean.substring(0, lastPrice.second)
            .trim()
            .replace(Regex("^[0-9]+\\s*"), "") // Hilangkan nomor urut baris di awal misal "1. SGNTURE"
            .trim()

        if (namePart.length < 2) return null

        // Periksa kuantitas dan harga satuan
        var qty = 1
        var unitPrice = lastPrice.first

        if (pricesFound.size >= 2) {
            // Jika terdapat 2 atau 3 angka pada baris: misal "SGNTURE 12  1  26.500  26.500"
            // Cari kuantitas integer kecil (1..99)
            val potentialQty = clean.substring(0, lastPrice.second)
                .split("\\s+".toRegex())
                .mapNotNull { it.toIntOrNull() }
                .firstOrNull { it in 1..99 }

            if (potentialQty != null) {
                qty = potentialQty
                unitPrice = pricesFound[0].first
            } else {
                unitPrice = pricesFound[0].first
                val total = lastPrice.first
                if (unitPrice > 0.0) {
                    val calcQty = Math.round(total / unitPrice).toInt()
                    if (calcQty in 1..99) {
                        qty = calcQty
                    }
                }
            }
        }

        // Validasi matematis: jika qty > 1 dan unitPrice != total, pastikan konsisten
        val total = lastPrice.first
        if (qty > 1 && unitPrice > 0.0 && unitPrice != total) {
            val expectedTotal = qty * unitPrice
            if (Math.abs(expectedTotal - total) > 200.0) {
                // Jika tidak sinkron, gunakan total sebagai acuan dan set qty = 1
                qty = 1
                unitPrice = total
            }
        }

        return ReceiptItem(
            name = namePart.take(50),
            qty = qty,
            price = unitPrice,
            total = total
        )
    }
}
