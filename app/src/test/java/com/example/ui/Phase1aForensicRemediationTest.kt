package com.example.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.chatbot.parser.LocalFinancialIntentParser
import com.example.data.chatbot.parser.WalletDraftManager
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.TransactionType
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
class Phase1aForensicRemediationTest {

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
    fun guestUserWithoutReward_deniedAiWalletCreation_databaseUnchanged() = runBlocking {
        val guestUser = User(
            id = 99L,
            username = "Guest User",
            email = "guest@example.com",
            role = UserRole.FREE_MEMBER,
            isGuest = true
        )
        database.userDao().insertUser(guestUser)
        SessionManager.setSessionForTesting(guestUser.id, guestUser)

        val cmd = LocalFinancialIntentParser.parse(
            userMessage = "Create wallet Dana opening balance 100k",
            availableWallets = emptyList()
        )
        val draft = WalletDraftManager.createDraft(cmd)
        org.junit.Assert.assertNotNull(draft)

        val countBefore = database.walletDao().getWalletCount(guestUser.id)
        assertEquals(0, countBefore)

        // Authoritative execution boundary check for Guest without reward
        val isUnlocked = { EntitlementManager.evaluate(guestUser, 0, System.currentTimeMillis()).isPremiumUnlocked }
        assertFalse("Guest without reward must evaluate to locked", isUnlocked())

        val result = WalletDraftManager.confirmAndSave(
            draftId = draft!!.id,
            repository = repository,
            userId = guestUser.id,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = isUnlocked
        )

        assertTrue("Execution must fail when entitlement is denied", result.isFailure)
        assertTrue("Error must be SecurityException", result.exceptionOrNull() is SecurityException)

        // Database must remain 100% UNCHANGED
        val countAfter = database.walletDao().getWalletCount(guestUser.id)
        assertEquals("Database must not have new wallets", 0, countAfter)
    }

    @Test
    fun freeUserWithoutSmartMaster_deniedAiWalletCreation_databaseUnchanged() = runBlocking {
        val freeUser = User(
            id = 101L,
            username = "Free Member",
            email = "free@example.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false
        )
        database.userDao().insertUser(freeUser)
        SessionManager.setSessionForTesting(freeUser.id, freeUser)

        val cmd = LocalFinancialIntentParser.parse(
            userMessage = "Buat dompet Gopay saldo awal 50000",
            availableWallets = emptyList()
        )
        val draft = WalletDraftManager.createDraft(cmd)
        org.junit.Assert.assertNotNull(draft)

        val isUnlocked = { EntitlementManager.evaluate(freeUser, 0, System.currentTimeMillis()).isPremiumUnlocked }
        assertFalse(isUnlocked())

        val result = WalletDraftManager.confirmAndSave(
            draftId = draft!!.id,
            repository = repository,
            userId = freeUser.id,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = isUnlocked
        )

        assertTrue(result.isFailure)
        val countAfter = database.walletDao().getWalletCount(freeUser.id)
        assertEquals(0, countAfter)
    }

    @Test
    fun rewardedSmartMasterUser_within60Minutes_allowsExecution() = runBlocking {
        val now = System.currentTimeMillis()
        val rewardedGuest = User(
            id = 200L,
            username = "Rewarded Guest",
            email = "guest@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = true,
            subscriptionExpiresAt = now + 1800000L // 30 mins remaining
        )
        database.userDao().insertUser(rewardedGuest)
        SessionManager.setSessionForTesting(rewardedGuest.id, rewardedGuest)

        val cmd = LocalFinancialIntentParser.parse(
            userMessage = "Create wallet BCA opening balance 250000",
            availableWallets = emptyList()
        )
        val draft = WalletDraftManager.createDraft(cmd)
        org.junit.Assert.assertNotNull(draft)

        val isUnlocked = { EntitlementManager.evaluate(rewardedGuest, 0, now).isPremiumUnlocked }
        assertTrue("Rewarded user within 60 mins must be unlocked", isUnlocked())

        val result = WalletDraftManager.confirmAndSave(
            draftId = draft!!.id,
            repository = repository,
            userId = rewardedGuest.id,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = isUnlocked
        )

        assertTrue("Execution must succeed for valid rewarded user: ${result.exceptionOrNull()}", result.isSuccess)
        val createdWallet = result.getOrNull()
        org.junit.Assert.assertNotNull(createdWallet)
        assertEquals("BCA", createdWallet?.name)

        val walletsAfter = database.walletDao().getAllWallets(rewardedGuest.id).first()
        assertEquals(1, walletsAfter.size)
        assertEquals(250000.0, walletsAfter[0].balance, 0.01)
    }

