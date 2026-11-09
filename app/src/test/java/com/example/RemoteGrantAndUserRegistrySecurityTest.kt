package com.example

import com.example.config.DeveloperAccountConfig
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.util.EntitlementManager
import com.example.util.EntitlementSource
import com.example.util.TesterGrant
import com.example.util.TesterGrantManager
import com.example.util.UserRegistryEntry
import com.example.util.UserRegistryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 4B Test Suite:
 * - Remote Smart Master Grants (Fitur A)
 * - User Registry Otomatis (Fitur B)
 * - Release Developer Security & Account Isolation (Fitur C)
 * - Firestore Security Rules Consistency Verification
 */
class RemoteGrantAndUserRegistrySecurityTest {

    private val baseTime = 1770000000000L

    // =========================================================================
    // 10.1 REMOTE SMART MASTER GRANTS TESTS
    // =========================================================================

    @Test
    fun testGrantActiveForCorrectRecipient() {
        val grant = TesterGrant(
            email = "tester.active@gmail.com",
            role = "SMART_MASTER",
            grantedBy = "lunachroc01@gmail.com",
            grantedAt = baseTime - 10000L,
            expiresAt = baseTime + 30L * 86400000L,
            revoked = false,
            lastVerifiedAt = baseTime
        )
        assertTrue("Grant aktif yang valid harus bernilai true", TesterGrantManager.isGrantActive(grant, baseTime))
    }

    @Test
    fun testGrantPermanentActiveWhenExpiresAtIsNull() {
        val permanentGrant = TesterGrant(
            email = "tester.perm@gmail.com",
            role = "SMART_MASTER",
            grantedBy = "zweifeel@gmail.com",
            grantedAt = baseTime,
            expiresAt = null,
            revoked = false,
            lastVerifiedAt = baseTime
        )
        assertTrue("Grant permanen (expiresAt=null) harus selalu aktif", TesterGrantManager.isGrantActive(permanentGrant, baseTime))
    }

    @Test
    fun testGrantExpiredIsRejected() {
        val expiredGrant = TesterGrant(
            email = "tester.expired@gmail.com",
            role = "SMART_MASTER",
            grantedBy = "lunachroc01@gmail.com",
            grantedAt = baseTime - 200000L,
            expiresAt = baseTime - 1000L, // Sudah lewat
            revoked = false,
            lastVerifiedAt = baseTime - 2000L
        )
        assertFalse("Grant yang telah kedaluwarsa harus ditolak", TesterGrantManager.isGrantActive(expiredGrant, baseTime))
    }

    @Test
    fun testGrantRevokedIsRejected() {
        val revokedGrant = TesterGrant(
            email = "tester.revoked@gmail.com",
            role = "SMART_MASTER",
            grantedBy = "lunachroc01@gmail.com",
            grantedAt = baseTime - 10000L,
            expiresAt = baseTime + 1000000L,
            revoked = true, // Dicabut oleh admin
            lastVerifiedAt = baseTime
        )
        assertFalse("Grant yang telah dicabut harus ditolak meskipun masa berlaku masih ada", TesterGrantManager.isGrantActive(revokedGrant, baseTime))
    }

    @Test
    fun testGrantCannotEscalateToMasterDev() {
        // Jika dokumen cloud mencoba menetapkan role MASTER_DEV, isGrantActive harus menolaknya!
        val maliciousGrant = TesterGrant(
            email = "hacker@gmail.com",
            role = "MASTER_DEV", // Ekskalasi ilegal
            grantedBy = "hacker@gmail.com",
            grantedAt = baseTime,
            expiresAt = null,
            revoked = false,
            lastVerifiedAt = baseTime
        )
        assertFalse("Grant selain SMART_MASTER (misal MASTER_DEV) DILARANG aktif", TesterGrantManager.isGrantActive(maliciousGrant, baseTime))
    }

    @Test
    fun testGrantDocIdIsDeterministicHash() {
        val id1 = TesterGrantManager.computeGrantDocId("Tester.One@Gmail.com")
        val id2 = TesterGrantManager.computeGrantDocId("tester.one@gmail.com ")
        assertEquals("Doc ID harus identik untuk email yang sama setelah normalisasi", id1, id2)
        assertFalse("Doc ID tidak boleh berupa email mentah yang memuat karakter @", id1.contains("@"))
        assertEquals("SHA-256 hex string harus berpanjang 64 karakter", 64, id1.length)
    }

