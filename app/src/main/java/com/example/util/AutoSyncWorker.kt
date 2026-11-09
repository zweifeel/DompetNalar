package com.example.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.FinSmartDatabase
import com.example.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class AutoSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            SessionManager.initialize(applicationContext)

            // Pastikan fitur auto sync aktif
            if (!GoogleDriveService.isAutoSyncEnabled(applicationContext)) {
                return Result.success()
            }

            val userId = SessionManager.getActiveUserId()
            if (userId == null || userId <= 0L) {
                // Tidak ada sesi pengguna aktif. Batalkan sinkronisasi latar belakang demi privasi.
                return Result.success()
            }

            val isStealth = try {
                SessionManager.isStealthModeActive()
            } catch (e: Exception) {
                true // Fail-safe
            }
            if (isStealth) {
                // Mode Stealth aktif: batalkan pekerjaan latar belakang tanpa retry instan
                return Result.success()
            }

            // Pastikan koneksi Google Drive aktif
            if (!GoogleDriveService.isConnected(applicationContext)) {
                return Result.success()
            }
            val connectedEmail = GoogleDriveService.getConnectedEmail(applicationContext)
            if (connectedEmail.isBlank()) {
                return Result.success()
            }

            val database = FinSmartDatabase.getDatabase(applicationContext, CoroutineScope(Dispatchers.IO))
            val activeUser = database.userDao().getUserById(userId)
            // Akun tamu (guest) tidak boleh melakukan backup cloud
            if (activeUser == null || activeUser.isGuest) {
                return Result.success()
            }

            val result = GoogleDriveService.backupNow(applicationContext, database, userId)
            if (result.first) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
