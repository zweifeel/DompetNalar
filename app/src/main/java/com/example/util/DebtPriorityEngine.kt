package com.example.util

import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPlanItem
import java.math.BigInteger
import java.util.Calendar
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Stage 3: Deterministic Debt Priority Engine for FinSmart.
 *
 * Implements the **Debt Avalanche** financial methodology:
 * 1. Honors mandatory minimum payments for all active obligations.
 * 2. Prioritizes extra payoff allocations strictly to the debt with the highest verified interest rate.
 * 3. Breaks ties by earliest due date and smallest balance.
 * 4. Never fabricates or assumes unverified interest rates.
 * 5. Provides deterministic simulation of debt freedom timeline.
 */
object DebtPriorityEngine {

    enum class DebtSourceType {
        STANDARD_DEBT,
        INSTALLMENT_PLAN
    }

    enum class Strategy {
        DEBT_AVALANCHE,
        DEBT_SNOWBALL
    }

    /**
     * Unified abstraction over [DebtItem] and [InstallmentPlanItem].
     */
    data class UnifiedDebt(
        val id: String,
        val name: String,
        val sourceType: DebtSourceType,
        val remainingBalance: Double,
        val interestRatePercent: Double,
        val minMonthlyPayment: Double,
        val dueDateString: String,
        val daysUntilDue: Int,
        val isUrgent: Boolean,
        val hasVerifiedInterest: Boolean,
        val originalItem: Any,
        val principalMinorUnits: Long = (remainingBalance * 100.0).roundToLong().coerceAtLeast(0L),
        val minMonthlyPaymentMinorUnits: Long = (minMonthlyPayment * 100.0).roundToLong().coerceAtLeast(0L),
        val interestRateBasisPoints: Int = java.math.BigDecimal(interestRatePercent.toString())
            .multiply(java.math.BigDecimal("100"))
            .setScale(0, java.math.RoundingMode.HALF_UP)
            .toInt()
            .coerceAtLeast(0),
        val monthlyInterestMinorUnits: Long = calculateMonthlyInterestMinorUnits(
            principalMinorUnits = (remainingBalance * 100.0).roundToLong().coerceAtLeast(0L),
            annualInterestRatePercent = java.math.BigDecimal(interestRatePercent.toString())
        ),
        val isNegativeAmortization: Boolean = checkNegativeAmortization(
            principalMinorUnits = (remainingBalance * 100.0).roundToLong().coerceAtLeast(0L),
            minMonthlyPaymentMinorUnits = (minMonthlyPayment * 100.0).roundToLong().coerceAtLeast(0L),
            annualInterestRatePercent = java.math.BigDecimal(interestRatePercent.toString())
        )
    )

    /**
     * Calculates monthly interest in minor units using exact BigInteger arithmetic:
     * interestMinorUnits = (principalMinorUnits * basisPoints) / 120000
     * with Half-Up rounding on the remainder.
     *
     * 1 basis point = 0.01% = 1/10000.
     * Monthly divisor = 10000 * 12 = 120000.
     *
     * Guarantees zero floating-point imprecision and rejects output overflow beyond Long.MAX_VALUE fail-closed.
     *
     * @throws IllegalArgumentException if principalMinorUnits or basisPoints is negative.
     * @throws ArithmeticException if computed monthly interest exceeds Long.MAX_VALUE.
     */
    fun calculateMonthlyInterestMinorUnits(
        principalMinorUnits: Long,
        basisPoints: Long
    ): Long {
        require(principalMinorUnits >= 0L) { "principalMinorUnits cannot be negative ($principalMinorUnits)" }
        require(basisPoints >= 0L) { "basisPoints cannot be negative ($basisPoints)" }
        if (principalMinorUnits == 0L || basisPoints == 0L) return 0L

        val product = BigInteger.valueOf(principalMinorUnits).multiply(BigInteger.valueOf(basisPoints))
        val divisor = BigInteger.valueOf(120000L)
        val halfDivisor = BigInteger.valueOf(60000L)
        val (quotient, remainder) = product.divideAndRemainder(divisor)
        val finalBigInt = if (remainder.compareTo(halfDivisor) >= 0) {
            quotient.add(BigInteger.ONE)
        } else {
            quotient
        }
        return finalBigInt.longValueExact()
    }

