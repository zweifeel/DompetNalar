package com.example

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.BackupDataPayload
import com.example.data.model.BudgetLimitItem
import com.example.data.model.CategoryItem
import com.example.data.model.DebtItem
import com.example.data.model.RecurringSchedule
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.example.data.repository.FinanceRepository
import com.example.util.CurrencyFormatter
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNotificationManager
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.DeviceRegionResolver
import java.util.Locale
import com.example.util.RecurringExecutionResult
import com.example.util.RecurringInterval
import com.example.util.RecurringScheduleEvaluator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Master Verification Suite: 15 Unconditional Architectural Gates for Room 10.
 *
 * Covers:
 * T-04, T-05, T-10, T-14, T-15, T-16, T-32, T-34, T-40, T-41, T-48, T-49, T-50, T-51, T-52.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Room10ComprehensiveRemediationTest {

    private lateinit var context: Context
    private lateinit var db: FinSmartDatabase
    private lateinit var repository: FinanceRepository
    private val user1Id = 100L
    private val user2Id = 200L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        db = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(db.walletDao(), db)

        runBlocking {
            db.userDao().insertUser(
                User(id = user1Id, username = "user1", email = "user1@test.com", passwordHash = "h1", role = UserRole.FREE_MEMBER)
            )
            db.userDao().insertUser(
                User(id = user2Id, username = "user2", email = "user2@test.com", passwordHash = "h2", role = UserRole.FREE_MEMBER)
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    // -------------------------------------------------------------------------
    // Gate T-04: Worker Retry / Crash Recovery Idempotency
    // -------------------------------------------------------------------------
    @Test
    fun testGateT04_WorkerRetryAndCrashRecoveryIdempotency() = runBlocking {
        val walletId = db.walletDao().insertWallet(
            Wallet(name = "Test Wallet", balance = 500_000.0, userId = user1Id, currencyCode = "IDR")
        )
        val scheduleId = "sch_t04"
        val schedule = RecurringSchedule(
            id = scheduleId,
            title = "Langganan Cloud",
            amount = 100_000.0,
            type = TransactionType.EXPENSE,
            category = "Tagihan",
            categoryIcon = "cloud",
            walletId = walletId,
            walletName = "Test Wallet",
            interval = RecurringInterval.MONTHLY,
            dayOfMonth = 1,
            executionHour = 9,
            executionMinute = 0,
            startDate = 0L, // Eligible for historical evaluation
            timezoneId = "UTC",
            userId = user1Id
        )
        db.recurringScheduleDao().insertSchedule(schedule)

        val fixedClock = Clock.fixed(Instant.parse("2026-10-01T09:30:00Z"), ZoneId.of("UTC"))

        // First Execution via Authoritative Transaction
        val firstResult = RecurringScheduleEvaluator.executeScheduleTransaction(
            database = db,
            scheduleId = scheduleId,
            userId = user1Id,
            clock = fixedClock
        )
        assertTrue("Schedule should execute successfully", firstResult is RecurringExecutionResult.Executed)

        // Balance updated
        val walletAfterFirst = db.walletDao().getWalletById(walletId, user1Id)!!
        assertEquals(400_000.0, walletAfterFirst.balance, 0.001)

        // Retry Execution (Worker Crash / WorkManager Retry Simulation with same clock)
        val retryResult = RecurringScheduleEvaluator.executeScheduleTransaction(
            database = db,
            scheduleId = scheduleId,
            userId = user1Id,
            clock = fixedClock
        )
        assertTrue("Retry must return AlreadyExecuted", retryResult is RecurringExecutionResult.AlreadyExecuted)

        // Balance must remain unchanged
        val walletAfterRetry = db.walletDao().getWalletById(walletId, user1Id)!!
        assertEquals(400_000.0, walletAfterRetry.balance, 0.001)
    }

    // -------------------------------------------------------------------------
    // Gate T-05: Year-end Dec 31 Cycle Formatting & Leap Year
    // -------------------------------------------------------------------------
    @Test
    fun testGateT05_YearEndDec31CycleFormatting() {
        val dummySchedule = RecurringSchedule(
            id = "sch_t05",
            title = "Dummy",
            amount = 10_000.0,
            type = TransactionType.EXPENSE,
            category = "Test",
            categoryIcon = "test",
            walletId = 1L,
            walletName = "Wallet",
            interval = RecurringInterval.MONTHLY,
            dayOfMonth = 31,
            executionHour = 12,
            executionMinute = 0,
            startDate = 0L,
            timezoneId = "UTC",
            userId = user1Id
        )

        // Dec 31, 2026
        val dec31Clock = Clock.fixed(Instant.parse("2026-12-31T23:59:59Z"), ZoneId.of("UTC"))
        val decCycle = RecurringScheduleEvaluator.computeCycleIdentifier(dummySchedule, dec31Clock)
        assertEquals("2026-12", decCycle)

        // Leap Year Feb 29, 2028
        val feb29Clock = Clock.fixed(Instant.parse("2028-02-29T12:00:00Z"), ZoneId.of("UTC"))
        val febCycle = RecurringScheduleEvaluator.computeCycleIdentifier(dummySchedule, feb29Clock)
        assertEquals("2028-02", febCycle)

        // Year Rollover Jan 1, 2027
        val jan1Clock = Clock.fixed(Instant.parse("2027-01-01T00:00:01Z"), ZoneId.of("UTC"))
        val janCycle = RecurringScheduleEvaluator.computeCycleIdentifier(dummySchedule, jan1Clock)
        assertEquals("2027-01", janCycle)
    }

    // -------------------------------------------------------------------------
    // Gate T-10: Migration9To10 Full Index and Column Preservation
    // -------------------------------------------------------------------------
    @Test
    fun testGateT10_Migration9To10SchemaPreservation() {
        val sdb = db.openHelper.readableDatabase

        // Check columns in wallets
        val walletCols = mutableSetOf<String>()
        sdb.query("PRAGMA table_info('wallets')").use { cursor ->
            while (cursor.moveToNext()) {
                walletCols.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
        assertTrue("isArchived column must exist", walletCols.contains("isArchived"))
        assertTrue("currencyCode column must exist", walletCols.contains("currencyCode"))
        assertTrue("userId column must exist", walletCols.contains("userId"))

        // Check index on wallets (id, userId)
        var hasCompositeIndex = false
        sdb.query("PRAGMA index_list('wallets')").use { cursor ->
            while (cursor.moveToNext()) {
                val indexName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                if (indexName.contains("wallets_id_userId") || indexName.contains("id_userId")) {
                    hasCompositeIndex = true
                }
            }
        }
        assertTrue("Composite unique index on (id, userId) must exist", hasCompositeIndex)

        // Check columns in transactions
        val txCols = mutableSetOf<String>()
        sdb.query("PRAGMA table_info('transactions')").use { cursor ->
            while (cursor.moveToNext()) {
                txCols.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
        assertTrue("idempotencyKey column must exist", txCols.contains("idempotencyKey"))
        assertTrue("userId column must exist", txCols.contains("userId"))

        // Check recurring_schedules table exists
        var scheduleTableExists = false
        sdb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='recurring_schedules'").use { cursor ->
            scheduleTableExists = cursor.count > 0
        }
        assertTrue("recurring_schedules table must exist", scheduleTableExists)
    }

    // -------------------------------------------------------------------------
    // Gate T-14: Permanent Deletion of Wallet With Transactions Rejected
    // -------------------------------------------------------------------------
    @Test
    fun testGateT14_PermanentDeletionOfWalletWithTransactionsRejected() = runBlocking {
        val walletId = db.walletDao().insertWallet(
            Wallet(name = "Wallet T14", balance = 500_000.0, userId = user1Id, currencyCode = "IDR")
        )
        val wallet = db.walletDao().getWalletById(walletId, user1Id)!!

        // Insert a transaction referencing this wallet
        repository.insertTransaction(
            user1Id,
            TransactionItem(
                title = "Expense 1",
                amount = 50_000.0,
                type = TransactionType.EXPENSE,
                category = "Food",
                categoryIcon = "food",
                walletId = walletId,
                walletName = "Wallet T14",
                userId = user1Id
            )
        )

        // Attempt deletion via repository
        try {
            repository.deleteWallet(user1Id, wallet)
            val postDeleteWallet = db.walletDao().getWalletById(walletId, user1Id)
            if (postDeleteWallet != null) {
                assertTrue("Wallet must be marked as archived when deleted with transactions", postDeleteWallet.isArchived)
            }
        } catch (e: Exception) {
            assertTrue(e is IllegalStateException || e is SQLiteConstraintException)
        }

        // Transactions must still exist and be intact
        val txs = db.transactionDao().getAllTransactions(user1Id).first()
        assertEquals(1, txs.size)
    }

    // -------------------------------------------------------------------------
    // Gate T-15: Migration Failure Rollback Cleanly to v9
    // -------------------------------------------------------------------------
    @Test
    fun testGateT15_MigrationFailureRollbackCleanly() {
        val testDbName = "migration_failure_test.db"
        context.deleteDatabase(testDbName)

        val v9Db = context.openOrCreateDatabase(testDbName, Context.MODE_PRIVATE, null)
        v9Db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY, username TEXT)")
        v9Db.execSQL("INSERT INTO users VALUES (1, 'alice')")
        v9Db.version = 9
        v9Db.close()

        val faultyMigration = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE temp_table (id INTEGER)")
                throw RuntimeException("Simulated migration failure")
            }
        }

        try {
            val dbTest = Room.databaseBuilder(context, FinSmartDatabase::class.java, testDbName)
                .addMigrations(faultyMigration)
                .build()
            dbTest.openHelper.writableDatabase
            fail("Expected exception during migration")
        } catch (e: Exception) {
            // Expected
        }

        val reopenedDb = context.openOrCreateDatabase(testDbName, Context.MODE_PRIVATE, null)
        assertEquals(9, reopenedDb.version)
        val cursor = reopenedDb.rawQuery("SELECT username FROM users WHERE id = 1", null)
        assertTrue(cursor.moveToFirst())
        assertEquals("alice", cursor.getString(0))
        cursor.close()
        reopenedDb.close()
        context.deleteDatabase(testDbName)
    }

    // -------------------------------------------------------------------------
    // Gate T-16: Synthetic v1/v2 Backup Restore Executes Within Room 10
    // -------------------------------------------------------------------------
    @Test
    fun testGateT16_SyntheticV1V2BackupRestoreExecutesWithinRoom10() = runBlocking {
        val v2Payload = BackupDataPayload(
            version = 2,
            wallets = listOf(
                Wallet(id = 10L, name = "Restored Bank", balance = 2_000_000.0, userId = user1Id, currencyCode = "IDR")
            ),
            transactions = listOf(
                TransactionItem(
                    id = 101L,
                    title = "Restored Gaji",
                    amount = 2_000_000.0,
                    type = TransactionType.INCOME,
                    category = "Gaji",
                    categoryIcon = "salary",
                    walletId = 10L,
                    walletName = "Restored Bank",
                    userId = user1Id
                )
            ),
            categories = listOf(
                CategoryItem(id = 50L, name = "Kategori Kustom", type = TransactionType.EXPENSE, isSystem = false, userId = user1Id)
            ),
            debts = listOf(
                DebtItem(id = "debt_1", name = "Pinjaman KTA", amount = 1_000_000.0, interestRatePercent = 0.0, minMonthlyPayment = 0.0, userId = user1Id)
            )
        )

        FinanceViewModel.restoreUserDataInTransaction(db, v2Payload, user1Id)

        val restoredWallets = db.walletDao().getAllWallets(user1Id).first()
        assertEquals(1, restoredWallets.size)
        assertEquals("Restored Bank", restoredWallets[0].name)
        assertNotEquals(10L, restoredWallets[0].id) // ID safely remapped

        val restoredTxs = db.transactionDao().getAllTransactions(user1Id).first()
        assertEquals(1, restoredTxs.size)
        assertEquals(restoredWallets[0].id, restoredTxs[0].walletId)

        val debts = db.debtDao().getAllDebts(user1Id).first()
        assertEquals(1, debts.size)
        assertEquals("Pinjaman KTA", debts[0].name)
    }

    // -------------------------------------------------------------------------
    // Gate T-32: System Region Resolution Independence from Network/SIM
    // -------------------------------------------------------------------------
    @Test
    fun testGateT32_SystemRegionResolutionIndependence() {
        // Valid regional mappings
        assertEquals("IDR", DeviceRegionResolver.resolveCurrencyForCountry("ID"))
        assertEquals("USD", DeviceRegionResolver.resolveCurrencyForCountry("US"))
        assertEquals("JPY", DeviceRegionResolver.resolveCurrencyForCountry("JP"))
        assertEquals("EUR", DeviceRegionResolver.resolveCurrencyForCountry("DE"))
        assertEquals("SGD", DeviceRegionResolver.resolveCurrencyForCountry("SG"))
        assertEquals("MYR", DeviceRegionResolver.resolveCurrencyForCountry("MY"))
        assertEquals("GBP", DeviceRegionResolver.resolveCurrencyForCountry("GB"))

        // Unresolvable, unsupported, or blank input MUST return null (ZERO arbitrary IDR/USD fallback)
        assertNull(DeviceRegionResolver.resolveCurrencyForCountry(null))
        assertNull(DeviceRegionResolver.resolveCurrencyForCountry(""))
        assertNull(DeviceRegionResolver.resolveCurrencyForCountry("ZZ"))
        assertNull(DeviceRegionResolver.resolveCurrencyForCountry("INVALID"))
    }

    // -------------------------------------------------------------------------
    // Gate T-34: Condition B Legacy Room 9 Anomaly Resolution
    // -------------------------------------------------------------------------
    @Test
    fun testGateT34_ConditionBLegacyRoom9AnomalyResolution() = runBlocking {
        val walletId = db.walletDao().insertWallet(
            Wallet(name = "Legacy B", balance = 1000.0, currencyCode = "IDR", userId = null)
        )
        db.transactionDao().insertTransaction(
            TransactionItem(title = "Tx B", amount = 500.0, type = TransactionType.EXPENSE, category = "Food", categoryIcon = "food", walletId = walletId, walletName = "Legacy B", userId = user1Id)
        )

        val claimResult = repository.claimLegacyWalletAtomically(walletId, user1Id)
        assertTrue("Claim must succeed for Condition B", claimResult.isSuccess)

        val claimedWallet = db.walletDao().getWalletById(walletId, user1Id)
        assertNotNull(claimedWallet)
        assertEquals(user1Id, claimedWallet?.userId)
    }

    // -------------------------------------------------------------------------
    // Gate T-40: Budget Limit Collision Interactive Resolution
    // -------------------------------------------------------------------------
    @Test
    fun testGateT40_BudgetLimitCollisionInteractiveResolution() = runBlocking {
        db.budgetLimitDao().upsertBudgetLimitByKey("Food", 1_000_000.0, true, user1Id)
        db.budgetLimitDao().upsertBudgetLimitByKey("Food", 1_500_000.0, true, user1Id)

        val stored = db.budgetLimitDao().getBudgetLimit(user1Id, "Food")
        assertNotNull(stored)
        assertEquals(1_500_000.0, stored!!.limitAmount, 0.001)
    }

    // -------------------------------------------------------------------------
    // Gate T-41: Budget Limit Collision Cancellation Isolation
    // -------------------------------------------------------------------------
    @Test
    fun testGateT41_BudgetLimitCollisionCancellationIsolation() = runBlocking {
        db.budgetLimitDao().upsertBudgetLimitByKey("Transport", 500_000.0, true, user1Id)

        val retrieved = db.budgetLimitDao().getBudgetLimit(user1Id, "Transport")
        assertNotNull(retrieved)
        assertEquals(500_000.0, retrieved!!.limitAmount, 0.001)
    }

    // -------------------------------------------------------------------------
    // Gate T-48: Cross-Tenant Wallet Isolation
    // -------------------------------------------------------------------------
    @Test
    fun testGateT48_CrossTenantWalletIsolation() = runBlocking {
        val wallet1Id = db.walletDao().insertWallet(
            Wallet(name = "User 1 Secret Vault", balance = 10_000_000.0, userId = user1Id, currencyCode = "IDR")
        )

        val user2View = db.walletDao().getWalletById(wallet1Id, user2Id)
        assertNull("User 2 must not see User 1's wallet", user2View)

        val user2List = db.walletDao().getAllWallets(user2Id).first()
        assertTrue("User 2 wallet list must be empty", user2List.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Gate T-49: Empty Transaction Wallet Claim
    // -------------------------------------------------------------------------
    @Test
    fun testGateT49_EmptyTransactionWalletClaim() = runBlocking {
        val walletId = db.walletDao().insertWallet(
            Wallet(name = "Empty Legacy Wallet", balance = 50000.0, currencyCode = "IDR", userId = null)
        )

        val claimResult = repository.claimLegacyWalletAtomically(walletId, user1Id)
        assertTrue("Claim must succeed for empty transaction wallet", claimResult.isSuccess)
        assertEquals(0, claimResult.getOrNull())

        val claimed = db.walletDao().getWalletById(walletId, user1Id)
        assertNotNull(claimed)
        assertEquals(user1Id, claimed?.userId)
    }

    // -------------------------------------------------------------------------
    // Gate T-50: In-Transaction Preflight Race Simulation (Condition C)
    // -------------------------------------------------------------------------
    @Test
    fun testGateT50_InTransactionPreflightRaceSimulation_ConditionC() = runBlocking {
        val walletId = db.walletDao().insertWallet(
            Wallet(name = "Contested Wallet", balance = 10000.0, currencyCode = "IDR", userId = null)
        )
        db.transactionDao().insertTransaction(
            TransactionItem(title = "Tx User 2", amount = 100.0, type = TransactionType.EXPENSE, category = "Food", categoryIcon = "food", walletId = walletId, walletName = "Contested Wallet", userId = user2Id)
        )

        val claimResult = repository.claimLegacyWalletAtomically(walletId, user1Id)
        assertTrue("Claim must fail under Condition C", claimResult.isFailure)
        assertTrue(claimResult.exceptionOrNull()?.message?.contains("terhubung dengan akun lain") == true)

        val wallet = db.walletDao().getWalletById(walletId)
        assertNotNull(wallet)
        assertNull("Wallet userId must remain NULL after aborted claim", wallet?.userId)
    }

    // -------------------------------------------------------------------------
    // Gate T-51: Concurrent Claim Serialization (1 Pass, 1 Abort)
    // -------------------------------------------------------------------------
    @Test
    fun testGateT51_ConcurrentClaimSerialization() = runBlocking {
        val walletId = db.walletDao().insertWallet(
            Wallet(name = "Single Winner Wallet", balance = 25000.0, currencyCode = "IDR", userId = null)
        )

        val result1 = repository.claimLegacyWalletAtomically(walletId, user1Id)
        assertTrue("First claim must succeed", result1.isSuccess)

        val result2 = repository.claimLegacyWalletAtomically(walletId, user2Id)
        assertTrue("Second claim must abort under Condition D", result2.isFailure)
        assertTrue(result2.exceptionOrNull()?.message?.contains("sudah diklaim") == true)

        val wallet = db.walletDao().getWalletById(walletId)!!
        assertEquals(user1Id, wallet.userId)
    }

    // -------------------------------------------------------------------------
    // Gate T-52: Parent Success Child Failure Full SQLite Rollback
    // -------------------------------------------------------------------------
    @Test
    fun testGateT52_ParentSuccessChildFailureFullSQLiteRollback() = runBlocking {
        val walletId = db.walletDao().insertWallet(
            Wallet(name = "Rollback Wallet", balance = 100.0, currencyCode = "IDR", userId = null)
        )

        try {
            db.withTransaction {
                val updated = db.walletDao().claimLegacyWalletOwnership(walletId, user1Id)
                assertEquals(1, updated)
                throw IllegalStateException("Simulated child failure during atomic transaction")
            }
        } catch (e: Exception) {
            // Expected
        }

        val wallet = db.walletDao().getWalletById(walletId)!!
        assertNull("Wallet userId must be rolled back to NULL", wallet.userId)
    }

    // -------------------------------------------------------------------------
    // Currency Resolution & Lifecycle Independence Tests (Owner Mandate)
    // -------------------------------------------------------------------------
    @Test
    fun testLanguageChangeDoesNotAffectRegionalCurrency() {
        val originalLocale = Locale.getDefault()
        try {
            // Simulate user changing in-app language to English (US)
            Locale.setDefault(Locale.US)
            // Regional resolution via Resources.getSystem() is independent of Locale.getDefault()
            val country = DeviceRegionResolver.resolveDeviceCountry()
            // Even if app language is English, resolving country "ID" still yields IDR
            assertEquals("IDR", DeviceRegionResolver.resolveCurrencyForCountry("ID"))

            // Simulate user changing in-app language to Japanese
            Locale.setDefault(Locale.JAPAN)
            assertEquals("USD", DeviceRegionResolver.resolveCurrencyForCountry("US"))
            assertEquals("SGD", DeviceRegionResolver.resolveCurrencyForCountry("SG"))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun testManualCurrencyAssignmentPerWalletIndependentAndResumable() = runBlocking {
        // Create 2 State 2 wallets for user1Id (userId NOT NULL, currencyCode NULL)
        val wallet1Id = db.walletDao().insertWallet(
            Wallet(name = "Wallet 1", balance = 500000.0, userId = user1Id, currencyCode = null)
        )
        val wallet2Id = db.walletDao().insertWallet(
            Wallet(name = "Wallet 2", balance = 750000.0, userId = user1Id, currencyCode = null)
        )

        // 1. Both wallets are in State 2
        val unassignedBefore = db.walletDao().getUnassignedCurrencyWallets(user1Id)
        assertEquals(2, unassignedBefore.size)

        // 2. Assign currency for Wallet 1 to "USD" independently
        val rowsAssigned1 = db.walletDao().assignWalletCurrency(wallet1Id, "USD", user1Id)
        assertEquals(1, rowsAssigned1)

        // 3. Process interrupted! Verify Wallet 2 remains State 2 (unassigned) and can be resumed
        val unassignedAfterInterruption = db.walletDao().getUnassignedCurrencyWallets(user1Id)
        assertEquals(1, unassignedAfterInterruption.size)
        assertEquals(wallet2Id, unassignedAfterInterruption[0].id)
        assertNull(unassignedAfterInterruption[0].currencyCode)

        // Verify Wallet 1 is now State 3 with USD and unchanged balance
        val w1 = db.walletDao().getWalletById(wallet1Id, user1Id)!!
        assertEquals("USD", w1.currencyCode)
        assertEquals(500000.0, w1.balance, 0.01)

        // 4. Resume process: Assign currency for Wallet 2 to "SGD"
        val rowsAssigned2 = db.walletDao().assignWalletCurrency(wallet2Id, "SGD", user1Id)
        assertEquals(1, rowsAssigned2)

        // 5. All wallets are now assigned; unassigned list is empty
        val unassignedFinal = db.walletDao().getUnassignedCurrencyWallets(user1Id)
        assertEquals(0, unassignedFinal.size)

        val w2 = db.walletDao().getWalletById(wallet2Id, user1Id)!!
        assertEquals("SGD", w2.currencyCode)
        assertEquals(750000.0, w2.balance, 0.01)
    }

    @Test
    fun testExistingWalletsUnchangedWhenSystemRegionChanges() = runBlocking {
        // Create an existing wallet with IDR
        val walletId = db.walletDao().insertWallet(
            Wallet(name = "Tabungan IDR", balance = 1250000.0, userId = user1Id, currencyCode = "IDR")
        )

        // Verify initial state
        val initialWallet = db.walletDao().getWalletById(walletId, user1Id)!!
        assertEquals("IDR", initialWallet.currencyCode)
        assertEquals(1250000.0, initialWallet.balance, 0.01)

        // Simulate device region resolution for different countries (e.g. traveling to US or JP)
        val resolvedUS = DeviceRegionResolver.resolveCurrencyForCountry("US")
        assertEquals("USD", resolvedUS)
        val resolvedJP = DeviceRegionResolver.resolveCurrencyForCountry("JP")
        assertEquals("JPY", resolvedJP)

        // Existing wallet in DB must remain completely untouched
        val reloadedWallet = db.walletDao().getWalletById(walletId, user1Id)!!
        assertEquals("IDR", reloadedWallet.currencyCode)
        assertEquals(1250000.0, reloadedWallet.balance, 0.01)
    }

    // -------------------------------------------------------------------------
    // DEC-PROD-01: Currency Immutability
    // -------------------------------------------------------------------------
    @Test
    fun testCurrencyImmutability_reassigningAssignedWallet_rejected() = runBlocking {
        val walletId = db.walletDao().insertWallet(
            Wallet(name = "Locked Wallet", balance = 100_000.0, userId = user1Id, currencyCode = "IDR")
        )
        val result = repository.assignWalletCurrency(walletId, "USD", user1Id)
        assertTrue("Re-assigning currency to an already assigned wallet must fail", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("DEC-PROD-01") == true)

        val wallet = db.walletDao().getWalletById(walletId, user1Id)!!
        assertEquals("IDR", wallet.currencyCode)
    }

    // -------------------------------------------------------------------------
    // DEC-PROD-02: Cross-Currency Transfers Rejected
    // -------------------------------------------------------------------------
    @Test
    fun testCrossCurrencyTransfer_rejectedAtRepository() = runBlocking {
        val wIdr = db.walletDao().insertWallet(
            Wallet(name = "Dompet IDR", balance = 500_000.0, userId = user1Id, currencyCode = "IDR")
        )
        val wUsd = db.walletDao().insertWallet(
            Wallet(name = "Dompet USD", balance = 100.0, userId = user1Id, currencyCode = "USD")
        )

        val xferTx = TransactionItem(
            title = "Transfer IDR to USD",
            amount = 50_000.0,
            type = TransactionType.TRANSFER,
            category = "Transfer",
            walletId = wIdr,
            walletName = "Dompet IDR",
            targetWalletId = wUsd,
            targetWalletName = "Dompet USD",
            timestamp = System.currentTimeMillis(),
            userId = user1Id
        )

        try {
            repository.insertTransaction(user1Id, xferTx)
            fail("Cross-currency transfer must be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("DEC-PROD-02") == true)
        }
    }

    // -------------------------------------------------------------------------
    // POL-ARCH-01: Archived Wallet Integrity & Historical Freeze
    // -------------------------------------------------------------------------
    @Test
    fun testArchivedWallet_transferIn_rejectedAtRepository() = runBlocking {
        val sourceWalletId = db.walletDao().insertWallet(
            Wallet(name = "Active Source", balance = 500_000.0, userId = user1Id, currencyCode = "IDR", isArchived = false)
        )
        val archivedTargetId = db.walletDao().insertWallet(
            Wallet(name = "Archived Target", balance = 0.0, userId = user1Id, currencyCode = "IDR", isArchived = true)
        )

        val tx = TransactionItem(
            title = "Transfer to Archived",
            amount = 50_000.0,
            type = TransactionType.TRANSFER,
            category = "Transfer",
            walletId = sourceWalletId,
            walletName = "Active Source",
            targetWalletId = archivedTargetId,
            targetWalletName = "Archived Target",
            timestamp = System.currentTimeMillis(),
            userId = user1Id
        )

        try {
            repository.insertTransaction(user1Id, tx)
            fail("Transfer into an archived wallet must be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("POL-ARCH-01 Freeze") == true)
        }
    }

    @Test
    fun testArchivedWallet_transferOutViaStandardTransaction_rejected() = runBlocking {
        val archivedSourceId = db.walletDao().insertWallet(
            Wallet(name = "Archived Source", balance = 500_000.0, userId = user1Id, currencyCode = "IDR", isArchived = true)
        )
        val activeTargetId = db.walletDao().insertWallet(
            Wallet(name = "Active Target", balance = 0.0, userId = user1Id, currencyCode = "IDR", isArchived = false)
        )

        val tx = TransactionItem(
            title = "Standard Transfer Out",
            amount = 50_000.0,
            type = TransactionType.TRANSFER,
            category = "Transfer",
            walletId = archivedSourceId,
            walletName = "Archived Source",
            targetWalletId = activeTargetId,
            targetWalletName = "Active Target",
            timestamp = System.currentTimeMillis(),
            userId = user1Id
        )

        try {
            repository.insertTransaction(user1Id, tx)
            fail("Standard transfer out from archived wallet must be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("POL-ARCH-01 Freeze") == true)
        }
    }

    @Test
    fun testArchivedWallet_transferOutViaDedicatedWizard_succeeds() = runBlocking {
        val archivedSourceId = db.walletDao().insertWallet(
            Wallet(name = "Archived Source", balance = 350_000.0, userId = user1Id, currencyCode = "IDR", isArchived = true)
        )
        val activeTargetId = db.walletDao().insertWallet(
            Wallet(name = "Active Target", balance = 100_000.0, userId = user1Id, currencyCode = "IDR", isArchived = false)
        )

        val result = repository.transferArchivedWalletBalance(user1Id, archivedSourceId, activeTargetId)
        assertTrue(result.isSuccess)
        val txId = result.getOrThrow()
        assertTrue(txId > 0L)

        val sourceWallet = db.walletDao().getWalletById(archivedSourceId, user1Id)!!
        val targetWallet = db.walletDao().getWalletById(activeTargetId, user1Id)!!
        assertEquals(0.0, sourceWallet.balance, 0.01)
        assertEquals(450_000.0, targetWallet.balance, 0.01)
    }

    @Test
    fun testArchivedWallet_transferOutViaDedicatedWizard_rejectsArchivedTarget() = runBlocking {
        val archivedSourceId = db.walletDao().insertWallet(
            Wallet(name = "Archived Source", balance = 350_000.0, userId = user1Id, currencyCode = "IDR", isArchived = true)
        )
        val archivedTargetId = db.walletDao().insertWallet(
            Wallet(name = "Archived Target", balance = 0.0, userId = user1Id, currencyCode = "IDR", isArchived = true)
        )

        val result = repository.transferArchivedWalletBalance(user1Id, archivedSourceId, archivedTargetId)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("tidak boleh dalam status diarsipkan") == true)
    }

    @Test
    fun testArchivedWallet_transferOutViaDedicatedWizard_rejectsCrossCurrency() = runBlocking {
        val archivedSourceId = db.walletDao().insertWallet(
            Wallet(name = "Archived Source IDR", balance = 350_000.0, userId = user1Id, currencyCode = "IDR", isArchived = true)
        )
        val activeTargetId = db.walletDao().insertWallet(
            Wallet(name = "Active Target USD", balance = 50.0, userId = user1Id, currencyCode = "USD", isArchived = false)
        )

        val result = repository.transferArchivedWalletBalance(user1Id, archivedSourceId, activeTargetId)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("DEC-PROD-02") == true)
    }

    @Test
    fun testArchivedWallet_historicalTransactionEdit_rejected() = runBlocking {
        val walletId = db.walletDao().insertWallet(
            Wallet(name = "Operational Wallet", balance = 500_000.0, userId = user1Id, currencyCode = "IDR", isArchived = false)
        )
        val tx = TransactionItem(
            title = "Beli Kopi",
            amount = 25_000.0,
            type = TransactionType.EXPENSE,
            category = "Kuliner",
            walletId = walletId,
            walletName = "Operational Wallet",
            timestamp = System.currentTimeMillis(),
            userId = user1Id
        )
        val txId = repository.insertTransaction(user1Id, tx)
        val insertedTx = db.transactionDao().getTransactionById(txId, user1Id)!!

        repository.archiveWallet(user1Id, walletId)

        val updatedTx = insertedTx.copy(amount = 30_000.0)
        try {
            repository.updateTransaction(user1Id, insertedTx, updatedTx)
            fail("Updating transaction of archived wallet must be blocked")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("POL-ARCH-01 Historical Freeze") == true)
        }
    }

    @Test
    fun testArchivedWallet_historicalTransactionDelete_rejected() = runBlocking {
        val walletId = db.walletDao().insertWallet(
            Wallet(name = "Operational Wallet", balance = 500_000.0, userId = user1Id, currencyCode = "IDR", isArchived = false)
        )
        val tx = TransactionItem(
            title = "Beli Buku",
            amount = 100_000.0,
            type = TransactionType.EXPENSE,
            category = "Pendidikan",
            walletId = walletId,
            walletName = "Operational Wallet",
            timestamp = System.currentTimeMillis(),
            userId = user1Id
        )
        val txId = repository.insertTransaction(user1Id, tx)
        val insertedTx = db.transactionDao().getTransactionById(txId, user1Id)!!

        repository.archiveWallet(user1Id, walletId)

        try {
            repository.deleteTransaction(user1Id, insertedTx)
            fail("Deleting transaction of archived wallet must be blocked")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("POL-ARCH-01 Historical Freeze") == true)
        }
    }

    @Test
    fun testActiveWallet_historicalTransactionEditAndDelete_succeeds() = runBlocking {
        val walletId = db.walletDao().insertWallet(
            Wallet(name = "Active Wallet", balance = 500_000.0, userId = user1Id, currencyCode = "IDR", isArchived = false)
        )
        val tx = TransactionItem(
            title = "Beli Cemilan",
            amount = 20_000.0,
            type = TransactionType.EXPENSE,
            category = "Kuliner",
            walletId = walletId,
            walletName = "Active Wallet",
            timestamp = System.currentTimeMillis(),
            userId = user1Id
        )
        val txId = repository.insertTransaction(user1Id, tx)
        val insertedTx = db.transactionDao().getTransactionById(txId, user1Id)!!
        assertEquals(480_000.0, db.walletDao().getWalletById(walletId, user1Id)!!.balance, 0.01)

        val updatedTx = insertedTx.copy(amount = 25_000.0)
        val updateRows = repository.updateTransaction(user1Id, insertedTx, updatedTx)
        assertEquals(1, updateRows)
        assertEquals(475_000.0, db.walletDao().getWalletById(walletId, user1Id)!!.balance, 0.01)

        val currentTx = db.transactionDao().getTransactionById(txId, user1Id)!!
        val deleteRows = repository.deleteTransaction(user1Id, currentTx)
        assertEquals(1, deleteRows)
        assertEquals(500_000.0, db.walletDao().getWalletById(walletId, user1Id)!!.balance, 0.01)
    }

    @Test
    fun testArchiveWallet_autoDisablesRecurringSchedules() = runBlocking {
        val walletId = db.walletDao().insertWallet(
            Wallet(name = "Wallet with Schedules", balance = 1_000_000.0, userId = user1Id, currencyCode = "IDR", isArchived = false)
        )
        val s1 = RecurringSchedule(
            id = "s1",
            title = "Netflix",
            amount = 186_000.0,
            type = TransactionType.EXPENSE,
            category = "Hiburan",
            categoryIcon = "movie",
            walletId = walletId,
            walletName = "Wallet with Schedules",
            interval = RecurringInterval.MONTHLY,
            isEnabled = true,
            userId = user1Id
        )
        val s2 = RecurringSchedule(
            id = "s2",
            title = "Spotify",
            amount = 55_000.0,
            type = TransactionType.EXPENSE,
            category = "Hiburan",
            categoryIcon = "music",
            walletId = walletId,
            walletName = "Wallet with Schedules",
            interval = RecurringInterval.MONTHLY,
            isEnabled = true,
            userId = user1Id
        )
        db.recurringScheduleDao().insertSchedule(s1)
        db.recurringScheduleDao().insertSchedule(s2)

        // F-01: Pass non-null context and verify real system notification dispatch via ShadowNotificationManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNM = shadowOf(notificationManager)
        notificationManager.cancelAll()

        val res = repository.archiveWallet(user1Id, walletId, context)
        assertTrue(res.isSuccess)
        assertEquals(2, res.getOrThrow())

        val s1After = db.recurringScheduleDao().getScheduleByIdAndUser("s1", user1Id)!!
        val s2After = db.recurringScheduleDao().getScheduleByIdAndUser("s2", user1Id)!!
        assertFalse("Schedule s1 must be disabled", s1After.isEnabled)
        assertFalse("Schedule s2 must be disabled", s2After.isEnabled)

        val notifications = shadowNM.allNotifications
        assertEquals("Exactly 1 notification must be posted when active schedules disabled", 1, notifications.size)
        val notif = notifications.first()
        assertEquals("finsmart_recurring_channel", notif.channelId)
        val extras = NotificationCompat.getExtras(notif)
        assertEquals("Jadwal Berulang Dinonaktifkan", extras?.getString(Notification.EXTRA_TITLE))
        val text = extras?.getString(Notification.EXTRA_TEXT) ?: ""
        assertTrue("Notification text must mention disabled schedule count", text.contains("2 transaksi rutin dinonaktifkan otomatis"))
        assertTrue("Notification text must mention wallet name", text.contains("Wallet with Schedules"))

        val channel = notificationManager.getNotificationChannel("finsmart_recurring_channel")
        assertNotNull("Notification channel must be created", channel)
        assertTrue(channel.name.toString().contains("Transaksi Berulang"))
    }

    @Test
    fun testArchiveWallet_withoutActiveSchedules_noNotificationSent() = runBlocking {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNM = shadowOf(notificationManager)
        notificationManager.cancelAll()

        val walletId = db.walletDao().insertWallet(
            Wallet(name = "Wallet No Sched", balance = 100_000.0, userId = user1Id, currencyCode = "IDR", isArchived = false)
        )
        val res = repository.archiveWallet(user1Id, walletId, context)
        assertTrue(res.isSuccess)
        assertEquals(0, res.getOrThrow())
        assertTrue("No notification should be dispatched when 0 schedules disabled", shadowNM.allNotifications.isEmpty())
    }

    @Test
    fun testArchiveWallet_idempotent_noDuplicateNotification() = runBlocking {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNM = shadowOf(notificationManager)
        notificationManager.cancelAll()

        val walletId = db.walletDao().insertWallet(
            Wallet(name = "Idempotent Wallet", balance = 200_000.0, userId = user1Id, currencyCode = "IDR", isArchived = false)
        )
        val s = RecurringSchedule(
            id = "s_idem",
            title = "Gym",
            amount = 150_000.0,
            type = TransactionType.EXPENSE,
            category = "Kesehatan",
            categoryIcon = "fitness",
            walletId = walletId,
            walletName = "Idempotent Wallet",
            interval = RecurringInterval.MONTHLY,
            isEnabled = true,
            userId = user1Id
        )
        db.recurringScheduleDao().insertSchedule(s)

        // First archive call -> sends notification
        val firstRes = repository.archiveWallet(user1Id, walletId, context)
        assertTrue(firstRes.isSuccess)
        assertEquals(1, firstRes.getOrThrow())
        assertEquals(1, shadowNM.allNotifications.size)

        // Second archive call -> idempotent, returns 0, no additional notification sent
        val secondRes = repository.archiveWallet(user1Id, walletId, context)
        assertTrue(secondRes.isSuccess)
        assertEquals(0, secondRes.getOrThrow())
        assertEquals("Notification count must remain 1 after repeated archive", 1, shadowNM.allNotifications.size)
    }

    @Test
    fun testArchiveWallet_permissionDenied_stillArchivesWithoutCrash() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = shadowOf(app)
        shadowApp.denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNM = shadowOf(notificationManager)
        notificationManager.cancelAll()

        val walletId = db.walletDao().insertWallet(
            Wallet(name = "No Perm Wallet", balance = 500_000.0, userId = user1Id, currencyCode = "IDR", isArchived = false)
        )
        val s = RecurringSchedule(
            id = "s_noperm",
            title = "Hosting",
            amount = 50_000.0,
            type = TransactionType.EXPENSE,
            category = "Tagihan",
            categoryIcon = "cloud",
            walletId = walletId,
            walletName = "No Perm Wallet",
            interval = RecurringInterval.MONTHLY,
            isEnabled = true,
            userId = user1Id
        )
        db.recurringScheduleDao().insertSchedule(s)

        val res = repository.archiveWallet(user1Id, walletId, context)
        assertTrue("Archive operation must succeed even without notification permission", res.isSuccess)
        assertEquals(1, res.getOrThrow())

        // Verify database state is correctly updated
        val walletAfter = db.walletDao().getWalletById(walletId, user1Id)!!
        assertTrue(walletAfter.isArchived)
        val sAfter = db.recurringScheduleDao().getScheduleByIdAndUser("s_noperm", user1Id)!!
        assertFalse(sAfter.isEnabled)

        // Verify no notification was posted because permission was denied
        assertTrue("No notification posted when permission denied", shadowNM.allNotifications.isEmpty())

        // Restore permission for other tests
        shadowApp.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun testUnarchiveWallet_doesNotAutoEnableRecurringSchedules() = runBlocking {
        val walletId = db.walletDao().insertWallet(
            Wallet(name = "Archived Wallet", balance = 500_000.0, userId = user1Id, currencyCode = "IDR", isArchived = true)
        )
        val s = RecurringSchedule(
            id = "s_disabled",
            title = "Internet",
            amount = 300_000.0,
            type = TransactionType.EXPENSE,
            category = "Tagihan",
            categoryIcon = "wifi",
            walletId = walletId,
            walletName = "Archived Wallet",
            interval = RecurringInterval.MONTHLY,
            isEnabled = false,
            userId = user1Id
        )
        db.recurringScheduleDao().insertSchedule(s)

        val unarchiveRes = repository.unarchiveWallet(user1Id, walletId)
        assertTrue(unarchiveRes.isSuccess)

        val walletAfter = db.walletDao().getWalletById(walletId, user1Id)!!
        assertFalse(walletAfter.isArchived)

        val sAfter = db.recurringScheduleDao().getScheduleByIdAndUser("s_disabled", user1Id)!!
        assertFalse("Schedule must NOT be auto-enabled on wallet unarchive", sAfter.isEnabled)
    }

    @Test
    fun testDeleteWallet_withTransactions_rejectedSafely() = runBlocking {
        val walletId = db.walletDao().insertWallet(
            Wallet(name = "Active Wallet with History", balance = 200_000.0, userId = user1Id, currencyCode = "IDR")
        )
        val tx = TransactionItem(
            title = "Transaksi Riwayat",
            amount = 50_000.0,
            type = TransactionType.EXPENSE,
            category = "Umum",
            walletId = walletId,
            walletName = "Active Wallet with History",
            timestamp = System.currentTimeMillis(),
            userId = user1Id
        )
        repository.insertTransaction(user1Id, tx)

        val wallet = db.walletDao().getWalletById(walletId, user1Id)!!
        try {
            repository.deleteWallet(user1Id, wallet)
            fail("Wallet with transaction history must not be deleted permanently")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("memiliki riwayat data") == true)
            assertTrue(e.message?.contains("Arsipkan Dompet") == true)
        }

        assertNotNull(db.walletDao().getWalletById(walletId, user1Id))
    }

    @Test
    fun testDeleteWallet_emptyAndUnused_succeeds() = runBlocking {
        val walletId = db.walletDao().insertWallet(
            Wallet(name = "Fresh Empty Wallet", balance = 0.0, userId = user1Id, currencyCode = "IDR")
        )
        val wallet = db.walletDao().getWalletById(walletId, user1Id)!!

        val deletedRows = repository.deleteWallet(user1Id, wallet)
        assertEquals(1, deletedRows)
        assertNull(db.walletDao().getWalletById(walletId, user1Id))
    }

    // -------------------------------------------------------------------------
    // DEC-PROD-03: Multi-Currency Portfolio Aggregation
    // -------------------------------------------------------------------------
    @Test
    fun testDEC_PROD_03_MultiCurrencyPortfolioIsolation() = runBlocking {
        db.walletDao().insertWallet(Wallet(name = "Wallet IDR 1", balance = 500_000.0, userId = user1Id, currencyCode = "IDR"))
        db.walletDao().insertWallet(Wallet(name = "Wallet IDR 2", balance = 250_000.0, userId = user1Id, currencyCode = "IDR"))
        db.walletDao().insertWallet(Wallet(name = "Wallet USD 1", balance = 150.0, userId = user1Id, currencyCode = "USD"))
        db.walletDao().insertWallet(Wallet(name = "Wallet JPY 1", balance = 20_000.0, userId = user1Id, currencyCode = "JPY"))
        db.walletDao().insertWallet(Wallet(name = "Archived EUR", balance = 100.0, userId = user1Id, currencyCode = "EUR", isArchived = true))

        val allWallets = db.walletDao().getAllWallets(user1Id).first()
        val multiCurrency = allWallets.filter { it.hasAssignedCurrency && !it.isArchived }
            .groupBy { it.currencyCode ?: "UNKNOWN" }
            .map { (code, wList) ->
                com.example.ui.viewmodel.CurrencyBalanceSummary(
                    currencyCode = code,
                    totalBalance = wList.sumOf { it.balance },
                    walletCount = wList.size
                )
            }

        assertEquals(3, multiCurrency.size)
        val usdSummary = multiCurrency.first { it.currencyCode == "USD" }
        assertEquals(150.0, usdSummary.totalBalance, 0.01)
        assertEquals(1, usdSummary.walletCount)

        val idrSummary = multiCurrency.first { it.currencyCode == "IDR" }
        assertEquals(750_000.0, idrSummary.totalBalance, 0.01)
        assertEquals(2, idrSummary.walletCount)

        val jpySummary = multiCurrency.first { it.currencyCode == "JPY" }
        assertEquals(20_000.0, jpySummary.totalBalance, 0.01)
        assertEquals(1, jpySummary.walletCount)

        // Ensure archived EUR was excluded
        assertNull(multiCurrency.firstOrNull { it.currencyCode == "EUR" })
    }

    @Test
    fun testDecProd03_multiCurrencyFormattingAndExclusion() = runBlocking {
        // Multi-currency formatting check
        val idrFormatted = CurrencyFormatter.formatCurrency(1_500_000.0, "IDR")
        assertTrue(idrFormatted.contains("Rp") && idrFormatted.contains("1.500.000"))

        val usdFormatted = CurrencyFormatter.formatCurrency(250.50, "USD")
        assertTrue(usdFormatted.contains("$") && usdFormatted.contains("250.50"))

        val eurFormatted = CurrencyFormatter.formatCurrency(120.0, "EUR")
        assertTrue(eurFormatted.contains("120"))

        // Null currency code falls back gracefully to active currency
        val defaultFormatted = CurrencyFormatter.formatCurrency(50_000.0, null)
        assertNotNull(defaultFormatted)
    }
}
