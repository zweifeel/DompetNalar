package com.example.ui.components

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPlanItem
import com.example.data.model.PayLaterBillScanResult
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FinSmartPrimary
import com.example.util.CurrencyFormatter
import com.example.util.PayLaterDeduplicator
import com.example.util.ReceiptCalculator
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PayLaterBillReviewDialog(
    scanResult: PayLaterBillScanResult,
    existingDebts: List<DebtItem>,
    existingInstallmentPlans: List<InstallmentPlanItem> = emptyList(),
    currentUserId: Long?,
    onConfirmSave: (DebtItem) -> Unit,
    onSaveAsInstallmentPlan: ((InstallmentPlanItem) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isDemoConfirmDialogOpen by remember { mutableStateOf(false) }
    var pendingSaveAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Form editing states
    var providerName by remember { mutableStateOf(scanResult.providerName) }
    var productName by remember { mutableStateOf(scanResult.productName ?: "") }

    var currentBillText by remember {
        mutableStateOf(if (scanResult.currentBillAmount > 0) scanResult.currentBillAmount.toLong().toString() else "")
    }

    var totalDebtText by remember {
        mutableStateOf(if (scanResult.totalRemainingAmount > 0) scanResult.totalRemainingAmount.toLong().toString() else "")
    }

    var installmentCurrentText by remember {
        mutableStateOf(scanResult.installmentCurrent?.toString() ?: "")
    }

    var installmentTotalText by remember {
        mutableStateOf(scanResult.installmentTotal?.toString() ?: "")
    }

    var dueDateText by remember { mutableStateOf(scanResult.dueDate) }
    var interestRateText by remember {
        mutableStateOf(scanResult.interestRate?.let { if (it > 0) it.toString() else "" } ?: "")
    }

    val availableCategories = listOf("Pinjaman Online", "Kartu Kredit", "Cicilan Kendaraan", "Lain-lain")
    var selectedCategory by remember { mutableStateOf("Pinjaman Online") }

    // Deduplication check
    val duplicateCheck by remember(providerName, currentBillText, totalDebtText, dueDateText) {
        derivedStateOf {
            val cBill = ReceiptCalculator.parseAmountSafe(currentBillText)
            val tDebt = ReceiptCalculator.parseAmountSafe(totalDebtText)
            PayLaterDeduplicator.checkDuplicate(
                targetUserId = currentUserId,
                userDebts = existingDebts,
                userInstallmentPlans = existingInstallmentPlans,
                providerName = providerName,
                currentBillAmount = cBill,
                totalRemainingAmount = tDebt,
                dueDateStr = dueDateText
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("dialog_paylater_review")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = FinSmartPrimary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = FinSmartPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TINJAU TAGIHAN HASIL SCAN AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = FinSmartPrimary
                        )
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Demo Preset Badge & Notice
            if (scanResult.isDemoPreset) {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("badge_demo_paylater"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "CONTOH PRESET / DEMO",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF0369A1)
                            )
                            Text(
                                text = "Data ini adalah simulasi offline dan bukan tagihan nyata.",
                                fontSize = 11.sp,
                                color = Color(0xFF075985)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Confidence Warnings Alert
            if (scanResult.confidenceWarnings.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ExpenseRed.copy(alpha = 0.1f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = ExpenseRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Periksa Kembali Data Tagihan:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = ExpenseRed
                            )
                            scanResult.confidenceWarnings.forEach { warn ->
                                Text(
                                    text = "• $warn",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Duplicate Detection Alert Banner
            if (duplicateCheck.isDuplicate) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFEEBA))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF856404),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Peringatan Kemungkinan Tagihan Ganda:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF856404)
                            )
                            Text(
                                text = duplicateCheck.reason ?: "Tagihan serupa sudah ada di daftar hutang Anda.",
                                fontSize = 11.sp,
                                color = Color(0xFF856404)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Form Fields
            OutlinedTextField(
                value = providerName,
                onValueChange = { providerName = it },
                label = { Text("Nama Layanan / Provider *") },
                placeholder = { Text("Contoh: Shopee PayLater, DANA Cicil") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_paylater_provider")
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = productName,
                onValueChange = { productName = it },
                label = { Text("Jenis / Produk Pinjaman (Opsional)") },
                placeholder = { Text("Contoh: SPayLater, Pinjaman Tunai") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Two-column Amount: Tagihan Bulan Ini vs Total Sisa Utang
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = currentBillText,
                    onValueChange = { currentBillText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Tagihan Bulan Ini (Rp) *") },
                    placeholder = { Text("350000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_paylater_current_bill")
                )

                OutlinedTextField(
                    value = totalDebtText,
                    onValueChange = { totalDebtText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Total Sisa Utang (Rp)") },
                    placeholder = { Text("1400000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_paylater_total_debt")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Installment Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = installmentCurrentText,
                    onValueChange = { installmentCurrentText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Cicilan Ke-") },
                    placeholder = { Text("2") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = installmentTotalText,
                    onValueChange = { installmentTotalText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Dari Total Cicilan") },
                    placeholder = { Text("6") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Due Date & Interest Rate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = dueDateText,
                    onValueChange = { dueDateText = it },
                    label = { Text("Jatuh Tempo *") },
                    placeholder = { Text("Tgl 5 tiap bulan") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("input_paylater_due_date")
                )

                OutlinedTextField(
                    value = interestRateText,
                    onValueChange = { interestRateText = it },
                    label = { Text("Bunga (%)") },
                    placeholder = { Text("0.0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(0.8f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Category Chips
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Kategori Tagihan:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableCategories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FinSmartPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = FinSmartPrimary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // If this is a multi-installment contract, offer direct saving as InstallmentPlanItem
                val totInstallment = installmentTotalText.toIntOrNull()
                if (totInstallment != null && totInstallment > 1 && onSaveAsInstallmentPlan != null) {
                    Button(
                        onClick = {
                            val parsedCurrentBill = ReceiptCalculator.parseAmountSafe(currentBillText)
                            val parsedTotalDebt = ReceiptCalculator.parseAmountSafe(totalDebtText)

                            if (providerName.isBlank()) {
                                Toast.makeText(context, "Nama penyedia layanan wajib diisi.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val curInstallment = installmentCurrentText.toIntOrNull() ?: 1
                            val finalScanResult = PayLaterBillScanResult(
                                providerName = providerName.trim(),
                                productName = productName.trim().ifBlank { null },
                                currentBillAmount = parsedCurrentBill,
                                totalRemainingAmount = parsedTotalDebt,
                                installmentCurrent = curInstallment,
                                installmentTotal = totInstallment,
                                dueDate = dueDateText.trim().ifBlank { "15 tiap bulan" },
                                interestRate = interestRateText.toDoubleOrNull() ?: 0.0,
                                billStatus = scanResult.billStatus,
                                rawNotes = scanResult.rawNotes,
                                isDemoPreset = scanResult.isDemoPreset
                            )

                            val planToSave = finalScanResult.toInstallmentPlanItem(currentUserId)
                            if (planToSave != null) {
                                val action = {
                                    onSaveAsInstallmentPlan(planToSave)
                                    Toast.makeText(context, "Rencana cicilan '${planToSave.name}' berhasil dibuat!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }

                                if (scanResult.isDemoPreset) {
                                    pendingSaveAction = action
                                    isDemoConfirmDialogOpen = true
                                } else {
                                    action()
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary),
                        modifier = Modifier.fillMaxWidth().testTag("btn_save_as_installment_plan")
                    ) {
                        Text("Simpan sbg Rencana Cicilan Berjangka ($totInstallment x)", fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("btn_cancel_paylater_review")
                    ) {
                        Text("Batal")
                    }

                    Button(
                        onClick = {
                            val parsedCurrentBill = ReceiptCalculator.parseAmountSafe(currentBillText)
                            val parsedTotalDebt = ReceiptCalculator.parseAmountSafe(totalDebtText)

                            if (providerName.isBlank()) {
                                Toast.makeText(context, "Nama penyedia layanan wajib diisi.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            if (parsedCurrentBill <= 0.0 && parsedTotalDebt <= 0.0) {
                                Toast.makeText(context, "Nominal tagihan harus lebih dari Rp 0.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val curInstallment = installmentCurrentText.toIntOrNull()
                            val totInstallmentVal = installmentTotalText.toIntOrNull()
                            val interestRate = interestRateText.toDoubleOrNull() ?: 0.0

                            val finalScanResult = PayLaterBillScanResult(
                                providerName = providerName.trim(),
                                productName = productName.trim().ifBlank { null },
                                currentBillAmount = parsedCurrentBill,
                                totalRemainingAmount = parsedTotalDebt,
                                installmentCurrent = curInstallment,
                                installmentTotal = totInstallmentVal,
                                dueDate = dueDateText.trim().ifBlank { "15 tiap bulan" },
                                interestRate = interestRate,
                                billStatus = scanResult.billStatus,
                                rawNotes = scanResult.rawNotes,
                                isDemoPreset = scanResult.isDemoPreset
                            )

                            val debtToSave = finalScanResult.toDebtItem(
                                userId = currentUserId,
                                customCategory = selectedCategory
                            )

                            val action = {
                                onConfirmSave(debtToSave)
                                Toast.makeText(context, "Tagihan ${debtToSave.name} berhasil disimpan.", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }

                            if (scanResult.isDemoPreset) {
                                pendingSaveAction = action
                                isDemoConfirmDialogOpen = true
                            } else {
                                action()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (totInstallment != null && totInstallment > 1 && onSaveAsInstallmentPlan != null)
                                MaterialTheme.colorScheme.secondary
                            else
                                FinSmartPrimary
                        ),
                        modifier = Modifier.weight(1.5f).testTag("btn_save_paylater_bill")
                    ) {
                        Text("Simpan ke Hutang Biasa", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Explicit Confirmation Dialog for Demo Preset
    if (isDemoConfirmDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                isDemoConfirmDialogOpen = false
                pendingSaveAction = null
            },
            title = {
                Text("Konfirmasi Simpan Data Demo", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Tagihan ini berasal dari preset simulasi demo offline ([CONTOH DEMO]). Apakah Anda yakin ingin menyimpannya sebagai catatan keuangan sungguhan?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDemoConfirmDialogOpen = false
                        pendingSaveAction?.invoke()
                        pendingSaveAction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary)
                ) {
                    Text("Ya, Simpan")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isDemoConfirmDialogOpen = false
                        pendingSaveAction = null
                    }
                ) {
                    Text("Batal")
                }
            }
        )
    }
}
