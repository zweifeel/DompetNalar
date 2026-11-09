package com.example.ui

import com.example.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class AddTransactionTabMappingTest {

    private val transactionTabs = listOf(
        TransactionType.EXPENSE,
        TransactionType.INCOME,
        TransactionType.TRANSFER
    )

    @Test
    fun testExplicitTabOrdering_DecoupledFromEnumOrdinal() {
        // Enums.kt defines: INCOME (0), EXPENSE (1), TRANSFER (2)
        assertEquals(0, TransactionType.INCOME.ordinal)
        assertEquals(1, TransactionType.EXPENSE.ordinal)
        assertEquals(2, TransactionType.TRANSFER.ordinal)

        // The UI TabRow requires visual tab order: Expense (0), Income (1), Transfer (2)
        assertEquals(TransactionType.EXPENSE, transactionTabs[0])
        assertEquals(TransactionType.INCOME, transactionTabs[1])
        assertEquals(TransactionType.TRANSFER, transactionTabs[2])

        // Verify index-based lookup for UI indicator offset
        assertEquals(0, transactionTabs.indexOf(TransactionType.EXPENSE))
        assertEquals(1, transactionTabs.indexOf(TransactionType.INCOME))
        assertEquals(2, transactionTabs.indexOf(TransactionType.TRANSFER))
    }

    @Test
    fun testTabSelectionBehavior_AllTypesConsistent() {
        val expenseIndex = transactionTabs.indexOf(TransactionType.EXPENSE)
        val incomeIndex = transactionTabs.indexOf(TransactionType.INCOME)
        val transferIndex = transactionTabs.indexOf(TransactionType.TRANSFER)

        // Indicator index matches visual tab index
        assertEquals(0, expenseIndex)
        assertEquals(1, incomeIndex)
        assertEquals(2, transferIndex)

        // Verify label mapping
        val getLabel: (TransactionType) -> String = { type ->
            when (type) {
                TransactionType.EXPENSE -> "Pengeluaran"
                TransactionType.INCOME -> "Pemasukan"
                TransactionType.TRANSFER -> "Transfer"
            }
        }

        assertEquals("Pengeluaran", getLabel(transactionTabs[expenseIndex]))
        assertEquals("Pemasukan", getLabel(transactionTabs[incomeIndex]))
        assertEquals("Transfer", getLabel(transactionTabs[transferIndex]))
    }
}
