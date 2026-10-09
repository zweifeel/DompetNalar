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
 */
object InstallmentDateHelper {

    private val supportedDateFormats = listOf(
        "dd-MM-yyyy",
        "yyyy-MM-dd",
        "dd/MM/yyyy",
        "yyyy/MM/dd",
        "d MMMM yyyy"
    )

    /**
     * Extracts the day of month number (1..31) from a string.
     */
    fun extractDayOfMonth(dateStr: String, fallback: Int = 15): Int {
        val regex = Regex("\\b([1-9]|[12][0-9]|3[01])\\b")
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
            // If dateStr is relative like "Tgl 15 tiap bulan", use current year and month
            val day = extractDayOfMonth(currentDateStr, anchorDay)
            cal.set(Calendar.DAY_OF_MONTH, day.coerceIn(1, cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
        }

        // Advance month
        cal.add(Calendar.MONTH, monthsToAdd)

        // Clamp to maximum days in the target month (handles February, leap years, etc.)
        val maxDaysInTargetMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val targetDay = if (anchorDay in 1..31) {
            anchorDay.coerceAtMost(maxDaysInTargetMonth)
        } else {
            cal.get(Calendar.DAY_OF_MONTH).coerceAtMost(maxDaysInTargetMonth)
        }
        cal.set(Calendar.DAY_OF_MONTH, targetDay)

        val outputFormat = SimpleDateFormat("dd-MM-yyyy", Locale("id", "ID"))
        return outputFormat.format(cal.time)
    }

    /**
     * Parses date string across multiple formats safely.
     */
    fun parseDateSafely(str: String): Date? {
        if (str.isBlank()) return null
        val trimmed = str.trim()

        for (pattern in supportedDateFormats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale("id", "ID")).apply {
                    isLenient = false
                }
                return sdf.parse(trimmed)
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Calculates days until due from today (negative indicates overdue).
     */
    fun calculateDaysUntilDue(dueDateStr: String, today: Calendar = Calendar.getInstance()): Int {
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

        // Fallback for relative strings like "Tgl 15 tiap bulan"
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
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale("id", "ID"))
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
     * Returns true if due date has passed.
     */
    fun isOverdue(dueDateStr: String, today: Calendar = Calendar.getInstance()): Boolean {
        val parsed = parseDateSafely(dueDateStr)
        if (parsed != null) {
            val dueCal = Calendar.getInstance().apply {
                time = parsed
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }
            return today.after(dueCal)
        }
        // Fallback for relative strings like "Tgl 5 tiap bulan"
        val dayNum = extractDayOfMonth(dueDateStr, -1)
        if (dayNum != -1) {
            val currentDay = today.get(Calendar.DAY_OF_MONTH)
            return currentDay > dayNum
        }
        return false
    }

    fun isOverdue(dueDateStr: String, currentDay: Int): Boolean {
        val today = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, currentDay)
        }
        return isOverdue(dueDateStr, today)
    }
}
