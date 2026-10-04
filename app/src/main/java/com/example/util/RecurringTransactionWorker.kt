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
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.Calendar
import java.util.UUID
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
            val title = inputData.getString("TITLE") ?: "Gaji Bulanan Otomatis"
            val amount = inputData.getDouble("AMOUNT", 0.0)
            val typeStr = inputData.getString("TYPE") ?: "INCOME"
            val category = inputData.getString("CATEGORY") ?: "Gaji & Penghasilan"
            val categoryIcon = inputData.getString("CATEGORY_ICON") ?: "payments"
            val walletId = inputData.getLong("WALLET_ID", 1L)
            val walletName = inputData.getString("WALLET_NAME") ?: "Rekening Utama"
            val dayOfMonth = inputData.getInt("DAY_OF_MONTH", 25)
            val intervalStr = inputData.getString("INTERVAL") ?: "MONTHLY"

            if (amount <= 0.0) return Result.success()

            val calendar = Calendar.getInstance()
            val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

            // For monthly, check if today is the scheduled day
            if (intervalStr == "MONTHLY" && currentDay != dayOfMonth) {
                // Not today, skip execution until scheduled day
                return Result.success()
            }

            val database = FinSmartDatabase.getDatabase(applicationContext, CoroutineScope(Dispatchers.IO))
            val transactionDao = database.transactionDao()
            val walletDao = database.walletDao()

            val txType = try { TransactionType.valueOf(typeStr) } catch (e: Exception) { TransactionType.INCOME }

            val newTx = TransactionItem(
                id = 0L,
                title = "$title (Auto Recurring)",
                amount = amount,
                type = txType,
                category = category,
                categoryIcon = categoryIcon,
                walletId = walletId,
                walletName = walletName,
                timestamp = System.currentTimeMillis(),
                note = "Pencatatan berulang otomatis oleh FinSmart Recurring Engine (Tgl $dayOfMonth)",
                tags = "#recurring #salary",
                accountMode = AccountMode.PERSONAL.name
            )

            transactionDao.insertTransaction(newTx)

            // Update wallet balance
            val currentWallet = walletDao.getWalletById(walletId)
            if (currentWallet != null) {
                val newBal = if (txType == TransactionType.INCOME) currentWallet.balance + amount else currentWallet.balance - amount
                walletDao.updateWallet(currentWallet.copy(balance = newBal.coerceAtLeast(0.0)))
            }

            // Trigger System Notification (isolated from financial operation)
            try {
                sendLocalNotification(
                    context = applicationContext,
                    title = "FinSmart Recurring Income 💰",
                    message = "Pemasukan rutin $title sebesar ${CurrencyFormatter.formatRupiah(amount)} berhasil dicatat ke $walletName!"
                )
            } catch (e: Exception) {
                // Silently isolate notification posting failure so it never fails the worker
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun sendLocalNotification(context: Context, title: String, message: String) {
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
                    "FinSmart Transaksi Berulang",
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

    companion object {
        fun scheduleRecurring(
            context: Context,
            title: String,
            amount: Double,
            type: TransactionType,
            category: String,
            categoryIcon: String,
            walletId: Long,
            walletName: String,
            interval: RecurringInterval,
            dayOfMonth: Int = 25
        ) {
            val data = androidx.work.Data.Builder()
                .putString("TITLE", title)
                .putDouble("AMOUNT", amount)
                .putString("TYPE", type.name)
                .putString("CATEGORY", category)
                .putString("CATEGORY_ICON", categoryIcon)
                .putLong("WALLET_ID", walletId)
                .putString("WALLET_NAME", walletName)
                .putString("INTERVAL", interval.name)
                .putInt("DAY_OF_MONTH", dayOfMonth)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<RecurringTransactionWorker>(1, TimeUnit.DAYS)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "recurring_tx_${walletId}_$dayOfMonth",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }
    }
}
