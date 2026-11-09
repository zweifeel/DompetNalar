package com.example.data.ai

import com.example.data.chatbot.parser.FinancialQueryEngine
import com.example.data.chatbot.parser.LocalFinancialIntentParser
import com.example.data.model.BudgetLimitItem
import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPlanItem
import com.example.data.model.TransactionItem
import com.example.data.model.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Layanan Konsultasi & Kueri Keuangan DompetNalar (100% Local-First).
 *
 * Menggantikan Gemini Cloud sepenuhnya:
 * - Menjawab kueri saldo, pengeluaran, pemasukan, kategori, rasio tabungan, tagihan, dan utang secara deterministik.
 * - 100% Berjalan di perangkat lokal tanpa internet dan tanpa mengirim data ke AI eksternal.
 */
object AdvisorAiService {

    suspend fun getAdvisorResponse(
        userMessage: String,
        transactions: List<TransactionItem>,
        budgetLimits: List<BudgetLimitItem>,
        wallets: List<Wallet> = emptyList(),
        debts: List<DebtItem> = emptyList(),
        installmentPlans: List<InstallmentPlanItem> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val parsed = LocalFinancialIntentParser.parse(
            userMessage = userMessage,
            availableWallets = wallets
        )

        val topic = parsed.queryTopic ?: LocalFinancialIntentParser.QueryTopic.FINANCIAL_HEALTH_SUMMARY

        FinancialQueryEngine.executeQuery(
            topic = topic,
            queriedWallet = parsed.sourceWallet,
            wallets = wallets,
            transactions = transactions,
            budgetLimits = budgetLimits,
            debts = debts,
            installmentPlans = installmentPlans
        )
    }
}
