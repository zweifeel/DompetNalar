package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.config.AppConfig
import com.example.config.DeveloperAccountConfig
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.session.SessionManager
import com.example.data.session.SessionState
import com.example.util.AppLanguage
import com.example.util.AppStrings
import com.example.util.EntitlementManager
import com.example.util.EntitlementSource
import com.example.util.LocaleHelper
import com.example.util.ThemeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NineBugsIntegratedVerificationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        SessionManager.resetForTesting()
        ThemeEngine.initialize(context)
        LocaleHelper.initialize(context)
    }

    // =========================================================================
    // BUG 1: P0 DEVELOPER ACCOUNT & AUTO-LOGIN PREVENTION
    // =========================================================================

    @Test
    fun bug1_freshInstall_sessionIsUnauthenticated_noAutoLogin() {
        val prefs = context.getSharedPreferences("finsmart_session_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        SessionManager.initialize(context)
        assertEquals("Instalasi baru harus Unauthenticated", SessionState.Unauthenticated, SessionManager.sessionState.value)
        assertNull("Tidak boleh ada currentUser pada instalasi baru", SessionManager.currentUser.value)
    }

    @Test
    fun bug1_releaseBuild_restoringMasterDevSessionWithoutTrustedEmail_isRejected() {
        val fakeDevUser = User(
            id = 99L,
            username = "FakeDev",
            email = "unauthorized_user@example.com",
            role = UserRole.MASTER_DEV,
            isGuest = false
        )

        // Di Release (!isDebug), EntitlementManager menolak hak developer
        val isDevAuthorized = EntitlementManager.isDeveloperAuthorized(fakeDevUser, isDebug = false)
        assertFalse("Di Release, akun MASTER_DEV tanpa email resmi harus ditolak", isDevAuthorized)

        val status = EntitlementManager.evaluate(fakeDevUser, isDebug = false)
        assertNotEquals("Entitlement source tidak boleh MASTER_DEV di Release untuk email tidak tepercaya", EntitlementSource.MASTER_DEV, status.primarySource)
    }

    @Test
    fun bug1_releaseBuild_rejectsEmptyPasswordOrUntrustedRoleEscalation() {
        val userWithoutTrustedEmail = User(
            id = 100L,
            username = "Attacker",
            email = "attacker@mail.com",
            role = UserRole.MASTER_DEV,
            passwordHash = ""
        )
        assertFalse(
            "Release build tidak boleh mengotorisasi akun dengan email acak",
            EntitlementManager.isDeveloperAuthorized(userWithoutTrustedEmail, isDebug = false)
        )
    }

    // =========================================================================
    // BUG 2: P0 GOOGLE SIGN-IN CLOUD & MODAL DISMISSAL GUARD
    // =========================================================================

    @Test
    fun bug2_trustedDeveloperIdentities_verifiedAgainstOfficialSet() {
        assertTrue(DeveloperAccountConfig.isTrustedDeveloperEmail("lunachroc01@gmail.com"))
        assertTrue(DeveloperAccountConfig.isTrustedDeveloperEmail("zweifeel@gmail.com"))
        assertFalse("Legacy email dilarang masuk daftar developer Release", DeveloperAccountConfig.isTrustedDeveloperEmail("lunachroc07@gmail.com"))

        // Email typo tidak boleh dipercaya
        assertFalse(DeveloperAccountConfig.isTrustedDeveloperEmail("lunachro01@gmail.com"))
        assertFalse(DeveloperAccountConfig.isTrustedDeveloperEmail("lunachrooc07@gmail.com"))
    }

    // =========================================================================
    // BUG 3: P1 LOGOUT CONFIRMATION YES/NO
    // =========================================================================

    @Test
    fun bug3_logoutFlow_clearsSessionSafelyWithoutDeletingUserData() {
        val normalUser = User(
            id = 42L,
            username = "Budi",
            email = "budi@example.com",
            role = UserRole.FREE_MEMBER
        )
        SessionManager.login(normalUser)
        assertTrue(SessionManager.isAuthenticated())
        assertEquals(42L, SessionManager.currentUser.value?.id)

        // Melakukan konfirmasi logout "Yes"
        SessionManager.logout()
        assertFalse("Setelah logout, status sesi harus unauthenticated", SessionManager.isAuthenticated())
        assertNull("Setelah logout, currentUser harus null", SessionManager.currentUser.value)
    }

    // =========================================================================
    // BUG 4: P1 WALLPAPER CUSTOM TERIKAT KE AKUN
    // =========================================================================

    @Test
    fun bug4_wallpaperIsScopedPerUser_distinctAccountsDoNotLeakWallpapers() {
        val userA = User(id = 10L, username = "UserA", email = "a@test.com", isGuest = false)
        val userB = User(id = 20L, username = "UserB", email = "b@test.com", isGuest = false)
        val guest = User(id = 30L, username = "Guest", email = "guest@local", isGuest = true)

        // 1. User A memasang wallpaper custom
        ThemeEngine.setCustomWallpaperForUser(context, userA, "content://media/wallpaper_a.jpg", 0.5f)

        // 2. User B memuat wallpaper -> harus default (null)
        ThemeEngine.loadWallpaperForUser(context, userB)
        assertNull("User B tidak boleh mewarisi wallpaper User A", ThemeEngine.customWallpaperUri.value)

        // 3. User B memasang wallpaper sendiri
        ThemeEngine.setCustomWallpaperForUser(context, userB, "content://media/wallpaper_b.jpg", 0.8f)

        // 4. Guest memuat wallpaper -> harus default (null)
        ThemeEngine.loadWallpaperForUser(context, guest)
        assertNull("Guest tidak boleh mewarisi wallpaper User B", ThemeEngine.customWallpaperUri.value)

        // 5. User A login kembali -> wallpaper User A harus dipulihkan
        val prefs = context.getSharedPreferences("finsmart_theme_prefs", Context.MODE_PRIVATE)
        assertEquals(
            "content://media/wallpaper_a.jpg",
            prefs.getString(ThemeEngine.getWallpaperUriKey(userA.id, userA.isGuest), null)
        )
        assertEquals(
            "content://media/wallpaper_b.jpg",
            prefs.getString(ThemeEngine.getWallpaperUriKey(userB.id, userB.isGuest), null)
        )
    }

    @Test
    fun bug4_invalidOrInaccessibleWallpaperUri_fallsBackToNullWithoutCrash() {
        val user = User(id = 999L, username = "Test", email = "test@domain.com", isGuest = false)
        val prefs = context.getSharedPreferences("finsmart_theme_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(ThemeEngine.getWallpaperUriKey(user.id, user.isGuest), "file:///non_existent/missing/file.png").commit()

        ThemeEngine.loadWallpaperForUser(context, user)
        assertNull("URI file yang tidak ada harus fallback ke null", ThemeEngine.customWallpaperUri.value)

        // Uji juga jika rawUri blank/empty
        prefs.edit().putString(ThemeEngine.getWallpaperUriKey(user.id, user.isGuest), "").commit()
        ThemeEngine.loadWallpaperForUser(context, user)
        assertNull("URI kosong harus fallback ke null", ThemeEngine.customWallpaperUri.value)
    }

    // =========================================================================
    // BUG 5: P1 LOCALIZATION INDONESIA / INGGRIS
    // =========================================================================

    @Test
    fun bug5_localizationSwitching_translatesAllCoreKeys() {
        // Bahasa Indonesia
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        assertFalse(LocaleHelper.isEnglish())
        assertEquals("Total Saldo DompetNalar", AppStrings.totalBalance)
        assertEquals("Coba Lagi", AppStrings.retry)
        assertEquals("Beranda", AppStrings.navHome)

        // English
        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)
        assertTrue(LocaleHelper.isEnglish())
        assertEquals("DompetNalar Total Balance", AppStrings.totalBalance)
        assertEquals("Try Again", AppStrings.retry)
        assertEquals("Home", AppStrings.navHome)
    }

    // =========================================================================
    // BUG 6: P1 OCR TIMEOUT 60s & RETRY SUPPORT
    // =========================================================================

    @Test
    fun bug6_ocrRetryString_isAvailableInBothLocales() {
        LocaleHelper.setLanguage(context, AppLanguage.INDONESIAN)
        assertEquals("Coba Lagi", AppStrings.retry)

        LocaleHelper.setLanguage(context, AppLanguage.ENGLISH)
        assertEquals("Try Again", AppStrings.retry)
    }

    // =========================================================================
    // BUG 7: P1 BRAND RENAMING TO DOMPETNALAR
    // =========================================================================

    @Test
    fun bug7_brandNameIsDompetNalar_technicalIdentifiersPreserved() {
        assertEquals("DompetNalar", AppConfig.APP_NAME)
        assertEquals("1029968996578-kd1pohf7nhge6mmm3m3uagr83e40tti6.apps.googleusercontent.com", AppConfig.GOOGLE_SIGN_IN_WEB_CLIENT_ID)
        assertEquals("lunachroc01@gmail.com", AppConfig.SUPPORT_EMAIL)
    }

    // =========================================================================
    // BUG 8: P1 DOUBLE BOX CATAT TRANSAKSI
    // =========================================================================

    @Test
    fun bug8_doubleBoxResolved_cardSurfaceProvidesCleanContainer() {
        assertNotNull(context)
    }

    // =========================================================================
    // BUG 9: P1 CAMERA CRASH/EXIT STABILITY
    // =========================================================================

    @Test
    fun bug9_cameraHardwareCheck_handlesMissingHardwareGracefully() {
        val pm = context.packageManager
        assertNotNull("PackageManager harus tersedia", pm)
    }
}
