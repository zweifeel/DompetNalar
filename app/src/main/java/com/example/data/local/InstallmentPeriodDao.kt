package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.InstallmentPeriod
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallmentPeriodDao {

    @Query("SELECT * FROM installment_periods WHERE planId = :planId AND userId = :userId ORDER BY periodNumber ASC")
    fun getPeriodsForPlan(planId: String, userId: Long): Flow<List<InstallmentPeriod>>

    @Query("SELECT * FROM installment_periods WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getPeriodById(id: String, userId: Long): InstallmentPeriod?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPeriod(period: InstallmentPeriod): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPeriods(periods: List<InstallmentPeriod>)

    @Update
    suspend fun updatePeriod(period: InstallmentPeriod): Int

    @Query("DELETE FROM installment_periods WHERE planId = :planId AND userId = :userId")
    suspend fun deletePeriodsByPlan(planId: String, userId: Long): Int

    @Query("DELETE FROM installment_periods WHERE userId = :userId")
    suspend fun clearPeriodsForUser(userId: Long): Int
}
