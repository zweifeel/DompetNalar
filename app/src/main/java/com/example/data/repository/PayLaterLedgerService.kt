package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.DatabaseWriteCoordinator
import com.example.data.local.FinSmartDatabase
import com.example.data.model.InstallmentPayment
import com.example.data.model.InstallmentPaymentAllocation
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * P0 PayLater Domain & Ledger Engine.
 *
 * Enforces:
 * - Multi-period sequential coverage in periodNumber ASC order
 * - Fail-closed cross-currency and cross-user rejection
 * - Double-entry payment voiding with matching reversal transaction
 * - Ledger integrity, unallocated remainder accounting, and period balance synchronization
 */
class PayLaterLedgerService(
    private val database: FinSmartDatabase,
    private val coordinator: DatabaseWriteCoordinator = database.writeCoordinator
) {
    suspend fun recordPayment(
        userId: Long,
        planId: String,
        walletId: Long,
        amountMinorUnits: Long,
        notes: String? = null
    ): Result<InstallmentPayment> = coordinator.withWritePermit {
        withContext(Dispatchers.IO) {
            require(userId > 0L) { "P0-C: Active authenticated userId is required" }
            require(amountMinorUnits > 0L) { "P0-C: Payment amount must be strictly positive" }

            try {
                val payment = database.withTransaction {
                    val planDao = database.installmentPlanDao()
                    val walletDao = database.walletDao()
                    val txDao = database.transactionDao()
                    val paymentDao = database.installmentPaymentDao()
                    val allocDao = database.installmentPaymentAllocationDao()

                    // 1. Validate Plan ownership
                    val plan = planDao.getInstallmentPlanById(planId, userId)
                        ?: throw IllegalArgumentException("P0-C: Installment plan '$planId' not found for user #$userId")

                    // 2. Validate Wallet ownership
                    val wallet = walletDao.getWalletById(walletId, userId)
                        ?: throw IllegalArgumentException("P0-C: Wallet #$walletId not found for user #$userId")

                    // 3. Cross-Currency Invariant Enforcement
                    val planCurrency = plan.currencyCode
                    val walletCurrency = wallet.currencyCode
                    if (planCurrency != walletCurrency) {
                        throw IllegalStateException("P0-B/C: Cross-currency rejection - wallet currency '$walletCurrency' does not match plan currency '$planCurrency'")
                    }

                    // 4. Wallet Balance Verification & Debit
                    val walletBalanceMinor = wallet.balanceMinorUnits
                    if (walletBalanceMinor < amountMinorUnits) {
                        throw IllegalStateException("P0-C: Insufficient wallet balance ($walletBalanceMinor minor units) for payment ($amountMinorUnits minor units)")
                    }

                    val doubleAmount = com.example.util.CurrencyRegistry.canonicalMinorUnitsToDisplay(amountMinorUnits, planCurrency)
                    val debitRows = walletDao.adjustBalance(walletId, userId, -doubleAmount)
                    check(debitRows == 1) { "P0-C: Failed to debit wallet balance" }

                    // 5. Create Parent Transaction Record
                    val paymentTx = TransactionItem(
                        title = "Pembayaran Cicilan: ${plan.name}",
                        amount = doubleAmount,
                        amountMinorUnits = amountMinorUnits,
                        currencyCode = planCurrency,
                        type = TransactionType.EXPENSE,
                        category = "Cicilan & Hutang",
                        categoryIcon = "credit_card",
                        walletId = walletId,
                        walletName = wallet.name,
                        timestamp = System.currentTimeMillis(),
                        note = notes ?: "Pembayaran cicilan rencana ${plan.name}",
                        tags = "installment_plan_id:$planId",
                        userId = userId,
                        isReversal = false,
                        reversedTransactionId = null
                    )
                    val txId = txDao.insertTransaction(paymentTx)
                    check(txId > 0L) { "P0-C: Failed to insert payment transaction" }

                    // 6. Create Installment Payment Record
                    val paymentId = UUID.randomUUID().toString()
                    val paymentRecord = InstallmentPayment(
                        id = paymentId,
                        planId = planId,
                        transactionId = txId,
                        currencyCode = planCurrency,
                        userId = userId,
                        totalMinorUnits = amountMinorUnits,
                        unallocatedMinorUnits = 0L,
                        isVoid = false,
                        reversalTransactionId = null,
                        createdAt = System.currentTimeMillis()
                    )
                    paymentDao.insertPayment(paymentRecord)

                    // 7. Multi-Period Sequential Coverage Allocation
                    val cursor = database.openHelper.writableDatabase.query("""
                        SELECT id, periodNumber, amountDueMinorUnits, amountPaidMinorUnits
                        FROM installment_periods
                        WHERE planId = '$planId' AND userId = $userId AND isPaid = 0
                        ORDER BY periodNumber ASC
                    """)

                    var remainingToAllocate = amountMinorUnits
                    val allocationsToInsert = mutableListOf<InstallmentPaymentAllocation>()

                    try {
                        while (cursor.moveToNext() && remainingToAllocate > 0L) {
                            val periodId = cursor.getString(0)
                            val due = cursor.getLong(2)
                            val paid = cursor.getLong(3)
                            val unpaid = due - paid
                            if (unpaid <= 0L) continue

                            val allocAmount = minOf(remainingToAllocate, unpaid)
                            val alloc = InstallmentPaymentAllocation(
                                id = UUID.randomUUID().toString(),
                                paymentId = paymentId,
                                periodId = periodId,
                                currencyCode = planCurrency,
                                userId = userId,
                                allocatedMinorUnits = allocAmount,
                                isVoid = false,
                                allocatedAt = System.currentTimeMillis()
                            )
                            allocationsToInsert.add(alloc)
                            remainingToAllocate -= allocAmount
                        }
                    } finally {
                        cursor.close()
                    }

                    if (allocationsToInsert.isNotEmpty()) {
                        allocDao.insertAllocations(allocationsToInsert)
                    }

                    // 8. Update unallocated remainder if payment exceeds remaining periods
                    val finalPayment = if (remainingToAllocate > 0L) {
                        val updatedPayment = paymentRecord.copy(unallocatedMinorUnits = remainingToAllocate)
                        paymentDao.updatePayment(updatedPayment)
                        updatedPayment
                    } else {
                        paymentRecord
                    }

                    finalPayment
                }
                Result.success(payment)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun voidPayment(
        userId: Long,
        paymentId: String,
        reason: String? = null
    ): Result<Unit> = coordinator.withWritePermit {
        withContext(Dispatchers.IO) {
            require(userId > 0L) { "P0-C: Active authenticated userId is required" }

            try {
                database.withTransaction {
                    val paymentDao = database.installmentPaymentDao()
                    val txDao = database.transactionDao()
                    val walletDao = database.walletDao()

                    val payment = paymentDao.getPaymentById(paymentId, userId)
                        ?: throw IllegalArgumentException("P0-C: Payment '$paymentId' not found for user #$userId")

                    if (payment.isVoid) {
                        throw IllegalStateException("P0-C: Payment '$paymentId' is already voided")
                    }

                    val origTx = txDao.getTransactionById(payment.transactionId, userId)
                        ?: throw IllegalStateException("P0-C: Original transaction #${payment.transactionId} not found")

                    // 1. Insert authoritative reversal transaction (Counter-entry typing: EXPENSE -> INCOME refund)
                    val reversalType = when (origTx.type) {
                        TransactionType.EXPENSE -> TransactionType.INCOME
                        TransactionType.INCOME -> TransactionType.EXPENSE
                        TransactionType.TRANSFER -> TransactionType.TRANSFER
                    }
                    val reversalTx = TransactionItem(
                        title = "Reversal: ${origTx.title}",
                        amount = origTx.amount,
                        amountMinorUnits = origTx.amountMinorUnits,
                        currencyCode = origTx.currencyCode,
                        type = reversalType,
                        category = origTx.category,
                        categoryIcon = origTx.categoryIcon,
                        walletId = origTx.walletId,
                        walletName = origTx.walletName,
                        timestamp = System.currentTimeMillis(),
                        note = reason ?: "Pembatalan pembayaran cicilan #$paymentId",
                        tags = "reversal:${origTx.id},plan_payment:$paymentId",
                        userId = userId,
                        isReversal = true,
                        reversedTransactionId = origTx.id
                    )
                    val reversalTxId = txDao.insertTransaction(reversalTx)
                    check(reversalTxId > 0L) { "P0-B: Failed to record reversal transaction" }

                    // 2. Void payment record with reversalTransactionId
                    val voidedPayment = payment.copy(
                        isVoid = true,
                        reversalTransactionId = reversalTxId,
                        unallocatedMinorUnits = payment.totalMinorUnits
                    )
                    val rows = paymentDao.updatePayment(voidedPayment)
                    check(rows == 1) { "P0-C: Failed to update payment as void" }

                    // 3. Refund wallet balance
                    val doubleAmount = com.example.util.CurrencyRegistry.canonicalMinorUnitsToDisplay(origTx.amountMinorUnits, origTx.currencyCode)
                    val refundRows = walletDao.adjustBalance(origTx.walletId, userId, doubleAmount)
                    check(refundRows == 1) { "P0-C: Failed to refund wallet balance" }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
