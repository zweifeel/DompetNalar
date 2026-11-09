package com.example.ui.screens
import com.example.util.AppStrings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BudgetLimitItem
import com.example.data.model.CategoryItem
import com.example.data.model.TransactionItem
import com.example.ui.components.FinSmartIcons
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.IncomeGreen
import com.example.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    budgetLimits: List<BudgetLimitItem>,
    currentMonthExpenses: List<TransactionItem>,
    categories: List<CategoryItem>,
    onSaveBudgetLimit: (id: String, amount: Double, isCategory: Boolean) -> Unit,
    onDeleteBudgetLimit: (BudgetLimitItem) -> Unit,
    onResetAllBudgetLimits: () -> Unit = {},
    onAddCustomCategory: (name: String, limit: Double) -> Unit = { _, _ -> },
    onDeleteCategory: (CategoryItem) -> Unit = {},
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // States
    var isEditTotalDialogOpen by remember { mutableStateOf(false) }
    var totalLimitInput by remember { mutableStateOf("") }

    var isEditCategoryDialogOpen by remember { mutableStateOf(false) }
    var selectedCategoryName by remember { mutableStateOf("") }
    var categoryLimitInput by remember { mutableStateOf("") }

    var isAddCategoryDialogOpen by remember { mutableStateOf(false) }
    var newCategoryNameInput by remember { mutableStateOf("") }
    var newCategoryLimitInput by remember { mutableStateOf("") }

    var isResetConfirmDialogOpen by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<CategoryItem?>(null) }
    var isDeleteCategoryConfirmOpen by remember { mutableStateOf(false) }

    // Computations
    val totalLimitItem = budgetLimits.firstOrNull { it.id == "total_monthly" }
    val totalLimitAmount = totalLimitItem?.limitAmount ?: 0.0
    val totalExpenseAmount = currentMonthExpenses.sumOf { it.amount }

    // Calculate Category Expenses for this month
    val categoryExpensesMap = currentMonthExpenses
        .groupBy { it.category }
        .mapValues { entry -> entry.value.sumOf { it.amount } }

    val categoryLimitsMap = budgetLimits
        .filter { it.isCategory }
        .associate { it.id to it.limitAmount }

    // Alerts collection for display
    val alertsList = mutableListOf<String>()

    // Global monthly budget check
    if (totalLimitAmount > 0.0) {
        val pct = totalExpenseAmount / totalLimitAmount
        if (pct >= 0.85) {
            alertsList.add("🔴 Anggaran bulanan gabungan telah melampaui 85%! Pengeluaran saat ini: ${CurrencyFormatter.formatRupiah(totalExpenseAmount)} dari limit ${CurrencyFormatter.formatRupiah(totalLimitAmount)}.")
        } else if (pct >= 0.70) {
            alertsList.add("⚠️ Waspada! Pengeluaran bulanan gabungan telah melampaui 70%: ${CurrencyFormatter.formatRupiah(totalExpenseAmount)} dari limit ${CurrencyFormatter.formatRupiah(totalLimitAmount)}.")
        }
    }

    // Category limits check
    categoryLimitsMap.forEach { (catName, limit) ->
        val spent = categoryExpensesMap[catName] ?: 0.0
        if (limit > 0.0) {
            val pct = spent / limit
            if (pct >= 1.0) {
                alertsList.add("🔴 Overbudget di Kategori [$catName]! Anda telah menghabiskan ${CurrencyFormatter.formatRupiah(spent)} dari limit ${CurrencyFormatter.formatRupiah(limit)}.")
            } else if (pct >= 0.85) {
                alertsList.add("🔴 Limit Kategori [$catName] kritis (melampaui 85%)! Terpakai ${CurrencyFormatter.formatRupiah(spent)} dari limit ${CurrencyFormatter.formatRupiah(limit)}.")
            } else if (pct >= 0.70) {
                alertsList.add("⚠️ Waspada! Kategori [$catName] telah melampaui 70%: Terpakai ${CurrencyFormatter.formatRupiah(spent)} dari limit ${CurrencyFormatter.formatRupiah(limit)}.")
            }
        }
    }

    // Progress color helper
    fun getProgressColor(spent: Double, limit: Double): Color {
        if (limit <= 0.0) return IncomeGreen
        val ratio = spent / limit
        return when {
            ratio >= 0.85 -> ExpenseRed
            ratio >= 0.70 -> Color(0xFFEAB308) // Yellow
            else -> IncomeGreen
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(com.example.util.AppStrings.t("Anggaran Cerdas", "Smart Budget"), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_back_budget")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = com.example.util.AppStrings.t("Kembali", "Back"))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isResetConfirmDialogOpen = true },
                        modifier = Modifier.testTag("btn_reset_all_budget")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = com.example.util.AppStrings.t("Reset Semua Anggaran", "Reset All Budgets"), tint = ExpenseRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isAddCategoryDialogOpen = true },
                containerColor = FinSmartPrimary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_custom_category")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(com.example.util.AppStrings.t("+ Kategori", "+ Category"), fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {

            // Dynamic Alert Banner System
            if (alertsList.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ExpenseRed.copy(alpha = 0.08f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("budget_alert_banner")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(20.dp))
                                Text(com.example.util.AppStrings.t("Peringatan Anggaran Cerdas AI", "AI Smart Budget Alert"), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = ExpenseRed)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            alertsList.forEach { alert ->
                                Text(
                                    text = alert,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 1. Total Monthly Budget Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(FinSmartPrimary.copy(alpha = 0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Savings, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(com.example.util.AppStrings.t("Anggaran Bulanan", "Monthly Budget"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(com.example.util.AppStrings.t("Seluruh kategori digabung", "All categories combined"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            IconButton(
                                onClick = {
                                    totalLimitInput = if (totalLimitAmount > 0.0) totalLimitAmount.toLong().toString() else ""
                                    isEditTotalDialogOpen = true
                                },
                                modifier = Modifier.testTag("btn_edit_total_budget")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = com.example.util.AppStrings.t("Ubah Limit Bulanan", "Edit Monthly Limit"), tint = FinSmartPrimary)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (totalLimitAmount > 0.0) {
                            val ratio = (totalExpenseAmount / totalLimitAmount).coerceIn(0.0, 1.0).toFloat()
                            val progressColor = getProgressColor(totalExpenseAmount, totalLimitAmount)
                            val remaining = (totalLimitAmount - totalExpenseAmount).coerceAtLeast(0.0)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${com.example.util.AppStrings.t("Terpakai:", "Spent:")} ${CurrencyFormatter.formatRupiah(totalExpenseAmount)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${com.example.util.AppStrings.t("Limit:", "Limit:")} ${CurrencyFormatter.formatRupiah(totalLimitAmount)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FinSmartPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = progressColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${com.example.util.AppStrings.t("Sisa Anggaran:", "Remaining Budget:")} ${CurrencyFormatter.formatRupiah(remaining)}",
                                    fontSize = 11.sp,
                                    color = if (remaining > 0.0) IncomeGreen else ExpenseRed,
                                    fontWeight = FontWeight.Bold
                                )

                                val usagePercentage = ((totalExpenseAmount / totalLimitAmount) * 100).toInt()
                                Text(
                                    text = "$usagePercentage%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = progressColor
                                )
                            }
                        } else {
                            // Empty State
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        totalLimitInput = ""
                                        isEditTotalDialogOpen = true
                                    }
                                    .padding(vertical = 18.dp, horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = AppStrings.t("+ Klik untuk Atur Limit Anggaran Bulanan", "+ Tap to Set Monthly Budget Limit"),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = FinSmartPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Category Budget Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = com.example.util.AppStrings.t("Batas Anggaran Per Kategori", "Category Budget Limits"),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // 3. Category Limits List
            val expenseCategories = categories.filter { it.type == com.example.data.model.TransactionType.EXPENSE }
            if (expenseCategories.isEmpty()) {
                item {
                    Text(
                        text = com.example.util.AppStrings.t("Belum ada kategori pengeluaran tersedia.", "No expense categories available."),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(expenseCategories) { category ->
                    val limit = categoryLimitsMap[category.name] ?: 0.0
                    val spent = categoryExpensesMap[category.name] ?: 0.0

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val catColor = try {
                                        Color(android.graphics.Color.parseColor(category.colorHex))
                                    } catch (e: Exception) {
                                        FinSmartPrimary
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(catColor.copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = FinSmartIcons.getCategoryIcon(category.iconName),
                                            contentDescription = null,
                                            tint = catColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(category.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(
                                            text = "${com.example.util.AppStrings.t("Terpakai:", "Spent:")} ${CurrencyFormatter.formatRupiah(spent)}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (limit > 0.0) {
                                        Text(
                                            text = CurrencyFormatter.formatRupiah(limit),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FinSmartPrimary
                                        )
                                    } else {
                                        Text(
                                            text = com.example.util.AppStrings.t("Belum Diatur", "Not Set"),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            selectedCategoryName = category.name
                                            categoryLimitInput = if (limit > 0.0) limit.toLong().toString() else ""
                                            isEditCategoryDialogOpen = true
                                        },
                                        modifier = Modifier.size(32.dp).testTag("btn_edit_budget_${category.name}")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = com.example.util.AppStrings.t("Ubah Limit Kategori", "Edit Category Limit"), tint = FinSmartPrimary, modifier = Modifier.size(16.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            categoryToDelete = category
                                            isDeleteCategoryConfirmOpen = true
                                        },
                                        modifier = Modifier.size(32.dp).testTag("btn_delete_category_${category.name}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = com.example.util.AppStrings.t("Hapus Kategori", "Delete Category"), tint = ExpenseRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            if (limit > 0.0) {
                                Spacer(modifier = Modifier.height(10.dp))
                                val ratio = (spent / limit).coerceIn(0.0, 1.0).toFloat()
                                val progressColor = getProgressColor(spent, limit)

                                LinearProgressIndicator(
                                    progress = { ratio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = progressColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val remaining = (limit - spent).coerceAtLeast(0.0)
                                    Text(
                                        text = if (remaining > 0.0) "${com.example.util.AppStrings.t("Sisa limit:", "Remaining:")} ${CurrencyFormatter.formatRupiah(remaining)}" else com.example.util.AppStrings.t("Overbudget!", "Over Budget!"),
                                        fontSize = 10.sp,
                                        color = if (remaining > 0.0) IncomeGreen else ExpenseRed,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    val usagePercentage = ((spent / limit) * 100).toInt()
                                    Text(
                                        text = "$usagePercentage%",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = progressColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Total Monthly Budget Limit Dialog
    if (isEditTotalDialogOpen) {
        AlertDialog(
            onDismissRequest = { isEditTotalDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Payments, contentDescription = null, tint = FinSmartPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Atur Limit Bulanan", "Set Monthly Limit"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(com.example.util.AppStrings.t("Masukkan batas maksimal total pengeluaran bulanan gabungan Anda.", "Enter your combined monthly maximum spending limit."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = totalLimitInput,
                        onValueChange = { totalLimitInput = it },
                        label = { Text(com.example.util.AppStrings.t("Limit Anggaran (Rp)", "Budget Limit")) },
                        placeholder = { Text(com.example.util.AppStrings.t("Contoh: 5000000", "e.g. 5000000")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("input_edit_total_budget")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = totalLimitInput.trim().toDoubleOrNull() ?: 0.0
                        if (amount > 0.0) {
                            onSaveBudgetLimit("total_monthly", amount, false)
                            Toast.makeText(context, com.example.util.AppStrings.t("Limit bulanan berhasil disimpan!", "Monthly limit saved successfully!"), Toast.LENGTH_SHORT).show()
                            isEditTotalDialogOpen = false
                        } else {
                            // If 0 or empty, delete the limit
                            val item = budgetLimits.firstOrNull { it.id == "total_monthly" }
                            if (item != null) {
                                onDeleteBudgetLimit(item)
                            }
                            Toast.makeText(context, com.example.util.AppStrings.t("Limit bulanan dinonaktifkan.", "Monthly limit disabled."), Toast.LENGTH_SHORT).show()
                            isEditTotalDialogOpen = false
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_total_budget")
                ) {
                    Text(com.example.util.AppStrings.t("Simpan", "Save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditTotalDialogOpen = false }) {
                    Text(com.example.util.AppStrings.t("Batal", "Cancel"))
                }
            }
        )
    }

    // Edit Category Budget Limit Dialog
    if (isEditCategoryDialogOpen) {
        AlertDialog(
            onDismissRequest = { isEditCategoryDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = FinSmartPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppStrings.t("Limit Kategori: ", "Category Limit: ") + selectedCategoryName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(com.example.util.AppStrings.t("Masukkan batas maksimal pengeluaran bulanan untuk kategori [$selectedCategoryName]. Biarkan kosong atau 0 untuk menonaktifkan.", "Enter monthly spending limit for category [$selectedCategoryName]. Leave empty or 0 to disable."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = categoryLimitInput,
                        onValueChange = { categoryLimitInput = it },
                        label = { Text(com.example.util.AppStrings.t("Limit Kategori (Rp)", "Category Limit")) },
                        placeholder = { Text(com.example.util.AppStrings.t("Contoh: 1000000", "e.g. 1000000")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("input_edit_category_budget")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = categoryLimitInput.trim().toDoubleOrNull() ?: 0.0
                        if (amount > 0.0) {
                            onSaveBudgetLimit(selectedCategoryName, amount, true)
                            Toast.makeText(context, "${com.example.util.AppStrings.t("Limit kategori", "Category limit")} $selectedCategoryName ${com.example.util.AppStrings.t("disimpan!", "saved!")}", Toast.LENGTH_SHORT).show()
                            isEditCategoryDialogOpen = false
                        } else {
                            val item = budgetLimits.firstOrNull { it.id == selectedCategoryName }
                            if (item != null) {
                                onDeleteBudgetLimit(item)
                            }
                            Toast.makeText(context, "${com.example.util.AppStrings.t("Limit kategori", "Category limit")} $selectedCategoryName ${com.example.util.AppStrings.t("dinonaktifkan.", "disabled.")}", Toast.LENGTH_SHORT).show()
                            isEditCategoryDialogOpen = false
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_category_budget")
                ) {
                    Text(com.example.util.AppStrings.t("Simpan", "Save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditCategoryDialogOpen = false }) {
                    Text(com.example.util.AppStrings.t("Batal", "Cancel"))
                }
            }
        )
    }

    // Add Custom Category Dialog
    if (isAddCategoryDialogOpen) {
        AlertDialog(
            onDismissRequest = { isAddCategoryDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = FinSmartPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Tambah Kategori Anggaran Baru", "Add New Budget Category"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(com.example.util.AppStrings.t("Buat kategori pengeluaran baru lengkap dengan alokasi batas anggaran.", "Create a new expense category with budget limit allocation."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = newCategoryNameInput,
                        onValueChange = { newCategoryNameInput = it },
                        label = { Text(com.example.util.AppStrings.t("Nama Kategori (Misal: Langganan, Hobi)", "Category Name (e.g., Subscription, Hobby)")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_new_category_name")
                    )
                    OutlinedTextField(
                        value = newCategoryLimitInput,
                        onValueChange = { newCategoryLimitInput = it },
                        label = { Text(com.example.util.AppStrings.t("Batas Limit (Rp)", "Limit Amount")) },
                        placeholder = { Text(com.example.util.AppStrings.t("Contoh: 500000", "e.g. 500000")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("input_new_category_limit")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newCategoryNameInput.trim()
                        val limit = newCategoryLimitInput.trim().toDoubleOrNull() ?: 0.0
                        if (name.isNotBlank()) {
                            onAddCustomCategory(name, limit)
                            if (limit > 0.0) {
                                onSaveBudgetLimit(name, limit, true)
                            }
                            Toast.makeText(context, "${com.example.util.AppStrings.t("Kategori", "Category")} [$name] ${com.example.util.AppStrings.t("berhasil dibuat!", "created successfully!")}", Toast.LENGTH_SHORT).show()
                            newCategoryNameInput = ""
                            newCategoryLimitInput = ""
                            isAddCategoryDialogOpen = false
                        } else {
                            Toast.makeText(context, com.example.util.AppStrings.t("Isi nama kategori terlebih dahulu.", "Please enter a category name first."), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_add_category")
                ) {
                    Text(com.example.util.AppStrings.t("Tambah Kategori", "Add Category"))
                }
            },
            dismissButton = {
                TextButton(onClick = { isAddCategoryDialogOpen = false }) {
                    Text(com.example.util.AppStrings.t("Batal", "Cancel"))
                }
            }
        )
    }

    // Reset All Budget Limits Confirmation Dialog
    if (isResetConfirmDialogOpen) {
        AlertDialog(
            onDismissRequest = { isResetConfirmDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = ExpenseRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Reset Semua Anggaran?", "Reset All Budgets?"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    text = com.example.util.AppStrings.t("Apakah Anda yakin ingin mereset seluruh batas anggaran bulanan dan kategori menjadi nol (0)? Tindakan Fresh Start ini tidak dapat dibatalkan.", "Are you sure you want to reset all monthly and category budget limits to zero (0)? This Fresh Start action cannot be undone."),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetAllBudgetLimits()
                        Toast.makeText(context, com.example.util.AppStrings.t("Seluruh limit anggaran berhasil di-reset ke nol (Fresh Start)! 🧹", "All budget limits reset to zero (Fresh Start)! 🧹"), Toast.LENGTH_SHORT).show()
                        isResetConfirmDialogOpen = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                    modifier = Modifier.testTag("btn_confirm_reset_all_budget")
                ) {
                    Text(com.example.util.AppStrings.t("Reset Semua (1-Klik)", "Reset All (1-Click)"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { isResetConfirmDialogOpen = false }) {
                    Text(com.example.util.AppStrings.t("Batal", "Cancel"))
                }
            }
        )
    }

    // Delete Category Confirmation Dialog
    if (isDeleteCategoryConfirmOpen && categoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { isDeleteCategoryConfirmOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = ExpenseRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Hapus Kategori?", "Delete Category?"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    text = "${com.example.util.AppStrings.t("Apakah Anda yakin ingin menghapus kategori", "Are you sure you want to delete category")} [${categoryToDelete?.name}]? ${com.example.util.AppStrings.t("Tindakan ini akan menghapus kategori dari daftar.", "This action will remove the category from the list.")}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        categoryToDelete?.let { cat ->
                            onDeleteCategory(cat)
                            Toast.makeText(context, "${com.example.util.AppStrings.t("Kategori", "Category")} [${cat.name}] ${com.example.util.AppStrings.t("berhasil dihapus.", "deleted successfully.")}", Toast.LENGTH_SHORT).show()
                        }
                        isDeleteCategoryConfirmOpen = false
                        categoryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                    modifier = Modifier.testTag("btn_confirm_delete_category")
                ) {
                    Text(com.example.util.AppStrings.t("Hapus", "Delete"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { isDeleteCategoryConfirmOpen = false }) {
                    Text(com.example.util.AppStrings.t("Batal", "Cancel"))
                }
            }
        )
    }
}
