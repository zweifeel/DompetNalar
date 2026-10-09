package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AccountMode
import com.example.data.model.AssetCategory
import com.example.data.model.AssetItem
import com.example.data.model.BudgetLimitItem
import com.example.data.model.CategoryItem
import com.example.data.model.DebtItem
import com.example.data.model.FeedbackItem
import com.example.data.model.SplitBillHistoryItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DaoIsolationTest {

    private lateinit var db: FinSmartDatabase
    private lateinit var context: Context

    private val userAId = 1L
    private val userBId = 2L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            // Insert User A and User B to satisfy foreign keys
            db.userDao().insertUser(
                User(
                    id = userAId,
                    username = "user_a",
                    email = "a@example.com",
                    role = UserRole.FREE_MEMBER
                )
            )
            db.userDao().insertUser(
                User(
                    id = userBId,
                    username = "user_b",
                    email = "b@example.com",
                    role = UserRole.FREE_MEMBER
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    // =========================================================================
    // DAO-01: A sees A's transactions; B sees none of A's transactions.
    // =========================================================================
    @Test
    fun dao01_userASeesOwnTransactions_userBSeesNone() = runBlocking {
        val walletAId = db.walletDao().insertWallet(
            Wallet(
                id = 101L,
                name = "A's Wallet",
                type = WalletType.BANK,
                balance = 1000.0,
                accountMode = AccountMode.PERSONAL.name,
                userId = userAId
            )
        )

        val txAId = db.transactionDao().insertTransaction(
            TransactionItem(
                id = 1001L,
                title = "A's Groceries",
                amount = 50.0,
                type = TransactionType.EXPENSE,
                category = "Groceries",
                walletId = walletAId,
                walletName = "A's Wallet",
                accountMode = AccountMode.PERSONAL.name,
                userId = userAId
            )
        )

        // User A reads
        val txsForA = db.transactionDao().getAllTransactions(userAId).first()
        val txsForAMode = db.transactionDao().getTransactionsByMode(userAId, AccountMode.PERSONAL.name).first()
        val txByIdForA = db.transactionDao().getTransactionById(txAId, userAId)
        val countForA = db.transactionDao().getTransactionCount(userAId)

        assertEquals(1, txsForA.size)
        assertEquals(1, txsForAMode.size)
        assertNotNull(txByIdForA)
        assertEquals(txAId, txByIdForA?.id)
        assertEquals(1, countForA)

        // User B reads (should see none)
        val txsForB = db.transactionDao().getAllTransactions(userBId).first()
        val txsForBMode = db.transactionDao().getTransactionsByMode(userBId, AccountMode.PERSONAL.name).first()
        val txByIdForB = db.transactionDao().getTransactionById(txAId, userBId)
        val countForB = db.transactionDao().getTransactionCount(userBId)

        assertTrue(txsForB.isEmpty())
        assertTrue(txsForBMode.isEmpty())
        assertNull(txByIdForB)
        assertEquals(0, countForB)
    }

    // =========================================================================
    // DAO-02: B cannot delete A's transaction.
    // =========================================================================
    @Test
    fun dao02_userBCannotDeleteOrUpdateUserATransaction() = runBlocking {
        val walletAId = db.walletDao().insertWallet(
            Wallet(
                id = 102L,
                name = "A's Wallet",
                type = WalletType.BANK,
                balance = 1000.0,
                accountMode = AccountMode.PERSONAL.name,
                userId = userAId
            )
        )

        val txAId = db.transactionDao().insertTransaction(
            TransactionItem(
                id = 1002L,
                title = "Original Title",
                amount = 75.0,
                type = TransactionType.EXPENSE,
                category = "Food",
                walletId = walletAId,
                walletName = "A's Wallet",
                accountMode = AccountMode.PERSONAL.name,
                userId = userAId
            )
        )

        // User B tries to delete A's transaction by ID
        val deleteRows = db.transactionDao().deleteTransaction(txAId, userBId)
        assertEquals(0, deleteRows)

        // Verify transaction still exists for User A
        val txAfterDeleteAttempt = db.transactionDao().getTransactionById(txAId, userAId)
        assertNotNull(txAfterDeleteAttempt)

        // User B tries to update A's transaction
        val maliciousTx = txAfterDeleteAttempt!!.copy(title = "Tampered Title")
        val updateRows = db.transactionDao().updateTransaction(maliciousTx, userBId)
        assertEquals(0, updateRows)

        // Verify transaction still retains original title
        val txAfterUpdateAttempt = db.transactionDao().getTransactionById(txAId, userAId)
        assertEquals("Original Title", txAfterUpdateAttempt?.title)

        // User A successfully deletes own transaction
        val successfulDelete = db.transactionDao().deleteTransaction(txAId, userAId)
        assertEquals(1, successfulDelete)
        assertNull(db.transactionDao().getTransactionById(txAId, userAId))
    }

    // =========================================================================
    // DAO-03: B cannot read A's wallet.
    // =========================================================================
    @Test
    fun dao03_userBCannotReadUserAWallet() = runBlocking {
        val walletAId = db.walletDao().insertWallet(
            Wallet(
                id = 103L,
                name = "A's Secret Bank",
                type = WalletType.BANK,
                balance = 50000.0,
                accountMode = AccountMode.PERSONAL.name,
                userId = userAId
            )
        )

        // User A reads
        val walletForA = db.walletDao().getWalletById(walletAId, userAId)
        val walletsForA = db.walletDao().getAllWallets(userAId).first()
        val countForA = db.walletDao().getWalletCount(userAId)

        assertNotNull(walletForA)
        assertEquals("A's Secret Bank", walletForA?.name)
        assertEquals(1, walletsForA.size)
        assertEquals(1, countForA)

        // User B reads (must return null / empty)
        val walletForB = db.walletDao().getWalletById(walletAId, userBId)
        val walletsForB = db.walletDao().getAllWallets(userBId).first()
        val walletsByModeForB = db.walletDao().getWalletsByMode(userBId, AccountMode.PERSONAL.name).first()
        val countForB = db.walletDao().getWalletCount(userBId)

        assertNull(walletForB)
        assertTrue(walletsForB.isEmpty())
        assertTrue(walletsByModeForB.isEmpty())
        assertEquals(0, countForB)
    }

    // =========================================================================
    // DAO-04: B cannot change A's wallet balance.
    // =========================================================================
    @Test
    fun dao04_userBCannotChangeUserAWalletBalance() = runBlocking {
        val walletAId = db.walletDao().insertWallet(
            Wallet(
                id = 104L,
                name = "A's Safe",
                type = WalletType.CASH,
                balance = 1_000_000.0,
                accountMode = AccountMode.PERSONAL.name,
                userId = userAId
            )
        )

        // B attempts balance adjustment
        val adjustRows = db.walletDao().adjustBalance(walletAId, userBId, -500_000.0)
        assertEquals(0, adjustRows)

        // B attempts balance set
        val setRows = db.walletDao().setBalance(walletAId, userBId, 0.0)
        assertEquals(0, setRows)

        // B attempts wallet delete
        val deleteRows = db.walletDao().deleteWallet(walletAId, userBId)
        assertEquals(0, deleteRows)

        // B attempts wallet update
        val walletA = db.walletDao().getWalletById(walletAId, userAId)!!
        val updateRows = db.walletDao().updateWallet(walletA.copy(name = "Hacked Safe"), userBId)
        assertEquals(0, updateRows)

        // Verify balance and attributes remain untouched for User A
        val walletCheck = db.walletDao().getWalletById(walletAId, userAId)
        assertNotNull(walletCheck)
        assertEquals(1_000_000.0, walletCheck?.balance ?: 0.0, 0.001)
        assertEquals("A's Safe", walletCheck?.name)
    }

    // =========================================================================
    // DAO-05: A and B can both have total_monthly budgets.
    // =========================================================================
    @Test
    fun dao05_usersCanBothHaveTotalMonthlyBudgetsWithoutCollision() = runBlocking {
        // User A inserts total_monthly budget
        db.budgetLimitDao().insertBudgetLimit(
            BudgetLimitItem(
                id = "total_monthly",
                limitAmount = 5_000_000.0,
                isCategory = false,
                userId = userAId
            )
        )

        // User B inserts total_monthly budget (must NOT collide or fail)
        db.budgetLimitDao().insertBudgetLimit(
            BudgetLimitItem(
                id = "total_monthly",
                limitAmount = 10_000_000.0,
                isCategory = false,
                userId = userBId
            )
        )

        // Read independently
        val budgetA = db.budgetLimitDao().getBudgetLimit(userAId, "total_monthly")
        val budgetB = db.budgetLimitDao().getBudgetLimit(userBId, "total_monthly")

        assertNotNull(budgetA)
        assertNotNull(budgetB)
        assertEquals(5_000_000.0, budgetA?.limitAmount ?: 0.0, 0.001)
        assertEquals(10_000_000.0, budgetB?.limitAmount ?: 0.0, 0.001)

        // User B deletes their total_monthly budget
        val deleted = db.budgetLimitDao().deleteBudgetLimit(userBId, "total_monthly")
        assertEquals(1, deleted)

        // User A budget must still exist!
        val budgetAAfterBDelete = db.budgetLimitDao().getBudgetLimit(userAId, "total_monthly")
        assertNotNull(budgetAAfterBDelete)
        assertEquals(5_000_000.0, budgetAAfterBDelete?.limitAmount ?: 0.0, 0.001)
        assertNull(db.budgetLimitDao().getBudgetLimit(userBId, "total_monthly"))
    }

    // =========================================================================
    // DAO-06: B cannot read/delete A's debt.
    // =========================================================================
    @Test
    fun dao06_userBCannotReadOrDeleteUserADebt() = runBlocking {
        val debtA = DebtItem(
            id = "debt-a-01",
            name = "A's Car Loan",
            amount = 150_000_000.0,
            interestRatePercent = 5.0,
            minMonthlyPayment = 3_000_000.0,
            userId = userAId
        )
        db.debtDao().insertDebt(debtA)

        // User B cannot read
        assertNull(db.debtDao().getDebtById("debt-a-01", userBId))
        assertTrue(db.debtDao().getAllDebts(userBId).first().isEmpty())
        assertEquals(0, db.debtDao().getDebtCount(userBId))

        // User B cannot delete
        val deleteRows = db.debtDao().deleteDebt("debt-a-01", userBId)
        assertEquals(0, deleteRows)

        // User B cannot clear
        db.debtDao().clearAllDebts(userBId)

        // User B cannot update
        val updateRows = db.debtDao().updateDebt(debtA.copy(name = "Tampered Debt"), userBId)
        assertEquals(0, updateRows)

        // User A debt intact
        val debtCheck = db.debtDao().getDebtById("debt-a-01", userAId)
        assertNotNull(debtCheck)
        assertEquals("A's Car Loan", debtCheck?.name)
    }

    // =========================================================================
    // DAO-07: B cannot read/delete A's asset.
    // =========================================================================
    @Test
    fun dao07_userBCannotReadOrDeleteUserAAsset() = runBlocking {
        val assetA = AssetItem(
            id = "asset-a-01",
            name = "A's Gold Bar",
            initialValue = 50_000_000.0,
            purchaseYear = 2024,
            category = AssetCategory.GOLD_INVESTMENT,
            userId = userAId
        )
        db.assetDao().insertAsset(assetA)

        // User B cannot read
        assertNull(db.assetDao().getAssetById("asset-a-01", userBId))
        assertTrue(db.assetDao().getAllAssets(userBId).first().isEmpty())
        assertEquals(0, db.assetDao().getAssetCount(userBId))

        // User B cannot delete
        val deleteRows = db.assetDao().deleteAsset("asset-a-01", userBId)
        assertEquals(0, deleteRows)

        // User B cannot update
        val updateRows = db.assetDao().updateAsset(assetA.copy(name = "Stolen Gold"), userBId)
        assertEquals(0, updateRows)

        // User A asset intact
        val assetCheck = db.assetDao().getAssetById("asset-a-01", userAId)
        assertNotNull(assetCheck)
        assertEquals("A's Gold Bar", assetCheck?.name)
    }

    // =========================================================================
    // DAO-08: B cannot read/delete A's split-bill history.
    // =========================================================================
    @Test
    fun dao08_userBCannotReadOrDeleteUserASplitBillHistory() = runBlocking {
        val billA = SplitBillHistoryItem(
            id = "bill-a-01",
            merchantName = "Cafe A",
            date = "2026-03-01",
            totalAmount = 250_000.0,
            formattedText = "Details A",
            userId = userAId
        )
        db.splitBillHistoryDao().insertSplitBillHistory(billA)

        // User B cannot read
        assertNull(db.splitBillHistoryDao().getSplitBillHistoryById("bill-a-01", userBId))
        assertTrue(db.splitBillHistoryDao().getAllSplitBillHistory(userBId).first().isEmpty())
        assertEquals(0, db.splitBillHistoryDao().getSplitBillHistoryCount(userBId))

        // User B cannot delete
        val deleteRows = db.splitBillHistoryDao().deleteSplitBillHistory("bill-a-01", userBId)
        assertEquals(0, deleteRows)

        // User B cannot clear
        db.splitBillHistoryDao().clearAllHistory(userBId)

        // User A history intact
        val billCheck = db.splitBillHistoryDao().getSplitBillHistoryById("bill-a-01", userAId)
        assertNotNull(billCheck)
        assertEquals("Cafe A", billCheck?.merchantName)
    }

    // =========================================================================
    // DAO-09: A custom category is invisible to B.
    // =========================================================================
    @Test
    fun dao09_customCategoryIsInvisibleToOtherUser() = runBlocking {
        val customAId = db.categoryDao().insertCategory(
            CategoryItem(
                name = "A's Hobby",
                type = TransactionType.EXPENSE,
                isSystem = false,
                userId = userAId
            )
        )

        // User A sees it
        val categoriesA = db.categoryDao().getAllCategories(userAId).first()
        assertTrue(categoriesA.any { it.id == customAId && it.name == "A's Hobby" })
        assertNotNull(db.categoryDao().getCategoryById(customAId, userAId))

        // User B cannot see it
        val categoriesB = db.categoryDao().getAllCategories(userBId).first()
        val expenseCatsB = db.categoryDao().getCategoriesByType(userBId, TransactionType.EXPENSE).first()
        assertTrue(categoriesB.none { it.id == customAId })
        assertTrue(expenseCatsB.none { it.id == customAId })
        assertNull(db.categoryDao().getCategoryById(customAId, userBId))
    }

    // =========================================================================
    // DAO-10: B cannot update/delete A's custom category.
    // =========================================================================
    @Test
    fun dao10_userBCannotUpdateOrDeleteUserACustomCategory() = runBlocking {
        val customAId = db.categoryDao().insertCategory(
            CategoryItem(
                name = "A's Private Category",
                type = TransactionType.EXPENSE,
                isSystem = false,
                userId = userAId
            )
        )

        // User B attempts delete
        val deleteRows = db.categoryDao().deleteCategory(customAId, userBId)
        assertEquals(0, deleteRows)

        // User B attempts update
        val customA = db.categoryDao().getCategoryById(customAId, userAId)!!
        val updateRows = db.categoryDao().updateCategory(customA.copy(name = "Tampered Category"), userBId)
        assertEquals(0, updateRows)

        // Verify still intact for User A
        val categoryCheck = db.categoryDao().getCategoryById(customAId, userAId)
        assertNotNull(categoryCheck)
        assertEquals("A's Private Category", categoryCheck?.name)
    }

    // =========================================================================
    // DAO-11: System categories remain visible to both users but cannot be modified through user mutation operations.
    // =========================================================================
    @Test
    fun dao11_systemCategoriesVisibleToAllUsers_protectedFromUserMutation() = runBlocking {
        val systemCatId = db.categoryDao().insertCategory(
            CategoryItem(
                name = "Makanan & Minuman",
                type = TransactionType.EXPENSE,
                isSystem = true,
                userId = null
            )
        )

        // Both User A and User B see the system category
        val categoriesA = db.categoryDao().getAllCategories(userAId).first()
        val categoriesB = db.categoryDao().getAllCategories(userBId).first()

        assertTrue(categoriesA.any { it.id == systemCatId && it.name == "Makanan & Minuman" })
        assertTrue(categoriesB.any { it.id == systemCatId && it.name == "Makanan & Minuman" })

        assertNotNull(db.categoryDao().getCategoryById(systemCatId, userAId))
        assertNotNull(db.categoryDao().getCategoryById(systemCatId, userBId))

        // User A attempts delete (must be rejected because isSystem = 1)
        val deleteA = db.categoryDao().deleteCategory(systemCatId, userAId)
        assertEquals(0, deleteA)

        // User B attempts delete
        val deleteB = db.categoryDao().deleteCategory(systemCatId, userBId)
        assertEquals(0, deleteB)

        // User A attempts update (must be rejected)
        val systemCat = db.categoryDao().getCategoryById(systemCatId, userAId)!!
        val updateA = db.categoryDao().updateCategory(systemCat.copy(name = "Modified Makanan"), userAId)
        assertEquals(0, updateA)

        // System category still exists with original name
        val finalCheck = db.categoryDao().getCategoryById(systemCatId, userAId)
        assertNotNull(finalCheck)
        assertEquals("Makanan & Minuman", finalCheck?.name)
    }

    // =========================================================================
    // DAO-12: userId IS NULL legacy rows are invisible to normal user DAO queries.
    // =========================================================================
    @Test
    fun dao12_legacyNullRowsAreInvisibleToNormalUserQueries() = runBlocking {
        // Insert legacy rows with userId = NULL
        val legacyWalletId = db.walletDao().insertWallet(
            Wallet(
                id = 201L,
                name = "Legacy Wallet",
                balance = 500.0,
                userId = null
            )
        )

        db.transactionDao().insertTransaction(
            TransactionItem(
                id = 2001L,
                title = "Legacy Transaction",
                amount = 25.0,
                type = TransactionType.EXPENSE,
                category = "General",
                walletId = legacyWalletId,
                walletName = "Legacy Wallet",
                userId = null
            )
        )

        db.budgetLimitDao().insertBudgetLimit(
            BudgetLimitItem(
                id = "legacy_budget",
                limitAmount = 1_000_000.0,
                isCategory = false,
                userId = null
            )
        )

        db.debtDao().insertDebt(
            DebtItem(
                id = "legacy-debt-01",
                name = "Legacy Debt",
                amount = 500_000.0,
                interestRatePercent = 0.0,
                minMonthlyPayment = 500_000.0,
                userId = null
            )
        )

        db.assetDao().insertAsset(
            AssetItem(
                id = "legacy-asset-01",
                name = "Legacy Asset",
                initialValue = 1_000_000.0,
                purchaseYear = 2020,
                category = AssetCategory.ELECTRONICS,
                userId = null
            )
        )

        db.splitBillHistoryDao().insertSplitBillHistory(
            SplitBillHistoryItem(
                id = "legacy-bill-01",
                merchantName = "Legacy Merchant",
                date = "2020-01-01",
                totalAmount = 100_000.0,
                formattedText = "Legacy bill",
                userId = null
            )
        )

        db.feedbackDao().insertFeedback(
            FeedbackItem(
                id = "legacy-feedback-01",
                userName = "Anonymous",
                userEmail = "anon@example.com",
                rating = 5,
                category = "General",
                message = "Legacy feedback",
                userId = null
            )
        )

        // Ambiguous custom category (isSystem = false, userId = null)
        val legacyAmbiguousCatId = db.categoryDao().insertCategory(
            CategoryItem(
                name = "Legacy Ambiguous Category",
                type = TransactionType.EXPENSE,
                isSystem = false,
                userId = null
            )
        )

        // Verify that User A (normal user query) CANNOT see any of these legacy null rows:
        assertTrue(db.walletDao().getAllWallets(userAId).first().none { it.id == legacyWalletId })
        assertNull(db.walletDao().getWalletById(legacyWalletId, userAId))

        assertTrue(db.transactionDao().getAllTransactions(userAId).first().none { it.id == 2001L })
        assertNull(db.transactionDao().getTransactionById(2001L, userAId))

        assertTrue(db.budgetLimitDao().getAllBudgetLimits(userAId).first().none { it.id == "legacy_budget" })
        assertNull(db.budgetLimitDao().getBudgetLimit(userAId, "legacy_budget"))

        assertTrue(db.debtDao().getAllDebts(userAId).first().none { it.id == "legacy-debt-01" })
        assertNull(db.debtDao().getDebtById("legacy-debt-01", userAId))

        assertTrue(db.assetDao().getAllAssets(userAId).first().none { it.id == "legacy-asset-01" })
        assertNull(db.assetDao().getAssetById("legacy-asset-01", userAId))

        assertTrue(db.splitBillHistoryDao().getAllSplitBillHistory(userAId).first().none { it.id == "legacy-bill-01" })
        assertNull(db.splitBillHistoryDao().getSplitBillHistoryById("legacy-bill-01", userAId))

        assertTrue(db.feedbackDao().getFeedbacksByUser(userAId).first().none { it.id == "legacy-feedback-01" })
        assertNull(db.feedbackDao().getFeedbackById("legacy-feedback-01", userAId))

        // Ambiguous category (isSystem = 0, userId = null) must be hidden from normal user queries
        val userACategories = db.categoryDao().getAllCategories(userAId).first()
        assertTrue(userACategories.none { it.id == legacyAmbiguousCatId })
        assertNull(db.categoryDao().getCategoryById(legacyAmbiguousCatId, userAId))
    }

    // =========================================================================
    // DAO-13: User A cannot read User B transactions.
    // =========================================================================
    @Test
    fun dao13_userACannotReadUserBTransactions() = runBlocking {
        val walletBId = db.walletDao().insertWallet(
            Wallet(
                id = 301L,
                name = "B's Wallet",
                type = WalletType.BANK,
                balance = 5000.0,
                userId = userBId
            )
        )

        val txBId = db.transactionDao().insertTransaction(
            TransactionItem(
                id = 3001L,
                title = "B's Confidential Purchase",
                amount = 250.0,
                type = TransactionType.EXPENSE,
                category = "Private",
                walletId = walletBId,
                walletName = "B's Wallet",
                accountMode = AccountMode.PERSONAL.name,
                userId = userBId
            )
        )

        val allTxsForA = db.transactionDao().getAllTransactions(userAId).first()
        val modeTxsForA = db.transactionDao().getTransactionsByMode(userAId, AccountMode.PERSONAL.name).first()
        val walletTxsForA = db.transactionDao().getTransactionsByWallet(userAId, walletBId).first()
        val betweenTxsForA = db.transactionDao().getTransactionsBetween(userAId, 0L, System.currentTimeMillis() + 100000L, AccountMode.PERSONAL.name).first()
        val txByIdForA = db.transactionDao().getTransactionById(txBId, userAId)

        assertTrue(allTxsForA.none { it.id == txBId })
        assertTrue(modeTxsForA.none { it.id == txBId })
        assertTrue(walletTxsForA.isEmpty())
        assertTrue(betweenTxsForA.none { it.id == txBId })
        assertNull(txByIdForA)
    }

    // =========================================================================
    // DAO-14: User A cannot read User B wallets.
    // =========================================================================
    @Test
    fun dao14_userACannotReadUserBWallets() = runBlocking {
        val walletBId = db.walletDao().insertWallet(
            Wallet(
                id = 302L,
                name = "B's Private Savings",
                type = WalletType.BANK,
                balance = 75000.0,
                accountMode = AccountMode.PERSONAL.name,
                userId = userBId
            )
        )

        val allWalletsForA = db.walletDao().getAllWallets(userAId).first()
        val modeWalletsForA = db.walletDao().getWalletsByMode(userAId, AccountMode.PERSONAL.name).first()
        val walletByIdForA = db.walletDao().getWalletById(walletBId, userAId)

        assertTrue(allWalletsForA.none { it.id == walletBId })
        assertTrue(modeWalletsForA.none { it.id == walletBId })
        assertNull(walletByIdForA)
    }

    // =========================================================================
    // DAO-15: User A cannot mutate User B wallet balance.
    // =========================================================================
    @Test
    fun dao15_userACannotMutateUserBWalletBalance() = runBlocking {
        val walletBId = db.walletDao().insertWallet(
            Wallet(
                id = 303L,
                name = "B's Vault",
                type = WalletType.CASH,
                balance = 2_500_000.0,
                accountMode = AccountMode.PERSONAL.name,
                isDefault = true,
                userId = userBId
            )
        )

        // User A tries adjustBalance
        val adjustRows = db.walletDao().adjustBalance(walletBId, userAId, -1_000_000.0)
        assertEquals(0, adjustRows)

        // User A tries setBalance
        val setRows = db.walletDao().setBalance(walletBId, userAId, 0.0)
        assertEquals(0, setRows)

        // User A tries clearDefaultWallet
        db.walletDao().clearDefaultWallet(userAId, AccountMode.PERSONAL.name)

        // User A tries setDefaultWallet on B's wallet
        val defaultRows = db.walletDao().setDefaultWallet(walletBId, userAId)
        assertEquals(0, defaultRows)

        // User A tries deleteWallet
        val deleteRows = db.walletDao().deleteWallet(walletBId, userAId)
        assertEquals(0, deleteRows)

        // Verify B's wallet remains completely intact
        val walletB = db.walletDao().getWalletById(walletBId, userBId)
        assertNotNull(walletB)
        assertEquals(2_500_000.0, walletB?.balance ?: 0.0, 0.001)
        assertTrue(walletB?.isDefault == true)
    }

    // =========================================================================
    // DAO-16: User A cannot update/delete User B transactions.
    // =========================================================================
    @Test
    fun dao16_userACannotUpdateOrDeleteUserBTransactions() = runBlocking {
        val walletBId = db.walletDao().insertWallet(
            Wallet(
                id = 304L,
                name = "B's Wallet",
                type = WalletType.BANK,
                balance = 1000.0,
                userId = userBId
            )
        )

        val txBId = db.transactionDao().insertTransaction(
            TransactionItem(
                id = 3004L,
                title = "B's Original Tx",
                amount = 120.0,
                type = TransactionType.EXPENSE,
                category = "Utilities",
                walletId = walletBId,
                walletName = "B's Wallet",
                userId = userBId
            )
        )

        // User A attempts delete
        val deleteRows = db.transactionDao().deleteTransaction(txBId, userAId)
        assertEquals(0, deleteRows)

        // User A attempts update
        val bTx = db.transactionDao().getTransactionById(txBId, userBId)!!
        val updateRows = db.transactionDao().updateTransaction(bTx.copy(title = "Tampered By A"), userAId)
        assertEquals(0, updateRows)

        // Verify B's transaction unchanged
        val txBCheck = db.transactionDao().getTransactionById(txBId, userBId)
        assertNotNull(txBCheck)
        assertEquals("B's Original Tx", txBCheck?.title)
    }

    // =========================================================================
    // DAO-17: User A cannot update/delete User B debt/assets/budget/split-bill records.
    // =========================================================================
    @Test
    fun dao17_userACannotMutateUserBDebtAssetBudgetOrSplitBill() = runBlocking {
        // Setup B records
        val debtB = DebtItem(id = "debt-b-17", name = "B's Loan", amount = 10_000_000.0, interestRatePercent = 2.0, minMonthlyPayment = 500_000.0, userId = userBId)
        db.debtDao().insertDebt(debtB)

        val assetB = AssetItem(id = "asset-b-17", name = "B's Laptop", initialValue = 15_000_000.0, purchaseYear = 2024, category = AssetCategory.ELECTRONICS, userId = userBId)
        db.assetDao().insertAsset(assetB)

        val budgetB = BudgetLimitItem(id = "budget_b_17", limitAmount = 3_000_000.0, isCategory = true, userId = userBId)
        db.budgetLimitDao().insertBudgetLimit(budgetB)

        val billB = SplitBillHistoryItem(id = "bill-b-17", merchantName = "B's Dinner", date = "2026-03-02", totalAmount = 500_000.0, formattedText = "B's split", userId = userBId)
        db.splitBillHistoryDao().insertSplitBillHistory(billB)

        // User A attempts deletions
        assertEquals(0, db.debtDao().deleteDebt("debt-b-17", userAId))
        assertEquals(0, db.assetDao().deleteAsset("asset-b-17", userAId))
        assertEquals(0, db.budgetLimitDao().deleteBudgetLimit(userAId, "budget_b_17"))
        assertEquals(0, db.splitBillHistoryDao().deleteSplitBillHistory("bill-b-17", userAId))

        // User A attempts updates
        assertEquals(0, db.debtDao().updateDebt(debtB.copy(name = "Tampered Debt"), userAId))
        assertEquals(0, db.assetDao().updateAsset(assetB.copy(name = "Tampered Asset"), userAId))
        assertEquals(0, db.splitBillHistoryDao().updateSplitBillHistory(billB.copy(merchantName = "Tampered Bill"), userAId))

        // User A attempts clears
        db.debtDao().clearAllDebts(userAId)
        db.assetDao().clearAllAssets(userAId)
        db.budgetLimitDao().clearAllBudgetLimits(userAId)
        db.splitBillHistoryDao().clearAllHistory(userAId)

        // Verify all B records still intact
        assertEquals("B's Loan", db.debtDao().getDebtById("debt-b-17", userBId)?.name)
        assertEquals("B's Laptop", db.assetDao().getAssetById("asset-b-17", userBId)?.name)
        assertNotNull(db.budgetLimitDao().getBudgetLimit(userBId, "budget_b_17"))
        assertEquals("B's Dinner", db.splitBillHistoryDao().getSplitBillHistoryById("bill-b-17", userBId)?.merchantName)
    }

    // =========================================================================
    // DAO-18: User A custom categories cannot leak into User B.
    // =========================================================================
    @Test
    fun dao18_userACustomCategoriesCannotLeakIntoUserB() = runBlocking {
        val catAId = db.categoryDao().insertCategory(
            CategoryItem(
                name = "A's Secret Project",
                type = TransactionType.EXPENSE,
                isSystem = false,
                userId = userAId
            )
        )

        val catsForB = db.categoryDao().getAllCategories(userBId).first()
        val expenseCatsForB = db.categoryDao().getCategoriesByType(userBId, TransactionType.EXPENSE).first()
        val catByIdForB = db.categoryDao().getCategoryById(catAId, userBId)

        assertTrue(catsForB.none { it.id == catAId })
        assertTrue(expenseCatsForB.none { it.id == catAId })
        assertNull(catByIdForB)
    }

    // =========================================================================
    // DAO-19: System categories remain accessible as system data but cannot be mutated as user-owned data.
    // =========================================================================
    @Test
    fun dao19_systemCategoriesAccessibleAsSystemData_immutableByUserMutations() = runBlocking {
        val sysCatId = db.categoryDao().insertCategory(
            CategoryItem(
                name = "Kebutuhan Pokok",
                type = TransactionType.EXPENSE,
                isSystem = true,
                userId = null
            )
        )

        // Accessible to both
        val sysCatA = db.categoryDao().getCategoryById(sysCatId, userAId)
        val sysCatB = db.categoryDao().getCategoryById(sysCatId, userBId)
        assertNotNull(sysCatA)
        assertNotNull(sysCatB)
        assertEquals("Kebutuhan Pokok", sysCatA?.name)
        assertEquals("Kebutuhan Pokok", sysCatB?.name)

        // User A mutation rejected
        assertEquals(0, db.categoryDao().deleteCategory(sysCatId, userAId))
        assertEquals(0, db.categoryDao().updateCategory(sysCatA!!.copy(name = "Tampered"), userAId))

        // User B mutation rejected
        assertEquals(0, db.categoryDao().deleteCategory(sysCatId, userBId))
        assertEquals(0, db.categoryDao().updateCategory(sysCatB!!.copy(name = "Tampered"), userBId))

        // Category remains pristine
        val checkSysCat = db.categoryDao().getCategoryById(sysCatId, userAId)
        assertEquals("Kebutuhan Pokok", checkSysCat?.name)
    }

    // =========================================================================
    // DAO-20: Global feedback access is either proven safely restricted OR removed from normal user flow.
    // =========================================================================
    @Test
    fun dao20_feedbackScopedByUser_userCannotDeleteOtherUserFeedback() = runBlocking {
        val feedbackA = FeedbackItem(
            id = "fb-a-20",
            userName = "User A",
            userEmail = "a@example.com",
            rating = 5,
            category = "Idea",
            message = "A's Private Feedback",
            userId = userAId
        )
        val feedbackB = FeedbackItem(
            id = "fb-b-20",
            userName = "User B",
            userEmail = "b@example.com",
            rating = 4,
            category = "Bug",
            message = "B's Private Feedback",
            userId = userBId
        )

        db.feedbackDao().insertFeedback(feedbackA)
        db.feedbackDao().insertFeedback(feedbackB)

        // User-scoped queries only show own feedback
        val aFeedbacks = db.feedbackDao().getFeedbacksByUser(userAId).first()
        val bFeedbacks = db.feedbackDao().getFeedbacksByUser(userBId).first()

        assertEquals(1, aFeedbacks.size)
        assertEquals("fb-a-20", aFeedbacks[0].id)
        assertEquals(1, bFeedbacks.size)
        assertEquals("fb-b-20", bFeedbacks[0].id)

        // User A cannot read B's feedback by ID
        assertNull(db.feedbackDao().getFeedbackById("fb-b-20", userAId))

        // User A cannot delete B's feedback
        val deleteRows = db.feedbackDao().deleteFeedback("fb-b-20", userAId)
        assertEquals(0, deleteRows)

        // User B's feedback remains intact
        assertNotNull(db.feedbackDao().getFeedbackById("fb-b-20", userBId))

        // Global query sees both (used strictly by developer/admin in DevFeedbackViewerScreen)
        val allFeedbacks = db.feedbackDao().getAllFeedbacks().first()
        assertTrue(allFeedbacks.any { it.id == "fb-a-20" })
        assertTrue(allFeedbacks.any { it.id == "fb-b-20" })
    }
}
