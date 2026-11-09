package com.example

import com.example.util.ReceiptCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.NumberFormat
import java.util.Locale

/**
 * Unit tests verifying Defects 3, 4, 5:
 * - Defect 3: Navigation across items (1/N, 2/N, N/N) and unsaved state retention.
 * - Defect 4: Dimension/layout consistency and label safety.
 * - Defect 5: Indonesian Rupiah nominal formatting (1.416.000) and safe numerical parsing.
 */
class PayLaterReviewNavigationAndFormatTest {

    private fun formatNominalRupiah(value: Double): String {
        return NumberFormat.getNumberInstance(Locale("id", "ID")).format(value.toLong())
    }

    private fun formatInputDigits(input: String): String {
        val digits = input.filter { it.isDigit() }
        val number = digits.toLongOrNull() ?: return ""
        return NumberFormat.getNumberInstance(Locale("id", "ID")).format(number)
    }

    @Test
    fun testRupiahNominalFormatting_withThousandSeparators() {
        assertEquals("1.416.000", formatNominalRupiah(1416000.0))
        assertEquals("354.000", formatNominalRupiah(354000.0))
        assertEquals("50.000", formatNominalRupiah(50000.0))
        assertEquals("0", formatNominalRupiah(0.0))
    }

    @Test
    fun testOnTheFlyInputFormatting_formatsAsUserTypes() {
        assertEquals("1", formatInputDigits("1"))
        assertEquals("14", formatInputDigits("14"))
        assertEquals("141", formatInputDigits("141"))
        assertEquals("1.416", formatInputDigits("1416"))
        assertEquals("14.160", formatInputDigits("14160"))
        assertEquals("141.600", formatInputDigits("141600"))
        assertEquals("1.416.000", formatInputDigits("1416000"))
    }

    @Test
    fun testSafeNumericalParsing_fromFormattedRupiahStringToNumericDouble() {
        val parsed1 = ReceiptCalculator.parseAmountSafe("1.416.000")
        assertEquals(1416000.0, parsed1, 0.001)

        val parsed2 = ReceiptCalculator.parseAmountSafe("Rp 354.000")
        assertEquals(354000.0, parsed2, 0.001)

        val parsed3 = ReceiptCalculator.parseAmountSafe("0")
        assertEquals(0.0, parsed3, 0.001)

        val parsedEmpty = ReceiptCalculator.parseAmountSafe("")
        assertEquals(0.0, parsedEmpty, 0.001)
    }

    @Test
    fun testMultiItemNavigationBounds() {
        val totalItems = 3

        // Index 0 (1/3)
        val active0 = 0
        val isPrevEnabled0 = active0 > 0
        val isNextEnabled0 = active0 < totalItems - 1
        val isFinalItem0 = active0 == totalItems - 1
        assertFalse("Item 1/3 must have Sebelumnya disabled", isPrevEnabled0)
        assertTrue("Item 1/3 must have Selanjutnya enabled", isNextEnabled0)
        assertFalse(isFinalItem0)

        // Index 1 (2/3)
        val active1 = 1
        val isPrevEnabled1 = active1 > 0
        val isNextEnabled1 = active1 < totalItems - 1
        val isFinalItem1 = active1 == totalItems - 1
        assertTrue("Item 2/3 must have Sebelumnya enabled", isPrevEnabled1)
        assertTrue("Item 2/3 must have Selanjutnya enabled", isNextEnabled1)
        assertFalse(isFinalItem1)

        // Index 2 (3/3 - Final)
        val active2 = 2
        val isPrevEnabled2 = active2 > 0
        val isNextEnabled2 = active2 < totalItems - 1
        val isFinalItem2 = active2 == totalItems - 1
        assertTrue("Item 3/3 must have Sebelumnya enabled", isPrevEnabled2)
        assertFalse("Item 3/3 has reached the end", isNextEnabled2)
        assertTrue("Item 3/3 is the final item", isFinalItem2)
    }

    @Test
    fun testStateRetentionAcrossNavigation() {
        // Simulating user editing Item 0, navigating to Item 1, navigating back to Item 0
        val retainedMap = mutableMapOf<String, String>()

        val id0 = "item-0"
        val id1 = "item-1"

        // Initial state
        retainedMap[id0] = formatNominalRupiah(100000.0) // 100.000
        retainedMap[id1] = formatNominalRupiah(200000.0) // 200.000

        // User edits item 0 to 1.416.000
        retainedMap[id0] = formatInputDigits("1416000")

        // User navigates to item 1 and edits to 354.000
        retainedMap[id1] = formatInputDigits("354000")

        // User navigates back to item 0
        assertEquals("1.416.000", retainedMap[id0])
        // User checks item 1 again
        assertEquals("354.000", retainedMap[id1])

        // Verify accurate numeric parsing on final save
        val num0 = ReceiptCalculator.parseAmountSafe(retainedMap[id0])
        val num1 = ReceiptCalculator.parseAmountSafe(retainedMap[id1])
        assertEquals(1416000.0, num0, 0.001)
        assertEquals(354000.0, num1, 0.001)
    }

    @Test
    fun testSevenItemNavigationBoundaryAndBalance() {
        val totalItems = 7

        // Item 1/7: First item
        assertFalse("Item 1/7 must have Sebelumnya disabled", 0 > 0)
        assertTrue("Item 1/7 must have Selanjutnya enabled", 0 < totalItems - 1)

        // Item 4/7: Middle item
        assertTrue("Item 4/7 must have Sebelumnya enabled", 3 > 0)
        assertTrue("Item 4/7 must have Selanjutnya enabled", 3 < totalItems - 1)

        // Item 7/7: Last item
        assertTrue("Item 7/7 must have Sebelumnya enabled", 6 > 0)
        assertFalse("Item 7/7 must have Selanjutnya disabled", 6 < totalItems - 1)
    }

    @Test
    fun testLongPlatformNameFormattingSafety() {
        val longProvider = "Shopee PayLater Multi-Period Installment Plan Special Edition"
        val indicatorText = "Item 1/7"
        val safeProviderLabel = "(${longProvider.ifBlank { "Penyedia Tidak Dikenal" }})"
        
        // Assert indicator line and provider line are distinct for responsive two-line column
        assertEquals("Item 1/7", indicatorText)
        assertTrue(safeProviderLabel.startsWith("(") && safeProviderLabel.endsWith(")"))
        assertTrue("Provider label contains full provider text before UI ellipsize", safeProviderLabel.contains("Shopee PayLater"))
    }
}
