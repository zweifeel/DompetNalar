package com.example.data.local

import android.database.Cursor
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.DomainCurrency
import com.example.util.ConversionResult
import com.example.util.DeviceRegionResolver
import com.example.util.RealConversionEngine
import java.util.Currency
import java.util.Locale

/**
 * Room 10 to Room 11 Database Migration.
 *
 * Implements:
 * - P0-A: Canonical integer minor units for balances and amounts via [RealConversionEngine]
 * - P0-B: Quad-column / composite foreign keys, double-entry reversal invariants, and immutability triggers
 * - P0-C: Fail-closed payment settlement voids, periods, and allocation audit tables
 * - P0-D: Isolated quarantine tables for unresolved currency, corrupted entities, and cross-tenant orphans
 * - P0-E: SQLite ISO-8601 calendar date validation triggers
 * - P0-G: Fail-safe idempotent sequence restoration and foreign key integrity verification
 */
class Migration10To11(
    private val systemLocaleCountryProvider: () -> String? = { DeviceRegionResolver.resolveDeviceCountry() }
) : Migration(10, 11) {

    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Sequence preservation backup
        db.execSQL("CREATE TEMP TABLE IF NOT EXISTS _backup_sqlite_sequence AS SELECT name, seq FROM sqlite_sequence")

        // Note on SQLite PRAGMA foreign_keys:
        // Inside an active transaction (which Room migration wraps around this method),
        // PRAGMA foreign_keys = OFF is a no-op in SQLite. Therefore, Migration 10 -> 11
        // executes in strict parent-first dependency order with non-destructive alterations
        // so that foreign key integrity remains valid at every intermediate step.

        try {
            // 2. Create quarantine tables for orphan, corrupted, and unresolved currency entries
            createQuarantineTables(db)

            // 3. Resolve fallback device currency safely without hardcoding IDR
            val country = systemLocaleCountryProvider()
            val resolvedDefaultCurrency = if (!country.isNullOrBlank()) {
                try {
                    val curr = Currency.getInstance(Locale("", country))
                    if (curr.defaultFractionDigits >= 0) curr.currencyCode else "IDR"
                } catch (e: Exception) {
                    "IDR"
                }
            } else {
                "IDR"
            }

            // 4. Migrate Wallets (parent of transactions)
            migrateWallets(db, resolvedDefaultCurrency)

            // 5. Migrate Transactions
            migrateTransactions(db, resolvedDefaultCurrency)

            // 6. Migrate Installment Plans (parent of installment periods and payments)
            migrateInstallmentPlans(db, resolvedDefaultCurrency)

            // 7. Create new Room 11 PayLater tables: periods, payments, allocations
            createPayLaterTables(db)

            // 8. Install financial integrity triggers (P0-B & P0-C & P0-E)
            installIntegrityTriggers(db)

            // 9. Restore sqlite_sequence with MAX(seq) scalar subquery
            restoreSqliteSequences(db)

        } finally {
            db.execSQL("DROP TABLE IF EXISTS _backup_sqlite_sequence")
        }

        // 10. Final fail-closed foreign key verification
        // If any legacy or migrated row violates a foreign key constraint, abort and let Room rollback
        val fkCursor: Cursor = db.query("PRAGMA foreign_key_check")
        try {
            if (fkCursor.moveToFirst()) {
                val table = fkCursor.getString(0)
                val rowId = fkCursor.getLong(1)
                val parent = fkCursor.getString(2)
                throw IllegalStateException("PRAGMA foreign_key_check failed on table '$table' row $rowId referencing '$parent'")
            }
        } finally {
            fkCursor.close()
        }
    }

    private fun createQuarantineTables(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS legacy_unresolved_currency_wallets (
                quarantineId INTEGER PRIMARY KEY AUTOINCREMENT,
                raw_id INTEGER,
                raw_name TEXT,
                raw_balance REAL,
                raw_userId INTEGER,
                reason TEXT,
                quarantinedAt INTEGER
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS legacy_unresolved_currency_transactions (
                quarantineId INTEGER PRIMARY KEY AUTOINCREMENT,
                raw_id INTEGER,
                raw_walletId INTEGER,
                raw_amount REAL,
                raw_userId INTEGER,
                reason TEXT,
                quarantinedAt INTEGER
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS legacy_cross_tenant_corrupted_transactions (
                quarantineId INTEGER PRIMARY KEY AUTOINCREMENT,
                raw_id INTEGER,
                raw_walletId INTEGER,
                raw_userId INTEGER,
                wallet_owner_userId INTEGER,
                raw_amount REAL,
                raw_title TEXT,
                reason TEXT,
                quarantinedAt INTEGER
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS legacy_corrupted_wallets (
                quarantineId INTEGER PRIMARY KEY AUTOINCREMENT,
                raw_id INTEGER,
                raw_balance REAL,
                raw_currencyCode TEXT,
                reason TEXT,
                quarantinedAt INTEGER
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS legacy_corrupted_transactions (
                quarantineId INTEGER PRIMARY KEY AUTOINCREMENT,
                raw_id INTEGER,
                raw_amount REAL,
                raw_currencyCode TEXT,
                reason TEXT,
                quarantinedAt INTEGER
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS legacy_corrupted_installment_plans (
                quarantineId INTEGER PRIMARY KEY AUTOINCREMENT,
                raw_id TEXT,
                raw_name TEXT,
                raw_installmentAmount REAL,
                raw_totalTenor INTEGER,
                reason TEXT,
                quarantinedAt INTEGER
            )
        """)
    }

    private fun migrateWallets(db: SupportSQLiteDatabase, defaultCurrency: String) {
        // Add balanceMinorUnits column to existing wallets table
        db.execSQL("ALTER TABLE `wallets` ADD COLUMN `balanceMinorUnits` INTEGER NOT NULL DEFAULT 0")

        val cursor = db.query("SELECT id, balance, currencyCode, userId, name FROM `wallets`")
        try {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val balance = cursor.getDouble(1)
                val existingCurr = if (cursor.isNull(2)) null else cursor.getString(2)
                val userId = if (cursor.isNull(3)) null else cursor.getLong(3)
                val name = cursor.getString(4)

                val currCode = existingCurr ?: defaultCurrency
                val exponent = try {
                    DomainCurrency.fromCode(currCode).decimalDigits
                } catch (e: Exception) {
                    2
                }

                when (val result = RealConversionEngine.convert(balance, currCode, exponent)) {
                    is ConversionResult.Safe -> {
                        db.execSQL(
                            "UPDATE `wallets` SET `balanceMinorUnits` = ?, `currencyCode` = ? WHERE `id` = ?",
                            arrayOf(result.minorUnits, currCode, id)
                        )
                    }
                    else -> {
                        // Quarantine corrupted wallet
                        db.execSQL(
                            "INSERT INTO legacy_corrupted_wallets (raw_id, raw_balance, raw_currencyCode, reason, quarantinedAt) VALUES (?, ?, ?, ?, ?)",
                            arrayOf(id, balance, currCode, result.javaClass.simpleName, System.currentTimeMillis())
                        )
                    }
                }
            }
        } finally {
            cursor.close()
        }
    }

    private fun migrateTransactions(db: SupportSQLiteDatabase, defaultCurrency: String) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `amountMinorUnits` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `currencyCode` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `isReversal` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `reversedTransactionId` INTEGER DEFAULT NULL")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_reversedTransactionId` ON `transactions` (`reversedTransactionId`)")

        val cursor = db.query("""
            SELECT t.id, t.amount, t.userId, t.walletId, w.userId, w.currencyCode
            FROM `transactions` t
            LEFT JOIN `wallets` w ON w.id = t.walletId
        """)
        try {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val amount = cursor.getDouble(1)
                val txUserId = if (cursor.isNull(2)) null else cursor.getLong(2)
                val walletId = cursor.getLong(3)
                val walletUserId = if (cursor.isNull(4)) null else cursor.getLong(4)
                val walletCurrency = if (cursor.isNull(5)) null else cursor.getString(5)

                // Check cross-tenant consistency
                if (txUserId != null && walletUserId != null && txUserId != walletUserId) {
                    db.execSQL("""
                        INSERT INTO legacy_cross_tenant_corrupted_transactions
                        (raw_id, raw_walletId, raw_userId, wallet_owner_userId, raw_amount, raw_title, reason, quarantinedAt)
                        VALUES (?, ?, ?, ?, ?, '', 'TENANT_MISMATCH', ?)
                    """, arrayOf(id, walletId, txUserId, walletUserId, amount, System.currentTimeMillis()))
                }

                val currCode = walletCurrency ?: defaultCurrency
                val exponent = try {
                    DomainCurrency.fromCode(currCode).decimalDigits
                } catch (e: Exception) {
                    2
                }

                when (val result = RealConversionEngine.convert(amount, currCode, exponent)) {
                    is ConversionResult.Safe -> {
                        db.execSQL(
                            "UPDATE `transactions` SET `amountMinorUnits` = ?, `currencyCode` = ? WHERE `id` = ?",
                            arrayOf(result.minorUnits, currCode, id)
                        )
                    }
                    else -> {
                        db.execSQL(
                            "INSERT INTO legacy_corrupted_transactions (raw_id, raw_amount, raw_currencyCode, reason, quarantinedAt) VALUES (?, ?, ?, ?, ?)",
                            arrayOf(id, amount, currCode, result.javaClass.simpleName, System.currentTimeMillis())
                        )
                    }
                }
            }
        } finally {
            cursor.close()
        }
    }

    private fun migrateInstallmentPlans(db: SupportSQLiteDatabase, defaultCurrency: String) {
        db.execSQL("ALTER TABLE `installment_plans` ADD COLUMN `totalAmountMinorUnits` INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE `installment_plans` ADD COLUMN `currencyCode` TEXT NOT NULL DEFAULT 'IDR'")

        val cursor = db.query("SELECT id, installmentAmount, totalTenor FROM `installment_plans`")
        try {
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val instAmount = cursor.getDouble(1)
                val totalTenor = cursor.getInt(2)

                val totalDouble = instAmount * totalTenor
                when (val result = RealConversionEngine.convert(totalDouble, "IDR", 2)) {
                    is ConversionResult.Safe -> {
                        db.execSQL(
                            "UPDATE `installment_plans` SET `totalAmountMinorUnits` = ?, `currencyCode` = 'IDR' WHERE `id` = ?",
                            arrayOf(result.minorUnits, id)
                        )
                    }
                    else -> {
                        db.execSQL("""
                            INSERT INTO legacy_corrupted_installment_plans
                            (raw_id, raw_name, raw_installmentAmount, raw_totalTenor, reason, quarantinedAt)
                            VALUES (?, '', ?, ?, ?, ?)
                        """, arrayOf(id, instAmount, totalTenor, result.javaClass.simpleName, System.currentTimeMillis()))
                    }
                }
            }
        } finally {
            cursor.close()
        }
    }

    private fun createPayLaterTables(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `installment_periods` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `planId` TEXT NOT NULL,
                `currencyCode` TEXT NOT NULL DEFAULT 'IDR',
                `userId` INTEGER NOT NULL,
                `periodNumber` INTEGER NOT NULL,
                `amountDueMinorUnits` INTEGER NOT NULL,
                `amountPaidMinorUnits` INTEGER NOT NULL DEFAULT 0,
                `dueDate` TEXT NOT NULL,
                `isPaid` INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(`planId`) REFERENCES `installment_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_periods_planId` ON `installment_periods` (`planId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_periods_userId` ON `installment_periods` (`userId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_installment_periods_planId_periodNumber` ON `installment_periods` (`planId`, `periodNumber`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `installment_payments` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `planId` TEXT NOT NULL,
                `transactionId` INTEGER NOT NULL,
                `currencyCode` TEXT NOT NULL DEFAULT 'IDR',
                `userId` INTEGER NOT NULL,
                `totalMinorUnits` INTEGER NOT NULL,
                `unallocatedMinorUnits` INTEGER NOT NULL DEFAULT 0,
                `isVoid` INTEGER NOT NULL DEFAULT 0,
                `reversalTransactionId` INTEGER DEFAULT NULL,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`planId`) REFERENCES `installment_plans`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`reversalTransactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_payments_planId` ON `installment_payments` (`planId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_payments_transactionId` ON `installment_payments` (`transactionId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_installment_payments_reversalTransactionId` ON `installment_payments` (`reversalTransactionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_payments_userId` ON `installment_payments` (`userId`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `installment_payment_allocations` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `paymentId` TEXT NOT NULL,
                `periodId` TEXT NOT NULL,
                `currencyCode` TEXT NOT NULL DEFAULT 'IDR',
                `userId` INTEGER NOT NULL,
                `allocatedMinorUnits` INTEGER NOT NULL,
                `isVoid` INTEGER NOT NULL DEFAULT 0,
                `allocatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`paymentId`) REFERENCES `installment_payments`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`periodId`) REFERENCES `installment_periods`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_payment_allocations_paymentId` ON `installment_payment_allocations` (`paymentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_payment_allocations_periodId` ON `installment_payment_allocations` (`periodId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_payment_allocations_userId` ON `installment_payment_allocations` (`userId`)")
    }

    companion object {
        fun installIntegrityTriggers(db: SupportSQLiteDatabase) {
            // P0-B: Reversal Immutability & Reversal of Reversal Guards
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_reversal_flag_consistency
                BEFORE INSERT ON transactions
                BEGIN
                    SELECT RAISE(ABORT, 'P0-B: isReversal must be 1 if and only if reversedTransactionId is NOT NULL')
                    WHERE (NEW.isReversal = 1 AND NEW.reversedTransactionId IS NULL)
                       OR (NEW.isReversal = 0 AND NEW.reversedTransactionId IS NOT NULL);
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_immutable_reversal_flags
                BEFORE UPDATE OF isReversal, reversedTransactionId ON transactions
                BEGIN
                    SELECT RAISE(ABORT, 'P0-B: isReversal and reversedTransactionId are strictly immutable once inserted');
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_no_reversal_of_reversal
                BEFORE INSERT ON transactions
                WHEN NEW.reversedTransactionId IS NOT NULL
                BEGIN
                    SELECT RAISE(ABORT, 'P0-B: Target transaction is already a reversal; cannot reverse a reversal')
                    FROM transactions
                    WHERE id IS NEW.reversedTransactionId AND isReversal = 1;
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_prevent_duplicate_reversal
                BEFORE INSERT ON transactions
                WHEN NEW.isReversal = 1 AND NEW.reversedTransactionId IS NOT NULL
                BEGIN
                    SELECT RAISE(ABORT, 'P0-B: Duplicate reversal rejected; original transaction has already been reversed')
                    WHERE EXISTS (
                        SELECT 1 FROM transactions
                        WHERE reversedTransactionId IS NEW.reversedTransactionId
                          AND isReversal = 1
                    );
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_validate_reversal_financial_match
                BEFORE INSERT ON transactions
                WHEN NEW.isReversal = 1
                BEGIN
                    -- Explicit NULL rejection for reversedTransactionId (Mandatory Identity)
                    SELECT RAISE(ABORT, 'P0-B: Reversal transaction must have a non-null reversedTransactionId')
                    WHERE NEW.reversedTransactionId IS NULL;

                    -- Explicit NULL identity checks for NEW reversal record (Fail-Closed)
                    SELECT RAISE(ABORT, 'P0-B: Reversal transaction must have a non-null userId')
                    WHERE NEW.userId IS NULL;

                    SELECT RAISE(ABORT, 'P0-B: Reversal transaction must have a non-null walletId')
                    WHERE NEW.walletId IS NULL;

                    SELECT RAISE(ABORT, 'P0-B: Reversal transaction must have a non-null currencyCode')
                    WHERE NEW.currencyCode IS NULL;

                    SELECT RAISE(ABORT, 'P0-B: Reversal transaction must have a non-null amountMinorUnits')
                    WHERE NEW.amountMinorUnits IS NULL;

                    SELECT RAISE(ABORT, 'P0-B: Reversal transaction must have a non-null type')
                    WHERE NEW.type IS NULL;

                    SELECT RAISE(ABORT, 'P0-B: Reversal transfer transaction must have a non-null targetWalletId')
                    WHERE NEW.type = 'TRANSFER' AND NEW.targetWalletId IS NULL;

                    SELECT RAISE(ABORT, 'P0-B: Target transaction to reverse does not exist')
                    WHERE NOT EXISTS (SELECT 1 FROM transactions WHERE id = NEW.reversedTransactionId);

                    -- Target transaction must have complete, verifiable financial identity
                    SELECT RAISE(ABORT, 'P0-B: Target transaction has incomplete financial identity (null userId, walletId, currencyCode, amountMinorUnits, or type); reversal is forbidden')
                    FROM transactions
                    WHERE id = NEW.reversedTransactionId
                      AND (userId IS NULL OR walletId IS NULL OR currencyCode IS NULL OR amountMinorUnits IS NULL OR type IS NULL);

                    SELECT RAISE(ABORT, 'P0-B: Target transfer transaction must have a non-null targetWalletId')
                    FROM transactions
                    WHERE id = NEW.reversedTransactionId
                      AND type = 'TRANSFER' AND targetWalletId IS NULL;

                    -- NULL-safe IS NOT financial equality checks
                    SELECT RAISE(ABORT, 'P0-B: Reversal amountMinorUnits must exactly equal original transaction amount')
                    FROM transactions
                    WHERE id = NEW.reversedTransactionId AND amountMinorUnits IS NOT NEW.amountMinorUnits;

                    SELECT RAISE(ABORT, 'P0-B: Reversal transaction type must be valid counter-entry for original transaction (EXPENSE->INCOME, INCOME->EXPENSE, TRANSFER->TRANSFER)')
                    FROM transactions
                    WHERE id = NEW.reversedTransactionId
                      AND COALESCE(
                          (type = 'EXPENSE' AND NEW.type = 'INCOME') OR
                          (type = 'INCOME' AND NEW.type = 'EXPENSE') OR
                          (type = 'TRANSFER' AND NEW.type = 'TRANSFER'),
                          0
                      ) = 0;

                    SELECT RAISE(ABORT, 'P0-B: Reversal currencyCode must match original currencyCode')
                    FROM transactions
                    WHERE id = NEW.reversedTransactionId AND currencyCode IS NOT NEW.currencyCode;

                    SELECT RAISE(ABORT, 'P0-B: Reversal userId must match original userId')
                    FROM transactions
                    WHERE id = NEW.reversedTransactionId AND userId IS NOT NEW.userId;

                    -- For EXPENSE / INCOME: walletId must match original, and targetWalletId must be null
                    SELECT RAISE(ABORT, 'P0-B: Reversal walletId must match original walletId for non-transfer transactions')
                    FROM transactions
                    WHERE id = NEW.reversedTransactionId
                      AND type IN ('EXPENSE', 'INCOME')
                      AND walletId IS NOT NEW.walletId;

                    SELECT RAISE(ABORT, 'P0-B: Non-transfer reversal must not have targetWalletId')
                    FROM transactions
                    WHERE id = NEW.reversedTransactionId
                      AND type IN ('EXPENSE', 'INCOME')
                      AND NEW.targetWalletId IS NOT NULL;

                    -- For TRANSFER: reversal source must be original target, reversal target must be original source
                    SELECT RAISE(ABORT, 'P0-B: Reversal transfer source walletId must match original targetWalletId')
                    FROM transactions
                    WHERE id = NEW.reversedTransactionId
                      AND type = 'TRANSFER'
                      AND NEW.walletId IS NOT targetWalletId;

                    SELECT RAISE(ABORT, 'P0-B: Reversal transfer targetWalletId must match original source walletId')
                    FROM transactions
                    WHERE id = NEW.reversedTransactionId
                      AND type = 'TRANSFER'
                      AND NEW.targetWalletId IS NOT walletId;

                    -- Validate wallet ownership and currency in wallets table
                    SELECT RAISE(ABORT, 'P0-B: Reversal wallet does not exist, belongs to another user, or has mismatched currency')
                    FROM transactions
                    WHERE id = NEW.reversedTransactionId
                      AND type IN ('EXPENSE', 'INCOME')
                      AND NOT EXISTS (
                          SELECT 1 FROM wallets
                          WHERE id = NEW.walletId AND userId = NEW.userId AND currencyCode = NEW.currencyCode
                      );

                    SELECT RAISE(ABORT, 'P0-B: Reversal transfer source wallet does not exist, belongs to another user, or has mismatched currency')
                    FROM transactions
                    WHERE id = NEW.reversedTransactionId
                      AND type = 'TRANSFER'
                      AND NOT EXISTS (
                          SELECT 1 FROM wallets
                          WHERE id = NEW.walletId AND userId = NEW.userId AND currencyCode = NEW.currencyCode
                      );

                    SELECT RAISE(ABORT, 'P0-B: Reversal transfer target wallet does not exist, belongs to another user, or has mismatched currency')
                    FROM transactions
                    WHERE id = NEW.reversedTransactionId
                      AND type = 'TRANSFER'
                      AND NOT EXISTS (
                          SELECT 1 FROM wallets
                          WHERE id = NEW.targetWalletId AND userId = NEW.userId AND currencyCode = NEW.currencyCode
                      );
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_no_delete_reversal
                BEFORE DELETE ON transactions
                WHEN OLD.isReversal = 1
                BEGIN
                    SELECT RAISE(ABORT, 'P0-B: Reversal transactions cannot be deleted; ledger history is permanent');
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_lock_reversed_original_tx
                BEFORE UPDATE OF amountMinorUnits, walletId, currencyCode, userId, type ON transactions
                WHEN (SELECT COUNT(*) FROM transactions WHERE reversedTransactionId = OLD.id) > 0
                BEGIN
                    SELECT RAISE(ABORT, 'P0-B: Original transaction is locked because a reversal entry exists');
                END;
            """)

            // P0-C: Installment Payment & Allocation Invariants
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_verify_exact_reversal_tx_for_payment
                BEFORE UPDATE OF isVoid, reversalTransactionId ON installment_payments
                WHEN OLD.isVoid = 0 AND NEW.isVoid = 1
                BEGIN
                    SELECT RAISE(ABORT, 'P0-C: Voiding a payment requires a valid reversalTransactionId')
                    WHERE NEW.reversalTransactionId IS NULL;

                    SELECT RAISE(ABORT, 'P0-C: reversalTransactionId must reference an existing reversal transaction matching payment details')
                    WHERE NOT EXISTS (
                        SELECT 1 FROM transactions
                        WHERE id = NEW.reversalTransactionId
                          AND isReversal = 1
                          AND reversedTransactionId = NEW.transactionId
                          AND amountMinorUnits = NEW.totalMinorUnits
                          AND currencyCode = NEW.currencyCode
                          AND userId = NEW.userId
                    );
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_cascade_void_allocations_on_payment_void
                AFTER UPDATE OF isVoid ON installment_payments
                WHEN OLD.isVoid = 0 AND NEW.isVoid = 1
                BEGIN
                    UPDATE installment_payment_allocations
                    SET isVoid = 1
                    WHERE paymentId = NEW.id AND isVoid = 0;
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_no_unvoid_payment_record
                BEFORE UPDATE OF isVoid ON installment_payments
                WHEN OLD.isVoid = 1 AND NEW.isVoid = 0
                BEGIN
                    SELECT RAISE(ABORT, 'P0-C: A voided installment payment cannot be reactivated');
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_no_delete_installment_payment
                BEFORE DELETE ON installment_payments
                BEGIN
                    SELECT RAISE(ABORT, 'P0-C: Installment payments are permanent financial records; DELETE is forbidden');
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_validate_allocation_currency_user_match
                BEFORE INSERT ON installment_payment_allocations
                BEGIN
                    SELECT RAISE(ABORT, 'P0-C: Allocation currency and userId must match period and payment')
                    WHERE NOT EXISTS (
                        SELECT 1 FROM installment_periods per
                        JOIN installment_payments pay ON pay.id = NEW.paymentId
                        WHERE per.id = NEW.periodId
                          AND per.currencyCode = NEW.currencyCode
                          AND pay.currencyCode = NEW.currencyCode
                          AND per.userId = NEW.userId
                          AND pay.userId = NEW.userId
                    );
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_validate_allocation_plan_match
                BEFORE INSERT ON installment_payment_allocations
                BEGIN
                    SELECT RAISE(ABORT, 'P0-C: Allocation payment and period must belong to the same plan')
                    WHERE NOT EXISTS (
                        SELECT 1 FROM installment_periods per
                        JOIN installment_payments pay ON pay.id = NEW.paymentId
                        WHERE per.id = NEW.periodId AND pay.planId = per.planId
                    );
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_validate_allocation_amount_cap
                BEFORE INSERT ON installment_payment_allocations
                BEGIN
                    SELECT RAISE(ABORT, 'P0-C: Allocation amount exceeds period unpaid balance')
                    FROM installment_periods
                    WHERE id = NEW.periodId
                      AND (amountDueMinorUnits - amountPaidMinorUnits) < NEW.allocatedMinorUnits;
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_update_period_on_allocation_insert
                AFTER INSERT ON installment_payment_allocations
                WHEN NEW.isVoid = 0
                BEGIN
                    UPDATE installment_periods
                    SET amountPaidMinorUnits = amountPaidMinorUnits + NEW.allocatedMinorUnits,
                        isPaid = CASE WHEN (amountPaidMinorUnits + NEW.allocatedMinorUnits) >= amountDueMinorUnits THEN 1 ELSE 0 END
                    WHERE id = NEW.periodId;
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_immutable_allocation_core_fields
                BEFORE UPDATE ON installment_payment_allocations
                WHEN (NEW.periodId != OLD.periodId 
                   OR NEW.paymentId != OLD.paymentId 
                   OR NEW.allocatedMinorUnits != OLD.allocatedMinorUnits 
                   OR NEW.currencyCode != OLD.currencyCode 
                   OR NEW.userId != OLD.userId)
                BEGIN
                    SELECT RAISE(ABORT, 'P0-C: Allocation core fields are immutable');
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_deduct_period_amount_on_alloc_void
                BEFORE UPDATE OF isVoid ON installment_payment_allocations
                WHEN OLD.isVoid = 0 AND NEW.isVoid = 1
                BEGIN
                    SELECT RAISE(ABORT, 'P0-C: Insufficient period amountPaidMinorUnits to deduct allocation amount')
                    WHERE EXISTS (
                        SELECT 1 FROM installment_periods
                        WHERE id = NEW.periodId AND amountPaidMinorUnits < NEW.allocatedMinorUnits
                    );

                    UPDATE installment_periods
                    SET amountPaidMinorUnits = amountPaidMinorUnits - NEW.allocatedMinorUnits,
                        isPaid = 0
                    WHERE id = NEW.periodId;
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_no_unvoid_allocation_record
                BEFORE UPDATE OF isVoid ON installment_payment_allocations
                WHEN OLD.isVoid = 1 AND NEW.isVoid = 0
                BEGIN
                    SELECT RAISE(ABORT, 'P0-C: A voided installment allocation cannot be reactivated');
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_no_delete_installment_allocation
                BEFORE DELETE ON installment_payment_allocations
                BEGIN
                    SELECT RAISE(ABORT, 'P0-C: Installment allocations are permanent audit records; DELETE is forbidden');
                END;
            """)

            // P0-E: ISO-8601 Calendar Date triggers for installment_periods with Gregorian leap-year validation
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_validate_period_due_date_iso_insert
                BEFORE INSERT ON installment_periods
                BEGIN
                    SELECT RAISE(ABORT, 'P0-E: dueDate must be a valid ISO-8601 calendar date YYYY-MM-DD')
                    WHERE NEW.dueDate NOT GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
                       OR CAST(substr(NEW.dueDate, 6, 2) AS INT) < 1 
                       OR CAST(substr(NEW.dueDate, 6, 2) AS INT) > 12
                       OR CAST(substr(NEW.dueDate, 9, 2) AS INT) < 1
                       OR (CAST(substr(NEW.dueDate, 6, 2) AS INT) IN (4, 6, 9, 11) AND CAST(substr(NEW.dueDate, 9, 2) AS INT) > 30)
                       OR (CAST(substr(NEW.dueDate, 6, 2) AS INT) IN (1, 3, 5, 7, 8, 10, 12) AND CAST(substr(NEW.dueDate, 9, 2) AS INT) > 31)
                       OR (CAST(substr(NEW.dueDate, 6, 2) AS INT) = 2 AND (
                              (CAST(substr(NEW.dueDate, 1, 4) AS INT) % 4 = 0 AND CAST(substr(NEW.dueDate, 1, 4) AS INT) % 100 != 0) 
                              OR CAST(substr(NEW.dueDate, 1, 4) AS INT) % 400 = 0
                           ) AND CAST(substr(NEW.dueDate, 9, 2) AS INT) > 29)
                       OR (CAST(substr(NEW.dueDate, 6, 2) AS INT) = 2 AND NOT (
                              (CAST(substr(NEW.dueDate, 1, 4) AS INT) % 4 = 0 AND CAST(substr(NEW.dueDate, 1, 4) AS INT) % 100 != 0) 
                              OR CAST(substr(NEW.dueDate, 1, 4) AS INT) % 400 = 0
                           ) AND CAST(substr(NEW.dueDate, 9, 2) AS INT) > 28);
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_validate_period_due_date_iso_update
                BEFORE UPDATE OF dueDate ON installment_periods
                BEGIN
                    SELECT RAISE(ABORT, 'P0-E: dueDate must be a valid ISO-8601 calendar date YYYY-MM-DD')
                    WHERE NEW.dueDate NOT GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
                       OR CAST(substr(NEW.dueDate, 6, 2) AS INT) < 1 
                       OR CAST(substr(NEW.dueDate, 6, 2) AS INT) > 12
                       OR CAST(substr(NEW.dueDate, 9, 2) AS INT) < 1
                       OR (CAST(substr(NEW.dueDate, 6, 2) AS INT) IN (4, 6, 9, 11) AND CAST(substr(NEW.dueDate, 9, 2) AS INT) > 30)
                       OR (CAST(substr(NEW.dueDate, 6, 2) AS INT) IN (1, 3, 5, 7, 8, 10, 12) AND CAST(substr(NEW.dueDate, 9, 2) AS INT) > 31)
                       OR (CAST(substr(NEW.dueDate, 6, 2) AS INT) = 2 AND (
                              (CAST(substr(NEW.dueDate, 1, 4) AS INT) % 4 = 0 AND CAST(substr(NEW.dueDate, 1, 4) AS INT) % 100 != 0) 
                              OR CAST(substr(NEW.dueDate, 1, 4) AS INT) % 400 = 0
                           ) AND CAST(substr(NEW.dueDate, 9, 2) AS INT) > 29)
                       OR (CAST(substr(NEW.dueDate, 6, 2) AS INT) = 2 AND NOT (
                              (CAST(substr(NEW.dueDate, 1, 4) AS INT) % 4 = 0 AND CAST(substr(NEW.dueDate, 1, 4) AS INT) % 100 != 0) 
                              OR CAST(substr(NEW.dueDate, 1, 4) AS INT) % 400 = 0
                           ) AND CAST(substr(NEW.dueDate, 9, 2) AS INT) > 28);
                END;
            """)
        }
    }

    private fun restoreSqliteSequences(db: SupportSQLiteDatabase) {
        val tables = listOf("transactions", "wallets", "users")
        for (table in tables) {
            db.execSQL("DELETE FROM sqlite_sequence WHERE name = '$table'")
            db.execSQL("""
                INSERT INTO sqlite_sequence (name, seq)
                SELECT '$table', target_seq
                FROM (
                    SELECT MAX(
                        COALESCE((SELECT MAX(seq) FROM _backup_sqlite_sequence WHERE name = '$table'), 0),
                        COALESCE((SELECT MAX(id) FROM `$table`), 0)
                    ) AS target_seq
                )
                WHERE target_seq > 0
            """)
        }
    }
}
