package com.example

import com.example.config.DeveloperAccountConfig
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.util.EntitlementManager
import com.example.util.EntitlementSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 4 — Targeted Security Remediation & Regression Verification
 *
 * Tujuan:
 *  - Memastikan email alias typo TIDAK mendapat hak MASTER_DEV di Release.
 *  - Memastikan 3 email developer resmi MASIH berfungsi dengan benar.
 *  - Memastikan firestore.rules lokal TIDAK mengandung email typo di isAdmin().
 *  - Memastikan EntitlementManager.evaluate() konsisten dengan DeveloperAccountConfig setelah patch.
 */
class Phase4SecurityRemediationTest {

    private val baseTime = 1770000000000L

    // =========================================================================
    // P0: Verifikasi Email Typo DIHAPUS dari TRUSTED_DEVELOPER_EMAILS
    // =========================================================================

    @Test
    fun typoEmail_lunachro01_isNotInTrustedDeveloperSet() {
        // lunachro01@gmail.com — hilang huruf 'c' sebelum 'hroc' — TYPO, bukan resmi
        assertFalse(
            "lunachro01@gmail.com adalah typo dan TIDAK BOLEH ada di TRUSTED_DEVELOPER_EMAILS",
            DeveloperAccountConfig.isTrustedDeveloperEmail("lunachro01@gmail.com")
        )
    }

    @Test
    fun typoEmail_lunachrooc07_isNotInTrustedDeveloperSet() {
        // lunachrooc07@gmail.com — huruf 'o' ganda — TYPO, bukan resmi
        assertFalse(
            "lunachrooc07@gmail.com adalah typo dan TIDAK BOLEH ada di TRUSTED_DEVELOPER_EMAILS",
            DeveloperAccountConfig.isTrustedDeveloperEmail("lunachrooc07@gmail.com")
        )
    }

    @Test
    fun typoEmail_uppercased_isNotInTrustedDeveloperSet() {
        // Verifikasi case-insensitive check tidak meloloskan typo
        assertFalse(
            "LUNACHRO01@GMAIL.COM (uppercase typo) TIDAK BOLEH lolos whitelist",
            DeveloperAccountConfig.isTrustedDeveloperEmail("LUNACHRO01@GMAIL.COM")
        )
        assertFalse(
            "LUNACHROOC07@GMAIL.COM (uppercase typo) TIDAK BOLEH lolos whitelist",
            DeveloperAccountConfig.isTrustedDeveloperEmail("LUNACHROOC07@GMAIL.COM")
        )
    }

    @Test
    fun typoEmail_withWhitespace_isNotInTrustedDeveloperSet() {
        // Verifikasi trim tidak meloloskan typo dengan spasi
        assertFalse(
            "Typo email dengan whitespace TIDAK BOLEH lolos",
            DeveloperAccountConfig.isTrustedDeveloperEmail("  lunachro01@gmail.com  ")
        )
        assertFalse(
            "Typo email dengan whitespace TIDAK BOLEH lolos",
            DeveloperAccountConfig.isTrustedDeveloperEmail("  lunachrooc07@gmail.com  ")
        )
    }

    // =========================================================================
    // P0: Verifikasi 3 Email Developer Resmi MASIH Berfungsi (Regression Guard)
    // =========================================================================

    @Test
    fun officialEmail_lunachroc01_isInTrustedDeveloperSet() {
        assertTrue(
            "lunachroc01@gmail.com (email resmi Google Play Console) HARUS ada di whitelist",
            DeveloperAccountConfig.isTrustedDeveloperEmail("lunachroc01@gmail.com")
        )
    }

    @Test
    fun officialEmail_zweifeel_isInTrustedDeveloperSet() {
        assertTrue(
            "zweifeel@gmail.com (Firebase Project Owner) HARUS ada di whitelist",
            DeveloperAccountConfig.isTrustedDeveloperEmail("zweifeel@gmail.com")
        )
    }

