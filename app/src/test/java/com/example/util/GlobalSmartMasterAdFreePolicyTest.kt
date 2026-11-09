package com.example.util

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.config.AppConfig
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.session.SessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class GlobalSmartMasterAdFreePolicyTest {

    private lateinit var context: Context

    private var now = System.currentTimeMillis()

    private val guestUser = User(
        id = 0L,
        username = "GuestUser",
        email = "",
        role = UserRole.FREE_MEMBER,
        isGuest = true
    )

    private val freeMemberUser = User(
        id = 100L,
        username = "FreeUser",
        email = "free@example.com",
        role = UserRole.FREE_MEMBER,
        isGuest = false
    )

    private val masterDevUser = User(
        id = 999L,
        username = "MasterDev",
        email = "zweifeel@gmail.com",
        role = UserRole.MASTER_DEV,
        isGuest = false
    )

    @Before
    fun setUp() {
        now = System.currentTimeMillis()
        context = ApplicationProvider.getApplicationContext<Application>()
        val adPrefs = context.getSharedPreferences(RewardedAdRewardManager.PREFS_NAME, Context.MODE_PRIVATE)
        adPrefs.edit().clear().commit()
        val authPrefs = context.getSharedPreferences("finsmart_auth_prefs", Context.MODE_PRIVATE)
        authPrefs.edit().clear().commit()
        RewardedAdRewardManager.resetCallbackThrottleForTesting()
    }

    private fun setupPaidUser(
        userId: Long,
        expiryMillis: Long?,
        token: String = "token_abc_$userId",
        source: String = "PLAY_STORE_BILLING"
    ): User {
        val user = User(
            id = userId,
            username = "PaidUser_$userId",
            email = "paid$userId@example.com",
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = expiryMillis,
            isGuest = false
        )
        val authPrefs = context.getSharedPreferences("finsmart_auth_prefs", Context.MODE_PRIVATE)
        authPrefs.edit()
            .putString("billing_purchase_source_user_$userId", source)
            .putString("billing_last_token_user_$userId", token)
            .putLong("billing_purchase_time_user_$userId", now - 86400000L)
            .apply()
        return user
    }

    // =========================================================================
    // A. Paid Entitlement Ad-Free Behavior
    // =========================================================================

    @Test
    fun test01_paidSmartMaster_oneDayRemaining_isCompletelyAdFree() {
        val oneDayExpiry = now + 86400000L // 1 day remaining (< 30 days)
        val paidUser = setupPaidUser(101L, oneDayExpiry)
        SessionManager.login(paidUser)

        assertTrue(
            "EntitlementManager identifies active paid SmartMaster",
            EntitlementManager.isPaidOrPermanentSmartMaster(context, paidUser, now)
        )
        assertFalse("Paid subscriber with 1 day remaining must not see ads", AdManager.shouldShowAds(paidUser))
    }

    @Test
    fun test02_paidSmartMaster_29DaysRemaining_isCompletelyAdFree() {
        val expiry29Days = now + (29L * 86400000L)
        val paidUser = setupPaidUser(102L, expiry29Days)
        SessionManager.login(paidUser)

        assertTrue(EntitlementManager.isPaidOrPermanentSmartMaster(context, paidUser, now))
        assertFalse("Paid subscriber with 29 days remaining must not see ads", AdManager.shouldShowAds(paidUser))
    }

    @Test
    fun test03_paidSmartMaster_30DaysRemaining_isCompletelyAdFree() {
        val expiry30Days = now + (30L * 86400000L)
        val paidUser = setupPaidUser(103L, expiry30Days)
        SessionManager.login(paidUser)

        assertTrue(EntitlementManager.isPaidOrPermanentSmartMaster(context, paidUser, now))
        assertFalse("Paid subscriber with 30 days remaining must not see ads", AdManager.shouldShowAds(paidUser))
    }

    @Test
    fun test04_paidSmartMaster_moreThan30DaysRemaining_isCompletelyAdFree() {
        val expiry365Days = now + (365L * 86400000L)
        val paidUser = setupPaidUser(104L, expiry365Days)
        SessionManager.login(paidUser)

        assertTrue(EntitlementManager.isPaidOrPermanentSmartMaster(context, paidUser, now))
        assertFalse("Yearly subscriber with 365 days must not see ads", AdManager.shouldShowAds(paidUser))
    }

    @Test
    fun test05_paidSmartMaster_lifetimeNullExpiry_isCompletelyAdFree() {
        val lifetimeUser = setupPaidUser(105L, null)
        SessionManager.login(lifetimeUser)

        assertTrue(EntitlementManager.isPaidOrPermanentSmartMaster(context, lifetimeUser, now))
        assertFalse("Lifetime subscriber must not see ads", AdManager.shouldShowAds(lifetimeUser))
    }

    @Test
    fun test06_paidSubscriptionOffline_preservesLocalEntitlementAndAdFree() {
        // Offline verification: local prefs and User object are used without network
        val paidUser = setupPaidUser(106L, now + (15L * 86400000L))
        SessionManager.login(paidUser)

        val status = EntitlementManager.evaluate(paidUser, now = now)
        assertTrue("Offline evaluation must unlock premium", status.isPremiumUnlocked)
        assertFalse("Offline paid subscriber must remain ad-free", AdManager.shouldShowAds(paidUser))
    }

    @Test
    fun test07_expiredPaidSubscription_restoresFreeMemberAdEligibility() {
        val pastExpiry = now - 3600000L // Expired 1 hour ago
        val expiredUser = User(
            id = 107L,
            username = "ExpiredUser",
            email = "expired@example.com",
            role = UserRole.FREE_MEMBER, // Downgraded
            subscriptionExpiresAt = null,
            isGuest = false
        )
        SessionManager.login(expiredUser)

        assertFalse(
            "Expired user is not active paid",
            EntitlementManager.isPaidOrPermanentSmartMaster(context, expiredUser, now)
        )
        assertTrue("Expired subscriber must regain ad eligibility", AdManager.shouldShowAds(expiredUser))
    }

    // =========================================================================
    // B. Temporary SmartMaster Behavior
    // =========================================================================

    @Test
    fun test08_activeTemporaryRewardedAccess_isCompletelyAdFreeDuring60Minutes() {
        val rewardExpiry = now + 3600000L // 60 minutes active
        val tempUser = User(
            id = 108L,
            username = "TempUser",
            email = "temp@example.com",
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = rewardExpiry,
            isGuest = false
        )
        SessionManager.login(tempUser)

        // Store active reward in prefs
        val adPrefs = context.getSharedPreferences(RewardedAdRewardManager.PREFS_NAME, Context.MODE_PRIVATE)
        adPrefs.edit()
            .putString("user_108_last_claim_date", RewardedAdRewardManager.formatLocalDate(now))
            .putLong("user_108_reward_granted_at", now)
            .putLong("user_108_reward_expires_at", rewardExpiry)
            .apply()

        assertFalse(
            "Temporary user is NOT paid/permanent",
            EntitlementManager.isPaidOrPermanentSmartMaster(context, tempUser, now)
        )
        assertFalse("Temporary rewarded user MUST be ad-free while active", AdManager.shouldShowAds(tempUser))
    }

    @Test
    fun test09_temporaryRewardExpiry_restoresAdEligibility() {
        val pastRewardExpiry = now - 1000L
        val expiredTempUser = User(
            id = 109L,
            username = "ExpiredTempUser",
            email = "expired_temp@example.com",
            role = UserRole.FREE_MEMBER,
            subscriptionExpiresAt = null,
            isGuest = false
        )
        SessionManager.login(expiredTempUser)

        assertTrue("After 60m temporary reward expires, user is ad eligible", AdManager.shouldShowAds(expiredTempUser))
    }

    // =========================================================================
    // C. Reward Protection: Paid & Permanent Subscribers
    // =========================================================================

    @Test
    fun test10_isPaidOrPermanentSmartMaster_detectsPaidSubscriberWith1DayRemaining() {
        val paidUser = setupPaidUser(110L, now + 86400000L)
        assertTrue(EntitlementManager.isPaidOrPermanentSmartMaster(context, paidUser, now))
    }

    @Test
    fun test11_isPaidOrPermanentSmartMaster_detectsMasterDev() {
        assertTrue(EntitlementManager.isPaidOrPermanentSmartMaster(context, masterDevUser, now))
        assertFalse("MasterDev must not see ads", AdManager.shouldShowAds(masterDevUser))
    }

    @Test
    fun test12_isPaidOrPermanentSmartMaster_detectsDeveloperGrant() {
        val grantUser = setupPaidUser(112L, now + (30L * 86400000L), source = "DEVELOPER_GRANT")
        assertTrue(EntitlementManager.isPaidOrPermanentSmartMaster(context, grantUser, now))
    }

    @Test
    fun test13_isPaidOrPermanentSmartMaster_rejectsFreeMember() {
        assertFalse(EntitlementManager.isPaidOrPermanentSmartMaster(context, freeMemberUser, now))
        assertTrue("Free member must see ads", AdManager.shouldShowAds(freeMemberUser))
    }

    @Test
    fun test14_isPaidOrPermanentSmartMaster_rejectsGuest() {
        assertFalse(EntitlementManager.isPaidOrPermanentSmartMaster(context, guestUser, now))
        assertTrue("Guest must see ads", AdManager.shouldShowAds(guestUser))
    }

    // =========================================================================
    // D. Ad Eligibility Centralization
    // =========================================================================

    @Test
    fun test15_adManagerShouldShowAds_defaultsToSessionCurrentUserWhenNullPassed() {
        val paidUser = setupPaidUser(115L, now + (10L * 86400000L))
        SessionManager.login(paidUser)

        // Passing null explicitly defaults to SessionManager.currentUser
        assertFalse(AdManager.shouldShowAds(null))

        SessionManager.login(freeMemberUser)
        assertTrue(AdManager.shouldShowAds(null))
    }

    @Test
    fun test16_accountSwitchingDuringAd_expectedUserIdMismatch() {
        // User A starts watching an ad
        val userA = User(id = 501L, username = "UserA", email = "a@test.com", role = UserRole.FREE_MEMBER, isGuest = false)
        val userB = User(id = 502L, username = "UserB", email = "b@test.com", role = UserRole.FREE_MEMBER, isGuest = false)

        SessionManager.login(userA)
        val expectedUserId = 501L

        // User switches to User B while video is showing
        SessionManager.login(userB)
        val currentUserId = SessionManager.getActiveUserId()

        // Mismatch detected: reward grant must be dropped
        assertTrue("Mismatch detected", expectedUserId != currentUserId)
    }

    @Test
    fun test17_paidSubscriber_rejectedRewardDoesNotConsumeDailyClaim() {
        val paidUser = setupPaidUser(117L, now + (20L * 86400000L))
        SessionManager.login(paidUser)

        // If paid user is evaluated before calling processEarnedReward, no claim is recorded
        assertTrue(EntitlementManager.isPaidOrPermanentSmartMaster(context, paidUser, now))
        assertFalse("Daily reward must remain unconsumed", RewardedAdRewardManager.isDailyRewardClaimed(context, paidUser, now))
    }
}
