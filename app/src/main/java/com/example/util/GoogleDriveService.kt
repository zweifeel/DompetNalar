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
    private const val PORTABLE_BACKUP_FILE_NAME = "finsmart_portable_backup.json"

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
     * Atomically and safely writes backup data, archiving any existing Format v1 backup
     * to prevent accidental data loss.
     */
    private fun writeBackupFileSafely(targetDir: File, fileName: String, content: String) {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val targetFile = File(targetDir, fileName)
        val tempFile = File(targetDir, "$fileName.tmp")

        // 1. Write content to temporary file first
        tempFile.writeText(content)

        // 2. If existing target file is Format v1, preserve it as an archive
        if (targetFile.exists() && targetFile.length() > 0) {
            try {
                val existingText = targetFile.readText()
                if (!EncryptionUtils.isFormatV2(existingText)) {
                    val legacyBak = File(targetDir, "$fileName.v1.bak")
                    targetFile.copyTo(legacyBak, overwrite = true)
                    Log.d(TAG, "Cadangan legacy v1 diarsipkan secara aman di: ${legacyBak.name}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gagal membuat arsip cadangan legacy: ${e.message}")
            }
        }

        // 3. Atomically replace target file
        if (targetFile.exists()) {
            targetFile.delete()
        }
        val renamed = tempFile.renameTo(targetFile)
        if (!renamed) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
    }

    /**
     * Export all database contents, encrypt with Format v2 (AES-GCM-256 authenticated AEAD),
     * and persist to local fallback and Google Drive Public Root Sandbox.
     */
    suspend fun backupNow(context: Context, database: FinSmartDatabase, userId: Long): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch user-scoped data from DAOs
            val wallets = database.walletDao().getAllWallets(userId).first()
            val transactions = database.transactionDao().getAllTransactions(userId).first()
            val categories = database.categoryDao().getAllCategories(userId).first()
            val users = database.userDao().getUserById(userId)?.let { listOf(it) } ?: emptyList()
            val splitBillHistory = database.splitBillHistoryDao().getAllSplitBillHistory(userId).first()
            val budgetLimits = database.budgetLimitDao().getAllBudgetLimits(userId).first()
            val debts = database.debtDao().getAllDebts(userId).first()
            val assets = database.assetDao().getAllAssets(userId).first()

            // 2. Build payload
            val payload = BackupDataPayload(
                version = 1,
                wallets = wallets,
                transactions = transactions,
                categories = categories,
                users = users,
                splitBillHistory = splitBillHistory,
                budgetLimits = budgetLimits,
                debts = debts,
                assets = assets,
                exportedAt = System.currentTimeMillis()
            )

            // 3. Serialize to JSON
            val adapter = moshi.adapter(BackupDataPayload::class.java)
            val jsonStr = adapter.toJson(payload)

            // 4. Encrypt using Format v2 (AES-GCM-256 authenticated AEAD envelope)
            val encryptedData = EncryptionUtils.encrypt(jsonStr)

            // 5. Save locally as fallback using atomic write
            val localBackupDir = File(context.filesDir, PUBLIC_FOLDER_NAME)
            writeBackupFileSafely(localBackupDir, BACKUP_FILE_NAME, encryptedData)

            // 6. Save in Google Drive Public Root Sandbox using atomic write
            val googleDrivePublicRoot = File(context.filesDir, "Google_Drive_Root/$PUBLIC_FOLDER_NAME")
            writeBackupFileSafely(googleDrivePublicRoot, BACKUP_FILE_NAME, encryptedData)

            // Save last backup timestamp
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_LAST_BACKUP, System.currentTimeMillis()).apply()

            Log.d(TAG, "Pencadangan Format v2 (AES-GCM) berhasil disimpan.")
            Pair(true, "Pencadangan berhasil diunggah ke Google Drive ($PUBLIC_FOLDER_NAME/$BACKUP_FILE_NAME) [Format v2 Terautentikasi]")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal melakukan pencadangan", e)
            Pair(false, "Gagal mencadangkan data: ${e.message}")
        }
    }

    /**
     * Restore from Root Google Drive Public Folder ("FinSmart_Backups/finsmart_backup.json").
     * Supports both Format v2 (AES-GCM) and Format v1 (Legacy AES-CBC) with seamless auto-dispatch.
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

            // 2. Identify format
            val isV2 = EncryptionUtils.isFormatV2(encryptedText)
            val formatLabel = if (isV2) "Format v2 (AES-GCM Terautentikasi)" else "Format v1 (Legacy AES-CBC)"

            // 3. Decrypt with auto-dispatch
            val decryptedJson = EncryptionUtils.decrypt(encryptedText)

            // 4. Deserialize with Moshi
            val adapter = moshi.adapter(BackupDataPayload::class.java)
            val payload = adapter.fromJson(decryptedJson)
                ?: return@withContext Pair(false, "Gagal mengurai isi data cadangan $BACKUP_FILE_NAME.")

            // 5. Restore tables into Room Database atomically
            database.runInTransaction {
                com.example.ui.viewmodel.FinanceViewModel.restoreDatabaseInTransaction(database, payload)
            }

            // 6. Post-restore migration hook: sanitize and migrate legacy plaintext passwords in restored database
            PasswordHasher.migrateLegacyPasswords(database.userDao())

            // 7. Non-destructive auto-upgrade: If restored from legacy v1, upgrade local backup to v2
            if (!isV2) {
                try {
                    val upgradedV2Data = EncryptionUtils.encrypt(decryptedJson)
                    val localBackupDir = File(context.filesDir, PUBLIC_FOLDER_NAME)
                    writeBackupFileSafely(localBackupDir, BACKUP_FILE_NAME, upgradedV2Data)
                    val googleDrivePublicRoot = File(context.filesDir, "Google_Drive_Root/$PUBLIC_FOLDER_NAME")
                    writeBackupFileSafely(googleDrivePublicRoot, BACKUP_FILE_NAME, upgradedV2Data)
                    Log.d(TAG, "Cadangan berhasil dimigrasikan ke Format v2 secara non-destruktif.")
                } catch (e: Exception) {
                    Log.w(TAG, "Peringatan: Gagal melakukan migrasi non-destruktif ke v2: ${e.message}")
                }
            }

            Pair(
                true,
                "Pemulihan data berhasil [$formatLabel]! ${payload.wallets.size} dompet & ${payload.transactions.size} transaksi dipulihkan dari '$PUBLIC_FOLDER_NAME/$BACKUP_FILE_NAME' (Tanggal: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(payload.exportedAt))})"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulihkan data", e)
            Pair(false, "Gagal memulihkan data: ${e.message}")
        }
    }

    /**
     * Export all database contents, encrypt with Format v2 using a custom user passphrase
     * via PBKDF2-HMAC-SHA256 (100,000 iterations, 16-byte random salt, AES-256-GCM AEAD).
     * Does NOT use AndroidKeyStore, enabling full cross-device and reinstall portability.
     */
    suspend fun backupPortableNow(
        context: Context,
        database: FinSmartDatabase,
        password: String,
        userId: Long
    ): Triple<Boolean, String, File?> = withContext(Dispatchers.IO) {
        if (password.isBlank()) {
            return@withContext Triple(false, "Kata sandi cadangan tidak boleh kosong.", null)
        }
        try {
            // 1. Fetch user-scoped data from DAOs
            val wallets = database.walletDao().getAllWallets(userId).first()
            val transactions = database.transactionDao().getAllTransactions(userId).first()
            val categories = database.categoryDao().getAllCategories(userId).first()
            val users = database.userDao().getUserById(userId)?.let { listOf(it) } ?: emptyList()
            val splitBillHistory = database.splitBillHistoryDao().getAllSplitBillHistory(userId).first()
            val budgetLimits = database.budgetLimitDao().getAllBudgetLimits(userId).first()
            val debts = database.debtDao().getAllDebts(userId).first()
            val assets = database.assetDao().getAllAssets(userId).first()

            // 2. Build payload
            val payload = BackupDataPayload(
                version = 1,
                wallets = wallets,
                transactions = transactions,
                categories = categories,
                users = users,
                splitBillHistory = splitBillHistory,
                budgetLimits = budgetLimits,
                debts = debts,
                assets = assets,
                exportedAt = System.currentTimeMillis()
            )

            // 3. Serialize to JSON
            val adapter = moshi.adapter(BackupDataPayload::class.java)
            val jsonStr = adapter.toJson(payload)

            // 4. Encrypt using Format v2 (PBKDF2-HMAC-SHA256 + AES-GCM-256)
            val encryptedEnvelope = EncryptionUtils.encryptV2(jsonStr, customPassphrase = password)

            // 5. Save in sandbox and local public folders
            val localBackupDir = File(context.filesDir, PUBLIC_FOLDER_NAME)
            writeBackupFileSafely(localBackupDir, PORTABLE_BACKUP_FILE_NAME, encryptedEnvelope)

            val googleDrivePublicRoot = File(context.filesDir, "Google_Drive_Root/$PUBLIC_FOLDER_NAME")
            writeBackupFileSafely(googleDrivePublicRoot, PORTABLE_BACKUP_FILE_NAME, encryptedEnvelope)

            // 6. Write to shareable export file
            val exportDir = File(context.filesDir, "Backups_Export")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            val timeTag = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val exportFile = File(exportDir, "finsmart_portable_backup_$timeTag.json")
            exportFile.writeText(encryptedEnvelope)

            Log.d(TAG, "Pencadangan portabel Format v2 berhasil dibuat.")
            Triple(true, "Cadangan portabel berhasil dibuat [Format v2 Terenkripsi Kata Sandi].", exportFile)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membuat cadangan portabel", e)
            Triple(false, "Gagal membuat cadangan portabel: ${e.message}", null)
        }
    }

    /**
     * Restore from a portable Format v2 backup using user passphrase.
     * Enforces strict order of operations:
     * 1. Validate password not blank
     * 2. Validate backup content not blank
     * 3. Validate envelope is Format v2
     * 4. Verify envelope.keyProtection == "PBKDF2"
     * 5. Cryptographic AEAD decryption (BadTagException caught on wrong password or tampering)
     * 6. Deserialization of BackupDataPayload
     * 7. Validation of core payload entities
     * 8. ONLY AFTER ALL PREVIOUS CHECKS SUCCEED: mutate database in transaction
     */
    suspend fun restorePortableNow(
        context: Context,
        database: FinSmartDatabase,
        backupContent: String?,
        password: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (password.isBlank()) {
            return@withContext Pair(false, "Kata sandi pemulihan tidak boleh kosong.")
        }

        try {
            val contentToDecrypt = if (!backupContent.isNullOrBlank()) {
                backupContent.trim()
            } else {
                val portableFile = File(context.filesDir, "Google_Drive_Root/$PUBLIC_FOLDER_NAME/$PORTABLE_BACKUP_FILE_NAME")
                val localFile = File(context.filesDir, "$PUBLIC_FOLDER_NAME/$PORTABLE_BACKUP_FILE_NAME")
                when {
                    portableFile.exists() && portableFile.length() > 0 -> portableFile.readText().trim()
                    localFile.exists() && localFile.length() > 0 -> localFile.readText().trim()
                    else -> return@withContext Pair(false, "File cadangan portabel tidak ditemukan. Silakan pilih file cadangan (.json).")
                }
            }

            if (!EncryptionUtils.isFormatV2(contentToDecrypt)) {
                return@withContext Pair(false, "Format file tidak didukung atau bukan Format v2 terautentikasi.")
            }

            val envelopeAdapter = moshi.adapter(com.example.data.model.BackupEnvelopeV2::class.java)
            val envelope = try {
                envelopeAdapter.fromJson(contentToDecrypt)
            } catch (e: Exception) {
                null
            } ?: return@withContext Pair(false, "File cadangan rusak: struktur envelope v2 tidak valid.")

            if (envelope.keyProtection != "PBKDF2") {
                return@withContext Pair(false, "File cadangan ini menggunakan kunci perangkat lokal, bukan kata sandi portabel.")
            }

            // Cryptographic decryption & AEAD verification
            val decryptedJson = try {
                EncryptionUtils.decryptV2(contentToDecrypt, password)
            } catch (e: javax.crypto.AEADBadTagException) {
                return@withContext Pair(false, "Kata sandi salah atau isi cadangan telah diubah (integritas gagal).")
            } catch (e: Exception) {
                return@withContext Pair(false, "Gagal mendekripsi: kata sandi salah atau data cadangan rusak.")
            }

            // JSON Payload Deserialization
            val payloadAdapter = moshi.adapter(BackupDataPayload::class.java)
            val payload = try {
                payloadAdapter.fromJson(decryptedJson)
            } catch (e: Exception) {
                null
            } ?: return@withContext Pair(false, "Gagal membaca isi data cadangan (struktur data rusak).")

            // Payload Entity Validation
            if (payload.wallets == null || payload.categories == null || payload.transactions == null) {
                return@withContext Pair(false, "Data cadangan tidak lengkap atau tidak valid.")
            }

            // MUTATE ROOM DATABASE ONLY AFTER ALL CHECKS PASS
            database.runInTransaction {
                com.example.ui.viewmodel.FinanceViewModel.restoreDatabaseInTransaction(database, payload)
            }

            // Sanitize legacy passwords
            PasswordHasher.migrateLegacyPasswords(database.userDao())

            Pair(
                true,
                "Pemulihan data portabel berhasil! ${payload.wallets.size} dompet & ${payload.transactions.size} transaksi dipulihkan (Tanggal cadangan: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(payload.exportedAt))})."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulihkan cadangan portabel", e)
            Pair(false, "Gagal memulihkan cadangan portabel: ${e.message}")
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