    @Test
    fun officialEmail_lunachroc07_isExcludedFromTrustedDeveloperSet() {
        assertFalse(
            "lunachroc07@gmail.com (legacy dev email) HARUS DIKELUARKAN dari whitelist pada Phase 4B",
            DeveloperAccountConfig.isTrustedDeveloperEmail("lunachroc07@gmail.com")
        )
    }

    @Test
    fun trustedDeveloperSetContainsExactlyTwoOfficialAdminEmails() {
        // Memastikan hanya 2 email resmi admin yang ada di whitelist
        assertEquals(
            "TRUSTED_DEVELOPER_EMAILS harus berisi tepat 2 email resmi admin setelah Phase 4B",
            2,
            DeveloperAccountConfig.TRUSTED_DEVELOPER_EMAILS.size
        )
    }

    @Test
    fun officialEmails_caseInsensitive_areRecognized() {
        // Case-insensitive check tetap berfungsi untuk email resmi admin
        assertTrue(DeveloperAccountConfig.isTrustedDeveloperEmail("LUNACHROC01@GMAIL.COM"))
        assertTrue(DeveloperAccountConfig.isTrustedDeveloperEmail("ZWEIFEEL@GMAIL.COM"))
        assertFalse(DeveloperAccountConfig.isTrustedDeveloperEmail("LUNACHROC07@GMAIL.COM"))
    }

    // =========================================================================
    // P0: Verifikasi Konstanta Typo Tersimpan sebagai Dokumentasi (bukan whitelist)
    // =========================================================================

    @Test
    fun typoConstant_CONTACT_DEV_EMAIL_ALIAS_TYPO_isOnlyDocumentation() {
        // Konstanta harus ada sebagai catatan historis
        assertEquals("lunachro01@gmail.com", DeveloperAccountConfig.CONTACT_DEV_EMAIL_ALIAS_TYPO)
        // Tetapi TIDAK masuk ke whitelist tepercaya
        assertFalse(DeveloperAccountConfig.isTrustedDeveloperEmail(DeveloperAccountConfig.CONTACT_DEV_EMAIL_ALIAS_TYPO))
    }

    @Test
    fun typoConstant_LEGACY_DEV_EMAIL_ALIAS_TYPO_isOnlyDocumentation() {
        assertEquals("lunachrooc07@gmail.com", DeveloperAccountConfig.LEGACY_DEV_EMAIL_ALIAS_TYPO)
        assertFalse(DeveloperAccountConfig.isTrustedDeveloperEmail(DeveloperAccountConfig.LEGACY_DEV_EMAIL_ALIAS_TYPO))
    }

    // =========================================================================
    // P0: EntitlementManager Tidak Memberikan MASTER_DEV untuk Email Typo
    // =========================================================================

    @Test
    fun entitlement_typoEmail_lunachro01_doesNotGetMasterDevRole() {
        val userWithTypo = User(
            id = 901L,
            username = "Impostor Typo",
            email = "lunachro01@gmail.com", // email typo (hilang 'c')
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            trialStartDate = 0L
        )
        val status = EntitlementManager.evaluate(userWithTypo, actionCredits = 0, now = baseTime)
        assertFalse(
            "lunachro01@gmail.com (typo) tidak boleh mendapat akses premium MASTER_DEV",
            status.isPremiumUnlocked
        )
        assertEquals(
            "Role yang dikembalikan harus FREE_MEMBER (bukan MASTER_DEV)",
            UserRole.FREE_MEMBER,
            status.effectiveRole
        )
        assertFalse(
            "Sumber entitlement TIDAK BOLEH MASTER_DEV untuk email typo",
            status.primarySource == EntitlementSource.MASTER_DEV
        )
    }

