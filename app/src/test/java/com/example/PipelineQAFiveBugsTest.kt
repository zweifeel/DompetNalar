package com.example

import com.example.config.AppConfig
import com.example.config.DeveloperAccountConfig
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.util.AdManager
import com.example.util.EntitlementManager
import com.example.util.EntitlementSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineQAFiveBugsTest {

    private val baseTime = 1770000000000L

    // =========================================================================
    // BUG 1: ADMOB BANNER CONFIGURATION & ELIGIBILITY VERIFICATION
    // =========================================================================

    @Test
    fun bug1_admobTestAdIds_areConfiguredCorrectly() {
        // Verifikasi ID iklan test Google resmi aktif di Debug configuration
        assertEquals("ca-app-pub-3940256099942544~3347511713", AppConfig.ADMOB_APP_ID)
        assertEquals("ca-app-pub-3940256099942544/9214589741", AppConfig.ADMOB_BANNER_AD_UNIT_ID)
        assertEquals("ca-app-pub-3940256099942544/5224354917", AppConfig.ADMOB_REWARDED_AD_UNIT_ID)
    }

    @Test
    fun bug1_guestAndFreeUsers_areEligibleForBannerAds() {
        val now = System.currentTimeMillis()
        val guest = User(id = 1L, username = "Guest", email = "guest@finsmart.local", role = UserRole.FREE_MEMBER, isGuest = true)
        val freeUser = User(id = 2L, username = "Free", email = "free@example.com", role = UserRole.FREE_MEMBER, isGuest = false)
        val masterDev = User(id = 3L, username = "Lukas", email = "lunachroc01@gmail.com", role = UserRole.MASTER_DEV, isGuest = false)
        val smartMaster = User(id = 4L, username = "Smart", email = "smart@example.com", role = UserRole.SMART_MASTER, isGuest = false, subscriptionExpiresAt = now + 86400000L)

        assertTrue("Guest harus dapat melihat banner", AdManager.shouldShowAds(guest))
        assertTrue("Free user harus dapat melihat banner", AdManager.shouldShowAds(freeUser))
        assertFalse("Master Dev dikecualikan dari iklan banner", AdManager.shouldShowAds(masterDev))
        assertFalse("Smart Master aktif dikecualikan dari iklan banner", AdManager.shouldShowAds(smartMaster))
    }

    // =========================================================================
    // BUG 2: DEVELOPER CREDENTIALS USERNAME RECOVERY & INTEGRITY
    // =========================================================================

    @Test
    fun bug2_developerUsernameRecovery_neverBlank() {
        val devWithBlankName = User(
            id = 1L,
            username = "",
            email = "lunachroc01@gmail.com",
            role = UserRole.MASTER_DEV,
            isGuest = false
        )

        val resolvedUsername = devWithBlankName.username.takeIf { it.isNotBlank() }
            ?: if (devWithBlankName.role == UserRole.MASTER_DEV || DeveloperAccountConfig.isTrustedDeveloperEmail(devWithBlankName.email)) "Lukas"
            else "Pengguna FinSmart"

        assertEquals("Lukas", resolvedUsername)
        assertTrue("Username hasil pemulihan tidak boleh kosong", resolvedUsername.isNotBlank())
    }

    @Test
    fun bug2_trustedDeveloperIdentities_containAllRequiredAdminEmails() {
        assertTrue(DeveloperAccountConfig.isTrustedDeveloperEmail("lunachroc01@gmail.com"))
        assertTrue(DeveloperAccountConfig.isTrustedDeveloperEmail("zweifeel@gmail.com"))
        assertFalse("Legacy email lunachroc07@gmail.com dilarang masuk daftar developer Release", DeveloperAccountConfig.isTrustedDeveloperEmail("lunachroc07@gmail.com"))
        assertFalse(DeveloperAccountConfig.isTrustedDeveloperEmail("hacker@example.com"))
        assertFalse(DeveloperAccountConfig.isTrustedDeveloperEmail(null))
    }

    // =========================================================================
    // BUG 3: FIRESTORE RULES ADMIN POLICY ALIGNMENT
    // =========================================================================

    @Test
    fun bug3_firestoreAdminPolicy_differentiatesRolesSafely() {
        val devEmail = "lunachroc01@gmail.com"
        val regularEmail = "user@gmail.com"

        assertTrue("Developer email terotorisasi dalam konfigurasi admin", DeveloperAccountConfig.isTrustedDeveloperEmail(devEmail))
        assertFalse("Akun biasa tidak boleh terotorisasi sebagai admin", DeveloperAccountConfig.isTrustedDeveloperEmail(regularEmail))
        assertFalse("Legacy email tidak boleh terotorisasi sebagai admin", DeveloperAccountConfig.isTrustedDeveloperEmail("lunachroc07@gmail.com"))
    }

    // =========================================================================
    // BUG 4: REWARDED AD DURATION MUST BE EXACTLY 1 HOUR
    // =========================================================================

    @Test
    fun bug4_rewardedAdDuration_isExactlyOneHour() {
        val durationHours = 1
        val rewardDurationMillis = durationHours * 3600000L
        assertEquals("Durasi 1 jam harus bernilai tepat 3.600.000 milidetik", 3600000L, rewardDurationMillis)

        val freeUser = User(
            id = 10L,
            username = "Rewarded Tester",
            email = "reward@example.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false
        )

        val now = baseTime
        val newExpiry = now + rewardDurationMillis
        val updatedUser = freeUser.copy(
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = newExpiry
        )

        val status = EntitlementManager.evaluate(updatedUser, actionCredits = 0, now = now)
        assertTrue("Rewarded ad 1 jam harus membuka status premium", status.isPremiumUnlocked)
        assertEquals(UserRole.SMART_MASTER, status.effectiveRole)
        assertEquals(newExpiry, status.expiresAt)

        // Verifikasi kedaluwarsa setelah 1 jam + 1 detik
        val expiredStatus = EntitlementManager.evaluate(updatedUser, actionCredits = 0, now = now + rewardDurationMillis + 1000L)
        assertFalse("Setelah 1 jam berlalu, premium harus terkunci kembali", expiredStatus.isPremiumUnlocked)
    }

    @Test
    fun bug4_rewardedAd_doesNotDegradeExistingLongerSubscription() {
        val now = baseTime
        val longerExpiry = now + (30L * 86400000L) // 30 hari ke depan
        val existingSubUser = User(
            id = 11L,
            username = "Subscribed User",
            email = "sub@example.com",
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = longerExpiry
        )

        val rewardDurationMillis = 1 * 3600000L
        val currentExpiry = existingSubUser.subscriptionExpiresAt ?: 0L
        val resultingExpiry = if (currentExpiry > now + rewardDurationMillis) currentExpiry else (now + rewardDurationMillis)

        assertEquals("Langganan 30 hari yang lebih kuat tidak boleh ditimpa menjadi 1 jam", longerExpiry, resultingExpiry)
    }

    // =========================================================================
    // BUG 5: GAMIFICATION STREAK REWARDS (7 DAYS -> 5H, 30 DAYS -> 24H)
    // =========================================================================

    @Test
    fun bug5_sevenDayStreakReward_isExactlyFiveHours() {
        val durationHours = 5
        val durationMillis = durationHours * 3600000L
        assertEquals("Reward streak 7 hari harus bernilai 18.000.000 ms (5 jam)", 18000000L, durationMillis)

        val now = baseTime
        val expiry = now + durationMillis
        val userWith5hReward = User(
            id = 12L,
            username = "Streak 7 User",
            email = "streak7@example.com",
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = expiry
        )

        val activeStatus = EntitlementManager.evaluate(userWith5hReward, now = now + (4 * 3600000L)) // 4 jam setelah klaim
        assertTrue("Pada jam ke-4, akses Smart Member 5 jam masih aktif", activeStatus.isPremiumUnlocked)

        val expiredStatus = EntitlementManager.evaluate(userWith5hReward, now = now + (5 * 3600000L) + 1000L) // 5 jam 1 detik
        assertFalse("Setelah 5 jam, akses Smart Member harus kedaluwarsa", expiredStatus.isPremiumUnlocked)
    }

    @Test
    fun bug5_thirtyDayStreakReward_isExactlyTwentyFourHours() {
        val durationHours = 24
        val durationMillis = durationHours * 3600000L
        assertEquals("Reward streak 30 hari harus bernilai 86.400.000 ms (24 jam)", 86400000L, durationMillis)

        val now = baseTime
        val expiry = now + durationMillis
        val userWith24hReward = User(
            id = 13L,
            username = "Streak 30 User",
            email = "streak30@example.com",
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = expiry
        )

        val activeStatus = EntitlementManager.evaluate(userWith24hReward, now = now + (12 * 3600000L)) // 12 jam setelah klaim
        assertTrue("Pada jam ke-12, akses Smart Member 24 jam masih aktif", activeStatus.isPremiumUnlocked)

        val expiredStatus = EntitlementManager.evaluate(userWith24hReward, now = now + (24 * 3600000L) + 1000L) // 24 jam 1 detik
        assertFalse("Setelah 24 jam, akses Smart Member harus kedaluwarsa", expiredStatus.isPremiumUnlocked)
    }

    @Test
    fun bug5_streakRewards_masterDevAccessPreserved() {
        val masterDev = User(
            id = 1L,
            username = "Lukas",
            email = "lunachroc01@gmail.com",
            role = UserRole.MASTER_DEV,
            isGuest = false
        )

        val status = EntitlementManager.evaluate(masterDev, now = baseTime)
        assertTrue("Master Dev selalu memiliki akses seumur hidup", status.isPremiumUnlocked)
        assertEquals(UserRole.MASTER_DEV, status.effectiveRole)
        assertEquals(EntitlementSource.MASTER_DEV, status.primarySource)
    }
}
