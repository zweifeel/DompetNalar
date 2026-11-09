package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.RecurringSchedule
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.util.RecurringExecutionResult
import com.example.util.RecurringInterval
import com.example.util.RecurringScheduleEvaluator
import kotlinx.coroutines.flow.first
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecurringTransactionWorkerTest {

    private lateinit var context: Context
    private lateinit var db: FinSmartDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            db.userDao().insertUser(
                User(id = 1L, username = "testuser", email = "test@user.com", passwordHash = "h", role = UserRole.FREE_MEMBER, createdAt = 1000L)
            )
            db.walletDao().insertWallet(
                Wallet(id = 10L, name = "Active Bank", type = WalletType.BANK, balance = 500000.0, currencyCode = "IDR", userId = 1L)
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `T-04 Daily cycle formatting yyyy-MM-dd`() {
        val schedule = RecurringSchedule(
            id = "sch_daily",
            title = "Daily Coffee",
            amount = 25000.0,
            type = TransactionType.EXPENSE,
            category = "Food",
            categoryIcon = "coffee",
            walletId = 10L,
            walletName = "Active Bank",
            interval = RecurringInterval.DAILY,
            executionHour = 8,
            executionMinute = 0,
            startDate = 0L,
            timezoneId = "Asia/Jakarta",
            userId = 1L
        )

        // Instant corresponding to 2026-11-05 09:00:00 in Asia/Jakarta (+07:00) -> 2026-11-05 02:00:00 UTC
        val fixedClock = Clock.fixed(Instant.parse("2026-11-05T02:00:00Z"), ZoneId.of("Asia/Jakarta"))
        val cycleKey = RecurringScheduleEvaluator.computeCycleIdentifier(schedule, fixedClock)
        assertEquals("2026-11-05", cycleKey)
        assertTrue(RecurringScheduleEvaluator.isScheduleDue(schedule, fixedClock))
    }

    @Test
    fun `T-05 Weekly cycle formatting YYYY-Www using WeekFields ISO`() {
        val schedule = RecurringSchedule(
            id = "sch_weekly",
            title = "Weekly Groceries",
            amount = 300000.0,
            type = TransactionType.EXPENSE,
            category = "Groceries",
            categoryIcon = "shopping_cart",
            walletId = 10L,
            walletName = "Active Bank",
            interval = RecurringInterval.WEEKLY,
            dayOfWeek = 4, // Thursday
            executionHour = 10,
            executionMinute = 0,
            startDate = 0L,
            timezoneId = "UTC",
            userId = 1L
        )

        // 2026-11-05 is a Thursday, belonging to ISO week 45 of 2026
        val fixedClock = Clock.fixed(Instant.parse("2026-11-05T10:30:00Z"), ZoneId.of("UTC"))
        val cycleKey = RecurringScheduleEvaluator.computeCycleIdentifier(schedule, fixedClock)
        assertEquals("2026-W45", cycleKey)
        assertTrue(RecurringScheduleEvaluator.isScheduleDue(schedule, fixedClock))
    }

    @Test
    fun `T-06 Monthly cycle formatting yyyy-MM with month-end day clamping`() {
        val schedule = RecurringSchedule(
            id = "sch_monthly_clamp",
            title = "End of Month Savings",
            amount = 100000.0,
            type = TransactionType.EXPENSE,
            category = "Savings",
            categoryIcon = "savings",
            walletId = 10L,
            walletName = "Active Bank",
            interval = RecurringInterval.MONTHLY,
            dayOfMonth = 31, // Scheduled on day 31
            executionHour = 12,
            executionMinute = 0,
            startDate = 0L,
            timezoneId = "UTC",
            userId = 1L
        )

        // Test on 2026-02-28 (February has 28 days in non-leap year 2026). Day 31 must clamp to 28.
        val feb28Clock = Clock.fixed(Instant.parse("2026-02-28T13:00:00Z"), ZoneId.of("UTC"))
        val cycleKey = RecurringScheduleEvaluator.computeCycleIdentifier(schedule, feb28Clock)
        assertEquals("2026-02", cycleKey)
        assertTrue("Schedule scheduled on day 31 must be due on clamped day Feb 28", RecurringScheduleEvaluator.isScheduleDue(schedule, feb28Clock))

        // On Feb 27, it should NOT be due
        val feb27Clock = Clock.fixed(Instant.parse("2026-02-27T13:00:00Z"), ZoneId.of("UTC"))
        assertFalse("Schedule scheduled on day 31 must NOT be due on Feb 27", RecurringScheduleEvaluator.isScheduleDue(schedule, feb27Clock))
    }

    @Test
    fun `T-03 Complete 13-step atomic transaction in worker`() = runBlocking {
        val schedule = RecurringSchedule(
            id = "sch_salary",
            title = "Monthly Salary",
            amount = 5000000.0,
            type = TransactionType.INCOME,
            category = "Salary",
            categoryIcon = "payments",
            walletId = 10L,
            walletName = "Active Bank",
            interval = RecurringInterval.MONTHLY,
            dayOfMonth = 1,
            executionHour = 9,
            executionMinute = 0,
            startDate = 0L,
            timezoneId = "UTC",
            version = 1,
            userId = 1L
        )
        db.recurringScheduleDao().insertSchedule(schedule)

        val executionClock = Clock.fixed(Instant.parse("2026-11-01T09:30:00Z"), ZoneId.of("UTC"))

        // Execute schedule transaction
        val result = RecurringScheduleEvaluator.executeScheduleTransaction(
            database = db,
            scheduleId = "sch_salary",
            userId = 1L,
            expectedVersion = 1,
            clock = executionClock
        )

        assertTrue(result is RecurringExecutionResult.Executed)
        val executed = result as RecurringExecutionResult.Executed
        assertEquals("2026-11", executed.cycleKey)
        assertEquals(5000000.0, executed.amount, 0.001)

        // Verify balance updated via Money: initial 500,000 + 5,000,000 = 5,500,000
        val wallet = db.walletDao().getWalletById(10L)
        assertEquals(5500000.0, wallet?.balance ?: 0.0, 0.001)

        // Verify transaction inserted with deterministic idempotencyKey
        val txs = db.transactionDao().getAllTransactions(1L).first()
        assertEquals(1, txs.size)
        assertEquals("rec_sch_salary_2026-11", txs[0].idempotencyKey)
        assertEquals(5000000.0, txs[0].amount, 0.001)

        // Verify schedule.lastExecutedCycle updated
        val updatedSchedule = db.recurringScheduleDao().getScheduleByIdAndUser("sch_salary", 1L)
        assertEquals("2026-11", updatedSchedule?.lastExecutedCycle)

        // Subsequent execution in same cycle returns AlreadyExecuted without balance mutation
        val secondResult = RecurringScheduleEvaluator.executeScheduleTransaction(
            database = db,
            scheduleId = "sch_salary",
            userId = 1L,
            expectedVersion = 1,
            clock = executionClock
        )
        assertTrue(secondResult is RecurringExecutionResult.AlreadyExecuted)
        val walletUnchanged = db.walletDao().getWalletById(10L)
        assertEquals(5500000.0, walletUnchanged?.balance ?: 0.0, 0.001)
    }

    @Test
    fun `T-07 Concurrent worker race condition unique key collision handled cleanly`() = runBlocking {
        val schedule = RecurringSchedule(
            id = "sch_race",
            title = "Race Subscription",
            amount = 50000.0,
            type = TransactionType.EXPENSE,
            category = "Subscription",
            categoryIcon = "movie",
            walletId = 10L,
            walletName = "Active Bank",
            interval = RecurringInterval.MONTHLY,
            dayOfMonth = 1,
            executionHour = 9,
            executionMinute = 0,
            startDate = 0L,
            timezoneId = "UTC",
            version = 1,
            userId = 1L
        )
        db.recurringScheduleDao().insertSchedule(schedule)

        val executionClock = Clock.fixed(Instant.parse("2026-11-01T09:30:00Z"), ZoneId.of("UTC"))

        // Thread 1 succeeds and commits
        val result1 = RecurringScheduleEvaluator.executeScheduleTransaction(
            database = db,
            scheduleId = "sch_race",
            userId = 1L,
            expectedVersion = 1,
            clock = executionClock
        )
        assertTrue(result1 is RecurringExecutionResult.Executed)

        // Thread 2 attempts execution: detects existing idempotencyKey rec_sch_race_2026-11 and returns clean AlreadyExecuted
        val result2 = RecurringScheduleEvaluator.executeScheduleTransaction(
            database = db,
            scheduleId = "sch_race",
            userId = 1L,
            expectedVersion = 1,
            clock = executionClock
        )
        assertTrue(result2 is RecurringExecutionResult.AlreadyExecuted)

        // Balance must only be deducted ONCE (500,000 - 50,000 = 450,000)
        val wallet = db.walletDao().getWalletById(10L)
        assertEquals(450000.0, wallet?.balance ?: 0.0, 0.001)

        // Exactly 1 transaction record
        val txs = db.transactionDao().getAllTransactions(1L).first()
        assertEquals(1, txs.size)
    }

    @Test
    fun `T-08 Stale worker rejection schedule version mismatch aborts safely`() = runBlocking {
        val schedule = RecurringSchedule(
            id = "sch_stale",
            title = "Stale Worker Test",
            amount = 100000.0,
            type = TransactionType.EXPENSE,
            category = "Utilities",
            categoryIcon = "receipt",
            walletId = 10L,
            walletName = "Active Bank",
            interval = RecurringInterval.DAILY,
            executionHour = 8,
            executionMinute = 0,
            startDate = 0L,
            timezoneId = "UTC",
            version = 2, // Schedule has been bumped to version 2 by user edit
            userId = 1L
        )
        db.recurringScheduleDao().insertSchedule(schedule)

        val executionClock = Clock.fixed(Instant.parse("2026-11-01T08:30:00Z"), ZoneId.of("UTC"))

        // Worker was enqueued with old expectedVersion = 1
        val result = RecurringScheduleEvaluator.executeScheduleTransaction(
            database = db,
            scheduleId = "sch_stale",
            userId = 1L,
            expectedVersion = 1,
            clock = executionClock
        )

        assertEquals(RecurringExecutionResult.StaleVersion, result)

        // Wallet balance untouched
        val wallet = db.walletDao().getWalletById(10L)
        assertEquals(500000.0, wallet?.balance ?: 0.0, 0.001)

        // No transactions inserted
        val txs = db.transactionDao().getAllTransactions(1L).first()
        assertEquals(0, txs.size)
    }

    @Test
    fun `T-09 Unassigned currency or archived wallet disables schedule safely`() = runBlocking {
        db.walletDao().insertWallet(
            Wallet(id = 20L, name = "Unassigned Wallet", type = WalletType.BANK, balance = 100000.0, currencyCode = null, userId = 1L)
        )

        val schedule = RecurringSchedule(
            id = "sch_no_curr",
            title = "Unassigned Currency Test",
            amount = 20000.0,
            type = TransactionType.EXPENSE,
            category = "General",
            categoryIcon = "general",
            walletId = 20L,
            walletName = "Unassigned Wallet",
            interval = RecurringInterval.DAILY,
            executionHour = 7,
            executionMinute = 0,
            startDate = 0L,
            timezoneId = "UTC",
            isEnabled = true,
            userId = 1L
        )
        db.recurringScheduleDao().insertSchedule(schedule)

        val executionClock = Clock.fixed(Instant.parse("2026-11-01T07:30:00Z"), ZoneId.of("UTC"))

        val result = RecurringScheduleEvaluator.executeScheduleTransaction(
            database = db,
            scheduleId = "sch_no_curr",
            userId = 1L,
            expectedVersion = 1,
            clock = executionClock
        )

        assertTrue(result is RecurringExecutionResult.ScheduleDisabled)

        // Verify schedule was disabled in DB
        val updatedSchedule = db.recurringScheduleDao().getScheduleByIdAndUser("sch_no_curr", 1L)
        assertFalse(updatedSchedule!!.isEnabled)

        // Verify balance untouched
        val wallet = db.walletDao().getWalletById(20L)
        assertEquals(100000.0, wallet?.balance ?: 0.0, 0.001)
    }
}
