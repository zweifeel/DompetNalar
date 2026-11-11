package com.example.data.chatbot.parser

import com.example.data.model.BudgetLimitItem
import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPlanItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import com.example.util.CurrencyFormatter
import com.example.util.LocaleHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Mesin Penjawab Kueri & Konsultasi Finansial Deterministik Terpadu (Local-First).
 *
 * Menggantikan Gemini API / AI Cloud untuk konsultasi dan analitik keuangan:
 * - 100% Berbasis data aktual Room (wallets, transactions, budget_limits, debts, installment_plans).
 * - Transfer dana DILARANG dihitung sebagai pemasukan atau pengeluaran.
 * - Bilingual: Mendukung respon bahasa Indonesia dan English.
 * - Menggunakan [CurrencyFormatter] untuk format mata uang global dinamis.
 */
object FinancialQueryEngine {

    fun executeQuery(
        topic: LocalFinancialIntentParser.QueryTopic,
        queriedWallet: Wallet?,
        wallets: List<Wallet>,
        transactions: List<TransactionItem>,
        budgetLimits: List<BudgetLimitItem>,
        debts: List<DebtItem> = emptyList(),
        installmentPlans: List<InstallmentPlanItem> = emptyList()
    ): String {
        val isEn = LocaleHelper.isEnglish()

        return when (topic) {
            LocalFinancialIntentParser.QueryTopic.TOTAL_BALANCE -> {
                buildTotalBalanceResponse(wallets, isEn)
            }
            LocalFinancialIntentParser.QueryTopic.SPECIFIC_WALLET_BALANCE -> {
                buildSpecificWalletBalanceResponse(queriedWallet, wallets, isEn)
            }
            LocalFinancialIntentParser.QueryTopic.MONTHLY_EXPENSE -> {
                buildMonthlyExpenseResponse(transactions, isEn)
            }
            LocalFinancialIntentParser.QueryTopic.MONTHLY_INCOME -> {
                buildMonthlyIncomeResponse(transactions, isEn)
            }
            LocalFinancialIntentParser.QueryTopic.LARGEST_EXPENSE -> {
                buildLargestExpenseResponse(transactions, isEn)
            }
            LocalFinancialIntentParser.QueryTopic.TOP_FIVE_EXPENSES -> {
                buildTopFiveExpensesResponse(transactions, isEn)
            }
            LocalFinancialIntentParser.QueryTopic.CATEGORY_BREAKDOWN -> {
                buildCategoryBreakdownResponse(transactions, isEn)
            }
            LocalFinancialIntentParser.QueryTopic.RECENT_TRANSACTIONS -> {
                buildRecentTransactionsResponse(transactions, isEn)
            }
            LocalFinancialIntentParser.QueryTopic.FINANCIAL_HEALTH_SUMMARY -> {
                buildFinancialHealthSummary(wallets, transactions, budgetLimits, debts, installmentPlans, isEn)
            }
            LocalFinancialIntentParser.QueryTopic.BUDGET_PROGRESS -> {
                buildBudgetProgressResponse(transactions, budgetLimits, isEn)
            }
            LocalFinancialIntentParser.QueryTopic.DAILY_SAFE_SPENDING -> {
                buildDailySafeSpendingResponse(transactions, budgetLimits, isEn)
            }
            LocalFinancialIntentParser.QueryTopic.SAVINGS_RATIO -> {
                buildSavingsRatioResponse(transactions, isEn)
            }
            LocalFinancialIntentParser.QueryTopic.NET_WORTH -> {
                buildNetWorthResponse(wallets, debts, isEn)
            }
            LocalFinancialIntentParser.QueryTopic.DEBT_TO_INCOME -> {
                buildDebtToIncomeResponse(transactions, debts, installmentPlans, isEn)
            }
            LocalFinancialIntentParser.QueryTopic.OUTSTANDING_INSTALLMENT -> {
                buildOutstandingInstallmentResponse(installmentPlans, isEn)
            }
            LocalFinancialIntentParser.QueryTopic.UPCOMING_BILLS -> {
                buildUpcomingBillsResponse(debts, installmentPlans, isEn)
            }
            LocalFinancialIntentParser.QueryTopic.DUE_DATE_ANALYSIS -> {
                buildDueDateAnalysisResponse(debts, installmentPlans, isEn)
            }
        }
    }

