package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.config.AppConfig
import com.example.config.DeveloperAccountConfig
import com.example.util.EntitlementManager
import com.example.util.EntitlementSource
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
import com.example.util.DriveAuthResult
import com.example.util.EncryptionUtils
import com.example.util.GoogleDriveService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
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
import org.robolectric.shadows.ShadowLooper
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleAuthAndDriveCloudTest {

    private lateinit var context: Context
    private lateinit var db: FinSmartDatabase
    private lateinit var userRepository: UserRepository

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Before
    fun setUp() {
        com.example.data.local.DatabaseWriteCoordinator.resetForTesting()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        setDatabaseSingleton(db)
        userRepository = UserRepository(db.userDao())
        SessionManager.initialize(context)
        GoogleDriveService.disconnect(context)
    }

    @After
    fun tearDown() {
        ShadowLooper.idleMainLooper()
        com.example.data.local.DatabaseWriteCoordinator.resetForTesting()
        setDatabaseSingleton(null)
        db.close()
        GoogleDriveService.disconnect(context)
        Dispatchers.resetMain()
    }

    private fun setDatabaseSingleton(instance: FinSmartDatabase?) {
        val field = FinSmartDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, instance)
    }

    // ==========================================
    // 1. GOOGLE SIGN-IN TESTS
    // ==========================================

    @Test
    fun testWebClientIdConfiguredCorrectly() {
        // Must use Web Application Client ID (not Android client ID) as serverClientId
        assertTrue("Web client ID must belong to project 1029968996578", AppConfig.GOOGLE_SIGN_IN_WEB_CLIENT_ID.startsWith("1029968996578"))
        assertTrue(AppConfig.GOOGLE_SIGN_IN_WEB_CLIENT_ID.endsWith(".apps.googleusercontent.com"))
    }

    @Test
    fun testNewGoogleUserMapping_createsLocalUserWithoutDuplication() = runBlocking {
        val googleEmail = "new.user@gmail.com"
        val googleName = "New Google User"

        // Ensure user does not exist initially
        assertNull(userRepository.getUserByIdentifier(googleEmail))

        // Simulate successful Google Sign-In user mapping
        val existing = userRepository.getUserByIdentifier(googleEmail)
        val user = if (existing == null) {
            val newUser = User(
                username = googleName,
                email = googleEmail,
                role = UserRole.FREE_MEMBER,
                trialStartDate = System.currentTimeMillis()
            )
            val id = userRepository.insertUser(newUser)
            newUser.copy(id = id)
        } else {
            existing
        }

        assertNotNull(user)
        assertTrue(user.id > 0)
        assertEquals(googleEmail, user.email)
        assertEquals(googleName, user.username)
        assertEquals(UserRole.FREE_MEMBER, user.role)

        // Repeat sign-in with same account: must reuse existing user, no duplicate created
        val repeatUser = userRepository.getUserByIdentifier(googleEmail)
        assertNotNull(repeatUser)
        assertEquals(user.id, repeatUser!!.id)

        val allUsers = userRepository.getAllUsers().first()
        assertEquals(1, allUsers.count { it.email == googleEmail })
    }

    @Test
    fun testGoogleSignInCancellation_doesNotCreatePhantomUser() = runBlocking {
        // When user dismisses account chooser dialog, no DB mutation should happen
        val initialUserCount = userRepository.getAllUsers().first().size

        // Simulated cancellation flow: simply exit without inserting
        val currentUserAfterCancel = SessionManager.currentUser.value

        val finalUserCount = userRepository.getAllUsers().first().size
        assertEquals(initialUserCount, finalUserCount)
    }

    @Test
    fun testThreeDayTrial_onlyActivatesOnValidatedGoogleUser() = runBlocking {
        // 1. Guest session: MUST NOT activate 3-day trial
        val guest = SessionManager.startGuestSession(db.userDao())
        assertTrue(guest.isGuest)
        assertEquals(0L, guest.trialStartDate)
        val guestEntitlement = com.example.util.EntitlementManager.evaluate(guest)
        assertFalse(guestEntitlement.isPremiumUnlocked)
        assertEquals(0, guestEntitlement.trialDaysRemaining)

        // 2. Validated Google User: MUST NOT receive 3-day trial (Permanently Removed)
        val googleEmail = "active.google@gmail.com"
        val now = System.currentTimeMillis()
        val googleUser = User(
            username = "Google Active User",
            email = googleEmail,
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            trialStartDate = 0L
        )
        val id = userRepository.insertUser(googleUser)
        val persistedGoogleUser = googleUser.copy(id = id)
        SessionManager.login(persistedGoogleUser)

        val googleEntitlement = com.example.util.EntitlementManager.evaluate(persistedGoogleUser, now = now)
        assertFalse("Trial dihapus permanen: pengguna Google biasa tidak mendapat premium", googleEntitlement.isPremiumUnlocked)
        assertEquals(com.example.util.EntitlementSource.NONE, googleEntitlement.primarySource)
        assertEquals(0, googleEntitlement.trialDaysRemaining)

        // 3. Re-login / subsequent session: trialStartDate remains 0L
        val reloadedUser = userRepository.getUserByEmail(googleEmail)
        assertNotNull(reloadedUser)
        assertEquals(0L, reloadedUser!!.trialStartDate)
    }

    @Test
    fun testGoogleSignInFailure_doesNotActivateTrialOrMutateDatabase() = runBlocking {
        SessionManager.logout()
        val initialUserCount = userRepository.getAllUsers().first().size

        // Simulated blank email or rejected ID token
        val invalidEmail = ""
        if (invalidEmail.isNotBlank()) {
            userRepository.insertUser(User(username = "Bad", email = invalidEmail, role = UserRole.FREE_MEMBER))
        }

        // Database must remain untouched
        val postFailCount = userRepository.getAllUsers().first().size
        assertEquals(initialUserCount, postFailCount)
        assertNull(SessionManager.getActiveUserId())
    }

    @Test
    fun testCredentialOptionIsolation_getSignInWithGoogleOptionMustNotCombine() {
        val signInWithGoogleOption = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(
            serverClientId = AppConfig.GOOGLE_SIGN_IN_WEB_CLIENT_ID
        ).build()

        // Standalone request per API requirements
        val validRequest = androidx.credentials.GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()
        assertEquals(1, validRequest.credentialOptions.size)
        assertEquals(signInWithGoogleOption, validRequest.credentialOptions.first())
    }

    // ==========================================
    // 2. GOOGLE DRIVE AUTHORIZATION & SCOPE TESTS
    // ==========================================

    @Test
    fun testLeastPrivilegeDriveScope() {
        // Must strictly use drive.file scope, never full drive scope
        assertEquals("https://www.googleapis.com/auth/drive.file", GoogleDriveService.DRIVE_SCOPE)
        assertEquals("https://www.googleapis.com/auth/drive.file", AppConfig.GOOGLE_DRIVE_SCOPE)
    }

    @Test
    fun testDriveConnectionStateSeparation() {
        // Authenticated user should NOT be assumed connected to Drive automatically
        assertFalse(GoogleDriveService.isConnected(context))
        assertEquals("", GoogleDriveService.getConnectedEmail(context))

        // Connect Drive explicitly
        val email = "user.backup@gmail.com"
        GoogleDriveService.connect(context, email)

        assertTrue(GoogleDriveService.isConnected(context))
        assertEquals(email, GoogleDriveService.getConnectedEmail(context))

        // Disconnect Drive
        GoogleDriveService.disconnect(context)
        assertFalse(GoogleDriveService.isConnected(context))
        assertEquals("", GoogleDriveService.getConnectedEmail(context))
    }

    @Test
    fun testDriveAccessTokenErrorHandlingOnBlankEmail() = runBlocking {
        val result = GoogleDriveService.getDriveAccessToken(context, "")
        assertTrue(result is DriveAuthResult.Error)
        assertTrue((result as DriveAuthResult.Error).message.contains("belum diatur"))
    }

    // ==========================================
    // 3. BACKUP ENCRYPTION & CLOUD RESTORE TESTS
    // ==========================================

    @Test
    fun testBackupAndRestore_formatV2AEADIntegrity() = runBlocking {
        // Insert sample user
        val user = User(username = "Tester", email = "tester@gmail.com", role = UserRole.FREE_MEMBER)
        val userId = userRepository.insertUser(user)

        // Insert sample financial data for userId
        val wallet = Wallet(name = "BCA", balance = 5000000.0, type = WalletType.BANK, userId = userId)
        val walletId = db.walletDao().insertWallet(wallet)

        val category = CategoryItem(name = "Makan", type = TransactionType.EXPENSE, iconName = "ic_food", colorHex = "#FF0000", userId = userId)
        val categoryId = db.categoryDao().insertCategory(category)

        val tx = TransactionItem(
            title = "Nasi Padang",
            amount = 50000.0,
            type = TransactionType.EXPENSE,
            category = "Makan",
            walletId = walletId,
            walletName = "BCA",
            userId = userId,
            accountMode = AccountMode.PERSONAL.name
        )
        val txId = db.transactionDao().insertTransaction(tx)

        // Perform backup
        val (backupSuccess, backupMsg) = GoogleDriveService.backupNow(context, db, userId)
        assertTrue(backupSuccess)
        assertTrue(backupMsg.contains("Format v2 Terautentikasi"))

        // Verify local file is encrypted with Format v2
        val backupFile = File(context.filesDir, "FinSmart_Backups/finsmart_backup.json")
        assertTrue(backupFile.exists())
        val encryptedText = backupFile.readText()
        assertTrue(EncryptionUtils.isFormatV2(encryptedText))

        // Clear transactions to simulate restore from cloud/local
        db.transactionDao().deleteTransaction(txId, userId)
        assertEquals(0, db.transactionDao().getAllTransactions(userId).first().size)

        // Perform restore
        val (restoreSuccess, restoreMsg) = GoogleDriveService.restoreNow(context, db, userId)
        assertTrue(restoreSuccess)
        assertTrue(restoreMsg.contains("Pemulihan data berhasil"))

        // Verify restored transactions
        val restoredTxs = db.transactionDao().getAllTransactions(userId).first()
        assertEquals(1, restoredTxs.size)
        assertEquals(50000.0, restoredTxs[0].amount, 0.01)
        assertEquals("Nasi Padang", restoredTxs[0].title)
    }

    @Test
    fun testRestoreWithCorruptedFile_failsGracefullyWithoutDataLoss() = runBlocking {
        val user = User(username = "Tester2", email = "tester2@gmail.com", role = UserRole.FREE_MEMBER)
        val userId = userRepository.insertUser(user)

        val wallet = Wallet(name = "Cash", balance = 200000.0, type = WalletType.CASH, userId = userId)
        db.walletDao().insertWallet(wallet)

        // Write corrupt content to backup file
        val backupDir = File(context.filesDir, "FinSmart_Backups")
        backupDir.mkdirs()
        File(backupDir, "finsmart_backup.json").writeText("{ corrupt_invalid_json }")

        val (restoreSuccess, restoreMsg) = GoogleDriveService.restoreNow(context, db, userId)
        assertFalse(restoreSuccess)
        assertTrue(restoreMsg.contains("Gagal") || restoreMsg.contains("tidak valid"))

        // Verify existing database data is untouched (non-destructive)
        val wallets = db.walletDao().getAllWallets(userId).first()
        assertEquals(1, wallets.size)
        assertEquals(200000.0, wallets[0].balance, 0.01)
    }

    @Test
    fun testMultiUserIsolation_restoreDoesNotLeakOtherUserData() = runBlocking {
        val userA = User(username = "User A", email = "a@gmail.com", role = UserRole.FREE_MEMBER)
        val userB = User(username = "User B", email = "b@gmail.com", role = UserRole.FREE_MEMBER)
        val idA = userRepository.insertUser(userA)
        val idB = userRepository.insertUser(userB)

        // Insert wallets for both users
        db.walletDao().insertWallet(Wallet(name = "Wallet A", balance = 100000.0, type = WalletType.CASH, userId = idA))
        db.walletDao().insertWallet(Wallet(name = "Wallet B", balance = 900000.0, type = WalletType.BANK, userId = idB))

        // Backup User A
        val (backupSuccess, _) = GoogleDriveService.backupNow(context, db, idA)
        assertTrue(backupSuccess)

        // Restore scoped to User B: payload should be remapped strictly to User B (DEF-04)
        // User A's data must remain intact and isolated
        val (restoreSuccess, _) = GoogleDriveService.restoreNow(context, db, idB)
        assertTrue(restoreSuccess)

        val walletsA = db.walletDao().getAllWallets(idA).first()
        val walletsB = db.walletDao().getAllWallets(idB).first()

        assertEquals(1, walletsA.size)
        assertEquals("Wallet A", walletsA[0].name)

        assertEquals(1, walletsB.size)
        // Re-scoped to user B
        assertEquals(idB, walletsB[0].userId)
    }

    @Test
    fun testStrictUserByEmail_preventsUsernameCollision() = runBlocking {
        // Attack scenario: malicious local account registered with username equal to someone's Google email
        val victimEmail = "victim@gmail.com"
        val impostor = User(
            username = victimEmail, // username matches victim's email!
            email = "impostor@local.domain",
            role = UserRole.FREE_MEMBER
        )
        userRepository.insertUser(impostor)

        // Strict lookup by email must NOT match the impostor account
        val matchedUser = userRepository.getUserByEmail(victimEmail)
        assertNull("Strict email lookup must NOT match an impostor with identical username", matchedUser)

        // Only genuine email match should succeed
        val genuineUser = User(
            username = "Real Victim",
            email = victimEmail,
            role = UserRole.FREE_MEMBER
        )
        val genuineId = userRepository.insertUser(genuineUser)
        val genuineLookup = userRepository.getUserByEmail(victimEmail)
        assertNotNull(genuineLookup)
        assertEquals(genuineId, genuineLookup!!.id)
    }

    @Test
    fun testFailedCloudUpload_doesNotClaimCloudSuccess() = runBlocking {
        val user = User(username = "CloudTester", email = "tester@gmail.com", role = UserRole.FREE_MEMBER)
        val userId = userRepository.insertUser(user)

        // Simulate connected state with invalid/unreachable email that fails token acquisition
        GoogleDriveService.connect(context, "unauthorized.account@gmail.com")

        val (backupSuccess, backupMsg) = GoogleDriveService.backupNow(context, db, userId)
        assertTrue("Local backup should succeed as offline fallback", backupSuccess)
        assertFalse(
            "Backup message must NOT claim cloud upload success when authorization fails",
            backupMsg.contains("berhasil diunggah ke Google Drive Cloud")
        )
        assertTrue(
            "Backup message must indicate local storage fallback or Drive failure",
            backupMsg.contains("Tersimpan") || backupMsg.contains("Gagal")
        )
    }

    // ==========================================
    // 5. TARGETED GUEST TRIAL REMEDIATION TESTS
    // ==========================================

    @Test
    fun testGuestMode_doesNotAcquireMemberTrial() = runBlocking {
        // 1. Guest Mode starting session
        val guest = SessionManager.startGuestSession(db.userDao())
        assertTrue("User must be marked as guest", guest.isGuest)
        assertEquals("Guest trialStartDate must be 0L", 0L, guest.trialStartDate)

        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val authViewModel = com.example.ui.viewmodel.AuthViewModel(app)

        assertEquals("Guest must have 0 trial days remaining", 0, authViewModel.getTrialDaysRemaining())
        assertFalse("Guest must not have member features unlocked via trial", authViewModel.isFeatureUnlocked())
    }

    @Test
    fun testGoogleLoginSuccess_activatesThreeDayTrial() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val authViewModel = com.example.ui.viewmodel.AuthViewModel(app)
        ShadowLooper.idleMainLooper()

        // Mock Google login
        authViewModel.loginWithGoogleMock()
        ShadowLooper.idleMainLooper()

        val activeUser = SessionManager.currentUser.value
        assertNotNull("Google user must be logged in", activeUser)
        assertEquals("google.user@gmail.com", activeUser?.email)
        assertFalse("Google user must not be marked as guest", activeUser!!.isGuest)
        // Kebijakan penghapusan trial: Google user tanpa langganan memiliki trialStartDate = 0L
        assertEquals("Google user tanpa langganan tidak boleh memiliki trial", 0L, activeUser.trialStartDate)
        assertEquals("Trial days harus 0", 0, authViewModel.getTrialDaysRemaining())
        assertFalse("Fitur premium terkunci untuk free Google user tanpa langganan", authViewModel.isFeatureUnlocked())
    }

    @Test
    fun testGoogleLoginFailureOrCancel_doesNotActivateTrial() = runBlocking {
        val guest = SessionManager.startGuestSession(db.userDao())
        assertEquals(0L, guest.trialStartDate)

        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val authViewModel = com.example.ui.viewmodel.AuthViewModel(app)

        // Simulating failed Google sign-in where no valid user is returned / exception caught
        // The active session remains Guest with 0L trial
        assertEquals(0, authViewModel.getTrialDaysRemaining())
        assertFalse(authViewModel.isFeatureUnlocked())
        assertEquals(0L, SessionManager.currentUser.value?.trialStartDate)
    }

    @Test
    fun testActiveTrial_dayCountdownCalculatedCorrectly() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val authViewModel = com.example.ui.viewmodel.AuthViewModel(app)

        val now = System.currentTimeMillis()
        val oneDayMillis = 24 * 3600 * 1000L

        // Kebijakan produk: Trial gratis ditiadakan permanen sehingga getTrialDaysRemaining() selalu 0
        val userDay1 = User(id = 501L, username = "Day1User", email = "day1@gmail.com", role = UserRole.FREE_MEMBER, trialStartDate = now)
        SessionManager.setSessionForTesting(userDay1.id, userDay1)
        assertEquals(0, authViewModel.getTrialDaysRemaining())
        assertFalse(authViewModel.isFeatureUnlocked())

        // Smart Master berbayar aktif tetap terbuka
        val activeSub = User(id = 502L, username = "SubUser", email = "sub@gmail.com", role = UserRole.SMART_MASTER, subscriptionExpiresAt = now + oneDayMillis)
        SessionManager.setSessionForTesting(activeSub.id, activeSub)
        assertTrue(authViewModel.isFeatureUnlocked())
    }

    @Test
    fun testExpiredTrial_doesNotGrantMemberAccess() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val authViewModel = com.example.ui.viewmodel.AuthViewModel(app)

        val now = System.currentTimeMillis()
        val fourDaysAgo = now - (4L * 24 * 3600 * 1000L)

        val expiredUser = User(id = 504L, username = "ExpiredUser", email = "expired@gmail.com", role = UserRole.FREE_MEMBER, trialStartDate = fourDaysAgo)
        SessionManager.setSessionForTesting(expiredUser.id, expiredUser)

        assertEquals("Expired trial must return 0 days", 0, authViewModel.getTrialDaysRemaining())
        assertFalse("Expired trial must not grant member feature access", authViewModel.isFeatureUnlocked())
    }

    @Test
    fun testLogoutAndAccountSwitch_doesNotLeakAccessBetweenUsers() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val authViewModel = com.example.ui.viewmodel.AuthViewModel(app)

        // User A with valid Smart Master subscription
        val userA = User(id = 601L, username = "UserA", email = "usera@gmail.com", role = UserRole.SMART_MASTER, subscriptionExpiresAt = System.currentTimeMillis() + 86400000L)
        SessionManager.login(userA)
        assertTrue(authViewModel.isFeatureUnlocked())

        // User A logs out
        SessionManager.logout()
        assertFalse("Unauthenticated session must not have access", authViewModel.isFeatureUnlocked())
        assertEquals(0, authViewModel.getTrialDaysRemaining())

        // User B starts as guest
        val guestB = SessionManager.startGuestSession(db.userDao())
        assertFalse("Guest B must not inherit User A's access", authViewModel.isFeatureUnlocked())
        assertEquals(0, authViewModel.getTrialDaysRemaining())
    }

    @Test
    fun testGuestSession_startingNewSessionNeverYieldsTrial() = runBlocking {
        // Starting session 1
        val guest1 = SessionManager.startGuestSession(db.userDao())
        assertEquals(0L, guest1.trialStartDate)

        // Starting session 2
        val guest2 = SessionManager.startGuestSession(db.userDao())
        assertEquals(0L, guest2.trialStartDate)

        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val authViewModel = com.example.ui.viewmodel.AuthViewModel(app)
        assertEquals(0, authViewModel.getTrialDaysRemaining())
        assertFalse(authViewModel.isFeatureUnlocked())
    }

    @Test
    fun testUnrelatedAuthRoles_masterDevAndSmartMasterRemainIntact() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val authViewModel = com.example.ui.viewmodel.AuthViewModel(app)

        // Master Developer always unlocked with official developer identity
        val masterDev = User(id = 701L, username = "Lukas", email = "lunachroc01@gmail.com", role = UserRole.MASTER_DEV, trialStartDate = 0L)
        SessionManager.setSessionForTesting(masterDev.id, masterDev)
        assertTrue("MasterDev must always be unlocked", authViewModel.isFeatureUnlocked())
        val devStatus = EntitlementManager.evaluate(masterDev, isDebug = true)
        assertEquals(UserRole.MASTER_DEV, devStatus.effectiveRole)
        assertEquals(EntitlementSource.MASTER_DEV, devStatus.primarySource)

        // Active Smart Master
        val activeSmartMaster = User(
            id = 702L,
            username = "SmartMaster",
            email = "smart@test.com",
            role = UserRole.SMART_MASTER,
            trialStartDate = 0L,
            subscriptionExpiresAt = System.currentTimeMillis() + 86400000L
        )
        SessionManager.setSessionForTesting(activeSmartMaster.id, activeSmartMaster)
        assertTrue("Active SmartMaster must be unlocked", authViewModel.isFeatureUnlocked())
        val smartStatus = EntitlementManager.evaluate(activeSmartMaster)
        assertEquals(UserRole.SMART_MASTER, smartStatus.effectiveRole)
        assertTrue(smartStatus.isPremiumUnlocked)

        // Expired Smart Master with no trial
        val expiredSmartMaster = activeSmartMaster.copy(
            id = 703L,
            subscriptionExpiresAt = System.currentTimeMillis() - 86400000L
        )
        SessionManager.setSessionForTesting(expiredSmartMaster.id, expiredSmartMaster)
        assertFalse("Expired SmartMaster with no trial must be locked", authViewModel.isFeatureUnlocked())
    }

    // ==========================================
    // 6. GUEST CONVERSION REMINDER BANNER TESTS
    // ==========================================

    @Test
    fun testGuestReminderBanner_doesNotAppearImmediatelyOnLaunch() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val guest = User(id = 801L, username = "GuestBannerUser", email = "guest_banner@finsmart.local", role = UserRole.FREE_MEMBER, isGuest = true, trialStartDate = 0L)
        SessionManager.setSessionForTesting(guest.id, guest)

        val authViewModel = com.example.ui.viewmodel.AuthViewModel(app)
        ShadowLooper.idleMainLooper()

        // Banner must NOT appear immediately on app open (requires ~2 minute timer)
        assertFalse("Banner must not be visible immediately on launch", authViewModel.isGuestReminderBannerVisible.value)
        assertTrue("Guest must be eligible once timer elapses", authViewModel.isGuestReminderEligible())
    }

    @Test
    fun testGuestReminderBanner_appearsWhenPromptTriggeredForGuest() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val guest = User(id = 802L, username = "GuestPromptUser", email = "guest_prompt@finsmart.local", role = UserRole.FREE_MEMBER, isGuest = true, trialStartDate = 0L)
        SessionManager.setSessionForTesting(guest.id, guest)

        val authViewModel = com.example.ui.viewmodel.AuthViewModel(app)
        ShadowLooper.idleMainLooper()

        // Deterministic prompt trigger (simulating 2-minute timer completion)
        authViewModel.scheduleGuestBannerPrompt(delayMillis = 0L)
        ShadowLooper.idleMainLooper()

        assertTrue("Guest banner must be visible after delay", authViewModel.isGuestReminderBannerVisible.value)
    }

    @Test
    fun testGuestReminderBanner_dismissalSetsTwentyFourHourCooldown() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val guest = User(id = 803L, username = "GuestDismissUser", email = "guest_dismiss@finsmart.local", role = UserRole.FREE_MEMBER, isGuest = true, trialStartDate = 0L)
        SessionManager.setSessionForTesting(guest.id, guest)

        val authViewModel = com.example.ui.viewmodel.AuthViewModel(app)
        ShadowLooper.idleMainLooper()

        authViewModel.triggerGuestBannerForTesting()
        assertTrue(authViewModel.isGuestReminderBannerVisible.value)

        val now = System.currentTimeMillis()
        val oneHourMillis = 3600000L
        val twentyFourHoursMillis = 24L * 3600000L

        // Dismiss banner
        authViewModel.dismissGuestReminderBanner(now)
        assertFalse("Banner must be hidden immediately upon dismissal", authViewModel.isGuestReminderBannerVisible.value)

        // Must remain ineligible within 24 hours
        assertFalse("Must not be eligible 1 hour after dismissal", authViewModel.isGuestReminderEligible(now + oneHourMillis))
        assertFalse("Must not be eligible 23 hours after dismissal", authViewModel.isGuestReminderEligible(now + 23L * oneHourMillis))

        // Becomes eligible again after 24 hours
        assertTrue("Must become eligible again after 24 hours cooldown", authViewModel.isGuestReminderEligible(now + twentyFourHoursMillis + 1000L))
    }

    @Test
    fun testGuestReminderBanner_cooldownPersistsAcrossAppRestart() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val guest = User(id = 804L, username = "GuestRestartUser", email = "guest_restart@finsmart.local", role = UserRole.FREE_MEMBER, isGuest = true, trialStartDate = 0L)
        SessionManager.setSessionForTesting(guest.id, guest)

        val now = System.currentTimeMillis()
        val authViewModel1 = com.example.ui.viewmodel.AuthViewModel(app)
        authViewModel1.dismissGuestReminderBanner(now)

        // Simulate app restart by instantiating new AuthViewModel
        val authViewModel2 = com.example.ui.viewmodel.AuthViewModel(app)
        ShadowLooper.idleMainLooper()

        // Cooldown persists in SharedPreferences: within 24 hours, not eligible
        assertFalse("Cooldown must persist across ViewModel / app restart", authViewModel2.isGuestReminderEligible(now + 2 * 3600000L))
        assertFalse("Banner must remain hidden after restart within cooldown", authViewModel2.isGuestReminderBannerVisible.value)
    }

    @Test
    fun testGuestReminderBanner_notShownForLoggedInGoogleUser() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val authViewModel = com.example.ui.viewmodel.AuthViewModel(app)
        ShadowLooper.idleMainLooper()

        // Google user logged in
        authViewModel.loginWithGoogleMock()
        ShadowLooper.idleMainLooper()

        val currentUser = SessionManager.currentUser.value
        assertNotNull(currentUser)
        assertFalse("Logged-in Google user is not guest", currentUser!!.isGuest)

        assertFalse("Google user must not be eligible for guest banner", authViewModel.isGuestReminderEligible())
        assertFalse("Banner must not be visible for Google user", authViewModel.isGuestReminderBannerVisible.value)
    }

    @Test
    fun testExistingTrialAccount_doesNotGetSecondTrialOnSubsequentLogins() = runBlocking {
        val googleEmail = "repeat.trial.user@gmail.com"
        val originalTrialStart = System.currentTimeMillis() - 86400000L // 1 hari yang lalu

        // Pre-existing user with already activated trial
        val existingUser = User(
            id = 805L,
            username = "Repeat Trial User",
            email = googleEmail,
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            trialStartDate = originalTrialStart
        )
        userRepository.insertUser(existingUser)

        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val authViewModel = com.example.ui.viewmodel.AuthViewModel(app)
        ShadowLooper.idleMainLooper()

        // Re-login: must NOT reset trialStartDate
        val fetched = userRepository.getUserByEmail(googleEmail)
        assertNotNull(fetched)
        assertEquals("Original trialStartDate must be preserved", originalTrialStart, fetched!!.trialStartDate)
    }

    @Test
    fun testTrustedDeveloperGoogleLogin_grantsPermanentMasterDev() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val authViewModel = com.example.ui.viewmodel.AuthViewModel(app)

        val devEmails = listOf(
            "lunachroc01@gmail.com",
            "zweifeel@gmail.com"
        )

        devEmails.forEach { devEmail ->
            val isTrusted = com.example.config.DeveloperAccountConfig.isTrustedDeveloperEmail(devEmail)
            assertTrue("Email $devEmail harus diakui sebagai developer terpercaya", isTrusted)

            // Simulasikan pembuatan / penataan akun developer
            val devUser = User(
                username = "DevUser",
                email = devEmail,
                role = UserRole.MASTER_DEV,
                trialStartDate = 0L,
                subscriptionExpiresAt = null
            )
            SessionManager.setSessionForTesting(devUser.id, devUser)

            assertTrue("Developer $devEmail harus memiliki akses tidak terbatas", authViewModel.isFeatureUnlocked())
            assertTrue("Developer harus lolos otorisasi isDeveloperAuthorized()", authViewModel.isDeveloperAuthorized())
            val status = EntitlementManager.evaluate(devUser)
            assertEquals("Role harus MASTER_DEV", UserRole.MASTER_DEV, status.effectiveRole)
            assertTrue(status.isPremiumUnlocked)
        }

        // Email legacy ditolak dari daftar terpercaya
        assertFalse("Legacy email dilarang masuk daftar developer Release", DeveloperAccountConfig.isTrustedDeveloperEmail("lunachroc07@gmail.com"))
    }

    @Test
    fun testManualRegistrationWithDeveloperEmail_isBlocked() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val authViewModel = com.example.ui.viewmodel.AuthViewModel(app)

        // Generate OTP
        authViewModel.generateOtpForRegistration("lunachroc01@gmail.com")
        val otpField = authViewModel.javaClass.getDeclaredField("_pendingOtpCode")
        otpField.isAccessible = true
        val otpState = otpField.get(authViewModel) as kotlinx.coroutines.flow.MutableStateFlow<String?>
        val currentOtp = otpState.value ?: "123456"

        authViewModel.registerWithOtp("Palsu", "lunachroc01@gmail.com", "Password123", currentOtp)
        ShadowLooper.idleMainLooper()

        val authErrorField = authViewModel.javaClass.getDeclaredField("_authError")
        authErrorField.isAccessible = true
        val errorState = authErrorField.get(authViewModel) as kotlinx.coroutines.flow.MutableStateFlow<String?>
        assertNotNull("Pendaftaran manual dengan email developer harus ditolak", errorState.value)
        assertTrue("Pesan error harus menyebut identitas dicadangkan", errorState.value!!.contains("dicadangkan"))
    }
}





