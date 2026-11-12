package com.example.ui.components
import com.example.util.LocaleHelper
import com.example.util.AppStrings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.IncomeGreen
import java.text.SimpleDateFormat
import java.util.Calendar
import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPlanItem
import com.example.data.model.TransactionItem
import com.example.data.model.Wallet
import com.example.util.InstallmentDateHelper
import java.util.Date
import java.util.Locale
import java.util.UUID
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.rememberCoroutineScope
import com.example.data.ai.PayLaterAiOcrScanner
import com.example.data.model.PayLaterBillScanResult
import com.example.data.session.SessionManager
import kotlinx.coroutines.launch

enum class DebtPayoffStrategy(val idTitle: String, val enTitle: String, val idDesc: String, val enDesc: String) {
    DUE_DATE("Tenggat Terdekat ⏰", "Nearest Due Date ⏰", "Mengurutkan hutang berdasarkan tanggal jatuh tempo paling dekat ke yang paling jauh.", "Sort debts by due date from nearest to furthest."),
    AVALANCHE("Bunga Tertinggi ⚡", "Highest Interest ⚡", "Mengurutkan berdasarkan persentase bunga tertinggi untuk efisiensi finansial.", "Sort by highest interest rate for financial efficiency."),
    SNOWBALL("Nominal Terkecil ⛄", "Smallest Balance ⛄", "Mengurutkan berdasarkan sisa pokok hutang terkecil untuk motivasi psikologis.", "Sort by smallest remaining balance for psychological motivation.");

    val title: String
        get() = if (com.example.util.LocaleHelper.isEnglish()) enTitle else idTitle

    val description: String
        get() = if (com.example.util.LocaleHelper.isEnglish()) enDesc else idDesc
}

