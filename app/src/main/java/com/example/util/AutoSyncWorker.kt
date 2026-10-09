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
            val userId = SessionManager.getActiveUserId()
            if (userId == null || userId <= 0L) {
                // DEF-03: No authenticated user session active. Skip background sync safely.
                return Result.success()
            }
            val database = FinSmartDatabase.getDatabase(applicationContext, CoroutineScope(Dispatchers.IO))
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
