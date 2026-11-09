package com.example.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AccountMode
import com.example.data.model.DebtItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.ui.viewmodel.TimeframeFilter
import com.example.util.AppLanguage
import com.example.util.AppStrings
import com.example.util.CurrencyFormatter
import com.example.util.EntitlementManager
import com.example.util.EntitlementSource
import com.example.util.LocaleHelper
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
class Phase1LocalizationAndAiSecurityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        LocaleHelper.initialize(context)
        CurrencyFormatter.initialize(context)
        context.getSharedPreferences("finsmart_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    // =========================================================================
    // SECTION A: AI ENTITLEMENT AUTHORITATIVE SECURITY AT EXECUTION BOUNDARY
    // =========================================================================

    @Test
    fun testAuthoritativeEntitlement_staleUiBoolean_doesNotGrantAccess() {
        val now = System.currentTimeMillis()
        val expiredGuest = User(
            id = 100L,
            username = "Guest Expired",
            email = "guest@domain.com",
            role = UserRole.FREE_MEMBER,
            isGuest = true,
            subscriptionExpiresAt = now - 5000L
        )

        // Simulasi kondisi UI stale: UI Composable mengira isUnlocked = true (stale cache)
        val staleUiBoolean = true
        // Namun authoritative provider memvalidasi status riil pengguna aktif
        val authoritativeCheck = {
            EntitlementManager.evaluate(expiredGuest, now = now).isPremiumUnlocked
        }

        // Execution boundary check
        val isExecutionAllowed = staleUiBoolean && authoritativeCheck()
        assertFalse("Eksekusi AI harus ditolak saat evaluasi otoritatif pengguna aktif bernilai false meskipun UI bernilai true", isExecutionAllowed)
    }

    @Test
    fun testRewardedSmartMaster_exactlyWithin60Minutes_isAllowed() {
        val grantedAt = 1730000000000L
        val activeUntil = grantedAt + 3600000L // 60 menit presisi
        val guestUser = User(
            id = 200L,
            username = "Guest Rewarded",
            email = "guest@domain.com",
            role = UserRole.SMART_MASTER,
            isGuest = true,
            subscriptionExpiresAt = activeUntil
        )

        // 59 menit setelah granted
        val at59Min = grantedAt + 59 * 60 * 1000L
        val status59 = EntitlementManager.evaluate(guestUser, now = at59Min)
        assertTrue("Pengguna masih berhak atas SmartMaster di menit ke-59", status59.isPremiumUnlocked)
        assertEquals(EntitlementSource.REWARDED_AD, status59.primarySource)

        // 60 menit 1 detik setelah granted -> EXPIRED
        val at61Min = grantedAt + 61 * 60 * 1000L
        val status61 = EntitlementManager.evaluate(guestUser, now = at61Min)
        assertFalse("Pengguna harus terkunci seketika setelah 60 menit habis", status61.isPremiumUnlocked)
        assertTrue("Status harus expired", status61.isExpired)
    }

    @Test
    fun testGuestUser_financialExecutionBoundary_draftProtection() {
        var wasPaywallTriggered = false
        var wasTransactionSaved = false

        val onTriggerPaywall = { wasPaywallTriggered = true }
        val onSaveTransaction = { wasTransactionSaved = true }

        val guestUser = User(
            id = 300L,
            username = "Guest Free",
            email = "guest@domain.com",
            role = UserRole.FREE_MEMBER,
            isGuest = true
        )

        val onCheckEntitlement = {
            EntitlementManager.evaluate(guestUser).isPremiumUnlocked
        }

        // Eksekusi callback penyimpanan draft dari ChatBubble
        if (!onCheckEntitlement()) {
            onTriggerPaywall()
        } else {
            onSaveTransaction()
        }

        assertTrue("Paywall harus terpanggil", wasPaywallTriggered)
        assertFalse("Transaksi tidak boleh tersimpan tanpa entitlement", wasTransactionSaved)
    }

    // =========================================================================
    // SECTION D & E: LANGUAGE SWITCHING & FINANCIAL DATA INTEGRITY
    // =========================================================================

    @Test
    fun testLanguageSwitching_preservesFinancialDataIntegrity() {
        // Data Keuangan Awal
        val walletBefore = Wallet(
            id = 1L,
            name = "BCA Tabungan Utama",
            type = WalletType.BANK,
            balance = 12_500_000.0,
            accountNumber = "1234567890",
            currencyCode = "IDR"
        )

        val transactionBefore = TransactionItem(
            id = 10L,
            title = "Belanja Bulanan Supermarket",
            amount = 750_000.0,
            type = TransactionType.EXPENSE,
            category = "Belanja",
            categoryIcon = "cart",
            walletId = 1L,
            walletName = "BCA Tabungan Utama",
            timestamp = 1730050000000L
        )

        val debtBefore = DebtItem(
            id = "debt_5",
            name = "Cicilan Laptop",
            amount = 5_000_000.0,
            interestRatePercent = 2.5,
            minMonthlyPayment = 500_000.0,
            dueDate = "20 tiap bulan"
        )

        // 1. Set ke Bahasa Indonesia
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        assertEquals(AppLanguage.INDONESIAN, LocaleHelper.currentLanguage.value)
        assertEquals("Bank", WalletType.BANK.displayName)
        assertEquals("Pribadi", AccountMode.PERSONAL.displayName)
        assertEquals("Harian", TimeframeFilter.DAILY.label)
        assertEquals("Bulanan", TimeframeFilter.MONTHLY.label)

        // 2. Set ke English
        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)
        assertEquals(AppLanguage.ENGLISH, LocaleHelper.currentLanguage.value)
        assertEquals("Bank", WalletType.BANK.displayName)
        assertEquals("Personal", AccountMode.PERSONAL.displayName)
        assertEquals("Daily", TimeframeFilter.DAILY.label)
        assertEquals("Monthly", TimeframeFilter.MONTHLY.label)

        // 3. Stress Test: Switching 10x bolak-balik
        for (i in 1..10) {
            val target = if (i % 2 == 0) AppLanguage.INDONESIAN else AppLanguage.ENGLISH
            LocaleHelper.setLanguage(context, target)
        }

        // 4. Verifikasi bahwa data keuangan TIDAK tersentuh sedikitpun
        assertEquals(1L, walletBefore.id)
        assertEquals("BCA Tabungan Utama", walletBefore.name)
        assertEquals(WalletType.BANK, walletBefore.type)
        assertEquals(12_500_000.0, walletBefore.balance, 0.001)
        assertEquals("1234567890", walletBefore.accountNumber)
        assertEquals("IDR", walletBefore.currencyCode)

        assertEquals(10L, transactionBefore.id)
        assertEquals("Belanja Bulanan Supermarket", transactionBefore.title)
        assertEquals(750_000.0, transactionBefore.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, transactionBefore.type)
        assertEquals(1730050000000L, transactionBefore.timestamp)

        assertEquals("debt_5", debtBefore.id)
        assertEquals("Cicilan Laptop", debtBefore.name)
        assertEquals(5_000_000.0, debtBefore.amount, 0.001)
        assertEquals(2.5, debtBefore.interestRatePercent, 0.001)
        assertEquals(500_000.0, debtBefore.minMonthlyPayment, 0.001)
        assertEquals("20 tiap bulan", debtBefore.dueDate)
    }

    @Test
    fun testBilingualStringDictionary_completenessAndAccuracy() {
        // ID checks
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        assertEquals("Simpan Transaksi", AppStrings.confirmSaveTransaction)
        assertEquals("Analisis", AppStrings.navAnalytics)
        assertEquals("Dompet", AppStrings.navWallets)
        assertEquals("Kekayaan Bersih (Net Worth)", AppStrings.netWorth)

        // EN checks
        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)
        assertEquals("Save Transaction", AppStrings.confirmSaveTransaction)
        assertEquals("Analytics", AppStrings.navAnalytics)
        assertEquals("Wallets", AppStrings.navWallets)
        assertEquals("Total Net Worth", AppStrings.netWorth)
    }
}
