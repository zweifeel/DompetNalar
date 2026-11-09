package com.example.util

import com.example.data.model.User
import com.example.data.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdManagerInterstitialTest {

    @Test
    fun isFrequencyCapped_withinTenMinutes_returnsTrue() {
        val now = 1000000000000L
        val lastShown = now - (5 * 60 * 1000L) // 5 minutes ago

        assertTrue(
            "Ad within 5 minutes of last display must be frequency capped",
            AdManager.isFrequencyCapped(lastShown, now)
        )
    }

    @Test
    fun isFrequencyCapped_afterTenMinutes_returnsFalse() {
        val now = 1000000000000L
        val lastShown = now - (10 * 60 * 1000L + 1L) // 10 minutes and 1 ms ago

        assertFalse(
            "Ad beyond 10-minute window must not be frequency capped",
            AdManager.isFrequencyCapped(lastShown, now)
        )
    }

    @Test
    fun isFrequencyCapped_noPriorDisplay_returnsFalse() {
        val now = 1000000000000L
        assertFalse(
            "First-time ad request (lastShown=0) must not be capped",
            AdManager.isFrequencyCapped(0L, now)
        )
        assertFalse(
            "Negative timestamp must not be capped",
            AdManager.isFrequencyCapped(-1L, now)
        )
    }

    @Test
    fun premiumUser_isExemptFromAds() {
        val smartMaster = User(
            id = 100L,
            username = "VIP Member",
            email = "vip@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = System.currentTimeMillis() + 86400000L
        )
        assertFalse("Smart Master must not see ads", AdManager.shouldShowAds(smartMaster))
    }

    @Test
    fun freeUser_isEligibleForAds() {
        val freeUser = User(
            id = 101L,
            username = "Free Member",
            email = "free@example.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false
        )
        assertTrue("Free user must be eligible for ads", AdManager.shouldShowAds(freeUser))
    }
}
