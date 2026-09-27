package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
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
import com.example.data.model.AccountMode
import com.example.data.model.CategoryItem
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import com.example.ui.components.FinSmartIcons
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.IncomeGreenDark
import com.example.ui.theme.TransferBlue
import com.example.util.CurrencyFormatter
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
    val isLoading: Boolean = false
)

@Composable
fun AiChatScreen(
    wallets: List<Wallet>,
    categories: List<CategoryItem>,
    allTransactions: List<com.example.data.model.TransactionItem>,
    budgetLimits: List<com.example.data.model.BudgetLimitItem>,
    accountMode: AccountMode,
    isUnlocked: Boolean,
    onTriggerPaywall: () -> Unit,
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
        tags: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var chatMode by remember { mutableStateOf(0) } // 0 = Catat Transaksi, 1 = Konsultan AI

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                sender = ChatSender.AI,
                text = "Halo! Saya FinSmart AI Assistant.\nKetik atau tekan mikrofon untuk mencatat transaksi secara otomatis.\n\nContoh:\n• 'Makan siang 35 ribu pakai GoPay'\n• 'Beli bensin 50rb tunai #transportasi'\n• 'Gaji bulanan 6.500.000 BCA'\n• 'Transfer 200rb dari BCA ke DANA'"
            )
        )
    }

    val voiceManager = remember { VoiceRecognitionManager(context) }
    val voiceState by voiceManager.voiceState.collectAsState()
    val rmsDb by voiceManager.rmsDb.collectAsState()

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

    // Handle voice recognition result
    LaunchedEffect(voiceState) {
        when (val state = voiceState) {
            is VoiceState.Success -> {
                inputText = state.text
                voiceManager.reset()
                // Auto send transcribed voice text
                if (inputText.isNotBlank()) {
                    val promptToSend = inputText
                    inputText = ""
                    sendMessage(
                        prompt = promptToSend,
                        chatMode = chatMode,
                        messages = messages,
                        wallets = wallets,
                        categories = categories,
                        allTransactions = allTransactions,
                        budgetLimits = budgetLimits,
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
            else -> {}
        }
    }

    val promptSuggestions = if (chatMode == 0) {
        listOf(
            "🍜 Makan siang 35rb GoPay",
            "⛽ Beli bensin 50rb Tunai",
            "🛒 Belanja Indomaret 85rb BCA",
            "💼 Gaji Pokok 7.500.000 BCA",
            "↔️ Transfer 150rb BCA ke DANA"
        )
    } else {
        listOf(
            "📈 Bagaimana kondisi keuanganku bulan ini?",
            "🍔 Berikan tips hemat makan luar",
            "📊 Analisis kategori pengeluaran terbanyakku",
            "🛡️ Apakah anggaran bulan ini aman?"
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("ai_chat_screen")
    ) {
        // Chat Header (WhatsApp style)
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
                            contentDescription = "FinSmart AI",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (chatMode == 0) "FinSmart AI Chat" else "Konsultan AI FinSmart",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = FinSmartPrimary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Gemini 3.5 Flash",
                                    color = FinSmartPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (chatMode == 0) "Mode ${accountMode.displayName} • Siap catat otomatis" else "FinSmart AI Health Advisor",
                            style = MaterialTheme.typography.bodySmall,
                            color = IncomeGreen
                        )
                    }
                }

                IconButton(
                    onClick = {
                        messages.clear()
                        if (chatMode == 0) {
                            messages.add(
                                ChatMessage(
                                    sender = ChatSender.AI,
                                    text = "Riwayat chat telah dibersihkan. Silakan ketik transaksi baru Anda!"
                                )
                            )
                        } else {
                            messages.add(
                                ChatMessage(
                                    sender = ChatSender.AI,
                                    text = "Riwayat konsultasi dibersihkan. Silakan ajukan pertanyaan keuangan Anda!"
                                )
                            )
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = "Bersihkan Chat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Two-Mode Selection Tabs
        TabRow(
            selectedTabIndex = chatMode,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = chatMode == 0,
                onClick = {
                    chatMode = 0
                    messages.clear()
                    messages.add(
                        ChatMessage(
                            sender = ChatSender.AI,
                            text = "Halo! Saya FinSmart AI Assistant.\nKetik atau tekan mikrofon untuk mencatat transaksi secara otomatis.\n\nContoh:\n• 'Makan siang 35 ribu pakai GoPay'\n• 'Beli bensin 50rb tunai #transportasi'\n• 'Gaji bulanan 6.500.000 BCA'\n• 'Transfer 200rb dari BCA ke DANA'"
                        )
                    )
                },
                text = { Text("Catat Transaksi", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                modifier = Modifier.testTag("tab_chat_catat")
            )
            Tab(
                selected = chatMode == 1,
                onClick = {
                    chatMode = 1
                    messages.clear()
                    messages.add(
                        ChatMessage(
                            sender = ChatSender.AI,
                            text = "Halo! Saya Konsultan Finansial AI Personal Anda 🧠.\n\nSaya dapat menganalisis data pengeluaran bulanan Anda, melacak batas anggaran (budget limits), menghitung rasio tabungan, dan memberikan saran penghematan konkret yang dipersonalisasi.\n\nSilakan pilih salah satu pertanyaan cepat di bawah atau ajukan pertanyaan Anda sendiri!"
                        )
                    )
                },
                text = { Text("Konsultan AI", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
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
                            text = "Masa trial 3 hari berakhir. Upgrade ke Smart Master 👑 untuk akses AI tanpa batas.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF92400E)
                        )
                    }
                    Text(
                        text = "Upgrade →",
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
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 14.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubble(
                    message = msg,
                    wallets = wallets,
                    onConfirmTransaction = { parsed ->
                        val matchedSource = wallets.firstOrNull {
                            it.name.contains(parsed.suggestedWalletName, ignoreCase = true) ||
                                    parsed.suggestedWalletName.contains(it.name, ignoreCase = true)
                        } ?: wallets.firstOrNull()

                        val matchedTarget = if (parsed.type == "TRANSFER") {
                            wallets.firstOrNull {
                                parsed.suggestedTargetWalletName != null && (
                                        it.name.contains(parsed.suggestedTargetWalletName, ignoreCase = true) ||
                                                parsed.suggestedTargetWalletName.contains(it.name, ignoreCase = true)
                                        )
                            }
                        } else null

                        val txType = when (parsed.type) {
                            "INCOME" -> TransactionType.INCOME
                            "TRANSFER" -> TransactionType.TRANSFER
                            else -> TransactionType.EXPENSE
                        }

                        if (matchedSource != null) {
                            onSaveTransaction(
                                parsed.title,
                                parsed.amount,
                                txType,
                                parsed.category,
                                parsed.categoryIcon,
                                matchedSource.id,
                                matchedSource.name,
                                matchedTarget?.id,
                                matchedTarget?.name,
                                parsed.timestamp,
                                parsed.note,
                                parsed.tags
                            )

                            // Mark message as saved
                            val idx = messages.indexOfFirst { it.id == msg.id }
                            if (idx != -1) {
                                messages[idx] = messages[idx].copy(isSaved = true)
                            }
                        }
                    }
                )
            }

            if (isProcessing) {
                item {
                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = FinSmartPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (chatMode == 0) "FinSmart AI sedang mengekstrak data transaksi..." else "FinSmart AI sedang menganalisis keuangan Anda...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Voice Listening Overlay Banner if mic active
        AnimatedVisibility(
            visible = voiceState is VoiceState.Listening,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val infiniteTransition = rememberInfiniteTransition()
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.35f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .scale(pulseScale)
                                .background(ExpenseRed, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Mendengarkan suara Anda...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Ucapkan nominal, kategori, & dompet transaksi",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Button(
                        onClick = { voiceManager.stopListening() },
                        colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Selesai", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Selesai", fontSize = 12.sp)
                    }
                }
            }
        }

        // Quick Suggestions Carousel
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(promptSuggestions) { prompt ->
                FilterChip(
                    selected = false,
                    onClick = {
                        if (!isUnlocked) {
                            onTriggerPaywall()
                            return@FilterChip
                        }
                        sendMessage(
                            prompt = prompt.substringAfter(" "),
                            chatMode = chatMode,
                            messages = messages,
                            wallets = wallets,
                            categories = categories,
                            allTransactions = allTransactions,
                            budgetLimits = budgetLimits,
                            onProcessingChange = { isProcessing = it },
                            coroutineScope = coroutineScope,
                            onCompleteScroll = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(messages.size)
                                }
                            }
                        )
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
                // Voice to Text Mic Button
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

                // Text Field
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ketik transaksi (misal: Beli kopi 25rb GoPay)...", fontSize = 13.sp) },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FinSmartPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_chat_ai_text")
                )

                // Send Button
                IconButton(
                    onClick = {
                        if (!isUnlocked) {
                            onTriggerPaywall()
                            return@IconButton
                        }
                        if (inputText.isNotBlank() && !isProcessing) {
                            val promptToSend = inputText.trim()
                            inputText = ""
                            sendMessage(
                                prompt = promptToSend,
                                chatMode = chatMode,
                                messages = messages,
                                wallets = wallets,
                                categories = categories,
                                allTransactions = allTransactions,
                                budgetLimits = budgetLimits,
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
                    enabled = inputText.isNotBlank() && !isProcessing,
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
}

private fun sendMessage(
    prompt: String,
    chatMode: Int,
    messages: MutableList<ChatMessage>,
    wallets: List<Wallet>,
    categories: List<CategoryItem>,
    allTransactions: List<com.example.data.model.TransactionItem>,
    budgetLimits: List<com.example.data.model.BudgetLimitItem>,
    onProcessingChange: (Boolean) -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onCompleteScroll: () -> Unit
) {
    // 1. Add user message
    messages.add(
        ChatMessage(
            sender = ChatSender.USER,
            text = prompt
        )
    )
    onCompleteScroll()
    onProcessingChange(true)

    // 2. Process in Coroutine
    coroutineScope.launch {
        try {
            if (chatMode == 0) {
                val parsedResult = ExpenseAiParser.parseExpenseMessage(
                    userMessage = prompt,
                    availableWallets = wallets,
                    availableCategories = categories
                )

                messages.add(
                    ChatMessage(
                        sender = ChatSender.AI,
                        text = parsedResult.aiExplanation,
                        parsedTransaction = parsedResult
                    )
                )
            } else {
                val responseText = com.example.data.ai.AdvisorAiService.getAdvisorResponse(
                    userMessage = prompt,
                    transactions = allTransactions,
                    budgetLimits = budgetLimits
                )

                messages.add(
                    ChatMessage(
                        sender = ChatSender.AI,
                        text = responseText
                    )
                )
            }
        } catch (e: Exception) {
            messages.add(
                ChatMessage(
                    sender = ChatSender.AI,
                    text = "Mohon maaf, terjadi kendala saat memproses: ${e.localizedMessage ?: "Coba ulangi format kalimatnya."}"
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
    onConfirmTransaction: (ParsedTransactionResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.sender == ChatSender.USER

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
            modifier = Modifier.widthIn(max = 310.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Message Bubble Box
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) {
                    FinSmartPrimary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = message.text,
                        color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = CurrencyFormatter.formatTime(message.timestamp),
                        fontSize = 10.sp,
                        color = if (isUser) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }

            // If AI extracted a valid transaction, show interactive action card
            message.parsedTransaction?.let { parsed ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier.fillMaxWidth().testTag("ai_transaction_card_${message.id}")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Type & Category Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val badgeColor = when (parsed.type) {
                                "INCOME" -> IncomeGreen
                                "TRANSFER" -> TransferBlue
                                else -> ExpenseRed
                            }
                            val typeLabel = when (parsed.type) {
                                "INCOME" -> "Pemasukan"
                                "TRANSFER" -> "Transfer Saldo"
                                else -> "Pengeluaran"
                            }

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
                                Text(
                                    text = parsed.category,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Title & Amount
                        Text(
                            text = parsed.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = CurrencyFormatter.formatRupiah(parsed.amount),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when (parsed.type) {
                                "INCOME" -> IncomeGreen
                                "TRANSFER" -> TransferBlue
                                else -> ExpenseRed
                            }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Wallet & Target Info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Dompet: ${parsed.suggestedWalletName}" +
                                        if (parsed.suggestedTargetWalletName != null) " → ${parsed.suggestedTargetWalletName}" else "",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (parsed.tags.isNotBlank()) {
                                Text(
                                    text = parsed.tags,
                                    fontSize = 11.sp,
                                    color = FinSmartPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Transaction Date & Backdate Indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = if (parsed.detectedDateText != null) FinSmartPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = CurrencyFormatter.formatDate(parsed.timestamp),
                                    fontSize = 11.sp,
                                    fontWeight = if (parsed.detectedDateText != null) FontWeight.Bold else FontWeight.Normal,
                                    color = if (parsed.detectedDateText != null) FinSmartPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (parsed.detectedDateText != null) {
                                Surface(
                                    color = FinSmartPrimary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "📅 ${parsed.detectedDateText}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FinSmartPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action / Saved Button
                        if (message.isSaved) {
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
                                        text = "✓ Transaksi Berhasil Disimpan",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = IncomeGreenDark
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = { onConfirmTransaction(parsed) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .testTag("btn_confirm_ai_tx_${message.id}"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = when (parsed.type) {
                                        "INCOME" -> IncomeGreen
                                        "TRANSFER" -> TransferBlue
                                        else -> ExpenseRed
                                    }
                                )
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Simpan ke Dompet",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
