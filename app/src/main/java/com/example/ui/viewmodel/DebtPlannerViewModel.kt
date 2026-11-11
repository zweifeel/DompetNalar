package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.data.model.DebtItem
import com.example.ui.components.DebtPayoffStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

class DebtPlannerViewModel : ViewModel() {

    private val _debtList = MutableStateFlow<List<DebtItem>>(
        if (com.example.util.LocaleHelper.isEnglish()) {
            listOf(
                DebtItem(name = "BCA Credit Card", amount = 15000000.0, interestRatePercent = 21.0, minMonthlyPayment = 1500000.0, category = "Kartu Kredit", dueDate = "Day 15 each month"),
                DebtItem(name = "Online Loan A", amount = 3000000.0, interestRatePercent = 18.0, minMonthlyPayment = 500000.0, category = "Pinjaman Online", dueDate = "Day 20 each month"),
                DebtItem(name = "Motorcycle Installment", amount = 8000000.0, interestRatePercent = 12.0, minMonthlyPayment = 800000.0, category = "Cicilan Kendaraan", dueDate = "Day 5 each month")
            )
        } else {
            listOf(
                DebtItem(name = "Kartu Kredit BCA", amount = 15000000.0, interestRatePercent = 21.0, minMonthlyPayment = 1500000.0, category = "Kartu Kredit", dueDate = "Tgl 15 tiap bulan"),
                DebtItem(name = "Pinjaman Online A", amount = 3000000.0, interestRatePercent = 18.0, minMonthlyPayment = 500000.0, category = "Pinjaman Online", dueDate = "Tgl 20 tiap bulan"),
                DebtItem(name = "Cicilan Motor", amount = 8000000.0, interestRatePercent = 12.0, minMonthlyPayment = 800000.0, category = "Cicilan Kendaraan", dueDate = "Tgl 5 tiap bulan")
            )
        }
    )
    val debtList: StateFlow<List<DebtItem>> = _debtList.asStateFlow()

    private val _selectedStrategy = MutableStateFlow(DebtPayoffStrategy.DUE_DATE)
    val selectedStrategy: StateFlow<DebtPayoffStrategy> = _selectedStrategy.asStateFlow()

    fun setStrategy(strategy: DebtPayoffStrategy) {
        _selectedStrategy.value = strategy
    }

    fun addDebt(item: DebtItem) {
        _debtList.value = _debtList.value + item
    }

    fun updateDebt(item: DebtItem) {
        _debtList.value = _debtList.value.map { if (it.id == item.id) item else it }
    }

    fun deleteDebt(id: String) {
        _debtList.value = _debtList.value.filter { it.id != id }
    }

    fun deleteCategory(categoryName: String) {
        _debtList.value = _debtList.value.filter { it.category != categoryName }
    }

    fun resetAllDebts() {
        _debtList.value = emptyList()
    }

    fun getDaysUntilDue(dueDateStr: String): Int {
        val digits = Regex("\\d+").find(dueDateStr)?.value?.toIntOrNull() ?: 15
        val now = Calendar.getInstance()
        val today = now.get(Calendar.DAY_OF_MONTH)
        val maxDays = now.getActualMaximum(Calendar.DAY_OF_MONTH)
        return if (digits >= today) {
            digits - today
        } else {
            (maxDays - today) + digits
        }
    }
}