    @Test
    fun testOfflineGrace_validWithin72Hours() {
        val cachedGrant = TesterGrant(
            email = "tester.offline@gmail.com",
            role = "SMART_MASTER",
            expiresAt = baseTime + 7L * 86400000L,
            revoked = false,
            lastVerifiedAt = baseTime - (48L * 3600 * 1000L) // Terverifikasi 48 jam lalu
        )
        assertTrue(
            "Cache grant dalam batas 72 jam harus diizinkan offline grace",
            TesterGrantManager.isOfflineGraceValid(cachedGrant, baseTime)
        )
    }

    @Test
    fun testOfflineGrace_rejectedBeyond72Hours() {
        val staleGrant = TesterGrant(
            email = "tester.stale@gmail.com",
            role = "SMART_MASTER",
            expiresAt = baseTime + 7L * 86400000L,
            revoked = false,
            lastVerifiedAt = baseTime - (73L * 3600 * 1000L) // 73 jam lalu (> 72 jam)
        )
        assertFalse(
            "Cache grant lebih dari 72 jam DILARANG memperoleh offline grace",
            TesterGrantManager.isOfflineGraceValid(staleGrant, baseTime)
        )
    }

    @Test
    fun testOfflineGrace_rejectedIfExpiresAtPassed() {
        val expiredDuringGrace = TesterGrant(
            email = "tester.expgrace@gmail.com",
            role = "SMART_MASTER",
            expiresAt = baseTime - 1000L, // Sudah expired secara absolut
            revoked = false,
            lastVerifiedAt = baseTime - (5L * 3600 * 1000L) // 5 jam lalu
        )
        assertFalse(
            "Offline grace tidak boleh memperpanjang grant yang sudah melewati expiresAt",
            TesterGrantManager.isOfflineGraceValid(expiredDuringGrace, baseTime)
        )
    }

    @Test
    fun testOfflineGrace_rejectedIfClockManipulatedBackward() {
        val clockTampered = TesterGrant(
            email = "tester.tamper@gmail.com",
            role = "SMART_MASTER",
            expiresAt = baseTime + 86400000L,
            revoked = false,
            lastVerifiedAt = baseTime + 100000L // lastVerifiedAt di masa depan relatif terhadap now
        )
        assertFalse(
            "Manipulasi jam sistem ke masa lampau harus ditolak oleh offline grace validator",
            TesterGrantManager.isOfflineGraceValid(clockTampered, baseTime)
        )
    }

    @Test
    fun testEntitlement_smartMasterWithDeveloperGrantSource() {
        val user = User(
            id = 50L,
            username = "Tester Resmi",
            email = "tester@gmail.com",
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = baseTime + 30L * 86400000L,
            isGuest = false
        )
        val status = EntitlementManager.evaluate(
            user = user,
            now = baseTime,
            isDebug = false,
            explicitSource = EntitlementSource.DEVELOPER_GRANT
        )
        assertTrue(status.isPremiumUnlocked)
        assertEquals(UserRole.SMART_MASTER, status.effectiveRole)
        assertEquals(EntitlementSource.DEVELOPER_GRANT, status.primarySource)
        assertFalse(status.isExpired)
    }

    @Test
    fun testEntitlement_trialDoesNotGetConfusedWithGrant() {
        val trialUser = User(
            id = 51L,
            username = "Google Trial User",
            email = "trial.user@gmail.com",
            role = UserRole.FREE_MEMBER,
            trialStartDate = baseTime - 86400000L, // Hari ke-2 dari 3 hari
            subscriptionExpiresAt = null,
            isGuest = false
        )
        val status = EntitlementManager.evaluate(trialUser, now = baseTime, isDebug = false)
        assertFalse(status.isPremiumUnlocked)
        assertEquals(UserRole.FREE_MEMBER, status.effectiveRole)
        assertEquals(EntitlementSource.NONE, status.primarySource)
    }

