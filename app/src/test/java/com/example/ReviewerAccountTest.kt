package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.config.ReviewerAccountConfig
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.data.session.SessionManager
import com.example.util.EntitlementManager
import com.example.util.EntitlementSource
import com.example.util.PasswordHasher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
class ReviewerAccountTest {

    private lateinit var context: Context
    private lateinit var db: FinSmartDatabase

    // Rotated strong plaintext password used strictly for verification tests
    private val rotatedPlaintextPassword = "Rev!ew2026#FinSmart$"

    @Before
    fun setup() {
        com.example.data.local.DatabaseWriteCoordinator.resetForTesting()
        SessionManager.resetForTesting()
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        SessionManager.initialize(context)
    }

    @After
    fun tearDown() {
        com.example.data.local.DatabaseWriteCoordinator.resetForTesting()
        SessionManager.resetForTesting()
        db.close()
    }

    @Test
    fun testReviewerAccountProvisioningAndIdempotency() = runBlocking {
        // Ensure base initial data - clean initialization per Phase 1G
        FinSmartDatabase.populateInitialData(db)
        val initialUsers = db.userDao().getAllUsers().first()
        assertEquals("Initial data has no dummy accounts", 0, initialUsers.size)

        // Provision reviewer account on fresh database
        FinSmartDatabase.ensureReviewerAccount(db)
        val usersAfterFirst = db.userDao().getAllUsers().first()
        assertEquals("Reviewer account added", 1, usersAfterFirst.size)

        val reviewer = db.userDao().getUserByIdentifier(ReviewerAccountConfig.REVIEWER_EMAIL)
        assertNotNull("Reviewer must be retrievable by email", reviewer)
        assertEquals(ReviewerAccountConfig.DEFAULT_REVIEWER_USER_ID, reviewer!!.id)
        assertEquals(ReviewerAccountConfig.REVIEWER_USERNAME, reviewer.username)
        assertEquals(ReviewerAccountConfig.REVIEWER_EMAIL, reviewer.email)
        assertEquals(UserRole.SMART_MASTER, reviewer.role)
        assertNull("Reviewer entitlement does not expire", reviewer.subscriptionExpiresAt)
        assertTrue(reviewer.isApproved)
        assertFalse(reviewer.isGuest)

        // Verify retrieval by username as well
        val reviewerByUsername = db.userDao().getUserByIdentifier(ReviewerAccountConfig.REVIEWER_USERNAME)
        assertNotNull("Reviewer must be retrievable by username", reviewerByUsername)
        assertEquals(reviewer.id, reviewerByUsername!!.id)

        // Idempotency: second call must not duplicate user or data
        FinSmartDatabase.ensureReviewerAccount(db)
        val usersAfterSecond = db.userDao().getAllUsers().first()
        assertEquals("Calling ensureReviewerAccount again must be idempotent", 1, usersAfterSecond.size)
    }

    @Test
    fun testReviewerPasswordAuthenticationWithRotatedSecret() = runBlocking {
        FinSmartDatabase.ensureReviewerAccount(db)
        val reviewer = db.userDao().getUserByIdentifier(ReviewerAccountConfig.REVIEWER_EMAIL)!!

        // Password hash must be valid PBKDF2
        assertTrue("Stored hash must be valid PBKDF2 format", PasswordHasher.isHashed(reviewer.passwordHash))
        assertEquals("Stored hash must match configured PBKDF2 hash", ReviewerAccountConfig.REVIEWER_PASSWORD_HASH, reviewer.passwordHash)

        // Correct rotated password verification
        val isCorrectValid = PasswordHasher.verifyPassword(
            rotatedPlaintextPassword,
            reviewer.passwordHash
        )
        assertTrue("Valid rotated reviewer password must verify successfully", isCorrectValid)

        // Old exposed password must be rejected
        val isOldValid = PasswordHasher.verifyPassword(
            "ReviewerFinSmart2026!",
            reviewer.passwordHash
        )
        assertFalse("Old exposed password must be rejected after rotation", isOldValid)

        // Incorrect password verification
        val isWrongValid = PasswordHasher.verifyPassword(
            "WrongPassword123!",
            reviewer.passwordHash
        )
        assertFalse("Incorrect password must be rejected", isWrongValid)

        // Blank password verification
        val isBlankValid = PasswordHasher.verifyPassword(
            "",
            reviewer.passwordHash
        )
        assertFalse("Blank password must be rejected", isBlankValid)
    }

