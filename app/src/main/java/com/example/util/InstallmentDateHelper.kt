package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Calendar and installment schedule calculation engine.
 * Handles:
 * - Next due date progression across months and years
 * - Safe clamping for shorter months (e.g. 28/29 Feb, 30 Apr)
 * - Anchor day preservation (preventing due day drift down to 28)
 * - Overdue and remaining days calculation
 * - Gregorian calendar strict validation (leap years, month days)
 * - Bilingual (ID/EN) structured date parsing for on-device OCR
 */
object InstallmentDateHelper {

    data class StructuredDueDateResult(
        val day: Int,
        val month: Int, // 1..12
        val year: Int,
        val isoDate: String, // YYYY-MM-DD (empty if ambiguous)
        val isAmbiguous: Boolean = false,
        val rawMatched: String = "",
        val confidenceStatus: String = if (isAmbiguous) "NEEDS_USER_CONFIRMATION" else "VERIFIED"
    )

    private val supportedDateFormats = listOf(
        "yyyy-MM-dd",
        "dd-MM-yyyy",
        "dd/MM/yyyy",
        "yyyy/MM/dd",
        "d MMMM yyyy",
        "d MMM yyyy"
    )

    val MONTH_MAP: Map<String, Int> = mapOf(
        "januari" to 1, "january" to 1, "jan" to 1,
        "februari" to 2, "february" to 2, "feb" to 2,
        "maret" to 3, "march" to 3, "mar" to 3,
        "april" to 4, "apr" to 4,
        "mei" to 5, "may" to 5,
        "juni" to 6, "june" to 6, "jun" to 6,
        "juli" to 7, "july" to 7, "jul" to 7,
        "agustus" to 8, "august" to 8, "agu" to 8, "ags" to 8, "agt" to 8, "aug" to 8,
        "september" to 9, "sept" to 9, "sep" to 9,
        "oktober" to 10, "october" to 10, "okt" to 10, "oct" to 10,
        "november" to 11, "nov" to 11,
        "desember" to 12, "december" to 12, "des" to 12, "dec" to 12
    )