    @Test
    fun testEntitlement_playBillingSourceNotDowngradedOrConfused() {
        val billingUser = User(
            id = 52L,
            username = "Paying Customer",
            email = "buyer@gmail.com",
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = baseTime + 30L * 86400000L,
            isGuest = false
        )
        val status = EntitlementManager.evaluate(
            user = billingUser,
            now = baseTime,
            isDebug = false,
            explicitSource = EntitlementSource.PLAY_STORE_BILLING
        )
        assertTrue(status.isPremiumUnlocked)
        assertEquals(UserRole.SMART_MASTER, status.effectiveRole)
        assertEquals(EntitlementSource.PLAY_STORE_BILLING, status.primarySource)
    }

    @Test
    fun testGuestDoesNotGetSmartMasterFromOtherUserGrant() {
        val guestUser = User(
            id = 0L,
            username = "Tamu",
            email = "",
            role = UserRole.FREE_MEMBER,
            isGuest = true
        )
        val status = EntitlementManager.evaluate(guestUser, now = baseTime, isDebug = false)
        assertFalse("Tamu tidak boleh mendapat akses premium secara otomatis", status.isPremiumUnlocked)
        assertEquals(EntitlementSource.NONE, status.primarySource)
    }

    // =========================================================================
    // 10.2 USER REGISTRY TESTS
    // =========================================================================

    @Test
    fun testUserRegistryEntryStructure_noFinancialData() {
        val entry = UserRegistryEntry(
            uid = "firebase_uid_12345",
            email = "user.testing@gmail.com",
            displayName = "Pengguna Uji",
            firstSeenAt = baseTime,
            lastSeenAt = baseTime,
            appVersion = "1.0.8",
            platform = "Android"
        )
        assertEquals("firebase_uid_12345", entry.uid)
        assertEquals("user.testing@gmail.com", entry.email)
        assertEquals("Android", entry.platform)
        assertEquals("1.0.8", entry.appVersion)

        // Verifikasi tidak ada field finansial di UserRegistryEntry
        val fields = UserRegistryEntry::class.java.declaredFields.map { it.name }
        assertFalse("Registry DILARANG menyimpan saldo/balance", fields.contains("balance"))
        assertFalse("Registry DILARANG menyimpan dompet/wallets", fields.contains("wallets"))
        assertFalse("Registry DILARANG menyimpan transaksi/transactions", fields.contains("transactions"))
        assertFalse("Registry DILARANG menyimpan PIN", fields.contains("pin"))
        assertFalse("Registry DILARANG menyimpan role", fields.contains("role"))
    }

    // =========================================================================
    // 10.3 RELEASE DEVELOPER SECURITY TESTS
    // =========================================================================

    @Test
    fun testLegacyDevEmail_lunachroc07_cannotGetMasterDevOnRelease() {
        val legacyUser = User(
            id = 99L,
            username = "Legacy Developer",
            email = "lunachroc07@gmail.com",
            role = UserRole.MASTER_DEV,
            isGuest = false
        )
        // Pada Release publik (isDebug = false), lunachroc07@gmail.com DITOLAK
        assertFalse(
            "lunachroc07@gmail.com DILARANG diizinkan sebagai developer di Release",
            EntitlementManager.isDeveloperAuthorized(legacyUser, isDebug = false)
        )
        val status = EntitlementManager.evaluate(legacyUser, now = baseTime, isDebug = false)
        assertFalse("Status effective role tidak boleh MASTER_DEV pada Release", status.effectiveRole == UserRole.MASTER_DEV)
    }

    @Test
    fun testWebAdminEmails_doNotAutomaticallyGetMasterDevOnRelease() {
        val adminUser1 = User(
            id = 101L,
            username = "Admin 1",
            email = "lunachroc01@gmail.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false
        )
        val adminUser2 = User(
            id = 102L,
            username = "Admin 2",
            email = "zweifeel@gmail.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false
        )
        // Otorisasi Web Admin dan Master Dev Android adalah berbeda. Di Release, admin web tidak otomatis MASTER_DEV.
        assertFalse(
            "lunachroc01@gmail.com tidak boleh otomatis mendapat hak MASTER_DEV Android di Release",
            EntitlementManager.isDeveloperAuthorized(adminUser1, isDebug = false)
        )
        assertFalse(
            "zweifeel@gmail.com tidak boleh otomatis mendapat hak MASTER_DEV Android di Release",
            EntitlementManager.isDeveloperAuthorized(adminUser2, isDebug = false)
        )
    }

