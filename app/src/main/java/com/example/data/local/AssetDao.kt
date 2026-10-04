package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AssetItem
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets ORDER BY createdAt DESC")
    fun getAllAssets(): Flow<List<AssetItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: AssetItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssets(assets: List<AssetItem>)

    @Update
    suspend fun updateAsset(asset: AssetItem)

    @Delete
    suspend fun deleteAsset(asset: AssetItem)

    @Query("DELETE FROM assets")
    suspend fun clearAllAssets()

    @Query("SELECT COUNT(*) FROM assets")
    suspend fun getAssetCount(): Int
}
