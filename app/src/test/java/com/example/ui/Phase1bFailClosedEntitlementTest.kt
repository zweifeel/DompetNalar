package com.example.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.chatbot.parser.LocalFinancialIntentParser
import com.example.data.chatbot.parser.WalletDraftManager
import com.example.data.local.FinSmartDatabase
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.data.repository.FinanceRepository
import com.example.data.session.SessionManager
import com.example.util.EntitlementManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase1bFailClosedEntitlementTest {

    private lateinit var context: Context
    private lateinit var database: FinSmartDatabase
    private lateinit var repository: FinanceRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(
            walletDao = database.walletDao(),
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            database = database
        )
    }

    @After
    fun tearDown() {
        SessionManager.resetForTesting()
        database.close()
    }

    @Test
    fun guestWithoutEntitlement_rejectedAndDatabaseUnchanged() = runBlocking {
        val user = User(id = 501L, username = "Guest", email = "g@example.com", role = UserRole.FREE_MEMBER, isGuest = true)
        database.userDao().insertUser(user)
        SessionManager.setSessionForTesting(user.id, user)

        val cmd = LocalFinancialIntentParser.parse("Create wallet OVO opening balance 20000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd)!!

        val countBefore = database.walletDao().getWalletCount(user.id)
        assertEquals(0, countBefore)

        val result = WalletDraftManager.confirmAndSave(
            draftId = draft.id,
            repository = repository,
            userId = user.id,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = { EntitlementManager.evaluate(user, 0, System.currentTimeMillis()).isPremiumUnlocked }
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertEquals("Database must be unchanged", 0, database.walletDao().getWalletCount(user.id))
    }

    @Test
    fun freeMemberWithoutEntitlement_rejectedAndDatabaseUnchanged() = runBlocking {
        val user = User(id = 502L, username = "Free", email = "f@example.com", role = UserRole.FREE_MEMBER, isGuest = false)
        database.userDao().insertUser(user)
        SessionManager.setSessionForTesting(user.id, user)

        val cmd = LocalFinancialIntentParser.parse("Create wallet ShopeePay opening balance 30000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd)!!

        val result = WalletDraftManager.confirmAndSave(
            draftId = draft.id,
            repository = repository,
            userId = user.id,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = { EntitlementManager.evaluate(user, 0, System.currentTimeMillis()).isPremiumUnlocked }
        )

        assertTrue(result.isFailure)
        assertEquals(0, database.walletDao().getWalletCount(user.id))
    }

    @Test
    fun rewardedSmartMaster_active_succeeds() = runBlocking {
        val now = System.currentTimeMillis()
        val user = User(
            id = 503L,
            username = "Rewarded",
            email = "r@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = true,
            subscriptionExpiresAt = now + 1800000L
        )
        database.userDao().insertUser(user)
        SessionManager.setSessionForTesting(user.id, user)

        val cmd = LocalFinancialIntentParser.parse("Create wallet Mandiri opening balance 500000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd)!!

        val result = WalletDraftManager.confirmAndSave(
            draftId = draft.id,
            repository = repository,
            userId = user.id,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = { EntitlementManager.evaluate(user, 0, now).isPremiumUnlocked }
        )

        assertTrue(result.isSuccess)
        assertEquals(1, database.walletDao().getWalletCount(user.id))
        val wallet = database.walletDao().getAllWallets(user.id).first()[0]
        assertEquals("Mandiri", wallet.name)
        assertEquals(500000.0, wallet.balance, 0.01)
    }

    @Test
    fun rewardedSmartMaster_expired_rejectedAndDatabaseUnchanged() = runBlocking {
        val now = System.currentTimeMillis()
        val user = User(
            id = 504L,
            username = "Expired",
            email = "e@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = true,
            subscriptionExpiresAt = now - 1000L
        )
        database.userDao().insertUser(user)
        SessionManager.setSessionForTesting(user.id, user)

        val cmd = LocalFinancialIntentParser.parse("Create wallet BNI opening balance 100000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd)!!

        val result = WalletDraftManager.confirmAndSave(
            draftId = draft.id,
            repository = repository,
            userId = user.id,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = { EntitlementManager.evaluate(user, 0, now).isPremiumUnlocked }
        )

        assertTrue(result.isFailure)
        assertEquals(0, database.walletDao().getWalletCount(user.id))
    }

    @Test
    fun paidSmartMaster_active_succeeds() = runBlocking {
        val now = System.currentTimeMillis()
        val user = User(
            id = 505L,
            username = "Paid Master",
            email = "paid@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = now + 30L * 86400000L // 30 days
        )
        database.userDao().insertUser(user)
        SessionManager.setSessionForTesting(user.id, user)

        val cmd = LocalFinancialIntentParser.parse("Create wallet Permata opening balance 150000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd)!!

        val result = WalletDraftManager.confirmAndSave(
            draftId = draft.id,
            repository = repository,
            userId = user.id,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = { EntitlementManager.evaluate(user, 0, now).isPremiumUnlocked }
        )

        assertTrue(result.isSuccess)
        assertEquals(1, database.walletDao().getWalletCount(user.id))
    }

    @Test
    fun accountSwitching_revokesEntitlementAndDatabaseUnchanged() = runBlocking {
        val now = System.currentTimeMillis()
        val masterUser = User(id = 506L, username = "Master", email = "m@example.com", role = UserRole.SMART_MASTER, isGuest = false)
        val guestUser = User(id = 507L, username = "Guest2", email = "g2@example.com", role = UserRole.FREE_MEMBER, isGuest = true)
        database.userDao().insertUser(masterUser)
        database.userDao().insertUser(guestUser)

        // Switch to guest
        SessionManager.setSessionForTesting(guestUser.id, guestUser)

        val cmd = LocalFinancialIntentParser.parse("Create wallet Jago opening balance 50000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd)!!

        val result = WalletDraftManager.confirmAndSave(
            draftId = draft.id,
            repository = repository,
            userId = guestUser.id,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = { EntitlementManager.evaluate(guestUser, 0, now).isPremiumUnlocked }
        )

        assertTrue("Switched guest must be rejected", result.isFailure)
        assertEquals(0, database.walletDao().getWalletCount(guestUser.id))
    }

    @Test
    fun logout_revokesEntitlementAndDatabaseUnchanged() = runBlocking {
        val now = System.currentTimeMillis()
        val masterUser = User(id = 508L, username = "Master3", email = "m3@example.com", role = UserRole.SMART_MASTER, isGuest = false)
        database.userDao().insertUser(masterUser)
        SessionManager.setSessionForTesting(masterUser.id, masterUser)

        val cmd = LocalFinancialIntentParser.parse("Create wallet BRI opening balance 75000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = masterUser.id)!!

        // User logs out
        SessionManager.logout()

        val activeUserId = SessionManager.getActiveUserId() ?: 0L

        val result = WalletDraftManager.confirmAndSave(
            draftId = draft.id,
            repository = repository,
            userId = activeUserId,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = { false } // No active authenticated session
        )

        assertTrue("Logged out session must fail closed", result.isFailure)
        assertEquals(0, database.walletDao().getWalletCount(masterUser.id))
    }

    @Test
    fun callbackThrowsException_failsClosedAndDatabaseUnchanged() = runBlocking {
        val user = User(id = 509L, username = "ErrorUser", email = "err@example.com", role = UserRole.SMART_MASTER, isGuest = false)
        database.userDao().insertUser(user)
        SessionManager.setSessionForTesting(user.id, user)

        val cmd = LocalFinancialIntentParser.parse("Create wallet Danamon opening balance 100000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd)!!

        val result = WalletDraftManager.confirmAndSave(
            draftId = draft.id,
            repository = repository,
            userId = user.id,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = { throw RuntimeException("Simulated network or billing crash") }
        )

        assertTrue("Exception in callback must fail closed", result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertEquals(0, database.walletDao().getWalletCount(user.id))
    }

    @Test
    fun callbackReturnsFalse_failsClosedAndDatabaseUnchanged() = runBlocking {
        val user = User(id = 510L, username = "DeniedUser", email = "denied@example.com", role = UserRole.FREE_MEMBER, isGuest = false)
        database.userDao().insertUser(user)
        SessionManager.setSessionForTesting(user.id, user)

        val cmd = LocalFinancialIntentParser.parse("Create wallet Seabank opening balance 100000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd)!!

        val result = WalletDraftManager.confirmAndSave(
            draftId = draft.id,
            repository = repository,
            userId = user.id,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = { false }
        )

        assertTrue(result.isFailure)
        assertEquals(0, database.walletDao().getWalletCount(user.id))
    }

    @Test
    fun invalidUserId_failsClosedAndDatabaseUnchanged() = runBlocking {
        val user = User(id = 509L, username = "ValidUser", email = "v@example.com", role = UserRole.SMART_MASTER)
        database.userDao().insertUser(user)
        SessionManager.setSessionForTesting(user.id, user)

        val cmd = LocalFinancialIntentParser.parse("Create wallet Cash opening balance 10000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = user.id)!!

        val result = WalletDraftManager.confirmAndSave(
            draftId = draft.id,
            repository = repository,
            userId = -1L,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = { true }
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertEquals(0, database.walletDao().getWalletCount(user.id))
    }

    @Test
    fun manualTransactionAndWallet_forFreeMember_continuesToWork() = runBlocking {
        val freeUser = User(id = 511L, username = "FreeUser2", email = "free2@example.com", role = UserRole.FREE_MEMBER, isGuest = false)
        database.userDao().insertUser(freeUser)
        SessionManager.setSessionForTesting(freeUser.id, freeUser)

        val manualWallet = Wallet(
            name = "Dompet Fisik",
            type = WalletType.CASH,
            accountNumber = "",
            balance = 30000.0,
            accountMode = "PERSONAL",
            userId = freeUser.id
        )
        val insertedId = repository.insertWallet(manualWallet)
        assertTrue(insertedId > 0)
        assertEquals(1, database.walletDao().getWalletCount(freeUser.id))
    }
}
