package com.example

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinSmartDatabase
import com.example.data.local.Migration10To11
import com.example.data.model.AccountMode
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.data.repository.FinanceRepository
import kotlinx.coroutines.runBlocking
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
class ReversalIntegrityTriggerTest {

    private lateinit var context: Context
    private lateinit var database: FinSmartDatabase
    private lateinit var repository: FinanceRepository

    private val user1Id = 1001L
    private val user2Id = 1002L

    @Before
    fun setUp() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            database = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
                .allowMainThreadQueries()
                .build()

            Migration10To11.installIntegrityTriggers(database.openHelper.writableDatabase)

            database.userDao().insertUser(
                User(id = user1Id, username = "ReversalUser1", email = "rev1@test.com", passwordHash = "h", role = UserRole.FREE_MEMBER)
            )
            database.userDao().insertUser(
                User(id = user2Id, username = "ReversalUser2", email = "rev2@test.com", passwordHash = "h", role = UserRole.FREE_MEMBER)
            )

            // Seed wallet 1 (IDR) and wallet 2 (IDR) for user 1
            database.walletDao().insertWallet(
                Wallet(id = 1L, name = "Primary IDR", type = WalletType.BANK, accountNumber = "1", balance = 1000000.0, balanceMinorUnits = 100000000L, colorHex = "#00", iconName = "i", accountMode = "PERSONAL", isDefault = true, createdAt = 1000L, currencyCode = "IDR", userId = user1Id)
            )
            database.walletDao().insertWallet(
                Wallet(id = 2L, name = "Target IDR", type = WalletType.BANK, accountNumber = "2", balance = 500000.0, balanceMinorUnits = 50000000L, colorHex = "#00", iconName = "i", accountMode = "PERSONAL", isDefault = false, createdAt = 1000L, currencyCode = "IDR", userId = user1Id)
            )
            // Seed wallet 3 (USD) for user 1
            database.walletDao().insertWallet(
                Wallet(id = 3L, name = "USD Vault", type = WalletType.BANK, accountNumber = "3", balance = 100.0, balanceMinorUnits = 10000L, colorHex = "#00", iconName = "i", accountMode = "PERSONAL", isDefault = false, createdAt = 1000L, currencyCode = "USD", userId = user1Id)
            )
            // Seed wallet 4 (IDR) for user 2
            database.walletDao().insertWallet(
                Wallet(id = 4L, name = "User2 Wallet", type = WalletType.BANK, accountNumber = "4", balance = 1000000.0, balanceMinorUnits = 100000000L, colorHex = "#00", iconName = "i", accountMode = "PERSONAL", isDefault = true, createdAt = 1000L, currencyCode = "IDR", userId = user2Id)
            )

            repository = FinanceRepository(database.walletDao(), database)
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==========================================
    // 1. POSITIVE REVERSAL TESTS & BALANCE VERIFICATION
    // ==========================================