    private fun buildTotalBalanceResponse(wallets: List<Wallet>, isEn: Boolean): String {
        if (wallets.isEmpty()) {
            return if (isEn) "You have no registered wallets yet. Please add a wallet in the Wallets tab."
            else "Anda belum memiliki dompet yang terdaftar. Silakan tambahkan dompet di menu Dompet atau katakan 'Buat dompet BCA'."
        }
        val total = wallets.sumOf { it.balance }
        val breakdown = wallets.joinToString("\n") { "• ${it.name}: ${formatMoney(it.balance)}" }
        return if (isEn) {
            """
                💰 **Total Wallet Balance**: ${formatMoney(total)}
                
                Breakdown by wallet:
                $breakdown
            """.trimIndent()
        } else {
            """
                💰 **Total Saldo Seluruh Dompet**: ${formatMoney(total)}
                
                Rincian per dompet:
                $breakdown
            """.trimIndent()
        }
    }

    private fun buildSpecificWalletBalanceResponse(wallet: Wallet?, allWallets: List<Wallet>, isEn: Boolean): String {
        if (wallet != null) {
            return if (isEn) "The current balance for wallet **${wallet.name}** is **${formatMoney(wallet.balance)}**."
            else "Saldo dompet **${wallet.name}** Anda saat ini adalah **${formatMoney(wallet.balance)}**."
        }
        val names = allWallets.joinToString(", ") { it.name }
        return if (isEn) "Wallet not found. Available wallets: ${if (names.isBlank()) "None" else names}."
        else "Dompet spesifik tidak ditemukan. Dompet yang tersedia: ${if (names.isBlank()) "Belum ada" else names}."
    }

    private fun buildMonthlyExpenseResponse(transactions: List<TransactionItem>, isEn: Boolean): String {
        val (startMonth, endMonth) = getCurrentMonthBounds()
        val currentMonthTxs = transactions.filter { it.timestamp in startMonth..endMonth }
        val totalExpense = currentMonthTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val txCount = currentMonthTxs.count { it.type == TransactionType.EXPENSE }

        return if (isEn) "Your total expenses this month are **${formatMoney(totalExpense)}** across $txCount recorded transactions."
        else "Total pengeluaran Anda pada bulan ini adalah **${formatMoney(totalExpense)}** ($txCount transaksi tercatat)."
    }

    private fun buildMonthlyIncomeResponse(transactions: List<TransactionItem>, isEn: Boolean): String {
        val (startMonth, endMonth) = getCurrentMonthBounds()
        val currentMonthTxs = transactions.filter { it.timestamp in startMonth..endMonth }
        val totalIncome = currentMonthTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val txCount = currentMonthTxs.count { it.type == TransactionType.INCOME }

        return if (isEn) "Your total income this month is **${formatMoney(totalIncome)}** across $txCount recorded income entries."
        else "Total pemasukan Anda pada bulan ini adalah **${formatMoney(totalIncome)}** ($txCount pemasukan tercatat)."
    }

    private fun buildLargestExpenseResponse(transactions: List<TransactionItem>, isEn: Boolean): String {
        val (startMonth, endMonth) = getCurrentMonthBounds()
        val currentMonthExpenses = transactions.filter {
            it.timestamp in startMonth..endMonth && it.type == TransactionType.EXPENSE
        }
        val largest = currentMonthExpenses.maxByOrNull { it.amount }

        return if (largest != null) {
            if (isEn) "Your largest expense this month is **${largest.title}** for **${formatMoney(largest.amount)}** (Category: ${largest.category}, Wallet: ${largest.walletName})."
            else "Pengeluaran terbesar Anda bulan ini adalah **${largest.title}** sebesar **${formatMoney(largest.amount)}** (Kategori: ${largest.category}, Dompet: ${largest.walletName})."
        } else {
            if (isEn) "No expense records found for this month yet."
            else "Belum ada catatan pengeluaran pada bulan ini."
        }
    }

