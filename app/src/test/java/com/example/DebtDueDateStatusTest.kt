package com.example

import com.example.util.InstallmentDateHelper
import com.example.util.InstallmentDateHelper.DueDateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests verifying Defect 1:
 * Complete date evaluation (year, month, day) for overdue debt calculations.
 * Ensures future dates (e.g. 1 April 2027) are never falsely flagged as overdue on 5 November 2026,
 * and today's due date is NOT overdue.
 */
class DebtDueDateStatusTest {

    private fun createCalendar(year: Int, month1Based: Int, day: Int): Calendar {
        return Calendar.getInstance().apply {
            clear()
            set(year, month1Based - 1, day, 12, 0, 0)
        }
    }

    @Test
    fun testFutureDateNextYear_isNotOverdue() {
        // Given today is 5 November 2026
        val today = createCalendar(2026, 11, 5)

        // Bill due 1 April 2027 (even though day 1 < today 5)
        val status = InstallmentDateHelper.evaluateDueDateStatus("1 April 2027", isPaid = false, today = today)
        assertEquals(DueDateStatus.BELUM_BAYAR, status)
        assertFalse(InstallmentDateHelper.isOverdue("1 April 2027", today))

        // Also test ISO format
        val isoStatus = InstallmentDateHelper.evaluateDueDateStatus("2027-04-01", isPaid = false, today = today)
        assertEquals(DueDateStatus.BELUM_BAYAR, isoStatus)
        assertFalse(InstallmentDateHelper.isOverdue("2027-04-01", today))
    }

    @Test
    fun testFutureDateNextMonth_isNotOverdue() {
        // Today is 31 December 2026
        val today = createCalendar(2026, 12, 31)

        // Bill due 1 January 2027
        val status = InstallmentDateHelper.evaluateDueDateStatus("1 Januari 2027", isPaid = false, today = today)
        assertEquals(DueDateStatus.BELUM_BAYAR, status)
        assertFalse(InstallmentDateHelper.isOverdue("1 Januari 2027", today))
    }

    @Test
    fun testPastDatePreviousMonth_isOverdue() {
        // Today is 1 December 2026
        val today = createCalendar(2026, 12, 1)

        // Bill was due 30 November 2026
        val status = InstallmentDateHelper.evaluateDueDateStatus("30 November 2026", isPaid = false, today = today)
        assertEquals(DueDateStatus.TERLAMBAT, status)
        assertTrue(InstallmentDateHelper.isOverdue("30 November 2026", today))
    }

    @Test
    fun testDueToday_isNotOverdue() {
        // Today is 5 November 2026
        val today = createCalendar(2026, 11, 5)

        // Bill due TODAY 5 November 2026 ("hari H jatuh tempo BELUM terlambat")
        val status = InstallmentDateHelper.evaluateDueDateStatus("5 November 2026", isPaid = false, today = today)
        assertEquals(DueDateStatus.BELUM_BAYAR, status)
        assertFalse(InstallmentDateHelper.isOverdue("5 November 2026", today))

        val isoStatus = InstallmentDateHelper.evaluateDueDateStatus("2026-11-05", isPaid = false, today = today)
        assertEquals(DueDateStatus.BELUM_BAYAR, isoStatus)
        assertFalse(InstallmentDateHelper.isOverdue("2026-11-05", today))
    }

    @Test
    fun testDueYesterday_isOverdue() {
        // Today is 5 November 2026
        val today = createCalendar(2026, 11, 5)

        // Bill due yesterday 4 November 2026
        val status = InstallmentDateHelper.evaluateDueDateStatus("4 November 2026", isPaid = false, today = today)
        assertEquals(DueDateStatus.TERLAMBAT, status)
        assertTrue(InstallmentDateHelper.isOverdue("4 November 2026", today))
    }

    @Test
    fun testPaidBill_isAlwaysLunas_neverOverdue() {
        val today = createCalendar(2026, 11, 5)

        // Even with a past date, if isPaid == true, it is LUNAS
        val status = InstallmentDateHelper.evaluateDueDateStatus("1 Januari 2025", isPaid = true, today = today)
        assertEquals(DueDateStatus.LUNAS, status)
        assertFalse(InstallmentDateHelper.isOverdue("1 Januari 2025", isPaid = true, today = today))

        // String "Lunas"
        val statusStr = InstallmentDateHelper.evaluateDueDateStatus("Lunas", isPaid = false, today = today)
        assertEquals(DueDateStatus.LUNAS, statusStr)
        assertFalse(InstallmentDateHelper.isOverdue("Lunas", today))
    }

    @Test
    fun testRelativeMonthlyAnchor_evaluatedSafely() {
        // Today is 5th of the month
        val today5th = createCalendar(2026, 11, 5)
        val statusBefore = InstallmentDateHelper.evaluateDueDateStatus("Tgl 15 tiap bulan", isPaid = false, today = today5th)
        assertEquals(DueDateStatus.BELUM_BAYAR, statusBefore)
        assertFalse(InstallmentDateHelper.isOverdue("Tgl 15 tiap bulan", today5th))

        // Today is 20th of the month
        val today20th = createCalendar(2026, 11, 20)
        val statusAfter = InstallmentDateHelper.evaluateDueDateStatus("Tgl 15 tiap bulan", isPaid = false, today = today20th)
        assertEquals(DueDateStatus.TERLAMBAT, statusAfter)
        assertTrue(InstallmentDateHelper.isOverdue("Tgl 15 tiap bulan", today20th))
    }

    @Test
    fun testUnparseableOrBlank_handledSafelyWithoutFalseOverdue() {
        val today = createCalendar(2026, 11, 5)

        assertEquals(DueDateStatus.TIDAK_DIKETAHUI, InstallmentDateHelper.evaluateDueDateStatus("", false, today))
        assertFalse(InstallmentDateHelper.isOverdue("", today))

        assertEquals(DueDateStatus.TIDAK_DIKETAHUI, InstallmentDateHelper.evaluateDueDateStatus(null, false, today))
        assertFalse(InstallmentDateHelper.isOverdue(null, today))
    }

    @Test
    fun testCalculateDaysUntilDue_positiveForFuture_zeroForToday_negativeForPast() {
        val today = createCalendar(2026, 11, 5)

        val daysFuture = InstallmentDateHelper.calculateDaysUntilDue("2026-11-10", today)
        assertEquals(5, daysFuture)

        val daysToday = InstallmentDateHelper.calculateDaysUntilDue("2026-11-05", today)
        assertEquals(0, daysToday)

        val daysPast = InstallmentDateHelper.calculateDaysUntilDue("2026-11-03", today)
        assertEquals(-2, daysPast)
    }

    @Test
    fun testLeapYearDates() {
        assertTrue(InstallmentDateHelper.isValidDate(2028, 2, 29))
        assertFalse(InstallmentDateHelper.isValidDate(2027, 2, 29))
    }
}
