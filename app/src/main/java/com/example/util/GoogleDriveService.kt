package com.example.util

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.local.FinSmartDatabase
import com.example.data.model.BackupDataPayload
import com.example.data.model.BackupEnvelopeV2
import com.example.data.session.SessionManager
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed class DriveAuthResult {
    data class Success(val token: String) : DriveAuthResult()
    data class NeedsUserRecoverableAuth(val recoveryIntent: Intent) : DriveAuthResult()
    data class Error(val message: String) : DriveAuthResult()
}

/**
 * Enterprise Google Drive Service supporting:
 * 1. Real Google Drive v3 REST Cloud API synchronization (least-privilege `drive.file` scope).
 * 2. Authenticated Format v2 (AES-GCM-256) encryption with local offline caching and fallback.
 * 3. Atomic, user-scoped (DEF-04) backup and non-destructive restore.
 * 4. User Passphrase Portable Backups (PBKDF2-HMAC-SHA256).
 */
object GoogleDriveService {
    private const val TAG = "GoogleDriveService"
    private const val PREFS_NAME = "google_drive_prefs"

    // Least-privilege Google Drive Public Scope
    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val PUBLIC_FOLDER_NAME = "FinSmart_Backups"
    private const val BACKUP_FILE_NAME = "finsmart_backup.json"
    private const val PORTABLE_BACKUP_FILE_NAME = "finsmart_portable_backup.json"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Configuration keys
    private const val KEY_CONNECTED_EMAIL = "connected_email"
    private const val KEY_IS_CONNECTED = "is_connected"
    private const val KEY_LAST_BACKUP = "last_backup_timestamp"
    private const val KEY_AUTO_SYNC = "weekly_auto_sync"
    const val KEY_AUTO_BACKUP_INTERVAL_HOURS = "auto_backup_interval_hours"
    const val DEFAULT_AUTO_BACKUP_INTERVAL_HOURS = 24L
    const val AUTO_SYNC_WORK_NAME = "auto_drive_backup"

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