    private fun buildTopFiveExpensesResponse(transactions: List<TransactionItem>, isEn: Boolean): String {
        val (startMonth, endMonth) = getCurrentMonthBounds()
        val topFive = transactions.filter { it.timestamp in startMonth..endMonth && it.type == TransactionType.EXPENSE }
            .sortedByDescending { it.amount }
            .take(5)

        if (topFive.isEmpty()) {
            return if (isEn) "No expenses recorded this month." else "Belum ada catatan pengeluaran bulan ini."
        }

        val rows = topFive.mapIndexed { index, tx ->
            "${index + 1}. **${tx.title}**: ${formatMoney(tx.amount)} [${tx.category}]"
        }.joinToString("\n")

        return if (isEn) """
            🏆 **Top 5 Expenses This Month**:
            $rows
        """.trimIndent() else """
            🏆 **5 Pengeluaran Terbesar Bulan Ini**:
            $rows
        """.trimIndent()
    }

    private fun buildCategoryBreakdownResponse(transactions: List<TransactionItem>, isEn: Boolean): String {
        val (startMonth, endMonth) = getCurrentMonthBounds()
        val expenses = transactions.filter {
            it.timestamp in startMonth..endMonth && it.type == TransactionType.EXPENSE
        }
        if (expenses.isEmpty()) {
            return if (isEn) "No expenses recorded this month to analyze."
            else "Belum ada pengeluaran yang tercatat pada bulan ini untuk dianalisis."
        }

        val totalExpense = expenses.sumOf { it.amount }
        val grouped = expenses.groupBy { it.category }
            .mapValues { it.value.sumOf { tx -> tx.amount } }
            .entries.sortedByDescending { it.value }

        val lines = grouped.joinToString("\n") { (cat, sum) ->
            val pct = if (totalExpense > 0) (sum / totalExpense) * 100 else 0.0
            "• **$cat**: ${formatMoney(sum)} (${String.format(Locale.ROOT, "%.1f", pct)}%)"
        }

        return if (isEn) """
            📊 **Expense Breakdown by Category This Month**:
            Total: ${formatMoney(totalExpense)}
            
            $lines
        """.trimIndent() else """
            📊 **Rincian Pengeluaran Bulan Ini Per Kategori**:
            Total: ${formatMoney(totalExpense)}
            
            $lines
        """.trimIndent()
    }

    private fun buildRecentTransactionsResponse(transactions: List<TransactionItem>, isEn: Boolean): String {
        val recent = transactions.sortedByDescending { it.timestamp }.take(5)
        if (recent.isEmpty()) {
            return if (isEn) "No transaction history recorded yet."
            else "Belum ada riwayat transaksi yang tersimpan."
        }

        val listStr = recent.joinToString("\n") { tx ->
            val sign = when (tx.type) {
                TransactionType.INCOME -> "(+)"
                TransactionType.EXPENSE -> "(-)"
                TransactionType.TRANSFER -> "(⇄)"
            }
            "• $sign ${tx.title}: ${formatMoney(tx.amount)} [${tx.walletName}]"
        }

        return if (isEn) """
            📝 **Your 5 Most Recent Transactions**:
            $listStr
        """.trimIndent() else """
            📝 **5 Transaksi Terakhir Anda**:
            $listStr
        """.trimIndent()
    }

    private fun buildFinancialHealthSummary(
        wallets: List<Wallet>,
        transactions: List<TransactionItem>,
        budgetLimits: List<BudgetLimitItem>,
        debts: List<DebtItem>,
        installmentPlans: List<InstallmentPlanItem>,
        isEn: Boolean
    ): String {
        val (startMonth, endMonth) = getCurrentMonthBounds()
        val currentMonthTxs = transactions.filter { it.timestamp in startMonth..endMonth }
        val incomeTotal = currentMonthTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expenseTotal = currentMonthTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val netSavings = incomeTotal - expenseTotal
        val savingsRatio = if (incomeTotal > 0) (netSavings / incomeTotal) * 100 else 0.0

        val totalDebtLiability = debts.sumOf { it.amount }
        val activeInstallments = installmentPlans.filter { it.status.equals("AKTIF", true) || it.status.equals("ACTIVE", true) }
        val monthlyDebtPayment = activeInstallments.sumOf { it.installmentAmount } + debts.sumOf { it.minMonthlyPayment }

        val topCategoryEntry = currentMonthTxs.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { tx -> tx.amount } }
            .maxByOrNull { it.value }

