package com.example.data.ai

import com.example.data.chatbot.parser.LocalFinancialIntentParser
import com.example.data.chatbot.parser.TransactionDraftManager
import com.example.data.chatbot.parser.WalletDraftManager
import com.example.data.model.CategoryItem
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import com.example.data.session.SessionManager
import com.example.util.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adaptor Intent & Eksekusi Finansial DompetNalar (100% Local-First).
 *
 * Mengalihkan seluruh parsing input teks pengguna ke [LocalFinancialIntentParser],
 * [TransactionDraftManager], dan [WalletDraftManager] tanpa memerlukan Gemini API Key atau koneksi internet.
 *
 * Menerapkan Strict Fail-Closed Draft Ownership:
 * - Menolak pembuatan draft jika sesi aktif tidak valid (userId <= 0L).
 */
object ExpenseAiParser {

    suspend fun parseExpenseMessage(
        userMessage: String,
        availableWallets: List<Wallet>,
        availableCategories: List<CategoryItem> = emptyList(),
        userId: Long = SessionManager.getActiveUserId() ?: 0L
    ): ParsedTransactionResult = withContext(Dispatchers.IO) {
        val activeUserId = if (userId > 0L) userId else (SessionManager.getActiveUserId() ?: 0L)
        if (activeUserId <= 0L) {
            return@withContext ParsedTransactionResult(
                title = "",
                amount = 0.0,
                type = "UNKNOWN",
                category = "Umum",
                categoryIcon = "payments",
                suggestedWalletName = "Pilih Dompet",
                suggestedTargetWalletName = null,
                note = "",
                tags = "",
                aiExplanation = if (LocaleHelper.isEnglish()) "Active login session required to process AI financial commands." else "Sesi login aktif diperlukan untuk memproses perintah finansial AI.",
                timestamp = System.currentTimeMillis(),
                detectedDateText = null,
                isValid = false,
                draftId = null,
                isWalletCreation = false,
                walletDraftId = null
            )
        }

        val command = LocalFinancialIntentParser.parse(
            userMessage = userMessage,
            availableWallets = availableWallets,
            availableCategories = availableCategories
        )

        // 1. Penanganan Pembuatan Dompet
        if (command.intentType in listOf(
                LocalFinancialIntentParser.IntentType.CREATE_WALLET,
                LocalFinancialIntentParser.IntentType.CREATE_WALLET_WITH_OPENING_BALANCE,
                LocalFinancialIntentParser.IntentType.CREATE_WALLET_WITH_INCOME
            ) && command.status == LocalFinancialIntentParser.ParseStatus.WALLET_DRAFT_READY
        ) {
            val walletDraft = WalletDraftManager.createDraft(command, ownerUserId = activeUserId)
            return@withContext ParsedTransactionResult(
                title = command.title,
                amount = command.amount ?: 0.0,
                type = "CREATE_WALLET",
                category = "Dompet Baru",
                categoryIcon = "account_balance_wallet",
                suggestedWalletName = command.title,
                suggestedTargetWalletName = null,
                note = "Pembuatan Dompet Baru via Chatbot",
                tags = "#dompet",
                aiExplanation = command.responseMessage,
                timestamp = command.timestamp,
                detectedDateText = null,
                isValid = walletDraft != null,
                draftId = null,
                isWalletCreation = true,
                walletDraftId = walletDraft?.id,
                walletType = command.walletType,
                openingBalance = command.initialBalance ?: 0.0,
                incomeAmount = command.incomeAmount
            )
        }

        // 2. Penanganan Transaksi Keuangan (Expense, Income, Transfer)
        val txType = command.transactionType ?: TransactionType.EXPENSE
        val txTypeStr = when (txType) {
            TransactionType.INCOME -> "INCOME"
            TransactionType.TRANSFER -> "TRANSFER"
            TransactionType.EXPENSE -> "EXPENSE"
        }

        val isActionableTransaction = command.status == LocalFinancialIntentParser.ParseStatus.READY_FOR_DRAFT &&
                (command.amount ?: 0.0) > 0.0 &&
                command.sourceWallet != null &&
                (command.transactionType != TransactionType.TRANSFER || (command.targetWallet != null && command.targetWallet.id != command.sourceWallet.id))

        val draft = if (isActionableTransaction) {
            TransactionDraftManager.createDraft(command, ownerUserId = activeUserId)
        } else null

        val amount = if (isActionableTransaction) (command.amount ?: 0.0) else 0.0
        val sourceWalletName = command.sourceWallet?.name ?: "Pilih Dompet"
        val targetWalletName = command.targetWallet?.name

        ParsedTransactionResult(
            title = command.title.ifBlank { command.categoryName },
            amount = amount,
            type = txTypeStr,
            category = command.categoryName.ifBlank { "Umum" },
            categoryIcon = command.categoryIcon.ifBlank { "payments" },
            suggestedWalletName = sourceWalletName,
            suggestedTargetWalletName = targetWalletName,
            note = if (command.detectedDateText != null) "Tanggal: ${command.detectedDateText}" else "Dicatat via Chatbot Lokal",
            tags = "#" + command.categoryName.split(" ").firstOrNull()?.lowercase().orEmpty(),
            aiExplanation = command.responseMessage,
            timestamp = command.timestamp,
            detectedDateText = command.detectedDateText,
            isValid = isActionableTransaction && draft != null,
            draftId = draft?.id,
            isWalletCreation = false,
            walletDraftId = null
        )
    }
}
