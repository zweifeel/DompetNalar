package com.example.data.chatbot.parser

import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import com.example.data.repository.FinanceRepository
import com.example.data.session.SessionManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Pengelola status dan siklus hidup Draft Transaksi (Transaction Draft & Confirmation Engine).
 *
 * Menerapkan prinsip:
 * 1. Menjaga draft dalam status PENDING sampai pengguna menekan tombol konfirmasi.
 * 2. Anti double-click / Idempotency: Mutex-guarded state transition agar transaksi tidak tersimpan ganda.
 * 3. Eksekusi langsung ke [FinanceRepository.insertTransaction] dengan isolasi [userId].
 * 4. Strict Fail-Closed Draft Ownership & Entitlement Guard:
 *    - userId > 0L && draft.ownerUserId > 0L && draft.ownerUserId == userId.
 *    - Pembuatan draft tanpa pemilik sah (ownerUserId <= 0L) langsung digagalkan (return null).
 *    - Konfirmasi draft tanpa pemilik sah, dari akun berbeda, atau tanpa entitlement SmartMaster langsung ditolak fail-closed.
 */
object TransactionDraftManager {

    enum class DraftStatus {
        PENDING,
        CONFIRMED,
        CANCELLED
    }

    data class TransactionDraft(
        val id: String = UUID.randomUUID().toString(),
        val title: String,
        val amount: Double,
        val type: TransactionType,
        val category: String,
        val categoryIcon: String,
        val sourceWallet: Wallet,
        val targetWallet: Wallet?,
        val timestamp: Long,
        val detectedDateText: String?,
        val note: String,
        val tags: String,
        val createdAt: Long = System.currentTimeMillis(),
        var status: DraftStatus = DraftStatus.PENDING,
        val ownerUserId: Long
    )

    const val DRAFT_MAX_AGE_MS = 24 * 3600 * 1000L // 24 jam batas kedaluwarsa draft

    private val activeDrafts = mutableMapOf<String, TransactionDraft>()
    private val executionMutex = Mutex()

    fun createDraft(
        command: LocalFinancialIntentParser.ParsedFinancialCommand,
        ownerUserId: Long = SessionManager.getActiveUserId() ?: 0L
    ): TransactionDraft? {
        if (ownerUserId <= 0L) return null
        val amount = command.amount ?: return null
        if (amount <= 0.0) return null
        val sourceWallet = command.sourceWallet ?: return null
        val type = command.transactionType ?: TransactionType.EXPENSE

        if (type == TransactionType.TRANSFER) {
            val targetWallet = command.targetWallet ?: return null
            if (targetWallet.id == sourceWallet.id) {
                return null
            }
        }

        val draft = TransactionDraft(
            title = command.title.ifBlank { command.categoryName },
            amount = amount,
            type = type,
            category = command.categoryName.ifBlank { if (type == TransactionType.INCOME) "Pemasukan Lain" else "Lain-lain" },
            categoryIcon = command.categoryIcon.ifBlank { "payments" },
            sourceWallet = sourceWallet,
            targetWallet = command.targetWallet,
            timestamp = command.timestamp,
            detectedDateText = command.detectedDateText,
            note = if (command.detectedDateText != null) {
                com.example.util.AppStrings.t("Dicatat via Chatbot Lokal • Tanggal: ${command.detectedDateText}", "Recorded via Local Chatbot • Date: ${command.detectedDateText}")
            } else {
                com.example.util.AppStrings.t("Dicatat otomatis via Chatbot DompetNalar", "Automatically recorded via DompetNalar Chatbot")
            },
            tags = "#" + command.categoryName.split(" ").firstOrNull()?.lowercase().orEmpty(),
            ownerUserId = ownerUserId
        )

        synchronized(activeDrafts) {
            activeDrafts[draft.id] = draft
        }
        return draft
    }

    fun getDraft(draftId: String): TransactionDraft? {
        return synchronized(activeDrafts) {
            activeDrafts[draftId]
        }
    }

