package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPeriod
import com.example.data.model.InstallmentPlanItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.data.repository.FinanceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests verifying Defect 2:
 * Scoped Reset of Debts & Installment Plans with Strict Ledger Preservation.
 * Ensures:
 * 1. Debts and Installment Plans (including periods) are wiped for the active user.
 * 2. Data of other users is untouched.
 * 3. CRITICAL: General ledger transactions (TransactionItem) and Wallets are 100% preserved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DebtPlannerResetTest {

    private lateinit var db: FinSmartDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var context: Context

    private val userAId = 101L
    private val userBId = 202L

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = FinanceRepository(db.walletDao(), db)

        // Seed users
        db.userDao().insertUser(
            User(
                id = userAId,
                username = "User A",
                email = "userA@example.com",
                role = UserRole.FREE_MEMBER
            )
        )
        db.userDao().insertUser(
            User(
                id = userBId,
                username = "User B",
                email = "userB@example.com",
                role = UserRole.FREE_MEMBER
            )
        )

        // Seed Wallets
        val walletAId = db.walletDao().insertWallet(
            Wallet(
                id = 1,
                name = "Dompet Utama A",
                balance = 2500000.0,
                type = WalletType.CASH,
                userId = userAId
            )
        )

        // Seed General Transactions for User A (Ledger)
        db.transactionDao().insertTransaction(
            TransactionItem(
                id = 101,
                title = "Gaji Bulanan",
                amount = 5000000.0,
                type = TransactionType.INCOME,
                category = "Gaji",
                walletId = walletAId,
                walletName = "Dompet Utama A",
                userId = userAId
            )
        )
        db.transactionDao().insertTransaction(
            TransactionItem(
                id = 102,
                title = "Belanja Bulanan",
                amount = 2500000.0,
                type = TransactionType.EXPENSE,
                category = "Belanja",
                walletId = walletAId,
                walletName = "Dompet Utama A",
                userId = userAId
            )
        )

        // Seed Debts for User A
        db.debtDao().insertDebt(
            DebtItem(
                id = "debt-a-1",
                name = "Pinjaman KTA A",
                amount = 3000000.0,
                interestRatePercent = 0.0,
                minMonthlyPayment = 300000.0,
                userId = userAId
            )
        )

        // Seed Installment Plan & Period for User A
        val planA = InstallmentPlanItem(
            id = "plan-a-1",
            name = "PayLater Phone A",
            provider = "SPayLater",
            installmentAmount = 500000.0,
            totalTenor = 3,
            paidCount = 0,
            startDate = "2026-11-01",
            nextDueDate = "2026-12-01",
            anchorDay = 1,
            userId = userAId
        )
        db.installmentPlanDao().insertInstallmentPlan(planA)
        db.installmentPeriodDao().insertPeriod(
            InstallmentPeriod(
                id = "period-a-1",
                planId = planA.id,
                userId = userAId,
                periodNumber = 1,
                amountDueMinorUnits = 50000000L,
                dueDate = "2026-12-01",
                isPaid = false
            )
        )

        // Seed Debts for User B
        db.debtDao().insertDebt(
            DebtItem(
                id = "debt-b-1",
                name = "Hutang User B",
                amount = 1500000.0,
                interestRatePercent = 0.0,
                minMonthlyPayment = 150000.0,
                userId = userBId
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testResetAllDebtsAndPlans_wipesDebtsAndPlansForUser_preservesLedgerAndWallets() = runBlocking {
        // Verify initial state
        val initialDebtsA = db.debtDao().getAllDebts(userAId).first()
        val initialPlansA = db.installmentPlanDao().getAllInstallmentPlans(userAId).first()
        val initialTxA = db.transactionDao().getAllTransactions(userAId).first()
        val initialWalletA = db.walletDao().getWalletById(1, userAId)

        assertEquals(1, initialDebtsA.size)
        assertEquals(1, initialPlansA.size)
        assertEquals(2, initialTxA.size)
        assertEquals(2500000.0, initialWalletA!!.balance, 0.001)

        // Execute scoped reset for User A
        val (deletedDebts, deletedPlans) = repository.resetAllDebtsAndPlans(userAId)
        assertEquals(1, deletedDebts)
        assertEquals(1, deletedPlans)

        // Verify debts and installment plans are wiped for User A
        val afterDebtsA = db.debtDao().getAllDebts(userAId).first()
        val afterPlansA = db.installmentPlanDao().getAllInstallmentPlans(userAId).first()
        assertTrue("Debts for User A must be empty after reset", afterDebtsA.isEmpty())
        assertTrue("Plans for User A must be empty after reset", afterPlansA.isEmpty())

        // Verify User B's debts are NOT affected (isolation)
        val afterDebtsB = db.debtDao().getAllDebts(userBId).first()
        assertEquals("User B debts must be untouched", 1, afterDebtsB.size)
        assertEquals("debt-b-1", afterDebtsB[0].id)

        // CRITICAL INVARIANT: General ledger transactions and wallet balances must remain 100% intact
        val afterTxA = db.transactionDao().getAllTransactions(userAId).first()
        assertEquals("Ledger transactions must remain intact", 2, afterTxA.size)
        assertEquals("Gaji Bulanan", afterTxA.find { it.id == 101L }?.title)
        assertEquals("Belanja Bulanan", afterTxA.find { it.id == 102L }?.title)

        val afterWalletA = db.walletDao().getWalletById(1, userAId)
        assertEquals("Wallet balance must be preserved", 2500000.0, afterWalletA!!.balance, 0.001)
    }
}