private fun calculateDaysUntilDue(dueDateStr: String): Int {
    return InstallmentDateHelper.calculateDaysUntilDue(dueDateStr)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DebtPlannerModalDialog(
    debts: List<DebtItem> = emptyList(),
    installmentPlans: List<InstallmentPlanItem> = emptyList(),
    wallets: List<Wallet> = emptyList(),
    transactions: List<TransactionItem> = emptyList(),
    budgetLimits: List<com.example.data.model.BudgetLimitItem> = emptyList(),
    currentUserId: Long? = SessionManager.getActiveUserId(),
    onSaveDebt: (DebtItem) -> Unit = {},
    onDeleteDebt: (DebtItem) -> Unit = {},
    onDeleteCategory: (String) -> Unit = {},
    onClearAllDebts: () -> Unit = {},
    onSaveInstallmentPlan: (InstallmentPlanItem) -> Unit = {},
    onConfirmInstallmentPayment: (InstallmentPlanItem, Long?, String?) -> Unit = { _, _, _ -> },
    onDeleteInstallmentPlan: (InstallmentPlanItem) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val debtCategories = listOf("Kartu Kredit", "Pinjaman Online", "Cicilan Kendaraan", "Pinjaman Bank", "Lain-lain")
    val debtList = debts

    var selectedTabIndex by remember { mutableStateOf(0) }

    // Installment Plan states
    var isCreatePlanDialogOpen by remember { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<InstallmentPlanItem?>(null) }
    var payingPlan by remember { mutableStateOf<InstallmentPlanItem?>(null) }
    var planToDelete by remember { mutableStateOf<InstallmentPlanItem?>(null) }
    var selectedWalletIdForPayment by remember { mutableStateOf<Long?>(null) }

    var inputPlanName by remember { mutableStateOf("") }
    var inputPlanProvider by remember { mutableStateOf("") }
    var inputPlanTenor by remember { mutableStateOf("") }
    var inputPlanAmount by remember { mutableStateOf("") }
    var inputPlanAnchorDay by remember { mutableStateOf("15") }
    var inputPlanPaidCount by remember { mutableStateOf("0") }
    var inputPlanPrincipal by remember { mutableStateOf("") }
    var inputPlanNotes by remember { mutableStateOf("") }

    // PayLater AI OCR Import States
    var isScanningPayLater by remember { mutableStateOf(false) }
    var payLaterScanProgress by remember { mutableStateOf("") }
    var payLaterScanError by remember { mutableStateOf<String?>(null) }
    var scannedPayLaterBatch by remember { mutableStateOf<List<PayLaterBillScanResult>>(emptyList()) }
    var isSampleSelectionDialogOpen by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            isScanningPayLater = true
            payLaterScanError = null
            payLaterScanProgress = if (com.example.util.LocaleHelper.isEnglish()) "Processing 1 of ${uris.size} screenshots..." else "Memproses 1 dari ${uris.size} screenshot..."
            scope.launch {
                try {
                    val batchResult = PayLaterAiOcrScanner.scanMultipleScreenshots(context, uris) { cur, tot ->
                        payLaterScanProgress = if (com.example.util.LocaleHelper.isEnglish()) "Processing $cur of $tot screenshots..." else "Memproses $cur dari $tot screenshot..."
                    }
                    isScanningPayLater = false
                    if (batchResult.bills.isNotEmpty()) {
                        scannedPayLaterBatch = batchResult.bills
                    } else {
                        payLaterScanError = if (com.example.util.LocaleHelper.isEnglish()) "Bill text not detected from selected screenshots." else "Teks tagihan tidak terdeteksi dari screenshot yang dipilih."
                    }
                } catch (e: Exception) {
                    isScanningPayLater = false
                    payLaterScanError = e.message ?: if (com.example.util.LocaleHelper.isEnglish()) "Failed to process bill screenshot." else "Gagal memproses screenshot tagihan."
                }
            }
        }
    }

    val configuredDebtBudgetLimit = remember(budgetLimits) {
        budgetLimits.firstOrNull { it.id == "Tagihan & Utilitas" }?.limitAmount
            ?: budgetLimits.firstOrNull { it.id == "total_monthly" }?.limitAmount
    }

    val activeInstallmentPlans = remember(installmentPlans) {
        installmentPlans.filter { it.isActive }
    }

    val monthlyInstallmentObligation = remember(activeInstallmentPlans) {
        activeInstallmentPlans.sumOf { it.installmentAmount }
    }

    val monthlyDebtObligation = remember(debtList) {
        debtList.sumOf { if (it.minMonthlyPayment > 0.0) it.minMonthlyPayment else (it.amount * 0.1).coerceAtMost(it.amount) }
    }

    val totalObligationThisMonth = monthlyInstallmentObligation + monthlyDebtObligation

    val totalKnownRemainingDebt = remember(debtList, activeInstallmentPlans) {
        debtList.sumOf { it.amount } + activeInstallmentPlans.sumOf { it.totalRemainingPrincipal ?: (it.installmentAmount * it.remainingTenor) }
    }

    var selectedStrategy by remember { mutableStateOf(DebtPayoffStrategy.DUE_DATE) }
    var monthlyBudgetInput by remember(configuredDebtBudgetLimit, totalObligationThisMonth) {
        mutableStateOf(
            configuredDebtBudgetLimit?.let { if (it > 0.0) it.toLong().toString() else "" }
                ?: if (totalObligationThisMonth > 0.0) totalObligationThisMonth.toLong().toString() else ""
        )
    }

    // Form states
    var editingDebtId by remember { mutableStateOf<String?>(null) }
    var inputDebtName by remember { mutableStateOf("") }
    var inputDebtAmount by remember { mutableStateOf("") }
    var inputDebtInterest by remember { mutableStateOf("") }
    var inputDebtCategory by remember { mutableStateOf("Kartu Kredit") }
    var inputDebtDueDate by remember {
        mutableStateOf(if (com.example.util.LocaleHelper.isEnglish()) "Day 15 each month" else "Tgl 15 tiap bulan")
    }

    // Dialog states
    var isResetConfirmDialogOpen by remember { mutableStateOf(false) }
    var isDeleteCategoryDialogOpen by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<String?>(null) }
    var debtItemToDelete by remember { mutableStateOf<DebtItem?>(null) }

    val budgetDouble = monthlyBudgetInput.toDoubleOrNull()
        ?: configuredDebtBudgetLimit
        ?: totalObligationThisMonth

    val priorityPlan = remember(debtList.toList(), installmentPlans.toList(), budgetDouble) {
        com.example.util.DebtPriorityEngine.calculatePlan(
            debts = debtList,
            installmentPlans = installmentPlans,
            totalMonthlyBudget = if (budgetDouble > 0.0) budgetDouble else totalObligationThisMonth
        )
    }

    val sortedDebts = remember(debtList.toList(), selectedStrategy, priorityPlan) {
        when (selectedStrategy) {
            DebtPayoffStrategy.DUE_DATE -> debtList.sortedBy { calculateDaysUntilDue(it.dueDate) }
            DebtPayoffStrategy.AVALANCHE -> {
                val rankMap = priorityPlan.prioritizedItems.associate { it.debt.id to it.rank }
                debtList.sortedBy { rankMap[it.id] ?: 999 }
            }
            DebtPayoffStrategy.SNOWBALL -> debtList.sortedBy { it.amount }
        }
    }

    val totalDebt = debtList.sumOf { it.amount }
    val effectiveBudget = if (budgetDouble > 0.0) budgetDouble else totalObligationThisMonth
    val estimatedMonths = priorityPlan.estimatedMonthsToDebtFree ?: if (effectiveBudget > 0) (totalDebt / effectiveBudget).toInt() + 1 else 0

    val freeDate = Calendar.getInstance().apply {
        add(Calendar.MONTH, estimatedMonths)
    }
    val dateFormatter = SimpleDateFormat("MMMM yyyy", com.example.util.LocaleHelper.getActiveLocale())
    val debtFreeDateString = dateFormatter.format(freeDate.time)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("debt_planner_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with Reset & Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = ExpenseRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.TrendingDown, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AI DEBT PAYOFF PLANNER",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = ExpenseRed
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Reset Semua Hutang Button (Fresh Start)
                    IconButton(
                        onClick = { isResetConfirmDialogOpen = true },
                        modifier = Modifier.size(34.dp).testTag("btn_reset_all_debts")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = com.example.util.AppStrings.t("Reset Semua Hutang", "Reset All Debts"), tint = ExpenseRed, modifier = Modifier.size(20.dp))
                    }

                    // Hapus Kategori Button
                    IconButton(
                        onClick = { isDeleteCategoryDialogOpen = true },
                        modifier = Modifier.size(34.dp).testTag("btn_delete_debt_category_open")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = com.example.util.AppStrings.t("Hapus Kategori Hutang", "Delete Debt Category"), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = AppStrings.t("Tutup", "Close"))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = FinSmartPrimary,
                modifier = Modifier.fillMaxWidth().testTag("tab_debt_planner")
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text(AppStrings.t("Hutang & Pinjaman", "Debts & Loans") + " (${debtList.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    modifier = Modifier.testTag("tab_debts")
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("${com.example.util.AppStrings.t("Cicilan Berjangka", "Term Installments")} (${installmentPlans.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    modifier = Modifier.testTag("tab_installments")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTabIndex == 0) {
                val currentCal = Calendar.getInstance()
                val nextCal = Calendar.getInstance().apply { add(Calendar.MONTH, 1) }
                val currentMonthName = SimpleDateFormat("MMMM yyyy", com.example.util.LocaleHelper.getActiveLocale()).format(currentCal.time)
                val nextMonthName = SimpleDateFormat("MMMM yyyy", com.example.util.LocaleHelper.getActiveLocale()).format(nextCal.time)
                val todayDayOfMonth = currentCal.get(Calendar.DAY_OF_MONTH)

                // 1. Debt & Monthly Obligation Summary Banner (BUG 5 & 6)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.2f)) {
                                Text(
                                    text = AppStrings.t("Kewajiban Pembayaran Bulan Berjalan", "Current Month Payment Obligations"),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = com.example.util.CurrencyFormatter.formatRupiah(totalObligationThisMonth),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ExpenseRed,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(0.8f)) {
                                Text(
                                    text = com.example.util.AppStrings.t("Total Sisa Pokok", "Total Remaining Principal"),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = com.example.util.CurrencyFormatter.formatRupiah(totalKnownRemainingDebt),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Perbandingan Kewajiban vs Batas Anggaran Pengguna (Kanonis)
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = AppStrings.t("Batas Anggaran Cicilan / Tagihan:", "Installment / Bill Budget Limit:"),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (configuredDebtBudgetLimit != null && configuredDebtBudgetLimit > 0.0) {
                                            com.example.util.CurrencyFormatter.formatRupiah(configuredDebtBudgetLimit)
                                        } else {
                                            AppStrings.t("Belum diatur", "Not set")
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FinSmartPrimary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = AppStrings.t("Status Terhadap Anggaran:", "Budget Status:"),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (configuredDebtBudgetLimit != null && configuredDebtBudgetLimit > 0.0) {
                                        val diff = totalObligationThisMonth - configuredDebtBudgetLimit
                                        if (diff > 0.0) {
                                            Text(
                                                text = "${com.example.util.CurrencyFormatter.formatRupiah(diff)} di atas limit (Defisit) ⚠️",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ExpenseRed,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        } else {
                                            Text(
                                                text = "${com.example.util.CurrencyFormatter.formatRupiah(Math.abs(diff))} di bawah limit (Aman) ✅",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = IncomeGreen,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = AppStrings.t("Sesuaikan di menu Anggaran", "Adjust in Budget menu"),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (totalKnownRemainingDebt > 0) {
                                            if (priorityPlan.isDeficit) {
                                                com.example.util.AppStrings.t("Perhatian: Defisit Anggaran Bulanan ⚠️", "Warning: Monthly Budget Deficit ⚠️")
                                            } else {
                                                "${com.example.util.AppStrings.t("Estimasi Bebas Hutang:", "Estimated Debt-Free:")} $debtFreeDateString"
                                            }
                                        } else com.example.util.AppStrings.t("Selamat! Anda Bebas Hutang 🎉", "Congratulations! You are Debt-Free 🎉"),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (priorityPlan.isDeficit) ExpenseRed else FinSmartPrimary
                                    )
                                    Text(
                                        text = if (totalKnownRemainingDebt > 0) {
                                            if (priorityPlan.isDeficit) {
                                                com.example.util.AppStrings.t(
                                                    "Alokasi bulanan (${com.example.util.CurrencyFormatter.formatRupiah(budgetDouble)}) belum menutup cicilan minimum (Defisit: ${com.example.util.CurrencyFormatter.formatRupiah(priorityPlan.monthlyShortfall)}/bln).",
                                                    "Monthly allocation (${com.example.util.CurrencyFormatter.formatRupiah(budgetDouble)}) does not cover minimum installments (Deficit: ${com.example.util.CurrencyFormatter.formatRupiah(priorityPlan.monthlyShortfall)}/mo)."
                                                )
                                            } else {
                                                com.example.util.AppStrings.t(
                                                    "Dengan simulasi alokasi ${com.example.util.CurrencyFormatter.formatRupiah(budgetDouble)}/bulan (Metode Avalanche: $estimatedMonths bulan).",
                                                    "With simulated allocation of ${com.example.util.CurrencyFormatter.formatRupiah(budgetDouble)}/month (Avalanche Method: $estimatedMonths months)."
                                                )
                                            }
                                        } else com.example.util.AppStrings.t("Tidak ada beban hutang aktif.", "No active debt obligations."),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Jadwal Pembayaran Bulanan (BUG 6: Transparansi Jadwal & Sumber Data)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = AppStrings.t("Jadwal Pembayaran Bulanan", "Monthly Payment Schedule"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = AppStrings.t("Rincian kewajiban periode berjalan dan estimasi periode berikutnya.", "Details of current period liabilities and next period estimates."),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // --- BULAN BERJALAN ---
                        Surface(
                            color = FinSmartPrimary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${AppStrings.t("Bulan Berjalan", "Current Month")} ($currentMonthName)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = FinSmartPrimary,
                                    modifier = Modifier.weight(1f, fill = false),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = com.example.util.CurrencyFormatter.formatRupiah(totalObligationThisMonth),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = ExpenseRed,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (activeInstallmentPlans.isEmpty() && debtList.isEmpty()) {
                            Text(
                                text = AppStrings.t("Belum ada jadwal pembayaran bulan ini.", "No payments scheduled for this month."),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            // Active installments
                            activeInstallmentPlans.forEach { plan ->
                                val dueDateFallback = if (com.example.util.LocaleHelper.isEnglish()) "Day ${plan.anchorDay} each month" else "Tgl ${plan.anchorDay} tiap bulan"
                                val isOverdue = !plan.isCompleted && InstallmentDateHelper.isOverdue(plan.nextDueDate.ifBlank { dueDateFallback })
                                val dataSource = if (plan.notes.contains("Shopee", ignoreCase = true) || plan.notes.contains("DANA", ignoreCase = true) || plan.notes.contains("GoPay", ignoreCase = true) || plan.notes.contains("impor", ignoreCase = true)) com.example.util.AppStrings.t("Impor Tagihan AI", "AI Bill Import") else com.example.util.AppStrings.t("Catatan Manual", "Manual Entry")

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                                Text(
                                                    text = plan.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${plan.provider.ifBlank { AppStrings.t("Cicilan", "Installment") }} • ${AppStrings.t("Jatuh Tempo: Tgl", "Due Date: Day")} ${plan.anchorDay}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = com.example.util.CurrencyFormatter.formatRupiah(plan.installmentAmount),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = ExpenseRed,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                                Surface(
                                                    color = if (isOverdue) ExpenseRed.copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = if (isOverdue) AppStrings.t("Terlambat ⚠️", "Overdue ⚠️") else AppStrings.t("Belum Bayar", "Unpaid"),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isOverdue) ExpenseRed else Color(0xFFD97706),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${AppStrings.t("Tenor: Ke-", "Tenor: ")}${plan.currentPeriod}/${plan.totalTenor} • ${AppStrings.t("Sisa:", "Remaining:")} ${com.example.util.CurrencyFormatter.formatRupiah(plan.totalRemainingPrincipal ?: (plan.installmentAmount * plan.remainingTenor))}",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f, fill = false),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${AppStrings.t("Sumber", "Source")}: ${if (dataSource == "Impor Tagihan AI") AppStrings.t("Impor Tagihan AI", "AI Bill Import") else AppStrings.t("Catatan Manual", "Manual Entry")}",
                                                fontSize = 9.sp,
                                                color = FinSmartPrimary,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }

                            // Debts
                            debtList.forEach { debt ->
                                val minDue = if (debt.minMonthlyPayment > 0.0) debt.minMonthlyPayment else (debt.amount * 0.1).coerceAtMost(debt.amount)
                                val days = calculateDaysUntilDue(debt.dueDate)
                                val isOverdue = InstallmentDateHelper.isOverdue(debt.dueDate)

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                                Text(
                                                    text = debt.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${debt.category} • ${debt.dueDate}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = com.example.util.CurrencyFormatter.formatRupiah(minDue),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = ExpenseRed,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                                Surface(
                                                    color = if (isOverdue) ExpenseRed.copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = if (isOverdue) AppStrings.t("Terlambat ⚠️", "Overdue ⚠️") else AppStrings.t("Belum Bayar", "Unpaid"),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isOverdue) ExpenseRed else Color(0xFFD97706),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${AppStrings.t("Sisa Pokok:", "Remaining Principal:")} ${com.example.util.CurrencyFormatter.formatRupiah(debt.amount)}",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f, fill = false),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = AppStrings.t("Sumber: Catatan Manual", "Source: Manual Entry"),
                                                fontSize = 9.sp,
                                                color = FinSmartPrimary,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // --- BULAN BERIKUTNYA ---
                        val nextMonthInstallments = activeInstallmentPlans.filter { it.remainingTenor > 1 }
                        val nextMonthDebts = debtList.filter { it.amount > (if (it.minMonthlyPayment > 0.0) it.minMonthlyPayment else (it.amount * 0.1)) }
                        val nextMonthEstimatedTotal = nextMonthInstallments.sumOf { it.installmentAmount } +
                            nextMonthDebts.sumOf { if (it.minMonthlyPayment > 0.0) it.minMonthlyPayment else (it.amount * 0.1) }

                        Surface(
                            color = Color(0xFF6366F1).copy(alpha = 0.08f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = AppStrings.t("Estimasi", "Estimated"),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = Color(0xFF6366F1)
                                    )
                                    Text(
                                        text = "${AppStrings.t("Bulan Berikutnya", "Next Month")} ($nextMonthName)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF4F46E5)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = com.example.util.CurrencyFormatter.formatRupiah(nextMonthEstimatedTotal),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF4F46E5),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (nextMonthInstallments.isEmpty() && nextMonthDebts.isEmpty()) {
                            Text(
                                text = AppStrings.t("Tidak ada tagihan yang dijadwalkan untuk bulan berikutnya.", "No bills scheduled for next month."),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            nextMonthInstallments.forEach { plan ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f, fill = false)) {
                                            Text(text = plan.name, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                text = if (com.example.util.LocaleHelper.isEnglish()) {
                                                    "Est. Day ${plan.anchorDay} $nextMonthName • Period ${plan.currentPeriod + 1}/${plan.totalTenor}"
                                                } else {
                                                    "Estimasi Tgl ${plan.anchorDay} $nextMonthName • Tenor ke-${plan.currentPeriod + 1}/${plan.totalTenor}"
                                                },
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = com.example.util.CurrencyFormatter.formatRupiah(plan.installmentAmount),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            nextMonthDebts.forEach { debt ->
                                val estPay = if (debt.minMonthlyPayment > 0.0) debt.minMonthlyPayment else (debt.amount * 0.1).coerceAtMost(debt.amount)
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f, fill = false)) {
                                            Text(text = debt.name, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                text = "${com.example.util.AppStrings.t("Estimasi", "Estimated")} ${debt.dueDate} • ${com.example.util.AppStrings.t("Sisa:", "Remaining:")} ${com.example.util.CurrencyFormatter.formatRupiah(debt.amount - estPay)}",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = com.example.util.CurrencyFormatter.formatRupiah(estPay),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            Spacer(modifier = Modifier.height(14.dp))

            // PayLater & Pinjaman Online AI Screenshot Import Section (FEAT-PAYLATER-IMPORT)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = FinSmartPrimary.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, FinSmartPrimary.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = FinSmartPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = AppStrings.t("Impor Tagihan dari Screenshot", "Import Bills from Screenshot"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = FinSmartPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = AppStrings.t("Scan otomatis Shopee PayLater, DANA Cicil, GoPayLater, Akulaku, AdaKami, dll.", "Automatically scan Shopee PayLater, DANA Cicil, GoPayLater, Akulaku, AdaKami, etc."),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary),
                            modifier = Modifier.weight(1.3f).testTag("btn_import_paylater_screenshot")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(com.example.util.AppStrings.t("Pilih Gambar", "Select Image"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { isSampleSelectionDialogOpen = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(0.9f).testTag("btn_sample_paylater_screenshot")
                        ) {
                            Text(com.example.util.AppStrings.t("Contoh", "Sample"), fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Strategy Selector Chips
            Text(text = com.example.util.AppStrings.t("Pilih Strategi Pelunasan Otomatis:", "Select Automatic Payoff Strategy:"), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DebtPayoffStrategy.values().forEach { strategy ->
                    FilterChip(
                        selected = selectedStrategy == strategy,
                        onClick = { selectedStrategy = strategy },
                        label = { Text(AppStrings.translatePayoffStrategyTitle(strategy.title), fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = monthlyBudgetInput,
                onValueChange = { monthlyBudgetInput = it.filter { ch -> ch.isDigit() } },
                label = { Text(com.example.util.AppStrings.t("Simulasi Alokasi Pelunasan Rutin per Bulan (Rp)", "Monthly Payoff Allocation Simulation")) },
                placeholder = { Text("Contoh: 2000000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("input_debt_monthly_budget"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Priority Debt List with Edit & Delete icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AppStrings.t("Daftar & Prioritas Pelunasan AI", "AI Repayment Priorities") + " (${debtList.size}):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                if (debtList.isNotEmpty()) {
                    TextButton(onClick = { isDeleteCategoryDialogOpen = true }) {
                        Text(com.example.util.AppStrings.t("Kelola Kategori", "Manage Categories"), fontSize = 11.sp, color = FinSmartPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (debtList.isEmpty()) {
                Text(
                    text = AppStrings.t("Belum ada catatan pinjaman/hutang. Gunakan form di bawah untuk menambah.", "No debt/loan records yet. Use the form below to add one."),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                sortedDebts.forEachIndexed { index, debt ->
                    val daysUntil = calculateDaysUntilDue(debt.dueDate)
                    val (statusTag, statusColor, isUrgent) = when {
                        daysUntil <= 3 -> Triple(
                            if (com.example.util.LocaleHelper.isEnglish()) "🚨 Due Date: $daysUntil Day(s) Left (Alert)" else "🚨 Jatuh Tempo: $daysUntil Hari Lagi (Alert)",
                            ExpenseRed,
                            true
                        )
                        daysUntil in 4..7 -> Triple(
                            if (com.example.util.LocaleHelper.isEnglish()) "⚠️ Warning: $daysUntil Day(s) Left" else "⚠️ Warning: $daysUntil Hari Lagi",
                            Color(0xFFF59E0B),
                            false
                        )
                        else -> Triple(
                            if (com.example.util.LocaleHelper.isEnglish()) "✅ Safe: $daysUntil Day(s) Left" else "✅ Aman: $daysUntil Hari Lagi",
                            Color(0xFF10B981),
                            false
                        )
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            if (index == 0) 1.5.dp else 1.dp,
                            if (index == 0) statusColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // AI Priority #1 Badge
                            if (index == 0) {
                                Surface(
                                    color = statusColor.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = com.example.util.AppStrings.t("Priority #1 (Mendesak) 🚨 • Rekomendasi AI: Prioritaskan tagihan ini untuk cegah denda!", "Priority #1 (Urgent) 🚨 • AI Recommendation: Prioritize this bill to prevent penalties!"),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(statusColor.copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "#${index + 1}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = statusColor)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(text = debt.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = "${com.example.util.AppStrings.t(debt.category, debt.category)} • ${com.example.util.AppStrings.t("Bunga", "Interest")} ${debt.interestRatePercent}%/${com.example.util.AppStrings.t("thn", "yr")}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Surface(
                                            color = statusColor.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = statusTag,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = statusColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = com.example.util.CurrencyFormatter.formatRupiah(debt.amount),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = ExpenseRed,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Text(
                                            text = "Min: ${com.example.util.CurrencyFormatter.formatRupiah(debt.minMonthlyPayment)}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Edit Button (Pensil)
                                    IconButton(
                                        onClick = {
                                            editingDebtId = debt.id
                                            inputDebtName = debt.name
                                            inputDebtAmount = debt.amount.toLong().toString()
                                            inputDebtInterest = debt.interestRatePercent.toString()
                                            inputDebtCategory = debt.category
                                            inputDebtDueDate = debt.dueDate
                                        },
                                        modifier = Modifier.size(28.dp).testTag("btn_edit_debt_${debt.id}")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = com.example.util.AppStrings.t("Edit Hutang", "Edit Debt"), tint = FinSmartPrimary, modifier = Modifier.size(16.dp))
                                    }

                                    // Delete Button (Sampah)
                                    IconButton(
                                        onClick = { debtItemToDelete = debt },
                                        modifier = Modifier.size(28.dp).testTag("btn_delete_debt_${debt.id}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = com.example.util.AppStrings.t("Hapus Hutang", "Delete Debt"), tint = ExpenseRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Add / Edit Debt Section Form
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = if (editingDebtId != null) com.example.util.AppStrings.t("Edit Data Pinjaman / Hutang", "Edit Loan / Debt Data") else com.example.util.AppStrings.t("Tambah Pinjaman / Hutang Baru:", "Add New Loan / Debt:"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Category selection chips
                    Text(text = AppStrings.t("Kategori Pinjaman:", "Loan Category:"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        debtCategories.forEach { cat ->
                            FilterChip(
                                selected = inputDebtCategory == cat,
                                onClick = { inputDebtCategory = cat },
                                label = { Text(AppStrings.translatePayLaterCategory(cat), fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = FinSmartPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = FinSmartPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = inputDebtName,
                        onValueChange = { inputDebtName = it },
                        label = { Text(com.example.util.AppStrings.t("Nama Pinjaman (Misal: Kartu Kredit Mandiri)", "Loan Name (e.g., Credit Card)")) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = inputDebtAmount,
                            onValueChange = { inputDebtAmount = it },
                            label = { Text(com.example.util.AppStrings.t("Nominal (Rp)", "Amount")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = inputDebtInterest,
                            onValueChange = { inputDebtInterest = it },
                            label = { Text(com.example.util.AppStrings.t("Bunga (%)", "Interest (%)")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Due Date (Tanggal Jatuh Tempo) Field with presets
                    OutlinedTextField(
                        value = inputDebtDueDate,
                        onValueChange = { inputDebtDueDate = it },
                        label = { Text(com.example.util.AppStrings.t("Tanggal Jatuh Tempo (Due Date)", "Due Date")) },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val datePresets = listOf(
                            Triple("Tgl 5", "Day 5", 5),
                            Triple("Tgl 10", "Day 10", 10),
                            Triple("Tgl 15", "Day 15", 15),
                            Triple("Tgl 20", "Day 20", 20),
                            Triple("Akhir", "End", null)
                        )
                        datePresets.forEach { (idLabel, enLabel, dayNum) ->
                            val label = if (com.example.util.LocaleHelper.isEnglish()) enLabel else idLabel
                            Surface(
                                onClick = {
                                    inputDebtDueDate = if (dayNum != null) {
                                        if (com.example.util.LocaleHelper.isEnglish()) "Day $dayNum each month" else "Tgl $dayNum tiap bulan"
                                    } else {
                                        if (com.example.util.LocaleHelper.isEnglish()) "End of each month" else "Akhir tiap bulan"
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (editingDebtId != null) {
                            OutlinedButton(
                                onClick = {
                                    editingDebtId = null
                                    inputDebtName = ""
                                    inputDebtAmount = ""
                                    inputDebtInterest = ""
                                    inputDebtCategory = "Kartu Kredit"
                                    inputDebtDueDate = if (com.example.util.LocaleHelper.isEnglish()) "Day 15 each month" else "Tgl 15 tiap bulan"
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(com.example.util.AppStrings.t("Batal Edit", "Cancel Edit"))
                            }
                        }

                        Button(
                            onClick = {
                                val amount = inputDebtAmount.toDoubleOrNull()
                                val interest = inputDebtInterest.toDoubleOrNull() ?: 10.0
                                if (inputDebtName.isBlank() || amount == null || amount <= 0) {
                                    Toast.makeText(context, com.example.util.AppStrings.t("Isi nama dan nominal hutang yang valid!", "Please enter a valid loan name and amount!"), Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                if (editingDebtId != null) {
                                    // Update existing item
                                    val existing = debtList.firstOrNull { it.id == editingDebtId }
                                    if (existing != null) {
                                        onSaveDebt(
                                            existing.copy(
                                                name = inputDebtName,
                                                amount = amount,
                                                interestRatePercent = interest,
                                                minMonthlyPayment = amount * 0.1,
                                                category = inputDebtCategory,
                                                dueDate = inputDebtDueDate
                                            )
                                        )
                                        Toast.makeText(context, com.example.util.AppStrings.t("Data hutang berhasil diperbarui!", "Debt data updated successfully!"), Toast.LENGTH_SHORT).show()
                                    }
                                    editingDebtId = null
                                } else {
                                    // Add new item
                                    onSaveDebt(
                                        DebtItem(
                                            name = inputDebtName,
                                            amount = amount,
                                            interestRatePercent = interest,
                                            minMonthlyPayment = amount * 0.1,
                                            category = inputDebtCategory,
                                            dueDate = inputDebtDueDate
                                        )
                                    )
                                    Toast.makeText(context, com.example.util.AppStrings.t("Hutang ditambahkan ke Planner!", "Debt added to Planner!"), Toast.LENGTH_SHORT).show()
                                }

                                inputDebtName = ""
                                inputDebtAmount = ""
                                inputDebtInterest = ""
                            },
                            modifier = Modifier
                                .weight(if (editingDebtId != null) 1f else 2f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary)
                        ) {
                            Text(
                                text = if (editingDebtId != null) com.example.util.AppStrings.t("Perbarui Hutang", "Update Debt") else com.example.util.AppStrings.t("Simpan ke Planner", "Save to Planner"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        } else {
            // Cicilan Berjangka Content
            val activePlans = installmentPlans.filter { it.isActive }
            val completedPlans = installmentPlans.filter { it.isCompleted }
            val totalMonthlyInstallment = activePlans.sumOf { it.installmentAmount }

            // Installment Summary Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = AppStrings.t("Total Kewajiban Angsuran Bulanan", "Total Monthly Installment Obligation"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = com.example.util.CurrencyFormatter.formatRupiah(totalMonthlyInstallment),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = FinSmartPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = FinSmartPrimary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(com.example.util.AppStrings.t("Cicilan Aktif", "Active Installments"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${activePlans.size} ${com.example.util.AppStrings.t("Kontrak", "Contracts")}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FinSmartPrimary)
                            }
                        }

                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(com.example.util.AppStrings.t("Sudah Lunas", "Paid Off"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${completedPlans.size} ${com.example.util.AppStrings.t("Kontrak", "Contracts")}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF047857))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Button Tambah Rencana Cicilan Baru
            Button(
                onClick = {
                    editingPlan = null
                    inputPlanName = ""
                    inputPlanProvider = ""
                    inputPlanTenor = ""
                    inputPlanAmount = ""
                    inputPlanAnchorDay = "15"
                    inputPlanPaidCount = "0"
                    inputPlanPrincipal = ""
                    inputPlanNotes = ""
                    isCreatePlanDialogOpen = true
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary),
                modifier = Modifier.fillMaxWidth().testTag("btn_add_installment_plan")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(com.example.util.AppStrings.t("Buat Rencana Cicilan Baru", "Create New Installment Plan"), fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (installmentPlans.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CreditCard, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = AppStrings.t("Belum Ada Rencana Cicilan Berjangka", "No Installment Plans Yet"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = AppStrings.t("Catat cicilan motor, mobil, barang elektronik, atau pinjaman dengan tenor tetap agar progres pembayaran dan tanggal jatuh tempo bulanan terpantau rapi tanpa membuat tagihan manual tiap bulan.", "Record vehicle, electronics, or fixed-term loans to track repayment progress and monthly due dates automatically."),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                installmentPlans.forEach { plan ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .testTag("card_installment_plan_${plan.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(
                            1.dp,
                            if (plan.isCompleted) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = plan.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!plan.provider.isNullOrBlank()) {
                                        Text(
                                            text = "${com.example.util.AppStrings.t("Kreditur:", "Creditor:")} ${plan.provider}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                val isOverdue = !plan.isCompleted && InstallmentDateHelper.isOverdue(plan.nextDueDate)
                                val (statusLabel, statusBg, statusColor) = when {
                                    plan.isCompleted -> Triple(com.example.util.AppStrings.t("LUNAS", "PAID"), Color(0xFFD1FAE5), Color(0xFF065F46))
                                    isOverdue -> Triple(com.example.util.AppStrings.t("TERLAMBAT", "OVERDUE"), ExpenseRed.copy(alpha = 0.15f), ExpenseRed)
                                    else -> Triple(com.example.util.AppStrings.t("AKTIF", "ACTIVE"), FinSmartPrimary.copy(alpha = 0.15f), FinSmartPrimary)
                                }

                                Surface(
                                    color = statusBg,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = statusLabel,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Progres Angsuran
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${com.example.util.AppStrings.t("Progres:", "Progress:")} ${plan.paidCount}/${plan.totalTenor} ${com.example.util.AppStrings.t("kali", "times")}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Text(
                                    text = if (plan.isCompleted) com.example.util.AppStrings.t("Selesai 🎉", "Completed 🎉") else "${com.example.util.AppStrings.t("Sisa:", "Left:")} ${plan.remainingTenor} ${com.example.util.AppStrings.t("kali lagi", "more")}",
                                    fontSize = 11.sp,
                                    color = if (plan.isCompleted) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            LinearProgressIndicator(
                                progress = (plan.paidCount.toFloat() / plan.totalTenor.toFloat()).coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = if (plan.isCompleted) Color(0xFF10B981) else FinSmartPrimary,
                                trackColor = MaterialTheme.colorScheme.surface
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Rincian Angsuran & Tanggal Jatuh Tempo
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(com.example.util.AppStrings.t("Angsuran:", "Installment:"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "${com.example.util.CurrencyFormatter.formatRupiah(plan.installmentAmount)} / ${com.example.util.AppStrings.t("bln", "mo")}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(com.example.util.AppStrings.t("Jatuh Tempo:", "Due Date:"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = if (plan.isCompleted) com.example.util.AppStrings.t("Lunas", "Paid Off") else plan.nextDueDate,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = if (!plan.isCompleted && InstallmentDateHelper.isOverdue(plan.nextDueDate)) ExpenseRed else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            if (plan.totalRemainingPrincipal != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${com.example.util.AppStrings.t("Sisa Pokok:", "Remaining Principal:")} ${com.example.util.CurrencyFormatter.formatRupiah(plan.totalRemainingPrincipal)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = com.example.util.AppStrings.t("Sisa Pokok: Belum ditentukan (tidak diestimasi sembarangan)", "Remaining Principal: Not set (unestimated)"),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            if (plan.lastPaymentDate != null && plan.lastPaymentDate > 0L) {
                                val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", com.example.util.LocaleHelper.getActiveLocale()).format(Date(plan.lastPaymentDate))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = AppStrings.t("Pembayaran Terakhir: ", "Last Payment: ") + dateStr,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            val linkedPayments = transactions.filter {
                                it.tags.contains("installment_plan_id:${plan.id}") ||
                                (it.title.startsWith("Angsuran ${plan.name}") && it.category == "Cicilan & Hutang")
                            }
                            if (linkedPayments.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = AppStrings.t("Histori Transaksi Terkait", "Related Transaction History") + " (${linkedPayments.size}):",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FinSmartPrimary
                                        )
                                        linkedPayments.take(3).forEach { tx ->
                                            val txDate = SimpleDateFormat("dd/MM/yyyy", com.example.util.LocaleHelper.getActiveLocale()).format(Date(tx.timestamp))
                                            Text(
                                                text = "• $txDate: ${com.example.util.CurrencyFormatter.formatRupiah(tx.amount)} (${tx.walletName})",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Actions: Bayar, Edit, Hapus
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row {
                                    IconButton(
                                        onClick = {
                                            editingPlan = plan
                                            inputPlanName = plan.name
                                            inputPlanProvider = plan.provider ?: ""
                                            inputPlanTenor = plan.totalTenor.toString()
                                            inputPlanAmount = plan.installmentAmount.toLong().toString()
                                            inputPlanAnchorDay = plan.anchorDay.toString()
                                            inputPlanPaidCount = plan.paidCount.toString()
                                            inputPlanPrincipal = plan.totalRemainingPrincipal?.toLong()?.toString() ?: ""
                                            inputPlanNotes = plan.notes ?: ""
                                            isCreatePlanDialogOpen = true
                                        },
                                        modifier = Modifier.size(32.dp).testTag("btn_edit_installment_${plan.id}")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = AppStrings.t("Edit", "Edit"), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    IconButton(
                                        onClick = { planToDelete = plan },
                                        modifier = Modifier.size(32.dp).testTag("btn_delete_installment_${plan.id}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = AppStrings.t("Hapus", "Delete"), modifier = Modifier.size(18.dp), tint = ExpenseRed)
                                    }
                                }

                                if (!plan.isCompleted) {
                                    Button(
                                        onClick = {
                                            payingPlan = plan
                                            selectedWalletIdForPayment = null
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary),
                                        modifier = Modifier.testTag("btn_pay_installment_${plan.id}")
                                    ) {
                                        Text(
                                            text = AppStrings.t("Bayar Angsuran ke-", "Pay Installment #") + "${plan.currentPeriod}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 1. Reset Semua Hutang Confirmation Dialog
    if (isResetConfirmDialogOpen) {
        val totalAffectedCount = debtList.size + activeInstallmentPlans.size
        AlertDialog(
            onDismissRequest = { isResetConfirmDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = ExpenseRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Reset Semua Hutang?", "Reset All Debts?"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(AppStrings.t("Apakah Anda yakin ingin menghapus seluruh catatan pinjaman/hutang dan rencana cicilan ($totalAffectedCount item)?\n\nTransaksi dompet dan riwayat pengeluaran umum Anda akan tetap aman. Tindakan ini tidak dapat dibatalkan.", "Are you sure you want to delete all loan/debt records and installment plans ($totalAffectedCount items)?\n\nYour wallets and general expense records will remain safe. This action cannot be undone."))
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllDebts()
                        isResetConfirmDialogOpen = false
                        Toast.makeText(context, "${com.example.util.AppStrings.t("Seluruh daftar hutang & cicilan", "All debts & installments")} ($totalAffectedCount ${com.example.util.AppStrings.t("item", "items")}) ${com.example.util.AppStrings.t("telah di-reset! 🧹", "have been reset! 🧹")}", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                    modifier = Modifier.testTag("btn_confirm_reset_debts")
                ) {
                    Text(com.example.util.AppStrings.t("Reset Semua", "Reset All"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { isResetConfirmDialogOpen = false }) {
                    Text(com.example.util.AppStrings.cancel)
                }
            }
        )
    }

    // 2. Hapus Kategori Hutang Dialog
    if (isDeleteCategoryDialogOpen) {
        AlertDialog(
            onDismissRequest = { isDeleteCategoryDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = ExpenseRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Hapus Kategori Hutang", "Delete Debt Category"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(com.example.util.AppStrings.t("Pilih kategori yang ingin dihapus seluruh itemnya dari planner:", "Select category to delete all its items from planner:"))
                    Spacer(modifier = Modifier.height(10.dp))
                    val availableCategories = debtList.map { it.category }.distinct()
                    if (availableCategories.isEmpty()) {
                        Text(com.example.util.AppStrings.t("Tidak ada kategori hutang aktif.", "No active debt categories."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    } else {
                        availableCategories.forEach { cat ->
                            val count = debtList.count { it.category == cat }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${com.example.util.AppStrings.t(cat, cat)} ($count ${com.example.util.AppStrings.t("item", "items")})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Button(
                                    onClick = {
                                        onDeleteCategory(cat)
                                        isDeleteCategoryDialogOpen = false
                                        Toast.makeText(context, "${com.example.util.AppStrings.t("Seluruh hutang dalam kategori", "All debts in category")} '$cat' ${com.example.util.AppStrings.t("berhasil dihapus!", "deleted successfully!")}", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(AppStrings.t("Hapus", "Delete"), fontSize = 10.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isDeleteCategoryDialogOpen = false }) {
                    Text(AppStrings.t("Tutup", "Close"))
                }
            }
        )
    }

    // 3. Delete Single Debt Item Confirmation Dialog
    debtItemToDelete?.let { debt ->
        AlertDialog(
            onDismissRequest = { debtItemToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = ExpenseRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Hapus Pinjaman?", "Delete Loan?"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(com.example.util.AppStrings.t("Hapus pinjaman", "Delete loan") + " '${debt.name}' senilai ${com.example.util.CurrencyFormatter.formatRupiah(debt.amount)} dari planner?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteDebt(debt)
                        debtItemToDelete = null
                        Toast.makeText(context, "${com.example.util.AppStrings.t("Pinjaman", "Loan")} '${debt.name}' ${com.example.util.AppStrings.t("berhasil dihapus!", "deleted successfully!")}", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text(AppStrings.t("Hapus", "Delete"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { debtItemToDelete = null }) {
                    Text(com.example.util.AppStrings.cancel)
                }
            }
        )
    }

    // 4. PayLater AI Bill Review & Confirmation Dialog
    if (scannedPayLaterBatch.isNotEmpty()) {
        PayLaterBillReviewDialog(
            batchScanResults = scannedPayLaterBatch,
            existingDebts = debtList,
            existingInstallmentPlans = installmentPlans,
            currentUserId = currentUserId,
            onConfirmSave = { debtToSave ->
                onSaveDebt(debtToSave)
            },
            onSaveAsInstallmentPlan = { planToSave ->
                onSaveInstallmentPlan(planToSave)
            },
            onSaveBatch = { debtsToSave, plansToSave ->
                debtsToSave.forEach { onSaveDebt(it) }
                plansToSave.forEach { (plan, _) ->
                    onSaveInstallmentPlan(plan)
                }
            },
            onDismiss = {
                scannedPayLaterBatch = emptyList()
            }
        )
    }

    // 5. PayLater AI Scanning Progress Dialog
    if (isScanningPayLater) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = FinSmartPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Memproses Tagihan AI", "Processing AI Bill"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = FinSmartPrimary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(com.example.util.AppStrings.t("Membaca screenshot tagihan...", "Reading bill screenshot..."), fontSize = 13.sp)
                }
            },
            confirmButton = {}
        )
    }

    // 6. PayLater Scanning Error Dialog
    payLaterScanError?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = { payLaterScanError = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ExpenseRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Gagal Scan Tagihan", "Bill Scan Failed"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(errorMsg, fontSize = 13.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        payLaterScanError = null
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Text(AppStrings.t("Coba Lagi", "Try Again"))
                }
            },
            dismissButton = {
                TextButton(onClick = { payLaterScanError = null }) {
                    Text(AppStrings.t("Tutup", "Close"))
                }
            }
        )
    }

    // 7. PayLater Sample Preset Picker Dialog
    if (isSampleSelectionDialogOpen) {
        AlertDialog(
            onDismissRequest = { isSampleSelectionDialogOpen = false },
            title = {
                Text(com.example.util.AppStrings.t("Pilih Contoh Tagihan", "Select Sample Bill"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = AppStrings.t("Pilih contoh tagihan untuk mencoba fitur tanpa screenshot fisik:", "Select sample bill to test feature without physical screenshot:"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            isSampleSelectionDialogOpen = false
                            scannedPayLaterBatch = listOf(PayLaterAiOcrScanner.getSamplePayLaterBill("shopee"))
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_sample_shopee")
                    ) {
                        Text("Shopee PayLater (SPayLater)")
                    }
                    OutlinedButton(
                        onClick = {
                            isSampleSelectionDialogOpen = false
                            scannedPayLaterBatch = listOf(PayLaterAiOcrScanner.getSamplePayLaterBill("dana"))
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_sample_dana")
                    ) {
                        Text(AppStrings.t("DANA Cicil (Cicilan 4x)", "DANA Installment (4x Installments)"))
                    }
                    OutlinedButton(
                        onClick = {
                            isSampleSelectionDialogOpen = false
                            scannedPayLaterBatch = listOf(PayLaterAiOcrScanner.getSamplePayLaterBill("gopay"))
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_sample_gopay")
                    ) {
                        Text(AppStrings.t("GoPayLater (Limit Bulanan)", "GoPayLater (Monthly Limit)"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isSampleSelectionDialogOpen = false }) {
                    Text(com.example.util.AppStrings.cancel)
                }
            }
        )
    }

    // 8. Create / Edit Installment Plan Dialog
    if (isCreatePlanDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                isCreatePlanDialogOpen = false
                editingPlan = null
            },
            title = {
                Text(
                    text = if (editingPlan != null) com.example.util.AppStrings.t("Edit Rencana Cicilan", "Edit Installment Plan") else com.example.util.AppStrings.t("Tambah Rencana Cicilan", "Add Installment Plan"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputPlanName,
                        onValueChange = { inputPlanName = it },
                        label = { Text(com.example.util.AppStrings.t("Nama Cicilan (wajib)", "Installment Name (Required)")) },
                        placeholder = { Text(com.example.util.AppStrings.t("Contoh: Cicilan Motor Vario", "e.g. Motorcycle Installment")) },
                        modifier = Modifier.fillMaxWidth().testTag("input_plan_name"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inputPlanProvider,
                        onValueChange = { inputPlanProvider = it },
                        label = { Text("Kreditur / Penyedia (opsional)") },
                        placeholder = { Text(AppStrings.t("Contoh: FIF Group, BCA Finance", "e.g., FIF Group, BCA Finance")) },
                        modifier = Modifier.fillMaxWidth().testTag("input_plan_provider"),
                        singleLine = true
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = inputPlanTenor,
                            onValueChange = { inputPlanTenor = it.filter { ch -> ch.isDigit() } },
                            label = { Text(AppStrings.t("Total Tenor", "Total Installments")) },
                            placeholder = { Text("35") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("input_plan_tenor"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = inputPlanPaidCount,
                            onValueChange = { inputPlanPaidCount = it.filter { ch -> ch.isDigit() } },
                            label = { Text(AppStrings.t("Sudah Bayar", "Paid")) },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("input_plan_paid_count"),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = inputPlanAmount,
                        onValueChange = { inputPlanAmount = it.filter { ch -> ch.isDigit() } },
                        label = { Text(com.example.util.AppStrings.t("Angsuran per Bulan (Rp)", "Installment per Month (Rp)")) },
                        placeholder = { Text(AppStrings.t("Contoh: 850000", "e.g., 850000")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("input_plan_amount"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inputPlanAnchorDay,
                        onValueChange = { inputPlanAnchorDay = it.filter { ch -> ch.isDigit() } },
                        label = { Text(com.example.util.AppStrings.t("Tanggal Jatuh Tempo (1-31)", "Due Date (1-31)")) },
                        placeholder = { Text("15") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("input_plan_anchor_day"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inputPlanPrincipal,
                        onValueChange = { inputPlanPrincipal = it.filter { ch -> ch.isDigit() } },
                        label = { Text(AppStrings.t("Total Sisa Pokok (opsional)", "Total Remaining Principal (optional)")) },
                        placeholder = { Text(AppStrings.t("Biarkan kosong jika belum pasti", "Leave empty if unsure")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("input_plan_principal"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inputPlanNotes,
                        onValueChange = { inputPlanNotes = it },
                        label = { Text(com.example.util.AppStrings.t("Catatan (opsional)", "Notes (Optional)")) },
                        modifier = Modifier.fillMaxWidth().testTag("input_plan_notes"),
                        singleLine = false
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = inputPlanName.trim()
                        val tenor = inputPlanTenor.toIntOrNull() ?: 0
                        val amount = inputPlanAmount.toDoubleOrNull() ?: 0.0
                        val anchorDay = (inputPlanAnchorDay.toIntOrNull() ?: 15).coerceIn(1, 31)
                        val paid = (inputPlanPaidCount.toIntOrNull() ?: 0).coerceIn(0, tenor)
                        val principal = inputPlanPrincipal.toDoubleOrNull()

                        if (name.isBlank()) {
                            Toast.makeText(context, com.example.util.AppStrings.t("Nama cicilan wajib diisi.", "Installment name is required."), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (tenor <= 0) {
                            Toast.makeText(context, com.example.util.AppStrings.t("Total tenor harus lebih dari 0 kali.", "Total tenor must be greater than 0."), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (amount <= 0.0) {
                            Toast.makeText(context, com.example.util.AppStrings.t("Nominal angsuran harus lebih dari Rp 0.", "Installment amount must be greater than 0."), Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val remaining = tenor - paid
                        val isComplete = remaining <= 0

                        val planItem = InstallmentPlanItem(
                            id = editingPlan?.id ?: UUID.randomUUID().toString(),
                            name = name,
                            provider = inputPlanProvider.trim(),
                            installmentAmount = amount,
                            totalTenor = tenor,
                            paidCount = paid,
                            anchorDay = anchorDay,
                            startDate = editingPlan?.startDate ?: InstallmentDateHelper.formatIsoDate(Calendar.getInstance()),
                            nextDueDate = if (isComplete) com.example.util.AppStrings.t("Lunas", "Paid Off") else if (com.example.util.LocaleHelper.isEnglish()) "Day $anchorDay each month" else "Tgl $anchorDay tiap bulan",
                            totalRemainingPrincipal = principal,
                            status = if (isComplete) "SELESAI" else "AKTIF",
                            notes = inputPlanNotes.trim(),
                            createdAt = editingPlan?.createdAt ?: System.currentTimeMillis(),
                            lastPaymentDate = editingPlan?.lastPaymentDate,
                            userId = currentUserId
                        )

                        onSaveInstallmentPlan(planItem)
                        isCreatePlanDialogOpen = false
                        editingPlan = null
                        Toast.makeText(context, "${com.example.util.AppStrings.t("Rencana cicilan", "Installment plan")} '${planItem.name}' ${com.example.util.AppStrings.t("berhasil disimpan!", "saved successfully!")}", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary),
                    modifier = Modifier.testTag("btn_save_installment_dialog")
                ) {
                    Text(if (editingPlan != null) com.example.util.AppStrings.t("Perbarui", "Update") else com.example.util.AppStrings.t("Simpan", "Save"))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    isCreatePlanDialogOpen = false
                    editingPlan = null
                }) {
                    Text(com.example.util.AppStrings.cancel)
                }
            }
        )
    }

    // 9. Payment Confirmation Dialog
    payingPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { payingPlan = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CreditCard, contentDescription = null, tint = FinSmartPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Konfirmasi Bayar Angsuran", "Confirm Installment Payment"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${com.example.util.AppStrings.t("Kontrak:", "Contract:")} ${plan.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = AppStrings.t("Pembayaran Angsuran ke-", "Payment of Installment #") + "${plan.currentPeriod} " + AppStrings.t("dari", "of") + " ${plan.totalTenor}",
                        fontSize = 13.sp
                    )
                    Text(
                        text = AppStrings.t("Nominal: ", "Amount: ") + com.example.util.CurrencyFormatter.formatRupiah(plan.installmentAmount),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ExpenseRed
                    )
                    Text(
                        text = AppStrings.t("Jatuh Tempo: ", "Due Date: ") + plan.nextDueDate,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = AppStrings.t("Pilih Dompet Sumber (Opsional):", "Select Source Wallet (Optional):"),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )

                    FilterChip(
                        selected = selectedWalletIdForPayment == null,
                        onClick = { selectedWalletIdForPayment = null },
                        label = { Text(com.example.util.AppStrings.t("Tanpa potong saldo dompet", "Without deducting wallet balance"), fontSize = 11.sp) },
                        modifier = Modifier.testTag("chip_wallet_none")
                    )

                    wallets.forEach { w ->
                        FilterChip(
                            selected = selectedWalletIdForPayment == w.id,
                            onClick = { selectedWalletIdForPayment = w.id },
                            label = { Text("${w.name} (${com.example.util.CurrencyFormatter.formatRupiah(w.balance)})", fontSize = 11.sp) },
                            modifier = Modifier.testTag("chip_wallet_${w.id}")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentPlan = plan
                        onConfirmInstallmentPayment(
                            currentPlan,
                            selectedWalletIdForPayment,
                            "${com.example.util.AppStrings.t("Pembayaran angsuran ke-", "Payment of installment #")}${currentPlan.currentPeriod} ${com.example.util.AppStrings.t("dari", "of")} ${currentPlan.totalTenor}"
                        )
                        Toast.makeText(
                            context,
                            "${com.example.util.AppStrings.t("Angsuran ke-", "Installment #")}${currentPlan.currentPeriod} ${com.example.util.AppStrings.t("dari", "of")} ${currentPlan.totalTenor} ${com.example.util.AppStrings.t("berhasil dikonfirmasi lunas!", "confirmed paid successfully!")}",
                            Toast.LENGTH_SHORT
                        ).show()
                        payingPlan = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary),
                    modifier = Modifier.testTag("btn_confirm_pay_installment")
                ) {
                    Text(com.example.util.AppStrings.t("Konfirmasi Bayar", "Confirm Payment"))
                }
            },
            dismissButton = {
                TextButton(onClick = { payingPlan = null }) {
                    Text(com.example.util.AppStrings.cancel)
                }
            }
        )
    }

    // 10. Delete Installment Plan Dialog
    planToDelete?.let { plan ->
        AlertDialog(
            onDismissRequest = { planToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = ExpenseRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Hapus Rencana Cicilan?", "Delete Installment Plan?"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(AppStrings.t("Apakah Anda yakin ingin menghapus rencana cicilan", "Are you sure you want to delete installment plan") + " '${plan.name}'? " + AppStrings.t("Catatan progres", "Progress records") + " (${plan.paidCount}/${plan.totalTenor}) " + AppStrings.t("akan dihapus.", "will be deleted."))
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteInstallmentPlan(plan)
                        planToDelete = null
                        Toast.makeText(context, "${com.example.util.AppStrings.t("Rencana cicilan", "Installment plan")} '${plan.name}' ${com.example.util.AppStrings.t("berhasil dihapus!", "deleted successfully!")}", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text(AppStrings.t("Hapus", "Delete"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { planToDelete = null }) {
                    Text(com.example.util.AppStrings.cancel)
                }
            }
        )
    }
}
}
