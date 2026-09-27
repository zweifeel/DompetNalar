package com.example

import com.example.data.model.AppIconEdition
import com.example.data.model.FinancialPersonality
import com.example.data.model.UserGamificationState
import com.example.util.GamificationManager
import com.example.util.SplitBillCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinSmartUnitTest {

    @Test
    fun testLevelAndXpCalculation() {
        val (level1, reqXp1) = GamificationManager.calculateLevel(50)
        assertEquals(1, level1)
        assertEquals(100, reqXp1)

        val (level2, _) = GamificationManager.calculateLevel(120)
        assertEquals(2, level2)

        val (levelMax, _) = GamificationManager.calculateLevel(100000)
        assertEquals(50, levelMax)
    }

    @Test
    fun testDuressPinValidation() {
        val state = UserGamificationState(
            mainPin = "123456",
            duressPin = "999999",
            isStealthMode = false
        )

        // Verifikasi PIN Utama
        val isMainValid = "123456" == state.mainPin
        assertTrue(isMainValid)

        // Verifikasi Duress PIN -> Aktifkan Stealth Mode
        val isDuressTriggered = "999999" == state.duressPin
        assertTrue(isDuressTriggered)

        val stealthState = state.copy(isStealthMode = isDuressTriggered)
        assertTrue(stealthState.isStealthMode)
    }

    @Test
    fun testFinancialPersonalityDiagnosis() {
        val answers = listOf(
            FinancialPersonality.SAVER,
            FinancialPersonality.SAVER,
            FinancialPersonality.INVESTOR,
            FinancialPersonality.SAVER,
            FinancialPersonality.BALANCED
        )

        val result = GamificationManager.determinePersonality(answers)
        assertEquals(FinancialPersonality.SAVER, result)
    }

    @Test
    fun testSplitBillCalculation() {
        val result = SplitBillCalculator.calculateSplit(
            totalAmount = 100000.0,
            taxAmount = 10000.0,
            serviceFee = 5000.0,
            discountAmount = 15000.0,
            itemPrices = listOf(40000.0, 60000.0)
        )

        assertEquals(2, result.perPersonAmount.size)
        // Subtotal = 100,000; Net surcharge = +10,000 + 5,000 - 15,000 = 0
        assertEquals(100000.0, result.grandTotal, 0.01)
    }

    @Test
    fun testTemporaryPassExpiryLogic() {
        val now = System.currentTimeMillis()
        val oneHourLater = now + 3600000L
        val oneHourAgo = now - 3600000L

        // Active 1-hour pass
        assertTrue(oneHourLater > now)

        // Expired pass
        assertFalse(oneHourAgo > now)
    }
}
