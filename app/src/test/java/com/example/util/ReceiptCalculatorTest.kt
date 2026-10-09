package com.example.util

import com.example.data.model.ReceiptItem
import com.example.data.model.ReceiptScanResult
import com.example.data.model.TaxTreatment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptCalculatorTest {

    private val EPSILON = 0.001

    @Test
    fun testCaseA_IndomaretRetailInclusiveTaxAndDiscount() {
        // Kasus A — Struk ritel Indomaret
        val subtotal = 103600.0
        val discount = 10600.0
        val tax = 9400.0
        val totalExpected = 93000.0

        val treatment = ReceiptCalculator.determineTaxTreatment(
            merchantName = "Indomaret SPBU DIPONEGORO BATU",
            rawText = "HARGA JUAL : 103,600\nVC REJOICE : (10,600)\nTOTAL : 93,000\nPPN : DPP= 94,000 PPN= 9,400\nNON PPN : DPP= 200",
            subtotal = subtotal,
            tax = tax,
            service = 0.0,
            discount = discount,
            totalAmount = totalExpected
        )

        assertEquals("Pajak Indomaret harus terdeteksi sebagai INCLUSIVE", TaxTreatment.INCLUSIVE, treatment)

        val calculatedTotal = ReceiptCalculator.calculateTotal(
            subtotal = subtotal,
            tax = tax,
            service = 0.0,
            discount = discount,
            taxTreatment = treatment,
            fallbackTotal = totalExpected
        )

        assertEquals(totalExpected, calculatedTotal, EPSILON)

        // Verifikasi jika item diedit tanpa perubahan total nilai transaksi
        val items = listOf(
            ReceiptItem(name = "Barang A", qty = 1, price = 50000.0, total = 50000.0),
            ReceiptItem(name = "Barang B", qty = 1, price = 53600.0, total = 53600.0)
        )
        val (recalculatedSubtotal, recalculatedTotal) = ReceiptCalculator.recalculate(
            items = items,
            tax = tax,
            service = 0.0,
            discount = discount,
            taxTreatment = treatment,
            fallbackTotal = totalExpected
        )

        assertEquals(103600.0, recalculatedSubtotal, EPSILON)
        assertEquals(93000.0, recalculatedTotal, EPSILON)
    }

    @Test
    fun testCaseB_RestaurantExclusiveTaxAndService() {
        // Kasus B — Restoran dengan pajak eksklusif
        val subtotal = 100000.0
        val tax = 10000.0
        val service = 5000.0
        val discount = 0.0
        val totalExpected = 115000.0

        val treatment = ReceiptCalculator.determineTaxTreatment(
            merchantName = "Cafe & Resto Nusantara",
            rawText = "Subtotal: 100.000\nPB1 (10%): 10.000\nService Charge: 5.000\nTotal: 115.000",
            subtotal = subtotal,
            tax = tax,
            service = service,
            discount = discount,
            totalAmount = totalExpected
        )

        assertEquals("Pajak restoran harus terdeteksi sebagai EXCLUSIVE", TaxTreatment.EXCLUSIVE, treatment)

        val calculatedTotal = ReceiptCalculator.calculateTotal(
            subtotal = subtotal,
            tax = tax,
            service = service,
            discount = discount,
            taxTreatment = treatment,
            fallbackTotal = totalExpected
        )

        assertEquals(totalExpected, calculatedTotal, EPSILON)
    }

    @Test
    fun testCaseC_NoDiscount() {
        // Kasus C — Tanpa diskon
        val subtotal = 50000.0
        val tax = 0.0
        val discount = 0.0

        val treatment = ReceiptCalculator.determineTaxTreatment(
            merchantName = "Warung Kopi",
            rawText = "Total: 50.000",
            subtotal = subtotal,
            tax = tax,
            service = 0.0,
            discount = discount,
            totalAmount = 50000.0
        )

        val total = ReceiptCalculator.calculateTotal(
            subtotal = subtotal,
            tax = tax,
            service = 0.0,
            discount = discount,
            taxTreatment = treatment,
            fallbackTotal = 50000.0
        )

        assertEquals(50000.0, total, EPSILON)
    }

    @Test
    fun testCaseD_UnknownTax() {
        // Kasus D — Pajak tidak diketahui
        val subtotal = 50000.0
        val tax = 5000.0
        val ocrTotal = 52000.0 // Angka anomali yang tidak cocok inklusif maupun eksklusif

        val treatment = ReceiptCalculator.determineTaxTreatment(
            merchantName = "Toko Serba Ada",
            rawText = "Belanja umum",
            subtotal = subtotal,
            tax = tax,
            service = 0.0,
            discount = 0.0,
            totalAmount = ocrTotal
        )

        assertEquals("Pajak yang tidak jelas harus berstatus UNKNOWN", TaxTreatment.UNKNOWN, treatment)

        // Harus mempertahankan total OCR yang valid dan tidak mengubahnya diam-diam
        val total = ReceiptCalculator.calculateTotal(
            subtotal = subtotal,
            tax = tax,
            service = 0.0,
            discount = 0.0,
            taxTreatment = treatment,
            fallbackTotal = ocrTotal
        )

        assertEquals("Total OCR harus dipertahankan saat tax treatment UNKNOWN", ocrTotal, total, EPSILON)
    }

    @Test
    fun testCaseE_ItemModifications() {
        // Kasus E — Perubahan item (tambah, edit, hapus)
        val initialItems = mutableListOf(
            ReceiptItem(name = "Item 1", qty = 1, price = 20000.0, total = 20000.0),
            ReceiptItem(name = "Item 2", qty = 2, price = 15000.0, total = 30000.0)
        )
        val tax = 5000.0
        val discount = 5000.0
        val treatment = TaxTreatment.EXCLUSIVE

        // 1. Initial recalculate
        val (sub1, tot1) = ReceiptCalculator.recalculate(
            items = initialItems,
            tax = tax,
            service = 0.0,
            discount = discount,
            taxTreatment = treatment
        )
        assertEquals(50000.0, sub1, EPSILON)
        assertEquals(50000.0 + 5000.0 - 5000.0, tot1, EPSILON) // 50000.0

        // 2. Tambah item baru
        initialItems.add(ReceiptItem(name = "Item 3", qty = 1, price = 10000.0, total = 10000.0))
        val (sub2, tot2) = ReceiptCalculator.recalculate(
            items = initialItems,
            tax = tax,
            service = 0.0,
            discount = discount,
            taxTreatment = treatment
        )
        assertEquals(60000.0, sub2, EPSILON)
        assertEquals(60000.0, tot2, EPSILON)

        // 3. Edit item (ubah qty Item 1 jadi 2 -> 40.000)
        initialItems[0] = initialItems[0].copy(qty = 2, total = 40000.0)
        val (sub3, tot3) = ReceiptCalculator.recalculate(
            items = initialItems,
            tax = tax,
            service = 0.0,
            discount = discount,
            taxTreatment = treatment
        )
        assertEquals(80000.0, sub3, EPSILON)
        assertEquals(80000.0, tot3, EPSILON)

        // 4. Hapus item (hapus Item 3)
        initialItems.removeAt(2)
        val (sub4, tot4) = ReceiptCalculator.recalculate(
            items = initialItems,
            tax = tax,
            service = 0.0,
            discount = discount,
            taxTreatment = treatment
        )
        assertEquals(70000.0, sub4, EPSILON)
        assertEquals(70000.0, tot4, EPSILON)
    }

    @Test
    fun testCaseF_ParsingAndBackwardsCompatibility() {
        // Kasus F — Parsing format angka Indonesia & kompatibilitas
        assertEquals(10600.0, ReceiptCalculator.parseAmountSafe("(10,600)"), EPSILON)
        assertEquals(10600.0, ReceiptCalculator.parseAmountSafe("(10.600)"), EPSILON)
        assertEquals(10600.0, ReceiptCalculator.parseAmountSafe("-10,600"), EPSILON)
        assertEquals(103600.0, ReceiptCalculator.parseAmountSafe("103,600"), EPSILON)
        assertEquals(103600.0, ReceiptCalculator.parseAmountSafe("103.600"), EPSILON)
        assertEquals(93000.0, ReceiptCalculator.parseAmountSafe("Rp 93.000"), EPSILON)
        assertEquals(93000.0, ReceiptCalculator.parseAmountSafe("IDR 93,000"), EPSILON)
        assertEquals(1250000.0, ReceiptCalculator.parseAmountSafe("1.250.000"), EPSILON)
        assertEquals(1250000.0, ReceiptCalculator.parseAmountSafe("1,250,000"), EPSILON)

        // Nilai kosong / null
        assertEquals(0.0, ReceiptCalculator.parseAmountSafe(null), EPSILON)
        assertEquals(0.0, ReceiptCalculator.parseAmountSafe(""), EPSILON)
        assertEquals(0.0, ReceiptCalculator.parseAmountSafe("   "), EPSILON)
        assertEquals(500.0, ReceiptCalculator.parseAmountSafe(null, defaultVal = 500.0), EPSILON)

        // Kompatibilitas model lama ReceiptScanResult tanpa taxTreatment atau discount
        val legacyResult = ReceiptScanResult(
            merchantName = "Toko Lama",
            subtotal = 50000.0,
            totalAmount = 50000.0
        )
        assertEquals(0.0, legacyResult.discount, EPSILON)
        assertEquals(TaxTreatment.UNKNOWN, legacyResult.taxTreatment)
    }
}
