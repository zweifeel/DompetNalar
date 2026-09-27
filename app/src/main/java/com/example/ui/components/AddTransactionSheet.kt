package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CategoryItem
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.ExpenseRedDark
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.IncomeGreenDark
import com.example.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionSheet(
    initialType: TransactionType,
    wallets: List<Wallet>,
    allCategories: List<CategoryItem>,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        amount: Double,
        type: TransactionType,
        category: String,
        categoryIcon: String,
        walletId: Long,
        walletName: String,
        targetWalletId: Long?,
        targetWalletName: String?,
        timestamp: Long,
        note: String,
        tags: String
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var currentType by remember { mutableStateOf(initialType) }
    var amountString by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }

    var selectedWallet by remember { mutableStateOf(wallets.firstOrNull()) }
    var selectedTargetWallet by remember { mutableStateOf(wallets.getOrNull(1)) }

    val filteredCategories = allCategories.filter { it.type == currentType }
    var selectedCategory by remember(currentType, allCategories) {
        mutableStateOf(filteredCategories.firstOrNull())
    }

    var walletDropdownExpanded by remember { mutableStateOf(false) }
    var targetWalletDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val quickAmounts = listOf(10_000.0, 50_000.0, 100_000.0, 250_000.0, 500_000.0, 1_000_000.0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("add_transaction_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Catat Transaksi",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Transaction Type Tabs (Pengeluaran, Pemasukan, Transfer)
            TabRow(
                selectedTabIndex = currentType.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .padding(4.dp),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier
                            .tabIndicatorOffset(tabPositions[currentType.ordinal])
                            .clip(RoundedCornerShape(12.dp)),
                        color = when (currentType) {
                            TransactionType.EXPENSE -> ExpenseRed
                            TransactionType.INCOME -> IncomeGreen
                            TransactionType.TRANSFER -> FinSmartPrimary
                        },
                        height = 3.dp
                    )
                },
                divider = {}
            ) {
                Tab(
                    selected = currentType == TransactionType.EXPENSE,
                    onClick = { currentType = TransactionType.EXPENSE },
                    text = {
                        Text(
                            "Pengeluaran",
                            fontWeight = if (currentType == TransactionType.EXPENSE) FontWeight.Bold else FontWeight.Normal,
                            color = if (currentType == TransactionType.EXPENSE) ExpenseRedDark else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                Tab(
                    selected = currentType == TransactionType.INCOME,
                    onClick = { currentType = TransactionType.INCOME },
                    text = {
                        Text(
                            "Pemasukan",
                            fontWeight = if (currentType == TransactionType.INCOME) FontWeight.Bold else FontWeight.Normal,
                            color = if (currentType == TransactionType.INCOME) IncomeGreenDark else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                Tab(
                    selected = currentType == TransactionType.TRANSFER,
                    onClick = { currentType = TransactionType.TRANSFER },
                    text = {
                        Text(
                            "Transfer",
                            fontWeight = if (currentType == TransactionType.TRANSFER) FontWeight.Bold else FontWeight.Normal,
                            color = if (currentType == TransactionType.TRANSFER) FinSmartPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Amount Input
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Nominal Transaksi",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Rp",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (currentType) {
                                TransactionType.EXPENSE -> ExpenseRed
                                TransactionType.INCOME -> IncomeGreen
                                TransactionType.TRANSFER -> FinSmartPrimary
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = amountString,
                            onValueChange = {
                                amountString = it.filter { ch -> ch.isDigit() }
                                errorMessage = null
                            },
                            placeholder = { Text("0", fontSize = 28.sp, fontWeight = FontWeight.Bold) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.testTag("input_transaction_amount")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Amount Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(quickAmounts) { amt ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            val current = amountString.toDoubleOrNull() ?: 0.0
                            amountString = (current + amt).toLong().toString()
                        },
                        label = { Text("+${CurrencyFormatter.formatCompactRupiah(amt)}", fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Wallet Selection
            if (currentType != TransactionType.TRANSFER) {
                Text(
                    text = if (currentType == TransactionType.EXPENSE) "Sumber Dana (Dompet / Bank):" else "Disimpan ke Dompet:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                ExposedDropdownMenuBox(
                    expanded = walletDropdownExpanded,
                    onExpandedChange = { walletDropdownExpanded = !walletDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedWallet?.let { "${it.name} (${CurrencyFormatter.formatRupiah(it.balance)})" } ?: "Pilih Dompet",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = walletDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag("dropdown_wallet_source")
                    )

                    ExposedDropdownMenu(
                        expanded = walletDropdownExpanded,
                        onDismissRequest = { walletDropdownExpanded = false }
                    ) {
                        wallets.forEach { wallet ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(wallet.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            CurrencyFormatter.formatRupiah(wallet.balance),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedWallet = wallet
                                    walletDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            } else {
                // Transfer Mode: Source & Target Wallets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dari:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ExposedDropdownMenuBox(
                            expanded = walletDropdownExpanded,
                            onExpandedChange = { walletDropdownExpanded = !walletDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedWallet?.name ?: "Pilih",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = walletDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = walletDropdownExpanded,
                                onDismissRequest = { walletDropdownExpanded = false }
                            ) {
                                wallets.forEach { w ->
                                    DropdownMenuItem(
                                        text = { Text(w.name) },
                                        onClick = {
                                            selectedWallet = w
                                            walletDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "ke",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ke:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ExposedDropdownMenuBox(
                            expanded = targetWalletDropdownExpanded,
                            onExpandedChange = { targetWalletDropdownExpanded = !targetWalletDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedTargetWallet?.name ?: "Pilih",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetWalletDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = targetWalletDropdownExpanded,
                                onDismissRequest = { targetWalletDropdownExpanded = false }
                            ) {
                                wallets.filter { it.id != selectedWallet?.id }.forEach { w ->
                                    DropdownMenuItem(
                                        text = { Text(w.name) },
                                        onClick = {
                                            selectedTargetWallet = w
                                            targetWalletDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category Selection (For Income & Expense)
            if (currentType != TransactionType.TRANSFER) {
                Text(
                    text = "Kategori Transaksi:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredCategories.forEach { cat ->
                        val isSelected = selectedCategory?.name == cat.name
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedCategory = cat }
                                .testTag("cat_chip_${cat.name}"),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = FinSmartIcons.getCategoryIcon(cat.iconName),
                                    contentDescription = cat.name,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = cat.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Title / Description
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Judul / Deskripsi Singkat") },
                placeholder = {
                    Text(
                        if (currentType == TransactionType.EXPENSE) "misal: Makan Siang Nasi Padang"
                        else if (currentType == TransactionType.INCOME) "misal: Gaji Bulanan / Omset"
                        else "misal: Top Up GoPay dari BCA"
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_transaction_title")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Note & Tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Catatan Tambahan") },
                    leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (#hobi)") },
                    leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = err, color = ExpenseRed, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = {
                    val amt = amountString.toDoubleOrNull() ?: 0.0
                    if (amt <= 0) {
                        errorMessage = "Masukkan nominal transaksi yang valid (> 0)."
                        return@Button
                    }
                    if (selectedWallet == null) {
                        errorMessage = "Pilih dompet terlebih dahulu."
                        return@Button
                    }
                    if (currentType == TransactionType.TRANSFER && (selectedTargetWallet == null || selectedTargetWallet?.id == selectedWallet?.id)) {
                        errorMessage = "Pilih dompet tujuan transfer yang berbeda."
                        return@Button
                    }

                    val catName = if (currentType == TransactionType.TRANSFER) "Transfer Saldo" else (selectedCategory?.name ?: "Lain-lain")
                    val catIcon = if (currentType == TransactionType.TRANSFER) "swap_horiz" else (selectedCategory?.iconName ?: "category")
                    val txTitle = if (title.isBlank()) {
                        if (currentType == TransactionType.TRANSFER) "Transfer ke ${selectedTargetWallet?.name}" else catName
                    } else title.trim()

                    onSave(
                        txTitle,
                        amt,
                        currentType,
                        catName,
                        catIcon,
                        selectedWallet!!.id,
                        selectedWallet!!.name,
                        if (currentType == TransactionType.TRANSFER) selectedTargetWallet?.id else null,
                        if (currentType == TransactionType.TRANSFER) selectedTargetWallet?.name else null,
                        System.currentTimeMillis(),
                        note.trim(),
                        tags.trim()
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_save_transaction"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (currentType) {
                        TransactionType.EXPENSE -> ExpenseRed
                        TransactionType.INCOME -> IncomeGreen
                        TransactionType.TRANSFER -> FinSmartPrimary
                    }
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = when (currentType) {
                        TransactionType.EXPENSE -> "Catat Pengeluaran"
                        TransactionType.INCOME -> "Catat Pemasukan"
                        TransactionType.TRANSFER -> "Simpan Transfer Saldo"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
