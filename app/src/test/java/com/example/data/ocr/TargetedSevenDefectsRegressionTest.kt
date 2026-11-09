package com.example.data.ocr

import com.example.data.model.PayLaterBillScanResult
import com.example.data.model.PayLaterSchedulePeriod
import com.example.util.InstallmentDateHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression Test Suite for DompetNalar 7 Targeted UI & OCR Fixes.
 * Specifically covering Defect 3 (Multiple Due Dates per Image) and Defect 4 (Multi-Image Provider Separation).
 */
class TargetedSevenDefectsRegressionTest {

    // =========================================================================
    // DEFECT 3: MULTIPLE DUE DATES IN ONE IMAGE REGRESSION TESTS
    // =========================================================================

    @Test
    fun testDefect3_SingleImageWithNovember12_December1_January30_YieldsThreeDistinctDueDates() {
        val ocrText = """
            GoPay Pinjam
            Jadwal Rincian Cicilan
            12 November 2026 Rp 350.000
            1 Desember 2026 Rp 350.000
            30 Januari 2027 Rp 350.000
        """.trimIndent()

        val parsedBills = DeterministicPayLaterParser.parseMultiBills(ocrText, "file://image1.jpg")

        assertEquals("Harus menghasilkan 3 item tagihan terpisah", 3, parsedBills.size)
        val dates = parsedBills.map { it.dueDate }
        assertEquals("Tagihan 1 harus jatuh tempo 12 Nov 2026", "2026-11-12", parsedBills[0].dueIsoDate)
        assertEquals("Tagihan 2 harus jatuh tempo 1 Des 2026", "2026-12-01", parsedBills[1].dueIsoDate)
        assertEquals("Tagihan 3 harus jatuh tempo 30 Jan 2027", "2027-01-30", parsedBills[2].dueIsoDate)

        // Verifikasi ketiga tanggal jatuh tempo berbeda dan tidak ter-flatten menjadi satu tanggal
        assertNotEquals(parsedBills[0].dueIsoDate, parsedBills[1].dueIsoDate)
        assertNotEquals(parsedBills[1].dueIsoDate, parsedBills[2].dueIsoDate)
        assertNotEquals(parsedBills[0].dueIsoDate, parsedBills[2].dueIsoDate)
    }

    @Test
    fun testDefect3_NonChronologicalDateOrderInOCRText_PreservesCorrectIndividualDates() {
        val ocrText = """
            Shopee PayLater
            Tagihan Berjalan
            Jatuh tempo 30 Januari 2027: Rp 200.000
            Jatuh tempo 12 November 2026: Rp 150.000
            Jatuh tempo 1 Desember 2026: Rp 180.000
        """.trimIndent()

        val schedules = DeterministicPayLaterParser.extractMultiPeriodSchedules(ocrText.lines())
        assertEquals("Harus mengekstrak 3 jadwal cicilan", 3, schedules.size)

        val parsedBills = DeterministicPayLaterParser.parseMultiBills(ocrText)
        assertEquals(3, parsedBills.size)

        // Item 1: Jan 30, Item 2: Nov 12, Item 3: Dec 1
        assertEquals(30, parsedBills[0].dueDay)
        assertEquals(1, parsedBills[0].dueMonth)
        assertEquals(12, parsedBills[1].dueDay)
        assertEquals(11, parsedBills[1].dueMonth)
        assertEquals(1, parsedBills[2].dueDay)
        assertEquals(12, parsedBills[2].dueMonth)
    }

    @Test
    fun testDefect3_TwoItemsWithSameMonthDifferentDays_PreservesDistinctDates() {
        val ocrText = """
            GoPay Pinjam
            Tagihan 1: Jatuh tempo 5 Desember 2026 Rp 100.000
            Tagihan 2: Jatuh tempo 20 Desember 2026 Rp 250.000
        """.trimIndent()

        val parsedBills = DeterministicPayLaterParser.parseMultiBills(ocrText)
        assertEquals(2, parsedBills.size)

        assertEquals("2026-12-05", parsedBills[0].dueIsoDate)
        assertEquals(5, parsedBills[0].dueDay)
        assertEquals("2026-12-20", parsedBills[1].dueIsoDate)
        assertEquals(20, parsedBills[1].dueDay)
        assertNotEquals(parsedBills[0].dueIsoDate, parsedBills[1].dueIsoDate)
    }

