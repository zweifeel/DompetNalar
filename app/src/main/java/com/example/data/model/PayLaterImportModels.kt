package com.example.data.model

import java.util.UUID

/**
 * Data model representing the result of OCR / AI extraction from
 * loan / PayLater bills and screenshots.
 */
data class PayLaterSchedulePeriod(
    val periodIndex: Int,
    val dueDate: String,
    val amount: Double,
    val status: String = "Belum Bayar",
    val dueIsoDate: String? = null,
    val dueYear: Int? = null,
    val dueMonth: Int? = null,
    val dueDay: Int? = null,
    val isPaid: Boolean = false
)

data class PayLaterBillScanResult(
    val id: String = UUID.randomUUID().toString(),
    val providerName: String = "",
    val productName: String? = null,
    val currentBillAmount: Double = 0.0,
    val totalRemainingAmount: Double = 0.0,
    val installmentCurrent: Int? = null,
    val installmentTotal: Int? = null,
    val dueDate: String = "",
    val dueDay: Int? = null,
    val dueMonth: Int? = null,
    val dueYear: Int? = null,
    val dueIsoDate: String? = null,
    val isDateAmbiguous: Boolean = false,
    val dateConfidenceStatus: String = if (isDateAmbiguous) "NEEDS_USER_CONFIRMATION" else "VERIFIED",
    val billStatus: String? = null,
    val interestRate: Double? = null,
    val rawNotes: String? = null,
    val confidenceWarnings: List<String> = emptyList(),
    val rawText: String? = null,
    val imageUri: String? = null,
    val isDemoPreset: Boolean = false,
    val installmentSchedule: List<PayLaterSchedulePeriod> = emptyList()
) {
    /**
     * Converts the scanned PayLater bill into an [InstallmentPlanItem] if it represents
     * a multi-period recurring installment contract (e.g. 1/3, 3/6).
     */
    fun toInstallmentPlanItem(userId: Long? = null): InstallmentPlanItem? {
        val total = installmentTotal ?: installmentSchedule.size.takeIf { it > 1 } ?: return null
        if (total <= 1) return null
        val current = installmentCurrent ?: 1
        val paid = if (current > 1) current - 1 else 0
        val day = dueDay ?: (Regex("\\d+").find(dueDate)?.value?.toIntOrNull() ?: 15)
        val nameStr = if (!productName.isNullOrBlank() && !productName.equals(providerName, ignoreCase = true)) {
            "$providerName ($productName)"
        } else if (providerName.isNotBlank()) {
            providerName
        } else {
            "Cicilan PayLater"
        }

        val principal = if (totalRemainingAmount > 0.0) totalRemainingAmount else null
        val nextDue = dueIsoDate ?: if (dueDate.isNotBlank()) dueDate else "Tgl $day tiap bulan"

        return InstallmentPlanItem(
            id = id,
            name = nameStr.trim(),
            provider = providerName.trim(),
            installmentAmount = if (currentBillAmount > 0.0) currentBillAmount else if (principal != null) principal / total else 0.0,
            totalTenor = total,
            paidCount = paid,
            anchorDay = day,
            startDate = com.example.util.InstallmentDateHelper.formatIsoDate(java.util.Calendar.getInstance()),
            nextDueDate = nextDue,
            totalRemainingPrincipal = principal,
            notes = rawNotes ?: "",
            userId = userId
        )
    }

    /**
     * Converts the scanned PayLater bill into a list of [InstallmentPeriod] items
     * to be persisted along with the parent plan in Room 11.
     */
    fun toInstallmentPeriods(planId: String, userId: Long): List<InstallmentPeriod> {
        val total = installmentTotal ?: installmentSchedule.size.takeIf { it > 0 } ?: return emptyList()
        val periods = mutableListOf<InstallmentPeriod>()
        val baseMinorUnits = (currentBillAmount * 100).toLong()

        if (installmentSchedule.isNotEmpty()) {
            installmentSchedule.forEachIndexed { idx, sched ->
                val pDue = sched.dueIsoDate ?: sched.dueDate
                val pAmountMinor = (sched.amount * 100).toLong()
                periods.add(
                    InstallmentPeriod(
                        id = UUID.randomUUID().toString(),
                        planId = planId,
                        userId = userId,
                        periodNumber = sched.periodIndex.takeIf { it > 0 } ?: (idx + 1),
                        amountDueMinorUnits = pAmountMinor,
                        amountPaidMinorUnits = 0L,
                        dueDate = pDue,
                        isPaid = sched.isPaid
                    )
                )
            }
        } else {
            val startDue = dueIsoDate ?: dueDate
            for (p in 1..total) {
                val periodDue = if (p == 1) {
                    startDue
                } else {
                    com.example.util.InstallmentDateHelper.calculateNextDueDate(
                        startDue,
                        anchorDay = dueDay ?: 15,
                        monthsToAdd = p - 1
                    )
                }
                periods.add(
                    InstallmentPeriod(
                        id = UUID.randomUUID().toString(),
                        planId = planId,
                        userId = userId,
                        periodNumber = p,
                        amountDueMinorUnits = baseMinorUnits,
                        amountPaidMinorUnits = 0L,
                        dueDate = periodDue,
                        isPaid = false
                    )
                )
            }
        }
        return periods
    }

    /**
     * Converts the scanned PayLater bill into a [DebtItem] for the specified user.
     * Accurately distinguishes between [currentBillAmount] (amount due now / min monthly payment)
     * and [totalRemainingAmount] (overall remaining debt balance).
     */
    fun toDebtItem(userId: Long? = null, customCategory: String = "Pinjaman Online"): DebtItem {
        val totalAmount = if (totalRemainingAmount > 0.0) {
            totalRemainingAmount
        } else {
            currentBillAmount
        }

        val monthlyPayment = if (currentBillAmount > 0.0) {
            currentBillAmount
        } else if (totalRemainingAmount > 0.0) {
            totalRemainingAmount
        } else {
            0.0
        }

        val baseName = if (!productName.isNullOrBlank() && !productName.equals(providerName, ignoreCase = true)) {
            "$providerName ($productName)"
        } else if (providerName.isNotBlank()) {
            providerName
        } else {
            "Tagihan PayLater / Pinjaman"
        }

        val installmentSuffix = if (installmentCurrent != null && installmentTotal != null && installmentTotal > 0) {
            " - Cicilan $installmentCurrent/$installmentTotal"
        } else {
            ""
        }

        val effectiveDueDate = dueIsoDate ?: if (dueDate.isNotBlank()) dueDate else "15 tiap bulan"

        return DebtItem(
            id = id,
            name = (baseName + installmentSuffix).trim(),
            amount = totalAmount,
            interestRatePercent = interestRate ?: 0.0,
            minMonthlyPayment = monthlyPayment,
            category = customCategory,
            dueDate = effectiveDueDate,
            userId = userId
        )
    }
}

/**
 * Result model for batch multi-screenshot import.
 */
data class PayLaterBatchImportResult(
    val bills: List<PayLaterBillScanResult> = emptyList(),
    val failedImageUris: List<String> = emptyList(),
    val totalImages: Int = 0,
    val processedCount: Int = 0
)
