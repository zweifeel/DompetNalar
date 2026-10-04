package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FinSmartDatabase
import com.example.data.local.BudgetLimitDao
import com.example.data.local.SplitBillHistoryDao
import com.example.data.local.DebtDao
import com.example.data.local.AssetDao
import com.example.data.model.AccountMode
import com.example.data.model.AssetCategory
import com.example.data.model.AssetItem
import com.example.data.model.BudgetLimitItem
import com.example.data.model.CategoryItem
import com.example.data.model.DebtItem
import com.example.data.model.SplitBillHistoryItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import com.example.data.model.BackupDataPayload
import com.example.data.repository.FinanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class TimeframeFilter(val label: String) {
    DAILY("Harian"),
    WEEKLY("Mingguan"),
    MONTHLY("Bulanan"),
    YEARLY("Tahunan"),
    ALL("Semua")
}

data class CashflowDataPoint(
    val label: String,
    val income: Double,
    val expense: Double,
    val timestamp: Long
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository
    private val splitBillHistoryDao: SplitBillHistoryDao
    private val budgetLimitDao: BudgetLimitDao
    private val debtDao: DebtDao
    private val assetDao: AssetDao

    private val _accountMode = MutableStateFlow(AccountMode.PERSONAL)
    val accountMode: StateFlow<AccountMode> = _accountMode.asStateFlow()

    private val _isPrivacyMode = MutableStateFlow(false)
    val isPrivacyMode: StateFlow<Boolean> = _isPrivacyMode.asStateFlow()

    private val _selectedTimeframe = MutableStateFlow(TimeframeFilter.MONTHLY)
    val selectedTimeframe: StateFlow<TimeframeFilter> = _selectedTimeframe.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    private val _selectedWalletFilter = MutableStateFlow<Long?>(null)
    val selectedWalletFilter: StateFlow<Long?> = _selectedWalletFilter.asStateFlow()

    // Dialog & Sheet States
    private val _isAddTransactionOpen = MutableStateFlow(false)
    val isAddTransactionOpen: StateFlow<Boolean> = _isAddTransactionOpen.asStateFlow()

    private val _defaultTransactionType = MutableStateFlow(TransactionType.EXPENSE)
    val defaultTransactionType: StateFlow<TransactionType> = _defaultTransactionType.asStateFlow()

    private val _editingWallet = MutableStateFlow<Wallet?>(null)
    val editingWallet: StateFlow<Wallet?> = _editingWallet.asStateFlow()

    private val _isWalletDialogOpen = MutableStateFlow(false)
    val isWalletDialogOpen: StateFlow<Boolean> = _isWalletDialogOpen.asStateFlow()

    private val _deletingWallet = MutableStateFlow<Wallet?>(null)
    val deletingWallet: StateFlow<Wallet?> = _deletingWallet.asStateFlow()

    // FEAT-34 Personal Finance Receipt Generator
    private val _selectedReceiptTransaction = MutableStateFlow<TransactionItem?>(null)
    val selectedReceiptTransaction: StateFlow<TransactionItem?> = _selectedReceiptTransaction.asStateFlow()

    init {
        val database = FinSmartDatabase.getDatabase(application, viewModelScope)
        repository = FinanceRepository(
            database.walletDao(),
            database.transactionDao(),
            database.categoryDao()
        )
        splitBillHistoryDao = database.splitBillHistoryDao()
        budgetLimitDao = database.budgetLimitDao()
        debtDao = database.debtDao()
        assetDao = database.assetDao()
    }

    val debts: StateFlow<List<DebtItem>> = debtDao.getAllDebts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveDebt(debt: DebtItem) {
        viewModelScope.launch {
            debtDao.insertDebt(debt)
        }
    }

    fun deleteDebt(debt: DebtItem) {
        viewModelScope.launch {
            debtDao.deleteDebt(debt)
        }
    }

    fun deleteDebtsByCategory(category: String) {
        viewModelScope.launch {
            debts.value.filter { it.category == category }.forEach {
                debtDao.deleteDebt(it)
            }
        }
    }

    fun clearAllDebts() {
        viewModelScope.launch {
            debtDao.clearAllDebts()
        }
    }

    val assets: StateFlow<List<AssetItem>> = assetDao.getAllAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveAsset(asset: AssetItem) {
        viewModelScope.launch {
            assetDao.insertAsset(asset)
        }
    }

    fun deleteAsset(asset: AssetItem) {
        viewModelScope.launch {
            assetDao.deleteAsset(asset)
        }
    }

    fun deleteAssetsByCategory(category: AssetCategory) {
        viewModelScope.launch {
            assets.value.filter { it.category == category }.forEach {
                assetDao.deleteAsset(it)
            }
        }
    }

    fun clearAllAssets() {
        viewModelScope.launch {
            assetDao.clearAllAssets()
        }
    }

    val budgetLimits: StateFlow<List<BudgetLimitItem>> = budgetLimitDao.getAllBudgetLimits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveBudgetLimit(id: String, amount: Double, isCategory: Boolean) {
        viewModelScope.launch {
            budgetLimitDao.insertBudgetLimit(BudgetLimitItem(id, amount, isCategory))
        }
    }

    fun deleteBudgetLimit(item: BudgetLimitItem) {
        viewModelScope.launch {
            budgetLimitDao.deleteBudgetLimit(item)
        }
    }

    fun resetAllBudgetLimits() {
        viewModelScope.launch {
            budgetLimits.value.forEach { item ->
                budgetLimitDao.deleteBudgetLimit(item)
            }
        }
    }

    val splitBillHistory: StateFlow<List<SplitBillHistoryItem>> = splitBillHistoryDao.getAllSplitBillHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveSplitBillHistory(merchantName: String, date: String, totalAmount: Double, formattedText: String) {
        viewModelScope.launch {
            splitBillHistoryDao.insertSplitBillHistory(
                SplitBillHistoryItem(
                    merchantName = merchantName,
                    date = date,
                    totalAmount = totalAmount,
                    formattedText = formattedText
                )
            )
        }
    }

    fun deleteSplitBillHistory(item: SplitBillHistoryItem) {
        viewModelScope.launch {
            splitBillHistoryDao.deleteSplitBillHistory(item)
        }
    }

    fun clearAllSplitBillHistory() {
        viewModelScope.launch {
            splitBillHistoryDao.clearAllHistory()
        }
    }

    val wallets: StateFlow<List<Wallet>> = _accountMode.flatMapLatest { mode ->
        repository.getWallets(mode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWallets: StateFlow<List<Wallet>> = repository.getAllWallets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransactions: StateFlow<List<TransactionItem>> = _accountMode.flatMapLatest { mode ->
        repository.getTransactions(mode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentMonthExpensesList: StateFlow<List<TransactionItem>> = allTransactions.map { txList ->
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startMonth = cal.timeInMillis
        txList.filter { it.timestamp >= startMonth && it.type == TransactionType.EXPENSE }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryItem>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalBalance: StateFlow<Double> = wallets.combine(_accountMode) { list, _ ->
        list.sumOf { it.balance }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalAssetsValue: StateFlow<Double> = assets.map { list ->
        list.sumOf { it.currentValue }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalDebtsAmount: StateFlow<Double> = debts.map { list ->
        list.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val netWorth: StateFlow<Double> = combine(
        totalBalance,
        totalAssetsValue,
        totalDebtsAmount
    ) { balance, assetsValue, debtsValue ->
        balance + assetsValue - debtsValue
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val filteredTransactions: StateFlow<List<TransactionItem>> = combine(
        allTransactions,
        _searchQuery,
        _selectedCategoryFilter,
        _selectedWalletFilter,
        _selectedTimeframe
    ) { txList, query, catFilter, walletFilter, timeframe ->
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()

        val timeFiltered = when (timeframe) {
            TimeframeFilter.DAILY -> {
                cal.timeInMillis = now
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startDay = cal.timeInMillis
                txList.filter { it.timestamp >= startDay }
            }
            TimeframeFilter.WEEKLY -> {
                cal.timeInMillis = now
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val startWeek = cal.timeInMillis
                txList.filter { it.timestamp >= startWeek }
            }
            TimeframeFilter.MONTHLY -> {
                cal.timeInMillis = now
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val startMonth = cal.timeInMillis
                txList.filter { it.timestamp >= startMonth }
            }
            TimeframeFilter.YEARLY -> {
                cal.timeInMillis = now
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val startYear = cal.timeInMillis
                txList.filter { it.timestamp >= startYear }
            }
            TimeframeFilter.ALL -> txList
        }

        timeFiltered.filter { tx ->
            val matchesQuery = query.isBlank() ||
                    tx.title.contains(query, ignoreCase = true) ||
                    tx.category.contains(query, ignoreCase = true) ||
                    tx.note.contains(query, ignoreCase = true) ||
                    tx.tags.contains(query, ignoreCase = true)

            val matchesCategory = catFilter == null || tx.category.equals(catFilter, ignoreCase = true)
            val matchesWallet = walletFilter == null || tx.walletId == walletFilter || tx.targetWalletId == walletFilter

            matchesQuery && matchesCategory && matchesWallet
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalIncome: StateFlow<Double> = filteredTransactions.combine(_selectedTimeframe) { txList, _ ->
        txList.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalExpense: StateFlow<Double> = filteredTransactions.combine(_selectedTimeframe) { txList, _ ->
        txList.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val chartData: StateFlow<List<CashflowDataPoint>> = combine(
        allTransactions,
        _selectedTimeframe
    ) { txList, timeframe ->
        generateChartDataPoints(txList, timeframe)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleAccountMode() {
        _accountMode.value = if (_accountMode.value == AccountMode.PERSONAL) AccountMode.BUSINESS else AccountMode.PERSONAL
    }

    fun setAccountMode(mode: AccountMode) {
        _accountMode.value = mode
    }

    fun togglePrivacyMode() {
        _isPrivacyMode.value = !_isPrivacyMode.value
    }

    fun setTimeframe(timeframe: TimeframeFilter) {
        _selectedTimeframe.value = timeframe
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(category: String?) {
        _selectedCategoryFilter.value = category
    }

    fun setWalletFilter(walletId: Long?) {
        _selectedWalletFilter.value = walletId
    }

    fun openAddTransaction(type: TransactionType = TransactionType.EXPENSE) {
        _defaultTransactionType.value = type
        _isAddTransactionOpen.value = true
    }

    fun closeAddTransaction() {
        _isAddTransactionOpen.value = false
    }

    fun openReceipt(transaction: TransactionItem) {
        _selectedReceiptTransaction.value = transaction
    }

    fun closeReceipt() {
        _selectedReceiptTransaction.value = null
    }

    fun openWalletDialog(wallet: Wallet? = null) {
        _editingWallet.value = wallet
        _isWalletDialogOpen.value = true
    }

    fun closeWalletDialog() {
        _editingWallet.value = null
        _isWalletDialogOpen.value = false
    }

    fun openDeleteWalletConfirm(wallet: Wallet) {
        _deletingWallet.value = wallet
    }

    fun closeDeleteWalletConfirm() {
        _deletingWallet.value = null
    }

    fun saveTransaction(
        title: String,
        amount: Double,
        type: TransactionType,
        category: String,
        categoryIcon: String,
        walletId: Long,
        walletName: String,
        targetWalletId: Long? = null,
        targetWalletName: String? = null,
        timestamp: Long = System.currentTimeMillis(),
        note: String = "",
        tags: String = ""
    ) {
        viewModelScope.launch {
            val item = TransactionItem(
                title = title,
                amount = amount,
                type = type,
                category = category,
                categoryIcon = categoryIcon,
                walletId = walletId,
                walletName = walletName,
                targetWalletId = targetWalletId,
                targetWalletName = targetWalletName,
                timestamp = timestamp,
                note = note,
                tags = tags,
                accountMode = _accountMode.value.name
            )
            repository.insertTransaction(item)
            _isAddTransactionOpen.value = false
        }
    }

    fun deleteTransaction(transaction: TransactionItem) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    fun saveWallet(
        name: String,
        type: com.example.data.model.WalletType,
        accountNumber: String,
        initialBalance: Double,
        colorHex: String,
        iconName: String,
        existingId: Long? = null
    ) {
        viewModelScope.launch {
            if (existingId == null || existingId == 0L) {
                val newWallet = Wallet(
                    name = name,
                    type = type,
                    accountNumber = accountNumber,
                    balance = initialBalance,
                    colorHex = colorHex,
                    iconName = iconName,
                    accountMode = _accountMode.value.name,
                    isDefault = wallets.value.isEmpty()
                )
                repository.insertWallet(newWallet)
            } else {
                val current = repository.getWalletById(existingId)
                if (current != null) {
                    val updated = current.copy(
                        name = name,
                        type = type,
                        accountNumber = accountNumber,
                        colorHex = colorHex,
                        iconName = iconName
                    )
                    repository.updateWallet(updated)
                }
            }
            closeWalletDialog()
        }
    }

    fun deleteWallet(wallet: Wallet) {
        viewModelScope.launch {
            repository.deleteWallet(wallet)
            closeDeleteWalletConfirm()
        }
    }

    fun insertCustomCategory(name: String, type: TransactionType, iconName: String = "category", colorHex: String = "#0B57D0") {
        viewModelScope.launch {
            val cat = CategoryItem(
                name = name,
                type = type,
                iconName = iconName,
                colorHex = colorHex
            )
            repository.insertCategory(cat)
        }
    }

    fun deleteCategory(category: CategoryItem) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }

    private fun generateChartDataPoints(
        transactions: List<TransactionItem>,
        timeframe: TimeframeFilter
    ): List<CashflowDataPoint> {
        val cal = Calendar.getInstance()
        val result = mutableListOf<CashflowDataPoint>()

        when (timeframe) {
            TimeframeFilter.DAILY -> {
                val dayCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = dayCal.timeInMillis
                val end = start + 86400000L - 1
                val dayTx = transactions.filter { it.timestamp in start..end }
                val inc = dayTx.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                val exp = dayTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                result.add(CashflowDataPoint("Hari Ini", inc, exp, start))
            }
            TimeframeFilter.WEEKLY -> {
                // Last 7 days
                val dayFormat = java.text.SimpleDateFormat("EEE", java.util.Locale("id", "ID"))
                for (i in 6 downTo 0) {
                    val dayCal = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -i)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val start = dayCal.timeInMillis
                    val end = start + 86400000L - 1

                    val dayTx = transactions.filter { it.timestamp in start..end }
                    val inc = dayTx.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                    val exp = dayTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

                    result.add(
                        CashflowDataPoint(
                            label = dayFormat.format(dayCal.time),
                            income = inc,
                            expense = exp,
                            timestamp = start
                        )
                    )
                }
            }
            TimeframeFilter.MONTHLY -> {
                // 4 weeks of the current month
                val monthName = java.text.SimpleDateFormat("MMM", java.util.Locale("id", "ID")).format(cal.time)
                for (week in 1..4) {
                    cal.set(Calendar.DAY_OF_MONTH, (week - 1) * 7 + 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    val start = cal.timeInMillis
                    val end = start + (7L * 86400000L) - 1

                    val weekTx = transactions.filter { it.timestamp in start..end }
                    val inc = weekTx.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                    val exp = weekTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

                    result.add(
                        CashflowDataPoint(
                            label = "M$week $monthName",
                            income = inc,
                            expense = exp,
                            timestamp = start
                        )
                    )
                }
            }
            TimeframeFilter.YEARLY, TimeframeFilter.ALL -> {
                // Last 6 months
                val monthFormat = java.text.SimpleDateFormat("MMM", java.util.Locale("id", "ID"))
                for (i in 5 downTo 0) {
                    val monthCal = Calendar.getInstance().apply {
                        add(Calendar.MONTH, -i)
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }
                    val start = monthCal.timeInMillis
                    monthCal.set(Calendar.DAY_OF_MONTH, monthCal.getActualMaximum(Calendar.DAY_OF_MONTH))
                    monthCal.set(Calendar.HOUR_OF_DAY, 23)
                    monthCal.set(Calendar.MINUTE, 59)
                    monthCal.set(Calendar.SECOND, 59)
                    val end = monthCal.timeInMillis

                    val mTx = transactions.filter { it.timestamp in start..end }
                    val inc = mTx.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                    val exp = mTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

                    result.add(
                        CashflowDataPoint(
                            label = monthFormat.format(start),
                            income = inc,
                            expense = exp,
                            timestamp = start
                        )
                    )
                }
            }
        }
        return result
    }

    companion object {
        fun restoreDatabaseInTransaction(database: FinSmartDatabase, payload: BackupDataPayload) {
            database.clearAllTables()
            kotlinx.coroutines.runBlocking {
                database.walletDao().insertWallets(payload.wallets)
                database.categoryDao().insertCategories(payload.categories)
                database.userDao().insertUsers(payload.users)
                payload.transactions.forEach { database.transactionDao().insertTransaction(it) }
                payload.splitBillHistory.forEach { database.splitBillHistoryDao().insertSplitBillHistory(it) }
                payload.budgetLimits.forEach { database.budgetLimitDao().insertBudgetLimit(it) }
                payload.debts.forEach { database.debtDao().insertDebt(it) }
                payload.assets.forEach { database.assetDao().insertAsset(it) }
            }
        }
    }
}
