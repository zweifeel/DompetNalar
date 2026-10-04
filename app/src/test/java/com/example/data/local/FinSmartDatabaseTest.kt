package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AccountMode
import com.example.data.model.BudgetLimitItem
import com.example.data.model.CategoryItem
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FinSmartDatabaseTest {

    private lateinit var db: FinSmartDatabase
    private lateinit var context: Context

    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testAllDaosAccessible() {
        assertNotNull(db.walletDao())
        assertNotNull(db.transactionDao())
        assertNotNull(db.categoryDao())
        assertNotNull(db.userDao())
        assertNotNull(db.splitBillHistoryDao())
        assertNotNull(db.budgetLimitDao())
        assertNotNull(db.feedbackDao())
    }

    @Test
    fun testPopulateInitialData() = runBlocking {
        FinSmartDatabase.populateInitialData(db)

        val wallets = db.walletDao().getAllWallets().first()
        assertEquals(4, wallets.size)

        val categories = db.categoryDao().getAllCategories().first()
        assertEquals(13, categories.size)

        val users = db.userDao().getAllUsers().first()
        assertEquals(1, users.size)
        assertEquals("Lukas", users[0].username)
        assertEquals(UserRole.MASTER_DEV, users[0].role)
    }

    @Test
    fun testWalletAndTransactionOperations() = runBlocking {
        val wallet = Wallet(
            id = 100L,
            name = "Test Wallet",
            type = WalletType.BANK,
            accountNumber = "111222333",
            balance = 500000.0,
            colorHex = "#0B57D0",
            iconName = "bank",
            accountMode = AccountMode.PERSONAL.name,
            isDefault = true
        )
        db.walletDao().insertWallet(wallet)

        val retrievedWallet = db.walletDao().getWalletById(100L)
        assertNotNull(retrievedWallet)
        assertEquals("Test Wallet", retrievedWallet?.name)
        assertEquals(500000.0, retrievedWallet?.balance ?: 0.0, 0.01)

        val tx = TransactionItem(
            id = 200L,
            title = "Makan Siang",
            amount = 45000.0,
            type = TransactionType.EXPENSE,
            category = "Makanan",
            categoryIcon = "restaurant",
            walletId = 100L,
            walletName = "Test Wallet",
            timestamp = System.currentTimeMillis(),
            note = "Nasi Padang",
            tags = "#makan",
            accountMode = AccountMode.PERSONAL.name
        )
        db.transactionDao().insertTransaction(tx)

        val transactions = db.transactionDao().getAllTransactions().first()
        assertEquals(1, transactions.size)
        assertEquals("Makan Siang", transactions[0].title)
        assertEquals(45000.0, transactions[0].amount, 0.01)
    }

    @Test
    fun testBudgetLimitAndSplitBillOperations() = runBlocking {
        val limit = BudgetLimitItem(
            id = "total_monthly",
            limitAmount = 3000000.0,
            isCategory = false
        )
        db.budgetLimitDao().insertBudgetLimit(limit)

        val limits = db.budgetLimitDao().getAllBudgetLimits().first()
        assertEquals(1, limits.size)
        assertEquals("total_monthly", limits[0].id)
        assertEquals(3000000.0, limits[0].limitAmount, 0.01)

        val splitBill = SplitBillHistoryItem(
            id = "bill-001",
            merchantName = "Cafe Senja",
            date = "04-10-2026",
            totalAmount = 150000.0,
            formattedText = "Total: 150000",
            timestamp = System.currentTimeMillis()
        )
        db.splitBillHistoryDao().insertSplitBillHistory(splitBill)

        val history = db.splitBillHistoryDao().getAllSplitBillHistory().first()
        assertEquals(1, history.size)
        assertEquals("Cafe Senja", history[0].merchantName)
    }

    @Test
    fun testFeedbackOperations() = runBlocking {
        val feedback = FeedbackItem(
            id = "fb-001",
            userName = "Tester",
            userEmail = "tester@example.com",
            rating = 5,
            category = "Fitur",
            message = "Sangat bagus!",
            timestamp = System.currentTimeMillis()
        )
        db.feedbackDao().insertFeedback(feedback)

        val feedbacks = db.feedbackDao().getAllFeedbacks().first()
        assertEquals(1, feedbacks.size)
        assertEquals("Sangat bagus!", feedbacks[0].message)
    }

    @Test
    fun testMigrationRegistryDefined() {
        assertNotNull(FinSmartDatabase.MIGRATIONS)
        assertTrue(FinSmartDatabase.MIGRATIONS.isEmpty())
    }
}
