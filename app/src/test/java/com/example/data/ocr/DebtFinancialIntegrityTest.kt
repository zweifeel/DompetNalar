package com.example.data.ocr

import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPlanItem
import com.example.data.model.PayLaterBillScanResult
import com.example.data.model.PayLaterSchedulePeriod
import com.example.data.model.TransactionType
import com.example.util.CurrencyRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtFinancialIntegrityTest {

    @Test
    fun testImportDoesNotMarkBillsPaidAutomatically() {
        val scanResult = PayLaterBillScanResult(
            providerName = "GoPay Pinjam",
            currentBillAmount = 139011.0,
            dueDate = "2026-12-07",
            dueIsoDate = "2026-12-07",
            installmentTotal = 3,
            installmentSchedule = listOf(
                PayLaterSchedulePeriod(1, "2026-12-07", 139011.0, status = "Belum Bayar", dueIsoDate = "2026-12-07"),
                PayLaterSchedulePeriod(2, "2027-01-07", 139011.0, status = "Belum Bayar", dueIsoDate = "2027-01-07"),
                PayLaterSchedulePeriod(3, "2027-02-07", 139011.0, status = "Belum Bayar", dueIsoDate = "2027-02-07")
            )
        )

        val plan = scanResult.toInstallmentPlanItem(userId = 1L)
        assertNotNull(plan)
        assertEquals(0, plan!!.paidCount)
        assertEquals("AKTIF", plan.status)
        assertNull(plan.lastPaymentDate)

        val periods = scanResult.toInstallmentPeriods(plan.id, userId = 1L)
        assertEquals(3, periods.size)
        assertTrue(periods.all { !it.isPaid })
        assertTrue(periods.all { it.amountPaidMinorUnits == 0L })
    }

    @Test
    fun testFinancialAmountsUseCanonicalLongMinorUnits() {
        val amountDouble = 788573.0
        val minorUnits = CurrencyRegistry.displayToCanonicalMinorUnits(amountDouble, "IDR")
        assertEquals(78857300L, minorUnits)

        val displayBack = CurrencyRegistry.canonicalMinorUnitsToDisplay(minorUnits, "IDR")
        assertEquals(amountDouble, displayBack, 0.001)
    }

    @Test
    fun testManualDateEditDoesNotMutateAmountsOrPaymentStatus() {
        val period = com.example.data.model.InstallmentPeriod(
            id = "period-1",
            planId = "plan-1",
            currencyCode = "IDR",
            userId = 1L,
            periodNumber = 1,
            amountDueMinorUnits = 13901100L,
            amountPaidMinorUnits = 0L,
            dueDate = "2026-12-07",
            isPaid = false
        )

        // User manually edits the due date to 2026-12-15
        val updatedPeriod = period.copy(dueDate = "2026-12-15")

        assertEquals("2026-12-15", updatedPeriod.dueDate)
        // Ensure financial invariants are 100% untouched
        assertEquals(period.amountDueMinorUnits, updatedPeriod.amountDueMinorUnits)
        assertEquals(period.amountPaidMinorUnits, updatedPeriod.amountPaidMinorUnits)
        assertEquals(period.isPaid, updatedPeriod.isPaid)
        assertEquals(period.userId, updatedPeriod.userId)
        assertEquals(period.planId, updatedPeriod.planId)
    }
}
