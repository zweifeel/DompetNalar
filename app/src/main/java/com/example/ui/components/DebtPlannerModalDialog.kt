package com.example.ui.components

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FinSmartPrimary
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

enum class DebtPayoffStrategy(val title: String, val description: String) {
    DUE_DATE("Tenggat Terdekat ⏰", "Mengurutkan hutang berdasarkan tanggal jatuh tempo paling dekat ke yang paling jauh."),
    AVALANCHE("Bunga Tertinggi ⚡", "Mengurutkan berdasarkan persentase bunga tertinggi untuk efisiensi finansial."),
    SNOWBALL("Nominal Terkecil ⛄", "Mengurutkan berdasarkan sisa pokok hutang terkecil untuk motivasi psikologis.")
}

private fun calculateDaysUntilDue(dueDateStr: String): Int {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DebtPlannerModalDialog(
    debts: List<DebtItem> = emptyList(),
    installmentPlans: List<InstallmentPlanItem> = emptyList(),
    wallets: List<Wallet> = emptyList(),
    transactions: List<TransactionItem> = emptyList(),
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
    var payLaterScanError by remember { mutableStateOf<String?>(null) }
    var scannedPayLaterResult by remember { mutableStateOf<PayLaterBillScanResult?>(null) }
    var isSampleSelectionDialogOpen by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isScanningPayLater = true
            payLaterScanError = null
            scope.launch {
                try {
                    val result = PayLaterAiOcrScanner.scanPayLaterScreenshot(context, uri)
                    isScanningPayLater = false
                    scannedPayLaterResult = result
                } catch (e: Exception) {
                    isScanningPayLater = false
                    payLaterScanError = e.message ?: "Gagal memproses screenshot tagihan."
                }
            }
        }
    }

    var selectedStrategy by remember { mutableStateOf(DebtPayoffStrategy.DUE_DATE) }
    var monthlyBudgetInput by remember { mutableStateOf("3000000") }

    // Form states
    var editingDebtId by remember { mutableStateOf<String?>(null) }
    var inputDebtName by remember { mutableStateOf("") }
    var inputDebtAmount by remember { mutableStateOf("") }
    var inputDebtInterest by remember { mutableStateOf("") }
    var inputDebtCategory by remember { mutableStateOf("Kartu Kredit") }
    var inputDebtDueDate by remember { mutableStateOf("Tgl 15 tiap bulan") }

    // Dialog states
    var isResetConfirmDialogOpen by remember { mutableStateOf(false) }
    var isDeleteCategoryDialogOpen by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<String?>(null) }
    var debtItemToDelete by remember { mutableStateOf<DebtItem?>(null) }

    val sortedDebts = remember(debtList.toList(), selectedStrategy) {
        when (selectedStrategy) {
            DebtPayoffStrategy.DUE_DATE -> debtList.sortedBy { calculateDaysUntilDue(it.dueDate) }
            DebtPayoffStrategy.AVALANCHE -> debtList.sortedByDescending { it.interestRatePercent }
            DebtPayoffStrategy.SNOWBALL -> debtList.sortedBy { it.amount }
        }
    }

    val totalDebt = debtList.sumOf { it.amount }
    val budgetDouble = monthlyBudgetInput.toDoubleOrNull() ?: 3000000.0
    val estimatedMonths = if (budgetDouble > 0) (totalDebt / budgetDouble).toInt() + 1 else 0

    val freeDate = Calendar.getInstance().apply {
        add(Calendar.MONTH, estimatedMonths)
    }
    val dateFormatter = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
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
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset Semua Hutang", tint = ExpenseRed, modifier = Modifier.size(20.dp))
                    }

                    // Hapus Kategori Button
                    IconButton(
                        onClick = { isDeleteCategoryDialogOpen = true },
                        modifier = Modifier.size(34.dp).testTag("btn_delete_debt_category_open")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Hapus Kategori Hutang", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
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
                    text = { Text("Hutang & Pinjaman (${debtList.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    modifier = Modifier.testTag("tab_debts")
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Cicilan Berjangka (${installmentPlans.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    modifier = Modifier.testTag("tab_installments")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTabIndex == 0) {
                // Debt Summary Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Total Beban Hutang Aktif", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = com.example.util.CurrencyFormatter.formatRupiah(totalDebt),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = ExpenseRed
                    )

                    Spacer(modifier = Modifier.height(12.dp))

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
                                    text = if (totalDebt > 0) "Estimasi Bebas Hutang: $debtFreeDateString" else "Selamat! Anda Bebas Hutang 🎉",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = FinSmartPrimary
                                )
                                Text(
                                    text = if (totalDebt > 0) "Dengan alokasi $estimatedMonths bulan cicilan rutin ${com.example.util.CurrencyFormatter.formatRupiah(budgetDouble)}/bulan." else "Tidak ada beban hutang aktif.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                            text = "Impor Tagihan dari Screenshot",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = FinSmartPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Scan otomatis Shopee PayLater, DANA Cicil, GoPayLater, Akulaku, AdaKami, dll.",
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
                            Text("Pilih Gambar", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { isSampleSelectionDialogOpen = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(0.9f).testTag("btn_sample_paylater_screenshot")
                        ) {
                            Text("Contoh", fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Strategy Selector Chips
            Text(text = "Pilih Strategi Pelunasan Otomatis:", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
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
                        label = { Text(strategy.title, fontSize = 11.sp) }
                    )
                }
            }

            Text(
                text = selectedStrategy.description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Priority Debt List with Edit & Delete icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daftar & Prioritas Pelunasan AI (${debtList.size}):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                if (debtList.isNotEmpty()) {
                    TextButton(onClick = { isDeleteCategoryDialogOpen = true }) {
                        Text("Kelola Kategori", fontSize = 11.sp, color = FinSmartPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (debtList.isEmpty()) {
                Text(
                    text = "Belum ada catatan pinjaman/hutang. Gunakan form di bawah untuk menambah.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                sortedDebts.forEachIndexed { index, debt ->
                    val daysUntil = calculateDaysUntilDue(debt.dueDate)
                    val (statusTag, statusColor, isUrgent) = when {
                        daysUntil <= 3 -> Triple("🚨 Jatuh Tempo: $daysUntil Hari Lagi (Alert)", ExpenseRed, true)
                        daysUntil in 4..7 -> Triple("⚠️ Warning: $daysUntil Hari Lagi", Color(0xFFF59E0B), false)
                        else -> Triple("✅ Aman: $daysUntil Hari Lagi", Color(0xFF10B981), false)
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
                                            text = "Priority #1 (Mendesak) 🚨 • Rekomendasi AI: Prioritaskan tagihan ini untuk cegah denda!",
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
                                    modifier = Modifier.weight(1f)
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
                                        Text(text = debt.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            text = "${debt.category} • Bunga ${debt.interestRatePercent}%/thn",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = com.example.util.CurrencyFormatter.formatRupiah(debt.amount),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = ExpenseRed
                                        )
                                        Text(
                                            text = "Min: ${com.example.util.CurrencyFormatter.formatRupiah(debt.minMonthlyPayment)}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Hutang", tint = FinSmartPrimary, modifier = Modifier.size(16.dp))
                                    }

                                    // Delete Button (Sampah)
                                    IconButton(
                                        onClick = { debtItemToDelete = debt },
                                        modifier = Modifier.size(28.dp).testTag("btn_delete_debt_${debt.id}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Hapus Hutang", tint = ExpenseRed, modifier = Modifier.size(16.dp))
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
                        text = if (editingDebtId != null) "Edit Data Pinjaman / Hutang" else "Tambah Pinjaman / Hutang Baru:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Category selection chips
                    Text(text = "Kategori Pinjaman:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        debtCategories.forEach { cat ->
                            FilterChip(
                                selected = inputDebtCategory == cat,
                                onClick = { inputDebtCategory = cat },
                                label = { Text(cat, fontSize = 10.sp) },
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
                        label = { Text("Nama Pinjaman (Misal: Kartu Kredit Mandiri)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = inputDebtAmount,
                            onValueChange = { inputDebtAmount = it },
                            label = { Text("Nominal (Rp)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = inputDebtInterest,
                            onValueChange = { inputDebtInterest = it },
                            label = { Text("Bunga (%)") },
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
                        label = { Text("Tanggal Jatuh Tempo (Due Date)") },
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
                        listOf("Tgl 5", "Tgl 10", "Tgl 15", "Tgl 20", "Akhir Bulan").forEach { datePreset ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 2.dp)
                            ) {
                                TextButton(
                                    onClick = { inputDebtDueDate = "$datePreset tiap bulan" },
                                    modifier = Modifier.padding(0.dp)
                                ) {
                                    Text(datePreset, fontSize = 9.sp, maxLines = 1)
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
                                    inputDebtDueDate = "Tgl 15 tiap bulan"
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Batal Edit")
                            }
                        }

                        Button(
                            onClick = {
                                val amount = inputDebtAmount.toDoubleOrNull()
                                val interest = inputDebtInterest.toDoubleOrNull() ?: 10.0
                                if (inputDebtName.isBlank() || amount == null || amount <= 0) {
                                    Toast.makeText(context, "Isi nama dan nominal hutang yang valid!", Toast.LENGTH_SHORT).show()
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
                                        Toast.makeText(context, "Data hutang berhasil diperbarui!", Toast.LENGTH_SHORT).show()
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
                                    Toast.makeText(context, "Hutang ditambahkan ke Planner!", Toast.LENGTH_SHORT).show()
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
                                text = if (editingDebtId != null) "Perbarui Hutang" else "Simpan ke Planner",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        } else {
            // Cicilan Berjangka Content
            val activePlans = installmentPlans.filter { it.status == "ACTIVE" }
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
                        text = "Total Kewajiban Angsuran Bulanan",
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
                                Text("Cicilan Aktif", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${activePlans.size} Kontrak", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FinSmartPrimary)
                            }
                        }

                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Sudah Lunas", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${completedPlans.size} Kontrak", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF047857))
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
                Text("Buat Rencana Cicilan Baru", fontWeight = FontWeight.Bold)
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
                            text = "Belum Ada Rencana Cicilan Berjangka",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Catat cicilan motor, mobil, barang elektronik, atau pinjaman dengan tenor tetap agar progres pembayaran dan tanggal jatuh tempo bulanan terpantau rapi tanpa membuat tagihan manual tiap bulan.",
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = plan.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    if (!plan.provider.isNullOrBlank()) {
                                        Text(
                                            text = "Kreditur: ${plan.provider}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                val isOverdue = !plan.isCompleted && InstallmentDateHelper.isOverdue(plan.nextDueDate)
                                val (statusLabel, statusBg, statusColor) = when {
                                    plan.isCompleted -> Triple("LUNAS", Color(0xFFD1FAE5), Color(0xFF065F46))
                                    isOverdue -> Triple("TERLAMBAT", ExpenseRed.copy(alpha = 0.15f), ExpenseRed)
                                    else -> Triple("AKTIF", FinSmartPrimary.copy(alpha = 0.15f), FinSmartPrimary)
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
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                                    text = "Progres: ${plan.paidCount}/${plan.totalTenor} kali",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = if (plan.isCompleted) "Selesai 🎉" else "Sisa: ${plan.remainingTenor} kali lagi",
                                    fontSize = 11.sp,
                                    color = if (plan.isCompleted) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant
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
                                Column {
                                    Text("Angsuran:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "${com.example.util.CurrencyFormatter.formatRupiah(plan.installmentAmount)} / bln",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Jatuh Tempo:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = if (plan.isCompleted) "Lunas" else plan.nextDueDate,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = if (!plan.isCompleted && InstallmentDateHelper.isOverdue(plan.nextDueDate)) ExpenseRed else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            if (plan.totalRemainingPrincipal != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Sisa Pokok: ${com.example.util.CurrencyFormatter.formatRupiah(plan.totalRemainingPrincipal)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Sisa Pokok: Belum ditentukan (tidak diestimasi sembarangan)",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            if (plan.lastPaymentDate != null && plan.lastPaymentDate > 0L) {
                                val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(plan.lastPaymentDate))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Pembayaran Terakhir: $dateStr",
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
                                            text = "Histori Transaksi Terkait (${linkedPayments.size}):",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FinSmartPrimary
                                        )
                                        linkedPayments.take(3).forEach { tx ->
                                            val txDate = SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID")).format(Date(tx.timestamp))
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
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    IconButton(
                                        onClick = { planToDelete = plan },
                                        modifier = Modifier.size(32.dp).testTag("btn_delete_installment_${plan.id}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Hapus", modifier = Modifier.size(18.dp), tint = ExpenseRed)
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
                                            text = "Bayar Angsuran ke-${plan.currentPeriod}",
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
        AlertDialog(
            onDismissRequest = { isResetConfirmDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = ExpenseRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset Semua Hutang?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text("Apakah Anda yakin ingin menghapus seluruh catatan pinjaman/hutang (Fresh Start Bebas Hutang)? Tindakan ini tidak dapat dibatalkan.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllDebts()
                        isResetConfirmDialogOpen = false
                        Toast.makeText(context, "Seluruh daftar hutang telah di-reset (Fresh Start)! 🧹", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                    modifier = Modifier.testTag("btn_confirm_reset_debts")
                ) {
                    Text("Reset Semua", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { isResetConfirmDialogOpen = false }) {
                    Text("Batal")
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
                    Text("Hapus Kategori Hutang", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column {
                    Text("Pilih kategori yang ingin dihapus seluruh itemnya dari planner:")
                    Spacer(modifier = Modifier.height(10.dp))
                    val availableCategories = debtList.map { it.category }.distinct()
                    if (availableCategories.isEmpty()) {
                        Text("Tidak ada kategori hutang aktif.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
                                Text("$cat ($count item)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Button(
                                    onClick = {
                                        onDeleteCategory(cat)
                                        isDeleteCategoryDialogOpen = false
                                        Toast.makeText(context, "Seluruh hutang dalam kategori '$cat' berhasil dihapus!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Hapus", fontSize = 10.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isDeleteCategoryDialogOpen = false }) {
                    Text("Tutup")
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
                    Text("Hapus Pinjaman?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text("Hapus pinjaman '${debt.name}' senilai ${com.example.util.CurrencyFormatter.formatRupiah(debt.amount)} dari planner?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteDebt(debt)
                        debtItemToDelete = null
                        Toast.makeText(context, "Pinjaman '${debt.name}' berhasil dihapus!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text("Hapus", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { debtItemToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // 4. PayLater AI Bill Review & Confirmation Dialog
    scannedPayLaterResult?.let { result ->
        PayLaterBillReviewDialog(
            scanResult = result,
            existingDebts = debtList,
            existingInstallmentPlans = installmentPlans,
            currentUserId = currentUserId,
            onConfirmSave = { debtToSave ->
                onSaveDebt(debtToSave)
                scannedPayLaterResult = null
            },
            onSaveAsInstallmentPlan = { planToSave ->
                onSaveInstallmentPlan(planToSave)
                scannedPayLaterResult = null
            },
            onDismiss = {
                scannedPayLaterResult = null
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
                    Text("Memproses Tagihan AI", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                    Text("Membaca screenshot tagihan...", fontSize = 13.sp)
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
                    Text("Gagal Scan Tagihan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                    Text("Coba Lagi")
                }
            },
            dismissButton = {
                TextButton(onClick = { payLaterScanError = null }) {
                    Text("Tutup")
                }
            }
        )
    }

    // 7. PayLater Sample Preset Picker Dialog
    if (isSampleSelectionDialogOpen) {
        AlertDialog(
            onDismissRequest = { isSampleSelectionDialogOpen = false },
            title = {
                Text("Pilih Contoh Tagihan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Pilih contoh tagihan untuk mencoba fitur tanpa screenshot fisik:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            isSampleSelectionDialogOpen = false
                            scannedPayLaterResult = PayLaterAiOcrScanner.getSamplePayLaterBill("shopee")
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_sample_shopee")
                    ) {
                        Text("Shopee PayLater (SPayLater)")
                    }
                    OutlinedButton(
                        onClick = {
                            isSampleSelectionDialogOpen = false
                            scannedPayLaterResult = PayLaterAiOcrScanner.getSamplePayLaterBill("dana")
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_sample_dana")
                    ) {
                        Text("DANA Cicil (Cicilan 4x)")
                    }
                    OutlinedButton(
                        onClick = {
                            isSampleSelectionDialogOpen = false
                            scannedPayLaterResult = PayLaterAiOcrScanner.getSamplePayLaterBill("gopay")
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_sample_gopay")
                    ) {
                        Text("GoPayLater (Limit Bulanan)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isSampleSelectionDialogOpen = false }) {
                    Text("Batal")
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
                    text = if (editingPlan != null) "Edit Rencana Cicilan" else "Tambah Rencana Cicilan",
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
                        label = { Text("Nama Cicilan (wajib)") },
                        placeholder = { Text("Contoh: Cicilan Motor Vario") },
                        modifier = Modifier.fillMaxWidth().testTag("input_plan_name"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inputPlanProvider,
                        onValueChange = { inputPlanProvider = it },
                        label = { Text("Kreditur / Penyedia (opsional)") },
                        placeholder = { Text("Contoh: FIF Group, BCA Finance") },
                        modifier = Modifier.fillMaxWidth().testTag("input_plan_provider"),
                        singleLine = true
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = inputPlanTenor,
                            onValueChange = { inputPlanTenor = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Total Tenor") },
                            placeholder = { Text("35") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("input_plan_tenor"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = inputPlanPaidCount,
                            onValueChange = { inputPlanPaidCount = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Sudah Bayar") },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("input_plan_paid_count"),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = inputPlanAmount,
                        onValueChange = { inputPlanAmount = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Angsuran per Bulan (Rp)") },
                        placeholder = { Text("Contoh: 850000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("input_plan_amount"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inputPlanAnchorDay,
                        onValueChange = { inputPlanAnchorDay = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Tanggal Jatuh Tempo (1-31)") },
                        placeholder = { Text("15") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("input_plan_anchor_day"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inputPlanPrincipal,
                        onValueChange = { inputPlanPrincipal = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Total Sisa Pokok (opsional)") },
                        placeholder = { Text("Biarkan kosong jika belum pasti") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("input_plan_principal"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inputPlanNotes,
                        onValueChange = { inputPlanNotes = it },
                        label = { Text("Catatan (opsional)") },
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
                            Toast.makeText(context, "Nama cicilan wajib diisi.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (tenor <= 0) {
                            Toast.makeText(context, "Total tenor harus lebih dari 0 kali.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (amount <= 0.0) {
                            Toast.makeText(context, "Nominal angsuran harus lebih dari Rp 0.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val remaining = tenor - paid
                        val isComplete = remaining <= 0
                        val status = if (isComplete) "COMPLETED" else "ACTIVE"

                        val planItem = InstallmentPlanItem(
                            id = editingPlan?.id ?: UUID.randomUUID().toString(),
                            name = name,
                            provider = inputPlanProvider.trim(),
                            installmentAmount = amount,
                            totalTenor = tenor,
                            paidCount = paid,
                            anchorDay = anchorDay,
                            startDate = editingPlan?.startDate ?: InstallmentDateHelper.formatIsoDate(Calendar.getInstance()),
                            nextDueDate = if (isComplete) "Lunas" else "Tgl $anchorDay tiap bulan",
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
                        Toast.makeText(context, "Rencana cicilan '${planItem.name}' berhasil disimpan!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary),
                    modifier = Modifier.testTag("btn_save_installment_dialog")
                ) {
                    Text(if (editingPlan != null) "Perbarui" else "Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    isCreatePlanDialogOpen = false
                    editingPlan = null
                }) {
                    Text("Batal")
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
                    Text("Konfirmasi Bayar Angsuran", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Kontrak: ${plan.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Pembayaran Angsuran ke-${plan.currentPeriod} dari ${plan.totalTenor}",
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Nominal: ${com.example.util.CurrencyFormatter.formatRupiah(plan.installmentAmount)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ExpenseRed
                    )
                    Text(
                        text = "Jatuh Tempo: ${plan.nextDueDate}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pilih Dompet Sumber (Opsional):",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )

                    FilterChip(
                        selected = selectedWalletIdForPayment == null,
                        onClick = { selectedWalletIdForPayment = null },
                        label = { Text("Tanpa potong saldo dompet", fontSize = 11.sp) },
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
                            "Pembayaran angsuran ke-${currentPlan.currentPeriod} dari ${currentPlan.totalTenor}"
                        )
                        Toast.makeText(
                            context,
                            "Angsuran ke-${currentPlan.currentPeriod} dari ${currentPlan.totalTenor} berhasil dikonfirmasi lunas!",
                            Toast.LENGTH_SHORT
                        ).show()
                        payingPlan = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary),
                    modifier = Modifier.testTag("btn_confirm_pay_installment")
                ) {
                    Text("Konfirmasi Bayar")
                }
            },
            dismissButton = {
                TextButton(onClick = { payingPlan = null }) {
                    Text("Batal")
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
                    Text("Hapus Rencana Cicilan?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text("Apakah Anda yakin ingin menghapus rencana cicilan '${plan.name}'? Catatan progres (${plan.paidCount}/${plan.totalTenor}) akan dihapus.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteInstallmentPlan(plan)
                        planToDelete = null
                        Toast.makeText(context, "Rencana cicilan '${plan.name}' berhasil dihapus!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text("Hapus", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { planToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }
}
}