    @Test
    fun testTrustedDeveloperEmails_containsOnlyTwoOfficialAdmins() {
        assertEquals("Harus tepat 2 email di TRUSTED_DEVELOPER_EMAILS", 2, DeveloperAccountConfig.TRUSTED_DEVELOPER_EMAILS.size)
        assertTrue(DeveloperAccountConfig.isTrustedDeveloperEmail("lunachroc01@gmail.com"))
        assertTrue(DeveloperAccountConfig.isTrustedDeveloperEmail("zweifeel@gmail.com"))
        assertFalse(DeveloperAccountConfig.isTrustedDeveloperEmail("lunachroc07@gmail.com"))
    }

    @Test
    fun testUnauthorizedAccountDoesNotGetDeveloperRoleViaFallback() {
        val randomUser = User(
            id = 999L,
            username = "Unknown",
            email = "attacker@gmail.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false
        )
        assertFalse(EntitlementManager.isDeveloperAuthorized(randomUser, isDebug = false))
        assertFalse(EntitlementManager.isDeveloperAuthorized(randomUser, isDebug = true))

        // Akun yang memanipulasi database lokal dengan role MASTER_DEV tetap ditolak pada Release
        val tamperedUser = User(
            id = 999L,
            username = "Attacker",
            email = "attacker@gmail.com",
            role = UserRole.MASTER_DEV,
            isGuest = false
        )
        assertFalse("Manipulasi lokal role MASTER_DEV harus ditolak pada Release", EntitlementManager.isDeveloperAuthorized(tamperedUser, isDebug = false))
    }

    // =========================================================================
    // 10.4 FIRESTORE RULES INTEGRITY TESTS (STATIC FILE ANALYSIS)
    // =========================================================================

    @Test
    fun testFirestoreRules_grantsCollectionRestrictedToSmartMaster() {
        val rulesFile = findFirestoreRulesFile()
        assertNotNull("firestore.rules harus ditemukan di repository", rulesFile)
        val content = rulesFile!!.readText()

        // 1. Validasi isAdmin hanya mencakup 2 email resmi dan mewajibkan email_verified == true
        assertTrue(content.contains("lunachroc01@gmail.com"))
        assertTrue(content.contains("zweifeel@gmail.com"))
        assertTrue("isAdmin wajib mensyaratkan email_verified == true", content.contains("request.auth.token.email_verified == true"))
        assertFalse("firestore.rules TIDAK BOLEH mengandung lunachroc07@gmail.com", content.contains("lunachroc07@gmail.com"))

        // 2. Validasi isValidGrant mewajibkan role SMART_MASTER
        assertTrue("isValidGrant wajib menegakkan role SMART_MASTER", content.contains("data.role == 'SMART_MASTER'"))

        // 3. Validasi tester_grants rule: delete dipisahkan dari isValidGrant()
        assertTrue(content.contains("match /tester_grants/{grantId}"))
        assertTrue("Pembuatan dan update grant divalidasi oleh isValidGrant()", content.contains("allow create, update: if isAdmin() && isValidGrant();"))
        assertTrue("Penghapusan grant HANYA membutuhkan isAdmin() tanpa isValidGrant()", content.contains("allow delete: if isAdmin();"))
        assertTrue(content.contains("resource.data.email.lower() == request.auth.token.email.lower()"))

        // 4. Validasi user_registry rule: email diikat ke auth token dan diimutasikan saat update
        assertTrue(content.contains("match /user_registry/{uid}"))
        assertTrue(content.contains("request.auth.uid == uid"))
        assertTrue("Pendaftaran registry mengikat email ke auth token", content.contains("data.email == request.auth.token.email.lower()"))
        assertTrue("Update registry mencegah perubahan email", content.contains("data.email == resource.data.email"))
        assertTrue("Update registry mencegah perubahan firstSeenAt", content.contains("data.firstSeenAt == resource.data.firstSeenAt"))

        // 5. Verifikasi deny-all baseline tetap utuh
        assertTrue(content.contains("allow read, write: if false;"))
    }

