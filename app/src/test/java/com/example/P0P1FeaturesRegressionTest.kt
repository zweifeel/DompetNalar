package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinSmartDatabase
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.data.repository.FinanceRepository
import com.example.data.session.SessionManager
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.FeedbackViewModel
import com.example.util.DriveAuthResult
import com.example.util.GoogleDriveService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class P0P1FeaturesRegressionTest {

    private lateinit var app: Application
    private lateinit var db: FinSmartDatabase
    private lateinit var financeRepo: FinanceRepository
    private lateinit var authViewModel: AuthViewModel

    private val testUser1Id = 501L
    private val testUser2Id = 502L

    @Before
    fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        financeRepo = FinanceRepository(walletDao = db.walletDao(), database = db)
        authViewModel = AuthViewModel(app)
        ShadowLooper.idleMainLooper()

        // Seed users
        db.userDao().insertUser(User(id = testUser1Id, username = "User One", email = "user1@example.com", role = UserRole.FREE_MEMBER))
        db.userDao().insertUser(User(id = testUser2Id, username = "User Two", email = "user2@example.com", role = UserRole.FREE_MEMBER))

        // Reset Session
        SessionManager.logout()
        com.example.data.local.DatabaseWriteCoordinator.resetForTesting()
    }

    @After
    fun tearDown() {
        com.example.data.local.DatabaseWriteCoordinator.resetForTesting()
        db.close()
        SessionManager.logout()
    }

    // =========================================================================
    // P0: AUTOMATIC DEFAULT WALLET PROVISIONING TESTS
    // =========================================================================

    @Test
    fun testDefaultWalletProvisioningOnNewUser_neutralizedInPhase1G() = runBlocking {
        // Phase 1G: Auto-provisioning is neutralized. New users start with 0 wallets.
        assertEquals(0, db.walletDao().getWalletCount(testUser1Id))

        val provisioned = financeRepo.provisionDefaultWalletsIfEmpty(testUser1Id)
        assertEquals(0, provisioned.size)

        val dbWallets = db.walletDao().getAllWallets(testUser1Id).first()
        assertEquals(0, dbWallets.size)
    }

    @Test
    fun testDefaultWalletProvisioningIsIdempotent() = runBlocking {
        val firstBatch = financeRepo.provisionDefaultWalletsIfEmpty(testUser1Id)
        assertEquals(0, firstBatch.size)

        val secondBatch = financeRepo.provisionDefaultWalletsIfEmpty(testUser1Id)
        assertEquals(0, secondBatch.size)

        val allWallets = db.walletDao().getAllWallets(testUser1Id).first()
        assertEquals(0, allWallets.size)
    }

    @Test
    fun testSeparateUsersReceiveSeparateWallets() = runBlocking {
        financeRepo.insertWallet(Wallet(name = "User 1 Wallet", type = WalletType.BANK, balance = 0.0, userId = testUser1Id))
        financeRepo.insertWallet(Wallet(name = "User 2 Wallet", type = WalletType.BANK, balance = 0.0, userId = testUser2Id))

        val walletsUser1 = db.walletDao().getAllWallets(testUser1Id).first()
        val walletsUser2 = db.walletDao().getAllWallets(testUser2Id).first()

        assertEquals(1, walletsUser1.size)
        assertEquals(1, walletsUser2.size)
        assertTrue(walletsUser1.all { it.userId == testUser1Id })
        assertTrue(walletsUser2.all { it.userId == testUser2Id })

        // Verify IDs do not collide
        val user1WalletIds = walletsUser1.map { it.id }.toSet()
        val user2WalletIds = walletsUser2.map { it.id }.toSet()
        assertTrue(user1WalletIds.intersect(user2WalletIds).isEmpty())
    }

    @Test
    fun testTransactionInsertRejectsUnownedWallet() = runBlocking {
        val u1wId = financeRepo.insertWallet(Wallet(name = "User 1 Wallet", type = WalletType.BANK, balance = 100000.0, userId = testUser1Id))
        val u2wId = financeRepo.insertWallet(Wallet(name = "User 2 Wallet", type = WalletType.BANK, balance = 200000.0, userId = testUser2Id))

        val user1Wallet = db.walletDao().getWalletById(id = u1wId, userId = testUser1Id)!!
        val user2Wallet = db.walletDao().getWalletById(id = u2wId, userId = testUser2Id)!!

        val initialBalance = user2Wallet.balance

        // Attempt to insert a transaction scoped to user 1 using user 2's wallet
        val invalidTx = TransactionItem(
            title = "Unauthorized Tx",
            amount = 50000.0,
            type = TransactionType.EXPENSE,
            category = "Makan",
            categoryIcon = "ic_food",
            walletId = user2Wallet.id, // belongs to user2!
            walletName = user2Wallet.name,
            timestamp = System.currentTimeMillis(),
            userId = testUser1Id // belongs to user1!
        )

        try {
            financeRepo.insertTransaction(userId = testUser1Id, transaction = invalidTx)
            fail("Expected IllegalArgumentException when saving transaction with unowned wallet")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("tidak ditemukan atau bukan milik"))
        }

        // Verify balance was untouched
        val reloadedUser2Wallet = db.walletDao().getWalletById(user2Wallet.id, testUser2Id)
        assertNotNull(reloadedUser2Wallet)
        assertEquals(initialBalance, reloadedUser2Wallet!!.balance, 0.001)
    }

    // =========================================================================
    // P0: 7-DAY STREAK MILESTONE REWARD 168-HOUR ANTI-ABUSE TESTS
    // =========================================================================

    @Test
    fun testStreakRewardInitialEligibilityAnd168HourLockout() {
        val prefs = app.getSharedPreferences("finsmart_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        val user = User(id = testUser1Id, username = "Streaker", email = "streaker@gmail.com", role = UserRole.FREE_MEMBER)
        SessionManager.login(user)

        // Ensure streak is set to 7 in gamification prefs
        prefs.edit().putInt("gamify_streak", 7).putBoolean("gamify_streak_initialized", true).apply()
        val vmStreak7 = AuthViewModel(app)

        // 1. Initial claim must be eligible
        assertTrue(vmStreak7.canClaimStreakReward(user))
        assertEquals(0L, vmStreak7.getStreakRewardRemainingHours(user))

        // 2. Claim reward
        val initialCredits = vmStreak7.singleFeaturePassCredits.value
        vmStreak7.grantStreakMilestoneReward(durationHours = 24, actionPassCredits = 3)

        // Verify credits granted & role upgraded
        assertEquals(initialCredits + 3, vmStreak7.singleFeaturePassCredits.value)
        assertEquals(UserRole.SMART_MASTER, SessionManager.currentUser.value?.role)

        // 3. Immediate repeat claim must be rejected!
        assertFalse(vmStreak7.canClaimStreakReward(user))
        val remainingHours = vmStreak7.getStreakRewardRemainingHours(user)
        assertTrue("Remaining hours should be > 0 and <= 168, got $remainingHours", remainingHours in 1..168)

        // Attempting to call grantStreakMilestoneReward again must do nothing
        val creditsAfterRepeat = vmStreak7.singleFeaturePassCredits.value
        vmStreak7.grantStreakMilestoneReward(durationHours = 24, actionPassCredits = 3)
        assertEquals(creditsAfterRepeat, vmStreak7.singleFeaturePassCredits.value)

        // 4. Fast forward time by 169 hours (simulated)
        val claimKey = "streak_reward_last_claimed_$testUser1Id"
        val pastTimestamp = System.currentTimeMillis() - (169L * 3600000L)
        prefs.edit().putLong(claimKey, pastTimestamp).apply()

        // Must now be eligible again!
        assertTrue(vmStreak7.canClaimStreakReward(user))
        assertEquals(0L, vmStreak7.getStreakRewardRemainingHours(user))
    }

    @Test
    fun testStreakRewardFutureTimestampAntiTamper() {
        val prefs = app.getSharedPreferences("finsmart_auth_prefs", Context.MODE_PRIVATE)
        val user = User(id = testUser1Id, username = "Streaker", email = "streaker@gmail.com", role = UserRole.FREE_MEMBER)
        SessionManager.login(user)
        prefs.edit().putInt("gamify_streak", 7).apply()

        // Clock tampering: stored timestamp is in future
        val claimKey = "streak_reward_last_claimed_$testUser1Id"
        prefs.edit().putLong(claimKey, System.currentTimeMillis() + 1000000L).apply()

        // Must reject claim due to future clock tampering
        assertFalse(authViewModel.canClaimStreakReward(user))
    }

    @Test
    fun testStreakRewardUnderThresholdIneligible() {
        val prefs = app.getSharedPreferences("finsmart_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        val user = User(id = testUser1Id, username = "LowStreak", email = "low@gmail.com", role = UserRole.FREE_MEMBER)
        SessionManager.login(user)

        // Recreate AuthViewModel so it picks up fresh streak = 3
        prefs.edit().putInt("gamify_streak", 3).apply()
        val freshVm = AuthViewModel(app)

        assertFalse(freshVm.canClaimStreakReward(user))
    }

    // =========================================================================
    // P1: GOOGLE DRIVE TOKEN ACQUISITION DISPATCHER TEST
    // =========================================================================

    @Test
    fun testDriveAccessTokenErrorHandlingOnBlankEmail() = runBlocking {
        val result = GoogleDriveService.getDriveAccessToken(app, "")
        assertTrue(result is DriveAuthResult.Error)
        assertTrue((result as DriveAuthResult.Error).message.contains("belum diatur"))
    }

    // =========================================================================
    // P2: CENTRALIZED FEEDBACK VALIDATION TESTS
    // =========================================================================

    @Test
    fun testFeedbackCategoryAndLengthConstants() {
        assertEquals(1000, FeedbackViewModel.MAX_MESSAGE_LENGTH)
        assertTrue(FeedbackViewModel.ALLOWED_CATEGORIES.contains("Bug / Kendala"))
        assertTrue(FeedbackViewModel.ALLOWED_CATEGORIES.contains("Saran Fitur"))
        assertTrue(FeedbackViewModel.ALLOWED_CATEGORIES.contains("Desain / UI"))
        assertTrue(FeedbackViewModel.ALLOWED_CATEGORIES.contains("Lainnya"))
    }

    // =========================================================================
    // REGRESSION TESTS: CRITICAL P0/P1 9 AUDIT FIXES
    // =========================================================================

    @Test
    fun testBug1_GuestLoginAndLogoutClearsAllPassCredits() {
        val prefs = app.getSharedPreferences("finsmart_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("single_feature_credits", 5).commit()

        // Guest login must reset pass credits to 0
        authViewModel.loginAsGuest()
        ShadowLooper.idleMainLooper()
        assertEquals(0, authViewModel.singleFeaturePassCredits.value)

        // Logout must also reset pass credits to 0 and remove from prefs
        val testContext = object : android.content.ContextWrapper(app) {
            override fun getSystemService(name: String): Any? {
                if (name == Context.CREDENTIAL_SERVICE || name == "credential") {
                    throw UnsupportedOperationException("Robolectric mock: CredentialManager disabled")
                }
                return super.getSystemService(name)
            }
        }
        var completed = false
        authViewModel.logout(context = testContext, onComplete = { completed = true })
        ShadowLooper.idleMainLooper()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertTrue("authViewModel.logout should complete", completed)
        assertEquals(0, authViewModel.singleFeaturePassCredits.value)
        assertFalse(prefs.contains("single_feature_credits"))
    }

    @Test
    fun testBug8_StreakReward30DaysGrants24HourSmartMemberAndBlocksGuest() {
        val prefs = app.getSharedPreferences("finsmart_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        // Guest cannot claim
        val guest = User(id = 888L, username = "Tamu", email = "guest@finsmart.local", role = UserRole.FREE_MEMBER, isGuest = true)
        assertFalse("Guest user tidak boleh bisa klaim streak 30 hari", authViewModel.canClaim30DayStreakReward(guest))

        // Authenticated user with streak < 30 cannot claim
        val normalUser = User(id = 889L, username = "Normal", email = "normal@gmail.com", role = UserRole.FREE_MEMBER, isGuest = false)
        SessionManager.login(normalUser)
        prefs.edit().putInt("gamify_streak", 15).apply()
        assertFalse("Streak 15 hari belum memenuhi syarat 30 hari", authViewModel.canClaim30DayStreakReward(normalUser))

        // Authenticated user with streak >= 30 CAN claim
        prefs.edit().putInt("gamify_streak", 30).apply()
        val vmStreak30 = AuthViewModel(app)
        assertTrue("Streak 30 hari harus bisa klaim", vmStreak30.canClaim30DayStreakReward(normalUser))

        // Claim reward (24 hours)
        val initialRole = SessionManager.currentUser.value?.role
        vmStreak30.grant30DayStreakMilestoneReward(durationHours = 24)

        // Role should be SMART_MASTER during the 24 hours
        assertEquals(UserRole.SMART_MASTER, SessionManager.currentUser.value?.role)

        // Cannot claim again immediately (anti-abuse cooldown 30 days)
        assertFalse("Klaim ulang langsung harus ditolak", vmStreak30.canClaim30DayStreakReward(normalUser))
        val remainingHours = vmStreak30.get30DayStreakRewardRemainingHours(normalUser)
        assertTrue("Cooldown streak 30 hari harus > 0 dan <= 720 jam", remainingHours in 1..720)
    }

    @Test
    fun testBug3_SecurityPinsAreHashedWithPBKDF2AndWipePlaintext() {
        authViewModel = AuthViewModel(app)
        ShadowLooper.idleMainLooper()
        val prefs = app.getSharedPreferences("finsmart_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        val mainPin = "123456"
        val duressPin = "654321"

        authViewModel.saveSecurityPins(mainPin = mainPin, duressPin = duressPin, biometricEnabled = false)

        // Plaintext must NOT be saved
        assertFalse(prefs.contains("security_main_pin"))
        assertFalse(prefs.contains("security_duress_pin"))

        // Hashes must be present
        assertTrue(prefs.contains("security_main_pin_hash"))
        assertTrue(prefs.contains("security_duress_pin_hash"))

        val mainHash = prefs.getString("security_main_pin_hash", null)
        val duressHash = prefs.getString("security_duress_pin_hash", null)
        assertNotNull(mainHash)
        assertNotNull(duressHash)
        assertFalse(mainHash!!.contains(mainPin))
        assertFalse(duressHash!!.contains(duressPin))

        // Verification: Normal PIN
        authViewModel.verifyPinAndUnlock("123456")
        assertFalse("Normal PIN tidak boleh mengaktifkan stealth mode", authViewModel.gamificationState.value.isStealthMode)
        assertEquals("✓ Keamanan Diverifikasi! Mode Asli Aktif", authViewModel.isSuccessToast.value)

        // Verification: Duress PIN
        authViewModel.verifyPinAndUnlock("654321")
        assertTrue("Duress PIN harus mengaktifkan stealth mode", authViewModel.gamificationState.value.isStealthMode)
        assertEquals("⚠️ DompetNalar Masuk Mode Stealth (Duress PIN Mode)", authViewModel.isSuccessToast.value)

        // Verification: Wrong PIN
        authViewModel.verifyPinAndUnlock("999999")
        assertEquals("❌ PIN Salah!", authViewModel.isSuccessToast.value)
    }

    @Test
    fun testBug3_FinanceViewModelStealthModeCamouflagesData() = runBlocking {
        val financeViewModel = com.example.ui.viewmodel.FinanceViewModel(app)

        // Initially not stealth
        financeViewModel.setStealthMode(false)
        assertEquals(false, financeViewModel.isStealthMode.value)

        // Switch to stealth mode
        financeViewModel.setStealthMode(true)
        assertEquals(true, financeViewModel.isStealthMode.value)

        // In stealth mode, wallets should contain only camouflage wallet with balance 25.000
        val stealthWallets = financeViewModel.wallets.first()
        assertEquals(1, stealthWallets.size)
        assertEquals("Uang Saku", stealthWallets.first().name)
        assertEquals(25000.0, stealthWallets.first().balance, 0.001)

        // Transactions should be camouflage
        val stealthTxs = financeViewModel.allTransactions.first()
        assertEquals(2, stealthTxs.size)
        assertEquals(15000.0, stealthTxs[0].amount, 0.001)
        assertEquals(2000.0, stealthTxs[1].amount, 0.001)

        // Debts, installments, assets must be empty
        assertEquals(0, financeViewModel.debts.first().size)
        assertEquals(0, financeViewModel.installmentPlans.first().size)
        assertEquals(0, financeViewModel.assets.first().size)
    }

    @Test
    fun testBug2_BillingManagerBlocksNewLifetimePurchases() {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val result = com.example.util.BillingManager.launchPurchaseFlow(
            activity = activity,
            productId = com.example.util.BillingManager.PRODUCT_ID_LIFETIME
        )

        assertEquals(com.android.billingclient.api.BillingClient.BillingResponseCode.DEVELOPER_ERROR, result.responseCode)
        assertTrue(result.debugMessage.contains("tidak lagi tersedia"))
    }
}

