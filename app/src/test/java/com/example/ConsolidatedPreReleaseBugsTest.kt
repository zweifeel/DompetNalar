package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.config.AppConfig
import com.example.data.local.FinSmartDatabase
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.session.SessionManager
import com.example.ui.viewmodel.AuthViewModel
import com.example.util.AdManager
import com.example.util.EntitlementManager
import com.example.util.EntitlementSource
import java.io.File
import com.example.util.BillingManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConsolidatedPreReleaseBugsTest {

    private lateinit var app: Application
    private lateinit var db: FinSmartDatabase
    private lateinit var authViewModel: AuthViewModel

    @Before
    fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val prefs = app.getSharedPreferences("finsmart_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        SessionManager.initialize(app)
        authViewModel = AuthViewModel(app)
    }

    @After
    fun tearDown() {
        try {
            com.example.data.local.DatabaseWriteCoordinator.resetForTesting()
        } catch (_: Exception) {}
        db.close()
        SessionManager.logout()
    }

    // =========================================================================
    // BUG 7: COUNTER STREAK STARTS AT DAY 1, DAILY ACTIVITY & 7-DAY REWARD LOCK
    // =========================================================================

    @Test
    fun bug7_newUser_startsAtStreak1_not7() {
        val state = authViewModel.gamificationState.value
        assertEquals("Pengguna baru harus mulai dari streak 1 hari", 1, state.dailyStreak)
        assertEquals("Best streak pengguna baru harus mulai dari 1 hari", 1, state.bestStreak)
        assertFalse("Hadiah streak 7 hari tidak boleh dapat diklaim pada hari 1", authViewModel.canClaimStreakReward())
    }

    @Test
    fun bug7_legacyDefault7WithoutActivity_isSanitizedTo1() {
        val prefs = app.getSharedPreferences("finsmart_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putInt("gamify_streak", 7).apply() // Legacy default without initialized flag

        val freshVm = AuthViewModel(app)
        assertEquals("Legacy uninitialized streak 7 harus disanitasi ke 1", 1, freshVm.gamificationState.value.dailyStreak)
    }

    @Test
    fun bug7_dailyActivityProgression_requiresConsecutiveDays() {
        val user = User(id = 55L, username = "StreakerUser", email = "streaker@gmail.com", role = UserRole.FREE_MEMBER)
        SessionManager.login(user)

        val baseTime = 1770000000000L // arbitrary fixed time
        val oneDayMillis = 86400000L

        // Day 1 activity
        val streakDay1 = authViewModel.recordDailyActivity(baseTime)
        assertEquals("Aktivitas pertama menghasilkan streak 1", 1, streakDay1)

        // Multiple activities on the SAME day do not advance streak
        val streakSameDay = authViewModel.recordDailyActivity(baseTime + 3600000L)
        assertEquals("Aktivitas tambahan di hari yang sama tidak menaikkan streak", 1, streakSameDay)

        // Day 2 activity (next day)
        val streakDay2 = authViewModel.recordDailyActivity(baseTime + oneDayMillis)
        assertEquals("Aktivitas hari berikutnya menaikkan streak ke 2", 2, streakDay2)

        // Progress up to Day 6
        for (day in 3..6) {
            val s = authViewModel.recordDailyActivity(baseTime + ((day - 1) * oneDayMillis))
            assertEquals(day, s)
        }
        assertFalse("Pada hari ke-6 reward 7 hari belum dapat diklaim", authViewModel.canClaimStreakReward())

        // Day 7 activity
        val streakDay7 = authViewModel.recordDailyActivity(baseTime + (6 * oneDayMillis))
        assertEquals(7, streakDay7)
        assertTrue("Pada hari ke-7 berturut-turut, reward 7 hari dapat diklaim", authViewModel.canClaimStreakReward())
    }

    @Test
    fun bug7_gapInActivity_resetsStreakTo1() {
        val baseTime = 1770000000000L
        val oneDayMillis = 86400000L

        authViewModel.recordDailyActivity(baseTime)
        authViewModel.recordDailyActivity(baseTime + oneDayMillis)
        assertEquals(2, authViewModel.gamificationState.value.dailyStreak)

        // Gap: skip 2 days
        val resetStreak = authViewModel.recordDailyActivity(baseTime + (3 * oneDayMillis))
        assertEquals("Streak yang terputus harus di-reset kembali ke 1", 1, resetStreak)
    }

    // =========================================================================
    // BUG 2: REWARDED ADS GUEST EXACTLY 60 MINUTES & AUTOMATIC EXPIRY
    // =========================================================================

    @Test
    fun bug2_guestRewardedAd_grantsSmartMasterForExactly60Minutes() {
        val guest = User(id = 101L, username = "GuestUser", email = "guest@test.local", role = UserRole.FREE_MEMBER, isGuest = true, trialStartDate = 0L)
        SessionManager.login(guest)

        val beforeGrant = System.currentTimeMillis()
        authViewModel.grantTemporaryRewardAccess(1)

        val currentUser = SessionManager.currentUser.value
        assertNotNull(currentUser)
        assertEquals(UserRole.SMART_MASTER, currentUser!!.role)
        val expiry = currentUser.subscriptionExpiresAt
        assertNotNull(expiry)
        val duration = expiry!! - beforeGrant
        assertTrue("Durasi harus tepat ~60 menit (3,600,000 ms), aktual: $duration", duration in 3590000L..3610000L)
        assertTrue("Akses fitur Smart Master harus terbuka", authViewModel.isFeatureUnlocked())
    }

    @Test
    fun bug2_guestRewardedAd_expiresAfter60Minutes_andLocksAccess() {
        val guest = User(id = 102L, username = "GuestUser2", email = "guest2@test.local", role = UserRole.FREE_MEMBER, isGuest = true, trialStartDate = 0L)
        SessionManager.login(guest)

        val pastTime = System.currentTimeMillis() - 3660000L // 61 minutes ago
        val prefs = app.getSharedPreferences("finsmart_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("guest_ad_reward_granted_at", pastTime)
            .putLong("guest_ad_reward_expires_at", pastTime + 3600000L)
            .apply()

        // Set expired user in session
        val expiredGuest = guest.copy(role = UserRole.SMART_MASTER, subscriptionExpiresAt = pastTime + 3600000L)
        SessionManager.login(expiredGuest)

        // Calling isFeatureUnlocked must detect expiration, auto-downgrade and lock access
        assertFalse("Setelah 60 menit, akses harus terkunci", authViewModel.isFeatureUnlocked())
        assertEquals("Peran pengguna harus kembali ke FREE_MEMBER", UserRole.FREE_MEMBER, SessionManager.currentUser.value?.role)
        assertEquals("Sisa detik countdown harus 0", 0L, authViewModel.adRewardRemainingSeconds.value)
    }

    @Test
    fun bug2_legacy24HourReward_isClampedTo60MinutesMax() {
        val guest = User(id = 103L, username = "GuestUser3", email = "guest3@test.local", role = UserRole.FREE_MEMBER, isGuest = true, trialStartDate = 0L)
        SessionManager.login(guest)

        val now = System.currentTimeMillis()
        val grantedAt = now - (90 * 60 * 1000L) // 90 minutes ago
        val legacy24hExpiry = grantedAt + (24 * 3600 * 1000L) // Legacy 24-hour expiry!

        val prefs = app.getSharedPreferences("finsmart_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("guest_ad_reward_granted_at", grantedAt)
            .putLong("guest_ad_reward_expires_at", legacy24hExpiry)
            .apply()

        val userWithLegacy = guest.copy(role = UserRole.SMART_MASTER, subscriptionExpiresAt = legacy24hExpiry)
        SessionManager.login(userWithLegacy)

        // Sanitization must clamp to grantedAt + 60min, which was 30 minutes ago, so it is EXPIRED!
        authViewModel.checkAndSanitizeEntitlement(now)
        assertFalse("Reward lama berdurasi 24 jam tidak boleh memperpanjang akses melewati 60 menit", authViewModel.isFeatureUnlocked())
        assertEquals(UserRole.FREE_MEMBER, SessionManager.currentUser.value?.role)
    }

    @Test
    fun bug2_guestMode_neverReceivesThreeDayTrial() {
        val guest = User(id = 104L, username = "GuestNoTrial", email = "guest_notrial@finsmart.local", role = UserRole.FREE_MEMBER, isGuest = true, trialStartDate = 0L)
        SessionManager.login(guest)

        assertEquals("Trial days remaining harus 0 untuk mode tamu", 0, authViewModel.getTrialDaysRemaining())
        val status = EntitlementManager.evaluate(guest)
        assertEquals(EntitlementSource.NONE, status.primarySource)
        assertFalse(status.isPremiumUnlocked)
    }

    @Test
    fun bug2_paidEntitlement_unaffectedWhenAdRewardExpires() {
        val now = System.currentTimeMillis()
        val paidUser = User(
            id = 201L,
            username = "PaidUser",
            email = "paid@gmail.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = now + (30L * 24 * 3600 * 1000L) // 30-day paid sub
        )
        SessionManager.login(paidUser)

        authViewModel.checkAndSanitizeEntitlement(now)
        assertTrue("Langganan berbayar harus tetap aktif", authViewModel.isFeatureUnlocked())
        assertEquals(UserRole.SMART_MASTER, SessionManager.currentUser.value?.role)
    }

    // =========================================================================
    // BUG 1: 30-DAY STREAK REWARD 24H SMART MASTER & WALLPAPER DECOUPLING
    // =========================================================================

    @Test
    fun bug1_streak30DayReward_grantsSmartMasterFor24Hours() {
        val prefs = app.getSharedPreferences("finsmart_auth_prefs", Context.MODE_PRIVATE)
        val user = User(id = 301L, username = "StreakMaster", email = "streakmaster@gmail.com", role = UserRole.FREE_MEMBER, isGuest = false)
        SessionManager.login(user)

        // Set streak = 30
        prefs.edit().putInt("gamify_streak", 30).apply()
        val vm = AuthViewModel(app)
        assertTrue("Streak 30 hari harus dapat mengklaim", vm.canClaim30DayStreakReward(user))

        val now = System.currentTimeMillis()
        vm.grant30DayStreakMilestoneReward(24)

        val updatedUser = SessionManager.currentUser.value
        assertEquals(UserRole.SMART_MASTER, updatedUser?.role)
        val expiry = updatedUser?.subscriptionExpiresAt ?: 0L
        assertTrue("Masa aktif harus 24 jam", expiry - now in (24 * 3600000L - 10000L)..(24 * 3600000L + 10000L))

        // Duplicate claim within 30 days cooldown must be blocked
        assertFalse("Klaim ulang dalam cooldown harus diblokir", vm.canClaim30DayStreakReward(user))
    }

    // =========================================================================
    // BUG 3: MANUAL PAYMENT PERMANENT REMOVAL & OFFICIAL BILLING REGRESSION DEFENSE
    // =========================================================================

    private fun findSourceFile(relativeSubPath: String): File {
        val candidates = listOf(
            relativeSubPath,
            "app/$relativeSubPath",
            "../$relativeSubPath",
            "../../$relativeSubPath",
            "E:/Antigravity Project/FinSmart/app/$relativeSubPath",
            "E:/Antigravity Project/FinSmart/$relativeSubPath"
        )
        val file = candidates.map { File(it) }.firstOrNull { it.exists() && it.isFile }
        assertNotNull("Source file must exist for regression contract testing: $relativeSubPath", file)
        return file!!
    }

    @Test
    fun bug3_appConfig_reflection_guaranteesAbsenceOfManualPaymentFields() {
        val fields = AppConfig::class.java.declaredFields
        val fieldNames = fields.map { it.name }

        // Must NOT declare any DANA, transfer, or manual payment constants
        for (name in fieldNames) {
            val upper = name.uppercase()
            assertFalse(
                "AppConfig must not contain manual payment fields, found: $name",
                upper.contains("DANA") || upper.contains("TRANSFER") || upper.contains("MANUAL_PAYMENT")
            )
        }

        // Must NOT contain the decommissioned DANA account number or recipient name in any field value
        for (field in fields) {
            field.isAccessible = true
            val value = try { field.get(AppConfig)?.toString() ?: "" } catch (_: Exception) { "" }
            assertFalse(
                "AppConfig field ${field.name} must not contain manual payment number 085155076566",
                value.contains("085155076566")
            )
            if (field.name.uppercase().contains("NAME") && field.name != "APP_NAME" && field.name != "APP_VERSION_NAME") {
                assertFalse(
                    "AppConfig field ${field.name} must not contain legacy recipient Lukas",
                    value.equals("Lukas", ignoreCase = true)
                )
            }
        }

        // Official billing prices must be positive and valid
        assertEquals(19000.0, AppConfig.MONTHLY_PRICE_IDR, 0.001)
        assertEquals(149000.0, AppConfig.YEARLY_PRICE_IDR, 0.001)
        assertEquals("DompetNalar", AppConfig.APP_NAME)
    }

    @Test
    fun bug3_smartMasterPaywall_sourceContract_strictlyBlocksManualPaymentAndBranches() {
        val paywallFile = findSourceFile("src/main/java/com/example/ui/components/SmartMasterPaywallDialog.kt")
        val content = paywallFile.readText()

        // 1. Prohibited manual payment account, phone number, and recipient identifiers
        assertFalse("Paywall must not contain manual payment number", content.contains("085155076566"))
        assertFalse("Paywall must not contain manual payment account header", content.contains("Rekening Tujuan Pembayaran"))
        assertFalse("Paywall must not contain manual payment channel label", content.contains("DANA / E-Wallet"))
        assertFalse("Paywall must not contain Salin Nomor action", content.contains("Salin Nomor"))
        assertFalse("Paywall must not contain Konfirmasi Sudah Transfer action", content.contains("Konfirmasi Sudah Transfer"))

        // 2. Prohibited conditional branches that toggle manual payment
        assertFalse("Paywall must not contain isPlayStoreBuild branching flag", content.contains("isPlayStoreBuild"))

        // 3. Prohibited copy icon and test tags for manual payment
        assertFalse("Paywall must not contain btn_copy_dana test tag", content.contains("btn_copy_dana"))
        assertFalse("Paywall must not contain btn_confirm_transfer test tag", content.contains("btn_confirm_transfer"))
        assertFalse("Paywall must not import ContentCopy icon", content.contains("ContentCopy"))
    }

    @Test
    fun bug3_smartMasterPaywall_sourceContract_unconditionallyExposesGooglePlayBilling() {
        val paywallFile = findSourceFile("src/main/java/com/example/ui/components/SmartMasterPaywallDialog.kt")
        val content = paywallFile.readText()

        // Official Google Play Billing buttons and methods must be present
        assertTrue("Paywall must contain official Billing button test tag", content.contains("btn_google_play_billing"))
        assertTrue("Paywall must contain restore purchases button test tag", content.contains("btn_restore_purchases"))
        assertTrue("Paywall must call BillingManager.launchPurchaseFlow", content.contains("BillingManager.launchPurchaseFlow"))
        assertTrue("Paywall must call BillingManager.restorePurchases", content.contains("BillingManager.restorePurchases"))
    }

    @Test
    fun bug3_finSmartApp_sourceContract_hasNoManualTransferConfirmationToast() {
        val appFile = findSourceFile("src/main/java/com/example/ui/FinSmartApp.kt")
        val content = appFile.readText()

        assertFalse(
            "FinSmartApp must not contain manual transfer toast message",
            content.contains("Permintaan konfirmasi transfer berhasil dikirim")
        )
        assertFalse(
            "FinSmartApp must not contain admin verification toast message",
            content.contains("Admin akan segera memverifikasi")
        )
    }

    @Test
    fun bug3_googlePlayBilling_productIdsAndStateFlows_fullyProtected() {
        // Assert Google Play Billing product IDs are exact and immutable
        assertEquals("smart_master_monthly", BillingManager.PRODUCT_ID_MONTHLY)
        assertEquals("smart_master_yearly", BillingManager.PRODUCT_ID_YEARLY)
        assertEquals("smart_master_lifetime", BillingManager.PRODUCT_ID_LIFETIME)

        // Assert StateFlow price and status streams exist and are active
        assertNotNull(BillingManager.monthlyPrice)
        assertNotNull(BillingManager.yearlyPrice)
        assertNotNull(BillingManager.connectionStatus)
        assertNotNull(BillingManager.isProcessing)
    }

    @Test
    fun bug3_paidSubscriber_billingEntitlement_activeAndAdFree() {
        val now = System.currentTimeMillis()
        val paidUser = User(
            id = 202L,
            username = "SubscriberUser",
            email = "subscriber@example.com",
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = now + 30L * 86400000L, // 30 days remaining
            isGuest = false
        )

        val entitlement = EntitlementManager.evaluate(paidUser, now = now, explicitSource = EntitlementSource.PLAY_STORE_BILLING)
        assertTrue("Paid subscriber must have premium unlocked", entitlement.isPremiumUnlocked)
        assertFalse("Paid subscriber must not be marked expired", entitlement.isExpired)
        assertEquals(UserRole.SMART_MASTER, entitlement.effectiveRole)
        assertEquals(EntitlementSource.PLAY_STORE_BILLING, entitlement.primarySource)

        // Strict ad-free verification: Paid subscribers must NEVER see ads
        assertFalse("Paid SmartMaster must not see ads", AdManager.shouldShowAds(paidUser))
            }

    @Test
    fun bug3_rewardedAdExpiry_doesNotDowngradePaidSubscriber() {
        val now = System.currentTimeMillis()
        val originalExpiry = now + 15L * 86400000L // 15 days remaining on paid sub

        val paidSubscriber = User(
            id = 303L,
            username = "ActiveSubscriber",
            email = "active.sub@example.com",
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = originalExpiry,
            isGuest = false
        )

        // Even after 60 minutes or 2 hours have passed, paid sub remains active until originalExpiry
        val evaluatedAfter2Hours = EntitlementManager.evaluate(paidSubscriber, now = now + 7200000L, explicitSource = EntitlementSource.PLAY_STORE_BILLING)
        assertTrue("Paid subscriber remains unlocked after 2 hours", evaluatedAfter2Hours.isPremiumUnlocked)
        assertFalse("Paid subscriber is not expired", evaluatedAfter2Hours.isExpired)
        assertEquals(UserRole.SMART_MASTER, evaluatedAfter2Hours.effectiveRole)
        assertEquals(EntitlementSource.PLAY_STORE_BILLING, evaluatedAfter2Hours.primarySource)

        // Evaluation exactly at or past original expiry
        val evaluatedPastExpiry = EntitlementManager.evaluate(paidSubscriber, now = originalExpiry + 1000L)
        assertFalse("Expired subscription must lock premium", evaluatedPastExpiry.isPremiumUnlocked)
        assertTrue("Expired subscription must be marked expired", evaluatedPastExpiry.isExpired)
        assertEquals(UserRole.FREE_MEMBER, evaluatedPastExpiry.effectiveRole)
    }
}
