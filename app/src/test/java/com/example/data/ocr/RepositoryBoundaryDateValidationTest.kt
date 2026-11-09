package com.example.data.ocr

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinSmartDatabase
import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPeriod
import com.example.data.model.InstallmentPlanItem
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.data.repository.FinanceRepository
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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryBoundaryDateValidationTest {

    private lateinit var context: Context
    private lateinit var database: FinSmartDatabase
    private lateinit var repository: FinanceRepository

    private val user1Id = 501L
    private val user2Id = 502L

    @Before
    fun setUp() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            database = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            repository = FinanceRepository(
                walletDao = database.walletDao(),
                transactionDao = database.transactionDao(),
                categoryDao = database.categoryDao(),
                database = database
            )

            // Seed users
            database.userDao().insertUser(User(id = user1Id, username = "User 1", email = "u1@test.com", passwordHash = "h", role = UserRole.FREE_MEMBER, createdAt = 1000))
            database.userDao().insertUser(User(id = user2Id, username = "User 2", email = "u2@test.com", passwordHash = "h", role = UserRole.FREE_MEMBER, createdAt = 1000))

            // Seed wallet for User 1
            database.walletDao().insertWallet(
                Wallet(id = 10L, name = "Dompet Utama", type = WalletType.BANK, balance = 1000000.0, balanceMinorUnits = 100000000L, userId = user1Id, currencyCode = "IDR")
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun test1_ValidNormalIsoDateIsAccepted() = runBlocking {
        val plan = InstallmentPlanItem(
            id = "plan-valid-1",
            name = "Shopee PayLater",
            provider = "Shopee",
            installmentAmount = 250000.0,
            currencyCode = "IDR",
            totalTenor = 1,
            startDate = "2026-11-01",
            nextDueDate = "2026-11-25",
            userId = user1Id
        )
        val period = InstallmentPeriod(
            id = "period-valid-1",
            planId = plan.id,
            currencyCode = "IDR",
            userId = user1Id,
            periodNumber = 1,
            amountDueMinorUnits = 25000000L,
            dueDate = "2026-11-25"
        )

        repository.saveInstallmentPlan(plan, user1Id, listOf(period))

        val storedPeriods = database.installmentPeriodDao().getPeriodsForPlan(plan.id, user1Id).first()
        assertEquals(1, storedPeriods.size)
        assertEquals("2026-11-25", storedPeriods[0].dueDate)
    }

    @Test
    fun test2_ValidLeapDate2024_02_29IsAccepted() = runBlocking {
        val plan = InstallmentPlanItem(
            id = "plan-leap-2024",
            name = "Kredivo",
            provider = "Kredivo",
            installmentAmount = 150000.0,
            currencyCode = "IDR",
            totalTenor = 1,
            startDate = "2024-02-01",
            nextDueDate = "2024-02-29",
            userId = user1Id
        )
        val period = InstallmentPeriod(
            id = "period-leap-2024",
            planId = plan.id,
            currencyCode = "IDR",
            userId = user1Id,
            periodNumber = 1,
            amountDueMinorUnits = 15000000L,
            dueDate = "2024-02-29"
        )

        repository.saveInstallmentPlan(plan, user1Id, listOf(period))

        val stored = database.installmentPeriodDao().getPeriodsForPlan(plan.id, user1Id).first()
        assertEquals(1, stored.size)
        assertEquals("2024-02-29", stored[0].dueDate)
    }

    @Test
    fun test3_InvalidLeapDate2025_02_29IsRejected() = runBlocking {
        val plan = InstallmentPlanItem(
            id = "plan-invalid-leap",
            name = "GoPay Later",
            provider = "GoPay",
            installmentAmount = 100000.0,
            currencyCode = "IDR",
            totalTenor = 1,
            startDate = "2025-02-01",
            nextDueDate = "2025-02-29",
            userId = user1Id
        )
        val period = InstallmentPeriod(
            id = "period-invalid-leap",
            planId = plan.id,
            currencyCode = "IDR",
            userId = user1Id,
            periodNumber = 1,
            amountDueMinorUnits = 10000000L,
            dueDate = "2025-02-29"
        )

        try {
            repository.saveInstallmentPlan(plan, user1Id, listOf(period))
            fail("Expected IllegalArgumentException for 2025-02-29")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("tidak valid") == true)
        }

        val stored = database.installmentPeriodDao().getPeriodsForPlan(plan.id, user1Id).first()
        assertTrue("No periods should be inserted", stored.isEmpty())
    }

    @Test
    fun test4_InvalidDayMonthCombinationsAreRejected() = runBlocking {
        val invalidDates = listOf(
            "2026-04-31", // April has 30 days
            "2026-06-31", // June has 30 days
            "2026-09-31", // September has 30 days
            "2026-11-31", // November has 30 days
            "2026-13-01", // Month 13 does not exist
            "2026-00-15"  // Month 0 does not exist
        )

        for (invalidDate in invalidDates) {
            val plan = InstallmentPlanItem(
                id = "plan-inv-$invalidDate",
                name = "Test Plan",
                provider = "Test",
                installmentAmount = 50000.0,
                currencyCode = "IDR",
                totalTenor = 1,
                startDate = "2026-01-01",
                nextDueDate = invalidDate,
                userId = user1Id
            )
            val period = InstallmentPeriod(
                id = "period-inv-$invalidDate",
                planId = plan.id,
                currencyCode = "IDR",
                userId = user1Id,
                periodNumber = 1,
                amountDueMinorUnits = 5000000L,
                dueDate = invalidDate
            )

            try {
                repository.saveInstallmentPlan(plan, user1Id, listOf(period))
                fail("Expected rejection for date: $invalidDate")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("tidak valid") == true)
            }
        }
    }

    @Test
    fun test5_MalformedIsoStringsAreRejected() = runBlocking {
        val malformedStrings = listOf(
            "2026/11/25",
            "25-11-2026",
            "2026-1-5",
            "2026-11-25 ",
            " 2026-11-25",
            "Tgl 15 tiap bulan",
            ""
        )

        for (malformed in malformedStrings) {
            val plan = InstallmentPlanItem(
                id = "plan-malformed-$malformed",
                name = "Malformed Plan",
                provider = "Test",
                installmentAmount = 50000.0,
                currencyCode = "IDR",
                totalTenor = 1,
                startDate = "2026-01-01",
                nextDueDate = malformed,
                userId = user1Id
            )
            val period = InstallmentPeriod(
                id = "period-malformed-$malformed",
                planId = plan.id,
                currencyCode = "IDR",
                userId = user1Id,
                periodNumber = 1,
                amountDueMinorUnits = 5000000L,
                dueDate = malformed
            )

            try {
                repository.saveInstallmentPlan(plan, user1Id, listOf(period))
                fail("Expected rejection for malformed string: '$malformed'")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("tidak valid") == true)
            }
        }
    }

    @Test
    fun test6_BatchContainingOneInvalidPeriodInsertsNoPlansOrPeriods() = runBlocking {
        val plan1 = InstallmentPlanItem(id = "batch-p1", name = "Plan 1", provider = "GoPay", installmentAmount = 100000.0, currencyCode = "IDR", totalTenor = 1, startDate = "2026-11-01", nextDueDate = "2026-11-20", userId = user1Id)
        val period1 = InstallmentPeriod(id = "batch-per1", planId = "batch-p1", currencyCode = "IDR", userId = user1Id, periodNumber = 1, amountDueMinorUnits = 10000000L, dueDate = "2026-11-20")

        // Plan 2 has an invalid date: 2026-04-31
        val plan2 = InstallmentPlanItem(id = "batch-p2", name = "Plan 2", provider = "Shopee", installmentAmount = 200000.0, currencyCode = "IDR", totalTenor = 1, startDate = "2026-04-01", nextDueDate = "2026-04-31", userId = user1Id)
        val period2 = InstallmentPeriod(id = "batch-per2", planId = "batch-p2", currencyCode = "IDR", userId = user1Id, periodNumber = 1, amountDueMinorUnits = 20000000L, dueDate = "2026-04-31")

        val batchInput = listOf(
            plan1 to listOf(period1),
            plan2 to listOf(period2)
        )

        val result = repository.saveBatchPayLaterBills(
            debtsToSave = emptyList(),
            plansWithPeriods = batchInput,
            userId = user1Id
        )

        assertTrue("Batch must fail when one item has invalid date", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)

        // Verify ATOMICITY: Plan 1 must NOT be inserted into database!
        val storedP1 = database.installmentPlanDao().getInstallmentPlanById("batch-p1", user1Id)
        val storedPer1 = database.installmentPeriodDao().getPeriodsForPlan("batch-p1", user1Id).first()
        assertTrue("Plan 1 must not be saved due to atomic batch rejection", storedP1 == null)
        assertTrue("Period 1 must not be saved due to atomic batch rejection", storedPer1.isEmpty())
    }

    @Test
    fun test7_ValidBatchPersistsAllExpectedPeriodsAtomically() = runBlocking {
        val plan1 = InstallmentPlanItem(id = "v-p1", name = "Plan 1", provider = "GoPay", installmentAmount = 100000.0, currencyCode = "IDR", totalTenor = 1, startDate = "2026-11-01", nextDueDate = "2026-11-20", userId = user1Id)
        val period1 = InstallmentPeriod(id = "v-per1", planId = "v-p1", currencyCode = "IDR", userId = user1Id, periodNumber = 1, amountDueMinorUnits = 10000000L, dueDate = "2026-11-20")

        val plan2 = InstallmentPlanItem(id = "v-p2", name = "Plan 2", provider = "Shopee", installmentAmount = 200000.0, currencyCode = "IDR", totalTenor = 1, startDate = "2026-12-01", nextDueDate = "2026-12-05", userId = user1Id)
        val period2 = InstallmentPeriod(id = "v-per2", planId = "v-p2", currencyCode = "IDR", userId = user1Id, periodNumber = 1, amountDueMinorUnits = 20000000L, dueDate = "2026-12-05")

        val debt1 = DebtItem(id = "v-debt1", name = "Kartu Kredit", amount = 500000.0, interestRatePercent = 2.5, minMonthlyPayment = 50000.0, dueDate = "2026-11-25", userId = user1Id)

        val result = repository.saveBatchPayLaterBills(
            debtsToSave = listOf(debt1),
            plansWithPeriods = listOf(plan1 to listOf(period1), plan2 to listOf(period2)),
            userId = user1Id
        )

        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrThrow()) // 1 debt + 2 plans

        val p1 = database.installmentPlanDao().getInstallmentPlanById("v-p1", user1Id)
        val p2 = database.installmentPlanDao().getInstallmentPlanById("v-p2", user1Id)
        assertNotNull(p1)
        assertNotNull(p2)
    }

    @Test
    fun test8_InvalidInputDoesNotCreatePaymentTransactions() = runBlocking {
        val txCountBefore = database.transactionDao().getTransactionCount(user1Id)

        val plan = InstallmentPlanItem(id = "p-tx", name = "Plan", provider = "P", installmentAmount = 100000.0, currencyCode = "IDR", totalTenor = 1, startDate = "2025-02-01", nextDueDate = "2025-02-29", userId = user1Id)
        val period = InstallmentPeriod(id = "per-tx", planId = "p-tx", currencyCode = "IDR", userId = user1Id, periodNumber = 1, amountDueMinorUnits = 10000000L, dueDate = "2025-02-29")

        try {
            repository.saveInstallmentPlan(plan, user1Id, listOf(period))
        } catch (_: Exception) {}

        val txCountAfter = database.transactionDao().getTransactionCount(user1Id)
        assertEquals("Transaction count must remain unchanged", txCountBefore, txCountAfter)
    }

    @Test
    fun test9_InvalidInputDoesNotChangeWalletBalances() = runBlocking {
        val walletBefore = database.walletDao().getWalletById(10L, user1Id)!!
        val balanceBefore = walletBefore.balanceMinorUnits

        val plan = InstallmentPlanItem(id = "p-bal", name = "Plan", provider = "P", installmentAmount = 100000.0, currencyCode = "IDR", totalTenor = 1, startDate = "2026-04-01", nextDueDate = "2026-04-31", userId = user1Id)
        val period = InstallmentPeriod(id = "per-bal", planId = "p-bal", currencyCode = "IDR", userId = user1Id, periodNumber = 1, amountDueMinorUnits = 10000000L, dueDate = "2026-04-31")

        try {
            repository.saveInstallmentPlan(plan, user1Id, listOf(period))
        } catch (_: Exception) {}

        val walletAfter = database.walletDao().getWalletById(10L, user1Id)!!
        assertEquals("Wallet balance minor units must remain unchanged", balanceBefore, walletAfter.balanceMinorUnits)
    }

    @Test
    fun test10_ExistingUserIsolationBehaviorRemainsIntact() = runBlocking {
        val plan = InstallmentPlanItem(id = "p-u1", name = "User 1 Plan", provider = "P", installmentAmount = 50000.0, currencyCode = "IDR", totalTenor = 1, startDate = "2026-11-01", nextDueDate = "2026-11-20", userId = user1Id)
        val period = InstallmentPeriod(id = "per-u1", planId = "p-u1", currencyCode = "IDR", userId = user1Id, periodNumber = 1, amountDueMinorUnits = 5000000L, dueDate = "2026-11-20")

        repository.saveInstallmentPlan(plan, user1Id, listOf(period))

        // User 2 must NOT see User 1's plan or periods
        val user2Plan = database.installmentPlanDao().getInstallmentPlanById("p-u1", user2Id)
        val user2Periods = database.installmentPeriodDao().getPeriodsForPlan("p-u1", user2Id).first()
        assertFalse("User 2 cannot access User 1's plan", user2Plan != null)
        assertTrue("User 2 cannot access User 1's periods", user2Periods.isEmpty())
    }
}