    /**
     * Exact BigDecimal overload preventing floating-point drift:
     * monthlyInterest = (principalMinorUnits * unscaledRate) / (1200 * 10^scale)
     * with standard HALF_UP rounding.
     */
    fun calculateMonthlyInterestMinorUnits(
        principalMinorUnits: Long,
        annualInterestRatePercent: java.math.BigDecimal
    ): Long {
        require(principalMinorUnits >= 0L) { "principalMinorUnits cannot be negative ($principalMinorUnits)" }
        require(annualInterestRatePercent.signum() >= 0) { "annualInterestRatePercent cannot be negative ($annualInterestRatePercent)" }
        if (principalMinorUnits == 0L || annualInterestRatePercent.signum() == 0) return 0L

        val scale = annualInterestRatePercent.scale().coerceAtLeast(0)
        val unscaledRate = annualInterestRatePercent.unscaledValue()
        val powerOfTen = BigInteger.TEN.pow(scale)
        val divisor = BigInteger.valueOf(1200L).multiply(powerOfTen)

        val product = BigInteger.valueOf(principalMinorUnits).multiply(unscaledRate)
        val (quotient, remainder) = product.divideAndRemainder(divisor)
        val finalBigInt = if (remainder.multiply(BigInteger.valueOf(2L)).compareTo(divisor) >= 0) {
            quotient.add(BigInteger.ONE)
        } else {
            quotient
        }
        return finalBigInt.longValueExact()
    }

    fun calculateMonthlyInterestMinorUnits(
        principalMinorUnits: Long,
        annualInterestRatePercent: Double
    ): Long {
        require(principalMinorUnits >= 0L) { "principalMinorUnits cannot be negative ($principalMinorUnits)" }
        require(annualInterestRatePercent >= 0.0) { "annualInterestRatePercent cannot be negative ($annualInterestRatePercent)" }
        if (principalMinorUnits == 0L || annualInterestRatePercent == 0.0) return 0L
        return calculateMonthlyInterestMinorUnits(
            principalMinorUnits,
            java.math.BigDecimal(annualInterestRatePercent.toString())
        )
    }

    fun checkNegativeAmortization(
        principalMinorUnits: Long,
        minMonthlyPaymentMinorUnits: Long,
        annualInterestRatePercent: java.math.BigDecimal
    ): Boolean {
        if (annualInterestRatePercent.signum() <= 0 || principalMinorUnits <= 0L) return false
        val monthlyInterest = calculateMonthlyInterestMinorUnits(principalMinorUnits, annualInterestRatePercent)
        return minMonthlyPaymentMinorUnits < monthlyInterest
    }

    fun checkNegativeAmortization(
        principalMinorUnits: Long,
        minMonthlyPaymentMinorUnits: Long,
        annualInterestRatePercent: Double
    ): Boolean {
        if (annualInterestRatePercent <= 0.0 || principalMinorUnits <= 0L) return false
        val monthlyInterest = calculateMonthlyInterestMinorUnits(principalMinorUnits, annualInterestRatePercent)
        return minMonthlyPaymentMinorUnits < monthlyInterest
    }

    /**
     * Checks if minimum monthly payment triggers negative amortization:
     * minMonthlyPaymentMinorUnits < calculateMonthlyInterestMinorUnits(principalMinorUnits, basisPoints)
     */
    fun checkNegativeAmortization(
        principalMinorUnits: Long,
        minMonthlyPaymentMinorUnits: Long,
        basisPoints: Long
    ): Boolean {
        if (basisPoints <= 0L || principalMinorUnits <= 0L) return false
        val monthlyInterest = calculateMonthlyInterestMinorUnits(principalMinorUnits, basisPoints)
        return minMonthlyPaymentMinorUnits < monthlyInterest
    }

    data class PrioritizedDebtItem(
        val rank: Int,
        val debt: UnifiedDebt,
        val mandatoryPayment: Double,
        val extraAllocation: Double,
        val totalMonthlyPayment: Double,
        val payoffPriorityReason: String
    )

    data class DebtPayoffMilestone(
        val monthIndex: Int,
        val debtId: String,
        val debtName: String,
        val totalMonthsElapsed: Int,
        val freedMonthlyCashflow: Double
    )

