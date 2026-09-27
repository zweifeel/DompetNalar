package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ai.ReceiptAiOcrScanner
import com.example.data.model.ReceiptScanResult
import com.example.data.model.TransactionType
import com.example.data.model.UserRole
import com.example.ui.components.AddTransactionSheet
import com.example.ui.components.AssetTrackerModalDialog
import com.example.ui.components.AuthModalDialog
import com.example.ui.components.DebtPlannerModalDialog
import com.example.ui.components.DeleteAccountDialog
import com.example.ui.components.DeleteWalletConfirmDialog
import com.example.ui.components.DuressPinModalDialog
import com.example.ui.components.GamificationHubDialog
import com.example.ui.components.MasterDevPanelSheet
import com.example.ui.components.PersonalityQuizModalDialog
import com.example.ui.components.ProminentDisclosureDialog
import com.example.ui.components.ReceiptGeneratorModalDialog
import com.example.ui.components.SmartMasterPaywallDialog
import com.example.ui.components.WalletDialog
import com.example.ui.screens.AiChatScreen
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ReceiptScanScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplitBillScreen
import com.example.ui.screens.TransactionsScreen
import androidx.compose.material.icons.filled.Description
import com.example.ui.screens.ExportScreen
import com.example.ui.screens.WalletsScreen
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.FinanceViewModel

enum class NavigationScreen(val label: String, val icon: ImageVector, val isBottomNavTab: Boolean = true) {
    DASHBOARD("Dasbor", Icons.Default.Home, true),
    AI_CHAT("Input AI", Icons.Default.AutoAwesome, true),
    TRANSACTIONS("Mutasi", Icons.Default.ReceiptLong, true),
    WALLETS("Dompet", Icons.Default.AccountBalanceWallet, true),
    ANALYTICS("Analisis", Icons.Default.BarChart, true),
    SETTINGS("Pengaturan", Icons.Default.Settings, true),
    SCAN_RECEIPT("Scan Nota", Icons.Default.CameraAlt, false),
    SPLIT_BILL("Split Bill", Icons.Default.Group, false),
    BUDGET("Anggaran", Icons.Default.AccountBalanceWallet, false),
    EXPORT_REPORT("Export Laporan", Icons.Default.Description, false)
}