    /**
     * Checks if a specific Gregorian calendar date is valid, taking into account
     * leap years for February (29th) and correct max days per month (30 for Apr, Jun, Sep, Nov).
     */
    fun isValidDate(year: Int, month: Int, day: Int): Boolean {
        if (year < 1000 || year > 9999 || month !in 1..12 || day < 1) return false
        val cal = Calendar.getInstance().apply {
            isLenient = false
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
        }
        return try {
            cal.time
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Strictly validates whether a date string is a valid ISO-8601 calendar date (YYYY-MM-DD).
     * Enforces Gregorian calendar leap-year rules and rejects non-existent dates
     * (e.g. 2026-02-29, 2026-04-31, 2026-13-01).
     */
    fun isValidIsoDate(dateStr: String?): Boolean {
        if (dateStr == null || !dateStr.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) return false
        val parts = dateStr.split("-")
        val year = parts[0].toIntOrNull() ?: return false
        val month = parts[1].toIntOrNull() ?: return false
        val day = parts[2].toIntOrNull() ?: return false
        return isValidDate(year, month, day)
    }

    /**
     * Parses structured calendar date from text in Indonesian and English formats.
     * Examples:
     * - "7 Desember 2026", "7 Des 2026", "13 Nov 2026", "27 October 2026"
     * - "2026-12-07"
     * - "07/12/2026", "07-12-2026"
     * Flags ambiguous strings without year (e.g. "7 Des", "Due 07/12") as NEEDS_USER_CONFIRMATION.
     */
    fun parseStructuredDueDate(rawText: String?): StructuredDueDateResult? {
        if (rawText.isNullOrBlank()) return null
        val clean = rawText.trim()

        // 1. ISO format: YYYY-MM-DD
        val isoMatch = Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""").find(clean)
        if (isoMatch != null) {
            val year = isoMatch.groupValues[1].toInt()
            val month = isoMatch.groupValues[2].toInt()
            val day = isoMatch.groupValues[3].toInt()
            if (isValidDate(year, month, day)) {
                val isoStr = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)
                return StructuredDueDateResult(
                    day = day,
                    month = month,
                    year = year,
                    isoDate = isoStr,
                    isAmbiguous = false,
                    rawMatched = isoMatch.value
                )
            }
        }

        // 2. Full Date with Month Name: "7 Desember 2026", "13 Nov 2026", "27 October 2026"
        val monthNameRegex = Regex(
            """\b(\d{1,2})\s+([a-zA-Z]{3,10})\s+(\d{4})\b"""
        )
        val mMatch = monthNameRegex.find(clean)
        if (mMatch != null) {
            val day = mMatch.groupValues[1].toInt()
            val monthStr = mMatch.groupValues[2].lowercase(Locale.ROOT)
            val year = mMatch.groupValues[3].toInt()
            val month = MONTH_MAP[monthStr]
            if (month != null && isValidDate(year, month, day)) {
                val isoStr = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)
                return StructuredDueDateResult(
                    day = day,
                    month = month,
                    year = year,
                    isoDate = isoStr,
                    isAmbiguous = false,
                    rawMatched = mMatch.value
                )
            }
        }

        // 3. DD/MM/YYYY or DD-MM-YYYY
        val dmyRegex = Regex("""\b(\d{1,2})[-/](\d{1,2})[-/](\d{4})\b""")
        val dmyMatch = dmyRegex.find(clean)
        if (dmyMatch != null) {
            val day = dmyMatch.groupValues[1].toInt()
            val month = dmyMatch.groupValues[2].toInt()
            val year = dmyMatch.groupValues[3].toInt()
            if (isValidDate(year, month, day)) {
                val isoStr = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)
                return StructuredDueDateResult(
                    day = day,
                    month = month,
                    year = year,
                    isoDate = isoStr,
                    isAmbiguous = false,
                    rawMatched = dmyMatch.value
                )
            }
        }

        // 4. Ambiguous: Month and Year only (e.g. "November 2026", "Desember 2026")
        val monthYearRegex = Regex("""\b([a-zA-Z]{3,10})\s+(\d{4})\b""")
        val myMatch = monthYearRegex.find(clean)
        if (myMatch != null) {
            val monthStr = myMatch.groupValues[1].lowercase(Locale.ROOT)
            val year = myMatch.groupValues[2].toInt()
            val month = MONTH_MAP[monthStr]
            if (month != null) {
                return StructuredDueDateResult(
                    day = 0,
                    month = month,
                    year = year,
                    isoDate = "",
                    isAmbiguous = true,
                    rawMatched = myMatch.value,
                    confidenceStatus = "NEEDS_USER_CONFIRMATION"
                )
            }
        }

        // 5. Ambiguous: Day and Month without Year (e.g. "7 Des", "13 Nov", "Due 07/12")
        val dayMonthRegex = Regex("""\b(?:due|jatuh tempo)?\s*(\d{1,2})\s*([a-zA-Z]{3,10})\b""", RegexOption.IGNORE_CASE)
        val dmMatch = dayMonthRegex.find(clean)
        if (dmMatch != null) {
            val day = dmMatch.groupValues[1].toInt()
            val monthStr = dmMatch.groupValues[2].lowercase(Locale.ROOT)
            val month = MONTH_MAP[monthStr]
            if (month != null && day in 1..31) {
                return StructuredDueDateResult(
                    day = day,
                    month = month,
                    year = 0,
                    isoDate = "",
                    isAmbiguous = true,
                    rawMatched = dmMatch.value,
                    confidenceStatus = "NEEDS_USER_CONFIRMATION"
                )
            }
        }

        val slashDmRegex = Regex("""\b(?:due|jatuh tempo)?[:\s]*(\d{1,2})/(\d{1,2})\b""", RegexOption.IGNORE_CASE)
        val sdmMatch = slashDmRegex.find(clean)
        if (sdmMatch != null) {
            val day = sdmMatch.groupValues[1].toInt()
            val month = sdmMatch.groupValues[2].toInt()
            if (month in 1..12 && day in 1..31) {
                return StructuredDueDateResult(
                    day = day,
                    month = month,
                    year = 0,
                    isoDate = "",
                    isAmbiguous = true,
                    rawMatched = sdmMatch.value,
                    confidenceStatus = "NEEDS_USER_CONFIRMATION"
                )
            }
        }

        return null
    }

    /**
     * Extracts the day of month number (1..31) from a string.
     */
    fun extractDayOfMonth(dateStr: String, fallback: Int = 15): Int {
        val parsed = parseStructuredDueDate(dateStr)
        if (parsed != null && parsed.day in 1..31) {
            return parsed.day
        }
        val regex = Regex("""\b([1-9]|[12][0-9]|3[01])\b""")
        return regex.find(dateStr)?.value?.toIntOrNull() ?: fallback
    }

    /**
     * Calculates the next due date after advancing by [monthsToAdd] months.
     * Preserves the [anchorDay] (e.g. 31st or 15th) by clamping to the target month's maximum days,
     * without permanently losing the anchor day.
     */
    fun calculateNextDueDate(
        currentDateStr: String,
        anchorDay: Int = 15,
        monthsToAdd: Int = 1,
        referenceCalendar: Calendar? = null
    ): String {
        val cal = referenceCalendar?.clone() as? Calendar ?: Calendar.getInstance()

        val parsedDate = parseDateSafely(currentDateStr)
        if (parsedDate != null) {
            cal.time = parsedDate
        } else {
            val day = extractDayOfMonth(currentDateStr, anchorDay)
            cal.set(Calendar.DAY_OF_MONTH, day.coerceIn(1, cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
        }

        cal.add(Calendar.MONTH, monthsToAdd)

        val maxDaysInTargetMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val targetDay = if (anchorDay in 1..31) {
            anchorDay.coerceAtMost(maxDaysInTargetMonth)
        } else {
            cal.get(Calendar.DAY_OF_MONTH).coerceAtMost(maxDaysInTargetMonth)
        }
        cal.set(Calendar.DAY_OF_MONTH, targetDay)

        val outputPattern = if (currentDateStr.contains("-") && currentDateStr.substringBefore("-").length == 2) {
            "dd-MM-yyyy"
        } else {
            "yyyy-MM-dd"
        }
        val outputFormat = SimpleDateFormat(outputPattern, Locale.ROOT)
        return outputFormat.format(cal.time)
    }

    enum class DueDateStatus {
        LUNAS,
        TERLAMBAT,
        BELUM_BAYAR,
        TIDAK_DIKETAHUI
    }

    /**
     * Parses date string across multiple formats safely.
     * Supports structured bilingual dates (e.g. '1 April 2027', '13 Nov 2026')
     * as well as standard ISO, slash, and hyphen formats.
     */
    fun parseDateSafely(str: String?): Date? {
        if (str.isNullOrBlank()) return null
        val trimmed = str.trim()

        // 1. Check structured parser first (handles bilingual months and prefix text)
        val structured = parseStructuredDueDate(trimmed)
        if (structured != null && structured.year > 0 && structured.month in 1..12 && structured.day in 1..31) {
            val cal = Calendar.getInstance().apply {
                isLenient = false
                clear()
                set(structured.year, structured.month - 1, structured.day)
            }
            try {
                return cal.time
            } catch (_: Exception) {}
        }

        // 2. Direct format patterns
        for (pattern in supportedDateFormats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale("id", "ID")).apply {
                    isLenient = false
                }
                return sdf.parse(trimmed)
            } catch (_: Exception) {}
            try {
                val sdfEn = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                }
                return sdfEn.parse(trimmed)
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Calculates days until due from today (negative indicates overdue).
     * Compares full calendar date (year, month, day).
     */
    fun calculateDaysUntilDue(dueDateStr: String?, today: Calendar = Calendar.getInstance()): Int {
        if (dueDateStr.isNullOrBlank()) return 0
        val parsed = parseDateSafely(dueDateStr)
        if (parsed != null) {
            val dueCal = Calendar.getInstance().apply {
                time = parsed
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val todayZero = (today.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val diffMillis = dueCal.timeInMillis - todayZero.timeInMillis
            return (diffMillis / (1000 * 60 * 60 * 24)).toInt()
        }

        val dayNum = extractDayOfMonth(dueDateStr, 15)
        val currentDay = today.get(Calendar.DAY_OF_MONTH)
        val maxDays = today.getActualMaximum(Calendar.DAY_OF_MONTH)

        return if (dayNum >= currentDay) {
            dayNum - currentDay
        } else {
            (maxDays - currentDay) + dayNum
        }
    }

    fun formatIsoDate(calendar: Calendar = Calendar.getInstance()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        return sdf.format(calendar.time)
    }

    fun getNextMonthCalendar(calendar: Calendar, anchorDay: Int = 15): Calendar {
        val cal = calendar.clone() as Calendar
        cal.add(Calendar.MONTH, 1)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val targetDay = anchorDay.coerceIn(1, maxDays)
        cal.set(Calendar.DAY_OF_MONTH, targetDay)
        return cal
    }

    /**
     * Evaluates complete due date status:
     * - LUNAS if isPaid == true or dueDate is 'lunas'/'paid'
     * - TERLAMBAT if !isPaid AND full dueDate < today (strictly before today's calendar date)
     * - BELUM_BAYAR if !isPaid AND full dueDate >= today (hari H jatuh tempo BELUM terlambat)
     * - TIDAK_DIKETAHUI if dueDate is blank
     * Never falsely marks future months/years as overdue.
     */
    fun evaluateDueDateStatus(
        dueDateStr: String?,
        isPaid: Boolean = false,
        today: Calendar = Calendar.getInstance()
    ): DueDateStatus {
        if (isPaid) return DueDateStatus.LUNAS
        if (dueDateStr.isNullOrBlank()) return DueDateStatus.TIDAK_DIKETAHUI

        val clean = dueDateStr.trim()
        if (clean.equals("lunas", ignoreCase = true) || clean.equals("paid", ignoreCase = true)) {
            return DueDateStatus.LUNAS
        }

        val parsedDate = parseDateSafely(clean)
        if (parsedDate != null) {
            val dueCal = Calendar.getInstance().apply {
                time = parsedDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val todayZero = (today.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return if (dueCal.before(todayZero)) {
                DueDateStatus.TERLAMBAT
            } else {
                DueDateStatus.BELUM_BAYAR
            }
        }

        // Fallback for purely relative recurring strings like 'Tgl 15 tiap bulan'
        val dayNum = extractDayOfMonth(clean, -1)
        if (dayNum in 1..31) {
            val currentDay = today.get(Calendar.DAY_OF_MONTH)
            return if (currentDay > dayNum) {
                DueDateStatus.TERLAMBAT
            } else {
                DueDateStatus.BELUM_BAYAR
            }
        }

        return DueDateStatus.BELUM_BAYAR
    }

    /**
     * Returns true if due date has passed (strictly before today).
     * Due date on today itself is NOT overdue.
     */
    fun isOverdue(dueDateStr: String?, today: Calendar = Calendar.getInstance()): Boolean {
        return evaluateDueDateStatus(dueDateStr, isPaid = false, today = today) == DueDateStatus.TERLAMBAT
    }

    fun isOverdue(dueDateStr: String?, isPaid: Boolean, today: Calendar = Calendar.getInstance()): Boolean {
        return evaluateDueDateStatus(dueDateStr, isPaid = isPaid, today = today) == DueDateStatus.TERLAMBAT
    }

    fun isOverdue(dueDateStr: String?, currentDay: Int): Boolean {
        val today = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, currentDay)
        }
        return isOverdue(dueDateStr, today)
    }
}