    @Test
    fun testDefect3_ItemWithoutRecognizableDate_FlagsWarningAndDoesNotCrash() {
        val ocrText = """
            Pinjaman Online
            Tagihan Berjalan: Rp 500.000
            Status: Belum Bayar
        """.trimIndent()

        val parsed = DeterministicPayLaterParser.parse(ocrText)
        assertEquals(500000.0, parsed.currentBillAmount, 0.01)
        assertTrue("Harus menandai ambiguitas jika tanggal tidak ditemukan", parsed.isDateAmbiguous)
        assertEquals("NEEDS_USER_CONFIRMATION", parsed.dateConfidenceStatus)
        assertTrue("Harus memberikan confidence warning", parsed.confidenceWarnings.isNotEmpty())
    }

    @Test
    fun testDefect3_AmbiguousDateWithoutYear_RequiresUserConfirmation() {
        val ocrText = """
            GoPay Pinjam
            Jatuh tempo 15 November
            Total Bayar Rp 250.000
        """.trimIndent()

        val parsed = DeterministicPayLaterParser.parse(ocrText)
        assertTrue("Tanggal tanpa tahun eksplisit harus berstatus ambiguous", parsed.isDateAmbiguous)
        assertEquals("NEEDS_USER_CONFIRMATION", parsed.dateConfidenceStatus)
    }

    @Test
    fun testDefect3_EditDateOnSecondItemDoesNotAffectFirstOrThirdItem() {
        val items = mutableListOf(
            PayLaterBillScanResult(id = "item-1", providerName = "GoPay Pinjam", dueDate = "2026-11-12", dueDay = 12, dueMonth = 11, dueYear = 2026, dueIsoDate = "2026-11-12"),
            PayLaterBillScanResult(id = "item-2", providerName = "GoPay Pinjam", dueDate = "2026-12-01", dueDay = 1, dueMonth = 12, dueYear = 2026, dueIsoDate = "2026-12-01"),
            PayLaterBillScanResult(id = "item-3", providerName = "GoPay Pinjam", dueDate = "2027-01-30", dueDay = 30, dueMonth = 1, dueYear = 2027, dueIsoDate = "2027-01-30")
        )

        // User edits item 2's due date to 2026-12-15
        val updatedItem2 = items[1].copy(
            dueDate = "2026-12-15",
            dueDay = 15,
            dueIsoDate = "2026-12-15"
        )
        items[1] = updatedItem2

        assertEquals("Item 1 date must remain 2026-11-12", "2026-11-12", items[0].dueIsoDate)
        assertEquals("Item 2 date must be updated to 2026-12-15", "2026-12-15", items[1].dueIsoDate)
        assertEquals("Item 3 date must remain 2027-01-30", "2027-01-30", items[2].dueIsoDate)
    }

    // =========================================================================
    // DEFECT 4: MULTI-IMAGE PROVIDER SEPARATION REGRESSION TESTS
    // =========================================================================

    @Test
    fun testDefect4_TwoScreenshotsGoPayPinjamAndShopeePinjam_RemainSeparateProviders() {
        val image1Text = """
            GoPay Pinjam
            Tagihan Berjalan Rp 450.000
            Jatuh tempo 10 Desember 2026
        """.trimIndent()

        val image2Text = """
            Shopee Pinjam
            SPinjam Rincian Tagihan
            Total Perlu Dibayar Rp 600.000
            Jatuh tempo 15 Desember 2026
        """.trimIndent()

        val bill1 = DeterministicPayLaterParser.parse(image1Text, "file://image1.jpg")
        val bill2 = DeterministicPayLaterParser.parse(image2Text, "file://image2.jpg")

        assertEquals("GoPay Pinjam", bill1.providerName)
        assertEquals("Shopee Pinjam", bill2.providerName)
        assertNotEquals("Provider tidak boleh tertukar atau mewarisi gambar pertama", bill1.providerName, bill2.providerName)
        assertEquals("file://image1.jpg", bill1.imageUri)
        assertEquals("file://image2.jpg", bill2.imageUri)
    }

