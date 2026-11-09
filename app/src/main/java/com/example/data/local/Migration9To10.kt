package com.example.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.util.DeviceRegionResolver
import java.util.Currency
import java.util.Locale

/**
 * Room 9 to Room 10 Database Migration.
 *
 * @param systemLocaleCountryProvider Lambda providing device regional ISO 3166 country code,
 *                                    read directly from Resources.getSystem() to ensure complete
 *                                    independence from in-app display language overrides.
 */
class Migration9To10(
    private val systemLocaleCountryProvider: () -> String? = { DeviceRegionResolver.resolveDeviceCountry() }
) : Migration(9, 10) {

    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Add idempotencyKey to transactions table and create unique index
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `idempotencyKey` TEXT DEFAULT NULL")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_idempotencyKey` ON `transactions` (`idempotencyKey`)")

        // 2. Add isArchived and currencyCode to wallets table
        // CRITICAL: currencyCode is TEXT DEFAULT NULL (NO NOT NULL DEFAULT 'IDR' or arbitrary fallback)
        db.execSQL("ALTER TABLE `wallets` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `wallets` ADD COLUMN `currencyCode` TEXT DEFAULT NULL")

        // 3. Resolve regional currency safely via system locale provider
        val country = systemLocaleCountryProvider()
        val resolvedCurrencyCode = if (!country.isNullOrBlank()) {
            try {
                val curr = Currency.getInstance(Locale("", country))
                if (curr.defaultFractionDigits >= 0) curr.currencyCode else null
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        // 4. Conditional Regional Backfill:
        // - VALID REGION: Atomically backfill all existing wallets with resolved code
        // - INVALID / MISSING REGION: Skip update; existing wallets remain NULL. Zero arbitrary default!
        if (resolvedCurrencyCode != null) {
            db.execSQL("UPDATE `wallets` SET `currencyCode` = ?", arrayOf(resolvedCurrencyCode))
        }

        // 5. Create composite unique index on wallets(id, userId) to support composite FK
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_wallets_id_userId` ON `wallets` (`id`, `userId`)")

        // 6. Create recurring_schedules table with complete timing contract and composite FK
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `recurring_schedules` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `title` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `type` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `categoryIcon` TEXT NOT NULL,
                `walletId` INTEGER NOT NULL,
                `walletName` TEXT NOT NULL,
                `interval` TEXT NOT NULL,
                `dayOfMonth` INTEGER NOT NULL DEFAULT 1,
                `dayOfWeek` INTEGER NOT NULL DEFAULT 1,
                `executionHour` INTEGER NOT NULL DEFAULT 9,
                `executionMinute` INTEGER NOT NULL DEFAULT 0,
                `startDate` INTEGER NOT NULL,
                `timezoneId` TEXT NOT NULL DEFAULT 'UTC',
                `version` INTEGER NOT NULL DEFAULT 1,
                `isEnabled` INTEGER NOT NULL DEFAULT 1,
                `lastExecutedCycle` TEXT DEFAULT NULL,
                `createdAt` INTEGER NOT NULL,
                `userId` INTEGER NOT NULL,
                FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`walletId`, `userId`) REFERENCES `wallets`(`id`, `userId`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_schedules_userId` ON `recurring_schedules` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_schedules_walletId_userId` ON `recurring_schedules` (`walletId`, `userId`)")
    }
}