    @Test
    fun entitlement_typoEmail_lunachrooc07_doesNotGetMasterDevRole() {
        val userWithTypo = User(
            id = 902L,
            username = "Impostor Typo 2",
            email = "lunachrooc07@gmail.com", // email typo (huruf 'o' ganda)
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            trialStartDate = 0L
        )
        val status = EntitlementManager.evaluate(userWithTypo, actionCredits = 0, now = baseTime)
        assertFalse(
            "lunachrooc07@gmail.com (typo 'oo') tidak boleh mendapat akses premium MASTER_DEV",
            status.isPremiumUnlocked
        )
        assertEquals(
            "Role yang dikembalikan harus FREE_MEMBER (bukan MASTER_DEV)",
            UserRole.FREE_MEMBER,
            status.effectiveRole
        )
    }

    @Test
    fun entitlement_typoEmail_withSmartMasterRole_stillBlockedFromMasterDev() {
        // Skenario ekstrem: email typo dengan role=SMART_MASTER di DB — harus diperlakukan sebagai SMART_MASTER biasa
        val userWithTypoAndSM = User(
            id = 903L,
            username = "Tricky Typo",
            email = "lunachro01@gmail.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = baseTime + (30L * 86400000L)
        )
        val status = EntitlementManager.evaluate(userWithTypoAndSM, actionCredits = 0, now = baseTime)
        // Boleh aktif sebagai SMART_MASTER biasa (valid), tapi TIDAK sebagai MASTER_DEV
        assertFalse(
            "Email typo tidak boleh mendapat sumber entitlement MASTER_DEV meskipun role=SMART_MASTER",
            status.primarySource == EntitlementSource.MASTER_DEV
        )
        assertFalse(
            "Role yang dikembalikan tidak boleh MASTER_DEV",
            status.effectiveRole == UserRole.MASTER_DEV
        )
    }

    // =========================================================================
    // P0: EntitlementManager Tetap Berfungsi untuk Email Resmi (Regression Guard)
    // =========================================================================

    @Test
    fun entitlement_officialDevEmail_lunachroc01_getsMasterDevPermanently() {
        val devUser = User(
            id = 1L,
            username = "Developer Resmi",
            email = "lunachroc01@gmail.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false
        )
        val status = EntitlementManager.evaluate(devUser, actionCredits = 0, now = baseTime)
        assertTrue("lunachroc01@gmail.com harus mendapat akses MASTER_DEV", status.isPremiumUnlocked)
        assertEquals(UserRole.MASTER_DEV, status.effectiveRole)
        assertEquals(EntitlementSource.MASTER_DEV, status.primarySource)
        assertFalse(status.isExpired)
        assertEquals(999, status.trialDaysRemaining)
    }

    @Test
    fun entitlement_officialDevEmail_zweifeel_getsMasterDevPermanently() {
        val devUser = User(
            id = 2L,
            username = "Firebase Owner",
            email = "zweifeel@gmail.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false
        )
        val status = EntitlementManager.evaluate(devUser, actionCredits = 0, now = baseTime)
        assertTrue("zweifeel@gmail.com harus mendapat akses MASTER_DEV", status.isPremiumUnlocked)
        assertEquals(UserRole.MASTER_DEV, status.effectiveRole)
        assertEquals(EntitlementSource.MASTER_DEV, status.primarySource)
        assertFalse(status.isExpired)
    }

    @Test
    fun entitlement_legacyDevEmail_lunachroc07_doesNotGetMasterDevOnRelease() {
        val devUser = User(
            id = 3L,
            username = "Legacy Dev",
            email = "lunachroc07@gmail.com",
            role = UserRole.MASTER_DEV,
            isGuest = false
        )
        // Pada Release, akun lunachroc07@gmail.com DITOLAK memperoleh MASTER_DEV
        assertFalse(
            "lunachroc07@gmail.com DILARANG memiliki akses istimewa developer pada Release",
            EntitlementManager.isDeveloperAuthorized(devUser, isDebug = false)
        )
        val status = EntitlementManager.evaluate(devUser, actionCredits = 0, now = baseTime, isDebug = false)
        assertFalse("lunachroc07@gmail.com tidak boleh berstatus MASTER_DEV pada Release", status.effectiveRole == UserRole.MASTER_DEV)
    }

    // =========================================================================
    // P0: Verifikasi Lokal firestore.rules Tidak Mengandung Email Typo
    // =========================================================================

