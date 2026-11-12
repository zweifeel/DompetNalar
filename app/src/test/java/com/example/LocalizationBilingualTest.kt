package com.example

import com.example.data.model.AssetCategory
import com.example.data.model.BudgetLimitItem
import com.example.data.model.DebtItem
import com.example.data.model.FinancialPersonality
import com.example.data.model.ReceiptItem
import com.example.data.model.ReceiptScanResult
import com.example.data.model.SplitBillPerson
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.util.AppLanguage
import com.example.util.AppStrings
import com.example.util.LocaleHelper
import com.example.util.NotificationDataHelper
import com.example.util.ReceiptFormatter
import com.example.util.SplitBillCalculator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class LocalizationBilingualTest {

    @Before
    fun setUp() {
        LocaleHelper.systemLocaleOverrideForTesting = null
        val field = LocaleHelper::class.java.getDeclaredField("_currentLanguage")
        field.isAccessible = true
        val flow = field.get(LocaleHelper) as kotlinx.coroutines.flow.MutableStateFlow<AppLanguage>
        flow.value = AppLanguage.SYSTEM
    }

    @After
    fun tearDown() {
        LocaleHelper.systemLocaleOverrideForTesting = null
        val field = LocaleHelper::class.java.getDeclaredField("_currentLanguage")
        field.isAccessible = true
        val flow = field.get(LocaleHelper) as kotlinx.coroutines.flow.MutableStateFlow<AppLanguage>
        flow.value = AppLanguage.SYSTEM
    }

    @Test
    fun testLocaleHelperIsEnglishSystemFollowsPhoneLocale() {
        // Test when phone is set to English (US)
        LocaleHelper.systemLocaleOverrideForTesting = Locale.US
        val field = LocaleHelper::class.java.getDeclaredField("_currentLanguage")
        field.isAccessible = true
        val flow = field.get(LocaleHelper) as kotlinx.coroutines.flow.MutableStateFlow<AppLanguage>
        flow.value = AppLanguage.SYSTEM

        assertTrue("When system locale is US, isEnglish() must be true", LocaleHelper.isEnglish())

        // Test when phone is set to Indonesian (ID)
        LocaleHelper.systemLocaleOverrideForTesting = Locale("id", "ID")
        assertFalse("When system locale is ID, isEnglish() must be false", LocaleHelper.isEnglish())
    }

    @Test
    fun testAppStringsTranslationSwitching() {
        val field = LocaleHelper::class.java.getDeclaredField("_currentLanguage")
        field.isAccessible = true
        val flow = field.get(LocaleHelper) as kotlinx.coroutines.flow.MutableStateFlow<AppLanguage>

        // Force English
        flow.value = AppLanguage.ENGLISH
        assertEquals("Save", AppStrings.save)
        assertEquals("Cancel", AppStrings.cancel)
        assertEquals("Transactions", AppStrings.navTransactions)
        assertEquals("Monthly Budget", AppStrings.budgetTitle)
        assertEquals("Food & Drinks", AppStrings.translateCategory("Makanan & Minuman"))

        // Force Indonesian
        flow.value = AppLanguage.INDONESIAN
        assertEquals("Simpan", AppStrings.save)
        assertEquals("Batal", AppStrings.cancel)
        assertEquals("Mutasi", AppStrings.navTransactions)
        assertEquals("Anggaran Bulanan", AppStrings.budgetTitle)
        assertEquals("Makanan & Minuman", AppStrings.translateCategory("Makanan & Minuman"))
    }

    @Test
    fun testAssetCategoryBilingual() {
        val field = LocaleHelper::class.java.getDeclaredField("_currentLanguage")
        field.isAccessible = true
        val flow = field.get(LocaleHelper) as kotlinx.coroutines.flow.MutableStateFlow<AppLanguage>

        flow.value = AppLanguage.ENGLISH
        assertEquals("Electronics / Gadgets", AssetCategory.ELECTRONICS.displayName)
        assertEquals("Gold & Savings", AssetCategory.GOLD_INVESTMENT.displayName)

        flow.value = AppLanguage.INDONESIAN
        assertEquals("Elektronik / Gadget", AssetCategory.ELECTRONICS.displayName)
        assertEquals("Emas & Tabungan", AssetCategory.GOLD_INVESTMENT.displayName)
    }

    @Test
    fun testFinancialPersonalityBilingual() {
        val field = LocaleHelper::class.java.getDeclaredField("_currentLanguage")
        field.isAccessible = true
        val flow = field.get(LocaleHelper) as kotlinx.coroutines.flow.MutableStateFlow<AppLanguage>

        flow.value = AppLanguage.ENGLISH
        assertEquals("The Saver", FinancialPersonality.SAVER.localizedTitle)

        flow.value = AppLanguage.INDONESIAN
        assertEquals("The Saver (Penyimpan Hemat)", FinancialPersonality.SAVER.localizedTitle)
    }

    @Test
    fun testReceiptFormatterBilingual() {
        val field = LocaleHelper::class.java.getDeclaredField("_currentLanguage")
        field.isAccessible = true
        val flow = field.get(LocaleHelper) as kotlinx.coroutines.flow.MutableStateFlow<AppLanguage>

        val sampleTx = TransactionItem(
            id = 101L,
            title = "Kopi Kenangan",
            amount = 25000.0,
            type = TransactionType.EXPENSE,
            category = "Makanan & Minuman",
            categoryIcon = "restaurant",
            walletId = 1L,
            walletName = "GoPay",
            timestamp = 1700000000000L,
            accountMode = "PERSONAL",
            userId = 1L
        )

        flow.value = AppLanguage.ENGLISH
        val receiptEn = ReceiptFormatter.formatReceiptText(sampleTx)
        assertTrue("English receipt must contain DIGITAL TRANSACTION RECEIPT", receiptEn.contains("DIGITAL TRANSACTION RECEIPT"))
        assertTrue("English receipt must contain Food & Drinks", receiptEn.contains("Food & Drinks"))
        assertTrue("English receipt must contain TOTAL EXPENSE", receiptEn.contains("TOTAL EXPENSE"))

        flow.value = AppLanguage.INDONESIAN
        val receiptId = ReceiptFormatter.formatReceiptText(sampleTx)
        assertTrue("Indonesian receipt must contain BUKTI TRANSAKSI DIGITAL", receiptId.contains("BUKTI TRANSAKSI DIGITAL"))
        assertTrue("Indonesian receipt must contain Makanan & Minuman", receiptId.contains("Makanan & Minuman"))
        assertTrue("Indonesian receipt must contain TOTAL KELUAR", receiptId.contains("TOTAL KELUAR"))
    }

    @Test
    fun testNotificationDataHelperBilingual() {
        val field = LocaleHelper::class.java.getDeclaredField("_currentLanguage")
        field.isAccessible = true
        val flow = field.get(LocaleHelper) as kotlinx.coroutines.flow.MutableStateFlow<AppLanguage>

        val wallets = listOf(Wallet(id = 1L, name = "BCA", type = WalletType.BANK, balance = 10000.0, userId = 1L))
        val debts = listOf(DebtItem(id = "d1", name = "Shopee PayLater", amount = 150000.0, interestRatePercent = 2.5, minMonthlyPayment = 150000.0, dueDate = "25", userId = 1L))
        val budgetLimits = listOf(BudgetLimitItem(id = "total_monthly", limitAmount = 1000000.0, isCategory = false, userId = 1L))
        val expenses = listOf(TransactionItem(id = 1L, title = "Belanja", amount = 900000.0, type = TransactionType.EXPENSE, category = "Belanja & Hobi", categoryIcon = "shopping_bag", walletId = 1L, walletName = "BCA", timestamp = System.currentTimeMillis(), accountMode = "PERSONAL", userId = 1L))

        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 25)

        flow.value = AppLanguage.ENGLISH
        val notifsEn = NotificationDataHelper.generateNotifications(
            wallets = wallets,
            debts = debts,
            budgetLimits = budgetLimits,
            currentMonthExpenses = expenses,
            calendar = cal
        )

        assertTrue("English notif must have Low Balance Alert", notifsEn.any { it.title.contains("Low Balance Alert") })
        assertTrue("English notif must have Due Today", notifsEn.any { it.title.contains("Due Today") })
        assertTrue("English notif must have Monthly Budget Warning", notifsEn.any { it.title.contains("Monthly Budget Warning") })

        flow.value = AppLanguage.INDONESIAN
        val notifsId = NotificationDataHelper.generateNotifications(
            wallets = wallets,
            debts = debts,
            budgetLimits = budgetLimits,
            currentMonthExpenses = expenses,
            calendar = cal
        )

        assertTrue("Indonesian notif must have Peringatan Saldo Rendah", notifsId.any { it.title.contains("Peringatan Saldo Rendah") })
        assertTrue("Indonesian notif must have Jatuh Tempo Hari Ini", notifsId.any { it.title.contains("Jatuh Tempo Hari Ini") })
        assertTrue("Indonesian notif must have Peringatan Anggaran Bulanan", notifsId.any { it.title.contains("Peringatan Anggaran Bulanan") })
    }

    @Test
    fun testSplitBillSummaryBilingual() {
        val field = LocaleHelper::class.java.getDeclaredField("_currentLanguage")
        field.isAccessible = true
        val flow = field.get(LocaleHelper) as kotlinx.coroutines.flow.MutableStateFlow<AppLanguage>

        val receipt = ReceiptScanResult(id = "r1", merchantName = "Cafe Nalar", totalAmount = 100000.0)
        val personA = SplitBillPerson(id = "p1", name = "Alice")
        val personB = SplitBillPerson(id = "p2", name = "Bob")
        val item1 = ReceiptItem(id = "i1", name = "Coffee", price = 30000.0, qty = 2, total = 60000.0, assignedPersonIds = listOf("p1", "p2"))

        val splitResult = SplitBillCalculator.calculateSplitBill(
            receipt = receipt,
            people = listOf(personA, personB),
            items = listOf(item1),
            paymentTargetInfo = "BCA 12345678"
        )

        flow.value = AppLanguage.ENGLISH
        val summaryEn = SplitBillCalculator.generateWhatsAppSummary(splitResult)
        assertTrue("English summary must contain SPLIT BILL BREAKDOWN", summaryEn.contains("SPLIT BILL BREAKDOWN"))
        assertTrue("English summary must contain split among", summaryEn.contains("split among"))

        flow.value = AppLanguage.INDONESIAN
        val summaryId = SplitBillCalculator.generateWhatsAppSummary(splitResult)
        assertTrue("Indonesian summary must contain RINCIAN SPLIT BILL", summaryId.contains("RINCIAN SPLIT BILL"))
        assertTrue("Indonesian summary must contain dibagi", summaryId.contains("dibagi"))
    }

    @Test
    fun testTransactionsAndDebtPlannerLocalizationIntegrity() {
        val field = LocaleHelper::class.java.getDeclaredField("_currentLanguage")
        field.isAccessible = true
        val flow = field.get(LocaleHelper) as kotlinx.coroutines.flow.MutableStateFlow<AppLanguage>

        // 1. English Mode Verification
        flow.value = AppLanguage.ENGLISH
        assertTrue("isEnglish() should be true", LocaleHelper.isEnglish())
        assertEquals("Shopping & Hobbies", AppStrings.translateCategory("Belanja & Hobi"))
        assertEquals("Cashflow Summary (Monthly)", AppStrings.t("Rekap Arus Kas (Monthly)", "Cashflow Summary (Monthly)"))
        assertEquals("Wallet", AppStrings.t("Dompet", "Wallet"))

        val enDueDateDefault = if (LocaleHelper.isEnglish()) "Day 15 each month" else "Tgl 15 tiap bulan"
        assertEquals("Day 15 each month", enDueDateDefault)

        // 2. Indonesian Mode Verification
        flow.value = AppLanguage.INDONESIAN
        assertFalse("isEnglish() should be false", LocaleHelper.isEnglish())
        assertEquals("Belanja & Hobi", AppStrings.translateCategory("Belanja & Hobi"))
        assertEquals("Rekap Arus Kas (Bulanan)", AppStrings.t("Rekap Arus Kas (Bulanan)", "Cashflow Summary (Monthly)"))
        assertEquals("Dompet", AppStrings.t("Dompet", "Wallet"))

        val idDueDateDefault = if (LocaleHelper.isEnglish()) "Day 15 each month" else "Tgl 15 tiap bulan"
        assertEquals("Tgl 15 tiap bulan", idDueDateDefault)
    }
}
