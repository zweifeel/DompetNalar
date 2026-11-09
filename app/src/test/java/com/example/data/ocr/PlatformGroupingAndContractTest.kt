package com.example.data.ocr

import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPlanItem
import com.example.util.PayLaterDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformGroupingAndContractTest {

    @Test
    fun testPlatformNormalization_CanonicalMapping() {
        // GoPay Pinjam
        assertEquals("GoPay Pinjam", PayLaterDeduplicator.normalizePlatformName("gopay pinjam"))
        assertEquals("GoPay Pinjam", PayLaterDeduplicator.normalizePlatformName("GoPayPinjam"))
        assertEquals("GoPay Pinjam", PayLaterDeduplicator.normalizePlatformName("pinjam gopay"))

        // DANA Cicil
        assertEquals("DANA Cicil", PayLaterDeduplicator.normalizePlatformName("dana cicil"))
        assertEquals("DANA Cicil", PayLaterDeduplicator.normalizePlatformName("danacicil"))
        assertEquals("DANA Cicil", PayLaterDeduplicator.normalizePlatformName("dana paylater"))

        // Shopee PayLater
        assertEquals("Shopee PayLater", PayLaterDeduplicator.normalizePlatformName("spaylater"))
        assertEquals("Shopee PayLater", PayLaterDeduplicator.normalizePlatformName("Shopee PayLater"))
        assertEquals("Shopee PayLater", PayLaterDeduplicator.normalizePlatformName("shopee pay later"))

        // Other providers
        assertEquals("Kredivo", PayLaterDeduplicator.normalizePlatformName("kredivo"))
        assertEquals("Akulaku", PayLaterDeduplicator.normalizePlatformName("aku laku"))
        assertEquals("AdaKami", PayLaterDeduplicator.normalizePlatformName("adakami"))
    }

    @Test
    fun testSamePlatformEnteredAtDifferentTimes_GroupsUnderSamePlatformWithoutMergingContracts() {
        val userDebts = listOf(
            DebtItem(
                id = "loan-1",
                name = "GoPay Pinjam - Pinjaman 1",
                amount = 139011.0,
                interestRatePercent = 0.0,
                minMonthlyPayment = 139011.0,
                dueDate = "2026-12-07",
                userId = 1L
            ),
            DebtItem(
                id = "loan-2",
                name = "GoPay Pinjam - Pinjaman 2",
                amount = 788573.0,
                interestRatePercent = 0.0,
                minMonthlyPayment = 788573.0,
                dueDate = "2026-12-10",
                userId = 1L
            )
        )

        // Group by canonical platform
        val grouped = userDebts.groupBy { PayLaterDeduplicator.normalizePlatformName(it.name) }
        assertEquals(1, grouped.keys.size)
        assertTrue(grouped.containsKey("GoPay Pinjam"))

        val contracts = grouped["GoPay Pinjam"]!!
        assertEquals(2, contracts.size)

        // Each contract retains its own distinct identity, amount, and due date
        assertEquals("loan-1", contracts[0].id)
        assertEquals(139011.0, contracts[0].amount, 0.01)
        assertEquals("2026-12-07", contracts[0].dueDate)

        assertEquals("loan-2", contracts[1].id)
        assertEquals(788573.0, contracts[1].amount, 0.01)
        assertEquals("2026-12-10", contracts[1].dueDate)
    }

    @Test
    fun testMultiUserIsolation_NoCrossUserMatchingOrDeduplication() {
        val user1Debts = listOf(
            DebtItem(
                id = "user1-debt",
                name = "GoPay Pinjam",
                amount = 500000.0,
                interestRatePercent = 0.0,
                minMonthlyPayment = 500000.0,
                dueDate = "2026-12-07",
                userId = 1L
            )
        )

        // Candidate belonging to user 2 with identical amount and date
        val checkUser2 = PayLaterDeduplicator.checkDuplicate(
            targetUserId = 2L,
            userDebts = user1Debts,
            providerName = "GoPay Pinjam",
            currentBillAmount = 500000.0,
            totalRemainingAmount = 500000.0,
            dueDateStr = "2026-12-07"
        )
        assertFalse("Debts belonging to User 1 must not trigger duplicate match for User 2", checkUser2.isDuplicate)

        // Candidate belonging to user 1 SHOULD trigger duplicate match
        val checkUser1 = PayLaterDeduplicator.checkDuplicate(
            targetUserId = 1L,
            userDebts = user1Debts,
            providerName = "GoPay Pinjam",
            currentBillAmount = 500000.0,
            totalRemainingAmount = 500000.0,
            dueDateStr = "2026-12-07"
        )
        assertTrue("Candidate for User 1 must match existing debt of User 1", checkUser1.isDuplicate)
    }
}
