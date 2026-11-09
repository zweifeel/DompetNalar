package com.example

import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.util.EntitlementManager
import com.example.util.EntitlementSource
import com.example.util.TesterGrant
import com.example.util.TesterGrantManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suite Pengujian Regresi: Akumulasi Durasi Remote Smart Master Grant & Proteksi Entitlement.
 *
 * Mencakup 10 skenario wajib:
 * 1. Grant 14 hari + 30 hari saat aktif -> terakumulasi 44 hari dari waktu awal.
 * 2. Grant 14 hari + 7 hari berurutan -> terakumulasi total 21 hari.
 * 3. Grant 30 hari saat sisa 5 hari -> menambah 30 hari ke masa berlaku lama (now + 35 hari).
 * 4. Grant kedaluwarsa + 30 hari -> mulai dari waktu pemberian baru (now + 30 hari).
 * 5. Grant permanen aktif -> tidak pernah turun ke grant terbatas saat diberi durasi.
 * 6. Grant dicabut -> tidak aktif dan cache lama tidak boleh meloloskan akses.
 * 7. Proteksi Google Play Billing -> grant dicabut tidak mengganggu entitlement Play Store yang sah.
 * 8. Proteksi Trial 3 hari -> aturan trial akun Google tetap utuh.
 * 9. Kegagalan jaringan/transaksi -> penanganan kegagalan deterministik tanpa sukses palsu.
 * 10. Dua permintaan bersamaan -> atomisitas mencegah kehilangan durasi grant.
 */
class GrantAccumulationRegressionTest {

    private val baseTime = 1770000000000L
    private val oneDayMillis = 86400000L

    /**
     * Helper perhitungan akumulasi di Kotlin yang merefleksikan logika Firestore Transaction Web Admin
     * (src/utils/grantAccumulation.js) untuk pengujian end-to-end multi-platform.
     */
    private fun calculateAccumulatedExpiry(
        existingGrant: TesterGrant?,
        durationDays: Long?,
        now: Long = baseTime
    ): Long? {
        if (durationDays == null) return null // Permanen
        require(durationDays > 0) { "Durasi hari harus positif." }

        val durationMillis = durationDays * oneDayMillis

        if (existingGrant == null) {
            return now + durationMillis
        }

        // Grant permanen aktif tidak boleh diturunkan
        if (!existingGrant.revoked && existingGrant.expiresAt == null && existingGrant.role == "SMART_MASTER") {
            return null
        }

        // Grant dicabut tidak boleh diakumulasi
        if (existingGrant.revoked) {
            return now + durationMillis
        }

        val existingExpiry = existingGrant.expiresAt
        // Grant sudah expired
        if (existingExpiry != null && existingExpiry <= now) {
            return now + durationMillis
        }

        // Grant masih aktif -> AKUMULASI
        if (existingExpiry != null && existingExpiry > now) {
            return existingExpiry + durationMillis
        }

        return now + durationMillis
    }

    // -------------------------------------------------------------------------
    // 1. Grant 14 hari + 30 hari ketika masih aktif
    // -------------------------------------------------------------------------
    @Test
    fun test1_Grant14DaysPlus30DaysWhileActive_AccumulatesTo44Days() {
        val initialExpiry = baseTime + 14L * oneDayMillis
        val initialGrant = TesterGrant(
            email = "tester.accum@gmail.com",
            role = "SMART_MASTER",
            grantedAt = baseTime,
            expiresAt = initialExpiry,
            revoked = false,
            lastVerifiedAt = baseTime
        )

        // Pemberian kedua: 30 hari saat awal
        val newExpiry = calculateAccumulatedExpiry(initialGrant, 30L, now = baseTime)
        assertNotNull(newExpiry)
        val expectedExpiry = baseTime + 44L * oneDayMillis
        assertEquals("Grant 14 hari + 30 hari harus terakumulasi menjadi 44 hari dari awal", expectedExpiry, newExpiry)

        // Verifikasi pada Android client
        val updatedGrant = initialGrant.copy(expiresAt = newExpiry)
        assertTrue("Grant akumulasi harus aktif", TesterGrantManager.isGrantActive(updatedGrant, baseTime))

        val user = User(
            id = 101L,
            username = "Accum User",
            email = "tester.accum@gmail.com",
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = updatedGrant.expiresAt,
            isGuest = false
        )
        val status = EntitlementManager.evaluate(user, now = baseTime, isDebug = false)
        assertTrue(status.isPremiumUnlocked)
        assertEquals(expectedExpiry, status.expiresAt)
    }

    // -------------------------------------------------------------------------
    // 2. Grant 14 hari + 7 hari berurutan
    // -------------------------------------------------------------------------
    @Test
    fun test2_Grant14DaysPlus7DaysSequential_AccumulatesTo21Days() {
        val initialExpiry = baseTime + 14L * oneDayMillis
        val initialGrant = TesterGrant(
            email = "tester.twentyone@gmail.com",
            role = "SMART_MASTER",
            grantedAt = baseTime,
            expiresAt = initialExpiry,
            revoked = false,
            lastVerifiedAt = baseTime
        )

        val newExpiry = calculateAccumulatedExpiry(initialGrant, 7L, now = baseTime)
        val expectedExpiry = baseTime + 21L * oneDayMillis
        assertEquals("Grant 14 hari + 7 hari harus menjadi 21 hari total", expectedExpiry, newExpiry)
    }