    fun getAutoBackupIntervalHours(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_AUTO_BACKUP_INTERVAL_HOURS, DEFAULT_AUTO_BACKUP_INTERVAL_HOURS)
    }

    fun setAutoBackupIntervalHours(context: Context, hours: Long) {
        val validHours = when (hours) {
            3L, 6L, 12L, 24L -> hours
            else -> 24L
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_AUTO_BACKUP_INTERVAL_HOURS, validHours).apply()
    }

    fun getNextBackupTime(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isAuto = prefs.getBoolean(KEY_AUTO_SYNC, false)
        if (!isAuto) return "Pencadangan otomatis nonaktif"
        val lastTimestamp = prefs.getLong(KEY_LAST_BACKUP, 0L)
        val intervalHours = getAutoBackupIntervalHours(context)
        val baseTime = if (lastTimestamp > 0L) lastTimestamp else System.currentTimeMillis()
        val nextTime = baseTime + (intervalHours * 3600000L)
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm 'WIB'", Locale("id", "ID"))
        return sdf.format(Date(nextTime))
    }

    fun isAutoSyncEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_SYNC, false)
    }

    fun connect(context: Context, email: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IS_CONNECTED, true)
            .putString(KEY_CONNECTED_EMAIL, email.trim())
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
     * Request an OAuth2 Bearer Access Token for the specified Google account using Google Play Services.
     * Strictly runs on [Dispatchers.IO] to avoid main-thread deadlocks (IllegalStateException).
     */
    suspend fun getDriveAccessToken(context: Context, email: String): DriveAuthResult = withContext(Dispatchers.IO) {
        if (email.isBlank()) {
            return@withContext DriveAuthResult.Error("Alamat email akun Google belum diatur.")
        }
        try {
            val account = Account(email.trim(), "com.google")
            val scope = "oauth2:$DRIVE_SCOPE"
            val token = GoogleAuthUtil.getToken(context, account, scope)
            DriveAuthResult.Success(token)
        } catch (e: UserRecoverableAuthException) {
            Log.i(TAG, "Izin Google Drive memerlukan persetujuan pengguna.")
            DriveAuthResult.NeedsUserRecoverableAuth(e.intent ?: Intent())
        } catch (e: GoogleAuthException) {
            Log.e(TAG, "GoogleAuthException: ${e.message}", e)
            DriveAuthResult.Error("Izin otorisasi Google Drive ditolak atau gagal: ${e.message}")
        } catch (e: IOException) {
            Log.e(TAG, "Koneksi jaringan terputus saat mengambil token Drive", e)
            DriveAuthResult.Error("Koneksi jaringan terputus saat menghubungi server Google: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Kesalahan otorisasi Drive tidak terduga", e)
            DriveAuthResult.Error("Kesalahan otorisasi Google Drive: ${e.message}")
        }
    }

    /**
     * Invalidate expired or revoked access token cache from Google Play Services.
     */
    fun clearDriveAccessToken(context: Context, token: String) {
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (e: Exception) {
            Log.w(TAG, "Gagal membersihkan cache token: ${e.message}")
        }
    }

    // --- Google Drive REST API v3 Methods ---

    private fun queryDriveFile(token: String, query: String): Pair<String?, Long?> {
        val encodedQ = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQ&spaces=drive&fields=files(id,name,modifiedTime)&pageSize=1"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    Log.e(TAG, "Query Drive error [${response.code}]: $err")
                    return Pair(null, null)
                }
                val bodyStr = response.body?.string() ?: return Pair(null, null)
                val json = JSONObject(bodyStr)
                val filesArray = json.optJSONArray("files") ?: return Pair(null, null)
                if (filesArray.length() > 0) {
                    val fileObj = filesArray.getJSONObject(0)
                    val id = fileObj.getString("id")
                    val modifiedTimeStr = fileObj.optString("modifiedTime", "")
                    val timestamp = parseIso8601ToMillis(modifiedTimeStr)
                    Pair(id, timestamp)
                } else {
                    Pair(null, null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kesalahan jaringan saat query file Drive", e)
            Pair(null, null)
        }
    }

    private fun findOrCreateDriveFolder(token: String, folderName: String): String? {
        val existing = queryDriveFile(token, "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
        if (existing.first != null) {
            return existing.first
        }

        val createFolderJson = JSONObject().apply {
            put("name", folderName)
            put("mimeType", "application/vnd.google-apps.folder")
        }

        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files")
            .addHeader("Authorization", "Bearer $token")
            .post(createFolderJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Gagal membuat folder Drive [${response.code}]: ${response.body?.string()}")
                    null
                } else {
                    val bodyStr = response.body?.string() ?: return null
                    JSONObject(bodyStr).optString("id").takeIf { it.isNotBlank() }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kesalahan saat membuat folder Drive", e)
            null
        }
    }

    private fun uploadFileToDriveCloud(
        token: String,
        folderId: String?,
        fileName: String,
        content: String
    ): Pair<Boolean, String> {
        val q = if (folderId != null) {
            "name = '$fileName' and '$folderId' in parents and trashed = false"
        } else {
            "name = '$fileName' and trashed = false"
        }
        val existingFile = queryDriveFile(token, q)

        return try {
            if (existingFile.first != null) {
                // Update existing file content via PATCH media
                val fileId = existingFile.first!!
                val updateUrl = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
                val request = Request.Builder()
                    .url(updateUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .patch(content.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Pair(true, "File cadangan di Google Drive berhasil diperbarui.")
                    } else {
                        val err = response.body?.string() ?: "HTTP ${response.code}"
                        Pair(false, "Gagal memperbarui file di Google Drive: $err")
                    }
                }
            } else {
                // Upload new file via multipart POST
                val metadataJson = JSONObject().apply {
                    put("name", fileName)
                    put("mimeType", "application/json")
                    if (folderId != null) {
                        put("parents", JSONArray().put(folderId))
                    }
                }

                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "metadata",
                        null,
                        metadataJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
                    )
                    .addFormDataPart(
                        "file",
                        fileName,
                        content.toRequestBody("application/json; charset=UTF-8".toMediaType())
                    )
                    .build()

                val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
                val request = Request.Builder()
                    .url(uploadUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .post(multipartBody)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Pair(true, "File cadangan berhasil diunggah ke Google Drive.")
                    } else {
                        val err = response.body?.string() ?: "HTTP ${response.code}"
                        Pair(false, "Gagal mengunggah file ke Google Drive: $err")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kesalahan jaringan saat upload ke Drive", e)
            Pair(false, "Kesalahan jaringan saat mengunggah ke Google Drive: ${e.message}")
        }
    }

    private fun downloadFileFromDriveCloud(token: String, fileId: String): Pair<Boolean, String?> {
        val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val request = Request.Builder()
            .url(downloadUrl)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val content = response.body?.string()
                    Pair(true, content)
                } else {
                    Pair(false, null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kesalahan saat mengunduh dari Google Drive", e)
            Pair(false, null)
        }
    }

    private fun parseIso8601ToMillis(isoString: String): Long? {
        if (isoString.isBlank()) return null
        return try {
            val clean = isoString.replace("Z", "+0000")
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
            sdf.parse(clean)?.time
        } catch (_: Exception) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
                sdf.parse(isoString.replace("Z", "+0000"))?.time
            } catch (_: Exception) {
                null
            }
        }
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

        tempFile.writeText(content)

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
     * persist to local offline storage, and sync to real Google Drive Cloud if connected.
     */
    suspend fun backupNow(context: Context, database: FinSmartDatabase, userId: Long): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val isStealth = try {
            SessionManager.isStealthModeActive()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memverifikasi status keamanan saat pencadangan: gagal-aman aktif.", e)
            true
        }
        if (isStealth) {
            Log.w(TAG, "Pencadangan ditolak: Mode Stealth sedang aktif demi perlindungan privasi data.")
            return@withContext Pair(false, "Pencadangan ditolak: Mode Stealth sedang aktif untuk melindungi privasi data.")
        }
        try {
            // 1. Fetch user-scoped data from DAOs under Read Permit
            val payload = database.writeCoordinator.withReadPermit {
                val wallets = database.walletDao().getAllWallets(userId).first()
                val transactions = database.transactionDao().getAllTransactions(userId).first()
                val categories = database.categoryDao().getAllCategories(userId).first()
                val users = database.userDao().getUserById(userId)?.let { listOf(it) } ?: emptyList()
                val splitBillHistory = database.splitBillHistoryDao().getAllSplitBillHistory(userId).first()
                val budgetLimits = database.budgetLimitDao().getAllBudgetLimits(userId).first()
                val debts = database.debtDao().getAllDebts(userId).first()
                val assets = database.assetDao().getAllAssets(userId).first()
                val installmentPlans = database.installmentPlanDao().getAllInstallmentPlans(userId).first()

                BackupDataPayload(
                    version = 1,
                    wallets = wallets,
                    transactions = transactions,
                    categories = categories,
                    users = users,
                    splitBillHistory = splitBillHistory,
                    budgetLimits = budgetLimits,
                    debts = debts,
                    assets = assets,
                    installmentPlans = installmentPlans,
                    exportedAt = System.currentTimeMillis()
                )
            }

            // 3. Serialize to JSON
            val adapter = moshi.adapter(BackupDataPayload::class.java)
            val jsonStr = adapter.toJson(payload)

            // 4. Encrypt using Format v2 (AES-GCM-256 authenticated AEAD envelope)
            val encryptedData = EncryptionUtils.encrypt(jsonStr)

            // 5. Always save locally as secure fallback using atomic write
            val localBackupDir = File(context.filesDir, PUBLIC_FOLDER_NAME)
            writeBackupFileSafely(localBackupDir, BACKUP_FILE_NAME, encryptedData)

            // Also keep sandbox copy for backwards test compatibility
            val googleDrivePublicRoot = File(context.filesDir, "Google_Drive_Root/$PUBLIC_FOLDER_NAME")
            writeBackupFileSafely(googleDrivePublicRoot, BACKUP_FILE_NAME, encryptedData)

            // Also persist to external app storage so backup survives process recreation/sandbox resets
            try {
                val extDir = context.getExternalFilesDir(null)
                if (extDir != null) {
                    val persistentExtDir = File(extDir, PUBLIC_FOLDER_NAME)
                    writeBackupFileSafely(persistentExtDir, BACKUP_FILE_NAME, encryptedData)
                }
                val extDocs = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
                if (extDocs != null && (extDocs.exists() || extDocs.mkdirs())) {
                    val publicDocsDir = File(extDocs, PUBLIC_FOLDER_NAME)
                    writeBackupFileSafely(publicDocsDir, BACKUP_FILE_NAME, encryptedData)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Optional external persistent backup note: ${e.message}")
            }

            // 6. Cloud Google Drive Upload if connected
            var cloudStatusMessage = ""
            val isDriveConn = isConnected(context)
            val connectedEmail = getConnectedEmail(context)

            if (isDriveConn && connectedEmail.isNotBlank()) {
                val authResult = getDriveAccessToken(context, connectedEmail)
                when (authResult) {
                    is DriveAuthResult.Success -> {
                        val folderId = findOrCreateDriveFolder(authResult.token, PUBLIC_FOLDER_NAME)
                        val uploadResult = uploadFileToDriveCloud(authResult.token, folderId, BACKUP_FILE_NAME, encryptedData)
                        if (uploadResult.first) {
                            cloudStatusMessage = " dan berhasil diunggah ke Google Drive Cloud ($connectedEmail)"
                        } else {
                            cloudStatusMessage = " (Tersimpan lokal; Gagal sinkronisasi Drive: ${uploadResult.second})"
                        }
                    }
                    is DriveAuthResult.NeedsUserRecoverableAuth -> {
                        cloudStatusMessage = " (Tersimpan lokal; Izin Google Drive perlu disetujui ulang di Pengaturan)"
                    }
                    is DriveAuthResult.Error -> {
                        cloudStatusMessage = " (Tersimpan lokal; Gagal otorisasi Drive: ${authResult.message})"
                    }
                }
            } else {
                cloudStatusMessage = " (Tersimpan di penyimpanan lokal yang aman)"
            }

            // Save last backup timestamp
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_LAST_BACKUP, System.currentTimeMillis()).apply()

            Log.d(TAG, "Pencadangan Format v2 (AES-GCM) berhasil diproses.")
            Pair(true, "Pencadangan berhasil dibuat [Format v2 Terautentikasi]$cloudStatusMessage")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal melakukan pencadangan", e)
            Pair(false, "Gagal mencadangkan data: ${e.message}")
        }
    }

    /**
     * Restore data with priority on Google Drive Cloud (if connected), falling back
     * to verified local backup storage. Scoped strictly to [userId] (DEF-04).
     */
    suspend fun restoreNow(context: Context, database: FinSmartDatabase, userId: Long): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val isStealth = try {
            SessionManager.isStealthModeActive()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memverifikasi status keamanan saat pemulihan: gagal-aman aktif.", e)
            true
        }
        if (isStealth) {
            Log.w(TAG, "Pemulihan ditolak: Mode Stealth sedang aktif demi perlindungan privasi data.")
            return@withContext Pair(false, "Pemulihan data ditolak: Mode Stealth sedang aktif untuk melindungi privasi data.")
        }
        if (userId <= 0L) {
            return@withContext Pair(false, "Gagal memulihkan: Sesi pengguna tidak aktif.")
        }
        val activeUser = database.userDao().getUserById(userId)
        if (activeUser == null) {
            return@withContext Pair(false, "Gagal memulihkan: Akun pengguna tidak ditemukan di database.")
        }

        try {
            var encryptedContent: String? = null
            var sourceLabel = ""

            // 1. Try to fetch from real Google Drive Cloud first if connected
            val isDriveConn = isConnected(context)
            val connectedEmail = getConnectedEmail(context)
            var driveAuthNotice: String? = null

            if (isDriveConn && connectedEmail.isNotBlank()) {
                val authResult = getDriveAccessToken(context, connectedEmail)
                when (authResult) {
                    is DriveAuthResult.NeedsUserRecoverableAuth -> {
                        driveAuthNotice = "Otorisasi Google Drive memerlukan persetujuan ulang di pengaturan."
                    }
                    is DriveAuthResult.Error -> {
                        driveAuthNotice = "Gagal otorisasi Google Drive: ${authResult.message}"
                    }
                    is DriveAuthResult.Success -> {
                        var token = authResult.token
                        val folderId = findOrCreateDriveFolder(token, PUBLIC_FOLDER_NAME)
                        val qFolder = if (folderId != null) {
                            "name = '$BACKUP_FILE_NAME' and '$folderId' in parents and trashed = false"
                        } else null

                        var fileInfo = if (qFolder != null) queryDriveFile(token, qFolder) else Pair(null, null)

                        // Fallback: Cari di seluruh Drive yang dapat diakses aplikasi jika tidak ditemukan di dalam subfolder
                        if (fileInfo.first == null) {
                            fileInfo = queryDriveFile(token, "name = '$BACKUP_FILE_NAME' and trashed = false")
                        }

                        // Jika token kadaluarsa atau error jaringan, coba clear token dan retry 1 kali
                        if (fileInfo.first == null) {
                            clearDriveAccessToken(context, token)
                            val retryAuth = getDriveAccessToken(context, connectedEmail)
                            if (retryAuth is DriveAuthResult.Success) {
                                token = retryAuth.token
                                val retryFolderId = findOrCreateDriveFolder(token, PUBLIC_FOLDER_NAME)
                                val qRetry = if (retryFolderId != null) "name = '$BACKUP_FILE_NAME' and '$retryFolderId' in parents and trashed = false" else null
                                fileInfo = if (qRetry != null) queryDriveFile(token, qRetry) else Pair(null, null)
                                if (fileInfo.first == null) {
                                    fileInfo = queryDriveFile(token, "name = '$BACKUP_FILE_NAME' and trashed = false")
                                }
                            }
                        }

                        if (fileInfo.first != null) {
                            val downloadResult = downloadFileFromDriveCloud(token, fileInfo.first!!)
                            if (downloadResult.first && !downloadResult.second.isNullOrBlank()) {
                                encryptedContent = downloadResult.second
                                sourceLabel = "Google Drive Cloud ($connectedEmail)"
                            }
                        }
                    }
                }
            }

            // 2. If cloud content not available, fall back to verified local backup
            if (encryptedContent.isNullOrBlank()) {
                val googleDrivePublicFile = File(context.filesDir, "Google_Drive_Root/$PUBLIC_FOLDER_NAME/$BACKUP_FILE_NAME")
                val localPublicFile = File(context.filesDir, "$PUBLIC_FOLDER_NAME/$BACKUP_FILE_NAME")
                val externalAppFile = try {
                    val ext = context.getExternalFilesDir(null)
                    if (ext != null) File(ext, "$PUBLIC_FOLDER_NAME/$BACKUP_FILE_NAME") else null
                } catch (_: Exception) { null }
                val publicDocsFile = try {
                    val docs = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
                    if (docs != null) File(docs, "$PUBLIC_FOLDER_NAME/$BACKUP_FILE_NAME") else null
                } catch (_: Exception) { null }

                val targetFile = when {
                    localPublicFile.exists() && localPublicFile.length() > 0 -> localPublicFile
                    googleDrivePublicFile.exists() && googleDrivePublicFile.length() > 0 -> googleDrivePublicFile
                    externalAppFile != null && externalAppFile.exists() && externalAppFile.length() > 0 -> externalAppFile
                    publicDocsFile != null && publicDocsFile.exists() && publicDocsFile.length() > 0 -> publicDocsFile
                    else -> {
                        val legacyDir = File(context.filesDir, "FinSmart_App/Backups")
                        val legacyFiles = legacyDir.listFiles { _, name -> name.endsWith(".json") }
                        legacyFiles?.maxByOrNull { it.lastModified() }
                    }
                }

                if (targetFile != null && targetFile.exists() && targetFile.length() > 0) {
                    encryptedContent = targetFile.readText()
                    sourceLabel = "Penyimpanan Lokal ($PUBLIC_FOLDER_NAME)"
                }
            }

            if (encryptedContent.isNullOrBlank()) {
                val detailMsg = if (driveAuthNotice != null) " [$driveAuthNotice]" else ""
                return@withContext Pair(
                    false,
                    "Tidak ditemukan file cadangan '$BACKUP_FILE_NAME' baik di Google Drive Cloud maupun penyimpanan lokal.$detailMsg"
                )
            }

            // 3. Identify format
            val isV2 = EncryptionUtils.isFormatV2(encryptedContent)
            val formatLabel = if (isV2) "Format v2 (AES-GCM Terautentikasi)" else "Format v1 (Legacy AES-CBC)"

            // 4. Decrypt with auto-dispatch
            val decryptedJson = EncryptionUtils.decrypt(encryptedContent)

            // 5. Deserialize with Moshi
            val adapter = moshi.adapter(BackupDataPayload::class.java)
            val payload = adapter.fromJson(decryptedJson)
                ?: return@withContext Pair(false, "Gagal mengurai isi data cadangan $BACKUP_FILE_NAME.")

            // 6. Restore tables under Exclusive Maintenance mode (DEF-04 & P0-F)
            try {
                database.writeCoordinator.withExclusiveMaintenance {
                    com.example.ui.viewmodel.FinanceViewModel.restoreUserDataInTransaction(database, payload, userId)
                    PasswordHasher.migrateLegacyPasswords(database.userDao())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gagal memulihkan data: ${e.message}")
                return@withContext Pair(false, "Gagal memulihkan data: ${e.message}")
            }

            // 8. Non-destructive auto-upgrade: If restored from legacy v1, upgrade local backup to v2
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
            } else {
                // Cache latest verified copy locally
                val localBackupDir = File(context.filesDir, PUBLIC_FOLDER_NAME)
                writeBackupFileSafely(localBackupDir, BACKUP_FILE_NAME, encryptedContent)
            }

            Pair(
                true,
                "Pemulihan data berhasil [$formatLabel dari $sourceLabel]! ${payload.wallets.size} dompet & ${payload.transactions.size} transaksi dipulihkan (Tanggal: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(payload.exportedAt))})"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulihkan data", e)
            val friendlyMsg = when {
                e is javax.crypto.AEADBadTagException || e.cause is javax.crypto.AEADBadTagException || (e.message?.contains("Tag mismatch", ignoreCase = true) == true) ->
                    "Gagal memulihkan data: Kunci perangkat tidak cocok (cadangan kemungkinan berasal dari perangkat atau instalasi lain). Silakan gunakan opsi 'Pulihkan Cadangan Portabel' dengan kata sandi Anda."
                e.message?.contains("Kata sandi cadangan diperlukan", ignoreCase = true) == true ->
                    "Gagal memulihkan data: File cadangan ini dilindungi kata sandi. Silakan gunakan opsi 'Pulihkan Cadangan Portabel' untuk memasukkan kata sandi."
                else -> "Gagal memulihkan data: ${e.message ?: "Format atau kunci cadangan tidak valid"}"
            }
            Pair(false, friendlyMsg)
        }
    }

    /**
     * Export all database contents, encrypt with Format v2 using a custom user passphrase
     * via PBKDF2-HMAC-SHA256 (100,000 iterations, 16-byte random salt, AES-256-GCM AEAD).
     */
    suspend fun backupPortableNow(
        context: Context,
        database: FinSmartDatabase,
        password: String,
        userId: Long
    ): Triple<Boolean, String, File?> = withContext(Dispatchers.IO) {
        val isStealth = try {
            SessionManager.isStealthModeActive()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memverifikasi status keamanan saat pencadangan portabel: gagal-aman aktif.", e)
            true
        }
        if (isStealth) {
            Log.w(TAG, "Pencadangan portabel ditolak: Mode Stealth sedang aktif demi perlindungan privasi data.")
            return@withContext Triple(false, "Pencadangan portabel ditolak: Mode Stealth sedang aktif untuk melindungi privasi data.", null)
        }
        if (password.isBlank()) {
            return@withContext Triple(false, "Kata sandi cadangan tidak boleh kosong.", null)
        }
        try {
            val payload = database.writeCoordinator.withReadPermit {
                val wallets = database.walletDao().getAllWallets(userId).first()
                val transactions = database.transactionDao().getAllTransactions(userId).first()
                val categories = database.categoryDao().getAllCategories(userId).first()
                val users = database.userDao().getUserById(userId)?.let { listOf(it) } ?: emptyList()
                val splitBillHistory = database.splitBillHistoryDao().getAllSplitBillHistory(userId).first()
                val budgetLimits = database.budgetLimitDao().getAllBudgetLimits(userId).first()
                val debts = database.debtDao().getAllDebts(userId).first()
                val assets = database.assetDao().getAllAssets(userId).first()
                val installmentPlans = database.installmentPlanDao().getAllInstallmentPlans(userId).first()

                BackupDataPayload(
                    version = 1,
                    wallets = wallets,
                    transactions = transactions,
                    categories = categories,
                    users = users,
                    splitBillHistory = splitBillHistory,
                    budgetLimits = budgetLimits,
                    debts = debts,
                    assets = assets,
                    installmentPlans = installmentPlans,
                    exportedAt = System.currentTimeMillis()
                )
            }

            val adapter = moshi.adapter(BackupDataPayload::class.java)
            val jsonStr = adapter.toJson(payload)

            val encryptedEnvelope = EncryptionUtils.encryptV2(jsonStr, customPassphrase = password)

            val localBackupDir = File(context.filesDir, PUBLIC_FOLDER_NAME)
            writeBackupFileSafely(localBackupDir, PORTABLE_BACKUP_FILE_NAME, encryptedEnvelope)

            val googleDrivePublicRoot = File(context.filesDir, "Google_Drive_Root/$PUBLIC_FOLDER_NAME")
            writeBackupFileSafely(googleDrivePublicRoot, PORTABLE_BACKUP_FILE_NAME, encryptedEnvelope)

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
     */
    suspend fun restorePortableNow(
        context: Context,
        database: FinSmartDatabase,
        backupContent: String?,
        password: String,
        userId: Long
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val isStealth = try {
            SessionManager.isStealthModeActive()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memverifikasi status keamanan saat pemulihan portabel: gagal-aman aktif.", e)
            true
        }
        if (isStealth) {
            Log.w(TAG, "Pemulihan portabel ditolak: Mode Stealth sedang aktif demi perlindungan privasi data.")
            return@withContext Pair(false, "Pemulihan portabel ditolak: Mode Stealth sedang aktif untuk melindungi privasi data.")
        }
        if (userId <= 0L) {
            return@withContext Pair(false, "Gagal memulihkan: Sesi pengguna tidak aktif.")
        }
        val activeUser = database.userDao().getUserById(userId)
        if (activeUser == null) {
            return@withContext Pair(false, "Gagal memulihkan: Akun pengguna tidak ditemukan di database.")
        }
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

            val envelopeAdapter = moshi.adapter(BackupEnvelopeV2::class.java)
            val envelope = try {
                envelopeAdapter.fromJson(contentToDecrypt)
            } catch (e: Exception) {
                null
            } ?: return@withContext Pair(false, "File cadangan rusak: struktur envelope v2 tidak valid.")

            if (envelope.keyProtection != "PBKDF2") {
                return@withContext Pair(false, "File cadangan ini menggunakan kunci perangkat lokal, bukan kata sandi portabel.")
            }

            val decryptedJson = try {
                EncryptionUtils.decryptV2(contentToDecrypt, password)
            } catch (e: javax.crypto.AEADBadTagException) {
                return@withContext Pair(false, "Kata sandi salah atau isi cadangan telah diubah (integritas gagal).")
            } catch (e: Exception) {
                return@withContext Pair(false, "Gagal mendekripsi: kata sandi salah atau data cadangan rusak.")
            }

            val payloadAdapter = moshi.adapter(BackupDataPayload::class.java)
            val payload = try {
                payloadAdapter.fromJson(decryptedJson)
            } catch (e: Exception) {
                null
            } ?: return@withContext Pair(false, "Gagal membaca isi data cadangan (struktur data rusak).")

            if (payload.wallets == null || payload.categories == null || payload.transactions == null) {
                return@withContext Pair(false, "Data cadangan tidak lengkap atau tidak valid.")
            }

            try {
                database.writeCoordinator.withExclusiveMaintenance {
                    com.example.ui.viewmodel.FinanceViewModel.restoreUserDataInTransaction(database, payload, userId)
                    PasswordHasher.migrateLegacyPasswords(database.userDao())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gagal memulihkan cadangan portabel: ${e.message}")
                return@withContext Pair(false, "Gagal memulihkan cadangan portabel: ${e.message}")
            }

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
            onComplete(true, "Laporan berhasil diarsipkan (DompetNalar_Reports/${reportFile.name})")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengunggah laporan", e)
            onComplete(false, "Gagal mengunggah laporan: ${e.message}")
        }
    }

    fun scheduleAutoBackup(context: Context, enabled: Boolean, intervalHours: Long? = null) {
        val workManager = androidx.work.WorkManager.getInstance(context)
        val targetHours = intervalHours ?: getAutoBackupIntervalHours(context)
        setAutoBackupIntervalHours(context, targetHours)
        setAutoSyncEnabled(context, enabled)

        workManager.cancelUniqueWork("weekly_auto_sync")
        workManager.cancelUniqueWork(AUTO_SYNC_WORK_NAME)

        if (enabled) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            val syncRequest = androidx.work.PeriodicWorkRequestBuilder<AutoSyncWorker>(
                targetHours, java.util.concurrent.TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                AUTO_SYNC_WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
            Log.i(TAG, "Pencadangan otomatis dijadwalkan setiap $targetHours jam.")
        } else {
            Log.i(TAG, "Pencadangan otomatis dinonaktifkan.")
        }
    }

    fun cancelAutoSync(context: Context) {
        val workManager = androidx.work.WorkManager.getInstance(context)
        workManager.cancelUniqueWork("weekly_auto_sync")
        workManager.cancelUniqueWork(AUTO_SYNC_WORK_NAME)
    }

    fun scheduleWeeklySync(context: Context, enabled: Boolean) {
        scheduleAutoBackup(context, enabled, getAutoBackupIntervalHours(context))
    }
}
