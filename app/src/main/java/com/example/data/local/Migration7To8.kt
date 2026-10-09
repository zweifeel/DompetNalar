package com.example.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 7 to 8:
 * Adds `installment_plans` table for recurring installment plans / tenor contracts.
 * Strictly scopes records with nullable `userId INTEGER` and foreign key cascade to `users(id)`.
 */
object Migration7To8 : Migration(7, 8) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
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

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_plans_userId` ON `installment_plans` (`userId`)")
    }
}
