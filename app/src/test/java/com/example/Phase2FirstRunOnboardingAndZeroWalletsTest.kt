package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.data.repository.FinanceRepository
import com.example.data.session.SessionManager
import com.example.util.AppCurrency
import com.example.util.AppLanguage
import com.example.util.AppStrings
import com.example.util.CurrencyFormatter
import com.example.util.CurrencyMode
import com.example.util.LocaleHelper
import com.example.util.OnboardingManager
import com.example.util.PolicyManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase2FirstRunOnboardingAndZeroWalletsTest {

    private lateinit var context: Context
    private lateinit var db: FinSmartDatabase
    private lateinit var financeRepo: FinanceRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        OnboardingManager.resetForTesting(context)
        PolicyManager.resetPolicyForTesting(context)
        LocaleHelper.initialize(context)
        CurrencyFormatter.initialize(context)
        SessionManager.initialize(context)

        com.example.data.local.DatabaseWriteCoordinator.resetForTesting()
        db = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        financeRepo = FinanceRepository(walletDao = db.walletDao(), database = db)
    }

    @After
    fun tearDown() {
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        CurrencyFormatter.setCurrency(AppCurrency.IDR, context)
        CurrencyFormatter.setMode(context, CurrencyMode.FOLLOW_DEVICE)
        OnboardingManager.resetForTesting(context)
        PolicyManager.resetPolicyForTesting(context)
        SessionManager.resetForTesting()
        com.example.data.local.DatabaseWriteCoordinator.resetForTesting()
        db.close()
    }

    // =========================================================================
    // 1. ONBOARDING MANAGER LIFECYCLE & STATE PERSISTENCE
    // =========================================================================

    @Test
    fun testOnboardingManager_initialStateIsPending() {
        assertFalse(
            "Penginstalan baru wajib belum menyelesaikan onboarding",
            OnboardingManager.isOnboardingCompleted(context)
        )
        assertEquals(0L, OnboardingManager.getCompletedTimestamp(context))
    }

    @Test
    fun testOnboardingManager_completeOnboardingPersistsCorrectly() {
        val beforeTime = System.currentTimeMillis()
        OnboardingManager.completeOnboarding(context)

        assertTrue(
            "Setelah ditandai selesai, isOnboardingCompleted harus bernilai true",
            OnboardingManager.isOnboardingCompleted(context)
        )
        val timestamp = OnboardingManager.getCompletedTimestamp(context)
        assertTrue(
            "Waktu penyelesaian onboarding harus valid dan >= waktu sebelum eksekusi",
            timestamp >= beforeTime
        )
    }

    @Test
    fun testOnboardingManager_resetForTestingClearsState() {
        OnboardingManager.completeOnboarding(context)
        assertTrue(OnboardingManager.isOnboardingCompleted(context))

        OnboardingManager.resetForTesting(context)
        assertFalse(
            "Setelah direset, status onboarding wajib kembali false",
            OnboardingManager.isOnboardingCompleted(context)
        )
    }

    // =========================================================================
    // 2. ONBOARDING SELECTIONS INTEGRATION (LANGUAGE & CURRENCY)
    // =========================================================================

    @Test
    fun testOnboarding_languageSelection_appliesImmediately() {
        // 1. Simulasi user memilih Bahasa Indonesia
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        assertFalse("LocaleHelper harus berada pada mode Bahasa Indonesia", LocaleHelper.isEnglish())
        assertEquals(AppLanguage.INDONESIAN, LocaleHelper.currentLanguage.value)

        // 2. Simulasi user memilih English
        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)
        assertTrue("LocaleHelper harus berada pada mode English", LocaleHelper.isEnglish())
        assertEquals(AppLanguage.ENGLISH, LocaleHelper.currentLanguage.value)

        // 3. Simulasi user memilih Ikuti Sistem HP
        LocaleHelper.setLanguage(context, AppLanguage.SYSTEM)
        assertEquals(AppLanguage.SYSTEM, LocaleHelper.currentLanguage.value)
    }

    @Test
    fun testOnboarding_currencySelection_followDeviceAppliesCorrectly() {
        // Simulasi user memilih Ikuti Wilayah HP
        CurrencyFormatter.setMode(context, CurrencyMode.FOLLOW_DEVICE)
        assertEquals(CurrencyMode.FOLLOW_DEVICE, CurrencyFormatter.currencyMode.value)
        assertNotNull(CurrencyFormatter.getCurrency())
    }

    @Test
    fun testOnboarding_currencySelection_manualCurrencyAppliesCorrectly() {
        // 1. Pilih USD
        CurrencyFormatter.setCurrency(AppCurrency.USD, context)
        assertEquals(CurrencyMode.MANUAL, CurrencyFormatter.currencyMode.value)
        assertEquals(AppCurrency.USD, CurrencyFormatter.getCurrency())
        assertEquals("$", CurrencyFormatter.getCurrency().symbol)

        // 2. Pilih IDR
        CurrencyFormatter.setCurrency(AppCurrency.IDR, context)
        assertEquals(CurrencyMode.MANUAL, CurrencyFormatter.currencyMode.value)
        assertEquals(AppCurrency.IDR, CurrencyFormatter.getCurrency())
        assertEquals("Rp", CurrencyFormatter.getCurrency().symbol)

        // 3. Pilih EUR
        CurrencyFormatter.setCurrency(AppCurrency.EUR, context)
        assertEquals(CurrencyMode.MANUAL, CurrencyFormatter.currencyMode.value)
        assertEquals(AppCurrency.EUR, CurrencyFormatter.getCurrency())
        assertEquals("€", CurrencyFormatter.getCurrency().symbol)
    }

    // =========================================================================
    // 3. JAMINAN NOL DOMPET AWAL (USER AUTONOMY & ANTI-FAKE-PASS)
    // =========================================================================

    @Test
    fun testZeroWallets_freshUserHasZeroWalletsInitially() = runBlocking {
        val newUserId = db.userDao().insertUser(
            User(
                username = "Pengguna Baru",
                email = "userbaru@example.com",
                role = UserRole.FREE_MEMBER,
                isApproved = true,
                isGuest = false
            )
        )

        // Verifikasi tidak ada dompet bawaan yang diselundupkan
        val wallets = db.walletDao().getAllWallets(newUserId).first()
        assertTrue("Pengguna baru wajib bersih dengan 0 dompet awal", wallets.isEmpty())
        assertEquals(0, db.walletDao().getWalletCount(newUserId))
    }

    @Test
    fun testZeroWallets_guestUserHasZeroWalletsInitially() = runBlocking {
        val guest = SessionManager.startGuestSession(db.userDao())

        val guestWallets = db.walletDao().getAllWallets(guest.id).first()
        assertTrue("Pengguna tamu baru wajib bersih dengan 0 dompet awal", guestWallets.isEmpty())
        assertEquals(0, db.walletDao().getWalletCount(guest.id))
    }

    @Test
    fun testZeroWallets_provisionDefaultWalletsReturnsEmptyList() = runBlocking {
        val newUserId = db.userDao().insertUser(
            User(
                username = "User Mandiri",
                email = "mandiri@example.com",
                role = UserRole.FREE_MEMBER,
                isApproved = true
            )
        )

        val provisioned = financeRepo.provisionDefaultWalletsIfEmpty(newUserId)
        assertTrue("provisionDefaultWalletsIfEmpty wajib mengembalikan list kosong", provisioned.isEmpty())

        val remainingWallets = db.walletDao().getAllWallets(newUserId).first()
        assertEquals("Tidak boleh ada dompet tersimpan", 0, remainingWallets.size)
    }

    @Test
    fun testZeroWallets_userAddsOwnWalletExplicitly() = runBlocking {
        val newUserId = db.userDao().insertUser(
            User(
                username = "Budi Dompet",
                email = "budi@example.com",
                role = UserRole.FREE_MEMBER,
                isApproved = true
            )
        )

        // Sebelum ditambah: 0 dompet
        assertEquals(0, db.walletDao().getAllWallets(newUserId).first().size)

        // Pengguna membuat dompet pertamanya sendiri
        val myWalletId = financeRepo.insertWallet(
            Wallet(
                name = "Tabungan Pribadi",
                type = WalletType.BANK,
                balance = 1000000.0,
                colorHex = "#0B57D0",
                iconName = "bank",
                accountMode = AccountMode.PERSONAL.name,
                isDefault = true,
                userId = newUserId,
                currencyCode = "IDR"
            )
        )

        assertTrue(myWalletId > 0L)
        val wallets = db.walletDao().getAllWallets(newUserId).first()
        assertEquals(1, wallets.size)
        assertEquals("Tabungan Pribadi", wallets.first().name)
        assertEquals(1000000.0, wallets.first().balance, 0.001)
    }

    // =========================================================================
    // 4. FIRST-RUN SEQUENTIAL FLOW INTEGRATION (ONBOARDING -> POLICY -> READY)
    // =========================================================================

    @Test
    fun testFirstRunSequentialLifecycle_orderIsPreserved() {
        // Step 1: Fresh install state
        var isFirstRunOnboardingPending = !OnboardingManager.isOnboardingCompleted(context)
        var isFirstRunPolicyPending = !PolicyManager.isPolicyAcknowledged(context)

        assertTrue("Onboarding wajib pending pada instalasi baru", isFirstRunOnboardingPending)
        assertTrue("Kebijakan privasi wajib pending pada instalasi baru", isFirstRunPolicyPending)

        // Step 2: Pengguna menyelesaikan Onboarding (Pilih Bahasa Inggris & USD)
        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)
        CurrencyFormatter.setCurrency(AppCurrency.USD, context)
        OnboardingManager.completeOnboarding(context)
        isFirstRunOnboardingPending = !OnboardingManager.isOnboardingCompleted(context)

        assertFalse("Onboarding selesai", isFirstRunOnboardingPending)
        assertTrue("Kebijakan privasi masih pending sebelum disetujui", isFirstRunPolicyPending)
        assertTrue("Bahasa aplikasi sudah aktif dalam English", LocaleHelper.isEnglish())
        assertEquals(AppCurrency.USD, CurrencyFormatter.getCurrency())

        // Step 3: Pengguna menyetujui kebijakan privasi
        PolicyManager.acknowledgePolicy(context)
        isFirstRunPolicyPending = !PolicyManager.isPolicyAcknowledged(context)

        assertFalse("Kebijakan privasi selesai", isFirstRunPolicyPending)
        assertFalse("Kedua dialog pembuka selesai, siap masuk Dasbor", isFirstRunOnboardingPending || isFirstRunPolicyPending)
    }

    // =========================================================================
    // 5. BILINGUAL STRINGS CONSISTENCY (ONBOARDING & EMPTY STATE WALLETS)
    // =========================================================================

    @Test
    fun testBilingualStrings_onboardingAndEmptyState_areAccurate() {
        // Mode Bahasa Indonesia
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        assertEquals("Selamat Datang di DompetNalar", AppStrings.t("Selamat Datang di DompetNalar", "Welcome to DompetNalar"))
        assertEquals("Belum Ada Dompet Terdaftar", AppStrings.t("Belum Ada Dompet Terdaftar", "No Wallets Registered Yet"))
        assertEquals("Tambah Dompet Pertama", AppStrings.t("Tambah Dompet Pertama", "Add First Wallet"))
        assertEquals("Simpan & Mulai", AppStrings.t("Simpan & Mulai", "Save & Get Started"))

        // Mode English
        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)
        assertEquals("Welcome to DompetNalar", AppStrings.t("Selamat Datang di DompetNalar", "Welcome to DompetNalar"))
        assertEquals("No Wallets Registered Yet", AppStrings.t("Belum Ada Dompet Terdaftar", "No Wallets Registered Yet"))
        assertEquals("Add First Wallet", AppStrings.t("Tambah Dompet Pertama", "Add First Wallet"))
        assertEquals("Save & Get Started", AppStrings.t("Simpan & Mulai", "Save & Get Started"))
    }
}
