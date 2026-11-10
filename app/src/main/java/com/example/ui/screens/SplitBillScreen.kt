package com.example.ui.screens
import com.example.util.AppStrings

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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ReceiptItem
import com.example.data.model.ReceiptScanResult
import com.example.data.model.SplitBillHistoryItem
import com.example.data.model.SplitBillPerson
import com.example.data.model.Wallet
import com.example.data.model.WalletType
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
    splitBillHistory: List<SplitBillHistoryItem>,
    onSaveSplitBillHistory: (merchantName: String, date: String, totalAmount: Double, formattedText: String) -> Unit,
    onDeleteSplitBillHistory: (SplitBillHistoryItem) -> Unit,
    onNavigateBack: () -> Unit,
    onAddNewWallet: ((name: String, type: WalletType, accountNumber: String, initialBalance: Double, colorHex: String, iconName: String) -> Unit)? = null,
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
                SplitBillPerson(id = UUID.randomUUID().toString(), name = "Teman 1", colorHex = defaultColors[1]),
                SplitBillPerson(id = UUID.randomUUID().toString(), name = "Teman 2", colorHex = defaultColors[2])
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
    var selectedTab by remember { mutableStateOf(0) }
    var isResetDialogOpen by remember { mutableStateOf(false) }

    fun resetSplitBill() {
        currentReceiptState = receipt.copy(
            merchantName = "",
            date = "",
            subtotal = 0.0,
            tax = 0.0,
            serviceCharge = 0.0,
            discount = 0.0,
            totalAmount = 0.0,
            items = emptyList()
        )
        people = listOf(
            SplitBillPerson(id = "user_me", name = currentUsername.ifBlank { "Saya" }, colorHex = defaultColors[0])
        )
        items = emptyList()
        paymentTargetInfo = defaultAccount
    }

    // Dialog state for adding new person
    var isAddPersonDialogOpen by remember { mutableStateOf(false) }
    var newPersonName by remember { mutableStateOf("") }

    // Dialog state for adding / editing items
    var isItemDialogOpen by remember { mutableStateOf(false) }
    var editingItemName by remember { mutableStateOf("") }
    var editingItemQty by remember { mutableStateOf("1") }
    var editingItemPrice by remember { mutableStateOf("") }
    var editingItemId by remember { mutableStateOf<String?>(null) }
    var isAllItemsSheetOpen by remember { mutableStateOf(false) }

    // Dialog state for adding a new wallet
    var isAddWalletDialogOpen by remember { mutableStateOf(false) }
    var newWalletNameInput by remember { mutableStateOf("") }
    var newWalletNumberInput by remember { mutableStateOf("") }
    var newWalletTypeSelection by remember { mutableStateOf<WalletType>(WalletType.EWALLET) }

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
        val clip = ClipData.newPlainText(AppStrings.t("Split Bill DompetNalar", "DompetNalar Split Bill"), text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, com.example.util.AppStrings.t("Rincian split bill berhasil disalin ke clipboard!", "Split bill details copied to clipboard!"), Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Split Bill",
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
                        Icon(Icons.Default.ArrowBack, contentDescription = com.example.util.AppStrings.t("Kembali", "Back"))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isResetDialogOpen = true },
                        modifier = Modifier.testTag("btn_reset_split_bill")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = com.example.util.AppStrings.t("Reset Sesi Split Bill", "Reset Split Bill Session"), tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Elegant Tab Selection Header
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(com.example.util.AppStrings.t("Kalkulator", "Calculator"), fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.testTag("tab_split_bill_calculator")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${com.example.util.AppStrings.t("Riwayat", "History")} (${splitBillHistory.size})", fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.testTag("tab_split_bill_history")
                )
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
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
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = currentReceiptState.merchantName.ifBlank { com.example.util.AppStrings.t("Struk Belanja", "Shopping Receipt") },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${com.example.util.AppStrings.t("Total Tagihan", "Total Bill")} (${items.size} ${com.example.util.AppStrings.t("Item + Pajak/Service):", "Items + Tax/Service):")}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = CurrencyFormatter.formatRupiah(currentReceiptState.totalAmount),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = FinSmartPrimary,
                                    maxLines = 1,
                                    softWrap = false
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Icon(Icons.Default.Group, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${com.example.util.AppStrings.t("Daftar Teman", "Friends List")} (${people.size} ${com.example.util.AppStrings.t("Orang", "People")})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
                                        Text(com.example.util.AppStrings.t("Tambah Teman", "Add Friend"), fontSize = 12.sp, maxLines = 1, softWrap = false)
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
                                                        contentDescription = com.example.util.AppStrings.t("Hapus Teman", "Delete Friend"),
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
                                text = com.example.util.AppStrings.t("Bagi Item ke Teman:", "Assign Items to Friends:"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                                Text(com.example.util.AppStrings.t("Tambah Item", "Add Item"), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                    }

                    val maxDisplayCount = 2
                    val displayedItems = items.take(maxDisplayCount)

                    items(displayedItems, key = { it.id }) { item ->
                        SplitBillItemCard(
                            item = item,
                            people = people,
                            onEdit = {
                                editingItemId = item.id
                                editingItemName = item.name
                                editingItemQty = item.qty.toString()
                                editingItemPrice = item.price.toLong().toString()
                                isItemDialogOpen = true
                            },
                            onDelete = {
                                val updatedItems = items.filter { it.id != item.id }
                                items = updatedItems
                                updateReceiptTotals(updatedItems)
                            },
                            onTogglePerson = { personId ->
                                val isAssigned = item.assignedPersonIds.contains(personId)
                                val updatedAssigned = if (isAssigned) {
                                    item.assignedPersonIds.filter { it != personId }
                                } else {
                                    item.assignedPersonIds + personId
                                }
                                items = items.map { if (it.id == item.id) it.copy(assignedPersonIds = updatedAssigned) else it }
                            }
                        )
                    }

                    if (items.size > maxDisplayCount) {
                        item {
                            OutlinedButton(
                                onClick = { isAllItemsSheetOpen = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("btn_view_more_split_bill_items"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${com.example.util.AppStrings.t("Lihat Selengkapnya / Riwayat Split Bill", "View Details / History")} (${items.size} ${com.example.util.AppStrings.t("item", "items")})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // 3. Proportional Breakdown Per Person
                    item {
                        Text(
                            text = com.example.util.AppStrings.t("Rincian Tagihan Per Orang (Proporsional Pajak & Service):", "Per Person Breakdown (Proportional Tax & Service):"),
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
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
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${breakdown.assignedItems.size} Item pesanan",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = com.example.util.AppStrings.t("Total Bayar:", "Total Payable:"),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Text(
                                            text = CurrencyFormatter.formatRupiah(breakdown.totalToPay),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp,
                                            color = personColor,
                                            maxLines = 1,
                                            softWrap = false
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
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${item.qty}x ${item.name}" + if (isShared) " (dibagi ${item.assignedPersonIds.size})" else "",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f, fill = false),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = CurrencyFormatter.formatRupiah(itemShare),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }

                                    if (breakdown.taxShare > 0 || breakdown.serviceShare > 0) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = com.example.util.AppStrings.t("Porsi PB1 & Service:", "Tax & Service Share:"),
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f, fill = false),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "+ " + CurrencyFormatter.formatRupiah(breakdown.taxShare + breakdown.serviceShare),
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                softWrap = false
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
                                        text = com.example.util.AppStrings.t("Rekening / E-Wallet Penyelenggara (Tujuan Transfer):", "Organizer Bank / E-Wallet (Transfer Destination):"),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Quick Select from user's active Wallets & Add New Wallet Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = com.example.util.AppStrings.t("Pilih Cepat dari Dompet Aktif:", "Quick Select from Active Wallets:"),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    TextButton(
                                        onClick = {
                                            newWalletNameInput = ""
                                            newWalletNumberInput = ""
                                            newWalletTypeSelection = WalletType.EWALLET
                                            isAddWalletDialogOpen = true
                                        },
                                        modifier = Modifier.testTag("btn_add_new_wallet_split_bill")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(AppStrings.t("+ Tambah Dompet Baru", "+ Add New Wallet"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FinSmartPrimary, maxLines = 1, softWrap = false)
                                    }
                                }

                                if (wallets.isNotEmpty()) {
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
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = paymentTargetInfo,
                                    onValueChange = { paymentTargetInfo = it },
                                    placeholder = { Text(com.example.util.AppStrings.t("Contoh: BCA 1234567890 (a.n Nama Pemilik Rekening)", "e.g. BCA 1234567890 (Account Holder Name)")) },
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
                                        text = com.example.util.AppStrings.t("Pratinjau Pesan WhatsApp Rapi", "Formatted Message Preview"),
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
                                        Text(com.example.util.AppStrings.t("Bagikan ke WhatsApp", "Share to WhatsApp"), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
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
                                        Text(com.example.util.AppStrings.t("Salin Teks", "Copy Text"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // "Simpan ke Riwayat Patungan" Button
                                Button(
                                    onClick = {
                                        onSaveSplitBillHistory(
                                            currentReceiptState.merchantName.ifBlank { com.example.util.AppStrings.t("Struk Belanja", "Shopping Receipt") },
                                            currentReceiptState.date,
                                            currentReceiptState.totalAmount,
                                            whatsappSummaryText
                                        )
                                        Toast.makeText(context, "Kalkulasi patungan berhasil disimpan ke riwayat!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("btn_save_to_history_split_bill"),
                                    colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(com.example.util.AppStrings.t("Simpan ke Riwayat Patungan", "Save to Split Bill History"), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            } else {
                // Tab 2: Split Bill History List View
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (splitBillHistory.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Text(
                                    text = AppStrings.t("Belum ada riwayat patungan", "No split bill history yet"),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = AppStrings.t("Gunakan kalkulator untuk membagi bill & simpan ke sini.", "Use calculator to split bill & save here."),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
                        ) {
                            items(splitBillHistory, key = { it.id }) { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                                Text(
                                                    text = item.merchantName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = item.date.ifBlank { "Tanpa Tanggal" },
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = CurrencyFormatter.formatRupiah(item.totalAmount),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 15.sp,
                                                color = FinSmartPrimary,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        Divider()
                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Collapsible / scrollable preview of the whatsapp summary
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(100.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ) {
                                            LazyColumn(modifier = Modifier.padding(8.dp)) {
                                                item {
                                                    Text(
                                                        text = item.formattedText,
                                                        fontSize = 10.sp,
                                                        lineHeight = 14.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Row of actions: share, copy, delete
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { shareToWhatsApp(item.formattedText) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(38.dp)
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Bagikan", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }

                                            OutlinedButton(
                                                onClick = { copyToClipboard(item.formattedText) },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(38.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Salin", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            IconButton(
                                                onClick = { onDeleteSplitBillHistory(item) },
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Hapus Riwayat",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
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
            title = { Text(com.example.util.AppStrings.t("Tambah Teman", "Add Friend"), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = newPersonName,
                        onValueChange = { newPersonName = it },
                        label = { Text(com.example.util.AppStrings.t("Nama Teman", "Friend Name")) },
                        placeholder = { Text(com.example.util.AppStrings.t("Contoh: Rian, Dita, Maya, dll.", "e.g. John, Alice, Bob, etc.")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
                    Text(AppStrings.t("Tambah", "Add"))
                }
            },
            dismissButton = {
                TextButton(onClick = { isAddPersonDialogOpen = false }) {
                    Text(com.example.util.AppStrings.t("Batal", "Cancel"))
                }
            }
        )
    }

    // Add / Edit Item Dialog
    if (isItemDialogOpen) {
        AlertDialog(
            onDismissRequest = { isItemDialogOpen = false },
            title = { Text(if (editingItemId != null) com.example.util.AppStrings.t("Edit Item Pesanan", "Edit Order Item") else com.example.util.AppStrings.t("Tambah Item Manual", "Add Item Manually"), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editingItemName,
                        onValueChange = { editingItemName = it },
                        label = { Text(com.example.util.AppStrings.t("Nama Item / Menu", "Item / Menu Name")) },
                        placeholder = { Text(com.example.util.AppStrings.t("Contoh: Nasi Goreng, Es Teh, dll.", "e.g. Fried Rice, Iced Tea, etc.")) },
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
                            label = { Text(com.example.util.AppStrings.t("Harga Satuan (Rp)", "Unit Price")) },
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
                    Text(com.example.util.AppStrings.t("Simpan", "Save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { isItemDialogOpen = false }) {
                    Text(com.example.util.AppStrings.t("Batal", "Cancel"))
                }
            }
        )
    }

    // Add New Wallet Dialog (Directly saves to DB & Selects for Transfer)
    if (isAddWalletDialogOpen) {
        AlertDialog(
            onDismissRequest = { isAddWalletDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Payments, contentDescription = null, tint = FinSmartPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Tambah Dompet Baru", "Add New Wallet"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = newWalletNameInput,
                        onValueChange = { newWalletNameInput = it },
                        label = { Text(com.example.util.AppStrings.t("Nama Dompet / Bank", "Wallet / Bank Name")) },
                        placeholder = { Text(com.example.util.AppStrings.t("Contoh: DANA, Mandiri, BCA, OVO", "e.g. DANA, Mandiri, BCA, OVO")) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_split_bill_new_wallet_name")
                    )

                    OutlinedTextField(
                        value = newWalletNumberInput,
                        onValueChange = { newWalletNumberInput = it },
                        label = { Text(com.example.util.AppStrings.t("Nomor Rekening / No. HP", "Account / Phone No.")) },
                        placeholder = { Text(com.example.util.AppStrings.t("Contoh: 08123456789 atau 1234567890", "e.g. 08123456789 or 1234567890")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_split_bill_new_wallet_number")
                    )

                    Text(
                        text = com.example.util.AppStrings.t("Tipe Dompet:", "Wallet Type:"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            WalletType.EWALLET to "E-Wallet",
                            WalletType.BANK to com.example.util.AppStrings.t("Bank", "Bank"),
                            WalletType.CASH to com.example.util.AppStrings.t("Tunai", "Cash")
                        ).forEach { (type, label) ->
                            FilterChip(
                                selected = newWalletTypeSelection == type,
                                onClick = { newWalletTypeSelection = type },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = FinSmartPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newWalletNameInput.trim()
                        val num = newWalletNumberInput.trim()
                        if (name.isNotBlank()) {
                            val color = when (name.lowercase()) {
                                "dana" -> "#118EEA"
                                "gopay" -> "#00AED6"
                                "ovo" -> "#4C2A86"
                                "shopeepay" -> "#EE4D2D"
                                "bca" -> "#005EAA"
                                "mandiri" -> "#003366"
                                "bri" -> "#00529C"
                                "bni" -> "#F15A24"
                                else -> "#2563EB"
                            }
                            val icon = when (newWalletTypeSelection) {
                                WalletType.BANK -> "account_balance"
                                WalletType.CASH -> "payments"
                                else -> "account_balance_wallet"
                            }

                            // Save to DB
                            onAddNewWallet?.invoke(name, newWalletTypeSelection, num, 0.0, color, icon)

                            // Immediately select as target
                            paymentTargetInfo = if (num.isNotBlank()) "$name: $num ($currentUsername)" else "$name ($currentUsername)"

                            Toast.makeText(context, "${com.example.util.AppStrings.t("Dompet", "Wallet")} $name ${com.example.util.AppStrings.t("berhasil ditambahkan & dipilih!", "added & selected successfully!")}", Toast.LENGTH_SHORT).show()
                            isAddWalletDialogOpen = false
                        } else {
                            Toast.makeText(context, com.example.util.AppStrings.t("Mohon isi nama dompet.", "Please enter wallet name."), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_add_wallet_split_bill")
                ) {
                    Text(com.example.util.AppStrings.t("Simpan & Pilih", "Save & Select"))
                }
            },
            dismissButton = {
                TextButton(onClick = { isAddWalletDialogOpen = false }) {
                    Text(com.example.util.AppStrings.t("Batal", "Cancel"))
                }
            }
        )
    }

    // Reset Confirmation Dialog
    if (isResetDialogOpen) {
        AlertDialog(
            onDismissRequest = { isResetDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Reset Sesi Patungan?", "Reset Split Bill Session?"), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(com.example.util.AppStrings.t("Apakah Anda yakin ingin menghapus seluruh data kalkulasi patungan saat ini (item, teman, dan rincian transfer) dan memulainya dari awal?", "Are you sure you want to reset all current split bill calculations (items, friends, and transfer details) and start fresh?"))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        resetSplitBill()
                        isResetDialogOpen = false
                        Toast.makeText(context, com.example.util.AppStrings.t("Sesi patungan berhasil direset!", "Split bill session reset successfully!"), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(com.example.util.AppStrings.t("Ya, Reset", "Yes, Reset"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { isResetDialogOpen = false }) {
                    Text(com.example.util.AppStrings.t("Batal", "Cancel"))
                }
            }
        )
    }

    // All Items / Riwayat Split Bill Bottom Sheet
    if (isAllItemsSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isAllItemsSheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = FinSmartPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${com.example.util.AppStrings.t("Semua Item Split Bill", "All Split Bill Items")} (${items.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = { isAllItemsSheetOpen = false }) {
                        Icon(Icons.Default.Close, contentDescription = com.example.util.AppStrings.t("Tutup", "Close"))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                items.forEach { item ->
                    SplitBillItemCard(
                        item = item,
                        people = people,
                        onEdit = {
                            editingItemId = item.id
                            editingItemName = item.name
                            editingItemQty = item.qty.toString()
                            editingItemPrice = item.price.toLong().toString()
                            isItemDialogOpen = true
                        },
                        onDelete = {
                            val updatedItems = items.filter { it.id != item.id }
                            items = updatedItems
                            updateReceiptTotals(updatedItems)
                        },
                        onTogglePerson = { personId ->
                            val isAssigned = item.assignedPersonIds.contains(personId)
                            val updatedAssigned = if (isAssigned) {
                                item.assignedPersonIds.filter { it != personId }
                            } else {
                                item.assignedPersonIds + personId
                            }
                            items = items.map { if (it.id == item.id) it.copy(assignedPersonIds = updatedAssigned) else it }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { isAllItemsSheetOpen = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary)
                ) {
                    Text(com.example.util.AppStrings.t("Selesai & Kembali ke Ringkasan", "Done & Return to Summary"), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitBillItemCard(
    item: ReceiptItem,
    people: List<SplitBillPerson>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePerson: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.qty}x @ ${CurrencyFormatter.formatRupiah(item.price)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = CurrencyFormatter.formatRupiah(item.total),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = FinSmartPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    // Edit Item Button
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = com.example.util.AppStrings.t("Edit Item", "Edit Item"), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }

                    // Delete Item Button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = com.example.util.AppStrings.t("Hapus Item", "Delete Item"), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = com.example.util.AppStrings.t("Pilih siapa yang pesan/ikut patungan item ini:", "Select who ordered/shares this item:"),
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
                        onClick = { onTogglePerson(person.id) },
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
                    text = "${com.example.util.AppStrings.t("Dibagi", "Split by")} ${item.assignedPersonIds.size} ${com.example.util.AppStrings.t("orang:", "people:")} ${CurrencyFormatter.formatRupiah(perPersonShare)} / ${com.example.util.AppStrings.t("orang", "person")}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF10B981),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
