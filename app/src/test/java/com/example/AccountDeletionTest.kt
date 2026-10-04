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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccountDeletionTest {

    private lateinit var db: FinSmartDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testWipeAccountDataInTransaction_erasesAllDataAndReseedsOnlyCategories() = runBlocking {
        // 1. Seed data into ALL 9 Room tables
        val userId = db.userDao().insertUser(
            User(
                username = "BudiSantoso",
                email = "budi@example.com",
                passwordHash = PasswordHasher.hashPassword("password123"),
                role = UserRole.FREE_MEMBER
            )
        )
        assertTrue(userId > 0)

        val walletId = db.walletDao().insertWallet(
            Wallet(
                name = "Dompet Pribadi",
                type = WalletType.BANK,
                accountNumber = "5551234567",
                balance = 5000000.0,
                colorHex = "#0B57D0",
                iconName = "bank",
                accountMode = AccountMode.PERSONAL.name,
                isDefault = true
            )
        )
        assertTrue(walletId > 0)

        db.transactionDao().insertTransaction(
            TransactionItem(
                title = "Makan Siang",
                amount = 45000.0,
                type = TransactionType.EXPENSE,
                category = "Kuliner",
                walletId = walletId,
                walletName = "Dompet Pribadi",
                timestamp = System.currentTimeMillis()
            )
        )

        db.categoryDao().insertCategory(
            CategoryItem(
                name = "Kategori Kustom Budi",
                type = TransactionType.EXPENSE,
                iconName = "star",
                colorHex = "#FF0000"
            )
        )

        db.splitBillHistoryDao().insertSplitBillHistory(
            SplitBillHistoryItem(
                merchantName = "Warung Kopi",
                date = "04 Okt 2026",
                totalAmount = 50000.0,
                formattedText = "Detail Split Bill",
                timestamp = System.currentTimeMillis()
            )
        )

        db.budgetLimitDao().insertBudgetLimit(
            BudgetLimitItem(
                id = "total_monthly",
                limitAmount = 5000000.0,
                isCategory = false
            )
        )

        db.feedbackDao().insertFeedback(
            FeedbackItem(
                userName = "Budi",
                userEmail = "budi@example.com",
                rating = 5,
                category = "General",
                message = "Aplikasi sangat membantu!",
                timestamp = System.currentTimeMillis()
            )
        )

        db.debtDao().insertDebt(
            DebtItem(
                name = "Hutang Pinjol Budi",
                amount = 2500000.0,
                interestRatePercent = 15.0,
                minMonthlyPayment = 300000.0,
                category = "Pinjaman Online",
                dueDate = "Tgl 25"
            )
        )

        db.assetDao().insertAsset(
            AssetItem(
                name = "Laptop Asus ROG",
                initialValue = 18000000.0,
                purchaseYear = 2023,
                category = AssetCategory.ELECTRONICS
            )
        )

        // 2. Pre-condition verify: all 9 tables have data
        assertEquals(1, db.userDao().getUserCount())
        assertEquals(1, db.walletDao().getAllWallets().first().size)
        assertEquals(1, db.transactionDao().getAllTransactions().first().size)
        assertEquals(1, db.splitBillHistoryDao().getAllSplitBillHistory().first().size)
        assertEquals(1, db.budgetLimitDao().getAllBudgetLimits().first().size)
        assertEquals(1, db.feedbackDao().getAllFeedbacks().first().size)
        assertEquals(1, db.debtDao().getDebtCount())
        assertEquals(1, db.assetDao().getAssetCount())
        assertEquals(1, db.categoryDao().getCategoryCount())

        // 3. Execute atomic account data erasure
        FinSmartDatabase.wipeAccountDataInTransaction(db)

        // 4. Verify Right to Erasure: ALL 8 user/financial tables are completely empty
        assertEquals("User table must be empty", 0, db.userDao().getUserCount())
        assertEquals("Wallets table must be empty", 0, db.walletDao().getAllWallets().first().size)
        assertEquals("Transactions table must be empty", 0, db.transactionDao().getAllTransactions().first().size)
        assertEquals("Split bills table must be empty", 0, db.splitBillHistoryDao().getAllSplitBillHistory().first().size)
        assertEquals("Budget limits table must be empty", 0, db.budgetLimitDao().getAllBudgetLimits().first().size)
        assertEquals("Feedbacks table must be empty", 0, db.feedbackDao().getAllFeedbacks().first().size)
        assertEquals("Debts table must be empty", 0, db.debtDao().getDebtCount())
        assertEquals("Assets table must be empty", 0, db.assetDao().getAssetCount())

        // 5. Verify Categories: ONLY the 13 generic default categories exist, custom category deleted
        val categories = db.categoryDao().getAllCategories().first()
        assertEquals("Only 13 default categories must be reseeded", 13, categories.size)
        assertFalse("Custom categories must be erased", categories.any { it.name == "Kategori Kustom Budi" })
        assertTrue("Default Makanan & Minuman category must exist", categories.any { it.name == "Makanan & Minuman" })
        assertTrue("Default Gaji Pokok category must exist", categories.any { it.name == "Gaji Pokok" })
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

        // Ensure all categories have valid icons and colors
        categories.forEach { cat ->
            assertTrue(cat.name.isNotBlank())
            assertTrue(cat.iconName.isNotBlank())
            assertTrue(cat.colorHex.startsWith("#"))
        }
    }
}
