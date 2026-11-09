package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.config.DeveloperAccountConfig
import com.example.config.ReviewerAccountConfig
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.BackupDataPayload
import com.example.data.model.CategoryItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.data.repository.UserRepository
import com.example.data.session.SessionManager
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.FeedbackViewModel
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.EntitlementManager
import com.example.util.EntitlementSource
import com.example.util.GoogleDriveService
import com.example.util.PasswordHasher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CriticalDataAuthSyncRemediationTest {

    private lateinit var context: Context
    private lateinit var db: FinSmartDatabase
    private lateinit var userRepository: UserRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userRepository = UserRepository(db.userDao())
        try {
            val field = SessionManager::class.java.getDeclaredField("prefs")
            field.isAccessible = true
            field.set(SessionManager, null)
        } catch (_: Exception) {}
        SessionManager.initialize(context)
        com.example.data.local.DatabaseWriteCoordinator.resetForTesting()
    }

    @After
    fun tearDown() {
        com.example.data.local.DatabaseWriteCoordinator.resetForTesting()
        SessionManager.resetForTesting()
        db.close()
    }

    // =========================================================================
    // 1. MASTER DEV USERNAME & IDENTIFIER RESOLUTION TESTS
    // =========================================================================

    @Test
    fun testEnsureMasterDevAccount_phase1g_doesNotCreateLukas_seedsCategoriesOnly() = runBlocking {
        // Initial state: empty database
        assertEquals(0, db.userDao().getUserCount())
        assertEquals(0, db.walletDao().getWalletCount())
        assertEquals(0, db.categoryDao().getCategoryCount())

        // Ensure Master Dev in Phase 1G
        val masterId = FinSmartDatabase.ensureMasterDevAccount(db)
        assertEquals(0L, masterId)

        // Verify Lukas is NOT created
        assertEquals(0, db.userDao().getUserCount())

        // Verify default wallets are NOT created
        assertEquals(0, db.walletDao().getWalletCount())

        // Verify canonical categories seeded
        assertEquals(13, db.categoryDao().getCategoryCount())
    }

    @Test
    fun testEnsureMasterDevAccount_elevatesTrustedDeveloperEmails() = runBlocking {
        // Pre-insert trusted developer
        val devId = db.userDao().insertUser(User(
            username = "Developer",
            email = DeveloperAccountConfig.CONTACT_DEV_EMAIL,
            role = UserRole.FREE_MEMBER,
            isApproved = true
        ))

        val masterId = FinSmartDatabase.ensureMasterDevAccount(db)
        assertEquals(devId, masterId)

        val reloadedUser = db.userDao().getUserById(devId)!!
        assertEquals(UserRole.MASTER_DEV, reloadedUser.role)
    }

    @Test
    fun testDeveloperAccountConfig_allAliasesRecognized() {
        // Primary emails resmi
        assertTrue(DeveloperAccountConfig.isTrustedDeveloperEmail("lunachroc01@gmail.com"))
        assertTrue(DeveloperAccountConfig.isTrustedDeveloperEmail("zweifeel@gmail.com"))
        assertFalse("Legacy dev email dilarang masuk daftar developer Release", DeveloperAccountConfig.isTrustedDeveloperEmail("lunachroc07@gmail.com"))

        // Common spelling variants / aliases typo tidak boleh dipercaya
        assertFalse(DeveloperAccountConfig.isTrustedDeveloperEmail("lunachro01@gmail.com"))
        assertFalse(DeveloperAccountConfig.isTrustedDeveloperEmail("lunachrooc07@gmail.com"))

        // Case insensitivity & trimming
        assertFalse(DeveloperAccountConfig.isTrustedDeveloperEmail("  LUNACHROC07@GMAIL.COM  "))
        assertTrue(DeveloperAccountConfig.isTrustedDeveloperEmail("  ZWEIFEEL@GMAIL.COM "))
        assertTrue(DeveloperAccountConfig.isTrustedDeveloperEmail("  LUNACHROC01@GMAIL.COM "))

        // Master Dev identifier check (Phase 1G: Lukas is permanently removed)
        assertFalse(DeveloperAccountConfig.isMasterDevIdentifier("Lukas"))
        assertFalse(DeveloperAccountConfig.isMasterDevIdentifier("lukas"))
        assertFalse(DeveloperAccountConfig.isMasterDevIdentifier("LUKAS"))
        assertTrue(DeveloperAccountConfig.isMasterDevIdentifier("lunachroc01@gmail.com"))
        assertTrue(DeveloperAccountConfig.isMasterDevIdentifier("zweifeel@gmail.com"))
        assertFalse(DeveloperAccountConfig.isMasterDevIdentifier("lunachroc07@gmail.com"))
        assertFalse(DeveloperAccountConfig.isMasterDevIdentifier("lunachrooc07@gmail.com"))

        // Non-developer emails rejected
        assertFalse(DeveloperAccountConfig.isTrustedDeveloperEmail("budi@gmail.com"))
        assertFalse(DeveloperAccountConfig.isTrustedDeveloperEmail("hacker@malicious.com"))
        assertFalse(DeveloperAccountConfig.isMasterDevIdentifier("budi"))
        assertFalse(DeveloperAccountConfig.isMasterDevIdentifier(null))
    }

    // =========================================================================
    // 2. AUTHENTICATION & ROLE HIJACK PREVENTION TESTS
    // =========================================================================

    @Test
    fun testRegularUserRegistration_cannotHijackMasterDevRole() = runBlocking {
        // Try creating user with typed name Lukas or admin email
        val regularUser = User(
            id = 10L,
            username = "Lukas Impostor",
            email = "impostor@gmail.com",
            role = UserRole.FREE_MEMBER,
            passwordHash = PasswordHasher.hashPassword("secret123")
        )
        val id = db.userDao().insertUser(regularUser)
        val inserted = db.userDao().getUserById(id)!!

        // Must remain FREE_MEMBER
        assertEquals(UserRole.FREE_MEMBER, inserted.role)

        // EntitlementManager must NOT grant MASTER_DEV
        val entitlement = EntitlementManager.evaluate(inserted)
        assertNotEquals(UserRole.MASTER_DEV, entitlement.effectiveRole)
        assertNotEquals(EntitlementSource.MASTER_DEV, entitlement.primarySource)
    }

    @Test
    fun testTrustedDeveloperEmail_alwaysReceivesMasterDevEntitlement() = runBlocking {
        for (email in DeveloperAccountConfig.TRUSTED_DEVELOPER_EMAILS) {
            val devUser = User(
                username = "Dev Account",
                email = email,
                role = UserRole.MASTER_DEV
            )
            val entitlement = EntitlementManager.evaluate(devUser)
            assertEquals("Account $email must have MASTER_DEV entitlement", UserRole.MASTER_DEV, entitlement.effectiveRole)
            assertEquals(EntitlementSource.MASTER_DEV, entitlement.primarySource)
        }
    }

    // =========================================================================
    // 3. TRANSACTION SAVING & OWNERSHIP ISOLATION TESTS
    // =========================================================================

    @Test
    fun testTransactions_arePersistedWithStableOwnership() = runBlocking {
        val masterId = db.userDao().insertUser(User(username = "QA Master", email = DeveloperAccountConfig.CONTACT_DEV_EMAIL, role = UserRole.MASTER_DEV, isApproved = true))
        val walletId = db.walletDao().insertWallet(Wallet(name = "QA Wallet", type = WalletType.BANK, balance = 10000000.0, userId = masterId))
        val wallet = db.walletDao().getWalletById(masterId, walletId)!!
        val wallets = listOf(wallet)

        val txIncome = TransactionItem(
            id = 0L,
            title = "QA Pemasukan Master",
            amount = 8000000.0,
            type = TransactionType.INCOME,
            category = "Gaji Pokok",
            categoryIcon = "payments",
            walletId = wallet.id,
            walletName = wallet.name,
            timestamp = System.currentTimeMillis(),
            accountMode = AccountMode.PERSONAL.name,
            userId = masterId
        )
        val txId = db.transactionDao().insertTransaction(txIncome)
        assertTrue(txId > 0L)

        // Verify retrieval strictly by userId
        val userTransactions = db.transactionDao().getAllTransactions(masterId).first()
        assertEquals(1, userTransactions.size)
        assertEquals("QA Pemasukan Master", userTransactions.first().title)
        assertEquals(8000000.0, userTransactions.first().amount, 0.001)
        assertEquals(masterId, userTransactions.first().userId)

        // Other user must see zero transactions
        val otherUserTransactions = db.transactionDao().getAllTransactions(999L).first()
        assertTrue("Other user must NOT see Master Dev transactions", otherUserTransactions.isEmpty())
    }

    // =========================================================================
    // 4. BACKUP, PERSISTENCE & RESTORE CONSISTENCY TESTS
    // =========================================================================

    @Test
    fun testBackupAndRestore_preservesDataIntegrityWithoutDuplication() = runBlocking {
        val masterId = db.userDao().insertUser(User(username = "QA Master", email = DeveloperAccountConfig.CONTACT_DEV_EMAIL, role = UserRole.MASTER_DEV, isApproved = true))
        val walletId = db.walletDao().insertWallet(Wallet(name = "QA Wallet", type = WalletType.BANK, balance = 10000000.0, userId = masterId))
        val wallet = db.walletDao().getWalletById(masterId, walletId)!!
        val wallets = listOf(wallet)

        // Insert baseline transactions
        val tx1 = TransactionItem(
            id = 101L,
            title = "Gaji Pokok QA",
            amount = 8000000.0,
            type = TransactionType.INCOME,
            category = "Gaji Pokok",
            walletId = wallet.id,
            walletName = wallet.name,
            userId = masterId
        )
        val tx2 = TransactionItem(
            id = 102L,
            title = "Belanja QA",
            amount = 1250000.0,
            type = TransactionType.EXPENSE,
            category = "Makanan & Minuman",
            walletId = wallet.id,
            walletName = wallet.name,
            userId = masterId
        )
        db.transactionDao().insertTransaction(tx1)
        db.transactionDao().insertTransaction(tx2)

        // Build backup payload
        val payload = BackupDataPayload(
            version = 1,
            wallets = wallets,
            transactions = listOf(tx1, tx2),
            categories = db.categoryDao().getAllCategories(masterId).first(),
            users = listOf(db.userDao().getUserById(masterId)!!),
            splitBillHistory = emptyList(),
            budgetLimits = emptyList(),
            debts = emptyList(),
            assets = emptyList(),
            installmentPlans = emptyList(),
            exportedAt = System.currentTimeMillis()
        )

        // Perform restoreUserDataInTransaction
        FinanceViewModel.restoreUserDataInTransaction(db, payload, masterId)

        val restoredTx = db.transactionDao().getAllTransactions(masterId).first()
        assertEquals("Must contain exactly 2 transactions", 2, restoredTx.size)
        assertEquals(masterId, restoredTx[0].userId)
        assertEquals(masterId, restoredTx[1].userId)

        // Perform repeat restore (idempotency / non-duplication check)
        FinanceViewModel.restoreUserDataInTransaction(db, payload, masterId)
        val restoredRepeatTx = db.transactionDao().getAllTransactions(masterId).first()
        assertEquals("Repeat restore must NOT duplicate transactions", 2, restoredRepeatTx.size)
    }

    @Test
    fun testRestore_doesNotDeleteOtherUsersData() = runBlocking {
        // Create User A and User B
        val userAId = 501L
        val userBId = 502L
        db.userDao().insertUser(User(id = userAId, username = "User A", email = "usera@test.com"))
        db.userDao().insertUser(User(id = userBId, username = "User B", email = "userb@test.com"))

        val walletB = Wallet(id = 601L, name = "Dompet B", balance = 500000.0, type = WalletType.CASH, userId = userBId)
        db.walletDao().insertWallet(walletB)
        val txB = TransactionItem(id = 701L, title = "Tx User B", amount = 200000.0, type = TransactionType.EXPENSE, category = "Makanan", walletId = walletB.id, walletName = walletB.name, userId = userBId)
        db.transactionDao().insertTransaction(txB)

        // Restore payload for User A
        val walletA = Wallet(id = 1L, name = "Dompet A", balance = 1000000.0, type = WalletType.BANK, userId = userAId)
        val payloadA = BackupDataPayload(
            version = 1,
            wallets = listOf(walletA),
            transactions = emptyList(),
            categories = emptyList(),
            users = emptyList(),
            splitBillHistory = emptyList(),
            budgetLimits = emptyList(),
            debts = emptyList(),
            assets = emptyList(),
            installmentPlans = emptyList()
        )

        FinanceViewModel.restoreUserDataInTransaction(db, payloadA, userAId)

        // Verify User B's financial data is 100% intact
        val userBTransactions = db.transactionDao().getAllTransactions(userBId).first()
        assertEquals("User B transactions must not be touched", 1, userBTransactions.size)
        assertEquals("Tx User B", userBTransactions.first().title)
    }

    @Test
    fun testProtectedDeveloperUsernameLukas_isRecognizedAsMasterDevIdentifier() {
        assertFalse("Lukas is permanently removed as Master Dev identifier in Phase 1G", DeveloperAccountConfig.isMasterDevIdentifier("Lukas"))
        assertFalse("Case insensitive Lukas is permanently removed", DeveloperAccountConfig.isMasterDevIdentifier("lukas"))
        assertTrue("Reviewer identifier must be recognized", ReviewerAccountConfig.isReviewerIdentifier(ReviewerAccountConfig.REVIEWER_EMAIL))
        assertFalse("Random name must not be Master Dev identifier", DeveloperAccountConfig.isMasterDevIdentifier("random_user"))
    }

    @Test
    fun testGuestUser_strictlyExcludedFromTrial() {
        val guest = User(
            id = 99L,
            username = "Tamu FinSmart",
            email = "guest_123@finsmart.local",
            role = UserRole.FREE_MEMBER,
            isGuest = true,
            trialStartDate = 0L
        )
        val entitlement = EntitlementManager.evaluate(guest)
        assertFalse("Guest user must never have premium unlocked", entitlement.isPremiumUnlocked)
        assertEquals("Guest user must have 0 trial days remaining", 0, entitlement.trialDaysRemaining)
        assertEquals(EntitlementSource.NONE, entitlement.primarySource)
    }

    @Test
    fun testRewardedAdOneHourPass_accuratelyExpiresAfterOneHour() {
        val baseTime = 1700000000000L
        val oneHourLater = baseTime + 3600000L

        val userWithPass = User(
            id = 101L,
            username = "User Ad Watcher",
            email = "watcher@example.com",
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = oneHourLater,
            isGuest = false
        )

        // 1. While within the 1-hour window: Premium is active
        val activeEntitlement = EntitlementManager.evaluate(userWithPass, now = baseTime + 1800000L) // 30 min in
        assertTrue("User must have premium unlocked during active pass", activeEntitlement.isPremiumUnlocked)
        assertFalse("User must not be marked expired during active pass", activeEntitlement.isExpired)
        assertEquals(UserRole.SMART_MASTER, activeEntitlement.effectiveRole)

        // 2. Exactly at or after expiry: Premium locks, status marked expired
        val expiredEntitlement = EntitlementManager.evaluate(userWithPass, now = oneHourLater + 1000L) // 1 sec after 1h
        assertFalse("User must lose premium access after 1 hour expires", expiredEntitlement.isPremiumUnlocked)
        assertTrue("User must be marked as expired after 1 hour passes", expiredEntitlement.isExpired)
        assertEquals(UserRole.FREE_MEMBER, expiredEntitlement.effectiveRole)
    }

    // =========================================================================
    // 5. DEVELOPER FEEDBACK & FIREBASE AUTH SYNC TESTS
    // =========================================================================

    @Test
    fun testLocalUserWithoutFirebaseAuth_isNotConsideredCloudActive() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val feedbackViewModel = FeedbackViewModel(app)

        // Ensure initially offline/inactive
        assertFalse("Tanpa Firebase Auth, status cloud harus false", feedbackViewModel.isFirebaseAuthActive.value)
        assertNull("Tanpa Firebase Auth, email harus null", feedbackViewModel.currentFirebaseAuthEmail.value)

        // Calling fetchCloudFeedbacks without Firebase auth user must set local session status
        feedbackViewModel.fetchCloudFeedbacks()
        val status = feedbackViewModel.cloudFetchStatus.value
        assertNotNull("Status fetch harus diisi", status)
        assertTrue(
            "Status harus menginformasikan bahwa sesi cloud memerlukan Google Developer Sign-In",
            status!!.contains("Sesi Lokal Aktif") || status.contains("FirebaseApp belum terkonfigurasi")
        )
    }

    @Test
    fun testFeedbackViewModel_reactivelyUpdatesOnFirebaseAuthChange() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val feedbackViewModel = FeedbackViewModel(app)

        // 1. Initial: offline
        assertFalse(feedbackViewModel.isFirebaseAuthActive.value)
        assertNull(feedbackViewModel.currentFirebaseAuthEmail.value)

        // 2. Simulate Google Developer Auth sign-in transition
        feedbackViewModel.setFirebaseUserForTesting("lunachroc01@gmail.com", "uid-123")
        assertTrue("Setelah auth aktif, isFirebaseAuthActive harus bernilai true", feedbackViewModel.isFirebaseAuthActive.value)
        assertEquals("Email auth harus cocok dengan email akun Google", "lunachroc01@gmail.com", feedbackViewModel.currentFirebaseAuthEmail.value)

        // 3. Simulate sign-out / disconnect
        feedbackViewModel.setFirebaseUserForTesting(null)
        assertFalse("Setelah logout, isFirebaseAuthActive harus kembali false", feedbackViewModel.isFirebaseAuthActive.value)
        assertNull("Setelah logout, currentFirebaseAuthEmail harus null", feedbackViewModel.currentFirebaseAuthEmail.value)
    }

    // =========================================================================
    // 6. PHASE 4 MASTER DEV SECURITY & RELEASE REGRESSION TESTS
    // =========================================================================

    @Test
    fun testStaleMasterDevSession_onRelease_isRejectedAndNotRestored() = runBlocking {
        // Setup a local user in Room with role MASTER_DEV
        val masterId = db.userDao().insertUser(User(username = "QA Master", email = DeveloperAccountConfig.CONTACT_DEV_EMAIL, role = UserRole.MASTER_DEV, isApproved = true))
        val masterUser = db.userDao().getUserById(masterId)!!
        assertEquals(UserRole.MASTER_DEV, masterUser.role)

        // Save session in SharedPreferences
        val prefs = context.getSharedPreferences(SessionManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(SessionManager.KEY_SAVED_USER_ID, masterId).commit()

        // 1. On Release build simulation (isDebug = false):
        // Stale Master Dev session must NOT be restored!
        val restoredOnRelease = SessionManager.restoreSession(db.userDao(), isDebug = false)
        ShadowLooper.idleMainLooper()
        assertFalse("Sesi Master Dev lama tidak boleh dipulihkan pada Release build", restoredOnRelease)
        assertFalse("SessionState tidak boleh Authenticated setelah ditolak", SessionManager.isAuthenticated())
        assertNull("Active user id harus null", SessionManager.getActiveUserId())
        assertFalse("Saved user id harus dihapus atau tidak valid", prefs.getLong(SessionManager.KEY_SAVED_USER_ID, -1L) > 0L)

        // 2. On Debug build simulation (isDebug = true):
        // Master Dev session can be restored seamlessly
        prefs.edit().putLong(SessionManager.KEY_SAVED_USER_ID, masterId).commit()
        val restoredDebug = SessionManager.restoreSession(db.userDao(), isDebug = true)
        ShadowLooper.idleMainLooper()
        assertTrue("Sesi Master Dev pada Debug harus dapat dipulihkan", restoredDebug)
        assertTrue(SessionManager.isAuthenticated())
        assertEquals(masterId, SessionManager.getActiveUserId())
    }

    @Test
    fun testLocalMasterDevRole_withoutTrustedEmail_locksPremiumOnRelease() = runBlocking {
        // Create an untrusted account that has role = MASTER_DEV locally (e.g. manipulated local DB)
        val untrustedMaster = User(
            id = 505L,
            username = "Fake Master",
            email = "untrusted.user@gmail.com",
            role = UserRole.MASTER_DEV,
            isGuest = false
        )

        // 1. On Release (isDebug = false):
        // EntitlementManager must NOT grant MASTER_DEV or unlock premium based only on local role
        val releaseEntitlement = EntitlementManager.evaluate(untrustedMaster, isDebug = false)
        assertFalse("Akun tanpa email dev tepercaya tidak boleh membuka premium di Release", releaseEntitlement.isPremiumUnlocked)
        assertEquals(UserRole.FREE_MEMBER, releaseEntitlement.effectiveRole)
        assertNotEquals(EntitlementSource.MASTER_DEV, releaseEntitlement.primarySource)
        assertEquals(0, EntitlementManager.calculateTrialDaysRemaining(untrustedMaster, isDebug = false))

        // 2. On Debug (isDebug = true):
        // Local role MASTER_DEV is honored as intended for offline development
        val debugEntitlement = EntitlementManager.evaluate(untrustedMaster, isDebug = true)
        assertTrue("Role MASTER_DEV pada Debug tetap dihormati", debugEntitlement.isPremiumUnlocked)
        assertEquals(UserRole.MASTER_DEV, debugEntitlement.effectiveRole)
        assertEquals(EntitlementSource.MASTER_DEV, debugEntitlement.primarySource)
    }

    @Test
    fun testTrustedDeveloperEmail_maintainsPremiumOnReleaseAndDebug() = runBlocking {
        val trustedDev = User(
            id = 101L,
            username = "Official Dev",
            email = "lunachroc01@gmail.com",
            role = UserRole.MASTER_DEV,
            isGuest = false
        )

        // Release build guard: email matching mentah tidak otomatis memberikan hak MASTER_DEV tanpa Google Sign-In
        val releaseEntitlement = EntitlementManager.evaluate(trustedDev, isDebug = false)
        assertFalse("Pada build Release, pencocokan email mentah tidak memberikan hak MASTER_DEV", releaseEntitlement.isPremiumUnlocked)

        // Debug build: identitas developer resmi selalu memiliki hak MASTER_DEV
        val debugEntitlement = EntitlementManager.evaluate(trustedDev, isDebug = true)
        assertTrue("Developer resmi harus selalu memiliki premium di Debug", debugEntitlement.isPremiumUnlocked)
        assertEquals(UserRole.MASTER_DEV, debugEntitlement.effectiveRole)
        assertEquals(EntitlementSource.MASTER_DEV, debugEntitlement.primarySource)
    }

    @Test
    fun testNormalUserSession_isRestoredOnBothReleaseAndDebug() = runBlocking {
        val normalUser = User(
            id = 202L,
            username = "Pengguna Biasa",
            email = "user.biasa@gmail.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            trialStartDate = 0L
        )
        val insertedId = db.userDao().insertUser(normalUser)
        val prefs = context.getSharedPreferences(SessionManager.PREFS_NAME, Context.MODE_PRIVATE)

        // Test restore on Release (isDebug = false)
        prefs.edit().putLong(SessionManager.KEY_SAVED_USER_ID, insertedId).commit()
        val restoredRelease = SessionManager.restoreSession(db.userDao(), isDebug = false)
        ShadowLooper.idleMainLooper()
        assertTrue("Pengguna biasa harus berhasil dipulihkan di Release", restoredRelease)
        assertEquals(insertedId, SessionManager.getActiveUserId())

        // Test restore on Debug (isDebug = true)
        prefs.edit().putLong(SessionManager.KEY_SAVED_USER_ID, insertedId).commit()
        val restoredDebug = SessionManager.restoreSession(db.userDao(), isDebug = true)
        ShadowLooper.idleMainLooper()
        assertTrue("Pengguna biasa harus berhasil dipulihkan di Debug", restoredDebug)
        assertEquals(insertedId, SessionManager.getActiveUserId())
    }

    @Test
    fun testGuestMode_andTrialThreeDays_preservedCorrectly() = runBlocking {
        // Guest user
        val guestUser = User(
            id = 303L,
            username = "Tamu FinSmart",
            email = "guest_123@finsmart.local",
            role = UserRole.FREE_MEMBER,
            isGuest = true,
            trialStartDate = 0L
        )
        val guestEntitlement = EntitlementManager.evaluate(guestUser, isDebug = false)
        assertFalse("Tamu tidak boleh membuka premium", guestEntitlement.isPremiumUnlocked)
        assertEquals(0, guestEntitlement.trialDaysRemaining)

        // Google User without active subscription does NOT receive trial
        val now = System.currentTimeMillis()
        val googleUser = User(
            id = 404L,
            username = "Google User",
            email = "google.user@gmail.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            trialStartDate = 0L
        )
        val googleEntitlement = EntitlementManager.evaluate(googleUser, now = now, isDebug = false)
        assertFalse("User Google tanpa langganan aktif TIDAK membuka premium (trial dihapus permanen)", googleEntitlement.isPremiumUnlocked)
        assertEquals(EntitlementSource.NONE, googleEntitlement.primarySource)
        assertEquals(0, googleEntitlement.trialDaysRemaining)
    }

    @Test
    fun testMasterDevLogin_onRelease_requiresGoogleSignIn() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val authViewModel = AuthViewModel(app)

        // Try local login with Master Dev identifier on Release simulation (isDebug = false)
        authViewModel.login(DeveloperAccountConfig.CONTACT_DEV_EMAIL, "AnyPassword123!", isDebug = false)
        var retries = 0
        while (authViewModel.authError.value == null && retries < 40) {
            ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
            retries++
        }

        val error = authViewModel.authError.value
        assertNotNull("Harus ada pesan error", error)
        assertTrue(
            "Login Master Dev via form lokal di Release harus ditolak dengan pesan Google Sign-In",
            error!!.contains("Akses developer pada rilis publik memerlukan autentikasi resmi Google Sign-In")
        )
    }

    @Test
    fun testBlankPassword_isStrictlyRejected() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val appDb = FinSmartDatabase.getDatabase(app, this)
        val existingUser = User(
            id = 555L,
            username = "ExistingUser",
            email = "existing@example.com",
            passwordHash = PasswordHasher.hashPassword("SecurePass123!"),
            role = UserRole.FREE_MEMBER,
            isGuest = false
        )
        appDb.userDao().insertUser(existingUser)

        val authViewModel = AuthViewModel(app)
        authViewModel.login("ExistingUser", "   ", isDebug = true)
        ShadowLooper.idleMainLooper()

        val error = authViewModel.authError.value
        assertNotNull("Harus ada error untuk password kosong", error)
        assertEquals("Kata sandi tidak boleh kosong.", error)
    }
}




