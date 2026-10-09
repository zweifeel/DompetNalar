package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPlanItem
import com.example.data.model.User
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
@Config(sdk = [36])
class Migration7To8Test {

    private lateinit var context: Context
    private val dbName = "migration_test_7_to_8.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createV7Database() {
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

        // Insert test user and existing debt in v7
        sqliteDb.execSQL("""
            INSERT INTO `users` (`id`, `username`, `email`, `passwordHash`, `role`, `createdAt`, `trialStartDate`, `isApproved`, `isGuest`)
            VALUES (1, 'user1', 'user1@example.com', 'hash123', 'USER', 1000, 1000, 1, 0)
        """.trimIndent())

        sqliteDb.execSQL("""
            INSERT INTO `debts` (`id`, `name`, `amount`, `interestRatePercent`, `minMonthlyPayment`, `category`, `dueDate`, `createdAt`, `userId`)
            VALUES ('debt-1', 'Kartu Kredit Mandiri', 5000000.0, 2.5, 500000.0, 'Kartu Kredit', '15 tiap bulan', 2000, 1)
        """.trimIndent())

        sqliteDb.version = 7
        sqliteDb.close()
    }

    @Test
    fun testMigration7To8_PreservesExistingDataAndCreatesInstallmentPlansTable() = runBlocking {
        createV7Database()

        // Open database through Room v8 with Migration7To8
        val db = Room.databaseBuilder(context, FinSmartDatabase::class.java, dbName)
            .addMigrations(Migration7To8)
            .build()

        // 1. Verify existing v7 debts are preserved
        val existingDebts = db.debtDao().getAllDebts(1L).first()
        assertEquals(1, existingDebts.size)
        assertEquals("debt-1", existingDebts[0].id)
        assertEquals("Kartu Kredit Mandiri", existingDebts[0].name)
        assertEquals(5000000.0, existingDebts[0].amount, 0.001)

        // 2. Verify installment_plans table works correctly with CRUD
        val installmentDao = db.installmentPlanDao()
        val plan = InstallmentPlanItem(
            id = "plan-motor-35",
            name = "Cicilan Motor Vario",
            provider = "FIF Group",
            installmentAmount = 850000.0,
            totalTenor = 35,
            paidCount = 0,
            anchorDay = 15,
            startDate = "2026-10-09",
            nextDueDate = "Tgl 15 tiap bulan",
            totalRemainingPrincipal = null,
            status = "ACTIVE",
            notes = "Cicilan motor 35 bulan",
            userId = 1L
        )

        installmentDao.insertInstallmentPlan(plan)

        val retrievedPlans = installmentDao.getAllInstallmentPlans(1L).first()
        assertEquals(1, retrievedPlans.size)
        val loaded = retrievedPlans[0]
        assertEquals("plan-motor-35", loaded.id)
        assertEquals("Cicilan Motor Vario", loaded.name)
        assertEquals(35, loaded.totalTenor)
        assertEquals(0, loaded.paidCount)
        assertEquals(35, loaded.remainingTenor)
        assertFalse(loaded.isCompleted)
        assertEquals("ACTIVE", loaded.status)

        db.close()
    }
}