    @Test
    fun testProvisioningWithExistingDatabaseAndIdConflict() = runBlocking {
        // Simulate existing database with user #1 and user #2 (different customer)
        val existingUser1 = User(id = 1L, username = "Lukas", email = "lukas@example.com", role = UserRole.MASTER_DEV)
        val existingUser2 = User(id = 2L, username = "ExistingCustomer", email = "customer@example.com", role = UserRole.FREE_MEMBER)
        db.userDao().insertUsers(listOf(existingUser1, existingUser2))

        val customerWallet = Wallet(
            id = 200L,
            name = "Dompet Customer Asli",
            type = WalletType.BANK,
            balance = 1000000.0,
            userId = 2L
        )
        db.walletDao().insertWallet(customerWallet)

        val customerTx = TransactionItem(
            id = 500L,
            title = "Transaksi Customer Asli",
            amount = 50000.0,
            type = TransactionType.EXPENSE,
            category = "Belanja",
            walletId = 200L,
            walletName = "Dompet Customer Asli",
            userId = 2L
        )
        db.transactionDao().insertTransaction(customerTx)

        // Provision reviewer account on existing database where ID 2 is already taken
        FinSmartDatabase.ensureReviewerAccount(db)

        // Verify ExistingCustomer with ID 2 is completely unchanged
        val preservedUser2 = db.userDao().getUserById(2L)
        assertNotNull("Existing user 2 must still exist", preservedUser2)
        assertEquals("ExistingCustomer", preservedUser2!!.username)
        assertEquals("customer@example.com", preservedUser2.email)
        assertEquals(UserRole.FREE_MEMBER, preservedUser2.role)

        val preservedWallet = db.walletDao().getWalletById(200L, 2L)
        assertNotNull("Existing customer wallet must not be deleted or modified", preservedWallet)
        assertEquals(1000000.0, preservedWallet!!.balance, 0.001)

        val preservedTxs = db.transactionDao().getAllTransactions(2L).first()
        assertEquals(1, preservedTxs.size)
        assertEquals("Transaksi Customer Asli", preservedTxs[0].title)

        // Verify Reviewer was assigned a new collision-free ID (e.g. 3L)
        val reviewer = db.userDao().getUserByIdentifier(ReviewerAccountConfig.REVIEWER_EMAIL)
        assertNotNull("Reviewer must be successfully provisioned", reviewer)
        assertNotEquals("Reviewer must NOT take ID 2 from existing customer", 2L, reviewer!!.id)
        assertTrue("Reviewer must have ID > 2", reviewer.id > 2L)
        assertEquals(UserRole.SMART_MASTER, reviewer.role)

        // Verify Reviewer synthetic wallets belong to reviewer.id, not customer
        val reviewerWallets = db.walletDao().getAllWallets(reviewer.id).first()
        assertTrue("Reviewer must have own synthetic wallets", reviewerWallets.isNotEmpty())
        assertTrue("Reviewer wallets must belong to reviewer.id", reviewerWallets.all { it.userId == reviewer.id })
    }

    @Test
    fun testReviewerEntitlementEvaluation() = runBlocking {
        FinSmartDatabase.ensureReviewerAccount(db)
        val reviewer = db.userDao().getUserByIdentifier(ReviewerAccountConfig.REVIEWER_EMAIL)!!

        val status = EntitlementManager.evaluate(reviewer)
        assertTrue("Reviewer has premium features unlocked", status.isPremiumUnlocked)
        assertEquals("Effective role is SMART_MASTER", UserRole.SMART_MASTER, status.effectiveRole)
        assertEquals("Primary source is DEVELOPER_GRANT", EntitlementSource.DEVELOPER_GRANT, status.primarySource)
        assertFalse("Reviewer status is not expired", status.isExpired)
        assertNull("Reviewer has no expiration date", status.expiresAt)

        // Guest user must NOT have premium unlocked and must NOT receive 3-day trial
        val guestUser = User(id = 999L, username = "Guest", email = "guest@finsmart.local", isGuest = true)
        val guestStatus = EntitlementManager.evaluate(guestUser)
        assertFalse("Guest user must NOT have premium unlocked", guestStatus.isPremiumUnlocked)
        assertEquals("Guest effective role is FREE_MEMBER", UserRole.FREE_MEMBER, guestStatus.effectiveRole)
        assertEquals("Guest primary source is NONE", EntitlementSource.NONE, guestStatus.primarySource)
        assertEquals("Guest has 0 trial days remaining", 0, guestStatus.trialDaysRemaining)
    }

    @Test
    fun testReviewerHasNoDeveloperPrivileges() = runBlocking {
        FinSmartDatabase.ensureReviewerAccount(db)
        val reviewer = db.userDao().getUserByIdentifier(ReviewerAccountConfig.REVIEWER_EMAIL)!!

        // Reviewer must NOT be MASTER_DEV
        assertFalse("Reviewer must not have MASTER_DEV role", reviewer.role == UserRole.MASTER_DEV)
        assertFalse("Reviewer username must not be Lukas", reviewer.username.equals("Lukas", ignoreCase = true))
        assertFalse("Reviewer email must not be lunachroc07@gmail.com", reviewer.email.equals("lunachroc07@gmail.com", ignoreCase = true))
    }

