package com.example.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration6To7Test {

    private lateinit var context: Context
    private val dbName = "migration_test_6_to_7.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    /**
     * Builds a v6 database exactly per 6.json schema with realistic multi-user and legacy data.
     */
    private fun createV6Database(): SupportSQLiteDatabase {
        val sqliteDb = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)

        sqliteDb.execSQL("""
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
        """.trimIndent())

        sqliteDb.execSQL("""
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
        """.trimIndent())

        sqliteDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `categories` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `iconName` TEXT NOT NULL,
                `colorHex` TEXT NOT NULL
            )
        """.trimIndent())

        sqliteDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `users` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `username` TEXT NOT NULL,
                `email` TEXT NOT NULL,
                `passwordHash` TEXT NOT NULL,
                `role` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `trialStartDate` INTEGER NOT NULL,
                `subscriptionExpiresAt` INTEGER,
                `isApproved` INTEGER NOT NULL,
                `isGuest` INTEGER NOT NULL
            )
        """.trimIndent())

        sqliteDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `split_bill_history` (
                `id` TEXT NOT NULL,
                `merchantName` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `totalAmount` REAL NOT NULL,
                `formattedText` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        sqliteDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `budget_limits` (
                `id` TEXT NOT NULL,
                `limitAmount` REAL NOT NULL,
                `isCategory` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        sqliteDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `feedbacks` (
                `id` TEXT NOT NULL,
                `userName` TEXT NOT NULL,
                `userEmail` TEXT NOT NULL,
                `rating` INTEGER NOT NULL,
                `category` TEXT NOT NULL,
                `message` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        sqliteDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `debts` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `interestRatePercent` REAL NOT NULL,
                `minMonthlyPayment` REAL NOT NULL,
                `category` TEXT NOT NULL,
                `dueDate` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        sqliteDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `assets` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `initialValue` REAL NOT NULL,
                `purchaseYear` INTEGER NOT NULL,
                `category` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        sqliteDb.version = 6
        sqliteDb.close()

        val frameworkFactory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        return frameworkFactory.create(config).writableDatabase
    }

    private fun upgradeToV7(): SupportSQLiteDatabase {
        val frameworkFactory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    if (oldVersion == 6 && newVersion == 7) {
                        Migration6To7.migrate(db)
                    }
                }
            })
            .build()
        return frameworkFactory.create(config).writableDatabase
    }

    /**
     * MIGRATION-01:
     * Representative v6 database with wallets, transactions, budget, debts, assets,
     * split bill, categories, feedback, and multiple users.
     * Run migration: verify no data loss, legacy financial ownership remains NULL, users intact.
     */
    @Test
    fun testMigration01_representativeV6DatabaseMigratesWithoutDataLossAndPreservesNullOwnership() {
        val v6Db = createV6Database()

        // 1. Seed Users (multiple users)
        v6Db.execSQL("INSERT INTO users (id, username, email, passwordHash, role, createdAt, trialStartDate, subscriptionExpiresAt, isApproved, isGuest) VALUES (1, 'Lukas', 'lukas@example.com', 'hash1', 'MASTER_DEV', 1000, 1000, NULL, 1, 0)")
        v6Db.execSQL("INSERT INTO users (id, username, email, passwordHash, role, createdAt, trialStartDate, subscriptionExpiresAt, isApproved, isGuest) VALUES (2, 'Alice', 'alice@example.com', 'hash2', 'FREE_MEMBER', 2000, 2000, NULL, 1, 0)")
        v6Db.execSQL("INSERT INTO users (id, username, email, passwordHash, role, createdAt, trialStartDate, subscriptionExpiresAt, isApproved, isGuest) VALUES (3, 'BobDuplicateEmail', 'duplicate@example.com', 'hash3', 'FREE_MEMBER', 3000, 3000, NULL, 1, 0)")
        v6Db.execSQL("INSERT INTO users (id, username, email, passwordHash, role, createdAt, trialStartDate, subscriptionExpiresAt, isApproved, isGuest) VALUES (4, 'CharlieDuplicateEmail', 'DUPLICATE@example.com', 'hash4', 'FREE_MEMBER', 4000, 4000, NULL, 1, 0)")

        // 2. Seed Wallets
        v6Db.execSQL("INSERT INTO wallets (id, name, type, accountNumber, balance, colorHex, iconName, accountMode, isDefault, createdAt) VALUES (10, 'Dompet Utama', 'BANK', '12345', 5000000.0, '#0B57D0', 'bank', 'PERSONAL', 1, 1000)")
        v6Db.execSQL("INSERT INTO wallets (id, name, type, accountNumber, balance, colorHex, iconName, accountMode, isDefault, createdAt) VALUES (20, 'GoPay', 'EWALLET', '08123', 250000.0, '#00897B', 'gopay', 'PERSONAL', 0, 1100)")

        // 3. Seed Transactions
        v6Db.execSQL("INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, targetWalletId, targetWalletName, timestamp, note, tags, accountMode) VALUES (101, 'Makan Siang', 45000.0, 'EXPENSE', 'Makanan & Minuman', 'restaurant', 10, 'Dompet Utama', NULL, NULL, 1000, 'Nasi Padang', '#makan', 'PERSONAL')")
        v6Db.execSQL("INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, targetWalletId, targetWalletName, timestamp, note, tags, accountMode) VALUES (102, 'Transfer Saldo', 100000.0, 'TRANSFER', 'Transfer', 'swap_horiz', 10, 'Dompet Utama', 20, 'GoPay', 2000, 'Topup', '#transfer', 'PERSONAL')")

        // 4. Seed Budget Limits
        v6Db.execSQL("INSERT INTO budget_limits (id, limitAmount, isCategory) VALUES ('total_monthly', 4000000.0, 0)")
        v6Db.execSQL("INSERT INTO budget_limits (id, limitAmount, isCategory) VALUES ('Makanan & Minuman', 1500000.0, 1)")

        // 5. Seed Debts
        v6Db.execSQL("INSERT INTO debts (id, name, amount, interestRatePercent, minMonthlyPayment, category, dueDate, createdAt) VALUES ('debt-01', 'Kartu Kredit BCA', 15000000.0, 21.0, 1500000.0, 'Kartu Kredit', 'Tgl 15 tiap bulan', 1000)")

        // 6. Seed Assets
        v6Db.execSQL("INSERT INTO assets (id, name, initialValue, purchaseYear, category, createdAt) VALUES ('asset-01', 'MacBook Pro', 24000000.0, 2024, 'ELECTRONICS', 1000)")

        // 7. Seed Split Bill
        v6Db.execSQL("INSERT INTO split_bill_history (id, merchantName, date, totalAmount, formattedText, timestamp) VALUES ('bill-01', 'Cafe Senja', '06-10-2026', 150000.0, 'Split Bill Text', 1000)")

        // 8. Seed Categories (Legacy ambiguous categories)
        v6Db.execSQL("INSERT INTO categories (id, name, type, iconName, colorHex) VALUES (1, 'Kategori Kustom Lama', 'EXPENSE', 'star', '#FF0000')")
        v6Db.execSQL("INSERT INTO categories (id, name, type, iconName, colorHex) VALUES (2, 'Makanan & Minuman', 'EXPENSE', 'restaurant', '#EF4444')") // Legacy name collision

        // 9. Seed Feedbacks
        // fb-1 has exact unique email match with user 2 (Alice)
        v6Db.execSQL("INSERT INTO feedbacks (id, userName, userEmail, rating, category, message, timestamp) VALUES ('fb-1', 'Alice User', 'alice@example.com', 5, 'Fitur', 'Bagus sekali', 1000)")
        // fb-2 has email with multiple matching users (duplicate@example.com) -> should remain NULL
        v6Db.execSQL("INSERT INTO feedbacks (id, userName, userEmail, rating, category, message, timestamp) VALUES ('fb-2', 'Multi User', 'duplicate@example.com', 4, 'General', 'Feedback ambigu', 2000)")
        // fb-3 has unmapped email -> should remain NULL
        v6Db.execSQL("INSERT INTO feedbacks (id, userName, userEmail, rating, category, message, timestamp) VALUES ('fb-3', 'Anon User', 'unknown@example.com', 3, 'Bug', 'Bug report', 3000)")

        v6Db.close()

        // RUN MIGRATION 6 -> 7
        val v7Db = upgradeToV7()

        // VERIFY USERS INTACT
        val userCursor = v7Db.query("SELECT id, username, email FROM users ORDER BY id ASC")
        assertEquals(4, userCursor.count)
        assertTrue(userCursor.moveToFirst())
        assertEquals(1L, userCursor.getLong(0))
        assertEquals("Lukas", userCursor.getString(1))
        userCursor.close()

        // VERIFY WALLETS (ALL COLUMNS)
        val walletCursor = v7Db.query("SELECT id, name, type, accountNumber, balance, colorHex, iconName, accountMode, isDefault, createdAt, userId FROM wallets ORDER BY id ASC")
        assertEquals(2, walletCursor.count)
        assertTrue(walletCursor.moveToFirst())
        assertEquals(10L, walletCursor.getLong(0))
        assertEquals("Dompet Utama", walletCursor.getString(1))
        assertEquals("BANK", walletCursor.getString(2))
        assertEquals("12345", walletCursor.getString(3))
        assertEquals(5000000.0, walletCursor.getDouble(4), 0.01)
        assertEquals("#0B57D0", walletCursor.getString(5))
        assertEquals("bank", walletCursor.getString(6))
        assertEquals("PERSONAL", walletCursor.getString(7))
        assertEquals(1, walletCursor.getInt(8))
        assertEquals(1000L, walletCursor.getLong(9))
        assertTrue("Legacy wallet userId MUST be NULL", walletCursor.isNull(10))
        walletCursor.close()

        // VERIFY TRANSACTIONS (ALL COLUMNS)
        val txCursor = v7Db.query("SELECT id, title, amount, type, category, categoryIcon, walletId, walletName, targetWalletId, targetWalletName, timestamp, note, tags, accountMode, userId FROM transactions ORDER BY id ASC")
        assertEquals(2, txCursor.count)
        assertTrue(txCursor.moveToFirst())
        assertEquals(101L, txCursor.getLong(0))
        assertEquals("Makan Siang", txCursor.getString(1))
        assertEquals(45000.0, txCursor.getDouble(2), 0.01)
        assertEquals("EXPENSE", txCursor.getString(3))
        assertEquals("Makanan & Minuman", txCursor.getString(4))
        assertEquals("restaurant", txCursor.getString(5))
        assertEquals(10L, txCursor.getLong(6))
        assertEquals("Dompet Utama", txCursor.getString(7))
        assertTrue(txCursor.isNull(8))
        assertTrue(txCursor.isNull(9))
        assertEquals(1000L, txCursor.getLong(10))
        assertEquals("Nasi Padang", txCursor.getString(11))
        assertEquals("#makan", txCursor.getString(12))
        assertEquals("PERSONAL", txCursor.getString(13))
        assertTrue("Legacy transaction userId MUST be NULL", txCursor.isNull(14))

        assertTrue(txCursor.moveToNext())
        assertEquals(102L, txCursor.getLong(0))
        assertEquals("Transfer Saldo", txCursor.getString(1))
        assertEquals(100000.0, txCursor.getDouble(2), 0.01)
        assertEquals("TRANSFER", txCursor.getString(3))
        assertEquals("Transfer", txCursor.getString(4))
        assertEquals("swap_horiz", txCursor.getString(5))
        assertEquals(10L, txCursor.getLong(6))
        assertEquals("Dompet Utama", txCursor.getString(7))
        assertEquals(20L, txCursor.getLong(8))
        assertEquals("GoPay", txCursor.getString(9))
        assertEquals(2000L, txCursor.getLong(10))
        assertEquals("Topup", txCursor.getString(11))
        assertEquals("#transfer", txCursor.getString(12))
        assertEquals("PERSONAL", txCursor.getString(13))
        assertTrue("Legacy transaction userId MUST be NULL", txCursor.isNull(14))
        txCursor.close()

        // VERIFY BUDGET LIMITS (ALL COLUMNS)
        val budgetCursor = v7Db.query("SELECT localId, categoryOrTotalId, limitAmount, isCategory, userId FROM budget_limits ORDER BY localId ASC")
        assertEquals(2, budgetCursor.count)
        assertTrue(budgetCursor.moveToFirst())
        assertTrue("Surrogate localId generated", budgetCursor.getLong(0) > 0)
        assertEquals("total_monthly", budgetCursor.getString(1))
        assertEquals(4000000.0, budgetCursor.getDouble(2), 0.01)
        assertEquals(0, budgetCursor.getInt(3))
        assertTrue("Legacy budget userId MUST be NULL", budgetCursor.isNull(4))

        assertTrue(budgetCursor.moveToNext())
        assertTrue("Surrogate localId generated", budgetCursor.getLong(0) > 0)
        assertEquals("Makanan & Minuman", budgetCursor.getString(1))
        assertEquals(1500000.0, budgetCursor.getDouble(2), 0.01)
        assertEquals(1, budgetCursor.getInt(3))
        assertTrue("Legacy budget userId MUST be NULL", budgetCursor.isNull(4))
        budgetCursor.close()

        // VERIFY DEBTS (ALL COLUMNS)
        val debtCursor = v7Db.query("SELECT id, name, amount, interestRatePercent, minMonthlyPayment, category, dueDate, createdAt, userId FROM debts WHERE id = 'debt-01'")
        assertTrue(debtCursor.moveToFirst())
        assertEquals("debt-01", debtCursor.getString(0))
        assertEquals("Kartu Kredit BCA", debtCursor.getString(1))
        assertEquals(15000000.0, debtCursor.getDouble(2), 0.01)
        assertEquals(21.0, debtCursor.getDouble(3), 0.01)
        assertEquals(1500000.0, debtCursor.getDouble(4), 0.01)
        assertEquals("Kartu Kredit", debtCursor.getString(5))
        assertEquals("Tgl 15 tiap bulan", debtCursor.getString(6))
        assertEquals(1000L, debtCursor.getLong(7))
        assertTrue("Legacy debt userId MUST be NULL", debtCursor.isNull(8))
        debtCursor.close()

        // VERIFY ASSETS (ALL COLUMNS)
        val assetCursor = v7Db.query("SELECT id, name, initialValue, purchaseYear, category, createdAt, userId FROM assets WHERE id = 'asset-01'")
        assertTrue(assetCursor.moveToFirst())
        assertEquals("asset-01", assetCursor.getString(0))
        assertEquals("MacBook Pro", assetCursor.getString(1))
        assertEquals(24000000.0, assetCursor.getDouble(2), 0.01)
        assertEquals(2024, assetCursor.getInt(3))
        assertEquals("ELECTRONICS", assetCursor.getString(4))
        assertEquals(1000L, assetCursor.getLong(5))
        assertTrue("Legacy asset userId MUST be NULL", assetCursor.isNull(6))
        assetCursor.close()

        // VERIFY SPLIT BILL (ALL COLUMNS)
        val splitCursor = v7Db.query("SELECT id, merchantName, date, totalAmount, formattedText, timestamp, userId FROM split_bill_history WHERE id = 'bill-01'")
        assertTrue(splitCursor.moveToFirst())
        assertEquals("bill-01", splitCursor.getString(0))
        assertEquals("Cafe Senja", splitCursor.getString(1))
        assertEquals("06-10-2026", splitCursor.getString(2))
        assertEquals(150000.0, splitCursor.getDouble(3), 0.01)
        assertEquals("Split Bill Text", splitCursor.getString(4))
        assertEquals(1000L, splitCursor.getLong(5))
        assertTrue("Legacy split bill userId MUST be NULL", splitCursor.isNull(6))
        splitCursor.close()

        // VERIFY FEEDBACK OWNERSHIP RULE & VALUES
        // fb-1 matches Alice exactly (userId = 2)
        val fb1Cursor = v7Db.query("SELECT id, userName, userEmail, rating, category, message, timestamp, userId FROM feedbacks WHERE id = 'fb-1'")
        assertTrue(fb1Cursor.moveToFirst())
        assertEquals("fb-1", fb1Cursor.getString(0))
        assertEquals("Alice User", fb1Cursor.getString(1))
        assertEquals("alice@example.com", fb1Cursor.getString(2))
        assertEquals(5, fb1Cursor.getInt(3))
        assertEquals("Fitur", fb1Cursor.getString(4))
        assertEquals("Bagus sekali", fb1Cursor.getString(5))
        assertEquals(1000L, fb1Cursor.getLong(6))
        assertEquals(2L, fb1Cursor.getLong(7))
        fb1Cursor.close()

        // fb-2 has duplicate emails in users -> MUST be NULL
        val fb2Cursor = v7Db.query("SELECT id, userName, userEmail, rating, category, message, timestamp, userId FROM feedbacks WHERE id = 'fb-2'")
        assertTrue(fb2Cursor.moveToFirst())
        assertEquals("Multi User", fb2Cursor.getString(1))
        assertTrue("Ambiguous feedback email MUST remain userId = NULL", fb2Cursor.isNull(7))
        fb2Cursor.close()

        // fb-3 has unknown email -> MUST be NULL
        val fb3Cursor = v7Db.query("SELECT id, userName, userEmail, rating, category, message, timestamp, userId FROM feedbacks WHERE id = 'fb-3'")
        assertTrue(fb3Cursor.moveToFirst())
        assertEquals("Anon User", fb3Cursor.getString(1))
        assertTrue("Unmatched feedback email MUST remain userId = NULL", fb3Cursor.isNull(7))
        fb3Cursor.close()

        v7Db.close()
    }

    /**
     * MIGRATION-02:
     * Verify physical SQLite Foreign Keys on all 8 child tables:
     * PRAGMA foreign_key_list(<table_name>);
     * Check references table 'users', to column 'id', on_delete 'CASCADE'.
     */
    @Test
    fun testMigration02_verifyPhysicalForeignKeysInSQLite() {
        val v6Db = createV6Database()
        v6Db.close()
        val v7Db = upgradeToV7()

        val tablesWithFk = listOf(
            "wallets",
            "transactions",
            "categories",
            "budget_limits",
            "feedbacks",
            "debts",
            "assets",
            "split_bill_history"
        )

        for (table in tablesWithFk) {
            val cursor = v7Db.query("PRAGMA foreign_key_list(`$table`)")
            assertTrue("Table $table must have a foreign key definition", cursor.moveToFirst())

            var foundUsersFk = false
            do {
                val targetTable = cursor.getString(cursor.getColumnIndexOrThrow("table"))
                val fromCol = cursor.getString(cursor.getColumnIndexOrThrow("from"))
                val toCol = cursor.getString(cursor.getColumnIndexOrThrow("to"))
                val onDelete = cursor.getString(cursor.getColumnIndexOrThrow("on_delete"))

                if (targetTable == "users" && fromCol == "userId" && toCol == "id") {
                    foundUsersFk = true
                    assertEquals("Foreign key on $table must be ON DELETE CASCADE", "CASCADE", onDelete)
                }
            } while (cursor.moveToNext())

            assertTrue("Table $table must have a foreign key pointing to users(id)", foundUsersFk)
            cursor.close()
        }

        v7Db.close()
    }

    /**
     * MIGRATION-03:
     * Verify required indexes exist physically in SQLite schema.
     */
    @Test
    fun testMigration03_verifyRequiredIndexesInSQLite() {
        val v6Db = createV6Database()
        v6Db.close()
        val v7Db = upgradeToV7()

        fun hasIndex(tableName: String, indexName: String, isUnique: Boolean = false): Boolean {
            val cursor = v7Db.query("PRAGMA index_list(`$tableName`)")
            var found = false
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val unique = cursor.getInt(cursor.getColumnIndexOrThrow("unique")) == 1
                if (name == indexName) {
                    found = true
                    if (isUnique) {
                        assertTrue("Index $indexName on $tableName must be unique", unique)
                    }
                    break
                }
            }
            cursor.close()
            return found
        }

        assertTrue(hasIndex("wallets", "index_wallets_userId"))
        assertTrue(hasIndex("transactions", "index_transactions_userId"))
        assertTrue(hasIndex("categories", "index_categories_userId"))
        assertTrue(hasIndex("categories", "index_categories_isSystem"))
        assertTrue(hasIndex("budget_limits", "index_budget_limits_userId"))
        assertTrue(hasIndex("budget_limits", "index_budget_limits_userId_categoryOrTotalId", isUnique = true))
        assertTrue(hasIndex("feedbacks", "index_feedbacks_userId"))
        assertTrue(hasIndex("debts", "index_debts_userId"))
        assertTrue(hasIndex("assets", "index_assets_userId"))
        assertTrue(hasIndex("split_bill_history", "index_split_bill_history_userId"))

        v7Db.close()
    }

    /**
     * MIGRATION-04:
     * Verify 13 canonical system categories exist with:
     * isSystem = 1, userId = NULL
     */
    @Test
    fun testMigration04_verifyThirteenCanonicalSystemCategoriesExist() {
        val v6Db = createV6Database()
        v6Db.close()
        val v7Db = upgradeToV7()

        val cursor = v7Db.query("SELECT name, type, isSystem, userId FROM categories WHERE isSystem = 1")
        assertEquals("Must contain exactly 13 canonical system categories", 13, cursor.count)

        val canonicalNames = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            val name = cursor.getString(0)
            val isSystem = cursor.getInt(2)
            val isUserIdNull = cursor.isNull(3)

            canonicalNames.add(name)
            assertEquals("isSystem must be 1", 1, isSystem)
            assertTrue("userId must be NULL for canonical categories", isUserIdNull)
        }
        cursor.close()

        val expectedCanonicalNames = setOf(
            "Makanan & Minuman",
            "Transportasi & Bensin",
            "Belanja & Hobi",
            "Tagihan & Utilitas",
            "Kesehatan",
            "Pendidikan",
            "Hiburan & Langganan",
            "Lain-lain",
            "Gaji Pokok",
            "Bonus & Tunjangan",
            "Investasi & Dividen",
            "Penjualan / Omset",
            "Pemasukan Lain"
        )
        assertEquals(expectedCanonicalNames, canonicalNames)

        v7Db.close()
    }

    /**
     * MIGRATION-05:
     * Verify ambiguous legacy category rows remain:
     * isSystem = 0, userId = NULL
     */
    @Test
    fun testMigration05_verifyAmbiguousLegacyCategoriesRemainUnassigned() {
        val v6Db = createV6Database()
        v6Db.execSQL("INSERT INTO categories (id, name, type, iconName, colorHex) VALUES (1, 'Kategori Kustom Budi', 'EXPENSE', 'star', '#FF0000')")
        v6Db.execSQL("INSERT INTO categories (id, name, type, iconName, colorHex) VALUES (2, 'Makanan & Minuman', 'EXPENSE', 'restaurant', '#EF4444')") // Legacy row
        v6Db.close()

        val v7Db = upgradeToV7()

        // 13 system + 2 legacy = 15 total categories
        val totalCursor = v7Db.query("SELECT COUNT(*) FROM categories")
        assertTrue(totalCursor.moveToFirst())
        assertEquals(15, totalCursor.getInt(0))
        totalCursor.close()

        // Check legacy rows
        val legacyCursor = v7Db.query("SELECT id, name, isSystem, userId FROM categories WHERE isSystem = 0")
        assertEquals(2, legacyCursor.count)
        while (legacyCursor.moveToNext()) {
            assertEquals("isSystem must be 0 for legacy categories", 0, legacyCursor.getInt(2))
            assertTrue("userId must be NULL for legacy categories", legacyCursor.isNull(3))
        }
        legacyCursor.close()

        v7Db.close()
    }

    /**
     * MIGRATION-06:
     * Verify budget schema supports multiple users later without primary-key collision.
     */
    @Test
    fun testMigration06_verifyBudgetSchemaSupportsMultiUserWithoutPrimaryKeyCollision() {
        val v6Db = createV6Database()
        // Legacy row
        v6Db.execSQL("INSERT INTO budget_limits (id, limitAmount, isCategory) VALUES ('total_monthly', 5000000.0, 0)")
        v6Db.close()

        val v7Db = upgradeToV7()

        // Insert users 1 and 2
        v7Db.execSQL("INSERT INTO users (id, username, email, passwordHash, role, createdAt, trialStartDate, subscriptionExpiresAt, isApproved, isGuest) VALUES (1, 'UserOne', 'one@test.com', 'h1', 'FREE_MEMBER', 100, 100, NULL, 1, 0)")
        v7Db.execSQL("INSERT INTO users (id, username, email, passwordHash, role, createdAt, trialStartDate, subscriptionExpiresAt, isApproved, isGuest) VALUES (2, 'UserTwo', 'two@test.com', 'h2', 'FREE_MEMBER', 200, 200, NULL, 1, 0)")

        // Both User 1 and User 2 can each have a 'total_monthly' budget limit row without collision
        v7Db.execSQL("INSERT INTO budget_limits (categoryOrTotalId, limitAmount, isCategory, userId) VALUES ('total_monthly', 3000000.0, 0, 1)")
        v7Db.execSQL("INSERT INTO budget_limits (categoryOrTotalId, limitAmount, isCategory, userId) VALUES ('total_monthly', 7000000.0, 0, 2)")

        val cursor = v7Db.query("SELECT localId, categoryOrTotalId, limitAmount, userId FROM budget_limits WHERE categoryOrTotalId = 'total_monthly' ORDER BY localId ASC")
        assertEquals("Must support unassigned legacy row + User 1 row + User 2 row", 3, cursor.count)

        assertTrue(cursor.moveToFirst())
        assertTrue("Legacy row has userId = NULL", cursor.isNull(3))
        assertEquals(5000000.0, cursor.getDouble(2), 0.01)

        assertTrue(cursor.moveToNext())
        assertEquals(1L, cursor.getLong(3))
        assertEquals(3000000.0, cursor.getDouble(2), 0.01)

        assertTrue(cursor.moveToNext())
        assertEquals(2L, cursor.getLong(3))
        assertEquals(7000000.0, cursor.getDouble(2), 0.01)

        cursor.close()
        v7Db.close()
    }

    /**
     * VERIFY FRESH DATABASE V7:
     * Check that a fresh Room v7 database opens without error, matches schema, and seeds correctly.
     */
    @Test
    fun testFreshV7DatabaseCreationAndCanonicalCategories() {
        val freshDbName = "fresh_v7_test.db"
        context.deleteDatabase(freshDbName)

        val roomDb = androidx.room.Room.databaseBuilder(
            context,
            FinSmartDatabase::class.java,
            freshDbName
        )
            .allowMainThreadQueries()
            .build()

        assertNotNull(roomDb.openHelper.writableDatabase)
        assertEquals(8, roomDb.openHelper.writableDatabase.version)

        // Seed initial data using normal populateInitialData callback logic
        runBlocking {
            FinSmartDatabase.populateInitialData(roomDb)
            val categories = roomDb.categoryDao().getSystemCategories().first()
            assertEquals(13, categories.size)
            assertTrue("All seeded default categories must be isSystem = true", categories.all { it.isSystem })
            assertTrue("All seeded default categories must have userId = null", categories.all { it.userId == null })
        }

        roomDb.close()
        context.deleteDatabase(freshDbName)
    }
}
