package com.example.data.chatbot

import com.example.data.chatbot.parser.FinancialQueryEngine
import com.example.data.chatbot.parser.LocalFinancialIntentParser
import com.example.data.model.BudgetLimitItem
import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPlanItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialAnalysisUpgradeTest {

    private val testWallets = listOf(
        Wallet(id = 1, name = "BCA", balance = 10_000_000.0, iconName = "account_balance", colorHex = "#005696", type = WalletType.BANK),
        Wallet(id = 2, name = "GoPay", balance = 500_000.0, iconName = "account_balance_wallet", colorHex = "#00AED6", type = WalletType.EWALLET),
        Wallet(id = 3, name = "Bibit Reksadana", balance = 15_000_000.0, iconName = "trending_up", colorHex = "#4CAF50", type = WalletType.INVESTMENT)
    )

    private val now = System.currentTimeMillis()
    private val yesterday = now - 86400000L

    private val testTransactions = listOf(
        TransactionItem(id = 1, title = "Gaji Bulanan", amount = 12_000_000.0, type = TransactionType.INCOME, category = "Gaji Pokok", walletId = 1, walletName = "BCA", timestamp = yesterday),
        TransactionItem(id = 2, title = "Belanja Bulanan Supermarket", amount = 1_500_000.0, type = TransactionType.EXPENSE, category = "Makanan & Minuman", walletId = 1, walletName = "BCA", timestamp = now),
        TransactionItem(id = 3, title = "Ojek Online", amount = 300_000.0, type = TransactionType.EXPENSE, category = "Transportasi", walletId = 2, walletName = "GoPay", timestamp = now),
        TransactionItem(id = 4, title = "Makan Siang", amount = 200_000.0, type = TransactionType.EXPENSE, category = "Makanan & Minuman", walletId = 1, walletName = "BCA", timestamp = now)
    )

    private val testBudgets = listOf(
        BudgetLimitItem(id = "Makanan & Minuman", limitAmount = 2_000_000.0, isCategory = true),
        BudgetLimitItem(id = "Transportasi", limitAmount = 500_000.0, isCategory = true),
        BudgetLimitItem(id = "total_monthly", limitAmount = 5_000_000.0, isCategory = false)
    )

    private val testDebts = listOf(
        DebtItem(
            id = "debt-1",
            name = "KTA Bank Mandiri",
            amount = 5_000_000.0,
            interestRatePercent = 0.99,
            minMonthlyPayment = 500_000.0,
            dueDate = "05 tiap bulan"
        )
    )

    private val testInstallmentPlans = listOf(
        InstallmentPlanItem(
            id = "plan-1",
            name = "Cicilan Laptop ShopeePayLater",
            provider = "ShopeePayLater",
            installmentAmount = 1_000_000.0,
            totalTenor = 6,
            paidCount = 2,
            startDate = "2026-08-01",
            nextDueDate = "2026-11-05",
            anchorDay = 5,
            totalRemainingPrincipal = 4_000_000.0,
            status = "AKTIF"
        )
    )

    // ==========================================
    // 1. All 17 Deterministic Query Topics
    // ==========================================

    @Test
    fun testAll17QueryTopics_ExecuteWithoutCrashAndProduceStructuredOutput() {
        val topics = LocalFinancialIntentParser.QueryTopic.values()
        assertEquals(17, topics.size)

        for (topic in topics) {
            val response = FinancialQueryEngine.executeQuery(
                topic = topic,
                queriedWallet = testWallets[0],
                wallets = testWallets,
                transactions = testTransactions,
                budgetLimits = testBudgets,
                debts = testDebts,
                installmentPlans = testInstallmentPlans
            )

            assertNotNull("Response for topic $topic must not be null", response)
            assertTrue("Response for topic $topic must not be blank", response.isNotBlank())
            assertFalse("Response for topic $topic must not contain Exception", response.contains("Exception"))
        }
    }

    // ==========================================
    // 2. Specific Topic Assertions
    // ==========================================

    @Test
    fun testTopic_UpcomingBills() {
        val response = FinancialQueryEngine.executeQuery(
            topic = LocalFinancialIntentParser.QueryTopic.UPCOMING_BILLS,
            queriedWallet = null,
            wallets = testWallets,
            transactions = testTransactions,
            budgetLimits = testBudgets,
            debts = testDebts,
            installmentPlans = testInstallmentPlans
        )

        // Must find both Mandiri and ShopeePayLater
        assertTrue("Harus memuat cicilan ShopeePayLater", response.contains("ShopeePayLater", ignoreCase = true))
        assertTrue("Harus memuat KTA Mandiri", response.contains("Mandiri", ignoreCase = true))
    }

    @Test
    fun testTopic_NetWorth_ProperCalculation() {
        val response = FinancialQueryEngine.executeQuery(
            topic = LocalFinancialIntentParser.QueryTopic.NET_WORTH,
            queriedWallet = null,
            wallets = testWallets,
            transactions = testTransactions,
            budgetLimits = testBudgets,
            debts = testDebts,
            installmentPlans = testInstallmentPlans
        )

        assertTrue("Harus menghitung kekayaan bersih", response.contains("Kekayaan Bersih") || response.contains("Net Worth"))
    }

    @Test
    fun testTopic_DailySafeSpending() {
        val response = FinancialQueryEngine.executeQuery(
            topic = LocalFinancialIntentParser.QueryTopic.DAILY_SAFE_SPENDING,
            queriedWallet = null,
            wallets = testWallets,
            transactions = testTransactions,
            budgetLimits = testBudgets,
            debts = testDebts,
            installmentPlans = testInstallmentPlans
        )

        assertTrue(response.contains("Harian") || response.contains("Daily"))
    }

    @Test
    fun testTopic_DebtToIncome() {
        val response = FinancialQueryEngine.executeQuery(
            topic = LocalFinancialIntentParser.QueryTopic.DEBT_TO_INCOME,
            queriedWallet = null,
            wallets = testWallets,
            transactions = testTransactions,
            budgetLimits = testBudgets,
            debts = testDebts,
            installmentPlans = testInstallmentPlans
        )

        assertTrue(response.contains("DTI") || response.contains("Rasio") || response.contains("Ratio"))
    }

    @Test
    fun testTopic_LargestExpense() {
        val response = FinancialQueryEngine.executeQuery(
            topic = LocalFinancialIntentParser.QueryTopic.LARGEST_EXPENSE,
            queriedWallet = null,
            wallets = testWallets,
            transactions = testTransactions,
            budgetLimits = testBudgets,
            debts = testDebts,
            installmentPlans = testInstallmentPlans
        )

        assertTrue(response.contains("Belanja") || response.contains("Makanan") || response.contains("1.500.000") || response.contains("1,500,000"))
    }
}
