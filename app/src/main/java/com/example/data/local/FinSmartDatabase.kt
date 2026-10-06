package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Database(
    entities = [
        Wallet::class,
        TransactionItem::class,
        CategoryItem::class,
        User::class,
        SplitBillHistoryItem::class,
        BudgetLimitItem::class,
        FeedbackItem::class,
        DebtItem::class,
        AssetItem::class
    ],
    version = 7,
    exportSchema = true
)
abstract class FinSmartDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun userDao(): UserDao
    abstract fun splitBillHistoryDao(): SplitBillHistoryDao
    abstract fun budgetLimitDao(): BudgetLimitDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun debtDao(): DebtDao
    abstract fun assetDao(): AssetDao

    companion object {
        @Volatile
        private var INSTANCE: FinSmartDatabase? = null

        /**
         * Migration from version 5 to 6:
         * Adds non-destructive tables for FEAT-32 (debts) and FEAT-33 (assets).
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
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

                db.execSQL("""
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
            }
        }

        /**
         * Migration from version 6 to 7 (DEF-03 Stage 1):
         * ownership columns, Category `userId`/`isSystem`, Budget `localId` schema and
         * physical Foreign Keys. See [Migration6To7] for details.
         */
        val MIGRATION_6_7: Migration = Migration6To7

        /**
         * Registry of explicit Room migrations.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_5_6,
            MIGRATION_6_7
        )

        fun getDatabase(context: Context, scope: CoroutineScope): FinSmartDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FinSmartDatabase::class.java,
                    "finsmart_database"
                )
                    .addCallback(FinSmartDatabaseCallback(scope))
                    .addMigrations(*MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class FinSmartDatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }
        }

        /**
         * The 13 canonical system categories (`isSystem = true`, `userId = NULL`).
         * Must stay in sync with [Migration6To7.CANONICAL_SYSTEM_CATEGORIES].
         */
        val DEFAULT_CATEGORIES = listOf(
            CategoryItem(name = "Makanan & Minuman", type = TransactionType.EXPENSE, iconName = "restaurant", colorHex = "#EF4444", isSystem = true),
            CategoryItem(name = "Transportasi & Bensin", type = TransactionType.EXPENSE, iconName = "commute", colorHex = "#F59E0B", isSystem = true),
            CategoryItem(name = "Belanja & Hobi", type = TransactionType.EXPENSE, iconName = "shopping_bag", colorHex = "#EC4899", isSystem = true),
            CategoryItem(name = "Tagihan & Utilitas", type = TransactionType.EXPENSE, iconName = "receipt", colorHex = "#8B5CF6", isSystem = true),
            CategoryItem(name = "Kesehatan", type = TransactionType.EXPENSE, iconName = "medical_services", colorHex = "#06B6D4", isSystem = true),
            CategoryItem(name = "Pendidikan", type = TransactionType.EXPENSE, iconName = "school", colorHex = "#3B82F6", isSystem = true),
            CategoryItem(name = "Hiburan & Langganan", type = TransactionType.EXPENSE, iconName = "movie", colorHex = "#6366F1", isSystem = true),
            CategoryItem(name = "Lain-lain", type = TransactionType.EXPENSE, iconName = "more_horiz", colorHex = "#64748B", isSystem = true),

            CategoryItem(name = "Gaji Pokok", type = TransactionType.INCOME, iconName = "payments", colorHex = "#10B981", isSystem = true),
            CategoryItem(name = "Bonus & Tunjangan", type = TransactionType.INCOME, iconName = "card_giftcard", colorHex = "#059669", isSystem = true),
            CategoryItem(name = "Investasi & Dividen", type = TransactionType.INCOME, iconName = "trending_up", colorHex = "#047857", isSystem = true),
            CategoryItem(name = "Penjualan / Omset", type = TransactionType.INCOME, iconName = "store", colorHex = "#0D9488", isSystem = true),
            CategoryItem(name = "Pemasukan Lain", type = TransactionType.INCOME, iconName = "account_balance_wallet", colorHex = "#0284C7", isSystem = true)
        )

        /**
         * Atomically wipes all user and financial records from all Room tables (FEAT-30 Right to Erasure)
         * and reseeds ONLY the 13 generic default categories taxonomy.
         * NO users, wallets, debts, assets, transactions, or budgets are recreated.
         */
        suspend fun wipeAccountDataInTransaction(database: FinSmartDatabase) {
            database.withTransaction {
                database.clearAllTables()
                database.categoryDao().insertCategories(DEFAULT_CATEGORIES)
            }
        }

        suspend fun populateInitialData(database: FinSmartDatabase) {
            val walletDao = database.walletDao()
            val categoryDao = database.categoryDao()
            val userDao = database.userDao()

            if (walletDao.getWalletCount() == 0) {
                val initialWallets = listOf(
                    Wallet(
                        name = "Dompet Utama (BCA)",
                        type = WalletType.BANK,
                        accountNumber = "1234567890",
                        balance = 0.0,
                        colorHex = "#0B57D0",
                        iconName = "bca",
                        accountMode = AccountMode.PERSONAL.name,
                        isDefault = true
                    ),
                    Wallet(
                        name = "GoPay",
                        type = WalletType.EWALLET,
                        accountNumber = "08123456789",
                        balance = 0.0,
                        colorHex = "#00897B",
                        iconName = "gopay",
                        accountMode = AccountMode.PERSONAL.name,
                        isDefault = false
                    ),
                    Wallet(
                        name = "Uang Tunai",
                        type = WalletType.CASH,
                        accountNumber = "",
                        balance = 0.0,
                        colorHex = "#10B981",
                        iconName = "cash",
                        accountMode = AccountMode.PERSONAL.name,
                        isDefault = false
                    ),
                    Wallet(
                        name = "Rekening Bisnis (Mandiri)",
                        type = WalletType.BANK,
                        accountNumber = "9876543210",
                        balance = 0.0,
                        colorHex = "#D97706",
                        iconName = "mandiri",
                        accountMode = AccountMode.BUSINESS.name,
                        isDefault = true
                    )
                )
                walletDao.insertWallets(initialWallets)
            }

            if (categoryDao.getCategoryCount() == 0) {
                categoryDao.insertCategories(DEFAULT_CATEGORIES)
            }

            if (userDao.getUserCount() == 0) {
                val initialUsers = listOf(
                    // Master Dev Lukas
                    User(
                        username = "Lukas",
                        email = "lunachroc07@gmail.com",
                        passwordHash = "",
                        role = UserRole.MASTER_DEV,
                        isApproved = true
                    )
                )
                userDao.insertUsers(initialUsers)
            }

            val debtDao = database.debtDao()
            if (debtDao.getAllDebts().first().isEmpty()) {
                val initialDebts = listOf(
                    DebtItem(name = "Kartu Kredit BCA", amount = 15000000.0, interestRatePercent = 21.0, minMonthlyPayment = 1500000.0, category = "Kartu Kredit", dueDate = "Tgl 15 tiap bulan"),
                    DebtItem(name = "Pinjaman Online A", amount = 3000000.0, interestRatePercent = 18.0, minMonthlyPayment = 500000.0, category = "Pinjaman Online", dueDate = "Tgl 20 tiap bulan"),
                    DebtItem(name = "Cicilan Motor", amount = 8000000.0, interestRatePercent = 12.0, minMonthlyPayment = 800000.0, category = "Cicilan Kendaraan", dueDate = "Tgl 5 tiap bulan")
                )
                debtDao.insertDebts(initialDebts)
            }

            val assetDao = database.assetDao()
            if (assetDao.getAllAssets().first().isEmpty()) {
                val initialAssets = listOf(
                    AssetItem(name = "MacBook Pro M2", initialValue = 24000000.0, purchaseYear = 2024, category = AssetCategory.ELECTRONICS),
                    AssetItem(name = "Honda Vario 160", initialValue = 28000000.0, purchaseYear = 2023, category = AssetCategory.VEHICLE),
                    AssetItem(name = "Logam Mulia Antam 10g", initialValue = 13000000.0, purchaseYear = 2022, category = AssetCategory.GOLD_INVESTMENT)
                )
                assetDao.insertAssets(initialAssets)
            }
        }
    }
}
