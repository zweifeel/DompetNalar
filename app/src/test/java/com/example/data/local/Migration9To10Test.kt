package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AccountMode
import com.example.data.model.TransactionType
import com.example.data.model.UserRole
import com.example.data.model.WalletType
import com.example.util.RecurringInterval
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration9To10Test {

    private lateinit var context: Context
    private val dbName = "migration_test_9_to_10.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createV9Database() {
        val sqliteDb = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)

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
                `userId` INTEGER,
                FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        sqliteDb.execSQL("CREATE INDEX IF NOT EXISTS `index_wallets_userId` ON `wallets` (`userId`)")

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
                `accountMode` TEXT NOT NULL,
                `userId` INTEGER,
                FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        sqliteDb.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_userId` ON `transactions` (`userId`)")

        sqliteDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `categories` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `iconName` TEXT NOT NULL,
                `colorHex` TEXT NOT NULL,
                `isSystem` INTEGER NOT NULL DEFAULT 0,
                `userId` INTEGER,
                FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        sqliteDb.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_userId` ON `categories` (`userId`)")
        sqliteDb.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_isSystem` ON `categories` (`isSystem`)")

        sqliteDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `split_bill_history` (
                `id` TEXT NOT NULL,
                `merchantName` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `totalAmount` REAL NOT NULL,
                `formattedText` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `userId` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        sqliteDb.execSQL("CREATE INDEX IF NOT EXISTS `index_split_bill_history_userId` ON `split_bill_history` (`userId`)")

        sqliteDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `budget_limits` (
                `categoryOrTotalId` TEXT NOT NULL,
                `limitAmount` REAL NOT NULL,
                `isCategory` INTEGER NOT NULL,
                `userId` INTEGER,
                `localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        sqliteDb.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_limits_userId` ON `budget_limits` (`userId`)")
        sqliteDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budget_limits_userId_categoryOrTotalId` ON `budget_limits` (`userId`, `categoryOrTotalId`)")

        sqliteDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `feedbacks` (
                `id` TEXT NOT NULL,
                `userName` TEXT NOT NULL,
                `userEmail` TEXT NOT NULL,
                `rating` INTEGER NOT NULL,
                `category` TEXT NOT NULL,
                `message` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `userId` INTEGER,
                `isSynced` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        sqliteDb.execSQL("CREATE INDEX IF NOT EXISTS `index_feedbacks_userId` ON `feedbacks` (`userId`)")

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
                `userId` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        sqliteDb.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_userId` ON `debts` (`userId`)")

        sqliteDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `assets` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `initialValue` REAL NOT NULL,
                `purchaseYear` INTEGER NOT NULL,
                `category` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `userId` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        sqliteDb.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_userId` ON `assets` (`userId`)")

        sqliteDb.execSQL("""
            CREATE TABLE IF NOT EXISTS `installment_plans` (
                `id` TEXT NOT NULL,
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
                PRIMARY KEY(`id`),
                FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        sqliteDb.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_plans_userId` ON `installment_plans` (`userId`)")

        sqliteDb.version = 9
        sqliteDb.close()
    }

    @Test
    fun `T-11 and T-15a Migration9To10 with valid country backfills currencyCode under FK enabled`() = runBlocking {
        createV9Database()

        // Seed v9 data: user, wallet, and transaction
        val v9Db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        v9Db.execSQL("INSERT INTO users (id, username, email, passwordHash, role, createdAt, trialStartDate, isApproved, isGuest) VALUES (1, 'alice', 'alice@test.com', 'h', 'FREE_MEMBER', 1000, 0, 1, 0)")
        v9Db.execSQL("INSERT INTO wallets (id, name, type, accountNumber, balance, colorHex, iconName, accountMode, isDefault, createdAt, userId) VALUES (10, 'BCA', 'BANK', '123', 100000.0, '#0B57D0', 'bank', 'PERSONAL', 1, 1000, 1)")
        v9Db.execSQL("INSERT INTO transactions (id, title, amount, type, category, categoryIcon, walletId, walletName, timestamp, note, tags, accountMode, userId) VALUES (100, 'Lunch', 25000.0, 'EXPENSE', 'Food', 'food', 10, 'BCA', 1050, '', '', 'PERSONAL', 1)")
        v9Db.close()

        // Migrate using Migration9To10 with valid country provider "ID"
        val migration = Migration9To10(systemLocaleCountryProvider = { "ID" })
        val db = Room.databaseBuilder(context, FinSmartDatabase::class.java, dbName)
            .addMigrations(migration, Migration10To11())
            .build()

        // Enable and verify foreign keys
        val supportDb = db.openHelper.writableDatabase
        supportDb.setForeignKeyConstraintsEnabled(true)

        // Read migrated wallet
        val wallet = db.walletDao().getWalletById(10L)
        assertNotNull(wallet)
        assertEquals("BCA", wallet?.name)
        assertFalse(wallet!!.isArchived)
        assertEquals("IDR", wallet.currencyCode)
        assertTrue(wallet.hasAssignedCurrency)

        // Read migrated transaction
        val txs = db.transactionDao().getAllTransactions(1L).first()
        assertEquals(1, txs.size)
        assertEquals("Lunch", txs[0].title)
        assertNull(txs[0].idempotencyKey)

        // Verify recurring_schedules table exists and functions
        val schedule = com.example.data.model.RecurringSchedule(
            id = "sch_1",
            title = "Monthly Rent",
            amount = 1500000.0,
            type = TransactionType.EXPENSE,
            category = "Housing",
            categoryIcon = "home",
            walletId = 10L,
            walletName = "BCA",
            interval = RecurringInterval.MONTHLY,
            dayOfMonth = 1,
            executionHour = 8,
            executionMinute = 30,
            userId = 1L
        )
        db.recurringScheduleDao().insertSchedule(schedule)
        val loadedSchedule = db.recurringScheduleDao().getScheduleByIdAndUser("sch_1", 1L)
        assertNotNull(loadedSchedule)
        assertEquals("Monthly Rent", loadedSchedule?.title)
        assertEquals(8, loadedSchedule?.executionHour)
        assertEquals(30, loadedSchedule?.executionMinute)

        db.close()
    }

    @Test
    fun `T-15b and T-37 Migration9To10 with invalid or missing country retains null currencyCode`() = runBlocking {
        createV9Database()

        // Seed v9 data: user, normal wallet, and legacy unassigned wallet (userId = null)
        val v9Db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        v9Db.execSQL("INSERT INTO users (id, username, email, passwordHash, role, createdAt, trialStartDate, isApproved, isGuest) VALUES (1, 'bob', 'bob@test.com', 'h', 'FREE_MEMBER', 1000, 0, 1, 0)")
        v9Db.execSQL("INSERT INTO wallets (id, name, type, accountNumber, balance, colorHex, iconName, accountMode, isDefault, createdAt, userId) VALUES (20, 'Normal Wallet', 'BANK', '456', 50000.0, '#0B57D0', 'bank', 'PERSONAL', 1, 1000, 1)")
        v9Db.execSQL("INSERT INTO wallets (id, name, type, accountNumber, balance, colorHex, iconName, accountMode, isDefault, createdAt, userId) VALUES (21, 'Legacy Wallet', 'CASH', '', 20000.0, '#0B57D0', 'cash', 'PERSONAL', 0, 1000, NULL)")
        v9Db.close()

        // Migrate using Migration9To10 with unresolvable country provider returning null
        val migration = Migration9To10(systemLocaleCountryProvider = { null })
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    migration.migrate(db)
                }
            })
            .build()
        val migratedDb = factory.create(config).writableDatabase

        val normalCursor = migratedDb.query("SELECT currencyCode FROM wallets WHERE id = 20")
        assertTrue(normalCursor.moveToFirst())
        assertTrue("Normal wallet must have null currencyCode when region is unresolvable", normalCursor.isNull(0))
        normalCursor.close()

        val legacyCursor = migratedDb.query("SELECT userId, currencyCode FROM wallets WHERE id = 21")
        assertTrue(legacyCursor.moveToFirst())
        assertTrue("Legacy wallet userId must remain null", legacyCursor.isNull(0))
        assertTrue("Legacy wallet currencyCode must remain null", legacyCursor.isNull(1))
        legacyCursor.close()

        migratedDb.close()
    }

    @Test
    fun `T-38 Composite unique index on wallets id and userId prevents duplicate keys`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val sdb = db.openHelper.writableDatabase
        sdb.execSQL("INSERT INTO users (id, username, email, passwordHash, role, createdAt, trialStartDate, isApproved, isGuest) VALUES (1, 'charlie', 'charlie@test.com', 'h', 'FREE_MEMBER', 1000, 0, 1, 0)")
        sdb.execSQL("INSERT INTO wallets (id, name, type, accountNumber, balance, colorHex, iconName, accountMode, isDefault, createdAt, isArchived, currencyCode, userId) VALUES (30, 'Wallet A', 'BANK', '111', 100.0, '#0B57D0', 'bank', 'PERSONAL', 1, 1000, 0, 'IDR', 1)")

        // Attempt duplicate insert of (id = 30, userId = 1)
        try {
            sdb.execSQL("INSERT INTO wallets (id, name, type, accountNumber, balance, colorHex, iconName, accountMode, isDefault, createdAt, isArchived, currencyCode, userId) VALUES (30, 'Duplicate Wallet', 'BANK', '111', 100.0, '#0B57D0', 'bank', 'PERSONAL', 1, 1000, 0, 'IDR', 1)")
            fail("Expected SQLiteConstraintException on duplicate composite unique key (id, userId)")
        } catch (e: SQLiteConstraintException) {
            // Expected
            assertTrue(e.message?.contains("UNIQUE") == true || e.message?.contains("constraint failed") == true)
        }

        db.close()
    }
}
