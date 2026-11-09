package com.example.util

import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPlanItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Stage 4: Debt Freedom Planner for FinSmart.
 *
 * Computes realistic debt payoff schedules based on:
 * - Monthly net income
 * - Essential living expenses (never cannibalized)
 * - Emergency savings cushion (strictly preserved)
 * - Mandatory minimum debt payments
 * - Genuine surplus cash flow allocated via Debt Avalanche
 */
object DebtFreedomPlanner {

    data class CashflowBreakdown(
        val monthlyIncome: Double,
        val essentialExpenses: Double,
        val emergencyCushion: Double,
        val uncommittedSurplus: Double,
        val mandatoryDebtMinimums: Double,
        val netExtraForDebt: Double,
        val isDeficit: Boolean,
        val deficitAmount: Double
    )

    data class FreedomPlanResult(
        val cashflow: CashflowBreakdown,
        val priorityPlan: DebtPriorityEngine.DebtPriorityPlan,
        val estimatedDebtFreeDateString: String?,
        val totalMonths: Int?,
        val isViable: Boolean,
        val financialAdvice: String,
        val recommendations: List<String>
    )

    /**
     * Calculates the comprehensive debt freedom plan.
     *
     * @param monthlyIncome Total reliable monthly income.
     * @param essentialExpenses Crucial living expenses (makan, sewa, listrik, obat).
     * @param emergencyCushion Minimal emergency savings reserve maintained per month.
     * @param debts Active standard debts owned by user.
     * @param installmentPlans Active installment plans owned by user.
     */
    fun calculateFreedomPlan(
        monthlyIncome: Double,
        essentialExpenses: Double,
        emergencyCushion: Double = 0.0,
        debts: List<DebtItem>,
        installmentPlans: List<InstallmentPlanItem> = emptyList()
    ): FreedomPlanResult {
        val sanitizedIncome = monthlyIncome.coerceAtLeast(0.0)
        val sanitizedEssentials = essentialExpenses.coerceAtLeast(0.0)
        val sanitizedCushion = emergencyCushion.coerceAtLeast(0.0)

        // 1. Mandatory base living requirements
        val livingNeeds = sanitizedEssentials + sanitizedCushion
        val uncommittedSurplus = (sanitizedIncome - livingNeeds).coerceAtLeast(0.0)

        // 2. Query engine for mandatory minimums
        val dummyInitialPlan = DebtPriorityEngine.calculatePlan(debts, installmentPlans, 0.0)
        val mandatoryMinimums = dummyInitialPlan.totalMandatoryMinimum

        val isDeficit = uncommittedSurplus < mandatoryMinimums
        val deficitAmount = if (isDeficit) mandatoryMinimums - uncommittedSurplus else 0.0
        val netExtraForDebt = if (!isDeficit) uncommittedSurplus - mandatoryMinimums else 0.0

        val cashflow = CashflowBreakdown(
            monthlyIncome = sanitizedIncome,
            essentialExpenses = sanitizedEssentials,
            emergencyCushion = sanitizedCushion,
            uncommittedSurplus = uncommittedSurplus,
            mandatoryDebtMinimums = mandatoryMinimums,
            netExtraForDebt = netExtraForDebt,
            isDeficit = isDeficit,
            deficitAmount = deficitAmount
        )

        // 3. Calculate full Avalanche priority plan with total available debt budget
        val totalDedicatedDebtBudget = if (!isDeficit) mandatoryMinimums + netExtraForDebt else uncommittedSurplus
        val priorityPlan = DebtPriorityEngine.calculatePlan(
            debts = debts,
            installmentPlans = installmentPlans,
            totalMonthlyBudget = totalDedicatedDebtBudget
        )

        // 4. Compute realistic debt-free calendar date
        val totalMonths = priorityPlan.estimatedMonthsToDebtFree
        val debtFreeDateString = totalMonths?.let { months ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, months)
            val sdf = SimpleDateFormat("MMMM yyyy", LocaleHelper.getActiveLocale())
            sdf.format(cal.time)
        }

        val isViable = !isDeficit && totalMonths != null && totalMonths > 0

        // 5. Generate transparent financial recommendations
        val adviceBuilder = StringBuilder()
        val recommendations = mutableListOf<String>()

        if (sanitizedIncome <= 0.0) {
            adviceBuilder.append("Masukkan data pendapatan bulanan Anda untuk memulai simulasi bebas utang.")
            recommendations.add("Isi estimasi penghasilan bulanan bersih pada kalkulator.")
        } else if (isDeficit) {
            adviceBuilder.append("Arus kas Anda mengalami defisit sebesar ${CurrencyFormatter.formatRupiah(deficitAmount)}/bulan setelah dikurangi kebutuhan pokok. Pendapatan saat ini belum mencukupi untuk memenuhi seluruh cicilan minimum.")
            recommendations.add("Segera hubungi kreditur untuk restrukturisasi tenor atau penurunan suku bunga.")
            recommendations.add("Kurangi pengeluaran sekunder sementara waktu agar kebutuhan pokok tetap terlindungi.")
            recommendations.add("Fokus bayar tagihan dengan jatuh tempo paling mendesak untuk menghindari penalti/denda.")
        } else {
            adviceBuilder.append("Rencana pelunasan realistis! Anda memiliki dana ekstra sebesar ${CurrencyFormatter.formatRupiah(netExtraForDebt)}/bulan setelah mengamankan kebutuhan pokok.")
            if (debtFreeDateString != null) {
                adviceBuilder.append(" Diestimasikan bebas utang pada $debtFreeDateString ($totalMonths bulan).")
            }

            recommendations.add("Pertahankan alokasi kebutuhan pokok sebesar ${CurrencyFormatter.formatRupiah(sanitizedEssentials)} agar kesehatan finansial stabil.")
            if (sanitizedCushion > 0.0) {
                recommendations.add("Dana cadangan darurat ${CurrencyFormatter.formatRupiah(sanitizedCushion)}/bulan tetap disisihkan demi perlindungan kejadian darurat.")
            }
            if (priorityPlan.prioritizedItems.isNotEmpty()) {
                val topTarget = priorityPlan.prioritizedItems.first()
                recommendations.add("Target #1: Pusatkan dana ekstra ${CurrencyFormatter.formatRupiah(netExtraForDebt)} ke '${topTarget.debt.name}'.")
            }
        }

        return FreedomPlanResult(
            cashflow = cashflow,
            priorityPlan = priorityPlan,
            estimatedDebtFreeDateString = debtFreeDateString,
            totalMonths = totalMonths,
            isViable = isViable,
            financialAdvice = adviceBuilder.toString(),
            recommendations = recommendations
        )
    }
}
