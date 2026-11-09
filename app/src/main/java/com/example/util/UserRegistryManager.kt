package com.example.util

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Data model untuk User Registry (Phase 4B).
 *
 * BATASAN KRITIS & JUJUR:
 * Registry ini merekam pengguna yang aktif masuk ke aplikasi DompetNalar Android.
 * Ini BUKAN daftar lengkap seluruh Firebase Authentication (karena keterbatasan teknis
 * Web/Client SDK tanpa server backend dengan izin administrator).
 *
 * PRINSIP PRIVASI:
 * Metadata yang dikirim HANYA metadata administratif minimum.
 * DILARANG KERAS mengirim data keuangan pribadi (saldo, mutasi, dompet, hutang, anggaran, PIN).
 */
data class UserRegistryEntry(
    val uid: String,
    val email: String,
    val displayName: String,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val appVersion: String,
    val platform: String = "Android"
)

object UserRegistryManager {

    private const val TAG = "UserRegistryManager"
    const val COLLECTION_REGISTRY = "user_registry"
    private const val PREFS_NAME = "finsmart_user_registry_sync"
    private const val SYNC_THROTTLE_MILLIS = 6L * 3600 * 1000L // 6 jam debounce

    /**
     * Menyinkronkan metadata dasar pengguna terotentikasi ke Firestore collection "user_registry".
     */
    suspend fun syncUserRegistry(
        context: Context,
        firebaseUser: FirebaseUser?,
        appVersion: String = "1.0.8",
        firestoreInstance: FirebaseFirestore? = null
    ): Result<Boolean> {
        if (firebaseUser == null || firebaseUser.isAnonymous) {
            Log.d(TAG, "Sinkronisasi dilewati: pengguna anonim atau belum masuk.")
            return Result.success(false)
        }

        val uid = firebaseUser.uid
        val email = firebaseUser.email ?: ""
        if (uid.isBlank() || email.isBlank()) {
            Log.w(TAG, "Sinkronisasi dilewati: UID atau email kosong.")
            return Result.success(false)
        }

        // Debounce pemeriksaan: jika baru saja disinkronkan dalam 6 jam terakhir dan versi sama, lewati
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSync = prefs.getLong("last_sync_$uid", 0L)
        val lastVer = prefs.getString("last_version_$uid", "")
        val now = System.currentTimeMillis()

        if (now - lastSync in 0..SYNC_THROTTLE_MILLIS && lastVer == appVersion) {
            Log.d(TAG, "Sinkronisasi user registry ter-throttle (terakhir disinkronkan ${ (now - lastSync) / 60000 } menit lalu).")
            return Result.success(true)
        }

        return try {
            val db = firestoreInstance ?: FirebaseFirestore.getInstance()
            val docRef = db.collection(COLLECTION_REGISTRY).document(uid)

            val displayName = firebaseUser.displayName
                ?: email.substringBefore("@")
                .ifBlank { "Pengguna DompetNalar" }

            // Gunakan snapshot check untuk mempertahankan firstSeenAt asli jika dokumen sudah ada
            val docSnapshot = docRef.get().await()

            val payload = mutableMapOf<String, Any>(
                "uid" to uid,
                "email" to email.trim().lowercase(),
                "displayName" to displayName.take(100),
                "appVersion" to appVersion.take(30),
                "platform" to "Android",
                "lastSeenAt" to FieldValue.serverTimestamp()
            )

            if (!docSnapshot.exists()) {
                payload["firstSeenAt"] = FieldValue.serverTimestamp()
            }

            docRef.set(payload, SetOptions.merge()).await()

            // Catat waktu sukses ke SharedPreferences lokal
            prefs.edit()
                .putLong("last_sync_$uid", now)
                .putString("last_version_$uid", appVersion)
                .apply()

            Log.i(TAG, "User registry berhasil disinkronkan untuk UID: $uid ($email)")
            Result.success(true)
        } catch (e: Exception) {
            // Kegagalan jaringan atau offline TIDAK boleh merusak alur aplikasi klien
            Log.w(TAG, "Gagal menyinkronkan user registry (offline/network): ${e.message}")
            Result.failure(e)
        }
    }
}
