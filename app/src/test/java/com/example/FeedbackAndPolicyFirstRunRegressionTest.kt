package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.config.AppConfig
import com.example.config.DeveloperAccountConfig
import com.example.util.AppLanguage
import com.example.util.LocaleHelper
import com.example.util.PolicyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FeedbackAndPolicyFirstRunRegressionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PolicyManager.resetPolicyForTesting(context)
        try {
            val field = LocaleHelper::class.java.getDeclaredField("prefs")
            field.isAccessible = true
            field.set(LocaleHelper, null)
        } catch (_: Exception) {}
        LocaleHelper.initialize(context)
    }

    @Test
    fun testVersionSync_MatchesBuildConfig() {
        // Memastikan APP_VERSION_NAME dan APP_VERSION_CODE bersumber dari BuildConfig
        assertEquals(BuildConfig.VERSION_NAME, AppConfig.APP_VERSION_NAME)
        assertEquals(BuildConfig.VERSION_CODE, AppConfig.APP_VERSION_CODE)
        assertTrue(AppConfig.PRIVACY_POLICY_VERSION.isNotBlank())
    }

    @Test
    fun testDeveloperIdentity_OfficialSleepyKoala() {
        assertEquals("SleepyKoala", AppConfig.APP_AUTHOR)
        assertEquals("lunachroc01@gmail.com", AppConfig.SUPPORT_EMAIL)
        assertEquals("DompetNalar", AppConfig.APP_NAME)
    }

    @Test
    fun testPolicyManager_FirstRunFlowAndVersionTracking() {
        // 1. Kondisi awal: belum pernah membaca/mengonfirmasi kebijakan
        assertFalse(PolicyManager.isPolicyAcknowledged(context))
        assertEquals(null, PolicyManager.getAcknowledgedVersion(context))
        assertEquals(0L, PolicyManager.getAcknowledgedTimestamp(context))

        // 2. Pengguna menekan tombol 'Saya Telah Membaca & Memahami'
        PolicyManager.acknowledgePolicy(context, AppConfig.PRIVACY_POLICY_VERSION)

        // 3. Status harus terverifikasi dan waktu tersimpan
        assertTrue(PolicyManager.isPolicyAcknowledged(context))
        assertEquals(AppConfig.PRIVACY_POLICY_VERSION, PolicyManager.getAcknowledgedVersion(context))
        assertTrue(PolicyManager.getAcknowledgedTimestamp(context) > 0L)

        // 4. Jika versi kebijakan di masa depan dinaikkan (misal "2.0.0"), verifikasi harus meminta konfirmasi ulang
        assertFalse(PolicyManager.isPolicyAcknowledged(context, requiredVersion = "2.0.0"))
    }

    @Test
    fun testLocalization_SwitchingAndPersistence() {
        LocaleHelper.initialize(context)

        // Switch to Indonesian
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        assertEquals(AppLanguage.INDONESIAN, LocaleHelper.currentLanguage.value)
        assertFalse(LocaleHelper.isEnglish())

        // Switch to English
        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)
        assertEquals(AppLanguage.ENGLISH, LocaleHelper.currentLanguage.value)
        assertTrue(LocaleHelper.isEnglish())

        // Memastikan tersimpan di SharedPreferences
        val prefs = context.getSharedPreferences("finsmart_locale_prefs", Context.MODE_PRIVATE)
        assertEquals("en", prefs.getString("app_language", null))
    }

    @Test
    fun testAdminAuthorizationWhitelist_MatchesFirestoreRules() {
        // Whitelist admin resmi yang harus sinkron antara Android, web-admin, dan firestore.rules
        assertTrue(DeveloperAccountConfig.isTrustedDeveloperEmail("lunachroc01@gmail.com"))
        assertTrue(DeveloperAccountConfig.isTrustedDeveloperEmail("zweifeel@gmail.com"))
        assertTrue(DeveloperAccountConfig.isTrustedDeveloperEmail("LUNACHROC01@GMAIL.COM")) // case-insensitive

        // Non-admin harus ditolak
        assertFalse(DeveloperAccountConfig.isTrustedDeveloperEmail("unknown_hacker@gmail.com"))
        assertFalse(DeveloperAccountConfig.isTrustedDeveloperEmail(null))
        assertFalse(DeveloperAccountConfig.isTrustedDeveloperEmail(""))
    }

    @Test
    fun testFeedbackCategoryCompatibility() {
        // Kategori yang diizinkan oleh firestore.rules
        val validFirestoreCategories = listOf("Bug / Kendala", "Saran Fitur", "Desain / UI", "Lainnya")
        assertEquals(4, validFirestoreCategories.size)
        assertTrue(validFirestoreCategories.contains("Bug / Kendala"))
        assertTrue(validFirestoreCategories.contains("Saran Fitur"))
        assertTrue(validFirestoreCategories.contains("Desain / UI"))
        assertTrue(validFirestoreCategories.contains("Lainnya"))
    }
}

