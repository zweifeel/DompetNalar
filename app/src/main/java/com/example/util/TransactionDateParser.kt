package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class ParsedDateResult(
    val timestamp: Long,
    val detectedDateText: String?,
    val isBackdated: Boolean,
    val cleanedText: String
)

object TransactionDateParser {

    private val monthNames = mapOf(
        "januari" to 0, "jan" to 0,
        "februari" to 1, "feb" to 1,
        "maret" to 2, "mar" to 2,
        "april" to 3, "apr" to 3,
        "mei" to 4,
        "juni" to 5, "jun" to 5,
        "juli" to 6, "jul" to 6,
        "agustus" to 7, "ags" to 7, "agt" to 7,
        "september" to 8, "sep" to 8, "sept" to 8,
        "oktober" to 9, "okt" to 9,
        "november" to 10, "nov" to 10,
        "desember" to 11, "des" to 11
    )

    fun parseDateFromMessage(rawMessage: String): ParsedDateResult {
        val lower = rawMessage.lowercase(Locale("id", "ID")).trim()
        val now = Calendar.getInstance()
        var targetCal: Calendar? = null
        var matchedPhrase: String? = null

        // 1. Relative Phrases
        // "kemarin lusa" / "2 hari lalu" / "2 hari yg lalu"
        val twoDaysAgoRegex = Pattern.compile("\\b(kemarin\\s+lusa|lusa\\s+kemarin|2\\s*hari\\s*(?:yang\\s*|yg\\s*)?lalu)\\b")
        val twoDaysMatcher = twoDaysAgoRegex.matcher(lower)
        if (twoDaysMatcher.find()) {
            targetCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -2) }
            matchedPhrase = twoDaysMatcher.group(1)
        }

        // "X hari lalu" (3 hari lalu, 5 hari yang lalu, dst)
        if (targetCal == null) {
            val daysAgoRegex = Pattern.compile("\\b(\\d+)\\s*hari\\s*(?:yang\\s*|yg\\s*)?lalu\\b")
            val daysMatcher = daysAgoRegex.matcher(lower)
            if (daysMatcher.find()) {
                val days = daysMatcher.group(1)?.toIntOrNull() ?: 1
                targetCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }
                matchedPhrase = daysMatcher.group(0)
            }
        }

        // "kemarin" / "kmrn"
        if (targetCal == null) {
            val yesterdayRegex = Pattern.compile("\\b(kemarin|kmrn)\\b")
            val yesterdayMatcher = yesterdayRegex.matcher(lower)
            if (yesterdayMatcher.find()) {
                targetCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                matchedPhrase = yesterdayMatcher.group(1)
            }
        }

        // "lusa" (contextually 2 days)
        if (targetCal == null) {
            val lusaRegex = Pattern.compile("\\blusa\\b")
            val lusaMatcher = lusaRegex.matcher(lower)
            if (lusaMatcher.find()) {
                // In Indonesian expense tracking context, "lusa" without modifier often means 2 days ago ("kemarin lusa")
                targetCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -2) }
                matchedPhrase = lusaMatcher.group(0)
            }
        }

        // "tadi pagi", "tadi siang", "tadi sore", "tadi malam", "subuh tadi"
        if (targetCal == null) {
            val todayTimeRegex = Pattern.compile("\\b(tadi\\s*pagi|tadi\\s*siang|tadi\\s*sore|tadi\\s*malam|subuh\\s*tadi)\\b")
            val todayTimeMatcher = todayTimeRegex.matcher(lower)
            if (todayTimeMatcher.find()) {
                val matched = todayTimeMatcher.group(1) ?: "tadi pagi"
                targetCal = Calendar.getInstance().apply {
                    when {
                        matched.contains("pagi") -> set(Calendar.HOUR_OF_DAY, 8)
                        matched.contains("siang") -> set(Calendar.HOUR_OF_DAY, 12)
                        matched.contains("sore") -> set(Calendar.HOUR_OF_DAY, 16)
                        matched.contains("malam") -> set(Calendar.HOUR_OF_DAY, 20)
                        matched.contains("subuh") -> set(Calendar.HOUR_OF_DAY, 5)
                    }
                    set(Calendar.MINUTE, 0)
                }
                matchedPhrase = todayTimeMatcher.group(1)
            }
        }

        // "minggu lalu" / "1 minggu lalu" / "seminggu lalu"
        if (targetCal == null) {
            val weekAgoRegex = Pattern.compile("\\b(?:1\\s*|se)?minggu\\s*(?:yang\\s*|yg\\s*)?lalu\\b")
            val weekMatcher = weekAgoRegex.matcher(lower)
            if (weekMatcher.find()) {
                targetCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
                matchedPhrase = weekMatcher.group(0)
            }
        }

        // "X minggu lalu"
        if (targetCal == null) {
            val weeksAgoRegex = Pattern.compile("\\b(\\d+)\\s*minggu\\s*(?:yang\\s*|yg\\s*)?lalu\\b")
            val weeksMatcher = weeksAgoRegex.matcher(lower)
            if (weeksMatcher.find()) {
                val weeks = weeksMatcher.group(1)?.toIntOrNull() ?: 1
                targetCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -weeks * 7) }
                matchedPhrase = weeksMatcher.group(0)
            }
        }

        // "bulan lalu" / "sebulan lalu"
        if (targetCal == null) {
            val monthAgoRegex = Pattern.compile("\\b(?:1\\s*|se)?bulan\\s*(?:yang\\s*|yg\\s*)?lalu\\b")
            val monthMatcher = monthAgoRegex.matcher(lower)
            if (monthMatcher.find()) {
                targetCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                matchedPhrase = monthMatcher.group(0)
            }
        }

        // 2. Specific Dates
        // Form: DD/MM/YYYY or DD-MM-YYYY or DD/MM
        if (targetCal == null) {
            val slashDateRegex = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b")
            val slashMatcher = slashDateRegex.matcher(lower)
            if (slashMatcher.find()) {
                val d = slashMatcher.group(1)?.toIntOrNull()
                val m = slashMatcher.group(2)?.toIntOrNull()
                val y = slashMatcher.group(3)?.toIntOrNull()
                if (d != null && m != null && d in 1..31 && m in 1..12) {
                    val year = when {
                        y == null -> now.get(Calendar.YEAR)
                        y < 100 -> 2000 + y
                        else -> y
                    }
                    targetCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, m - 1)
                        set(Calendar.DAY_OF_MONTH, d)
                    }
                    matchedPhrase = slashMatcher.group(0)
                }
            }
        }

        // Form: "tanggal 15 September 2026" or "15 September" or "tgl 15 September"
        if (targetCal == null) {
            val monthNamesJoined = monthNames.keys.sortedByDescending { it.length }.joinToString("|")
            val namedDateRegex = Pattern.compile("\\b(?:tanggal|tgl)?\\s*(\\d{1,2})\\s*($monthNamesJoined)(?:\\s*(\\d{4}))?\\b")
            val namedMatcher = namedDateRegex.matcher(lower)
            if (namedMatcher.find()) {
                val d = namedMatcher.group(1)?.toIntOrNull()
                val monthName = namedMatcher.group(2)
                val y = namedMatcher.group(3)?.toIntOrNull()
                val m = monthNames[monthName]
                if (d != null && m != null && d in 1..31) {
                    val year = y ?: now.get(Calendar.YEAR)
                    targetCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, m)
                        set(Calendar.DAY_OF_MONTH, d)
                    }
                    matchedPhrase = namedMatcher.group(0)
                }
            }
        }

        // Form: "tanggal 15" / "tgl 15" (implies current month)
        if (targetCal == null) {
            val tglOnlyRegex = Pattern.compile("\\b(?:tanggal|tgl)\\s*(\\d{1,2})\\b")
            val tglMatcher = tglOnlyRegex.matcher(lower)
            if (tglMatcher.find()) {
                val d = tglMatcher.group(1)?.toIntOrNull()
                if (d != null && d in 1..31) {
                    targetCal = Calendar.getInstance().apply {
                        set(Calendar.DAY_OF_MONTH, d)
                        // If date is in future of current month, might refer to previous month or current
                        if (timeInMillis > now.timeInMillis) {
                            add(Calendar.MONTH, -1)
                        }
                    }
                    matchedPhrase = tglMatcher.group(0)
                }
            }
        }

        val cleaned = if (matchedPhrase != null) {
            rawMessage.replace(Regex("(?i)\\b" + Regex.escape(matchedPhrase) + "\\b"), "")
                .replace(Regex("\\s{2,}"), " ")
                .trim()
        } else {
            rawMessage
        }

        return if (targetCal != null) {
            ParsedDateResult(
                timestamp = targetCal.timeInMillis,
                detectedDateText = matchedPhrase,
                isBackdated = true,
                cleanedText = cleaned
            )
        } else {
            ParsedDateResult(
                timestamp = System.currentTimeMillis(),
                detectedDateText = null,
                isBackdated = false,
                cleanedText = rawMessage
            )
        }
    }
}
