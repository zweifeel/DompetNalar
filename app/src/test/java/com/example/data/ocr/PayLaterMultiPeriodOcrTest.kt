package com.example.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayLaterMultiPeriodOcrTest {

    @Test
    fun testPayLater_MultiPeriodInstallmentScheduleExtraction() {
        val rawBillText = """
            GoPay PayLater
            Rincian Tagihan & Cicilan Aktif
            
            Tagihan Bulan Ini: Rp 350.000
            Jatuh Tempo: 05 Nov 2026
            
            Jadwal Cicilan Mendatang:
            1. 05 Nov 2026 - Rp 350.000 (Cicilan 1 dari 5)
            2. 05 Des 2026 - Rp 350.000 (Cicilan 2 dari 5)
            3. 05 Jan 2027 - Rp 350.000 (Cicilan 3 dari 5)
            4. 05 Feb 2027 - Rp 350.000 (Cicilan 4 dari 5)
            5. 05 Mar 2027 - Rp 350.000 (Cicilan 5 dari 5)
            
            Total Sisa Pinjaman: Rp 1.750.000
            Limit Tersedia: Rp 3.250.000
        """.trimIndent()

        val result = DeterministicPayLaterParser.parse(rawBillText)

        // 1. Provider identification
        assertEquals("GoPay PayLater", result.providerName)

        // 2. Current bill and total remaining
        assertEquals(350000.0, result.currentBillAmount, 0.01)
        assertEquals(1750000.0, result.totalRemainingAmount, 0.01)

        // 3. Multi-period schedule extraction
        assertNotNull(result.installmentSchedule)
        assertEquals("Harus mengekstrak 5 periode cicilan", 5, result.installmentSchedule.size)

        // 4. Verify each installment period
        val period1 = result.installmentSchedule[0]
        assertEquals(1, period1.periodIndex)
        assertEquals(350000.0, period1.amount, 0.01)
        assertTrue(period1.dueDate.contains("Nov 2026"))

        val period5 = result.installmentSchedule[4]
        assertEquals(5, period5.periodIndex)
        assertEquals(350000.0, period5.amount, 0.01)
        assertTrue(period5.dueDate.contains("Mar 2027"))

        // 5. Sum of schedule periods equals total remaining
        val scheduleSum = result.installmentSchedule.sumOf { it.amount }
        assertEquals(1750000.0, scheduleSum, 0.01)
    }

    @Test
    fun testPayLater_ShopeePayLater_TableSchedule() {
        val rawBillText = """
            SPayLater
            Tagihan Berjalan
            Rp 500.000
            Jatuh tempo 25 Nov 2026
            
            Rincian Pelunasan:
            Bulan 1 (25 Nov 2026): Rp 500.000
            Bulan 2 (25 Des 2026): Rp 500.000
            Bulan 3 (25 Jan 2027): Rp 500.000
            Bulan 4 (25 Feb 2027): Rp 500.000
            Bulan 5 (25 Mar 2027): Rp 500.000
            Bulan 6 (25 Apr 2027): Rp 500.000
            
            Total Tagihan: Rp 3.000.000
        """.trimIndent()

        val result = DeterministicPayLaterParser.parse(rawBillText)

        assertEquals("Shopee PayLater", result.providerName)
        assertEquals(500000.0, result.currentBillAmount, 0.01)
        assertEquals(3000000.0, result.totalRemainingAmount, 0.01)
        assertEquals(6, result.installmentSchedule.size)
        assertEquals(6, result.installmentTotal)
    }

    @Test
    fun testPayLater_SinglePeriod_GracefulFallback() {
        val rawBillText = """
            Kredivo
            Tagihan 30 Hari
            Rp 150.000
            Jatuh tempo: 10 Nov 2026
        """.trimIndent()

        val result = DeterministicPayLaterParser.parse(rawBillText)

        assertEquals("Kredivo", result.providerName)
        assertEquals(150000.0, result.currentBillAmount, 0.01)
        // Should not crash and handle empty schedule safely
        assertTrue(result.installmentSchedule.isEmpty() || result.installmentSchedule.size == 1)
    }
}
