package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FinSmartDatabase
import com.example.data.model.RecurringSchedule
import com.example.data.model.TransactionType
import com.example.data.repository.FinanceRepository
import com.example.data.session.SessionManager
import com.example.util.RecurringInterval
import com.example.util.RecurringTransactionWorker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class RecurringTransactionViewModel(
    application: Application,
    private val repository: FinanceRepository = FinanceRepository(
        walletDao = FinSmartDatabase.getDatabase(application, CoroutineScope(Dispatchers.IO)).walletDao(),
        database = FinSmartDatabase.getDatabase(application, CoroutineScope(Dispatchers.IO))
    ),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    val recurringSchedules: StateFlow<List<RecurringSchedule>> = SessionManager.activeUserId
        .flatMapLatest { userId: Long? ->
            if (userId != null && userId > 0L) {
                repository.getAllRecurringSchedules(userId)
            } else {
                flowOf(emptyList<RecurringSchedule>())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    ): Job {
        return viewModelScope.launch(ioDispatcher) {
            val userId = SessionManager.requireActiveUserId()
            val newSchedule = RecurringSchedule(
                id = java.util.UUID.randomUUID().toString(),
                title = title,
                amount = amount,
                type = type,
                category = category,
                categoryIcon = categoryIcon,
                walletId = walletId,
                walletName = walletName,
                interval = interval,
                dayOfMonth = dayOfMonth,
                isEnabled = true,
                userId = userId
            )

            repository.saveRecurringSchedule(newSchedule)
            RecurringTransactionWorker.scheduleSchedule(getApplication(), newSchedule)
        }
    }
}
