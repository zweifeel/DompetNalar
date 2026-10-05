package com.example.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.AccountMode
import com.example.data.model.ExportFormatConfig
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.ui.components.ExportFormatManagementDialog
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.IncomeGreen
import com.example.util.CurrencyFormatter
import com.example.util.ExportFormatManager
import com.example.util.ReportExporter
import androidx.compose.material.icons.filled.Tune
import java.io.File
import java.util.Calendar
import java.util.Date

enum class ExportPeriod {
    THIS_MONTH,
    LAST_MONTH,
    LAST_30_DAYS,
    THIS_YEAR,
    CUSTOM
}

enum class ExportFormat {
    PDF,
    EXCEL_CSV
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    allTransactions: List<TransactionItem>,
    currentAccountMode: AccountMode,
    username: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedMode by remember { mutableStateOf(currentAccountMode) }
    var selectedPeriod by remember { mutableStateOf(ExportPeriod.THIS_MONTH) }
    var selectedFormat by remember { mutableStateOf(ExportFormat.PDF) }
    var availableFormats by remember { mutableStateOf(ExportFormatManager.getAllFormats(context)) }
    var selectedFormatConfig by remember {
        mutableStateOf(
            availableFormats.find { it.id == ExportFormatManager.getActiveFormatId(context) } ?: availableFormats.first()
        )
    }
    var isManageFormatsOpen by remember { mutableStateOf(false) }

    // Custom Date Range states (millis)
    var customStartDate by remember {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        mutableStateOf(cal.timeInMillis)
    }
    var customEndDate by remember {
        mutableStateOf(System.currentTimeMillis())
    }

