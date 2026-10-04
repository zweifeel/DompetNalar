package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DebtItem
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY createdAt DESC")
    fun getAllDebts(): Flow<List<DebtItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: DebtItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebts(debts: List<DebtItem>)

    @Update
    suspend fun updateDebt(debt: DebtItem)

    @Delete
    suspend fun deleteDebt(debt: DebtItem)

    @Query("DELETE FROM debts")
    suspend fun clearAllDebts()

    @Query("SELECT COUNT(*) FROM debts")
    suspend fun getDebtCount(): Int
}
