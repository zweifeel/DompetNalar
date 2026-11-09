package com.example

import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.util.BillingManager
import com.example.util.EntitlementManager
import com.example.util.EntitlementSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingManagerTest {

    private val baseTime = 1770000000000L

    @Test
    fun productIds_areConsistentAndCorrect() {
        assertEquals("smart_master_monthly", BillingManager.PRODUCT_ID_MONTHLY)
        assertEquals("smart_master_yearly", BillingManager.PRODUCT_ID_YEARLY)
        assertEquals("smart_master_lifetime", BillingManager.PRODUCT_ID_LIFETIME)
    }

    @Test
    fun getFormattedPrice_returnsFallbackWhenPricesNotLoaded() {
        val monthly = BillingManager.getFormattedPrice(BillingManager.PRODUCT_ID_MONTHLY, "Rp19.000")
        val yearly = BillingManager.getFormattedPrice(BillingManager.PRODUCT_ID_YEARLY, "Rp149.000")
        val lifetime = BillingManager.getFormattedPrice(BillingManager.PRODUCT_ID_LIFETIME, "Rp399.000")
        val unknown = BillingManager.getFormattedPrice("unknown_id", "Rp0")

        assertEquals("Rp19.000", monthly)
        assertEquals("Rp149.000", yearly)
        assertEquals("Rp399.000", lifetime)
        assertEquals("Rp0", unknown)
    }

    @Test
    fun monthlyPurchase_grantsThirtyDaysEntitlement() {
        val now = baseTime
        val thirtyDaysDuration = 30L * 86400000L
        val expiry = now + thirtyDaysDuration

        val user = User(
            id = 10L,
            username = "Pembeli Bulanan",
            email = "buyer_monthly@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = expiry
        )

        val status = EntitlementManager.evaluate(user, now = now)
        assertTrue("Pengguna dengan subscription bulanan aktif harus membuka Smart Master", status.isPremiumUnlocked)
        assertEquals(UserRole.SMART_MASTER, status.effectiveRole)
        assertFalse(status.isExpired)
    }

    @Test
    fun yearlyPurchase_grants365DaysEntitlement() {
        val now = baseTime
        val yearlyDuration = 365L * 86400000L
        val expiry = now + yearlyDuration

        val user = User(
            id = 11L,
            username = "Pembeli Tahunan",
            email = "buyer_yearly@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = expiry
        )

        val status = EntitlementManager.evaluate(user, now = now)
        assertTrue("Pengguna dengan subscription tahunan aktif harus membuka Smart Master", status.isPremiumUnlocked)
        assertEquals(UserRole.SMART_MASTER, status.effectiveRole)
        assertFalse(status.isExpired)
    }

    @Test
    fun lifetimePurchase_grantsTenYearsEntitlement() {
        val now = baseTime
        val lifetimeDuration = 3650L * 86400000L // 10 tahun
        val expiry = now + lifetimeDuration

        val user = User(
            id = 12L,
            username = "Pembeli Lifetime",
            email = "buyer_lifetime@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = expiry
        )

        val status = EntitlementManager.evaluate(user, now = now)
        assertTrue("Pengguna dengan in-app lifetime harus membuka Smart Master permanen", status.isPremiumUnlocked)
        assertEquals(UserRole.SMART_MASTER, status.effectiveRole)
        assertFalse(status.isExpired)
    }

    @Test
    fun expiredGooglePlayPurchase_locksEntitlement() {
        val now = baseTime
        val expiredTime = now - 5000L // 5 detik yang lalu

        val user = User(
            id = 13L,
            username = "Pembeli Kedaluwarsa",
            email = "buyer_expired@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = expiredTime
        )

        val status = EntitlementManager.evaluate(user, now = now)
        assertFalse("Subscription Google Play yang kedaluwarsa harus mengunci kembali hak akses", status.isPremiumUnlocked)
        assertEquals(UserRole.FREE_MEMBER, status.effectiveRole)
        assertTrue(status.isExpired)
    }

    @Test
    fun guestUser_cannotBeUpgradedDirectlyWithoutAccount() {
        val guest = User(
            id = 999L,
            username = "Tamu",
            email = "guest_temp@finsmart.local",
            role = UserRole.FREE_MEMBER,
            isGuest = true,
            trialStartDate = 0L
        )

        // Verifikasi bahwa guest tidak memenuhi syarat entitlement sebelum registrasi
        val status = EntitlementManager.evaluate(guest, now = baseTime)
        assertFalse(status.isPremiumUnlocked)
        assertEquals(EntitlementSource.NONE, status.primarySource)
    }
}
