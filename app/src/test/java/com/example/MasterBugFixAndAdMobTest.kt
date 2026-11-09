package com.example

import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.util.AdManager
import com.example.util.EntitlementManager
import com.example.util.EntitlementSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterBugFixAndAdMobTest {

    private val baseTime = 1770000000000L

    // =========================================================================
    // TASK 2: ADMOB ELIGIBILITY AND ENTITLEMENT TESTS
    // =========================================================================

    @Test
    fun guestUser_isEligibleForAds() {
        val guest = User(
            id = 1L,
            username = "Tamu FinSmart",
            email = "guest@finsmart.local",
            role = UserRole.FREE_MEMBER,
            isGuest = true,
            trialStartDate = 0L
        )

        // Guest harus diizinkan melihat iklan reguler
        assertTrue("Pengguna Guest harus diizinkan melihat iklan", AdManager.shouldShowAds(guest))
    }

    @Test
    fun freeNonPremiumUser_isEligibleForAds() {
        val freeUser = User(
            id = 2L,
            username = "Pengguna Gratis",
            email = "free@example.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            trialStartDate = 0L
        )

        // Pengguna terdaftar non-Premium harus diizinkan melihat iklan
        assertTrue("Pengguna Free non-Premium harus diizinkan melihat iklan", AdManager.shouldShowAds(freeUser))
    }

    @Test
    fun activeTrialUser_isExemptFromAds() {
        val freeUser = User(
            id = 3L,
            username = "Free User",
            email = "free@gmail.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            trialStartDate = 0L
        )
        // Free user tanpa langganan aktif harus melihat iklan
        assertTrue("Free user tanpa langganan aktif harus melihat iklan", AdManager.shouldShowAds(freeUser))

        val smartMasterUser = User(
            id = 5L,
            username = "Smart Master User",
            email = "subscriber@gmail.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = System.currentTimeMillis() + 86400000L
        )
        // Pengguna Smart Master aktif mendapatkan benefit bebas iklan
        assertFalse("Pengguna Smart Master aktif tidak boleh melihat iklan", AdManager.shouldShowAds(smartMasterUser))
    }

    @Test
    fun expiredTrialUser_isEligibleForAdsAgain() {
        val expiredSubUser = User(
            id = 4L,
            username = "Expired Sub User",
            email = "expired@gmail.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = System.currentTimeMillis() - (4L * 24 * 3600 * 1000L)
        )

        // Pengguna dengan langganan yang sudah kedaluwarsa kembali melihat iklan
        assertTrue("Pengguna dengan langganan kedaluwarsa harus kembali melihat iklan", AdManager.shouldShowAds(expiredSubUser))
    }

    @Test
    fun activeSmartMaster_isExemptFromAds() {
        val smartMaster = User(
            id = 5L,
            username = "Smart Master Aktif",
            email = "master@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = System.currentTimeMillis() + (30L * 86400000L)
        )

        // Smart Master aktif bebas iklan
        assertFalse("Smart Master aktif tidak boleh melihat iklan", AdManager.shouldShowAds(smartMaster))
    }

    @Test
    fun expiredSmartMaster_isEligibleForAds() {
        val expiredSmartMaster = User(
            id = 6L,
            username = "Smart Master Expired",
            email = "expiredmaster@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = System.currentTimeMillis() - 1000L // sudah kedaluwarsa
        )

        // Smart Master kedaluwarsa kembali melihat iklan
        assertTrue("Smart Master yang langganannya berakhir harus kembali melihat iklan", AdManager.shouldShowAds(expiredSmartMaster))
    }

    @Test
    fun masterDeveloper_isAlwaysExemptFromAds() {
        val masterDev = User(
            id = 99L,
            username = "Developer",
            email = "lunachroc07@gmail.com",
            role = UserRole.MASTER_DEV,
            isGuest = false
        )

        // Master Dev bebas iklan permanen
        assertFalse("Master Dev tidak boleh melihat iklan", AdManager.shouldShowAds(masterDev))
    }

    @Test
    fun nullUser_isEligibleForAdsSafely() {
        // Fallback jika session user null (dianggap guest/free)
        assertTrue("User null harus fallback menampilkan iklan secara aman", AdManager.shouldShowAds(null))
    }

    // =========================================================================
    // TASK 3: SMART MEMBER COUNTER EVALUATION IN SETTINGS
    // =========================================================================

    @Test
    fun settingsCounter_guestNeverGetsTrial() {
        val guest = User(
            id = 10L,
            username = "Tamu",
            email = "guest@finsmart.local",
            role = UserRole.FREE_MEMBER,
            isGuest = true,
            trialStartDate = baseTime
        )

        val status = EntitlementManager.evaluate(guest, now = baseTime)
        assertFalse("Guest tidak boleh memiliki premium aktif", status.isPremiumUnlocked)
        assertEquals(0, status.trialDaysRemaining)
        assertEquals(EntitlementSource.NONE, status.primarySource)
    }

    @Test
    fun settingsCounter_activeTrialShowsAccurateDays() {
        // 1 hari tersisa dari 3 hari (2 hari berlalu)
        val userDay2 = User(
            id = 11L,
            username = "Trial Day 2",
            email = "user@gmail.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            trialStartDate = baseTime - (2L * 86400000L)
        )

        val status = EntitlementManager.evaluate(userDay2, now = baseTime)
        assertFalse("Trial dihapus permanen: pengguna bebas tidak boleh membuka premium", status.isPremiumUnlocked)
        assertEquals(EntitlementSource.NONE, status.primarySource)
        assertEquals(0, status.trialDaysRemaining)
    }

    @Test
    fun settingsCounter_smartMasterShowsAccurateExpiry() {
        val expiryTime = baseTime + (10L * 86400000L)
        val userMaster = User(
            id = 12L,
            username = "Master User",
            email = "user@finsmart.app",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = expiryTime
        )

        val status = EntitlementManager.evaluate(userMaster, now = baseTime)
        assertTrue("Smart Master harus aktif", status.isPremiumUnlocked)
        assertEquals(UserRole.SMART_MASTER, status.effectiveRole)
        assertEquals(expiryTime, status.expiresAt)
    }

    @Test
    fun settingsCounter_masterDevIsPermanentLifetime() {
        val dev = User(
            id = 13L,
            username = "Master Dev",
            email = "lunachroc07@gmail.com",
            role = UserRole.MASTER_DEV,
            isGuest = false
        )

        val status = EntitlementManager.evaluate(dev, now = baseTime)
        assertTrue("Master Dev selalu aktif", status.isPremiumUnlocked)
        assertEquals(UserRole.MASTER_DEV, status.effectiveRole)
        assertEquals(EntitlementSource.MASTER_DEV, status.primarySource)
    }

    @Test
    fun settingsCounter_nearlyExpiredTrial() {
        // Trial yang tinggal 30 menit (2 hari 23 jam 30 menit telah berlalu)
        val elapsed = EntitlementManager.THREE_DAYS_MILLIS - (30 * 60 * 1000L)
        val userNearlyExpired = User(
            id = 14L,
            username = "Nearly Expired",
            email = "nearly@gmail.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            trialStartDate = baseTime - elapsed
        )

        val status = EntitlementManager.evaluate(userNearlyExpired, now = baseTime)
        assertFalse("Trial dihapus permanen: status premium terkunci", status.isPremiumUnlocked)
        assertEquals(EntitlementSource.NONE, status.primarySource)
        assertEquals(0, status.trialDaysRemaining)
    }

    @Test
    fun settingsCounter_expiredTrial() {
        // Trial yang sudah lewat 3 hari 1 menit
        val elapsed = EntitlementManager.THREE_DAYS_MILLIS + (60 * 1000L)
        val userExpired = User(
            id = 15L,
            username = "Expired Trial",
            email = "expired@gmail.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            trialStartDate = baseTime - elapsed
        )

        val status = EntitlementManager.evaluate(userExpired, now = baseTime)
        assertFalse("Trial dihapus permanen: tidak boleh membuka premium", status.isPremiumUnlocked)
        assertEquals(EntitlementSource.NONE, status.primarySource)
        assertEquals(0, status.trialDaysRemaining)
    }

    @Test
    fun settingsCounter_smartMasterWithoutExpirationTimestamp() {
        // Smart Master tanpa batas waktu (expiresAt = null)
        val userUnlimited = User(
            id = 16L,
            username = "Unlimited Master",
            email = "unlimited@finsmart.app",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = null
        )

        val status = EntitlementManager.evaluate(userUnlimited, now = baseTime)
        assertTrue("Smart Master unlimited harus aktif", status.isPremiumUnlocked)
        assertEquals(UserRole.SMART_MASTER, status.effectiveRole)
        assertFalse(status.isExpired)
        assertEquals("Tidak Terbatas", status.getFormattedExpiry())
    }

    @Test
    fun settingsCounter_expiredSmartMasterSubscription() {
        val expiredTime = baseTime - (3600 * 1000L) // 1 jam yang lalu
        val userExpiredMaster = User(
            id = 17L,
            username = "Expired Master",
            email = "expiredmaster@finsmart.app",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = expiredTime
        )

        val status = EntitlementManager.evaluate(userExpiredMaster, now = baseTime)
        assertFalse("Smart Master kedaluwarsa tidak boleh aktif", status.isPremiumUnlocked)
        assertTrue("Harus isExpired = true", status.isExpired)
    }

    @Test
    fun settingsCounter_freeUserWithoutTrial() {
        val freeUser = User(
            id = 18L,
            username = "Free Regular",
            email = "free@example.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            trialStartDate = 0L
        )

        val status = EntitlementManager.evaluate(freeUser, now = baseTime)
        assertFalse("Free user reguler tidak premium", status.isPremiumUnlocked)
        assertFalse("Bukan expired trial", status.isExpired)
        assertEquals(EntitlementSource.NONE, status.primarySource)
        assertEquals(0, status.trialDaysRemaining)
    }

    @Test
    fun settingsCounter_accountSwitchingEvaluation() {
        val userA = User(
            id = 19L,
            username = "User A (Smart Master)",
            email = "a@gmail.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = baseTime + 86400000L
        )
        val userB = User(
            id = 20L,
            username = "User B (Guest)",
            email = "guest@finsmart.local",
            role = UserRole.FREE_MEMBER,
            isGuest = true,
            trialStartDate = 0L
        )

        val statusA = EntitlementManager.evaluate(userA, now = baseTime)
        val statusB = EntitlementManager.evaluate(userB, now = baseTime)

        assertTrue("User A dengan Smart Master aktif harus premium unlocked", statusA.isPremiumUnlocked)
        assertFalse("User B (guest) dilarang premium", statusB.isPremiumUnlocked)
    }

    @Test
    fun settingsCounter_corruptOrNegativeTimestampsHandledSafely() {
        val corruptUser = User(
            id = 21L,
            username = "Corrupt Time User",
            email = "corrupt@gmail.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            trialStartDate = -999999L
        )

        val status = EntitlementManager.evaluate(corruptUser, now = baseTime)
        assertFalse("Timestamp negatif harus ditolak secara aman", status.isPremiumUnlocked)
        assertEquals(0, status.trialDaysRemaining)
    }

    // =========================================================================
    // TASK: REWARDED AD LIFECYCLE, 60-MIN ENTITLEMENT & PAID PROTECTION
    // =========================================================================

    @Test
    fun rewardedAd_grantsStrictly60MinutesAndIdentifiesAsRewardedAdSource() {
        val now = baseTime
        val sixtyMinutes = 3600000L
        val guestWithAdReward = User(
            id = 20L,
            username = "Guest With Reward",
            email = "guest@finsmart.local",
            role = UserRole.SMART_MASTER,
            isGuest = true,
            subscriptionExpiresAt = now + sixtyMinutes
        )

        val status = EntitlementManager.evaluate(guestWithAdReward, now = now)
        assertTrue("Pengguna dengan reward iklan harus memperoleh akses premium", status.isPremiumUnlocked)
        assertEquals(EntitlementSource.REWARDED_AD, status.primarySource)
        assertEquals(now + sixtyMinutes, status.expiresAt)
        assertFalse(status.isExpired)
    }

    @Test
    fun rewardedAd_expiresAfter60MinutesAndLocksFeatures() {
        val now = baseTime
        val sixtyMinutes = 3600000L
        val expiredRewardUser = User(
            id = 21L,
            username = "Expired Guest Reward",
            email = "guest@finsmart.local",
            role = UserRole.SMART_MASTER,
            isGuest = true,
            subscriptionExpiresAt = now - 1000L // 1 detik setelah 60 menit habis
        )

        val status = EntitlementManager.evaluate(expiredRewardUser, now = now)
        assertFalse("Setelah 60 menit habis, akses premium harus kembali terkunci", status.isPremiumUnlocked)
        assertTrue("Status entitlement harus ditandai expired", status.isExpired)
    }

    @Test
    fun paidSubscription_isProtectedAndNotOverwrittenOrDowngradedByAdLogic() {
        val now = baseTime
        val thirtyDays = 30L * 86400000L
        val paidSubscriber = User(
            id = 22L,
            username = "Paid User",
            email = "paid@finsmart.app",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = now + thirtyDays
        )

        val status = EntitlementManager.evaluate(paidSubscriber, now = now)
        assertTrue("Pelanggan berbayar harus memiliki akses premium aktif", status.isPremiumUnlocked)
        assertEquals(UserRole.SMART_MASTER, status.effectiveRole)
        assertEquals(now + thirtyDays, status.expiresAt)
        // Sumber bukan REWARDED_AD karena durasi lebih dari 60 menit
        org.junit.Assert.assertNotEquals(EntitlementSource.REWARDED_AD, status.primarySource)
    }

    @Test
    fun appConfig_adMobIdsAreConfigured() {
        assertTrue("AppConfig.ADMOB_APP_ID tidak boleh kosong", com.example.config.AppConfig.ADMOB_APP_ID.isNotBlank())
        assertTrue("AppConfig.ADMOB_BANNER_AD_UNIT_ID tidak boleh kosong", com.example.config.AppConfig.ADMOB_BANNER_AD_UNIT_ID.isNotBlank())
        assertTrue("AppConfig.ADMOB_REWARDED_AD_UNIT_ID tidak boleh kosong", com.example.config.AppConfig.ADMOB_REWARDED_AD_UNIT_ID.isNotBlank())
        assertTrue("AppConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID tidak boleh kosong", com.example.config.AppConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID.isNotBlank())
    }
}


