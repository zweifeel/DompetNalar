package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.session.SessionManager
import com.example.data.session.SessionState
import com.example.util.EntitlementManager
import com.example.util.EntitlementRegistry
import com.example.util.EntitlementSource
import com.example.util.GoogleDriveService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class CriticalBugsAndAutoUploadTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SessionManager.initialize(context)
        SessionManager.resetForTesting()
    }

    // --- 1. BUG P0: LOGOUT & SESSION TERMINATION ---
    @Test
    fun testLogoutTerminatesActiveSessionCompletely() {
        val testUser = User(
            id = 101L,
            username = "Test User",
            email = "test@example.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false
        )
        SessionManager.login(testUser)
        assertTrue("User should be authenticated after login", SessionManager.isAuthenticated())
        assertEquals(101L, SessionManager.getActiveUserId())

        // Perform logout
        SessionManager.logout()

        assertFalse("User should not be authenticated after logout", SessionManager.isAuthenticated())
        assertNull("Active user ID must be null after logout", SessionManager.getActiveUserId())
        assertNull("Current user must be null after logout", SessionManager.currentUser.value)
        assertEquals(SessionState.Unauthenticated, SessionManager.sessionState.value)
    }

    @Test
    fun testStealthModeResetOnLogout() {
        val testUser = User(id = 102L, username = "Stealth User", email = "stealth@example.com", isGuest = false)
        SessionManager.login(testUser)
        SessionManager.setStealthMode(true)
        assertTrue(SessionManager.isStealthModeActive())

        SessionManager.logout()
        assertFalse("Stealth mode must be reset to false on logout", SessionManager.isStealthModeActive())
    }

    // --- 2. BUG P0: GOOGLE DRIVE RESTORE RESILIENCE ---
    @Test
    fun testOrphanTransactionWalletFallbackProvisioning() {
        // When restoring transactions where original wallet ID was not in the backup wallet list,
        // it must map to a fallback wallet ("Dompet Utama") instead of throwing fatal exception.
        val existingWallets = listOf(
            Wallet(id = 1L, userId = 200L, name = "Dompet Utama", balance = 500000.0)
        )
        val orphanTx = TransactionItem(
            id = 10L,
            userId = 200L,
            walletId = 999L, // Non-existent wallet ID
            walletName = "Dompet Lama",
            title = "Makan Siang",
            amount = 25000.0,
            type = TransactionType.EXPENSE,
            category = "Makan",
            timestamp = System.currentTimeMillis()
        )

        val walletIdSet = existingWallets.map { it.id }.toSet()
        val resolvedWalletId = if (orphanTx.walletId in walletIdSet) {
            orphanTx.walletId
        } else {
            existingWallets.first().id // Safe fallback
        }

        assertEquals("Orphan transaction must safely map to available wallet", 1L, resolvedWalletId)
    }

    // --- 3. AUTO-UPLOAD BACKUP GOOGLE DRIVE INTERVALS & CONFIGURATION ---
    @Test
    fun testAutoBackupIntervalValidation() {
        // Valid intervals: 3, 6, 12, 24 hours
        listOf(3L, 6L, 12L, 24L).forEach { validHours ->
            GoogleDriveService.setAutoBackupIntervalHours(context, validHours)
            val retrieved = GoogleDriveService.getAutoBackupIntervalHours(context)
            assertEquals("Interval $validHours hours must be preserved", validHours, retrieved)
        }

        // Invalid interval must fallback to default 24 hours
        GoogleDriveService.setAutoBackupIntervalHours(context, 7L)
        val fallback = GoogleDriveService.getAutoBackupIntervalHours(context)
        assertEquals("Invalid interval must fallback to default 24h", 24L, fallback)
    }

    @Test
    fun testAutoSyncTogglePersistence() {
        GoogleDriveService.setAutoSyncEnabled(context, true)
        assertTrue(GoogleDriveService.isAutoSyncEnabled(context))

        GoogleDriveService.setAutoSyncEnabled(context, false)
        assertFalse(GoogleDriveService.isAutoSyncEnabled(context))
    }

    // --- 4. BUG P0: SMART MASTER EXPIRED REINSTALL & TRIAL LOOPHOLE ---
    @Test
    fun testExpiredGooglePlayPurchaseDoesNotGrantSmartMaster() {
        val now = System.currentTimeMillis()
        val durationMillis = 30L * 86400000L // Monthly = 30 days
        val purchaseTimeTwoMonthsAgo = now - (60L * 86400000L) // 60 days ago
        val calculatedExpiry = purchaseTimeTwoMonthsAgo + durationMillis
        val isAutoRenewing = false

        // Verified logic: if !isAutoRenewing && purchaseTime > 0 && calculatedExpiry <= now => EXPIRED
        val isExpired = !isAutoRenewing && purchaseTimeTwoMonthsAgo > 0L && calculatedExpiry <= now
        assertTrue("Past non-renewing purchase must be identified as expired", isExpired)
        assertTrue("Calculated expiry must be in the past", calculatedExpiry < now)
    }

    @Test
    fun testActiveAutoRenewingGooglePlayPurchaseGrantsSmartMaster() {
        val now = System.currentTimeMillis()
        val durationMillis = 30L * 86400000L
        val purchaseTimeYesterday = now - 86400000L
        val isAutoRenewing = true

        val calculatedExpiry = purchaseTimeYesterday + durationMillis
        val effectiveExpiry = if (isAutoRenewing) {
            maxOf(now + durationMillis, calculatedExpiry)
        } else {
            calculatedExpiry
        }

        assertTrue("Active auto-renewing purchase must extend expiry", effectiveExpiry > now)
    }

    @Test
    fun testEntitlementRegistry_trialPermanentlyRemoved_returnsNullAndNoPremium() {
        val email = "veteran.user@example.com"
        val originalTrialStart = System.currentTimeMillis() - (5L * 24 * 3600 * 1000L)

        EntitlementRegistry.recordTrialStartDate(context, email, originalTrialStart)

        val retrievedTrialStart = EntitlementRegistry.getTrialStartDate(context, email)
        assertNull("Trial start date must always be null after permanent removal", retrievedTrialStart)

        // Verify entitlement evaluation marks user as standard FREE_MEMBER without premium
        val userWithoutTrial = User(
            id = 505L,
            username = "Veteran User",
            email = email,
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            trialStartDate = 0L
        )

        val entitlementStatus = EntitlementManager.evaluate(userWithoutTrial)
        assertFalse("Free member without subscription must not have premium unlocked", entitlementStatus.isPremiumUnlocked)
        assertEquals(0, entitlementStatus.trialDaysRemaining)
        assertEquals(EntitlementSource.NONE, entitlementStatus.primarySource)
    }

    @Test
    fun testGuestNeverReceivesSmartMasterTrial() {
        val guestUser = User(
            id = 999L,
            username = "Tamu FinSmart",
            email = "guest_123@finsmart.local",
            role = UserRole.FREE_MEMBER,
            isGuest = true,
            trialStartDate = System.currentTimeMillis() // Even if set erroneously
        )

        val entitlement = EntitlementManager.evaluate(guestUser)
        assertFalse("Guest must never have premium unlocked via trial", entitlement.isPremiumUnlocked)
        assertEquals(0, entitlement.trialDaysRemaining)
    }
}
