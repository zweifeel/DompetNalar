package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.hasKeyWithValueOfType
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.RecurringSchedule
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class RecurringInterval(val label: String) {
    DAILY("Harian"),
    WEEKLY("Mingguan"),
    MONTHLY("Bulanan (Hari Gajian / Tanggal Rutin)")
}

class RecurringTransactionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val userId = inputData.getLong("USER_ID", -1L)
            if (userId <= 0L) {
                // DEF-03: Missing or unauthenticated user context must fail safely without touching financial data
                return Result.failure()
            }

            val database = FinSmartDatabase.getDatabase(applicationContext, CoroutineScope(Dispatchers.IO))
            val scheduleId = inputData.getString("SCHEDULE_ID")

            if (!scheduleId.isNullOrBlank()) {
                val expectedVersion = if (inputData.hasKeyWithValueOfType<Int>("VERSION")) {
                    inputData.getInt("VERSION", 1)
                } else null

                when (val result = RecurringScheduleEvaluator.executeScheduleTransaction(database, scheduleId, userId, expectedVersion)) {
                    is RecurringExecutionResult.Executed -> {
                        // Trigger System Notification POST-COMMIT ONLY
                        val isStealth = try { SessionManager.isStealthModeActive() } catch (e: Exception) { true }
                        if (!isStealth) {
                            try {
                                sendLocalNotification(
                                    context = applicationContext,
                                    title = "DompetNalar Transaksi Berulang 💰",
                                    message = "Transaksi rutin ${result.title} sebesar ${CurrencyFormatter.formatRupiah(result.amount)} berhasil dicatat ke ${result.walletName}!"
                                )
                            } catch (e: Exception) {
                                // Silently isolate notification posting failure so it never fails the worker
                            }
                        }
                        Result.success()
                    }
                    is RecurringExecutionResult.AlreadyExecuted -> Result.success()
                    is RecurringExecutionResult.NotDue -> Result.success()
                    is RecurringExecutionResult.StaleVersion -> Result.success()
                    is RecurringExecutionResult.ScheduleDisabled -> Result.success()
                    is RecurringExecutionResult.Failed -> Result.retry()
                }
            } else {
                val title = inputData.getString("TITLE")
                val walletId = inputData.getLong("WALLET_ID", -1L)
                val amount = inputData.getDouble("AMOUNT", 0.0)
                if (title != null && walletId > 0L && amount > 0.0) {
                    val wallet = database.walletDao().getWalletById(walletId, userId)
                        ?: return Result.failure()

                    if (wallet.currencyCode == null) {
                        // State 2: Currency is not yet assigned by user. Financial mutation strictly forbidden.
                        return Result.failure()
                    }

                    val typeStr = inputData.getString("TYPE") ?: "INCOME"
                    val txType = try { TransactionType.valueOf(typeStr) } catch (e: Exception) { TransactionType.INCOME }
                    val dayOfMonth = inputData.getInt("DAY_OF_MONTH", Calendar.getInstance().get(Calendar.DAY_OF_MONTH))
                    val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                    val intervalStr = inputData.getString("INTERVAL") ?: "MONTHLY"
                    if (intervalStr == "MONTHLY" && currentDay != dayOfMonth) {
                        return Result.success()
                    }

                    val repository = com.example.data.repository.FinanceRepository(database.walletDao(), database)
                    val newTx = TransactionItem(
                        id = 0L,
                        title = "$title (Auto Recurring)",
                        amount = amount,
                        type = txType,
                        category = inputData.getString("CATEGORY") ?: "Gaji & Penghasilan",
                        categoryIcon = inputData.getString("CATEGORY_ICON") ?: "payments",
                        walletId = walletId,
                        walletName = inputData.getString("WALLET_NAME") ?: wallet.name,
                        timestamp = System.currentTimeMillis(),
                        accountMode = AccountMode.PERSONAL.name,
                        userId = userId
                    )
                    repository.insertTransaction(userId, newTx)
                    Result.success()
                } else {
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        // Notification helper
        fun sendLocalNotification(context: Context, title: String, message: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPerm) return
            }

            val channelId = "finsmart_recurring_channel"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "DompetNalar Transaksi Berulang",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifikasi transaksi berulang otomatis (Gaji / Rutin)"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            // Silently catch to prevent notification delivery issues from disrupting worker
        }
    }

        fun scheduleSchedule(
            context: Context,
            schedule: RecurringSchedule
        ) {
            val data = androidx.work.Data.Builder()
                .putLong("USER_ID", schedule.userId)
                .putString("SCHEDULE_ID", schedule.id)
                .putInt("VERSION", schedule.version)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<RecurringTransactionWorker>(1, TimeUnit.DAYS)
                .setInputData(data)
                .build()

            try { WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "recurring_schedule_${schedule.id}",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest) } catch (e: Throwable) { /* ignore in tests */ }
        }


    }
}
