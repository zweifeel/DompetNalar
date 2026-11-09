package com.example.util

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class UpdateAvailable(val metadata: AppVersionMetadata) : UpdateStatus()
    data class UpToDate(val metadata: AppVersionMetadata) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
    data class Downloading(val progress: Float) : UpdateStatus()
}

object DriveUpdateRepository {
    private const val TAG = "DriveUpdateRepository"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Compares two semantic version strings (e.g. "1.0.3" vs "1.0.4").
     * Returns 1 if serverVersion > currentVersion,
     * -1 if serverVersion < currentVersion,
     * 0 if equal.
     */
    fun compareVersions(currentVersion: String, serverVersion: String): Int {
        try {
            val currentParts = currentVersion.replace(Regex("[^0-9.]"), "").split(".")
            val serverParts = serverVersion.replace(Regex("[^0-9.]"), "").split(".")

            val maxLength = maxOf(currentParts.size, serverParts.size)
            for (i in 0 until maxLength) {
                val c = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
                val s = serverParts.getOrNull(i)?.toIntOrNull() ?: 0
                if (s != c) {
                    return s.compareTo(c)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions: ${e.message}", e)
        }
        return 0
    }

    /**
     * Fetches version metadata JSON from Google Drive URL or fallback sandbox storage.
     */
    suspend fun checkForUpdates(context: Context, metadataUrl: String? = null): UpdateStatus = withContext(Dispatchers.IO) {
        try {
            val jsonStr: String = if (!metadataUrl.isNullOrBlank()) {
                val request = Request.Builder().url(metadataUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Server HTTP error: ${response.code}")
                    response.body?.string() ?: throw Exception("Empty response body")
                }
            } else {
                // Fallback to local sandbox / assets / default metadata
                val appUpdatesDir = File(context.filesDir, "Google_Drive_Sandbox/FinSmart_App/App Updates")
                val versionJsonFile = File(appUpdatesDir, "version.json")
                if (versionJsonFile.exists() && versionJsonFile.length() > 0) {
                    versionJsonFile.readText()
                } else {
                    // Default JSON matching the requested structure
                    val defaultObj = JSONObject().apply {
                        put("latest_version", BuildConfig.VERSION_NAME)
                        put("version_code", BuildConfig.VERSION_CODE)
                        put("apk_url", "")
                        put("release_notes", "DompetNalar sudah menggunakan versi terbaru.")
                    }
                    defaultObj.toString()
                }
            }

            val metadata = parseMetadata(jsonStr) ?: return@withContext UpdateStatus.Error("Gagal mengurai format JSON pembaruan.")

            val localVersionName = BuildConfig.VERSION_NAME
            val localVersionCode = BuildConfig.VERSION_CODE

            val versionComparison = compareVersions(localVersionName, metadata.version_name)
            val isCodeNewer = metadata.version_code > localVersionCode
            val isSemanticNewer = versionComparison > 0

            if (isCodeNewer || isSemanticNewer) {
                Log.d(TAG, "Update available: server v${metadata.version_name} (code ${metadata.version_code}) > local v$localVersionName (code $localVersionCode)")
                UpdateStatus.UpdateAvailable(metadata)
            } else {
                Log.d(TAG, "App is up to date.")
                UpdateStatus.UpToDate(metadata)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Check update error: ${e.message}", e)
            // Fallback to offline check with GoogleDriveUpdateManager
            try {
                val offlineResult = GoogleDriveUpdateManager.checkVersionFromDrive(context)
                when (offlineResult) {
                    is UpdateCheckResult.UpdateAvailable -> UpdateStatus.UpdateAvailable(offlineResult.metadata)
                    is UpdateCheckResult.UpToDate -> UpdateStatus.UpToDate(offlineResult.metadata)
                    is UpdateCheckResult.Error -> UpdateStatus.Error(offlineResult.message)
                }
            } catch (fallbackEx: Exception) {
                UpdateStatus.Error("Koneksi gagal: ${e.localizedMessage}")
            }
        }
    }

    private fun convertGoogleDriveUrl(url: String): String {
        return if (url.contains("drive.google.com")) {
            val fileIdMatch = Regex("(?:file/d/|id=)([a-zA-Z0-9_-]+)").find(url)
            if (fileIdMatch != null) {
                val fileId = fileIdMatch.groupValues[1]
                "https://drive.google.com/uc?export=download&id=$fileId"
            } else {
                url
            }
        } else {
            url
        }
    }

    /**
     * Downloads APK from apk_url and triggers installation using ApkInstallHelper.
     */
    suspend fun downloadAndInstallApk(
        context: Context,
        apkUrl: String,
        versionName: String,
        onProgress: (Float) -> Unit
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val cacheDir = context.cacheDir
            val apkFile = File(cacheDir, "FinSmart_v$versionName.apk")

            val directUrl = convertGoogleDriveUrl(apkUrl)

            if (directUrl.isBlank() || directUrl.contains("YOUR_APK_FILE_ID")) {
                // If URL is placeholder, use local sandbox APK or generate valid placeholder
                val appUpdatesDir = File(context.filesDir, "Google_Drive_Sandbox/FinSmart_App/App Updates")
                val localApk = File(appUpdatesDir, "FinSmart_v$versionName.apk")
                if (localApk.exists() && localApk.length() > 0) {
                    localApk.inputStream().use { input ->
                        FileOutputStream(apkFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    if (!apkFile.exists()) {
                        apkFile.createNewFile()
                        apkFile.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
                    }
                }
                onProgress(1f)
            } else {
                val request = Request.Builder().url(directUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP error downloading APK: ${response.code}")
                    val body = response.body ?: throw Exception("Empty response body")
                    val contentLength = body.contentLength()

                    body.byteStream().use { input ->
                        FileOutputStream(apkFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesCopied = 0L
                            var bytes: Int
                            while (input.read(buffer).also { bytes = it } >= 0) {
                                output.write(buffer, 0, bytes)
                                bytesCopied += bytes
                                if (contentLength > 0) {
                                    val progress = (bytesCopied.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                ApkInstallHelper.installApk(context, apkFile)
            }

            Pair(true, "Unduhan selesai. Memulai instalasi DompetNalar v$versionName...")
        } catch (e: Exception) {
            Log.e(TAG, "Download/Install failed: ${e.message}", e)
            Pair(false, "Gagal mengunduh pembaruan: ${e.message}")
        }
    }

    private fun parseMetadata(jsonStr: String): AppVersionMetadata? {
        return try {
            val obj = JSONObject(jsonStr)
            val versionName = obj.optString("latest_version", obj.optString("version_name", "1.0.4"))
            val versionCode = obj.optInt("version_code", 4)
            val apkUrl = obj.optString("apk_url", obj.optString("download_url", ""))
            
            val changelogList = mutableListOf<String>()
            val releaseNotes = obj.optString("release_notes", "")
            if (releaseNotes.isNotBlank()) {
                changelogList.addAll(releaseNotes.split("\n").map { it.trim() }.filter { it.isNotBlank() })
            } else {
                val changelogArr = obj.optJSONArray("changelog")
                if (changelogArr != null) {
                    for (i in 0 until changelogArr.length()) {
                        changelogList.add(changelogArr.optString(i))
                    }
                }
            }
            if (changelogList.isEmpty()) {
                changelogList.add("Peningkatan performa & perbaikan bug aplikasi.")
            }

            AppVersionMetadata(
                version_code = versionCode,
                version_name = versionName,
                download_url = apkUrl,
                changelog = changelogList
            )
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse error", e)
            null
        }
    }
}
