package com.example.util

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * EntitlementRegistry provides a hardened local registry for user trial dates and purchase expiration.
 *
 * It prevents the loophole where clearing app data or reinstalling the app resets the 3-day trial
 * or causes expired Google Play purchases to be re-activated as fresh 30-day subscriptions.
 */
object EntitlementRegistry {
    private const val TAG = "EntitlementRegistry"
    private const val PREFS_NAME = "finsmart_entitlement_registry"
    private const val FILE_NAME = ".finsmart_entitlement_record"

    private fun hashEmail(email: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(email.trim().lowercase().toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            email.trim().lowercase().replace("[^a-zA-Z0-9]".toRegex(), "_")
        }
    }

    /**
     * Resolves the persistent trial start date for a given email.
     * Mekanisme trial 3 hari telah dihapus secara permanen, sehingga fungsi ini selalu mengembalikan null.
     */
    fun getTrialStartDate(context: Context, email: String): Long? {
        return null
    }

    /**
     * Records the trial start timestamp across multiple persistent stores.
     * Dinonaktifkan: tidak ada lagi data trial yang ditulis ke penyimpanan persisten.
     */
    fun recordTrialStartDate(context: Context, email: String, timestamp: Long) {
        // No-op: trial registration ditiadakan secara permanen
    }

    /**
     * Membersihkan seluruh jejak data trial historis dari SharedPreferences dan penyimpanan file.
     */
    fun purgeTrialRecords(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            for (key in prefs.all.keys) {
                if (key.startsWith("trial_")) {
                    editor.remove(key)
                }
            }
            editor.apply()
        } catch (e: Exception) {
            Log.w(TAG, "Gagal membersihkan trial dari prefs: ${e.message}")
        }

        try {
            val internalFile = File(context.filesDir, FILE_NAME)
            if (internalFile.exists()) {
                internalFile.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menghapus file internal entitlement: ${e.message}")
        }

        try {
            val extDir = context.getExternalFilesDir(null)
            if (extDir != null) {
                val extFile = File(extDir, FILE_NAME)
                if (extFile.exists()) {
                    extFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menghapus file eksternal entitlement: ${e.message}")
        }
    }

    private fun readTimestampFromFile(file: File, prefix: String): Long? {
        if (!file.exists() || !file.canRead()) return null
        return file.useLines { lines ->
            lines.firstOrNull { it.startsWith("$prefix=") }
                ?.substringAfter("=")
                ?.toLongOrNull()
        }
    }

    private fun writeTimestampToFile(file: File, key: String, timestamp: Long) {
        val records = mutableMapOf<String, Long>()
        if (file.exists() && file.canRead()) {
            file.forEachLine { line ->
                val parts = line.split("=")
                if (parts.size == 2) {
                    parts[1].toLongOrNull()?.let { records[parts[0]] = it }
                }
            }
        }
        records[key] = timestamp
        file.bufferedWriter().use { writer ->
            records.forEach { (k, v) ->
                writer.write("$k=$v\n")
            }
        }
    }
}
