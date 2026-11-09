package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.config.DeveloperAccountConfig
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.BackupDataPayload
import com.example.data.model.FeedbackItem
import com.example.data.session.SessionManager
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.data.repository.FinanceRepository
import com.example.util.AppLanguage
import com.example.util.AppStrings
import com.example.util.CurrencyFormatter
import com.example.util.LocaleHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase1gUiLocalizationAndAccountRemovalTest {

    private lateinit var context: Context
    private lateinit var db: FinSmartDatabase
    private lateinit var financeRepo: FinanceRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        LocaleHelper.initialize(context)
        SessionManager.initialize(context)

        com.example.data.local.DatabaseWriteCoordinator.resetForTesting()
        db = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        financeRepo = FinanceRepository(walletDao = db.walletDao(), database = db)
    }

    @After
    fun tearDown() {
        setDatabaseSingleton(null)
        SessionManager.resetForTesting()
        com.example.data.local.DatabaseWriteCoordinator.resetForTesting()
        db.close()
    }

    private fun setDatabaseSingleton(instance: FinSmartDatabase?) {
        val field = FinSmartDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, instance)
    }

    // =========================================================================
    // 1. DYNAMIC BILINGUAL LOCALIZATION & SYSTEM LOCALE RESOLUTION
    // =========================================================================

    @Test
    fun testAppLanguage_bilingualProperties_switchDynamically() {
        // Force Indonesian
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        assertFalse("isEnglish must be false in Indonesian mode", LocaleHelper.isEnglish())
        assertEquals("Ikuti Sistem HP", AppLanguage.SYSTEM.displayName)
        assertEquals("Bahasa Indonesia", AppLanguage.INDONESIAN.displayName)
        assertEquals("Gunakan Bahasa Indonesia secara penuh", AppLanguage.INDONESIAN.subtitle)

        // Force English
        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)
        assertTrue("isEnglish must be true in English mode", LocaleHelper.isEnglish())
        assertEquals("Follow System", AppLanguage.SYSTEM.displayName)
        assertEquals("Indonesian", AppLanguage.INDONESIAN.displayName)
        assertEquals("English", AppLanguage.ENGLISH.displayName)
        assertEquals("Matches your device system language setting", AppLanguage.SYSTEM.subtitle)
        assertEquals("Use English throughout the application", AppLanguage.ENGLISH.subtitle)
    }

    @Test
    fun testAppLanguage_systemFollowsDeviceLocale() {
        LocaleHelper.setLanguage(context, AppLanguage.SYSTEM)

        // When device locale is English
        Locale.setDefault(Locale.US)
        try {
            LocaleHelper.systemLocaleOverrideForTesting = Locale("id", "ID")
            assertFalse("Must resolve to Indonesian when device is id_ID", LocaleHelper.isEnglish())

            LocaleHelper.systemLocaleOverrideForTesting = Locale("in", "ID")
            assertFalse("Must resolve to Indonesian when device is in_ID", LocaleHelper.isEnglish())

            LocaleHelper.systemLocaleOverrideForTesting = Locale.US
            assertTrue("Must resolve to English when device is US", LocaleHelper.isEnglish())

            LocaleHelper.systemLocaleOverrideForTesting = Locale.FRANCE
            assertTrue("International system language must fallback cleanly to English dictionary", LocaleHelper.isEnglish())

            LocaleHelper.systemLocaleOverrideForTesting = Locale.JAPAN
            assertTrue("Japanese system language must fallback cleanly to English dictionary", LocaleHelper.isEnglish())
        } finally {
            LocaleHelper.systemLocaleOverrideForTesting = null
        }
        assertNotNull(LocaleHelper.getActiveLocale())
    }

    @Test
    fun testSummaryCardsAndDialogStrings_areBilingual() {
        // Test in Indonesian
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        val idUnassignedBanner = if (LocaleHelper.isEnglish()) {
            "3 wallet(s) have unassigned currency. Tap to set."
        } else {
            "Ada 3 dompet belum ditetapkan mata uangnya. Ketuk untuk atur."
        }
        assertEquals("Ada 3 dompet belum ditetapkan mata uangnya. Ketuk untuk atur.", idUnassignedBanner)
        assertEquals("Halo, ", AppStrings.t("Halo, ", "Hello, "))
        assertEquals("Pilih Mata Uang Global", AppStrings.t("Pilih Mata Uang Global", "Select Global Currency"))

        // Test in English
        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)
        val enUnassignedBanner = if (LocaleHelper.isEnglish()) {
            "3 wallet(s) have unassigned currency. Tap to set."
        } else {
            "Ada 3 dompet belum ditetapkan mata uangnya. Ketuk untuk atur."
        }
        assertEquals("3 wallet(s) have unassigned currency. Tap to set.", enUnassignedBanner)
        assertEquals("Hello, ", AppStrings.t("Halo, ", "Hello, "))
        assertEquals("Select Global Currency", AppStrings.t("Pilih Mata Uang Global", "Select Global Currency"))
        assertEquals("Clear Search", AppStrings.t("Hapus Pencarian", "Clear Search"))
        assertEquals("Delete Wallet?", AppStrings.t("Hapus Dompet?", "Delete Wallet?"))
    }

    // =========================================================================
    // 2. DEFAULT WALLET REMOVAL & STRICT USER AUTONOMY
    // =========================================================================

    @Test
    fun testNeutralDefaultWallets_provisionDefaultWalletsReturnsEmpty() = runBlocking {
        val testUserId = db.userDao().insertUser(User(username = "Fresh User", email = "fresh@example.com", role = UserRole.FREE_MEMBER, isApproved = true))
        val initialWallets = financeRepo.provisionDefaultWalletsIfEmpty(testUserId)

        assertTrue("provisionDefaultWallets must return empty list for user autonomy", initialWallets.isEmpty())

        val storedWallets = db.walletDao().getAllWallets(testUserId).first()
        assertEquals("No default wallets (such as BCA) must be auto-created for new users", 0, storedWallets.size)
    }

    @Test
    fun testWalletsExplicitCreation_functionsNormally() = runBlocking {
        val testUserId = db.userDao().insertUser(User(id = 42L, username = "Test User", email = "test@example.com", role = UserRole.FREE_MEMBER, isApproved = true))
        val walletId = financeRepo.insertWallet(
            Wallet(
                name = "My Personal Cash",
                type = WalletType.CASH,
                balance = 50000.0,
                colorHex = "#10B981",
                iconName = "cash",
                accountMode = AccountMode.PERSONAL.name,
                isDefault = true,
                userId = testUserId,
                currencyCode = "IDR"
            )
        )
        assertTrue("Explicit wallet insertion must succeed", walletId > 0L)

        val wallets = db.walletDao().getAllWallets(testUserId).first()
        assertEquals(1, wallets.size)
        assertEquals("My Personal Cash", wallets.first().name)
        assertEquals(50000.0, wallets.first().balance, 0.001)
    }

    // =========================================================================
    // 3. PERMANENT REMOVAL OF LUKAS MASTER_DEV ACCOUNT & P0 ATOMIC PURGE
    // =========================================================================

    @Test
    fun testLukasAccount_isNotAutoProvisioned() = runBlocking {
        FinSmartDatabase.populateInitialData(db, isDebug = true)
        assertEquals("Zero users seeded by populateInitialData in Phase 1G", 0, db.userDao().getUserCount())

        val masterId = FinSmartDatabase.ensureMasterDevAccount(db, isDebug = true)
        assertEquals("ensureMasterDevAccount returns 0 when no trusted developer email exists", 0L, masterId)
        assertNull("Lukas user must not exist in DB", db.userDao().getUserByIdentifier("Lukas"))
        assertNull("Legacy dev email must not exist in DB", db.userDao().getUserByEmail("lunachroc07@gmail.com"))
    }

    @Test
    fun testDeveloperAccountConfig_rejectsLukasIdentifier() {
        assertFalse("Lukas is no longer a valid master dev identifier", DeveloperAccountConfig.isMasterDevIdentifier("Lukas"))
        assertFalse("lukas is no longer a valid master dev identifier", DeveloperAccountConfig.isMasterDevIdentifier("lukas"))
        assertFalse("LUKAS is no longer a valid master dev identifier", DeveloperAccountConfig.isMasterDevIdentifier("LUKAS"))
        assertFalse("lunachroc07@gmail.com is rejected", DeveloperAccountConfig.isMasterDevIdentifier("lunachroc07@gmail.com"))

        // Trusted developer emails remain valid
        assertTrue(DeveloperAccountConfig.isMasterDevIdentifier(DeveloperAccountConfig.CONTACT_DEV_EMAIL))
        assertTrue(DeveloperAccountConfig.isMasterDevIdentifier(DeveloperAccountConfig.OWNER_PROJECT_EMAIL))
    }

    @Test
    fun testPurgeTargetDeveloperAccountAtomically_success() = runBlocking {
        // 1. Setup target Lukas user + wallet
        val lukasId = db.userDao().insertUser(
            User(
                username = "Lukas",
                email = "lunachroc07@gmail.com",
                passwordHash = "",
                role = UserRole.MASTER_DEV,
                isApproved = true
            )
        )
        assertTrue(lukasId > 0L)
        val lukasWallet = db.walletDao().insertWallet(
            Wallet(name = "BCA Lukas", type = WalletType.BANK, balance = 500000.0, userId = lukasId)
        )
        assertTrue(lukasWallet > 0L)

        // 2. Setup legitimate user (Budi) + wallet
        val budiId = db.userDao().insertUser(
            User(
                username = "Budi",
                email = "budi@example.com",
                passwordHash = "hash",
                role = UserRole.FREE_MEMBER,
                isApproved = true
            )
        )
        assertTrue(budiId > 0L)
        val budiWallet = db.walletDao().insertWallet(
            Wallet(name = "Mandiri Budi", type = WalletType.BANK, balance = 250000.0, userId = budiId)
        )
        assertTrue(budiWallet > 0L)

        // 3. Setup feedbacks: orphan legacy sample feedback with userId=null vs Budi's legitimate feedback
        db.feedbackDao().insertFeedback(
            FeedbackItem(
                id = "sample_lukas_1",
                userId = null,
                userName = "Lukas",
                userEmail = "lunachroc07@gmail.com",
                rating = 5,
                category = "Saran Fitur",
                message = "Sample feedback by Lukas"
            )
        )
        db.feedbackDao().insertFeedback(
            FeedbackItem(
                id = "budi_feedback_1",
                userId = budiId,
                userName = "Budi",
                userEmail = "budi@example.com",
                rating = 4,
                category = "Desain / UI",
                message = "Feedback dari Budi"
            )
        )

        assertEquals(2, db.feedbackDao().getAllFeedbacks().first().size)

        // 4. Execute atomic purge
        val result = FinSmartDatabase.purgeTargetDeveloperAccountAtomically(db)
        assertTrue("Purge must succeed", result.isSuccess)
        assertTrue("Purge returned true indicating user was deleted", result.getOrNull() == true)

        // 5. Verify Lukas and Lukas wallet deleted
        assertNull("Lukas user must be deleted", db.userDao().getUserById(lukasId))
        assertEquals("Wallets belonging to Lukas must be deleted", 0, db.walletDao().getWalletCount(lukasId))

        // 6. Verify Budi and Budi wallet preserved
        assertNotNull("Budi user must NOT be deleted", db.userDao().getUserById(budiId))
        assertEquals("Wallets belonging to Budi must be intact", 1, db.walletDao().getWalletCount(budiId))

        // 7. Verify sample feedback deleted, legitimate feedback retained
        val remainingFeedbacks = db.feedbackDao().getAllFeedbacks().first()
        assertEquals(1, remainingFeedbacks.size)
        assertEquals("budi_feedback_1", remainingFeedbacks.first().id)
        assertEquals(budiId, remainingFeedbacks.first().userId)
    }

    @Test
    fun testPurgeTargetDeveloperAccountAtomically_ambiguityGuard_abortsWhenMismatch() = runBlocking {
        // Setup user with target email but mismatched username
        val imposterId = db.userDao().insertUser(
            User(
                username = "NotLukas",
                email = "lunachroc07@gmail.com",
                passwordHash = "",
                role = UserRole.FREE_MEMBER,
                isApproved = true
            )
        )
        assertTrue(imposterId > 0L)

        val result = FinSmartDatabase.purgeTargetDeveloperAccountAtomically(db)
        assertTrue("Purge must abort and return failure on ambiguity", result.isFailure)
        assertNotNull("User must not be deleted on ambiguity", db.userDao().getUserById(imposterId))
    }

    @Test
    fun testDeleteSampleFeedbackByEmailAndName_caseInsensitive() = runBlocking {
        db.feedbackDao().insertFeedback(
            FeedbackItem(
                id = "fb_upper",
                userId = null,
                userName = "LUKAS",
                userEmail = "LUNACHROC07@GMAIL.COM",
                rating = 5,
                category = "Bug / Kendala",
                message = "Testing uppercase sample"
            )
        )
        db.feedbackDao().insertFeedback(
            FeedbackItem(
                id = "fb_keep",
                userId = null,
                userName = "Sarah",
                userEmail = "sarah@example.com",
                rating = 5,
                category = "Saran Fitur",
                message = "Sarah feedback"
            )
        )

        val deletedCount = db.feedbackDao().deleteSampleFeedbackByEmailAndName("lunachroc07@gmail.com", "Lukas")
        assertEquals("Must delete exactly 1 matching feedback ignoring case", 1, deletedCount)

        val remaining = db.feedbackDao().getAllFeedbacks().first()
        assertEquals(1, remaining.size)
        assertEquals("fb_keep", remaining.first().id)
    }

    @Test
    fun testEmptyFeedbackTableRemainsEmpty() = runBlocking {
        // Table starts empty
        val list = db.feedbackDao().getAllFeedbacks().first()
        assertTrue("Empty table must remain empty", list.isEmpty())

        // Executing purge does not seed any data
        val result = FinSmartDatabase.purgeTargetDeveloperAccountAtomically(db)
        assertTrue(result.isSuccess)
        assertFalse(result.getOrDefault(false)) // returns false because no target user existed
        assertTrue("Feedbacks must remain empty", db.feedbackDao().getAllFeedbacks().first().isEmpty())
    }

    @Test
    fun testPurgeTargetDeveloperAccountAtomically_usernameMatches_emailDiffers_aborts() = runBlocking {
        val user = db.userDao().insertUser(
            User(
                username = "Lukas",
                email = "different_email@gmail.com",
                passwordHash = "",
                role = UserRole.MASTER_DEV,
                isApproved = true
            )
        )
        assertTrue(user > 0L)

        val result = FinSmartDatabase.purgeTargetDeveloperAccountAtomically(db)
        assertTrue("Purge must abort when email differs", result.isFailure)
        assertNotNull("User must NOT be deleted", db.userDao().getUserById(user))
    }

    @Test
    fun testPurgeTargetDeveloperAccountAtomically_emailMatches_usernameDiffers_aborts() = runBlocking {
        val user = db.userDao().insertUser(
            User(
                username = "DifferentUser",
                email = "lunachroc07@gmail.com",
                passwordHash = "",
                role = UserRole.MASTER_DEV,
                isApproved = true
            )
        )
        assertTrue(user > 0L)

        val result = FinSmartDatabase.purgeTargetDeveloperAccountAtomically(db)
        assertTrue("Purge must abort when username differs", result.isFailure)
        assertNotNull("User must NOT be deleted", db.userDao().getUserById(user))
    }

    @Test
    fun testPurgeTargetDeveloperAccountAtomically_roleMismatch_aborts() = runBlocking {
        val user = db.userDao().insertUser(
            User(
                username = "Lukas",
                email = "lunachroc07@gmail.com",
                passwordHash = "",
                role = UserRole.FREE_MEMBER, // Not MASTER_DEV
                isApproved = true
            )
        )
        assertTrue(user > 0L)

        val result = FinSmartDatabase.purgeTargetDeveloperAccountAtomically(db)
        assertTrue("Purge must abort when role is not MASTER_DEV", result.isFailure)
        assertNotNull("User must NOT be deleted", db.userDao().getUserById(user))
    }

    @Test
    fun testPurgeTargetDeveloperAccountAtomically_allFinancialTables_noFkViolation() = runBlocking {
        val targetId = db.userDao().insertUser(
            User(
                username = "Lukas",
                email = "lunachroc07@gmail.com",
                passwordHash = "",
                role = UserRole.MASTER_DEV,
                isApproved = true
            )
        )
        val walletId = db.walletDao().insertWallet(Wallet(name = "BCA Target", type = WalletType.BANK, balance = 100000.0, userId = targetId))
        val txId = db.transactionDao().insertTransaction(
            com.example.data.model.TransactionItem(
                title = "Payment Tx",
                amount = 100000.0,
                type = com.example.data.model.TransactionType.EXPENSE,
                category = "Tagihan & Utilitas",
                walletId = walletId,
                walletName = "BCA Target",
                accountMode = AccountMode.PERSONAL.name,
                userId = targetId
            )
        )

        db.recurringScheduleDao().insertSchedule(
            com.example.data.model.RecurringSchedule(
                id = "rec_1",
                title = "Tagihan",
                amount = 50000.0,
                type = com.example.data.model.TransactionType.EXPENSE,
                category = "Tagihan & Utilitas",
                categoryIcon = "receipt",
                walletId = walletId,
                walletName = "BCA Target",
                interval = com.example.util.RecurringInterval.MONTHLY,
                userId = targetId
            )
        )

        db.installmentPlanDao().insertInstallmentPlan(
            com.example.data.model.InstallmentPlanItem(
                id = "plan_1",
                name = "Kredit HP",
                provider = "Bank ABC",
                installmentAmount = 100000.0,
                totalTenor = 12,
                paidCount = 1,
                startDate = "2026-11-01",
                nextDueDate = "2026-12-01",
                userId = targetId
            )
        )

        val dbHelper = db.openHelper.writableDatabase

        dbHelper.execSQL("""
            INSERT INTO installment_periods (id, planId, currencyCode, userId, periodNumber, amountDueMinorUnits, amountPaidMinorUnits, dueDate, isPaid)
            VALUES ('per_1', 'plan_1', 'IDR', $targetId, 1, 10000000, 10000000, '2026-11-15', 1)
        """.trimIndent())

        dbHelper.execSQL("""
            INSERT INTO installment_payments (id, planId, transactionId, currencyCode, userId, totalMinorUnits, unallocatedMinorUnits, isVoid, reversalTransactionId, createdAt)
            VALUES ('pay_1', 'plan_1', $txId, 'IDR', $targetId, 10000000, 0, 0, NULL, 1731000000)
        """.trimIndent())

        dbHelper.execSQL("""
            INSERT INTO installment_payment_allocations (id, paymentId, periodId, currencyCode, userId, allocatedMinorUnits, isVoid, allocatedAt)
            VALUES ('alloc_1', 'pay_1', 'per_1', 'IDR', $targetId, 10000000, 0, 1731000000)
        """.trimIndent())

        val result = FinSmartDatabase.purgeTargetDeveloperAccountAtomically(db)
        assertTrue("Purge with all RESTRICT child entities must succeed", result.isSuccess)
        assertNull("Target user must be purged", db.userDao().getUserById(targetId))
    }

    @Test
    fun testPurgeTargetDeveloperAccountAtomically_idempotentSecondExecution() = runBlocking {
        // First execution when user exists
        val targetId = db.userDao().insertUser(
            User(
                username = "Lukas",
                email = "lunachroc07@gmail.com",
                passwordHash = "",
                role = UserRole.MASTER_DEV,
                isApproved = true
            )
        )
        val budiId = db.userDao().insertUser(
            User(username = "Budi", email = "budi@example.com", passwordHash = "", role = UserRole.FREE_MEMBER, isApproved = true)
        )
        val budiWallet = db.walletDao().insertWallet(Wallet(name = "Budi Cash", type = WalletType.CASH, balance = 20000.0, userId = budiId))

        val result1 = FinSmartDatabase.purgeTargetDeveloperAccountAtomically(db)
        assertTrue(result1.isSuccess)
        assertTrue(result1.getOrNull() == true)

        // Second startup execution when user no longer exists
        val result2 = FinSmartDatabase.purgeTargetDeveloperAccountAtomically(db)
        assertTrue(result2.isSuccess)
        assertFalse("Second execution returns false as user is already purged", result2.getOrNull() == true)

        // Budi user and wallet remain completely untouched
        assertNotNull(db.userDao().getUserById(budiId))
        assertEquals(1, db.walletDao().getWalletCount(budiId))
    }

    // =========================================================================
    // 4. UI CLIPPING SAFEGUARDS & ROW OVERFLOW PREVENTION
    // =========================================================================

    @Test
    fun testWalletsScreen_headerStringsFitBounds() {
        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)
        val titleText = AppStrings.t("Total Saldo Akun Terdaftar", "Total Balance of Registered Accounts")
        val badgeText = "4 ${AppStrings.t("Akun", "Accounts")}"

        assertEquals("Total Balance of Registered Accounts", titleText)
        assertEquals("4 Accounts", badgeText)

        // Verify badge string does not contain newline or vertical breaks
        assertFalse(badgeText.contains("\n"))
        assertTrue(badgeText.length <= 15)
    }

    // =========================================================================
    // 5. PHASE 1G.6: RESTORE LOCK SAFETY & PERMANENT RESTORATION GUARDS
    // =========================================================================

    @Test
    fun testReentrantPurgeUnderExclusiveMaintenance_completesCleanly() = runBlocking {
        // Direct proof that purge can be invoked reentrantly inside withExclusiveMaintenance
        val result = db.writeCoordinator.withExclusiveMaintenance {
            FinSmartDatabase.purgeTargetDeveloperAccountAtomically(db)
        }
        assertTrue("Reentrant purge under withExclusiveMaintenance must succeed", result.isSuccess)
    }

    @Test
    fun testRestoreUserDataInTransaction_purgesLukasUnderExclusiveMaintenanceWithoutDeadlock() = runBlocking {
        // Seed legitimate user
        val legitimateUserId = 888L
        val legUser = User(
            id = legitimateUserId,
            username = "ActiveUser",
            email = "active@example.com",
            role = UserRole.FREE_MEMBER
        )
        db.userDao().insertUser(legUser)

        // Seed target developer Lukas
        val lukasId = 999L
        val lukas = User(
            id = lukasId,
            username = "Lukas",
            email = "lunachroc07@gmail.com",
            role = UserRole.MASTER_DEV
        )
        db.userDao().insertUser(lukas)

        val payload = BackupDataPayload(
            version = 1,
            wallets = listOf(
                Wallet(id = 1L, name = "Active Wallet", type = WalletType.BANK, balance = 100000.0, userId = legitimateUserId)
            ),
            transactions = emptyList(),
            categories = emptyList(),
            budgetLimits = emptyList(),
            debts = emptyList(),
            assets = emptyList(),
            splitBillHistory = emptyList(),
            installmentPlans = emptyList(),
            recurringSchedules = emptyList()
        )

        // Execute restoreUserDataInTransaction which runs purge INSIDE withExclusiveMaintenance
        com.example.ui.viewmodel.FinanceViewModel.restoreUserDataInTransaction(
            database = db,
            payload = payload,
            targetUserId = legitimateUserId
        )

        // Verify Lukas is completely purged
        assertNull("Lukas user must be purged after restore", db.userDao().getUserById(lukasId))
        assertNull(db.userDao().getUserByEmail("lunachroc07@gmail.com"))
        assertNull(db.userDao().getUserByIdentifier("Lukas"))

        // Verify ActiveUser and restored wallet are present and untouched
        val restoredUser = db.userDao().getUserById(legitimateUserId)
        assertNotNull(restoredUser)
        assertEquals("ActiveUser", restoredUser?.username)
        val wallets = db.walletDao().getAllWallets(legitimateUserId).first()
        assertEquals(1, wallets.size)
        assertEquals("Active Wallet", wallets.first().name)
    }

    @Test
    fun testSessionManagerRestoreSession_blocksLukasEvenIfPresentInDatabase() = runBlocking {
        SessionManager.initialize(context)
        val lukasId = 991L
        val lukas = User(
            id = lukasId,
            username = "Lukas",
            email = "lunachroc07@gmail.com",
            role = UserRole.MASTER_DEV
        )
        db.userDao().insertUser(lukas)

        // Set saved session ID to Lukas
        val prefs = context.getSharedPreferences("finsmart_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("saved_user_id", lukasId).apply()

        // Even in DEBUG mode (isDebug = true), SessionManager MUST reject restoring Lukas
        val restored = SessionManager.restoreSession(db.userDao(), isDebug = true)
        assertFalse("SessionManager must NEVER restore Lukas session", restored)
        assertEquals("Saved user ID must be wiped", -1L, prefs.getLong("saved_user_id", -1L))
        assertNull("Current session user must be null", SessionManager.currentUser.value)
    }

    // =========================================================================
    // 7. PHASE 1G.7 MAINTENANCE FAILURE-HANDLING & STARTUP BARRIER TESTS
    // =========================================================================

    @Test
    fun testPurgeTargetDeveloperAccountAtomically_failsExplicitlyOnAmbiguity() = runBlocking {
        // Create conflicting user with target email but mismatched username
        val imposter = User(
            id = 888L,
            username = "ImposterUser",
            email = "lunachroc07@gmail.com",
            role = UserRole.MASTER_DEV
        )
        db.userDao().insertUser(imposter)

        val result = FinSmartDatabase.purgeTargetDeveloperAccountAtomically(db)
        assertTrue("Ambiguous target user must fail purge", result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertTrue("Error message must indicate mismatched username", ex?.message?.contains("mismatched username") == true)

        // Verify imposter was NOT deleted
        assertNotNull("Imposter user must remain untouched on failure", db.userDao().getUserById(888L))
    }

    @Test
    fun testPurgeTargetDeveloperAccountAtomically_idempotentSuccessWhenTargetAbsent() = runBlocking {
        // Database is clean of Lukas
        assertNull(db.userDao().getUserByEmail("lunachroc07@gmail.com"))
        assertNull(db.userDao().getUserByIdentifier("Lukas"))

        val result = FinSmartDatabase.purgeTargetDeveloperAccountAtomically(db)
        assertTrue("Idempotent run must succeed", result.isSuccess)
        assertFalse("Idempotent run must indicate no user deleted", result.getOrNull() == true)
    }

    @Test
    fun testAuthViewModel_startupPurgeExecutionAndMaintenanceState() = runBlocking {
        // Insert Lukas before ViewModel init
        val lukasId = 992L
        val lukas = User(
            id = lukasId,
            username = "Lukas",
            email = "lunachroc07@gmail.com",
            role = UserRole.MASTER_DEV
        )
        db.userDao().insertUser(lukas)
        assertNotNull(db.userDao().getUserById(lukasId))

        try {
            // Set singleton so AuthViewModel uses this in-memory DB
            setDatabaseSingleton(db)

            val authVm = com.example.ui.viewmodel.AuthViewModel(context as android.app.Application)

            // Idle Robolectric main looper so viewModelScope coroutines execute
            org.robolectric.shadows.ShadowLooper.idleMainLooper()

            // Wait for maintenance to finish
            var attempts = 0
            while (authVm.maintenanceState.value !is com.example.ui.viewmodel.MaintenanceState.Success && attempts < 30) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(50)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                attempts++
            }

            assertTrue("Maintenance must transition to Success", authVm.maintenanceState.value is com.example.ui.viewmodel.MaintenanceState.Success)
            assertNull("Lukas must be purged from database by startup maintenance", db.userDao().getUserById(lukasId))
            assertNull(db.userDao().getUserByEmail("lunachroc07@gmail.com"))
            assertFalse("Session user must never be Lukas", authVm.currentUser.value?.username == "Lukas")
        } finally {
            setDatabaseSingleton(null)
            com.example.data.local.DatabaseWriteCoordinator.resetForTesting()
        }
    }
}
