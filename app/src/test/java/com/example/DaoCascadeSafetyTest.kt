package com.example

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinSmartDatabase
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
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
class DaoCascadeSafetyTest {

    private lateinit var db: FinSmartDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `T-01 UserDao insert conflict must ABORT without triggering cascade deletion`() = runBlocking {
        val user = User(
            id = 1L,
            username = "original_user",
            email = "user@example.com",
            passwordHash = "hash",
            role = UserRole.FREE_MEMBER,
            createdAt = 1000L
        )
        db.userDao().insertUser(user)

        val wallet = Wallet(
            id = 10L,
            name = "Main Wallet",
            type = WalletType.BANK,
            balance = 500000.0,
            userId = 1L
        )
        db.walletDao().insertWallet(wallet)

        val tx = TransactionItem(
            id = 100L,
            title = "Groceries",
            amount = 50000.0,
            type = TransactionType.EXPENSE,
            category = "Food",
            categoryIcon = "food",
            walletId = 10L,
            walletName = "Main Wallet",
            timestamp = 2000L,
            userId = 1L
        )
        db.transactionDao().insertTransaction(tx)

        // Attempt duplicate insert on the same PK
        val conflictingUser = User(
            id = 1L,
            username = "imposter_user",
            email = "imposter@example.com",
            passwordHash = "hash2",
            role = UserRole.FREE_MEMBER,
            createdAt = 3000L
        )

        try {
            db.userDao().insertUser(conflictingUser)
            fail("Expected SQLiteConstraintException upon duplicate user PK insertion")
        } catch (e: SQLiteConstraintException) {
            // Expected under OnConflictStrategy.ABORT
            assertTrue(e.message?.contains("UNIQUE") == true || e.message?.contains("constraint") == true)
        }

        // Verify that the original user and ALL dependent records (wallet, transaction) are preserved intact!
        val preservedUser = db.userDao().getUserById(1L)
        assertNotNull(preservedUser)
        assertEquals("original_user", preservedUser?.username)

        val preservedWallet = db.walletDao().getWalletById(10L, 1L)
        assertNotNull(preservedWallet)
        assertEquals(500000.0, preservedWallet?.balance ?: 0.0, 0.001)

        val txCount = db.transactionDao().getTransactionCount(1L)
        assertEquals(1, txCount)
    }
}
