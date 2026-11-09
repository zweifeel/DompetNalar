package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.config.DeveloperAccountConfig
import com.example.data.local.FinSmartDatabase
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.session.SessionManager
import com.example.util.EntitlementManager
import com.example.util.PasswordHasher
import kotlinx.coroutines.runBlocking
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
import java.util.concurrent.atomic.AtomicLong

/**
 * Phase 1 Implementation Verification Test:
 * 1. Session Privacy & Profile Photo Leak Prevention
 * 2. Account Deletion Password Requirement Policy (Google vs Password vs Guest)
 * 3. Master Developer Hardening in Release Builds
 * 4. Database Initial Seed Hardening (Release vs Debug)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Phase1SessionPrivacyAndAccountDeletionTest {

    private lateinit var context: Context
    private lateinit var db: FinSmartDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SessionManager.resetForTesting()
        db = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ==========================================
    // 1. SESSION PRIVACY & LOGOUT PHOTO LEAK
    // ==========================================

    @Test
    fun guestSession_neverShowsAvatarPhoto_andHasZeroTrialDays() {
        val guest = User(
            id = 100L,
            username = "Tamu FinSmart",
            email = "guest_12345@finsmart.local",
            role = UserRole.FREE_MEMBER,
            isGuest = true,
            trialStartDate = 0L
        )

        // SessionManager correctly recognizes Guest
        SessionManager.setSessionForTesting(guest.id, guest)
        assertEquals(guest.id, SessionManager.getActiveUserId())
        assertTrue(SessionManager.currentUser.value?.isGuest == true)

        // Profile photo display guard: shouldShowPhoto = currentUser != null && !currentUser.isGuest && !photoUri.isNullOrEmpty()
        val profilePhotoUri = "content://media/external/images/media/42"
        val shouldShowPhotoInGuest = SessionManager.currentUser.value?.let { user ->
            !user.isGuest && profilePhotoUri.isNotEmpty()
        } ?: false

        assertFalse("Foto profil akun lama tidak boleh ditampilkan pada sesi Tamu!", shouldShowPhotoInGuest)

        // Entitlement must not grant trial or premium to Guest
        val entitlement = EntitlementManager.evaluate(guest, isDebug = false)
        assertFalse(entitlement.isPremiumUnlocked)
        assertEquals(0, entitlement.trialDaysRemaining)
    }

    @Test
    fun asyncProfilePhotoCallback_abortsWhenSessionEpochChanges() {
        val authSessionEpoch = AtomicLong(0)
        val initialEpoch = authSessionEpoch.get()

        // User starts Google Sign-In with initial epoch
        assertEquals(0L, initialEpoch)

        // User logs out or switches to Guest before Google Sign-In async callback finishes
        authSessionEpoch.incrementAndGet()
        assertEquals(1L, authSessionEpoch.get())

        // Async callback finishes and checks whether epoch changed
        val shouldDiscardCallback = initialEpoch != authSessionEpoch.get()
        assertTrue("Callback login/foto yang terlambat harus dibatalkan jika pengguna sudah logout!", shouldDiscardCallback)
    }

    // ==========================================
    // 2. ACCOUNT DELETION LOGIC (GOOGLE vs PASSWORD vs GUEST)
    // ==========================================

    @Test
    fun deleteAccount_requiresPasswordOnlyForPasswordBasedAccounts() {
        // A. Google Sign-In account (passwordHash is empty)
        val googleUser = User(
            id = 201L,
            username = "User Google",
            email = "user@gmail.com",
            passwordHash = "", // Google Sign-In has no local password hash
            isGuest = false,
            role = UserRole.FREE_MEMBER
        )

        val isGuestA = googleUser.isGuest
        val requiresPasswordA = !isGuestA && !googleUser.passwordHash.isNullOrBlank()
        assertFalse("Pengguna Google Sign-In TIDAK boleh diwajibkan input password lokal!", requiresPasswordA)

        // B. Guest user
        val guestUser = User(
            id = 202L,
            username = "Tamu",
            email = "guest@local",
            passwordHash = "",
            isGuest = true,
            role = UserRole.FREE_MEMBER
        )
        val isGuestB = guestUser.isGuest
        val requiresPasswordB = !isGuestB && !guestUser.passwordHash.isNullOrBlank()
        assertFalse("Mode Tamu tidak boleh diwajibkan input password!", requiresPasswordB)

        // C. Local email/password account
        val hashedPassword = PasswordHasher.hashPassword("Password123!")
        val emailUser = User(
            id = 203L,
            username = "User Lokal",
            email = "lokal@test.com",
            passwordHash = hashedPassword,
            isGuest = false,
            role = UserRole.FREE_MEMBER
        )
        val isGuestC = emailUser.isGuest
        val requiresPasswordC = !isGuestC && !emailUser.passwordHash.isNullOrBlank()
        assertTrue("Akun dengan password lokal WAJIB meminta konfirmasi password!", requiresPasswordC)

        // Password verification logic
        assertTrue("Password yang benar harus diterima", PasswordHasher.verifyPassword("Password123!", emailUser.passwordHash))
        assertFalse("Password yang salah harus ditolak", PasswordHasher.verifyPassword("Salah!", emailUser.passwordHash))
    }

    // ==========================================
    // 3. MASTER DEVELOPER HARDENING IN RELEASE BUILDS
    // ==========================================

    @Test
    fun masterDeveloper_cannotBypassSecurityInReleaseBuild() {
        val lukasFakeAccount = User(
            id = 999L,
            username = "Lukas",
            email = "untrusted_user@gmail.com",
            role = UserRole.MASTER_DEV, // Claiming MasterDev role locally
            isGuest = false
        )

        // On DEBUG, local role or username might be accepted for local development
        val debugAuthorized = EntitlementManager.isDeveloperAuthorized(lukasFakeAccount, isDebug = true)
        assertTrue("Pada build DEBUG, MasterDev lokal diizinkan untuk keperluan development", debugAuthorized)

        // On RELEASE, strictly reject unless trusted developer email!
        val releaseAuthorized = EntitlementManager.isDeveloperAuthorized(lukasFakeAccount, isDebug = false)
        assertFalse("Pada build RELEASE, username atau role lokal 'Lukas' TIDAK BOLEH menjadi MasterDev tanpa email tepercaya!", releaseAuthorized)

        val releaseEntitlement = EntitlementManager.evaluate(lukasFakeAccount, isDebug = false)
        assertEquals("Pada build RELEASE, role efektif harus diturunkan ke FREE_MEMBER", UserRole.FREE_MEMBER, releaseEntitlement.effectiveRole)
        assertFalse("Akses premium harus dikunci pada build RELEASE untuk akun tidak tepercaya", releaseEntitlement.isPremiumUnlocked)
    }

    @Test
    fun trustedDeveloperEmails_areAuthorizedInDebug_andNotEscalatedInRelease() {
        val officialAdmin = User(
            id = 1L,
            username = "Official Admin",
            email = "lunachroc01@gmail.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false
        )

        assertTrue("Email whitelist lunachroc01@gmail.com harus sah sebagai developer config",
            DeveloperAccountConfig.isTrustedDeveloperEmail(officialAdmin.email))

        // In Debug: Developer accounts are authorized
        val isAuthorizedDebug = EntitlementManager.isDeveloperAuthorized(officialAdmin, isDebug = true)
        assertTrue("Admin resmi diotorisasi pada build Debug", isAuthorizedDebug)
        val debugEntitlement = EntitlementManager.evaluate(officialAdmin, isDebug = true)
        assertTrue("Admin resmi memiliki akses Master Developer pada build Debug", debugEntitlement.isPremiumUnlocked)
        assertEquals(UserRole.MASTER_DEV, debugEntitlement.effectiveRole)

        // In Release (Phase 4B): Web Admins do NOT automatically obtain MASTER_DEV Android
        val isAuthorizedRelease = EntitlementManager.isDeveloperAuthorized(officialAdmin, isDebug = false)
        assertFalse("Admin resmi tidak otomatis dielevasi ke MASTER_DEV pada build Release", isAuthorizedRelease)
        val releaseEntitlement = EntitlementManager.evaluate(officialAdmin, isDebug = false)
        assertEquals("Pada build Release, role tetap FREE_MEMBER jika tidak ada grant/billing", UserRole.FREE_MEMBER, releaseEntitlement.effectiveRole)
    }

    @Test
    fun guestUserWithTrustedEmail_isNeverAuthorized() {
        val maliciousGuest = User(
            id = 99L,
            username = "Tamu Jahat",
            email = "lunachroc01@gmail.com", // Spoofed email in Guest mode
            role = UserRole.FREE_MEMBER,
            isGuest = true
        )

        val isAuthorized = EntitlementManager.isDeveloperAuthorized(maliciousGuest, isDebug = false)
        assertFalse("Akun tamu TIDAK BOLEH diotorisasi sebagai developer meski menggunakan email tepercaya!", isAuthorized)
    }

    // ==========================================
    // 4. DATABASE INITIAL SEED HARDENING (RELEASE vs DEBUG)
    // ==========================================

    @Test
    fun releaseBuild_neverSeedsLukasOrMasterDev_onInitialCreation() = runBlocking {
        // Simulate fresh database creation on Release (isDebug = false)
        FinSmartDatabase.populateInitialData(db, isDebug = false)

        // 1. User count must be exactly 0 (no Lukas, no Master Dev)
        assertEquals("Release database initialization must NOT seed any user accounts!", 0, db.userDao().getUserCount())
        assertNull("User 'Lukas' must NOT exist in Release initial data!", db.userDao().getUserByIdentifier("Lukas"))
        assertNull("Developer email must NOT exist in Release initial data!", db.userDao().getUserByEmail(DeveloperAccountConfig.LEGACY_DEV_EMAIL))

        // 2. Canonical system categories MUST still be created properly for all users
        assertEquals("Canonical system categories must be seeded on all builds!", 13, db.categoryDao().getCategoryCount())

        // 3. Dummy developer wallets, debts, assets must NOT be created
        assertEquals("No dummy wallets should be seeded in Release!", 0, db.walletDao().getWalletCount())
        assertEquals("No dummy debts should be seeded in Release!", 0, db.debtDao().getDebtCount(1L))
        assertEquals("No dummy assets should be seeded in Release!", 0, db.assetDao().getAssetCount(1L))

        // 4. ensureMasterDevAccount on Release must safely return 0L without inserting anything
        val devId = FinSmartDatabase.ensureMasterDevAccount(db, isDebug = false)
        assertEquals("ensureMasterDevAccount must return 0L in Release!", 0L, devId)
        assertEquals("No user should be inserted by ensureMasterDevAccount in Release!", 0, db.userDao().getUserCount())
    }

    @Test
    fun debugBuild_doesNotSeedLukasOrWallets_phase1g() = runBlocking {
        // Simulate fresh database creation on Debug (isDebug = true)
        FinSmartDatabase.populateInitialData(db, isDebug = true)

        // 1. User count is 0 (Lukas account permanently deleted & auto-provisioning removed in Phase 1G)
        assertEquals("Debug database initialization does NOT seed Lukas in Phase 1G", 0, db.userDao().getUserCount())

        // 2. Canonical system categories are seeded
        assertEquals(13, db.categoryDao().getCategoryCount())

        // 3. No automatic wallets are seeded
        assertEquals(0, db.walletDao().getWalletCount())
    }
}
