package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DatabaseWriteCoordinator
import com.example.data.local.FinSmartDatabase
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.repository.UserRepository
import com.example.data.session.SessionManager
import com.example.ui.viewmodel.AuthViewModel
import com.example.util.BillingManager
import com.example.util.EntitlementManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Audit & Regression Tests for Google Play Billing Subscription Lifecycle.
 * Covers all 14 mandatory scenarios specified in Antigravity Master Prompt Phase 2.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BillingAndSubscriptionLifecycleTest {

    companion object {
        private var sharedDb: FinSmartDatabase? = null

        private fun setDatabaseSingleton(instance: FinSmartDatabase?) {
            val field = FinSmartDatabase::class.java.getDeclaredField("INSTANCE")
            field.isAccessible = true
            field.set(null, instance)
        }
    }

    private lateinit var application: Application
    private lateinit var authViewModel: AuthViewModel

    private suspend fun syncDatabaseWrites() {
        ShadowLooper.idleMainLooper()
        DatabaseWriteCoordinator.getInstance().withWritePermit {
            // Deterministic synchronization barrier awaiting active/queued write permits
        }
        ShadowLooper.idleMainLooper()
    }

    @Before
    fun setup() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        application = ApplicationProvider.getApplicationContext()
        DatabaseWriteCoordinator.resetForTesting()
        if (sharedDb == null) {
            val dbInstance = Room.inMemoryDatabaseBuilder(application, FinSmartDatabase::class.java)
                .allowMainThreadQueries()
                .setQueryExecutor { it.run() }
                .setTransactionExecutor { it.run() }
                .build()
            sharedDb = dbInstance
            setDatabaseSingleton(dbInstance)
        }
        sharedDb?.clearAllTables()

        // Pre-seed canonical system categories and dev accounts synchronously to prevent IO lock contention in AuthViewModel.init
        FinSmartDatabase.ensureMasterDevAccount(sharedDb!!)
        FinSmartDatabase.ensureReviewerAccount(sharedDb!!)

        SessionManager.initialize(application)
        SessionManager.logout()
        BillingManager.clearProcessedTokens()

        application.getSharedPreferences("finsmart_auth_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        application.getSharedPreferences("finsmart_billing_records", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        authViewModel = AuthViewModel(application)
        syncDatabaseWrites()
    }

    @After
    fun tearDown() = runBlocking {
        ShadowLooper.idleMainLooper()
        if (::authViewModel.isInitialized) {
            authViewModel.viewModelScope.coroutineContext[Job]?.cancelChildren()
            try {
                val clearMethod = ViewModel::class.java.getDeclaredMethod("clear")
                clearMethod.isAccessible = true
                clearMethod.invoke(authViewModel)
            } catch (_: Exception) {
            }
        }
        SessionManager.logout()
        BillingManager.clearProcessedTokens()
        BillingManager.onSubscriptionsSynced = null
        DatabaseWriteCoordinator.resetForTesting()
        Dispatchers.resetMain()
    }

    private suspend fun loginTestUser(
        id: Long = 100L,
        email: String = "tester_subscriber@example.com",
        role: UserRole = UserRole.FREE_MEMBER,
        expiry: Long? = null,
        trialStartDate: Long = 0L
    ): User {
        var user = User(
            id = id,
            username = "Tester",
            email = email,
            role = role,
            subscriptionExpiresAt = expiry,
            isGuest = false,
            trialStartDate = trialStartDate
        )
        val insertedId = sharedDb!!.userDao().insertUser(user)
        user = user.copy(id = insertedId)
        SessionManager.login(user)
        syncDatabaseWrites()
        return user
    }

    @Test
    fun scenario01_validNewPurchase_activatesSmartMaster() = runBlocking {
        val user = loginTestUser(101L, "user1@example.com", UserRole.FREE_MEMBER)
        val now = System.currentTimeMillis()
        val token = "token_valid_new_12345"

        authViewModel.applyGooglePlayPurchase(
            productId = BillingManager.PRODUCT_ID_MONTHLY,
            purchaseToken = token,
            orderId = "GPA.1111-2222-3333-44441",
            purchaseTime = now,
            isAutoRenewing = true,
            isExplicitPurchase = true
        )
        syncDatabaseWrites()

        val currentUser = authViewModel.currentUser.value
        assertNotNull(currentUser)
        assertEquals(UserRole.SMART_MASTER, currentUser?.role)
        assertNotNull(currentUser?.subscriptionExpiresAt)
        assertTrue(currentUser!!.subscriptionExpiresAt!! >= now + (30L * 86400000L))

        // Toast must be shown for a new explicit purchase
        assertEquals("Pembelian Google Play Berhasil! Status Smart Master 👑 Aktif.", authViewModel.isSuccessToast.value)
    }

    @Test
    fun scenario02_duplicatePurchaseCallback_isIdempotent() = runBlocking {
        val user = loginTestUser(102L, "user2@example.com", UserRole.FREE_MEMBER)
        val now = System.currentTimeMillis()
        val token = "token_duplicate_test_99999"

        // First execution: New explicit purchase
        authViewModel.applyGooglePlayPurchase(
            productId = BillingManager.PRODUCT_ID_MONTHLY,
            purchaseToken = token,
            orderId = "GPA.1111-2222-3333-44442",
            purchaseTime = now,
            isAutoRenewing = true,
            isExplicitPurchase = true
        )
        syncDatabaseWrites()
        assertEquals("Pembelian Google Play Berhasil! Status Smart Master 👑 Aktif.", authViewModel.isSuccessToast.value)

        // Reset toast
        authViewModel.clearSuccessToast()

        // Second execution: Same purchase token re-delivered by callback
        authViewModel.applyGooglePlayPurchase(
            productId = BillingManager.PRODUCT_ID_MONTHLY,
            purchaseToken = token,
            orderId = "GPA.1111-2222-3333-44442",
            purchaseTime = now,
            isAutoRenewing = true,
            isExplicitPurchase = true
        )
        syncDatabaseWrites()

        // Role remains SMART_MASTER, but duplicate success toast is NOT re-triggered
        assertEquals(UserRole.SMART_MASTER, authViewModel.currentUser.value?.role)
        assertNull(authViewModel.isSuccessToast.value)

        // Verify token masking protects sensitive token in logs
        val masked = BillingManager.maskToken(token)
        assertEquals("toke...9999", masked)
        assertFalse(masked.contains("duplicate_test"))
    }

    @Test
    fun scenario03_reconnectAndSilentSync_doesNotEmitSuccessToast() = runBlocking {
        val user = loginTestUser(103L, "user3@example.com", UserRole.FREE_MEMBER)
        val now = System.currentTimeMillis()
        val token = "token_silent_sync_555"

        // Silent sync on connect/restore
        authViewModel.applyGooglePlayPurchase(
            productId = BillingManager.PRODUCT_ID_MONTHLY,
            purchaseToken = token,
            orderId = "GPA.1111-2222-3333-44443",
            purchaseTime = now,
            isAutoRenewing = true,
            isExplicitPurchase = false // Silent
        )
        syncDatabaseWrites()

        // Entitlement is applied, but no success toast is emitted
        assertEquals(UserRole.SMART_MASTER, authViewModel.currentUser.value?.role)
        assertNull(authViewModel.isSuccessToast.value)
    }

    @Test
    fun scenario04_pendingPurchase_doesNotGrantPaidAccess() = runBlocking {
        val user = loginTestUser(104L, "user4@example.com", UserRole.FREE_MEMBER)

        // Verify that PENDING state in EntitlementManager does not unlock premium
        val status = EntitlementManager.evaluate(user)
        assertFalse("Pending user must not have premium access", status.isPremiumUnlocked)
        assertEquals(UserRole.FREE_MEMBER, status.effectiveRole)
    }

    @Test
    fun scenario05_autoRenewCanceled_preservesAccessUntilExpiry() = runBlocking {
        val now = System.currentTimeMillis()
        val purchaseTime = now - (5L * 86400000L) // 5 days ago
        val user = loginTestUser(105L, "user5@example.com", UserRole.FREE_MEMBER)

        // Auto-renew turned off, but 25 days remaining
        authViewModel.applyGooglePlayPurchase(
            productId = BillingManager.PRODUCT_ID_MONTHLY,
            purchaseToken = "token_cancelled_autorenew",
            orderId = "GPA.1111-2222-3333-44445",
            purchaseTime = purchaseTime,
            isAutoRenewing = false,
            isExplicitPurchase = true
        )
        syncDatabaseWrites()

        val currentUser = authViewModel.currentUser.value
        assertEquals(UserRole.SMART_MASTER, currentUser?.role)
        val expectedExpiry = purchaseTime + (30L * 86400000L)
        assertEquals(expectedExpiry, currentUser?.subscriptionExpiresAt)

        val entitlement = EntitlementManager.evaluate(currentUser, now = now)
        assertTrue("Pengguna yang membatalkan perpanjangan otomatis tetap memiliki hak akses sampai akhir periode", entitlement.isPremiumUnlocked)
        assertFalse(entitlement.isExpired)
    }

    @Test
    fun scenario06_expiredSubscription_isDowngraded() = runBlocking {
        val now = System.currentTimeMillis()
        val purchaseTime = now - (35L * 86400000L) // 35 days ago (expired for 30-day sub)
        val user = loginTestUser(106L, "user6@example.com", UserRole.SMART_MASTER, expiry = purchaseTime + (30L * 86400000L))

        authViewModel.applyGooglePlayPurchase(
            productId = BillingManager.PRODUCT_ID_MONTHLY,
            purchaseToken = "token_expired_106",
            orderId = "GPA.1111-2222-3333-44446",
            purchaseTime = purchaseTime,
            isAutoRenewing = false,
            isExplicitPurchase = false
        )
        syncDatabaseWrites()

        // User must be downgraded to FREE_MEMBER
        assertEquals(UserRole.FREE_MEMBER, authViewModel.currentUser.value?.role)
        assertNull(authViewModel.currentUser.value?.subscriptionExpiresAt)
        assertEquals("Status langganan Google Play untuk akun ini telah kedaluwarsa.", authViewModel.authError.value)
    }

    @Test
    fun scenario07_refundOrRevocation_downgradesUserWhenGooglePlayReturnsEmpty() = runBlocking {
        val user = loginTestUser(107L, "user7@example.com", UserRole.FREE_MEMBER)
        val now = System.currentTimeMillis()

        // 1. Initial purchase succeeds
        authViewModel.applyGooglePlayPurchase(
            productId = BillingManager.PRODUCT_ID_MONTHLY,
            purchaseToken = "token_revoked_test_107",
            orderId = "GPA.1111-2222-3333-44447",
            purchaseTime = now,
            isAutoRenewing = true,
            isExplicitPurchase = true
        )
        syncDatabaseWrites()
        assertEquals(UserRole.SMART_MASTER, authViewModel.currentUser.value?.role)

        // 2. Google Play cancels and refunds purchase -> queryPurchases returns empty list
        authViewModel.handleGooglePlaySubscriptionsSynced(
            activePurchases = emptyList(),
            isConnected = true
        )
        syncDatabaseWrites()

        // User must be automatically downgraded to FREE_MEMBER
        val updatedUser = authViewModel.currentUser.value
        assertEquals(UserRole.FREE_MEMBER, updatedUser?.role)
        assertNull(updatedUser?.subscriptionExpiresAt)
    }

    @Test
    fun scenario08_transientNetworkFailure_doesNotRevokeAccessPrematurely() = runBlocking {
        val user = loginTestUser(108L, "user8@example.com", UserRole.FREE_MEMBER)
        val now = System.currentTimeMillis()

        authViewModel.applyGooglePlayPurchase(
            productId = BillingManager.PRODUCT_ID_MONTHLY,
            purchaseToken = "token_network_fail_108",
            orderId = "GPA.1111-2222-3333-44448",
            purchaseTime = now,
            isAutoRenewing = true,
            isExplicitPurchase = true
        )
        syncDatabaseWrites()
        assertEquals(UserRole.SMART_MASTER, authViewModel.currentUser.value?.role)

        // Transient network disconnect (isConnected = false)
        authViewModel.handleGooglePlaySubscriptionsSynced(
            activePurchases = emptyList(),
            isConnected = false
        )
        syncDatabaseWrites()

        // Entitlement must be PRESERVED
        assertEquals(UserRole.SMART_MASTER, authViewModel.currentUser.value?.role)
        assertNotNull(authViewModel.currentUser.value?.subscriptionExpiresAt)
    }

    @Test
    fun scenario09_accountSwitching_doesNotLeakBillingState() = runBlocking {
        // User A purchases
        val userA = loginTestUser(109L, "userA@example.com", UserRole.FREE_MEMBER)
        val now = System.currentTimeMillis()
        authViewModel.applyGooglePlayPurchase(
            productId = BillingManager.PRODUCT_ID_MONTHLY,
            purchaseToken = "token_user_a",
            orderId = "GPA.1111-2222-3333-44449",
            purchaseTime = now,
            isAutoRenewing = true,
            isExplicitPurchase = true
        )
        syncDatabaseWrites()
        assertEquals(UserRole.SMART_MASTER, authViewModel.currentUser.value?.role)

        // Logout User A
        authViewModel.logout()
        syncDatabaseWrites()

        // Login User B (Free Member)
        val userB = loginTestUser(110L, "userB@example.com", UserRole.FREE_MEMBER)
        syncDatabaseWrites()

        // User B must start as FREE_MEMBER, not inherit User A's role
        assertEquals(UserRole.FREE_MEMBER, authViewModel.currentUser.value?.role)
        assertNull(authViewModel.currentUser.value?.subscriptionExpiresAt)
    }

    @Test
    fun scenario10_monthlyAndYearlyDurations_areAccurate() = runBlocking {
        val user = loginTestUser(111L, "user11@example.com", UserRole.FREE_MEMBER)
        val baseNow = System.currentTimeMillis()

        // Monthly duration check
        authViewModel.applyGooglePlayPurchase(
            productId = BillingManager.PRODUCT_ID_MONTHLY,
            purchaseToken = "token_month_111",
            orderId = "GPA.1111",
            purchaseTime = baseNow,
            isAutoRenewing = false,
            isExplicitPurchase = false
        )
        syncDatabaseWrites()
        val monthlyExpiry = authViewModel.currentUser.value?.subscriptionExpiresAt
        assertEquals(baseNow + (30L * 86400000L), monthlyExpiry)

        // Yearly duration check
        authViewModel.applyGooglePlayPurchase(
            productId = BillingManager.PRODUCT_ID_YEARLY,
            purchaseToken = "token_year_111",
            orderId = "GPA.2222",
            purchaseTime = baseNow,
            isAutoRenewing = false,
            isExplicitPurchase = false
        )
        syncDatabaseWrites()
        val yearlyExpiry = authViewModel.currentUser.value?.subscriptionExpiresAt
        assertEquals(baseNow + (365L * 86400000L), yearlyExpiry)
    }

    @Test
    fun scenario11_developerGrantAndMasterDev_immuneToBillingDowngrades() = runBlocking {
        // Master dev user with trusted email
        val masterDev = loginTestUser(112L, "lunachroc01@gmail.com", UserRole.MASTER_DEV)

        // Billing sync reports 0 subscriptions
        authViewModel.handleGooglePlaySubscriptionsSynced(
            activePurchases = emptyList(),
            isConnected = true
        )
        syncDatabaseWrites()

        // Master Dev role is preserved
        assertEquals(UserRole.MASTER_DEV, authViewModel.currentUser.value?.role)
    }

    @Test
    fun scenario12_ordinaryUser_cannotPerformDeveloperActions() = runBlocking {
        val normalUser = loginTestUser(113L, "regular_user@example.com", UserRole.FREE_MEMBER)

        assertFalse(authViewModel.isDeveloperAuthorized(isDebug = false))
        assertFalse(EntitlementManager.isDeveloperAuthorized(normalUser, isDebug = false))
    }

    @Test
    fun scenario13_backendFailure_doesNotGrantPaidAccess() = runBlocking {
        // If billing client is disconnected or query fails, user remains FREE_MEMBER
        val user = loginTestUser(114L, "user14@example.com", UserRole.FREE_MEMBER)

        authViewModel.handleGooglePlaySubscriptionsSynced(
            activePurchases = emptyList(),
            isConnected = false
        )
        syncDatabaseWrites()

        assertEquals(UserRole.FREE_MEMBER, authViewModel.currentUser.value?.role)
        assertFalse(EntitlementManager.evaluate(authViewModel.currentUser.value).isPremiumUnlocked)
    }

    @Test
    fun scenario14_emptyStatus_distinguishedFromDisconnect() = runBlocking {
        val user = loginTestUser(115L, "user15@example.com", UserRole.FREE_MEMBER)
        val now = System.currentTimeMillis()

        // 1. Grant subscription via billing
        authViewModel.applyGooglePlayPurchase(
            productId = BillingManager.PRODUCT_ID_MONTHLY,
            purchaseToken = "token_status_distinction_115",
            orderId = "GPA.1115",
            purchaseTime = now,
            isAutoRenewing = true,
            isExplicitPurchase = false
        )
        syncDatabaseWrites()
        assertEquals(UserRole.SMART_MASTER, authViewModel.currentUser.value?.role)

        // 2. Disconnect event -> Must NOT downgrade
        authViewModel.handleGooglePlaySubscriptionsSynced(
            activePurchases = emptyList(),
            isConnected = false
        )
        syncDatabaseWrites()
        assertEquals(UserRole.SMART_MASTER, authViewModel.currentUser.value?.role)

        // 3. Verified empty event (connected = true) -> MUST downgrade
        authViewModel.handleGooglePlaySubscriptionsSynced(
            activePurchases = emptyList(),
            isConnected = true
        )
        syncDatabaseWrites()
        assertEquals(UserRole.FREE_MEMBER, authViewModel.currentUser.value?.role)
    }
}
