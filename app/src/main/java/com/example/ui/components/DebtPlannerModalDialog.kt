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
import java.util.Locale
import java.util.UUID

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
    onSaveDebt: (DebtItem) -> Unit = {},
    onDeleteDebt: (DebtItem) -> Unit = {},
    onDeleteCategory: (String) -> Unit = {},
    onClearAllDebts: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val debtCategories = listOf("Kartu Kredit", "Pinjaman Online", "Cicilan Kendaraan", "Pinjaman Bank", "Lain-lain")
    val debtList = debts

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

            Spacer(modifier = Modifier.height(16.dp))

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
}
