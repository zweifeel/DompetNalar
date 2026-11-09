package com.example.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.util.EntitlementManager
import com.example.util.EntitlementSource
import com.example.util.RewardedAdRewardManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GuestAiInputEntitlementTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Reset SharedPreferences
        context.getSharedPreferences("finsmart_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun guestUser_default_aiInputIsLocked() {
        val guestUser = User(
            id = 999L,
            username = "Guest User",
            email = "guest_temp@finsmart.local",
            role = UserRole.FREE_MEMBER,
            isGuest = true
        )

        val entitlement = EntitlementManager.evaluate(guestUser)
        assertFalse("Guest user biasa tidak boleh memiliki akses SmartMaster AI", entitlement.isPremiumUnlocked)
        assertEquals(UserRole.FREE_MEMBER, entitlement.effectiveRole)
    }

    @Test
    fun guestUser_withActiveRewardedAd_aiInputIsUnlockedForOneHour() {
        val now = System.currentTimeMillis()
        val guestSmartMaster = User(
            id = 999L,
            username = "Guest User",
            email = "guest_temp@finsmart.local",
            role = UserRole.SMART_MASTER,
            isGuest = true,
            subscriptionExpiresAt = now + 3600000L
        )

        val entitlement = EntitlementManager.evaluate(guestSmartMaster, now = now)
        assertTrue("Guest user dengan reward iklan aktif harus memiliki akses SmartMaster", entitlement.isPremiumUnlocked)
        assertEquals(UserRole.SMART_MASTER, entitlement.effectiveRole)
        assertEquals(EntitlementSource.REWARDED_AD, entitlement.primarySource)
    }

    @Test
    fun guestUser_withExpiredRewardedAd_aiInputIsStrictlyLocked() {
        val now = System.currentTimeMillis()
        val expiredGuest = User(
            id = 999L,
            username = "Guest User",
            email = "guest_temp@finsmart.local",
            role = UserRole.SMART_MASTER,
            isGuest = true,
            subscriptionExpiresAt = now - 1000L // sudah kedaluwarsa
        )

        val entitlement = EntitlementManager.evaluate(expiredGuest, now = now)
        assertFalse("Guest user dengan reward kedaluwarsa harus terkunci dari AI", entitlement.isPremiumUnlocked)
        assertTrue("Status harus expired", entitlement.isExpired)
    }

    @Test
    fun guestUser_actionCredits_doesNotExceedPolicy() {
        val now = System.currentTimeMillis()
        val guestUser = User(
            id = 999L,
            username = "Guest User",
            email = "guest_temp@finsmart.local",
            role = UserRole.FREE_MEMBER,
            isGuest = true
        )

        // Guest tanpa action credits
        val entitlement = EntitlementManager.evaluate(guestUser, actionCredits = 0, now = now)
        assertFalse("Guest tanpa credit harus terkunci", entitlement.isPremiumUnlocked)
    }
}
