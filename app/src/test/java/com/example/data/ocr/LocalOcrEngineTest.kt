package com.example.data.ocr

import com.example.data.model.TaxTreatment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalOcrEngineTest {

    // ==========================================
    // 1. DeterministicReceiptParser Tests
    // ==========================================

    @Test
    fun testReceiptParser_IndomaretReceipt() {
        val rawReceiptText = """
            INDOMARET POINT
            PT INDOMARCO PRISMATAMA
            JL. SUDIRMAN NO. 45 JAKARTA
            TGL: 28/10/2026 14:30
            
            1  ROTI TAWAR SARI ROTI    15.000
            2  SUSU ULTRA MILK 250ML   14.000
            1  MINERAL WATER 600ML      4.000
            
            SUBTOTAL                   33.000
            DISKON MEMBER              -3.000
            PPN 11%                     3.300
            TOTAL                      33.300
            TUNAI                      50.000
            KEMBALI                    16.700
            
            TERIMA KASIH ATAS KUNJUNGAN ANDA
        """.trimIndent()

        val result = DeterministicReceiptParser.parse(rawReceiptText)

        assertTrue("Nama toko harus memuat Indomaret", result.merchantName.contains("INDOMARET", ignoreCase = true))
        assertEquals(33300.0, result.totalAmount, 0.01)
        assertEquals(33000.0, result.subtotal, 0.01)
        assertEquals(3300.0, result.tax, 0.01)
        assertEquals(3000.0, result.discount, 0.01)
        assertEquals(TaxTreatment.INCLUSIVE, result.taxTreatment)
        assertTrue("Item list tidak boleh kosong", result.items.isNotEmpty())
        assertTrue("Harus mengandung roti", result.items.any { it.name.contains("ROTI", ignoreCase = true) })
    }

    @Test
    fun testReceiptParser_AlfamartReceipt() {
        val rawReceiptText = """
            ALFAMART GATOT SUBROTO
            JL. GATOT SUBROTO KAV 12
            25-10-2026
            
            KOPI KENANGAN BOTOL       9.500
            BISKUIT ROMA KELAPA      10.500
            
            TOTAL BELANJA            20.000
            CASH                     20.000
        """.trimIndent()

        val result = DeterministicReceiptParser.parse(rawReceiptText)

        assertTrue("Nama toko harus memuat Alfamart", result.merchantName.contains("ALFAMART", ignoreCase = true))
        assertEquals(20000.0, result.totalAmount, 0.01)
        assertTrue(result.items.size >= 2)
    }

    // ==========================================
    // 2. DeterministicPayLaterParser Tests
    // ==========================================

    @Test
    fun testPayLaterParser_SPayLater() {
        val rawBillText = """
            ShopeePayLater
            Tagihan Bulan Ini
            Rp 450.000
            Jatuh tempo: 05 Nov 2026
            Total sisa pinjaman: Rp 1.800.000
            Cicilan 2 dari 6 bulan
        """.trimIndent()

        val result = DeterministicPayLaterParser.parse(rawBillText)

        assertEquals("Shopee PayLater", result.providerName)
        assertEquals(450000.0, result.currentBillAmount, 0.01)
        assertEquals(1800000.0, result.totalRemainingAmount, 0.01)
        assertNotNull("Due date harus terdeteksi", result.dueDate)
        assertTrue("Jatuh tempo tanggal 5", result.dueDate.contains("5"))
        assertEquals(6, result.installmentTotal)
    }

    @Test
    fun testPayLaterParser_Kredivo() {
        val rawBillText = """
            Kredivo
            Rincian Tagihan Anda
            Total Tagihan: Rp 1.250.000
            Jatuh tempo pada 15/12/2026
            Total limit terpakai: Rp 3.500.000
            Tenor 3 bulan
        """.trimIndent()

        val result = DeterministicPayLaterParser.parse(rawBillText)

        assertEquals("Kredivo", result.providerName)
        assertEquals(1250000.0, result.currentBillAmount, 0.01)
        assertTrue("Jatuh tempo tanggal 15", result.dueDate.contains("15"))
        assertEquals(3, result.installmentTotal)
    }

    @Test
    fun testPayLaterParser_GoPayLater() {
        val rawBillText = """
            GoPay Later
            Tagihan jatuh tempo: 01-11-2026
            Jumlah yang harus dibayar
            Rp 320.000
        """.trimIndent()

        val result = DeterministicPayLaterParser.parse(rawBillText)

        assertEquals("GoPay Later", result.providerName)
        assertEquals(320000.0, result.currentBillAmount, 0.01)
        assertTrue("Jatuh tempo tanggal 1", result.dueDate.contains("1"))
    }
}
