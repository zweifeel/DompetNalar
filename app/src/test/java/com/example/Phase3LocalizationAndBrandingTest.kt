package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.config.AppConfig
import com.example.data.model.AppIconEdition
import com.example.util.AppAccentColor
import com.example.util.AppCurrency
import com.example.util.AppLanguage
import com.example.util.AppStrings
import com.example.util.AppThemeMode
import com.example.util.CurrencyFormatter
import com.example.util.CurrencyMode
import com.example.util.LocaleHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase3LocalizationAndBrandingTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        LocaleHelper.initialize(context)
        CurrencyFormatter.initialize(context)
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
    }

    @After
    fun tearDown() {
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        CurrencyFormatter.setCurrency(AppCurrency.IDR, context)
        CurrencyFormatter.setMode(context, CurrencyMode.FOLLOW_DEVICE)
    }

    @Test
    fun test01_commonActionsAndButtonsBilingual() {
        // Mode Indonesian
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        assertEquals("Tutup", AppStrings.close)
        assertEquals("Selesai", AppStrings.done)
        assertEquals("Sebelumnya", AppStrings.previous)
        assertEquals("Selanjutnya", AppStrings.next)
        assertEquals("Coba Lagi", AppStrings.retry)
        assertEquals("Batal", AppStrings.cancel)
        assertEquals("Simpan", AppStrings.save)

        // Mode English
        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)
        assertEquals("Close", AppStrings.close)
        assertEquals("Done", AppStrings.done)
        assertEquals("Previous", AppStrings.previous)
        assertEquals("Next", AppStrings.next)
        assertEquals("Try Again", AppStrings.retry)
        assertEquals("Cancel", AppStrings.cancel)
        assertEquals("Save", AppStrings.save)
    }

    @Test
    fun test02_themeModeAndAccentColorBilingual() {
        // Mode Indonesian
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        assertEquals("Ikuti Sistem HP", AppThemeMode.SYSTEM.localizedTitle)
        assertEquals("Mode Terang", AppThemeMode.LIGHT.localizedTitle)
        assertEquals("Mode Gelap", AppThemeMode.DARK.localizedTitle)
        assertEquals("Ocean Blue (Bawaan)", AppAccentColor.OCEAN_BLUE.localizedDisplayName)

        // Mode English
        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)
        assertEquals("Follow System", AppThemeMode.SYSTEM.localizedTitle)
        assertEquals("Light Mode", AppThemeMode.LIGHT.localizedTitle)
        assertEquals("Dark Mode", AppThemeMode.DARK.localizedTitle)
        assertEquals("Ocean Blue (Default)", AppAccentColor.OCEAN_BLUE.localizedDisplayName)
    }

    @Test
    fun test03_appIconEditionBilingual() {
        // Mode Indonesian
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        assertTrue(AppIconEdition.DEFAULT_CLASSIC.localizedDisplayName.contains("Bawaan"))
        assertTrue(AppIconEdition.GOLD_LUXURY.localizedDisplayName.contains("Emas Mewah"))
        assertTrue(AppIconEdition.DARK_OBSIDIAN.localizedDisplayName.contains("Obsidian Gelap"))

        // Mode English
        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)
        assertEquals("Default Classic", AppIconEdition.DEFAULT_CLASSIC.localizedDisplayName)
        assertTrue(AppIconEdition.GOLD_LUXURY.localizedDisplayName.contains("Gold Luxury"))
        assertTrue(AppIconEdition.DARK_OBSIDIAN.localizedDisplayName.contains("Dark Obsidian"))
    }

    @Test
    fun test04_shakeGestureDescriptionsBilingual() {
        // Mode Indonesian
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        val idTitle = AppStrings.t("Aksi Goyang HP (Shake Gesture)", "Shake Phone Action (Shake Gesture)")
        val idOffDesc = AppStrings.t("Sensor mati, tidak ada aksi goyangan (Bawaan)", "Sensor off, no shake action (Default)")
        val idTxDesc = AppStrings.t("Buka lembar input transaksi baru", "Open new transaction input sheet")
        val idStealthDesc = AppStrings.t("Samarkan antarmuka seketika ke mode stealth", "Immediately camouflage interface into stealth mode")

        assertEquals("Aksi Goyang HP (Shake Gesture)", idTitle)
        assertTrue(idOffDesc.contains("Bawaan"))
        assertTrue(idTxDesc.contains("transaksi"))
        assertTrue(idStealthDesc.contains("stealth"))

        // Mode English
        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)
        val enTitle = AppStrings.t("Aksi Goyang HP (Shake Gesture)", "Shake Phone Action (Shake Gesture)")
        val enOffDesc = AppStrings.t("Sensor mati, tidak ada aksi goyangan (Bawaan)", "Sensor off, no shake action (Default)")
        val enTxDesc = AppStrings.t("Buka lembar input transaksi baru", "Open new transaction input sheet")
        val enStealthDesc = AppStrings.t("Samarkan antarmuka seketika ke mode stealth", "Immediately camouflage interface into stealth mode")

        assertEquals("Shake Phone Action (Shake Gesture)", enTitle)
        assertTrue(enOffDesc.contains("Default"))
        assertTrue(enTxDesc.contains("transaction"))
        assertTrue(enStealthDesc.contains("Immediately camouflage"))
    }

    @Test
    fun test05_cloudBackupAndSyncBilingual() {
        // Mode Indonesian
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        val idSync = AppStrings.t("Google Drive & Sinkronisasi Cloud", "Google Drive & Cloud Sync")
        val idInterval = AppStrings.t("Interval Unggah:", "Upload Interval:")
        val idDisconnect = AppStrings.t("Putuskan", "Disconnect")
        val idHour3 = if (LocaleHelper.isEnglish()) "3h" else "3j"

        assertEquals("Google Drive & Sinkronisasi Cloud", idSync)
        assertEquals("Interval Unggah:", idInterval)
        assertEquals("Putuskan", idDisconnect)
        assertEquals("3j", idHour3)

        // Mode English
        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)
        val enSync = AppStrings.t("Google Drive & Sinkronisasi Cloud", "Google Drive & Cloud Sync")
        val enInterval = AppStrings.t("Interval Unggah:", "Upload Interval:")
        val enDisconnect = AppStrings.t("Putuskan", "Disconnect")
        val enHour3 = if (LocaleHelper.isEnglish()) "3h" else "3j"

        assertEquals("Google Drive & Cloud Sync", enSync)
        assertEquals("Upload Interval:", enInterval)
        assertEquals("Disconnect", enDisconnect)
        assertEquals("3h", enHour3)
    }

    @Test
    fun test06_brandNameConsistencyDompetNalar() {
        assertEquals("DompetNalar", AppConfig.APP_NAME)
        assertTrue(AppStrings.totalBalance.contains("DompetNalar"))
        assertTrue(AppStrings.chatTitle.contains("DompetNalar"))
        assertFalse(AppConfig.APP_NAME.contains("FinSmart"))
    }
}