        val topCategoryStr = topCategoryEntry?.let { "${it.key} (${formatMoney(it.value)})" } ?: (if (isEn) "None" else "Belum ada")

        val monthlyBudget = budgetLimits.firstOrNull { it.id == "total_monthly" }?.limitAmount ?: 0.0
        val budgetStatus = if (monthlyBudget > 0.0) {
            val pct = (expenseTotal / monthlyBudget) * 100
            if (expenseTotal > monthlyBudget) {
                if (isEn) "⚠️ **Budget Exceeded**: Spending has exceeded the monthly limit of ${formatMoney(monthlyBudget)} (${String.format(Locale.ROOT, "%.0f", pct)}%)."
                else "⚠️ **Melebihi Anggaran**: Pengeluaran telah melampaui limit bulanan ${formatMoney(monthlyBudget)} (${String.format(Locale.ROOT, "%.0f", pct)}%)."
            } else {
                if (isEn) "✅ **Budget on Track**: Spent ${formatMoney(expenseTotal)} of ${formatMoney(monthlyBudget)} (${String.format(Locale.ROOT, "%.0f", pct)}%)."
                else "✅ **Anggaran Aman**: Terpakai ${formatMoney(expenseTotal)} dari limit ${formatMoney(monthlyBudget)} (${String.format(Locale.ROOT, "%.0f", pct)}%)."
            }
        } else {
            if (isEn) "ℹ️ Combined monthly budget limit not set yet." else "ℹ️ Belum mengatur limit bulanan gabungan."
        }

        val advice = when {
            incomeTotal == 0.0 && expenseTotal > 0.0 ->
                if (isEn) "No income recorded this month. Keep spending strictly to essentials."
                else "Belum ada pemasukan bulan ini. Pertahankan pengeluaran hanya untuk kebutuhan esensial."
            savingsRatio >= 20.0 ->
                if (isEn) "Excellent Financial Health! Savings Ratio: ${String.format(Locale.ROOT, "%.1f", savingsRatio)}% (above the 20% benchmark)."
                else "Kesehatan Keuangan Prima! Rasio Tabungan: ${String.format(Locale.ROOT, "%.1f", savingsRatio)}% (di atas target ideal 20%)."
            savingsRatio in 0.0..19.9 ->
                if (isEn) "Savings ratio is ${String.format(Locale.ROOT, "%.1f", savingsRatio)}% (below recommended 20%). Consider trimming spending on $topCategoryStr."
                else "Rasio tabungan ${String.format(Locale.ROOT, "%.1f", savingsRatio)}% sedikit di bawah target ideal (20%). Coba kurangi pengeluaran di kategori $topCategoryStr."
            else ->
                if (isEn) "Cashflow deficit: expenses exceed income. Review your expenses!"
                else "Defisit arus kas: pengeluaran lebih besar dari pemasukan. Evaluasi kembali pos belanja Anda!"
        }

        val debtLine = if (monthlyDebtPayment > 0.0) {
            if (isEn) "\n• **Monthly Debt Obligations**: ${formatMoney(monthlyDebtPayment)}"
            else "\n• **Beban Kewajiban Bulanan**: ${formatMoney(monthlyDebtPayment)}"
        } else ""

