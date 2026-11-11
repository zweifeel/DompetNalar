package com.example.ui.viewmodel

import android.content.Context



import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.data.local.FinSmartDatabase
import com.example.data.local.BudgetLimitDao
import com.example.data.local.SplitBillHistoryDao
import com.example.data.local.DebtDao
import com.example.data.local.AssetDao
import com.example.data.local.InstallmentPlanDao
import com.example.data.model.AccountMode
import com.example.data.model.AssetCategory
import com.example.data.model.AssetItem
import com.example.data.model.BudgetLimitItem
import com.example.data.model.CategoryItem
import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPlanItem
import com.example.data.model.SplitBillHistoryItem
import com.example.data.model.TransactionItem
import com.example.util.AppStrings
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.data.model.BackupDataPayload
import com.example.data.repository.FinanceRepository
import com.example.data.session.SessionManager
import com.example.util.InstallmentDateHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Calendar

data class CurrencyBalanceSummary(
    val currencyCode: String,
    val totalBalanceMinorUnits: Long = 0L,
    val walletCount: Int = 0,
    val totalBalance: Double = com.example.util.CurrencyRegistry.canonicalMinorUnitsToDisplay(totalBalanceMinorUnits, currencyCode)
) {
    constructor(currencyCode: String, totalBalance: Double, walletCount: Int) : this(
        currencyCode = currencyCode,
        totalBalanceMinorUnits = com.example.util.CurrencyRegistry.displayToCanonicalMinorUnits(totalBalance, currencyCode),
        walletCount = walletCount,
        totalBalance = totalBalance
    )
}

data class CurrencyCashflowSummary(
    val currencyCode: String,
    val totalIncomeMinorUnits: Long = 0L,
    val totalExpenseMinorUnits: Long = 0L,
    val netCashflowMinorUnits: Long = totalIncomeMinorUnits - totalExpenseMinorUnits,
    val transactionCount: Int = 0,
    val totalIncome: Double = com.example.util.CurrencyRegistry.canonicalMinorUnitsToDisplay(totalIncomeMinorUnits, currencyCode),
    val totalExpense: Double = com.example.util.CurrencyRegistry.canonicalMinorUnitsToDisplay(totalExpenseMinorUnits, currencyCode),
    val netCashflow: Double = com.example.util.CurrencyRegistry.canonicalMinorUnitsToDisplay(netCashflowMinorUnits, currencyCode)
) {
    constructor(currencyCode: String, totalIncome: Double, totalExpense: Double, netCashflow: Double, transactionCount: Int) : this(
        currencyCode = currencyCode,
        totalIncomeMinorUnits = com.example.util.CurrencyRegistry.displayToCanonicalMinorUnits(totalIncome, currencyCode),
        totalExpenseMinorUnits = com.example.util.CurrencyRegistry.displayToCanonicalMinorUnits(totalExpense, currencyCode),
        netCashflowMinorUnits = com.example.util.CurrencyRegistry.displayToCanonicalMinorUnits(netCashflow, currencyCode),
        transactionCount = transactionCount,
        totalIncome = totalIncome,
        totalExpense = totalExpense,
        netCashflow = netCashflow
    )
}

enum class TimeframeFilter(val idLabel: String, val enLabel: String) {
    DAILY("Harian", "Daily"),
    WEEKLY("Mingguan", "Weekly"),
    MONTHLY("Bulanan", "Monthly"),
    YEARLY("Tahunan", "Yearly"),
    ALL("Semua", "All");

    val label: String
        get() = if (com.example.util.LocaleHelper.isEnglish()) enLabel else idLabel
}

