package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AccountMode
import com.example.data.model.CategoryItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.Wallet
import com.example.ui.components.AdMobBanner
import com.example.ui.components.TransactionItemRow
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.viewmodel.TimeframeFilter
import com.example.util.CurrencyFormatter

@Composable
fun TransactionsScreen(
    transactions: List<TransactionItem>,
    wallets: List<Wallet>,
    categories: List<CategoryItem>,
    accountMode: AccountMode,
    isPrivacyMode: Boolean,
    searchQuery: String,
    selectedCategoryFilter: String?,
    selectedWalletFilter: Long?,
    selectedTimeframe: TimeframeFilter,
    onSearchChange: (String) -> Unit,
    onCategoryFilterChange: (String?) -> Unit,
    onWalletFilterChange: (Long?) -> Unit,
    onTimeframeChange: (TimeframeFilter) -> Unit,
    onDeleteTransaction: (TransactionItem) -> Unit,
    onOpenExportReport: () -> Unit,
    currentUser: User? = null,
    modifier: Modifier = Modifier
) {
    var walletMenuExpanded by remember { mutableStateOf(false) }

    val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val totalExpense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("transactions_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 100.dp
        )
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Riwayat Transaksi (Mutasi)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Catatan keuangan mode ${accountMode.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onOpenExportReport,
                    modifier = Modifier.testTag("btn_export_transactions_header")
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "Ekspor Laporan Keuangan",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Search Box
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Cari judul, kategori, catatan, tag...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Hapus Pencarian")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_search_transactions")
            )
        }

        // Timeframe Filter Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(TimeframeFilter.values()) { tf ->
                    FilterChip(
                        selected = selectedTimeframe == tf,
                        onClick = { onTimeframeChange(tf) },
                        label = { Text(tf.label, fontSize = 12.sp) }
                    )
                }
            }
        }

        // Filter by Wallet & Category
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Wallet Filter Dropdown
                Box {
                    val activeWallet = wallets.firstOrNull { it.id == selectedWalletFilter }
                    FilterChip(
                        selected = selectedWalletFilter != null,
                        onClick = { walletMenuExpanded = true },
                        label = { Text(activeWallet?.name ?: "Semua Dompet", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )

                    DropdownMenu(
                        expanded = walletMenuExpanded,
                        onDismissRequest = { walletMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Semua Dompet") },
                            onClick = {
                                onWalletFilterChange(null)
                                walletMenuExpanded = false
                            }
                        )
                        wallets.forEach { w ->
                            DropdownMenuItem(
                                text = { Text(w.name) },
                                onClick = {
                                    onWalletFilterChange(w.id)
                                    walletMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Category chips (horizontal scroll)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryFilter == null,
                            onClick = { onCategoryFilterChange(null) },
                            label = { Text("Semua Kategori", fontSize = 11.sp) }
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategoryFilter == cat.name,
                            onClick = {
                                onCategoryFilterChange(if (selectedCategoryFilter == cat.name) null else cat.name)
                            },
                            label = { Text(cat.name, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        // Summary Bar for filtered items (Daily/Period Summary Breakdown)
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("summary_card_period"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Rekapitulasi Arus Kas (${selectedTimeframe.label})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val netDiff = totalIncome - totalExpense
                        Text(
                            text = if (isPrivacyMode) "Net: Rp •••" else "Net: ${if (netDiff >= 0) "+" else ""}${CurrencyFormatter.formatRupiah(netDiff)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (netDiff >= 0) IncomeGreen else ExpenseRed
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Pemasukan", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (isPrivacyMode) "Rp •••" else CurrencyFormatter.formatRupiah(totalIncome),
                                color = IncomeGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total Pengeluaran", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (isPrivacyMode) "Rp •••" else CurrencyFormatter.formatRupiah(totalExpense),
                                color = ExpenseRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Transactions grouped by date
        val groupedByDate = transactions.groupBy { CurrencyFormatter.formatRelativeDate(it.timestamp) }

        if (transactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Tidak Ada Transaksi Ditemukan",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Coba ubah kata kunci pencarian atau filter yang dipilih.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            groupedByDate.forEach { (dateHeader, txList) ->
                val dayIncome = txList.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                val dayExpense = txList.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dateHeader,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (dayIncome > 0.0) {
                                Text(
                                    text = if (isPrivacyMode) "+Rp •••" else "+${CurrencyFormatter.formatRupiah(dayIncome)}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = IncomeGreen
                                )
                            }
                            if (dayExpense > 0.0) {
                                Text(
                                    text = if (isPrivacyMode) "-Rp •••" else "-${CurrencyFormatter.formatRupiah(dayExpense)}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ExpenseRed
                                )
                            }
                        }
                    }
                }

                items(txList, key = { it.id }) { tx ->
                    TransactionItemRow(
                        transaction = tx,
                        isPrivacyMode = isPrivacyMode,
                        onDelete = onDeleteTransaction
                    )
                }
            }
        }

        // AdMob Banner Ad at bottom for Free users
        item {
            AdMobBanner(
                user = currentUser,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            )
        }
    }
}
