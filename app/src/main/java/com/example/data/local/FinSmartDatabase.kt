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
import com.example.config.DeveloperAccountConfig
import com.example.config.ReviewerAccountConfig
import com.example.util.PasswordHasher

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
        AssetItem::class,
        com.example.data.model.InstallmentPlanItem::class,
        com.example.data.model.RecurringSchedule::class,
        com.example.data.model.InstallmentPeriod::class,
        com.example.data.model.InstallmentPayment::class,
        com.example.data.model.InstallmentPaymentAllocation::class
    ],
    version = 11,
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
    abstract fun installmentPlanDao(): InstallmentPlanDao
    abstract fun recurringScheduleDao(): RecurringScheduleDao
    abstract fun installmentPeriodDao(): InstallmentPeriodDao
    abstract fun installmentPaymentDao(): InstallmentPaymentDao
    abstract fun installmentPaymentAllocationDao(): InstallmentPaymentAllocationDao

    open val writeCoordinator: DatabaseWriteCoordinator
        get() = DatabaseWriteCoordinator.getInstance()

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
        val MIGRATION_7_8: Migration = Migration7To8
        val MIGRATION_8_9: Migration = Migration8To9
        val MIGRATION_9_10: Migration = Migration9To10()
        val MIGRATION_10_11: Migration = Migration10To11()

        /**
         * Registry of explicit Room migrations.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11
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
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.setForeignKeyConstraintsEnabled(true)


                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        ensureReviewerAccount(database)
                    }
                }
            }

            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                        ensureReviewerAccount(database)
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
         * Atomically deletes a user account and cascades deletion to all user-owned records (DEF-03 Stage 3).
         * Room ON DELETE CASCADE removes all child financial records (wallets, transactions, budgets, debts, assets, etc.).
         * Other users, their records, system categories, and legacy quarantined rows remain completely intact.
         * Never invokes clearAllTables().
         */
        /**
         * Atomically deletes a user account and cascades deletion to all user-owned records (DEF-03 Stage 3).
         * P0: Explicitly deletes child entities with RESTRICT foreign keys in strict order:
         * 1. installment_payment_allocations (RESTRICT to payments and periods)
         * 2. installment_payments (RESTRICT to plans and transactions)
         * 3. installment_periods (CASCADE to plans)
         * 4. installment_plans (CASCADE to user)
         * 5. recurring_schedules (CASCADE to user & wallets)
         * 6. Target user (Room ON DELETE CASCADE removes wallets, transactions, debts, assets, budgets, categories).
         * Other users, their records, system categories, and legacy quarantined rows remain completely intact.
         * Never invokes clearAllTables().
         */
        suspend fun deleteUserAccountInTransaction(database: FinSmartDatabase, user: User) {
            database.writeCoordinator.withWritePermit {
                database.withTransaction {
                    val userId = user.id
                    val dbHelper = database.openHelper.writableDatabase
                    dbHelper.execSQL("DELETE FROM installment_payment_allocations WHERE userId = $userId")
                    dbHelper.execSQL("DELETE FROM installment_payments WHERE userId = $userId")
                    dbHelper.execSQL("DELETE FROM installment_periods WHERE userId = $userId")
                    dbHelper.execSQL("DELETE FROM installment_plans WHERE userId = $userId")
                    dbHelper.execSQL("DELETE FROM recurring_schedules WHERE userId = $userId")
                    database.userDao().deleteUser(user)
                }
            }
        }

        /**
         * P0: Controlled atomic developer account purge.
         * Verifies target identity using:
         * - Username: 'Lukas' (case-insensitive)
         * - Email: 'lunachroc07@gmail.com' (case-insensitive)
         * - Historic role: MASTER_DEV
         * Aborts safely if identity is ambiguous or multiple candidates found.
         * Performs ordered transactional deletion of RESTRICT children (installment allocations/payments),
         * cleans sample feedbacks, and removes user row.
         */
        suspend fun purgeTargetDeveloperAccountAtomically(database: FinSmartDatabase): Result<Boolean> {
            return database.writeCoordinator.withWritePermit {
                try {
                    val userDao = database.userDao()
                    val targetEmail = "lunachroc07@gmail.com"
                    val targetUsername = "Lukas"

                    val candidateByEmail = userDao.getUserByEmail(targetEmail)
                    val candidateByUsername = userDao.getUserByIdentifier(targetUsername)

                    // If neither candidate exists, clean orphan sample feedbacks and return success(false)
                    if (candidateByEmail == null && candidateByUsername == null) {
                        database.feedbackDao().deleteSampleFeedbackByEmailAndName(targetEmail, targetUsername)
                        return@withWritePermit Result.success(false)
                    }

                    // Ambiguity guard 1: If candidates exist by both email and username, they must refer to the exact same user ID
                    if (candidateByEmail != null && candidateByUsername != null && candidateByEmail.id != candidateByUsername.id) {
                        return@withWritePermit Result.failure(
                            IllegalStateException("Purge aborted: multiple distinct candidate users found for target identity ($targetUsername vs $targetEmail).")
                        )
                    }

                    // Ambiguity guard 2: If found by email, username must match targetUsername
                    if (candidateByEmail != null && !candidateByEmail.username.equals(targetUsername, ignoreCase = true)) {
                        return@withWritePermit Result.failure(
                            IllegalStateException("Purge aborted: user with email '$targetEmail' has mismatched username '${candidateByEmail.username}'.")
                        )
                    }

                    // Ambiguity guard 3: If found by username, email must match targetEmail
                    if (candidateByUsername != null && !candidateByUsername.email.equals(targetEmail, ignoreCase = true)) {
                        return@withWritePermit Result.failure(
                            IllegalStateException("Purge aborted: user with username '$targetUsername' has mismatched email '${candidateByUsername.email}'.")
                        )
                    }

                    val targetUser = candidateByEmail ?: candidateByUsername!!

                    // Target identity guard 4: User role must strictly match MASTER_DEV
                    if (targetUser.role != com.example.data.model.UserRole.MASTER_DEV) {
                        return@withWritePermit Result.failure(
                            IllegalStateException("Purge aborted: user '$targetUsername' has mismatched role '${targetUser.role}' (expected MASTER_DEV).")
                        )
                    }

                    val targetUserId = targetUser.id

                    database.withTransaction {
                        val dbHelper = database.openHelper.writableDatabase
                        // Ordered transactional deletion of RESTRICT foreign key children
                        dbHelper.execSQL("DELETE FROM installment_payment_allocations WHERE userId = $targetUserId")
                        dbHelper.execSQL("DELETE FROM installment_payments WHERE userId = $targetUserId")
                        dbHelper.execSQL("DELETE FROM installment_periods WHERE userId = $targetUserId")
                        dbHelper.execSQL("DELETE FROM installment_plans WHERE userId = $targetUserId")
                        dbHelper.execSQL("DELETE FROM recurring_schedules WHERE userId = $targetUserId")
                        dbHelper.execSQL("DELETE FROM feedbacks WHERE userId = $targetUserId OR (userId IS NULL AND LOWER(userEmail) = LOWER('$targetEmail') AND LOWER(userName) = LOWER('$targetUsername'))")

                        // Delete target user row (cascades to wallets, transactions, debts, assets, budgets, categories)
                        userDao.deleteUser(targetUser)

                        // Post-purge database verification inside transaction: database is source of truth
                        if (userDao.getUserById(targetUserId) != null ||
                            userDao.getUserByEmail(targetEmail) != null ||
                            userDao.getUserByIdentifier(targetUsername) != null) {
                            throw IllegalStateException("Purge verification failed: target user still exists in database after deletion.")
                        }
                    }

                    // Clear active session and saved credentials if they belonged to target user
                    if (com.example.data.session.SessionManager.getActiveUserId() == targetUserId) {
                        com.example.data.session.SessionManager.logout()
                    }

                    android.util.Log.i("FinSmartDatabase", "Target developer account '$targetUsername' ($targetUserId) purged atomically and verified.")
                    Result.success(true)
                } catch (e: Exception) {
                    android.util.Log.e("FinSmartDatabase", "Purge failed and transaction rolled back: ${e.message}", e)
                    Result.failure(e)
                }
            }
        }

        suspend fun populateInitialData(
            database: FinSmartDatabase,
            isDebug: Boolean = com.example.BuildConfig.DEBUG
        ) {
            database.writeCoordinator.withWritePermit {
                val categoryDao = database.categoryDao()

                // Canonical system categories are ALWAYS seeded for all builds (Release & Debug)
                if (categoryDao.getCategoryCount() == 0) {
                    categoryDao.insertCategories(DEFAULT_CATEGORIES)
                }
            }
        }

        /**
         * Ensures canonical categories exist and verified developer accounts have MASTER_DEV role.
         * Phase 1G: Lukas and default wallets are permanently deleted and never auto-provisioned.
         */
        suspend fun ensureMasterDevAccount(
            database: FinSmartDatabase,
            isDebug: Boolean = com.example.BuildConfig.DEBUG
        ): Long {
            return database.writeCoordinator.withWritePermit {
                val userDao = database.userDao()
                val categoryDao = database.categoryDao()

                // Ensure canonical system categories exist
                if (categoryDao.getCategoryCount() == 0) {
                    categoryDao.insertCategories(DEFAULT_CATEGORIES)
                }

                if (!isDebug) {
                    return@withWritePermit 0L
                }

                // Ensure all trusted developer emails present in database possess verified MASTER_DEV role
                var primaryDevId = 0L
                for (devEmail in com.example.config.DeveloperAccountConfig.TRUSTED_DEVELOPER_EMAILS) {
                    val devUser = userDao.getUserByEmail(devEmail)
                    if (devUser != null) {
                        if (devUser.role != com.example.data.model.UserRole.MASTER_DEV || devUser.subscriptionExpiresAt != null) {
                            userDao.updateUserRole(devUser.id, com.example.data.model.UserRole.MASTER_DEV, null)
                        }
                        if (primaryDevId == 0L) primaryDevId = devUser.id
                    }
                }

                primaryDevId
            }
        }


        /**
         * Provisions the dedicated Google Play Reviewer account idempotently.
         * Safe for Release builds: provides isolated synthetic data with SMART_MASTER entitlement,
         * without developer privileges or exposure of real user data.
         */
        suspend fun ensureReviewerAccount(database: FinSmartDatabase) {
            database.writeCoordinator.withWritePermit {
                val userDao = database.userDao()
            val existing = userDao.getUserByIdentifier(ReviewerAccountConfig.REVIEWER_EMAIL)
                ?: userDao.getUserByIdentifier(ReviewerAccountConfig.REVIEWER_USERNAME)

            val reviewerId = if (existing != null) {
                // Ensure role and entitlement remain SMART_MASTER without expiry
                if (existing.role != UserRole.SMART_MASTER || existing.subscriptionExpiresAt != null) {
                    userDao.updateUserRole(existing.id, UserRole.SMART_MASTER, null)
                }
                existing.id
            } else {
                val candidateId = if (userDao.getUserById(ReviewerAccountConfig.DEFAULT_REVIEWER_USER_ID) == null) {
                    ReviewerAccountConfig.DEFAULT_REVIEWER_USER_ID
                } else {
                    0L // auto-generate primary key to prevent collision with existing user ID
                }
                val newReviewer = User(
                    id = candidateId,
                    username = ReviewerAccountConfig.REVIEWER_USERNAME,
                    email = ReviewerAccountConfig.REVIEWER_EMAIL,
                    passwordHash = ReviewerAccountConfig.REVIEWER_PASSWORD_HASH,
                    role = UserRole.SMART_MASTER,
                    subscriptionExpiresAt = null,
                    isApproved = true,
                    isGuest = false
                )
                val insertedId = userDao.insertUser(newReviewer)
                if (insertedId > 0L) insertedId else candidateId
            }

            // Provision synthetic wallets if none exist for this reviewer
            val walletDao = database.walletDao()
            if (walletDao.getWalletCount(reviewerId) == 0) {
                val reviewerWallets = listOf(
                    Wallet(
                        name = "Dompet Utama (BCA)",
                        type = WalletType.BANK,
                        accountNumber = "8899001122",
                        balance = 25000000.0,
                        colorHex = "#0B57D0",
                        iconName = "bca",
                        accountMode = AccountMode.PERSONAL.name,
                        isDefault = true,
                        userId = reviewerId
                    ),
                    Wallet(
                        name = "E-Wallet (GoPay)",
                        type = WalletType.EWALLET,
                        accountNumber = "081199887766",
                        balance = 1500000.0,
                        colorHex = "#00897B",
                        iconName = "gopay",
                        accountMode = AccountMode.PERSONAL.name,
                        isDefault = false,
                        userId = reviewerId
                    ),
                    Wallet(
                        name = "Uang Tunai Reviewer",
                        type = WalletType.CASH,
                        accountNumber = "",
                        balance = 500000.0,
                        colorHex = "#10B981",
                        iconName = "cash",
                        accountMode = AccountMode.PERSONAL.name,
                        isDefault = false,
                        userId = reviewerId
                    ),
                    Wallet(
                        name = "Rekening Bisnis (Mandiri)",
                        type = WalletType.BANK,
                        accountNumber = "7766554433",
                        balance = 50000000.0,
                        colorHex = "#D97706",
                        iconName = "mandiri",
                        accountMode = AccountMode.BUSINESS.name,
                        isDefault = true,
                        userId = reviewerId
                    )
                )
                walletDao.insertWallets(reviewerWallets)
            }

            // Provision synthetic transactions if none exist for this reviewer
            val transactionDao = database.transactionDao()
            if (transactionDao.getTransactionCount(reviewerId) == 0) {
                val personalWallets = walletDao.getAllWallets(reviewerId).first()
                val primaryWallet = personalWallets.firstOrNull { it.isDefault && it.accountMode == AccountMode.PERSONAL.name }
                    ?: personalWallets.firstOrNull()
                val walletId = primaryWallet?.id ?: 0L
                val walletName = primaryWallet?.name ?: "Dompet Utama (BCA)"

                val initialTransactions = listOf(
                    TransactionItem(
                        title = "Gaji Pokok Bulanan",
                        amount = 15000000.0,
                        type = TransactionType.INCOME,
                        category = "Gaji Pokok",
                        categoryIcon = "payments",
                        walletId = walletId,
                        walletName = walletName,
                        accountMode = AccountMode.PERSONAL.name,
                        userId = reviewerId
                    ),
                    TransactionItem(
                        title = "Makan Siang & Kopi",
                        amount = 75000.0,
                        type = TransactionType.EXPENSE,
                        category = "Makanan & Minuman",
                        categoryIcon = "restaurant",
                        walletId = walletId,
                        walletName = walletName,
                        accountMode = AccountMode.PERSONAL.name,
                        userId = reviewerId
                    ),
                    TransactionItem(
                        title = "Bensin Kendaraan",
                        amount = 100000.0,
                        type = TransactionType.EXPENSE,
                        category = "Transportasi & Bensin",
                        categoryIcon = "commute",
                        walletId = walletId,
                        walletName = walletName,
                        accountMode = AccountMode.PERSONAL.name,
                        userId = reviewerId
                    )
                )
                initialTransactions.forEach { transactionDao.insertTransaction(it) }
            }

            // Provision synthetic debts if none exist for reviewer
            val debtDao = database.debtDao()
            if (debtDao.getDebtCount(reviewerId) == 0) {
                val reviewerDebts = listOf(
                    DebtItem(
                        name = "Kartu Kredit Bank Reviewer",
                        amount = 5000000.0,
                        interestRatePercent = 15.0,
                        minMonthlyPayment = 500000.0,
                        category = "Kartu Kredit",
                        dueDate = "Tgl 25 tiap bulan",
                        userId = reviewerId
                    )
                )
                debtDao.insertDebts(reviewerDebts)
            }

            // Provision synthetic assets if none exist for reviewer
            val assetDao = database.assetDao()
            if (assetDao.getAssetCount(reviewerId) == 0) {
                val reviewerAssets = listOf(
                    AssetItem(
                        name = "Laptop Kerja Reviewer",
                        initialValue = 18000000.0,
                        purchaseYear = 2024,
                        category = AssetCategory.ELECTRONICS,
                        userId = reviewerId
                    ),
                    AssetItem(
                        name = "Logam Mulia Antam 5g",
                        initialValue = 6500000.0,
                        purchaseYear = 2023,
                        category = AssetCategory.GOLD_INVESTMENT,
                        userId = reviewerId
                    )
                )
                assetDao.insertAssets(reviewerAssets)
            }

            // Provision synthetic budget limits if none exist for reviewer
            val budgetLimitDao = database.budgetLimitDao()
            if (budgetLimitDao.getBudgetLimitCount(reviewerId) == 0) {
                budgetLimitDao.insertBudgetLimit(
                    BudgetLimitItem(
                        id = "total_monthly",
                        limitAmount = 10000000.0,
                        isCategory = false,
                        userId = reviewerId
                    )
                )
                budgetLimitDao.insertBudgetLimit(
                    BudgetLimitItem(
                        id = "Makanan & Minuman",
                        limitAmount = 3000000.0,
                        isCategory = true,
                        userId = reviewerId
                    )
                )
            }
            }
        }
    }
}