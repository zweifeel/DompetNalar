package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.AssetCategory
import com.example.data.model.AssetItem
import com.example.data.model.BudgetLimitItem
import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPlanItem
import com.example.data.model.SplitBillHistoryItem
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase5DomainAndClaimTest {

    private lateinit var context: Context
    private lateinit var db: FinSmartDatabase
    private lateinit var repository: FinanceRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(db.walletDao(), db)

        runBlocking {
            db.userDao().insertUser(
                User(id = 1L, username = "alice", email = "alice@test.com", passwordHash = "h", role = UserRole.FREE_MEMBER, createdAt = 1000L)
            )
            db.userDao().insertUser(
                User(id = 2L, username = "bob", email = "bob@test.com", passwordHash = "h", role = UserRole.FREE_MEMBER, createdAt = 2000L)
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `T-25 and T-28 Atomic claim parent-first child-second succeeds under FK enforcement`() = runBlocking {
        // Seed legacy wallet (userId = null)
        db.walletDao().insertWallet(
            Wallet(id = 100L, name = "Legacy Wallet", type = WalletType.BANK, balance = 250000.0, currencyCode = "IDR", userId = null)
        )
        // Seed legacy transactions (userId = null)
        db.transactionDao().insertTransaction(
            TransactionItem(
                id = 1001L,
                title = "Legacy Grocery",
                amount = 50000.0,
                type = TransactionType.EXPENSE,
                category = "Food",
                walletId = 100L,
                walletName = "Legacy Wallet",
                userId = null
            )
        )
        db.transactionDao().insertTransaction(
            TransactionItem(
                id = 1002L,
                title = "Legacy Transport",
                amount = 20000.0,
                type = TransactionType.EXPENSE,
                category = "Transport",
                walletId = 100L,
                walletName = "Legacy Wallet",
                userId = null
            )
        )

        // User 1 claims the wallet atomically
        val result = repository.claimLegacyWalletAtomically(100L, 1L)
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())

        // Verify wallet is now owned by user 1
        val wallet = db.walletDao().getWalletById(100L)
        assertNotNull(wallet)
        assertEquals(1L, wallet?.userId)

        // Verify transactions are now owned by user 1
        val txs = db.transactionDao().getAllTransactions(1L).first()
        assertEquals(2, txs.size)
        assertTrue(txs.all { it.userId == 1L })
    }

    @Test
    fun `T-26 Condition C Multi-tenant conflict strictly aborts claim with zero mutations`() = runBlocking {
        // Legacy wallet
        db.walletDao().insertWallet(
            Wallet(id = 200L, name = "Contested Wallet", type = WalletType.BANK, balance = 100000.0, currencyCode = "IDR", userId = null)
        )
        // Transaction owned by another user (user 2)
        db.transactionDao().insertTransaction(
            TransactionItem(
                id = 2001L,
                title = "Bob Transaction",
                amount = 30000.0,
                type = TransactionType.EXPENSE,
                category = "General",
                walletId = 200L,
                walletName = "Contested Wallet",
                userId = 2L
            )
        )

        // User 1 attempts to claim wallet 200
        val result = repository.claimLegacyWalletAtomically(200L, 1L)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("terhubung dengan akun lain") == true)

        // Verify wallet remains null (untouched)
        val wallet = db.walletDao().getWalletById(200L)
        assertNull(wallet?.userId)

        // Verify user 2's transaction is completely untouched
        val bobTxs = db.transactionDao().getAllTransactions(2L).first()
        assertEquals(1, bobTxs.size)
    }

    @Test
    fun `T-27 Condition D Already claimed wallet aborts safely`() = runBlocking {
        // Wallet already claimed by user 2
        db.walletDao().insertWallet(
            Wallet(id = 300L, name = "Claimed Wallet", type = WalletType.BANK, balance = 100000.0, currencyCode = "IDR", userId = 2L)
        )

        val result = repository.claimLegacyWalletAtomically(300L, 1L)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("sudah diklaim") == true)

        val wallet = db.walletDao().getWalletById(300L)
        assertEquals(2L, wallet?.userId)
    }

    @Test
    fun `T-31 to T-33 Resumable one-by-one legacy wallet assignment workflow`() = runBlocking {
        // Seed 3 unassigned wallets for user 1
        db.walletDao().insertWallet(
            Wallet(id = 11L, name = "Wallet 1", type = WalletType.BANK, balance = 10000.0, currencyCode = null, userId = 1L)
        )
        db.walletDao().insertWallet(
            Wallet(id = 12L, name = "Wallet 2", type = WalletType.CASH, balance = 20000.0, currencyCode = null, userId = 1L)
        )
        db.walletDao().insertWallet(
            Wallet(id = 13L, name = "Wallet 3", type = WalletType.EWALLET, balance = 30000.0, currencyCode = null, userId = 1L)
        )

        // T-31: Startup detection returns all 3 unassigned wallets
        var pending = repository.getUnassignedCurrencyWallets(1L)
        assertEquals(3, pending.size)
        assertEquals(11L, pending[0].id)

        // T-32: Assign first wallet to IDR
        val assignResult1 = repository.assignWalletCurrency(11L, "IDR", 1L)
        assertTrue(assignResult1.isSuccess)
        val w1 = db.walletDao().getWalletById(11L)
        assertEquals("IDR", w1?.currencyCode)
        assertTrue(w1!!.hasAssignedCurrency)

        // T-33: Process death simulation - query pending wallets returns remaining 2
        pending = repository.getUnassignedCurrencyWallets(1L)
        assertEquals(2, pending.size)
        assertEquals(12L, pending[0].id)
        assertEquals(13L, pending[1].id)

        // Assign second wallet to USD
        val assignResult2 = repository.assignWalletCurrency(12L, "USD", 1L)
        assertTrue(assignResult2.isSuccess)

        // Assign third wallet to JPY
        val assignResult3 = repository.assignWalletCurrency(13L, "JPY", 1L)
        assertTrue(assignResult3.isSuccess)

        // Final query returns empty list
        pending = repository.getUnassignedCurrencyWallets(1L)
        assertEquals(0, pending.size)
    }

    @Test
    fun `T-39 to T-43 Non-wallet table isolation and atomic legacy claims`() = runBlocking {
        // 1. Budget limits
        db.budgetLimitDao().insertBudgetLimit(
            BudgetLimitItem(id = "FOOD", limitAmount = 1500000.0, isCategory = true, userId = null)
        )
        val claimedLimits = repository.claimLegacyBudgetLimits(1L)
        assertEquals(1, claimedLimits)
        val limit = db.budgetLimitDao().getBudgetLimit(1L, "FOOD")
        assertNotNull(limit)
        assertEquals(1L, limit?.userId)

        // 2. Debts
        db.debtDao().insertDebt(
            DebtItem(id = "debt_1", name = "Loan", amount = 1000000.0, interestRatePercent = 5.0, minMonthlyPayment = 100000.0, category = "Personal", dueDate = "1", userId = null)
        )
        val claimedDebts = repository.claimLegacyDebts(1L)
        assertEquals(1, claimedDebts)
        val debts = db.debtDao().getAllDebts(1L).first()
        assertEquals(1, debts.size)
        assertEquals(1L, debts[0].userId)

        // 3. Installments
        db.installmentPlanDao().insertInstallmentPlan(
            InstallmentPlanItem(id = "plan_1", name = "Motor", installmentAmount = 500000.0, totalTenor = 12, startDate = "2026-01-01", nextDueDate = "2026-02-01", userId = null)
        )
        val claimedInstallments = repository.claimLegacyInstallments(1L)
        assertEquals(1, claimedInstallments)
        val plans = db.installmentPlanDao().getAllInstallmentPlans(1L).first()
        assertEquals(1, plans.size)
        assertEquals(1L, plans[0].userId)

        // 4. Assets
        db.assetDao().insertAsset(
            AssetItem(id = "asset_1", name = "Laptop", initialValue = 15000000.0, purchaseYear = 2024, category = AssetCategory.ELECTRONICS, userId = null)
        )
        val claimedAssets = repository.claimLegacyAssets(1L)
        assertEquals(1, claimedAssets)
        val assets = db.assetDao().getAllAssets(1L).first()
        assertEquals(1, assets.size)
        assertEquals(1L, assets[0].userId)

        // 5. Split bill history
        db.splitBillHistoryDao().insertSplitBillHistory(
            SplitBillHistoryItem(id = "sb_1", merchantName = "Cafe", date = "2026-10-31", totalAmount = 120000.0, formattedText = "", userId = null)
        )
        val claimedBills = repository.claimLegacySplitBills(1L)
        assertEquals(1, claimedBills)
        val bills = db.splitBillHistoryDao().getAllSplitBillHistory(1L).first()
        assertEquals(1, bills.size)
        assertEquals(1L, bills[0].userId)
    }
}