    @Test
    fun rewardedSmartMasterUser_afterExpiry_revokedAndDatabaseUnchanged() = runBlocking {
        val now = System.currentTimeMillis()
        val expiredRewardGuest = User(
            id = 201L,
            username = "Expired Guest",
            email = "guest@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = true,
            subscriptionExpiresAt = now - 5000L // Expired 5 seconds ago
        )
        database.userDao().insertUser(expiredRewardGuest)
        SessionManager.setSessionForTesting(expiredRewardGuest.id, expiredRewardGuest)

        val cmd = LocalFinancialIntentParser.parse(
            userMessage = "Create wallet Jago opening balance 50000",
            availableWallets = emptyList()
        )
        val draft = WalletDraftManager.createDraft(cmd)
        org.junit.Assert.assertNotNull(draft)

        val isUnlocked = { EntitlementManager.evaluate(expiredRewardGuest, 0, now).isPremiumUnlocked }
        assertFalse("Expired reward must evaluate to locked", isUnlocked())

        val result = WalletDraftManager.confirmAndSave(
            draftId = draft!!.id,
            repository = repository,
            userId = expiredRewardGuest.id,
            accountMode = "PERSONAL",
            isEntitlementUnlocked = isUnlocked
        )

        assertTrue("Expired reward must be blocked at execution boundary", result.isFailure)
        val countAfter = database.walletDao().getWalletCount(expiredRewardGuest.id)
        assertEquals("Database must remain unchanged", 0, countAfter)
    }

    @Test
    fun accountSwitch_doesNotCarryOverEntitlement() = runBlocking {
        val now = System.currentTimeMillis()
        val smartMasterUser = User(
            id = 301L,
            username = "Master User",
            email = "master@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = false
        )
        val plainGuestUser = User(
            id = 302L,
            username = "New Guest",
            email = "guest@example.com",
            role = UserRole.FREE_MEMBER,
            isGuest = true
        )
        database.userDao().insertUser(smartMasterUser)
        database.userDao().insertUser(plainGuestUser)

        var activeUser = smartMasterUser
        assertTrue(EntitlementManager.evaluate(activeUser, 0, now).isPremiumUnlocked)

        // Switch account to plain Guest
        activeUser = plainGuestUser
        SessionManager.setSessionForTesting(plainGuestUser.id, plainGuestUser)

        // Entitlement must immediately be locked
        assertFalse("Switched guest must not inherit previous user entitlement", EntitlementManager.evaluate(activeUser, 0, now).isPremiumUnlocked)
    }

    @Test
    fun manualWalletAndTransactionCreation_forFreeUser_remainsAllowed() = runBlocking {
        val freeUser = User(
            id = 401L,
            username = "Free User",
            email = "free@example.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false
        )
        database.userDao().insertUser(freeUser)
        SessionManager.setSessionForTesting(freeUser.id, freeUser)

        // Free user manually creates a wallet (via manual wallet dialog)
        val manualWallet = Wallet(
            name = "Dompet Tunai",
            type = WalletType.CASH,
            accountNumber = "",
            balance = 50000.0,
            accountMode = "PERSONAL",
            userId = freeUser.id
        )
        val insertedId = repository.insertWallet(manualWallet)
        assertTrue(insertedId > 0)

        val wallets = database.walletDao().getAllWallets(freeUser.id).first()
        assertEquals(1, wallets.size)
        assertEquals("Dompet Tunai", wallets[0].name)
    }
}