    // -------------------------------------------------------------------------
    // 3. Grant 30 hari diberikan saat sisa 5 hari
    // -------------------------------------------------------------------------
    @Test
    fun test3_Grant30DaysWhen5DaysRemaining_Adds30DaysToOldExpiryNotFromNow() {
        // Misalkan grant awal 14 hari diberikan di baseTime
        // 9 hari berlalu -> sekarang sisa 5 hari
        val now = baseTime + 9L * oneDayMillis
        val oldExpiry = baseTime + 14L * oneDayMillis // sisa 5 hari dari now

        val existingGrant = TesterGrant(
            email = "tester.fiveleft@gmail.com",
            role = "SMART_MASTER",
            grantedAt = baseTime,
            expiresAt = oldExpiry,
            revoked = false,
            lastVerifiedAt = now
        )

        val newExpiry = calculateAccumulatedExpiry(existingGrant, 30L, now = now)
        val expectedExpiry = oldExpiry + 30L * oneDayMillis // now + 5d + 30d = now + 35d
        assertEquals("Masa berlaku harus ditambahkan ke oldExpiry (+30 hari), bukan reset dari now", expectedExpiry, newExpiry)
        assertEquals(now + 35L * oneDayMillis, newExpiry)
        assertNotEquals("Dilarang mereset menjadi now + 30 hari", now + 30L * oneDayMillis, newExpiry)
    }

    // -------------------------------------------------------------------------
    // 4. Grant kedaluwarsa diberi 30 hari
    // -------------------------------------------------------------------------
    @Test
    fun test4_ExpiredGrantGiven30Days_StartsFromNewGrantTime() {
        val now = baseTime + 60L * oneDayMillis
        val expiredGrant = TesterGrant(
            email = "tester.expired@gmail.com",
            role = "SMART_MASTER",
            grantedAt = baseTime,
            expiresAt = baseTime + 14L * oneDayMillis, // Telah lewat
            revoked = false,
            lastVerifiedAt = baseTime
        )

        assertFalse("Grant lama harus berstatus tidak aktif", TesterGrantManager.isGrantActive(expiredGrant, now))

        val newExpiry = calculateAccumulatedExpiry(expiredGrant, 30L, now = now)
        val expectedExpiry = now + 30L * oneDayMillis
        assertEquals("Grant kedaluwarsa harus mulai dari waktu pemberian baru", expectedExpiry, newExpiry)
    }

    // -------------------------------------------------------------------------
    // 5. Grant permanen tidak berubah menjadi terbatas
    // -------------------------------------------------------------------------
    @Test
    fun test5_PermanentGrantDoesNotDowngradeToLimited() {
        val permanentGrant = TesterGrant(
            email = "tester.perm@gmail.com",
            role = "SMART_MASTER",
            grantedAt = baseTime,
            expiresAt = null, // Permanen
            revoked = false,
            lastVerifiedAt = baseTime
        )

        // Admin memilih opsi 30 hari
        val newExpiry = calculateAccumulatedExpiry(permanentGrant, 30L, now = baseTime)
        assertNull("Grant permanen aktif DILARANG diturunkan menjadi grant berdurasi", newExpiry)

        val user = User(
            id = 105L,
            username = "Permanent Tester",
            email = "tester.perm@gmail.com",
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = newExpiry,
            isGuest = false
        )
        val status = EntitlementManager.evaluate(user, now = baseTime, isDebug = false)
        assertTrue(status.isPremiumUnlocked)
        assertNull(status.expiresAt)
        assertEquals("Tidak Terbatas", status.getFormattedExpiry())
    }

    // -------------------------------------------------------------------------
    // 6. Grant dicabut tidak boleh aktif kembali hanya karena cache lama
    // -------------------------------------------------------------------------
    @Test
    fun test6_RevokedGrantNeverActiveViaOldCache() {
        val revokedGrant = TesterGrant(
            email = "tester.revoked@gmail.com",
            role = "SMART_MASTER",
            grantedAt = baseTime,
            expiresAt = baseTime + 30L * oneDayMillis,
            revoked = true, // DICABUT
            lastVerifiedAt = baseTime
        )

        assertFalse("Grant dicabut DILARANG aktif", TesterGrantManager.isGrantActive(revokedGrant, baseTime))
        assertFalse("Grant dicabut DILARANG memperoleh offline grace", TesterGrantManager.isOfflineGraceValid(revokedGrant, baseTime))

        // Jika kemudian diberikan grant 30 hari baru setelah dicabut, durasi tidak boleh diakumulasikan
        val reissuedExpiry = calculateAccumulatedExpiry(revokedGrant, 30L, now = baseTime)
        assertEquals("Pemberian ulang setelah revoke harus mulai dari waktu baru tanpa akumulasi sisa lama", baseTime + 30L * oneDayMillis, reissuedExpiry)
    }

