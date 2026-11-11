package com.example.data.chatbot.parser

import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.data.repository.FinanceRepository
import com.example.data.session.SessionManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Pengelola status dan siklus hidup Draft Pembuatan Dompet via Chat (Wallet Draft & Confirmation Engine).
 *
 * Menerapkan prinsip:
 * 1. Draft berstatus PENDING hingga pengguna menekan tombol konfirmasi.
 * 2. Idempotency & Mutex Guard: Menolak double-tap / double-submit.
 * 3. Pemisahan Saldo Awal vs Pemasukan:
 *    - Saldo awal: Diisikan langsung ke `Wallet.balance`, TANPA membuat transaksi INCOME (tidak menggelembungkan cashflow).
 *    - Pemasukan: Dompet dibuat dengan saldo 0, kemudian mencatat `TransactionItem(INCOME)` sehingga saldo disesuaikan tepat satu kali.
 * 4. Strict Fail-Closed Draft Ownership:
 *    - userId > 0L && draft.ownerUserId > 0L && draft.ownerUserId == userId.
 *    - Pembuatan draft tanpa pemilik sah (ownerUserId <= 0L) langsung digagalkan (return null).
 *    - Konfirmasi draft tanpa pemilik sah atau dari akun berbeda langsung ditolak dengan SecurityException.
 */
object WalletDraftManager {

    enum class DraftStatus {
        PENDING,
        CONFIRMED,
        CANCELLED
    }

    data class WalletDraft(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val type: WalletType,
        val initialBalance: Double = 0.0,
        val incomeAmount: Double? = null,
        val currencyCode: String? = null,
        val createdAt: Long = System.currentTimeMillis(),
        var status: DraftStatus = DraftStatus.PENDING,
        val ownerUserId: Long
    )

    private val activeDrafts = mutableMapOf<String, WalletDraft>()
    private val executionMutex = Mutex()

    fun createDraft(
        command: LocalFinancialIntentParser.ParsedFinancialCommand,
        ownerUserId: Long = SessionManager.getActiveUserId() ?: 0L
    ): WalletDraft? {
        if (ownerUserId <= 0L) return null
        val name = command.targetWalletNameStr?.ifBlank { command.title } ?: command.title
        if (name.isBlank()) return null

        val draft = WalletDraft(
            name = name,
            type = command.walletType ?: WalletType.BANK,
            initialBalance = command.initialBalance ?: 0.0,
            incomeAmount = command.incomeAmount,
            currencyCode = command.currencyCode,
            ownerUserId = ownerUserId
        )

        synchronized(activeDrafts) {
            activeDrafts[draft.id] = draft
        }
        return draft
    }

    fun getDraft(draftId: String): WalletDraft? {
        return synchronized(activeDrafts) {
            activeDrafts[draftId]
        }
    }

    fun cancelDraft(draftId: String): Boolean {
        synchronized(activeDrafts) {
            val draft = activeDrafts[draftId] ?: return false
            if (draft.status == DraftStatus.PENDING) {
                draft.status = DraftStatus.CANCELLED
                return true
            }
            return false
        }
    }

    suspend fun confirmAndSave(
        draftId: String,
        repository: FinanceRepository,
        userId: Long,
        accountMode: String = "PERSONAL",
        isEntitlementUnlocked: () -> Boolean
    ): Result<Wallet> = executionMutex.withLock {
        val isUnlocked = try {
            isEntitlementUnlocked()
        } catch (e: Exception) {
            false
        }
        if (!isUnlocked) {
            return Result.failure(SecurityException(com.example.util.AppStrings.t("Akses ditolak: Fitur SmartMaster diperlukan untuk membuat dompet melalui AI.", "Access denied: SmartMaster feature required to create wallet via AI.")))
        }
        if (userId <= 0L) {
            return Result.failure(SecurityException(com.example.util.AppStrings.t("Akses ditolak: Identitas pengguna tidak valid untuk eksekusi AI.", "Access denied: Invalid user identity for AI execution.")))
        }
        val draft = synchronized(activeDrafts) { activeDrafts[draftId] }
            ?: return Result.failure(IllegalStateException(com.example.util.AppStrings.t("Draft dompet tidak ditemukan.", "Wallet draft not found.")))

        // Strict ownership invariant: userId > 0L && draft.ownerUserId > 0L && draft.ownerUserId == userId
        if (draft.ownerUserId <= 0L || draft.ownerUserId != userId) {
            return Result.failure(SecurityException(com.example.util.AppStrings.t("Akses ditolak: Draft dompet tidak memiliki pemilik sah atau bukan milik pengguna aktif.", "Access denied: Wallet draft has no valid owner or does not belong to active user.")))
        }

        if (draft.status == DraftStatus.CONFIRMED) {
            return Result.failure(IllegalStateException(com.example.util.AppStrings.t("Dompet ini sudah berhasil dibuat sebelumnya.", "This wallet has already been created.")))
        }

        if (draft.status == DraftStatus.CANCELLED) {
            return Result.failure(IllegalStateException(com.example.util.AppStrings.t("Draft dompet ini telah dibatalkan.", "This wallet draft was cancelled.")))
        }

        val hasIncome = draft.incomeAmount != null && draft.incomeAmount > 0.0
        val startingBalance = if (hasIncome) 0.0 else draft.initialBalance

        val newWallet = Wallet(
            name = draft.name,
            type = draft.type,
            accountNumber = "",
            balance = startingBalance,
            colorHex = when (draft.type) {
                WalletType.CASH -> "#10B981"
                WalletType.BANK -> "#0B57D0"
                WalletType.EWALLET -> "#F59E0B"
                else -> "#6B7280"
            },
            iconName = when (draft.type) {
                WalletType.CASH -> "payments"
                WalletType.BANK -> "account_balance"
                WalletType.EWALLET -> "account_balance_wallet"
                else -> "wallet"
            },
            accountMode = accountMode,
            isDefault = false,
            userId = userId
        )

        return try {
            val insertedWalletId = repository.insertWallet(newWallet)
            val createdWallet = newWallet.copy(id = insertedWalletId)

            if (hasIncome) {
                val incomeTx = TransactionItem(
                    title = com.example.util.AppStrings.t("Pemasukan Awal ${draft.name}", "Initial Income ${draft.name}"),
                    amount = draft.incomeAmount ?: 0.0,
                    type = TransactionType.INCOME,
                    category = "Gaji Pokok",
                    categoryIcon = "payments",
                    walletId = insertedWalletId,
                    walletName = draft.name,
                    timestamp = System.currentTimeMillis(),
                    note = com.example.util.AppStrings.t("Dicatat otomatis saat pembuatan dompet via Chatbot", "Automatically recorded during wallet creation via Chatbot"),
                    tags = "#pemasukan",
                    accountMode = accountMode,
                    userId = userId
                )
                repository.insertTransaction(userId, incomeTx)
            }

            synchronized(activeDrafts) {
                draft.status = DraftStatus.CONFIRMED
            }
            Result.success(createdWallet)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun clearAllDrafts() {
        synchronized(activeDrafts) {
            activeDrafts.clear()
        }
    }

    fun clearAllForTesting() {
        clearAllDrafts()
    }
}