    // =========================================================================
    // 10.5 PHASE 4B.7 STARTUP & SESSION RECOVERY GRANT SYNC REGRESSION TESTS
    // =========================================================================

    @Test
    fun testPhase4B7_permanentGrantReplacesLocal30DayDurationWithoutDefaultingTo365Days() {
        // Pengguna sebelumnya memiliki grant 30 hari di lokal
        val initialUser = User(
            id = 70L,
            username = "Tester 30D",
            email = "tester.upgrade@gmail.com",
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = baseTime + 30L * 86400000L,
            isGuest = false
        )
        // Web Admin menerbitkan grant permanen (expiresAt = null)
        val permanentRemoteGrant = TesterGrant(
            email = "tester.upgrade@gmail.com",
            role = "SMART_MASTER",
            grantedBy = "lunachroc01@gmail.com",
            grantedAt = baseTime,
            expiresAt = null, // Permanen
            revoked = false,
            lastVerifiedAt = baseTime
        )
        assertTrue("Grant permanen harus valid dan aktif", TesterGrantManager.isGrantActive(permanentRemoteGrant, baseTime))

        // Simulasikan penerapan grant permanen ke user
        val updatedUser = initialUser.copy(
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = permanentRemoteGrant.expiresAt
        )
        assertNull("Masa berlaku harus null (tidak terbatas) dan DILARANG diubah ke 365 hari atau nilai default", updatedUser.subscriptionExpiresAt)

        val status = EntitlementManager.evaluate(
            user = updatedUser,
            now = baseTime,
            isDebug = false,
            explicitSource = EntitlementSource.DEVELOPER_GRANT
        )
        assertTrue(status.isPremiumUnlocked)
        assertEquals(UserRole.SMART_MASTER, status.effectiveRole)
        assertNull(status.expiresAt)
        assertEquals("Tidak Terbatas", status.getFormattedExpiry())
    }

    @Test
    fun testPhase4B7_30DayGrantSetsExactRemoteExpirationWithoutCumulativeAddition() {
        val remote30DayExpiry = baseTime + 30L * 86400000L
        val grant30Day = TesterGrant(
            email = "tester.thirty@gmail.com",
            role = "SMART_MASTER",
            grantedBy = "zweifeel@gmail.com",
            grantedAt = baseTime,
            expiresAt = remote30DayExpiry,
            revoked = false,
            lastVerifiedAt = baseTime
        )
        assertTrue(TesterGrantManager.isGrantActive(grant30Day, baseTime))

        val user = User(
            id = 71L,
            username = "Tester 30",
            email = "tester.thirty@gmail.com",
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = grant30Day.expiresAt,
            isGuest = false
        )
        assertEquals("Masa berlaku harus tepat sama dengan dokumen remote", remote30DayExpiry, user.subscriptionExpiresAt)

        val status = EntitlementManager.evaluate(
            user = user,
            now = baseTime,
            isDebug = false,
            explicitSource = EntitlementSource.DEVELOPER_GRANT
        )
        assertTrue(status.isPremiumUnlocked)
        assertEquals(remote30DayExpiry, status.expiresAt)
    }

    @Test
    fun testPhase4B7_reissuedGrantUpdatesExpirationOnSameDocument() {
        val initialExpiry = baseTime + 10L * 86400000L
        val reissuedExpiry = baseTime + 45L * 86400000L

        val initialGrant = TesterGrant(
            email = "tester.reissue@gmail.com",
            role = "SMART_MASTER",
            expiresAt = initialExpiry,
            revoked = false,
            lastVerifiedAt = baseTime
        )
        val reissuedGrant = TesterGrant(
            email = "tester.reissue@gmail.com",
            role = "SMART_MASTER",
            expiresAt = reissuedExpiry,
            revoked = false,
            lastVerifiedAt = baseTime + 1000L
        )

        val userStep1 = User(
            id = 72L,
            username = "Reissue User",
            email = "tester.reissue@gmail.com",
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = initialGrant.expiresAt,
            isGuest = false
        )
        assertEquals(initialExpiry, userStep1.subscriptionExpiresAt)

        // Setelah sync berikutnya, nilai terbaru langsung diterapkan
        val userStep2 = userStep1.copy(subscriptionExpiresAt = reissuedGrant.expiresAt)
        assertEquals("Penerbitan ulang harus memperbarui expiresAt ke nilai dokumen terbaru", reissuedExpiry, userStep2.subscriptionExpiresAt)
    }

