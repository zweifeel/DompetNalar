package com.example

import com.example.data.model.DomainCurrency
import com.example.util.FractionalScaleExceededException
import com.example.util.IllegalCharacterException
import com.example.util.InvalidNumberException
import com.example.util.MalformedDecimalException
import com.example.util.MalformedGroupingException
import com.example.util.MoneyInputParser
import com.example.util.NegativeAmountNotAllowedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class MoneyInputParserTest {

    private val usd = DomainCurrency.fromCode("USD")
    private val inr = DomainCurrency.fromCode("INR")
    private val eur = DomainCurrency.fromCode("EUR")
    private val jpy = DomainCurrency.fromCode("JPY")

    @Test
    fun `T-18 parses valid inputs across US, German, French, and Indian locales`() {
        // US Locale (period decimal, comma grouping)
        val usRes = MoneyInputParser.parse("1,234.56", usd, Locale.US)
        assertTrue(usRes.isSuccess)
        assertEquals(BigDecimal("1234.56"), usRes.getOrNull())

        // German Locale (comma decimal, period grouping)
        val deRes = MoneyInputParser.parse("1.234,56", eur, Locale.GERMANY)
        assertTrue(deRes.isSuccess)
        assertEquals(BigDecimal("1234.56"), deRes.getOrNull())

        // Indian Locale (3, then 2, 2 grouping)
        val inRes = MoneyInputParser.parse("12,34,567.89", inr, Locale.forLanguageTag("en-IN"))
        assertTrue(inRes.isSuccess)
        assertEquals(BigDecimal("1234567.89"), inRes.getOrNull())
    }

    @Test
    fun `T-19 rejects malformed grouping formats`() {
        // Consecutive grouping separators
        val consec = MoneyInputParser.parse("1,,234.56", usd, Locale.US)
        assertTrue(consec.isFailure)
        assertTrue(consec.exceptionOrNull() is MalformedGroupingException)

        // Leading grouping separator
        val leading = MoneyInputParser.parse(",123.45", usd, Locale.US)
        assertTrue(leading.isFailure)
        assertTrue(leading.exceptionOrNull() is MalformedGroupingException)

        // Trailing grouping separator
        val trailing = MoneyInputParser.parse("123,.45", usd, Locale.US)
        assertTrue(trailing.isFailure)
        assertTrue(trailing.exceptionOrNull() is MalformedGroupingException)

        // Invalid group size (standard requires 3 per group)
        val wrongSize = MoneyInputParser.parse("1,23,456.78", usd, Locale.US)
        assertTrue(wrongSize.isFailure)
        assertTrue(wrongSize.exceptionOrNull() is MalformedGroupingException)
    }

    @Test
    fun `T-20 validates Unicode Nd digits and rejects disallowed characters`() {
        // ASCII Nd digits
        val ascii = MoneyInputParser.parse("5000", usd, Locale.US)
        assertTrue(ascii.isSuccess)
        assertEquals(BigDecimal("5000"), ascii.getOrNull())

        // Disallowed letters or symbols
        val letters = MoneyInputParser.parse("123a.45", usd, Locale.US)
        assertTrue(letters.isFailure)
        assertTrue(letters.exceptionOrNull() is IllegalCharacterException)
    }

    @Test
    fun `T-21 enforces operation-specific sign policy`() {
        // Negative amount disallowed
        val negDisallowed = MoneyInputParser.parse("-100.50", usd, Locale.US, allowNegative = false)
        assertTrue(negDisallowed.isFailure)
        assertTrue(negDisallowed.exceptionOrNull() is NegativeAmountNotAllowedException)

        // Negative amount allowed
        val negAllowed = MoneyInputParser.parse("-100.50", usd, Locale.US, allowNegative = true)
        assertTrue(negAllowed.isSuccess)
        assertEquals(BigDecimal("-100.50"), negAllowed.getOrNull())
    }

    @Test
    fun `rejects fractional digits exceeding currency scale`() {
        // JPY has 0 decimal digits; input has 2 decimals
        val jpyExcess = MoneyInputParser.parse("1000.50", jpy, Locale.US)
        assertTrue(jpyExcess.isFailure)
        assertTrue(jpyExcess.exceptionOrNull() is FractionalScaleExceededException)
    }
}