    data class DebtPriorityPlan(
        val strategy: String = "DEBT_AVALANCHE",
        val prioritizedItems: List<PrioritizedDebtItem>,
        val totalRemainingDebt: Double,
        val totalMandatoryMinimum: Double,
        val availableBudget: Double,
        val effectiveMonthlyPayment: Double,
        val isDeficit: Boolean,
        val monthlyShortfall: Double,
        val estimatedMonthsToDebtFree: Int?,
        val milestones: List<DebtPayoffMilestone>,
        val unverifiedInterestCount: Int,
        val summaryExplanation: String,
        val negativeAmortizationDebtIds: List<String> = emptyList()
    )

    /**
     * Normalizes a due date string (e.g. "Tgl 15 tiap bulan", "15", "2026-11-20")
     * into remaining days until due from today.
     */
    fun calculateDaysUntilDue(dueDateStr: String): Int {
        val trimmed = dueDateStr.trim()
        val digits = Regex("\\d+").find(trimmed)?.value?.toIntOrNull() ?: 15
        val now = Calendar.getInstance()
        val today = now.get(Calendar.DAY_OF_MONTH)
        val maxDays = now.getActualMaximum(Calendar.DAY_OF_MONTH)
        return if (digits >= today) {
            digits - today
        } else {
            (maxDays - today) + digits
        }
    }

    /**
     * Converts a [DebtItem] into a [UnifiedDebt].
     */
    fun fromDebtItem(item: DebtItem): UnifiedDebt? {
        if (item.amount <= 0.0) return null
        val days = calculateDaysUntilDue(item.dueDate)
        val hasInterest = item.interestRatePercent > 0.0
        val minPay = if (item.minMonthlyPayment > 0.0) {
            item.minMonthlyPayment.coerceAtMost(item.amount)
        } else {
            // Default minimum payment rule: 10% of balance or full if balance < 50,000
            (item.amount * 0.10).coerceAtLeast(50000.0).coerceAtMost(item.amount)
        }

        return UnifiedDebt(
            id = item.id,
            name = item.name.ifBlank { "Tagihan Utang" },
            sourceType = DebtSourceType.STANDARD_DEBT,
            remainingBalance = item.amount,
            interestRatePercent = if (item.interestRatePercent.isFinite() && item.interestRatePercent >= 0.0) item.interestRatePercent else 0.0,
            minMonthlyPayment = minPay,
            dueDateString = item.dueDate,
            daysUntilDue = days,
            isUrgent = days <= 5,
            hasVerifiedInterest = hasInterest,
            originalItem = item
        )
    }

    /**
     * Converts an active [InstallmentPlanItem] into a [UnifiedDebt].
     */
    fun fromInstallmentPlan(plan: InstallmentPlanItem): UnifiedDebt? {
        if (plan.isCompleted || plan.remainingTenor <= 0) return null
        val remainingPrincipal = plan.totalRemainingPrincipal?.takeIf { it > 0.0 }
            ?: (plan.installmentAmount * plan.remainingTenor)
        if (remainingPrincipal <= 0.0) return null

        val days = plan.anchorDay.let { day ->
            val now = Calendar.getInstance()
            val today = now.get(Calendar.DAY_OF_MONTH)
            val maxDays = now.getActualMaximum(Calendar.DAY_OF_MONTH)
            if (day >= today) day - today else (maxDays - today) + day
        }

        return UnifiedDebt(
            id = plan.id,
            name = plan.name.ifBlank { "Cicilan ${plan.provider}" },
            sourceType = DebtSourceType.INSTALLMENT_PLAN,
            remainingBalance = remainingPrincipal,
            interestRatePercent = 0.0, // PayLater contracts typically bake fees into fixed installments
            minMonthlyPayment = plan.installmentAmount.coerceAtMost(remainingPrincipal),
            dueDateString = plan.nextDueDate.ifBlank { "Tgl ${plan.anchorDay} tiap bulan" },
            daysUntilDue = days,
            isUrgent = days <= 5,
            hasVerifiedInterest = false,
            originalItem = plan
        )
    }

