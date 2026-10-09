package com.example.util

import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPlanItem
import com.example.data.model.PayLaterBillScanResult

/**
 * Deduplication engine for imported PayLater and loan bills.
 * Enforces strict per-user isolation: checks candidates ONLY against
 * debts owned by the same user.
 */
object PayLaterDeduplicator {

    data class DuplicateCheckResult(
        val isDuplicate: Boolean,
        val matchedDebt: DebtItem? = null,
        val matchedPlan: InstallmentPlanItem? = null,
        val reason: String? = null
    )

    /**
     * Checks whether [candidate] or [scanResult] is a potential duplicate of an existing debt in [userDebts]
     * or an existing installment plan in [userInstallmentPlans].
     *
     * @param targetUserId The ID of the active user. If null, matching is skipped for safety.
     * @param userDebts The list of existing debts belonging strictly to the active user.
     * @param userInstallmentPlans The list of existing installment plans belonging strictly to the active user.
     * @param providerName The provider name of the candidate bill.
     * @param currentBillAmount The amount due for the current period.
     * @param totalRemainingAmount The total remaining debt balance.
     * @param dueDateStr The due date string.
     */
    fun checkDuplicate(
        targetUserId: Long?,
        userDebts: List<DebtItem>,
        userInstallmentPlans: List<InstallmentPlanItem> = emptyList(),
        providerName: String,
        currentBillAmount: Double,
        totalRemainingAmount: Double,
        dueDateStr: String
    ): DuplicateCheckResult {
        if (targetUserId == null) {
            return DuplicateCheckResult(isDuplicate = false)
        }

        val normalizedProvider = providerName.lowercase().trim()
        val candidateDay = extractDayNumber(dueDateStr)

        val candidateEffectiveAmount = if (totalRemainingAmount > 0.0) totalRemainingAmount else currentBillAmount
        val candidateBillAmount = if (currentBillAmount > 0.0) currentBillAmount else totalRemainingAmount

        for (debt in userDebts) {
            // Strictly check that debt belongs to target user
            if (debt.userId != targetUserId) {
                continue
            }

            val existingName = debt.name.lowercase().trim()
            val isProviderMatch = isSimilarProvider(normalizedProvider, existingName)

            if (isProviderMatch) {
                val existingDay = extractDayNumber(debt.dueDate)
                val isDueDayMatch = candidateDay != null && existingDay != null && candidateDay == existingDay

                val isAmountMatch = Math.abs(debt.amount - candidateEffectiveAmount) <= 2.0 ||
                        (debt.minMonthlyPayment > 0.0 && Math.abs(debt.minMonthlyPayment - candidateBillAmount) <= 2.0)

                if (isDueDayMatch && isAmountMatch) {
                    return DuplicateCheckResult(
                        isDuplicate = true,
                        matchedDebt = debt,
                        reason = "Ditemukan tagihan '${debt.name}' dengan nominal dan tanggal jatuh tempo yang sama (${debt.dueDate})."
                    )
                } else if (isDueDayMatch && (candidateBillAmount <= 0.0 && totalRemainingAmount <= 0.0)) {
                    // Candidate has no amount extracted yet, but provider and due day match exactly
                    return DuplicateCheckResult(
                        isDuplicate = true,
                        matchedDebt = debt,
                        reason = "Ditemukan tagihan '${debt.name}' dari penyedia yang sama dengan tanggal jatuh tempo serupa (${debt.dueDate}). Harap periksa apakah ini tagihan yang sama."
                    )
                } else if (isAmountMatch && (candidateDay == null || existingDay == null)) {
                    return DuplicateCheckResult(
                        isDuplicate = true,
                        matchedDebt = debt,
                        reason = "Ditemukan tagihan '${debt.name}' dari penyedia yang sama dengan nominal serupa."
                    )
                }
            }
        }

        // Check against active Installment Plans belonging to target user
        for (plan in userInstallmentPlans) {
            if (plan.userId != targetUserId) continue
            val planName = plan.name.lowercase().trim()
            val isProviderMatch = isSimilarProvider(normalizedProvider, planName) ||
                    (plan.provider.isNotBlank() && isSimilarProvider(normalizedProvider, plan.provider.lowercase().trim()))

            if (isProviderMatch) {
                val isAmountMatch = candidateBillAmount > 0.0 && Math.abs(candidateBillAmount - plan.installmentAmount) <= 100.0
                val isDayMatch = candidateDay != null && candidateDay == plan.anchorDay
                val isAmountUnspecified = candidateBillAmount <= 0.0 && totalRemainingAmount <= 0.0

                if (isAmountMatch || (isDayMatch && isAmountUnspecified)) {
                    return DuplicateCheckResult(
                        isDuplicate = true,
                        matchedPlan = plan,
                        reason = "Ditemukan kontrak cicilan berjangka '${plan.name}' (${plan.paidCount}/${plan.totalTenor} angsuran). Tagihan ini terdeteksi berasal dari kontrak cicilan yang sama."
                    )
                }
            }
        }

        return DuplicateCheckResult(isDuplicate = false)
    }

    fun checkDuplicate(
        targetUserId: Long?,
        userDebts: List<DebtItem>,
        userInstallmentPlans: List<InstallmentPlanItem> = emptyList(),
        scanResult: PayLaterBillScanResult
    ): DuplicateCheckResult {
        return checkDuplicate(
            targetUserId = targetUserId,
            userDebts = userDebts,
            userInstallmentPlans = userInstallmentPlans,
            providerName = scanResult.providerName,
            currentBillAmount = scanResult.currentBillAmount,
            totalRemainingAmount = scanResult.totalRemainingAmount,
            dueDateStr = scanResult.dueDate
        )
    }

    fun checkDuplicate(
        targetUserId: Long?,
        userDebts: List<DebtItem>,
        scanResult: PayLaterBillScanResult
    ): DuplicateCheckResult {
        return checkDuplicate(
            targetUserId = targetUserId,
            userDebts = userDebts,
            userInstallmentPlans = emptyList(),
            scanResult = scanResult
        )
    }

    private fun extractDayNumber(str: String): Int? {
        val match = Regex("\\b([1-9]|[12][0-9]|3[01])\\b").find(str)
        return match?.value?.toIntOrNull()
    }

    private fun isSimilarProvider(providerA: String, providerB: String): Boolean {
        if (providerA.isBlank() || providerB.isBlank()) return false
        if (providerA == providerB) return true
        if (providerA.contains(providerB) || providerB.contains(providerA)) return true

        val knownKeywords = listOf(
            "shopee", "spaylater", "spinjam",
            "gopay", "gopaylater", "gopinjam",
            "dana", "danacicil",
            "akulaku",
            "adakami",
            "kredivo",
            "indodana",
            "kartu kredit",
            "bca", "mandiri", "bri", "bni"
        )

        for (kw in knownKeywords) {
            val aHas = providerA.contains(kw)
            val bHas = providerB.contains(kw)
            if (aHas && bHas) return true
        }

        return false
    }
}