    /**
     * Membatalkan draft sebelum disimpan.
     */
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

    /**
     * Mengubah dompet sumber pada draft (misal saat pengguna memilih dari opsi ambigu).
     */
    fun updateSourceWallet(draftId: String, newWallet: Wallet): TransactionDraft? {
        synchronized(activeDrafts) {
            val draft = activeDrafts[draftId] ?: return null
            if (draft.status == DraftStatus.PENDING) {
                val updated = draft.copy(sourceWallet = newWallet)
                activeDrafts[draftId] = updated
                return updated
            }
            return null
        }
    }

    /**
     * Menyimpan draft yang telah dikonfirmasi ke basis data Room melalui [FinanceRepository].
     * Mutex-guarded untuk mencegah double submission.
     */
    suspend fun confirmAndSave(
        draftId: String,
        repository: FinanceRepository,
        userId: Long,
        accountMode: String = "PERSONAL",
        isEntitlementUnlocked: () -> Boolean
    ): Result<Long> = executionMutex.withLock {
        val isUnlocked = try {
            isEntitlementUnlocked()
        } catch (e: Exception) {
            false
        }
        if (!isUnlocked) {
            return Result.failure(SecurityException(com.example.util.AppStrings.t("Akses ditolak: Fitur SmartMaster diperlukan untuk menyimpan transaksi melalui AI.", "Access denied: SmartMaster feature required to save transactions via AI.")))
        }

        if (userId <= 0L) {
            return Result.failure(SecurityException(com.example.util.AppStrings.t("Akses ditolak: Identitas pengguna tidak valid untuk eksekusi AI.", "Access denied: Invalid user identity for AI execution.")))
        }

        val draft = synchronized(activeDrafts) { activeDrafts[draftId] }
            ?: return Result.failure(IllegalStateException(com.example.util.AppStrings.t("Draft transaksi tidak ditemukan.", "Transaction draft not found.")))

        // Strict ownership invariant: userId > 0L && draft.ownerUserId > 0L && draft.ownerUserId == userId
        if (draft.ownerUserId <= 0L || draft.ownerUserId != userId) {
            return Result.failure(SecurityException(com.example.util.AppStrings.t("Akses ditolak: Draft transaksi tidak memiliki pemilik sah atau bukan milik pengguna aktif.", "Access denied: Transaction draft has no valid owner or does not belong to active user.")))
        }

        if (draft.status == DraftStatus.CONFIRMED) {
            return Result.failure(IllegalStateException(com.example.util.AppStrings.t("Transaksi ini sudah berhasil disimpan sebelumnya.", "This transaction has already been saved.")))
        }

        if (draft.status == DraftStatus.CANCELLED) {
            return Result.failure(IllegalStateException(com.example.util.AppStrings.t("Draft transaksi ini telah dibatalkan.", "This transaction draft was cancelled.")))
        }

        if (System.currentTimeMillis() - draft.createdAt > DRAFT_MAX_AGE_MS) {
            return Result.failure(IllegalStateException(com.example.util.AppStrings.t("Draft transaksi telah kedaluwarsa. Silakan masukkan kembali transaksi Anda.", "Transaction draft has expired. Please enter your transaction again.")))
        }

        val item = TransactionItem(
            title = draft.title,
            amount = draft.amount,
            type = draft.type,
            category = draft.category,
            categoryIcon = draft.categoryIcon,
            walletId = draft.sourceWallet.id,
            walletName = draft.sourceWallet.name,
            targetWalletId = draft.targetWallet?.id,
            targetWalletName = draft.targetWallet?.name,
            timestamp = draft.timestamp,
            note = draft.note,
            tags = draft.tags,
            accountMode = accountMode,
            userId = userId
        )

        return try {
            val insertedId = repository.insertTransaction(userId, item)
            synchronized(activeDrafts) {
                draft.status = DraftStatus.CONFIRMED
            }
            Result.success(insertedId)
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