    @Test
    fun testReviewerSyntheticDataIsolation() = runBlocking {
        FinSmartDatabase.populateInitialData(db)
        FinSmartDatabase.ensureReviewerAccount(db)

        val reviewer = db.userDao().getUserByIdentifier(ReviewerAccountConfig.REVIEWER_EMAIL)!!
        val reviewerId = reviewer.id
        val lukasId = 1L

        // Reviewer Wallets
        val reviewerWallets = db.walletDao().getAllWallets(reviewerId).first()
        assertTrue("Reviewer has synthetic wallets", reviewerWallets.isNotEmpty())
        assertTrue("All reviewer wallets belong to reviewerId", reviewerWallets.all { it.userId == reviewerId })

        // Lukas account is permanently neutralized in Phase 1G
        val lukasWallets = db.walletDao().getAllWallets(lukasId).first()
        assertEquals("Lukas has no wallets", 0, lukasWallets.size)

        // Reviewer Transactions
        val reviewerTransactions = db.transactionDao().getAllTransactions(reviewerId).first()
        assertTrue("Reviewer has synthetic transactions", reviewerTransactions.isNotEmpty())
        assertTrue("All reviewer transactions belong to reviewerId", reviewerTransactions.all { it.userId == reviewerId })

        val lukasTransactions = db.transactionDao().getAllTransactions(lukasId).first()
        assertEquals("Lukas has no transactions by default", 0, lukasTransactions.size)

        // Reviewer Debts
        val reviewerDebts = db.debtDao().getAllDebts(reviewerId).first()
        assertTrue("Reviewer has synthetic debts", reviewerDebts.isNotEmpty())
        assertTrue("All reviewer debts belong to reviewerId", reviewerDebts.all { it.userId == reviewerId })

        // Reviewer Assets
        val reviewerAssets = db.assetDao().getAllAssets(reviewerId).first()
        assertTrue("Reviewer has synthetic assets", reviewerAssets.isNotEmpty())
        assertTrue("All reviewer assets belong to reviewerId", reviewerAssets.all { it.userId == reviewerId })

        // Reviewer Budgets
        val reviewerBudgets = db.budgetLimitDao().getAllBudgetLimits(reviewerId).first()
        assertTrue("Reviewer has synthetic budget limits", reviewerBudgets.isNotEmpty())
        assertTrue("All reviewer budgets belong to reviewerId", reviewerBudgets.all { it.userId == reviewerId })
    }

    @Test
    fun testSessionLoginAndLogoutFlow() = runBlocking {
        FinSmartDatabase.ensureReviewerAccount(db)
        val reviewer = db.userDao().getUserByIdentifier(ReviewerAccountConfig.REVIEWER_EMAIL)!!

        // Login as Reviewer
        SessionManager.login(reviewer)
        assertEquals("Active user ID is reviewer", reviewer.id, SessionManager.getActiveUserId())
        val activeUser = SessionManager.currentUser.first()
        assertNotNull("Active user is not null", activeUser)
        assertEquals("Active username is Reviewer", ReviewerAccountConfig.REVIEWER_USERNAME, activeUser!!.username)
        assertEquals("Active role is SMART_MASTER", UserRole.SMART_MASTER, activeUser.role)

        // Logout
        SessionManager.logout()
        assertNull("After logout, active user ID is null", SessionManager.getActiveUserId())
        assertNull("After logout, current user is null", SessionManager.currentUser.first())

        // Re-login
        SessionManager.login(reviewer)
        assertEquals("Re-login restores reviewer session", reviewer.id, SessionManager.getActiveUserId())
    }

    @Test
    fun testReservedIdentifierHelper() {
        assertTrue(ReviewerAccountConfig.isReviewerIdentifier("GoogleReviewer"))
        assertTrue(ReviewerAccountConfig.isReviewerIdentifier("googlereviewer"))
        assertTrue(ReviewerAccountConfig.isReviewerIdentifier("  GoogleReviewer  "))
        assertTrue(ReviewerAccountConfig.isReviewerIdentifier("reviewer@finsmart.app"))
        assertTrue(ReviewerAccountConfig.isReviewerIdentifier("REVIEWER@FINSMART.APP"))
        assertFalse(ReviewerAccountConfig.isReviewerIdentifier("random_user"))
        assertFalse(ReviewerAccountConfig.isReviewerIdentifier("user@finsmart.app"))
    }
}
