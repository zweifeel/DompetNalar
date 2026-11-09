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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TrendingUp
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
import com.example.util.WalletBrandHelper
import com.example.util.WalletBrandInfo

@Composable
fun WalletBrandLogoIcon(
    brandInfo: WalletBrandInfo,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.22f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (brandInfo.badgeLabel) {
                        "GOPAY", "DANA", "OVO", "LINKAJA" -> Icons.Default.AccountBalanceWallet
                        "SHOPEE" -> Icons.Default.ShoppingBag
                        "CASH" -> Icons.Default.Payments
                        "INVEST" -> Icons.Default.TrendingUp
                        else -> Icons.Default.AccountBalance
                    },
                    contentDescription = brandInfo.brandName,
                    tint = brandInfo.brandColor,
                    modifier = Modifier.size(10.dp)
                )
            }
            Text(
                text = brandInfo.badgeLabel,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

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

    val brandInfo = WalletBrandHelper.detectBrand(wallet.name)
    val baseColor = try {
        if (wallet.colorHex != "#0B57D0") {
            Color(android.graphics.Color.parseColor(wallet.colorHex))
        } else {
            brandInfo.brandColor
        }
    } catch (e: Exception) {
        brandInfo.brandColor
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
                            baseColor.copy(alpha = 0.85f),
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
                // Top Row: Brand Badge & Logo Icon, Wallet Name & More Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WalletBrandLogoIcon(brandInfo = brandInfo)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = wallet.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                                text = { Text(com.example.util.AppStrings.t("Ubah Dompet", "Edit Wallet")) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit(wallet)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(com.example.util.AppStrings.t("Hapus Dompet", "Delete Wallet"), color = ExpenseRed) },
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
                        text = com.example.util.AppStrings.t("Saldo", "Balance"),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                    Text(
                        text = if (isPrivacyMode) "Rp •••••••" else CurrencyFormatter.formatRupiah(wallet.balance),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        softWrap = false
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
                    contentDescription = com.example.util.AppStrings.t("Tambah Dompet", "Add Wallet"),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = com.example.util.AppStrings.t("Tambah\nDompet", "Add\nWallet"),
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
                text = com.example.util.AppStrings.t("Dompet & Rekening", "Wallets & Accounts"),
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
                Text(com.example.util.AppStrings.t("Tambah", "Add"), fontSize = 12.sp)
            }
        }

        if (wallets.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("empty_wallets_dashboard_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = com.example.util.AppStrings.t("Belum Ada Dompet Terdaftar", "No Wallets Registered Yet"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = com.example.util.AppStrings.t(
                            "Tambahkan akun bank, dompet digital, atau uang tunai Anda untuk mulai mencatat keuangan.",
                            "Add your bank account, e-wallet, or cash to start tracking your finances."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onAddWalletClick,
                        modifier = Modifier.testTag("btn_add_wallet_empty_dashboard"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(com.example.util.AppStrings.t("Tambah Dompet Pertama", "Add First Wallet"))
                    }
                }
            }
        } else {
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
}

