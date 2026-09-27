package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FinSmartPrimary
import com.example.util.CurrencyFormatter

@Composable
fun WalletCard(
    wallet: Wallet,
    isPrivacyMode: Boolean,
    onEdit: (Wallet) -> Unit,
    onDelete: (Wallet) -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val baseColor = try {
        Color(android.graphics.Color.parseColor(wallet.colorHex))
    } catch (e: Exception) {
        FinSmartIcons.getWalletPresetColor(wallet.iconName)
    }

    Card(
        modifier = modifier
            .width(220.dp)
            .height(130.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            )
            .testTag("wallet_card_${wallet.id}"),
        shape = RoundedCornerShape(20.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            baseColor,
                            baseColor.copy(alpha = 0.8f),
                            Color(0xFF0F172A)
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Type Tag, Wallet Name & More Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = FinSmartIcons.getWalletIcon(wallet.iconName),
                                contentDescription = wallet.name,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = wallet.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu Dompet",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Dompet") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit(wallet)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Hapus Dompet", color = ExpenseRed) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ExpenseRed) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete(wallet)
                                }
                            )
                        }
                    }
                }

                // Middle: Account Number
                if (wallet.accountNumber.isNotBlank()) {
                    Text(
                        text = if (isPrivacyMode) "•••• ••••" else wallet.accountNumber,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                } else {
                    Text(
                        text = wallet.type.displayName,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp
                    )
                }

                // Bottom: Balance
                Column {
                    Text(
                        text = "Saldo",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                    Text(
                        text = if (isPrivacyMode) "Rp •••••••" else CurrencyFormatter.formatRupiah(wallet.balance),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AddWalletPlaceholderCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(130.dp)
            .height(130.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .testTag("btn_add_new_wallet_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Tambah Dompet",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tambah\nDompet",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun WalletsSection(
    wallets: List<Wallet>,
    isPrivacyMode: Boolean,
    onAddWalletClick: () -> Unit,
    onEditWallet: (Wallet) -> Unit,
    onDeleteWallet: (Wallet) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Dompet & Rekening",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(
                onClick = onAddWalletClick,
                modifier = Modifier.testTag("btn_add_wallet_header")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Tambah", fontSize = 12.sp)
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(wallets, key = { it.id }) { wallet ->
                WalletCard(
                    wallet = wallet,
                    isPrivacyMode = isPrivacyMode,
                    onEdit = onEditWallet,
                    onDelete = onDeleteWallet
                )
            }
            item {
                AddWalletPlaceholderCard(onClick = onAddWalletClick)
            }
        }
    }
}

@Composable
fun WalletDialog(
    existingWallet: Wallet?,
    onDismiss: () -> Unit,
    onSave: (name: String, type: WalletType, accountNumber: String, initialBalance: Double, colorHex: String, iconName: String, existingId: Long?) -> Unit
) {
    var name by remember { mutableStateOf(existingWallet?.name ?: "") }
    var selectedType by remember { mutableStateOf(existingWallet?.type ?: WalletType.BANK) }
    var accountNumber by remember { mutableStateOf(existingWallet?.accountNumber ?: "") }
    var initialBalanceText by remember { mutableStateOf(if (existingWallet != null) existingWallet.balance.toLong().toString() else "0") }
    var selectedColor by remember { mutableStateOf(existingWallet?.colorHex ?: "#0B57D0") }
    var selectedIcon by remember { mutableStateOf(existingWallet?.iconName ?: "bank") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val presetWallets = listOf(
        PresetOption("BCA", WalletType.BANK, "#005DAA", "bca"),
        PresetOption("BRI", WalletType.BANK, "#00529C", "bri"),
        PresetOption("Mandiri", WalletType.BANK, "#003D79", "mandiri"),
        PresetOption("BNI", WalletType.BANK, "#E55300", "bni"),
        PresetOption("GoPay", WalletType.EWALLET, "#00AA13", "gopay"),
        PresetOption("DANA", WalletType.EWALLET, "#118EEA", "dana"),
        PresetOption("OVO", WalletType.EWALLET, "#4C2A86", "ovo"),
        PresetOption("ShopeePay", WalletType.EWALLET, "#EE4D2D", "shopeepay"),
        PresetOption("Uang Tunai", WalletType.CASH, "#10B981", "cash")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingWallet == null) "Tambah Dompet / Rekening" else "Edit Dompet",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Preset Quick Chips (only for new wallets)
                if (existingWallet == null) {
                    Text("Pilih Template Populer:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(presetWallets) { preset ->
                            FilterChip(
                                selected = name == preset.name,
                                onClick = {
                                    name = preset.name
                                    selectedType = preset.type
                                    selectedColor = preset.colorHex
                                    selectedIcon = preset.iconName
                                },
                                label = { Text(preset.name, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Nama Dompet / Bank") },
                    placeholder = { Text("misal: BCA Utama, GoPay, Tunai") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_wallet_name")
                )

                // Type Chips
                Text("Tipe Dompet:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(WalletType.BANK, WalletType.EWALLET, WalletType.CASH).forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.displayName, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it },
                    label = { Text("No. Rekening / No. HP (Opsional)") },
                    placeholder = { Text("misal: 1234567890") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_wallet_number")
                )

                if (existingWallet == null) {
                    OutlinedTextField(
                        value = initialBalanceText,
                        onValueChange = { initialBalanceText = it },
                        label = { Text("Saldo Awal (Rp)") },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_wallet_balance")
                    )
                }

                // Color choices
                Text("Pilih Warna Tema:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("#0B57D0", "#00897B", "#10B981", "#E55300", "#4C2A86", "#EE4D2D", "#003D79", "#D97706").forEach { hex ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = hex }
                                .border(
                                    width = if (selectedColor == hex) 2.dp else 0.dp,
                                    color = if (selectedColor == hex) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == hex) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                errorMessage?.let { msg ->
                    Text(text = msg, color = ExpenseRed, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMessage = "Nama dompet tidak boleh kosong."
                        return@Button
                    }
                    val balance = initialBalanceText.toDoubleOrNull() ?: 0.0
                    onSave(
                        name.trim(),
                        selectedType,
                        accountNumber.trim(),
                        balance,
                        selectedColor,
                        selectedIcon,
                        existingWallet?.id
                    )
                },
                modifier = Modifier.testTag("btn_save_wallet")
            ) {
                Text(if (existingWallet == null) "Tambah Dompet" else "Simpan Perubahan")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun DeleteWalletConfirmDialog(
    wallet: Wallet,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hapus Dompet?", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Apakah Anda yakin ingin menghapus dompet '${wallet.name}'?\n" +
                        "Saldo saat ini: ${CurrencyFormatter.formatRupiah(wallet.balance)}.\n" +
                        "Data transaksi terkait dompet ini akan tetap tersimpan."
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                modifier = Modifier.testTag("btn_confirm_delete_wallet")
            ) {
                Text("Hapus", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

private data class PresetOption(
    val name: String,
    val type: WalletType,
    val colorHex: String,
    val iconName: String
)
