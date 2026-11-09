package com.example.data.ocr

import com.example.data.ai.PayLaterAiOcrScanner
import com.example.util.InstallmentDateHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredDueDateParserTest {

    @Test
    fun testIndonesianMonthNamesAndAbbreviations() {
        val testCases = listOf(
            "7 Januari 2026" to "2026-01-07",
            "14 Feb 2026" to "2026-02-14",
            "20 Maret 2026" to "2026-03-20",
            "30 Apr 2026" to "2026-04-30",
            "1 Mei 2026" to "2026-05-01",
            "15 Juni 2026" to "2026-06-15",
            "22 Jul 2026" to "2026-07-22",
            "17 Agustus 2026" to "2026-08-17",
            "5 Agu 2026" to "2026-08-05",
            "10 Ags 2026" to "2026-08-10",
            "9 September 2026" to "2026-09-09",
            "25 Sep 2026" to "2026-09-25",
            "27 Oktober 2026" to "2026-10-27",
            "27 Okt 2026" to "2026-10-27",
            "13 November 2026" to "2026-11-13",
            "13 Nov 2026" to "2026-11-13",
            "7 Desember 2026" to "2026-12-07",
            "7 Des 2026" to "2026-12-07"
        )

        for ((input, expectedIso) in testCases) {
            val parsed = InstallmentDateHelper.parseStructuredDueDate(input)
            assertNotNull("Failed to parse: $input", parsed)
            assertEquals("ISO mismatch for $input", expectedIso, parsed!!.isoDate)
            assertFalse("Should not be ambiguous: $input", parsed.isAmbiguous)
            assertEquals("VERIFIED", parsed.confidenceStatus)
        }
    }

    @Test
    fun testEnglishMonthNamesAndAbbreviations() {
        val testCases = listOf(
            "Due date: 15 January 2027" to "2027-01-15",
            "Pay before 28 Feb 2027" to "2027-02-28",
            "10 March 2027" to "2027-03-10",
            "5 Apr 2027" to "2027-04-05",
            "25 May 2027" to "2027-05-25",
            "30 June 2027" to "2027-06-30",
            "4 July 2027" to "2027-07-04",
            "12 August 2027" to "2027-08-12",
            "18 Aug 2027" to "2027-08-18",
            "19 September 2027" to "2027-09-19",
            "22 Sept 2027" to "2027-09-22",
            "31 October 2027" to "2027-10-31",
            "31 Oct 2027" to "2027-10-31",
            "15 November 2027" to "2027-11-15",
            "25 December 2027" to "2027-12-25",
            "25 Dec 2027" to "2027-12-25"
        )

        for ((input, expectedIso) in testCases) {
            val parsed = InstallmentDateHelper.parseStructuredDueDate(input)
            assertNotNull("Failed to parse English date: $input", parsed)
            assertEquals("ISO mismatch for $input", expectedIso, parsed!!.isoDate)
            assertFalse(parsed.isAmbiguous)
        }
    }

    @Test
    fun testDatesCrossingYearBoundaries() {
        // Tagihanmu multi-tenor progression from Nov 2026 to Apr 2027
        val nov2026 = InstallmentDateHelper.parseStructuredDueDate("27 November 2026")
        val des2026 = InstallmentDateHelper.parseStructuredDueDate("27 Desember 2026")
        val jan2027 = InstallmentDateHelper.parseStructuredDueDate("27 Januari 2027")
        val feb2027 = InstallmentDateHelper.parseStructuredDueDate("27 Februari 2027")

        assertNotNull(nov2026)
        assertEquals("2026-11-27", nov2026!!.isoDate)

        assertNotNull(des2026)
        assertEquals("2026-12-27", des2026!!.isoDate)

        assertNotNull(jan2027)
        assertEquals("2027-01-27", jan2027!!.isoDate)

        assertNotNull(feb2027)
        assertEquals("2027-02-27", feb2027!!.isoDate)
    }

    @Test
    fun testGregorianCalendarStrictValidation_LeapYearAndMonthDays() {
        // Leap year 2024 -> 29 Feb is valid
        assertTrue(InstallmentDateHelper.isValidDate(2024, 2, 29))
        assertTrue(InstallmentDateHelper.isValidIsoDate("2024-02-29"))

        // Non-leap year 2025, 2026, 2027 -> 29 Feb is invalid
        assertFalse(InstallmentDateHelper.isValidDate(2025, 2, 29))
        assertFalse(InstallmentDateHelper.isValidIsoDate("2025-02-29"))
        assertFalse(InstallmentDateHelper.isValidDate(2026, 2, 29))
        assertFalse(InstallmentDateHelper.isValidIsoDate("2026-02-29"))

        // 31 April does not exist (April has 30 days)
        assertFalse(InstallmentDateHelper.isValidDate(2026, 4, 31))
        assertFalse(InstallmentDateHelper.isValidIsoDate("2026-04-31"))

        // 30 April exists
        assertTrue(InstallmentDateHelper.isValidDate(2026, 4, 30))
        assertTrue(InstallmentDateHelper.isValidIsoDate("2026-04-30"))

        // 31 June does not exist (June has 30 days)
        assertFalse(InstallmentDateHelper.isValidDate(2026, 6, 31))
    }

    @Test
    fun testAmbiguousDatesRequireConfirmation() {
        // Date without year: "7 Des", "13 Nov", "Due 07/12"
        val ambiguous1 = InstallmentDateHelper.parseStructuredDueDate("Jatuh tempo 7 Des")
        assertNotNull(ambiguous1)
        assertTrue("7 Des without year must be ambiguous", ambiguous1!!.isAmbiguous)
        assertEquals("NEEDS_USER_CONFIRMATION", ambiguous1.confidenceStatus)
        assertEquals("", ambiguous1.isoDate)
        assertEquals(7, ambiguous1.day)
        assertEquals(12, ambiguous1.month)

        val ambiguous2 = InstallmentDateHelper.parseStructuredDueDate("Due 07/12")
        assertNotNull(ambiguous2)
        assertTrue(ambiguous2!!.isAmbiguous)
        assertEquals("NEEDS_USER_CONFIRMATION", ambiguous2.confidenceStatus)

        // Month and Year only: "November 2026"
        val ambiguous3 = InstallmentDateHelper.parseStructuredDueDate("November 2026")
        assertNotNull(ambiguous3)
        assertTrue(ambiguous3!!.isAmbiguous)
        assertEquals("NEEDS_USER_CONFIRMATION", ambiguous3.confidenceStatus)
        assertEquals(11, ambiguous3.month)
        assertEquals(2026, ambiguous3.year)
        assertEquals(0, ambiguous3.day)
    }

    @Test
    fun testFullDateIsNotReducedToRecurringDayDescription() {
        // Required by Stage 2: Do NOT reduce "7 Des 2026" to "Tgl 7 tiap bulan"
        val (norm, day) = PayLaterAiOcrScanner.parseDueDateString("Jatuh tempo: 7 Des 2026")
        assertEquals("2026-12-07", norm)
        assertEquals(7, day)

        // Recurring description without specific calendar date should retain recurring text
        val (normRecur, dayRecur) = PayLaterAiOcrScanner.parseDueDateString("Tgl 15 tiap bulan")
        assertEquals("Tgl 15 tiap bulan", normRecur)
        assertEquals(15, dayRecur)
    }
}
