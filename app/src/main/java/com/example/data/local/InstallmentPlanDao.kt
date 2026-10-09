package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.InstallmentPlanItem
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallmentPlanDao {

    @Query("SELECT * FROM installment_plans WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllInstallmentPlans(userId: Long): Flow<List<InstallmentPlanItem>>

    @Query("SELECT * FROM installment_plans WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getInstallmentPlanById(id: String, userId: Long): InstallmentPlanItem?

    @Query("SELECT * FROM installment_plans WHERE status = 'AKTIF' AND userId = :userId ORDER BY nextDueDate ASC")
    fun getActiveInstallmentPlans(userId: Long): Flow<List<InstallmentPlanItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallmentPlan(plan: InstallmentPlanItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallmentPlans(plans: List<InstallmentPlanItem>)

    @Update
    suspend fun updateInstallmentPlan(plan: InstallmentPlanItem)

    @Query("DELETE FROM installment_plans WHERE id = :id AND userId = :userId")
    suspend fun deleteInstallmentPlan(id: String, userId: Long): Int

    @Query("DELETE FROM installment_plans WHERE userId = :userId")
    suspend fun clearAllInstallmentPlans(userId: Long): Int

    @Query("SELECT COUNT(*) FROM installment_plans WHERE userId = :userId")
    suspend fun getCount(userId: Long): Int
}