@Composable
fun FinSmartApp(
    financeViewModel: FinanceViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(NavigationScreen.DASHBOARD) }
    var activeReceiptForSplitBill by remember { mutableStateOf<ReceiptScanResult?>(null) }

    // User & Auth State
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val allRegisteredUsers by authViewModel.allRegisteredUsers.collectAsStateWithLifecycle()
    val isMasterDevPanelOpen by authViewModel.isMasterDevPanelOpen.collectAsStateWithLifecycle()
    val isPaywallOpen by authViewModel.isPaywallOpen.collectAsStateWithLifecycle()
    val isAuthModalOpen by authViewModel.isAuthModalOpen.collectAsStateWithLifecycle()
    val isDeleteAccountOpen by authViewModel.isDeleteAccountOpen.collectAsStateWithLifecycle()
    val isProminentDisclosureOpen by authViewModel.isProminentDisclosureOpen.collectAsStateWithLifecycle()
    val isGamificationHubOpen by authViewModel.isGamificationHubOpen.collectAsStateWithLifecycle()
    val isPersonalityQuizOpen by authViewModel.isPersonalityQuizOpen.collectAsStateWithLifecycle()
    val isDuressPinModalOpen by authViewModel.isDuressPinModalOpen.collectAsStateWithLifecycle()
    val isDebtPlannerOpen by authViewModel.isDebtPlannerOpen.collectAsStateWithLifecycle()
    val isAssetTrackerOpen by authViewModel.isAssetTrackerOpen.collectAsStateWithLifecycle()
    val isReceiptGeneratorOpen by authViewModel.isReceiptGeneratorOpen.collectAsStateWithLifecycle()
    val gamificationState by authViewModel.gamificationState.collectAsStateWithLifecycle()
    val pendingOtpCode by authViewModel.pendingOtpCode.collectAsStateWithLifecycle()
    val authError by authViewModel.authError.collectAsStateWithLifecycle()
    val toastMessage by authViewModel.isSuccessToast.collectAsStateWithLifecycle()

    // Google Drive Sync States
    val isDriveConnected by authViewModel.isDriveConnected.collectAsStateWithLifecycle()
    val driveEmail by authViewModel.driveEmail.collectAsStateWithLifecycle()
    val driveLastBackup by authViewModel.driveLastBackup.collectAsStateWithLifecycle()
    val driveAutoSync by authViewModel.driveAutoSync.collectAsStateWithLifecycle()

    // Financial State
    val accountMode by financeViewModel.accountMode.collectAsStateWithLifecycle()
    val isPrivacyMode by financeViewModel.isPrivacyMode.collectAsStateWithLifecycle()
    val profilePhotoUri by authViewModel.profilePhotoUri.collectAsStateWithLifecycle()
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            authViewModel.updateProfilePhotoUri(it.toString())
            Toast.makeText(context, "Foto profil berhasil diperbarui!", Toast.LENGTH_SHORT).show()
        }
    }
    val totalBalance by financeViewModel.totalBalance.collectAsStateWithLifecycle()
    val totalIncome by financeViewModel.totalIncome.collectAsStateWithLifecycle()
    val totalExpense by financeViewModel.totalExpense.collectAsStateWithLifecycle()
    val wallets by financeViewModel.wallets.collectAsStateWithLifecycle()
    val allWallets by financeViewModel.allWallets.collectAsStateWithLifecycle()
    val allTransactions by financeViewModel.allTransactions.collectAsStateWithLifecycle()
    val filteredTransactions by financeViewModel.filteredTransactions.collectAsStateWithLifecycle()
    val categories by financeViewModel.categories.collectAsStateWithLifecycle()
    val chartData by financeViewModel.chartData.collectAsStateWithLifecycle()
    val selectedTimeframe by financeViewModel.selectedTimeframe.collectAsStateWithLifecycle()
    val searchQuery by financeViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategoryFilter by financeViewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val selectedWalletFilter by financeViewModel.selectedWalletFilter.collectAsStateWithLifecycle()
    val splitBillHistory by financeViewModel.splitBillHistory.collectAsStateWithLifecycle()
    val budgetLimits by financeViewModel.budgetLimits.collectAsStateWithLifecycle()

    val isAddTransactionOpen by financeViewModel.isAddTransactionOpen.collectAsStateWithLifecycle()
    val defaultTransactionType by financeViewModel.defaultTransactionType.collectAsStateWithLifecycle()
    val isWalletDialogOpen by financeViewModel.isWalletDialogOpen.collectAsStateWithLifecycle()
    val editingWallet by financeViewModel.editingWallet.collectAsStateWithLifecycle()
    val deletingWallet by financeViewModel.deletingWallet.collectAsStateWithLifecycle()

    // Toast Feedback
    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            authViewModel.clearToast()
        }
    }

    BackHandler(enabled = currentScreen != NavigationScreen.DASHBOARD) {
        currentScreen = NavigationScreen.DASHBOARD
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationScreen.values().filter { it.isBottomNavTab }.forEach { screen ->
                    val isSelected = currentScreen == screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentScreen = screen },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.label
                            )
                        },
                        label = {
                            Text(
                                text = screen.label,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_tab_${screen.name.lowercase()}")
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentScreen == NavigationScreen.DASHBOARD || currentScreen == NavigationScreen.TRANSACTIONS) {
                FloatingActionButton(
                    onClick = { financeViewModel.openAddTransaction(TransactionType.EXPENSE) },
                    containerColor = FinSmartPrimary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    modifier = Modifier.testTag("fab_add_transaction")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Catat Transaksi Cepat",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentScreen) {
                NavigationScreen.DASHBOARD -> {
                    DashboardScreen(
                        totalBalance = totalBalance,
                        totalIncome = totalIncome,
                        totalExpense = totalExpense,
                        accountMode = accountMode,
                        isPrivacyMode = isPrivacyMode,
                        wallets = wallets,
                        recentTransactions = allTransactions,
                        chartData = chartData,
                        selectedTimeframe = selectedTimeframe,
                        currentUser = currentUser,
                        trialDaysRemaining = authViewModel.getTrialDaysRemaining(),
                        onTimeframeSelected = { financeViewModel.setTimeframe(it) },
                        onTogglePrivacy = { financeViewModel.togglePrivacyMode() },
                        onToggleAccountMode = { financeViewModel.toggleAccountMode() },
                        onAddIncomeClick = { financeViewModel.openAddTransaction(TransactionType.INCOME) },
                        onAddExpenseClick = { financeViewModel.openAddTransaction(TransactionType.EXPENSE) },
                        onTransferClick = { financeViewModel.openAddTransaction(TransactionType.TRANSFER) },
                        onAddWalletClick = { financeViewModel.openWalletDialog() },
                        onEditWallet = { financeViewModel.openWalletDialog(it) },
                        onDeleteWallet = { financeViewModel.openDeleteWalletConfirm(it) },
                        onDeleteTransaction = { financeViewModel.deleteTransaction(it) },
                        onViewAllTransactionsClick = { currentScreen = NavigationScreen.TRANSACTIONS },
                        onOpenAiChat = { currentScreen = NavigationScreen.AI_CHAT },
                        onOpenScanReceipt = { currentScreen = NavigationScreen.SCAN_RECEIPT },
                        onOpenSplitBill = {
                            activeReceiptForSplitBill = ReceiptAiOcrScanner.generateRealisticReceiptFallback()
                            currentScreen = NavigationScreen.SPLIT_BILL
                        },
                        onNavigateToBudget = {
                            currentScreen = NavigationScreen.BUDGET
                        },
                        onBadgeClick = {
                            if (currentUser?.role == UserRole.MASTER_DEV) {
                                authViewModel.openMasterDevPanel()
                            } else {
                                authViewModel.openPaywall()
                            }
                        },
                        onAvatarClick = {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        profilePhotoUri = profilePhotoUri
                    )
                }

                NavigationScreen.SCAN_RECEIPT -> {
                    ReceiptScanScreen(
                        wallets = wallets,
                        categories = categories,
                        accountMode = accountMode,
                        isUnlocked = authViewModel.isFeatureUnlocked(),
                        onTriggerPaywall = { authViewModel.openPaywall() },
                        onNavigateBack = { currentScreen = NavigationScreen.DASHBOARD },
                        onOpenSplitBill = { receiptResult ->
                            activeReceiptForSplitBill = receiptResult
                            currentScreen = NavigationScreen.SPLIT_BILL
                        },
                        onSaveTransaction = { title, amount, type, category, categoryIcon, walletId, walletName, targetWalletId, targetWalletName, timestamp, note, tags ->
                            financeViewModel.saveTransaction(
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
                                tags = tags
                            )
                        }
                    )
                }

                NavigationScreen.SPLIT_BILL -> {
                    SplitBillScreen(
                        receipt = activeReceiptForSplitBill ?: ReceiptAiOcrScanner.generateRealisticReceiptFallback(),
                        wallets = wallets,
                        currentUsername = currentUser?.username ?: "Saya",
                        splitBillHistory = splitBillHistory,
                        onSaveSplitBillHistory = { merchant, date, total, formattedText ->
                            financeViewModel.saveSplitBillHistory(merchant, date, total, formattedText)
                        },
                        onDeleteSplitBillHistory = { item ->
                            financeViewModel.deleteSplitBillHistory(item)
                        },
                        onNavigateBack = { currentScreen = NavigationScreen.DASHBOARD },
                        onAddNewWallet = { name, type, accountNumber, initialBalance, colorHex, iconName ->
                            financeViewModel.saveWallet(
                                name = name,
                                type = type,
                                accountNumber = accountNumber,
                                initialBalance = initialBalance,
                                colorHex = colorHex,
                                iconName = iconName
                            )
                        }
                    )
                }

                NavigationScreen.AI_CHAT -> {
                    AiChatScreen(
                        wallets = wallets,
                        categories = categories,
                        allTransactions = allTransactions,
                        budgetLimits = budgetLimits,
                        accountMode = accountMode,
                        isUnlocked = authViewModel.isFeatureUnlocked(),
                        onTriggerPaywall = { authViewModel.openPaywall() },
                        onSaveTransaction = { title, amount, type, category, categoryIcon, walletId, walletName, targetWalletId, targetWalletName, timestamp, note, tags ->
                            financeViewModel.saveTransaction(
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
                                tags = tags
                            )
                        }
                    )
                }

                NavigationScreen.BUDGET -> {
                    com.example.ui.screens.BudgetScreen(
                        budgetLimits = budgetLimits,
                        currentMonthExpenses = financeViewModel.currentMonthExpensesList.collectAsStateWithLifecycle().value,
                        categories = categories,
                        onSaveBudgetLimit = { id, amount, isCategory ->
                            financeViewModel.saveBudgetLimit(id, amount, isCategory)
                        },
                        onDeleteBudgetLimit = { item ->
                            financeViewModel.deleteBudgetLimit(item)
                        },
                        onResetAllBudgetLimits = {
                            financeViewModel.resetAllBudgetLimits()
                        },
                        onAddCustomCategory = { name, _ ->
                            financeViewModel.insertCustomCategory(name, TransactionType.EXPENSE)
                        },
                        onDeleteCategory = { category ->
                            financeViewModel.deleteCategory(category)
                        },
                        onNavigateBack = { currentScreen = NavigationScreen.DASHBOARD }
                    )
                }

                NavigationScreen.TRANSACTIONS -> {
                    TransactionsScreen(
                        transactions = filteredTransactions,
                        wallets = wallets,
                        categories = categories,
                        accountMode = accountMode,
                        isPrivacyMode = isPrivacyMode,
                        searchQuery = searchQuery,
                        selectedCategoryFilter = selectedCategoryFilter,
                        selectedWalletFilter = selectedWalletFilter,
                        selectedTimeframe = selectedTimeframe,
                        onSearchChange = { financeViewModel.setSearchQuery(it) },
                        onCategoryFilterChange = { financeViewModel.setCategoryFilter(it) },
                        onWalletFilterChange = { financeViewModel.setWalletFilter(it) },
                        onTimeframeChange = { financeViewModel.setTimeframe(it) },
                        onDeleteTransaction = { financeViewModel.deleteTransaction(it) },
                        onOpenExportReport = { currentScreen = NavigationScreen.EXPORT_REPORT },
                        currentUser = currentUser
                    )
                }

                NavigationScreen.WALLETS -> {
                    WalletsScreen(
                        wallets = wallets,
                        accountMode = accountMode,
                        isPrivacyMode = isPrivacyMode,
                        onAddWalletClick = { financeViewModel.openWalletDialog() },
                        onEditWallet = { financeViewModel.openWalletDialog(it) },
                        onDeleteWallet = { financeViewModel.openDeleteWalletConfirm(it) },
                        onTransferClick = { financeViewModel.openAddTransaction(TransactionType.TRANSFER) },
                        onOpenAssetTracker = { authViewModel.openAssetTracker() },
                        onOpenDebtPlanner = { authViewModel.openDebtPlanner() }
                    )
                }

                NavigationScreen.ANALYTICS -> {
                    AnalyticsScreen(
                        transactions = allTransactions,
                        chartData = chartData,
                        selectedTimeframe = selectedTimeframe,
                        onTimeframeSelected = { financeViewModel.setTimeframe(it) },
                        accountMode = accountMode,
                        isPrivacyMode = isPrivacyMode,
                        currentUser = currentUser
                    )
                }

                NavigationScreen.SETTINGS -> {
                    SettingsScreen(
                        currentUser = currentUser,
                        accountMode = accountMode,
                        isPrivacyMode = isPrivacyMode,
                        onTogglePrivacy = { financeViewModel.togglePrivacyMode() },
                        onToggleAccountMode = { financeViewModel.toggleAccountMode() },
                        onOpenMasterDevPanel = { authViewModel.openMasterDevPanel() },
                        onOpenPaywall = { authViewModel.openPaywall() },
                        onOpenAuthModal = { authViewModel.openAuthModal() },
                        onOpenDeleteAccount = { authViewModel.openDeleteAccount() },
                        onOpenProminentDisclosure = { authViewModel.openProminentDisclosure() },
                        onTogglePlayStoreMode = { authViewModel.togglePlayStoreBuildMode() },
                        onLogout = { authViewModel.logout() },
                        onOpenExportReport = { currentScreen = NavigationScreen.EXPORT_REPORT },
                        isDriveConnected = isDriveConnected,
                        driveEmail = driveEmail ?: "",
                        driveLastBackup = driveLastBackup,
                        driveAutoSync = driveAutoSync,
                        onConnectDrive = { email -> authViewModel.connectGoogleDrive(email) },
                        onDisconnectDrive = { authViewModel.disconnectGoogleDrive() },
                        onToggleAutoSync = { enabled -> authViewModel.toggleAutoSync(enabled) },
                        onTriggerBackup = { authViewModel.triggerManualBackup() },
                        onTriggerRestore = { authViewModel.triggerManualRestore() },
                        onOpenGamificationHub = { authViewModel.openGamificationHub() },
                        onOpenPersonalityQuiz = { authViewModel.openPersonalityQuiz() },
                        onOpenDuressPinModal = { authViewModel.openDuressPinModal() },
                        onOpenDebtPlanner = { authViewModel.openDebtPlanner() },
                        onOpenAssetTracker = { authViewModel.openAssetTracker() },
                        onOpenReceiptGenerator = { authViewModel.openReceiptGenerator() },
                        profilePhotoUri = profilePhotoUri,
                        onAvatarClick = {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        dailyStreak = gamificationState.dailyStreak
                    )
                }

                NavigationScreen.EXPORT_REPORT -> {
                    ExportScreen(
                        allTransactions = allTransactions,
                        currentAccountMode = accountMode,
                        username = currentUser?.username ?: "Lukas",
                        onNavigateBack = { currentScreen = NavigationScreen.DASHBOARD }
                    )
                }
            }

            // Modal Sheets and Dialogs
            if (isAddTransactionOpen) {
                AddTransactionSheet(
                    initialType = defaultTransactionType,
                    wallets = if (defaultTransactionType == TransactionType.TRANSFER) allWallets else wallets,
                    allCategories = categories,
                    onDismiss = { financeViewModel.closeAddTransaction() },
                    onSave = { title, amount, type, category, categoryIcon, walletId, walletName, targetWalletId, targetWalletName, timestamp, note, tags ->
                        financeViewModel.saveTransaction(
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
                            tags = tags
                        )
                    }
                )
            }

            if (isWalletDialogOpen) {
                WalletDialog(
                    existingWallet = editingWallet,
                    onDismiss = { financeViewModel.closeWalletDialog() },
                    onSave = { name, type, accountNumber, initialBalance, colorHex, iconName, existingId ->
                        financeViewModel.saveWallet(
                            name = name,
                            type = type,
                            accountNumber = accountNumber,
                            initialBalance = initialBalance,
                            colorHex = colorHex,
                            iconName = iconName,
                            existingId = existingId
                        )
                    }
                )
            }

            deletingWallet?.let { walletToDelete ->
                DeleteWalletConfirmDialog(
                    wallet = walletToDelete,
                    onConfirm = { financeViewModel.deleteWallet(walletToDelete) },
                    onDismiss = { financeViewModel.closeDeleteWalletConfirm() }
                )
            }

            // Master Dev User Management Sheet (Bab 2 & 3)
            if (isMasterDevPanelOpen) {
                MasterDevPanelSheet(
                    users = allRegisteredUsers,
                    onDismiss = { authViewModel.closeMasterDevPanel() },
                    onUpgradeUser = { userId -> authViewModel.upgradeUserToSmartMaster(userId) },
                    onDowngradeUser = { userId -> authViewModel.downgradeUserToMember(userId) }
                )
            }

            // Smart Master Paywall Upgrade Modal (Bab 3 & 13)
            if (isPaywallOpen) {
                SmartMasterPaywallDialog(
                    onDismiss = { authViewModel.closePaywall() },
                    onRequestUpgrade = { plan ->
                        Toast.makeText(context, "Permintaan upgrade terkirim. Menunggu konfirmasi approval.", Toast.LENGTH_LONG).show()
                    },
                    onWatchRewardedAd = {
                        authViewModel.grantTemporaryRewardAccess(24)
                    }
                )
            }

            // Authentication Modal (Login / Register OTP / Google / Guest) (Bab 2)
            if (isAuthModalOpen) {
                AuthModalDialog(
                    pendingOtpCode = pendingOtpCode,
                    authError = authError,
                    onDismiss = { authViewModel.closeAuthModal() },
                    onLogin = { id, pass -> authViewModel.login(id, pass) },
                    onSendOtp = { email -> authViewModel.generateOtpForRegistration(email) },
                    onVerifyOtpAndRegister = { username, email, pass, otp ->
                        authViewModel.registerWithOtp(username, email, pass, otp)
                    },
                    onGoogleLogin = { authViewModel.loginWithGoogleMock() },
                    onGuestLogin = { authViewModel.loginAsGuest() }
                )
            }

            // Delete Account & Local Data Dialog (Bab 2 & 13)
            if (isDeleteAccountOpen) {
                DeleteAccountDialog(
                    currentUser = currentUser,
                    onDismiss = { authViewModel.closeDeleteAccount() },
                    onConfirmDelete = { password ->
                        authViewModel.deleteAccountPermanently(password) {
                            currentScreen = NavigationScreen.DASHBOARD
                        }
                    }
                )
            }

            // Prominent Disclosure & GDPR Modal (Bab 13 & 14)
            if (isProminentDisclosureOpen) {
                ProminentDisclosureDialog(
                    onDismiss = { authViewModel.closeProminentDisclosure() }
                )
            }

            // Gamification & Rewards Hub Modal (Bab 6)
            if (isGamificationHubOpen) {
                GamificationHubDialog(
                    state = gamificationState,
                    onDismiss = { authViewModel.closeGamificationHub() },
                    onSelectAppIcon = { icon -> authViewModel.selectAppIconEdition(icon) },
                    onToggleNoSpendChallenge = { enabled -> authViewModel.toggleNoSpendChallenge(enabled) },
                    onUpdateCustomBackground = { uri, opacity -> authViewModel.updateCustomBackground(uri, opacity) },
                    onTriggerPersonalityTest = {
                        authViewModel.closeGamificationHub()
                        authViewModel.openPersonalityQuiz()
                    },
                    onClaimStreakReward = {
                        authViewModel.grantStreakMilestoneReward(24, 3)
                    }
                )
            }

            // Onboarding Financial Personality Test Modal (Bab 6)
            if (isPersonalityQuizOpen) {
                PersonalityQuizModalDialog(
                    onDismiss = { authViewModel.closePersonalityQuiz() },
                    onQuizCompleted = { personality ->
                        authViewModel.updatePersonality(personality)
                    }
                )
            }

            // Duress PIN & Security Suite Modal (Bab 10)
            if (isDuressPinModalOpen) {
                DuressPinModalDialog(
                    state = gamificationState,
                    onDismiss = { authViewModel.closeDuressPinModal() },
                    onSavePins = { mainPin, duressPin, bio ->
                        authViewModel.saveSecurityPins(mainPin, duressPin, bio)
                    },
                    onVerifyPinAndUnlock = { inputPin ->
                        authViewModel.verifyPinAndUnlock(inputPin)
                    }
                )
            }

            // AI Debt Payoff Strategy Planner Modal (Bab 12)
            if (isDebtPlannerOpen) {
                DebtPlannerModalDialog(
                    onDismiss = { authViewModel.closeDebtPlanner() }
                )
            }

            // Asset & Depreciating Asset Tracker Modal (Bab 12)
            if (isAssetTrackerOpen) {
                AssetTrackerModalDialog(
                    totalWalletBalance = totalBalance,
                    onDismiss = { authViewModel.closeAssetTracker() }
                )
            }

            // Business Merchant Receipt Generator & Thermal Printing Modal (Bab 12)
            if (isReceiptGeneratorOpen) {
                ReceiptGeneratorModalDialog(
                    transaction = allTransactions.firstOrNull(),
                    onDismiss = { authViewModel.closeReceiptGenerator() }
                )
            }
        }
    }
}
