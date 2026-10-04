package com.example

import com.example.data.model.BudgetLimitItem
import com.example.data.model.DebtItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import com.example.util.NotificationDataHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NotificationDataHelperTest {

    private fun createFixedCalendar(dayOfMonth: Int): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }
    }

    @Test
    fun testEmptyStateProducesNoNotifications() {
        val wallets = listOf(
            Wallet(id = 1L, name = "BCA", balance = 2_000_000.0)
        )
        val debts = emptyList<DebtItem>()
        val budgetLimits = listOf(
            BudgetLimitItem(id = "total_monthly", limitAmount = 5_000_000.0, isCategory = false)
        )
        val expenses = listOf(
            TransactionItem(
                id = 1L,
                title = "Makan Siang",
                amount = 50_000.0,
                type = TransactionType.EXPENSE,
                category = "Makanan",
                categoryIcon = "restaurant",
                walletId = 1L,
                walletName = "BCA",
                timestamp = System.currentTimeMillis()
            )
        )

        val notifications = NotificationDataHelper.generateNotifications(
            wallets = wallets,
            debts = debts,
            budgetLimits = budgetLimits,
            currentMonthExpenses = expenses,
            recentTransactions = expenses,
            dailyStreak = 3,
            calendar = createFixedCalendar(10)
        )

        assertTrue("Notifications should be empty when no alert thresholds are met", notifications.isEmpty())
    }

    @Test
    fun testLowBalanceWarningAtOrBelowFiftyThousand() {
        val wallets = listOf(
            Wallet(id = 1L, name = "Dompet Utama", balance = 50_000.0),
            Wallet(id = 2L, name = "E-Wallet Kas", balance = 0.0),
            Wallet(id = 3L, name = "Tabungan Aman", balance = 500_000.0)
        )

        val notifications = NotificationDataHelper.generateNotifications(
            wallets = wallets,
            debts = emptyList(),
            budgetLimits = emptyList(),
            currentMonthExpenses = emptyList(),
            dailyStreak = 1
        )

        assertEquals("Should trigger exactly 2 low balance notifications", 2, notifications.size)
        assertTrue(notifications.any { it.title.contains("Dompet Utama") && it.isWarning })
        assertTrue(notifications.any { it.title.contains("E-Wallet Kas") && it.isWarning })
        assertFalse(notifications.any { it.title.contains("Tabungan Aman") })
    }

    @Test
    fun testDebtDueRemindersWithinThreeDaysAndOverdue() {
        val calendar = createFixedCalendar(15) // Today is 15th
        val debts = listOf(
            DebtItem(id = "1", name = "Kartu Kredit", amount = 2_000_000.0, interestRatePercent = 2.0, minMonthlyPayment = 200_000.0, dueDate = "Tgl 15 tiap bulan"),
            DebtItem(id = "2", name = "Cicilan Laptop", amount = 1_000_000.0, interestRatePercent = 1.5, minMonthlyPayment = 100_000.0, dueDate = "Tgl 17 tiap bulan"),
            DebtItem(id = "3", name = "Paylater", amount = 500_000.0, interestRatePercent = 3.0, minMonthlyPayment = 50_000.0, dueDate = "Tgl 12 tiap bulan"),
            DebtItem(id = "4", name = "KPR", amount = 50_000_000.0, interestRatePercent = 7.0, minMonthlyPayment = 2_000_000.0, dueDate = "Tgl 28 tiap bulan"),
            DebtItem(id = "5", name = "Pinjaman Lunas", amount = 0.0, interestRatePercent = 0.0, minMonthlyPayment = 0.0, dueDate = "Tgl 15 tiap bulan")
        )

        val notifications = NotificationDataHelper.generateNotifications(
            wallets = listOf(Wallet(id = 1L, name = "BCA", balance = 10_000_000.0)),
            debts = debts,
            budgetLimits = emptyList(),
            currentMonthExpenses = emptyList(),
            dailyStreak = 1,
            calendar = calendar
        )

        assertEquals("Should trigger 3 debt notifications (today, upcoming in 2 days, overdue)", 3, notifications.size)
        assertTrue("Should include due today", notifications.any { it.title.contains("Jatuh Tempo Hari Ini") && it.title.contains("Kartu Kredit") })
        assertTrue("Should include due in 2 days", notifications.any { it.title.contains("Pengingat Hutang") && it.title.contains("Cicilan Laptop") })
        assertTrue("Should include overdue", notifications.any { it.title.contains("Tagihan Terlewat") && it.title.contains("Paylater") })
        assertFalse("Should not include KPR due on 28th", notifications.any { it.title.contains("KPR") })
        assertFalse("Should not include paid debt", notifications.any { it.title.contains("Pinjaman Lunas") })
    }

    @Test
    fun testBudgetLimitsEightyPercentWarningAndOverbudget() {
        val budgetLimits = listOf(
            BudgetLimitItem(id = "total_monthly", limitAmount = 1_000_000.0, isCategory = false),
            BudgetLimitItem(id = "Makanan", limitAmount = 500_000.0, isCategory = true)
        )

        // Case A: 85% total budget, 40% category
        val expensesA = listOf(
            TransactionItem(
                id = 1L,
                title = "Belanja Bulanan",
                amount = 850_000.0,
                type = TransactionType.EXPENSE,
                category = "Lain-lain",
                categoryIcon = "receipt",
                walletId = 1L,
                walletName = "BCA",
                timestamp = System.currentTimeMillis()
            )
        )

        val notifsA = NotificationDataHelper.generateNotifications(
            wallets = listOf(Wallet(id = 1L, name = "BCA", balance = 5_000_000.0)),
            debts = emptyList(),
            budgetLimits = budgetLimits,
            currentMonthExpenses = expensesA,
            dailyStreak = 1
        )

        assertEquals(1, notifsA.size)
        assertTrue(notifsA.first().title.contains("Peringatan Anggaran Bulanan"))
        assertTrue(notifsA.first().isWarning)

        // Case B: 110% total budget, 120% category Makanan
        val expensesB = listOf(
            TransactionItem(
                id = 2L,
                title = "Makan Mewah",
                amount = 600_000.0,
                type = TransactionType.EXPENSE,
                category = "Makanan",
                categoryIcon = "restaurant",
                walletId = 1L,
                walletName = "BCA",
                timestamp = System.currentTimeMillis()
            ),
            TransactionItem(
                id = 3L,
                title = "Belanja Tambahan",
                amount = 550_000.0,
                type = TransactionType.EXPENSE,
                category = "Lain-lain",
                categoryIcon = "receipt",
                walletId = 1L,
                walletName = "BCA",
                timestamp = System.currentTimeMillis()
            )
        )

        val notifsB = NotificationDataHelper.generateNotifications(
            wallets = listOf(Wallet(id = 1L, name = "BCA", balance = 5_000_000.0)),
            debts = emptyList(),
            budgetLimits = budgetLimits,
            currentMonthExpenses = expensesB,
            dailyStreak = 1
        )

        assertEquals("Should trigger both total and category overbudget warnings", 2, notifsB.size)
        assertTrue(notifsB.any { it.title.contains("Overbudget Anggaran Bulanan") })
        assertTrue(notifsB.any { it.title.contains("Overbudget: Makanan") })
    }

    @Test
    fun testStreakMilestoneVsNonMilestone() {
        // Milestone: 7 days
        val notifs7 = NotificationDataHelper.generateNotifications(
            wallets = listOf(Wallet(id = 1L, name = "BCA", balance = 1_000_000.0)),
            debts = emptyList(),
            budgetLimits = emptyList(),
            currentMonthExpenses = emptyList(),
            dailyStreak = 7
        )
        assertEquals(1, notifs7.size)
        assertTrue(notifs7.first().title.contains("Streak 7 Hari"))
        assertFalse("Streak celebration should not be warning", notifs7.first().isWarning)

        // Milestone: 30 days
        val notifs30 = NotificationDataHelper.generateNotifications(
            wallets = listOf(Wallet(id = 1L, name = "BCA", balance = 1_000_000.0)),
            debts = emptyList(),
            budgetLimits = emptyList(),
            currentMonthExpenses = emptyList(),
            dailyStreak = 30
        )
        assertEquals(1, notifs30.size)
        assertTrue(notifs30.first().title.contains("Streak 30 Hari"))

        // Non-milestone: 5 days
        val notifs5 = NotificationDataHelper.generateNotifications(
            wallets = listOf(Wallet(id = 1L, name = "BCA", balance = 1_000_000.0)),
            debts = emptyList(),
            budgetLimits = emptyList(),
            currentMonthExpenses = emptyList(),
            dailyStreak = 5
        )
        assertTrue("Non-milestone streak should not emit notification", notifs5.isEmpty())
    }

    @Test
    fun testRecurringTransactionNotification() {
        val txs = listOf(
            TransactionItem(
                id = 10L,
                title = "Gaji Bulanan (Auto Recurring)",
                amount = 8_000_000.0,
                type = TransactionType.INCOME,
                category = "Gaji",
                categoryIcon = "payments",
                walletId = 1L,
                walletName = "BCA Payroll",
                timestamp = System.currentTimeMillis(),
                tags = "#recurring #salary"
            )
        )

        val notifs = NotificationDataHelper.generateNotifications(
            wallets = listOf(Wallet(id = 1L, name = "BCA Payroll", balance = 10_000_000.0)),
            debts = emptyList(),
            budgetLimits = emptyList(),
            currentMonthExpenses = emptyList(),
            recentTransactions = txs,
            dailyStreak = 1
        )

        assertEquals(1, notifs.size)
        assertTrue(notifs.first().title.contains("Transaksi Berulang"))
        assertTrue(notifs.first().message.contains("Gaji Bulanan"))
        assertFalse(notifs.first().isWarning)
    }
}
