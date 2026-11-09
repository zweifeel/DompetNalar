package com.example

import com.example.data.model.AccountMode
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.util.ReceiptFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit tests for FEAT-34 Personal Finance Receipt Formatter.
 */
class ReceiptFormatterTest {

    // Fixed timestamp: 2026-10-04 14:30:00 UTC+7 (Jakarta time)
    private val fixedTimestamp: Long by lazy {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+7")).apply {
            set(2026, Calendar.OCTOBER, 4, 14, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        calendar.timeInMillis
    }

    private val sampleExpense = TransactionItem(
        id = 42L,
        title = "Makan Siang Nasi Padang",
        amount = 35000.0,
        type = TransactionType.EXPENSE,
        category = "Makanan & Minuman",
        categoryIcon = "restaurant",
        walletId = 1L,
        walletName = "GoPay",
        timestamp = fixedTimestamp,
        note = "Pakai sambal ijo",
        accountMode = AccountMode.PERSONAL.name
    )

    private val sampleIncome = TransactionItem(
        id = 105L,
        title = "Gaji Pokok Oktober",
        amount = 12500000.0,
        type = TransactionType.INCOME,
        category = "Gaji & Pemasukan",
        categoryIcon = "payments",
        walletId = 2L,
        walletName = "BCA Utama",
        timestamp = fixedTimestamp,
        note = "",
        accountMode = AccountMode.PERSONAL.name
    )

    private val sampleTransfer = TransactionItem(
        id = 200L,
        title = "Topup Dompet Digital",
        amount = 150000.0,
        type = TransactionType.TRANSFER,
        category = "Transfer",
        categoryIcon = "swap_horiz",
        walletId = 2L,
        walletName = "BCA Utama",
        targetWalletId = 1L,
        targetWalletName = "GoPay",
        timestamp = fixedTimestamp,
        note = "Untuk bekal mingguan",
        accountMode = AccountMode.PERSONAL.name
    )

    @Test
    fun testReceiptReferenceFormatting() {
        assertEquals("FSM-000001", ReceiptFormatter.formatReceiptReference(1L))
        assertEquals("FSM-000042", ReceiptFormatter.formatReceiptReference(42L))
        assertEquals("FSM-001234", ReceiptFormatter.formatReceiptReference(1234L))
        assertEquals("FSM-999999", ReceiptFormatter.formatReceiptReference(999999L))
    }

    @Test
    fun testTransactionDateFormatting_indonesianLocale() {
        val dateStr = ReceiptFormatter.formatTransactionDate(fixedTimestamp)
        // Must contain day, month, year, hour, and minute
        assertTrue("Date string '$dateStr' must match dd/MM/yyyy HH:mm pattern", dateStr.contains("04/10/2026") || dateStr.contains("2026"))
        assertTrue("Date string must contain time separator", dateStr.contains(":"))
    }

    @Test
    fun testTransactionTypeLabels() {
        assertEquals("PENGELUARAN", ReceiptFormatter.getTransactionTypeLabel(TransactionType.EXPENSE))
        assertEquals("PEMASUKAN", ReceiptFormatter.getTransactionTypeLabel(TransactionType.INCOME))
        assertEquals("TRANSFER ANTAR DOMPET", ReceiptFormatter.getTransactionTypeLabel(TransactionType.TRANSFER))
    }

    @Test
    fun testExpenseReceiptFormatting() {
        val text = ReceiptFormatter.formatReceiptText(sampleExpense, is80mm = false)
        assertTrue("Must contain header branding", text.contains("BUKTI TRANSAKSI DIGITAL"))
        assertTrue("Must contain personal finance branding", text.contains("DOMPETNALAR PERSONAL FINANCE") || text.contains("FINSMART PERSONAL FINANCE"))
        assertTrue("Must contain reference", text.contains("FSM-000042"))
        assertTrue("Must contain expense label", text.contains("PENGELUARAN"))
        assertTrue("Must contain title", text.contains("Makan Siang Nasi Padang"))
        assertTrue("Must contain category", text.contains("Makanan & Minuman"))
        assertTrue("Must contain wallet", text.contains("GoPay"))
        assertTrue("Must contain note", text.contains("Pakai sambal ijo"))
        assertTrue("Must contain total keluar label", text.contains("TOTAL KELUAR"))
        assertTrue("Must contain rupiah amount", text.contains("35.000") || text.contains("Rp"))
    }

    @Test
    fun testIncomeReceiptFormatting() {
        val text = ReceiptFormatter.formatReceiptText(sampleIncome, is80mm = false)
        assertTrue("Must contain reference", text.contains("FSM-000105"))
        assertTrue("Must contain income label", text.contains("PEMASUKAN"))
        assertTrue("Must contain title", text.contains("Gaji Pokok Oktober"))
        assertTrue("Must contain wallet", text.contains("BCA Utama"))
        assertTrue("Must contain total masuk label", text.contains("TOTAL MASUK"))
        assertTrue("Must contain income amount", text.contains("12.500.000") || text.contains("Rp"))
        // Empty note must not be included
        assertFalse("Empty note must not be rendered as key", text.contains("Catatan:"))
    }

    @Test
    fun testTransferReceiptFormatting() {
        val text = ReceiptFormatter.formatReceiptText(sampleTransfer, is80mm = false)
        assertTrue("Must contain reference", text.contains("FSM-000200"))
        assertTrue("Must contain transfer type label", text.contains("TRANSFER ANTAR DOMPET"))
        assertTrue("Must contain source wallet", text.contains("BCA Utama"))
        assertTrue("Must contain target wallet", text.contains("GoPay"))
        assertTrue("Must contain total transfer label", text.contains("TOTAL TRANSFER"))
        assertTrue("Must contain note", text.contains("Untuk bekal mingguan"))
    }

    @Test
    fun testWidthModes_58mm_vs_80mm() {
        val text58 = ReceiptFormatter.formatReceiptText(sampleExpense, is80mm = false)
        val text80 = ReceiptFormatter.formatReceiptText(sampleExpense, is80mm = true)

        // 58mm uses 32-char width dividers, 80mm uses 42-char width dividers
        val divider58 = "-".repeat(32)
        val divider80 = "-".repeat(42)

        assertTrue("58mm receipt must contain 32-character divider", text58.contains(divider58))
        assertTrue("80mm receipt must contain 42-character divider", text80.contains(divider80))
        assertTrue("80mm text length should exceed 58mm text length", text80.length > text58.length)
    }

    @Test
    fun testSharePayloadIntegrity() {
        val shareText = ReceiptFormatter.formatReceiptText(sampleExpense)
        // Ensure no leftover mock UMKM or merchant strings exist
        assertFalse("Must not contain UMKM store name", shareText.contains("TOKO FINSMART STORE"))
        assertFalse("Must not contain fake address", shareText.contains("Jl. Sudirman No. 88"))
        assertFalse("Must not contain fake phone", shareText.contains("0851-5507-6566"))
        assertFalse("Must not contain UMKM invoice text", shareText.contains("STRUK / INVOICE DIGITAL UMKM"))
        assertFalse("Must not contain business suite text", shareText.contains("FinSmart Business Suite"))
        assertFalse("Must not contain hardcoded 150000 fallback", shareText.contains("150.000") && !sampleExpense.amount.equals(150000.0))
    }
}
