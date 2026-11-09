package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.InstallmentPeriod
import com.example.data.model.InstallmentPlanItem
import com.example.data.model.User
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.data.repository.PayLaterLedgerService
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

import com.example.data.local.Migration10To11
import com.example.data.model.UserRole

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PayLaterLedgerTest {

    private lateinit var context: Context
    private lateinit var database: FinSmartDatabase
    private lateinit var ledgerService: PayLaterLedgerService

    private val user1Id = 101L
    private val user2Id = 102L

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        Migration10To11.installIntegrityTriggers(database.openHelper.writableDatabase)
        ledgerService = PayLaterLedgerService(database)

        // Seed users
        database.userDao().insertUser(User(id = user1Id, username = "User One", email = "u1@test.com", passwordHash = "h", role = UserRole.FREE_MEMBER, createdAt = 1000, trialStartDate = 1000, isApproved = true, isGuest = false))
        database.userDao().insertUser(User(id = user2Id, username = "User Two", email = "u2@test.com", passwordHash = "h", role = UserRole.FREE_MEMBER, createdAt = 1000, trialStartDate = 1000, isApproved = true, isGuest = false))

        // Seed wallets
        database.walletDao().insertWallet(Wallet(id = 1L, name = "IDR Wallet", type = WalletType.BANK, accountNumber = "1", balance = 5000.0, balanceMinorUnits = 50000000L, colorHex = "#00", iconName = "i", accountMode = "PERSONAL", isDefault = true, createdAt = 1000, currencyCode = "IDR", userId = user1Id))
        database.walletDao().insertWallet(Wallet(id = 2L, name = "USD Wallet", type = WalletType.BANK, accountNumber = "2", balance = 500.0, balanceMinorUnits = 50000L, colorHex = "#00", iconName = "i", accountMode = "PERSONAL", isDefault = false, createdAt = 1000, currencyCode = "USD", userId = user1Id))
        database.walletDao().insertWallet(Wallet(id = 3L, name = "User 2 Wallet", type = WalletType.BANK, accountNumber = "3", balance = 5000.0, balanceMinorUnits = 50000000L, colorHex = "#00", iconName = "i", accountMode = "PERSONAL", isDefault = true, createdAt = 1000, currencyCode = "IDR", userId = user2Id))

        // Seed installment plan for User 1
        database.installmentPlanDao().insertInstallmentPlan(
            InstallmentPlanItem(
                id = "plan-multi",
                name = "Gadget Purchase",
                provider = "FinPay",
                installmentAmount = 1000.0,
                totalAmountMinorUnits = 30000000L, // 300,000 IDR
                currencyCode = "IDR",
                totalTenor = 3,
                paidCount = 0,
                frequency = "Bulanan",
                startDate = "2024-01-01",
                nextDueDate = "2024-02-01",
                anchorDay = 1,
                status = "AKTIF",
                notes = "Multi-period test",
                createdAt = 1000,
                userId = user1Id
            )
        )

        // Seed 3 periods for plan-multi (100,000 minor units each)
        database.installmentPeriodDao().insertPeriods(
            listOf(
                InstallmentPeriod(id = "per-1", planId = "plan-multi", currencyCode = "IDR", userId = user1Id, periodNumber = 1, amountDueMinorUnits = 10000000L, amountPaidMinorUnits = 0L, dueDate = "2024-02-01", isPaid = false),
                InstallmentPeriod(id = "per-2", planId = "plan-multi", currencyCode = "IDR", userId = user1Id, periodNumber = 2, amountDueMinorUnits = 10000000L, amountPaidMinorUnits = 0L, dueDate = "2024-03-01", isPaid = false),
                InstallmentPeriod(id = "per-3", planId = "plan-multi", currencyCode = "IDR", userId = user1Id, periodNumber = 3, amountDueMinorUnits = 10000000L, amountPaidMinorUnits = 0L, dueDate = "2024-04-01", isPaid = false)
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testMultiPeriodCoverageSequentialAllocation() = runBlocking {
        // Pay 250,000 minor units (should fully pay per-1 and per-2, and 50,000 to per-3)
        val result = ledgerService.recordPayment(
            userId = user1Id,
            planId = "plan-multi",
            walletId = 1L,
            amountMinorUnits = 25000000L
        )

        assertTrue("Payment should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val payment = result.getOrThrow()
        assertEquals(25000000L, payment.totalMinorUnits)
        assertEquals(0L, payment.unallocatedMinorUnits)
        assertFalse(payment.isVoid)

        // Verify Period 1: Fully paid
        val p1 = database.installmentPeriodDao().getPeriodById("per-1", user1Id)!!
        assertEquals(10000000L, p1.amountPaidMinorUnits)
        assertTrue(p1.isPaid)

        // Verify Period 2: Fully paid
        val p2 = database.installmentPeriodDao().getPeriodById("per-2", user1Id)!!
        assertEquals(10000000L, p2.amountPaidMinorUnits)
        assertTrue(p2.isPaid)

        // Verify Period 3: Partially paid (50,000)
        val p3 = database.installmentPeriodDao().getPeriodById("per-3", user1Id)!!
        assertEquals(5000000L, p3.amountPaidMinorUnits)
        assertFalse(p3.isPaid)

        // Verify wallet debit
        val wallet = database.walletDao().getWalletById(1L, user1Id)!!
        assertEquals(25000000L, wallet.balanceMinorUnits)
    }

    @Test
    fun testExcessPaymentStoredInUnallocatedMinorUnits() = runBlocking {
        // Total plan due is 300,000. Pay 350,000 minor units -> 50,000 unallocated
        val result = ledgerService.recordPayment(
            userId = user1Id,
            planId = "plan-multi",
            walletId = 1L,
            amountMinorUnits = 35000000L
        )

        assertTrue(result.isSuccess)
        val payment = result.getOrThrow()
        assertEquals(35000000L, payment.totalMinorUnits)
        assertEquals(5000000L, payment.unallocatedMinorUnits)

        // All 3 periods must be fully paid
        val p1 = database.installmentPeriodDao().getPeriodById("per-1", user1Id)!!
        val p2 = database.installmentPeriodDao().getPeriodById("per-2", user1Id)!!
        val p3 = database.installmentPeriodDao().getPeriodById("per-3", user1Id)!!
        assertTrue(p1.isPaid && p2.isPaid && p3.isPaid)
    }

    @Test
    fun testCrossCurrencyRejection() = runBlocking {
        // Wallet 2 is USD, plan is IDR -> Rejection
        val result = ledgerService.recordPayment(
            userId = user1Id,
            planId = "plan-multi",
            walletId = 2L,
            amountMinorUnits = 10000L
        )

        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message ?: ""
        assertTrue("Error must mention cross-currency rejection: $msg", msg.contains("Cross-currency rejection"))
    }

    @Test
    fun testCrossUserRejection() = runBlocking {
        // User 2 attempts to pay User 1's plan -> Rejection
        val result = ledgerService.recordPayment(
            userId = user2Id,
            planId = "plan-multi",
            walletId = 3L,
            amountMinorUnits = 10000000L
        )

        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message ?: ""
        assertTrue(msg.contains("not found for user #102"))
    }

    @Test
    fun testPaymentVoidingAndReversalIntegrity() = runBlocking {
        // 1. Pay 100,000 to cover period 1
        val payResult = ledgerService.recordPayment(
            userId = user1Id,
            planId = "plan-multi",
            walletId = 1L,
            amountMinorUnits = 10000000L
        )
        assertTrue(payResult.isSuccess)
        val payment = payResult.getOrThrow()

        val p1Before = database.installmentPeriodDao().getPeriodById("per-1", user1Id)!!
        assertTrue(p1Before.isPaid)
        assertEquals(10000000L, p1Before.amountPaidMinorUnits)

        // 2. Void the payment
        val voidResult = ledgerService.voidPayment(
            userId = user1Id,
            paymentId = payment.id,
            reason = "Customer cancelled order"
        )
        assertTrue("Void should succeed: ${voidResult.exceptionOrNull()}", voidResult.isSuccess)

        // 3. Verify payment is marked void with reversalTransactionId
        val voidedPayment = database.installmentPaymentDao().getPaymentById(payment.id, user1Id)!!
        assertTrue(voidedPayment.isVoid)
        assertNotNull(voidedPayment.reversalTransactionId)

        // 4. Verify reversal transaction was created with matching details and proper INCOME counter-entry type
        val reversalTx = database.transactionDao().getTransactionById(voidedPayment.reversalTransactionId!!, user1Id)!!
        assertTrue(reversalTx.isReversal)
        assertEquals(payment.transactionId, reversalTx.reversedTransactionId)
        assertEquals(payment.totalMinorUnits, reversalTx.amountMinorUnits)
        assertEquals("IDR", reversalTx.currencyCode)
        assertEquals(com.example.data.model.TransactionType.INCOME, reversalTx.type)

        // 5. Verify Period 1 amount was deducted back to 0 and isPaid reset to false
        val p1After = database.installmentPeriodDao().getPeriodById("per-1", user1Id)!!
        assertFalse(p1After.isPaid)
        assertEquals(0L, p1After.amountPaidMinorUnits)

        // 6. Verify wallet balance was refunded
        val walletAfter = database.walletDao().getWalletById(1L, user1Id)!!
        assertEquals(50000000L, walletAfter.balanceMinorUnits)

        // 7. Verify duplicate void attempt fails
        val duplicateVoidResult = ledgerService.voidPayment(
            userId = user1Id,
            paymentId = payment.id
        )
        assertTrue(duplicateVoidResult.isFailure)
    }
}