    // Helper to filter transactions based on parameters
    val filteredTransactions by remember(allTransactions, selectedMode, selectedPeriod, customStartDate, customEndDate) {
        derivedStateOf {
            val now = Calendar.getInstance()
            val startLimit = Calendar.getInstance()
            val endLimit = Calendar.getInstance()

            when (selectedPeriod) {
                ExportPeriod.THIS_MONTH -> {
                    startLimit.apply {
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }
                    endLimit.timeInMillis = System.currentTimeMillis()
                }
                ExportPeriod.LAST_MONTH -> {
                    startLimit.apply {
                        add(Calendar.MONTH, -1)
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }
                    endLimit.apply {
                        set(Calendar.DAY_OF_MONTH, 1)
                        add(Calendar.DAY_OF_MONTH, -1)
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                    }
                }
                ExportPeriod.LAST_30_DAYS -> {
                    startLimit.apply {
                        add(Calendar.DAY_OF_YEAR, -30)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }
                    endLimit.timeInMillis = System.currentTimeMillis()
                }
                ExportPeriod.THIS_YEAR -> {
                    startLimit.apply {
                        set(Calendar.DAY_OF_YEAR, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }
                    endLimit.timeInMillis = System.currentTimeMillis()
                }
                ExportPeriod.CUSTOM -> {
                    startLimit.timeInMillis = customStartDate
                    endLimit.timeInMillis = customEndDate
                }
            }

            allTransactions.filter { tx ->
                tx.accountMode == selectedMode.name &&
                        tx.timestamp >= startLimit.timeInMillis &&
                        tx.timestamp <= endLimit.timeInMillis
            }.sortedByDescending { it.timestamp }
        }
    }

    // Dynamic period title for display in PDF
    val displayPeriodTitle by remember(selectedPeriod, customStartDate, customEndDate) {
        derivedStateOf {
            when (selectedPeriod) {
                ExportPeriod.THIS_MONTH -> "Bulan Ini (${CurrencyFormatter.formatDate(System.currentTimeMillis(), "MMMM yyyy")})"
                ExportPeriod.LAST_MONTH -> {
                    val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                    "Bulan Lalu (${CurrencyFormatter.formatDate(cal.timeInMillis, "MMMM yyyy")})"
                }
                ExportPeriod.LAST_30_DAYS -> "30 Hari Terakhir"
                ExportPeriod.THIS_YEAR -> "Tahun ${Calendar.getInstance().get(Calendar.YEAR)}"
                ExportPeriod.CUSTOM -> "${CurrencyFormatter.formatDate(customStartDate)} s/d ${CurrencyFormatter.formatDate(customEndDate)}"
            }
        }
    }

    // Financial calculations for summary
    val summaryIncome = filteredTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val summaryExpense = filteredTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val summaryNet = summaryIncome - summaryExpense

    // Handle back button
    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Ekspor Laporan Keuangan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FinSmartPrimary
                ),
                modifier = Modifier.testTag("export_top_bar")
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .testTag("export_screen"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Explanation Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                "Fitur Cetak & Ekspor",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Ekspor pembukuan finansial pribadi atau UMKM ke dalam dokumen PDF formal atau format spreadsheet Excel.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Mode Selector (Pribadi vs Bisnis)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "1. Pilih Mode Keuangan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            listOf(AccountMode.PERSONAL, AccountMode.BUSINESS).forEach { mode ->
                                val isSel = selectedMode == mode
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedMode = mode },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        contentColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = if (isSel) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp, horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSel) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(mode.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Period Selector
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "2. Tentukan Rentang Periode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Radios
                        val periods = listOf(
                            ExportPeriod.THIS_MONTH to "Bulan Ini",
                            ExportPeriod.LAST_MONTH to "Bulan Lalu",
                            ExportPeriod.LAST_30_DAYS to "30 Hari Terakhir",
                            ExportPeriod.THIS_YEAR to "Tahun Ini",
                            ExportPeriod.CUSTOM to "Custom Tanggal (Pilih Sendiri)"
                        )

                        periods.forEach { (p, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPeriod = p }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedPeriod == p,
                                    onClick = { selectedPeriod = p },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        if (selectedPeriod == ExportPeriod.CUSTOM) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Start Date picker
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                        .clickable {
                                            showDatePicker(context, customStartDate) { customStartDate = it }
                                        }
                                        .padding(12.dp)
                                ) {
                                    Text("Dari Tanggal", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(CurrencyFormatter.formatDate(customStartDate, "dd MMM yyyy"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // End Date picker
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                        .clickable {
                                            showDatePicker(context, customEndDate) { customEndDate = it }
                                        }
                                        .padding(12.dp)
                                ) {
                                    Text("Sampai Tanggal", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(CurrencyFormatter.formatDate(customEndDate, "dd MMM yyyy"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Format Selection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "3. Pilih Format File Laporan",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedButton(
                                onClick = { isManageFormatsOpen = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp).testTag("btn_manage_export_formats")
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kelola Format", fontSize = 11.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            availableFormats.forEach { format ->
                                val isSel = selectedFormatConfig.id == format.id
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedFormatConfig = format
                                            ExportFormatManager.setActiveFormatId(context, format.id)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = if (isSel) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (format.type == "PDF") Icons.Default.Print else Icons.Default.GridOn,
                                                contentDescription = null,
                                                tint = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(format.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        color = if (format.isBuiltIn) Color(0xFF6B7280).copy(alpha = 0.2f) else FinSmartPrimary.copy(alpha = 0.2f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = if (format.isBuiltIn) "Bawaan" else "Kustom",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (format.isBuiltIn) Color(0xFF374151) else FinSmartPrimary,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                val subText = if (format.type == "PDF") {
                                                    "PDF Formal siap cetak • ${format.columns.size} Kolom"
                                                } else {
                                                    "CSV • Delimiter '${format.delimiter}' • ${format.columns.size} Kolom"
                                                }
                                                Text(subText, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Icon(
                                            imageVector = if (isSel) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Summary & Count Preview
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Ringkasan Laporan Terdeteksi",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Mutasi Transaksi:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${filteredTransactions.size} Transaksi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Pemasukan:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(CurrencyFormatter.formatRupiah(summaryIncome), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IncomeGreen)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Pengeluaran:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(CurrencyFormatter.formatRupiah(summaryExpense), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ExpenseRed)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Estimasi Keuntungan (Net):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                CurrencyFormatter.formatRupiah(summaryNet),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (summaryNet >= 0) IncomeGreen else ExpenseRed
                            )
                        }
                    }
                }
            }

            // Export Actions Section
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Action 1: Download File
                    Button(
                        onClick = {
                            if (filteredTransactions.isEmpty()) {
                                Toast.makeText(context, "Tidak ada data mutasi transaksi untuk rentang tanggal ini.", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            try {
                                val generatedFile = ReportExporter.exportWithCustomFormat(
                                    context = context,
                                    transactions = filteredTransactions,
                                    format = selectedFormatConfig,
                                    periodTitle = displayPeriodTitle,
                                    username = username
                                )

                                val savedFile = ReportExporter.saveToPublicDownloads(context, generatedFile)
                                if (savedFile != null) {
                                    Toast.makeText(
                                        context,
                                        "Laporan berhasil diunduh ke folder Downloads: ${savedFile.name}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(context, "Gagal mengunduh file ke folder Downloads.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Kesalahan ekspor: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_download_report")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Download Laporan (${selectedFormatConfig.name})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    // Action 2: Share File (Intent Share)
                    OutlinedButton(
                        onClick = {
                            if (filteredTransactions.isEmpty()) {
                                Toast.makeText(context, "Tidak ada data mutasi transaksi untuk rentang tanggal ini.", Toast.LENGTH_LONG).show()
                                return@OutlinedButton
                            }

                            try {
                                val generatedFile = ReportExporter.exportWithCustomFormat(
                                    context = context,
                                    transactions = filteredTransactions,
                                    format = selectedFormatConfig,
                                    periodTitle = displayPeriodTitle,
                                    username = username
                                )

                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", generatedFile)
                                val mimeType = if (selectedFormatConfig.type == "PDF") "application/pdf" else "text/comma-separated-values"

                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = mimeType
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    putExtra(Intent.EXTRA_SUBJECT, "Laporan Keuangan FinSmart - $displayPeriodTitle")
                                    putExtra(Intent.EXTRA_TEXT, "Halo, berikut adalah laporan keuangan FinSmart saya untuk periode $displayPeriodTitle.")
                                }

                                context.startActivity(Intent.createChooser(intent, "Bagikan Laporan Keuangan"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Kesalahan membagikan: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_share_report")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = FinSmartPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bagikan Laporan (Share via WhatsApp, dll)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = FinSmartPrimary
                        )
                    }
                }
            }
        }
    }

    if (isManageFormatsOpen) {
        ExportFormatManagementDialog(
            onDismiss = {
                isManageFormatsOpen = false
                availableFormats = ExportFormatManager.getAllFormats(context)
                val activeId = ExportFormatManager.getActiveFormatId(context)
                selectedFormatConfig = availableFormats.find { it.id == activeId } ?: availableFormats.first()
            },
            onFormatSelected = { format ->
                selectedFormatConfig = format
            }
        )
    }
}

// Show native Android DatePicker
private fun showDatePicker(context: Context, currentMillis: Long, onDateSelected: (Long) -> Unit) {
    val calendar = Calendar.getInstance().apply { timeInMillis = currentMillis }
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val selectedCalendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            onDateSelected(selectedCalendar.timeInMillis)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}
