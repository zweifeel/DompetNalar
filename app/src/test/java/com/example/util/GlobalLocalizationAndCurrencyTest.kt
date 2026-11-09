package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GlobalLocalizationAndCurrencyTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        LocaleHelper.initialize(context)
        CurrencyFormatter.initialize(context)
    }

    @org.junit.After
    fun tearDown() {
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        CurrencyFormatter.setCurrency(AppCurrency.IDR, context)
        CurrencyFormatter.setMode(context, CurrencyMode.FOLLOW_DEVICE)
    }

    // ==========================================
    // 1. 10 International Currencies & Decimal Handling
    // ==========================================

    @Test
    fun testCurrencyFormatter_All10SupportedCurrencies() {
        val testAmount = 1500.0

        // 1. IDR (0 decimals)
        CurrencyFormatter.setCurrency(AppCurrency.IDR, context)
        val idrFormatted = CurrencyFormatter.formatCurrency(testAmount)
        assertTrue("IDR must contain Rp: $idrFormatted", idrFormatted.contains("Rp"))
        assertFalse("IDR should have 0 decimals: $idrFormatted", idrFormatted.contains(",00") || idrFormatted.contains(".00"))

        // 2. JPY (0 decimals)
        CurrencyFormatter.setCurrency(AppCurrency.JPY, context)
        val jpyFormatted = CurrencyFormatter.formatCurrency(testAmount)
        assertTrue("JPY must contain ¥: $jpyFormatted", jpyFormatted.contains("¥"))
        assertFalse("JPY should have 0 decimals: $jpyFormatted", jpyFormatted.contains(",00") || jpyFormatted.contains(".00"))

        // 3. USD (2 decimals)
        CurrencyFormatter.setCurrency(AppCurrency.USD, context)
        val usdFormatted = CurrencyFormatter.formatCurrency(testAmount)
        assertTrue("USD must contain $: $usdFormatted", usdFormatted.contains("$"))
        assertTrue("USD should have 2 decimals: $usdFormatted", usdFormatted.contains(".00") || usdFormatted.contains(",00"))

        // 4. EUR (2 decimals)
        CurrencyFormatter.setCurrency(AppCurrency.EUR, context)
        val eurFormatted = CurrencyFormatter.formatCurrency(testAmount)
        assertTrue("EUR must contain €: $eurFormatted", eurFormatted.contains("€"))

        // 5. GBP (2 decimals)
        CurrencyFormatter.setCurrency(AppCurrency.GBP, context)
        val gbpFormatted = CurrencyFormatter.formatCurrency(testAmount)
        assertTrue("GBP must contain £: $gbpFormatted", gbpFormatted.contains("£"))

        // 6. SGD (2 decimals)
        CurrencyFormatter.setCurrency(AppCurrency.SGD, context)
        val sgdFormatted = CurrencyFormatter.formatCurrency(testAmount)
        assertTrue("SGD must contain S$: $sgdFormatted", sgdFormatted.contains("S$") || sgdFormatted.contains("$"))

        // 7. MYR (2 decimals)
        CurrencyFormatter.setCurrency(AppCurrency.MYR, context)
        val myrFormatted = CurrencyFormatter.formatCurrency(testAmount)
        assertTrue("MYR must contain RM: $myrFormatted", myrFormatted.contains("RM"))

        // 8. AUD (2 decimals)
        CurrencyFormatter.setCurrency(AppCurrency.AUD, context)
        val audFormatted = CurrencyFormatter.formatCurrency(testAmount)
        assertTrue("AUD must contain A$: $audFormatted", audFormatted.contains("A$") || audFormatted.contains("$"))

        // 9. CAD (2 decimals)
        CurrencyFormatter.setCurrency(AppCurrency.CAD, context)
        val cadFormatted = CurrencyFormatter.formatCurrency(testAmount)
        assertTrue("CAD must contain C$: $cadFormatted", cadFormatted.contains("C$") || cadFormatted.contains("$"))

        // 10. INR (2 decimals)
        CurrencyFormatter.setCurrency(AppCurrency.INR, context)
        val inrFormatted = CurrencyFormatter.formatCurrency(testAmount)
        assertTrue("INR must contain ₹: $inrFormatted", inrFormatted.contains("₹") || inrFormatted.contains("Rs"))
    }

    // ==========================================
    // 2. Strict Zero Fake Conversion Rule
    // ==========================================

    @Test
    fun testCurrencyFormatter_ZeroFakeConversion() {
        val amount = 500.0

        CurrencyFormatter.setCurrency(AppCurrency.USD, context)
        val usdStr = CurrencyFormatter.formatCurrency(amount)

        CurrencyFormatter.setCurrency(AppCurrency.EUR, context)
        val eurStr = CurrencyFormatter.formatCurrency(amount)

        // Numbers must be 500 in both, not multiplied by 16000 or anything
        assertTrue("USD formatted must contain 500: $usdStr", usdStr.contains("500"))
        assertTrue("EUR formatted must contain 500: $eurStr", eurStr.contains("500"))
    }

    // ==========================================
    // 3. CurrencyMode FOLLOW_DEVICE vs MANUAL
    // ==========================================

    @Test
    fun testCurrencyMode_FollowDeviceAndManual() {
        CurrencyFormatter.setMode(context, CurrencyMode.FOLLOW_DEVICE)
        assertEquals(CurrencyMode.FOLLOW_DEVICE, CurrencyFormatter.currencyMode.value)

        CurrencyFormatter.setCurrency(AppCurrency.USD, context)
        assertEquals(CurrencyMode.MANUAL, CurrencyFormatter.currencyMode.value)
        assertEquals(AppCurrency.USD, CurrencyFormatter.selectedCurrency.value)
    }

    // ==========================================
    // 4. Synchronous LocaleHelper Persistence
    // ==========================================

    @Test
    fun testLocaleHelper_Persistence() {
        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)
        assertTrue(LocaleHelper.isEnglish())
        assertEquals(AppLanguage.ENGLISH, LocaleHelper.currentLanguage.value)

        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        assertFalse(LocaleHelper.isEnglish())
        assertEquals(AppLanguage.INDONESIAN, LocaleHelper.currentLanguage.value)
    }

    // ==========================================
    // 5. Bilingual AppStrings Verification
    // ==========================================

    @Test
    fun testBilingualStrings_NewFeatureStrings() {
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        assertTrue(AppStrings.commandCatalogTitle.isNotBlank())
        assertTrue(AppStrings.confirmCreateWallet.isNotBlank())
        assertTrue(AppStrings.financialHealthAnalytics.isNotBlank())

        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)
        assertTrue(AppStrings.commandCatalogTitle.isNotBlank())
        assertTrue(AppStrings.confirmCreateWallet.isNotBlank())
        assertTrue(AppStrings.financialHealthAnalytics.isNotBlank())
        assertEquals("Smart Command Catalog", AppStrings.commandCatalogTitle)
    }
}
