package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CorporateFare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.example.data.model.AssetCategory
import com.example.data.model.AssetItem
import com.example.data.model.DebtItem
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.IncomeGreen
import com.example.util.CurrencyFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetTrackerModalDialog(
    totalWalletBalance: Double,
    assets: List<AssetItem> = emptyList(),
    debts: List<DebtItem> = emptyList(),
    onSaveAsset: (AssetItem) -> Unit = {},
    onDeleteAsset: (AssetItem) -> Unit = {},
    onDeleteCategory: (AssetCategory) -> Unit = {},
    onClearAllAssets: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val assetList = assets

    // Form states
    var editingAssetId by remember { mutableStateOf<String?>(null) }
    var inputAssetName by remember { mutableStateOf("") }
    var inputAssetValue by remember { mutableStateOf("") }
    var inputAssetYear by remember { mutableStateOf("2025") }
    var selectedCategory by remember { mutableStateOf(AssetCategory.ELECTRONICS) }

    // Dialog states
    var isResetConfirmDialogOpen by remember { mutableStateOf(false) }
    var isDeleteCategoryDialogOpen by remember { mutableStateOf(false) }
    var assetItemToDelete by remember { mutableStateOf<AssetItem?>(null) }

    val totalAssetsValue = assetList.sumOf { it.currentValue }
    val totalDebtsAmount = debts.sumOf { it.amount }
    val netWorth = totalWalletBalance + totalAssetsValue - totalDebtsAmount

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("asset_tracker_modal")
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
                    color = GoldAccent.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CorporateFare, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ASSET & NET WORTH TRACKER",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFFD97706)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Reset Semua Aset Button (Fresh Start)
                    IconButton(
                        onClick = { isResetConfirmDialogOpen = true },
                        modifier = Modifier.size(34.dp).testTag("btn_reset_all_assets")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset Semua Aset", tint = ExpenseRed, modifier = Modifier.size(20.dp))
                    }

                    // Hapus Kategori Button
                    IconButton(
                        onClick = { isDeleteCategoryDialogOpen = true },
                        modifier = Modifier.size(34.dp).testTag("btn_delete_asset_category_open")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Hapus Kategori Aset", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Net Worth Summary Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Estimasi Total Kekayaan Bersih (Net Worth)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = CurrencyFormatter.formatRupiah(netWorth),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (netWorth >= 0) IncomeGreen else ExpenseRed
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Breakdown (Liquid + Assets - Debts)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Saldo Kas", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(CurrencyFormatter.formatCompactRupiah(totalWalletBalance), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CorporateFare, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Aset Fisik", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(CurrencyFormatter.formatCompactRupiah(totalAssetsValue), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFD97706))
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Beban Hutang", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("-${CurrencyFormatter.formatCompactRupiah(totalDebtsAmount)}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ExpenseRed)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Registered Asset List with Edit & Delete icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daftar Aset Fisik & Estimasi Nilai (${assetList.size}):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                if (assetList.isNotEmpty()) {
                    TextButton(onClick = { isDeleteCategoryDialogOpen = true }) {
                        Text("Kelola Kategori", fontSize = 11.sp, color = FinSmartPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (assetList.isEmpty()) {
                Text(
                    text = "Belum ada aset fisik yang dicatat. Tambahkan melalui form di bawah.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                assetList.forEach { asset ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = when (asset.category) {
                                        AssetCategory.ELECTRONICS -> Icons.Default.Devices
                                        AssetCategory.VEHICLE -> Icons.Default.DirectionsCar
                                        AssetCategory.PROPERTY -> Icons.Default.CorporateFare
                                        AssetCategory.GOLD_INVESTMENT -> Icons.Default.Savings
                                    },
                                    contentDescription = null,
                                    tint = FinSmartPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(text = asset.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        text = "Beli ${asset.purchaseYear} (${asset.category.displayName})",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val isDepreciating = asset.category.annualDepreciationPercent > 0
                                    Text(
                                        text = if (isDepreciating) "Depresiasi ${asset.category.annualDepreciationPercent.toInt()}%/thn" else "Apresiasi ${(-asset.category.annualDepreciationPercent).toInt()}%/thn",
                                        fontSize = 9.sp,
                                        color = if (isDepreciating) ExpenseRed else IncomeGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = CurrencyFormatter.formatRupiah(asset.currentValue),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Awal: ${CurrencyFormatter.formatCompactRupiah(asset.initialValue)}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Edit Button (Pensil)
                                IconButton(
                                    onClick = {
                                        editingAssetId = asset.id
                                        inputAssetName = asset.name
                                        inputAssetValue = asset.initialValue.toLong().toString()
                                        inputAssetYear = asset.purchaseYear.toString()
                                        selectedCategory = asset.category
                                    },
                                    modifier = Modifier.size(28.dp).testTag("btn_edit_asset_${asset.id}")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Aset", tint = FinSmartPrimary, modifier = Modifier.size(16.dp))
                                }

                                // Delete Button (Sampah)
                                IconButton(
                                    onClick = { assetItemToDelete = asset },
                                    modifier = Modifier.size(28.dp).testTag("btn_delete_asset_${asset.id}")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus Aset", tint = ExpenseRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Add / Edit Asset Section Form
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = if (editingAssetId != null) "Edit Data Aset Fisik" else "Tambah Aset Fisik Baru:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssetCategory.values().forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat.displayName.split(" ").first(), fontSize = 10.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = inputAssetName,
                        onValueChange = { inputAssetName = it },
                        label = { Text("Nama Aset (Misal: Laptop Asus / Honda Vario)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = inputAssetValue,
                            onValueChange = { inputAssetValue = it },
                            label = { Text("Harga Beli (Rp)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = inputAssetYear,
                            onValueChange = { inputAssetYear = it },
                            label = { Text("Tahun Beli") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (editingAssetId != null) {
                            OutlinedButton(
                                onClick = {
                                    editingAssetId = null
                                    inputAssetName = ""
                                    inputAssetValue = ""
                                    inputAssetYear = "2025"
                                    selectedCategory = AssetCategory.ELECTRONICS
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Batal Edit")
                            }
                        }

                        Button(
                            onClick = {
                                val value = inputAssetValue.toDoubleOrNull()
                                val year = inputAssetYear.toIntOrNull() ?: 2025
                                if (inputAssetName.isBlank() || value == null || value <= 0) {
                                    Toast.makeText(context, "Isi nama aset dan harga beli yang valid!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                if (editingAssetId != null) {
                                    val existing = assetList.firstOrNull { it.id == editingAssetId }
                                    if (existing != null) {
                                        onSaveAsset(
                                            existing.copy(
                                                name = inputAssetName,
                                                initialValue = value,
                                                purchaseYear = year,
                                                category = selectedCategory
                                            )
                                        )
                                        Toast.makeText(context, "Data aset berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                                    }
                                    editingAssetId = null
                                } else {
                                    onSaveAsset(
                                        AssetItem(
                                            name = inputAssetName,
                                            initialValue = value,
                                            purchaseYear = year,
                                            category = selectedCategory
                                        )
                                    )
                                    Toast.makeText(context, "Aset berhasil ditambahkan!", Toast.LENGTH_SHORT).show()
                                }

                                inputAssetName = ""
                                inputAssetValue = ""
                                inputAssetYear = "2025"
                            },
                            modifier = Modifier
                                .weight(if (editingAssetId != null) 1f else 2f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary)
                        ) {
                            Text(
                                text = if (editingAssetId != null) "Perbarui Aset" else "Simpan Aset",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // 1. Reset Semua Aset Confirmation Dialog
    if (isResetConfirmDialogOpen) {
        AlertDialog(
            onDismissRequest = { isResetConfirmDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = ExpenseRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset Semua Aset?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text("Apakah Anda yakin ingin menghapus seluruh catatan aset fisik (Fresh Start)? Tindakan ini tidak dapat dibatalkan.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllAssets()
                        isResetConfirmDialogOpen = false
                        Toast.makeText(context, "Seluruh daftar aset telah di-reset (Fresh Start)! 🧹", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                    modifier = Modifier.testTag("btn_confirm_reset_assets")
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

    // 2. Hapus Kategori Aset Dialog
    if (isDeleteCategoryDialogOpen) {
        AlertDialog(
            onDismissRequest = { isDeleteCategoryDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = ExpenseRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hapus Kategori Aset", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column {
                    Text("Pilih kategori yang ingin dihapus seluruh aset fisiknya:")
                    Spacer(modifier = Modifier.height(10.dp))
                    val availableCategories = assetList.map { it.category }.distinct()
                    if (availableCategories.isEmpty()) {
                        Text("Tidak ada kategori aset aktif.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    } else {
                        availableCategories.forEach { cat ->
                            val count = assetList.count { it.category == cat }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${cat.displayName} ($count item)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Button(
                                    onClick = {
                                        onDeleteCategory(cat)
                                        isDeleteCategoryDialogOpen = false
                                        Toast.makeText(context, "Seluruh aset kategori '${cat.displayName}' berhasil dihapus!", Toast.LENGTH_SHORT).show()
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

    // 3. Delete Single Asset Item Confirmation Dialog
    assetItemToDelete?.let { asset ->
        AlertDialog(
            onDismissRequest = { assetItemToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = ExpenseRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hapus Aset?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text("Hapus aset '${asset.name}' (${asset.category.displayName}) senilai ${CurrencyFormatter.formatRupiah(asset.currentValue)}?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAsset(asset)
                        assetItemToDelete = null
                        Toast.makeText(context, "Aset '${asset.name}' berhasil dihapus!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text("Hapus", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { assetItemToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }
}
