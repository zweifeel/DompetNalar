package com.example

import com.example.util.ConversionResult
import com.example.util.RealConversionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealConversionEngineTest {

    @Test
    fun `V-MAT-01 safe IDR at exact 900B ceiling`() {
        val res = RealConversionEngine.convert(900_000_000_000.0, "IDR", 2)
        assertTrue(res is ConversionResult.Safe)
        assertEquals(90000000000000L, (res as ConversionResult.Safe).minorUnits)
    }

    @Test
    fun `V-MAT-02 nextUp of 900B IDR rejected without silent rounding`() {
        val nextVal = Math.nextUp(900_000_000_000.0)
        val res = RealConversionEngine.convert(nextVal, "IDR", 2)
        assertTrue(
            res is ConversionResult.PrecisionAmbiguous || res is ConversionResult.BusinessCeilingExceeded
        )
    }

    @Test
    fun `V-MAT-03 900B USD is safe because IDR ceiling does not bind USD`() {
        val res = RealConversionEngine.convert(900_000_000_000.0, "USD", 2)
        assertTrue(res is ConversionResult.Safe)
        assertEquals(90000000000000L, (res as ConversionResult.Safe).minorUnits)
    }

    @Test
    fun `V-MAT-04 hardware overflow rejected safely`() {
        val res = RealConversionEngine.convert(92233720368547758.07, "IDR", 2)
        assertTrue(
            res is ConversionResult.PrecisionAmbiguous || res is ConversionResult.MathematicalOverflow
        )
    }

    @Test
    fun `V-MAT-05 value exceeding IDR 900B business ceiling is rejected`() {
        val res = RealConversionEngine.convert(90071992547409.91, "IDR", 2)
        assertTrue(
            res is ConversionResult.BusinessCeilingExceeded || res is ConversionResult.PrecisionAmbiguous
        )
    }

    @Test
    fun `V-MAT-06 positive overflow above Long MAX rejected`() {
        val res = RealConversionEngine.convert(1.0E19, "IDR", 2)
        assertTrue(res is ConversionResult.MathematicalOverflow)
    }

    @Test
    fun `V-MAT-07 negative overflow below Long MIN rejected`() {
        val res = RealConversionEngine.convert(-1.0E19, "IDR", 2)
        assertTrue(res is ConversionResult.MathematicalOverflow)
    }

    @Test
    fun `V-MAT-08 zero is safe zero minor units`() {
        val res = RealConversionEngine.convert(0.0, "IDR", 2)
        assertTrue(res is ConversionResult.Safe)
        assertEquals(0L, (res as ConversionResult.Safe).minorUnits)
    }

    @Test
    fun `V-MAT-09 negative zero normalized to zero minor units`() {
        val res = RealConversionEngine.convert(-0.0, "IDR", 2)
        assertTrue(res is ConversionResult.Safe)
        assertEquals(0L, (res as ConversionResult.Safe).minorUnits)
    }

    @Test
    fun `V-MAT-10 NaN rejected as PrecisionAmbiguous`() {
        val res = RealConversionEngine.convert(Double.NaN, "IDR", 2)
        assertTrue(res is ConversionResult.PrecisionAmbiguous)
        assertEquals("NAN_VALUE", (res as ConversionResult.PrecisionAmbiguous).reason)
    }

    @Test
    fun `V-MAT-11a positive infinity rejected as MathematicalOverflow`() {
        val res = RealConversionEngine.convert(Double.POSITIVE_INFINITY, "IDR", 2)
        assertTrue(res is ConversionResult.MathematicalOverflow)
        assertEquals("INFINITY_VALUE", (res as ConversionResult.MathematicalOverflow).reason)
    }

    @Test
    fun `V-MAT-11b negative infinity rejected as MathematicalOverflow`() {
        val res = RealConversionEngine.convert(Double.NEGATIVE_INFINITY, "IDR", 2)
        assertTrue(res is ConversionResult.MathematicalOverflow)
        assertEquals("INFINITY_VALUE", (res as ConversionResult.MathematicalOverflow).reason)
    }

    @Test
    fun `V-MAT-12 JPY integer double is safe with 0 exponent`() {
        val res = RealConversionEngine.convert(5000.0, "JPY", 0)
        assertTrue(res is ConversionResult.Safe)
        assertEquals(5000L, (res as ConversionResult.Safe).minorUnits)
    }

    @Test
    fun `V-MAT-13 JPY with fractional digits rejected as PrecisionAmbiguous`() {
        val res = RealConversionEngine.convert(5000.5, "JPY", 0)
        assertTrue(res is ConversionResult.PrecisionAmbiguous)
    }

    @Test
    fun `V-MAT-14 IDR with 3 decimal digits rejected as PrecisionAmbiguous`() {
        val res = RealConversionEngine.convert(15000.123, "IDR", 2)
        assertTrue(res is ConversionResult.PrecisionAmbiguous)
    }
}
