package com.example.util

import com.example.data.model.DomainCurrency
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

class EmptyInputException(message: String) : IllegalArgumentException(message)
class NegativeAmountNotAllowedException(message: String) : IllegalArgumentException(message)
class InvalidNumberException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
class InvalidDigitException(message: String) : IllegalArgumentException(message)
class MalformedGroupingException(message: String) : IllegalArgumentException(message)
class MalformedDecimalException(message: String) : IllegalArgumentException(message)
class IllegalCharacterException(message: String) : IllegalArgumentException(message)
class FractionalScaleExceededException(message: String) : IllegalArgumentException(message)

/**
 * State-machine monetary input parser enforcing:
 * 1. Strict Unicode Nd digit validation (rejects non-Nd digits).
 * 2. Locale-specific grouping separation (rejects consecutive, leading, trailing, or misplaced grouping separators).
 * 3. Standard 3-digit and Indian (3-2-2) grouping verification.
 * 4. Maximum fractional scale bounded by target DomainCurrency decimalDigits.
 * 5. Configurable operation-specific negative sign policy.
 */
object MoneyInputParser {

    fun parse(
        input: String,
        currency: DomainCurrency,
        locale: Locale,
        allowNegative: Boolean = false
    ): Result<BigDecimal> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Result.failure(EmptyInputException("Input cannot be empty"))

        val symbols = DecimalFormatSymbols.getInstance(locale)
        val groupingSep = symbols.groupingSeparator
        val decimalSep = symbols.decimalSeparator

        var offset = 0
        var isNegative = false

        // Check optional leading sign
        val firstChar = trimmed[0]
        if (firstChar == '-' || firstChar == '+') {
            if (firstChar == '-') {
                if (!allowNegative) {
                    return Result.failure(NegativeAmountNotAllowedException("Negative amounts are not allowed for this operation"))
                }
                isNegative = true
            }
            offset++
            if (offset >= trimmed.length) {
                return Result.failure(InvalidNumberException("Input contains only sign character"))
            }
        }

        val integerPartDigits = StringBuilder()
        val fractionalDigits = StringBuilder()
        var decimalEncountered = false

        val groupLengths = mutableListOf<Int>()
        var currentGroupLen = 0
        var hasGroupingSeparators = false

        while (offset < trimmed.length) {
            val codePoint = trimmed.codePointAt(offset)
            val charCount = Character.charCount(codePoint)
            offset += charCount

            // 1. Strict Unicode Category Nd validation
            if (Character.getType(codePoint) == Character.DECIMAL_DIGIT_NUMBER.toInt()) {
                val digitValue = Character.digit(codePoint, 10)
                if (digitValue == -1) {
                    return Result.failure(InvalidDigitException("Code point 0x${Integer.toHexString(codePoint)} is not a radix-10 digit"))
                }
                val asciiDigit = ('0'.code + digitValue).toChar()

                if (!decimalEncountered) {
                    integerPartDigits.append(asciiDigit)
                    currentGroupLen++
                } else {
                    fractionalDigits.append(asciiDigit)
                }
            } else if (charCount == 1) {
                val c = codePoint.toChar()
                val isSpaceGrouping = (groupingSep == ' ' || groupingSep == '\u00A0' || groupingSep == '\u202F') &&
                        (c == ' ' || c == '\u00A0' || c == '\u202F')

                if (c == groupingSep || isSpaceGrouping) {
                    if (decimalEncountered) {
                        return Result.failure(MalformedGroupingException("Grouping separator cannot appear after decimal separator"))
                    }
                    if (currentGroupLen == 0) {
                        return Result.failure(MalformedGroupingException("Consecutive or leading grouping separator detected"))
                    }
                    groupLengths.add(currentGroupLen)
                    currentGroupLen = 0
                    hasGroupingSeparators = true
                } else if (c == decimalSep) {
                    if (decimalEncountered) {
                        return Result.failure(MalformedDecimalException("Multiple decimal separators encountered"))
                    }
                    if (hasGroupingSeparators && currentGroupLen == 0) {
                        return Result.failure(MalformedGroupingException("Trailing grouping separator immediately before decimal"))
                    }
                    if (hasGroupingSeparators) {
                        groupLengths.add(currentGroupLen)
                        currentGroupLen = 0
                    }
                    decimalEncountered = true
                } else {
                    return Result.failure(IllegalCharacterException("Disallowed character: '$c' (U+${Integer.toHexString(codePoint)})"))
                }
            } else {
                return Result.failure(IllegalCharacterException("Supplementary non-digit code point: U+${Integer.toHexString(codePoint)}"))
            }
        }

        // Final group check after iteration finishes
        if (!decimalEncountered && hasGroupingSeparators) {
            if (currentGroupLen == 0) {
                return Result.failure(MalformedGroupingException("Trailing grouping separator at end of input"))
            }
            groupLengths.add(currentGroupLen)
        }

        // Validate grouping pattern integrity if grouping separators were used
        if (hasGroupingSeparators) {
            val isIndianLocale = locale.country == "IN"
            if (isIndianLocale) {
                val lastGroup = groupLengths.last()
                if (lastGroup != 3) {
                    return Result.failure(MalformedGroupingException("Indian currency grouping requires last group to have exactly 3 digits, found $lastGroup"))
                }
                for (i in 1 until groupLengths.size - 1) {
                    if (groupLengths[i] != 2) {
                        return Result.failure(MalformedGroupingException("Indian currency grouping requires intermediate groups of 2 digits, found ${groupLengths[i]}"))
                    }
                }
                if (groupLengths.first() !in 1..2) {
                    return Result.failure(MalformedGroupingException("Indian currency grouping first group must be 1 or 2 digits, found ${groupLengths.first()}"))
                }
            } else {
                for (i in 1 until groupLengths.size) {
                    if (groupLengths[i] != 3) {
                        return Result.failure(MalformedGroupingException("Standard grouping requires 3 digits per group, found ${groupLengths[i]} at group $i"))
                    }
                }
                if (groupLengths.first() !in 1..3) {
                    return Result.failure(MalformedGroupingException("First group must contain 1 to 3 digits, found ${groupLengths.first()}"))
                }
            }
        }

        if (fractionalDigits.length > currency.decimalDigits) {
            return Result.failure(FractionalScaleExceededException(
                "Currency ${currency.code} allows at most ${currency.decimalDigits} decimal places, found ${fractionalDigits.length}"
            ))
        }

        if (integerPartDigits.isEmpty() && fractionalDigits.isEmpty()) {
            return Result.failure(InvalidNumberException("No digits found in input"))
        }

        val rawNumberString = StringBuilder().apply {
            if (isNegative) append('-')
            append(if (integerPartDigits.isNotEmpty()) integerPartDigits.toString() else "0")
            if (fractionalDigits.isNotEmpty()) {
                append('.')
                append(fractionalDigits.toString())
            }
        }.toString()

        return try {
            Result.success(BigDecimal(rawNumberString))
        } catch (e: NumberFormatException) {
            Result.failure(InvalidNumberException("Cannot parse sanitized string into BigDecimal", e))
        }
    }
}
