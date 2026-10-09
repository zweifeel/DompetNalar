package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.CategoryItem
import com.example.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    // --- User-Scoped Operations (DEF-03 Stage 2) ---

    @Query("SELECT * FROM categories WHERE isSystem = 1 ORDER BY name ASC")
    fun getSystemCategories(): Flow<List<CategoryItem>>

    @Query("SELECT * FROM categories WHERE isSystem = 1 OR userId = :userId ORDER BY name ASC")
    fun getAllCategories(userId: Long): Flow<List<CategoryItem>>

    @Query("SELECT * FROM categories WHERE (isSystem = 1 OR userId = :userId) AND type = :type ORDER BY name ASC")
    fun getCategoriesByType(userId: Long, type: TransactionType): Flow<List<CategoryItem>>

    @Query("SELECT * FROM categories WHERE id = :id AND (isSystem = 1 OR userId = :userId) LIMIT 1")
    suspend fun getCategoryById(id: Long, userId: Long): CategoryItem?

    @Query("DELETE FROM categories WHERE id = :id AND userId = :userId AND isSystem = 0")
    suspend fun deleteCategoryById(id: Long, userId: Long): Int

    suspend fun deleteCategory(id: Long, userId: Long): Int =
        deleteCategoryById(id, userId)

    suspend fun deleteCategory(category: CategoryItem, userId: Long): Int =
        deleteCategoryById(category.id, userId)

    @Query("""
        UPDATE categories
        SET name = :name,
            type = :type,
            iconName = :iconName,
            colorHex = :colorHex
        WHERE id = :id AND userId = :userId AND isSystem = 0
    """)
    suspend fun updateCustomCategory(
        id: Long,
        userId: Long,
        name: String,
        type: TransactionType,
        iconName: String,
        colorHex: String
    ): Int

    suspend fun updateCategory(category: CategoryItem, userId: Long): Int =
        updateCustomCategory(
            id = category.id,
            userId = userId,
            name = category.name,
            type = category.type,
            iconName = category.iconName,
            colorHex = category.colorHex
        )

    @Query("SELECT COUNT(*) FROM categories WHERE userId = :userId")
    suspend fun getCustomCategoryCount(userId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryItem>)

    /**
     * Database lifecycle query for initial seeding on cold startup.
     */
    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int
}
