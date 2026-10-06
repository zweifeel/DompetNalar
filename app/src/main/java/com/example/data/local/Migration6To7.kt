package com.example.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * DEF-03 (Room v7 Migration):
 *
 * 1. Wallets: recreates table, adds nullable `userId INTEGER`, FK -> users(id) ON DELETE CASCADE,
 *    creates index on `userId`. Preserves all legacy rows with `userId = NULL`.
 *
 * 2. Transactions: recreates table, adds nullable `userId INTEGER`, FK -> users(id) ON DELETE CASCADE,
 *    creates index on `userId`. Preserves all legacy rows with `userId = NULL`. Preserves walletId/targetWalletId values.
 *
 * 3. Budget Limits: recreates table with surrogate primary key `localId INTEGER PRIMARY KEY AUTOINCREMENT`,
 *    renames `id` column to `categoryOrTotalId TEXT NOT NULL`, adds nullable `userId INTEGER`,
 *    FK -> users(id) ON DELETE CASCADE, creates index on `userId` and unique index on `(userId, categoryOrTotalId)`.
 *    Preserves all legacy rows with `userId = NULL`.
 *
 * 4. Categories: recreates table, adds nullable `userId INTEGER` and `isSystem INTEGER NOT NULL`,
 *    FK -> users(id) ON DELETE CASCADE, creates indexes on `userId` and `isSystem`.
 *    Preserves all existing legacy rows with `isSystem = 0, userId = NULL`.
 *    Inserts the 13 canonical system categories with `isSystem = 1, userId = NULL`.
 *
 * 5. Feedbacks: recreates table, adds nullable `userId INTEGER`, FK -> users(id) ON DELETE CASCADE,
 *    creates index on `userId`.
 *    Ownership resolution rule: if feedback.userEmail matches exactly one user in `users`
 *    (case-insensitive), assigns `userId = user.id`. Otherwise leaves `userId = NULL`.
 *
 * 6. Debts: recreates table, adds nullable `userId INTEGER`, FK -> users(id) ON DELETE CASCADE,
 *    creates index on `userId`. Preserves legacy rows with `userId = NULL`.
 *
 * 7. Assets: recreates table, adds nullable `userId INTEGER`, FK -> users(id) ON DELETE CASCADE,
 *    creates index on `userId`. Preserves legacy rows with `userId = NULL`.
 *
 * 8. Split Bill History: recreates table, adds nullable `userId INTEGER`, FK -> users(id) ON DELETE CASCADE,
 *    creates index on `userId`. Preserves legacy rows with `userId = NULL`.
 */
object Migration6To7 : Migration(6, 7) {

