package com.example

import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.util.EntitlementManager
import com.example.util.EntitlementSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementManagerTest {

    private val baseTime = 1770000000000L

    @Test
    fun guestUser_doesNotGetTrialOrPremium() {
        val guest = User(
            id = 1L,
            username = "Tamu FinSmart",
            email = "guest_123@finsmart.local",
            role = UserRole.FREE_MEMBER,
            isGuest = true,
            trialStartDate = 0L
        )

        val status = EntitlementManager.evaluate(guest, actionCredits = 0, now = baseTime)
        assertFalse("Tamu tidak boleh memiliki akses premium", status.isPremiumUnlocked)
        assertEquals(0, status.trialDaysRemaining)
        assertEquals(EntitlementSource.NONE, status.primarySource)
        assertEquals(UserRole.FREE_MEMBER, status.effectiveRole)
    }

    @Test
    fun googleUser_withoutSubscription_neverReceivesTrial_remainsLocked() {
        val googleUser = User(
            id = 2L,
            username = "Rian Google",
            email = "rian@gmail.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            trialStartDate = 0L
        )

        val status = EntitlementManager.evaluate(googleUser, actionCredits = 0, now = baseTime)
        assertFalse("Akun Google tanpa langganan aktif tidak boleh membuka premium", status.isPremiumUnlocked)
        assertEquals(EntitlementSource.NONE, status.primarySource)
        assertEquals(0, status.trialDaysRemaining)
        assertFalse(status.isExpired)
    }

    @Test
    fun userWithLegacyTrialTimestamp_neverUnlocksPremium() {
        val googleUser = User(
            id = 3L,
            username = "Maya Google",
            email = "maya@gmail.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            trialStartDate = baseTime - (12 * 3600 * 1000L)
        )

        val status = EntitlementManager.evaluate(googleUser, actionCredits = 0, now = baseTime)
        assertFalse("Timestamp trial historis tidak boleh membuka premium", status.isPremiumUnlocked)
        assertEquals(0, status.trialDaysRemaining)
        assertEquals(EntitlementSource.NONE, status.primarySource)
    }

    @Test
    fun smartMaster_activeSubscription_grantsPremium() {
        val smartMaster = User(
            id = 4L,
            username = "Budi Master",
            email = "budi@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = baseTime + (30L * 86400000L) // 30 hari ke depan
        )

        val status = EntitlementManager.evaluate(smartMaster, actionCredits = 0, now = baseTime)
        assertTrue("Smart Master aktif harus membuka fitur premium", status.isPremiumUnlocked)
        assertEquals(UserRole.SMART_MASTER, status.effectiveRole)
        assertEquals(EntitlementSource.DEVELOPER_GRANT, status.primarySource)
        assertFalse(status.isExpired)
    }

    @Test
    fun smartMaster_expiredSubscription_locksPremium() {
        val expiredMaster = User(
            id = 5L,
            username = "Siti Expired",
            email = "siti@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = baseTime - 1000L // 1 detik yang lalu (kedaluwarsa)
        )

        val status = EntitlementManager.evaluate(expiredMaster, actionCredits = 0, now = baseTime)
        assertFalse("Langganan kedaluwarsa harus mengunci fitur premium", status.isPremiumUnlocked)
        assertEquals(UserRole.FREE_MEMBER, status.effectiveRole)
        assertTrue(status.isExpired)
    }

    @Test
    fun masterDeveloper_alwaysHasPermanentAccess() {
        val dev = User(
            id = 99L,
            username = "Lukas Dev",
            email = "lunachroc07@gmail.com",
            role = UserRole.MASTER_DEV,
            isGuest = false,
            subscriptionExpiresAt = null
        )

        val status = EntitlementManager.evaluate(dev, actionCredits = 0, now = baseTime)
        assertTrue("Master Dev selalu memiliki akses premium permanen", status.isPremiumUnlocked)
        assertEquals(UserRole.MASTER_DEV, status.effectiveRole)
        assertEquals(EntitlementSource.MASTER_DEV, status.primarySource)
        assertEquals(999, status.trialDaysRemaining)
        assertNull("Expiry harus null untuk akses permanen", status.expiresAt)
        assertEquals("Permanen (Master Developer)", status.getFormattedExpiry())
        assertFalse(status.isExpired)
    }

    @Test
    fun trustedDeveloperEmails_alwaysReceivePermanentMasterDevEntitlement_withoutOneDayCountdown() {
        val devEmails = listOf(
            com.example.config.DeveloperAccountConfig.CONTACT_DEV_EMAIL,
            com.example.config.DeveloperAccountConfig.OWNER_PROJECT_EMAIL
        )

        devEmails.forEach { email ->
            // Simulasikan akun developer meskipun role di database sempat tersimpan sebagai FREE_MEMBER atau memiliki subscriptionExpiresAt 1 hari
            val devUser = User(
                id = 101L,
                username = "FinSmart Developer",
                email = email,
                role = UserRole.FREE_MEMBER,
                isGuest = false,
                subscriptionExpiresAt = baseTime + 86400000L // 1 hari ke depan
            )

            val status = EntitlementManager.evaluate(devUser, actionCredits = 0, now = baseTime, isDebug = true)
            assertTrue("Developer $email harus selalu memperoleh akses premium", status.isPremiumUnlocked)
            assertEquals("Developer $email harus memiliki effectiveRole MASTER_DEV", UserRole.MASTER_DEV, status.effectiveRole)
            assertEquals(EntitlementSource.MASTER_DEV, status.primarySource)
            assertEquals("Trial days remaining harus 999 (tidak terbatas)", 999, status.trialDaysRemaining)
            assertNull("Developer tidak boleh memiliki tanggal kedaluwarsa 1 hari", status.expiresAt)
            assertEquals("Permanen (Master Developer)", status.getFormattedExpiry())
            assertFalse("Developer tidak boleh kedaluwarsa", status.isExpired)
        }
    }

    @Test
    fun legacyDeveloperEmail_doesNotReceiveDeveloperPrivileges() {
        val legacyEmail = com.example.config.DeveloperAccountConfig.LEGACY_DEV_EMAIL
        val legacyUser = User(
            id = 103L,
            username = "Legacy FinSmart Dev",
            email = legacyEmail,
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            subscriptionExpiresAt = null
        )

        // 1. Verifikasi whitelist konfigurasi tidak mencakup email legacy
        assertFalse("Legacy email lunachroc07@gmail.com tidak boleh ada di TRUSTED_DEVELOPER_EMAILS",
            com.example.config.DeveloperAccountConfig.isTrustedDeveloperEmail(legacyEmail))

        // 2. Verifikasi evaluasi pada build DEBUG
        val debugStatus = EntitlementManager.evaluate(legacyUser, actionCredits = 0, now = baseTime, isDebug = true)
        assertNotEquals("Legacy dev email tidak boleh otomatis menjadi MASTER_DEV", UserRole.MASTER_DEV, debugStatus.effectiveRole)

        // 3. Verifikasi evaluasi pada build RELEASE
        val releaseStatus = EntitlementManager.evaluate(legacyUser, actionCredits = 0, now = baseTime, isDebug = false)
        assertFalse("Legacy dev email tidak boleh memperoleh akses premium pada Release", releaseStatus.isPremiumUnlocked)
        assertEquals(UserRole.FREE_MEMBER, releaseStatus.effectiveRole)
    }

    @Test
    fun ordinaryGoogleUser_doesNotObtainDeveloperAccess() {
        val regularUser = User(
            id = 202L,
            username = "Regular User",
            email = "user.biasa@gmail.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            trialStartDate = baseTime - (12 * 3600 * 1000L) // 12 jam yang lalu
        )

        val status = EntitlementManager.evaluate(regularUser, actionCredits = 0, now = baseTime)
        assertFalse("Pengguna biasa tanpa langganan tidak boleh memperoleh premium", status.isPremiumUnlocked)
        assertEquals("Role pengguna biasa harus FREE_MEMBER bukan MASTER_DEV", UserRole.FREE_MEMBER, status.effectiveRole)
        assertEquals("Primary source harus NONE", EntitlementSource.NONE, status.primarySource)
        assertEquals(0, status.trialDaysRemaining)
        assertFalse(status.isExpired)
    }

    @Test
    fun actionPassCredit_unlocksFeatureTemporarily() {
        val freeUser = User(
            id = 6L,
            username = "User Kredit",
            email = "kredit@example.com",
            role = UserRole.FREE_MEMBER,
            isGuest = true,
            trialStartDate = 0L
        )

        val status = EntitlementManager.evaluate(freeUser, actionCredits = 2, now = baseTime)
        assertTrue("Action credit harus membuka akses fitur sementara", status.isPremiumUnlocked)
        assertEquals(EntitlementSource.ACTION_PASS_CREDIT, status.primarySource)
        assertEquals(2, status.actionCredits)
    }

    @Test
    fun validateCustomDuration_acceptsValidDurations() {
        val result7 = EntitlementManager.validateCustomDuration("7")
        assertTrue(result7.isSuccess)
        assertEquals(7L, result7.getOrNull())

        val result30 = EntitlementManager.validateCustomDuration("  30 ")
        assertTrue(result30.isSuccess)
        assertEquals(30L, result30.getOrNull())

        val result3650 = EntitlementManager.validateCustomDuration("3650")
        assertTrue(result3650.isSuccess)
        assertEquals(3650L, result3650.getOrNull())
    }

    @Test
    fun rewardedAd_grantsOneHourAccess_andExpiresAfterOneHour() {
        // 30 minutes in - still valid
        val activeUser = User(
            id = 7L,
            username = "Ad Watcher",
            email = "ad@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = baseTime + (30 * 60 * 1000L)
        )
        val activeStatus = EntitlementManager.evaluate(user = activeUser, now = baseTime)
        assertTrue("Akses Rewarded Ad aktif harus membuka premium", activeStatus.isPremiumUnlocked)
        assertEquals(EntitlementSource.REWARDED_AD, activeStatus.primarySource)
        assertFalse(activeStatus.isExpired)

        // 65 minutes in - expired
        val expiredUser = User(
            id = 7L,
            username = "Ad Watcher",
            email = "ad@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = baseTime - (5 * 60 * 1000L)
        )
        val expiredStatus = EntitlementManager.evaluate(user = expiredUser, now = baseTime)
        assertFalse("Akses Rewarded Ad yang kedaluwarsa harus mengunci kembali", expiredStatus.isPremiumUnlocked)
        assertEquals(UserRole.FREE_MEMBER, expiredStatus.effectiveRole)
    }

    @Test
    fun streakReward24H_grantsOneDayAccess_andExpiresAfter24Hours() {
        // 12 hours remaining
        val activeUser = User(
            id = 8L,
            username = "Streak 30 Hari",
            email = "streak30@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = baseTime + (12 * 3600 * 1000L)
        )
        val activeStatus = EntitlementManager.evaluate(user = activeUser, now = baseTime)
        assertTrue("Akses Streak Reward 24 jam aktif harus membuka premium", activeStatus.isPremiumUnlocked)
        assertEquals(EntitlementSource.STREAK_REWARD_24H, activeStatus.primarySource)
        assertFalse(activeStatus.isExpired)

        // 25 hours later - expired
        val expiredUser = User(
            id = 8L,
            username = "Streak 30 Hari",
            email = "streak30@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = baseTime - (1 * 3600 * 1000L)
        )
        val expiredStatus = EntitlementManager.evaluate(user = expiredUser, now = baseTime)
        assertFalse("Akses Streak Reward 24 jam kedaluwarsa harus mengunci kembali", expiredStatus.isPremiumUnlocked)
    }

    @Test
    fun paidSubscription_isNotOverriddenByExpiredTemporaryReward() {
        val paidUser = User(
            id = 9L,
            username = "Paid User",
            email = "paid@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = baseTime + (30L * 86400000L) // 30 hari lagi
        )

        val status = EntitlementManager.evaluate(user = paidUser, now = baseTime)
        assertTrue("Langganan berbayar tetap aktif", status.isPremiumUnlocked)
        assertEquals(UserRole.SMART_MASTER, status.effectiveRole)
    }

    @Test
    fun validateCustomDuration_rejectsInvalidInputs() {
        assertTrue("Harus menolak string kosong", EntitlementManager.validateCustomDuration("").isFailure)
        assertTrue("Harus menolak nol", EntitlementManager.validateCustomDuration("0").isFailure)
        assertTrue("Harus menolak angka negatif", EntitlementManager.validateCustomDuration("-5").isFailure)
        assertTrue("Harus menolak non-digit", EntitlementManager.validateCustomDuration("abc").isFailure)
        assertTrue("Harus menolak nilai di atas batas 3650 hari", EntitlementManager.validateCustomDuration("3651").isFailure)
    }
}