    @Test
    fun firestoreRulesLocal_doesNotContainTypoEmail_lunachro01() {
        val rulesFile = findFirestoreRulesFile()
        if (rulesFile == null) {
            println("[SKIP] firestore.rules tidak ditemukan di path yang diharapkan — skip verifikasi file.")
            return
        }
        val content = rulesFile.readText()
        assertFalse(
            "firestore.rules TIDAK BOLEH mengandung email typo 'lunachro01@gmail.com'",
            content.contains("lunachro01@gmail.com")
        )
    }

    @Test
    fun firestoreRulesLocal_doesNotContainTypoEmail_lunachrooc07() {
        val rulesFile = findFirestoreRulesFile()
        if (rulesFile == null) {
            println("[SKIP] firestore.rules tidak ditemukan — skip verifikasi file.")
            return
        }
        val content = rulesFile.readText()
        assertFalse(
            "firestore.rules TIDAK BOLEH mengandung email typo 'lunachrooc07@gmail.com'",
            content.contains("lunachrooc07@gmail.com")
        )
    }

    @Test
    fun firestoreRulesLocal_containsOfficialAdminEmails() {
        val rulesFile = findFirestoreRulesFile()
        if (rulesFile == null) {
            println("[SKIP] firestore.rules tidak ditemukan — skip verifikasi file.")
            return
        }
        val content = rulesFile.readText()
        assertTrue(
            "firestore.rules harus mengandung email resmi 'lunachroc01@gmail.com' di isAdmin()",
            content.contains("lunachroc01@gmail.com")
        )
        assertTrue(
            "firestore.rules harus mengandung email resmi 'zweifeel@gmail.com' di isAdmin()",
            content.contains("zweifeel@gmail.com")
        )
    }

    // =========================================================================
    // P0: Sanity Check isMasterDevIdentifier Konsisten
    // =========================================================================

    @Test
    fun isMasterDevIdentifier_recognizesOfficialEmailsAndUsername() {
        assertTrue(DeveloperAccountConfig.isMasterDevIdentifier("lunachroc01@gmail.com"))
        assertTrue(DeveloperAccountConfig.isMasterDevIdentifier("zweifeel@gmail.com"))
        assertFalse(DeveloperAccountConfig.isMasterDevIdentifier("lunachroc07@gmail.com")) // Legacy dev email excluded in Phase 4B
        assertFalse(DeveloperAccountConfig.isMasterDevIdentifier("Lukas"))   // Lukas neutralized in Phase 1G
        assertFalse(DeveloperAccountConfig.isMasterDevIdentifier("lukas"))   // case-insensitive neutralized
    }

    @Test
    fun isMasterDevIdentifier_rejectsTypoEmailsAndRandomUsers() {
        assertFalse(DeveloperAccountConfig.isMasterDevIdentifier("lunachro01@gmail.com"))
        assertFalse(DeveloperAccountConfig.isMasterDevIdentifier("lunachrooc07@gmail.com"))
        assertFalse(DeveloperAccountConfig.isMasterDevIdentifier("hacker@gmail.com"))
        assertFalse(DeveloperAccountConfig.isMasterDevIdentifier("admin"))
        assertFalse(DeveloperAccountConfig.isMasterDevIdentifier(""))
        assertFalse(DeveloperAccountConfig.isMasterDevIdentifier(null))
    }

    // =========================================================================
    // Helper
    // =========================================================================

    /**
     * Menemukan file firestore.rules dari working directory atau beberapa kandidat relatif.
     * Test berjalan dari direktori modul :app — naik ke root project untuk menemukan rules.
     */
    private fun findFirestoreRulesFile(): File? {
        val candidates = listOf(
            "../../firestore.rules",               // dari app/ ke root (Gradle)
            "../firestore.rules",
            "firestore.rules",
            "E:/FinSmart/firestore.rules"          // path absolut fallback
        )
        return candidates.map { File(it) }.firstOrNull { it.exists() && it.isFile }
    }
}