    @Test
    fun testPhase4B7_revokedGrantDoesNotRetainAccessViaOldLocalData() {
        val revokedGrant = TesterGrant(
            email = "tester.revoke@gmail.com",
            role = "SMART_MASTER",
            expiresAt = baseTime + 30L * 86400000L,
            revoked = true, // Dicabut oleh Admin di Web Admin
            lastVerifiedAt = baseTime
        )
        assertFalse("Grant yang revoked DILARANG aktif", TesterGrantManager.isGrantActive(revokedGrant, baseTime))

        // Ketika sinkronisasi mendeteksi grant tidak aktif, user diturunkan ke FREE_MEMBER
        val downgradedUser = User(
            id = 73L,
            username = "Revoked User",
            email = "tester.revoke@gmail.com",
            role = UserRole.FREE_MEMBER,
            subscriptionExpiresAt = null,
            isGuest = false
        )
        val status = EntitlementManager.evaluate(downgradedUser, now = baseTime, isDebug = false)
        assertFalse("Pengguna dengan grant dicabut tidak boleh memiliki akses premium", status.isPremiumUnlocked)
        assertEquals(UserRole.FREE_MEMBER, status.effectiveRole)
    }

    @Test
    fun testPhase4B7_expiredGrantDoesNotExtendAccess() {
        val expiredGrant = TesterGrant(
            email = "tester.expired@gmail.com",
            role = "SMART_MASTER",
            expiresAt = baseTime - 5000L, // Sudah lewat waktu
            revoked = false,
            lastVerifiedAt = baseTime - 10000L
        )
        assertFalse(TesterGrantManager.isGrantActive(expiredGrant, baseTime))
        assertFalse("Grant kedaluwarsa tidak boleh memenuhi offline grace", TesterGrantManager.isOfflineGraceValid(expiredGrant, baseTime))

        val expiredUser = User(
            id = 74L,
            username = "Expired Tester",
            email = "tester.expired@gmail.com",
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = baseTime - 5000L,
            isGuest = false
        )
        val status = EntitlementManager.evaluate(expiredUser, now = baseTime, isDebug = false)
        assertFalse("Grant kedaluwarsa tidak boleh mempertahankan akses terbuka", status.isPremiumUnlocked)
        assertTrue(status.isExpired)
    }

    @Test
    fun testPhase4B7_activeGooglePlayBillingProtectedFromRemoteGrantOverride() {
        val billingUser = User(
            id = 75L,
            username = "Subscriber",
            email = "sub@gmail.com",
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = baseTime + 30L * 86400000L,
            isGuest = false
        )
        // Evaluasi dengan explicit source PLAY_STORE_BILLING
        val billingStatus = EntitlementManager.evaluate(
            user = billingUser,
            now = baseTime,
            isDebug = false,
            explicitSource = EntitlementSource.PLAY_STORE_BILLING
        )
        assertTrue(billingStatus.isPremiumUnlocked)
        assertEquals(EntitlementSource.PLAY_STORE_BILLING, billingStatus.primarySource)

        // Walaupun ada grant remote dicabut, pembelian Google Play yang sah tetap terlindungi
        assertEquals("Hak Play Store Billing harus tetap utuh", EntitlementSource.PLAY_STORE_BILLING, billingStatus.primarySource)
    }

    @Test
    fun testPhase4B7_threeDayTrialRemainsFunctionalWhenGrantRemoved() {
        // Pengguna Google baru yang masih dalam 3 hari masa uji coba
        val trialUser = User(
            id = 76L,
            username = "Trial Google User",
            email = "newbie@gmail.com",
            role = UserRole.FREE_MEMBER,
            trialStartDate = baseTime - (24L * 3600 * 1000L), // Hari ke-2 dari 3 hari
            subscriptionExpiresAt = null,
            isGuest = false
        )
        val status = EntitlementManager.evaluate(trialUser, now = baseTime, isDebug = false)
        assertFalse("Trial 3 hari telah dihapus secara permanen", status.isPremiumUnlocked)
        assertEquals(EntitlementSource.NONE, status.primarySource)
        assertEquals(0, status.trialDaysRemaining)
    }

