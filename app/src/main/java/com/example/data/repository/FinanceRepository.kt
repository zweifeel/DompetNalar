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

class FinanceRepository(
    private val walletDao: WalletDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao
) {
    fun getWallets(accountMode: AccountMode): Flow<List<Wallet>> {
        return walletDao.getWalletsByMode(accountMode.name)
    }

    fun getAllWallets(): Flow<List<Wallet>> {
        return walletDao.getAllWallets()
    }

    suspend fun getWalletById(id: Long): Wallet? {
        return walletDao.getWalletById(id)
    }

    suspend fun insertWallet(wallet: Wallet): Long {
        return walletDao.insertWallet(wallet)
    }

    suspend fun updateWallet(wallet: Wallet) {
        walletDao.updateWallet(wallet)
    }

    suspend fun deleteWallet(wallet: Wallet) {
        walletDao.deleteWallet(wallet)
    }

    fun getTransactions(accountMode: AccountMode): Flow<List<TransactionItem>> {
        return transactionDao.getTransactionsByMode(accountMode.name)
    }

    fun getTransactionsByWallet(walletId: Long): Flow<List<TransactionItem>> {
        return transactionDao.getTransactionsByWallet(walletId)
    }

    fun getTransactionsBetween(startTime: Long, endTime: Long, accountMode: AccountMode): Flow<List<TransactionItem>> {
        return transactionDao.getTransactionsBetween(startTime, endTime, accountMode.name)
    }

    suspend fun insertTransaction(transaction: TransactionItem): Long {
        val id = transactionDao.insertTransaction(transaction)
        when (transaction.type) {
            TransactionType.INCOME -> {
                walletDao.adjustBalance(transaction.walletId, transaction.amount)
            }
            TransactionType.EXPENSE -> {
                walletDao.adjustBalance(transaction.walletId, -transaction.amount)
            }
            TransactionType.TRANSFER -> {
                walletDao.adjustBalance(transaction.walletId, -transaction.amount)
                transaction.targetWalletId?.let { targetId ->
                    walletDao.adjustBalance(targetId, transaction.amount)
                }
            }
        }
        return id
    }

    suspend fun deleteTransaction(transaction: TransactionItem) {
        // Rollback wallet balance
        when (transaction.type) {
            TransactionType.INCOME -> {
                walletDao.adjustBalance(transaction.walletId, -transaction.amount)
            }
            TransactionType.EXPENSE -> {
                walletDao.adjustBalance(transaction.walletId, transaction.amount)
            }
            TransactionType.TRANSFER -> {
                walletDao.adjustBalance(transaction.walletId, transaction.amount)
                transaction.targetWalletId?.let { targetId ->
                    walletDao.adjustBalance(targetId, -transaction.amount)
                }
            }
        }
        transactionDao.deleteTransaction(transaction)
    }

    suspend fun updateTransaction(oldTransaction: TransactionItem, newTransaction: TransactionItem) {
        // First rollback old
        when (oldTransaction.type) {
            TransactionType.INCOME -> walletDao.adjustBalance(oldTransaction.walletId, -oldTransaction.amount)
            TransactionType.EXPENSE -> walletDao.adjustBalance(oldTransaction.walletId, oldTransaction.amount)
            TransactionType.TRANSFER -> {
                walletDao.adjustBalance(oldTransaction.walletId, oldTransaction.amount)
                oldTransaction.targetWalletId?.let { walletDao.adjustBalance(it, -oldTransaction.amount) }
            }
        }
        // Apply new
        when (newTransaction.type) {
            TransactionType.INCOME -> walletDao.adjustBalance(newTransaction.walletId, newTransaction.amount)
            TransactionType.EXPENSE -> walletDao.adjustBalance(newTransaction.walletId, -newTransaction.amount)
            TransactionType.TRANSFER -> {
                walletDao.adjustBalance(newTransaction.walletId, -newTransaction.amount)
                newTransaction.targetWalletId?.let { walletDao.adjustBalance(it, newTransaction.amount) }
            }
        }
        transactionDao.updateTransaction(newTransaction)
    }

    fun getAllCategories(): Flow<List<CategoryItem>> {
        return categoryDao.getAllCategories()
    }

    fun getCategoriesByType(type: TransactionType): Flow<List<CategoryItem>> {
        return categoryDao.getCategoriesByType(type)
    }

    suspend fun insertCategory(category: CategoryItem): Long {
        return categoryDao.insertCategory(category)
    }

    suspend fun deleteCategory(category: CategoryItem) {
        categoryDao.deleteCategory(category)
    }
}
