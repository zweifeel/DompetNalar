package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ReceiptItem
import com.example.data.model.ReceiptScanResult
import com.example.data.model.SplitBillPerson
import com.example.data.model.Wallet
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.IncomeGreen
import com.example.util.CurrencyFormatter
import com.example.util.SplitBillCalculator
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SplitBillScreen(
    receipt: ReceiptScanResult,
    wallets: List<Wallet>,
    currentUsername: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Mutable receipt state to allow live tax/service/total recalculations
    var currentReceiptState by remember(receipt) { mutableStateOf(receipt) }

    // List of Friends / Participants
    val defaultColors = listOf("#2563EB", "#10B981", "#F59E0B", "#8B5CF6", "#EC4899", "#06B6D4", "#E11D48", "#14B8A6")
    var people by remember {
        mutableStateOf(
            listOf(
                SplitBillPerson(id = "user_me", name = currentUsername.ifBlank { "Saya" }, colorHex = defaultColors[0]),
                SplitBillPerson(id = UUID.randomUUID().toString(), name = "Budi", colorHex = defaultColors[1]),
                SplitBillPerson(id = UUID.randomUUID().toString(), name = "Siti", colorHex = defaultColors[2])
            )
        )
    }

    // Receipt Items state with assignment to person IDs
    var items by remember(receipt) {
        mutableStateOf(
            if (receipt.items.isNotEmpty()) {
                receipt.items.map { it.copy(assignedPersonIds = if (it.assignedPersonIds.isEmpty()) listOf("user_me") else it.assignedPersonIds) }
            } else {
                listOf(
                    ReceiptItem(name = "Item 1", qty = 1, price = 50000.0, total = 50000.0, assignedPersonIds = listOf("user_me"))
                )
            }
        )
    }

    // Payment target info (derived strictly from user's active wallets or customizable)
    val defaultAccount = remember(wallets, currentUsername) {
        val walletWithNumber = wallets.firstOrNull { it.accountNumber.isNotBlank() }
        if (walletWithNumber != null) {
            "${walletWithNumber.name}: ${walletWithNumber.accountNumber} ($currentUsername)"
        } else if (wallets.isNotEmpty()) {
            val firstWallet = wallets.first()
            "${firstWallet.name}: [Masukkan No. Rekening] ($currentUsername)"
        } else {
            "Rekening / E-Wallet ($currentUsername)"
        }
    }
    var paymentTargetInfo by remember { mutableStateOf(defaultAccount) }

    // Dialog state for adding new person
    var isAddPersonDialogOpen by remember { mutableStateOf(false) }
    var newPersonName by remember { mutableStateOf("") }

    // Dialog state for adding / editing items
    var isItemDialogOpen by remember { mutableStateOf(false) }
    var editingItemName by remember { mutableStateOf("") }
    var editingItemQty by remember { mutableStateOf("1") }
    var editingItemPrice by remember { mutableStateOf("") }
    var editingItemId by remember { mutableStateOf<String?>(null) }

    // Helper to recalculate receipt total when items change
    fun updateReceiptTotals(newItems: List<ReceiptItem>) {
        val newSubtotal = newItems.sumOf { it.total }
        val taxRate = if (receipt.subtotal > 0) receipt.tax / receipt.subtotal else 0.10
        val serviceRate = if (receipt.subtotal > 0) receipt.serviceCharge / receipt.subtotal else 0.0
        val newTax = if (currentReceiptState.tax > 0) newSubtotal * taxRate else 0.0
        val newService = if (currentReceiptState.serviceCharge > 0) newSubtotal * serviceRate else 0.0
        val newTotal = (newSubtotal + newTax + newService - currentReceiptState.discount).coerceAtLeast(0.0)

        currentReceiptState = currentReceiptState.copy(
            subtotal = newSubtotal,
            tax = newTax,
            serviceCharge = newService,
            totalAmount = newTotal,
            items = newItems
        )
    }

    // Calculate Split Bill Result dynamically
    val splitResult = remember(currentReceiptState, people, items, paymentTargetInfo) {
        SplitBillCalculator.calculateSplitBill(
            receipt = currentReceiptState,
            people = people,
            items = items,
            paymentTargetInfo = paymentTargetInfo
        )
    }

    // Formatted WhatsApp summary
    val whatsappSummaryText = remember(splitResult) {
        SplitBillCalculator.generateWhatsAppSummary(splitResult)
    }

    fun shareToWhatsApp(text: String) {
        try {
            val whatsappIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
                setPackage("com.whatsapp")
            }
            context.startActivity(whatsappIntent)
        } catch (e: Exception) {
            // Fallback to general sharing chooser
            val chooser = Intent.createChooser(
                Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                },
                "Bagikan Rincian Split Bill via..."
            )
            context.startActivity(chooser)
        }
    }

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Split Bill FinSmart", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Rincian split bill berhasil disalin ke clipboard!", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Split Bill AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFF10B981),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "WHATSAPP READY",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
        ) {
            // Receipt Header Overview Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = currentReceiptState.merchantName.ifBlank { "Struk Belanja" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Total Tagihan (${items.size} Item + Pajak/Service):",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = CurrencyFormatter.formatRupiah(currentReceiptState.totalAmount),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = FinSmartPrimary
                        )
                    }
                }
            }

            // 1. People Section (Daftar Orang/Teman)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Group, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Daftar Teman (${people.size} Orang)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            TextButton(
                                onClick = {
                                    newPersonName = ""
                                    isAddPersonDialogOpen = true
                                },
                                modifier = Modifier.testTag("btn_add_friend_split_bill")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tambah Teman", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            people.forEach { person ->
                                val pColor = try {
                                    Color(android.graphics.Color.parseColor(person.colorHex))
                                } catch (e: Exception) {
                                    FinSmartPrimary
                                }

                                Surface(
                                    color = pColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(20.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, pColor.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(pColor, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = person.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        // Allow deleting non-primary friends and clean up their item allocations automatically
                                        if (people.size > 1 && person.id != "user_me") {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Hapus Teman",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier
                                                    .size(15.dp)
                                                    .clickable {
                                                        val deletedId = person.id
                                                        people = people.filter { it.id != deletedId }
                                                        items = items.map { it.copy(assignedPersonIds = it.assignedPersonIds.filter { pid -> pid != deletedId }) }
                                                    }
                                                    .testTag("btn_delete_friend_${person.id}")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Assign Items Section with Add/Edit/Delete Item controls
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bagi Item ke Teman:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    // + Tambah Item Manual Button
                    FilledTonalButton(
                        onClick = {
                            editingItemId = null
                            editingItemName = ""
                            editingItemQty = "1"
                            editingItemPrice = ""
                            isItemDialogOpen = true
                        },
                        modifier = Modifier.testTag("btn_add_manual_item_split_bill"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tambah Item", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            items(items, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${item.qty}x @ ${CurrencyFormatter.formatRupiah(item.price)}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = CurrencyFormatter.formatRupiah(item.total),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = FinSmartPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))

                                // Edit Item Button
                                IconButton(
                                    onClick = {
                                        editingItemId = item.id
                                        editingItemName = item.name
                                        editingItemQty = item.qty.toString()
                                        editingItemPrice = item.price.toLong().toString()
                                        isItemDialogOpen = true
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Item", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }

                                // Delete Item Button
                                IconButton(
                                    onClick = {
                                        val updatedItems = items.filter { it.id != item.id }
                                        items = updatedItems
                                        updateReceiptTotals(updatedItems)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus Item", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Pilih siapa yang pesan/ikut patungan item ini:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Chips for assigning person
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            people.forEach { person ->
                                val isAssigned = item.assignedPersonIds.contains(person.id)
                                val personColor = try {
                                    Color(android.graphics.Color.parseColor(person.colorHex))
                                } catch (e: Exception) {
                                    FinSmartPrimary
                                }

                                FilterChip(
                                    selected = isAssigned,
                                    onClick = {
                                        val updatedAssigned = if (isAssigned) {
                                            item.assignedPersonIds.filter { it != person.id }
                                        } else {
                                            item.assignedPersonIds + person.id
                                        }
                                        items = items.map { if (it.id == item.id) it.copy(assignedPersonIds = updatedAssigned) else it }
                                    },
                                    label = {
                                        Text(
                                            text = person.name,
                                            fontSize = 11.sp,
                                            fontWeight = if (isAssigned) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = if (isAssigned) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = personColor.copy(alpha = 0.2f),
                                        selectedLabelColor = personColor
                                    )
                                )
                            }
                        }

                        if (item.assignedPersonIds.size > 1) {
                            val perPersonShare = item.total / item.assignedPersonIds.size
                            Text(
                                text = "Dibagi ${item.assignedPersonIds.size} orang: ${CurrencyFormatter.formatRupiah(perPersonShare)} / orang",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // 3. Proportional Breakdown Per Person
            item {
                Text(
                    text = "Rincian Tagihan Per Orang (Proporsional Pajak & Service):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(splitResult.breakdowns, key = { it.person.id }) { breakdown ->
                val personColor = try {
                    Color(android.graphics.Color.parseColor(breakdown.person.colorHex))
                } catch (e: Exception) {
                    FinSmartPrimary
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, personColor.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(personColor.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = breakdown.person.name.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = personColor,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = breakdown.person.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "${breakdown.assignedItems.size} Item pesanan",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Total Bayar:",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = CurrencyFormatter.formatRupiah(breakdown.totalToPay),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = personColor
                                )
                            }
                        }

                        if (breakdown.assignedItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(8.dp))

                            breakdown.assignedItems.forEach { item ->
                                val isShared = item.assignedPersonIds.size > 1
                                val itemShare = if (isShared) item.total / item.assignedPersonIds.size else item.total
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${item.qty}x ${item.name}" + if (isShared) " (dibagi ${item.assignedPersonIds.size})" else "",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = CurrencyFormatter.formatRupiah(itemShare),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (breakdown.taxShare > 0 || breakdown.serviceShare > 0) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Porsi PB1 & Service:",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "+ " + CurrencyFormatter.formatRupiah(breakdown.taxShare + breakdown.serviceShare),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Payment Target Info Input with Quick Wallet Selector
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Payments, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Rekening / E-Wallet Penyelenggara (Tujuan Transfer):",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Select from user's active Wallets
                        if (wallets.isNotEmpty()) {
                            Text(
                                text = "Pilih Cepat dari Dompet Aktif:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                wallets.forEach { w ->
                                    val accStr = if (w.accountNumber.isNotBlank()) "${w.name}: ${w.accountNumber} ($currentUsername)" else "${w.name} ($currentUsername)"
                                    FilterChip(
                                        selected = paymentTargetInfo == accStr,
                                        onClick = { paymentTargetInfo = accStr },
                                        label = { Text(if (w.accountNumber.isNotBlank()) "${w.name} (${w.accountNumber})" else w.name, fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = FinSmartPrimary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        OutlinedTextField(
                            value = paymentTargetInfo,
                            onValueChange = { paymentTargetInfo = it },
                            placeholder = { Text("Contoh: BCA 1234567890 (a.n Lukas)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // 5. WhatsApp Preview & Share Action Buttons
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF075E54).copy(alpha = 0.08f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFF25D366), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Pratinjau Pesan WhatsApp Rapi",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Box for text preview
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = whatsappSummaryText,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Share & Copy Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { shareToWhatsApp(whatsappSummaryText) },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp)
                                    .testTag("btn_share_whatsapp_split_bill"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Bagikan ke WhatsApp", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                            }

                            OutlinedButton(
                                onClick = { copyToClipboard(whatsappSummaryText) },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(48.dp)
                                    .testTag("btn_copy_split_bill_text"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Salin Teks", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Person Dialog
    if (isAddPersonDialogOpen) {
        AlertDialog(
            onDismissRequest = { isAddPersonDialogOpen = false },
            title = { Text("Tambah Teman", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPersonName,
                    onValueChange = { newPersonName = it },
                    label = { Text("Nama Teman") },
                    placeholder = { Text("Contoh: Rian, Dita, Maya, dll.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPersonName.isNotBlank()) {
                            val colorIndex = people.size % defaultColors.size
                            val newPerson = SplitBillPerson(
                                id = UUID.randomUUID().toString(),
                                name = newPersonName.trim(),
                                colorHex = defaultColors[colorIndex]
                            )
                            people = people + newPerson
                            isAddPersonDialogOpen = false
                        }
                    }
                ) {
                    Text("Tambah")
                }
            },
            dismissButton = {
                TextButton(onClick = { isAddPersonDialogOpen = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Add / Edit Item Dialog
    if (isItemDialogOpen) {
        AlertDialog(
            onDismissRequest = { isItemDialogOpen = false },
            title = { Text(if (editingItemId != null) "Edit Item Pesanan" else "Tambah Item Manual", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editingItemName,
                        onValueChange = { editingItemName = it },
                        label = { Text("Nama Item / Menu") },
                        placeholder = { Text("Contoh: Nasi Goreng, Es Teh, dll.") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editingItemQty,
                            onValueChange = { editingItemQty = it },
                            label = { Text("Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editingItemPrice,
                            onValueChange = { editingItemPrice = it },
                            label = { Text("Harga Satuan (Rp)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(2f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = editingItemQty.toIntOrNull() ?: 1
                        val price = editingItemPrice.toDoubleOrNull() ?: 0.0
                        if (editingItemName.isNotBlank() && price > 0) {
                            if (editingItemId != null) {
                                val updated = items.map { item ->
                                    if (item.id == editingItemId) {
                                        item.copy(name = editingItemName.trim(), qty = qty, price = price, total = price * qty)
                                    } else item
                                }
                                items = updated
                                updateReceiptTotals(updated)
                            } else {
                                val newItem = ReceiptItem(
                                    id = UUID.randomUUID().toString(),
                                    name = editingItemName.trim(),
                                    qty = qty,
                                    price = price,
                                    total = price * qty,
                                    assignedPersonIds = listOf(people.firstOrNull()?.id ?: "user_me")
                                )
                                val updated = items + newItem
                                items = updated
                                updateReceiptTotals(updated)
                            }
                            isItemDialogOpen = false
                        }
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { isItemDialogOpen = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
