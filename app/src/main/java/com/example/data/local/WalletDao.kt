package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {

    // --- User-Scoped Operations (DEF-03 Stage 2) ---

    @Query("SELECT * FROM wallets WHERE userId = :userId AND accountMode = :accountMode ORDER BY isDefault DESC, createdAt ASC")
    fun getWalletsByMode(userId: Long, accountMode: String): Flow<List<Wallet>>

    @Query("SELECT * FROM wallets WHERE userId = :userId ORDER BY createdAt ASC")
    fun getAllWallets(userId: Long): Flow<List<Wallet>>

    @Query("SELECT * FROM wallets WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getWalletById(id: Long, userId: Long): Wallet?

    @Query("UPDATE wallets SET balance = balance + :amount WHERE id = :walletId AND userId = :userId")
    suspend fun adjustBalance(walletId: Long, userId: Long, amount: Double): Int

    @Query("UPDATE wallets SET balance = :newBalance WHERE id = :walletId AND userId = :userId")
    suspend fun setBalance(walletId: Long, userId: Long, newBalance: Double): Int

    @Query("UPDATE wallets SET isDefault = 0 WHERE userId = :userId AND accountMode = :accountMode")
    suspend fun clearDefaultWallet(userId: Long, accountMode: String): Int

    @Query("UPDATE wallets SET isDefault = 1 WHERE id = :walletId AND userId = :userId")
    suspend fun setDefaultWallet(walletId: Long, userId: Long): Int

    @Query("DELETE FROM wallets WHERE id = :id AND userId = :userId")
    suspend fun deleteWalletById(id: Long, userId: Long): Int

    suspend fun deleteWallet(id: Long, userId: Long): Int =
        deleteWalletById(id, userId)

    suspend fun deleteWallet(wallet: Wallet, userId: Long): Int =
        deleteWalletById(wallet.id, userId)

    @Query("""
        UPDATE wallets
        SET name = :name,
            type = :type,
            accountNumber = :accountNumber,
            balance = :balance,
            colorHex = :colorHex,
            iconName = :iconName,
            accountMode = :accountMode,
            isDefault = :isDefault
        WHERE id = :id AND userId = :userId
    """)
    suspend fun updateWalletScoped(
        id: Long,
        userId: Long,
        name: String,
        type: WalletType,
        accountNumber: String,
        balance: Double,
        colorHex: String,
        iconName: String,
        accountMode: String,
        isDefault: Boolean
    ): Int

    suspend fun updateWallet(wallet: Wallet, userId: Long): Int =
        updateWalletScoped(
            id = wallet.id,
            userId = userId,
            name = wallet.name,
            type = wallet.type,
            accountNumber = wallet.accountNumber,
            balance = wallet.balance,
            colorHex = wallet.colorHex,
            iconName = wallet.iconName,
            accountMode = wallet.accountMode,
            isDefault = wallet.isDefault
        )

    @Query("SELECT COUNT(*) FROM wallets WHERE userId = :userId")
    suspend fun getWalletCount(userId: Long): Int

    @Query("DELETE FROM wallets WHERE userId = :userId")
    suspend fun clearAllWallets(userId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallet(wallet: Wallet): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallets(wallets: List<Wallet>)

    /**
     * Database lifecycle query for initial seeding on cold startup.
     */
    @Query("SELECT COUNT(*) FROM wallets")
    suspend fun getWalletCount(): Int
}
