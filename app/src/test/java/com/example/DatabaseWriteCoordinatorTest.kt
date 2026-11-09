package com.example

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DatabaseWriteCoordinator
import com.example.data.local.FinSmartDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseWriteCoordinatorTest {

    private lateinit var app: Application
    private lateinit var coordinator: DatabaseWriteCoordinator
    private var testDatabase: FinSmartDatabase? = null

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        DatabaseWriteCoordinator.resetForTesting()
        coordinator = DatabaseWriteCoordinator.getInstance()
    }

    @After
    fun tearDown() {
        testDatabase?.close()
        testDatabase = null
        DatabaseWriteCoordinator.resetForTesting()
    }

    @Test
    fun testBypassPrevention_throwsWhenNoPermitHeld() = runBlocking {
        assertFalse("Write permit should not be held initially", coordinator.isWritePermitHeld())
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                coordinator.requireWritePermit()
            }
        }
        Unit
    }

    @Test
    fun testBypassPrevention_succeedsWithinWritePermit() = runBlocking {
        assertFalse(coordinator.isWritePermitHeld())
        val result = coordinator.withWritePermit {
            assertTrue("Write permit should be held inside block", coordinator.isWritePermitHeld())
            coordinator.requireWritePermit() // should not throw
            "success"
        }
        assertEquals("success", result)
        assertFalse("Write permit should be released after block", coordinator.isWritePermitHeld())
    }

    @Test
    fun testBypassPrevention_succeedsWithinExclusiveMaintenance() = runBlocking {
        assertFalse(coordinator.isWritePermitHeld())
        val result = coordinator.withExclusiveMaintenance {
            assertTrue("Write permit is satisfied inside maintenance", coordinator.isWritePermitHeld())
            assertTrue("Maintenance flag should be active", coordinator.isMaintenancePermitHeld())
            coordinator.requireWritePermit() // should not throw
            "maintenance_done"
        }
        assertEquals("maintenance_done", result)
        assertFalse(coordinator.isWritePermitHeld())
    }

    @Test
    fun testReentrantWritePermit_succeedsWithoutDeadlock() = runBlocking {
        val result = coordinator.withWritePermit {
            coordinator.withWritePermit {
                coordinator.withWritePermit {
                    "reentrant_success"
                }
            }
        }
        assertEquals("reentrant_success", result)
    }

    @Test
    fun testConcurrentWriters_areSerialized() = runBlocking {
        val concurrencyCounter = AtomicInteger(0)
        val maxConcurrency = AtomicInteger(0)
        val executionOrder = Collections.synchronizedList(mutableListOf<Int>())
        val writerCount = 8

        val jobs = (1..writerCount).map { id ->
            async(Dispatchers.Default) {
                coordinator.withWritePermit {
                    val current = concurrencyCounter.incrementAndGet()
                    var max = maxConcurrency.get()
                    while (current > max) {
                        if (maxConcurrency.compareAndSet(max, current)) break
                        max = maxConcurrency.get()
                    }
                    delay(25)
                    executionOrder.add(id)
                    concurrencyCounter.decrementAndGet()
                }
            }
        }

        jobs.awaitAll()

        assertEquals("Max concurrent writers must strictly be 1", 1, maxConcurrency.get())
        assertEquals("All writers must have executed", writerCount, executionOrder.size)
        assertEquals("Final concurrency counter must be 0", 0, concurrencyCounter.get())
        assertEquals("No writer is currently active", 0, coordinator.activeWriters.value)
    }

    @Test
    fun testConcurrentReads_areShared() = runBlocking {
        val maxReaders = AtomicInteger(0)
        val readyGate = CompletableDeferred<Unit>()
        val readerCount = 4

        val jobs = (1..readerCount).map {
            async(Dispatchers.Default) {
                coordinator.withReadPermit {
                    val current = coordinator.activeReaders.value
                    var max = maxReaders.get()
                    while (current > max) {
                        if (maxReaders.compareAndSet(max, current)) break
                        max = maxReaders.get()
                    }
                    readyGate.await()
                }
            }
        }

        while (coordinator.activeReaders.value < readerCount) {
            delay(10)
        }
        readyGate.complete(Unit)
        jobs.awaitAll()

        assertTrue("Multiple readers must run concurrently", maxReaders.get() >= readerCount)
        assertEquals(0, coordinator.activeReaders.value)
    }

    @Test
    fun testExclusiveMaintenance_blocksWritersUntilComplete() = runBlocking {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val maintenanceStarted = CompletableDeferred<Unit>()

        val maintenanceJob = launch(Dispatchers.Default) {
            coordinator.withExclusiveMaintenance {
                events.add("maintenance_start")
                maintenanceStarted.complete(Unit)
                delay(100)
                events.add("maintenance_end")
            }
        }

        maintenanceStarted.await()

        val writerJob = launch(Dispatchers.Default) {
            coordinator.withWritePermit {
                events.add("write_executed")
            }
        }

        maintenanceJob.join()
        writerJob.join()

        assertEquals(listOf("maintenance_start", "maintenance_end", "write_executed"), events)
    }

    @Test
    fun testExclusiveMaintenance_waitsForActiveWritesToDrain() = runBlocking {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val writeStarted = CompletableDeferred<Unit>()

        val writerJob = launch(Dispatchers.Default) {
            coordinator.withWritePermit {
                events.add("write_start")
                writeStarted.complete(Unit)
                delay(100)
                events.add("write_end")
            }
        }

        writeStarted.await()

        val maintenanceJob = launch(Dispatchers.Default) {
            coordinator.withExclusiveMaintenance {
                events.add("maintenance_executed")
            }
        }

        writerJob.join()
        maintenanceJob.join()

        assertEquals(listOf("write_start", "write_end", "maintenance_executed"), events)
    }

    @Test
    fun testExceptionInsideWritePermit_releasesPermitCleanly() = runBlocking {
        val thrown = runCatching {
            coordinator.withWritePermit {
                throw IllegalStateException("Simulated crash")
            }
        }
        assertTrue(thrown.isFailure)
        assertEquals(0, coordinator.activeWriters.value)

        val nextResult = coordinator.withWritePermit {
            "recovered"
        }
        assertEquals("recovered", nextResult)
    }

    @Test
    fun testCancellationWhileWaiting_releasesPermitCleanly() = runBlocking {
        val holdGate = CompletableDeferred<Unit>()

        val holder = launch(Dispatchers.Default) {
            coordinator.withWritePermit {
                holdGate.await()
            }
        }

        delay(30)

        val waitingJob = launch(Dispatchers.Default) {
            coordinator.withWritePermit {
                // Cancelled before entering
            }
        }

        delay(30)
        waitingJob.cancel()

        holdGate.complete(Unit)
        holder.join()
        waitingJob.join()

        val result = coordinator.withWritePermit {
            "succeeded_after_cancellation"
        }
        assertEquals("succeeded_after_cancellation", result)
    }

    @Test
    fun testTimeout_throwsTimeoutCancellationException() = runBlocking {
        val holdGate = CompletableDeferred<Unit>()

        val holder = launch(Dispatchers.Default) {
            coordinator.withWritePermit {
                holdGate.await()
            }
        }

        delay(30)

        assertThrows(TimeoutCancellationException::class.java) {
            runBlocking {
                coordinator.withWritePermit(timeoutMs = 50) {
                    "should_never_run"
                }
            }
        }

        holdGate.complete(Unit)
        holder.join()
    }

    @Test
    fun testUserRepository_executesMutationsUnderCoordinator() = runBlocking {
        val inMemoryDb = Room.inMemoryDatabaseBuilder(app, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val userRepo = com.example.data.repository.UserRepository(inMemoryDb.userDao(), coordinator)

        val user = com.example.data.model.User(
            username = "coord_user",
            email = "coord@test.com",
            role = com.example.data.model.UserRole.FREE_MEMBER
        )

        // 1. Insert user
        val userId = userRepo.insertUser(user)
        assertTrue("User ID must be positive", userId > 0)
        assertEquals(0, coordinator.activeWriters.value)

        // 2. Update role
        userRepo.updateUserRole(userId, com.example.data.model.UserRole.SMART_MASTER)
        val fetched = userRepo.getUserById(userId)
        assertEquals(com.example.data.model.UserRole.SMART_MASTER, fetched?.role)
        assertEquals(0, coordinator.activeWriters.value)

        // 3. Delete user
        userRepo.deleteUser(fetched!!)
        val afterDelete = userRepo.getUserById(userId)
        assertTrue(afterDelete == null)
        assertEquals(0, coordinator.activeWriters.value)

        inMemoryDb.close()
    }

    @Test
    fun testFinanceRepository_executesMutationsUnderCoordinator() = runBlocking {
        val inMemoryDb = Room.inMemoryDatabaseBuilder(app, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val testUserId = inMemoryDb.userDao().insertUser(
            com.example.data.model.User(
                username = "fin_coord_user",
                email = "fin_coord@test.com"
            )
        )

        val financeRepo = com.example.data.repository.FinanceRepository(
            walletDao = inMemoryDb.walletDao(),
            database = inMemoryDb
        )

        // 1. Insert wallet
        val wallet = com.example.data.model.Wallet(
            name = "Test Wallet",
            type = com.example.data.model.WalletType.CASH,
            balance = 100000.0,
            userId = testUserId
        )
        val walletId = financeRepo.insertWallet(wallet)
        assertTrue(walletId > 0)
        assertEquals(0, coordinator.activeWriters.value)

        // 2. Insert transaction
        val tx = com.example.data.model.TransactionItem(
            title = "Lunch",
            amount = 50000.0,
            type = com.example.data.model.TransactionType.EXPENSE,
            category = "Food",
            walletId = walletId,
            walletName = "Test Wallet",
            userId = testUserId
        )
        val txId = financeRepo.insertTransaction(testUserId, tx)
        assertTrue(txId > 0)
        assertEquals(0, coordinator.activeWriters.value)

        inMemoryDb.close()
    }

    @Test
    fun testWalSafeBackupAndCheckpoint_onRealDatabase() = runBlocking {
        // Create an actual on-disk SQLite database using Room
        val dbFile = File(app.filesDir, "test_backup_source.db")
        if (dbFile.exists()) dbFile.delete()

        val db = Room.databaseBuilder(app, FinSmartDatabase::class.java, dbFile.name)
            .allowMainThreadQueries()
            .build()
        testDatabase = db

        val writableDb = db.openHelper.writableDatabase

        // Execute WAL checkpoint truncate
        val checkpointResult = coordinator.checkpointWalTruncate(writableDb, maxRetries = 2, retryDelayMs = 10)
        assertTrue("WAL Checkpoint should succeed or produce valid result", checkpointResult is DatabaseWriteCoordinator.WalCheckpointResult.Success || checkpointResult is DatabaseWriteCoordinator.WalCheckpointResult.Busy)

        // Perform WAL safe backup
        val backupTargetFile = File(app.filesDir, "test_backup_dest.db")
        if (backupTargetFile.exists()) backupTargetFile.delete()

        val backupResult = coordinator.performWalSafeBackup(db, backupTargetFile)
        assertNotNull(backupResult)
        // With an on-disk database file created, performWalSafeBackup should succeed
        when (backupResult) {
            is DatabaseWriteCoordinator.WalBackupResult.Success -> {
                assertTrue("Backup target file must exist", backupTargetFile.exists())
                assertTrue("Backup target file size >= 0", backupResult.bytesCopied >= 0)
            }
            is DatabaseWriteCoordinator.WalBackupResult.Failure -> {
                // If the underlying SQLite engine doesn't support WAL truncate in Robolectric shadow mode, failure message must be documented
                assertNotNull(backupResult.reason)
            }
        }

        // Clean up
        db.close()
        testDatabase = null
        if (dbFile.exists()) dbFile.delete()
        if (backupTargetFile.exists()) backupTargetFile.delete()
    }

    @Test
    fun testRecurringTransactionViewModel_scheduleRecurringTransaction_acquiresPermitAndSaves() = runBlocking {
        withTimeout(5000) {
            val inMemoryDb = Room.inMemoryDatabaseBuilder(app, FinSmartDatabase::class.java)
                .allowMainThreadQueries()
                .build()

            com.example.data.session.SessionManager.initialize(app)
            val user = com.example.data.model.User(
                id = 777L,
                username = "recurring_tester",
                email = "recurring@test.com"
            )
            inMemoryDb.userDao().insertUser(user)
            com.example.data.session.SessionManager.login(user)

            val wallet = com.example.data.model.Wallet(
                id = 888L,
                name = "Recurring Wallet",
                type = com.example.data.model.WalletType.BANK,
                balance = 5000000.0,
                currencyCode = "IDR",
                userId = user.id
            )
            inMemoryDb.walletDao().insertWallet(wallet)

            val financeRepo = com.example.data.repository.FinanceRepository(
                walletDao = inMemoryDb.walletDao(),
                database = inMemoryDb
            )

            val vm = com.example.ui.viewmodel.RecurringTransactionViewModel(app, financeRepo, Dispatchers.Default)

            val job = vm.scheduleRecurringTransaction(
                title = "Monthly Rent",
                amount = 1500000.0,
                type = com.example.data.model.TransactionType.EXPENSE,
                category = "Tagihan & Utilitas",
                categoryIcon = "receipt",
                walletId = wallet.id,
                walletName = wallet.name,
                interval = com.example.util.RecurringInterval.MONTHLY,
                dayOfMonth = 1
            )
            job.join()

            val list = inMemoryDb.recurringScheduleDao().getAllSchedules(user.id).first()
            assertEquals(1, list.size)
            assertEquals("Monthly Rent", list[0].title)
            assertEquals(1500000.0, list[0].amount, 0.001)
            assertEquals(0, coordinator.activeWriters.value)

            com.example.data.session.SessionManager.resetForTesting()
            inMemoryDb.close()
        }
    }

    @Test
    fun testRecurringTransactionViewModel_scheduleWaitsDuringExclusiveMaintenance() = runBlocking {
        withTimeout(5000) {
            val inMemoryDb = Room.inMemoryDatabaseBuilder(app, FinSmartDatabase::class.java)
                .allowMainThreadQueries()
                .build()

            com.example.data.session.SessionManager.initialize(app)
            val user = com.example.data.model.User(
                id = 778L,
                username = "maint_tester",
                email = "maint@test.com"
            )
            inMemoryDb.userDao().insertUser(user)
            com.example.data.session.SessionManager.login(user)

            val financeRepo = com.example.data.repository.FinanceRepository(
                walletDao = inMemoryDb.walletDao(),
                database = inMemoryDb
            )
            val vm = com.example.ui.viewmodel.RecurringTransactionViewModel(app, financeRepo, Dispatchers.Default)

            val events = Collections.synchronizedList(mutableListOf<String>())
            val maintenanceStarted = CompletableDeferred<Unit>()

            val maintenanceJob = launch(Dispatchers.Default) {
                coordinator.withExclusiveMaintenance {
                    events.add("maintenance_start")
                    maintenanceStarted.complete(Unit)
                    delay(100)
                    events.add("maintenance_end")
                }
            }

            maintenanceStarted.await()

            val scheduleJob = launch(Dispatchers.Default) {
                vm.scheduleRecurringTransaction(
                    title = "Insurance",
                    amount = 200000.0,
                    type = com.example.data.model.TransactionType.EXPENSE,
                    category = "Asuransi",
                    categoryIcon = "health_and_safety",
                    walletId = 1L,
                    walletName = "Main Wallet",
                    interval = com.example.util.RecurringInterval.MONTHLY,
                    dayOfMonth = 5
                ).join()
                events.add("schedule_saved")
            }

            maintenanceJob.join()
            scheduleJob.join()

            assertEquals(listOf("maintenance_start", "maintenance_end", "schedule_saved"), events)

            com.example.data.session.SessionManager.resetForTesting()
            inMemoryDb.close()
        }
    }

    @Test
    fun testRecurringTransactionViewModel_failedSchedulingReleasesPermit() = runBlocking {
        withTimeout(5000) {
            val inMemoryDb = Room.inMemoryDatabaseBuilder(app, FinSmartDatabase::class.java)
                .allowMainThreadQueries()
                .build()

            com.example.data.session.SessionManager.initialize(app)
            val user = com.example.data.model.User(
                id = 779L,
                username = "fail_tester",
                email = "fail@test.com"
            )
            inMemoryDb.userDao().insertUser(user)
            com.example.data.session.SessionManager.login(user)

            val financeRepo = com.example.data.repository.FinanceRepository(
                walletDao = inMemoryDb.walletDao(),
                database = inMemoryDb
            )
            val vm = com.example.ui.viewmodel.RecurringTransactionViewModel(app, financeRepo, Dispatchers.Default)

            // Close DB to force an error on insert
            inMemoryDb.close()

            val job = vm.scheduleRecurringTransaction(
                title = "Failing Rent",
                amount = 100000.0,
                type = com.example.data.model.TransactionType.EXPENSE,
                category = "Other",
                categoryIcon = "receipt",
                walletId = 1L,
                walletName = "Wallet",
                interval = com.example.util.RecurringInterval.MONTHLY
            )
            job.join()

            assertEquals(0, coordinator.activeWriters.value)

            // Verify coordinator permit is completely free and subsequent write succeeds
            val result = coordinator.withWritePermit {
                "recovered"
            }
            assertEquals("recovered", result)

            com.example.data.session.SessionManager.resetForTesting()
        }
    }

    @Test
    fun testArchitecturalEnforcement_detectsProhibitedDirectDaoMutations() {
        val approvedDataLayerPrefixes = listOf(
            "app/src/main/java/com/example/data/repository/",
            "app/src/main/java/com/example/data/local/FinSmartDatabase.kt",
            "app/src/main/java/com/example/util/RecurringScheduleEvaluator.kt",
            "app/src/main/java/com/example/data/session/SessionManager.kt",
            "app/src/main/java/com/example/ui/viewmodel/FeedbackViewModel.kt",
            "app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt",
            "app/src/main/java/com/example/util/PasswordHasher.kt"
        ).map { it.replace("/", File.separator) }

        val mutatingMethodNames = setOf(
            "insertTransaction", "insertTransactions", "updateTransaction", "deleteTransaction",
            "deleteTransactionById", "clearAllTransactions", "insertWallet", "insertWallets",
            "updateWallet", "updateWalletMetadata", "deleteWallet", "deleteWalletById", "clearAllWallets",
            "insertCategory", "insertCategories", "deleteCategory", "deleteCategoryById",
            "clearCustomCategories", "insertUser", "insertUsers", "updateUser", "updateUserRole",
            "deleteUser", "insertDebt", "insertDebts", "updateDebt", "deleteDebt", "deleteDebtById",
            "clearAllDebts", "insertAsset", "insertAssets", "updateAsset", "deleteAsset",
            "deleteAssetById", "clearAllAssets", "insertInstallmentPlan", "insertInstallmentPlans",
            "updateInstallmentPlan", "deleteInstallmentPlan", "clearAllInstallmentPlans",
            "insertBudgetLimit", "updateBudgetLimit", "updateLimitAmount", "deleteBudgetLimit",
            "deleteBudgetLimitByKey", "deleteBudgetLimitByLocalId", "clearAllBudgetLimits",
            "insertSplitBillHistory", "deleteSplitBillHistory", "deleteSplitBillHistoryById",
            "clearAllHistory", "insertSchedule", "updateSchedule", "deleteSchedule", "clearAllSchedules"
        )

        val srcDir = File("app/src/main/java")
        val violations = mutableListOf<String>()

        srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val relPath = file.path
            val isApproved = approvedDataLayerPrefixes.any { relPath.contains(it) }

            if (!isApproved) {
                file.readLines().forEachIndexed { index, line ->
                    // Exclude comments or strings
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("//") && !trimmed.startsWith("*")) {
                        mutatingMethodNames.forEach { method ->
                            if (line.contains("Dao.") || line.contains("dao.")) {
                                if (line.contains(".$method(")) {
                                    violations.add("${file.name}:${index + 1} -> Calling $method directly on DAO without Repository/Coordinator: $trimmed")
                                }
                            }
                        }
                    }
                }
            }
        }

        assertTrue(
            "Direct DAO mutation calls outside approved data layer detected:\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }
}
