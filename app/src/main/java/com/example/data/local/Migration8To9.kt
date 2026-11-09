package com.example.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 8 to 9 (DEF-04):
 * Adds non-destructive `isSynced` INTEGER column (default 0) to `feedbacks` table.
 * Preserves all existing local user feedbacks and ensures seamless backward compatibility.
 */
object Migration8To9 : Migration(8, 9) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `feedbacks` ADD COLUMN `isSynced` INTEGER NOT NULL DEFAULT 0")
    }
}
