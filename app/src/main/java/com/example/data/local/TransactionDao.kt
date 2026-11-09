package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    // --- User-Scoped Operations (DEF-03 Stage 2) ---

    @Query("SELECT * FROM transactions WHERE userId = :userId AND accountMode = :accountMode ORDER BY timestamp DESC")
    fun getTransactionsByMode(userId: Long, accountMode: String): Flow<List<TransactionItem>>

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllTransactions(userId: Long): Flow<List<TransactionItem>>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND (walletId = :walletId OR targetWalletId = :walletId) ORDER BY timestamp DESC")
    fun getTransactionsByWallet(userId: Long, walletId: Long): Flow<List<TransactionItem>>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND accountMode = :accountMode AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getTransactionsBetween(userId: Long, startTime: Long, endTime: Long, accountMode: String): Flow<List<TransactionItem>>

    @Query("SELECT * FROM transactions WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getTransactionById(id: Long, userId: Long): TransactionItem?

    @Query("DELETE FROM transactions WHERE id = :id AND userId = :userId")
    suspend fun deleteTransactionById(id: Long, userId: Long): Int

    suspend fun deleteTransaction(id: Long, userId: Long): Int =
        deleteTransactionById(id, userId)

    suspend fun deleteTransaction(transaction: TransactionItem, userId: Long): Int =
        deleteTransactionById(transaction.id, userId)

    @Query("""
        UPDATE transactions
        SET title = :title,
            amount = :amount,
            type = :type,
            category = :category,
            categoryIcon = :categoryIcon,
            walletId = :walletId,
            walletName = :walletName,
            targetWalletId = :targetWalletId,
            targetWalletName = :targetWalletName,
            timestamp = :timestamp,
            note = :note,
            tags = :tags,
            accountMode = :accountMode
        WHERE id = :id AND userId = :userId
    """)
    suspend fun updateTransactionScoped(
        id: Long,
        userId: Long,
        title: String,
        amount: Double,
        type: TransactionType,
        category: String,
        categoryIcon: String,
        walletId: Long,
        walletName: String,
        targetWalletId: Long?,
        targetWalletName: String?,
        timestamp: Long,
        note: String,
        tags: String,
        accountMode: String
    ): Int

    suspend fun updateTransaction(transaction: TransactionItem, userId: Long): Int =
        updateTransactionScoped(
            id = transaction.id,
            userId = userId,
            title = transaction.title,
            amount = transaction.amount,
            type = transaction.type,
            category = transaction.category,
            categoryIcon = transaction.categoryIcon,
            walletId = transaction.walletId,
            walletName = transaction.walletName,
            targetWalletId = transaction.targetWalletId,
            targetWalletName = transaction.targetWalletName,
            timestamp = transaction.timestamp,
            note = transaction.note,
            tags = transaction.tags,
            accountMode = transaction.accountMode
        )

    @Query("SELECT * FROM transactions WHERE idempotencyKey = :idempotencyKey LIMIT 1")
    suspend fun getTransactionByIdempotencyKey(idempotencyKey: String): TransactionItem?

    @Query("SELECT COUNT(*) FROM transactions WHERE userId = :userId")
    suspend fun getTransactionCount(userId: Long): Int

    @Query("DELETE FROM transactions WHERE userId = :userId")
    suspend fun clearAllTransactions(userId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: TransactionItem): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransactions(transactions: List<TransactionItem>)

    @Query("""
        SELECT 
            COUNT(*) as totalTx,
            COALESCE(SUM(CASE WHEN userId IS NULL THEN 1 ELSE 0 END), 0) as nullOwnerTx,
            COALESCE(SUM(CASE WHEN userId = :currentUserId THEN 1 ELSE 0 END), 0) as callerOwnerTx,
            COALESCE(SUM(CASE WHEN userId IS NOT NULL AND userId != :currentUserId THEN 1 ELSE 0 END), 0) as otherUserTx
        FROM transactions
        WHERE walletId = :walletId
    """)
    suspend fun getTransactionOwnershipPreflight(walletId: Long, currentUserId: Long): TransactionPreflightSummary

    @Query("UPDATE transactions SET userId = :userId WHERE walletId = :walletId AND userId IS NULL")
    suspend fun claimLegacyTransactionsForWallet(walletId: Long, userId: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE userId = :userId AND (walletId = :walletId OR targetWalletId = :walletId)")
    suspend fun getTransactionCountByWallet(walletId: Long, userId: Long): Int
}

data class TransactionPreflightSummary(
    val totalTx: Int,
    val nullOwnerTx: Int,
    val callerOwnerTx: Int,
    val otherUserTx: Int
)
