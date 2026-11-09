package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.InstallmentPayment
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallmentPaymentDao {

    @Query("SELECT * FROM installment_payments WHERE planId = :planId AND userId = :userId ORDER BY createdAt DESC")
    fun getPaymentsForPlan(planId: String, userId: Long): Flow<List<InstallmentPayment>>

    @Query("SELECT * FROM installment_payments WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getPaymentById(id: String, userId: Long): InstallmentPayment?

    @Query("SELECT * FROM installment_payments WHERE transactionId = :txId AND userId = :userId LIMIT 1")
    suspend fun getPaymentByTransactionId(txId: Long, userId: Long): InstallmentPayment?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(payment: InstallmentPayment): Long

    @Update
    suspend fun updatePayment(payment: InstallmentPayment): Int

    @Query("DELETE FROM installment_payments WHERE userId = :userId")
    suspend fun clearPaymentsForUser(userId: Long): Int
}