@Composable
fun WalletDialog(
    existingWallet: Wallet?,
    onDismiss: () -> Unit,
    onSave: (name: String, type: WalletType, accountNumber: String, initialBalance: Double, colorHex: String, iconName: String, existingId: Long?, currencyCode: String) -> Unit
) {
    var name by remember { mutableStateOf(existingWallet?.name ?: "") }
    var selectedType by remember { mutableStateOf(existingWallet?.type ?: WalletType.BANK) }
    var accountNumber by remember { mutableStateOf(existingWallet?.accountNumber ?: "") }
    var initialBalanceText by remember { mutableStateOf(if (existingWallet != null) existingWallet.balance.toLong().toString() else "0") }
    var selectedColor by remember { mutableStateOf(existingWallet?.colorHex ?: "#0B57D0") }
    var selectedIcon by remember { mutableStateOf(existingWallet?.iconName ?: "bank") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedCurrency by remember {
        mutableStateOf(existingWallet?.currencyCode ?: com.example.util.DeviceRegionResolver.resolveDeviceCurrencyCode() ?: "IDR")
    }

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
                text = if (existingWallet == null) com.example.util.AppStrings.t("Tambah Dompet / Rekening", "Add Wallet / Account") else com.example.util.AppStrings.t("Edit Dompet", "Edit Wallet"),
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
                    Text(com.example.util.AppStrings.t("Pilih Template Populer:", "Choose Popular Template:"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    label = { Text(com.example.util.AppStrings.t("Nama Dompet / Bank", "Wallet / Bank Name")) },
                    placeholder = { Text(com.example.util.AppStrings.t("misal: BCA Utama, GoPay, Tunai", "e.g., Main BCA, GoPay, Cash")) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_wallet_name")
                )

                // Currency Chips
                Text(com.example.util.AppStrings.t("Mata Uang:", "Currency:"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("IDR", "USD", "EUR", "SGD").forEach { curr ->
                        FilterChip(
                            selected = selectedCurrency == curr,
                            onClick = {
                                if (existingWallet == null || !existingWallet.hasAssignedCurrency) {
                                    selectedCurrency = curr
                                }
                            },
                            enabled = existingWallet == null || !existingWallet.hasAssignedCurrency,
                            label = { Text(curr, fontSize = 11.sp) }
                        )
                    }
                }

                // Type Chips
                Text(com.example.util.AppStrings.t("Tipe Dompet:", "Wallet Type:"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    label = { Text(com.example.util.AppStrings.t("No. Rekening / No. HP (Opsional)", "Account / Phone No. (Optional)")) },
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
                        label = { Text(com.example.util.AppStrings.t("Saldo Awal", "Initial Balance")) },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_wallet_balance")
                    )
                }

                // Color choices
                Text(com.example.util.AppStrings.t("Pilih Warna Tema:", "Choose Theme Color:"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        errorMessage = com.example.util.AppStrings.t("Nama dompet tidak boleh kosong.", "Wallet name cannot be empty.")
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
                        existingWallet?.id,
                        selectedCurrency
                    )
                },
                modifier = Modifier.testTag("btn_save_wallet")
            ) {
                Text(if (existingWallet == null) com.example.util.AppStrings.t("Tambah Dompet", "Add Wallet") else com.example.util.AppStrings.t("Simpan Perubahan", "Save Changes"))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(com.example.util.AppStrings.t("Batal", "Cancel"))
            }
        }
    )
}

@Composable
fun DeleteWalletConfirmDialog(
    wallet: Wallet,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onArchive: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(com.example.util.AppStrings.t("Hapus Dompet?", "Delete Wallet?"), fontWeight = FontWeight.Bold) },
        text = {
            Text(
                if (com.example.util.LocaleHelper.isEnglish()) {
                    "Are you sure you want to delete wallet '${wallet.name}'?\n" +
                            "Current balance: ${CurrencyFormatter.formatCurrency(wallet.balance, wallet.currencyCode)}.\n\n" +
                            "Note: Wallets with transaction history cannot be deleted permanently for financial integrity. Please use the Archive option if this wallet is no longer in use."
                } else {
                    "Apakah Anda yakin ingin menghapus dompet '${wallet.name}'?\n" +
                            "Saldo saat ini: ${CurrencyFormatter.formatRupiah(wallet.balance)}.\n\n" +
                            "Catatan: Dompet dengan riwayat data tidak dapat dihapus permanen demi integritas finansial. Silakan gunakan opsi Arsipkan jika dompet tidak lagi digunakan."
                }
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onArchive != null) {
                    OutlinedButton(
                        onClick = onArchive,
                        modifier = Modifier.testTag("btn_archive_wallet_from_dialog")
                    ) {
                        Text(com.example.util.AppStrings.t("Arsipkan", "Archive"))
                    }
                }
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                    modifier = Modifier.testTag("btn_confirm_delete_wallet")
                ) {
                    Text(com.example.util.AppStrings.t("Hapus", "Delete"), color = Color.White)
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(com.example.util.AppStrings.t("Batal", "Cancel"))
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


@Composable
fun CurrencyAssignmentWizardDialog(
    unassignedWallets: List<Wallet>,
    onAssignCurrency: (walletId: Long, currencyCode: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedWalletId by remember { mutableStateOf(unassignedWallets.firstOrNull()?.id ?: 0L) }
    var selectedCurrency by remember { 
        mutableStateOf(com.example.util.DeviceRegionResolver.resolveDeviceCurrencyCode() ?: "IDR") 
    }

    val supportedCurrencies = remember {
        listOf("IDR", "USD", "EUR", "SGD", "MYR", "JPY")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(com.example.util.AppStrings.t("Tetapkan Mata Uang Dompet", "Assign Wallet Currency"), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = com.example.util.AppStrings.t("Beberapa dompet Anda belum memiliki mata uang yang ditentukan. Pilih mata uang untuk melanjutkan transaksi secara aman.", "Some of your wallets do not have an assigned currency. Select a currency to continue transactions securely."),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                unassignedWallets.forEach { wallet ->
                    Surface(
                        color = if (selectedWalletId == wallet.id) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedWalletId = wallet.id }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(wallet.name, fontWeight = FontWeight.SemiBold)
                            Text(CurrencyFormatter.formatRupiah(wallet.balance), fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(com.example.util.AppStrings.t("Mata Uang:", "Currency:"), fontWeight = FontWeight.Medium, fontSize = 13.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    supportedCurrencies.take(4).forEach { code ->
                        FilterChip(
                            selected = selectedCurrency == code,
                            onClick = { selectedCurrency = code },
                            label = { Text(code) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedWalletId > 0L) {
                        onAssignCurrency(selectedWalletId, selectedCurrency)
                    }
                },
                enabled = unassignedWallets.isNotEmpty()
            ) {
                Text(com.example.util.AppStrings.t("Tetapkan", "Assign"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(com.example.util.AppStrings.t("Tutup", "Close"))
            }
        }
    )
}

@Composable
fun LegacyWalletClaimDialog(
    legacyWallets: List<Wallet>,
    onClaimWallet: (walletId: Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(com.example.util.AppStrings.t("Klaim Dompet Warisan", "Claim Legacy Wallets"), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = com.example.util.AppStrings.t("Ditemukan dompet dari versi sebelumnya yang belum memiliki pemilik akun. Klaim dompet untuk menghubungkannya ke akun Anda.", "Found wallets from an earlier version with no owner. Claim wallets to link them to your account."),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (legacyWallets.isEmpty()) {
                    Text(
                        text = com.example.util.AppStrings.t("Tidak ada dompet warisan yang belum diklaim.", "No unclaimed legacy wallets found."),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    legacyWallets.forEach { wallet ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(wallet.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        com.example.util.AppStrings.t("Saldo: ", "Balance: ") + CurrencyFormatter.formatRupiah(wallet.balance),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { onClaimWallet(wallet.id) },
                                    modifier = Modifier.testTag("btn_claim_wallet_" + wallet.id)
                                ) {
                                    Text(com.example.util.AppStrings.t("Klaim", "Claim"))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(com.example.util.AppStrings.t("Selesai", "Done"))
            }
        }
    )
}
