package com.example.data.ocr

import com.example.data.ai.PayLaterAiOcrScanner
import com.example.data.model.DebtItem
import com.example.data.model.PayLaterBillScanResult
import com.example.util.PayLaterDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiBillOcrAndImportTest {

    @Test
    fun testGoPayPinjam_MultipleSeparateBillsFromOneText() {
        // Reference screenshot evidence:
        // Tagihan 1: Rp139.011, jatuh tempo 7 Desember 2026
        // Tagihan 2: Rp788.573, jatuh tempo 10 Desember 2026
        val ocrRawText = """
            GoPay Pinjam
            Rincian Pinjaman Anda
            
            Tagihan Rp139.011
            Jatuh tempo: 7 Des 2026
            Status: Belum Bayar
            
            Tagihan Rp788.573
            Jatuh tempo: 10 Des 2026
            Status: Belum Bayar
        """.trimIndent()

        val multiBills = DeterministicPayLaterParser.parseMultiBills(ocrRawText)
        assertEquals(2, multiBills.size)

        val bill1 = multiBills[0]
        assertEquals("GoPay Pinjam", bill1.providerName)
        assertEquals(139011.0, bill1.currentBillAmount, 0.01)
        assertEquals("2026-12-07", bill1.dueIsoDate)
        assertEquals(7, bill1.dueDay)

        val bill2 = multiBills[1]
        assertEquals("GoPay Pinjam", bill2.providerName)
        assertEquals(788573.0, bill2.currentBillAmount, 0.01)
        assertEquals("2026-12-10", bill2.dueIsoDate)
        assertEquals(10, bill2.dueDay)

        // Verify that deduplication does NOT mark these 2 separate loans as duplicates
        val dedupResult = PayLaterDeduplicator.checkDuplicateInBatch(
            candidateIndex = 0,
            candidate = bill1,
            batch = multiBills
        )
        assertFalse("Separate loans with different amounts and dates must not be duplicates", dedupResult.isDuplicate)
    }

    @Test
    fun testDanaCicil_MultiPeriodDistinctDueDates() {
        // Reference screenshot evidence:
        // 27 Okt 2026, 13 Nov 2026, 27 Nov 2026, 13 Des 2026, 27 Des 2026
        val ocrRawText = """
            DANA Cicil
            Jadwal Pembayaran Cicilan
            27 Okt 2026 Rp250.000 Belum Bayar
            13 Nov 2026 Rp250.000 Belum Bayar
            27 Nov 2026 Rp250.000 Belum Bayar
            13 Des 2026 Rp250.000 Belum Bayar
            27 Des 2026 Rp250.000 Belum Bayar
        """.trimIndent()

        val scanResult = DeterministicPayLaterParser.parse(ocrRawText)
        assertEquals("DANA Cicil", scanResult.providerName)
        assertEquals(5, scanResult.installmentSchedule.size)

        val dates = scanResult.installmentSchedule.map { it.dueIsoDate ?: it.dueDate }
        assertEquals("2026-10-27", dates[0])
        assertEquals("2026-11-13", dates[1])
        assertEquals("2026-11-27", dates[2])
        assertEquals("2026-12-13", dates[3])
        assertEquals("2026-12-27", dates[4])

        // Verify periods generation
        val plan = scanResult.toInstallmentPlanItem(userId = 1L)
        assertNotNull(plan)
        val periods = scanResult.toInstallmentPeriods(plan!!.id, userId = 1L)
        assertEquals(5, periods.size)
        assertEquals("2026-10-27", periods[0].dueDate)
        assertEquals("2026-11-13", periods[1].dueDate)
        assertEquals("2026-11-27", periods[2].dueDate)
        assertEquals("2026-12-13", periods[3].dueDate)
        assertEquals("2026-12-27", periods[4].dueDate)
    }

    @Test
    fun testBatchDuplicateDetection_IdenticalItemFlagged() {
        val item1 = PayLaterBillScanResult(
            providerName = "GoPay Pinjam",
            currentBillAmount = 139011.0,
            dueDate = "2026-12-07",
            dueDay = 7
        )
        val item2 = PayLaterBillScanResult(
            providerName = "gopay pinjam",
            currentBillAmount = 139011.0,
            dueDate = "2026-12-07",
            dueDay = 7
        )
        val batch = listOf(item1, item2)

        val check = PayLaterDeduplicator.checkDuplicateInBatch(
            candidateIndex = 1,
            candidate = item2,
            batch = batch
        )
        assertTrue("Identical platform, amount, and date in same batch must be flagged as duplicate", check.isDuplicate)
    }
}