        return if (isEn) """
            💡 **Monthly Financial Health Summary**:
            
            • **Total Income**: ${formatMoney(incomeTotal)}
            • **Total Expenses**: ${formatMoney(expenseTotal)}
            • **Net Cashflow**: ${formatMoney(netSavings)} (Savings Ratio: ${String.format(Locale.ROOT, "%.1f", savingsRatio)}%)
            • **Top Expense Category**: $topCategoryStr$debtLine
            
            $budgetStatus
            
            📌 *Advisor Guidance*:
            $advice
        """.trimIndent() else """
            💡 **Ringkasan Kesehatan Keuangan Bulan Ini** (Offline Engine):
            
            • **Pemasukan**: ${formatMoney(incomeTotal)}
            • **Pengeluaran**: ${formatMoney(expenseTotal)}
            • **Sisa Tabungan**: ${formatMoney(netSavings)} (Rasio: ${String.format(Locale.ROOT, "%.1f", savingsRatio)}%)
            • **Pengeluaran Terbesar**: $topCategoryStr$debtLine
            
            $budgetStatus
            
            📌 *Saran Konsultan*:
            $advice
        """.trimIndent()
    }

    private fun buildBudgetProgressResponse(
        transactions: List<TransactionItem>,
        budgetLimits: List<BudgetLimitItem>,
        isEn: Boolean
    ): String {
        val (startMonth, endMonth) = getCurrentMonthBounds()
        val expenses = transactions.filter { it.timestamp in startMonth..endMonth && it.type == TransactionType.EXPENSE }
        val totalExpense = expenses.sumOf { it.amount }

        val monthlyBudget = budgetLimits.firstOrNull { it.id == "total_monthly" }?.limitAmount ?: 0.0
        if (monthlyBudget <= 0.0 && budgetLimits.isEmpty()) {
            return if (isEn) "You haven't set any budget limits yet. Please set budgets in the Budget menu."
            else "Anda belum mengatur target anggaran. Silakan tentukan anggaran di menu Anggaran."
        }

        val remaining = monthlyBudget - totalExpense
        val pct = if (monthlyBudget > 0.0) (totalExpense / monthlyBudget) * 100 else 0.0

        return if (isEn) """
            🛡️ **Budget Progress**:
            • Total Budget: ${formatMoney(monthlyBudget)}
            • Spent so far: ${formatMoney(totalExpense)} (${String.format(Locale.ROOT, "%.1f", pct)}%)
            • Remaining: ${formatMoney(Math.max(0.0, remaining))}
            
            Status: ${if (remaining >= 0) "✅ Safe & On Track" else "⚠️ Exceeded by ${formatMoney(Math.abs(remaining))}"}
        """.trimIndent() else """
            🛡️ **Evaluasi Anggaran Bulanan**:
            • Total Anggaran: ${formatMoney(monthlyBudget)}
            • Terpakai: ${formatMoney(totalExpense)} (${String.format(Locale.ROOT, "%.1f", pct)}%)
            • Sisa Anggaran: ${formatMoney(Math.max(0.0, remaining))}
            
            Status: ${if (remaining >= 0) "✅ Anggaran Masih Aman" else "⚠️ Melebihi Anggaran sebesar ${formatMoney(Math.abs(remaining))}"}
        """.trimIndent()
    }

    private fun buildDailySafeSpendingResponse(
        transactions: List<TransactionItem>,
        budgetLimits: List<BudgetLimitItem>,
        isEn: Boolean
    ): String {
        val (startMonth, endMonth) = getCurrentMonthBounds()
        val totalExpense = transactions.filter { it.timestamp in startMonth..endMonth && it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val monthlyBudget = budgetLimits.firstOrNull { it.id == "total_monthly" }?.limitAmount ?: 0.0

        val cal = Calendar.getInstance()
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val daysRemaining = Math.max(1, maxDays - currentDay + 1)

        if (monthlyBudget <= 0.0) {
            return if (isEn) "Please set a monthly budget first to calculate your daily safe spending limit."
            else "Silakan atur target anggaran bulanan di menu Anggaran untuk menghitung batas belanja harian yang aman."
        }

        val remainingBudget = monthlyBudget - totalExpense
        val safeDaily = if (remainingBudget > 0) remainingBudget / daysRemaining else 0.0

        return if (isEn) """
            🎯 **Daily Safe Spending Limit**:
            • Sisa Hari Bulan Ini: $daysRemaining hari
            • Sisa Anggaran: ${formatMoney(remainingBudget)}
            • Batas Belanja Aman per Hari: **${formatMoney(safeDaily)}**/hari
        """.trimIndent() else """
            🎯 **Batas Pengeluaran Harian yang Aman**:
            • Sisa Hari Bulan Ini: $daysRemaining hari
            • Sisa Anggaran Tersedia: ${formatMoney(remainingBudget)}
            • Batas Belanja Aman per Hari: **${formatMoney(safeDaily)}**/hari
        """.trimIndent()
    }

    private fun buildSavingsRatioResponse(transactions: List<TransactionItem>, isEn: Boolean): String {
        val (startMonth, endMonth) = getCurrentMonthBounds()
        val monthTxs = transactions.filter { it.timestamp in startMonth..endMonth }
        val income = monthTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expense = monthTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val netSavings = income - expense
        val ratio = if (income > 0) (netSavings / income) * 100 else 0.0

        return if (isEn) """
            💰 **Savings Ratio This Month**:
            • Income: ${formatMoney(income)}
            • Expenses: ${formatMoney(expense)}
            • Net Savings: ${formatMoney(netSavings)}
            • **Savings Ratio**: **${String.format(Locale.ROOT, "%.1f", ratio)}%**
            
            Benchmark: Ideal savings ratio is 20% or higher.
        """.trimIndent() else """
            💰 **Rasio Tabungan Bulan Ini**:
            • Pemasukan: ${formatMoney(income)}
            • Pengeluaran: ${formatMoney(expense)}
            • Sisa Bersih: ${formatMoney(netSavings)}
            • **Rasio Tabungan**: **${String.format(Locale.ROOT, "%.1f", ratio)}%**
            
            Pedoman: Rasio tabungan ideal adalah 20% atau lebih tinggi.
        """.trimIndent()
    }

    private fun buildNetWorthResponse(wallets: List<Wallet>, debts: List<DebtItem>, isEn: Boolean): String {
        val totalAssets = wallets.sumOf { it.balance }
        val totalDebts = debts.sumOf { it.amount }
        val netWorth = totalAssets - totalDebts

        return if (isEn) """
            🏛️ **Net Worth Calculation**:
            • Total Assets (Wallets): ${formatMoney(totalAssets)}
            • Total Liabilities (Debts): ${formatMoney(totalDebts)}
            • **Total Net Worth**: **${formatMoney(netWorth)}**
        """.trimIndent() else """
            🏛️ **Kalkulasi Kekayaan Bersih (Net Worth)**:
            • Total Aset (Seluruh Dompet): ${formatMoney(totalAssets)}
            • Total Kewajiban (Utang): ${formatMoney(totalDebts)}
            • **Kekayaan Bersih**: **${formatMoney(netWorth)}**
        """.trimIndent()
    }

    private fun buildDebtToIncomeResponse(
        transactions: List<TransactionItem>,
        debts: List<DebtItem>,
        installmentPlans: List<InstallmentPlanItem>,
        isEn: Boolean
    ): String {
        val (startMonth, endMonth) = getCurrentMonthBounds()
        val income = transactions.filter { it.timestamp in startMonth..endMonth && it.type == TransactionType.INCOME }.sumOf { it.amount }
        val activePlans = installmentPlans.filter { it.status.equals("AKTIF", true) || it.status.equals("ACTIVE", true) }
        val monthlyDebtPayments = activePlans.sumOf { it.installmentAmount } + debts.sumOf { it.minMonthlyPayment }
        val totalDebtBalance = debts.sumOf { it.amount } + activePlans.sumOf { it.totalRemainingPrincipal ?: (it.installmentAmount * it.remainingTenor) }

        val dsr = if (income > 0) (monthlyDebtPayments / income) * 100 else 0.0

        val evaluation = when {
            dsr == 0.0 -> if (isEn) "Zero debt! Wonderful financial freedom." else "Bebas utang! Kondisi keuangan sangat prima."
            dsr <= 30.0 -> if (isEn) "Debt Service Ratio is healthy (<= 30% of income)." else "Beban cicilan aman (di bawah batas maksimal 30% dari pemasukan)."
            else -> if (isEn) "⚠️ Caution: Debt payments exceed 30% of income. Avoid taking on new debt." else "⚠️ Waspada: Beban cicilan melampaui 30% dari pemasukan. Tunda mengambil utang baru."
        }

        return if (isEn) """
            💳 **Debt-to-Income Analysis**:
            • Monthly Debt Obligations: ${formatMoney(monthlyDebtPayments)}
            • Total Outstanding Debt Balance: ${formatMoney(totalDebtBalance)}
            • Debt Service Ratio (DSR): **${String.format(Locale.ROOT, "%.1f", dsr)}%**
            
            Evaluation: $evaluation
        """.trimIndent() else """
            💳 **Analisis Rasio Beban Utang (DSR)**:
            • Kewajiban Cicilan Bulanan: ${formatMoney(monthlyDebtPayments)}
            • Total Saldo Utang Tersisa: ${formatMoney(totalDebtBalance)}
            • Rasio Pembayaran Utang (DSR): **${String.format(Locale.ROOT, "%.1f", dsr)}%**
            
            Evaluasi: $evaluation
        """.trimIndent()
    }

    private fun buildOutstandingInstallmentResponse(installmentPlans: List<InstallmentPlanItem>, isEn: Boolean): String {
        val active = installmentPlans.filter { it.status.equals("AKTIF", true) || it.status.equals("ACTIVE", true) }
        if (active.isEmpty()) {
            return if (isEn) "You have no active installment contracts recorded." else "Tidak ada rencana cicilan aktif yang tercatat."
        }

        val rows = active.joinToString("\n\n") { plan ->
            val paid = plan.paidCount
            val total = plan.totalTenor
            val rem = plan.remainingTenor
            "• **${plan.name}** (${plan.provider})\n  Angsuran: ${formatMoney(plan.installmentAmount)}/bulan\n  Status: Cicilan ke-$paid dari $total tenor (Sisa: $rem kali)\n  Jatuh tempo: ${plan.nextDueDate}"
        }

        return if (isEn) """
            📑 **Active Installment Plans**:
            $rows
        """.trimIndent() else """
            📑 **Rencana Cicilan Aktif**:
            $rows
        """.trimIndent()
    }

    private fun buildUpcomingBillsResponse(
        debts: List<DebtItem>,
        installmentPlans: List<InstallmentPlanItem>,
        isEn: Boolean
    ): String {
        val activePlans = installmentPlans.filter { it.status.equals("AKTIF", true) || it.status.equals("ACTIVE", true) }
        if (debts.isEmpty() && activePlans.isEmpty()) {
            return if (isEn) "No active bills or debts due found." else "Tidak ada tagihan atau utang jatuh tempo yang tercatat."
        }

        val items = mutableListOf<String>()

        for (plan in activePlans) {
            items.add("• [Cicilan] **${plan.name}**: ${formatMoney(plan.installmentAmount)} — Jatuh tempo: ${plan.nextDueDate} (Sisa ${plan.remainingTenor}x)")
        }

        for (debt in debts) {
            items.add("• [Tagihan] **${debt.name}**: ${formatMoney(if (debt.minMonthlyPayment > 0) debt.minMonthlyPayment else debt.amount)} — Jatuh tempo: ${debt.dueDate}")
        }

        return if (isEn) """
            ⏰ **Upcoming Bills & Installments**:
            ${items.joinToString("\n")}
        """.trimIndent() else """
            ⏰ **Tagihan & Cicilan Terdekat**:
            ${items.joinToString("\n")}
        """.trimIndent()
    }

    private fun buildDueDateAnalysisResponse(
        debts: List<DebtItem>,
        installmentPlans: List<InstallmentPlanItem>,
        isEn: Boolean
    ): String {
        return buildUpcomingBillsResponse(debts, installmentPlans, isEn)
    }

    private fun getCurrentMonthBounds(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis

        return Pair(start, end)
    }

    private fun formatMoney(amount: Double): String {
        return CurrencyFormatter.formatCurrency(amount)
    }
}