    @Test
    fun testExpenseReversedByIncomeViaRepositoryRestoresBalance() = runBlocking {
        // Initial balance: 1,000,000 IDR
        val initialWallet = database.walletDao().getWalletById(1L, user1Id)!!
        assertEquals(1000000.0, initialWallet.balance, 0.001)

        // Step 1: Insert EXPENSE of 200,000 IDR
        val origExpense = TransactionItem(
            id = 10L, title = "Groceries", amount = 200000.0, amountMinorUnits = 20000000L,
            currencyCode = "IDR", type = TransactionType.EXPENSE, category = "Food",
            walletId = 1L, walletName = "Primary IDR", userId = user1Id, isReversal = false
        )
        val origId = repository.insertTransaction(user1Id, origExpense)
        assertTrue(origId > 0L)

        // Balance should be debited to 800,000 IDR
        val postExpenseWallet = database.walletDao().getWalletById(1L, user1Id)!!
        assertEquals(800000.0, postExpenseWallet.balance, 0.001)
        assertEquals(80000000L, postExpenseWallet.balanceMinorUnits)

        // Step 2: Reverse the transaction via repository
        val revResult = repository.reverseTransaction(user1Id, origId, "Salah input toko")
        assertTrue("Reversal must succeed: ${revResult.exceptionOrNull()?.message}", revResult.isSuccess)
        val revId = revResult.getOrThrow()
        assertTrue(revId > 0L)

        // Balance must be credited back to exactly 1,000,000 IDR
        val postReversalWallet = database.walletDao().getWalletById(1L, user1Id)!!
        assertEquals(1000000.0, postReversalWallet.balance, 0.001)
        assertEquals(100000000L, postReversalWallet.balanceMinorUnits)

        // Check reversal transaction record
        val revTx = database.transactionDao().getTransactionById(revId, user1Id)!!
        assertEquals(TransactionType.INCOME, revTx.type)
        assertEquals(200000.0, revTx.amount, 0.001)
        assertEquals(20000000L, revTx.amountMinorUnits)
        assertEquals(1L, revTx.walletId)
        assertEquals(true, revTx.isReversal)
        assertEquals(origId, revTx.reversedTransactionId)
    }

    @Test
    fun testIncomeReversedByExpenseViaRepositoryRestoresBalance() = runBlocking {
        val initialWallet = database.walletDao().getWalletById(1L, user1Id)!!
        assertEquals(1000000.0, initialWallet.balance, 0.001)

        // Step 1: Insert INCOME of 500,000 IDR
        val origIncome = TransactionItem(
            id = 20L, title = "Bonus", amount = 500000.0, amountMinorUnits = 50000000L,
            currencyCode = "IDR", type = TransactionType.INCOME, category = "Gaji",
            walletId = 1L, walletName = "Primary IDR", userId = user1Id, isReversal = false
        )
        val origId = repository.insertTransaction(user1Id, origIncome)
        assertTrue(origId > 0L)

        val postIncomeWallet = database.walletDao().getWalletById(1L, user1Id)!!
        assertEquals(1500000.0, postIncomeWallet.balance, 0.001)

        // Step 2: Reverse the income
        val revResult = repository.reverseTransaction(user1Id, origId, "Bonus dibatalkan")
        assertTrue(revResult.isSuccess)
        val revId = revResult.getOrThrow()

        // Balance must be debited back to exactly 1,000,000 IDR
        val postReversalWallet = database.walletDao().getWalletById(1L, user1Id)!!
        assertEquals(1000000.0, postReversalWallet.balance, 0.001)
        assertEquals(100000000L, postReversalWallet.balanceMinorUnits)

        val revTx = database.transactionDao().getTransactionById(revId, user1Id)!!
        assertEquals(TransactionType.EXPENSE, revTx.type)
        assertEquals(origId, revTx.reversedTransactionId)
    }

