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
        val isEn = LocaleHelper.isEnglish()

        // 1. Low Balance Warnings (wallet <= Rp50.000 or <= 0)
        wallets.forEach { wallet ->
            if (wallet.balance <= 0.0) {
                notifications.add(
                    NotificationItem(
                        title = if (isEn) "Zero Balance: ${wallet.name}" else "Saldo Habis: ${wallet.name}",
                        message = if (isEn) {
                            "Balance in ${wallet.name} is ${CurrencyFormatter.formatRupiah(wallet.balance)}. Please top up your account balance immediately."
                        } else {
                            "Saldo pada ${wallet.name} bernilai ${CurrencyFormatter.formatRupiah(wallet.balance)}. Harap segera isi ulang saldo rekening."
                        },
                        timeAgo = AppStrings.t("Hari ini", "Today"),
                        isWarning = true
                    )
                )
            } else if (wallet.balance <= 50_000.0) {
                notifications.add(
                    NotificationItem(
                        title = if (isEn) "Low Balance Alert: ${wallet.name}" else "Peringatan Saldo Rendah: ${wallet.name}",
                        message = if (isEn) {
                            "Balance in ${wallet.name} is down to ${CurrencyFormatter.formatRupiah(wallet.balance)}, falling below the minimum threshold of Rp50,000."
                        } else {
                            "Saldo pada ${wallet.name} tersisa ${CurrencyFormatter.formatRupiah(wallet.balance)}, berada di bawah batas minimum Rp50.000."
                        },
                        timeAgo = AppStrings.t("Hari ini", "Today"),
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
                            title = if (isEn) "Due Today: ${debt.name}" else "Jatuh Tempo Hari Ini: ${debt.name}",
                            message = if (isEn) {
                                "Bill for ${debt.name} of $formattedAmount is due today (${debt.dueDate}). Please complete your payment promptly!"
                            } else {
                                "Tagihan ${debt.name} sebesar $formattedAmount jatuh tempo hari ini (${debt.dueDate}). Segera lakukan pembayaran!"
                            },
                            timeAgo = AppStrings.t("Hari ini", "Today"),
                            isWarning = true
                        )
                    )
                } else if (dueDay in (today + 1)..(today + 3)) {
                    val daysLeft = dueDay - today
                    notifications.add(
                        NotificationItem(
                            title = if (isEn) "Debt Reminder: ${debt.name}" else "Pengingat Hutang: ${debt.name}",
                            message = if (isEn) {
                                "Bill for ${debt.name} is due in $daysLeft days (${debt.dueDate}). Prepare payment funds of $formattedAmount."
                            } else {
                                "Tagihan ${debt.name} jatuh tempo dalam $daysLeft hari (${debt.dueDate}). Siapkan dana pembayaran sebesar $formattedAmount."
                            },
                            timeAgo = if (isEn) "In $daysLeft days" else "$daysLeft hari lagi",
                            isWarning = true
                        )
                    )
                } else if (today > dueDay && (today - dueDay) in 1..7) {
                    val daysPassed = today - dueDay
                    notifications.add(
                        NotificationItem(
                            title = if (isEn) "Overdue Bill: ${debt.name}" else "Tagihan Terlewat: ${debt.name}",
                            message = if (isEn) {
                                "Due date ${debt.dueDate} passed $daysPassed days ago. Ensure payment is settled to avoid late penalties."
                            } else {
                                "Jatuh tempo ${debt.dueDate} telah terlewat $daysPassed hari lalu. Pastikan tagihan telah dibayarkan untuk menghindari denda."
                            },
                            timeAgo = if (isEn) "$daysPassed days ago" else "$daysPassed hari lalu",
                            isWarning = true
                        )
                    )
                }
            }
        }

        // 2B. Installment Plans Due-Date & Overdue Reminders
        installmentPlans.forEach { plan ->
            if (!plan.isActive) return@forEach

            val dueDay = Regex("\\d+").find(plan.nextDueDate)?.value?.toIntOrNull()
            val formattedAmount = CurrencyFormatter.formatRupiah(plan.installmentAmount)
            val periodStr = if (isEn) "${plan.currentPeriod} of ${plan.totalTenor}" else "Ke-${plan.currentPeriod}/${plan.totalTenor}"

            if (dueDay != null) {
                if (dueDay == today) {
                    notifications.add(
                        NotificationItem(
                            title = if (isEn) "Installment Due Today: ${plan.name}" else "Jatuh Tempo Cicilan Hari Ini: ${plan.name}",
                            message = if (isEn) {
                                "Installment for ${plan.name} ($periodStr) of $formattedAmount is due today (${plan.nextDueDate}). Please pay your installment!"
                            } else {
                                "Angsuran ${plan.name} ($periodStr) sebesar $formattedAmount jatuh tempo hari ini (${plan.nextDueDate}). Segera bayar angsuran!"
                            },
                            timeAgo = AppStrings.t("Hari ini", "Today"),
                            isWarning = true
                        )
                    )
                } else if (dueDay in (today + 1)..(today + 3)) {
                    val daysLeft = dueDay - today
                    notifications.add(
                        NotificationItem(
                            title = if (isEn) "Installment Reminder: ${plan.name}" else "Pengingat Cicilan: ${plan.name}",
                            message = if (isEn) {
                                "Installment for ${plan.name} ($periodStr) is due in $daysLeft days (${plan.nextDueDate}). Prepare payment funds of $formattedAmount."
                            } else {
                                "Angsuran ${plan.name} ($periodStr) jatuh tempo dalam $daysLeft hari (${plan.nextDueDate}). Siapkan dana sebesar $formattedAmount."
                            },
                            timeAgo = if (isEn) "In $daysLeft days" else "$daysLeft hari lagi",
                            isWarning = true
                        )
                    )
                } else if (today > dueDay && (today - dueDay) in 1..7) {
                    val daysPassed = today - dueDay
                    notifications.add(
                        NotificationItem(
                            title = if (isEn) "Overdue Installment: ${plan.name}" else "Cicilan Terlewat: ${plan.name}",
                            message = if (isEn) {
                                "Installment for ${plan.name} ($periodStr) on ${plan.nextDueDate} passed $daysPassed days ago. Please confirm payment immediately."
                            } else {
                                "Angsuran ${plan.name} ($periodStr) pada ${plan.nextDueDate} telah terlewat $daysPassed hari lalu. Harap segera konfirmasi pembayaran."
                            },
                            timeAgo = if (isEn) "$daysPassed days ago" else "$daysPassed hari lalu",
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
                        title = AppStrings.t("Overbudget Anggaran Bulanan!", "Monthly Budget Overspent!"),
                        message = if (isEn) {
                            "Total spending this month (${CurrencyFormatter.formatRupiah(totalSpent)}) has exceeded your budget limit (${CurrencyFormatter.formatRupiah(totalLimit.limitAmount)})."
                        } else {
                            "Total pengeluaran bulan ini (${CurrencyFormatter.formatRupiah(totalSpent)}) telah melampaui limit anggaran (${CurrencyFormatter.formatRupiah(totalLimit.limitAmount)})."
                        },
                        timeAgo = AppStrings.t("Bulan ini", "This month"),
                        isWarning = true
                    )
                )
            } else if (totalSpent >= totalLimit.limitAmount * 0.8) {
                val pct = ((totalSpent / totalLimit.limitAmount) * 100).toInt()
                notifications.add(
                    NotificationItem(
                        title = if (isEn) "Monthly Budget Warning ($pct%)" else "Peringatan Anggaran Bulanan ($pct%)",
                        message = if (isEn) {
                            "Spending has reached $pct% of your monthly budget limit (${CurrencyFormatter.formatRupiah(totalSpent)} / ${CurrencyFormatter.formatRupiah(totalLimit.limitAmount)})."
                        } else {
                            "Pengeluaran telah mencapai $pct% dari limit bulanan (${CurrencyFormatter.formatRupiah(totalSpent)} / ${CurrencyFormatter.formatRupiah(totalLimit.limitAmount)})."
                        },
                        timeAgo = AppStrings.t("Bulan ini", "This month"),
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
                val displayCat = AppStrings.translateCategory(catLimit.id)
                if (spent >= catLimit.limitAmount) {
                    notifications.add(
                        NotificationItem(
                            title = if (isEn) "Overbudget: $displayCat" else "Overbudget: ${catLimit.id}",
                            message = if (isEn) {
                                "Spending in category $displayCat (${CurrencyFormatter.formatRupiah(spent)}) has exceeded the limit (${CurrencyFormatter.formatRupiah(catLimit.limitAmount)})."
                            } else {
                                "Pengeluaran kategori ${catLimit.id} (${CurrencyFormatter.formatRupiah(spent)}) telah melampaui limit (${CurrencyFormatter.formatRupiah(catLimit.limitAmount)})."
                            },
                            timeAgo = AppStrings.t("Bulan ini", "This month"),
                            isWarning = true
                        )
                    )
                } else if (spent >= catLimit.limitAmount * 0.8) {
                    val pct = ((spent / catLimit.limitAmount) * 100).toInt()
                    notifications.add(
                        NotificationItem(
                            title = if (isEn) "Approaching Limit: $displayCat ($pct%)" else "Mendekati Limit: ${catLimit.id} ($pct%)",
                            message = if (isEn) {
                                "Spending in $displayCat has reached $pct% of the budget limit (${CurrencyFormatter.formatRupiah(spent)} / ${CurrencyFormatter.formatRupiah(catLimit.limitAmount)})."
                            } else {
                                "Pengeluaran ${catLimit.id} telah mencapai $pct% dari limit anggaran (${CurrencyFormatter.formatRupiah(spent)} / ${CurrencyFormatter.formatRupiah(catLimit.limitAmount)})."
                            },
                            timeAgo = AppStrings.t("Bulan ini", "This month"),
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
                        title = AppStrings.t("🔥 Pencapaian Legendaris: Streak 365 Hari!", "🔥 Legendary Achievement: 365-Day Streak!"),
                        message = AppStrings.t(
                            "Luar biasa! Konsistensi finansial 1 tahun penuh. Gelar Centurion DompetNalar aktif!",
                            "Incredible! 1 full year of financial consistency. Centurion DompetNalar badge active!"
                        ),
                        timeAgo = AppStrings.t("Aktif", "Active"),
                        isWarning = false
                    )
                )
            }
            dailyStreak >= 90 -> {
                notifications.add(
                    NotificationItem(
                        title = if (isEn) "🔥 Financial Guru Milestone: Streak $dailyStreak Days!" else "🔥 Milestone Financial Guru: Streak $dailyStreak Hari!",
                        message = AppStrings.t(
                            "Konsistensi finansial luar biasa! Anda telah konsisten mencatat selama lebih dari 90 hari.",
                            "Outstanding financial consistency! You have tracked consistently for over 90 days."
                        ),
                        timeAgo = AppStrings.t("Aktif", "Active"),
                        isWarning = false
                    )
                )
            }
            dailyStreak >= 30 -> {
                notifications.add(
                    NotificationItem(
                        title = if (isEn) "🔥 Budget Master Milestone: Streak $dailyStreak Days!" else "🔥 Milestone Budget Master: Streak $dailyStreak Hari!",
                        message = AppStrings.t(
                            "Hebat! Streak 30 hari tercapai. Klaim Akses Smart Master Gratis 24 Jam di Gamification Hub!",
                            "Awesome! 30-day streak reached. Claim Free 24-Hour Smart Master Access in Gamification Hub!"
                        ),
                        timeAgo = AppStrings.t("Aktif", "Active"),
                        isWarning = false
                    )
                )
            }
            dailyStreak >= 14 -> {
                notifications.add(
                    NotificationItem(
                        title = if (isEn) "🔥 Streak Milestone: $dailyStreak Days!" else "🔥 Milestone Streak $dailyStreak Hari!",
                        message = AppStrings.t(
                            "Pertahankan konsistensi Anda! Dynamic App Icons telah terbuka di Gamification Hub.",
                            "Keep up your consistency! Dynamic App Icons unlocked in Gamification Hub."
                        ),
                        timeAgo = AppStrings.t("Aktif", "Active"),
                        isWarning = false
                    )
                )
            }
            dailyStreak >= 7 -> {
                notifications.add(
                    NotificationItem(
                        title = AppStrings.t("🔥 Milestone Streak 7 Hari Aktif!", "🔥 7-Day Active Streak Milestone!"),
                        message = AppStrings.t(
                            "Selamat! Anda berhasil mencatat keuangan selama 7 hari berturut-turut. Klaim reward Anda di Gamification Hub!",
                            "Congratulations! You tracked your finances for 7 days in a row. Claim your reward in Gamification Hub!"
                        ),
                        timeAgo = AppStrings.t("Aktif", "Active"),
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
                    title = AppStrings.t("Transaksi Berulang Tercatat", "Recurring Transaction Recorded"),
                    message = if (isEn) {
                        "${tx.title}: ${CurrencyFormatter.formatRupiah(tx.amount)} recorded to ${tx.walletName}."
                    } else {
                        "${tx.title}: ${CurrencyFormatter.formatRupiah(tx.amount)} berhasil dicatat ke ${tx.walletName}."
                    },
                    timeAgo = AppStrings.t("Otomatis", "Automatic"),
                    isWarning = false
                )
            )
        }

        return notifications
    }
}