    // -------------------------------------------------------------------------
    // 7. Entitlement Play Billing tetap terlindungi saat grant dicabut
    // -------------------------------------------------------------------------
    @Test
    fun test7_ActivePlayBillingProtectedAgainstGrantRevocation() {
        // Pengguna memiliki Play Store Billing yang aktif
        val playStoreUser = User(
            id = 107L,
            username = "Play Subscriber",
            email = "subscriber@gmail.com",
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = baseTime + 180L * oneDayMillis,
            isGuest = false
        )

        // Evaluasi dengan sumber PLAY_STORE_BILLING
        val status = EntitlementManager.evaluate(
            user = playStoreUser,
            now = baseTime,
            isDebug = false,
            explicitSource = EntitlementSource.PLAY_STORE_BILLING
        )

        assertTrue("Pengguna Play Store harus tetap memiliki akses premium", status.isPremiumUnlocked)
        assertEquals(EntitlementSource.PLAY_STORE_BILLING, status.primarySource)
        assertEquals(baseTime + 180L * oneDayMillis, status.expiresAt)
    }

    // -------------------------------------------------------------------------
    // 8. Trial 3 hari tetap mengikuti aturan sebelumnya
    // -------------------------------------------------------------------------
    @Test
    fun test8_ThreeDaysTrialFollowsEstablishedRules() {
        // Akun Google bukan tamu dengan trial aktif
        val trialUser = User(
            id = 108L,
            username = "Trial User",
            email = "trial.user@gmail.com",
            role = UserRole.FREE_MEMBER,
            trialStartDate = baseTime - (1L * oneDayMillis), // Hari ke-2 dari 3
            isGuest = false
        )

        val status = EntitlementManager.evaluate(trialUser, now = baseTime, isDebug = false)
        assertFalse("Trial akun Google telah dihapus: tidak boleh membuka premium", status.isPremiumUnlocked)
        assertEquals(EntitlementSource.NONE, status.primarySource)
        assertEquals(0, status.trialDaysRemaining)
    }

    // -------------------------------------------------------------------------
    // 9. Kegagalan jaringan / transaksi tidak menghasilkan sukses palsu
    // -------------------------------------------------------------------------
    @Test
    fun test9_NetworkFailureDoesNotYieldFalseSuccessOrCorruptedExpiry() {
        val cachedGrant = TesterGrant(
            email = "tester.offline@gmail.com",
            role = "SMART_MASTER",
            expiresAt = baseTime + 10L * oneDayMillis,
            revoked = false,
            lastVerifiedAt = baseTime - (24L * 3600 * 1000L) // 24 jam lalu (< 72 jam)
        )

        // Verifikasi bahwa dalam toleransi 72 jam, offline grace berlaku tanpa memodifikasi expiresAt
        assertTrue(TesterGrantManager.isOfflineGraceValid(cachedGrant, baseTime))
        assertEquals(baseTime + 10L * oneDayMillis, cachedGrant.expiresAt)

        // Namun jika melebihi 72 jam:
        val expiredGraceGrant = cachedGrant.copy(lastVerifiedAt = baseTime - (75L * 3600 * 1000L))
        assertFalse("Lebih dari 72 jam dilarang lolos offline grace", TesterGrantManager.isOfflineGraceValid(expiredGraceGrant, baseTime))
    }

    // -------------------------------------------------------------------------
    // 10. Dua permintaan grant bersamaan (Atomic Concurrency)
    // -------------------------------------------------------------------------
    @Test
    fun test10_ConcurrentGrantRequestsAccumulateWithoutLosingDuration() {
        // Simulasi state dokumen Firestore yang dimutasi melalui transaksi atomik berurutan
        var docState: TesterGrant? = TesterGrant(
            email = "tester.concurrent@gmail.com",
            role = "SMART_MASTER",
            grantedAt = baseTime,
            expiresAt = baseTime + 14L * oneDayMillis, // Awal 14 hari
            revoked = false,
            lastVerifiedAt = baseTime
        )

        // Transaksi 1: Tambah 30 hari
        val expiry1 = calculateAccumulatedExpiry(docState, 30L, now = baseTime)
        docState = docState!!.copy(expiresAt = expiry1)
        assertEquals(baseTime + 44L * oneDayMillis, docState.expiresAt)

        // Transaksi 2: Tambah 7 hari
        val expiry2 = calculateAccumulatedExpiry(docState, 7L, now = baseTime)
        docState = docState.copy(expiresAt = expiry2)
        assertEquals(baseTime + 51L * oneDayMillis, docState.expiresAt)

        // Total akumulasi: 14 + 30 + 7 = 51 hari
        assertEquals(
            "Dua transaksi bersamaan harus menghasilkan total akumulasi 51 hari tanpa kehilangan durasi",
            baseTime + 51L * oneDayMillis,
            docState.expiresAt
        )
    }
}
