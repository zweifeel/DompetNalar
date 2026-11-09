package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.ai.ExpenseAiParser
import com.example.data.ai.ParsedTransactionResult
import com.example.data.chatbot.parser.FinancialQueryEngine
import com.example.data.chatbot.parser.LocalFinancialIntentParser
import com.example.data.chatbot.parser.WalletDraftManager
import com.example.data.model.AccountMode
import com.example.data.model.CategoryItem
import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPlanItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.ui.components.FinSmartIcons
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.IncomeGreenDark
import com.example.ui.theme.TransferBlue
import com.example.util.AppStrings
import com.example.util.CurrencyFormatter
import com.example.util.LocaleHelper
import com.example.util.VoiceRecognitionManager
import com.example.util.VoiceState
import kotlinx.coroutines.launch
import java.util.UUID

enum class ChatSender {
    USER,
    AI
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: ChatSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val parsedTransaction: ParsedTransactionResult? = null,
    val isSaved: Boolean = false,
    val isLoading: Boolean = false,
    val showAnalyticsDetailButton: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    wallets: List<Wallet>,
    categories: List<CategoryItem>,
    allTransactions: List<TransactionItem>,
    budgetLimits: List<com.example.data.model.BudgetLimitItem>,
    accountMode: AccountMode,
    isUnlocked: Boolean,
    onTriggerPaywall: () -> Unit,
    onCheckEntitlement: () -> Boolean = { isUnlocked },
    debts: List<DebtItem> = emptyList(),
    installmentPlans: List<InstallmentPlanItem> = emptyList(),
    onConfirmWallet: ((String, (Wallet) -> Unit, (String) -> Unit) -> Unit)? = null,
    onConfirmTransactionDraft: ((String, (Long) -> Unit, (String) -> Unit) -> Unit)? = null,
    onSaveTransaction: (
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
        tags: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var chatMode by remember { mutableStateOf(0) } // 0 = Catat Transaksi, 1 = Konsultan AI
    var isCommandCatalogOpen by remember { mutableStateOf(false) }
    var isAnalyticsDetailOpen by remember { mutableStateOf(false) }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                sender = ChatSender.AI,
                text = AppStrings.chatGreeting
            )
        )
    }

    val voiceManager = remember { VoiceRecognitionManager(context) }
    val voiceState by voiceManager.voiceState.collectAsState()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            voiceManager.startListening()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceManager.stopListening()
        }
    }

    // Voice recognition result handler
    LaunchedEffect(voiceState) {
        when (val state = voiceState) {
            is VoiceState.Success -> {
                inputText = state.text
                voiceManager.reset()
                if (inputText.isNotBlank()) {
                    if (!onCheckEntitlement() || !isUnlocked) {
                        onTriggerPaywall()
                    } else {
                        val promptToSend = inputText
                        inputText = ""
                        sendMessage(
                            prompt = promptToSend,
                            chatMode = chatMode,
                            isUnlocked = isUnlocked,
                            onCheckEntitlement = onCheckEntitlement,
                            onTriggerPaywall = onTriggerPaywall,
                            messages = messages,
                        wallets = wallets,
                        categories = categories,
                        allTransactions = allTransactions,
                        budgetLimits = budgetLimits,
                        debts = debts,
                        installmentPlans = installmentPlans,
                        onOpenCatalog = { if (isUnlocked) isCommandCatalogOpen = true else onTriggerPaywall() },
                        onProcessingChange = { isProcessing = it },
                        coroutineScope = coroutineScope,
                        onCompleteScroll = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(messages.size)
                            }
                        }
                    )
                    }
                }
            }
            else -> {}
        }
    }

    val isEn = LocaleHelper.isEnglish()

    val promptSuggestions = listOf(
        "⚡ " + AppStrings.commandCatalogTitle,
        if (isEn) "📈 How is my financial health this month?" else "📈 Bagaimana kondisi keuanganku bulan ini?",
        if (isEn) "⏰ Which bills are due this week?" else "⏰ Tagihan apa yang jatuh tempo minggu ini?",
        if (isEn) "🏆 What is my largest expense?" else "🏆 Pengeluaran terbesarku apa?",
        if (isEn) "➕ Create wallet Dana opening balance 100k" else "➕ Buat dompet Dana saldo awal 100rb"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("ai_chat_screen")
    ) {
        // Chat Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    listOf(FinSmartPrimary, Color(0xFF00897B))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "DompetNalar AI",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = AppStrings.chatTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = if (isEn) "Unified Financial Assistant • 100% On-Device" else "Asisten Keuangan Terpadu • 100% Lokal",
                            style = MaterialTheme.typography.bodySmall,
                            color = IncomeGreen
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (isUnlocked) {
                            isCommandCatalogOpen = true
                        } else {
                            onTriggerPaywall()
                        }
                    }) {
                        Icon(
                            imageVector = if (isUnlocked) Icons.Default.FlashOn else Icons.Default.Lock,
                            contentDescription = AppStrings.commandCatalogTitle,
                            tint = FinSmartPrimary
                        )
                    }
                    IconButton(
                        onClick = {
                            messages.clear()
                            messages.add(
                                ChatMessage(
                                    sender = ChatSender.AI,
                                    text = AppStrings.chatGreeting
                                )
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = AppStrings.t("Bersihkan Chat", "Clear Chat"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Two-Mode Selection Tabs (Preserved for compatibility & quick focus)
        TabRow(
            selectedTabIndex = chatMode,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = chatMode == 0,
                onClick = { chatMode = 0 },
                text = { Text(if (isEn) "All-in-One Assistant" else "Asisten Terpadu", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                modifier = Modifier.testTag("tab_chat_catat")
            )
            Tab(
                selected = chatMode == 1,
                onClick = {
                    chatMode = 1
                    isAnalyticsDetailOpen = true
                },
                text = { Text(if (isEn) "Financial Advisor" else "Konsultan Analitik", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                modifier = Modifier.testTag("tab_chat_advisor")
            )
        }

        // Paywall Banner if Free User Trial Expired
        if (!isUnlocked) {
            Surface(
                color = Color(0xFFFEF3C7),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTriggerPaywall() }
                    .testTag("chat_paywall_warning_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEn) "Upgrade to Smart Master for unlimited AI logging & advisor" else "Tingkatkan ke Smart Master untuk akses AI tanpa batas",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF92400E)
                        )
                    }
                    Text(
                        text = if (isEn) "Upgrade >" else "Buka >",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97706)
                    )
                }
            }
        }

        // Chat Message List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                ChatBubble(
                    message = message,
                    wallets = wallets,
                    onOpenAnalyticsDetail = { isAnalyticsDetailOpen = true },
                    onConfirmTransaction = { parsed ->
                        if (!onCheckEntitlement() || !isUnlocked) {
                            onTriggerPaywall()
                            return@ChatBubble
                        }
                        if (parsed.draftId != null && onConfirmTransactionDraft != null) {
                            onConfirmTransactionDraft(
                                parsed.draftId,
                                { txId ->
                                    val idx = messages.indexOfFirst { it.id == message.id }
                                    if (idx != -1) {
                                        messages[idx] = messages[idx].copy(isSaved = true)
                                    }
                                    Toast.makeText(context, if (isEn) "Transaction saved!" else "Transaksi berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                },
                                { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        } else {
                            val targetWalletId = wallets.firstOrNull { it.name.equals(parsed.suggestedTargetWalletName, ignoreCase = true) }?.id
                            val sourceWalletId = wallets.firstOrNull { it.name.equals(parsed.suggestedWalletName, ignoreCase = true) }?.id
                                ?: wallets.firstOrNull { it.isDefault }?.id
                                ?: wallets.firstOrNull()?.id
                                ?: 1L

                            val txType = when (parsed.type) {
                                "INCOME" -> TransactionType.INCOME
                                "TRANSFER" -> TransactionType.TRANSFER
                                else -> TransactionType.EXPENSE
                            }

                            onSaveTransaction(
                                parsed.title,
                                parsed.amount,
                                txType,
                                parsed.category,
                                parsed.categoryIcon,
                                sourceWalletId,
                                parsed.suggestedWalletName,
                                targetWalletId,
                                parsed.suggestedTargetWalletName,
                                parsed.timestamp,
                                parsed.note,
                                parsed.tags,
                                {
                                    val idx = messages.indexOfFirst { it.id == message.id }
                                    if (idx != -1) {
                                        messages[idx] = messages[idx].copy(isSaved = true)
                                    }
                                    Toast.makeText(context, if (isEn) "Transaction saved!" else "Transaksi berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                },
                                { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    },
                    onConfirmWalletDraft = { draftId ->
                        if (!onCheckEntitlement() || !isUnlocked) {
                            onTriggerPaywall()
                            return@ChatBubble
                        }
                        if (onConfirmWallet != null) {
                            onConfirmWallet(
                                draftId,
                                { createdWallet ->
                                    val idx = messages.indexOfFirst { it.id == message.id }
                                    if (idx != -1) {
                                        messages[idx] = messages[idx].copy(isSaved = true)
                                    }
                                    Toast.makeText(context, "${AppStrings.walletCreatedSuccess} (${createdWallet.name})", Toast.LENGTH_SHORT).show()
                                },
                                { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        } else {
                            Toast.makeText(context, "Wallet creation handler is not configured.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        // Pinned Suggestions Chip Bar
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(promptSuggestions) { prompt ->
                FilterChip(
                    selected = false,
                    onClick = {
                        if (!onCheckEntitlement() || !isUnlocked) {
                            onTriggerPaywall()
                            return@FilterChip
                        }
                        if (prompt.startsWith("⚡")) {
                            isCommandCatalogOpen = true
                        } else {
                            val cleanPrompt = prompt.replace(Regex("^[\\p{So}\\p{Sk}\\s]+"), "").trim()
                            sendMessage(
                                prompt = cleanPrompt,
                                chatMode = chatMode,
                                isUnlocked = isUnlocked,
                                onCheckEntitlement = onCheckEntitlement,
                                onTriggerPaywall = onTriggerPaywall,
                                messages = messages,
                                wallets = wallets,
                                categories = categories,
                                allTransactions = allTransactions,
                                budgetLimits = budgetLimits,
                                debts = debts,
                                installmentPlans = installmentPlans,
                                onOpenCatalog = { if (isUnlocked) isCommandCatalogOpen = true else onTriggerPaywall() },
                                onProcessingChange = { isProcessing = it },
                                coroutineScope = coroutineScope,
                                onCompleteScroll = {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(messages.size)
                                    }
                                }
                            )
                        }
                    },
                    label = { Text(prompt, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
                )
            }
        }

        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        // Input Bottom Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Voice Mic Button
                IconButton(
                    onClick = {
                        if (!isUnlocked) {
                            onTriggerPaywall()
                            return@IconButton
                        }
                        if (voiceState is VoiceState.Listening) {
                            voiceManager.stopListening()
                        } else {
                            if (hasAudioPermission) {
                                voiceManager.startListening()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            if (voiceState is VoiceState.Listening) ExpenseRed else FinSmartPrimary.copy(alpha = 0.12f),
                            CircleShape
                        )
                        .testTag("btn_voice_input")
                ) {
                    Icon(
                        imageVector = if (voiceState is VoiceState.Listening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice Input Logging",
                        tint = if (voiceState is VoiceState.Listening) Color.White else FinSmartPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Text Input Field
                val isAiInputActive = isUnlocked && onCheckEntitlement()
                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        if (!isAiInputActive) {
                            onTriggerPaywall()
                        } else {
                            inputText = it
                        }
                    },
                    readOnly = !isAiInputActive,
                    placeholder = {
                        Text(
                            if (!isAiInputActive) {
                                if (isEn) "🔒 Smart Master required for AI Input (Tap to Unlock)"
                                else "🔒 Perlu Smart Master untuk Input AI (Buka Akses)"
                            } else {
                                if (isEn) "Type command (e.g. Lunch 35k GoPay, Create wallet Dana)..."
                                else "Ketik transaksi / buat dompet / pertanyaan..."
                            },
                            fontSize = 13.sp
                        )
                    },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FinSmartPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (!isAiInputActive) {
                                onTriggerPaywall()
                            }
                        }
                        .testTag("input_chat_ai_text")
                )

                // Send Button
                IconButton(
                    onClick = {
                        if (!onCheckEntitlement() || !isUnlocked) {
                            onTriggerPaywall()
                            return@IconButton
                        }
                        if (inputText.isNotBlank() && !isProcessing) {
                            val promptToSend = inputText.trim()
                            inputText = ""
                            sendMessage(
                                prompt = promptToSend,
                                chatMode = chatMode,
                                isUnlocked = isUnlocked,
                                onCheckEntitlement = onCheckEntitlement,
                                onTriggerPaywall = onTriggerPaywall,
                                messages = messages,
                                wallets = wallets,
                                categories = categories,
                                allTransactions = allTransactions,
                                budgetLimits = budgetLimits,
                                debts = debts,
                                installmentPlans = installmentPlans,
                                onOpenCatalog = { if (isUnlocked) isCommandCatalogOpen = true else onTriggerPaywall() },
                                onProcessingChange = { isProcessing = it },
                                coroutineScope = coroutineScope,
                                onCompleteScroll = {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(messages.size)
                                    }
                                }
                            )
                        }
                    },
                    enabled = inputText.isNotBlank() && !isProcessing && isUnlocked && onCheckEntitlement(),
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            if (inputText.isNotBlank()) FinSmartPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                        .testTag("btn_send_chat_ai")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Kirim",
                        tint = if (inputText.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    // Modal BottomSheet: Command Catalog
    if (isCommandCatalogOpen) {
        ModalBottomSheet(
            onDismissRequest = { isCommandCatalogOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            CommandCatalogSheetContent(
                onSelectCommand = { cmd ->
                    isCommandCatalogOpen = false
                    if (cmd.contains("[") || cmd.contains("<")) {
                        // Needs user variable, insert into input field
                        inputText = cmd
                    } else {
                        if (!onCheckEntitlement() || !isUnlocked) {
                            onTriggerPaywall()
                        } else {
                            // Direct runnable query
                            sendMessage(
                                prompt = cmd,
                                chatMode = chatMode,
                                isUnlocked = isUnlocked,
                                onCheckEntitlement = onCheckEntitlement,
                                onTriggerPaywall = onTriggerPaywall,
                                messages = messages,
                            wallets = wallets,
                            categories = categories,
                            allTransactions = allTransactions,
                            budgetLimits = budgetLimits,
                            debts = debts,
                            installmentPlans = installmentPlans,
                            onOpenCatalog = {},
                            onProcessingChange = { isProcessing = it },
                            coroutineScope = coroutineScope,
                            onCompleteScroll = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(messages.size)
                                }
                            }
                        )
                        }
                    }
                },
                onClose = { isCommandCatalogOpen = false }
            )
        }
    }

    // Modal BottomSheet: Detailed Analytics
    if (isAnalyticsDetailOpen) {
        ModalBottomSheet(
            onDismissRequest = { isAnalyticsDetailOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            FinancialAnalyticsDetailSheetContent(
                wallets = wallets,
                transactions = allTransactions,
                budgetLimits = budgetLimits,
                debts = debts,
                installmentPlans = installmentPlans,
                onClose = { isAnalyticsDetailOpen = false }
            )
        }
    }
}

private fun sendMessage(
    prompt: String,
    chatMode: Int,
    isUnlocked: Boolean,
    onCheckEntitlement: () -> Boolean,
    onTriggerPaywall: () -> Unit,
    messages: MutableList<ChatMessage>,
    wallets: List<Wallet>,
    categories: List<CategoryItem>,
    allTransactions: List<TransactionItem>,
    budgetLimits: List<com.example.data.model.BudgetLimitItem>,
    debts: List<DebtItem>,
    installmentPlans: List<InstallmentPlanItem>,
    onOpenCatalog: () -> Unit,
    onProcessingChange: (Boolean) -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onCompleteScroll: () -> Unit
) {
    if (!onCheckEntitlement() || !isUnlocked) {
        onTriggerPaywall()
        return
    }

    // 1. Add user message
    messages.add(
        ChatMessage(
            sender = ChatSender.USER,
            text = prompt
        )
    )
    onCompleteScroll()
    onProcessingChange(true)

    // 2. Process via Unified Local NLP Router
    coroutineScope.launch {
        try {
            val command = LocalFinancialIntentParser.parse(
                userMessage = prompt,
                availableWallets = wallets,
                availableCategories = categories
            )

            when (command.intentType) {
                LocalFinancialIntentParser.IntentType.SHOW_COMMAND_CATALOG -> {
                    onOpenCatalog()
                    messages.add(
                        ChatMessage(
                            sender = ChatSender.AI,
                            text = command.responseMessage
                        )
                    )
                }

                LocalFinancialIntentParser.IntentType.CREATE_WALLET,
                LocalFinancialIntentParser.IntentType.CREATE_WALLET_WITH_OPENING_BALANCE,
                LocalFinancialIntentParser.IntentType.CREATE_WALLET_WITH_INCOME -> {
                    val parsedResult = ExpenseAiParser.parseExpenseMessage(prompt, wallets, categories)
                    messages.add(
                        ChatMessage(
                            sender = ChatSender.AI,
                            text = parsedResult.aiExplanation,
                            parsedTransaction = if (parsedResult.isValid) parsedResult else null
                        )
                    )
                }

                LocalFinancialIntentParser.IntentType.CREATE_EXPENSE,
                LocalFinancialIntentParser.IntentType.CREATE_INCOME,
                LocalFinancialIntentParser.IntentType.CREATE_TRANSFER -> {
                    val parsedResult = ExpenseAiParser.parseExpenseMessage(prompt, wallets, categories)
                    val actionCard = if (parsedResult.isValid && parsedResult.amount > 0.0) parsedResult else null

                    messages.add(
                        ChatMessage(
                            sender = ChatSender.AI,
                            text = parsedResult.aiExplanation.ifBlank {
                                AppStrings.t("Format transaksi dikenali. Konfirmasi draft di bawah untuk menyimpan.", "Transaction format recognized. Confirm draft below to save.")
                            },
                            parsedTransaction = actionCard
                        )
                    )
                }

                LocalFinancialIntentParser.IntentType.QUERY_FINANCE,
                LocalFinancialIntentParser.IntentType.QUERY_BILLS_AND_DEBTS -> {
                    val topic = command.queryTopic ?: LocalFinancialIntentParser.QueryTopic.FINANCIAL_HEALTH_SUMMARY
                    val responseText = FinancialQueryEngine.executeQuery(
                        topic = topic,
                        queriedWallet = command.sourceWallet,
                        wallets = wallets,
                        transactions = allTransactions,
                        budgetLimits = budgetLimits,
                        debts = debts,
                        installmentPlans = installmentPlans
                    )

                    messages.add(
                        ChatMessage(
                            sender = ChatSender.AI,
                            text = responseText,
                            showAnalyticsDetailButton = (topic == LocalFinancialIntentParser.QueryTopic.FINANCIAL_HEALTH_SUMMARY)
                        )
                    )
                }

                else -> {
                    messages.add(
                        ChatMessage(
                            sender = ChatSender.AI,
                            text = command.responseMessage.ifBlank {
                                AppStrings.t("Perintah belum dikenali. Ketik 'katalog' untuk melihat daftar perintah finansial.", "Command not recognized. Type 'catalog' to view financial command list.")
                            }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            messages.add(
                ChatMessage(
                    sender = ChatSender.AI,
                    text = "${AppStrings.t("Mohon maaf, terjadi kendala saat memproses:", "Sorry, an issue occurred while processing:")} ${e.localizedMessage ?: AppStrings.t("Coba ulangi format kalimatnya.", "Please try rephrasing the sentence.")}"
                )
            )
        } finally {
            onProcessingChange(false)
            onCompleteScroll()
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    wallets: List<Wallet>,
    onOpenAnalyticsDetail: () -> Unit,
    onConfirmTransaction: (ParsedTransactionResult) -> Unit,
    onConfirmWalletDraft: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.sender == ChatSender.USER
    val isEn = LocaleHelper.isEnglish()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(FinSmartPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Text Message Bubble
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) FinSmartPrimary else MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = message.text,
                        color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    // Button for Detailed Analytics
                    if (message.showAnalyticsDetailButton) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onOpenAnalyticsDetail,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = FinSmartPrimary)
                        ) {
                            Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(AppStrings.viewFullAnalytics, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = CurrencyFormatter.formatTime(message.timestamp),
                        fontSize = 10.sp,
                        color = if (isUser) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }

            // Interactive Action Cards (Transactions or Wallet Creation)
            message.parsedTransaction?.let { parsed ->
                if (parsed.isWalletCreation) {
                    Spacer(modifier = Modifier.height(8.dp))
                    WalletDraftCard(
                        parsed = parsed,
                        isSaved = message.isSaved,
                        onConfirm = { parsed.walletDraftId?.let { onConfirmWalletDraft(it) } }
                    )
                } else if (parsed.isValid && parsed.amount > 0.0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TransactionActionCard(
                        parsed = parsed,
                        isSaved = message.isSaved,
                        messageId = message.id,
                        onConfirm = { onConfirmTransaction(parsed) }
                    )
                }
            }
        }
    }
}

@Composable
fun WalletDraftCard(
    parsed: ParsedTransactionResult,
    isSaved: Boolean,
    onConfirm: () -> Unit
) {
    val isEn = LocaleHelper.isEnglish()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFF0B57D0).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isEn) "NEW WALLET" else "DOMPET BARU",
                        color = Color(0xFF0B57D0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val iconVector = when (parsed.walletType) {
                        WalletType.CASH -> Icons.Default.Payments
                        WalletType.EWALLET -> Icons.Default.AccountBalanceWallet
                        else -> Icons.Default.AccountBalance
                    }
                    Icon(imageVector = iconVector, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = parsed.walletType?.name ?: "BANK", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = parsed.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)

            Spacer(modifier = Modifier.height(4.dp))
            val balanceLabel = if (parsed.incomeAmount != null && parsed.incomeAmount > 0.0) {
                "Pemasukan Baru: ${CurrencyFormatter.formatCurrency(parsed.incomeAmount)}"
            } else if (parsed.openingBalance > 0.0) {
                "Saldo Awal Murni: ${CurrencyFormatter.formatCurrency(parsed.openingBalance)}"
            } else {
                "Saldo Awal: ${CurrencyFormatter.formatCurrency(0.0)}"
            }

            Text(
                text = balanceLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = IncomeGreenDark
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (isSaved) {
                Surface(
                    color = IncomeGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = IncomeGreenDark, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = AppStrings.walletCreatedSuccess,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = IncomeGreenDark
                        )
                    }
                }
            } else {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(AppStrings.confirmCreateWallet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TransactionActionCard(
    parsed: ParsedTransactionResult,
    isSaved: Boolean,
    messageId: String,
    onConfirm: () -> Unit
) {
    val badgeColor = when (parsed.type) {
        "INCOME" -> IncomeGreen
        "TRANSFER" -> TransferBlue
        else -> ExpenseRed
    }
    val typeLabel = when (parsed.type) {
        "INCOME" -> AppStrings.income
        "TRANSFER" -> AppStrings.transfer
        else -> AppStrings.expense
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth().testTag("ai_transaction_card_$messageId")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = typeLabel,
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = FinSmartIcons.getCategoryIcon(parsed.categoryIcon),
                        contentDescription = parsed.category,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = parsed.category, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = parsed.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = CurrencyFormatter.formatCurrency(parsed.amount),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = badgeColor
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${AppStrings.wallet}: ${parsed.suggestedWalletName}" +
                            if (parsed.suggestedTargetWalletName != null) " → ${parsed.suggestedTargetWalletName}" else "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (parsed.tags.isNotBlank()) {
                    Text(text = parsed.tags, fontSize = 11.sp, color = FinSmartPrimary, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isSaved) {
                Surface(
                    color = IncomeGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = IncomeGreenDark, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "✓ " + (if (LocaleHelper.isEnglish()) "Transaction Saved" else "Transaksi Berhasil Disimpan"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = IncomeGreenDark
                        )
                    }
                }
            } else {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().height(42.dp).testTag("btn_confirm_ai_tx_$messageId"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = badgeColor)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(AppStrings.confirmSaveTransaction, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun CommandCatalogSheetContent(
    onSelectCommand: (String) -> Unit,
    onClose: () -> Unit
) {
    val isEn = LocaleHelper.isEnglish()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(AppStrings.commandCatalogTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(AppStrings.commandCatalogSubtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = AppStrings.t("Tutup", "Close"))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.height(420.dp)) {
            // Group A: Catat Cepat
            item {
                CommandGroupSection(
                    title = "⚡ " + AppStrings.catQuickLog,
                    commands = listOf(
                        if (isEn) "Lunch 35,000 Cash" else "Makan siang 35.000 BCA",
                        if (isEn) "Salary 5,000,000 into Bank" else "Gaji 5 juta masuk ke Mandiri",
                        if (isEn) "Transfer 200,000 from Bank to Wallet" else "Pindahkan 200 ribu dari BCA ke DANA"
                    ),
                    onSelect = onSelectCommand
                )
            }

            // Group B: Kelola Dompet
            item {
                CommandGroupSection(
                    title = "💼 " + AppStrings.catWalletManage,
                    commands = listOf(
                        if (isEn) "Create wallet Dana" else "Buat dompet Dana",
                        if (isEn) "Create wallet Cash opening balance 500,000" else "Buat dompet Dompet Usaha saldo awal 500 ribu",
                        if (isEn) "Create wallet Jago with salary income 5,000,000" else "Buat dompet Jago dan catat pemasukan 5 juta",
                        if (isEn) "What is the balance of Dana wallet?" else "Berapa saldo dompet Dana?"
                    ),
                    onSelect = onSelectCommand
                )
            }

            // Group C: Analisis Keuangan
            item {
                CommandGroupSection(
                    title = "📊 " + AppStrings.catFinancialAnalysis,
                    commands = listOf(
                        if (isEn) "How is my financial health this month?" else "Bagaimana kondisi keuanganku bulan ini?",
                        if (isEn) "What is my largest expense?" else "Apa pengeluaran terbesarku?",
                        if (isEn) "Top 5 expenses this month" else "5 pengeluaran terbesar bulan ini",
                        if (isEn) "Expense breakdown by category" else "Rincian pengeluaran per kategori",
                        if (isEn) "Is my budget safe?" else "Apakah anggaranku masih aman?",
                        if (isEn) "What is my daily safe spending limit?" else "Berapa batas pengeluaran harian yang aman?",
                        if (isEn) "What is my savings ratio?" else "Berapa rasio tabunganku?",
                        if (isEn) "What is my total net worth?" else "Berapa total kekayaanku?"
                    ),
                    onSelect = onSelectCommand
                )
            }

            // Group D: Tagihan dan Cicilan
            item {
                CommandGroupSection(
                    title = "⏰ " + AppStrings.catBillsAndDebts,
                    commands = listOf(
                        if (isEn) "Which bills are due this week?" else "Tagihan apa yang jatuh tempo minggu ini?",
                        if (isEn) "When is my next upcoming bill?" else "Kapan tagihan terdekat?",
                        if (isEn) "How many installments left?" else "Berapa sisa cicilan aktif?",
                        if (isEn) "Total debt to income analysis" else "Berapa total utang dan cicilan saya?"
                    ),
                    onSelect = onSelectCommand
                )
            }
        }
    }
}

@Composable
fun CommandGroupSection(
    title: String,
    commands: List<String>,
    onSelect: (String) -> Unit
) {
    Column {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = FinSmartPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        commands.forEach { cmd ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable { onSelect(cmd) }
            ) {
                Text(
                    text = cmd,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun FinancialAnalyticsDetailSheetContent(
    wallets: List<Wallet>,
    transactions: List<TransactionItem>,
    budgetLimits: List<com.example.data.model.BudgetLimitItem>,
    debts: List<DebtItem>,
    installmentPlans: List<InstallmentPlanItem>,
    onClose: () -> Unit
) {
    val isEn = LocaleHelper.isEnglish()
    val totalAssets = wallets.sumOf { it.balance }
    val totalDebt = debts.sumOf { it.amount } + installmentPlans.sumOf { it.totalRemainingPrincipal ?: 0.0 }
    val netWorth = totalAssets - totalDebt

    val currentMonthExpenses = transactions.filter { it.type == TransactionType.EXPENSE }
    val totalExpense = currentMonthExpenses.sumOf { it.amount }
    val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val monthlyBudget = budgetLimits.firstOrNull { it.id == "total_monthly" }?.limitAmount ?: 0.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(AppStrings.financialHealthAnalytics, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = AppStrings.t("Tutup", "Close"))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Net Worth Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = FinSmartPrimary.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(AppStrings.netWorth, fontSize = 12.sp, color = FinSmartPrimary, fontWeight = FontWeight.SemiBold)
                Text(CurrencyFormatter.formatCurrency(netWorth), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = FinSmartPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${if (isEn) "Assets" else "Aset"}: ${CurrencyFormatter.formatCurrency(totalAssets)}", fontSize = 11.sp)
                    Text("${if (isEn) "Debts" else "Utang"}: ${CurrencyFormatter.formatCurrency(totalDebt)}", fontSize = 11.sp, color = ExpenseRed)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Monthly Income vs Expense
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = IncomeGreen.copy(alpha = 0.1f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(AppStrings.income, fontSize = 11.sp, color = IncomeGreenDark, fontWeight = FontWeight.Bold)
                    Text(CurrencyFormatter.formatCurrency(totalIncome), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = IncomeGreenDark)
                }
            }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ExpenseRed.copy(alpha = 0.1f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(AppStrings.expense, fontSize = 11.sp, color = ExpenseRed, fontWeight = FontWeight.Bold)
                    Text(CurrencyFormatter.formatCurrency(totalExpense), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ExpenseRed)
                }
            }
        }

        // Budget Progress Bar
        if (monthlyBudget > 0.0) {
            Spacer(modifier = Modifier.height(14.dp))
            val progress = (totalExpense / monthlyBudget).toFloat().coerceIn(0f, 1f)
            Text("${AppStrings.budgetSpent}: ${CurrencyFormatter.formatCurrency(totalExpense)} / ${CurrencyFormatter.formatCurrency(monthlyBudget)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = if (totalExpense > monthlyBudget) ExpenseRed else FinSmartPrimary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(AppStrings.close)
        }
    }
}
