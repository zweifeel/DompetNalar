package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AssetCategory
import com.example.data.model.AssetItem
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {

    // --- User-Scoped Operations (DEF-03 Stage 2) ---

    @Query("SELECT * FROM assets WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllAssets(userId: Long): Flow<List<AssetItem>>

    @Query("SELECT * FROM assets WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getAssetById(id: String, userId: Long): AssetItem?

    @Query("DELETE FROM assets WHERE id = :id AND userId = :userId")
    suspend fun deleteAssetById(id: String, userId: Long): Int

    suspend fun deleteAsset(id: String, userId: Long): Int =
        deleteAssetById(id, userId)

    suspend fun deleteAsset(asset: AssetItem, userId: Long): Int =
        deleteAssetById(asset.id, userId)

    @Query("""
        UPDATE assets
        SET name = :name,
            initialValue = :initialValue,
            purchaseYear = :purchaseYear,
            category = :category
        WHERE id = :id AND userId = :userId
    """)
    suspend fun updateAssetScoped(
        id: String,
        userId: Long,
        name: String,
        initialValue: Double,
        purchaseYear: Int,
        category: AssetCategory
    ): Int

    suspend fun updateAsset(asset: AssetItem, userId: Long): Int =
        updateAssetScoped(
            id = asset.id,
            userId = userId,
            name = asset.name,
            initialValue = asset.initialValue,
            purchaseYear = asset.purchaseYear,
            category = asset.category
        )

    @Query("DELETE FROM assets WHERE userId = :userId")
    suspend fun clearAllAssets(userId: Long): Int

    @Query("SELECT COUNT(*) FROM assets WHERE userId = :userId")
    suspend fun getAssetCount(userId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAsset(asset: AssetItem)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAssets(assets: List<AssetItem>)

    @androidx.room.Update
    suspend fun updateAsset(asset: AssetItem): Int

    @Query("SELECT * FROM assets WHERE userId IS NULL ORDER BY id ASC")
    suspend fun getLegacyNullOwnerAssets(): List<AssetItem>

    @Query("UPDATE assets SET userId = :userId WHERE id = :id AND userId IS NULL")
    suspend fun claimLegacyAsset(id: String, userId: Long): Int

    @Query("UPDATE assets SET userId = :userId WHERE userId IS NULL")
    suspend fun claimLegacyAssets(userId: Long): Int

}
