package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.NavigationScreen
import com.example.util.AppLanguage
import com.example.util.AppStrings
import com.example.util.CurrencyFormatter
import com.example.util.LocaleHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class LocalizationBilingualTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        LocaleHelper.initialize(context)
    }

    @Test
    fun scenarioA_indonesianLocale_displaysIndonesianStringsAndFormats() {
        // Set ke Bahasa Indonesia
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)

        assertFalse("Harus bukan English", LocaleHelper.isEnglish())
        assertEquals(AppLanguage.INDONESIAN, LocaleHelper.currentLanguage.value)
        assertEquals("id", LocaleHelper.getActiveLocale().language)

        // Verifikasi AppStrings
        assertEquals("Beranda", AppStrings.navHome)
        assertEquals("Mutasi", AppStrings.navTransactions)
        assertEquals("Dompet", AppStrings.navWallets)
        assertEquals("Total Saldo DompetNalar", AppStrings.totalBalance)
        assertEquals("Pemasukan", AppStrings.income)
        assertEquals("Pengeluaran", AppStrings.expense)
        assertEquals("Mode Pribadi", AppStrings.personalMode)
        assertEquals("Hari Ini", AppStrings.today)
        assertEquals("Kemarin", AppStrings.yesterday)

        // Verifikasi NavigationScreen dynamic labels
        assertEquals("Dasbor", NavigationScreen.DASHBOARD.label)
        assertEquals("Mutasi", NavigationScreen.TRANSACTIONS.label)
        assertEquals("Pengaturan", NavigationScreen.SETTINGS.label)

        // Verifikasi Formatter Relative Date & Compact Numbers
        val now = System.currentTimeMillis()
        assertEquals("Hari Ini", CurrencyFormatter.formatRelativeDate(now))
        val yesterday = now - 86400000L
        assertEquals("Kemarin", CurrencyFormatter.formatRelativeDate(yesterday))

        val compactMillions = CurrencyFormatter.formatCompactRupiah(5_000_000.0)
        assertTrue("Format jutaan di Indonesia harus mengandung Jt: $compactMillions", compactMillions.contains("Jt"))
    }

    @Test
    fun scenarioB_englishLocale_displaysEnglishStringsAndFormats() {
        // Set ke English
        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)

        assertTrue("Harus English", LocaleHelper.isEnglish())
        assertEquals(AppLanguage.ENGLISH, LocaleHelper.currentLanguage.value)
        assertEquals("en", LocaleHelper.getActiveLocale().language)

        // Verifikasi AppStrings
        assertEquals("Home", AppStrings.navHome)
        assertEquals("Transactions", AppStrings.navTransactions)
        assertEquals("Wallets", AppStrings.navWallets)
        assertEquals("DompetNalar Total Balance", AppStrings.totalBalance)
        assertEquals("Income", AppStrings.income)
        assertEquals("Expense", AppStrings.expense)
        assertEquals("Personal Mode", AppStrings.personalMode)
        assertEquals("Today", AppStrings.today)
        assertEquals("Yesterday", AppStrings.yesterday)

        // Verifikasi NavigationScreen dynamic labels
        assertEquals("Home", NavigationScreen.DASHBOARD.label)
        assertEquals("Transactions", NavigationScreen.TRANSACTIONS.label)
        assertEquals("Settings", NavigationScreen.SETTINGS.label)

        // Verifikasi Formatter Relative Date & Compact Numbers
        val now = System.currentTimeMillis()
        assertEquals("Today", CurrencyFormatter.formatRelativeDate(now))
        val yesterday = now - 86400000L
        assertEquals("Yesterday", CurrencyFormatter.formatRelativeDate(yesterday))

        val compactMillions = CurrencyFormatter.formatCompactRupiah(5_000_000.0)
        assertTrue("Format jutaan di Inggris harus mengandung M: $compactMillions", compactMillions.contains("M"))
    }

    @Test
    fun scenarioC_bidirectionalSwitching_preservesIntegrity() {
        // 1. Mulai dari ID
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        assertEquals("Pemasukan", AppStrings.income)

        // 2. Ubah ke EN
        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)
        assertEquals("Income", AppStrings.income)
        assertEquals("Today", AppStrings.today)

        // 3. Kembalikan lagi ke ID
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        assertEquals("Pemasukan", AppStrings.income)
        assertEquals("Hari Ini", AppStrings.today)
        assertFalse(LocaleHelper.isEnglish())
    }

    @Test
    fun scenarioD_languageCodePersistenceAndParsing() {
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromCode("id"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("en"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode("system"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode("unknown"))

        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)
        // Reset in-memory state dan verifikasi pembacaan dari SharedPreferences
        LocaleHelper.initialize(context)
        assertEquals("Preferensi bahasa harus bertahan setelah restart", AppLanguage.ENGLISH, LocaleHelper.currentLanguage.value)
        assertTrue(LocaleHelper.isEnglish())

        // Kembalikan ke sistem
        LocaleHelper.setLanguage(context, AppLanguage.SYSTEM)
        LocaleHelper.initialize(context)
        assertEquals(AppLanguage.SYSTEM, LocaleHelper.currentLanguage.value)
    }

    @Test
    fun scenarioE_wrapContextAndRepeatedSwitching_noCrash() {
        val wrappedContext = LocaleHelper.wrapContext(context)
        org.junit.Assert.assertNotNull(wrappedContext)

        // Multiple rapid switches should not throw exceptions or crash
        for (i in 0..5) {
            val target = if (i % 2 == 0) AppLanguage.INDONESIAN else AppLanguage.ENGLISH
            LocaleHelper.setLanguage(context, target)
            assertEquals(target, LocaleHelper.currentLanguage.value)
        }
    }

    @Test
    fun scenarioF_categoryAndPayLaterTranslation_bilingualIntegrity() {
        // Mode Bahasa Indonesia
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        assertEquals("Belanja & Hobi", AppStrings.translateCategory("Belanja & Hobi"))
        assertEquals("Pinjaman Online", AppStrings.translatePayLaterCategory("Pinjaman Online"))
        assertEquals("Kartu Kredit", AppStrings.translatePayLaterCategory("Kartu Kredit"))
        assertEquals("Tenggat Terdekat ⏰", AppStrings.translatePayoffStrategyTitle("Tenggat Terdekat ⏰"))

        // Mode Bahasa Inggris
        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)
        assertEquals("Shopping & Hobbies", AppStrings.translateCategory("Belanja & Hobi"))
        assertEquals("Entertainment & Subscriptions", AppStrings.translateCategory("Hiburan & Langganan"))
        assertEquals("Health", AppStrings.translateCategory("Kesehatan"))
        assertEquals("Online Loan", AppStrings.translatePayLaterCategory("Pinjaman Online"))
        assertEquals("Credit Card", AppStrings.translatePayLaterCategory("Kartu Kredit"))
        assertEquals("Vehicle Installment", AppStrings.translatePayLaterCategory("Cicilan Kendaraan"))
        assertEquals("Others", AppStrings.translatePayLaterCategory("Lain-lain"))
        assertEquals("Earliest Due Date ⏰", AppStrings.translatePayoffStrategyTitle("Tenggat Terdekat ⏰"))
        assertEquals("Highest Interest ⚡", AppStrings.translatePayoffStrategyTitle("Bunga Tertinggi ⚡"))
        assertEquals("Lowest Balance ⛄", AppStrings.translatePayoffStrategyTitle("Nominal Terkecil ⛄"))
    }
}
