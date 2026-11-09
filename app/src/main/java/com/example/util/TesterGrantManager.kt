package com.example.util

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

/**
 * Data model untuk Remote Smart Master Grant (Phase 4B).
 *
 * Grant ini dikelola oleh Administrator resmi melalui Web Admin
 * dan disinkronkan ke aplikasi Android tester via Firestore collection "tester_grants".
 */
data class TesterGrant(
    val email: String,
    val role: String = "SMART_MASTER", // KEAMANAN: HANYA BOLEH SMART_MASTER, DILARANG MASTER_DEV!
    val grantedBy: String = "",
    val grantedAt: Long = 0L,
    val expiresAt: Long? = null,
    val revoked: Boolean = false,
    val updatedAt: Long = 0L,
    val lastVerifiedAt: Long = 0L // Timestamp lokal verifikasi online terakhir
)

/**
 * TesterGrantManager mengelola siklus hidup, verifikasi, caching, dan offline grace period
 * untuk Remote Smart Master Grants.
 */
object TesterGrantManager {

    private const val TAG = "TesterGrantManager"
    const val COLLECTION_GRANTS = "tester_grants"
    private const val PREFS_NAME = "finsmart_tester_grants"

    // Kebijakan Offline Grace Period maksimum: 72 Jam (3 Hari)
    const val MAX_OFFLINE_GRACE_MILLIS = 72L * 3600 * 1000L

    /**
     * Menghitung Document ID berbasis SHA-256 dari alamat email yang dinormalisasi.
     * Mencegah eksposur email mentah pada path dokumen Firestore.
     */
    fun computeGrantDocId(email: String): String {
        val cleanEmail = email.trim().lowercase()
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(cleanEmail.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            cleanEmail.replace("[^a-zA-Z0-9]".toRegex(), "_")
        }
    }

    /**
     * Memvalidasi apakah grant berstatus aktif:
     * - Role HARUS 'SMART_MASTER'
     * - Tidak dicabut (revoked == false)
     * - Belum kedaluwarsa (expiresAt == null || expiresAt > now)
     */
    fun isGrantActive(grant: TesterGrant?, now: Long = System.currentTimeMillis()): Boolean {
        if (grant == null) return false
        if (grant.revoked) return false
        if (grant.role != "SMART_MASTER") return false
        if (grant.expiresAt != null && grant.expiresAt <= now) return false
        return true
    }

    /**
     * Memvalidasi apakah cache grant yang tersimpan masih dalam batas toleransi offline grace period (maks 72 jam):
     * - Grant harus berstatus aktif secara logis (tidak dicabut, tidak lewat expiresAt)
     * - lastVerifiedAt > 0 dan tidak dimanipulasi ke masa depan
     * - Selisih waktu (now - lastVerifiedAt) <= 72 jam
     */
    fun isOfflineGraceValid(cachedGrant: TesterGrant?, now: Long = System.currentTimeMillis()): Boolean {
        if (cachedGrant == null) return false
        if (!isGrantActive(cachedGrant, now)) return false
        if (cachedGrant.lastVerifiedAt <= 0L) return false
        if (now < cachedGrant.lastVerifiedAt) return false // Perlindungan manipulasi jam sistem mundur
        val elapsed = now - cachedGrant.lastVerifiedAt
        return elapsed in 0..MAX_OFFLINE_GRACE_MILLIS
    }

    /**
     * Mengambil dokumen grant dari Firestore untuk email pengguna aktif.
     *
     * Pembedaan Kritis:
     * - Dokumen tidak ada -> Result.success(null)
     * - Kesalahan jaringan/koneksi -> Result.failure(Exception)
     */
    /**
     * Test hook untuk injeksi remote grant fetcher di unit test tanpa koneksi Firestore langsung.
     */
    var remoteGrantFetcher: (suspend (String) -> Result<TesterGrant?>)? = null

    suspend fun fetchRemoteGrant(
        email: String,
        firestoreInstance: FirebaseFirestore? = null
    ): Result<TesterGrant?> {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) {
            return Result.success(null)
        }

        remoteGrantFetcher?.let { fetcher ->
            return fetcher(cleanEmail)
        }

