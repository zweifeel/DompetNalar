package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.InstallmentPaymentAllocation
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallmentPaymentAllocationDao {

    @Query("SELECT * FROM installment_payment_allocations WHERE paymentId = :paymentId AND userId = :userId")
    fun getAllocationsForPayment(paymentId: String, userId: Long): Flow<List<InstallmentPaymentAllocation>>

    @Query("SELECT * FROM installment_payment_allocations WHERE periodId = :periodId AND userId = :userId")
    fun getAllocationsForPeriod(periodId: String, userId: Long): Flow<List<InstallmentPaymentAllocation>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllocation(allocation: InstallmentPaymentAllocation): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllocations(allocations: List<InstallmentPaymentAllocation>)

    @Update
    suspend fun updateAllocation(allocation: InstallmentPaymentAllocation): Int

    @Query("DELETE FROM installment_payment_allocations WHERE userId = :userId")
    suspend fun clearAllocationsForUser(userId: Long): Int
}
