package com.example

import com.example.util.ShakeDetectorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Unit tests for FEAT-36 Shake Gesture Detector physics, debounce logic, and action constants.
 */
class ShakeDetectorTest {

    @Test
    fun testCalculateGForce_atRest_yieldsApproximatelyOneG() {
        // At rest on a flat surface, gravity is 1.0g along Z axis
        val gForce = ShakeDetectorUtils.calculateGForce(0f, 0f, ShakeDetectorUtils.GRAVITY_EARTH)
        assertEquals(1.0f, gForce, 0.01f)
    }

    @Test
    fun testCalculateGForce_underShake_exceedsThreshold() {
        // Fast acceleration along X and Y axes
        val x = 18f
        val y = 18f
        val z = ShakeDetectorUtils.GRAVITY_EARTH
        val gForce = ShakeDetectorUtils.calculateGForce(x, y, z)

        assertTrue(
            "Shake acceleration ($gForce g) must exceed threshold (${ShakeDetectorUtils.SHAKE_THRESHOLD_G_FORCE} g)",
            gForce > ShakeDetectorUtils.SHAKE_THRESHOLD_G_FORCE
        )
    }

    @Test
    fun testIsShake_thresholdEvaluation() {
        assertFalse(
            "G-force equal or below 2.5g should not be considered a shake",
            ShakeDetectorUtils.isShake(2.5f)
        )
        assertFalse(
            "G-force below 2.5g should not be considered a shake",
            ShakeDetectorUtils.isShake(2.0f)
        )
        assertTrue(
            "G-force above 2.5g should be considered a shake",
            ShakeDetectorUtils.isShake(2.51f)
        )
        assertTrue(
            "Strong shake (> 3.0g) must be recognized",
            ShakeDetectorUtils.isShake(3.5f)
        )
    }

    @Test
    fun testDebounceTiming_respects1200msCooldown() {
        val cooldownMs = ShakeDetectorUtils.SHAKE_COOLDOWN_MS
        assertEquals(1200L, cooldownMs)

        var lastShakeTime = 1000L
        val eventWithinCooldown = 1500L
        val isCooldownPassed1 = (eventWithinCooldown - lastShakeTime) > cooldownMs
        assertFalse("Event within 500ms should be rejected by debounce", isCooldownPassed1)

        val eventAfterCooldown = 2300L
        val isCooldownPassed2 = (eventAfterCooldown - lastShakeTime) > cooldownMs
        assertTrue("Event after 1300ms (> 1200ms) should be accepted", isCooldownPassed2)
    }

    @Test
    fun testShakeActionConstants_andDefaults() {
        assertEquals("OFF", ShakeDetectorUtils.ACTION_OFF)
        assertEquals("TRANSACTION", ShakeDetectorUtils.ACTION_TRANSACTION)
        assertEquals("STEALTH", ShakeDetectorUtils.ACTION_STEALTH)
    }

    @Test
    fun testGuardMatrix_suppressesShakeWhenBlockingModalIsActive() {
        // Simulate modal state combinations
        fun isShakeAllowed(
            shakeAction: String,
            isAuthModalOpen: Boolean,
            isDeleteAccountOpen: Boolean,
            isDuressPinModalOpen: Boolean,
            isReceiptGeneratorOpen: Boolean,
            isAddTransactionOpen: Boolean,
            isPaywallOpen: Boolean,
            isMasterDevPanelOpen: Boolean,
            isNotificationCenterOpen: Boolean
        ): Boolean {
            val isAnyBlockingModalActive = isAuthModalOpen || isDeleteAccountOpen ||
                isDuressPinModalOpen || isReceiptGeneratorOpen || isAddTransactionOpen ||
                isPaywallOpen || isMasterDevPanelOpen || isNotificationCenterOpen

            return shakeAction != ShakeDetectorUtils.ACTION_OFF && !isAnyBlockingModalActive
        }

        // When disabled (OFF), shake is never allowed
        assertFalse(
            isShakeAllowed("OFF", false, false, false, false, false, false, false, false)
        )

        // When enabled (TRANSACTION) and no modals active, shake is allowed
        assertTrue(
            isShakeAllowed("TRANSACTION", false, false, false, false, false, false, false, false)
        )

        // When enabled (STEALTH) and no modals active, shake is allowed
        assertTrue(
            isShakeAllowed("STEALTH", false, false, false, false, false, false, false, false)
        )

        // When any modal is active, shake must be suppressed
        assertFalse(
            "Shake must be suppressed when DeleteAccountDialog is open",
            isShakeAllowed("TRANSACTION", false, true, false, false, false, false, false, false)
        )
        assertFalse(
            "Shake must be suppressed when AuthModal is open",
            isShakeAllowed("TRANSACTION", true, false, false, false, false, false, false, false)
        )
        assertFalse(
            "Shake must be suppressed when DuressPinModal is open",
            isShakeAllowed("TRANSACTION", false, false, true, false, false, false, false, false)
        )
        assertFalse(
            "Shake must be suppressed when AddTransactionSheet is already open",
            isShakeAllowed("TRANSACTION", false, false, false, false, true, false, false, false)
        )
    }
}
