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

    @Query("""
        UPDATE wallets 
        SET balance = balance + :amount,
            balanceMinorUnits = balanceMinorUnits + CAST(
                ROUND(
                    :amount * CASE 
                        WHEN currencyCode IN ('JPY', 'KRW') THEN 1.0 
                        WHEN currencyCode = 'BHD' THEN 1000.0 
                        ELSE 100.0 
                    END
                ) AS INTEGER
            )
        WHERE id = :walletId AND userId = :userId
    """)
    suspend fun adjustBalance(walletId: Long, userId: Long, amount: Double): Int

    @Query("""
        UPDATE wallets 
        SET balance = :newBalance,
            balanceMinorUnits = CAST(
                ROUND(
                    :newBalance * CASE 
                        WHEN currencyCode IN ('JPY', 'KRW') THEN 1.0 
                        WHEN currencyCode = 'BHD' THEN 1000.0 
                        ELSE 100.0 
                    END
                ) AS INTEGER
            )
        WHERE id = :walletId AND userId = :userId
    """)
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
            balanceMinorUnits = CAST(
                ROUND(
                    :balance * CASE 
                        WHEN currencyCode IN ('JPY', 'KRW') THEN 1.0 
                        WHEN currencyCode = 'BHD' THEN 1000.0 
                        ELSE 100.0 
                    END
                ) AS INTEGER
            ),
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

    @Query("""
        UPDATE wallets 
        SET name = :name,
            type = :type,
            accountNumber = :accountNumber,
            colorHex = :colorHex,
            iconName = :iconName,
            accountMode = :accountMode,
            isDefault = :isDefault
        WHERE id = :id AND userId = :userId
    """)
    suspend fun updateWalletMetadata(
        id: Long,
        userId: Long,
        name: String,
        type: WalletType,
        accountNumber: String,
        colorHex: String,
        iconName: String,
        accountMode: String,
        isDefault: Boolean
    ): Int

    @Query("SELECT COUNT(*) FROM wallets WHERE userId = :userId")
    suspend fun getWalletCount(userId: Long): Int

    @Query("DELETE FROM wallets WHERE userId = :userId")
    suspend fun clearAllWallets(userId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWallet(wallet: Wallet): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWallets(wallets: List<Wallet>)

    @Update
    suspend fun updateWallet(wallet: Wallet): Int

    @Query("SELECT * FROM wallets WHERE id = :walletId LIMIT 1")
    suspend fun getWalletById(walletId: Long): Wallet?

    @Query("SELECT * FROM wallets WHERE userId IS NULL ORDER BY id ASC")
    suspend fun getLegacyNullOwnerWallets(): List<Wallet>

    @Query("SELECT COUNT(*) FROM wallets WHERE userId IS NULL")
    suspend fun countLegacyNullOwnerWallets(): Int

    @Query("UPDATE wallets SET userId = :userId WHERE id = :walletId AND userId IS NULL")
    suspend fun claimLegacyWalletOwnership(walletId: Long, userId: Long): Int

    @Query("SELECT * FROM wallets WHERE userId = :userId AND currencyCode IS NULL ORDER BY id ASC")
    suspend fun getUnassignedCurrencyWallets(userId: Long): List<Wallet>

    @Query("SELECT COUNT(*) FROM wallets WHERE userId = :userId AND currencyCode IS NULL")
    suspend fun countUnassignedCurrencyWallets(userId: Long): Int

    @Query("""
        UPDATE wallets 
        SET currencyCode = :currencyCode,
            balanceMinorUnits = CAST(
                ROUND(
                    balance * CASE 
                        WHEN :currencyCode IN ('JPY', 'KRW') THEN 1.0 
                        WHEN :currencyCode = 'BHD' THEN 1000.0 
                        ELSE 100.0 
                    END
                ) AS INTEGER
            )
        WHERE id = :walletId AND userId = :userId
    """)
    suspend fun assignWalletCurrency(walletId: Long, currencyCode: String, userId: Long): Int

    @Query("UPDATE wallets SET isArchived = :isArchived WHERE id = :walletId AND userId = :userId")
    suspend fun setWalletArchived(walletId: Long, userId: Long, isArchived: Boolean): Int

    /**
     * Database lifecycle query for initial seeding on cold startup.
     */
    @Query("SELECT COUNT(*) FROM wallets")
    suspend fun getWalletCount(): Int
}