    @Test
    fun testTransferReversedByTransferViaRepositoryRestoresBothWallets() = runBlocking {
        // Wallet 1 has 1,000,000 IDR, Wallet 2 has 500,000 IDR
        val origTransfer = TransactionItem(
            id = 30L, title = "Transfer ke Dompet 2", amount = 200000.0, amountMinorUnits = 20000000L,
            currencyCode = "IDR", type = TransactionType.TRANSFER, category = "Transfer",
            walletId = 1L, walletName = "Primary IDR", targetWalletId = 2L, targetWalletName = "Target IDR",
            userId = user1Id, isReversal = false
        )
        val origId = repository.insertTransaction(user1Id, origTransfer)
        assertTrue(origId > 0L)

        // After transfer: Wallet 1 has 800,000 IDR, Wallet 2 has 700,000 IDR
        val w1After = database.walletDao().getWalletById(1L, user1Id)!!
        val w2After = database.walletDao().getWalletById(2L, user1Id)!!
        assertEquals(800000.0, w1After.balance, 0.001)
        assertEquals(80000000L, w1After.balanceMinorUnits)
        assertEquals(700000.0, w2After.balance, 0.001)
        assertEquals(70000000L, w2After.balanceMinorUnits)

        // Execute reversal of transfer
        val revResult = repository.reverseTransaction(user1Id, origId, "Salah transfer")
        assertTrue("Transfer reversal must succeed: ${revResult.exceptionOrNull()?.message}", revResult.isSuccess)
        val revId = revResult.getOrThrow()

        // Both wallet balances must be restored cleanly to original values:
        val w1Restored = database.walletDao().getWalletById(1L, user1Id)!!
        val w2Restored = database.walletDao().getWalletById(2L, user1Id)!!
        assertEquals(1000000.0, w1Restored.balance, 0.001)
        assertEquals(100000000L, w1Restored.balanceMinorUnits)
        assertEquals(500000.0, w2Restored.balance, 0.001)
        assertEquals(50000000L, w2Restored.balanceMinorUnits)

        // Verify reversal transaction record has SWAPPED wallets (2 -> 1)
        val revTx = database.transactionDao().getTransactionById(revId, user1Id)!!
        assertEquals(TransactionType.TRANSFER, revTx.type)
        assertEquals(2L, revTx.walletId) // Source of reversal is target of original
        assertEquals(1L, revTx.targetWalletId) // Target of reversal is source of original
        assertEquals(true, revTx.isReversal)
        assertEquals(origId, revTx.reversedTransactionId)
        assertEquals(20000000L, revTx.amountMinorUnits)
    }

    // ==========================================
    // 2. SQLITE THREE-VALUED LOGIC & NULL REJECTION NEGATIVE TESTS
    // ==========================================

    @Test
    fun testReversalWithNullReversedTransactionIdRejected() = runBlocking {
        try {
            database.openHelper.writableDatabase.execSQL("""
                INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
                VALUES (400, 'Reversal With Null Target', 50000.0, 5000000, 'IDR', 'INCOME', 'Refund', 'i', 1, 'Primary IDR', $user1Id, 1000, '', '', 'PERSONAL', 1, NULL)
            """)
            fail("Inserting isReversal = 1 with reversedTransactionId = NULL must be rejected")
        } catch (e: Exception) {
            assertTrue("Expected rejection for null reversedTransactionId: ${e.message}", 
                e.message?.contains("non-null reversedTransactionId") == true || 
                e.message?.contains("isReversal must be 1 if and only if reversedTransactionId is NOT NULL") == true ||
                e is SQLiteConstraintException
            )
        }
    }

    @Test
    fun testRegularTransactionWithIsReversalZeroAccepted() = runBlocking {
        val tx = TransactionItem(
            id = 490L, title = "Regular Coffee", amount = 25000.0, amountMinorUnits = 2500000L,
            currencyCode = "IDR", type = TransactionType.EXPENSE, category = "Food",
            walletId = 1L, walletName = "Primary IDR", userId = user1Id, isReversal = false, reversedTransactionId = null
        )
        val insertedId = database.transactionDao().insertTransaction(tx)
        assertTrue("Regular transaction with isReversal = 0 must succeed", insertedId > 0L)
        val readTx = database.transactionDao().getTransactionById(insertedId, user1Id)
        assertNotNull(readTx)
        assertEquals(false, readTx?.isReversal)
        assertEquals(null, readTx?.reversedTransactionId)
    }

    @Test
    fun testReversalFailsIfTargetTransactionDoesNotExist() = runBlocking {
        try {
            database.openHelper.writableDatabase.execSQL("""
                INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
                VALUES (401, 'Ghost Reversal', 50000.0, 5000000, 'IDR', 'INCOME', 'Refund', 'i', 1, 'Primary IDR', $user1Id, 1000, '', '', 'PERSONAL', 1, 999999)
            """)
            fail("Reversal for non-existent target transaction must be aborted")
        } catch (e: Exception) {
            assertTrue("Expected target transaction does not exist error: ${e.message}", e.message?.contains("Target transaction to reverse does not exist") == true || e is SQLiteConstraintException)
        }
    }

