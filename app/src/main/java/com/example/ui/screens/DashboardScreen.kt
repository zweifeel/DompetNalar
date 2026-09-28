package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.AccountBalanceWallet
import com.example.ui.theme.ExpenseRed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AccountMode
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import com.example.ui.components.CashflowChartCard
import com.example.ui.components.MainBalanceCard
import com.example.ui.components.QuickActionButtons
import com.example.ui.components.TransactionItemRow
import com.example.ui.components.WalletsSection
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.viewmodel.CashflowDataPoint
import com.example.ui.viewmodel.TimeframeFilter

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.util.ThemeEngine

@Composable
fun DashboardScreen(
    totalBalance: Double,
    totalIncome: Double,
    totalExpense: Double,
    accountMode: AccountMode,
    isPrivacyMode: Boolean,
    wallets: List<Wallet>,
    recentTransactions: List<TransactionItem>,
    chartData: List<CashflowDataPoint>,
    selectedTimeframe: TimeframeFilter,
    currentUser: com.example.data.model.User?,
    trialDaysRemaining: Int,
    onTimeframeSelected: (TimeframeFilter) -> Unit,
    onTogglePrivacy: () -> Unit,
    onToggleAccountMode: () -> Unit,
    onAddIncomeClick: () -> Unit,
    onAddExpenseClick: () -> Unit,
    onTransferClick: () -> Unit,
    onAddWalletClick: () -> Unit,
    onEditWallet: (Wallet) -> Unit,
    onDeleteWallet: (Wallet) -> Unit,
    onDeleteTransaction: (TransactionItem) -> Unit,
    onViewAllTransactionsClick: () -> Unit,
    onOpenAiChat: () -> Unit,
    onOpenScanReceipt: () -> Unit,
    onOpenSplitBill: () -> Unit,
    onNavigateToBudget: () -> Unit = {},
    onBadgeClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    profilePhotoUri: String? = null,
    budgetLimits: List<com.example.data.model.BudgetLimitItem> = emptyList(),
    currentMonthExpenses: List<com.example.data.model.TransactionItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    val wallpaperUri by ThemeEngine.customWallpaperUri.collectAsState()
    val wallpaperOpacity by ThemeEngine.customWallpaperOpacity.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Custom Wallpaper Layer if active
        if (!wallpaperUri.isNullOrEmpty()) {
            AsyncImage(
                model = wallpaperUri,
                contentDescription = "Custom Wallpaper",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Adaptive Contrast & Darkness Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.background.copy(
                            alpha = (1f - (wallpaperOpacity.coerceIn(0.05f, 0.95f) * 0.7f)).coerceIn(0.2f, 0.95f)
                        )
                    )
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("dashboard_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp)
        ) {
        // Top App Bar / Header with FinSmart Logo & Greeting
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable { onAvatarClick() }
                            .background(
                                brush = Brush.linearGradient(
                                    when (currentUser?.role) {
                                        com.example.data.model.UserRole.MASTER_DEV -> listOf(Color(0xFFD97706), Color(0xFF92400E))
                                        com.example.data.model.UserRole.SMART_MASTER -> listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                                        else -> listOf(FinSmartPrimary, Color(0xFF00897B))
                                    }
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!profilePhotoUri.isNullOrEmpty()) {
                            AsyncImage(
                                model = profilePhotoUri,
                                contentDescription = "Foto Profil",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = (currentUser?.username?.take(1) ?: "F").uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        // Header Flexbox Row (white-space nowrap style)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.testTag("header_user_greeting_row")
                        ) {
                            Text(
                                text = "Halo, ${currentUser?.username ?: "Sobat FinSmart"}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(6.dp))

                            // Account Status & Badge Hierarchy
                            val role = currentUser?.role ?: com.example.data.model.UserRole.FREE_MEMBER
                            Surface(
                                color = when (role) {
                                    com.example.data.model.UserRole.MASTER_DEV -> Color(0xFFD97706)
                                    com.example.data.model.UserRole.SMART_MASTER -> Color(0xFFF59E0B).copy(alpha = 0.25f)
                                    com.example.data.model.UserRole.FREE_MEMBER -> FinSmartPrimary.copy(alpha = 0.15f)
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onBadgeClick() }
                                    .testTag("header_badge_${role.name.lowercase()}")
                            ) {
                                Text(
                                    text = when (role) {
                                        com.example.data.model.UserRole.MASTER_DEV -> "MasterDev 👑"
                                        com.example.data.model.UserRole.SMART_MASTER -> "Smart Master 👑"
                                        com.example.data.model.UserRole.FREE_MEMBER -> if (trialDaysRemaining in 1..3) "Trial $trialDaysRemaining Hari" else "Member"
                                    },
                                    color = when (role) {
                                        com.example.data.model.UserRole.MASTER_DEV -> Color.White
                                        com.example.data.model.UserRole.SMART_MASTER -> Color(0xFFD97706)
                                        com.example.data.model.UserRole.FREE_MEMBER -> FinSmartPrimary
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (currentUser?.role == com.example.data.model.UserRole.MASTER_DEV) "Panel Master Developer Aktif" else "Kelola finansialmu dengan cerdas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onOpenNotifications,
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .testTag("btn_bell_notifications")
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifikasi",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 0. Overbudget Alert Banner System
        val totalLimitItem = budgetLimits.firstOrNull { it.id == "total_monthly" }
        val totalLimitAmount = totalLimitItem?.limitAmount ?: 0.0
        val totalExpenseAmount = currentMonthExpenses.sumOf { it.amount }

        val categoryExpensesMap = currentMonthExpenses
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        val categoryLimitsMap = budgetLimits
            .filter { it.isCategory }
            .associate { it.id to it.limitAmount }

        val overbudgetAlerts = mutableListOf<String>()

        if (totalLimitAmount > 0.0 && totalExpenseAmount > totalLimitAmount) {
            overbudgetAlerts.add("Total pengeluaran bulanan melampaui limit anggaran!")
        }

        categoryLimitsMap.forEach { (catName, limit) ->
            val spent = categoryExpensesMap[catName] ?: 0.0
            if (limit > 0.0 && spent > limit) {
                overbudgetAlerts.add("Kategori [$catName] telah overbudget!")
            }
        }

        if (overbudgetAlerts.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { onNavigateToBudget() }
                        .testTag("dashboard_overbudget_alert"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ExpenseRed.copy(alpha = 0.08f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Peringatan",
                            tint = ExpenseRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Peringatan Overbudget Cerdas!",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = ExpenseRed
                            )
                            Text(
                                text = overbudgetAlerts.first() + if (overbudgetAlerts.size > 1) " (+${overbudgetAlerts.size - 1} lainnya)" else "",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Detail ->",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRed
                        )
                    }
                }
            }
        }

        // Main Balance Card
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                MainBalanceCard(
                    totalBalance = totalBalance,
                    totalIncome = totalIncome,
                    totalExpense = totalExpense,
                    accountMode = accountMode,
                    isPrivacyMode = isPrivacyMode,
                    onTogglePrivacy = onTogglePrivacy,
                    onToggleAccountMode = onToggleAccountMode,
                    onAddIncomeClick = onAddIncomeClick,
                    onAddExpenseClick = onAddExpenseClick,
                    onTransferClick = onTransferClick
                )
            }
        }

        // Quick Action Buttons
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                QuickActionButtons(
                    onAddIncomeClick = onAddIncomeClick,
                    onAddExpenseClick = onAddExpenseClick,
                    onTransferClick = onTransferClick
                )
            }
        }

        // AI Smart Features Section (Scan Nota AI & Split Bill AI)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Two-Column Grid for Scan Nota & Split Bill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Scan Nota OCR AI Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onOpenScanReceipt() }
                            .testTag("banner_scan_receipt_ocr"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = FinSmartPrimary.copy(alpha = 0.12f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(FinSmartPrimary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Surface(
                                    color = FinSmartPrimary,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.wrapContentWidth()
                                ) {
                                    Text(
                                        text = "OCR AI",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Scan Nota",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Ekstraksi struk otomatis via kamera",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 2. Split Bill AI Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onOpenSplitBill() }
                            .testTag("banner_split_bill_ai"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.12f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFF10B981), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Surface(
                                    color = Color(0xFF10B981),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.wrapContentWidth()
                                ) {
                                    Text(
                                        text = "WA READY",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Split Bill",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Bagi tagihan & share ke WhatsApp",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 3. Smart Budgeting Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onNavigateToBudget() }
                            .testTag("banner_smart_budget"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.12f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFF59E0B), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Surface(
                                    color = Color(0xFFF59E0B),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.wrapContentWidth()
                                ) {
                                    Text(
                                        text = "BUDGET",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Anggaran",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Atur limit bulanan & kategori",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // AI Chat & Voice Input Hero Card
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onOpenAiChat() }
                        .testTag("banner_ai_chat_input"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(FinSmartPrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Input AI & Voice Logging",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = FinSmartPrimary,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "GEMINI",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Ucapkan atau ketik transaksi via Chat WhatsApp style",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Buka Chat AI",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Wallets & Bank Management Section
        item {
            WalletsSection(
                wallets = wallets,
                isPrivacyMode = isPrivacyMode,
                onAddWalletClick = onAddWalletClick,
                onEditWallet = onEditWallet,
                onDeleteWallet = onDeleteWallet
            )
        }
    }
}
}