    /**
     * Calculates the deterministic Debt Avalanche priority plan.
     *
     * @param debts Raw [DebtItem] list belonging strictly to the active user.
     * @param installmentPlans Raw [InstallmentPlanItem] list belonging strictly to the active user.
     * @param totalMonthlyBudget Total monthly fund dedicated to debt repayment.
     */
    fun calculatePlan(
        debts: List<DebtItem>,
        installmentPlans: List<InstallmentPlanItem> = emptyList(),
        totalMonthlyBudget: Double,
        strategy: Strategy = Strategy.DEBT_AVALANCHE
    ): DebtPriorityPlan {
        val unifiedList = mutableListOf<UnifiedDebt>()
        debts.forEach { fromDebtItem(it)?.let { d -> unifiedList.add(d) } }
        installmentPlans.forEach { fromInstallmentPlan(it)?.let { p -> unifiedList.add(p) } }

        val totalDebt = unifiedList.sumOf { it.remainingBalance }
        val totalMandatoryMin = unifiedList.sumOf { it.minMonthlyPayment }
        val sanitizedBudget = totalMonthlyBudget.coerceAtLeast(0.0)

        if (unifiedList.isEmpty()) {
            return DebtPriorityPlan(
                strategy = strategy.name,
                prioritizedItems = emptyList(),
                totalRemainingDebt = 0.0,
                totalMandatoryMinimum = 0.0,
                availableBudget = sanitizedBudget,
                effectiveMonthlyPayment = 0.0,
                isDeficit = false,
                monthlyShortfall = 0.0,
                estimatedMonthsToDebtFree = 0,
                milestones = emptyList(),
                unverifiedInterestCount = 0,
                summaryExplanation = "Selamat! Anda tidak memiliki kewajiban utang atau cicilan aktif saat ini."
            )
        }

        // Deficit check: budget cannot even cover mandatory minimums
        val isDeficit = sanitizedBudget < totalMandatoryMin
        val shortfall = if (isDeficit) totalMandatoryMin - sanitizedBudget else 0.0

        val sortedDebts = when (strategy) {
            Strategy.DEBT_AVALANCHE -> {
                unifiedList.sortedWith(
                    compareByDescending<UnifiedDebt> { it.interestRateBasisPoints }
                        .thenBy { it.daysUntilDue }
                        .thenBy { it.remainingBalance }
                )
            }
            Strategy.DEBT_SNOWBALL -> {
                unifiedList.sortedWith(
                    compareBy<UnifiedDebt> { it.remainingBalance }
                        .thenBy { it.daysUntilDue }
                        .thenByDescending { it.interestRateBasisPoints }
                )
            }
        }

        var remainingExtra = if (!isDeficit) sanitizedBudget - totalMandatoryMin else 0.0
        val prioritizedItems = mutableListOf<PrioritizedDebtItem>()

        sortedDebts.forEachIndexed { index, debt ->
            val rank = index + 1
            val minPay = debt.minMonthlyPayment
            val extraAlloc = if (rank == 1 && remainingExtra > 0.0) {
                val maxNeeded = (debt.remainingBalance - minPay).coerceAtLeast(0.0)
                val allocated = remainingExtra.coerceAtMost(maxNeeded)
                remainingExtra -= allocated
                allocated
            } else 0.0

            val totalMonthly = minPay + extraAlloc

            val reason = buildString {
                if (rank == 1) {
                    if (debt.hasVerifiedInterest) {
                        append("Target Utama (Bunga Tertinggi ${debt.interestRatePercent}%)")
                    } else if (debt.isUrgent) {
                        append("Target Utama (Jatuh Tempo Mendekat dalam ${debt.daysUntilDue} hari)")
                    } else {
                        append("Target Pelunasan Utama")
                    }
                    if (extraAlloc > 0.0) {
                        append(" — Menerima seluruh alokasi dana ekstra")
                    }
                } else {
                    append("Prioritas #$rank (Bayar cicilan minimum bulanan)")
                }
            }

            prioritizedItems.add(
                PrioritizedDebtItem(
                    rank = rank,
                    debt = debt,
                    mandatoryPayment = minPay,
                    extraAllocation = extraAlloc,
                    totalMonthlyPayment = totalMonthly,
                    payoffPriorityReason = reason
                )
            )
        }

        val unverifiedCount = unifiedList.count { !it.hasVerifiedInterest }

        // Run multi-month simulation to estimate timeline & milestones
        val (estimatedMonths, milestones) = simulatePayoffMonths(
            debts = sortedDebts,
            totalMonthlyBudget = sanitizedBudget,
            isDeficit = isDeficit
        )

        val explanation = buildString {
            if (isDeficit) {
                append("Anggaran bulanan Anda (${CurrencyFormatter.formatRupiah(sanitizedBudget)}) belum mencukupi total kewajiban minimum bulanan (${CurrencyFormatter.formatRupiah(totalMandatoryMin)}). Terdapat defisit sebesar ${CurrencyFormatter.formatRupiah(shortfall)}/bulan. Prioritaskan pelunasan kewajiban yang jatuh tempo paling mendesak.")
            } else {
                append("Dengan metode Debt Avalanche, seluruh utang Anda dibayar dengan cicilan minimum, dan sisa dana ekstra dipusatkan ke utang dengan biaya bunga terbesar.")
                if (estimatedMonths != null && estimatedMonths > 0) {
                    append(" Diestimasikan lunas dalam $estimatedMonths bulan.")
                }
                if (unverifiedCount > 0) {
                    append(" (Catatan: $unverifiedCount tagihan tidak memiliki data suku bunga terverifikasi dan diprioritaskan berdasarkan jatuh tempo).")
                }
            }
        }

        val negAmortIds = unifiedList.filter { it.isNegativeAmortization }.map { it.id }

        return DebtPriorityPlan(
            strategy = strategy.name,
            prioritizedItems = prioritizedItems,
            totalRemainingDebt = totalDebt,
            totalMandatoryMinimum = totalMandatoryMin,
            availableBudget = sanitizedBudget,
            effectiveMonthlyPayment = if (isDeficit) sanitizedBudget else totalMandatoryMin + (sanitizedBudget - totalMandatoryMin - remainingExtra),
            isDeficit = isDeficit,
            monthlyShortfall = shortfall,
            estimatedMonthsToDebtFree = estimatedMonths,
            milestones = milestones,
            unverifiedInterestCount = unverifiedCount,
            summaryExplanation = explanation,
            negativeAmortizationDebtIds = negAmortIds
        )
    }

