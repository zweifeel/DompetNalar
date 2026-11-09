package com.example.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.chatbot.parser.LocalFinancialIntentParser
import com.example.data.chatbot.parser.TransactionDraftManager
import com.example.data.chatbot.parser.WalletDraftManager
import com.example.data.local.FinSmartDatabase
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.data.repository.FinanceRepository
import com.example.data.session.SessionManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase1cMultiAccountDraftOwnershipTest {

    private lateinit var context: Context
    private lateinit var database: FinSmartDatabase
    private lateinit var repository: FinanceRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        SessionManager.initialize(context)
        database = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(
            walletDao = database.walletDao(),
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            database = database
        )
        WalletDraftManager.clearAllDrafts()
        TransactionDraftManager.clearAllDrafts()
    }

    @After
    fun tearDown() {
        SessionManager.resetForTesting()
        WalletDraftManager.clearAllDrafts()
        TransactionDraftManager.clearAllDrafts()
        database.close()
    }

    @Test
    fun accountA_createsWalletDraft_accountB_cannotConfirm_databaseUnchanged() = runBlocking {
        val userA = User(id = 701L, username = "User A", email = "usera@example.com", role = UserRole.SMART_MASTER, isGuest = false)
        val userB = User(id = 702L, username = "User B", email = "userb@example.com", role = UserRole.SMART_MASTER, isGuest = false)
        database.userDao().insertUser(userA)
        database.userDao().insertUser(userB)

        SessionManager.login(userA)
        val cmd = LocalFinancialIntentParser.parse("Create wallet Tabungan A opening balance 100000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = userA.id)
        assertNotNull(draft)
        assertEquals(userA.id, draft!!.ownerUserId)

        // User B attempts to confirm draft of User A
        val result = WalletDraftManager.confirmAndSave(
            draftId = draft.id,
            repository = repository,
            userId = userB.id,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = { true }
        )

        assertTrue("Confirmation by User B must fail", result.isFailure)
        assertTrue("Exception must be SecurityException", result.exceptionOrNull() is SecurityException)
        assertTrue(result.exceptionOrNull()?.message?.contains("bukan milik pengguna aktif") == true || result.exceptionOrNull()?.message?.contains("dimiliki oleh akun lain") == true)

        // Database must remain completely empty for both users
        assertEquals(0, database.walletDao().getWalletCount(userA.id))
        assertEquals(0, database.walletDao().getWalletCount(userB.id))
    }

    @Test
    fun accountA_createsTransactionDraft_accountB_cannotConfirm_databaseUnchanged() = runBlocking {
        val userA = User(id = 701L, username = "User A", email = "usera@example.com", role = UserRole.FREE_MEMBER, isGuest = false)
        val userB = User(id = 702L, username = "User B", email = "userb@example.com", role = UserRole.FREE_MEMBER, isGuest = false)
        database.userDao().insertUser(userA)
        database.userDao().insertUser(userB)

        val wId = database.walletDao().insertWallet(
            Wallet(name = "BCA A", type = WalletType.BANK, balance = 500000.0, userId = userA.id, currencyCode = "IDR")
        )
        val walletA = database.walletDao().getWalletById(wId, userA.id)!!

        val cmd = LocalFinancialIntentParser.parse("Beli kopi 25rb pakai BCA A", listOf(walletA))
        val draft = TransactionDraftManager.createDraft(cmd, ownerUserId = userA.id)
        assertNotNull(draft)
        assertEquals(userA.id, draft!!.ownerUserId)

        // User B attempts to confirm transaction draft of User A
        val result = TransactionDraftManager.confirmAndSave(
            draftId = draft.id,
            repository = repository,
            userId = userB.id,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = { true }
        )

        assertTrue("Confirmation by User B must fail", result.isFailure)
        assertTrue("Exception must be SecurityException", result.exceptionOrNull() is SecurityException)
        assertTrue(result.exceptionOrNull()?.message?.contains("bukan milik pengguna aktif") == true || result.exceptionOrNull()?.message?.contains("dimiliki oleh akun lain") == true)

        // Transactions table must remain empty
        assertEquals(0, database.transactionDao().getTransactionCount(userA.id))
        assertEquals(0, database.transactionDao().getTransactionCount(userB.id))
    }

    @Test
    fun accountA_createsWalletDraft_logsOut_draftsPurged_cannotConfirm() = runBlocking {
        val userA = User(id = 701L, username = "User A", email = "usera@example.com", role = UserRole.SMART_MASTER, isGuest = false)
        database.userDao().insertUser(userA)
        SessionManager.login(userA)

        val cmd = LocalFinancialIntentParser.parse("Create wallet Dana opening balance 50000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = userA.id)!!
        assertNotNull(WalletDraftManager.getDraft(draft.id))

        // User logs out -> all drafts must be purged
        SessionManager.logout()
        assertNull("Draft must be cleared on logout", WalletDraftManager.getDraft(draft.id))

        val result = WalletDraftManager.confirmAndSave(
            draftId = draft.id,
            repository = repository,
            userId = userA.id,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = { true }
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(0, database.walletDao().getWalletCount(userA.id))
    }

    @Test
    fun accountA_createsTransactionDraft_clearsSession_draftsPurged_cannotConfirm() = runBlocking {
        val userA = User(id = 701L, username = "User A", email = "usera@example.com", role = UserRole.FREE_MEMBER, isGuest = false)
        database.userDao().insertUser(userA)
        val walletA = Wallet(id = 12L, name = "Gopay", type = WalletType.EWALLET, balance = 100000.0, userId = userA.id)
        database.walletDao().insertWallet(walletA)

        val cmd = LocalFinancialIntentParser.parse("Makan siang 35000 pakai Gopay", listOf(walletA))
        val draft = TransactionDraftManager.createDraft(cmd, ownerUserId = userA.id)!!
        assertNotNull(TransactionDraftManager.getDraft(draft.id))

        // Session cleared
        SessionManager.clearSession()
        assertNull("Draft must be cleared on clearSession", TransactionDraftManager.getDraft(draft.id))

        val result = TransactionDraftManager.confirmAndSave(
            draftId = draft.id,
            repository = repository,
            userId = userA.id,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = { true }
        )
        assertTrue(result.isFailure)
        assertEquals(0, database.transactionDao().getTransactionCount(userA.id))
    }

    @Test
    fun accountSwitch_purgesPreviousAccountDrafts() = runBlocking {
        val userA = User(id = 701L, username = "User A", email = "usera@example.com", role = UserRole.SMART_MASTER, isGuest = false)
        val userB = User(id = 702L, username = "User B", email = "userb@example.com", role = UserRole.SMART_MASTER, isGuest = false)
        database.userDao().insertUser(userA)
        database.userDao().insertUser(userB)

        SessionManager.login(userA)
        val cmd = LocalFinancialIntentParser.parse("Create wallet Mandiri opening balance 200000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = userA.id)!!
        assertNotNull(WalletDraftManager.getDraft(draft.id))

        // Switch to User B
        SessionManager.login(userB)
        assertNull("Draft of User A must be cleared on switch to User B", WalletDraftManager.getDraft(draft.id))
    }

    @Test
    fun invalidUserId_zeroOrNegative_cannotConfirmDraft() = runBlocking {
        val cmd = LocalFinancialIntentParser.parse("Create wallet BNI opening balance 75000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = 701L)!!

        val resultZero = WalletDraftManager.confirmAndSave(
            draftId = draft.id,
            repository = repository,
            userId = 0L,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = { true }
        )
        assertTrue(resultZero.isFailure)
        assertTrue(resultZero.exceptionOrNull() is SecurityException)

        val resultNegative = WalletDraftManager.confirmAndSave(
            draftId = draft.id,
            repository = repository,
            userId = -1L,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = { true }
        )
        assertTrue(resultNegative.isFailure)
        assertTrue(resultNegative.exceptionOrNull() is SecurityException)
    }

    @Test
    fun legitimateOwner_matchingUserId_withEntitlement_confirmsSuccessfully() = runBlocking {
        val userA = User(id = 701L, username = "User A", email = "usera@example.com", role = UserRole.SMART_MASTER, isGuest = false)
        database.userDao().insertUser(userA)
        SessionManager.login(userA)

        val cmd = LocalFinancialIntentParser.parse("Create wallet Tabungan BCA opening balance 300000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = userA.id)!!

        val result = WalletDraftManager.confirmAndSave(
            draftId = draft.id,
            repository = repository,
            userId = userA.id,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = { true }
        )

        assertTrue("Legitimate owner confirmation should succeed", result.isSuccess)
        val created = result.getOrNull()
        assertNotNull(created)
        assertEquals("Tabungan BCA", created!!.name)
        assertEquals(300000.0, created.balance, 0.01)
        assertEquals(1, database.walletDao().getWalletCount(userA.id))
    }

    @Test
    fun legitimateOwner_createsTransactionDraft_confirmsSuccessfully() = runBlocking {
        val userA = User(id = 701L, username = "User A", email = "usera@example.com", role = UserRole.FREE_MEMBER, isGuest = false)
        database.userDao().insertUser(userA)
        val wId = database.walletDao().insertWallet(
            Wallet(name = "Cash", type = WalletType.CASH, balance = 200000.0, userId = userA.id, currencyCode = "IDR")
        )
        val wallet = database.walletDao().getWalletById(wId, userA.id)!!

        val cmd = LocalFinancialIntentParser.parse("Beli bensin 50000 pakai Cash", listOf(wallet))
        val draft = TransactionDraftManager.createDraft(cmd, ownerUserId = userA.id)!!

        val result = TransactionDraftManager.confirmAndSave(
            draftId = draft.id,
            repository = repository,
            userId = userA.id,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = { true }
        )

        assertTrue(result.isSuccess)
        assertEquals(1, database.transactionDao().getTransactionCount(userA.id))
    }
}