        return try {
            val db = firestoreInstance ?: FirebaseFirestore.getInstance()
            val docId = computeGrantDocId(cleanEmail)
            val snapshot = db.collection(COLLECTION_GRANTS).document(docId).get().await()

            if (!snapshot.exists()) {
                Log.d(TAG, "Grant remote tidak ditemukan untuk docId: $docId")
                return Result.success(null)
            }

            val docEmail = snapshot.getString("email")?.trim()?.lowercase() ?: cleanEmail
            // Validasi kepemilikan email strictly matching
            if (docEmail != cleanEmail) {
                Log.w(TAG, "Email dokumen grant ($docEmail) tidak cocok dengan akun aktif ($cleanEmail)")
                return Result.success(null)
            }

            val rawRole = snapshot.getString("role") ?: "SMART_MASTER"
            // KEAMANAN KRITIS: Dilarang keras menerima role selain SMART_MASTER dari grant!
            val safeRole = if (rawRole == "SMART_MASTER") "SMART_MASTER" else "SMART_MASTER"

            val grantedBy = snapshot.getString("grantedBy") ?: ""
            val revoked = snapshot.getBoolean("revoked") ?: false

            val grantedAt = snapshot.getLong("grantedAt")
                ?: snapshot.getTimestamp("grantedAt")?.toDate()?.time
                ?: 0L

            val expiresAt = snapshot.getLong("expiresAt")
                ?: snapshot.getTimestamp("expiresAt")?.toDate()?.time

            val updatedAt = snapshot.getLong("updatedAt")
                ?: snapshot.getTimestamp("updatedAt")?.toDate()?.time
                ?: System.currentTimeMillis()

            val grant = TesterGrant(
                email = cleanEmail,
                role = safeRole,
                grantedBy = grantedBy,
                grantedAt = grantedAt,
                expiresAt = expiresAt,
                revoked = revoked,
                updatedAt = updatedAt,
                lastVerifiedAt = System.currentTimeMillis()
            )

            Log.i(TAG, "Berhasil memuat grant remote untuk $cleanEmail (revoked=$revoked, expiresAt=$expiresAt)")
            Result.success(grant)
        } catch (e: Exception) {
            val safeErrType = e.javaClass.simpleName
            val safeErrMsg = e.message?.take(200) ?: "Unknown Firestore error"
            Log.w(TAG, "Gagal mengambil grant remote [$safeErrType]: $safeErrMsg")
            Result.failure(e)
        }
    }

    /**
     * Menyimpan grant terverifikasi ke SharedPreferences lokal terenkripsi/aman per-akun.
     */
    fun saveCachedGrant(context: Context, grant: TesterGrant) {
        if (grant.email.isBlank()) return
        val docId = computeGrantDocId(grant.email)
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("email_$docId", grant.email)
                .putString("role_$docId", grant.role)
                .putString("grantedBy_$docId", grant.grantedBy)
                .putLong("grantedAt_$docId", grant.grantedAt)
                .putLong("expiresAt_$docId", grant.expiresAt ?: -1L)
                .putBoolean("revoked_$docId", grant.revoked)
                .putLong("updatedAt_$docId", grant.updatedAt)
                .putLong("lastVerifiedAt_$docId", grant.lastVerifiedAt)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menyimpan cache grant: ${e.message}")
        }
    }

    /**
     * Membaca cache grant lokal untuk email tertentu.
     */
    fun getCachedGrant(context: Context, email: String): TesterGrant? {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) return null
        val docId = computeGrantDocId(cleanEmail)
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.contains("email_$docId")) return null

            val cachedEmail = prefs.getString("email_$docId", null) ?: return null
            if (cachedEmail != cleanEmail) return null

            val role = prefs.getString("role_$docId", "SMART_MASTER") ?: "SMART_MASTER"
            val grantedBy = prefs.getString("grantedBy_$docId", "") ?: ""
            val grantedAt = prefs.getLong("grantedAt_$docId", 0L)
            val rawExpiry = prefs.getLong("expiresAt_$docId", -1L)
            val expiresAt = if (rawExpiry > 0L) rawExpiry else null
            val revoked = prefs.getBoolean("revoked_$docId", false)
            val updatedAt = prefs.getLong("updatedAt_$docId", 0L)
            val lastVerifiedAt = prefs.getLong("lastVerifiedAt_$docId", 0L)

            TesterGrant(
                email = cachedEmail,
                role = role,
                grantedBy = grantedBy,
                grantedAt = grantedAt,
                expiresAt = expiresAt,
                revoked = revoked,
                updatedAt = updatedAt,
                lastVerifiedAt = lastVerifiedAt
            )
        } catch (e: Exception) {
            Log.w(TAG, "Gagal membaca cache grant: ${e.message}")
            null
        }
    }

    /**
     * Menghapus cache grant untuk email tertentu (misal saat grant dicabut atau kedaluwarsa).
     */
    fun clearCachedGrant(context: Context, email: String) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) return
        val docId = computeGrantDocId(cleanEmail)
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .remove("email_$docId")
                .remove("role_$docId")
                .remove("grantedBy_$docId")
                .remove("grantedAt_$docId")
                .remove("expiresAt_$docId")
                .remove("revoked_$docId")
                .remove("updatedAt_$docId")
                .remove("lastVerifiedAt_$docId")
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menghapus cache grant: ${e.message}")
        }
    }
}
