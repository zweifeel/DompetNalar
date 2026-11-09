package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.RecurringSchedule
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringScheduleDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSchedule(schedule: RecurringSchedule)

    @Update
    suspend fun updateSchedule(schedule: RecurringSchedule): Int

    @Query("SELECT * FROM recurring_schedules WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getScheduleByIdAndUser(id: String, userId: Long): RecurringSchedule?

    @Query("SELECT * FROM recurring_schedules WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllSchedules(userId: Long): Flow<List<RecurringSchedule>>

    @Query("SELECT * FROM recurring_schedules WHERE userId = :userId AND isEnabled = 1 ORDER BY createdAt DESC")
    fun getActiveSchedules(userId: Long): Flow<List<RecurringSchedule>>

    @Query("UPDATE recurring_schedules SET lastExecutedCycle = :cycleKey WHERE id = :id")
    suspend fun updateLastExecutedCycle(id: String, cycleKey: String): Int

    @Query("UPDATE recurring_schedules SET isEnabled = :isEnabled WHERE id = :id AND userId = :userId")
    suspend fun setScheduleEnabled(id: String, userId: Long, isEnabled: Boolean): Int

    @Query("DELETE FROM recurring_schedules WHERE id = :id AND userId = :userId")
    suspend fun deleteSchedule(id: String, userId: Long): Int

    @Query("UPDATE recurring_schedules SET isEnabled = 0 WHERE walletId = :walletId AND userId = :userId AND isEnabled = 1")
    suspend fun disableSchedulesByWallet(walletId: Long, userId: Long): Int

    @Query("SELECT COUNT(*) FROM recurring_schedules WHERE walletId = :walletId AND userId = :userId")
    suspend fun getScheduleCountByWallet(walletId: Long, userId: Long): Int

    @Query("SELECT * FROM recurring_schedules WHERE walletId = :walletId AND userId = :userId AND isEnabled = 1")
    suspend fun getActiveSchedulesByWallet(walletId: Long, userId: Long): List<RecurringSchedule>

    @Query("DELETE FROM recurring_schedules WHERE userId = :userId")
    suspend fun clearAllSchedules(userId: Long): Int
}
