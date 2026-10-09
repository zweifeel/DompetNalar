package com.example.util

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

/**
 * Stage 3 Worker Security Tests:
 * - Worker fails safely when USER_ID is missing or invalid.
 * - Worker fails safely and does not mutate wallet when wallet belongs to another user.
 * - Worker executes correctly and updates balance when wallet is owned by the matching userId.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkerSessionSecurityTest {

    private lateinit var context: Context
    private lateinit var db: FinSmartDatabase

    private val userAId = 101L
    private val userBId = 202L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        setDatabaseSingleton(db)

        runBlocking {
            db.userDao().insertUser(User(id = userAId, username = "UserA", email = "a@test.com", role = UserRole.FREE_MEMBER))
            db.userDao().insertUser(User(id = userBId, username = "UserB", email = "b@test.com", role = UserRole.FREE_MEMBER))
        }
    }

    @After
    fun tearDown() {
        setDatabaseSingleton(null)
        db.close()
    }

    private fun setDatabaseSingleton(instance: FinSmartDatabase?) {
        val field = FinSmartDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, instance)
    }

    @Test
    fun testWorker_missingUserId_failsSafelyWithoutModifyingData() = runBlocking {
        val walletAId = db.walletDao().insertWallet(
            Wallet(name = "Wallet A", type = WalletType.BANK, balance = 500000.0, accountMode = AccountMode.PERSONAL.name, userId = userAId)
        )

        // Work input with NO USER_ID (legacy format)
        val input = Data.Builder()
            .putString("TITLE", "Salary")
            .putDouble("AMOUNT", 1000000.0)
            .putLong("WALLET_ID", walletAId)
            .build()

        val worker = TestListenableWorkerBuilder<RecurringTransactionWorker>(context)
            .setInputData(input)
            .build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)

        // Wallet and transactions remain completely untouched
        val wallet = db.walletDao().getWalletById(walletAId, userAId)
        assertEquals(500000.0, wallet!!.balance, 0.01)
        assertEquals(0, db.transactionDao().getAllTransactions(userAId).first().size)
    }

    @Test
    fun testWorker_wrongUserWallet_failsSafelyAndRejectsCrossUserMutation() = runBlocking {
        // Wallet owned by User A
        val walletAId = db.walletDao().insertWallet(
            Wallet(name = "Wallet A", type = WalletType.BANK, balance = 500000.0, accountMode = AccountMode.PERSONAL.name, userId = userAId)
        )

        // Work input carries User B, but targets User A's walletId
        val input = Data.Builder()
            .putLong("USER_ID", userBId)
            .putString("TITLE", "Illegal Transfer")
            .putDouble("AMOUNT", 200000.0)
            .putLong("WALLET_ID", walletAId)
            .putString("INTERVAL", "DAILY")
            .build()

        val worker = TestListenableWorkerBuilder<RecurringTransactionWorker>(context)
            .setInputData(input)
            .build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)

        // User A's wallet must NOT be touched
        val walletA = db.walletDao().getWalletById(walletAId, userAId)
        assertEquals(500000.0, walletA!!.balance, 0.01)

        // Neither user gets any transaction
        assertEquals(0, db.transactionDao().getAllTransactions(userAId).first().size)
        assertEquals(0, db.transactionDao().getAllTransactions(userBId).first().size)
    }

    @Test
    fun testWorker_validUserAndOwnedWallet_succeedsAndCarriesUserId() = runBlocking {
        val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

        val walletAId = db.walletDao().insertWallet(
            Wallet(name = "Wallet A", type = WalletType.BANK, balance = 500000.0, accountMode = AccountMode.PERSONAL.name, userId = userAId)
        )

        val input = Data.Builder()
            .putLong("USER_ID", userAId)
            .putString("TITLE", "Gaji Bulanan")
            .putDouble("AMOUNT", 2500000.0)
            .putString("TYPE", "INCOME")
            .putString("CATEGORY", "Gaji")
            .putLong("WALLET_ID", walletAId)
            .putString("WALLET_NAME", "Wallet A")
            .putInt("DAY_OF_MONTH", currentDay)
            .putString("INTERVAL", "MONTHLY")
            .build()

        val worker = TestListenableWorkerBuilder<RecurringTransactionWorker>(context)
            .setInputData(input)
            .build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)

        // Verify balance updated
        val updatedWallet = db.walletDao().getWalletById(walletAId, userAId)
        assertEquals(3000000.0, updatedWallet!!.balance, 0.01)

        // Verify transaction carries matching userId
        val txs = db.transactionDao().getAllTransactions(userAId).first()
        assertEquals(1, txs.size)
        assertEquals(userAId, txs[0].userId)
        assertEquals("Gaji Bulanan (Auto Recurring)", txs[0].title)
    }
}