    override fun migrate(db: SupportSQLiteDatabase) {
        // --- 1. WALLETS ---
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `wallets_new` (
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

        db.execSQL("""
            INSERT INTO `wallets_new` (
                `id`, `name`, `type`, `accountNumber`, `balance`, `colorHex`, `iconName`, `accountMode`, `isDefault`, `createdAt`, `userId`
            )
            SELECT
                `id`, `name`, `type`, `accountNumber`, `balance`, `colorHex`, `iconName`, `accountMode`, `isDefault`, `createdAt`, NULL
            FROM `wallets`
        """.trimIndent())

        db.execSQL("DROP TABLE `wallets`")
        db.execSQL("ALTER TABLE `wallets_new` RENAME TO `wallets`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_wallets_userId` ON `wallets` (`userId`)")

        // --- 2. TRANSACTIONS ---
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `transactions_new` (
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

        db.execSQL("""
            INSERT INTO `transactions_new` (
                `id`, `title`, `amount`, `type`, `category`, `categoryIcon`, `walletId`, `walletName`, `targetWalletId`, `targetWalletName`, `timestamp`, `note`, `tags`, `accountMode`, `userId`
            )
            SELECT
                `id`, `title`, `amount`, `type`, `category`, `categoryIcon`, `walletId`, `walletName`, `targetWalletId`, `targetWalletName`, `timestamp`, `note`, `tags`, `accountMode`, NULL
            FROM `transactions`
        """.trimIndent())

        db.execSQL("DROP TABLE `transactions`")
        db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_userId` ON `transactions` (`userId`)")

        // --- 3. CATEGORIES ---
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `categories_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `iconName` TEXT NOT NULL,
                `colorHex` TEXT NOT NULL,
                `userId` INTEGER,
                `isSystem` INTEGER NOT NULL,
                FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())

        // Preserve all legacy category rows as isSystem = 0, userId = NULL
        db.execSQL("""
            INSERT INTO `categories_new` (
                `id`, `name`, `type`, `iconName`, `colorHex`, `userId`, `isSystem`
            )
            SELECT
                `id`, `name`, `type`, `iconName`, `colorHex`, NULL, 0
            FROM `categories`
        """.trimIndent())

        db.execSQL("DROP TABLE `categories`")
        db.execSQL("ALTER TABLE `categories_new` RENAME TO `categories`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_userId` ON `categories` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_isSystem` ON `categories` (`isSystem`)")

        // Insert the 13 canonical system categories (isSystem = 1, userId = NULL)
        insertCanonicalSystemCategories(db)

        // --- 4. BUDGET LIMITS ---
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `budget_limits_new` (
                `categoryOrTotalId` TEXT NOT NULL,
                `limitAmount` REAL NOT NULL,
                `isCategory` INTEGER NOT NULL,
                `userId` INTEGER,
                `localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO `budget_limits_new` (
                `categoryOrTotalId`, `limitAmount`, `isCategory`, `userId`
            )
            SELECT
                `id`, `limitAmount`, `isCategory`, NULL
            FROM `budget_limits`
        """.trimIndent())

        db.execSQL("DROP TABLE `budget_limits`")
        db.execSQL("ALTER TABLE `budget_limits_new` RENAME TO `budget_limits`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_limits_userId` ON `budget_limits` (`userId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budget_limits_userId_categoryOrTotalId` ON `budget_limits` (`userId`, `categoryOrTotalId`)")

        // --- 5. FEEDBACKS ---
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `feedbacks_new` (
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

        // Assign userId ONLY if userEmail matches exactly one user in users (case-insensitive)
        db.execSQL("""
            INSERT INTO `feedbacks_new` (
                `id`, `userName`, `userEmail`, `rating`, `category`, `message`, `timestamp`, `userId`
            )
            SELECT
                f.`id`,
                f.`userName`,
                f.`userEmail`,
                f.`rating`,
                f.`category`,
                f.`message`,
                f.`timestamp`,
                (
                    SELECT u.`id`
                    FROM `users` u
                    WHERE LOWER(u.`email`) = LOWER(f.`userEmail`)
                    GROUP BY LOWER(u.`email`)
                    HAVING COUNT(*) = 1
                ) AS `userId`
            FROM `feedbacks` f
        """.trimIndent())

        db.execSQL("DROP TABLE `feedbacks`")
        db.execSQL("ALTER TABLE `feedbacks_new` RENAME TO `feedbacks`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_feedbacks_userId` ON `feedbacks` (`userId`)")

        // --- 6. DEBTS ---
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `debts_new` (
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

        db.execSQL("""
            INSERT INTO `debts_new` (
                `id`, `name`, `amount`, `interestRatePercent`, `minMonthlyPayment`, `category`, `dueDate`, `createdAt`, `userId`
            )
            SELECT
                `id`, `name`, `amount`, `interestRatePercent`, `minMonthlyPayment`, `category`, `dueDate`, `createdAt`, NULL
            FROM `debts`
        """.trimIndent())

        db.execSQL("DROP TABLE `debts`")
        db.execSQL("ALTER TABLE `debts_new` RENAME TO `debts`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_userId` ON `debts` (`userId`)")

        // --- 7. ASSETS ---
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `assets_new` (
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

        db.execSQL("""
            INSERT INTO `assets_new` (
                `id`, `name`, `initialValue`, `purchaseYear`, `category`, `createdAt`, `userId`
            )
            SELECT
                `id`, `name`, `initialValue`, `purchaseYear`, `category`, `createdAt`, NULL
            FROM `assets`
        """.trimIndent())

        db.execSQL("DROP TABLE `assets`")
        db.execSQL("ALTER TABLE `assets_new` RENAME TO `assets`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_userId` ON `assets` (`userId`)")

        // --- 8. SPLIT BILL HISTORY ---
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `split_bill_history_new` (
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

        db.execSQL("""
            INSERT INTO `split_bill_history_new` (
                `id`, `merchantName`, `date`, `totalAmount`, `formattedText`, `timestamp`, `userId`
            )
            SELECT
                `id`, `merchantName`, `date`, `totalAmount`, `formattedText`, `timestamp`, NULL
            FROM `split_bill_history`
        """.trimIndent())

        db.execSQL("DROP TABLE `split_bill_history`")
        db.execSQL("ALTER TABLE `split_bill_history_new` RENAME TO `split_bill_history`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_split_bill_history_userId` ON `split_bill_history` (`userId`)")
    }

    private fun insertCanonicalSystemCategories(db: SupportSQLiteDatabase) {
        val canonicals = listOf(
            Triple("Makanan & Minuman", "EXPENSE", Pair("restaurant", "#EF4444")),
            Triple("Transportasi & Bensin", "EXPENSE", Pair("commute", "#F59E0B")),
            Triple("Belanja & Hobi", "EXPENSE", Pair("shopping_bag", "#EC4899")),
            Triple("Tagihan & Utilitas", "EXPENSE", Pair("receipt", "#8B5CF6")),
            Triple("Kesehatan", "EXPENSE", Pair("medical_services", "#06B6D4")),
            Triple("Pendidikan", "EXPENSE", Pair("school", "#3B82F6")),
            Triple("Hiburan & Langganan", "EXPENSE", Pair("movie", "#6366F1")),
            Triple("Lain-lain", "EXPENSE", Pair("more_horiz", "#64748B")),
            Triple("Gaji Pokok", "INCOME", Pair("payments", "#10B981")),
            Triple("Bonus & Tunjangan", "INCOME", Pair("card_giftcard", "#059669")),
            Triple("Investasi & Dividen", "INCOME", Pair("trending_up", "#047857")),
            Triple("Penjualan / Omset", "INCOME", Pair("store", "#0D9488")),
            Triple("Pemasukan Lain", "INCOME", Pair("account_balance_wallet", "#0284C7"))
        )

        for (c in canonicals) {
            val name = c.first.replace("'", "''")
            val type = c.second
            val icon = c.third.first
            val color = c.third.second
            db.execSQL(
                "INSERT INTO `categories` (`name`, `type`, `iconName`, `colorHex`, `userId`, `isSystem`) " +
                "VALUES ('$name', '$type', '$icon', '$color', NULL, 1)"
            )
        }
    }
}
