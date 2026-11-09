package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration10To11Test {

    private lateinit var context: Context
    private var dbName = "migration_test_10_to_11.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "m10_${System.currentTimeMillis() % 10000}.db"
        context.getDatabasePath(dbName).parentFile?.mkdirs()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createV10Database(): SQLiteDatabase {
        context.getDatabasePath(dbName).parentFile?.mkdirs()
        val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)

        db.execSQL("""
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

        db.execSQL("""
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
                `createdAt` INTEGER NOT NULL,
                `isArchived` INTEGER NOT NULL DEFAULT 0,
                `currencyCode` TEXT DEFAULT NULL,
                `userId` INTEGER,
                FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
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
                `accountMode` TEXT NOT NULL,
                `idempotencyKey` TEXT DEFAULT NULL,
                `userId` INTEGER,
                FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `installment_plans` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `provider` TEXT NOT NULL,
                `installmentAmount` REAL NOT NULL,
                `totalTenor` INTEGER NOT NULL,
                `paidCount` INTEGER NOT NULL,
                `frequency` TEXT NOT NULL,
                `startDate` TEXT NOT NULL,
                `nextDueDate` TEXT NOT NULL,
                `anchorDay` INTEGER NOT NULL,
                `totalRemainingPrincipal` REAL,
                `status` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `lastPaymentDate` INTEGER,
                `userId` INTEGER,
                FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())

        db.version = 10
        return db
    }

    @Test
    fun `migration 10 to 11 preserves existing financial data and populates canonical minor units`() {
        val rawDb = createV10Database()

        // Insert Room 10 sample user, wallet, transaction, plan
        rawDb.execSQL("INSERT INTO users (id, username, email, passwordHash, role, createdAt, trialStartDate, isApproved, isGuest) VALUES (1, 'TestUser', 'test@test.com', 'hash', 'USER', 1000, 1000, 1, 0)")
        rawDb.execSQL("INSERT INTO wallets (id, name, type, accountNumber, balance, colorHex, iconName, accountMode, isDefault, createdAt, currencyCode, userId) VALUES (1, 'Main Wallet', 'BANK', '123', 500000.50, '#000000', 'bank', 'PERSONAL', 1, 1000, 'IDR', 1)")
        rawDb.execSQL("INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId) VALUES (1, 'Groceries', 25000.25, 'EXPENSE', 'Food', 'food', 1, 'Main Wallet', 1000, '', '', 'PERSONAL', 1)")
        rawDb.execSQL("INSERT INTO installment_plans (id, name, provider, installmentAmount, totalTenor, paidCount, frequency, startDate, nextDueDate, anchorDay, status, notes, createdAt, userId) VALUES ('plan-1', 'Motorcycle', 'Leasing', 100000.0, 12, 0, 'Bulanan', '2024-01-01', '2024-02-01', 1, 'AKTIF', '', 1000, 1)")

        // Add sqlite_sequence entries to verify P0-G sequence restoration
        rawDb.execSQL("INSERT INTO sqlite_sequence (name, seq) VALUES ('transactions', 1)")
        rawDb.execSQL("INSERT INTO sqlite_sequence (name, seq) VALUES ('wallets', 1)")
        rawDb.execSQL("INSERT INTO sqlite_sequence (name, seq) VALUES ('users', 1)")

        rawDb.close()

        // Open via FrameworkSQLiteOpenHelper to run SupportSQLiteDatabase migration
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    if (oldVersion == 10 && newVersion == 11) {
                        Migration10To11().migrate(db)
                    }
                }
            })
            .build()

        val helper = factory.create(config)
        val migratedDb = helper.writableDatabase

        // 1. Verify wallets.balanceMinorUnits converted correctly: 500000.50 -> 50000050 minor units
        val walletCursor = migratedDb.query("SELECT balanceMinorUnits, currencyCode FROM wallets WHERE id = 1")
        assertTrue(walletCursor.moveToFirst())
        assertEquals(50000050L, walletCursor.getLong(0))
        assertEquals("IDR", walletCursor.getString(1))
        walletCursor.close()

        // 2. Verify transactions.amountMinorUnits converted: 25000.25 -> 2500025 minor units
        val txCursor = migratedDb.query("SELECT amountMinorUnits, currencyCode, isReversal, reversedTransactionId FROM transactions WHERE id = 1")
        assertTrue(txCursor.moveToFirst())
        assertEquals(2500025L, txCursor.getLong(0))
        assertEquals("IDR", txCursor.getString(1))
        assertEquals(0, txCursor.getInt(2))
        assertTrue(txCursor.isNull(3))
        txCursor.close()

        // 3. Verify installment_plans.totalAmountMinorUnits: 100000.0 * 12 = 1200000.0 -> 120000000L
        val planCursor = migratedDb.query("SELECT totalAmountMinorUnits, currencyCode FROM installment_plans WHERE id = 'plan-1'")
        assertTrue(planCursor.moveToFirst())
        assertEquals(120000000L, planCursor.getLong(0))
        assertEquals("IDR", planCursor.getString(1))
        planCursor.close()

        // 4. Verify PayLater tables exist
        val periodsCursor = migratedDb.query("SELECT COUNT(*) FROM installment_periods")
        assertTrue(periodsCursor.moveToFirst())
        assertEquals(0L, periodsCursor.getLong(0))
        periodsCursor.close()

        // 5. Verify P0-G sequence restoration
        val seqCursor = migratedDb.query("SELECT seq FROM sqlite_sequence WHERE name = 'transactions'")
        assertTrue(seqCursor.moveToFirst())
        assertEquals(1L, seqCursor.getLong(0))
        seqCursor.close()

        migratedDb.close()
    }

    @Test
    fun p0bReversalImmutabilityTriggersPreventModification() {
        val rawDb = createV10Database()
        rawDb.execSQL("INSERT INTO users (id, username, email, passwordHash, role, createdAt, trialStartDate, isApproved, isGuest) VALUES (1, 'TestUser', 'test@test.com', 'hash', 'USER', 1000, 1000, 1, 0)")
        rawDb.execSQL("INSERT INTO wallets (id, name, type, accountNumber, balance, colorHex, iconName, accountMode, isDefault, createdAt, currencyCode, userId) VALUES (1, 'Main Wallet', 'BANK', '123', 500000.0, '#000000', 'bank', 'PERSONAL', 1, 1000, 'IDR', 1)")
        rawDb.execSQL("INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId) VALUES (1, 'Payment', 50000.0, 'EXPENSE', 'Food', 'food', 1, 'Main Wallet', 1000, '', '', 'PERSONAL', 1)")
        rawDb.close()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    Migration10To11().migrate(db)
                }
            })
            .build()

        val migratedDb = factory.create(config).writableDatabase

        // Insert valid reversal of transaction 1 (counter-entry type INCOME)
        migratedDb.execSQL("""
            INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId, amountMinorUnits, currencyCode, isReversal, reversedTransactionId)
            VALUES (2, 'Reversal of Payment', 50000.0, 'INCOME', 'Food', 'food', 1, 'Main Wallet', 2000, '', '', 'PERSONAL', 1, 5000000, 'IDR', 1, 1)
        """)

        // Trigger 1: Attempt to mutate isReversal flag -> ABORT
        try {
            migratedDb.execSQL("UPDATE transactions SET isReversal = 0 WHERE id = 2")
            fail("trg_immutable_reversal_flags should have aborted update of isReversal")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("P0-B") == true)
        }

        // Trigger 2: Attempt to reverse a reversal (reverse transaction 2) -> ABORT
        try {
            migratedDb.execSQL("""
                INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId, amountMinorUnits, currencyCode, isReversal, reversedTransactionId)
                VALUES (3, 'Reverse Reversal', 50000.0, 'EXPENSE', 'Food', 'food', 1, 'Main Wallet', 3000, '', '', 'PERSONAL', 1, 5000000, 'IDR', 1, 2)
            """)
            fail("trg_no_reversal_of_reversal should have aborted reversing transaction 2")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("P0-B") == true)
        }

        // Trigger 3: Attempt to delete reversal transaction -> ABORT
        try {
            migratedDb.execSQL("DELETE FROM transactions WHERE id = 2")
            fail("trg_no_delete_reversal should have aborted delete of reversal transaction")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("P0-B") == true)
        }

        migratedDb.close()
    }

    @Test
    fun `P0-C installment payment and allocation triggers enforce financial invariants`() {
        val rawDb = createV10Database()
        rawDb.execSQL("INSERT INTO users (id, username, email, passwordHash, role, createdAt, trialStartDate, isApproved, isGuest) VALUES (1, 'TestUser', 'test@test.com', 'hash', 'USER', 1000, 1000, 1, 0)")
        rawDb.execSQL("INSERT INTO wallets (id, name, type, accountNumber, balance, colorHex, iconName, accountMode, isDefault, createdAt, currencyCode, userId) VALUES (1, 'Main Wallet', 'BANK', '123', 500000.0, '#000000', 'bank', 'PERSONAL', 1, 1000, 'IDR', 1)")
        rawDb.execSQL("INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId) VALUES (1, 'Pay Plan', 100000.0, 'EXPENSE', 'Debt', 'debt', 1, 'Main Wallet', 1000, '', '', 'PERSONAL', 1)")
        rawDb.execSQL("INSERT INTO installment_plans (id, name, provider, installmentAmount, totalTenor, paidCount, frequency, startDate, nextDueDate, anchorDay, status, notes, createdAt, userId) VALUES ('plan-1', 'Motor', 'Leasing', 100000.0, 10, 0, 'Bulanan', '2024-01-01', '2024-02-01', 1, 'AKTIF', '', 1000, 1)")
        rawDb.close()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    Migration10To11().migrate(db)
                }
            })
            .build()

        val migratedDb = factory.create(config).writableDatabase

        // 1. Create a period
        migratedDb.execSQL("""
            INSERT INTO installment_periods (id, planId, currencyCode, userId, periodNumber, amountDueMinorUnits, amountPaidMinorUnits, dueDate, isPaid)
            VALUES ('period-1', 'plan-1', 'IDR', 1, 1, 10000000, 0, '2024-02-01', 0)
        """)

        // 2. Create a payment
        migratedDb.execSQL("""
            INSERT INTO installment_payments (id, planId, transactionId, currencyCode, userId, totalMinorUnits, unallocatedMinorUnits, isVoid, reversalTransactionId, createdAt)
            VALUES ('payment-1', 'plan-1', 1, 'IDR', 1, 10000000, 0, 0, NULL, 1000)
        """)

        // 3. Create an allocation (triggers automatic update to period amountPaidMinorUnits and isPaid)
        migratedDb.execSQL("""
            INSERT INTO installment_payment_allocations (id, paymentId, periodId, currencyCode, userId, allocatedMinorUnits, isVoid, allocatedAt)
            VALUES ('alloc-1', 'payment-1', 'period-1', 'IDR', 1, 10000000, 0, 1000)
        """)

        val initialPeriodCursor = migratedDb.query("SELECT amountPaidMinorUnits, isPaid FROM installment_periods WHERE id = 'period-1'")
        assertTrue(initialPeriodCursor.moveToFirst())
        assertEquals(10000000L, initialPeriodCursor.getLong(0))
        assertEquals(1, initialPeriodCursor.getInt(1))
        initialPeriodCursor.close()

        // Test 1: Deleting payment record -> ABORT
        try {
            migratedDb.execSQL("DELETE FROM installment_payments WHERE id = 'payment-1'")
            fail("trg_no_delete_installment_payment should prevent delete")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("P0-C") == true)
        }

        // Test 2: Deleting allocation record -> ABORT
        try {
            migratedDb.execSQL("DELETE FROM installment_payment_allocations WHERE id = 'alloc-1'")
            fail("trg_no_delete_installment_allocation should prevent delete")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("P0-C") == true)
        }

        // Test 3: Modifying allocation core fields -> ABORT
        try {
            migratedDb.execSQL("UPDATE installment_payment_allocations SET allocatedMinorUnits = 5000000 WHERE id = 'alloc-1'")
            fail("trg_immutable_allocation_core_fields should prevent mutating allocatedMinorUnits")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("P0-C") == true)
        }

        // Test 4: Voiding allocation deducts amount from period and sets isPaid = 0
        migratedDb.execSQL("UPDATE installment_payment_allocations SET isVoid = 1 WHERE id = 'alloc-1'")
        val pCursor = migratedDb.query("SELECT amountPaidMinorUnits, isPaid FROM installment_periods WHERE id = 'period-1'")
        assertTrue(pCursor.moveToFirst())
        assertEquals(0L, pCursor.getLong(0))
        assertEquals(0, pCursor.getInt(1))
        pCursor.close()

        // Test 5: Voiding payment requires valid matching reversal transaction
        // First try to void without reversalTransactionId -> ABORT
        try {
            migratedDb.execSQL("UPDATE installment_payments SET isVoid = 1 WHERE id = 'payment-1'")
            fail("Voiding without reversalTransactionId must fail")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("P0-C") == true)
        }

        // Create matching reversal transaction for tx 1 (counter-entry type INCOME)
        migratedDb.execSQL("""
            INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId, amountMinorUnits, currencyCode, isReversal, reversedTransactionId)
            VALUES (2, 'Reversal of Payment', 100000.0, 'INCOME', 'Debt', 'debt', 1, 'Main Wallet', 2000, '', '', 'PERSONAL', 1, 10000000, 'IDR', 1, 1)
        """)

        // Void with reversalTransactionId = 2 -> SUCCESS
        migratedDb.execSQL("UPDATE installment_payments SET isVoid = 1, reversalTransactionId = 2, unallocatedMinorUnits = totalMinorUnits WHERE id = 'payment-1'")

        // Test 6: Once voided, cannot unvoid payment
        try {
            migratedDb.execSQL("UPDATE installment_payments SET isVoid = 0 WHERE id = 'payment-1'")
            fail("trg_no_unvoid_payment_record should prevent unvoiding")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("P0-C") == true)
        }

        migratedDb.close()
    }

    @Test
    fun `P0-B financial match trigger rejects mismatched walletId currency or userId on reversal`() {
        val rawDb = createV10Database()
        rawDb.execSQL("INSERT INTO users (id, username, email, passwordHash, role, createdAt, trialStartDate, isApproved, isGuest) VALUES (1, 'User 1', 'u1@test.com', 'h', 'USER', 1000, 1000, 1, 0)")
        rawDb.execSQL("INSERT INTO users (id, username, email, passwordHash, role, createdAt, trialStartDate, isApproved, isGuest) VALUES (2, 'User 2', 'u2@test.com', 'h', 'USER', 1000, 1000, 1, 0)")
        rawDb.execSQL("INSERT INTO wallets (id, name, type, accountNumber, balance, colorHex, iconName, accountMode, isDefault, createdAt, currencyCode, userId) VALUES (1, 'W1', 'BANK', '1', 1000.0, '#00', 'i', 'PERSONAL', 1, 1000, 'IDR', 1)")
        rawDb.execSQL("INSERT INTO wallets (id, name, type, accountNumber, balance, colorHex, iconName, accountMode, isDefault, createdAt, currencyCode, userId) VALUES (2, 'W2', 'BANK', '2', 1000.0, '#00', 'i', 'PERSONAL', 1, 1000, 'USD', 2)")
        rawDb.execSQL("INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId) VALUES (1, 'T1', 50.0, 'EXPENSE', 'Food', 'f', 1, 'W1', 1000, '', '', 'PERSONAL', 1)")
        rawDb.close()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    Migration10To11().migrate(db)
                }
            })
            .build()

        val migratedDb = factory.create(config).writableDatabase

        // 1. Mismatched walletId (walletId = 2 instead of 1) -> ABORT
        try {
            migratedDb.execSQL("""
                INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId, amountMinorUnits, currencyCode, isReversal, reversedTransactionId)
                VALUES (10, 'Rev Mismatch Wallet', 50.0, 'INCOME', 'Food', 'f', 2, 'W2', 2000, '', '', 'PERSONAL', 1, 5000, 'IDR', 1, 1)
            """)
            fail("Reversal with mismatched walletId must fail")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("P0-B: Reversal walletId must match") == true)
        }

        // 2. Mismatched currencyCode (currencyCode = 'USD' instead of 'IDR') -> ABORT
        try {
            migratedDb.execSQL("""
                INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId, amountMinorUnits, currencyCode, isReversal, reversedTransactionId)
                VALUES (11, 'Rev Mismatch Curr', 50.0, 'INCOME', 'Food', 'f', 1, 'W1', 2000, '', '', 'PERSONAL', 1, 5000, 'USD', 1, 1)
            """)
            fail("Reversal with mismatched currencyCode must fail")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("P0-B: Reversal currencyCode must match") == true)
        }

        // 3. Mismatched userId (userId = 2 instead of 1) -> ABORT
        try {
            migratedDb.execSQL("""
                INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId, amountMinorUnits, currencyCode, isReversal, reversedTransactionId)
                VALUES (12, 'Rev Mismatch User', 50.0, 'INCOME', 'Food', 'f', 1, 'W1', 2000, '', '', 'PERSONAL', 2, 5000, 'IDR', 1, 1)
            """)
            fail("Reversal with mismatched userId must fail")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("P0-B: Reversal userId must match") == true)
        }

        // 4. Inconsistent reversal flags: isReversal = 1 but reversedTransactionId = NULL -> ABORT
        try {
            migratedDb.execSQL("""
                INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId, amountMinorUnits, currencyCode, isReversal, reversedTransactionId)
                VALUES (13, 'Rev No Target', 50.0, 'INCOME', 'Food', 'f', 1, 'W1', 2000, '', '', 'PERSONAL', 1, 5000, 'IDR', 1, NULL)
            """)
            fail("isReversal = 1 without target must fail")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("P0-B: isReversal must be 1 if and only if") == true || e.message?.contains("P0-B: Reversal transaction must have a non-null reversedTransactionId") == true)
        }

        migratedDb.close()
    }

    @Test
    fun `P0-B reversal type semantics enforces exact counter-entry typing and rejects invalid types`() {
        val rawDb = createV10Database()
        rawDb.execSQL("INSERT INTO users (id, username, email, passwordHash, role, createdAt, trialStartDate, isApproved, isGuest) VALUES (1, 'User 1', 'u1@test.com', 'h', 'USER', 1000, 1000, 1, 0)")
        rawDb.execSQL("INSERT INTO wallets (id, name, type, accountNumber, balance, colorHex, iconName, accountMode, isDefault, createdAt, currencyCode, userId) VALUES (1, 'W1', 'BANK', '1', 1000.0, '#00', 'i', 'PERSONAL', 1, 1000, 'IDR', 1)")
        rawDb.execSQL("INSERT INTO wallets (id, name, type, accountNumber, balance, colorHex, iconName, accountMode, isDefault, createdAt, currencyCode, userId) VALUES (2, 'W2', 'BANK', '2', 500.0, '#00', 'i', 'PERSONAL', 0, 1000, 'IDR', 1)")
        // T1 is EXPENSE, T2 is INCOME, T3 is TRANSFER
        rawDb.execSQL("INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId) VALUES (1, 'Expense Tx', 50.0, 'EXPENSE', 'Food', 'f', 1, 'W1', 1000, '', '', 'PERSONAL', 1)")
        rawDb.execSQL("INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId) VALUES (2, 'Income Tx', 80.0, 'INCOME', 'Salary', 's', 1, 'W1', 1000, '', '', 'PERSONAL', 1)")
        rawDb.execSQL("INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, targetWalletId, targetWalletName, timestamp, note, tags, accountMode, userId) VALUES (3, 'Transfer Tx', 30.0, 'TRANSFER', 'Transfer', 't', 1, 'W1', 2, 'W2', 1000, '', '', 'PERSONAL', 1)")
        rawDb.close()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    Migration10To11().migrate(db)
                }
            })
            .build()

        val migratedDb = factory.create(config).writableDatabase

        // 1. Reversing EXPENSE with EXPENSE -> FAILS (must be INCOME)
        try {
            migratedDb.execSQL("""
                INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId, amountMinorUnits, currencyCode, isReversal, reversedTransactionId)
                VALUES (101, 'Rev Same Type', 50.0, 'EXPENSE', 'Food', 'f', 1, 'W1', 2000, '', '', 'PERSONAL', 1, 5000, 'IDR', 1, 1)
            """)
            fail("Reversing EXPENSE with EXPENSE must fail")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("P0-B: Reversal transaction type must be valid counter-entry") == true)
        }

        // 2. Reversing EXPENSE with valid counter-entry INCOME -> SUCCEEDS
        migratedDb.execSQL("""
            INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId, amountMinorUnits, currencyCode, isReversal, reversedTransactionId)
            VALUES (102, 'Rev Expense to Income', 50.0, 'INCOME', 'Food', 'f', 1, 'W1', 2000, '', '', 'PERSONAL', 1, 5000, 'IDR', 1, 1)
        """)

        // 3. Reversing INCOME with valid counter-entry EXPENSE -> SUCCEEDS
        migratedDb.execSQL("""
            INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId, amountMinorUnits, currencyCode, isReversal, reversedTransactionId)
            VALUES (103, 'Rev Income to Expense', 80.0, 'EXPENSE', 'Salary', 's', 1, 'W1', 2000, '', '', 'PERSONAL', 1, 8000, 'IDR', 1, 2)
        """)

        // 4. Reversing TRANSFER with valid counter-entry TRANSFER -> SUCCEEDS
        migratedDb.execSQL("""
            INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, targetWalletId, targetWalletName, timestamp, note, tags, accountMode, userId, amountMinorUnits, currencyCode, isReversal, reversedTransactionId)
            VALUES (104, 'Rev Transfer to Transfer', 30.0, 'TRANSFER', 'Transfer', 't', 2, 'W2', 1, 'W1', 2000, '', '', 'PERSONAL', 1, 3000, 'IDR', 1, 3)
        """)

        migratedDb.close()
    }

    @Test
    fun `P0-B single reversal guard prevents duplicate reversals for the same original transaction`() {
        val rawDb = createV10Database()
        rawDb.execSQL("INSERT INTO users (id, username, email, passwordHash, role, createdAt, trialStartDate, isApproved, isGuest) VALUES (1, 'User 1', 'u1@test.com', 'h', 'USER', 1000, 1000, 1, 0)")
        rawDb.execSQL("INSERT INTO wallets (id, name, type, accountNumber, balance, colorHex, iconName, accountMode, isDefault, createdAt, currencyCode, userId) VALUES (1, 'W1', 'BANK', '1', 1000.0, '#00', 'i', 'PERSONAL', 1, 1000, 'IDR', 1)")
        rawDb.execSQL("INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId) VALUES (1, 'T1', 50.0, 'EXPENSE', 'Food', 'f', 1, 'W1', 1000, '', '', 'PERSONAL', 1)")
        rawDb.close()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    Migration10To11().migrate(db)
                }
            })
            .build()

        val migratedDb = factory.create(config).writableDatabase

        // 1. First reversal of T1 -> SUCCESS
        migratedDb.execSQL("""
            INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId, amountMinorUnits, currencyCode, isReversal, reversedTransactionId)
            VALUES (201, 'Rev 1', 50.0, 'INCOME', 'Food', 'f', 1, 'W1', 2000, '', '', 'PERSONAL', 1, 5000, 'IDR', 1, 1)
        """)

        // 2. Second reversal of T1 -> REJECTED (Duplicate reversal rejected)
        try {
            migratedDb.execSQL("""
                INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId, amountMinorUnits, currencyCode, isReversal, reversedTransactionId)
                VALUES (202, 'Rev 2 Duplicate', 50.0, 'INCOME', 'Food', 'f', 1, 'W1', 2001, '', '', 'PERSONAL', 1, 5000, 'IDR', 1, 1)
            """)
            fail("Second reversal of T1 must fail")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("P0-B: Duplicate reversal rejected") == true || e.message?.contains("UNIQUE") == true)
        }

        // 3. Reversing a reversal (attempting to reverse 201) -> REJECTED
        try {
            migratedDb.execSQL("""
                INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId, amountMinorUnits, currencyCode, isReversal, reversedTransactionId)
                VALUES (203, 'Rev of Rev', 50.0, 'EXPENSE', 'Food', 'f', 1, 'W1', 2002, '', '', 'PERSONAL', 1, 5000, 'IDR', 1, 201)
            """)
            fail("Reversing a reversal must fail")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("cannot reverse a reversal") == true)
        }

        migratedDb.close()
    }

    @Test
    fun p0bNullSafeReversalValidationFailClosed() {
        val rawDb = createV10Database()
        rawDb.execSQL("INSERT INTO users (id, username, email, passwordHash, role, createdAt, trialStartDate, isApproved, isGuest) VALUES (1, 'User 1', 'u1@test.com', 'h', 'USER', 1000, 1000, 1, 0)")
        rawDb.execSQL("INSERT INTO wallets (id, name, type, accountNumber, balance, colorHex, iconName, accountMode, isDefault, createdAt, currencyCode, userId) VALUES (1, 'W1', 'BANK', '1', 1000.0, '#00', 'i', 'PERSONAL', 1, 1000, 'IDR', 1)")
        // Valid complete tx: T1
        rawDb.execSQL("INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId) VALUES (1, 'T1 Complete', 50.0, 'EXPENSE', 'Food', 'f', 1, 'W1', 1000, '', '', 'PERSONAL', 1)")
        // Incomplete legacy tx with null userId: T2
        rawDb.execSQL("INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId) VALUES (2, 'T2 Incomplete User', 50.0, 'EXPENSE', 'Food', 'f', 1, 'W1', 1000, '', '', 'PERSONAL', NULL)")
        rawDb.close()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    Migration10To11().migrate(db)
                }
            })
            .build()

        val migratedDb = factory.create(config).writableDatabase

        // 1. Reversal with NULL userId -> REJECTED
        try {
            migratedDb.execSQL("""
                INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId, amountMinorUnits, currencyCode, isReversal, reversedTransactionId)
                VALUES (301, 'Rev Null User', 50.0, 'INCOME', 'Food', 'f', 1, 'W1', 2000, '', '', 'PERSONAL', NULL, 5000, 'IDR', 1, 1)
            """)
            fail("Reversal with NULL userId must fail")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("P0-B: Reversal transaction must have a non-null userId") == true)
        }

        // 2. Reversal with NULL currencyCode -> REJECTED
        try {
            migratedDb.execSQL("""
                INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId, amountMinorUnits, currencyCode, isReversal, reversedTransactionId)
                VALUES (302, 'Rev Null Curr', 50.0, 'INCOME', 'Food', 'f', 1, 'W1', 2000, '', '', 'PERSONAL', 1, 5000, NULL, 1, 1)
            """)
            fail("Reversal with NULL currencyCode must fail")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("P0-B: Reversal transaction must have a non-null currencyCode") == true)
        }

        // 3. Attempting to reverse incomplete legacy transaction T2 (null userId) -> REJECTED fail-closed
        try {
            migratedDb.execSQL("""
                INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId, amountMinorUnits, currencyCode, isReversal, reversedTransactionId)
                VALUES (303, 'Rev Legacy Incomplete', 50.0, 'INCOME', 'Food', 'f', 1, 'W1', 2000, '', '', 'PERSONAL', 1, 5000, 'IDR', 1, 2)
            """)
            fail("Reversing incomplete legacy transaction must fail fail-closed")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("incomplete financial identity") == true)
        }

        // 4. Valid reversal with complete financial identity -> SUCCEEDS
        migratedDb.execSQL("""
            INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId, amountMinorUnits, currencyCode, isReversal, reversedTransactionId)
            VALUES (304, 'Valid Complete Rev', 50.0, 'INCOME', 'Food', 'f', 1, 'W1', 2000, '', '', 'PERSONAL', 1, 5000, 'IDR', 1, 1)
        """)

        migratedDb.close()
    }

    @Test
    fun `migration atomic rollback preserves Room 10 state atomically on mid-migration failure`() {
        val rawDb = createV10Database()
        rawDb.execSQL("INSERT INTO users (id, username, email, passwordHash, role, createdAt, trialStartDate, isApproved, isGuest) VALUES (1, 'User 1', 'u1@test.com', 'h', 'USER', 1000, 1000, 1, 0)")
        rawDb.execSQL("INSERT INTO wallets (id, name, type, accountNumber, balance, colorHex, iconName, accountMode, isDefault, createdAt, currencyCode, userId) VALUES (1, 'W1', 'BANK', '1', 500000.0, '#00', 'i', 'PERSONAL', 1, 1000, 'IDR', 1)")
        rawDb.execSQL("INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId) VALUES (1, 'T1', 50000.0, 'EXPENSE', 'Food', 'f', 1, 'W1', 1000, '', '', 'PERSONAL', 1)")
        rawDb.close()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    db.beginTransaction()
                    try {
                        Migration10To11().migrate(db)
                        // Trigger intentional mid-migration failure
                        throw IllegalStateException("Simulated mid-migration failure for atomic rollback verification")
                    } finally {
                        db.endTransaction()
                    }
                }
            })
            .build()

        try {
            factory.create(config).writableDatabase
            fail("Migration should have failed and thrown IllegalStateException")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Simulated mid-migration failure") == true)
        }

        // Open database directly at version 10 to inspect its state
        val rollbackDb = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        try {
            // 1. Verify PRAGMA integrity_check is "ok"
            val integrityCursor = rollbackDb.rawQuery("PRAGMA integrity_check", null)
            assertTrue(integrityCursor.moveToFirst())
            assertEquals("ok", integrityCursor.getString(0))
            integrityCursor.close()

            // 2. Verify Room 11 new tables DO NOT exist
            val tableCursor = rollbackDb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('installment_periods', 'installment_payments', 'installment_payment_allocations')",
                null
            )
            assertEquals(0, tableCursor.count)
            tableCursor.close()

            // 3. Verify Room 11 new columns DO NOT exist on wallets
            val colCursor = rollbackDb.rawQuery("PRAGMA table_info(wallets)", null)
            val colNames = mutableListOf<String>()
            while (colCursor.moveToNext()) {
                colNames.add(colCursor.getString(1))
            }
            colCursor.close()
            assertTrue("balanceMinorUnits must NOT exist after rollback", !colNames.contains("balanceMinorUnits"))

            // 4. Verify Room 11 triggers DO NOT exist
            val trigCursor = rollbackDb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='trigger' AND name LIKE 'trg_%'",
                null
            )
            assertEquals(0, trigCursor.count)
            trigCursor.close()

            // 5. Verify Room 10 data is intact
            val walletCheck = rollbackDb.rawQuery("SELECT balance FROM wallets WHERE id = 1", null)
            assertTrue(walletCheck.moveToFirst())
            assertEquals(500000.0, walletCheck.getDouble(0), 0.001)
            walletCheck.close()

            val txCheck = rollbackDb.rawQuery("SELECT amount, title FROM transactions WHERE id = 1", null)
            assertTrue(txCheck.moveToFirst())
            assertEquals(50000.0, txCheck.getDouble(0), 0.001)
            assertEquals("T1", txCheck.getString(1))
            txCheck.close()
        } finally {
            rollbackDb.close()
        }
    }

    @Test
    fun `P0-E calendar date trigger rejects invalid ISO dates in installment_periods`() {
        val rawDb = createV10Database()
        rawDb.execSQL("INSERT INTO users (id, username, email, passwordHash, role, createdAt, trialStartDate, isApproved, isGuest) VALUES (1, 'User 1', 'u1@test.com', 'h', 'USER', 1000, 1000, 1, 0)")
        rawDb.execSQL("INSERT INTO installment_plans (id, name, provider, installmentAmount, totalTenor, paidCount, frequency, startDate, nextDueDate, anchorDay, status, notes, createdAt, userId) VALUES ('p-1', 'Plan', 'P', 100.0, 1, 0, 'M', '2024-01-01', '2024-02-01', 1, 'AKTIF', '', 1000, 1)")
        rawDb.close()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    Migration10To11().migrate(db)
                }
            })
            .build()

        val migratedDb = factory.create(config).writableDatabase

        // Invalid non-leap year Feb 29 (2023-02-29) -> ABORT
        try {
            migratedDb.execSQL("""
                INSERT INTO installment_periods (id, planId, currencyCode, userId, periodNumber, amountDueMinorUnits, amountPaidMinorUnits, dueDate, isPaid)
                VALUES ('per-inv-1', 'p-1', 'IDR', 1, 1, 10000, 0, '2023-02-29', 0)
            """)
            fail("Invalid date 2023-02-29 must be rejected by P0-E trigger")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("P0-E: dueDate must be a valid ISO-8601") == true)
        }

        // Malformed date -> ABORT
        try {
            migratedDb.execSQL("""
                INSERT INTO installment_periods (id, planId, currencyCode, userId, periodNumber, amountDueMinorUnits, amountPaidMinorUnits, dueDate, isPaid)
                VALUES ('per-inv-2', 'p-1', 'IDR', 1, 2, 10000, 0, '2024-13-01', 0)
            """)
            fail("Invalid month 2024-13-01 must be rejected by P0-E trigger")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("P0-E: dueDate must be a valid ISO-8601") == true)
        }

        // Valid leap year Feb 29 (2024-02-29) -> SUCCESS
        migratedDb.execSQL("""
            INSERT INTO installment_periods (id, planId, currencyCode, userId, periodNumber, amountDueMinorUnits, amountPaidMinorUnits, dueDate, isPaid)
            VALUES ('per-val-1', 'p-1', 'IDR', 1, 1, 10000, 0, '2024-02-29', 0)
        """)

        val c = migratedDb.query("SELECT COUNT(*) FROM installment_periods WHERE id = 'per-val-1'")
        assertTrue(c.moveToFirst())
        assertEquals(1L, c.getLong(0))
        c.close()

        migratedDb.close()
    }
}
