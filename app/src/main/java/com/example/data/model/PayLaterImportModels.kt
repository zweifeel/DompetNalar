package com.example.data.model

import java.util.UUID

/**
 * Data model representing the result of OCR / AI extraction from
 * loan / PayLater bills and screenshots.
 */
data class PayLaterBillScanResult(
    val providerName: String = "",
    val productName: String? = null,
    val currentBillAmount: Double = 0.0,
    val totalRemainingAmount: Double = 0.0,
    val installmentCurrent: Int? = null,
    val installmentTotal: Int? = null,
    val dueDate: String = "",
    val dueDay: Int? = null,
    val billStatus: String? = null,
    val interestRate: Double? = null,
    val rawNotes: String? = null,
    val confidenceWarnings: List<String> = emptyList(),
    val rawText: String? = null,
    val imageUri: String? = null,
    val isDemoPreset: Boolean = false
) {
    /**
     * Converts the scanned PayLater bill into an [InstallmentPlanItem] if it represents
     * a multi-period recurring installment contract (e.g. 1/3, 3/6).
     */
    fun toInstallmentPlanItem(userId: Long? = null): InstallmentPlanItem? {
        val total = installmentTotal ?: return null
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

        return InstallmentPlanItem(
            id = UUID.randomUUID().toString(),
            name = nameStr.trim(),
            provider = providerName.trim(),
            installmentAmount = if (currentBillAmount > 0.0) currentBillAmount else if (principal != null) principal / total else 0.0,
            totalTenor = total,
            paidCount = paid,
            anchorDay = day,
            startDate = com.example.util.InstallmentDateHelper.formatIsoDate(java.util.Calendar.getInstance()),
            nextDueDate = if (dueDate.isNotBlank()) dueDate else "Tgl $day tiap bulan",
            totalRemainingPrincipal = principal,
            notes = rawNotes ?: "",
            userId = userId
        )
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

        // Construct descriptive debt name
        val baseName = if (!productName.isNullOrBlank() && !productName.equals(providerName, ignoreCase = true)) {
            "$providerName ($productName)"
        } else if (providerName.isNotBlank()) {
            providerName
        } else {
            "Tagihan PayLater / Pinjaman"
        }

        val installmentSuffix = if (installmentCurrent != null && installmentTotal != null && installmentTotal > 0) {
            " - Cicilan $installmentCurrent/$installmentTotal"
        } else if (installmentCurrent != null) {
            " - Cicilan ke-$installmentCurrent"
        } else {
            ""
        }

        val finalName = (baseName + installmentSuffix).trim()

        val normalizedDueDate = if (dueDate.isNotBlank()) {
            dueDate
        } else if (dueDay != null && dueDay in 1..31) {
            "Tgl $dueDay tiap bulan"
        } else {
            "15 tiap bulan"
        }

        return DebtItem(
            id = UUID.randomUUID().toString(),
            name = finalName,
            amount = totalAmount,
            interestRatePercent = interestRate ?: 0.0,
            minMonthlyPayment = monthlyPayment,
            category = customCategory,
            dueDate = normalizedDueDate,
            createdAt = System.currentTimeMillis(),
            userId = userId
        )
    }
}