data class CashflowDataPoint(
    val label: String,
    val income: Double,
    val expense: Double,
    val timestamp: Long
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val database: FinSmartDatabase
    private val repository: FinanceRepository
    private val splitBillHistoryDao: SplitBillHistoryDao
    private val budgetLimitDao: BudgetLimitDao
    private val debtDao: DebtDao
    private val assetDao: AssetDao
    private val installmentPlanDao: InstallmentPlanDao

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

    // FEAT-20 Stealth Mode & Duress Camouflage State
    val isStealthMode: StateFlow<Boolean> = SessionManager.isStealthMode

    fun setStealthMode(enabled: Boolean) {
        SessionManager.setStealthMode(enabled)
    }

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
        database = FinSmartDatabase.getDatabase(application, viewModelScope)
        repository = FinanceRepository(
            database.walletDao(),
            database.transactionDao(),
            database.categoryDao(),
            database
        )
        splitBillHistoryDao = database.splitBillHistoryDao()
        budgetLimitDao = database.budgetLimitDao()
        debtDao = database.debtDao()
        assetDao = database.assetDao()
        installmentPlanDao = database.installmentPlanDao()

        // Cleanup ephemeral states on logout or user switch
        viewModelScope.launch {
            SessionManager.activeUserId.collect { id ->
                if (id == null) {
                    _editingWallet.value = null
                    _deletingWallet.value = null
                    _selectedReceiptTransaction.value = null
                    _selectedCategoryFilter.value = null
                    _selectedWalletFilter.value = null
                    _searchQuery.value = ""
                }
            }
        }
    }

    val debts: StateFlow<List<DebtItem>> = combine(SessionManager.activeUserId, isStealthMode) { userId, isStealth ->
        userId to isStealth
    }.flatMapLatest { (userId, isStealth) ->
        if (isStealth) flowOf(emptyList())
        else if (userId != null && userId > 0L) debtDao.getAllDebts(userId)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installmentPlans: StateFlow<List<InstallmentPlanItem>> = combine(SessionManager.activeUserId, isStealthMode) { userId, isStealth ->
        userId to isStealth
    }.flatMapLatest { (userId, isStealth) ->
        if (isStealth) flowOf(emptyList())
        else if (userId != null && userId > 0L) installmentPlanDao.getAllInstallmentPlans(userId)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveDebt(debt: DebtItem) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            repository.saveDebt(debt, userId)
        }
    }

    fun deleteDebt(debt: DebtItem) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            repository.deleteDebt(debt, userId)
        }
    }

    fun deleteDebtsByCategory(category: String) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            debts.value.filter { it.category == category }.forEach {
                repository.deleteDebt(it, userId)
            }
        }
    }

    fun clearAllDebts() {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            repository.resetAllDebtsAndPlans(userId)
        }
    }

    fun resetAllDebtsAndPlans(onSuccess: ((Pair<Int, Int>) -> Unit)? = null) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            val res = repository.resetAllDebtsAndPlans(userId)
            onSuccess?.invoke(res)
        }
    }

    fun saveInstallmentPlan(
        plan: InstallmentPlanItem,
        explicitPeriods: List<com.example.data.model.InstallmentPeriod> = emptyList()
    ) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            repository.saveInstallmentPlan(plan, userId, explicitPeriods)
        }
    }

    fun updateInstallmentPeriodDueDate(
        periodId: String,
        newDueDate: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            val success = repository.updateInstallmentPeriodDueDate(periodId, newDueDate, userId)
            onComplete?.invoke(success)
        }
    }

    fun saveBatchPayLaterBills(
        debtsToSave: List<DebtItem>,
        plansWithPeriods: List<Pair<InstallmentPlanItem, List<com.example.data.model.InstallmentPeriod>>>,
        onComplete: ((Result<Int>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: run {
                onComplete?.invoke(Result.failure(IllegalStateException(AppStrings.t("Pengguna belum login", "User is not logged in"))))
                return@launch
            }
            val result = repository.saveBatchPayLaterBills(debtsToSave, plansWithPeriods, userId)
            onComplete?.invoke(result)
        }
    }

    fun deleteInstallmentPlan(plan: InstallmentPlanItem) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            repository.deleteInstallmentPlan(plan.id, userId)
        }
    }

    fun getDebtPriorityPlan(monthlyBudget: Double): com.example.util.DebtPriorityEngine.DebtPriorityPlan {
        return com.example.util.DebtPriorityEngine.calculatePlan(
            debts = debts.value,
            installmentPlans = installmentPlans.value,
            totalMonthlyBudget = monthlyBudget
        )
    }

    fun getDebtFreedomPlan(
        monthlyIncome: Double,
        essentialExpenses: Double,
        emergencyCushion: Double = 0.0
    ): com.example.util.DebtFreedomPlanner.FreedomPlanResult {
        return com.example.util.DebtFreedomPlanner.calculateFreedomPlan(
            monthlyIncome = monthlyIncome,
            essentialExpenses = essentialExpenses,
            emergencyCushion = emergencyCushion,
            debts = debts.value,
            installmentPlans = installmentPlans.value
        )
    }

    private val activePaymentPlanIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun confirmInstallmentPayment(
        plan: InstallmentPlanItem,
        walletId: Long? = null,
        notes: String? = null
    ) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            if (plan.userId != userId) return@launch

            // Double-click / concurrency protection
            if (!activePaymentPlanIds.add(plan.id)) return@launch

            try {
                val result = repository.payInstallmentAuthoritative(userId, plan.id, walletId, notes)
                if (result.isFailure) {
                    android.util.Log.e("FinanceViewModel", "Failed to process installment payment", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                android.util.Log.e("FinanceViewModel", "Failed to process installment payment", e)
            } finally {
                activePaymentPlanIds.remove(plan.id)
            }
        }
    }

    val assets: StateFlow<List<AssetItem>> = combine(SessionManager.activeUserId, isStealthMode) { userId, isStealth ->
        userId to isStealth
    }.flatMapLatest { (userId, isStealth) ->
        if (isStealth) flowOf(emptyList())
        else if (userId != null && userId > 0L) assetDao.getAllAssets(userId)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveAsset(asset: AssetItem) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            repository.saveAsset(asset, userId)
        }
    }

    fun deleteAsset(asset: AssetItem) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            repository.deleteAsset(asset, userId)
        }
    }

    fun deleteAssetsByCategory(category: AssetCategory) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            assets.value.filter { it.category == category }.forEach {
                repository.deleteAsset(it, userId)
            }
        }
    }

    fun clearAllAssets() {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            repository.clearAllAssets(userId)
        }
    }

    val budgetLimits: StateFlow<List<BudgetLimitItem>> = combine(SessionManager.activeUserId, isStealthMode) { userId, isStealth ->
        userId to isStealth
    }.flatMapLatest { (userId, isStealth) ->
        if (isStealth) flowOf(emptyList())
        else if (userId != null && userId > 0L) budgetLimitDao.getAllBudgetLimits(userId)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveBudgetLimit(id: String, amount: Double, isCategory: Boolean) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            repository.saveBudgetLimit(BudgetLimitItem(id = id, limitAmount = amount, isCategory = isCategory, userId = userId))
        }
    }

    fun deleteBudgetLimit(item: BudgetLimitItem) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            repository.deleteBudgetLimit(item, userId)
        }
    }

    fun resetAllBudgetLimits() {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            repository.clearAllBudgetLimits(userId)
        }
    }

    val splitBillHistory: StateFlow<List<SplitBillHistoryItem>> = combine(SessionManager.activeUserId, isStealthMode) { userId, isStealth ->
        userId to isStealth
    }.flatMapLatest { (userId, isStealth) ->
        if (isStealth) flowOf(emptyList())
        else if (userId != null && userId > 0L) splitBillHistoryDao.getAllSplitBillHistory(userId)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveSplitBillHistory(merchantName: String, date: String, totalAmount: Double, formattedText: String) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            repository.saveSplitBillHistory(
                SplitBillHistoryItem(
                    merchantName = merchantName,
                    date = date,
                    totalAmount = totalAmount,
                    formattedText = formattedText,
                    userId = userId
                )
            )
        }
    }

    fun deleteSplitBillHistory(item: SplitBillHistoryItem) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            repository.deleteSplitBillHistory(item, userId)
        }
    }

    fun clearAllSplitBillHistory() {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            repository.clearAllSplitBillHistory(userId)
        }
    }

    val wallets: StateFlow<List<Wallet>> = combine(SessionManager.activeUserId, _accountMode, isStealthMode) { userId, mode, isStealth ->
        Triple(userId, mode, isStealth)
    }.flatMapLatest { (userId, mode, isStealth) ->
        if (isStealth) {
            flowOf(
                listOf(
                    Wallet(
                        id = -999L,
                        name = "Uang Saku",
                        balance = 25000.0,
                        type = WalletType.CASH,
                        colorHex = "#10B981",
                        iconName = "cash",
                        accountMode = mode.name,
                        isDefault = true,
                        userId = userId ?: 0L
                    )
                )
            )
        } else if (userId != null && userId > 0L) {
            repository.getWallets(userId, mode)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWallets: StateFlow<List<Wallet>> = combine(SessionManager.activeUserId, isStealthMode) { userId, isStealth ->
        userId to isStealth
    }.flatMapLatest { (userId, isStealth) ->
        if (isStealth) {
            flowOf(
                listOf(
                    Wallet(
                        id = -999L,
                        name = "Uang Saku",
                        balance = 25000.0,
                        type = WalletType.CASH,
                        colorHex = "#10B981",
                        iconName = "cash",
                        accountMode = AccountMode.PERSONAL.name,
                        isDefault = true,
                        userId = userId ?: 0L
                    )
                )
            )
        } else if (userId != null && userId > 0L) {
            repository.getAllWallets(userId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransactions: StateFlow<List<TransactionItem>> = combine(SessionManager.activeUserId, _accountMode, isStealthMode) { userId, mode, isStealth ->
        Triple(userId, mode, isStealth)
    }.flatMapLatest { (userId, mode, isStealth) ->
        if (isStealth) {
            flowOf(
                listOf(
                    TransactionItem(
                        id = -998L,
                        title = "Makan Siang Warteg",
                        amount = 15000.0,
                        type = TransactionType.EXPENSE,
                        category = "Makanan & Minuman",
                        walletId = -999L,
                        walletName = "Uang Saku",
                        timestamp = System.currentTimeMillis() - 7200000L,
                        note = "Nasi rames",
                        userId = userId ?: 0L
                    ),
                    TransactionItem(
                        id = -999L,
                        title = "Parkir Motor",
                        amount = 2000.0,
                        type = TransactionType.EXPENSE,
                        category = "Transportasi",
                        walletId = -999L,
                        walletName = "Uang Saku",
                        timestamp = System.currentTimeMillis() - 14400000L,
                        note = "Parkir",
                        userId = userId ?: 0L
                    )
                )
            )
        } else if (userId != null && userId > 0L) {
            repository.getTransactions(userId, mode)
        } else {
            flowOf(emptyList())
        }
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

    val categories: StateFlow<List<CategoryItem>> = SessionManager.activeUserId.flatMapLatest { userId ->
        if (userId != null && userId > 0L) repository.getAllCategories(userId) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())



    private val _aggregationError = MutableStateFlow<String?>(null)
    val aggregationError: StateFlow<String?> = _aggregationError.asStateFlow()

    val unassignedCurrencyWallets: StateFlow<List<Wallet>> = wallets.map { list ->
        list.filter { !it.hasAssignedCurrency }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unresolvedCurrencyTransactions: StateFlow<List<TransactionItem>> = allTransactions.map { list ->
        list.filter { it.currencyCode.isNullOrBlank() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDashboardCurrency = MutableStateFlow<String?>(null)
    val selectedDashboardCurrency: StateFlow<String?> = _selectedDashboardCurrency.asStateFlow()

    fun setSelectedDashboardCurrency(currencyCode: String?) {
        _selectedDashboardCurrency.value = currencyCode
    }

    val activeDashboardCurrency: StateFlow<String?> = combine(
        _selectedDashboardCurrency,
        wallets
    ) { explicitCurrency, walletList ->
        if (explicitCurrency != null) {
            explicitCurrency
        } else {
            val assignedCurrencies = walletList
                .filter { it.hasAssignedCurrency && !it.isArchived }
                .mapNotNull { it.currencyCode }
                .distinct()
            if (assignedCurrencies.size == 1) {
                assignedCurrencies.first()
            } else {
                null // Multi-currency or unassigned requires explicit user selection
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val totalBalanceMinorUnits: StateFlow<Long?> = combine(
        wallets,
        activeDashboardCurrency,
        _accountMode
    ) { list, activeCurr, _ ->
        if (activeCurr.isNullOrBlank()) null
        else {
            try {
                val matching = list.asSequence()
                    .filter { it.hasAssignedCurrency && !it.isArchived && it.currencyCode == activeCurr }
                    .map { it.balanceMinorUnits }
                val res = safeSumMinorUnits(matching)
                _aggregationError.value = null
                res
            } catch (e: ArithmeticException) {
                _aggregationError.value = AppStrings.t(
                    "Kalkulasi saldo melampaui batas numerik 64-bit (Overflow)",
                    "Balance calculation exceeded 64-bit numeric limit (Overflow)"
                )
                null
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val totalBalance: StateFlow<Double?> = combine(
        totalBalanceMinorUnits,
        activeDashboardCurrency
    ) { minorUnits, curr ->
        if (minorUnits == null || curr.isNullOrBlank()) null
        else com.example.util.CurrencyRegistry.canonicalMinorUnitsToDisplay(minorUnits, curr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val multiCurrencyBalances: StateFlow<List<CurrencyBalanceSummary>> = wallets.map { list ->
        list.filter { it.hasAssignedCurrency && !it.isArchived && !it.currencyCode.isNullOrBlank() }
            .groupBy { it.currencyCode!! }
            .map { (code, wList) ->
                val sumMinor = safeSumMinorUnits(wList.asSequence().map { it.balanceMinorUnits })
                CurrencyBalanceSummary(
                    currencyCode = code,
                    totalBalanceMinorUnits = sumMinor,
                    walletCount = wList.size
                )
            }.sortedByDescending { it.totalBalanceMinorUnits }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalAssetsValue: StateFlow<Double> = assets.map { list ->
        list.sumOf { it.currentValue }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalDebtsAmount: StateFlow<Double> = combine(
        debts,
        installmentPlans
    ) { debtList, planList ->
        val standardDebts = debtList.sumOf { it.amount }
        val installmentDebts = planList
            .filter { it.isActive }
            .sumOf { it.totalRemainingPrincipal ?: 0.0 }
        standardDebts + installmentDebts
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val netWorth: StateFlow<Double> = combine(
        totalBalance,
        totalAssetsValue,
        totalDebtsAmount
    ) { balance, assetsValue, debtsValue ->
        (balance ?: 0.0) + assetsValue - debtsValue
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

    val totalIncomeMinorUnits: StateFlow<Long?> = combine(
        filteredTransactions,
        activeDashboardCurrency,
        _selectedTimeframe
    ) { txList, activeCurr, _ ->
        if (activeCurr.isNullOrBlank()) null
        else {
            try {
                val matching = txList.asSequence()
                    .filter { it.type == TransactionType.INCOME && it.currencyCode == activeCurr }
                    .map { it.amountMinorUnits }
                safeSumMinorUnits(matching)
            } catch (e: ArithmeticException) {
                _aggregationError.value = AppStrings.t(
                    "Kalkulasi pemasukan melampaui batas numerik 64-bit (Overflow)",
                    "Income calculation exceeded 64-bit numeric limit (Overflow)"
                )
                null
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val totalIncome: StateFlow<Double?> = combine(
        totalIncomeMinorUnits,
        activeDashboardCurrency
    ) { minorUnits, curr ->
        if (minorUnits == null || curr.isNullOrBlank()) null
        else com.example.util.CurrencyRegistry.canonicalMinorUnitsToDisplay(minorUnits, curr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val totalExpenseMinorUnits: StateFlow<Long?> = combine(
        filteredTransactions,
        activeDashboardCurrency,
        _selectedTimeframe
    ) { txList, activeCurr, _ ->
        if (activeCurr.isNullOrBlank()) null
        else {
            try {
                val matching = txList.asSequence()
                    .filter { it.type == TransactionType.EXPENSE && it.currencyCode == activeCurr }
                    .map { it.amountMinorUnits }
                safeSumMinorUnits(matching)
            } catch (e: ArithmeticException) {
                _aggregationError.value = AppStrings.t(
                    "Kalkulasi pengeluaran melampaui batas numerik 64-bit (Overflow)",
                    "Expense calculation exceeded 64-bit numeric limit (Overflow)"
                )
                null
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val totalExpense: StateFlow<Double?> = combine(
        totalExpenseMinorUnits,
        activeDashboardCurrency
    ) { minorUnits, curr ->
        if (minorUnits == null || curr.isNullOrBlank()) null
        else com.example.util.CurrencyRegistry.canonicalMinorUnitsToDisplay(minorUnits, curr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val netCashflowMinorUnits: StateFlow<Long?> = combine(
        totalIncomeMinorUnits,
        totalExpenseMinorUnits
    ) { inc, exp ->
        if (inc == null || exp == null) null
        else {
            try {
                java.math.BigInteger.valueOf(inc)
                    .subtract(java.math.BigInteger.valueOf(exp))
                    .longValueExact()
            } catch (e: ArithmeticException) {
                _aggregationError.value = AppStrings.t(
                    "Kalkulasi arus kas bersih melampaui batas numerik 64-bit (Overflow)",
                    "Net cashflow calculation exceeded 64-bit numeric limit (Overflow)"
                )
                null
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val multiCurrencyCashflows: StateFlow<List<CurrencyCashflowSummary>> = filteredTransactions.map { txList ->
        txList.filter { !it.currencyCode.isNullOrBlank() }
            .groupBy { it.currencyCode!! }
            .map { (curr, list) ->
                val incMinor = safeSumMinorUnits(list.asSequence().filter { it.type == TransactionType.INCOME }.map { it.amountMinorUnits })
                val expMinor = safeSumMinorUnits(list.asSequence().filter { it.type == TransactionType.EXPENSE }.map { it.amountMinorUnits })
                val netMinor = try {
                    java.math.BigInteger.valueOf(incMinor)
                        .subtract(java.math.BigInteger.valueOf(expMinor))
                        .longValueExact()
                } catch (e: ArithmeticException) {
                    0L
                }
                CurrencyCashflowSummary(
                    currencyCode = curr,
                    totalIncomeMinorUnits = incMinor,
                    totalExpenseMinorUnits = expMinor,
                    netCashflowMinorUnits = netMinor,
                    transactionCount = list.size
                )
            }.sortedByDescending { it.transactionCount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        tags: String = "",
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId()
            if (userId == null || userId <= 0L) {
                onError?.invoke(AppStrings.t("Sesi login tidak aktif. Silakan masuk akun terlebih dahulu.", "Login session inactive. Please log in first."))
                return@launch
            }
            if (amount <= 0.0) {
                onError?.invoke(AppStrings.t("Nominal transaksi harus lebih dari Rp 0.", "Transaction amount must be greater than 0."))
                return@launch
            }

            if (type == TransactionType.TRANSFER) {
                if (targetWalletId == null || targetWalletId <= 0L) {
                    onError?.invoke(AppStrings.t("Dompet tujuan transfer harus dipilih.", "Target wallet must be selected."))
                    return@launch
                }
                if (walletId == targetWalletId) {
                    onError?.invoke(AppStrings.t("Dompet sumber dan dompet tujuan transfer tidak boleh sama.", "Source and target wallets cannot be the same."))
                    return@launch
                }
            }

            var wallet = repository.getWalletById(userId, walletId)
            if (wallet == null) {
                // If wallet is missing (e.g. newly registered user with zero wallets), provision defaults and check again
                repository.provisionDefaultWalletsIfEmpty(userId)
                wallet = repository.getWalletById(userId, walletId) ?: repository.getAllWallets(userId).first().firstOrNull()
            }

            if (wallet == null) {
                onError?.invoke(com.example.util.AppStrings.t("Dompet tidak ditemukan atau bukan milik akun ini.", "Wallet not found or does not belong to this account."))
                return@launch
            }

            try {
                val item = TransactionItem(
                    title = title.ifBlank { AppStrings.t("Transaksi", "Transaction") },
                    amount = amount,
                    type = type,
                    category = category.ifBlank { AppStrings.t("Umum", "General") },
                    categoryIcon = categoryIcon.ifBlank { "payments" },
                    walletId = wallet.id,
                    walletName = wallet.name,
                    targetWalletId = targetWalletId,
                    targetWalletName = targetWalletName,
                    timestamp = timestamp,
                    note = note,
                    tags = tags,
                    accountMode = _accountMode.value.name,
                    userId = userId
                )
                repository.insertTransaction(userId, item)
                _isAddTransactionOpen.value = false
                onSuccess?.invoke()
            } catch (e: Exception) {
                val prefix = AppStrings.t("Gagal menyimpan transaksi: ", "Failed to save transaction: ")
                val fallback = AppStrings.t("Kesalahan database", "Database error")
                onError?.invoke("$prefix${e.message ?: fallback}")
            }
        }
    }

    fun deleteTransaction(transaction: TransactionItem) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            repository.deleteTransaction(userId, transaction)
        }
    }

    fun saveWallet(
        name: String,
        type: com.example.data.model.WalletType,
        accountNumber: String,
        initialBalance: Double,
        colorHex: String,
        iconName: String,
        existingId: Long? = null,
        currencyCode: String? = null
    ) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            if (existingId == null || existingId == 0L) {
                val resolvedCurrency = currencyCode ?: com.example.util.DeviceRegionResolver.resolveDeviceCurrencyCode()
                val newWallet = Wallet(
                    name = name,
                    type = type,
                    accountNumber = accountNumber,
                    balance = initialBalance,
                    colorHex = colorHex,
                    iconName = iconName,
                    currencyCode = resolvedCurrency,
                    accountMode = _accountMode.value.name,
                    isDefault = wallets.value.isEmpty(),
                    userId = userId
                )
                repository.insertWallet(newWallet)
            } else {
                val current = repository.getWalletById(userId, existingId)
                if (current != null) {
                    val updated = current.copy(
                        name = name,
                        type = type,
                        accountNumber = accountNumber,
                        colorHex = colorHex,
                        iconName = iconName
                    )
                    repository.updateWallet(userId, updated)
                }
            }
            closeWalletDialog()
        }
    }

    fun claimLegacyWallet(walletId: Long, onResult: (Result<Int>) -> Unit = {}) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            val result = repository.claimLegacyWalletAtomically(walletId, userId)
            onResult(result)
        }
    }

    fun assignWalletCurrency(walletId: Long, currencyCode: String, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            val result = repository.assignWalletCurrency(walletId, currencyCode, userId)
            onResult(result)
        }
    }

    suspend fun getLegacyUnownedWallets(): List<Wallet> {
        return repository.getLegacyUnownedWallets()
    }

    val deleteWalletErrorMessage = MutableStateFlow<String?>(null)

    fun clearDeleteWalletError() {
        deleteWalletErrorMessage.value = null
    }

    fun deleteWallet(wallet: Wallet, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            try {
                repository.deleteWallet(userId, wallet)
                closeDeleteWalletConfirm()
            } catch (e: Exception) {
                val msg = e.message ?: AppStrings.t("Gagal menghapus dompet.", "Failed to delete wallet.")
                deleteWalletErrorMessage.value = msg
                onError(msg)
            }
        }
    }

    fun archiveWallet(walletId: Long, context: Context? = null, onResult: (Result<Int>) -> Unit = {}) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            val res = repository.archiveWallet(userId, walletId, context)
            onResult(res)
        }
    }

    fun unarchiveWallet(walletId: Long, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            val res = repository.unarchiveWallet(userId, walletId)
            onResult(res)
        }
    }

    fun transferArchivedWalletBalance(
        sourceWalletId: Long,
        targetWalletId: Long,
        onResult: (Result<Long>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            val res = repository.transferArchivedWalletBalance(userId, sourceWalletId, targetWalletId)
            onResult(res)
        }
    }

    fun confirmWalletDraft(
        draftId: String,
        isEntitlementUnlocked: () -> Boolean,
        onSuccess: (com.example.data.model.Wallet) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val isUnlocked = try {
                isEntitlementUnlocked()
            } catch (e: Exception) {
                false
            }
            if (!isUnlocked) {
                onError(AppStrings.t("Akses SmartMaster diperlukan untuk membuat dompet via AI.", "SmartMaster access required to create wallet via AI."))
                return@launch
            }
            val userId = SessionManager.getActiveUserId()
            if (userId == null || userId <= 0L) {
                onError(AppStrings.t("Sesi login tidak aktif.", "Login session inactive."))
                return@launch
            }
            val existingDraft = com.example.data.chatbot.parser.WalletDraftManager.getDraft(draftId)
            if (existingDraft == null || existingDraft.ownerUserId <= 0L || existingDraft.ownerUserId != userId) {
                onError(AppStrings.t("Akses ditolak: Draft dompet ini tidak memiliki pemilik sah atau bukan milik sesi aktif.", "Access denied: This wallet draft has no valid owner or does not belong to active session."))
                return@launch
            }
            val result = com.example.data.chatbot.parser.WalletDraftManager.confirmAndSave(
                draftId = draftId,
                repository = repository,
                userId = userId,
                accountMode = _accountMode.value.name,
                isEntitlementUnlocked = isEntitlementUnlocked
            )
            result.onSuccess { wallet ->
                onSuccess(wallet)
            }.onFailure { err ->
                onError(err.localizedMessage ?: AppStrings.t("Gagal membuat dompet.", "Failed to create wallet."))
            }
        }
    }

    fun confirmTransactionDraft(
        draftId: String,
        isEntitlementUnlocked: () -> Boolean,
        onSuccess: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val isUnlocked = try {
                isEntitlementUnlocked()
            } catch (e: Exception) {
                false
            }
            if (!isUnlocked) {
                onError(if (com.example.util.LocaleHelper.isEnglish()) "SmartMaster access required to record transaction via AI." else "Akses SmartMaster diperlukan untuk menyimpan transaksi via AI.")
                return@launch
            }
            val userId = SessionManager.getActiveUserId()
            if (userId == null || userId <= 0L) {
                onError("Sesi login tidak aktif.")
                return@launch
            }
            val existingDraft = com.example.data.chatbot.parser.TransactionDraftManager.getDraft(draftId)
            if (existingDraft == null || existingDraft.ownerUserId <= 0L || existingDraft.ownerUserId != userId) {
                onError(AppStrings.t("Akses ditolak: Draft transaksi ini tidak memiliki pemilik sah atau bukan milik sesi aktif.", "Access denied: This transaction draft does not have a valid owner or belong to the active session."))
                return@launch
            }
            val result = com.example.data.chatbot.parser.TransactionDraftManager.confirmAndSave(
                draftId = draftId,
                repository = repository,
                userId = userId,
                accountMode = _accountMode.value.name,
                isEntitlementUnlocked = isEntitlementUnlocked
            )
            result.onSuccess { txId ->
                onSuccess(txId)
            }.onFailure { err ->
                onError(err.localizedMessage ?: AppStrings.t("Gagal menyimpan transaksi.", "Failed to save transaction."))
            }
        }
    }

    fun insertCustomCategory(name: String, type: TransactionType, iconName: String = "category", colorHex: String = "#0B57D0") {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            val cat = CategoryItem(
                name = name,
                type = type,
                iconName = iconName,
                colorHex = colorHex,
                isSystem = false,
                userId = userId
            )
            repository.insertCategory(cat)
        }
    }

    fun deleteCategory(category: CategoryItem) {
        viewModelScope.launch {
            val userId = SessionManager.getActiveUserId() ?: return@launch
            repository.deleteCategory(userId, category)
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
                result.add(CashflowDataPoint(if (com.example.util.LocaleHelper.isEnglish()) "Today" else "Hari Ini", inc, exp, start))
            }
            TimeframeFilter.WEEKLY -> {
                // Last 7 days
                val dayFormat = java.text.SimpleDateFormat("EEE", com.example.util.LocaleHelper.getActiveLocale())
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
                val monthName = java.text.SimpleDateFormat("MMM", com.example.util.LocaleHelper.getActiveLocale()).format(cal.time)
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
                            label = "${if (com.example.util.LocaleHelper.isEnglish()) "W" else "M"}$week $monthName",
                            income = inc,
                            expense = exp,
                            timestamp = start
                        )
                    )
                }
            }
            TimeframeFilter.YEARLY, TimeframeFilter.ALL -> {
                // Last 6 months
                val monthFormat = java.text.SimpleDateFormat("MMM", com.example.util.LocaleHelper.getActiveLocale())
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
        /**
         * P0-1: Exact BigInteger Accumulation with Strict Overflow Rejection.
         * Accumulates using BigInteger and converts using longValueExact().
         * Throws ArithmeticException fail-closed if total exceeds Long.MAX_VALUE or is less than Long.MIN_VALUE.
         * Saturation to Long.MAX_VALUE / Long.MIN_VALUE is strictly prohibited.
         */
        fun safeSumMinorUnits(values: Iterable<Long>): Long {
            var sum = java.math.BigInteger.ZERO
            for (v in values) {
                sum = sum.add(java.math.BigInteger.valueOf(v))
            }
            return sum.longValueExact()
        }

        fun safeSumMinorUnits(values: Sequence<Long>): Long = safeSumMinorUnits(values.asIterable())

        /**
         * DEF-04: Multi-User Scoped Restore.
         * Replaces ONLY the financial records belonging to [targetUserId].
         * Never invokes clearAllTables().
         * Never touches other users' records, system categories (isSystem = 1, userId = NULL),
         * or quarantined legacy rows (userId = NULL).
         * Never overwrites or modifies the local User profile or password hash in users table.
         * Remaps wallet IDs safely and ensures all transaction/transfer relations point
         * strictly to restored user-scoped wallets.
         */
        suspend fun restoreUserDataInTransaction(
            database: FinSmartDatabase,
            payload: BackupDataPayload,
            targetUserId: Long
        ) {
            require(targetUserId > 0L) { "ID pengguna tidak valid: $targetUserId" }

            // 1. Validasi eksistensi akun pengguna aktif di basis data
            val activeUser = database.userDao().getUserById(targetUserId)
                ?: throw IllegalStateException("Akun pengguna (ID: $targetUserId) tidak ditemukan di database.")

            // 2. Validasi integritas dan skema payload sebelum mutasi basis data
            if (payload.version < 1) {
                throw IllegalArgumentException("Versi data cadangan tidak didukung: ${payload.version}")
            }

            // Validasi nilai transaksi
            for (tx in payload.transactions) {
                if (tx.amount.isNaN() || tx.amount.isInfinite() || tx.amount < 0.0) {
                    throw IllegalArgumentException(AppStrings.t("Jumlah transaksi '${tx.title}' tidak valid.", "Invalid amount for transaction '${tx.title}'."))
                }
            }

            // Validasi anggaran, hutang, dan aset
            for (b in payload.budgetLimits) {
                if (b.limitAmount.isNaN() || b.limitAmount.isInfinite() || b.limitAmount < 0.0) {
                    throw IllegalArgumentException(AppStrings.t("Batas anggaran untuk '${b.id}' tidak valid.", "Invalid budget limit for '${b.id}'."))
                }
            }
            for (d in payload.debts) {
                if (d.amount.isNaN() || d.amount.isInfinite() || d.amount < 0.0) {
                    throw IllegalArgumentException(AppStrings.t("Jumlah hutang '${d.name}' tidak valid.", "Invalid debt amount for '${d.name}'."))
                }
            }
            for (a in payload.assets) {
                if (a.initialValue.isNaN() || a.initialValue.isInfinite() || a.initialValue < 0.0) {
                    throw IllegalArgumentException(AppStrings.t("Nilai aset '${a.name}' tidak valid.", "Invalid asset value for '${a.name}'."))
                }
            }
            for (p in payload.installmentPlans) {
                if (p.installmentAmount.isNaN() || p.installmentAmount.isInfinite() || p.installmentAmount < 0.0) {
                    throw IllegalArgumentException(AppStrings.t("Nominal angsuran '${p.name}' tidak valid.", "Invalid installment amount for '${p.name}'."))
                }
            }

            // Validasi integritas relasi dompet sebelum mutasi basis data lokal (Zero Synthetic Wallets Rule)
            if (payload.wallets.isEmpty() && payload.transactions.isNotEmpty()) {
                throw IllegalArgumentException(AppStrings.t("Data cadangan tidak valid: Ditemukan transaksi tetapi tidak ada dompet dalam cadangan.", "Invalid backup data: Transactions found but no wallets present in backup."))
            }
            if (payload.wallets.isEmpty() && payload.recurringSchedules.isNotEmpty()) {
                throw IllegalArgumentException(AppStrings.t("Data cadangan tidak valid: Ditemukan jadwal berulang tetapi tidak ada dompet dalam cadangan.", "Invalid backup data: Recurring schedules found but no wallets present in backup."))
            }

            val payloadWalletIdSet = payload.wallets.map { it.id }.toSet()
            for (tx in payload.transactions) {
                if (tx.walletId !in payloadWalletIdSet) {
                    throw IllegalArgumentException(AppStrings.t("Integritas data cadangan tidak valid: Transaksi '${tx.title}' merujuk pada dompet (ID: ${tx.walletId}) yang tidak ditemukan dalam data cadangan.", "Backup data integrity invalid: Transaction '${tx.title}' references wallet (ID: ${tx.walletId}) not found in backup data."))
                }
                if (tx.type == com.example.data.model.TransactionType.TRANSFER) {
                    if (tx.targetWalletId == null || tx.targetWalletId !in payloadWalletIdSet) {
                        throw IllegalArgumentException(AppStrings.t("Transaksi transfer '${tx.title}' tidak memiliki dompet tujuan yang valid dalam cadangan.", "Transfer transaction '${tx.title}' does not have a valid target wallet in backup."))
                    }
                    if (tx.targetWalletId == tx.walletId) {
                        throw IllegalArgumentException(AppStrings.t("Transaksi transfer '${tx.title}' memiliki dompet asal dan tujuan yang sama.", "Transfer transaction '${tx.title}' has identical source and destination wallets."))
                    }
                } else if (tx.targetWalletId != null && tx.targetWalletId > 0L) {
                    if (tx.targetWalletId !in payloadWalletIdSet) {
                        throw IllegalArgumentException(AppStrings.t("Transaksi '${tx.title}' merujuk pada dompet tujuan (ID: ${tx.targetWalletId}) yang tidak ditemukan dalam cadangan.", "Transaction '${tx.title}' references target wallet (ID: ${tx.targetWalletId}) not found in backup."))
                    }
                }
            }
            for (rs in payload.recurringSchedules) {
                if (rs.amount.isNaN() || rs.amount.isInfinite() || rs.amount < 0.0) {
                    throw IllegalArgumentException(AppStrings.t("Nominal jadwal berulang '${rs.title}' tidak valid.", "Invalid amount for recurring schedule '${rs.title}'."))
                }
                if (rs.walletId !in payloadWalletIdSet) {
                    throw IllegalArgumentException(AppStrings.t("Jadwal berulang '${rs.title}' merujuk dompet (ID: ${rs.walletId}) yang tidak ditemukan dalam cadangan.", "Recurring schedule '${rs.title}' references wallet (ID: ${rs.walletId}) not found in backup."))
                }
            }

            // 3. Eksekusi mutasi dalam satu transaksi atomik Room di bawah Exclusive Maintenance permit
            database.writeCoordinator.withExclusiveMaintenance {
                database.withTransaction {
                // A. Scoped deletion: Hapus data finansial milik akun aktif (Child-First Scoped Deletion)
                database.transactionDao().clearAllTransactions(targetUserId)
                database.recurringScheduleDao().clearAllSchedules(targetUserId)
                database.splitBillHistoryDao().clearAllHistory(targetUserId)
                database.budgetLimitDao().clearAllBudgetLimits(targetUserId)
                database.debtDao().clearAllDebts(targetUserId)
                database.installmentPlanDao().clearAllInstallmentPlans(targetUserId)
                database.assetDao().clearAllAssets(targetUserId)
                database.categoryDao().clearCustomCategories(targetUserId)
                database.walletDao().clearAllWallets(targetUserId)

                // B. Insert Wallets & Susun Peta Remapping ID (Parent-First, Zero Synthetic Wallets)
                val walletIdMap = mutableMapOf<Long, Long>()
                val walletNameMap = mutableMapOf<Long, String>()

                for (wallet in payload.wallets) {
                    val oldId = wallet.id
                    val insertedId = database.walletDao().insertWallet(
                        wallet.copy(
                            id = 0L, // Auto-generate primary key baru di SQLite
                            userId = targetUserId,
                            currencyCode = wallet.currencyCode ?: com.example.util.DeviceRegionResolver.resolveDeviceCurrencyCode()
                        )
                    )
                    walletIdMap[oldId] = insertedId
                    walletNameMap[oldId] = wallet.name
                }

                // C. Insert Categories: Abaikan kategori sistem kanonik agar tidak terduplikasi
                val canonicalCategoryNames = FinSmartDatabase.DEFAULT_CATEGORIES
                    .map { it.name.trim().lowercase() }
                    .toSet()

                for (cat in payload.categories) {
                    if (cat.isSystem || cat.name.trim().lowercase() in canonicalCategoryNames) {
                        continue
                    }
                    database.categoryDao().insertCategory(
                        cat.copy(
                            id = 0L,
                            userId = targetUserId,
                            isSystem = false
                        )
                    )
                }

                // D. Insert Transactions dengan Peta Remapping yang Terverifikasi
                for (tx in payload.transactions) {
                    val mappedWalletId = walletIdMap[tx.walletId]
                        ?: throw IllegalStateException(AppStrings.t("Pemetaan dompet gagal untuk ID: ${tx.walletId}", "Wallet remapping failed for ID: ${tx.walletId}"))
                    val mappedTargetWalletId = if (tx.targetWalletId != null && tx.targetWalletId > 0L) {
                        walletIdMap[tx.targetWalletId]
                    } else null

                    val mappedWalletName = walletNameMap[tx.walletId] ?: tx.walletName
                    val mappedTargetWalletName = if (mappedTargetWalletId != null) {
                        walletNameMap[tx.targetWalletId] ?: tx.targetWalletName
                    } else null

                    database.transactionDao().insertTransaction(
                        tx.copy(
                            id = 0L,
                            walletId = mappedWalletId,
                            walletName = mappedWalletName,
                            targetWalletId = mappedTargetWalletId,
                            targetWalletName = mappedTargetWalletName,
                            userId = targetUserId
                        )
                    )
                }

                // Insert Recurring Schedules
                for (rs in payload.recurringSchedules) {
                    val mappedWalletId = walletIdMap[rs.walletId]
                        ?: throw IllegalStateException(AppStrings.t("Pemetaan dompet jadwal berulang gagal untuk ID: ${rs.walletId}", "Recurring schedule wallet remapping failed for ID: ${rs.walletId}"))
                    val scheduleId = if (rs.id.isBlank()) java.util.UUID.randomUUID().toString() else rs.id
                    database.recurringScheduleDao().insertSchedule(
                        rs.copy(
                            id = scheduleId,
                            walletId = mappedWalletId,
                            userId = targetUserId
                        )
                    )
                }

                // E. Insert Budget Limits
                val seenBudgetKeys = mutableSetOf<String>()
                for (budget in payload.budgetLimits) {
                    if (budget.id.isBlank() || budget.id in seenBudgetKeys) continue
                    seenBudgetKeys.add(budget.id)
                    database.budgetLimitDao().insertBudgetLimit(
                        budget.copy(
                            localId = 0L,
                            userId = targetUserId
                        )
                    )
                }

                // F. Insert Debts
                for (debt in payload.debts) {
                    val debtId = if (debt.id.isBlank()) java.util.UUID.randomUUID().toString() else debt.id
                    database.debtDao().insertDebt(
                        debt.copy(
                            id = debtId,
                            userId = targetUserId
                        )
                    )
                }

                // G. Insert Assets
                for (asset in payload.assets) {
                    val assetId = if (asset.id.isBlank()) java.util.UUID.randomUUID().toString() else asset.id
                    database.assetDao().insertAsset(
                        asset.copy(
                            id = assetId,
                            userId = targetUserId
                        )
                    )
                }

                // H. Insert Split Bill History
                for (history in payload.splitBillHistory) {
                    val historyId = if (history.id.isBlank()) java.util.UUID.randomUUID().toString() else history.id
                    database.splitBillHistoryDao().insertSplitBillHistory(
                        history.copy(
                            id = historyId,
                            userId = targetUserId
                        )
                    )
                }

                // I. Insert Installment Plans
                for (plan in payload.installmentPlans) {
                    val planId = if (plan.id.isBlank()) java.util.UUID.randomUUID().toString() else plan.id
                    database.installmentPlanDao().insertInstallmentPlan(
                        plan.copy(
                            id = planId,
                            userId = targetUserId
                        )
                    )
                }
                }

                // Phase 1G.6: Ensure target developer account cannot be reintroduced via restore,
                // executed under continuous exclusive maintenance lock (Reentrant via MaintenancePermitElement)
                FinSmartDatabase.purgeTargetDeveloperAccountAtomically(database)
            }
        }

        @Deprecated(
            message = "Use restoreUserDataInTransaction with explicit targetUserId. clearAllTables() is permanently removed.",
            replaceWith = ReplaceWith("restoreUserDataInTransaction(database, payload, targetUserId)")
        )
        fun restoreDatabaseInTransaction(
            database: FinSmartDatabase,
            payload: BackupDataPayload,
            targetUserId: Long
        ) {
            kotlinx.coroutines.runBlocking {
                restoreUserDataInTransaction(database, payload, targetUserId)
            }
        }
    }
}
