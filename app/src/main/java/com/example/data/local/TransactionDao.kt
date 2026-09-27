package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.TransactionItem
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE accountMode = :accountMode ORDER BY timestamp DESC")
    fun getTransactionsByMode(accountMode: String): Flow<List<TransactionItem>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionItem>>

    @Query("SELECT * FROM transactions WHERE walletId = :walletId OR targetWalletId = :walletId ORDER BY timestamp DESC")
    fun getTransactionsByWallet(walletId: Long): Flow<List<TransactionItem>>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startTime AND :endTime AND accountMode = :accountMode ORDER BY timestamp DESC")
    fun getTransactionsBetween(startTime: Long, endTime: Long, accountMode: String): Flow<List<TransactionItem>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): TransactionItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionItem): Long

    @Update
    suspend fun updateTransaction(transaction: TransactionItem)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionItem)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int
}