    @Test
    fun testDefect4_ThreeScreenshotsFromThreeDifferentProviders() {
        val img1 = "GoPay Pinjam\nTagihan Rp 100.000\nJatuh tempo 1 Nov 2026"
        val img2 = "Shopee PayLater\nSPayLater Rp 200.000\nJatuh tempo 5 Nov 2026"
        val img3 = "Kredivo\nCicilan Kredivo Rp 300.000\nJatuh tempo 10 Nov 2026"

        val b1 = DeterministicPayLaterParser.parse(img1, "uri1")
        val b2 = DeterministicPayLaterParser.parse(img2, "uri2")
        val b3 = DeterministicPayLaterParser.parse(img3, "uri3")

        assertEquals("GoPay Pinjam", b1.providerName)
        assertEquals("Shopee PayLater", b2.providerName)
        assertEquals("Kredivo", b3.providerName)

        val providers = listOf(b1.providerName, b2.providerName, b3.providerName).distinct()
        assertEquals("Semua 3 penyedia harus independen dan terpisah", 3, providers.size)
    }

    @Test
    fun testDefect4_TwoScreenshotsFromSameProvider_KeptAsDistinctItems() {
        val img1 = "GoPay Pinjam\nPinjaman 1\nTagihan Rp 150.000\nJatuh tempo 7 Des 2026"
        val img2 = "GoPay Pinjam\nPinjaman 2\nTagihan Rp 750.000\nJatuh tempo 10 Des 2026"

        val b1 = DeterministicPayLaterParser.parse(img1, "uri_gopay_1")
        val b2 = DeterministicPayLaterParser.parse(img2, "uri_gopay_2")

        assertEquals("GoPay Pinjam", b1.providerName)
        assertEquals("GoPay Pinjam", b2.providerName)
        assertNotEquals("ID item harus unik", b1.id, b2.id)
        assertEquals(150000.0, b1.currentBillAmount, 0.01)
        assertEquals(750000.0, b2.currentBillAmount, 0.01)
        assertEquals("2026-12-07", b1.dueIsoDate)
        assertEquals("2026-12-10", b2.dueIsoDate)
    }

    @Test
    fun testDefect4_UnrecognizedProvider_RequiresConfirmationAndDoesNotGuess() {
        val unknownText = """
            Koperasi Maju Bersama Pinjaman
            Tagihan Jatuh Tempo 25 Desember 2026
            Rp 1.000.000
        """.trimIndent()

        val parsed = DeterministicPayLaterParser.parse(unknownText)
        assertEquals("PayLater / Pinjaman", parsed.providerName)
        assertTrue("Harus memberikan warning konfirmasi nama penyedia", parsed.confidenceWarnings.any { it.contains("penyedia") })
    }

    @Test
    fun testDefect4_SingleImageWithMultipleSectionsFromDifferentProviders() {
        val combinedText = """
            Shopee Pinjam
            Tagihan SPinjam Rp 300.000
            Jatuh tempo 5 Des 2026

            GoPay Pinjam
            Tagihan GoPay Rp 400.000
            Jatuh tempo 12 Des 2026
        """.trimIndent()

        val bills = DeterministicPayLaterParser.parseMultiBills(combinedText)
        assertEquals(2, bills.size)
        assertEquals("Shopee Pinjam", bills[0].providerName)
        assertEquals("GoPay Pinjam", bills[1].providerName)
    }

    @Test
    fun testDefect4_BatchContainsDifferentProvidersWithDifferentDueDates() {
        val batch = listOf(
            DeterministicPayLaterParser.parse("GoPay Pinjam\nTagihan Rp 100.000\nJatuh tempo 12 Nov 2026"),
            DeterministicPayLaterParser.parse("Shopee Pinjam\nTagihan Rp 200.000\nJatuh tempo 1 Des 2026"),
            DeterministicPayLaterParser.parse("DANA Cicil\nTagihan Rp 300.000\nJatuh tempo 30 Jan 2027")
        )

        assertEquals(3, batch.size)
        assertEquals("GoPay Pinjam", batch[0].providerName)
        assertEquals("2026-11-12", batch[0].dueIsoDate)

        assertEquals("Shopee Pinjam", batch[1].providerName)
        assertEquals("2026-12-01", batch[1].dueIsoDate)

        assertEquals("DANA Cicil", batch[2].providerName)
        assertEquals("2027-01-30", batch[2].dueIsoDate)
    }
}
