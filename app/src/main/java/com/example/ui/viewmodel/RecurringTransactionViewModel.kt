package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.TransactionType
import com.example.util.RecurringInterval
import com.example.util.RecurringTransactionWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecurringSchedule(
    val id: String,
    val title: String,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val walletId: Long,
    val walletName: String,
    val interval: RecurringInterval,
    val dayOfMonth: Int,
    val isEnabled: Boolean = true
)

class RecurringTransactionViewModel(application: Application) : AndroidViewModel(application) {

    private val _recurringSchedules = MutableStateFlow<List<RecurringSchedule>>(
        listOf(
            RecurringSchedule(
                id = "salary_schedule_1",
                title = "Gaji Pokok Bulanan",
                amount = 7500000.0,
                type = TransactionType.INCOME,
                category = "Gaji & Penghasilan",
                walletId = 1L,
                walletName = "Rekening Utama (BCA)",
                interval = RecurringInterval.MONTHLY,
                dayOfMonth = 25,
                isEnabled = true
            )
        )
    )
    val recurringSchedules: StateFlow<List<RecurringSchedule>> = _recurringSchedules.asStateFlow()

    fun scheduleRecurringTransaction(
        title: String,
        amount: Double,
        type: TransactionType,
        category: String,
        categoryIcon: String,
        walletId: Long,
        walletName: String,
        interval: RecurringInterval,
        dayOfMonth: Int = 25
    ) {
        viewModelScope.launch {
            val newSchedule = RecurringSchedule(
                id = java.util.UUID.randomUUID().toString(),
                title = title,
                amount = amount,
                type = type,
                category = category,
                walletId = walletId,
                walletName = walletName,
                interval = interval,
                dayOfMonth = dayOfMonth,
                isEnabled = true
            )
            _recurringSchedules.value = _recurringSchedules.value + newSchedule

            val userId = com.example.data.session.SessionManager.requireActiveUserId()
            RecurringTransactionWorker.scheduleRecurring(
                context = getApplication(),
                userId = userId,
                title = title,
                amount = amount,
                type = type,
                category = category,
                categoryIcon = categoryIcon,
                walletId = walletId,
                walletName = walletName,
                interval = interval,
                dayOfMonth = dayOfMonth
            )
        }
    }
}
