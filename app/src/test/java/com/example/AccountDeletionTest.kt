package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinSmartDatabase
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
import com.example.util.PasswordHasher
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

/**
 * Stage 3 Account Deletion Security Tests:
 * Verifies that account deletion is strictly user-scoped and never executes global wipe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountDeletionTest {

    private lateinit var db: FinSmartDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            // Seed system categories
            db.categoryDao().insertCategories(FinSmartDatabase.DEFAULT_CATEGORIES)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * ACCOUNT-DEL-01:
     * User A has full financial records; User B has equivalent records.
     * Deleting User A removes User A and cascades only to A-owned records.
     * User B and B-owned records remain completely intact. System categories remain intact.
     */
    @Test
    fun accountDel01_deleteUserA_removesOnlyUserAData_andUserBDataRemainsIntact() = runBlocking {
        // 1. Create User A and User B with real database-generated IDs
        val userAId = db.userDao().insertUser(
            User(username = "UserA", email = "usera@example.com", passwordHash = PasswordHasher.hashPassword("secret123"), role = UserRole.FREE_MEMBER)
        )
        val userBId = db.userDao().insertUser(
            User(username = "UserB", email = "userb@example.com", passwordHash = PasswordHasher.hashPassword("secret456"), role = UserRole.FREE_MEMBER)
        )
        val userA = db.userDao().getUserById(userAId)!!
        val userB = db.userDao().getUserById(userBId)!!

        // 2. Seed User A's data
        val walletAId = db.walletDao().insertWallet(
            Wallet(name = "Wallet A", type = WalletType.BANK, balance = 1000000.0, accountMode = AccountMode.PERSONAL.name, userId = userA.id)
        )
        db.transactionDao().insertTransaction(
            TransactionItem(title = "Tx A", amount = 50000.0, type = TransactionType.EXPENSE, category = "Makanan", walletId = walletAId, walletName = "Wallet A", userId = userA.id)
        )
        db.debtDao().insertDebt(
            DebtItem(id = "debt-a", name = "Debt A", amount = 500000.0, interestRatePercent = 10.0, minMonthlyPayment = 50000.0, category = "Pinjaman", dueDate = "Tgl 10", userId = userA.id)
        )
        db.assetDao().insertAsset(
            AssetItem(id = "asset-a", name = "Asset A", initialValue = 10000000.0, purchaseYear = 2024, category = AssetCategory.ELECTRONICS, userId = userA.id)
        )
        db.budgetLimitDao().insertBudgetLimit(
            BudgetLimitItem(id = "budget-a", limitAmount = 2000000.0, isCategory = false, userId = userA.id)
        )
        db.splitBillHistoryDao().insertSplitBillHistory(
            SplitBillHistoryItem(id = "sb-a", merchantName = "Cafe A", date = "01-01-2026", totalAmount = 75000.0, formattedText = "Txt A", timestamp = 1000L, userId = userA.id)
        )
        db.categoryDao().insertCategory(
            CategoryItem(name = "Custom Cat A", type = TransactionType.EXPENSE, iconName = "star", colorHex = "#FF0000", isSystem = false, userId = userA.id)
        )
        db.feedbackDao().insertFeedback(
            FeedbackItem(userName = "UserA", userEmail = "usera@example.com", rating = 5, category = "UI", message = "Good", userId = userA.id)
        )

        // 3. Seed User B's equivalent data
        val walletBId = db.walletDao().insertWallet(
            Wallet(name = "Wallet B", type = WalletType.CASH, balance = 500000.0, accountMode = AccountMode.PERSONAL.name, userId = userB.id)
        )
        db.transactionDao().insertTransaction(
            TransactionItem(title = "Tx B", amount = 25000.0, type = TransactionType.EXPENSE, category = "Transport", walletId = walletBId, walletName = "Wallet B", userId = userB.id)
        )
        db.debtDao().insertDebt(
            DebtItem(id = "debt-b", name = "Debt B", amount = 300000.0, interestRatePercent = 5.0, minMonthlyPayment = 30000.0, category = "Pinjaman", dueDate = "Tgl 20", userId = userB.id)
        )
        db.assetDao().insertAsset(
            AssetItem(id = "asset-b", name = "Asset B", initialValue = 5000000.0, purchaseYear = 2023, category = AssetCategory.GOLD_INVESTMENT, userId = userB.id)
        )
        db.budgetLimitDao().insertBudgetLimit(
            BudgetLimitItem(id = "budget-b", limitAmount = 1500000.0, isCategory = false, userId = userB.id)
        )
        db.splitBillHistoryDao().insertSplitBillHistory(
            SplitBillHistoryItem(id = "sb-b", merchantName = "Resto B", date = "02-01-2026", totalAmount = 120000.0, formattedText = "Txt B", timestamp = 2000L, userId = userB.id)
        )
        db.categoryDao().insertCategory(
            CategoryItem(name = "Custom Cat B", type = TransactionType.EXPENSE, iconName = "shopping", colorHex = "#00FF00", isSystem = false, userId = userB.id)
        )
        db.feedbackDao().insertFeedback(
            FeedbackItem(userName = "UserB", userEmail = "userb@example.com", rating = 4, category = "Performance", message = "Fast", userId = userB.id)
        )

        // 4. Seed legacy quarantined record (userId = null)
        db.walletDao().insertWallet(
            Wallet(name = "Legacy Vault Wallet", type = WalletType.CASH, balance = 123456.0, accountMode = AccountMode.PERSONAL.name, userId = null)
        )

        // Pre-condition: both users have 1 wallet
        assertEquals(1, db.walletDao().getAllWallets(userA.id).first().size)
        assertEquals(1, db.walletDao().getAllWallets(userB.id).first().size)

        // 5. Delete User A
        FinSmartDatabase.deleteUserAccountInTransaction(db, userA)

        // 6. Verify User A is deleted and child rows cascaded
        assertNull("User A row must be deleted", db.userDao().getUserById(userA.id))
        assertEquals("User A wallets must be deleted", 0, db.walletDao().getAllWallets(userA.id).first().size)
        assertEquals("User A transactions must be deleted", 0, db.transactionDao().getAllTransactions(userA.id).first().size)
        assertEquals("User A debts must be deleted", 0, db.debtDao().getDebtCount(userA.id))
        assertEquals("User A assets must be deleted", 0, db.assetDao().getAssetCount(userA.id))
        assertEquals("User A budgets must be deleted", 0, db.budgetLimitDao().getAllBudgetLimits(userA.id).first().size)
        assertEquals("User A split bills must be deleted", 0, db.splitBillHistoryDao().getAllSplitBillHistory(userA.id).first().size)
        assertEquals("User A custom categories must be deleted", 0, db.categoryDao().getAllCategories(userA.id).first().filter { !it.isSystem }.size)

        // 7. Verify User B remains completely intact
        assertNotNull("User B row must remain intact", db.userDao().getUserById(userB.id))
        val walletsB = db.walletDao().getAllWallets(userB.id).first()
        assertEquals("User B wallet must remain", 1, walletsB.size)
        assertEquals("Wallet B", walletsB[0].name)
        assertEquals(500000.0, walletsB[0].balance, 0.01)

        val txsB = db.transactionDao().getAllTransactions(userB.id).first()
        assertEquals("User B transactions must remain", 1, txsB.size)
        assertEquals("Tx B", txsB[0].title)

        assertEquals("User B debts must remain", 1, db.debtDao().getDebtCount(userB.id))
        assertEquals("User B assets must remain", 1, db.assetDao().getAssetCount(userB.id))
        assertEquals("User B budgets must remain", 1, db.budgetLimitDao().getAllBudgetLimits(userB.id).first().size)
        assertEquals("User B split bills must remain", 1, db.splitBillHistoryDao().getAllSplitBillHistory(userB.id).first().size)
        assertEquals("User B custom category must remain", 1, db.categoryDao().getAllCategories(userB.id).first().filter { !it.isSystem }.size)

        // 8. Verify system categories remain intact
        assertEquals("13 system categories must remain intact", 13, db.categoryDao().getSystemCategories().first().size)

        // 9. Verify legacy NULL-owner rows remain quarantined in table and not claimed by User B
        val totalWalletsInDb = db.walletDao().getWalletCount()
        assertEquals("Total wallets in DB must be 2 (User B's wallet + legacy quarantined wallet)", 2, totalWalletsInDb)
        assertEquals("User B must still see only their 1 wallet", 1, db.walletDao().getAllWallets(userB.id).first().size)
    }

    /**
     * ACCOUNT-DEL-02:
     * After User A deletion, User B can still query and use the database normally.
     */
    @Test
    fun accountDel02_userBCanStillQueryAndUseDatabaseNormallyAfterUserADeletion() = runBlocking {
        val userAId = db.userDao().insertUser(User(username = "UserA2", email = "a2@example.com", role = UserRole.FREE_MEMBER))
        val userBId = db.userDao().insertUser(User(username = "UserB2", email = "b2@example.com", role = UserRole.FREE_MEMBER))
        val userA = db.userDao().getUserById(userAId)!!
        val userB = db.userDao().getUserById(userBId)!!

        val walletBId = db.walletDao().insertWallet(
            Wallet(name = "B Wallet", type = WalletType.BANK, balance = 200000.0, accountMode = AccountMode.PERSONAL.name, userId = userB.id)
        )

        // Delete User A
        FinSmartDatabase.deleteUserAccountInTransaction(db, userA)

        // User B performs normal mutations
        db.walletDao().adjustBalance(walletBId, userB.id, 50000.0)
        db.transactionDao().insertTransaction(
            TransactionItem(title = "B New Tx", amount = 50000.0, type = TransactionType.INCOME, category = "Gaji Pokok", walletId = walletBId, walletName = "B Wallet", userId = userB.id)
        )

        // Verify User B's state updated correctly
        val updatedWallet = db.walletDao().getWalletById(walletBId, userB.id)
        assertNotNull(updatedWallet)
        assertEquals(250000.0, updatedWallet!!.balance, 0.01)

        val txsB = db.transactionDao().getAllTransactions(userB.id).first()
        assertEquals(1, txsB.size)
        assertEquals("B New Tx", txsB[0].title)
    }

    /**
     * ACCOUNT-DEL-03:
     * Deletion of A cannot affect B wallet balance or financial records.
     */
    @Test
    fun accountDel03_deletionOfACannotAffectBWalletBalanceOrFinancialRecords() = runBlocking {
        val userAId = db.userDao().insertUser(User(username = "UserA3", email = "a3@example.com", role = UserRole.FREE_MEMBER))
        val userBId = db.userDao().insertUser(User(username = "UserB3", email = "b3@example.com", role = UserRole.FREE_MEMBER))
        val userA = db.userDao().getUserById(userAId)!!
        val userB = db.userDao().getUserById(userBId)!!

        db.walletDao().insertWallet(
            Wallet(name = "A Wallet", type = WalletType.BANK, balance = 999999.0, accountMode = AccountMode.PERSONAL.name, userId = userA.id)
        )
        val walletBId = db.walletDao().insertWallet(
            Wallet(name = "B Wallet", type = WalletType.BANK, balance = 888888.0, accountMode = AccountMode.PERSONAL.name, userId = userB.id)
        )

        val initialBWallet = db.walletDao().getWalletById(walletBId, userB.id)!!
        assertEquals(888888.0, initialBWallet.balance, 0.01)

        // Delete User A
        FinSmartDatabase.deleteUserAccountInTransaction(db, userA)

        val postDeleteBWallet = db.walletDao().getWalletById(walletBId, userB.id)!!
        assertEquals("User B balance must remain strictly identical", 888888.0, postDeleteBWallet.balance, 0.01)
        assertEquals(initialBWallet.id, postDeleteBWallet.id)
    }

    /**
     * ACCOUNT-DEL-04:
     * Deleting User A does not remove system categories.
     */
    @Test
    fun accountDel04_deletingUserADoesNotRemoveSystemCategories() = runBlocking {
        val userAId = db.userDao().insertUser(User(username = "UserA4", email = "a4@example.com", role = UserRole.FREE_MEMBER))
        val userA = db.userDao().getUserById(userAId)!!

        val initialSystemCount = db.categoryDao().getSystemCategories().first().size
        assertEquals(13, initialSystemCount)

        FinSmartDatabase.deleteUserAccountInTransaction(db, userA)

        val postSystemCount = db.categoryDao().getSystemCategories().first().size
        assertEquals("All 13 system categories must remain intact after user deletion", 13, postSystemCount)
    }

    @Test
    fun testPasswordVerification_forRegisteredUserDeletion() {
        val rawPassword = "ValidPassword123!"
        val hashedPassword = PasswordHasher.hashPassword(rawPassword)

        assertTrue(
            "Correct password must be verified",
            PasswordHasher.verifyPassword(rawPassword, hashedPassword)
        )
        assertFalse(
            "Wrong password must be rejected",
            PasswordHasher.verifyPassword("WrongPassword!", hashedPassword)
        )
        assertFalse(
            "Empty password must be rejected for registered user",
            PasswordHasher.verifyPassword("", hashedPassword)
        )
    }

    @Test
    fun testDefaultCategories_specification() {
        val categories = FinSmartDatabase.DEFAULT_CATEGORIES
        assertEquals(13, categories.size)

        val expenseCategories = categories.filter { it.type == TransactionType.EXPENSE }
        val incomeCategories = categories.filter { it.type == TransactionType.INCOME }

        assertEquals(8, expenseCategories.size)
        assertEquals(5, incomeCategories.size)

        categories.forEach { cat ->
            assertTrue(cat.name.isNotBlank())
            assertTrue(cat.iconName.isNotBlank())
            assertTrue(cat.colorHex.startsWith("#"))
        }
    }
}
