package com.example.data.repository

import com.example.data.local.CategoryDao
import com.example.data.local.TransactionDao
import com.example.data.local.WalletDao
import com.example.data.model.AccountMode
import com.example.data.model.CategoryItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import kotlinx.coroutines.flow.Flow

/**
 * DEF-03 Stage 3: User-scoped Finance Repository.
 *
 * Every normal operation requires an explicit authenticated [userId].
 * Unscoped DAO methods are never invoked.
 */
class FinanceRepository(
    private val walletDao: WalletDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao
) {
    fun getWallets(userId: Long, accountMode: AccountMode): Flow<List<Wallet>> {
        return walletDao.getWalletsByMode(userId, accountMode.name)
    }

    fun getAllWallets(userId: Long): Flow<List<Wallet>> {
        return walletDao.getAllWallets(userId)
    }

    suspend fun getWalletById(userId: Long, id: Long): Wallet? {
        return walletDao.getWalletById(id, userId)
    }

    suspend fun insertWallet(wallet: Wallet): Long {
        requireNotNull(wallet.userId) { "Cannot insert wallet without an owning userId" }
        return walletDao.insertWallet(wallet)
    }

    suspend fun updateWallet(userId: Long, wallet: Wallet): Int {
        return walletDao.updateWallet(wallet, userId)
    }

    suspend fun deleteWallet(userId: Long, wallet: Wallet): Int {
        return walletDao.deleteWallet(wallet, userId)
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

    suspend fun insertTransaction(userId: Long, transaction: TransactionItem): Long {
        val scopedTransaction = transaction.copy(userId = userId)
        val id = transactionDao.insertTransaction(scopedTransaction)
        when (transaction.type) {
            TransactionType.INCOME -> {
                walletDao.adjustBalance(transaction.walletId, userId, transaction.amount)
            }
            TransactionType.EXPENSE -> {
                walletDao.adjustBalance(transaction.walletId, userId, -transaction.amount)
            }
            TransactionType.TRANSFER -> {
                walletDao.adjustBalance(transaction.walletId, userId, -transaction.amount)
                transaction.targetWalletId?.let { targetId ->
                    walletDao.adjustBalance(targetId, userId, transaction.amount)
                }
            }
        }
        return id
    }

    suspend fun deleteTransaction(userId: Long, transaction: TransactionItem): Int {
        // Rollback wallet balance with strict user ownership
        when (transaction.type) {
            TransactionType.INCOME -> {
                walletDao.adjustBalance(transaction.walletId, userId, -transaction.amount)
            }
            TransactionType.EXPENSE -> {
                walletDao.adjustBalance(transaction.walletId, userId, transaction.amount)
            }
            TransactionType.TRANSFER -> {
                walletDao.adjustBalance(transaction.walletId, userId, transaction.amount)
                transaction.targetWalletId?.let { targetId ->
                    walletDao.adjustBalance(targetId, userId, -transaction.amount)
                }
            }
        }
        return transactionDao.deleteTransaction(transaction, userId)
    }

    suspend fun updateTransaction(userId: Long, oldTransaction: TransactionItem, newTransaction: TransactionItem): Int {
        // First rollback old balances with user ownership
        when (oldTransaction.type) {
            TransactionType.INCOME -> walletDao.adjustBalance(oldTransaction.walletId, userId, -oldTransaction.amount)
            TransactionType.EXPENSE -> walletDao.adjustBalance(oldTransaction.walletId, userId, oldTransaction.amount)
            TransactionType.TRANSFER -> {
                walletDao.adjustBalance(oldTransaction.walletId, userId, oldTransaction.amount)
                oldTransaction.targetWalletId?.let { walletDao.adjustBalance(it, userId, -oldTransaction.amount) }
            }
        }
        // Apply new balances with user ownership
        when (newTransaction.type) {
            TransactionType.INCOME -> walletDao.adjustBalance(newTransaction.walletId, userId, newTransaction.amount)
            TransactionType.EXPENSE -> walletDao.adjustBalance(newTransaction.walletId, userId, -newTransaction.amount)
            TransactionType.TRANSFER -> {
                walletDao.adjustBalance(newTransaction.walletId, userId, -newTransaction.amount)
                newTransaction.targetWalletId?.let { walletDao.adjustBalance(it, userId, newTransaction.amount) }
            }
        }
        return transactionDao.updateTransaction(newTransaction.copy(userId = userId), userId)
    }

    fun getAllCategories(userId: Long): Flow<List<CategoryItem>> {
        return categoryDao.getAllCategories(userId)
    }

    fun getCategoriesByType(userId: Long, type: TransactionType): Flow<List<CategoryItem>> {
        return categoryDao.getCategoriesByType(userId, type)
    }

    suspend fun insertCategory(category: CategoryItem): Long {
        return categoryDao.insertCategory(category)
    }

    suspend fun deleteCategory(userId: Long, category: CategoryItem): Int {
        return categoryDao.deleteCategory(category, userId)
    }
}
