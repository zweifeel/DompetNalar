package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.RecurringScheduleDao
import com.example.data.model.RecurringSchedule
import com.example.util.RecurringTransactionWorker
import com.example.data.local.CategoryDao
import com.example.data.local.DatabaseWriteCoordinator
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AssetCategory
import com.example.data.model.AssetItem
import com.example.data.model.BudgetLimitItem
import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPlanItem
import com.example.data.model.InstallmentPeriod
import com.example.data.model.SplitBillHistoryItem
import com.example.data.local.TransactionDao
import com.example.data.local.WalletDao
import com.example.data.model.AccountMode
import com.example.data.model.CategoryItem
import com.example.data.model.DomainCurrency
import com.example.data.model.Money
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * DEF-03 Stage 3: User-scoped Finance Repository.
 *
 * Every normal operation requires an explicit authenticated [userId].
 * Unscoped DAO methods are never invoked.
 */
class FinanceRepository(
    private val walletDao: WalletDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val database: FinSmartDatabase? = null,
    private val recurringScheduleDao: RecurringScheduleDao? = database?.recurringScheduleDao(),
    val coordinator: DatabaseWriteCoordinator = database?.writeCoordinator ?: DatabaseWriteCoordinator.getInstance()
) {
    constructor(walletDao: WalletDao, database: FinSmartDatabase) : this(
        walletDao = walletDao,
        transactionDao = database.transactionDao(),
        categoryDao = database.categoryDao(),
        database = database,
        recurringScheduleDao = database.recurringScheduleDao(),
        coordinator = database.writeCoordinator
    )

    fun getWallets(userId: Long, accountMode: AccountMode): Flow<List<Wallet>> {
        return walletDao.getWalletsByMode(userId, accountMode.name)
    }

    fun getAllWallets(userId: Long): Flow<List<Wallet>> {
        return walletDao.getAllWallets(userId)
    }

    suspend fun getWalletById(userId: Long, id: Long): Wallet? {
        return walletDao.getWalletById(id, userId)
    }

    suspend fun insertWallet(wallet: Wallet): Long = coordinator.withWritePermit {
        requireNotNull(wallet.userId) { "Cannot insert wallet without an owning userId" }
        val resolvedCurrency = wallet.currencyCode ?: com.example.util.DeviceRegionResolver.resolveDeviceCurrencyCode()
        walletDao.insertWallet(wallet.copy(currencyCode = resolvedCurrency))
    }

    suspend fun updateWallet(userId: Long, wallet: Wallet): Int = coordinator.withWritePermit {
        require(userId > 0L) { "Operasi memerlukan user ID aktif yang valid." }
        val block: suspend () -> Int = {
            val liveWallet = walletDao.getWalletById(wallet.id, userId)
                ?: throw IllegalArgumentException("Dompet #${wallet.id} tidak ditemukan atau bukan milik pengguna #$userId.")

            // P-17 Balance Protection: Reject any attempt to directly modify wallet balance via updateWallet
            if (liveWallet.balance != wallet.balance) {
                throw IllegalArgumentException("Modifikasi saldo langsung melalui updateWallet dilarang. Seluruh perubahan saldo wajib melalui mutasi transaksi resmi.")
            }

            val rows = walletDao.updateWalletMetadata(
                id = wallet.id,
                userId = userId,
                name = wallet.name,
                type = wallet.type,
                accountNumber = wallet.accountNumber,
                colorHex = wallet.colorHex,
                iconName = wallet.iconName,
                accountMode = wallet.accountMode,
                isDefault = wallet.isDefault
            )
            check(rows == 1) { "Gagal memperbarui metadata dompet: baris terdampak $rows" }
            rows
        }
        if (database != null) {
            database.withTransaction { block() }
        } else {
            block()
        }
    }

    suspend fun deleteWallet(userId: Long, wallet: Wallet): Int = coordinator.withWritePermit {
        require(userId > 0L) { "Operasi memerlukan user ID aktif yang valid." }
        val txCount = transactionDao.getTransactionCountByWallet(wallet.id, userId)
        val scheduleDao = recurringScheduleDao ?: database?.recurringScheduleDao()
        val scheduleCount = scheduleDao?.getScheduleCountByWallet(wallet.id, userId) ?: 0
        if (txCount > 0 || scheduleCount > 0) {
            throw IllegalStateException(
                "Dompet '${wallet.name}' memiliki riwayat data ($txCount transaksi, $scheduleCount jadwal berulang) " +
                "dan tidak dapat dihapus permanen demi integritas pembukuan. Silakan gunakan fitur Arsipkan Dompet."
            )
        }
        walletDao.deleteWallet(wallet, userId)
    }

    suspend fun archiveWallet(userId: Long, walletId: Long, context: Context? = null): Result<Int> = coordinator.withWritePermit {
        withContext(Dispatchers.IO) {
        require(userId > 0L) { "Operasi memerlukan user ID aktif yang valid." }
        try {
            val db = database
            val block: suspend () -> Int = block@{
                val activeWalletDao = db?.walletDao() ?: walletDao
                val currentWallet = activeWalletDao.getWalletById(walletId, userId)
                    ?: throw IllegalArgumentException("Dompet #$walletId tidak ditemukan atau bukan milik pengguna #$userId.")
                if (currentWallet.isArchived) {
                    return@block 0
                }
                val rows = activeWalletDao.setWalletArchived(walletId, userId, true)
                check(rows == 1) { "Gagal mengarsipkan dompet #$walletId" }

                val activeSchedDao = db?.recurringScheduleDao() ?: recurringScheduleDao
                val disabledCount = activeSchedDao?.disableSchedulesByWallet(walletId, userId) ?: 0
                if (disabledCount > 0 && context != null) {
                    RecurringTransactionWorker.sendLocalNotification(
                        context = context,
                        title = com.example.util.AppStrings.t("Jadwal Berulang Dinonaktifkan", "Recurring Schedules Disabled"),
                        message = if (com.example.util.LocaleHelper.isEnglish()) {
                            "$disabledCount recurring transactions were automatically disabled because wallet '${currentWallet.name}' was archived."
                        } else {
                            "$disabledCount transaksi rutin dinonaktifkan otomatis karena dompet '${currentWallet.name}' telah diarsipkan."
                        }
                    )
                }
                disabledCount
            }
            val count = if (db != null) db.withTransaction { block() } else block()
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
        }
    }

    suspend fun unarchiveWallet(userId: Long, walletId: Long): Result<Unit> = coordinator.withWritePermit {
        withContext(Dispatchers.IO) {
        require(userId > 0L) { "Operasi memerlukan user ID aktif yang valid." }
        try {
            val db = database
            val block: suspend () -> Unit = {
                val activeWalletDao = db?.walletDao() ?: walletDao
                val currentWallet = activeWalletDao.getWalletById(walletId, userId)
                    ?: throw IllegalArgumentException("Dompet #$walletId tidak ditemukan atau bukan milik pengguna #$userId.")
                val rows = activeWalletDao.setWalletArchived(walletId, userId, false)
                check(rows == 1) { "Gagal mengaktifkan kembali dompet #$walletId" }
                // POL-ARCH-01: Re-activating wallet does NOT auto-enable recurring schedules
            }
            if (db != null) db.withTransaction { block() } else block()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
        }
    }

    suspend fun transferArchivedWalletBalance(
        userId: Long,
        sourceWalletId: Long,
        targetWalletId: Long
    ): Result<Long> = coordinator.withWritePermit {
        withContext(Dispatchers.IO) {
        require(userId > 0L) { "Operasi memerlukan user ID aktif yang valid." }
        require(sourceWalletId != targetWalletId) { "Dompet tujuan harus berbeda dari dompet sumber." }

        try {
            val db = database
            val block: suspend () -> Long = {
                val activeWalletDao = db?.walletDao() ?: walletDao
                val activeTxDao = db?.transactionDao() ?: transactionDao

                val sourceWallet = activeWalletDao.getWalletById(sourceWalletId, userId)
                    ?: throw IllegalArgumentException("Dompet sumber #$sourceWalletId tidak ditemukan.")
                require(sourceWallet.isArchived) { "Metode ini khusus untuk memindahkan saldo dari dompet yang diarsipkan." }
                require(sourceWallet.balance > 0.0) { "Dompet sumber '${sourceWallet.name}' tidak memiliki saldo tersisa untuk dipindahkan." }
                requireNotNull(sourceWallet.currencyCode) { "Dompet sumber belum memiliki mata uang yang ditetapkan." }

                val targetWallet = activeWalletDao.getWalletById(targetWalletId, userId)
                    ?: throw IllegalArgumentException("Dompet tujuan #$targetWalletId tidak ditemukan.")
                require(!targetWallet.isArchived) { "Dompet tujuan '${targetWallet.name}' tidak boleh dalam status diarsipkan." }
                requireNotNull(targetWallet.currencyCode) { "Dompet tujuan belum memiliki mata uang yang ditetapkan." }
                require(sourceWallet.currencyCode == targetWallet.currencyCode) {
                    "Transfer saldo dompet terarsip lintas mata uang (${sourceWallet.currencyCode} -> ${targetWallet.currencyCode}) dilarang (DEC-PROD-02 Guard)."
                }

                val transferAmount = sourceWallet.balance

                val debitRows = activeWalletDao.adjustBalance(sourceWalletId, userId, -transferAmount)
                check(debitRows == 1) { "Gagal mengosongkan saldo dompet terarsip" }
                val creditRows = activeWalletDao.adjustBalance(targetWalletId, userId, transferAmount)
                check(creditRows == 1) { "Gagal memindahkan saldo ke dompet tujuan" }

                val transferTx = TransactionItem(
                    title = "Pemindahan Sisa Saldo: ${sourceWallet.name} -> ${targetWallet.name}",
                    amount = transferAmount,
                    type = TransactionType.TRANSFER,
                    category = "Transfer",
                    categoryIcon = "swap_horiz",
                    walletId = sourceWalletId,
                    walletName = sourceWallet.name,
                    targetWalletId = targetWalletId,
                    targetWalletName = targetWallet.name,
                    timestamp = System.currentTimeMillis(),
                    note = "Pemindahan sisa saldo penutupan/arsip dompet (POL-ARCH-01)",
                    tags = "archive,transfer",
                    accountMode = sourceWallet.accountMode,
                    userId = userId
                )
                val insertedId = activeTxDao.insertTransaction(transferTx)
                check(insertedId > 0L) { "Gagal mencatat transaksi pemindahan saldo" }
                insertedId
            }

            val txId = if (db != null) db.withTransaction { block() } else block()
            Result.success(txId)
        } catch (e: Exception) {
            Result.failure(e)
        }
        }
    }

    fun getTransactions(userId: Long, accountMode: AccountMode): Flow<List<TransactionItem>> {
        return transactionDao.getTransactionsByMode(userId, accountMode.name)
    }

    fun getTransactionsByWallet(userId: Long, walletId: Long): Flow<List<TransactionItem>> {
        return transactionDao.getTransactionsByWallet(userId, walletId)
    }

    fun getTransactionsBetween(userId: Long, startTime: Long, endTime: Long, accountMode: AccountMode): Flow<List<TransactionItem>> {
        return transactionDao.getTransactionsBetween(userId, startTime, endTime, accountMode.name)
    }

    /**
     * Neutralized in Phase 1G:
     * Automatic BCA default wallet creation has been removed.
     * New users start with 0 wallets until explicitly created by the user.
     */
    suspend fun provisionDefaultWalletsIfEmpty(userId: Long): List<Wallet> = coordinator.withWritePermit {
        emptyList()
    }

    suspend fun insertTransaction(userId: Long, transaction: TransactionItem): Long = coordinator.withWritePermit {
        require(userId > 0L) { "Operasi memerlukan user ID aktif yang valid." }
        require(transaction.amount > 0.0 && !transaction.amount.isNaN() && !transaction.amount.isInfinite()) {
            "Nominal transaksi harus berupa angka positif yang sah."
        }

        val block: suspend () -> Long = {
            val sourceWallet = walletDao.getWalletById(transaction.walletId, userId)
                ?: throw IllegalArgumentException("Dompet sumber #${transaction.walletId} tidak ditemukan atau bukan milik pengguna #$userId.")
            require(!sourceWallet.isArchived) {
                "Dompet sumber '${sourceWallet.name}' dalam status diarsipkan. Transaksi finansial baru ditolak (POL-ARCH-01 Freeze)."
            }
            requireNotNull(sourceWallet.currencyCode) {
                "Dompet sumber #${transaction.walletId} belum memiliki mata uang yang ditetapkan (State 2). Transaksi finansial ditolak."
            }

            if (transaction.type == TransactionType.TRANSFER) {
                val targetId = transaction.targetWalletId
                    ?: throw IllegalArgumentException("Dompet tujuan harus ditentukan untuk transaksi transfer.")
                if (transaction.walletId == targetId) {
                    throw IllegalArgumentException("Dompet sumber dan dompet tujuan transfer tidak boleh sama.")
                }
                val targetWallet = walletDao.getWalletById(targetId, userId)
                    ?: throw IllegalArgumentException("Dompet tujuan #$targetId tidak ditemukan atau bukan milik pengguna #$userId.")
                require(!targetWallet.isArchived) {
                    "Transfer masuk ke dompet tujuan '${targetWallet.name}' ditolak karena dompet dalam status diarsipkan (POL-ARCH-01 Freeze)."
                }
                requireNotNull(targetWallet.currencyCode) {
                    "Dompet tujuan #$targetId belum memiliki mata uang yang ditetapkan (State 2). Transaksi finansial ditolak."
                }
                require(sourceWallet.currencyCode == targetWallet.currencyCode) {
                    "Transfer lintas mata uang (${sourceWallet.currencyCode} -> ${targetWallet.currencyCode}) sementara dinonaktifkan (DEC-PROD-02 Guard)."
                }

                val debitRows = walletDao.adjustBalance(transaction.walletId, userId, -transaction.amount)
                check(debitRows == 1) { "Gagal mendebit saldo dompet sumber transfer" }
                val creditRows = walletDao.adjustBalance(targetId, userId, transaction.amount)
                check(creditRows == 1) { "Gagal mengkredit saldo dompet tujuan transfer" }
            } else {
                require(transaction.targetWalletId == null) {
                    "Transaksi non-transfer tidak boleh memiliki dompet tujuan."
                }
                when (transaction.type) {
                    TransactionType.INCOME -> {
                        val rows = walletDao.adjustBalance(transaction.walletId, userId, transaction.amount)
                        check(rows == 1) { "Gagal mengkredit saldo dompet pendapatan" }
                    }
                    TransactionType.EXPENSE -> {
                        val rows = walletDao.adjustBalance(transaction.walletId, userId, -transaction.amount)
                        check(rows == 1) { "Gagal mendebit saldo dompet pengeluaran" }
                    }
                    else -> Unit
                }
            }

            val scopedTransaction = transaction.copy(userId = userId)
            val id = transactionDao.insertTransaction(scopedTransaction)
            check(id > 0L) { "Gagal mencatat transaksi ke database" }
            id
        }

        if (database != null) {
            database.withTransaction { block() }
        } else {
            block()
        }
    }

    suspend fun deleteTransaction(userId: Long, transaction: TransactionItem): Int = coordinator.withWritePermit {
        require(userId > 0L) { "Operasi memerlukan user ID aktif yang valid." }
        val block: suspend () -> Int = {
            // Authoritative in-transaction re-read
            val liveTx = transactionDao.getTransactionById(transaction.id, userId)
                ?: throw IllegalArgumentException("Transaksi #${transaction.id} tidak ditemukan atau bukan milik pengguna #$userId.")

            val sourceWallet = walletDao.getWalletById(liveTx.walletId, userId)
                ?: throw IllegalArgumentException("Dompet sumber #${liveTx.walletId} tidak ditemukan.")
            if (sourceWallet.isArchived) {
                throw IllegalStateException("Transaksi #${liveTx.id} tidak dapat dihapus karena dompet sumber '${sourceWallet.name}' dalam status diarsipkan (POL-ARCH-01 Historical Freeze).")
            }
            requireNotNull(sourceWallet.currencyCode) {
                "Dompet sumber #${liveTx.walletId} belum memiliki mata uang (State 2)."
            }

            when (liveTx.type) {
                TransactionType.INCOME -> {
                    val rows = walletDao.adjustBalance(liveTx.walletId, userId, -liveTx.amount)
                    check(rows == 1) { "Gagal membalikkan saldo pendapatan" }
                }
                TransactionType.EXPENSE -> {
                    // FIX DEFECT LINE 198: Deleting an expense must CREDIT the amount back to wallet!
                    val rows = walletDao.adjustBalance(liveTx.walletId, userId, liveTx.amount)
                    check(rows == 1) { "Gagal membalikkan saldo pengeluaran" }
                }
                TransactionType.TRANSFER -> {
                    val targetId = liveTx.targetWalletId
                        ?: throw IllegalStateException("Transaksi transfer #${liveTx.id} tidak memiliki targetWalletId yang sah.")
                    val targetWallet = walletDao.getWalletById(targetId, userId)
                        ?: throw IllegalArgumentException("Dompet tujuan #$targetId tidak ditemukan.")
                    if (targetWallet.isArchived) {
                        throw IllegalStateException("Transaksi transfer #${liveTx.id} tidak dapat dihapus karena dompet tujuan '${targetWallet.name}' dalam status diarsipkan (POL-ARCH-01 Historical Freeze).")
                    }
                    requireNotNull(targetWallet.currencyCode) {
                        "Dompet tujuan #$targetId belum memiliki mata uang (State 2)."
                    }

                    // Source was debited, so reverse credits it back
                    val debitRevRows = walletDao.adjustBalance(liveTx.walletId, userId, liveTx.amount)
                    check(debitRevRows == 1) { "Gagal membalikkan debit transfer dompet sumber" }

                    // Target was credited, so reverse debits it back
                    val creditRevRows = walletDao.adjustBalance(targetId, userId, -liveTx.amount)
                    check(creditRevRows == 1) { "Gagal membalikkan kredit transfer dompet tujuan" }
                }
            }
            val deletedRows = transactionDao.deleteTransactionById(liveTx.id, userId)
            check(deletedRows == 1) { "Gagal menghapus catatan transaksi" }
            deletedRows
        }

        if (database != null) {
            database.withTransaction { block() }
        } else {
            block()
        }
    }

    suspend fun updateTransaction(userId: Long, oldTransaction: TransactionItem, newTransaction: TransactionItem): Int = coordinator.withWritePermit {
        require(userId > 0L) { "Operasi memerlukan user ID aktif yang valid." }
        require(newTransaction.amount > 0.0 && !newTransaction.amount.isNaN() && !newTransaction.amount.isInfinite()) {
            "Nominal transaksi harus berupa angka positif yang sah."
        }
        val block: suspend () -> Int = {
            // Authoritative in-transaction re-read of existing transaction
            val liveOldTx = transactionDao.getTransactionById(oldTransaction.id, userId)
                ?: throw IllegalArgumentException("Transaksi #${oldTransaction.id} tidak ditemukan atau bukan milik pengguna #$userId.")

            val oldSourceWallet = walletDao.getWalletById(liveOldTx.walletId, userId)
                ?: throw IllegalArgumentException("Dompet sumber lama #${liveOldTx.walletId} tidak ditemukan.")
            if (oldSourceWallet.isArchived) {
                throw IllegalStateException("Transaksi #${liveOldTx.id} tidak dapat diubah karena dompet sumber '${oldSourceWallet.name}' dalam status diarsipkan (POL-ARCH-01 Historical Freeze).")
            }
            requireNotNull(oldSourceWallet.currencyCode) {
                "Dompet sumber lama belum memiliki mata uang (State 2)."
            }

            // 1. Rollback old transaction impact
            when (liveOldTx.type) {
                TransactionType.INCOME -> {
                    val r = walletDao.adjustBalance(liveOldTx.walletId, userId, -liveOldTx.amount)
                    check(r == 1) { "Gagal membalikkan saldo pendapatan lama" }
                }
                TransactionType.EXPENSE -> {
                    val r = walletDao.adjustBalance(liveOldTx.walletId, userId, liveOldTx.amount)
                    check(r == 1) { "Gagal membalikkan saldo pengeluaran lama" }
                }
                TransactionType.TRANSFER -> {
                    val oldTargetId = liveOldTx.targetWalletId
                        ?: throw IllegalStateException("Transaksi transfer lama tidak memiliki targetWalletId.")
                    val oldTargetWallet = walletDao.getWalletById(oldTargetId, userId)
                        ?: throw IllegalArgumentException("Dompet tujuan lama tidak ditemukan.")
                    if (oldTargetWallet.isArchived) {
                        throw IllegalStateException("Transaksi transfer #${liveOldTx.id} tidak dapat diubah karena dompet tujuan lama '${oldTargetWallet.name}' dalam status diarsipkan (POL-ARCH-01 Historical Freeze).")
                    }
                    requireNotNull(oldTargetWallet.currencyCode) {
                        "Dompet tujuan lama belum memiliki mata uang (State 2)."
                    }

                    val r1 = walletDao.adjustBalance(liveOldTx.walletId, userId, liveOldTx.amount)
                    check(r1 == 1) { "Gagal membalikkan debit transfer lama" }
                    val r2 = walletDao.adjustBalance(oldTargetId, userId, -liveOldTx.amount)
                    check(r2 == 1) { "Gagal membalikkan kredit transfer lama" }
                }
            }

            // 2. Validate new transaction parameters
            val newSourceWallet = walletDao.getWalletById(newTransaction.walletId, userId)
                ?: throw IllegalArgumentException("Dompet sumber baru #${newTransaction.walletId} tidak ditemukan.")
            if (newSourceWallet.isArchived) {
                throw IllegalStateException("Transaksi tidak dapat dialihkan ke dompet sumber '${newSourceWallet.name}' yang dalam status diarsipkan (POL-ARCH-01 Freeze).")
            }
            requireNotNull(newSourceWallet.currencyCode) {
                "Dompet sumber baru belum memiliki mata uang (State 2)."
            }

            if (newTransaction.type == TransactionType.TRANSFER) {
                val newTargetId = newTransaction.targetWalletId
                    ?: throw IllegalArgumentException("Dompet tujuan harus ditentukan untuk transfer.")
                if (newTransaction.walletId == newTargetId) {
                    throw IllegalArgumentException("Dompet sumber dan tujuan transfer tidak boleh sama.")
                }
                val newTargetWallet = walletDao.getWalletById(newTargetId, userId)
                    ?: throw IllegalArgumentException("Dompet tujuan baru #$newTargetId tidak ditemukan.")
                if (newTargetWallet.isArchived) {
                    throw IllegalStateException("Transaksi transfer tidak dapat dialihkan ke dompet tujuan '${newTargetWallet.name}' yang dalam status diarsipkan (POL-ARCH-01 Freeze).")
                }
                requireNotNull(newTargetWallet.currencyCode) {
                    "Dompet tujuan baru belum memiliki mata uang (State 2)."
                }
                require(newSourceWallet.currencyCode == newTargetWallet.currencyCode) {
                    "Transfer lintas mata uang (${newSourceWallet.currencyCode} -> ${newTargetWallet.currencyCode}) sementara dinonaktifkan (DEC-PROD-02 Guard)."
                }

                val r1 = walletDao.adjustBalance(newTransaction.walletId, userId, -newTransaction.amount)
                check(r1 == 1) { "Gagal mendebit transfer baru" }
                val r2 = walletDao.adjustBalance(newTargetId, userId, newTransaction.amount)
                check(r2 == 1) { "Gagal mengkredit transfer baru" }
            } else {
                require(newTransaction.targetWalletId == null) {
                    "Transaksi non-transfer tidak boleh memiliki targetWalletId."
                }
                when (newTransaction.type) {
                    TransactionType.INCOME -> {
                        val r = walletDao.adjustBalance(newTransaction.walletId, userId, newTransaction.amount)
                        check(r == 1) { "Gagal mengkredit pendapatan baru" }
                    }
                    TransactionType.EXPENSE -> {
                        val r = walletDao.adjustBalance(newTransaction.walletId, userId, -newTransaction.amount)
                        check(r == 1) { "Gagal mendebit pengeluaran baru" }
                    }
                    else -> Unit
                }
            }

            // 3. Update transaction record
            val updatedTx = newTransaction.copy(id = liveOldTx.id, userId = userId)
            val rows = transactionDao.updateTransaction(updatedTx, userId)
            check(rows == 1) { "Gagal memperbarui catatan transaksi di database" }
            rows
        }

        if (database != null) {
            database.withTransaction { block() }
        } else {
            block()
        }
    }

    suspend fun reverseTransaction(
        userId: Long,
        originalTransactionId: Long,
        reason: String = ""
    ): Result<Long> = coordinator.withWritePermit {
        require(userId > 0L) { "Operasi memerlukan user ID aktif yang valid." }
        require(originalTransactionId > 0L) { "ID transaksi original harus valid." }

        val block: suspend () -> Long = {
            val origTx = transactionDao.getTransactionById(originalTransactionId, userId)
                ?: throw IllegalArgumentException("Transaksi #$originalTransactionId tidak ditemukan atau bukan milik pengguna #$userId.")

            check(!origTx.isReversal) { "Transaksi #${origTx.id} merupakan transaksi reversal; tidak dapat membalik transaksi reversal." }
            check(origTx.reversedTransactionId == null) { "Transaksi #${origTx.id} sudah merupakan reversal." }

            val sourceWallet = walletDao.getWalletById(origTx.walletId, userId)
                ?: throw IllegalArgumentException("Dompet sumber #${origTx.walletId} tidak ditemukan.")
            if (sourceWallet.isArchived) {
                throw IllegalStateException("Transaksi tidak dapat dibalik karena dompet '${sourceWallet.name}' dalam status diarsipkan (POL-ARCH-01 Freeze).")
            }
            requireNotNull(sourceWallet.currencyCode) { "Dompet sumber belum memiliki mata uang yang ditetapkan." }
            require(origTx.currencyCode == sourceWallet.currencyCode) {
                "Mata uang transaksi (${origTx.currencyCode}) tidak cocok dengan dompet sumber (${sourceWallet.currencyCode})."
            }

            val reversalTx = when (origTx.type) {
                TransactionType.EXPENSE -> {
                    // Original EXPENSE debited sourceWallet. Reversal credits sourceWallet.
                    val rows = walletDao.adjustBalance(origTx.walletId, userId, origTx.amount)
                    check(rows == 1) { "Gagal membalikkan saldo pengeluaran pada dompet #${origTx.walletId}." }

                    TransactionItem(
                        title = if (reason.isNotBlank()) "Pembalikan: ${origTx.title} ($reason)" else "Pembalikan: ${origTx.title}",
                        amount = origTx.amount,
                        amountMinorUnits = origTx.amountMinorUnits,
                        currencyCode = origTx.currencyCode,
                        type = TransactionType.INCOME,
                        category = "Pembalikan",
                        categoryIcon = "undo",
                        walletId = origTx.walletId,
                        walletName = origTx.walletName,
                        targetWalletId = null,
                        targetWalletName = null,
                        timestamp = System.currentTimeMillis(),
                        note = "Pembalikan otomatis transaksi #${origTx.id}. $reason".trim(),
                        tags = "reversal",
                        accountMode = origTx.accountMode,
                        userId = userId,
                        isReversal = true,
                        reversedTransactionId = origTx.id
                    )
                }
                TransactionType.INCOME -> {
                    // Original INCOME credited sourceWallet. Reversal debits sourceWallet.
                    val rows = walletDao.adjustBalance(origTx.walletId, userId, -origTx.amount)
                    check(rows == 1) { "Gagal membalikkan saldo pemasukan pada dompet #${origTx.walletId}." }

                    TransactionItem(
                        title = if (reason.isNotBlank()) "Pembalikan: ${origTx.title} ($reason)" else "Pembalikan: ${origTx.title}",
                        amount = origTx.amount,
                        amountMinorUnits = origTx.amountMinorUnits,
                        currencyCode = origTx.currencyCode,
                        type = TransactionType.EXPENSE,
                        category = "Pembalikan",
                        categoryIcon = "undo",
                        walletId = origTx.walletId,
                        walletName = origTx.walletName,
                        targetWalletId = null,
                        targetWalletName = null,
                        timestamp = System.currentTimeMillis(),
                        note = "Pembalikan otomatis transaksi #${origTx.id}. $reason".trim(),
                        tags = "reversal",
                        accountMode = origTx.accountMode,
                        userId = userId,
                        isReversal = true,
                        reversedTransactionId = origTx.id
                    )
                }
                TransactionType.TRANSFER -> {
                    val origTargetId = origTx.targetWalletId
                        ?: throw IllegalStateException("Transaksi transfer #${origTx.id} tidak memiliki targetWalletId yang sah.")
                    val targetWallet = walletDao.getWalletById(origTargetId, userId)
                        ?: throw IllegalArgumentException("Dompet tujuan #$origTargetId tidak ditemukan.")
                    if (targetWallet.isArchived) {
                        throw IllegalStateException("Transaksi transfer #${origTx.id} tidak dapat dibalik karena dompet tujuan '${targetWallet.name}' dalam status diarsipkan (POL-ARCH-01 Freeze).")
                    }
                    requireNotNull(targetWallet.currencyCode) { "Dompet tujuan belum memiliki mata uang yang ditetapkan." }
                    require(sourceWallet.currencyCode == targetWallet.currencyCode) {
                        "Transfer lintas mata uang (${sourceWallet.currencyCode} -> ${targetWallet.currencyCode}) tidak didukung untuk pembalikan otomatis."
                    }

                    // Original transfer debited sourceWallet and credited targetWallet.
                    // Reversal must debit targetWallet and credit sourceWallet:
                    val debitRows = walletDao.adjustBalance(origTargetId, userId, -origTx.amount)
                    check(debitRows == 1) { "Gagal membalikkan kredit pada dompet tujuan transfer #$origTargetId." }

                    val creditRows = walletDao.adjustBalance(origTx.walletId, userId, origTx.amount)
                    check(creditRows == 1) { "Gagal membalikkan debit pada dompet sumber transfer #${origTx.walletId}." }

                    TransactionItem(
                        title = if (reason.isNotBlank()) "Pembalikan Transfer: ${origTx.title} ($reason)" else "Pembalikan Transfer: ${origTx.title}",
                        amount = origTx.amount,
                        amountMinorUnits = origTx.amountMinorUnits,
                        currencyCode = origTx.currencyCode,
                        type = TransactionType.TRANSFER,
                        category = "Transfer",
                        categoryIcon = "swap_horiz",
                        walletId = origTargetId, // Swapped! Reversal source is original destination
                        walletName = targetWallet.name,
                        targetWalletId = origTx.walletId, // Swapped! Reversal destination is original source
                        targetWalletName = sourceWallet.name,
                        timestamp = System.currentTimeMillis(),
                        note = "Pembalikan transfer #${origTx.id} dari ${targetWallet.name} kembali ke ${sourceWallet.name}. $reason".trim(),
                        tags = "reversal,transfer",
                        accountMode = origTx.accountMode,
                        userId = userId,
                        isReversal = true,
                        reversedTransactionId = origTx.id
                    )
                }
            }

            val insertedId = transactionDao.insertTransaction(reversalTx)
            check(insertedId > 0L) { "Gagal mencatat transaksi pembalikan." }
            insertedId
        }

        try {
            val id = if (database != null) {
                database.withTransaction { block() }
            } else {
                block()
            }
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun payInstallmentAuthoritative(
        userId: Long,
        planId: String,
        walletId: Long?,
        notes: String?
    ): Result<Unit> = coordinator.withWritePermit {
        withContext(Dispatchers.IO) {
        require(userId > 0L) { "Operasi memerlukan user ID aktif yang valid." }
        val db = database ?: return@withContext Result.failure(IllegalStateException("Database instance is required for payInstallmentAuthoritative"))
        try {
            db.withTransaction {
                val planDao = db.installmentPlanDao()
                val currentPlan = planDao.getInstallmentPlanById(planId, userId)
                    ?: throw IllegalArgumentException("Rencana cicilan #$planId tidak ditemukan.")
                if (currentPlan.remainingTenor <= 0 || currentPlan.isCompleted) {
                    throw IllegalStateException("Rencana cicilan sudah selesai atau tidak memiliki sisa tenor.")
                }

                if (walletId != null && walletId > 0L) {
                    val wallet = walletDao.getWalletById(walletId, userId)
                        ?: throw IllegalArgumentException("Dompet #$walletId tidak ditemukan.")
                    requireNotNull(wallet.currencyCode) {
                        "Dompet #${wallet.id} belum memiliki mata uang yang ditetapkan (State 2)."
                    }
                    if (wallet.balance < currentPlan.installmentAmount) {
                        throw IllegalStateException("Saldo dompet '${wallet.name}' tidak mencukupi untuk pembayaran cicilan.")
                    }

                    val debitRows = walletDao.adjustBalance(walletId, userId, -currentPlan.installmentAmount)
                    check(debitRows == 1) { "Gagal mendebit saldo dompet untuk cicilan" }

                    val newPaidCount = currentPlan.paidCount + 1
                    val paymentTx = TransactionItem(
                        title = "Angsuran ${currentPlan.name} ($newPaidCount/${currentPlan.totalTenor})",
                        amount = currentPlan.installmentAmount,
                        type = TransactionType.EXPENSE,
                        category = "Cicilan & Hutang",
                        categoryIcon = "credit_card",
                        walletId = walletId,
                        walletName = wallet.name,
                        timestamp = System.currentTimeMillis(),
                        note = notes ?: "Pembayaran angsuran ke-$newPaidCount dari ${currentPlan.totalTenor}",
                        tags = "installment_plan_id:${currentPlan.id},period:$newPaidCount",
                        userId = userId
                    )
                    val txId = transactionDao.insertTransaction(paymentTx)
                    check(txId > 0L) { "Gagal mencatat transaksi pembayaran cicilan" }
                }

                val newPaidCount = currentPlan.paidCount + 1
                val newRemaining = currentPlan.totalTenor - newPaidCount
                val isNowCompleted = newRemaining <= 0
                val newStatus = if (isNowCompleted) "SELESAI" else "AKTIF"

                val nextDue = if (!isNowCompleted) {
                    com.example.util.InstallmentDateHelper.calculateNextDueDate(currentPlan.nextDueDate, currentPlan.anchorDay)
                } else {
                    currentPlan.nextDueDate
                }

                val newRemainingPrincipal = currentPlan.totalRemainingPrincipal?.let {
                    val updated = it - currentPlan.installmentAmount
                    if (updated < 0.0) 0.0 else updated
                }

                val updatedPlan = currentPlan.copy(
                    paidCount = newPaidCount,
                    status = newStatus,
                    nextDueDate = nextDue,
                    totalRemainingPrincipal = newRemainingPrincipal,
                    lastPaymentDate = System.currentTimeMillis()
                )
                planDao.updateInstallmentPlan(updatedPlan)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
        }
    }

    fun getAllCategories(userId: Long): Flow<List<CategoryItem>> {
        return categoryDao.getAllCategories(userId)
    }

    fun getCategoriesByType(userId: Long, type: TransactionType): Flow<List<CategoryItem>> {
        return categoryDao.getCategoriesByType(userId, type)
    }

    suspend fun insertCategory(category: CategoryItem): Long = coordinator.withWritePermit {
        categoryDao.insertCategory(category)
    }

    suspend fun deleteCategory(userId: Long, category: CategoryItem): Int = coordinator.withWritePermit {
        categoryDao.deleteCategory(category, userId)
    }

    /**
     * P0 Data Loss Prevention: Atomically reassigns financial and personal data
     * from a temporary guest account to a newly authenticated or registered user account.
     * Prevents data wiping when a guest user signs in with Google or registers via OTP.
     *
     * Invariants & Safety:
     * - Only operates if [fromUserId] is a genuine guest user (isGuest == 1).
     * - Safely resolves budget limit unique constraints (userId, categoryOrTotalId)
     *   by keeping the existing user's budget if a conflict exists.
     * - Safely ignores system categories (isSystem == 1) and retains guest custom categories.
     * - Preserves feedback submissions without corrupting identity.
     * - Executed within atomic Room withTransaction boundary with full rollback on failure.
     */
    suspend fun reassignUserData(fromUserId: Long, toUserId: Long) = coordinator.withWritePermit {
        if (fromUserId <= 0L || toUserId <= 0L || fromUserId == toUserId) return@withWritePermit
        val db = database ?: return@withWritePermit
        db.withTransaction {
            val openHelper = db.openHelper
            val writableDb = openHelper.writableDatabase

            // Verify fromUserId is indeed a guest user to prevent accidental cross-account reassignment
            val cursor = writableDb.query("SELECT isGuest FROM users WHERE id = ?", arrayOf(fromUserId))
            val isGuest = cursor.use {
                if (it.moveToFirst()) it.getInt(0) == 1 else false
            }
            if (!isGuest) {
                // Not a guest account: abort reassignment to prevent data hijacking between accounts
                return@withTransaction
            }

            // 1. Reassign wallets (foreign key parent)
            writableDb.execSQL(
                "UPDATE wallets SET userId = ? WHERE userId = ?",
                arrayOf(toUserId, fromUserId)
            )

            // 2. Reassign transactions
            writableDb.execSQL(
                "UPDATE transactions SET userId = ? WHERE userId = ?",
                arrayOf(toUserId, fromUserId)
            )

            // 3. Reassign debts
            writableDb.execSQL(
                "UPDATE debts SET userId = ? WHERE userId = ?",
                arrayOf(toUserId, fromUserId)
            )

            // 4. Reassign installment plans
            writableDb.execSQL(
                "UPDATE installment_plans SET userId = ? WHERE userId = ?",
                arrayOf(toUserId, fromUserId)
            )

            // 5. Reassign budget limits with conflict resolution
            // If target user already has a budget limit for the same categoryOrTotalId, delete guest's duplicate
            // before updating to avoid UNIQUE constraint violation on index (userId, categoryOrTotalId)
            writableDb.execSQL("""
                DELETE FROM budget_limits 
                WHERE userId = ? 
                  AND categoryOrTotalId IN (
                      SELECT categoryOrTotalId FROM budget_limits WHERE userId = ?
                  )
            """.trimIndent(), arrayOf(fromUserId, toUserId))

            writableDb.execSQL(
                "UPDATE budget_limits SET userId = ? WHERE userId = ?",
                arrayOf(toUserId, fromUserId)
            )

            // 6. Reassign assets
            writableDb.execSQL(
                "UPDATE assets SET userId = ? WHERE userId = ?",
                arrayOf(toUserId, fromUserId)
            )

            // 7. Reassign split bill history
            writableDb.execSQL(
                "UPDATE split_bill_history SET userId = ? WHERE userId = ?",
                arrayOf(toUserId, fromUserId)
            )

            // 8. Reassign feedbacks
            writableDb.execSQL(
                "UPDATE feedbacks SET userId = ? WHERE userId = ?",
                arrayOf(toUserId, fromUserId)
            )

            // 9. Reassign custom categories (strictly non-system)
            writableDb.execSQL(
                "UPDATE categories SET userId = ? WHERE userId = ? AND isSystem = 0",
                arrayOf(toUserId, fromUserId)
            )

            // 10. Remove the temporary guest account now that its data has safely migrated
            writableDb.execSQL(
                "DELETE FROM users WHERE id = ? AND isGuest = 1",
                arrayOf(fromUserId)
            )
        }
    }

    /**
     * Authoritative atomic claim of a legacy wallet and its associated transactions.
     * Enforces immediate write transaction locking, preflight verification, and Parent-first child-second update order.
     */
    suspend fun claimLegacyWalletAtomically(walletId: Long, userId: Long): Result<Int> = coordinator.withWritePermit {
        withContext(Dispatchers.IO) {
        val db = database ?: return@withContext Result.failure(IllegalStateException("Database reference required for atomic claim"))
        try {
            db.withTransaction {
                // 1. Authoritative Re-read of Target Wallet
                val wallet = db.walletDao().getWalletById(walletId)
                    ?: throw IllegalStateException("Dompet tidak ditemukan.")
                if (wallet.userId != null) {
                    // Condition D: Wallet already claimed by another user or session
                    throw IllegalStateException("Dompet tidak ditemukan atau sudah diklaim oleh proses lain.")
                }

                // 2. Authoritative In-Transaction Ownership Preflight (Empty-Safe)
                val preflight = db.transactionDao().getTransactionOwnershipPreflight(walletId, userId)

                // 3. Condition C: Transactions belong to another user - strictly ABORT with zero mutations
                if (preflight.otherUserTx > 0) {
                    throw IllegalStateException("Dompet ini memiliki riwayat transaksi yang terhubung dengan akun lain. Klaim dibatalkan.")
                }

                // 4. Validate allowed legacy ownership distribution
                if (preflight.totalTx != preflight.nullOwnerTx + preflight.callerOwnerTx) {
                    throw IllegalStateException("Struktur kepemilikan transaksi warisan tidak konsisten. Klaim dibatalkan.")
                }

                // 5. Statement 1: Parent First (wallets) to satisfy immediate composite FK
                val walletUpdated = db.walletDao().claimLegacyWalletOwnership(walletId, userId)
                if (walletUpdated != 1) {
                    throw IllegalStateException("Gagal memperbarui kepemilikan dompet (baris terpengaruh: $walletUpdated).")
                }

                // 6. Statement 2: Child Second (transactions)
                val txUpdated = db.transactionDao().claimLegacyTransactionsForWallet(walletId, userId)
                if (txUpdated != preflight.nullOwnerTx) {
                    throw IllegalStateException("Jumlah transaksi yang diperbarui ($txUpdated) tidak sesuai dengan data terverifikasi (${preflight.nullOwnerTx}).")
                }

                // 7. Commit transaction and return count of claimed transactions
                Result.success(txUpdated)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
        }
    }

    suspend fun getLegacyUnownedWallets(): List<Wallet> = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext walletDao.getLegacyNullOwnerWallets()
        db.walletDao().getLegacyNullOwnerWallets()
    }

    suspend fun getUnassignedCurrencyWallets(userId: Long): List<Wallet> = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext walletDao.getUnassignedCurrencyWallets(userId)
        db.walletDao().getUnassignedCurrencyWallets(userId)
    }

    suspend fun assignWalletCurrency(walletId: Long, currencyCode: String, userId: Long): Result<Unit> = coordinator.withWritePermit {
        withContext(Dispatchers.IO) {
        try {
            val domainCurrency = DomainCurrency.fromCode(currencyCode)
            val db = database
            val existing = if (db != null) {
                db.walletDao().getWalletById(walletId, userId)
            } else {
                walletDao.getWalletById(walletId, userId)
            } ?: return@withContext Result.failure(IllegalArgumentException("Dompet #$walletId tidak ditemukan atau bukan milik pengguna #$userId."))

            if (existing.currencyCode != null) {
                return@withContext Result.failure(IllegalStateException("Mata uang dompet #${walletId} sudah ditetapkan (${existing.currencyCode}) dan bersifat permanen (DEC-PROD-01 Immutability)."))
            }

            val rows = if (db != null) {
                db.walletDao().assignWalletCurrency(walletId, domainCurrency.code, userId)
            } else {
                walletDao.assignWalletCurrency(walletId, domainCurrency.code, userId)
            }
            if (rows > 0) Result.success(Unit) else Result.failure(IllegalStateException("Gagal menetapkan mata uang untuk dompet $walletId"))
        } catch (e: Exception) {
            Result.failure(e)
        }
        }
    }

    suspend fun claimLegacyBudgetLimits(userId: Long): Int = coordinator.withWritePermit {
        withContext(Dispatchers.IO) {
            val db = database ?: return@withContext 0
            db.budgetLimitDao().claimLegacyBudgetLimits(userId)
        }
    }

    suspend fun claimLegacyDebts(userId: Long): Int = coordinator.withWritePermit {
        withContext(Dispatchers.IO) {
            val db = database ?: return@withContext 0
            db.debtDao().claimLegacyDebts(userId)
        }
    }

    suspend fun claimLegacyInstallments(userId: Long): Int = coordinator.withWritePermit {
        withContext(Dispatchers.IO) {
            val db = database ?: return@withContext 0
            db.installmentPlanDao().claimLegacyInstallments(userId)
        }
    }

    suspend fun claimLegacyAssets(userId: Long): Int = coordinator.withWritePermit {
        withContext(Dispatchers.IO) {
            val db = database ?: return@withContext 0
            db.assetDao().claimLegacyAssets(userId)
        }
    }

    suspend fun claimLegacySplitBills(userId: Long): Int = coordinator.withWritePermit {
        withContext(Dispatchers.IO) {
            val db = database ?: return@withContext 0
            db.splitBillHistoryDao().claimLegacySplitBills(userId)
        }
    }

    suspend fun restoreUserDataAuthoritative(
        targetUserId: Long,
        payload: com.example.data.model.BackupDataPayload
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext Result.failure(IllegalStateException("Database reference required for restore"))
        try {
            com.example.ui.viewmodel.FinanceViewModel.restoreUserDataInTransaction(db, payload, targetUserId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    // --- Coordinated DAO Mutation Delegations (P0-F Structural Encapsulation) ---

    suspend fun saveDebt(debt: DebtItem, userId: Long): Unit = coordinator.withWritePermit {
        val scoped = debt.copy(userId = userId)
        val debtDao = database?.debtDao() ?: throw IllegalStateException("Database instance required for debt operations")
        val updated = debtDao.updateDebt(scoped)
        if (updated == 0) {
            debtDao.insertDebt(scoped)
        }
    }

    suspend fun deleteDebt(debt: DebtItem, userId: Long): Int = coordinator.withWritePermit {
        val debtDao = database?.debtDao() ?: throw IllegalStateException("Database instance required for debt operations")
        debtDao.deleteDebt(debt, userId)
    }

    suspend fun clearAllDebts(userId: Long): Int = coordinator.withWritePermit {
        val debtDao = database?.debtDao() ?: throw IllegalStateException("Database instance required for debt operations")
        debtDao.clearAllDebts(userId)
    }

    /**
     * Resets all debt items and installment plans (along with child periods/allocations)
     * scoped strictly to [userId].
     * CRITICAL: Preserves general transactions (TransactionItem), wallet balances, and general expenses intact.
     */
    suspend fun resetAllDebtsAndPlans(userId: Long): Pair<Int, Int> = coordinator.withWritePermit {
        withContext(Dispatchers.IO) {
            val db = database ?: throw IllegalStateException("Database instance required for debt operations")
            db.withTransaction {
                db.installmentPaymentAllocationDao().clearAllocationsForUser(userId)
                db.installmentPaymentDao().clearPaymentsForUser(userId)
                db.installmentPeriodDao().clearPeriodsForUser(userId)
                val deletedPlans = db.installmentPlanDao().clearAllInstallmentPlans(userId)
                val deletedDebts = db.debtDao().clearAllDebts(userId)
                Pair(deletedDebts, deletedPlans)
            }
        }
    }

    suspend fun saveAsset(asset: AssetItem, userId: Long): Unit = coordinator.withWritePermit {
        val scoped = asset.copy(userId = userId)
        val assetDao = database?.assetDao() ?: throw IllegalStateException("Database instance required for asset operations")
        val updated = assetDao.updateAsset(scoped)
        if (updated == 0) {
            assetDao.insertAsset(scoped)
        }
    }

    suspend fun deleteAsset(asset: AssetItem, userId: Long): Int = coordinator.withWritePermit {
        val assetDao = database?.assetDao() ?: throw IllegalStateException("Database instance required for asset operations")
        assetDao.deleteAsset(asset, userId)
    }

    suspend fun clearAllAssets(userId: Long): Int = coordinator.withWritePermit {
        val assetDao = database?.assetDao() ?: throw IllegalStateException("Database instance required for asset operations")
        assetDao.clearAllAssets(userId)
    }

    suspend fun saveInstallmentPlan(
        plan: InstallmentPlanItem,
        userId: Long,
        explicitPeriods: List<InstallmentPeriod> = emptyList()
    ): Unit = coordinator.withWritePermit {
        withContext(Dispatchers.IO) {
            val db = database ?: throw IllegalStateException("Database instance required for installment plan operations")

            // REPOSITORY BOUNDARY VALIDATION: Validate before starting transaction or writes
            if (explicitPeriods.isNotEmpty()) {
                for (p in explicitPeriods) {
                    require(com.example.util.InstallmentDateHelper.isValidIsoDate(p.dueDate)) {
                        "Tanggal jatuh tempo periode #${p.periodNumber} '${p.dueDate}' tidak valid (wajib format ISO YYYY-MM-DD Gregorian)."
                    }
                }
            } else if (plan.totalTenor > 0 && plan.nextDueDate.isNotBlank()) {
                require(com.example.util.InstallmentDateHelper.isValidIsoDate(plan.nextDueDate)) {
                    "Tanggal jatuh tempo pertama rencana '${plan.nextDueDate}' tidak valid (wajib format ISO YYYY-MM-DD Gregorian)."
                }
            }

            db.withTransaction {
                val planDao = db.installmentPlanDao()
                val periodDao = db.installmentPeriodDao()

                planDao.insertInstallmentPlan(plan.copy(userId = userId))

                val periodsToInsert = if (explicitPeriods.isNotEmpty()) {
                    explicitPeriods.map { it.copy(planId = plan.id, userId = userId) }
                } else if (plan.totalTenor > 0) {
                    val minorUnits = plan.totalAmountMinorUnits?.let { it / plan.totalTenor }
                        ?: (plan.installmentAmount * 100).toLong()
                    val startDue = plan.nextDueDate.takeIf { com.example.util.InstallmentDateHelper.isValidIsoDate(it) }
                        ?: com.example.util.InstallmentDateHelper.formatIsoDate(java.util.Calendar.getInstance())

                    (1..plan.totalTenor).map { p ->
                        val pDue = if (p == 1) startDue else com.example.util.InstallmentDateHelper.calculateNextDueDate(
                            startDue,
                            anchorDay = plan.anchorDay,
                            monthsToAdd = p - 1
                        )
                        InstallmentPeriod(
                            id = java.util.UUID.randomUUID().toString(),
                            planId = plan.id,
                            currencyCode = plan.currencyCode,
                            userId = userId,
                            periodNumber = p,
                            amountDueMinorUnits = minorUnits,
                            amountPaidMinorUnits = 0L,
                            dueDate = pDue,
                            isPaid = p <= plan.paidCount
                        )
                    }
                } else {
                    emptyList()
                }

                if (periodsToInsert.isNotEmpty()) {
                    periodDao.insertPeriods(periodsToInsert)
                }
            }
        }
    }

    suspend fun deleteInstallmentPlan(planId: String, userId: Long): Int = coordinator.withWritePermit {
        withContext(Dispatchers.IO) {
            val db = database ?: throw IllegalStateException("Database instance required for installment plan operations")
            db.withTransaction {
                db.installmentPeriodDao().deletePeriodsByPlan(planId, userId)
                db.installmentPlanDao().deleteInstallmentPlan(planId, userId)
            }
        }
    }

    fun getPeriodsForPlan(planId: String, userId: Long): kotlinx.coroutines.flow.Flow<List<InstallmentPeriod>> {
        return database?.installmentPeriodDao()?.getPeriodsForPlan(planId, userId) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    suspend fun updateInstallmentPeriodDueDate(
        periodId: String,
        newDueDate: String,
        userId: Long
    ): Boolean = coordinator.withWritePermit {
        withContext(Dispatchers.IO) {
            val db = database ?: return@withContext false
            require(com.example.util.InstallmentDateHelper.isValidIsoDate(newDueDate)) {
                "Format tanggal jatuh tempo harus valid ISO YYYY-MM-DD"
            }
            db.withTransaction {
                val periodDao = db.installmentPeriodDao()
                val period = periodDao.getPeriodById(periodId, userId) ?: return@withTransaction false
                val updated = period.copy(dueDate = newDueDate)
                periodDao.updatePeriod(updated) > 0
            }
        }
    }

    suspend fun saveBatchPayLaterBills(
        debtsToSave: List<DebtItem>,
        plansWithPeriods: List<Pair<InstallmentPlanItem, List<InstallmentPeriod>>>,
        userId: Long
    ): Result<Int> = coordinator.withWritePermit {
        withContext(Dispatchers.IO) {
            val db = database ?: return@withContext Result.failure(IllegalStateException("Database instance required"))
            try {
                // REPOSITORY BOUNDARY VALIDATION: Validate all periods and plans before opening write transaction
                for ((plan, periods) in plansWithPeriods) {
                    if (periods.isNotEmpty()) {
                        for (p in periods) {
                            require(com.example.util.InstallmentDateHelper.isValidIsoDate(p.dueDate)) {
                                "Batch rejected: Tanggal jatuh tempo periode #${p.periodNumber} '${p.dueDate}' pada kontrak '${plan.name}' tidak valid (wajib format ISO YYYY-MM-DD)."
                            }
                        }
                    } else if (plan.totalTenor > 0 && plan.nextDueDate.isNotBlank()) {
                        require(com.example.util.InstallmentDateHelper.isValidIsoDate(plan.nextDueDate)) {
                            "Batch rejected: Tanggal jatuh tempo rencana '${plan.nextDueDate}' pada kontrak '${plan.name}' tidak valid (wajib format ISO YYYY-MM-DD)."
                        }
                    }
                }

                db.withTransaction {
                    val debtDao = db.debtDao()
                    val planDao = db.installmentPlanDao()
                    val periodDao = db.installmentPeriodDao()

                    for (debt in debtsToSave) {
                        debtDao.insertDebt(debt.copy(userId = userId))
                    }

                    for ((plan, periods) in plansWithPeriods) {
                        planDao.insertInstallmentPlan(plan.copy(userId = userId))
                        if (periods.isNotEmpty()) {
                            val scopedPeriods = periods.map { it.copy(planId = plan.id, userId = userId) }
                            periodDao.insertPeriods(scopedPeriods)
                        }
                    }
                }
                Result.success(debtsToSave.size + plansWithPeriods.size)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun saveBudgetLimit(item: BudgetLimitItem): Unit = coordinator.withWritePermit {
        val budgetDao = database?.budgetLimitDao() ?: throw IllegalStateException("Database instance required for budget limit operations")
        budgetDao.insertBudgetLimit(item)
    }

    suspend fun deleteBudgetLimit(item: BudgetLimitItem, userId: Long): Int = coordinator.withWritePermit {
        val budgetDao = database?.budgetLimitDao() ?: throw IllegalStateException("Database instance required for budget limit operations")
        budgetDao.deleteBudgetLimit(item, userId)
    }

    suspend fun clearAllBudgetLimits(userId: Long): Int = coordinator.withWritePermit {
        val budgetDao = database?.budgetLimitDao() ?: throw IllegalStateException("Database instance required for budget limit operations")
        budgetDao.clearAllBudgetLimits(userId)
    }

    suspend fun saveSplitBillHistory(item: SplitBillHistoryItem): Unit = coordinator.withWritePermit {
        val splitBillDao = database?.splitBillHistoryDao() ?: throw IllegalStateException("Database instance required for split bill operations")
        splitBillDao.insertSplitBillHistory(item)
    }

    suspend fun deleteSplitBillHistory(item: SplitBillHistoryItem, userId: Long): Int = coordinator.withWritePermit {
        val splitBillDao = database?.splitBillHistoryDao() ?: throw IllegalStateException("Database instance required for split bill operations")
        splitBillDao.deleteSplitBillHistory(item, userId)
    }

    suspend fun clearAllSplitBillHistory(userId: Long): Int = coordinator.withWritePermit {
        val splitBillDao = database?.splitBillHistoryDao() ?: throw IllegalStateException("Database instance required for split bill operations")
        splitBillDao.clearAllHistory(userId)
    }

    suspend fun saveRecurringSchedule(schedule: RecurringSchedule): Unit = coordinator.withWritePermit {
        val scheduleDao = recurringScheduleDao ?: database?.recurringScheduleDao()
            ?: throw IllegalStateException("Database instance required for recurring schedule operations")
        scheduleDao.insertSchedule(schedule)
    }

    fun getAllRecurringSchedules(userId: Long): Flow<List<RecurringSchedule>> {
        val scheduleDao = recurringScheduleDao ?: database?.recurringScheduleDao()
            ?: throw IllegalStateException("Database instance required for recurring schedule operations")
        return scheduleDao.getAllSchedules(userId)
    }
}
