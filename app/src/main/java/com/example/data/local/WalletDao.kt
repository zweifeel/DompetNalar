package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Wallet
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallets WHERE accountMode = :accountMode ORDER BY isDefault DESC, createdAt ASC")
    fun getWalletsByMode(accountMode: String): Flow<List<Wallet>>

    @Query("SELECT * FROM wallets ORDER BY createdAt ASC")
    fun getAllWallets(): Flow<List<Wallet>>

    @Query("SELECT * FROM wallets WHERE id = :id LIMIT 1")
    suspend fun getWalletById(id: Long): Wallet?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallet(wallet: Wallet): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallets(wallets: List<Wallet>)

    @Update
    suspend fun updateWallet(wallet: Wallet)

    @Delete
    suspend fun deleteWallet(wallet: Wallet)

    @Query("UPDATE wallets SET balance = balance + :amount WHERE id = :walletId")
    suspend fun adjustBalance(walletId: Long, amount: Double)

    @Query("UPDATE wallets SET balance = :newBalance WHERE id = :walletId")
    suspend fun setBalance(walletId: Long, newBalance: Double)

    @Query("SELECT COUNT(*) FROM wallets")
    suspend fun getWalletCount(): Int
}
