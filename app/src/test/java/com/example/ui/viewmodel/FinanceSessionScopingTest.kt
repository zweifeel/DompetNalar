package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.AssetCategory
import com.example.data.model.AssetItem
import com.example.data.model.BudgetLimitItem
import com.example.data.model.CategoryItem
import com.example.data.model.DebtItem
import com.example.data.model.SplitBillHistoryItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
 * DEF-03 Stage 3 Comprehensive Verification Tests:
 * - Session switching between User A and User B
 * - Flow scoping and cancellation on logout
 * - Unauthenticated mutation rejection
 * - Cross-user data isolation across wallets, transactions, debts, assets, budgets, split bills
 * - Guest mode real database ID generation (no hardcoded 9999L)
 * - Session restoration and invalid session cleanup
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FinanceSessionScopingTest {

    private lateinit var app: Application
    private lateinit var db: FinSmartDatabase
    private lateinit var viewModel: FinanceViewModel

    private lateinit var userA: User
    private lateinit var userB: User

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        SessionManager.initialize(app)
        SessionManager.resetForTesting()

        db = Room.inMemoryDatabaseBuilder(app, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        setDatabaseSingleton(db)

        runBlocking {
            val aId = db.userDao().insertUser(
                User(username = "UserA", email = "usera@example.com", role = UserRole.FREE_MEMBER)
            )
            val bId = db.userDao().insertUser(
                User(username = "UserB", email = "userb@example.com", role = UserRole.FREE_MEMBER)
            )
            userA = db.userDao().getUserById(aId)!!
            userB = db.userDao().getUserById(bId)!!
        }

        viewModel = FinanceViewModel(app)
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        SessionManager.resetForTesting()
        setDatabaseSingleton(null)
        db.close()
        Dispatchers.resetMain()
    }

    private fun setDatabaseSingleton(instance: FinSmartDatabase?) {
        val field = FinSmartDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, instance)
    }

    @Test
    fun testUserIsolation_UserALogin_seesOnlyUserAData() = runBlocking {
        // 1. Seed data for User A
        val walletAId = db.walletDao().insertWallet(
            Wallet(name = "Wallet A", type = WalletType.BANK, balance = 1000000.0, accountMode = AccountMode.PERSONAL.name, userId = userA.id)
        )
        db.transactionDao().insertTransaction(
            TransactionItem(title = "Tx A", amount = 50000.0, type = TransactionType.EXPENSE, category = "Makanan", walletId = walletAId, walletName = "Wallet A", userId = userA.id)
        )
        db.debtDao().insertDebt(
            DebtItem(id = "debt-a", name = "Debt A", amount = 200000.0, interestRatePercent = 5.0, minMonthlyPayment = 10000.0, category = "Pinjaman", dueDate = "Tgl 1", userId = userA.id)
        )
        db.assetDao().insertAsset(
            AssetItem(id = "asset-a", name = "Asset A", initialValue = 500000.0, purchaseYear = 2025, category = AssetCategory.ELECTRONICS, userId = userA.id)
        )
        db.budgetLimitDao().insertBudgetLimit(
            BudgetLimitItem(id = "total_monthly", limitAmount = 2000000.0, isCategory = false, userId = userA.id)
        )
        db.splitBillHistoryDao().insertSplitBillHistory(
            SplitBillHistoryItem(id = "sb-a", merchantName = "Resto A", date = "01-01-2026", totalAmount = 100000.0, formattedText = "Txt A", timestamp = 1000L, userId = userA.id)
        )

        // 2. Seed distinct data for User B
        val walletBId = db.walletDao().insertWallet(
            Wallet(name = "Wallet B", type = WalletType.CASH, balance = 500000.0, accountMode = AccountMode.PERSONAL.name, userId = userB.id)
        )
        db.transactionDao().insertTransaction(
            TransactionItem(title = "Tx B", amount = 20000.0, type = TransactionType.EXPENSE, category = "Transportasi", walletId = walletBId, walletName = "Wallet B", userId = userB.id)
        )

        // 3. Login as User A
        SessionManager.login(userA)

        // 4. Assert User A sees only A's data
        val walletsA = viewModel.wallets.filter { it.isNotEmpty() }.first()
        assertEquals(1, walletsA.size)
        assertEquals("Wallet A", walletsA[0].name)

        val txsA = viewModel.allTransactions.filter { it.isNotEmpty() }.first()
        assertEquals(1, txsA.size)
        assertEquals("Tx A", txsA[0].title)

        val debtsA = viewModel.debts.filter { it.isNotEmpty() }.first()
        assertEquals(1, debtsA.size)
        assertEquals("Debt A", debtsA[0].name)

        val assetsA = viewModel.assets.filter { it.isNotEmpty() }.first()
        assertEquals(1, assetsA.size)
        assertEquals("Asset A", assetsA[0].name)

        val budgetsA = viewModel.budgetLimits.filter { it.isNotEmpty() }.first()
        assertEquals(1, budgetsA.size)
        assertEquals(2000000.0, budgetsA[0].limitAmount, 0.01)

        val splitBillsA = viewModel.splitBillHistory.filter { it.isNotEmpty() }.first()
        assertEquals(1, splitBillsA.size)
        assertEquals("Resto A", splitBillsA[0].merchantName)
    }

    @Test
    fun testUserIsolation_Logout_immediatelyEmitsEmptyListAcrossAllFlows() = runBlocking {
        // Seed and login
        db.walletDao().insertWallet(
            Wallet(name = "Wallet A", type = WalletType.BANK, balance = 1000000.0, accountMode = AccountMode.PERSONAL.name, userId = userA.id)
        )
        SessionManager.login(userA)
        assertEquals(1, viewModel.wallets.filter { it.isNotEmpty() }.first().size)

        // Logout
        SessionManager.logout()

        // Assert all flows immediately emit empty list
        assertTrue(viewModel.wallets.filter { it.isEmpty() }.first().isEmpty())
        assertTrue(viewModel.allWallets.filter { it.isEmpty() }.first().isEmpty())
        assertTrue(viewModel.allTransactions.filter { it.isEmpty() }.first().isEmpty())
        assertTrue(viewModel.debts.filter { it.isEmpty() }.first().isEmpty())
        assertTrue(viewModel.assets.filter { it.isEmpty() }.first().isEmpty())
        assertTrue(viewModel.budgetLimits.filter { it.isEmpty() }.first().isEmpty())
        assertTrue(viewModel.splitBillHistory.filter { it.isEmpty() }.first().isEmpty())
    }

    @Test
    fun testUserIsolation_AtoBtoASwitching_noDataLeakage() = runBlocking {
        // Seed User A data
        db.walletDao().insertWallet(
            Wallet(name = "Wallet User A", type = WalletType.BANK, balance = 1000.0, accountMode = AccountMode.PERSONAL.name, userId = userA.id)
        )
        // Seed User B data
        db.walletDao().insertWallet(
            Wallet(name = "Wallet User B", type = WalletType.CASH, balance = 2000.0, accountMode = AccountMode.PERSONAL.name, userId = userB.id)
        )

        // Login A
        SessionManager.login(userA)
        var wallets = viewModel.wallets.filter { it.any { w -> w.userId == userA.id } }.first()
        assertEquals(1, wallets.size)
        assertEquals("Wallet User A", wallets[0].name)

        // Switch to B
        SessionManager.login(userB)
        wallets = viewModel.wallets.filter { it.any { w -> w.userId == userB.id } }.first()
        assertEquals(1, wallets.size)
        assertEquals("Wallet User B", wallets[0].name)

        // Switch back to A
        SessionManager.login(userA)
        wallets = viewModel.wallets.filter { it.any { w -> w.userId == userA.id } }.first()
        assertEquals(1, wallets.size)
        assertEquals("Wallet User A", wallets[0].name)
    }

    @Test
    fun testUnauthenticatedMutation_isSafelyRejectedWithoutDatabaseWrite() = runBlocking {
        // Ensure unauthenticated
        SessionManager.logout()
        assertFalse(SessionManager.isAuthenticated())

        // Attempt mutation through ViewModel
        viewModel.saveWallet(
            name = "Illegal Wallet",
            type = WalletType.BANK,
            accountNumber = "00000",
            initialBalance = 500000.0,
            colorHex = "#000000",
            iconName = "bank"
        )

        viewModel.insertCustomCategory(
            name = "Illegal Category",
            type = TransactionType.EXPENSE
        )

        // Verify zero items created in DB for both users
        assertEquals(0, db.walletDao().getAllWallets(userA.id).first().size)
        assertEquals(0, db.walletDao().getAllWallets(userB.id).first().size)
        assertEquals(0, db.categoryDao().getAllCategories(userA.id).first().filter { !it.isSystem }.size)
        assertEquals(0, db.categoryDao().getAllCategories(userB.id).first().filter { !it.isSystem }.size)
    }

    @Test
    fun testCrossUserMutation_failsSafelyAtDaoLevel() = runBlocking {
        // Seed wallet owned by User A
        val walletAId = db.walletDao().insertWallet(
            Wallet(name = "Wallet A", type = WalletType.BANK, balance = 100000.0, accountMode = AccountMode.PERSONAL.name, userId = userA.id)
        )

        // User B attempts to adjust balance of User A's wallet
        val updatedRows = db.walletDao().adjustBalance(walletAId, userB.id, 50000.0)
        assertEquals("Mutation across user boundary must update 0 rows", 0, updatedRows)

        // Verify User A's wallet balance remains completely untouched
        val walletA = db.walletDao().getWalletById(walletAId, userA.id)
        assertNotNull(walletA)
        assertEquals(100000.0, walletA!!.balance, 0.01)

        // User B attempts to delete User A's wallet
        val deletedRows = db.walletDao().deleteWalletById(walletAId, userB.id)
        assertEquals("Deletion across user boundary must delete 0 rows", 0, deletedRows)
        assertNotNull(db.walletDao().getWalletById(walletAId, userA.id))
    }

    @Test
    fun testGuestSession_createsRealDatabaseRowWithGeneratedId() = runBlocking {
        val guestUser = SessionManager.startGuestSession(db.userDao())

        assertTrue("Guest user id must be positive real database ID", guestUser.id > 0L)
        assertTrue("isGuest flag must be true", guestUser.isGuest)
        assertTrue("SessionManager must reflect guest user as active", SessionManager.isAuthenticated())
        assertEquals(guestUser.id, SessionManager.getActiveUserId())

        // Verify genuine row in DB
        val dbRow = db.userDao().getUserById(guestUser.id)
        assertNotNull("Guest must exist as genuine DB row", dbRow)
        assertTrue("Guest username must indicate guest", dbRow?.username?.startsWith("Tamu") == true)
    }

    @Test
    fun testSessionRestore_withValidUser_restoresSuccessfully() = runBlocking {
        SessionManager.login(userA)
        val initialId = SessionManager.getActiveUserId()

        // Reset memory state but keep prefs
        SessionManager.resetForTesting()
        assertNull(SessionManager.getActiveUserId())

        val restored = SessionManager.restoreSession(db.userDao())
        assertTrue("Restoration must succeed for existing user", restored)
        assertEquals(initialId, SessionManager.getActiveUserId())
    }

    @Test
    fun testSessionRestore_withDeletedUser_clearsSessionSafely() = runBlocking {
        SessionManager.login(userA)

        // Delete User A from database
        db.userDao().deleteUser(userA)

        // Reset memory state
        SessionManager.resetForTesting()

        val restored = SessionManager.restoreSession(db.userDao())
        assertFalse("Restoration must fail for deleted user", restored)
        assertNull("Active user must be null after failed restore", SessionManager.getActiveUserId())
    }
}
