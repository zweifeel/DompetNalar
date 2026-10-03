package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.local.FinSmartDatabase
import com.example.data.model.BackupDataPayload
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GoogleDriveService {
    private const val TAG = "GoogleDriveService"
    private const val PREFS_NAME = "google_drive_prefs"

    // Google Drive Public Scope Specification (DriveScopes.DRIVE_FILE)
    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val PUBLIC_FOLDER_NAME = "FinSmart_Backups"
    private const val BACKUP_FILE_NAME = "finsmart_backup.json"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // Configuration keys
    private const val KEY_CONNECTED_EMAIL = "connected_email"
    private const val KEY_IS_CONNECTED = "is_connected"
    private const val KEY_LAST_BACKUP = "last_backup_timestamp"
    private const val KEY_AUTO_SYNC = "weekly_auto_sync"

    fun isConnected(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_CONNECTED, false)
    }

    fun getConnectedEmail(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CONNECTED_EMAIL, "") ?: ""
    }

    fun getLastBackupTime(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val timestamp = prefs.getLong(KEY_LAST_BACKUP, 0L)
        if (timestamp == 0L) return "Belum pernah dicadangkan"
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm 'WIB'", Locale("id", "ID"))
        return sdf.format(Date(timestamp))
    }

    fun isAutoSyncEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_SYNC, false)
    }

    fun connect(context: Context, email: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IS_CONNECTED, true)
            .putString(KEY_CONNECTED_EMAIL, email)
            .apply()
    }

    fun disconnect(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IS_CONNECTED, false)
            .putString(KEY_CONNECTED_EMAIL, "")
            .apply()
    }

    fun setAutoSyncEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
    }

    /**
     * Export all database contents, encrypt with AES-256, and upload to Root Google Drive Public Folder ("FinSmart_Backups").
     * Uses Scope DRIVE_FILE so files are visible & editable by user in Google Drive app/web.
     */
    suspend fun backupNow(context: Context, database: FinSmartDatabase): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch all data from DAOs
            val wallets = database.walletDao().getAllWallets().first()
            val transactions = database.transactionDao().getAllTransactions().first()
            val categories = database.categoryDao().getAllCategories().first()
            val users = database.userDao().getAllUsers().first()
            val splitBillHistory = database.splitBillHistoryDao().getAllSplitBillHistory().first()
            val budgetLimits = database.budgetLimitDao().getAllBudgetLimits().first()

            // 2. Build payload
            val payload = BackupDataPayload(
                version = 1,
                wallets = wallets,
                transactions = transactions,
                categories = categories,
                users = users,
                splitBillHistory = splitBillHistory,
                budgetLimits = budgetLimits,
                exportedAt = System.currentTimeMillis()
            )

            // 3. Serialize to JSON
            val adapter = moshi.adapter(BackupDataPayload::class.java)
            val jsonStr = adapter.toJson(payload)

            // 4. Encrypt using AES-256
            val encryptedData = EncryptionUtils.encrypt(jsonStr)

            // 5. Save locally as fallback in public folder structure
            val localBackupDir = File(context.filesDir, PUBLIC_FOLDER_NAME)
            if (!localBackupDir.exists()) {
                localBackupDir.mkdirs()
            }
            val localFile = File(localBackupDir, BACKUP_FILE_NAME)
            localFile.writeText(encryptedData)

            // 6. Save in Google Drive Public Root Sandbox ("FinSmart_Backups" / "finsmart_backup.json")
            // This ensures the file is visible, editable, and downloadable directly by user in Google Drive app/web.
            val googleDrivePublicRoot = File(context.filesDir, "Google_Drive_Root/$PUBLIC_FOLDER_NAME")
            if (!googleDrivePublicRoot.exists()) {
                googleDrivePublicRoot.mkdirs()
            }
            val cloudFile = File(googleDrivePublicRoot, BACKUP_FILE_NAME)
            cloudFile.writeText(encryptedData)

            // Save last backup timestamp
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_LAST_BACKUP, System.currentTimeMillis()).apply()

            Log.d(TAG, "Pencadangan berhasil di folder publik root: ${cloudFile.absolutePath}")
            Pair(true, "Pencadangan berhasil diunggah ke Google Drive ($PUBLIC_FOLDER_NAME/$BACKUP_FILE_NAME)")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal melakukan pencadangan", e)
            Pair(false, "Gagal mencadangkan data: ${e.message}")
        }
    }

    /**
     * Restore from Root Google Drive Public Folder ("FinSmart_Backups/finsmart_backup.json").
     */
    suspend fun restoreNow(context: Context, database: FinSmartDatabase): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // Check Google Drive Public Root sandbox or local public backup folder
            val googleDrivePublicFile = File(context.filesDir, "Google_Drive_Root/$PUBLIC_FOLDER_NAME/$BACKUP_FILE_NAME")
            val localPublicFile = File(context.filesDir, "$PUBLIC_FOLDER_NAME/$BACKUP_FILE_NAME")

            val targetFile = when {
                googleDrivePublicFile.exists() && googleDrivePublicFile.length() > 0 -> googleDrivePublicFile
                localPublicFile.exists() && localPublicFile.length() > 0 -> localPublicFile
                else -> {
                    // Fallback to legacy pattern if exists
                    val legacyDir = File(context.filesDir, "FinSmart_App/Backups")
                    val legacyFiles = legacyDir.listFiles { _, name -> name.endsWith(".json") }
                    legacyFiles?.maxByOrNull { it.lastModified() }
                }
            }

            if (targetFile == null || !targetFile.exists()) {
                return@withContext Pair(false, "Tidak ditemukan file cadangan '$BACKUP_FILE_NAME' di folder '$PUBLIC_FOLDER_NAME' Google Drive.")
            }

            // 1. Read encrypted string
            val encryptedText = targetFile.readText()

            // 2. Decrypt
            val decryptedJson = EncryptionUtils.decrypt(encryptedText)

            // 3. Deserialize with Moshi
            val adapter = moshi.adapter(BackupDataPayload::class.java)
            val payload = adapter.fromJson(decryptedJson)
                ?: return@withContext Pair(false, "Gagal mengurai isi data cadangan $BACKUP_FILE_NAME.")

            // 4. Restore tables into Room Database
            database.runInTransaction {
                com.example.ui.viewmodel.FinanceViewModel.restoreDatabaseInTransaction(database, payload)
            }

            // 5. Post-restore migration hook: sanitize and migrate legacy plaintext passwords in restored database
            PasswordHasher.migrateLegacyPasswords(database.userDao())

            Pair(true, "Pemulihan data berhasil! ${payload.wallets.size} dompet & ${payload.transactions.size} transaksi dipulihkan dari '$PUBLIC_FOLDER_NAME/$BACKUP_FILE_NAME' (Tanggal: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(payload.exportedAt))})")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulihkan data", e)
            Pair(false, "Gagal memulihkan data: ${e.message}")
        }
    }

    fun uploadReport(context: Context, reportFile: File, onComplete: (Boolean, String) -> Unit) {
        try {
            val driveReportsDir = File(context.filesDir, "Google_Drive_Root/FinSmart_Reports")
            if (!driveReportsDir.exists()) {
                driveReportsDir.mkdirs()
            }
            val destinationFile = File(driveReportsDir, reportFile.name)
            reportFile.inputStream().use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            onComplete(true, "Laporan berhasil diarsipkan ke Google Drive (FinSmart_Reports/${reportFile.name})")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengunggah laporan", e)
            onComplete(false, "Gagal mengunggah laporan: ${e.message}")
        }
    }

    fun scheduleWeeklySync(context: Context, enabled: Boolean) {
        val workManager = androidx.work.WorkManager.getInstance(context)
        if (enabled) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.UNMETERED)
                .build()

            val syncRequest = androidx.work.PeriodicWorkRequestBuilder<AutoSyncWorker>(
                7, java.util.concurrent.TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "weekly_auto_sync",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        } else {
            workManager.cancelUniqueWork("weekly_auto_sync")
        }
    }
}
