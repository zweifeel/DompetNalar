package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.AssetCategory
import com.example.data.model.AssetItem
import com.example.data.model.DebtItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.data.session.SessionManager
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.AutoSyncWorker
import com.example.util.GoogleDriveService
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
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
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StealthModeDataLeakageRemediationTest {

    private lateinit var app: Application
    private lateinit var db: FinSmartDatabase
    private lateinit var authViewModel: AuthViewModel
    private lateinit var financeViewModel: FinanceViewModel

    private val testUserId = 777L

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()

        setDatabaseSingleton(db)

        SessionManager.initialize(app)
        val testUser = User(
            id = testUserId,
            username = "Stealth Tester",
            email = "tester@finsmart.local",
            role = UserRole.FREE_MEMBER
        )
        db.userDao().insertUser(testUser)
        SessionManager.login(testUser)

        authViewModel = AuthViewModel(app)
        financeViewModel = FinanceViewModel(app)

        SessionManager.setStealthMode(false)
        GoogleDriveService.disconnect(app)
    }

    @After
    fun tearDown() {
        try {
            if (::financeViewModel.isInitialized) {
                financeViewModel.viewModelScope.cancel()
            }
            ShadowLooper.idleMainLooper()
            SessionManager.setStealthMode(false)
            SessionManager.logout()
            setDatabaseSingleton(null)
            if (::db.isInitialized) {
                db.close()
            }
            GoogleDriveService.disconnect(app)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun setDatabaseSingleton(instance: FinSmartDatabase?) {
        val field = FinSmartDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, instance)
    }

    @Test
    fun test1_backupRejectedWhenStealthModeActive() = runBlocking {
        SessionManager.setStealthMode(true)
        assertTrue(SessionManager.isStealthModeActive())

        // 1. Direct GoogleDriveService.backupNow
        val result = GoogleDriveService.backupNow(app, db, testUserId)
        assertFalse("Backup harus gagal saat stealth mode aktif", result.first)
        assertTrue("Pesan penolakan harus jelas", result.second.contains("Mode Stealth"))

        // 2. Direct GoogleDriveService.backupPortableNow
        val portableResult = GoogleDriveService.backupPortableNow(app, db, "Secret123!", testUserId)
        assertFalse("Backup portabel harus gagal saat stealth mode aktif", portableResult.first)
        assertTrue(portableResult.second.contains("Mode Stealth"))
        assertNull(portableResult.third)

        // 3. AuthViewModel triggerManualBackup
        authViewModel.triggerManualBackup()
        val toast1 = authViewModel.isSuccessToast.value
        assertNotNull(toast1)
        assertTrue(toast1!!.contains("Mode Stealth"))

        // 4. AuthViewModel triggerPortableBackup
        var portableCallbackSuccess: Boolean? = null
        var portableCallbackMsg: String? = null
        authViewModel.triggerPortableBackup("Secret123!") { success, msg, _ ->
            portableCallbackSuccess = success
            portableCallbackMsg = msg
        }
        assertEquals(false, portableCallbackSuccess)
        assertNotNull(portableCallbackMsg)
        assertTrue(portableCallbackMsg!!.contains("Mode Stealth"))
    }

    @Test
    fun test2_restoreRejectedWhenStealthModeActive() = runBlocking {
        SessionManager.setStealthMode(true)
        assertTrue(SessionManager.isStealthModeActive())

        // 1. Direct GoogleDriveService.restoreNow
        val result = GoogleDriveService.restoreNow(app, db, testUserId)
        assertFalse("Restore harus gagal saat stealth mode aktif", result.first)
        assertTrue(result.second.contains("Mode Stealth"))

        // 2. Direct GoogleDriveService.restorePortableNow
        val portableResult = GoogleDriveService.restorePortableNow(app, db, null, "Secret123!", testUserId)
        assertFalse("Restore portabel harus gagal saat stealth mode aktif", portableResult.first)
        assertTrue(portableResult.second.contains("Mode Stealth"))

        // 3. AuthViewModel triggerManualRestore
        authViewModel.triggerManualRestore()
        val toast2 = authViewModel.isSuccessToast.value
        assertNotNull(toast2)
        assertTrue(toast2!!.contains("Mode Stealth"))

        // 4. AuthViewModel triggerPortableRestore
        var portableRestoreCallbackSuccess: Boolean? = null
        var portableRestoreCallbackMsg: String? = null
        authViewModel.triggerPortableRestore(null, "Secret123!") { success, msg ->
            portableRestoreCallbackSuccess = success
            portableRestoreCallbackMsg = msg
        }
        assertEquals(false, portableRestoreCallbackSuccess)
        assertNotNull(portableRestoreCallbackMsg)
        assertTrue(portableRestoreCallbackMsg!!.contains("Mode Stealth"))
    }

    @Test
    fun test3_directServiceCallsAndBackgroundWorkerRejectedInStealthMode() = runBlocking {
        SessionManager.setStealthMode(true)

        // Calling GoogleDriveService directly without ViewModel
        val directBackup = GoogleDriveService.backupNow(app, db, testUserId)
        assertFalse(directBackup.first)

        val directRestore = GoogleDriveService.restoreNow(app, db, testUserId)
        assertFalse(directRestore.first)

        // AutoSyncWorker background worker execution
        val worker = TestListenableWorkerBuilder<AutoSyncWorker>(app).build()
        val workerResult = worker.doWork()
        assertEquals(androidx.work.ListenableWorker.Result.success(), workerResult)
    }

    @Test
    fun test4_normalBackupAndRestoreWorksWhenStealthModeInactive() = runBlocking {
        SessionManager.setStealthMode(false)
        assertFalse(SessionManager.isStealthModeActive())

        // Seed wallet to backup
        db.walletDao().insertWallet(
            Wallet(
                id = 101L,
                name = "Dompet Asli",
                balance = 500000.0,
                type = WalletType.BANK,
                colorHex = "#3B82F6",
                iconName = "bank",
                accountMode = AccountMode.PERSONAL.name,
                isDefault = true,
                userId = testUserId
            )
        )

        // Backup should succeed
        val backupResult = GoogleDriveService.backupNow(app, db, testUserId)
        assertTrue("Backup normal harus sukses saat stealth mode nonaktif: ${backupResult.second}", backupResult.first)

        // Restore should succeed
        val restoreResult = GoogleDriveService.restoreNow(app, db, testUserId)
        assertTrue("Restore normal harus sukses saat stealth mode nonaktif: ${restoreResult.second}", restoreResult.first)
    }

    @Test
    fun test5_realDataIntactAfterStealthModeToggled() = runBlocking {
        // Clear any auto-provisioned default wallets so test starts with exactly the isolated test wallet
        db.walletDao().getAllWallets(testUserId).first().forEach {
            db.walletDao().deleteWallet(it.id, testUserId)
        }

        // Seed authentic user data in database (wallets, debts, assets, transactions)
        val realWalletId = db.walletDao().insertWallet(
            Wallet(
                id = 201L,
                name = "Rekening Tabungan",
                balance = 12500000.0,
                type = WalletType.BANK,
                colorHex = "#10B981",
                iconName = "bank",
                accountMode = AccountMode.PERSONAL.name,
                isDefault = true,
                userId = testUserId
            )
        )
        db.debtDao().insertDebt(
            DebtItem(
                id = "301",
                name = "KPR Rumah",
                amount = 250000000.0,
                interestRatePercent = 5.0,
                minMonthlyPayment = 2000000.0,
                dueDate = "15 Setiap Bulan",
                userId = testUserId
            )
        )
        db.assetDao().insertAsset(
            AssetItem(
                id = "401",
                name = "Emas Batangan",
                initialValue = 50000000.0,
                purchaseYear = 2024,
                category = AssetCategory.GOLD_INVESTMENT,
                userId = testUserId
            )
        )
        db.transactionDao().insertTransaction(
            TransactionItem(
                id = 501L,
                title = "Gaji Bulanan",
                amount = 15000000.0,
                type = TransactionType.INCOME,
                category = "Gaji",
                walletId = 201L,
                walletName = "Rekening Tabungan",
                timestamp = System.currentTimeMillis(),
                userId = testUserId
            )
        )

        // 1. In Normal Mode: shows real data
        SessionManager.setStealthMode(false)
        ShadowLooper.idleMainLooper()
        val normalWallets = withTimeout(5000L) {
            financeViewModel.wallets.first { it.any { w -> w.id == 201L } }
        }
        assertEquals(1, normalWallets.size)
        assertEquals("Rekening Tabungan", normalWallets.first().name)
        assertEquals(12500000.0, normalWallets.first().balance, 0.001)

        val normalDebts = withTimeout(5000L) {
            financeViewModel.debts.first { it.any { d -> d.id == "301" } }
        }
        assertEquals(1, normalDebts.size)
        assertEquals("KPR Rumah", normalDebts.first().name)

        val normalAssets = withTimeout(5000L) {
            financeViewModel.assets.first { it.any { a -> a.id == "401" } }
        }
        assertEquals(1, normalAssets.size)
        assertEquals("Emas Batangan", normalAssets.first().name)

        val normalTxs = withTimeout(5000L) {
            financeViewModel.allTransactions.first { it.any { t -> t.id == 501L } }
        }
        assertEquals(1, normalTxs.size)
        assertEquals("Gaji Bulanan", normalTxs.first().title)
        assertEquals(15000000.0, normalTxs.first().amount, 0.001)

        // 2. In Stealth Mode: camouflaged
        SessionManager.setStealthMode(true)
        ShadowLooper.idleMainLooper()
        val stealthWallets = withTimeout(5000L) {
            financeViewModel.wallets.first { it.any { w -> w.id == -999L } }
        }
        assertEquals(1, stealthWallets.size)
        assertEquals("Uang Saku", stealthWallets.first().name)
        assertEquals(25000.0, stealthWallets.first().balance, 0.001)

        val stealthTxs = withTimeout(5000L) {
            financeViewModel.allTransactions.first { it.isNotEmpty() }
        }
        assertEquals(2, stealthTxs.size)
        assertFalse("Transaksi nyata tidak boleh bocor di stealth mode", stealthTxs.any { it.id == 501L })

        assertEquals(0, financeViewModel.debts.first().size)
        assertEquals(0, financeViewModel.assets.first().size)
        assertEquals(0, financeViewModel.budgetLimits.first().size)
        assertEquals(0, financeViewModel.splitBillHistory.first().size)

        // 3. Return to Normal Mode: real data remains 100% intact
        SessionManager.setStealthMode(false)
        ShadowLooper.idleMainLooper()
        val restoredWallets = withTimeout(5000L) {
            financeViewModel.wallets.first { it.any { w -> w.id == 201L } }
        }
        assertEquals(1, restoredWallets.size)
        assertEquals("Rekening Tabungan", restoredWallets.first().name)
        assertEquals(12500000.0, restoredWallets.first().balance, 0.001)

        val restoredDebts = withTimeout(5000L) {
            financeViewModel.debts.first { it.any { d -> d.id == "301" } }
        }
        assertEquals(1, restoredDebts.size)
        assertEquals("KPR Rumah", restoredDebts.first().name)

        val restoredAssets = withTimeout(5000L) {
            financeViewModel.assets.first { it.any { a -> a.id == "401" } }
        }
        assertEquals(1, restoredAssets.size)
        assertEquals("Emas Batangan", restoredAssets.first().name)

        val restoredTxs = withTimeout(5000L) {
            financeViewModel.allTransactions.first { it.any { t -> t.id == 501L } }
        }
        assertEquals(1, restoredTxs.size)
        assertEquals("Gaji Bulanan", restoredTxs.first().title)
        assertEquals(15000000.0, restoredTxs.first().amount, 0.001)
    }

    @Test
    fun test6_dummyDataNeverWrittenToSqliteRoomDatabase() = runBlocking {
        // Activate Stealth Mode
        SessionManager.setStealthMode(true)

        // Observe dummy data in UI StateFlow
        val uiWallets = withTimeout(5000L) { financeViewModel.wallets.first { it.isNotEmpty() } }
        assertEquals("Uang Saku", uiWallets.first().name)
        assertEquals(25000.0, uiWallets.first().balance, 0.001)

        val uiTxs = withTimeout(5000L) { financeViewModel.allTransactions.first { it.isNotEmpty() } }
        assertEquals(2, uiTxs.size)

        // Verify SQLite Database directly via Room DAO: NO dummy wallet or tx in database!
        val dbWallets = withTimeout(5000L) { db.walletDao().getAllWallets(testUserId).first() }
        assertFalse("Database tidak boleh memuat dummy wallet", dbWallets.any { it.name == "Uang Saku" && it.id == -999L })

        val dbTxs = withTimeout(5000L) { db.transactionDao().getAllTransactions(testUserId).first() }
        assertFalse("Database tidak boleh memuat dummy transactions", dbTxs.any { it.id == -998L || it.id == -999L })
    }

    @Test
    fun test7_failSafeBehaviorBlocksSensitiveOperations() = runBlocking {
        // Status stealth mode is authoritative and default safe
        SessionManager.setStealthMode(true)
        val isStealth = SessionManager.isStealthModeActive()
        assertTrue(isStealth)

        val backupResult = GoogleDriveService.backupNow(app, db, testUserId)
        assertFalse(backupResult.first)
    }

    @Test
    fun test8_mainPinAndDuressPinFlowIntegrity() = runBlocking {
        // Configure PINs via AuthViewModel
        val mainPin = "112233"
        val duressPin = "998877"
        authViewModel.saveSecurityPins(mainPin, duressPin, false)

        // 1. Enter Duress PIN -> Activates Stealth Mode
        authViewModel.verifyPinAndUnlock(duressPin)
        assertTrue("Duress PIN harus mengaktifkan stealth mode di SessionManager", SessionManager.isStealthModeActive())
        assertTrue("Duress PIN harus mengaktifkan stealth mode di UI state", authViewModel.gamificationState.value.isStealthMode)
        assertTrue("Duress toast notification must be shown", authViewModel.isSuccessToast.value?.contains("Masuk Mode Stealth (Duress PIN Mode)") == true)

        // 2. Enter Main PIN -> Deactivates Stealth Mode (Normal Mode)
        authViewModel.verifyPinAndUnlock(mainPin)
        assertFalse("Main PIN harus menonaktifkan stealth mode di SessionManager", SessionManager.isStealthModeActive())
        assertFalse("Main PIN harus menonaktifkan stealth mode di UI state", authViewModel.gamificationState.value.isStealthMode)
        assertEquals("✓ Keamanan Diverifikasi! Mode Asli Aktif", authViewModel.isSuccessToast.value)

        // 3. Enter Invalid PIN -> Fails without changing state
        authViewModel.verifyPinAndUnlock("000000")
        assertFalse(SessionManager.isStealthModeActive())
        assertEquals("❌ PIN Salah!", authViewModel.isSuccessToast.value)
    }
}