    @Test
    fun testReversalFailsIfReversalUserIdIsNull() = runBlocking {
        // Seed original transaction
        database.openHelper.writableDatabase.execSQL("""
            INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
            VALUES (410, 'Valid Orig', 50000.0, 5000000, 'IDR', 'EXPENSE', 'Food', 'i', 1, 'Primary IDR', $user1Id, 1000, '', '', 'PERSONAL', 0, NULL)
        """)

        try {
            database.openHelper.writableDatabase.execSQL("""
                INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
                VALUES (411, 'Null User Rev', 50000.0, 5000000, 'IDR', 'INCOME', 'Refund', 'i', 1, 'Primary IDR', NULL, 1000, '', '', 'PERSONAL', 1, 410)
            """)
            fail("Reversal with NULL userId must be aborted")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("non-null userId") == true || e is SQLiteConstraintException)
        }
    }

    @Test
    fun testReversalFailsIfReversalWalletIdIsNull() = runBlocking {
        database.openHelper.writableDatabase.execSQL("""
            INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
            VALUES (420, 'Valid Orig', 50000.0, 5000000, 'IDR', 'EXPENSE', 'Food', 'i', 1, 'Primary IDR', $user1Id, 1000, '', '', 'PERSONAL', 0, NULL)
        """)

        try {
            database.openHelper.writableDatabase.execSQL("""
                INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
                VALUES (421, 'Null Wallet Rev', 50000.0, 5000000, 'IDR', 'INCOME', 'Refund', 'i', NULL, 'Unknown', $user1Id, 1000, '', '', 'PERSONAL', 1, 420)
            """)
            fail("Reversal with NULL walletId must be aborted")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("non-null walletId") == true || e is SQLiteConstraintException)
        }
    }

    @Test
    fun testReversalFailsIfReversalCurrencyCodeIsNull() = runBlocking {
        database.openHelper.writableDatabase.execSQL("""
            INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
            VALUES (430, 'Valid Orig', 50000.0, 5000000, 'IDR', 'EXPENSE', 'Food', 'i', 1, 'Primary IDR', $user1Id, 1000, '', '', 'PERSONAL', 0, NULL)
        """)

        try {
            database.openHelper.writableDatabase.execSQL("""
                INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
                VALUES (431, 'Null Currency Rev', 50000.0, 5000000, NULL, 'INCOME', 'Refund', 'i', 1, 'Primary IDR', $user1Id, 1000, '', '', 'PERSONAL', 1, 430)
            """)
            fail("Reversal with NULL currencyCode must be aborted")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("non-null currencyCode") == true || e is SQLiteConstraintException)
        }
    }

    @Test
    fun testReversalFailsIfReversalAmountMinorUnitsIsNull() = runBlocking {
        database.openHelper.writableDatabase.execSQL("""
            INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
            VALUES (440, 'Valid Orig', 50000.0, 5000000, 'IDR', 'EXPENSE', 'Food', 'i', 1, 'Primary IDR', $user1Id, 1000, '', '', 'PERSONAL', 0, NULL)
        """)

        try {
            database.openHelper.writableDatabase.execSQL("""
                INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
                VALUES (441, 'Null Amount Rev', 50000.0, NULL, 'IDR', 'INCOME', 'Refund', 'i', 1, 'Primary IDR', $user1Id, 1000, '', '', 'PERSONAL', 1, 440)
            """)
            fail("Reversal with NULL amountMinorUnits must be aborted")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("non-null amountMinorUnits") == true || e is SQLiteConstraintException)
        }
    }

    @Test
    fun testReversalFailsIfReversalTypeIsNull() = runBlocking {
        database.openHelper.writableDatabase.execSQL("""
            INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
            VALUES (450, 'Valid Orig', 50000.0, 5000000, 'IDR', 'EXPENSE', 'Food', 'i', 1, 'Primary IDR', $user1Id, 1000, '', '', 'PERSONAL', 0, NULL)
        """)

        try {
            database.openHelper.writableDatabase.execSQL("""
                INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
                VALUES (451, 'Null Type Rev', 50000.0, 5000000, 'IDR', NULL, 'Refund', 'i', 1, 'Primary IDR', $user1Id, 1000, '', '', 'PERSONAL', 1, 450)
            """)
            fail("Reversal with NULL type must be aborted")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("non-null type") == true || e is SQLiteConstraintException)
        }
    }

    @Test
    fun testReversalFailsIfTargetHasIncompleteIdentityNullUserId() = runBlocking {
        // Insert legacy transaction with NULL userId
        database.openHelper.writableDatabase.execSQL("""
            INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
            VALUES (460, 'Legacy Null User', 50000.0, 5000000, 'IDR', 'EXPENSE', 'Food', 'i', 1, 'Primary IDR', NULL, 1000, '', '', 'PERSONAL', 0, NULL)
        """)

        try {
            database.openHelper.writableDatabase.execSQL("""
                INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
                VALUES (461, 'Reversal Attempt', 50000.0, 5000000, 'IDR', 'INCOME', 'Refund', 'i', 1, 'Primary IDR', $user1Id, 1000, '', '', 'PERSONAL', 1, 460)
            """)
            fail("Reversal of transaction with incomplete financial identity must be aborted")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("incomplete financial identity") == true || e is SQLiteConstraintException)
        }
    }

    // ==========================================
    // 3. TRANSFER REVERSAL INVARIANT TESTS
    // ==========================================

    @Test
    fun testTransferReversalFailsIfReversalTargetWalletIsNull() = runBlocking {
        // Original valid transfer
        database.openHelper.writableDatabase.execSQL("""
            INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, targetWalletId, targetWalletName, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
            VALUES (500, 'Transfer 1 to 2', 50000.0, 5000000, 'IDR', 'TRANSFER', 'Transfer', 'i', 1, 'Primary IDR', 2, 'Target IDR', $user1Id, 1000, '', '', 'PERSONAL', 0, NULL)
        """)

        try {
            database.openHelper.writableDatabase.execSQL("""
                INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, targetWalletId, targetWalletName, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
                VALUES (501, 'Reversal Without Target', 50000.0, 5000000, 'IDR', 'TRANSFER', 'Transfer', 'i', 2, 'Target IDR', NULL, NULL, $user1Id, 1000, '', '', 'PERSONAL', 1, 500)
            """)
            fail("Transfer reversal with NULL targetWalletId must be aborted")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("non-null targetWalletId") == true || e is SQLiteConstraintException)
        }
    }

    @Test
    fun testTransferReversalFailsIfSourceAndTargetWalletsNotSwapped() = runBlocking {
        database.openHelper.writableDatabase.execSQL("""
            INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, targetWalletId, targetWalletName, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
            VALUES (510, 'Transfer 1 to 2', 50000.0, 5000000, 'IDR', 'TRANSFER', 'Transfer', 'i', 1, 'Primary IDR', 2, 'Target IDR', $user1Id, 1000, '', '', 'PERSONAL', 0, NULL)
        """)

        // Attempting to reverse transfer with unswapped wallets (1 -> 2 instead of 2 -> 1)
        try {
            database.openHelper.writableDatabase.execSQL("""
                INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, targetWalletId, targetWalletName, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
                VALUES (511, 'Unswapped Reversal', 50000.0, 5000000, 'IDR', 'TRANSFER', 'Transfer', 'i', 1, 'Primary IDR', 2, 'Target IDR', $user1Id, 1000, '', '', 'PERSONAL', 1, 510)
            """)
            fail("Transfer reversal with unswapped wallets must be aborted")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("source walletId must match original targetWalletId") == true || e is SQLiteConstraintException)
        }
    }

    @Test
    fun testTransferReversalFailsIfCrossUserWallet() = runBlocking {
        // Original transfer between user 1's wallets 1 and 2
        database.openHelper.writableDatabase.execSQL("""
            INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, targetWalletId, targetWalletName, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
            VALUES (520, 'Transfer 1 to 2', 50000.0, 5000000, 'IDR', 'TRANSFER', 'Transfer', 'i', 1, 'Primary IDR', 2, 'Target IDR', $user1Id, 1000, '', '', 'PERSONAL', 0, NULL)
        """)

        // Attempting to reverse by setting target to user 2's wallet 4
        try {
            database.openHelper.writableDatabase.execSQL("""
                INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, targetWalletId, targetWalletName, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
                VALUES (521, 'Cross User Reversal', 50000.0, 5000000, 'IDR', 'TRANSFER', 'Transfer', 'i', 2, 'Target IDR', 4, 'User 2 Wallet', $user1Id, 1000, '', '', 'PERSONAL', 1, 520)
            """)
            fail("Transfer reversal with cross-user target wallet must be aborted")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("targetWalletId must match original source walletId") == true || e is SQLiteConstraintException)
        }
    }

    @Test
    fun testNonTransferReversalFailsIfTargetWalletSpecified() = runBlocking {
        database.openHelper.writableDatabase.execSQL("""
            INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
            VALUES (530, 'Expense', 50000.0, 5000000, 'IDR', 'EXPENSE', 'Food', 'i', 1, 'Primary IDR', $user1Id, 1000, '', '', 'PERSONAL', 0, NULL)
        """)

        // Attempting to set targetWalletId on an EXPENSE reversal
        try {
            database.openHelper.writableDatabase.execSQL("""
                INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, targetWalletId, userId, timestamp, note, tags, accountMode, isReversal, reversedTransactionId)
                VALUES (531, 'Expense Rev with Target', 50000.0, 5000000, 'IDR', 'INCOME', 'Refund', 'i', 1, 'Primary IDR', 2, $user1Id, 1000, '', '', 'PERSONAL', 1, 530)
            """)
            fail("Non-transfer reversal with targetWalletId must be aborted")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Non-transfer reversal must not have targetWalletId") == true || e is SQLiteConstraintException)
        }
    }

    // ==========================================
    // 4. DUPLICATE, CHAINED & MUTATION LOCK TESTS
    // ==========================================

    @Test
    fun testDuplicateReversalRejected() = runBlocking {
        database.transactionDao().insertTransaction(
            TransactionItem(
                id = 90L, title = "Orig Expense", amount = 50000.0, amountMinorUnits = 5000000L,
                currencyCode = "IDR", type = TransactionType.EXPENSE, category = "Food",
                walletId = 1L, walletName = "Primary IDR", userId = user1Id
            )
        )

        // First reversal succeeds
        database.transactionDao().insertTransaction(
            TransactionItem(
                id = 91L, title = "Reversal 1", amount = 50000.0, amountMinorUnits = 5000000L,
                currencyCode = "IDR", type = TransactionType.INCOME, category = "Refund",
                walletId = 1L, walletName = "Primary IDR", userId = user1Id,
                isReversal = true, reversedTransactionId = 90L
            )
        )

        // Second reversal for same transaction 90 -> REJECTED
        try {
            database.transactionDao().insertTransaction(
                TransactionItem(
                    id = 92L, title = "Reversal 2", amount = 50000.0, amountMinorUnits = 5000000L,
                    currencyCode = "IDR", type = TransactionType.INCOME, category = "Refund",
                    walletId = 1L, walletName = "Primary IDR", userId = user1Id,
                    isReversal = true, reversedTransactionId = 90L
                )
            )
            fail("Duplicate reversal must be rejected")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Duplicate reversal rejected") == true || e.message?.contains("UNIQUE") == true || e is SQLiteConstraintException)
        }
    }

    @Test
    fun testReversalOfReversalRejected() = runBlocking {
        database.transactionDao().insertTransaction(
            TransactionItem(
                id = 80L, title = "Orig Expense", amount = 50000.0, amountMinorUnits = 5000000L,
                currencyCode = "IDR", type = TransactionType.EXPENSE, category = "Food",
                walletId = 1L, walletName = "Primary IDR", userId = user1Id
            )
        )

        database.transactionDao().insertTransaction(
            TransactionItem(
                id = 81L, title = "Reversal 1", amount = 50000.0, amountMinorUnits = 5000000L,
                currencyCode = "IDR", type = TransactionType.INCOME, category = "Refund",
                walletId = 1L, walletName = "Primary IDR", userId = user1Id,
                isReversal = true, reversedTransactionId = 80L
            )
        )

        // Attempt to reverse reversal 81 -> REJECTED
        try {
            database.transactionDao().insertTransaction(
                TransactionItem(
                    id = 82L, title = "Reversal of Reversal", amount = 50000.0, amountMinorUnits = 5000000L,
                    currencyCode = "IDR", type = TransactionType.EXPENSE, category = "Adjustment",
                    walletId = 1L, walletName = "Primary IDR", userId = user1Id,
                    isReversal = true, reversedTransactionId = 81L
                )
            )
            fail("Reversing a reversal must be rejected")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("cannot reverse a reversal") == true || e is SQLiteConstraintException)
        }
    }

    @Test
    fun testDeleteReversalRejected() = runBlocking {
        database.transactionDao().insertTransaction(
            TransactionItem(
                id = 100L, title = "Orig Expense", amount = 50000.0, amountMinorUnits = 5000000L,
                currencyCode = "IDR", type = TransactionType.EXPENSE, category = "Food",
                walletId = 1L, walletName = "Primary IDR", userId = user1Id
            )
        )

        database.transactionDao().insertTransaction(
            TransactionItem(
                id = 101L, title = "Reversal", amount = 50000.0, amountMinorUnits = 5000000L,
                currencyCode = "IDR", type = TransactionType.INCOME, category = "Refund",
                walletId = 1L, walletName = "Primary IDR", userId = user1Id,
                isReversal = true, reversedTransactionId = 100L
            )
        )

        try {
            database.openHelper.writableDatabase.execSQL("DELETE FROM transactions WHERE id = 101")
            fail("Deleting a reversal transaction must be rejected")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("ledger history is permanent") == true || e is SQLiteConstraintException)
        }
    }

    @Test
    fun testLockedOriginalTransactionCannotBeMutated() = runBlocking {
        database.transactionDao().insertTransaction(
            TransactionItem(
                id = 120L, title = "Orig Expense", amount = 50000.0, amountMinorUnits = 5000000L,
                currencyCode = "IDR", type = TransactionType.EXPENSE, category = "Food",
                walletId = 1L, walletName = "Primary IDR", userId = user1Id
            )
        )

        database.transactionDao().insertTransaction(
            TransactionItem(
                id = 121L, title = "Reversal", amount = 50000.0, amountMinorUnits = 5000000L,
                currencyCode = "IDR", type = TransactionType.INCOME, category = "Refund",
                walletId = 1L, walletName = "Primary IDR", userId = user1Id,
                isReversal = true, reversedTransactionId = 120L
            )
        )

        try {
            database.openHelper.writableDatabase.execSQL("UPDATE transactions SET amountMinorUnits = 9999999 WHERE id = 120")
            fail("Updating original transaction after reversal must be rejected")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Original transaction is locked") == true || e is SQLiteConstraintException)
        }
    }
}
