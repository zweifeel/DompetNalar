package com.example.util

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.User
import com.example.data.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class RewardedAdDailyPolicyTest {

    private lateinit var context: Context

    private val guestUser = User(
        id = 0L,
        username = "Guest",
        email = "",
        role = UserRole.FREE_MEMBER,
        isGuest = true
    )

    private val authUser1 = User(
        id = 101L,
        username = "User One",
        email = "user1@example.com",
        role = UserRole.FREE_MEMBER,
        isGuest = false
    )

    private val authUser2 = User(
        id = 202L,
        username = "User Two",
        email = "user2@example.com",
        role = UserRole.FREE_MEMBER,
        isGuest = false
    )

    private val masterDevUser = User(
        id = 999L,
        username = "Master Dev",
        email = "zweifeel@gmail.com",
        role = UserRole.MASTER_DEV,
        isGuest = false
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = context.getSharedPreferences(RewardedAdRewardManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        RewardedAdRewardManager.resetCallbackThrottleForTesting()
    }

    // =========================================================================
    // 1. Pure Logic Tests (canClaimToday & computeNewExpiry)
    // =========================================================================

    @Test
    fun test01_canClaimToday_nullLastClaim_returnsTrue() {
        assertTrue(RewardedAdRewardManager.canClaimToday(null, "2026-11-05"))
    }

    @Test
    fun test02_canClaimToday_blankLastClaim_returnsTrue() {
        assertTrue(RewardedAdRewardManager.canClaimToday("", "2026-11-05"))
        assertTrue(RewardedAdRewardManager.canClaimToday("   ", "2026-11-05"))
    }

    @Test
    fun test03_canClaimToday_yesterdayLastClaim_returnsTrue() {
        assertTrue(RewardedAdRewardManager.canClaimToday("2026-11-04", "2026-11-05"))
    }

    @Test
    fun test04_canClaimToday_sameDayLastClaim_returnsFalse() {
        assertFalse(RewardedAdRewardManager.canClaimToday("2026-11-05", "2026-11-05"))
    }

    @Test
    fun test05_computeNewExpiry_whenCurrentExpiryInPast_startsFromNow() {
        val now = 1_000_000_000L
        val pastExpiry = 900_000_000L
        val newExpiry = RewardedAdRewardManager.computeNewExpiry(pastExpiry, now, 3_600_000L)
        assertEquals(now + 3_600_000L, newExpiry)
    }

    @Test
    fun test06_computeNewExpiry_whenCurrentExpiryInFuture_extendsFromCurrentExpiry() {
        val now = 1_000_000_000L
        val unexpiredExpiry = now + 1_800_000L // 30 mins remaining
        val newExpiry = RewardedAdRewardManager.computeNewExpiry(unexpiredExpiry, now, 3_600_000L)
        assertEquals(unexpiredExpiry + 3_600_000L, newExpiry)
    }

    // =========================================================================
    // 2. Daily Limit & Multiple Ads on Same Day
    // =========================================================================

    @Test
    fun test07_firstClaim_grants60MinutesSuccessfully() {
        val now = 1_700_000_000_000L // arbitrary timestamp
        val result = RewardedAdRewardManager.processEarnedReward(context, authUser1, now)

        assertTrue("First claim must be granted", result.isGranted)
        assertFalse("Daily limit must not be reached prior to first claim", result.isDailyLimitAlreadyReached)
        assertEquals("New expiry must be exactly 60 minutes from now", now + 3_600_000L, result.newExpiryMillis)
        assertTrue("Storage reflects daily reward claimed", RewardedAdRewardManager.isDailyRewardClaimed(context, authUser1, now))
        assertEquals("Active expiry in prefs matches", now + 3_600_000L, RewardedAdRewardManager.getActiveRewardExpiry(context, authUser1, now))
    }

    @Test
    fun test08_secondClaimOnSameDay_deniesRewardAndDoesNotExtend() {
        val now1 = 1_700_000_000_000L
        val result1 = RewardedAdRewardManager.processEarnedReward(context, authUser1, now1)
        assertTrue(result1.isGranted)

        // Advance 10 minutes (600,000 ms) within the same calendar day
        RewardedAdRewardManager.resetCallbackThrottleForTesting()
        val now2 = now1 + 600_000L
        val result2 = RewardedAdRewardManager.processEarnedReward(context, authUser1, now2)

        assertFalse("Second claim on same day MUST be denied", result2.isGranted)
        assertTrue("isDailyLimitAlreadyReached MUST be true", result2.isDailyLimitAlreadyReached)
        assertEquals("Expiration MUST remain unchanged from first claim", result1.newExpiryMillis, result2.newExpiryMillis)
        assertTrue("Message explains daily limit", result2.message.contains("sudah digunakan"))
    }

    @Test
    fun test09_thirdClaimOnSameDay_alsoDeniesRewardAndPreservesExpiry() {
        val now1 = 1_700_000_000_000L
        val result1 = RewardedAdRewardManager.processEarnedReward(context, authUser1, now1)
        assertTrue(result1.isGranted)

        RewardedAdRewardManager.resetCallbackThrottleForTesting()
        val now2 = now1 + 600_000L
        RewardedAdRewardManager.processEarnedReward(context, authUser1, now2)

        RewardedAdRewardManager.resetCallbackThrottleForTesting()
        val now3 = now1 + 1_800_000L // 30 mins later
        val result3 = RewardedAdRewardManager.processEarnedReward(context, authUser1, now3)

        assertFalse("Third claim on same day MUST be denied", result3.isGranted)
        assertEquals("Expiration MUST NOT move", result1.newExpiryMillis, result3.newExpiryMillis)
    }

    @Test
    fun test10_nextCalendarDay_resetsQuotaAndAllowsNewReward() {
        // Day 1: 2026-11-05 10:00:00 UTC (1762336800000L)
        val day1 = 1762336800000L
        val result1 = RewardedAdRewardManager.processEarnedReward(context, authUser1, day1)
        assertTrue(result1.isGranted)

        // Day 2: 2026-11-06 10:00:00 UTC (1762423200000L, +24 hours)
        RewardedAdRewardManager.resetCallbackThrottleForTesting()
        val day2 = day1 + 86_400_000L
        assertFalse("Day 2 should not be claimed before user watches", RewardedAdRewardManager.isDailyRewardClaimed(context, authUser1, day2))

        val result2 = RewardedAdRewardManager.processEarnedReward(context, authUser1, day2)
        assertTrue("Day 2 reward MUST be granted", result2.isGranted)
        assertEquals("Day 2 expiry is day2 + 60m", day2 + 3_600_000L, result2.newExpiryMillis)
    }

    @Test
    fun test11_nextDayExtension_extendsActiveRewardSeamlessly() {
        // Day 1 at 23:45:00
        // Assume day1Timestamp is 23:45
        // Let's create an exact timestamp: e.g. 1762386300000L (23:45)
        // Expiry is day1 + 60m = 00:45 on Day 2
        // Then at 00:15 on Day 2 (+30 minutes), user watches another ad!
        val day1Night = 1762386300000L
        val result1 = RewardedAdRewardManager.processEarnedReward(context, authUser1, day1Night)
        assertTrue(result1.isGranted)
        val initialExpiry = result1.newExpiryMillis

        RewardedAdRewardManager.resetCallbackThrottleForTesting()
        val day2Morning = day1Night + (30 * 60 * 1000L) // 30 minutes later, now on day 2
        val day1DateStr = RewardedAdRewardManager.formatLocalDate(day1Night)
        val day2DateStr = RewardedAdRewardManager.formatLocalDate(day2Morning)

        if (day1DateStr != day2DateStr) {
            val result2 = RewardedAdRewardManager.processEarnedReward(context, authUser1, day2Morning)
            assertTrue("Day 2 claim must be granted", result2.isGranted)
            // It should extend initialExpiry by 60 minutes!
            assertEquals("New expiry must extend initialExpiry by 60 minutes", initialExpiry + 3_600_000L, result2.newExpiryMillis)
        }
    }

    // =========================================================================
    // 3. User Storage Key & Isolation Tests
    // =========================================================================

    @Test
    fun test12_userStorageKey_guestMapsToGuest() {
        assertEquals("guest", RewardedAdRewardManager.getUserStorageKey(guestUser))
        assertEquals("guest", RewardedAdRewardManager.getUserStorageKey(null))
        val invalidGuest = guestUser.copy(id = -1L)
        assertEquals("guest", RewardedAdRewardManager.getUserStorageKey(invalidGuest))
    }

    @Test
    fun test13_userStorageKey_authUserMapsToUserPrefix() {
        assertEquals("user_101", RewardedAdRewardManager.getUserStorageKey(authUser1))
        assertEquals("user_202", RewardedAdRewardManager.getUserStorageKey(authUser2))
    }

    @Test
    fun test14_userIsolation_guestAndAuthUserDoNotCollide() {
        val now = 1_700_000_000_000L

        // Guest claims reward
        val guestResult = RewardedAdRewardManager.processEarnedReward(context, guestUser, now)
        assertTrue(guestResult.isGranted)
        assertTrue(RewardedAdRewardManager.isDailyRewardClaimed(context, guestUser, now))

        // authUser1 has NOT claimed
        assertFalse(RewardedAdRewardManager.isDailyRewardClaimed(context, authUser1, now))
        assertEquals(0L, RewardedAdRewardManager.getActiveRewardExpiry(context, authUser1, now))

        // authUser1 claims reward independently
        RewardedAdRewardManager.resetCallbackThrottleForTesting()
        val authResult = RewardedAdRewardManager.processEarnedReward(context, authUser1, now)
        assertTrue("Authenticated user must be able to claim independently", authResult.isGranted)
        assertTrue(RewardedAdRewardManager.isDailyRewardClaimed(context, authUser1, now))
    }

    @Test
    fun test15_userIsolation_user1AndUser2DoNotCollide() {
        val now = 1_700_000_000_000L

        // User 1 claims
        val res1 = RewardedAdRewardManager.processEarnedReward(context, authUser1, now)
        assertTrue(res1.isGranted)

        // User 2 has not claimed
        assertFalse(RewardedAdRewardManager.isDailyRewardClaimed(context, authUser2, now))

        // User 2 claims
        RewardedAdRewardManager.resetCallbackThrottleForTesting()
        val res2 = RewardedAdRewardManager.processEarnedReward(context, authUser2, now)
        assertTrue("User 2 must be able to claim independently", res2.isGranted)
    }

    // =========================================================================
    // 4. Debounce & Throttle Protection
    // =========================================================================

    @Test
    fun test16_debounceProtection_rapidCallbacksWithin5SecondsIgnored() {
        val now = 1_700_000_000_000L

        val res1 = RewardedAdRewardManager.processEarnedReward(context, authUser1, now)
        assertTrue(res1.isGranted)

        // Immediate callback 1 second later (debounce check triggers before daily limit check)
        val res2 = RewardedAdRewardManager.processEarnedReward(context, authUser1, now + 1000L)
        assertFalse("Duplicate callback within 5s must be ignored", res2.isGranted)
        assertFalse(res2.isDailyLimitAlreadyReached)
        assertEquals("Duplicate callback ignored", res2.message)
    }

    // =========================================================================
    // 5. Expiration & Remaining Seconds Calculation
    // =========================================================================

    @Test
    fun test17_getRemainingRewardSeconds_returnsAccurateSeconds() {
        val now = 1_700_000_000_000L
        RewardedAdRewardManager.processEarnedReward(context, authUser1, now)

        val remainingAtStart = RewardedAdRewardManager.getRemainingRewardSeconds(context, authUser1, now)
        assertEquals(3600L, remainingAtStart)

        val remaining10MinsLater = RewardedAdRewardManager.getRemainingRewardSeconds(context, authUser1, now + 600_000L)
        assertEquals(3000L, remaining10MinsLater)

        val remainingAfterExpiry = RewardedAdRewardManager.getRemainingRewardSeconds(context, authUser1, now + 3_600_001L)
        assertEquals(0L, remainingAfterExpiry)
    }

    @Test
    fun test18_clearUserData_clearsAllStorageKeysForUser() {
        val now = 1_700_000_000_000L
        RewardedAdRewardManager.processEarnedReward(context, authUser1, now)
        assertTrue(RewardedAdRewardManager.isDailyRewardClaimed(context, authUser1, now))

        RewardedAdRewardManager.clearUserData(context, authUser1)
        assertFalse("Storage must be empty after clearUserData", RewardedAdRewardManager.isDailyRewardClaimed(context, authUser1, now))
        assertEquals(0L, RewardedAdRewardManager.getActiveRewardExpiry(context, authUser1, now))
    }
}
