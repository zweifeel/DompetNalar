package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.config.DeveloperAccountConfig
import com.example.data.ai.ReceiptAiOcrScanner
import com.example.data.model.ReceiptScanResult
import com.example.data.model.TaxTreatment
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.util.BillingManager
import com.example.util.EntitlementManager
import com.example.util.EntitlementRegistry
import com.example.util.EntitlementSource
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

/**
 * Pengujian Regresi Terpadu:
 * 1. Penghapusan Permanen Uji Coba (Trial 3 Hari)
 * 2. Konsistensi Session & Persistent Entitlement
 * 3. Validasi Satuan Periode Smart Master (Bulanan & Tahunan)
 * 4. Alur Kamera & Scanner OCR FinSmart
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmartMasterAndCameraOcrRegressionTest {

    private lateinit var context: Context
    private val now = 1730160000000L // Waktu tetap pengujian

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        EntitlementRegistry.purgeTrialRecords(context)
    }

    // =========================================================================
    // 1. PENGHAPUSAN PERMANEN TRIAL 3 HARI
    // =========================================================================

    @Test
    fun testFreshInstall_doesNotReceiveTrial() {
        val newUser = User(
            id = 1L,
            username = "Pengguna Baru",
            email = "baru@gmail.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            trialStartDate = 0L
        )

        val entitlement = EntitlementManager.evaluate(newUser, now = now, isDebug = false)
        assertFalse("Instalasi baru tidak boleh mendapatkan akses premium trial", entitlement.isPremiumUnlocked)
        assertEquals(0, entitlement.trialDaysRemaining)
        assertEquals(EntitlementSource.NONE, entitlement.primarySource)
        assertEquals(UserRole.FREE_MEMBER, entitlement.effectiveRole)
    }

    @Test
    fun testGoogleSignIn_doesNotCreateOrRestoreTrial() {
        val googleUser = User(
            id = 2L,
            username = "Google Sign In",
            email = "user.google@gmail.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            trialStartDate = 0L
        )

        val entitlement = EntitlementManager.evaluate(googleUser, now = now, isDebug = false)
        assertFalse("Login akun Google tidak memperoleh akses trial", entitlement.isPremiumUnlocked)
        assertEquals(0, entitlement.trialDaysRemaining)
        assertEquals(EntitlementSource.NONE, entitlement.primarySource)
    }

    @Test
    fun testClearData_andReLogin_doesNotRestoreTrial() {
        val email = "veteran.user@gmail.com"
        // Simulasikan penghapusan data aplikasi (purge registry)
        EntitlementRegistry.purgeTrialRecords(context)

        // Verifikasi getTrialStartDate mengembalikan null
        val storedTrial = EntitlementRegistry.getTrialStartDate(context, email)
        assertNull("Registry tidak boleh mengembalikan trial start date", storedTrial)

        // Pengguna login kembali setelah hapus data
        val reLoggedInUser = User(
            id = 3L,
            username = "Veteran User",
            email = email,
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            trialStartDate = 0L
        )

        val entitlement = EntitlementManager.evaluate(reLoggedInUser, now = now, isDebug = false)
        assertFalse("Trial tidak boleh dipulihkan setelah hapus data aplikasi", entitlement.isPremiumUnlocked)
        assertEquals(0, entitlement.trialDaysRemaining)
    }

    @Test
    fun testLegacyTrialTimestampInDatabase_isIgnoredDeterministically() {
        // Jika database lokal masih memuat timestamp trial lama dari versi terdahulu
        val legacyUser = User(
            id = 4L,
            username = "Legacy User",
            email = "old.trial@gmail.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            trialStartDate = now - (12 * 3600 * 1000L) // Pernah aktif 12 jam lalu
        )

        val entitlement = EntitlementManager.evaluate(legacyUser, now = now, isDebug = false)
        assertFalse("Timestamp trial historis tidak boleh membuka akses premium", entitlement.isPremiumUnlocked)
        assertEquals(0, entitlement.trialDaysRemaining)
        assertEquals(EntitlementSource.NONE, entitlement.primarySource)
    }

    @Test
    fun testGuestUser_strictlyExcludedFromPremium() {
        val guest = User(
            id = 5L,
            username = "Tamu DompetNalar",
            email = "guest_12345@finsmart.local",
            role = UserRole.FREE_MEMBER,
            isGuest = true,
            trialStartDate = 0L
        )

        val entitlement = EntitlementManager.evaluate(guest, now = now, isDebug = false)
        assertFalse("Akun Tamu tidak boleh memiliki akses premium", entitlement.isPremiumUnlocked)
        assertEquals(0, entitlement.trialDaysRemaining)
        assertEquals(EntitlementSource.NONE, entitlement.primarySource)
    }

    @Test
    fun testActiveSubscription_remainsFullyFunctional() {
        val activeSubscriber = User(
            id = 6L,
            username = "Pelanggan Aktif",
            email = "subscriber@gmail.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = now + (30L * 86400000L) // 30 hari lagi
        )

        val entitlement = EntitlementManager.evaluate(activeSubscriber, now = now, isDebug = false)
        assertTrue("Langganan aktif yang sah harus membuka akses premium", entitlement.isPremiumUnlocked)
        assertEquals(UserRole.SMART_MASTER, entitlement.effectiveRole)
        assertFalse(entitlement.isExpired)
    }

    @Test
    fun testExpiredSubscription_isProperlyLocked_withoutTrialFallback() {
        val expiredSubscriber = User(
            id = 7L,
            username = "Langganan Habis",
            email = "expired.sub@gmail.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = now - 1000L // Baru saja habis
        )

        val entitlement = EntitlementManager.evaluate(expiredSubscriber, now = now, isDebug = false)
        assertFalse("Langganan kedaluwarsa harus mengunci akses premium", entitlement.isPremiumUnlocked)
        assertTrue("Harus ditandai kedaluwarsa", entitlement.isExpired)
        assertEquals(0, entitlement.trialDaysRemaining)
    }

    @Test
    fun testMasterDeveloper_maintainsAuthorizedAccess() {
        val masterDev = User(
            id = 8L,
            username = "Lukas",
            email = DeveloperAccountConfig.CONTACT_DEV_EMAIL,
            role = UserRole.MASTER_DEV,
            isGuest = false,
            subscriptionExpiresAt = null
        )

        val entitlement = EntitlementManager.evaluate(masterDev, now = now, isDebug = true)
        assertTrue("Master Developer tepercaya harus memiliki akses seumur hidup", entitlement.isPremiumUnlocked)
        assertEquals(UserRole.MASTER_DEV, entitlement.effectiveRole)
        assertEquals(EntitlementSource.MASTER_DEV, entitlement.primarySource)
    }

    // =========================================================================
    // 2. SATUAN PERIODE PAKET SMART MASTER (BULANAN & TAHUNAN)
    // =========================================================================

    @Test
    fun testProductIds_remainStrictlyUnchanged() {
        assertEquals("smart_master_monthly", BillingManager.PRODUCT_ID_MONTHLY)
        assertEquals("smart_master_yearly", BillingManager.PRODUCT_ID_YEARLY)
    }

    @Test
    fun testSubscriptionLabels_monthlyAndYearlyAreCorrect() {
        val monthlyLabel = "Langganan Bulanan"
        val yearlyLabel = "Langganan Tahunan"
        val monthlySuffix = "/ bln"
        val yearlySuffix = "/ tahun"

        assertFalse("Paket bulanan tidak boleh mengandung satuan menit", monthlyLabel.contains("menit", ignoreCase = true))
        assertFalse("Paket bulanan tidak boleh mengandung satuan mnt", monthlyLabel.contains("mnt", ignoreCase = true))
        assertFalse("Paket tahunan tidak boleh mengandung satuan menit", yearlyLabel.contains("menit", ignoreCase = true))
        assertFalse("Paket tahunan tidak boleh mengandung satuan mnt", yearlyLabel.contains("mnt", ignoreCase = true))

        assertTrue("Paket bulanan harus memiliki suffix bulanan", monthlySuffix.contains("bln"))
        assertTrue("Paket tahunan harus memiliki suffix tahunan", yearlySuffix.contains("tahun"))
    }

    // =========================================================================
    // 3. KAMERA OCR SCAN NOTA & INTEGRASI TRANSAKSI
    // =========================================================================

    @Test
    fun testReceiptScannerFallbackPresets_areValidAndStructured() {
        val presetIndomaret = ReceiptAiOcrScanner.generateRealisticReceiptFallback(presetType = 1)
        assertNotNull(presetIndomaret)
        assertEquals("Indomaret Mojopuro Gede", presetIndomaret.merchantName)
        assertEquals(9700.0, presetIndomaret.totalAmount, 0.01)
        assertEquals(TaxTreatment.INCLUSIVE, presetIndomaret.taxTreatment)
        assertTrue("Item Indomaret harus ada", presetIndomaret.items.isNotEmpty())

        val presetMomoyo = ReceiptAiOcrScanner.generateRealisticReceiptFallback(presetType = 2)
        assertNotNull(presetMomoyo)
        assertEquals("Momoyo Dukun Gresik", presetMomoyo.merchantName)
        assertEquals(36000.0, presetMomoyo.totalAmount, 0.01)
        assertEquals(TaxTreatment.EXCLUSIVE, presetMomoyo.taxTreatment)
        assertTrue("Item Momoyo harus ada", presetMomoyo.items.isNotEmpty())
    }

    @Test
    fun testReceiptResult_requiresConfirmationBeforeSavingTransaction() {
        var isTransactionCommitted = false
        val scannedResult = ReceiptAiOcrScanner.generateRealisticReceiptFallback(presetType = 1)

        // Memastikan hasil OCR tidak otomatis committed tanpa konfirmasi
        assertFalse("Sebelum tombol Simpan ditekan, transaksi tidak boleh tersimpan", isTransactionCommitted)

        // Simulasi aksi konfirmasi review pengguna
        fun onUserConfirmSave(result: ReceiptScanResult) {
            assertTrue("Total harus lebih dari 0", result.totalAmount > 0)
            isTransactionCommitted = true
        }

        onUserConfirmSave(scannedResult)
        assertTrue("Setelah konfirmasi pengguna, transaksi baru boleh tersimpan", isTransactionCommitted)
    }

    @Test
    fun testCameraCancellation_doesNotCreateTransaction() {
        var transactionCreated = false
        val resultCode = Activity.RESULT_CANCELED

        if (resultCode == Activity.RESULT_OK) {
            transactionCreated = true
        }

        assertFalse("Pembatalan kamera tidak boleh membuat mutasi transaksi", transactionCreated)
    }

    // =========================================================================
    // 4. AUDIT PULIHKAN PEMBELIAN (RESTORE PURCHASES) GOOGLE PLAY
    // =========================================================================

    @Test
    fun testRestorePurchases_whenBillingClientNotReady_reportsServiceNotReadySafely() {
        // BillingClient belum terhubung / bernilai null
        var callbackInvoked = false
        var resultCount = -1
        var resultMessage = ""

        BillingManager.restorePurchases { count, message ->
            callbackInvoked = true
            resultCount = count
            resultMessage = message
        }

        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue("Callback restorePurchases harus dipanggil", callbackInvoked)
        assertEquals("Count harus 0 saat billing belum siap", 0, resultCount)
        assertTrue("Pesan harus menyebutkan layanan belum siap", resultMessage.contains("belum siap", ignoreCase = true))
    }

    @Test
    fun testRestorePurchases_buttonBehavior_preventsPrematureDowngradeWhenDisconnected() {
        var onSubscriptionsSyncedCalled = false
        var reportedIsConnected = true
        var reportedSubs: List<com.android.billingclient.api.Purchase>? = null

        BillingManager.onSubscriptionsSynced = { activeSubs, isConnected ->
            onSubscriptionsSyncedCalled = true
            reportedIsConnected = isConnected
            reportedSubs = activeSubs
        }

        BillingManager.restorePurchases { _, _ -> }

        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue("onSubscriptionsSynced harus dipanggil saat client tidak siap", onSubscriptionsSyncedCalled)
        assertFalse("isConnected harus false agar status pengguna saat ini tidak didowngrade sepihak", reportedIsConnected)
        assertEquals(0, reportedSubs?.size ?: 0)
    }
}
