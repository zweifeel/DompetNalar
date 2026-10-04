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

import com.example.data.model.AssetCategory
import com.example.data.model.AssetItem
import com.example.data.model.DebtItem
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

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
        assertNotNull(db.debtDao())
        assertNotNull(db.assetDao())
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

        val debts = db.debtDao().getAllDebts().first()
        assertEquals(3, debts.size)

        val assets = db.assetDao().getAllAssets().first()
        assertEquals(3, assets.size)
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
    fun testDebtDaoOperations() = runBlocking {
        val debt = DebtItem(
            id = "debt-test-01",
            name = "Pinjaman Bank Mandiri",
            amount = 10000000.0,
            interestRatePercent = 9.5,
            minMonthlyPayment = 1000000.0,
            category = "Pinjaman Bank",
            dueDate = "Tgl 10 tiap bulan",
            createdAt = 1700000000000L
        )

        // 1. Insert
        db.debtDao().insertDebt(debt)
        var debts = db.debtDao().getAllDebts().first()
        assertEquals(1, debts.size)
        assertEquals("debt-test-01", debts[0].id)
        assertEquals("Pinjaman Bank Mandiri", debts[0].name)
        assertEquals(10000000.0, debts[0].amount, 0.01)
        assertEquals(9.5, debts[0].interestRatePercent, 0.01)
        assertEquals(1000000.0, debts[0].minMonthlyPayment, 0.01)
        assertEquals("Pinjaman Bank", debts[0].category)
        assertEquals("Tgl 10 tiap bulan", debts[0].dueDate)

        // 2. Update
        val updatedDebt = debt.copy(amount = 8000000.0, minMonthlyPayment = 800000.0)
        db.debtDao().updateDebt(updatedDebt)
        debts = db.debtDao().getAllDebts().first()
        assertEquals(1, debts.size)
        assertEquals(8000000.0, debts[0].amount, 0.01)

        // 3. Delete
        db.debtDao().deleteDebt(updatedDebt)
        debts = db.debtDao().getAllDebts().first()
        assertTrue(debts.isEmpty())

        // 4. Clear all
        db.debtDao().insertDebt(debt)
        assertEquals(1, db.debtDao().getDebtCount())
        db.debtDao().clearAllDebts()
        assertEquals(0, db.debtDao().getDebtCount())
    }

    @Test
    fun testAssetDaoOperations() = runBlocking {
        val asset = AssetItem(
            id = "asset-test-01",
            name = "iPhone 15 Pro",
            initialValue = 20000000.0,
            purchaseYear = 2024,
            category = AssetCategory.ELECTRONICS,
            createdAt = 1700000000000L
        )

        // 1. Insert
        db.assetDao().insertAsset(asset)
        var assets = db.assetDao().getAllAssets().first()
        assertEquals(1, assets.size)
        assertEquals("asset-test-01", assets[0].id)
        assertEquals("iPhone 15 Pro", assets[0].name)
        assertEquals(20000000.0, assets[0].initialValue, 0.01)
        assertEquals(2024, assets[0].purchaseYear)
        assertEquals(AssetCategory.ELECTRONICS, assets[0].category)

        // 2. Computed currentValue check (AssetTracker formula)
        assertTrue(assets[0].currentValue <= assets[0].initialValue)

        // 3. Update
        val updatedAsset = asset.copy(initialValue = 22000000.0)
        db.assetDao().updateAsset(updatedAsset)
        assets = db.assetDao().getAllAssets().first()
        assertEquals(1, assets.size)
        assertEquals(22000000.0, assets[0].initialValue, 0.01)

        // 4. Delete
        db.assetDao().deleteAsset(updatedAsset)
        assets = db.assetDao().getAllAssets().first()
        assertTrue(assets.isEmpty())

        // 5. Clear all
        db.assetDao().insertAsset(asset)
        assertEquals(1, db.assetDao().getAssetCount())
        db.assetDao().clearAllAssets()
        assertEquals(0, db.assetDao().getAssetCount())
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
        assertEquals(1, FinSmartDatabase.MIGRATIONS.size)
        assertEquals(5, FinSmartDatabase.MIGRATIONS[0].startVersion)
        assertEquals(6, FinSmartDatabase.MIGRATIONS[0].endVersion)
    }

    @Test
    fun testMigration5To6PreservesDataAndAddsNewTables() {
        val dbName = "migration_test_5_to_6.db"
        context.deleteDatabase(dbName)
        val sqliteDb = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)

        // 1. Create v5 tables
        sqliteDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `wallets` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `accountNumber` TEXT NOT NULL,
                `balance` REAL NOT NULL,
                `colorHex` TEXT NOT NULL,
                `iconName` TEXT NOT NULL,
                `accountMode` TEXT NOT NULL,
                `isDefault` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        sqliteDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `transactions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `type` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `categoryIcon` TEXT NOT NULL,
                `walletId` INTEGER NOT NULL,
                `walletName` TEXT NOT NULL,
                `targetWalletId` INTEGER,
                `targetWalletName` TEXT,
                `timestamp` INTEGER NOT NULL,
                `note` TEXT NOT NULL,
                `tags` TEXT NOT NULL,
                `accountMode` TEXT NOT NULL
            )
            """.trimIndent()
        )
        sqliteDb.version = 5

        // Insert baseline data at v5
        sqliteDb.execSQL("INSERT INTO wallets (id, name, type, accountNumber, balance, colorHex, iconName, accountMode, isDefault, createdAt) VALUES (1, 'Dompet Utama', 'CASH', '123', 250000.0, '#000', 'cash', 'PERSONAL', 1, 1000)")
        sqliteDb.execSQL("INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, targetWalletId, targetWalletName, timestamp, note, tags, accountMode) VALUES (1, 'Belanja', 50000.0, 'EXPENSE', 'Belanja', 'shop', 1, 'Dompet Utama', null, null, 1000, '', '', 'PERSONAL')")

        sqliteDb.close()

        // 2. Open with SupportSQLiteOpenHelper to execute MIGRATION_5_6
        val frameworkFactory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) {
                    if (oldVersion == 5 && newVersion == 6) {
                        FinSmartDatabase.MIGRATION_5_6.migrate(db)
                    }
                }
            })
            .build()
        val helper = frameworkFactory.create(config)
        val upgradedDb = helper.writableDatabase

        // 3. Verify wallets & transactions are intact
        val walletCursor = upgradedDb.query("SELECT id, name, balance FROM wallets WHERE id = 1")
        assertTrue(walletCursor.moveToFirst())
        assertEquals("Dompet Utama", walletCursor.getString(1))
        assertEquals(250000.0, walletCursor.getDouble(2), 0.01)
        walletCursor.close()

        val txCursor = upgradedDb.query("SELECT id, title, amount FROM transactions WHERE id = 1")
        assertTrue(txCursor.moveToFirst())
        assertEquals("Belanja", txCursor.getString(1))
        assertEquals(50000.0, txCursor.getDouble(2), 0.01)
        txCursor.close()

        // 4. Verify debts table exists and can insert/read
        upgradedDb.execSQL("INSERT INTO debts (id, name, amount, interestRatePercent, minMonthlyPayment, category, dueDate, createdAt) VALUES ('debt-1', 'Kartu Kredit', 5000000.0, 2.5, 500000.0, 'Kartu Kredit', 'Tgl 15', 1000)")
        val debtCursor = upgradedDb.query("SELECT id, name, amount FROM debts WHERE id = 'debt-1'")
        assertTrue(debtCursor.moveToFirst())
        assertEquals("Kartu Kredit", debtCursor.getString(1))
        assertEquals(5000000.0, debtCursor.getDouble(2), 0.01)
        debtCursor.close()

        // 5. Verify assets table exists and can insert/read
        upgradedDb.execSQL("INSERT INTO assets (id, name, initialValue, purchaseYear, category, createdAt) VALUES ('asset-1', 'Laptop Asus', 15000000.0, 2024, 'ELECTRONICS', 1000)")
        val assetCursor = upgradedDb.query("SELECT id, name, initialValue, category FROM assets WHERE id = 'asset-1'")
        assertTrue(assetCursor.moveToFirst())
        assertEquals("Laptop Asus", assetCursor.getString(1))
        assertEquals(15000000.0, assetCursor.getDouble(2), 0.01)
        assertEquals("ELECTRONICS", assetCursor.getString(3))
        assetCursor.close()

        upgradedDb.close()
        context.deleteDatabase(dbName)
    }
}
