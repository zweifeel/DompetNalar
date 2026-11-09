package com.example.data.ocr

import com.example.data.model.TaxTreatment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptStrictIndomaretOcrTest {

    @Test
    fun testIndomaretReceipt_StrictZonalAndMetadataFiltering() {
        // Complete realistic Indomaret receipt containing typical metadata noise:
        // NPWP, DPP, PPN, Kasir, No Bon, Call Center, SMS, Poinku, Barcode
        val rawReceiptText = """
            INDOMARET POINT
            PT INDOMARCO PRISMATAMA
            JL. ANCOL BARAT VIII NO. 2 JAKARTA UTARA
            NPWP : 01.308.232.0-058.000
            TGL : 28/10/2026 14:30:22
            NO BON : 01-12-8889
            KASIR : ANDI PRATAMA
            ------------------------------------------------
            1  ROTI TAWAR KUPAS                15.000
            2  ULTRA MILK CHOCO 250ML          14.000
            1  AIR MINERAL 600ML                4.000
            ------------------------------------------------
            SUBTOTAL                           33.000
            DPP                                29.730
            PPN 11%                             3.270
            TOTAL BAYAR                        33.000
            TUNAI                              50.000
            KEMBALI                            17.000
            ------------------------------------------------
            JUMLAH ITEM : 3
            POINKU ID : 9988776655
            CALL CENTER : 1500-280
            SMS : 0816-1500-280
            BARCODE : 8992775312019
            KRITIK & SARAN: CUSTOMERCARE@INDOMARET.CO.ID
            TERIMA KASIH TELAH BERBELANJA DI INDOMARET
        """.trimIndent()

        val result = DeterministicReceiptParser.parse(rawReceiptText)

        // 1. Merchant Name
        assertTrue("Merchant must identify Indomaret", result.merchantName.contains("INDOMARET", ignoreCase = true))

        // 2. Exact Item Count
        assertEquals("Must extract exactly 3 purchased items", 3, result.items.size)

        // 3. Verify Purchased Item Names
        val itemNames = result.items.map { it.name.uppercase() }
        assertTrue("Must contain Roti", itemNames.any { it.contains("ROTI") })
        assertTrue("Must contain Milk", itemNames.any { it.contains("MILK") })
        assertTrue("Must contain Air Mineral", itemNames.any { it.contains("MINERAL") })

        // 4. Strict Blacklist Check: Ensure NO metadata lines crept into items
        val forbiddenMetadataTerms = listOf(
            "NPWP", "DPP", "PPN", "KASIR", "NO BON", "CALL CENTER",
            "SMS", "POINKU", "BARCODE", "SUBTOTAL", "TOTAL BAYAR",
            "TUNAI", "KEMBALI", "JUMLAH ITEM", "KRITIK"
        )
        for (item in result.items) {
            for (noise in forbiddenMetadataTerms) {
                assertFalse(
                    "Item '${item.name}' must not contain blacklisted metadata token '$noise'",
                    item.name.uppercase().contains(noise)
                )
            }
        }

        // 5. Total, Tax, Subtotal
        assertEquals(33000.0, result.totalAmount, 0.01)
        assertEquals(33000.0, result.subtotal, 0.01)
        assertEquals(3270.0, result.tax, 0.01)
        assertEquals(TaxTreatment.INCLUSIVE, result.taxTreatment)

        // 6. Math check: Sum of item line totals must equal subtotal/total
        val calculatedSum = result.items.sumOf { it.total }
        assertEquals(33000.0, calculatedSum, 0.01)
    }

    @Test
    fun testReceiptParser_ExclusiveTaxReceipt() {
        val rawReceiptText = """
            COFFEE SHOP NUSANTARA
            JL. SENOPATI NO 10
            
            1  CAFFE LATTE                     40.000
            1  CROISSANT                       25.000
            
            SUBTOTAL                           65.000
            TAX 10%                             6.500
            TOTAL                              71.500
            CASH                              100.000
            CHANGE                             28.500
        """.trimIndent()

        val result = DeterministicReceiptParser.parse(rawReceiptText)

        assertEquals(2, result.items.size)
        assertEquals(65000.0, result.subtotal, 0.01)
        assertEquals(6500.0, result.tax, 0.01)
        assertEquals(71500.0, result.totalAmount, 0.01)
        assertEquals(TaxTreatment.EXCLUSIVE, result.taxTreatment)
    }
}
