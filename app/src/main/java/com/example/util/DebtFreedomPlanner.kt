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
            adviceBuilder.append(AppStrings.t("Masukkan data pendapatan bulanan Anda untuk memulai simulasi bebas utang.", "Enter your monthly income data to start the debt freedom simulation."))
            recommendations.add(AppStrings.t("Isi estimasi penghasilan bulanan bersih pada kalkulator.", "Fill in estimated net monthly income in the calculator."))
        } else if (isDeficit) {
            val defFormatted = CurrencyFormatter.formatRupiah(deficitAmount)
            adviceBuilder.append(AppStrings.t(
                "Arus kas Anda mengalami defisit sebesar $defFormatted/bulan setelah dikurangi kebutuhan pokok. Pendapatan saat ini belum mencukupi untuk memenuhi seluruh cicilan minimum.",
                "Your cashflow has a deficit of $defFormatted/month after basic necessities. Current income is insufficient to cover all minimum installments."
            ))
            recommendations.add(AppStrings.t("Segera hubungi kreditur untuk restrukturisasi tenor atau penurunan suku bunga.", "Contact creditors promptly for tenure restructuring or interest rate reduction."))
            recommendations.add(AppStrings.t("Kurangi pengeluaran sekunder sementara waktu agar kebutuhan pokok tetap terlindungi.", "Cut back on discretionary spending temporarily to protect living essentials."))
            recommendations.add(AppStrings.t("Fokus bayar tagihan dengan jatuh tempo paling mendesak untuk menghindari penalti/denda.", "Prioritize bills with the most urgent due dates to avoid late fees."))
        } else {
            val netExtraFormatted = CurrencyFormatter.formatRupiah(netExtraForDebt)
            adviceBuilder.append(AppStrings.t(
                "Rencana pelunasan realistis! Anda memiliki dana ekstra sebesar $netExtraFormatted/bulan setelah mengamankan kebutuhan pokok.",
                "Realistic payoff plan! You have an extra $netExtraFormatted/month after securing essential needs."
            ))
            if (debtFreeDateString != null) {
                adviceBuilder.append(AppStrings.t(
                    " Diestimasikan bebas utang pada $debtFreeDateString ($totalMonths bulan).",
                    " Estimated debt-free date: $debtFreeDateString ($totalMonths months)."
                ))
            }

            val essFormatted = CurrencyFormatter.formatRupiah(sanitizedEssentials)
            recommendations.add(AppStrings.t(
                "Pertahankan alokasi kebutuhan pokok sebesar $essFormatted agar kesehatan finansial stabil.",
                "Maintain essential living allocation of $essFormatted for financial stability."
            ))
            if (sanitizedCushion > 0.0) {
                val cushionFormatted = CurrencyFormatter.formatRupiah(sanitizedCushion)
                recommendations.add(AppStrings.t(
                    "Dana cadangan darurat $cushionFormatted/bulan tetap disisihkan demi perlindungan kejadian darurat.",
                    "Emergency reserve fund of $cushionFormatted/month set aside for unexpected events."
                ))
            }
            if (priorityPlan.prioritizedItems.isNotEmpty()) {
                val topTarget = priorityPlan.prioritizedItems.first()
                recommendations.add(AppStrings.t(
                    "Target #1: Pusatkan dana ekstra $netExtraFormatted ke '${topTarget.debt.name}'.",
                    "Target #1: Direct extra debt funds of $netExtraFormatted to '${topTarget.debt.name}'."
                ))
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