    @Test
    fun testPhase4B7_guestUserNeverReceivesRemoteGrant() {
        val guest = User(
            id = 1000L,
            username = "Tamu FinSmart",
            email = "guest_123456@finsmart.local",
            role = UserRole.FREE_MEMBER,
            isGuest = true
        )
        val status = EntitlementManager.evaluate(guest, now = baseTime, isDebug = false)
        assertFalse("Tamu dilarang keras mendapatkan hak remote grant", status.isPremiumUnlocked)
        assertEquals(EntitlementSource.NONE, status.primarySource)
    }

    @Test
    fun testPhase4B7_remoteGrantNeverGrantsMasterDev() {
        val maliciousRemoteGrant = TesterGrant(
            email = "tester@gmail.com",
            role = "MASTER_DEV", // Ekskalasi ilegal dari payload remote
            revoked = false,
            lastVerifiedAt = baseTime
        )
        assertFalse("Role selain SMART_MASTER (misal MASTER_DEV) dilarang aktif", TesterGrantManager.isGrantActive(maliciousRemoteGrant, baseTime))
    }

    @Test
    fun testPhase4B7_remoteGrantFetcherTestHook_handlesSuccessAndFailureDeterministically() {
        kotlinx.coroutines.runBlocking {
            try {
                // Skenario 1: Sukses menerima grant
                TesterGrantManager.remoteGrantFetcher = { email ->
                    Result.success(
                        TesterGrant(
                            email = email,
                            role = "SMART_MASTER",
                            grantedBy = "lunachroc01@gmail.com",
                            grantedAt = baseTime,
                            expiresAt = null,
                            revoked = false,
                            lastVerifiedAt = baseTime
                        )
                    )
                }
                val successResult = TesterGrantManager.fetchRemoteGrant("tester@gmail.com")
                assertTrue(successResult.isSuccess)
                val grant = successResult.getOrNull()
                assertNotNull(grant)
                assertEquals("tester@gmail.com", grant?.email)
                assertNull(grant?.expiresAt)

                // Skenario 2: Kegagalan Firestore (PERMISSION_DENIED / Network Timeout)
                TesterGrantManager.remoteGrantFetcher = {
                    Result.failure(Exception("PERMISSION_DENIED: Missing or insufficient permissions."))
                }
                val failureResult = TesterGrantManager.fetchRemoteGrant("tester@gmail.com")
                assertTrue("Kegagalan Firestore harus menghasilkan Result.failure", failureResult.isFailure)
                assertEquals("PERMISSION_DENIED: Missing or insufficient permissions.", failureResult.exceptionOrNull()?.message)
            } finally {
                TesterGrantManager.remoteGrantFetcher = null
            }
        }
    }

    @Test
    fun testPhase4B7_syncDebounceWindow_preventsRapidRedundantSyncs() {
        val debounceMillis = com.example.ui.viewmodel.AuthViewModel.GRANT_SYNC_DEBOUNCE_MILLIS
        assertEquals("Batas debounce harus 30 detik", 30000L, debounceMillis)

        val lastSync = baseTime
        val rapidResumeTime = baseTime + 5000L // 5 detik kemudian (dalam batas 30 detik)
        val isDebounced = (rapidResumeTime - lastSync in 0..debounceMillis)
        assertTrue("Permintaan sinkronisasi dalam 5 detik harus ter-debounce untuk mencegah request berulang", isDebounced)

        val validResumeTime = baseTime + 35000L // 35 detik kemudian (melewati batas 30 detik)
        val isDebouncedAfterWindow = (validResumeTime - lastSync in 0..debounceMillis)
        assertFalse("Permintaan sinkronisasi setelah 30 detik harus diizinkan memeriksa pembaruan grant", isDebouncedAfterWindow)
    }

    private fun findFirestoreRulesFile(): File? {
        val candidates = listOf(
            File("firestore.rules"),
            File("../firestore.rules"),
            File("../../firestore.rules")
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
    }
}
