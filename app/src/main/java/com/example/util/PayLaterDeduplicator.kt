package com.example.util

import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPlanItem
import com.example.data.model.PayLaterBillScanResult
import java.util.Locale

/**
 * Deduplication and platform normalization engine for imported PayLater and loan bills.
 * Enforces strict per-user isolation: checks candidates ONLY against
 * debts owned by the same user.
 */
object PayLaterDeduplicator {

    data class DuplicateCheckResult(
        val isDuplicate: Boolean,
        val matchedDebt: DebtItem? = null,
        val matchedPlan: InstallmentPlanItem? = null,
        val matchedBatchIndex: Int? = null,
        val reason: String? = null
    )

    /**
     * Maps raw or OCR-extracted platform strings to a canonical platform name.
     */
    fun normalizePlatformName(raw: String): String {
        val clean = raw.lowercase(Locale.ROOT).trim()
        return when {
            clean.contains("gopay pinjam") || clean.contains("gopaypinjam") || clean.contains("pinjam gopay") -> "GoPay Pinjam"
            clean.contains("gopay paylater") || clean.contains("gopay pay later") -> "GoPay PayLater"
            clean.contains("gopay later") || clean.contains("gopaylater") -> "GoPay Later"
            clean.contains("dana cicil") || clean.contains("danacicil") || clean.contains("dana paylater") -> "DANA Cicil"
            clean.contains("spinjam") || clean.contains("s pinjam") || clean.contains("shopee pinjam") || clean.contains("pinjam shopee") || clean.contains("shopee loan") -> "Shopee Pinjam"
            clean.contains("spaylater") || clean.contains("s paylater") || clean.contains("shopee paylater") || clean.contains("shopee pay later") || clean.contains("spay later") || (clean.contains("shopee") && !clean.contains("pinjam")) -> "Shopee PayLater"
            clean.contains("kredivo") -> "Kredivo"
            clean.contains("akulaku") || clean.contains("aku laku") -> "Akulaku"
            clean.contains("adakami") || clean.contains("ada kami") -> "AdaKami"
            clean.contains("indodana") || clean.contains("indo dana") -> "Indodana"
            clean.contains("home credit") || clean.contains("homecredit") -> "Home Credit"
            clean.contains("julo") -> "Julo"
            clean.contains("easycash") || clean.contains("easy cash") -> "Easycash"
            clean.contains("rupiah cepat") || clean.contains("rupiahcepat") -> "Rupiah Cepat"
            clean.contains("ovo u card") || clean.contains("ovo paylater") || clean.contains("ovo") -> "OVO PayLater"
            clean.contains("bca card") || clean.contains("kartu kredit bca") -> "Kartu Kredit BCA"
            else -> raw.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    /**
     * Checks whether [candidate] is a potential duplicate of an existing debt in [userDebts]
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

        val normalizedProvider = normalizePlatformName(providerName).lowercase(Locale.ROOT)
        val candidateDay = extractDayNumber(dueDateStr)

        val candidateEffectiveAmount = if (totalRemainingAmount > 0.0) totalRemainingAmount else currentBillAmount
        val candidateBillAmount = if (currentBillAmount > 0.0) currentBillAmount else totalRemainingAmount

        for (debt in userDebts) {
            if (debt.userId != targetUserId) {
                continue
            }

            val existingProvider = normalizePlatformName(debt.name).lowercase(Locale.ROOT)
            val isProviderMatch = isSimilarProvider(normalizedProvider, existingProvider) ||
                    debt.name.lowercase(Locale.ROOT).contains(normalizedProvider)

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
            val planProvider = normalizePlatformName(if (plan.provider.isNotBlank()) plan.provider else plan.name).lowercase(Locale.ROOT)
            val isProviderMatch = isSimilarProvider(normalizedProvider, planProvider) ||
                    plan.name.lowercase(Locale.ROOT).contains(normalizedProvider)

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

    /**
     * Checks whether a candidate scan result duplicates another item in the same import batch.
     */
    fun checkDuplicateInBatch(
        candidateIndex: Int,
        candidate: PayLaterBillScanResult,
        batch: List<PayLaterBillScanResult>
    ): DuplicateCheckResult {
        val candPlatform = normalizePlatformName(candidate.providerName).lowercase(Locale.ROOT)
        val candDay = candidate.dueDay ?: extractDayNumber(candidate.dueDate)
        val candAmount = if (candidate.currentBillAmount > 0) candidate.currentBillAmount else candidate.totalRemainingAmount

        for (i in batch.indices) {
            if (i == candidateIndex) continue
            val other = batch[i]
            val otherPlatform = normalizePlatformName(other.providerName).lowercase(Locale.ROOT)
            val otherDay = other.dueDay ?: extractDayNumber(other.dueDate)
            val otherAmount = if (other.currentBillAmount > 0) other.currentBillAmount else other.totalRemainingAmount

            if (candPlatform == otherPlatform) {
                val dayMatch = candDay != null && otherDay != null && candDay == otherDay
                val amtMatch = Math.abs(candAmount - otherAmount) <= 2.0 && candAmount > 0.0
                if (dayMatch && amtMatch) {
                    return DuplicateCheckResult(
                        isDuplicate = true,
                        matchedBatchIndex = i,
                        reason = "Item ke-${i + 1} dalam batch import ini memiliki penyedia, tanggal jatuh tempo, dan nominal yang identik."
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
            dueDateStr = scanResult.dueIsoDate ?: scanResult.dueDate
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

    private fun isSimilarProvider(cand: String, existing: String): Boolean {
        if (cand.isBlank() || existing.isBlank()) return false
        if (cand == existing) return true
        if (cand.contains(existing) || existing.contains(cand)) return true

        val candKeywords = cand.split(" ", "_", "-").filter { it.length > 2 }
        for (kw in candKeywords) {
            if (existing.contains(kw)) return true
        }
        return false
    }

    private fun extractDayNumber(dueDateStr: String): Int? {
        val parsed = InstallmentDateHelper.parseStructuredDueDate(dueDateStr)
        if (parsed != null && parsed.day in 1..31) {
            return parsed.day
        }
        val match = Regex("""\b([1-9]|[12][0-9]|3[01])\b""").find(dueDateStr)
        return match?.value?.toIntOrNull()
    }
}
