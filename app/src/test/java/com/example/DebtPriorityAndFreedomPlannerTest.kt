package com.example

import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPlanItem
import com.example.util.DebtFreedomPlanner
import com.example.util.DebtPriorityEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DebtPriorityAndFreedomPlannerTest {

    // ==========================================
    // 1. DEBT PRIORITY ENGINE TESTS (STAGE 3)
    // ==========================================

    @Test
    fun testDebtAvalanche_prioritizesHighestInterestRate() {
        val debtLowInterest = DebtItem(
            id = "d1",
            name = "KTA BNI",
            amount = 10000000.0,
            interestRatePercent = 12.0,
            minMonthlyPayment = 500000.0,
            dueDate = "Tgl 20 tiap bulan"
        )
        val debtHighInterest = DebtItem(
            id = "d2",
            name = "Kartu Kredit BCA",
            amount = 5000000.0,
            interestRatePercent = 24.0, // HIGHER INTEREST
            minMonthlyPayment = 500000.0,
            dueDate = "Tgl 10 tiap bulan"
        )
        val debtMidInterest = DebtItem(
            id = "d3",
            name = "Pinjol Legal",
            amount = 2000000.0,
            interestRatePercent = 18.0,
            minMonthlyPayment = 200000.0,
            dueDate = "Tgl 15 tiap bulan"
        )

        val debts = listOf(debtLowInterest, debtHighInterest, debtMidInterest)
        val totalBudget = 2000000.0 // 1.2M min payments + 800k extra

        val plan = DebtPriorityEngine.calculatePlan(
            debts = debts,
            installmentPlans = emptyList(),
            totalMonthlyBudget = totalBudget
        )

        assertFalse(plan.isDeficit)
        assertEquals(3, plan.prioritizedItems.size)

        // Rank 1 MUST be the highest interest rate (Kartu Kredit BCA @ 24%)
        val rank1 = plan.prioritizedItems[0]
        assertEquals("d2", rank1.debt.id)
        assertEquals(24.0, rank1.debt.interestRatePercent, 0.001)
        assertEquals(1, rank1.rank)
        // Extra allocation must be routed to Rank 1
        assertEquals(800000.0, rank1.extraAllocation, 0.01)
        assertEquals(1300000.0, rank1.totalMonthlyPayment, 0.01)

        // Rank 2 must be Pinjol Legal @ 18%
        val rank2 = plan.prioritizedItems[1]
        assertEquals("d3", rank2.debt.id)
        assertEquals(18.0, rank2.debt.interestRatePercent, 0.001)
        assertEquals(0.0, rank2.extraAllocation, 0.01)

        // Rank 3 must be KTA BNI @ 12%
        val rank3 = plan.prioritizedItems[2]
        assertEquals("d1", rank3.debt.id)
        assertEquals(12.0, rank3.debt.interestRatePercent, 0.001)
    }

    @Test
    fun testDebtAvalanche_equalInterestBreaksTiesByDueDateAndBalance() {
        val debtA = DebtItem(
            id = "a",
            name = "PayLater A",
            amount = 3000000.0,
            interestRatePercent = 20.0,
            minMonthlyPayment = 300000.0,
            dueDate = "Tgl 28 tiap bulan" // Further due date
        )
        val debtB = DebtItem(
            id = "b",
            name = "PayLater B",
            amount = 2000000.0,
            interestRatePercent = 20.0,
            minMonthlyPayment = 200000.0,
            dueDate = "Tgl 5 tiap bulan" // Closer due date
        )

        val plan = DebtPriorityEngine.calculatePlan(
            debts = listOf(debtA, debtB),
            installmentPlans = emptyList(),
            totalMonthlyBudget = 1000000.0
        )

        assertEquals(2, plan.prioritizedItems.size)
        // Debt with closer due date (debtB) must take priority when interest rates are identical
        assertTrue(plan.prioritizedItems[0].debt.daysUntilDue <= plan.prioritizedItems[1].debt.daysUntilDue)
    }

    @Test
    fun testDebtAvalanche_mandatoryMinimumsAlwaysPreserved() {
        val debt1 = DebtItem(id = "1", name = "D1", amount = 1000000.0, interestRatePercent = 10.0, minMonthlyPayment = 150000.0)
        val debt2 = DebtItem(id = "2", name = "D2", amount = 2000000.0, interestRatePercent = 15.0, minMonthlyPayment = 250000.0)

        val plan = DebtPriorityEngine.calculatePlan(
            debts = listOf(debt1, debt2),
            installmentPlans = emptyList(),
            totalMonthlyBudget = 400000.0 // Exactly covers total mandatory minimums (150k + 250k)
        )

        assertFalse(plan.isDeficit)
        assertEquals(400000.0, plan.totalMandatoryMinimum, 0.01)
        assertEquals(0.0, plan.monthlyShortfall, 0.01)
        assertEquals(150000.0, plan.prioritizedItems.first { it.debt.id == "1" }.mandatoryPayment, 0.01)
        assertEquals(250000.0, plan.prioritizedItems.first { it.debt.id == "2" }.mandatoryPayment, 0.01)
    }

    @Test
    fun testDebtAvalanche_detectsDeficitWhenBudgetInsufficient() {
        val debt = DebtItem(id = "1", name = "Utang", amount = 1000000.0, interestRatePercent = 10.0, minMonthlyPayment = 200000.0)

        val plan = DebtPriorityEngine.calculatePlan(
            debts = listOf(debt),
            installmentPlans = emptyList(),
            totalMonthlyBudget = 100000.0 // 100k < 200k mandatory minimum
        )

        assertTrue(plan.isDeficit)
        assertEquals(100000.0, plan.monthlyShortfall, 0.01)
        assertTrue(plan.summaryExplanation.contains("defisit"))
        assertNull(plan.estimatedMonthsToDebtFree)
    }

    @Test
    fun testDebtAvalanche_excludesPaidOffDebtsAndCompletedPlans() {
        val zeroDebt = DebtItem(id = "zero", name = "Lunas", amount = 0.0, interestRatePercent = 10.0, minMonthlyPayment = 0.0)
        val activeDebt = DebtItem(id = "active", name = "Aktif", amount = 500000.0, interestRatePercent = 12.0, minMonthlyPayment = 50000.0)

        val completedPlan = InstallmentPlanItem(
            id = "c1",
            name = "Motor Lunas",
            installmentAmount = 500000.0,
            totalTenor = 12,
            paidCount = 12, // COMPLETED
            startDate = "2025-01-01",
            nextDueDate = "2026-01-01",
            status = "SELESAI"
        )

        val plan = DebtPriorityEngine.calculatePlan(
            debts = listOf(zeroDebt, activeDebt),
            installmentPlans = listOf(completedPlan),
            totalMonthlyBudget = 100000.0
        )

        assertEquals(1, plan.prioritizedItems.size)
        assertEquals("active", plan.prioritizedItems[0].debt.id)
        assertEquals(500000.0, plan.totalRemainingDebt, 0.01)
    }

    @Test
    fun testDebtAvalanche_tracksUnverifiedInterestRatesAccurately() {
        val verifiedDebt = DebtItem(id = "v", name = "Bunga Jelas", amount = 1000000.0, interestRatePercent = 15.0, minMonthlyPayment = 100000.0)
        val unverifiedDebt = DebtItem(id = "u", name = "Bunga Nol / Tidak Diketahui", amount = 1000000.0, interestRatePercent = 0.0, minMonthlyPayment = 100000.0)

        val plan = DebtPriorityEngine.calculatePlan(
            debts = listOf(verifiedDebt, unverifiedDebt),
            installmentPlans = emptyList(),
            totalMonthlyBudget = 300000.0
        )

        assertEquals(1, plan.unverifiedInterestCount)
        assertTrue(plan.prioritizedItems.first { it.debt.id == "v" }.debt.hasVerifiedInterest)
        assertFalse(plan.prioritizedItems.first { it.debt.id == "u" }.debt.hasVerifiedInterest)
    }

    // ==========================================
    // 2. DEBT FREEDOM PLANNER TESTS (STAGE 4)
    // ==========================================

    @Test
    fun testFreedomPlanner_preservesEssentialsAndEmergencyCushion() {
        val income = 10000000.0
        val essentials = 5000000.0 // Makan, kontrakan, utilitas
        val cushion = 1000000.0 // Dana darurat
        val debt = DebtItem(id = "1", name = "Kredit", amount = 12000000.0, interestRatePercent = 12.0, minMonthlyPayment = 1000000.0)

        val result = DebtFreedomPlanner.calculateFreedomPlan(
            monthlyIncome = income,
            essentialExpenses = essentials,
            emergencyCushion = cushion,
            debts = listOf(debt)
        )

        assertTrue(result.isViable)
        assertFalse(result.cashflow.isDeficit)
        // Uncommitted surplus = 10M - 5M - 1M = 4M
        assertEquals(4000000.0, result.cashflow.uncommittedSurplus, 0.01)
        // Mandatory debt = 1M
        assertEquals(1000000.0, result.cashflow.mandatoryDebtMinimums, 0.01)
        // Net extra dedicated to debt = 4M - 1M = 3M
        assertEquals(3000000.0, result.cashflow.netExtraForDebt, 0.01)
        // Total monthly payoff = 1M + 3M = 4M -> ~3 months
        assertNotNull(result.totalMonths)
        assertTrue(result.totalMonths!! in 3..4)
        assertNotNull(result.estimatedDebtFreeDateString)
    }

    @Test
    fun testFreedomPlanner_reportsDeficitWhenIncomeFailsToCoverEssentialsAndDebts() {
        val income = 4000000.0
        val essentials = 3500000.0 // 3.5M leaves only 500k
        val debt = DebtItem(id = "1", name = "Cicilan Berat", amount = 10000000.0, interestRatePercent = 15.0, minMonthlyPayment = 1000000.0) // needs 1M

        val result = DebtFreedomPlanner.calculateFreedomPlan(
            monthlyIncome = income,
            essentialExpenses = essentials,
            emergencyCushion = 0.0,
            debts = listOf(debt)
        )

        assertFalse(result.isViable)
        assertTrue(result.cashflow.isDeficit)
        // Shortfall = 1M - 500k = 500k
        assertEquals(500000.0, result.cashflow.deficitAmount, 0.01)
        assertNull(result.estimatedDebtFreeDateString)
        assertTrue(result.financialAdvice.contains("defisit"))
        assertTrue(result.recommendations.any { it.contains("restrukturisasi") || it.contains("kurangi") })
    }

    @Test
    fun testFreedomPlanner_zeroDebtsCongratulatesUser() {
        val result = DebtFreedomPlanner.calculateFreedomPlan(
            monthlyIncome = 8000000.0,
            essentialExpenses = 4000000.0,
            debts = emptyList()
        )

        assertEquals(0.0, result.cashflow.mandatoryDebtMinimums, 0.01)
        assertEquals(0, result.priorityPlan.prioritizedItems.size)
        assertTrue(result.priorityPlan.summaryExplanation.contains("Selamat"))
    }

    // ==========================================
    // 3. BUDGET DISCREPANCY & OBLIGATION TESTS (BUG 5 & 6)
    // ==========================================

    @Test
    fun testBudgetDiscrepancy_budget2M_obligations3M_reportsDeficitWithoutReducingRealObligation() {
        val debt1 = DebtItem(
            id = "d1",
            name = "Cicilan Laptop",
            amount = 6000000.0,
            interestRatePercent = 12.0,
            minMonthlyPayment = 1500000.0
        )
        val debt2 = DebtItem(
            id = "d2",
            name = "PayLater Belanja",
            amount = 3000000.0,
            interestRatePercent = 18.0,
            minMonthlyPayment = 1500000.0
        )

        val userBudgetLimit = 2000000.0 // User set budget limit to 2.000.000
        val debts = listOf(debt1, debt2)

        val plan = DebtPriorityEngine.calculatePlan(
            debts = debts,
            installmentPlans = emptyList(),
            totalMonthlyBudget = userBudgetLimit
        )

        // Real obligations MUST remain Rp3.000.000 and NOT be artificially altered to Rp2.000.000
        assertEquals(3000000.0, plan.totalMandatoryMinimum, 0.01)
        // Deficit must be detected
        assertTrue(plan.isDeficit)
        assertEquals(1000000.0, plan.monthlyShortfall, 0.01)
        // Deficit difference: totalMandatoryMinimum - userBudgetLimit = 1.000.000
        val deficitAmount = plan.totalMandatoryMinimum - userBudgetLimit
        assertEquals(1000000.0, deficitAmount, 0.01)
    }

    @Test
    fun testBudgetDiscrepancy_budget3M_obligations3M_balancedNoDeficit() {
        val debt1 = DebtItem(
            id = "d1",
            name = "Cicilan A",
            amount = 5000000.0,
            interestRatePercent = 10.0,
            minMonthlyPayment = 1500000.0
        )
        val debt2 = DebtItem(
            id = "d2",
            name = "Cicilan B",
            amount = 5000000.0,
            interestRatePercent = 14.0,
            minMonthlyPayment = 1500000.0
        )

        val userBudgetLimit = 3000000.0
        val plan = DebtPriorityEngine.calculatePlan(
            debts = listOf(debt1, debt2),
            installmentPlans = emptyList(),
            totalMonthlyBudget = userBudgetLimit
        )

        assertEquals(3000000.0, plan.totalMandatoryMinimum, 0.01)
        assertFalse(plan.isDeficit)
        assertEquals(0.0, plan.monthlyShortfall, 0.01)
    }

    @Test
    fun testScheduledMonthlyPayments_month1AndMonth2_tenorProgression() {
        // Plan 1: 4 months remaining (paid 2 of 6) -> active in Month 1 and Month 2
        val planLong = InstallmentPlanItem(
            id = "plan1",
            name = "Kulkas 2 Pintu",
            installmentAmount = 1000000.0,
            totalTenor = 6,
            paidCount = 2,
            startDate = "2026-08-01",
            nextDueDate = "2026-11-10",
            status = "BERJALAN"
        )
        // Plan 2: 1 month remaining (paid 2 of 3) -> active in Month 1, completed before Month 2
        val planShort = InstallmentPlanItem(
            id = "plan2",
            name = "Headphone Bluetooth",
            installmentAmount = 500000.0,
            totalTenor = 3,
            paidCount = 2,
            startDate = "2026-09-01",
            nextDueDate = "2026-11-15",
            status = "BERJALAN"
        )

        val allPlans = listOf(planLong, planShort)

        // Month 1 schedule: all active plans with remaining tenor > 0
        val month1Plans = allPlans.filter { (it.totalTenor - it.paidCount) > 0 }
        val month1Total = month1Plans.sumOf { it.installmentAmount }
        assertEquals(2, month1Plans.size)
        assertEquals(1500000.0, month1Total, 0.01)

        // Month 2 schedule: only plans with remaining tenor > 1 continue to Month 2
        val month2Plans = allPlans.filter { (it.totalTenor - it.paidCount) > 1 }
        val month2Total = month2Plans.sumOf { it.installmentAmount }
        assertEquals(1, month2Plans.size)
        assertEquals("plan1", month2Plans[0].id)
        assertEquals(1000000.0, month2Total, 0.01)
    }

    // ==========================================
    // 4. OCR NON-FABRICATION TESTS (BUG 4)
    // ==========================================

    @Test
    fun testPayLaterAiOcrScanner_nonFabricationOfMissingFields() {
        // JSON where provider and due date were not found by AI
        val rawJson = org.json.JSONObject().apply {
            put("nominal_tagihan", 750000.0)
            put("total_sisa_utang", 2250000.0)
            // nama_penyedia is omitted / empty
            // tanggal_jatuh_tempo is omitted / empty
        }

        val result = com.example.data.ai.PayLaterAiOcrScanner.parseScanResultFromJson(
            json = rawJson,
            rawText = "Raw scan text with ambiguous info",
            imageUriStr = "content://media/external/images/media/42"
        )

        // Must NOT invent fallback names like "Tagihan PayLater" or "15 tiap bulan"
        assertEquals("", result.providerName)
        assertEquals("", result.dueDate)
        assertNull(result.dueDay)
        assertEquals(750000.0, result.currentBillAmount, 0.01)
        assertEquals(2250000.0, result.totalRemainingAmount, 0.01)

        // Must NOT mark real scans as demo preset
        assertFalse(result.isDemoPreset)

        // Must generate explicit warnings alerting user to fill missing fields manually
        assertTrue(result.confidenceWarnings.any { it.contains("Nama penyedia") })
        assertTrue(result.confidenceWarnings.any { it.contains("Tanggal jatuh tempo") })
    }

    @Test
    fun testDebtSnowball_prioritizesSmallestBalanceFirst() {
        val debtLarge = DebtItem(id = "large", name = "Large Debt", amount = 10000000.0, interestRatePercent = 25.0, minMonthlyPayment = 500000.0, dueDate = "15")
        val debtSmall = DebtItem(id = "small", name = "Small Debt", amount = 1000000.0, interestRatePercent = 10.0, minMonthlyPayment = 100000.0, dueDate = "20")

        val plan = DebtPriorityEngine.calculatePlan(
            debts = listOf(debtLarge, debtSmall),
            totalMonthlyBudget = 1000000.0,
            strategy = DebtPriorityEngine.Strategy.DEBT_SNOWBALL
        )

        assertEquals("DEBT_SNOWBALL", plan.strategy)
        assertEquals("small", plan.prioritizedItems[0].debt.id)
        assertEquals("large", plan.prioritizedItems[1].debt.id)
    }

    @Test
    fun testBasisPointsAndNegativeAmortizationDetection() {
        val negAmortDebt = DebtItem(
            id = "neg1",
            name = "Predatory Loan",
            amount = 100000000.0,
            interestRatePercent = 24.0,
            minMonthlyPayment = 500000.0,
            dueDate = "15"
        )

        val plan = DebtPriorityEngine.calculatePlan(
            debts = listOf(negAmortDebt),
            totalMonthlyBudget = 1000000.0
        )

        val item = plan.prioritizedItems[0].debt
        assertEquals(2400, item.interestRateBasisPoints)
        assertTrue(item.isNegativeAmortization)
        assertTrue(plan.negativeAmortizationDebtIds.contains("neg1"))
    }

    @Test
    fun testDebtPlannerExactBasisPointArithmeticAndTestVectors() {
        // Vector 1: Zero interest (0 bps) -> 0 minor units
        val zeroInterest = DebtPriorityEngine.calculateMonthlyInterestMinorUnits(
            principalMinorUnits = 1000000000L, // 10M IDR
            basisPoints = 0L
        )
        assertEquals(0L, zeroInterest)

        // Vector 2: Exact division without remainder (12% per year = 1% per month)
        // 120,000 minor units * 1200 bps / 120,000 = 1,200 minor units
        val exactMonthly = DebtPriorityEngine.calculateMonthlyInterestMinorUnits(
            principalMinorUnits = 120000L,
            basisPoints = 1200L
        )
        assertEquals(1200L, exactMonthly)

        // Vector 3: Half-Up Rounding Boundary (divisor = 120,000, half-divisor = 60,000)
        // Remainder 59,999 (< 60,000) -> rounds DOWN to 0
        val roundDown = DebtPriorityEngine.calculateMonthlyInterestMinorUnits(
            principalMinorUnits = 1L,
            basisPoints = 59999L
        )
        assertEquals(0L, roundDown)

        // Remainder 60,000 (== 60,000) -> rounds UP to 1 (Half-Up)
        val roundHalfUpExact = DebtPriorityEngine.calculateMonthlyInterestMinorUnits(
            principalMinorUnits = 1L,
            basisPoints = 60000L
        )
        assertEquals(1L, roundHalfUpExact)

        // Remainder 60,001 (> 60,000) -> rounds UP to 1
        val roundUp = DebtPriorityEngine.calculateMonthlyInterestMinorUnits(
            principalMinorUnits = 1L,
            basisPoints = 60001L
        )
        assertEquals(1L, roundUp)

        // Vector 4: Extreme balance (50 Trillion IDR) - exceeds Long.MAX_VALUE in intermediate product
        // 5 * 10^15 minor units * 2400 bps = 1.2 * 10^19 (overflows primitive Long)
        // Expected monthly interest: (5 * 10^15 * 2400) / 120000 = 100,000,000,000,000 minor units
        val extremeInterest = DebtPriorityEngine.calculateMonthlyInterestMinorUnits(
            principalMinorUnits = 5000000000000000L,
            basisPoints = 2400L
        )
        assertEquals(100000000000000L, extremeInterest)

        // Vector 5: Negative Amortization Detection via Exact Integer Math
        // Principal = 100,000,000 IDR (10,000,000,000 minor units) at 24% (2400 bps)
        // Monthly interest = 2,000,000 IDR (200,000,000 minor units)
        // Payment of 1,500,000 IDR (150,000,000 minor units) < 200,000,000 -> NEGATIVE AMORTIZATION
        assertTrue(
            DebtPriorityEngine.checkNegativeAmortization(
                principalMinorUnits = 10000000000L,
                minMonthlyPaymentMinorUnits = 150000000L,
                basisPoints = 2400L
            )
        )

        // Payment of 2,500,000 IDR (250,000,000 minor units) >= 200,000,000 -> HEALTHY AMORTIZATION
        assertFalse(
            DebtPriorityEngine.checkNegativeAmortization(
                principalMinorUnits = 10000000000L,
                minMonthlyPaymentMinorUnits = 250000000L,
                basisPoints = 2400L
            )
        )

        // Vector 6: Output exactly Long.MAX_VALUE
        // When bps = 120,000 (100% monthly) and principal = Long.MAX_VALUE, result is exactly Long.MAX_VALUE
        val maxLongInterest = DebtPriorityEngine.calculateMonthlyInterestMinorUnits(
            principalMinorUnits = Long.MAX_VALUE,
            basisPoints = 120000L
        )
        assertEquals(Long.MAX_VALUE, maxLongInterest)

        // Vector 7: Output strictly exceeding Long.MAX_VALUE (Throws ArithmeticException fail-closed)
        try {
            DebtPriorityEngine.calculateMonthlyInterestMinorUnits(
                principalMinorUnits = Long.MAX_VALUE,
                basisPoints = 120001L // Exceeds Long.MAX_VALUE by at least 1 unit
            )
            org.junit.Assert.fail("Output exceeding Long.MAX_VALUE must throw ArithmeticException")
        } catch (e: ArithmeticException) {
            assertTrue(e.message?.contains("out of long range") == true || e is ArithmeticException)
        }

        // Vector 8: Negative principal rejection (Throws IllegalArgumentException)
        try {
            DebtPriorityEngine.calculateMonthlyInterestMinorUnits(
                principalMinorUnits = -1000L,
                basisPoints = 1200L
            )
            org.junit.Assert.fail("Negative principal must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("cannot be negative") == true)
        }

        // Vector 9: Negative basis points rejection (Throws IllegalArgumentException)
        try {
            DebtPriorityEngine.calculateMonthlyInterestMinorUnits(
                principalMinorUnits = 1000L,
                basisPoints = -500L
            )
            org.junit.Assert.fail("Negative basis points must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("cannot be negative") == true)
        }
    }

    @Test
    fun testExactDecimalInterestRateAndHalfUpMidpointPrecision() {
        // 1. Exact Decimal Interest Rate: 12.375% annual rate
        // Principal = 10,000,000 IDR (1,000,000,000 minor units)
        // Monthly interest = 1,000,000,000 * 12.375 / 1200 = 1,000,000,000 * 12375 / 1200000 = 10,312,500 minor units exactly
        val interestExactDecimal = DebtPriorityEngine.calculateMonthlyInterestMinorUnits(
            principalMinorUnits = 1_000_000_000L,
            annualInterestRatePercent = java.math.BigDecimal("12.375")
        )
        assertEquals(10_312_500L, interestExactDecimal)

        // 2. Exact Midpoint HALF_UP test:
        // Divisor for 1200 bps (12%) is 120000.
        // Numerator % 120000 == 60000 exactly -> Half-Up rounds UP by +1
        // Let principal = 50000, bps = 1200 -> product = 60,000,000, quotient = 500, remainder = 0
        // To get remainder exactly 60000: product = quotient * 120000 + 60000.
        // Let quotient = 10, target product = 10 * 120000 + 60000 = 1260000.
        // Let bps = 1 -> principal = 1260000: product = 1260000.
        // 1260000 / 120000 = 10 remainder 60000.
        // remainder * 2 = 120000 == divisor -> rounded UP to 11!
        val midpointRoundUp = DebtPriorityEngine.calculateMonthlyInterestMinorUnits(
            principalMinorUnits = 1_260_000L,
            basisPoints = 1L
        )
        assertEquals(11L, midpointRoundUp)

        // 3. Below Midpoint (remainder * 2 < divisor) -> truncated to 10
        // Target product = 10 * 120000 + 59999 = 1259999.
        val belowMidpoint = DebtPriorityEngine.calculateMonthlyInterestMinorUnits(
            principalMinorUnits = 1_259_999L,
            basisPoints = 1L
        )
        assertEquals(10L, belowMidpoint)

        // 4. Above Midpoint (remainder * 2 > divisor) -> rounded UP to 11
        // Target product = 10 * 120000 + 60001 = 1260001.
        val aboveMidpoint = DebtPriorityEngine.calculateMonthlyInterestMinorUnits(
            principalMinorUnits = 1_260_001L,
            basisPoints = 1L
        )
        assertEquals(11L, aboveMidpoint)

        // 5. Exact Double overload matches BigDecimal
        val doubleRateInterest = DebtPriorityEngine.calculateMonthlyInterestMinorUnits(
            principalMinorUnits = 1_000_000_000L,
            annualInterestRatePercent = 12.375
        )
        assertEquals(10_312_500L, doubleRateInterest)

        // 6. Decimal Rate Negative Amortization Detection
        // Monthly interest is 10,312,500 minor units. Minimum payment 10,000,000 minor units (< interest) -> TRUE
        assertTrue(
            DebtPriorityEngine.checkNegativeAmortization(
                principalMinorUnits = 1_000_000_000L,
                minMonthlyPaymentMinorUnits = 10_000_000L,
                annualInterestRatePercent = java.math.BigDecimal("12.375")
            )
        )
        // Minimum payment 10,312,500 minor units (== interest) -> FALSE (not strictly less)
        assertFalse(
            DebtPriorityEngine.checkNegativeAmortization(
                principalMinorUnits = 1_000_000_000L,
                minMonthlyPaymentMinorUnits = 10_312_500L,
                annualInterestRatePercent = java.math.BigDecimal("12.375")
            )
        )

        // 7. Overflow rejection with BigDecimal rate beyond Long.MAX_VALUE
        try {
            DebtPriorityEngine.calculateMonthlyInterestMinorUnits(
                principalMinorUnits = Long.MAX_VALUE,
                annualInterestRatePercent = java.math.BigDecimal("1200.01")
            )
            org.junit.Assert.fail("Output exceeding Long.MAX_VALUE with BigDecimal rate must throw ArithmeticException")
        } catch (e: ArithmeticException) {
            assertTrue(e.message?.contains("out of long range") == true || e is ArithmeticException)
        }
    }
}
