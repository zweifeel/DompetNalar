package com.example.ui.components
import com.example.util.AppStrings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.data.model.InstallmentPeriod
import com.example.data.model.InstallmentPlanItem
import com.example.data.model.PayLaterBillScanResult
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.IncomeGreen
import com.example.util.CurrencyFormatter
import com.example.util.InstallmentDateHelper
import com.example.util.PayLaterDeduplicator
import com.example.util.ReceiptCalculator
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

class FormItemState(
    providerName: String,
    productName: String,
    currentBillText: String,
    totalDebtText: String,
    installmentCurrentText: String,
    installmentTotalText: String,
    dueDayText: String,
    dueMonthText: String,
    dueYearText: String,
    interestRateText: String,
    selectedCategory: String
) {
    var providerName by mutableStateOf(providerName)
    var productName by mutableStateOf(productName)
    var currentBillText by mutableStateOf(currentBillText)
    var totalDebtText by mutableStateOf(totalDebtText)
    var installmentCurrentText by mutableStateOf(installmentCurrentText)
    var installmentTotalText by mutableStateOf(installmentTotalText)
    var dueDayText by mutableStateOf(dueDayText)
    var dueMonthText by mutableStateOf(dueMonthText)
    var dueYearText by mutableStateOf(dueYearText)
    var interestRateText by mutableStateOf(interestRateText)
    var selectedCategory by mutableStateOf(selectedCategory)
}

private fun formatNominalRupiah(value: Double): String {
    return NumberFormat.getNumberInstance(Locale("id", "ID")).format(value.toLong())
}

private fun formatNominalRupiah(value: Long): String {
    return NumberFormat.getNumberInstance(Locale("id", "ID")).format(value)
}