    /**
     * Simulates monthly debt reduction until all debts are paid off or up to 360 months cap.
     */
    private fun simulatePayoffMonths(
        debts: List<UnifiedDebt>,
        totalMonthlyBudget: Double,
        isDeficit: Boolean
    ): Pair<Int?, List<DebtPayoffMilestone>> {
        if (isDeficit || totalMonthlyBudget <= 0.0) return Pair(null, emptyList())

        // Working mutable balances
        val balances = debts.map { it.remainingBalance }.toMutableList()
        val minimums = debts.map { it.minMonthlyPayment }.toMutableList()
        val milestones = mutableListOf<DebtPayoffMilestone>()

        var months = 0
        val maxMonths = 360 // 30-year safety limit

        while (months < maxMonths && balances.any { it > 0.01 }) {
            months++
            var monthlyPool = totalMonthlyBudget

            // 1. Pay minimums for all active debts
            for (i in balances.indices) {
                if (balances[i] > 0.01) {
                    val pay = minimums[i].coerceAtMost(balances[i]).coerceAtMost(monthlyPool)
                    balances[i] -= pay
                    monthlyPool -= pay
                }
            }

            // 2. Cascade remaining pool into highest priority debt with positive balance
            if (monthlyPool > 0.0) {
                for (i in balances.indices) {
                    if (balances[i] > 0.01) {
                        val extraPay = monthlyPool.coerceAtMost(balances[i])
                        balances[i] -= extraPay
                        monthlyPool -= extraPay
                        if (balances[i] <= 0.01) {
                            balances[i] = 0.0
                            milestones.add(
                                DebtPayoffMilestone(
                                    monthIndex = months,
                                    debtId = debts[i].id,
                                    debtName = debts[i].name,
                                    totalMonthsElapsed = months,
                                    freedMonthlyCashflow = minimums[i]
                                )
                            )
                        }
                        if (monthlyPool <= 0.0) break
                    }
                }
            } else {
                // Check if any debt finished from minimum payment alone
                for (i in balances.indices) {
                    if (balances[i] <= 0.01 && !milestones.any { it.debtId == debts[i].id }) {
                        balances[i] = 0.0
                        milestones.add(
                            DebtPayoffMilestone(
                                monthIndex = months,
                                debtId = debts[i].id,
                                debtName = debts[i].name,
                                totalMonthsElapsed = months,
                                freedMonthlyCashflow = minimums[i]
                            )
                        )
                    }
                }
            }
        }

        val estimatedMonths = if (balances.all { it <= 0.01 }) months else null
        return Pair(estimatedMonths, milestones)
    }
}
