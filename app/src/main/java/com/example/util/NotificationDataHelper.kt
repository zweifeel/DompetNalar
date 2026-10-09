package com.example.util

import com.example.data.model.BudgetLimitItem
import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPlanItem
import com.example.data.model.TransactionItem
import com.example.data.model.Wallet
import com.example.ui.components.NotificationItem
import java.util.Calendar

object NotificationDataHelper {

    /**
     * Synthesizes notification items reactively from existing live application state.
     * Pure Kotlin function: zero UI code, zero database calls, zero side-effects.
     */
    fun generateNotifications(
        wallets: List<Wallet>,
        debts: List<DebtItem>,
        budgetLimits: List<BudgetLimitItem>,
        currentMonthExpenses: List<TransactionItem>,
        recentTransactions: List<TransactionItem> = emptyList(),
        installmentPlans: List<InstallmentPlanItem> = emptyList(),
        dailyStreak: Int = 1,
        calendar: Calendar = Calendar.getInstance()
    ): List<NotificationItem> {
        val notifications = mutableListOf<NotificationItem>()

        // 1. Low Balance Warnings (wallet <= Rp50.000 or <= 0)
        wallets.forEach { wallet ->
            if (wallet.balance <= 0.0) {
                notifications.add(
                    NotificationItem(
                        title = "Saldo Habis: ${wallet.name}",
                        message = "Saldo pada ${wallet.name} bernilai ${CurrencyFormatter.formatRupiah(wallet.balance)}. Harap segera isi ulang saldo rekening.",
                        timeAgo = "Hari ini",
                        isWarning = true
                    )
                )
            } else if (wallet.balance <= 50_000.0) {
                notifications.add(
                    NotificationItem(
                        title = "Peringatan Saldo Rendah: ${wallet.name}",
                        message = "Saldo pada ${wallet.name} tersisa ${CurrencyFormatter.formatRupiah(wallet.balance)}, berada di bawah batas minimum Rp50.000.",
                        timeAgo = "Hari ini",
                        isWarning = true
                    )
                )
            }
        }

        // 2. Debt Due-Date & Overdue Reminders
        val today = calendar.get(Calendar.DAY_OF_MONTH)
        debts.forEach { debt ->
            // Paid debts (amount <= 0.0) must not generate alerts
            if (debt.amount <= 0.0) return@forEach

            val dueDay = Regex("\\d+").find(debt.dueDate)?.value?.toIntOrNull()
            if (dueDay != null) {
                val paymentAmount = if (debt.minMonthlyPayment > 0.0) debt.minMonthlyPayment else debt.amount
                val formattedAmount = CurrencyFormatter.formatRupiah(paymentAmount)

                if (dueDay == today) {
                    notifications.add(
                        NotificationItem(
                            title = "Jatuh Tempo Hari Ini: ${debt.name}",
                            message = "Tagihan ${debt.name} sebesar $formattedAmount jatuh tempo hari ini (${debt.dueDate}). Segera lakukan pembayaran!",
                            timeAgo = "Hari ini",
                            isWarning = true
                        )
                    )
                } else if (dueDay in (today + 1)..(today + 3)) {
                    val daysLeft = dueDay - today
                    notifications.add(
                        NotificationItem(
                            title = "Pengingat Hutang: ${debt.name}",
                            message = "Tagihan ${debt.name} jatuh tempo dalam $daysLeft hari (${debt.dueDate}). Siapkan dana pembayaran sebesar $formattedAmount.",
                            timeAgo = "$daysLeft hari lagi",
                            isWarning = true
                        )
                    )
                } else if (today > dueDay && (today - dueDay) in 1..7) {
                    val daysPassed = today - dueDay
                    notifications.add(
                        NotificationItem(
                            title = "Tagihan Terlewat: ${debt.name}",
                            message = "Jatuh tempo ${debt.dueDate} telah terlewat $daysPassed hari lalu. Pastikan tagihan telah dibayarkan untuk menghindari denda.",
                            timeAgo = "$daysPassed hari lalu",
                            isWarning = true
                        )
                    )
                }
            }
        }

        // 2B. Installment Plans Due-Date & Overdue Reminders
        installmentPlans.forEach { plan ->
            if (plan.isCompleted || plan.remainingTenor <= 0 || (!plan.status.equals("AKTIF", ignoreCase = true) && !plan.status.equals("ACTIVE", ignoreCase = true))) return@forEach

            val dueDay = Regex("\\d+").find(plan.nextDueDate)?.value?.toIntOrNull()
            val formattedAmount = CurrencyFormatter.formatRupiah(plan.installmentAmount)
            val periodStr = "Ke-${plan.currentPeriod}/${plan.totalTenor}"

            if (dueDay != null) {
                if (dueDay == today) {
                    notifications.add(
                        NotificationItem(
                            title = "Jatuh Tempo Cicilan Hari Ini: ${plan.name}",
                            message = "Angsuran ${plan.name} ($periodStr) sebesar $formattedAmount jatuh tempo hari ini (${plan.nextDueDate}). Segera bayar angsuran!",
                            timeAgo = "Hari ini",
                            isWarning = true
                        )
                    )
                } else if (dueDay in (today + 1)..(today + 3)) {
                    val daysLeft = dueDay - today
                    notifications.add(
                        NotificationItem(
                            title = "Pengingat Cicilan: ${plan.name}",
                            message = "Angsuran ${plan.name} ($periodStr) jatuh tempo dalam $daysLeft hari (${plan.nextDueDate}). Siapkan dana sebesar $formattedAmount.",
                            timeAgo = "$daysLeft hari lagi",
                            isWarning = true
                        )
                    )
                } else if (today > dueDay && (today - dueDay) in 1..7) {
                    val daysPassed = today - dueDay
                    notifications.add(
                        NotificationItem(
                            title = "Cicilan Terlewat: ${plan.name}",
                            message = "Angsuran ${plan.name} ($periodStr) pada ${plan.nextDueDate} telah terlewat $daysPassed hari lalu. Harap segera konfirmasi pembayaran.",
                            timeAgo = "$daysPassed hari lalu",
                            isWarning = true
                        )
                    )
                }
            }
        }

        // 3. Budget / Overbudget Alerts (>= 80% warning, >= 100% overbudget)
        val totalLimit = budgetLimits.firstOrNull { it.id == "total_monthly" }
        if (totalLimit != null && totalLimit.limitAmount > 0.0) {
            val totalSpent = currentMonthExpenses.sumOf { it.amount }
            if (totalSpent >= totalLimit.limitAmount) {
                notifications.add(
                    NotificationItem(
                        title = "Overbudget Anggaran Bulanan!",
                        message = "Total pengeluaran bulan ini (${CurrencyFormatter.formatRupiah(totalSpent)}) telah melampaui limit anggaran (${CurrencyFormatter.formatRupiah(totalLimit.limitAmount)}).",
                        timeAgo = "Bulan ini",
                        isWarning = true
                    )
                )
            } else if (totalSpent >= totalLimit.limitAmount * 0.8) {
                val pct = ((totalSpent / totalLimit.limitAmount) * 100).toInt()
                notifications.add(
                    NotificationItem(
                        title = "Peringatan Anggaran Bulanan ($pct%)",
                        message = "Pengeluaran telah mencapai $pct% dari limit bulanan (${CurrencyFormatter.formatRupiah(totalSpent)} / ${CurrencyFormatter.formatRupiah(totalLimit.limitAmount)}).",
                        timeAgo = "Bulan ini",
                        isWarning = true
                    )
                )
            }
        }

        budgetLimits.filter { it.isCategory }.forEach { catLimit ->
            if (catLimit.limitAmount > 0.0) {
                val spent = currentMonthExpenses
                    .filter { it.category.equals(catLimit.id, ignoreCase = true) }
                    .sumOf { it.amount }
                if (spent >= catLimit.limitAmount) {
                    notifications.add(
                        NotificationItem(
                            title = "Overbudget: ${catLimit.id}",
                            message = "Pengeluaran kategori ${catLimit.id} (${CurrencyFormatter.formatRupiah(spent)}) telah melampaui limit (${CurrencyFormatter.formatRupiah(catLimit.limitAmount)}).",
                            timeAgo = "Bulan ini",
                            isWarning = true
                        )
                    )
                } else if (spent >= catLimit.limitAmount * 0.8) {
                    val pct = ((spent / catLimit.limitAmount) * 100).toInt()
                    notifications.add(
                        NotificationItem(
                            title = "Mendekati Limit: ${catLimit.id} ($pct%)",
                            message = "Pengeluaran ${catLimit.id} telah mencapai $pct% dari limit anggaran (${CurrencyFormatter.formatRupiah(spent)} / ${CurrencyFormatter.formatRupiah(catLimit.limitAmount)}).",
                            timeAgo = "Bulan ini",
                            isWarning = true
                        )
                    )
                }
            }
        }

        // 4. Daily Streak Milestones (7, 14, 30, 90, 365)
        when {
            dailyStreak >= 365 -> {
                notifications.add(
                    NotificationItem(
                        title = "🔥 Pencapaian Legendaris: Streak 365 Hari!",
                        message = "Luar biasa! Konsistensi finansial 1 tahun penuh. Gelar Centurion FinSmart aktif!",
                        timeAgo = "Aktif",
                        isWarning = false
                    )
                )
            }
            dailyStreak >= 90 -> {
                notifications.add(
                    NotificationItem(
                        title = "🔥 Milestone Financial Guru: Streak $dailyStreak Hari!",
                        message = "Konsistensi finansial luar biasa! Anda telah konsisten mencatat selama lebih dari 90 hari.",
                        timeAgo = "Aktif",
                        isWarning = false
                    )
                )
            }
            dailyStreak >= 30 -> {
                notifications.add(
                    NotificationItem(
                        title = "🔥 Milestone Budget Master: Streak $dailyStreak Hari!",
                        message = "Hebat! Streak 30 hari tercapai. Fitur Custom Background telah terbuka di Gamification Hub.",
                        timeAgo = "Aktif",
                        isWarning = false
                    )
                )
            }
            dailyStreak >= 14 -> {
                notifications.add(
                    NotificationItem(
                        title = "🔥 Milestone Streak $dailyStreak Hari!",
                        message = "Pertahankan konsistensi Anda! Dynamic App Icons telah terbuka di Gamification Hub.",
                        timeAgo = "Aktif",
                        isWarning = false
                    )
                )
            }
            dailyStreak >= 7 -> {
                notifications.add(
                    NotificationItem(
                        title = "🔥 Milestone Streak 7 Hari Aktif!",
                        message = "Selamat! Anda berhasil mencatat keuangan selama 7 hari berturut-turut. Klaim reward Anda di Gamification Hub!",
                        timeAgo = "Aktif",
                        isWarning = false
                    )
                )
            }
        }

        // 5. Recurring Transactions (actual recorded recurring transactions)
        val recurringTxs = recentTransactions
            .filter { it.tags.contains("#recurring", ignoreCase = true) || it.title.contains("(Auto Recurring)", ignoreCase = true) }
            .sortedByDescending { it.timestamp }
            .take(2)

        recurringTxs.forEach { tx ->
            notifications.add(
                NotificationItem(
                    title = "Transaksi Berulang Tercatat",
                    message = "${tx.title}: ${CurrencyFormatter.formatRupiah(tx.amount)} berhasil dicatat ke ${tx.walletName}.",
                    timeAgo = "Otomatis",
                    isWarning = false
                )
            )
        }

        return notifications
    }
}