private fun formatInputDigits(input: String): String {
    val digits = input.filter { it.isDigit() }
    val number = digits.toLongOrNull() ?: return ""
    return formatNominalRupiah(number)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PayLaterBillReviewDialog(
    scanResult: PayLaterBillScanResult? = null,
    batchScanResults: List<PayLaterBillScanResult> = emptyList(),
    existingDebts: List<DebtItem>,
    existingInstallmentPlans: List<InstallmentPlanItem> = emptyList(),
    currentUserId: Long?,
    onConfirmSave: (DebtItem) -> Unit,
    onSaveAsInstallmentPlan: ((InstallmentPlanItem) -> Unit)? = null,
    onSaveAsInstallmentPlanWithPeriods: ((InstallmentPlanItem, List<InstallmentPeriod>) -> Unit)? = null,
    onSaveBatch: ((List<DebtItem>, List<Pair<InstallmentPlanItem, List<InstallmentPeriod>>>) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val itemsList = remember(scanResult, batchScanResults) {
        val list = mutableStateListOf<PayLaterBillScanResult>()
        if (batchScanResults.isNotEmpty()) {
            list.addAll(batchScanResults)
        } else if (scanResult != null) {
            list.add(scanResult)
        }
        list
    }

    val itemConfirmationMap = remember { mutableStateMapOf<String, Boolean>() }

    var activeIndex by remember { mutableStateOf(0) }
    if (itemsList.isEmpty()) {
        onDismiss()
        return
    }

    if (activeIndex >= itemsList.size) {
        activeIndex = itemsList.size - 1
    }

    val currentItem = itemsList[activeIndex]

    // Form editing states for current item (retained across navigation)
    val itemStates = remember { mutableStateMapOf<String, FormItemState>() }
    val editState = itemStates.getOrPut(currentItem.id) {
        val now = Calendar.getInstance()
        FormItemState(
            providerName = PayLaterDeduplicator.normalizePlatformName(currentItem.providerName),
            productName = currentItem.productName ?: "",
            currentBillText = if (currentItem.currentBillAmount > 0) formatNominalRupiah(currentItem.currentBillAmount) else "",
            totalDebtText = if (currentItem.totalRemainingAmount > 0) formatNominalRupiah(currentItem.totalRemainingAmount) else "",
            installmentCurrentText = currentItem.installmentCurrent?.toString() ?: "",
            installmentTotalText = currentItem.installmentTotal?.toString() ?: "",
            dueDayText = (currentItem.dueDay ?: (currentItem.dueIsoDate?.split("-")?.getOrNull(2)?.toIntOrNull() ?: 15)).toString(),
            dueMonthText = (currentItem.dueMonth ?: (currentItem.dueIsoDate?.split("-")?.getOrNull(1)?.toIntOrNull() ?: (now.get(Calendar.MONTH) + 1))).toString(),
            dueYearText = (currentItem.dueYear ?: (currentItem.dueIsoDate?.split("-")?.getOrNull(0)?.toIntOrNull() ?: now.get(Calendar.YEAR))).toString(),
            interestRateText = currentItem.interestRate?.let { if (it > 0) it.toString() else "" } ?: "",
            selectedCategory = "Pinjaman Online"
        )
    }

    var providerName by editState::providerName
    var productName by editState::productName
    var currentBillText by editState::currentBillText
    var totalDebtText by editState::totalDebtText
    var installmentCurrentText by editState::installmentCurrentText
    var installmentTotalText by editState::installmentTotalText
    var dueDayText by editState::dueDayText
    var dueMonthText by editState::dueMonthText
    var dueYearText by editState::dueYearText
    var interestRateText by editState::interestRateText
    var selectedCategory by editState::selectedCategory

    val availableCategories = listOf("Pinjaman Online", "Kartu Kredit", "Cicilan Kendaraan", "Lain-lain")

    // Live Calendar Date Validation
    val parsedDay = dueDayText.toIntOrNull() ?: 0
    val parsedMonth = dueMonthText.toIntOrNull() ?: 0
    val parsedYear = dueYearText.toIntOrNull() ?: 0

    val isDateValid = remember(parsedDay, parsedMonth, parsedYear) {
        InstallmentDateHelper.isValidDate(parsedYear, parsedMonth, parsedDay)
    }

    val calendarValidationError = remember(parsedDay, parsedMonth, parsedYear, isDateValid) {
        if (isDateValid) null
        else if (parsedYear < 2000 || parsedYear > 2100) "Tahun harus antara 2000 - 2100"
        else if (parsedMonth !in 1..12) "Bulan harus antara 1 - 12"
        else if (parsedMonth == 2 && parsedDay == 29 && !InstallmentDateHelper.isValidDate(parsedYear, 2, 29)) {
            "$parsedYear bukan tahun kabisat (Februari hanya sampai tanggal 28)"
        } else if (parsedDay !in 1..31) "Hari harus antara 1 - 31"
        else "Tanggal $parsedDay/$parsedMonth/$parsedYear tidak valid dalam kalender"
    }

    val formattedIsoDate = remember(parsedDay, parsedMonth, parsedYear, isDateValid) {
        if (isDateValid) String.format(Locale.ROOT, "%04d-%02d-%02d", parsedYear, parsedMonth, parsedDay)
        else currentItem.dueDate
    }

    val isCurrentItemDateAmbiguous = currentItem.isDateAmbiguous ||
        currentItem.dateConfidenceStatus == "NEEDS_USER_CONFIRMATION" ||
        currentItem.dueIsoDate.isNullOrBlank() ||
        !InstallmentDateHelper.isValidIsoDate(currentItem.dueIsoDate)

    val isDateConfirmed = if (!isCurrentItemDateAmbiguous) true else (itemConfirmationMap[currentItem.id] == true)

    // Deduplication check against DB and Batch
    val duplicateCheck by remember(providerName, currentBillText, totalDebtText, formattedIsoDate, activeIndex) {
        derivedStateOf {
            val cBill = ReceiptCalculator.parseAmountSafe(currentBillText)
            val tDebt = ReceiptCalculator.parseAmountSafe(totalDebtText)

            val dbCheck = PayLaterDeduplicator.checkDuplicate(
                targetUserId = currentUserId,
                userDebts = existingDebts,
                userInstallmentPlans = existingInstallmentPlans,
                providerName = providerName,
                currentBillAmount = cBill,
                totalRemainingAmount = tDebt,
                dueDateStr = formattedIsoDate
            )
            if (dbCheck.isDuplicate) return@derivedStateOf dbCheck

            // Intra-batch check
            PayLaterDeduplicator.checkDuplicateInBatch(
                candidateIndex = activeIndex,
                candidate = currentItem.copy(
                    providerName = providerName,
                    currentBillAmount = cBill,
                    totalRemainingAmount = tDebt,
                    dueDate = formattedIsoDate
                ),
                batch = itemsList
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
            // Header with Batch Counter
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
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = FinSmartPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (itemsList.size > 1) "Review Tagihan (${activeIndex + 1} dari ${itemsList.size})" else "Review Hasil Scan Tagihan",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = FinSmartPrimary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (itemsList.size > 1) {
                        IconButton(
                            onClick = {
                                val removedId = currentItem.id
                                itemsList.removeAt(activeIndex)
                                itemConfirmationMap.remove(removedId)
                                if (itemsList.isEmpty()) onDismiss()
                                else if (activeIndex >= itemsList.size) activeIndex = itemsList.size - 1
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = com.example.util.AppStrings.t("Hapus item ini", "Delete this item"), tint = ExpenseRed)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = com.example.util.AppStrings.t("Tutup", "Close"))
                    }
                }
            }

            val syncCurrentItem: () -> Unit = {
                if (activeIndex in itemsList.indices) {
                    val curInstallment = installmentCurrentText.toIntOrNull()
                    val pCurrentBill = ReceiptCalculator.parseAmountSafe(currentBillText)
                    val pTotalDebt = ReceiptCalculator.parseAmountSafe(totalDebtText)
                    val updated = currentItem.copy(
                        providerName = providerName.trim(),
                        productName = productName.trim().ifBlank { null },
                        currentBillAmount = pCurrentBill,
                        totalRemainingAmount = pTotalDebt,
                        installmentCurrent = curInstallment,
                        installmentTotal = installmentTotalText.toIntOrNull(),
                        dueDate = formattedIsoDate,
                        dueDay = parsedDay,
                        dueMonth = parsedMonth,
                        dueYear = parsedYear,
                        dueIsoDate = formattedIsoDate.takeIf { isDateValid },
                        isDateAmbiguous = if (isDateConfirmed) false else isCurrentItemDateAmbiguous,
                        dateConfidenceStatus = if (isDateConfirmed) "USER_CONFIRMED" else currentItem.dateConfidenceStatus
                    )
                    itemsList[activeIndex] = updated
                }
            }

            // Batch Navigation Bar & Platform Groups (if multiple items)
            if (itemsList.size > 1) {
                Spacer(modifier = Modifier.height(6.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(itemsList.size) { idx ->
                        val itm = itemsList[idx]
                        val isCurrent = idx == activeIndex
                        val pLabel = itm.providerName.ifBlank { "Item ${idx + 1}" }
                        FilterChip(
                            selected = isCurrent,
                            onClick = {
                                syncCurrentItem()
                                activeIndex = idx
                            },
                            label = {
                                Text(
                                    text = "${idx + 1}. $pLabel",
                                    fontSize = 11.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            syncCurrentItem()
                            if (activeIndex > 0) activeIndex--
                        },
                        enabled = activeIndex > 0,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_review_prev")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = com.example.util.AppStrings.previous, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(1.dp))
                        Text(com.example.util.AppStrings.previous, fontSize = 12.sp, maxLines = 1)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1.3f)
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Item ${activeIndex + 1}/${itemsList.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        val safeProvider = providerName.ifBlank { "Penyedia Tidak Dikenal" }
                        Text(
                            text = "($safeProvider)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = {
                            syncCurrentItem()
                            if (activeIndex < itemsList.size - 1) {
                                activeIndex++
                            }
                        },
                        enabled = activeIndex < itemsList.size - 1,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FinSmartPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_review_next")
                    ) {
                        Text(com.example.util.AppStrings.next, fontSize = 12.sp, maxLines = 1)
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = com.example.util.AppStrings.next, modifier = Modifier.size(15.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Ambiguity / Confirmation Warning Banner & Mandatory Checkbox
            if (isCurrentItemDateAmbiguous) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDateConfirmed) IncomeGreen.copy(alpha = 0.12f) else Color(0xFFFEF3C7)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isDateConfirmed) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isDateConfirmed) IncomeGreen else Color(0xFFD97706),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isDateConfirmed) "Tanggal Terkonfirmasi oleh Anda" else "Konfirmasi Tanggal Wajib Diperlukan",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDateConfirmed) IncomeGreen else Color(0xFF92400E)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Komponen bulan atau tahun tidak tertera lengkap pada gambar. Periksa hari, bulan, dan tahun di bawah secara seksama.",
                            fontSize = 11.sp,
                            color = Color(0xFF92400E)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isDateValid) {
                                    if (isDateValid) {
                                        val current = itemConfirmationMap[currentItem.id] ?: false
                                        itemConfirmationMap[currentItem.id] = !current
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = itemConfirmationMap[currentItem.id] ?: false,
                                onCheckedChange = { checked ->
                                    if (isDateValid) {
                                        itemConfirmationMap[currentItem.id] = checked
                                    }
                                },
                                enabled = isDateValid,
                                modifier = Modifier.testTag("checkbox_confirm_ambiguous_date")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = AppStrings.t("Saya sudah memeriksa dan mengonfirmasi bahwa hari, bulan, dan tahun jatuh tempo di atas sudah benar.", "I have verified and confirmed that the due day, month, and year above are correct."),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDateValid) Color(0xFF92400E) else Color.Gray
                            )
                        }
                    }
                }
            }

            // Duplicate Detection Banner
            if (duplicateCheck.isDuplicate) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ExpenseRed.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = duplicateCheck.reason ?: "Peringatan: Tagihan serupa sudah tersimpan.",
                            fontSize = 11.sp,
                            color = ExpenseRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Form Fields
            OutlinedTextField(
                value = providerName,
                onValueChange = { providerName = it },
                label = { Text(com.example.util.AppStrings.t("Penyedia Layanan / Platform *", "Service Provider / Platform *")) },
                placeholder = { Text(com.example.util.AppStrings.t("misal: GoPay Pinjam, DANA Cicil", "e.g. Loan Provider, PayLater")) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("input_paylater_provider")
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = productName,
                onValueChange = { productName = it },
                label = { Text(com.example.util.AppStrings.t("Nama Produk / Kontrak", "Product / Contract Name")) },
                placeholder = { Text(com.example.util.AppStrings.t("misal: Pinjaman Tunai #1, Cicilan 4x", "e.g. Cash Loan #1, 4x Installments")) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Amounts (Tagihan Berjalan & Total Sisa Pokok - Responsive Layout & IDR Formatting)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = currentBillText,
                    onValueChange = { currentBillText = formatInputDigits(it) },
                    label = {
                        Text(
                            text = com.example.util.AppStrings.t("Tagihan Berjalan", "Current Bill"),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    prefix = { Text(com.example.util.CurrencyFormatter.activeCurrency.prefix + " ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    placeholder = { Text("0", fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("input_paylater_current_bill")
                )

                OutlinedTextField(
                    value = totalDebtText,
                    onValueChange = { totalDebtText = formatInputDigits(it) },
                    label = {
                        Text(
                            text = com.example.util.AppStrings.t("Total Sisa Pokok", "Total Remaining Principal"),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    prefix = { Text(com.example.util.CurrencyFormatter.activeCurrency.prefix + " ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    placeholder = { Text("0", fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("input_paylater_total_debt")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tenor
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = installmentCurrentText,
                    onValueChange = { installmentCurrentText = it },
                    label = { Text(com.example.util.AppStrings.t("Cicilan Ke", "Installment No.")) },
                    placeholder = { Text("1") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = installmentTotalText,
                    onValueChange = { installmentTotalText = it },
                    label = { Text(com.example.util.AppStrings.t("Total Tenor", "Total Tenor")) },
                    placeholder = { Text("6") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // STRUCTURED CALENDAR DUE DATE EDITOR
            Text(
                text = AppStrings.t("Tanggal Jatuh Tempo Kalender (Hari / Bulan / Tahun):", "Calendar Due Date (Day / Month / Year):"),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = dueDayText,
                    onValueChange = {
                        if (it.length <= 2) {
                            dueDayText = it
                            if (isCurrentItemDateAmbiguous) {
                                itemConfirmationMap[currentItem.id] = false
                            }
                        }
                    },
                    label = { Text(com.example.util.AppStrings.t("Tgl (1-31)", "Day (1-31)")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("input_due_day")
                )

                OutlinedTextField(
                    value = dueMonthText,
                    onValueChange = {
                        if (it.length <= 2) {
                            dueMonthText = it
                            if (isCurrentItemDateAmbiguous) {
                                itemConfirmationMap[currentItem.id] = false
                            }
                        }
                    },
                    label = { Text(com.example.util.AppStrings.t("Bln (1-12)", "Month (1-12)")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("input_due_month")
                )

                OutlinedTextField(
                    value = dueYearText,
                    onValueChange = {
                        if (it.length <= 4) {
                            dueYearText = it
                            if (isCurrentItemDateAmbiguous) {
                                itemConfirmationMap[currentItem.id] = false
                            }
                        }
                    },
                    label = { Text(com.example.util.AppStrings.t("Tahun", "Year")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.2f).testTag("input_due_year")
                )
            }

            if (calendarValidationError != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = calendarValidationError,
                    color = ExpenseRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = AppStrings.t("Format tersimpan: ", "Stored format: ") + formattedIsoDate,
                    color = IncomeGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Category Chips
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = AppStrings.t("Kategori Tagihan:", "Bill Category:"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    availableCategories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            val totInstallment = installmentTotalText.toIntOrNull()
            val parsedCurrentBill = ReceiptCalculator.parseAmountSafe(currentBillText)
            val parsedTotalDebt = ReceiptCalculator.parseAmountSafe(totalDebtText)
            val canSave = isDateValid && isDateConfirmed && providerName.isNotBlank()

            val buildFinalResult: () -> PayLaterBillScanResult = {
                val curInstallment = installmentCurrentText.toIntOrNull()
                PayLaterBillScanResult(
                    id = currentItem.id,
                    providerName = providerName.trim(),
                    productName = productName.trim().ifBlank { null },
                    currentBillAmount = parsedCurrentBill,
                    totalRemainingAmount = parsedTotalDebt,
                    installmentCurrent = curInstallment,
                    installmentTotal = totInstallment,
                    dueDate = formattedIsoDate,
                    dueDay = parsedDay,
                    dueMonth = parsedMonth,
                    dueYear = parsedYear,
                    dueIsoDate = formattedIsoDate.takeIf { isDateValid },
                    isDateAmbiguous = false,
                    dateConfidenceStatus = if (isCurrentItemDateAmbiguous) "USER_CONFIRMED" else "VERIFIED",
                    billStatus = currentItem.billStatus,
                    rawNotes = currentItem.rawNotes,
                    isDemoPreset = currentItem.isDemoPreset,
                    installmentSchedule = currentItem.installmentSchedule
                )
            }

            if (totInstallment != null && totInstallment > 1) {
                Button(
                    onClick = {
                        if (!canSave) {
                            if (!isDateValid) {
                                Toast.makeText(context, "Periksa kembali tanggal jatuh tempo yang valid.", Toast.LENGTH_SHORT).show()
                            } else if (!isDateConfirmed) {
                                Toast.makeText(context, "Harap centang konfirmasi tanggal jatuh tempo sebelum menyimpan.", Toast.LENGTH_SHORT).show()
                            }
                            return@Button
                        }
                        val finalResult = buildFinalResult()
                        val planToSave = finalResult.toInstallmentPlanItem(userId = currentUserId)
                        if (planToSave != null) {
                            val periods = finalResult.toInstallmentPeriods(planToSave.id, currentUserId ?: 1L)
                            if (onSaveAsInstallmentPlanWithPeriods != null) {
                                onSaveAsInstallmentPlanWithPeriods(planToSave, periods)
                            } else {
                                onSaveAsInstallmentPlan?.invoke(planToSave)
                            }
                            Toast.makeText(context, "Kontrak cicilan '${planToSave.name}' disimpan!", Toast.LENGTH_SHORT).show()
                            val removedId = currentItem.id
                            itemsList.removeAt(activeIndex)
                            itemConfirmationMap.remove(removedId)
                            if (itemsList.isEmpty()) onDismiss()
                        }
                    },
                    enabled = canSave,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary),
                    modifier = Modifier.fillMaxWidth().testTag("btn_save_as_installment_plan")
                ) {
                    Text(AppStrings.t("Simpan sbg Rencana Cicilan", "Save as Installment Plan") + " ($totInstallment x)", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(AppStrings.t("Batal", "Cancel"))
                }

                Button(
                    onClick = {
                        if (!canSave) {
                            if (!isDateValid) {
                                Toast.makeText(context, "Periksa kembali tanggal jatuh tempo yang valid.", Toast.LENGTH_SHORT).show()
                            } else if (!isDateConfirmed) {
                                Toast.makeText(context, "Harap centang konfirmasi tanggal jatuh tempo sebelum menyimpan.", Toast.LENGTH_SHORT).show()
                            } else if (providerName.isBlank()) {
                                Toast.makeText(context, "Nama penyedia layanan wajib diisi.", Toast.LENGTH_SHORT).show()
                            }
                            return@Button
                        }
                        val finalResult = buildFinalResult()
                        val debtToSave = finalResult.toDebtItem(userId = currentUserId, customCategory = selectedCategory)
                        onConfirmSave(debtToSave)
                        Toast.makeText(context, "Tagihan '${debtToSave.name}' disimpan.", Toast.LENGTH_SHORT).show()
                        val removedId = currentItem.id
                        itemsList.removeAt(activeIndex)
                        itemConfirmationMap.remove(removedId)
                        if (itemsList.isEmpty()) onDismiss()
                    },
                    enabled = canSave,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (totInstallment != null && totInstallment > 1) MaterialTheme.colorScheme.secondary else FinSmartPrimary
                    ),
                    modifier = Modifier.weight(1.5f).testTag("btn_save_paylater_bill")
                ) {
                    Text(if (itemsList.size > 1) "Simpan Item Ini" else "Simpan Tagihan")
                }
            }

            if (itemsList.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        syncCurrentItem()
                        // Synchronize all items from itemStates before saving
                        itemsList.indices.forEach { idx ->
                            val itm = itemsList[idx]
                            val st = itemStates[itm.id]
                            if (st != null) {
                                val cBill = ReceiptCalculator.parseAmountSafe(st.currentBillText)
                                val tDebt = ReceiptCalculator.parseAmountSafe(st.totalDebtText)
                                val pDay = st.dueDayText.toIntOrNull() ?: itm.dueDay ?: 15
                                val pMonth = st.dueMonthText.toIntOrNull() ?: itm.dueMonth ?: 1
                                val pYear = st.dueYearText.toIntOrNull() ?: itm.dueYear ?: 2026
                                val dValid = InstallmentDateHelper.isValidDate(pYear, pMonth, pDay)
                                val iso = if (dValid) String.format(Locale.ROOT, "%04d-%02d-%02d", pYear, pMonth, pDay) else itm.dueDate
                                itemsList[idx] = itm.copy(
                                    providerName = st.providerName.trim(),
                                    productName = st.productName.trim().ifBlank { null },
                                    currentBillAmount = cBill,
                                    totalRemainingAmount = tDebt,
                                    installmentCurrent = st.installmentCurrentText.toIntOrNull(),
                                    installmentTotal = st.installmentTotalText.toIntOrNull(),
                                    dueDate = iso,
                                    dueDay = pDay,
                                    dueMonth = pMonth,
                                    dueYear = pYear,
                                    dueIsoDate = iso.takeIf { dValid },
                                    isDateAmbiguous = if (itemConfirmationMap[itm.id] == true) false else itm.isDateAmbiguous,
                                    dateConfidenceStatus = if (itemConfirmationMap[itm.id] == true) "USER_CONFIRMED" else itm.dateConfidenceStatus
                                )
                            }
                        }
                        val unconfirmed = itemsList.firstOrNull { it.isDateAmbiguous && itemConfirmationMap[it.id] != true }
                        if (unconfirmed != null) {
                            Toast.makeText(context, "Item '${unconfirmed.providerName.ifBlank { "Tagihan" }}' memerlukan konfirmasi tanggal jatuh tempo.", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        val blankProvider = itemsList.firstOrNull { it.providerName.isBlank() }
                        if (blankProvider != null) {
                            Toast.makeText(context, "Penyedia layanan untuk salah satu tagihan belum diisi.", Toast.LENGTH_LONG).show()
                            return@Button
                        }

                        val debtsToSave = mutableListOf<DebtItem>()
                        val plansToSave = mutableListOf<Pair<InstallmentPlanItem, List<InstallmentPeriod>>>()

                        itemsList.forEach { itm ->
                            if (itm.installmentTotal != null && itm.installmentTotal > 1) {
                                val plan = itm.toInstallmentPlanItem(userId = currentUserId)
                                if (plan != null) {
                                    val periods = itm.toInstallmentPeriods(plan.id, currentUserId ?: 1L)
                                    plansToSave.add(Pair(plan, periods))
                                } else {
                                    debtsToSave.add(itm.toDebtItem(userId = currentUserId))
                                }
                            } else {
                                debtsToSave.add(itm.toDebtItem(userId = currentUserId))
                            }
                        }

                        if (onSaveBatch != null) {
                            onSaveBatch(debtsToSave, plansToSave)
                        } else {
                            debtsToSave.forEach { onConfirmSave(it) }
                            plansToSave.forEach { (plan, periods) ->
                                onSaveAsInstallmentPlanWithPeriods?.invoke(plan, periods) ?: onSaveAsInstallmentPlan?.invoke(plan)
                            }
                        }
                        Toast.makeText(context, "Semua ${itemsList.size} tagihan berhasil disimpan!", Toast.LENGTH_SHORT).show()
                        itemsList.clear()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary),
                    modifier = Modifier.fillMaxWidth().testTag("btn_save_all_batch")
                ) {
                    Text(AppStrings.t("Simpan Semua", "Save All") + " (${itemsList.size} " + AppStrings.t("Tagihan", "Bills") + ")", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
