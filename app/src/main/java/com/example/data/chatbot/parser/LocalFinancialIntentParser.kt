package com.example.data.chatbot.parser

import com.example.data.model.CategoryItem
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.util.AppStrings
import com.example.util.CurrencyFormatter
import com.example.util.LocaleHelper
import com.example.util.TransactionDateParser
import java.util.Locale
import java.util.regex.Pattern

/**
 * Mesin Intent Parser Finansial Lokal Terpadu (Unified Rule-Based NLP & Slot-Filling Engine).
 *
 * Mampu memetakan teks/suara bahasa Indonesia dan English ke dalam perintah terstruktur:
 * 1. Pencatatan Transaksi (Pengeluaran, Pemasukan, Transfer)
 * 2. Pembuatan Dompet Dinamis (CREATE_WALLET, CREATE_WALLET_WITH_OPENING_BALANCE, CREATE_WALLET_WITH_INCOME)
 * 3. Kueri & Analisis Finansial (Saldo, Arus Kas, Top Kategori, Anggaran, Tagihan, Cicilan, Utang, Net Worth)
 * 4. Pinned Commands & Financial Command Center
 * 100% deterministik, offline, tanpa bergantung pada LLM cloud atau API Key.
 */
object LocalFinancialIntentParser {

    enum class IntentType {
        CREATE_INCOME,
        CREATE_EXPENSE,
        CREATE_TRANSFER,
        CREATE_WALLET,
        CREATE_WALLET_WITH_OPENING_BALANCE,
        CREATE_WALLET_WITH_INCOME,
        QUERY_FINANCE,
        QUERY_BILLS_AND_DEBTS,
        SHOW_COMMAND_CATALOG,
        CANCEL_ACTION,
        CONVERSATION,
        UNKNOWN
    }

    enum class ParseStatus {
        READY_FOR_DRAFT,
        WALLET_DRAFT_READY,
        MISSING_AMOUNT,
        MISSING_WALLET,
        MISSING_WALLET_NAME,
        DUPLICATE_WALLET_NAME,
        AMBIGUOUS_WALLET,
        INVALID_TRANSFER_SAME_WALLET,
        INVALID_TRANSFER_MISSING_TARGET,
        QUERY_READY,
        CANCEL_REQUESTED,
        CONVERSATION_ONLY,
        UNCLEAR_INTENT
    }

    data class ParsedFinancialCommand(
        val intentType: IntentType,
        val status: ParseStatus,
        val transactionType: TransactionType?,
        val amount: Double?,
        val title: String,
        val categoryName: String,
        val categoryIcon: String,
        val sourceWallet: Wallet?,
        val targetWallet: Wallet?,
        val ambiguousWallets: List<Wallet> = emptyList(),
        val timestamp: Long,
        val detectedDateText: String?,
        val rawInput: String,
        val responseMessage: String,
        val queryTopic: QueryTopic? = null,
        val targetWalletNameStr: String? = null,
        val walletType: WalletType? = null,
        val initialBalance: Double? = null,
        val incomeAmount: Double? = null,
        val currencyCode: String? = null
    )

    enum class QueryTopic {
        TOTAL_BALANCE,
        SPECIFIC_WALLET_BALANCE,
        MONTHLY_EXPENSE,
        MONTHLY_INCOME,
        LARGEST_EXPENSE,
        TOP_FIVE_EXPENSES,
        CATEGORY_BREAKDOWN,
        RECENT_TRANSACTIONS,
        FINANCIAL_HEALTH_SUMMARY,
        BUDGET_PROGRESS,
        DAILY_SAFE_SPENDING,
        SAVINGS_RATIO,
        NET_WORTH,
        DEBT_TO_INCOME,
        OUTSTANDING_INSTALLMENT,
        UPCOMING_BILLS,
        DUE_DATE_ANALYSIS
    }

    /**
     * Mem-parsing pesan teks pengguna menjadi [ParsedFinancialCommand].
     */
    fun parse(
        userMessage: String,
        availableWallets: List<Wallet>,
        availableCategories: List<CategoryItem> = emptyList()
    ): ParsedFinancialCommand {
        val trimmed = userMessage.trim()
        val isEn = LocaleHelper.isEnglish()

        if (trimmed.isBlank()) {
            return ParsedFinancialCommand(
                intentType = IntentType.UNKNOWN,
                status = ParseStatus.UNCLEAR_INTENT,
                transactionType = null,
                amount = null,
                title = "",
                categoryName = "",
                categoryIcon = "",
                sourceWallet = null,
                targetWallet = null,
                timestamp = System.currentTimeMillis(),
                detectedDateText = null,
                rawInput = userMessage,
                responseMessage = if (isEn)
                    "Please type or speak your transaction, wallet command, or financial question."
                else
                    "Silakan ketik atau sebutkan transaksi, perintah buat dompet, maupun pertanyaan keuangan Anda."
            )
        }

        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. Deteksi Pembatalan ("batal", "cancel", "jangan disimpan")
        if (isCancelIntent(lower)) {
            return ParsedFinancialCommand(
                intentType = IntentType.CANCEL_ACTION,
                status = ParseStatus.CANCEL_REQUESTED,
                transactionType = null,
                amount = null,
                title = "",
                categoryName = "",
                categoryIcon = "",
                sourceWallet = null,
                targetWallet = null,
                timestamp = System.currentTimeMillis(),
                detectedDateText = null,
                rawInput = userMessage,
                responseMessage = if (isEn) "Action cancelled. How else may I assist you?" else "Perintah dibatalkan. Ada transaksi atau pertanyaan lain yang ingin dibantu?"
            )
        }

        // 2. Deteksi Permintaan Katalog Perintah (Command Catalog)
        if (isCommandCatalogIntent(lower)) {
            return ParsedFinancialCommand(
                intentType = IntentType.SHOW_COMMAND_CATALOG,
                status = ParseStatus.CONVERSATION_ONLY,
                transactionType = null,
                amount = null,
                title = "",
                categoryName = "",
                categoryIcon = "",
                sourceWallet = null,
                targetWallet = null,
                timestamp = System.currentTimeMillis(),
                detectedDateText = null,
                rawInput = userMessage,
                responseMessage = if (isEn)
                    "Opening Smart Command Catalog. You can tap any command to execute or auto-fill."
                else
                    "Membuka Katalog Perintah Pintar. Anda dapat mengetuk perintah apa saja untuk langsung dijalankan atau disalin."
            )
        }

        // 3. Deteksi Perintah Pembuatan Dompet (Wallet Management via Chat)
        val walletCommand = tryParseWalletCreation(trimmed, lower, availableWallets, isEn)
        if (walletCommand != null) {
            return walletCommand
        }

        // 4. Deteksi Pertanyaan Finansial, Tagihan, Utang, dan Analisis (Queries)
        val queryTopic = detectQueryTopic(lower)
        if (queryTopic != null) {
            val queriedWallet = if (queryTopic == QueryTopic.SPECIFIC_WALLET_BALANCE) {
                when (val res = WalletResolver.resolveWalletFromText(lower, availableWallets)) {
                    is WalletResolver.Result.ExactMatch -> res.wallet
                    else -> null
                }
            } else null

            val isBillOrDebt = queryTopic in listOf(
                QueryTopic.UPCOMING_BILLS,
                QueryTopic.DUE_DATE_ANALYSIS,
                QueryTopic.OUTSTANDING_INSTALLMENT,
                QueryTopic.DEBT_TO_INCOME
            )

            return ParsedFinancialCommand(
                intentType = if (isBillOrDebt) IntentType.QUERY_BILLS_AND_DEBTS else IntentType.QUERY_FINANCE,
                status = ParseStatus.QUERY_READY,
                transactionType = null,
                amount = null,
                title = "",
                categoryName = "",
                categoryIcon = "",
                sourceWallet = queriedWallet,
                targetWallet = null,
                timestamp = System.currentTimeMillis(),
                detectedDateText = null,
                rawInput = userMessage,
                responseMessage = if (isEn) "Financial query detected. Processing via Financial Query Engine / Mode Konsultasi." else "Kueri finansial terdeteksi. Diproses via Mesin Analisis Finansial / Mode Konsultasi.",
                queryTopic = queryTopic
            )
        }

        // 5. Deteksi Percakapan Santai / Bantuan / Salam
        val conversationResponse = detectConversationIntent(lower, isEn)
        if (conversationResponse != null) {
            return ParsedFinancialCommand(
                intentType = IntentType.CONVERSATION,
                status = ParseStatus.CONVERSATION_ONLY,
                transactionType = null,
                amount = null,
                title = "",
                categoryName = "",
                categoryIcon = "",
                sourceWallet = null,
                targetWallet = null,
                timestamp = System.currentTimeMillis(),
                detectedDateText = null,
                rawInput = userMessage,
                responseMessage = conversationResponse,
                queryTopic = null
            )
        }

        // 6. Deteksi Tanggal & Waktu
        val dateResult = TransactionDateParser.parseDateFromMessage(trimmed)
        val cleanedText = dateResult.cleanedText

        // 7. Deteksi Nominal Uang
        val extractedAmount = AmountNormalizer.extractAmount(cleanedText)
        val amount = extractedAmount?.value

        // 8. Klasifikasi Jenis Transaksi (Transfer vs Pemasukan vs Pengeluaran)
        val isTransfer = lower.contains("transfer") || lower.contains("pindahkan") ||
                lower.contains("kirim uang") || lower.contains("pindah saldo") ||
                lower.contains("top up") || lower.contains("topup") || lower.contains("move ")

        val isExplicitIncome = !isTransfer && (
                lower.contains("pemasukan") || lower.contains("uang masuk") ||
                lower.contains("gaji") || lower.contains("bonus") || lower.contains("dapat uang") ||
                lower.contains("terima uang") || lower.contains("penjualan") || lower.contains("omset") ||
                lower.contains("cair") || lower.contains("dividen") || lower.contains("thr") ||
                lower.contains("income") || lower.contains("salary") || lower.contains("deposit")
        )

        val isExplicitExpense = !isTransfer && !isExplicitIncome && (
                lower.contains("pengeluaran") || lower.contains("beli") || lower.contains("bayar") ||
                lower.contains("makan") || lower.contains("minum") || lower.contains("belanja") ||
                lower.contains("bensin") || lower.contains("ongkir") || lower.contains("tagihan") ||
                lower.contains("pulsa") || lower.contains("kuota") || lower.contains("jajan") ||
                lower.contains("expense") || lower.contains("bought") || lower.contains("paid") ||
                lower.contains("spend")
        )

        // Jika tidak ada nominal dan bukan pesan transaksi eksplisit
        if ((amount == null || amount <= 0.0) && !isTransfer && !isExplicitIncome && !isExplicitExpense) {
            return ParsedFinancialCommand(
                intentType = IntentType.UNKNOWN,
                status = ParseStatus.UNCLEAR_INTENT,
                transactionType = null,
                amount = null,
                title = "",
                categoryName = "",
                categoryIcon = "",
                sourceWallet = null,
                targetWallet = null,
                timestamp = System.currentTimeMillis(),
                detectedDateText = null,
                rawInput = userMessage,
                responseMessage = if (isEn)
                    "Format not recognized as a financial command. Example: 'Lunch 35,000 Cash' or 'Create wallet Dana opening balance 100k'."
                else
                    "Format kalimat belum dikenali sebagai transaksi atau perintah finansial. Contoh format: 'Beli makan 25rb Tunai' atau 'Buat dompet Dana saldo awal 100rb'."
            )
        }

        val transactionType = when {
            isTransfer -> TransactionType.TRANSFER
            isExplicitIncome -> TransactionType.INCOME
            else -> TransactionType.EXPENSE
        }

        // 9. Tangani Alur Transfer
        if (isTransfer) {
            return parseTransferCommand(
                userMessage = userMessage,
                cleanedText = cleanedText,
                amount = amount,
                extractedAmount = extractedAmount,
                availableWallets = availableWallets,
                dateResult = dateResult
            )
        }

        // 10. Resolusi Dompet untuk Pemasukan / Pengeluaran
        val walletResult = WalletResolver.resolveWalletFromText(cleanedText, availableWallets)
        val sourceWallet: Wallet?
        val ambiguousWallets: List<Wallet>
        val parseStatus: ParseStatus

        when (walletResult) {
            is WalletResolver.Result.ExactMatch -> {
                sourceWallet = walletResult.wallet
                ambiguousWallets = emptyList()
                parseStatus = if (amount != null && amount > 0.0) {
                    ParseStatus.READY_FOR_DRAFT
                } else {
                    ParseStatus.MISSING_AMOUNT
                }
            }
            is WalletResolver.Result.Ambiguous -> {
                sourceWallet = null
                ambiguousWallets = walletResult.candidateWallets
                parseStatus = ParseStatus.AMBIGUOUS_WALLET
            }
            is WalletResolver.Result.NotFound -> {
                sourceWallet = availableWallets.firstOrNull { it.isDefault } ?: availableWallets.firstOrNull()
                ambiguousWallets = emptyList()
                parseStatus = if (sourceWallet != null) {
                    if (amount != null && amount > 0.0) ParseStatus.READY_FOR_DRAFT else ParseStatus.MISSING_AMOUNT
                } else {
                    ParseStatus.MISSING_WALLET
                }
            }
        }

        val (catName, catIcon) = resolveCategory(lower, transactionType, availableCategories)
        val finalTitle = cleanTitle(cleanedText, extractedAmount, sourceWallet)

        val responseMsg = when (parseStatus) {
            ParseStatus.READY_FOR_DRAFT -> {
                val formattedAmt = formatCurrencyStr(amount ?: 0.0)
                val typeName = if (transactionType == TransactionType.INCOME) {
                    AppStrings.t("Pemasukan", "Income")
                } else {
                    AppStrings.t("Pengeluaran", "Expense")
                }
                if (isEn) {
                    "Draft $typeName ready: $finalTitle of $formattedAmt using wallet ${sourceWallet?.name}."
                } else {
                    "Draft $typeName siap: $finalTitle sebesar $formattedAmt menggunakan dompet ${sourceWallet?.name}."
                }
            }
            ParseStatus.MISSING_AMOUNT -> if (isEn) "Amount not detected. Please include the transaction amount." else "Nominal uang belum terdeteksi. Silakan sertakan nominalnya."
            ParseStatus.MISSING_WALLET -> if (isEn) "You don't have a wallet yet. Create one with: 'Create wallet Cash'." else "Anda belum memiliki dompet. Buat dompet dengan perintah seperti: 'Buat dompet Dana'."
            ParseStatus.AMBIGUOUS_WALLET -> if (isEn) "Multiple matching wallets found. Please select the intended wallet." else "Ditemukan beberapa dompet yang cocok. Silakan pilih dompet yang sesuai."
            else -> if (isEn) "Transaction draft ready for confirmation." else "Draft transaksi siap dikonfirmasi."
        }

        return ParsedFinancialCommand(
            intentType = if (transactionType == TransactionType.INCOME) IntentType.CREATE_INCOME else IntentType.CREATE_EXPENSE,
            status = parseStatus,
            transactionType = transactionType,
            amount = amount,
            title = finalTitle,
            categoryName = catName,
            categoryIcon = catIcon,
            sourceWallet = sourceWallet,
            targetWallet = null,
            ambiguousWallets = ambiguousWallets,
            timestamp = dateResult.timestamp,
            detectedDateText = dateResult.detectedDateText,
            rawInput = userMessage,
            responseMessage = responseMsg,
            currencyCode = extractedAmount?.detectedCurrencyCode
        )
    }

    private fun tryParseWalletCreation(
        rawMessage: String,
        lower: String,
        availableWallets: List<Wallet>,
        isEn: Boolean
    ): ParsedFinancialCommand? {
        val isWalletCreation = lower.startsWith("buat dompet") || lower.startsWith("tambah dompet") ||
                lower.startsWith("bikin dompet") || lower.startsWith("buat wallet") ||
                lower.startsWith("tambah wallet") || lower.startsWith("bikin wallet") ||
                lower.startsWith("create wallet") || lower.startsWith("add wallet") ||
                lower.startsWith("new wallet") || lower.startsWith("make wallet")

        if (!isWalletCreation) return null

        // Tentukan apakah ada permintaan saldo awal vs pemasukan
        val hasOpeningBalance = lower.contains("saldo awal") || lower.contains("isi awal") ||
                lower.contains("opening balance") || lower.contains("initial balance") ||
                lower.contains("saldo:") || lower.contains("balance:")

        val hasIncome = !hasOpeningBalance && (
                lower.contains("catat pemasukan") || lower.contains("ada uang masuk") ||
                lower.contains("dari pemasukan") || lower.contains("pemasukan") ||
                lower.contains("dari gaji") || lower.contains("gaji") ||
                lower.contains("with income") || lower.contains("add income") || lower.contains("income")
        )

        // Ekstraksi nominal jika ada
        val extractedAmount = AmountNormalizer.extractAmount(rawMessage)
        val parsedAmt = extractedAmount?.value ?: 0.0

        // Ekstraksi nama dompet
        var namePart = rawMessage
            .replace(Regex("(?i)^(?:buat|tambah|bikin|create|add|new|make)\\s+(?:dompet|wallet|akun|account)\\s+"), "")
            .trim()

        // Potong nama dompet sebelum keyword saldo awal / pemasukan / jenis dompet
        val splitKeywords = listOf(
            "saldo awal", "isi awal", "opening balance", "initial balance",
            "catat pemasukan", "ada uang masuk", "dari pemasukan", "pemasukan",
            "dari gaji", "gaji", "with income", "with balance",
            "saldo", "balance", "sebesar", "nominal",
            "jenis ewallet", "jenis e-wallet", "jenis bank", "jenis tunai", "jenis cash", "jenis investasi",
            "tipe ewallet", "tipe bank", "tipe tunai", "tipe cash",
            "type ewallet", "type bank", "type cash", "type investment",
            "jenis", "tipe", "type"
        )
        for (kw in splitKeywords) {
            val idx = namePart.indexOf(kw, ignoreCase = true)
            if (idx > 0) {
                namePart = namePart.substring(0, idx).trim()
            }
        }

        // Bersihkan tanda baca dan nominal jika masih tersisa di nama dompet
        namePart = namePart.replace(Regex("(?i)\\b(?:rp|usd|eur|[$€£¥₹])?\\s*\\d+(?:[.,]\\d+)?\\s*(?:k|rb|ribu|jt|juta|million|m)?\\b"), "").trim()
        namePart = namePart.replace(Regex("[,:;-]+$"), "").trim()

        if (namePart.isBlank()) {
            return ParsedFinancialCommand(
                intentType = IntentType.CREATE_WALLET,
                status = ParseStatus.MISSING_WALLET_NAME,
                transactionType = null,
                amount = null,
                title = "",
                categoryName = "",
                categoryIcon = "",
                sourceWallet = null,
                targetWallet = null,
                timestamp = System.currentTimeMillis(),
                detectedDateText = null,
                rawInput = rawMessage,
                responseMessage = if (isEn) "Please specify a name for the new wallet (e.g. 'Create wallet Dana')." else "Nama dompet belum ditentukan. Contoh: 'Buat dompet Dana saldo awal 100rb'."
            )
        }

        // Periksa duplikasi nama
        val isDuplicate = availableWallets.any { it.name.equals(namePart, ignoreCase = true) }
        if (isDuplicate) {
            return ParsedFinancialCommand(
                intentType = IntentType.CREATE_WALLET,
                status = ParseStatus.DUPLICATE_WALLET_NAME,
                transactionType = null,
                amount = null,
                title = namePart,
                categoryName = "",
                categoryIcon = "",
                sourceWallet = null,
                targetWallet = null,
                timestamp = System.currentTimeMillis(),
                detectedDateText = null,
                rawInput = rawMessage,
                responseMessage = if (isEn) "A wallet named '$namePart' already exists. Please choose a different name." else "Dompet dengan nama '$namePart' sudah ada. Silakan gunakan nama lain."
            )
        }

        // Inferensi Jenis Dompet (WalletType)
        val nameLower = namePart.lowercase(Locale.ROOT)
        val inferredType = when {
            lower.contains("jenis cash") || lower.contains("jenis tunai") || lower.contains("tipe tunai") || lower.contains("type cash") ||
                    nameLower.contains("cash") || nameLower.contains("tunai") || nameLower.contains("fisik") || nameLower.contains("kas") -> WalletType.CASH
            lower.contains("jenis ewallet") || lower.contains("jenis e-wallet") || lower.contains("tipe ewallet") || lower.contains("type ewallet") ||
                    nameLower.contains("gopay") || nameLower.contains("ovo") || nameLower.contains("dana") || nameLower.contains("shopeepay") ||
                    nameLower.contains("linkaja") || nameLower.contains("paypal") || nameLower.contains("ewallet") -> WalletType.EWALLET
            lower.contains("jenis investasi") || lower.contains("tipe investasi") || lower.contains("type investment") ||
                    nameLower.contains("bibit") || nameLower.contains("bareksa") || nameLower.contains("ajaib") || nameLower.contains("saham") || nameLower.contains("reksa") -> WalletType.INVESTMENT
            else -> WalletType.BANK
        }

        val intentType = when {
            hasIncome && parsedAmt > 0.0 -> IntentType.CREATE_WALLET_WITH_INCOME
            hasOpeningBalance && parsedAmt > 0.0 -> IntentType.CREATE_WALLET_WITH_OPENING_BALANCE
            parsedAmt > 0.0 && (lower.contains("saldo") || lower.contains("isi") || lower.contains("balance")) -> IntentType.CREATE_WALLET_WITH_OPENING_BALANCE
            else -> IntentType.CREATE_WALLET
        }

        val openingBalance = if (intentType == IntentType.CREATE_WALLET_WITH_OPENING_BALANCE) parsedAmt else 0.0
        val incomeAmt = if (intentType == IntentType.CREATE_WALLET_WITH_INCOME) parsedAmt else null

        val responseMsg = when (intentType) {
            IntentType.CREATE_WALLET_WITH_OPENING_BALANCE -> {
                val amtStr = formatCurrencyStr(openingBalance)
                if (isEn) "Ready to create wallet '$namePart' with an opening balance of $amtStr."
                else "Draft dompet siap: Buat dompet '$namePart' dengan saldo awal murni $amtStr (tidak dihitung sebagai pemasukan bulanan)."
            }
            IntentType.CREATE_WALLET_WITH_INCOME -> {
                val amtStr = formatCurrencyStr(incomeAmt ?: 0.0)
                if (isEn) "Ready to create wallet '$namePart' and record income of $amtStr."
                else "Draft dompet siap: Buat dompet '$namePart' sekaligus mencatat transaksi pemasukan sebesar $amtStr."
            }
            else -> {
                if (isEn) "Ready to create new wallet '$namePart' (initial balance 0)."
                else "Draft dompet siap: Buat dompet '$namePart' (saldo awal Rp 0)."
            }
        }

        return ParsedFinancialCommand(
            intentType = intentType,
            status = ParseStatus.WALLET_DRAFT_READY,
            transactionType = if (intentType == IntentType.CREATE_WALLET_WITH_INCOME) TransactionType.INCOME else null,
            amount = if (intentType == IntentType.CREATE_WALLET) null else parsedAmt,
            title = namePart,
            categoryName = "Dompet Baru",
            categoryIcon = "account_balance_wallet",
            sourceWallet = null,
            targetWallet = null,
            timestamp = System.currentTimeMillis(),
            detectedDateText = null,
            rawInput = rawMessage,
            responseMessage = responseMsg,
            targetWalletNameStr = namePart,
            walletType = inferredType,
            initialBalance = openingBalance,
            incomeAmount = incomeAmt,
            currencyCode = extractedAmount?.detectedCurrencyCode
        )
    }

    private fun detectQueryTopic(lower: String): QueryTopic? {
        // Tagihan & Cicilan (BILLS & DEBTS)
        if (lower.contains("tagihan minggu ini") || lower.contains("bills due this week") || lower.contains("jatuh tempo minggu ini")) {
            return QueryTopic.DUE_DATE_ANALYSIS
        }
        if (lower.contains("tagihan terdekat") || lower.contains("tagihan berikutnya") || lower.contains("upcoming bills") ||
            lower.contains("next bill") || lower.contains("kapan tagihan") || lower.contains("when is my next bill")
        ) {
            return QueryTopic.UPCOMING_BILLS
        }
        if (lower.contains("sisa cicilan") || lower.contains("berapa kali lagi cicilan") || lower.contains("installments left") || lower.contains("cicilan motor")) {
            return QueryTopic.OUTSTANDING_INSTALLMENT
        }
        if (lower.contains("total utang") || lower.contains("total cicilan") || lower.contains("total debt") || lower.contains("beban utang") || lower.contains("debt to income")) {
            return QueryTopic.DEBT_TO_INCOME
        }

        // Saldo & Kekayaan Bersih (BALANCE & NET WORTH)
        if (lower.contains("kekayaan bersih") || lower.contains("total kekayaan") || lower.contains("net worth") || lower.contains("total aset")) {
            return QueryTopic.NET_WORTH
        }
        val isAskingBalance = lower.contains("saldo") || lower.contains("sisa uang") || lower.contains("uang saya") || lower.contains("balance") || lower.contains("how much money")
        if (isAskingBalance) {
            val mentionsSpecificWallet = lower.contains("gopay") || lower.contains("bca") ||
                    lower.contains("cash") || lower.contains("tunai") || lower.contains("dana") ||
                    lower.contains("ovo") || lower.contains("bri") || lower.contains("mandiri") || lower.contains("seabank") || lower.contains("jago")
            return if (mentionsSpecificWallet) QueryTopic.SPECIFIC_WALLET_BALANCE else QueryTopic.TOTAL_BALANCE
        }

        // Pengeluaran & Pemasukan (EXPENSES & INCOME)
        if (lower.contains("5 pengeluaran terbesar") || lower.contains("top 5 expense") || lower.contains("top five")) {
            return QueryTopic.TOP_FIVE_EXPENSES
        }
        if (lower.contains("pengeluaran terbesar") || lower.contains("terboros") || lower.contains("terbanyak") || lower.contains("largest expense") || lower.contains("highest expense")) {
            return QueryTopic.LARGEST_EXPENSE
        }
        if (lower.contains("pengeluaran") && (lower.contains("bulan ini") || lower.contains("total")) || lower.contains("expense this month") || lower.contains("total expense")) {
            return QueryTopic.MONTHLY_EXPENSE
        }
        if (lower.contains("pemasukan") && (lower.contains("bulan ini") || lower.contains("total")) || lower.contains("income this month") || lower.contains("total income")) {
            return QueryTopic.MONTHLY_INCOME
        }

        // Anggaran & Pengeluaran Harian Aman (BUDGET & SAFE SPENDING)
        if (lower.contains("pengeluaran harian yang aman") || lower.contains("daily safe spending") || lower.contains("sisa harian") || lower.contains("safe to spend")) {
            return QueryTopic.DAILY_SAFE_SPENDING
        }
        if (lower.contains("anggaran aman") || lower.contains("sisa budget") || lower.contains("anggaran terpakai") || lower.contains("budget progress") || lower.contains("is my budget safe")) {
            return QueryTopic.BUDGET_PROGRESS
        }

        // Rasio Tabungan (SAVINGS RATIO)
        if (lower.contains("rasio tabungan") || lower.contains("persen yang tersisa") || lower.contains("savings ratio") || lower.contains("how much saved")) {
            return QueryTopic.SAVINGS_RATIO
        }

        // Kategori & Riwayat
        if (lower.contains("kategori") || lower.contains("category breakdown") || lower.contains("paling banyak habis buat apa")) {
            return QueryTopic.CATEGORY_BREAKDOWN
        }
        if (lower.contains("riwayat") || lower.contains("transaksi terakhir") || lower.contains("mutasi") || lower.contains("recent transactions") || lower.contains("last transactions")) {
            return QueryTopic.RECENT_TRANSACTIONS
        }

        // Ringkasan Kesehatan Finansial Bulanan (PINNED COMMAND)
        if (lower.contains("kondisi keuangan") || lower.contains("kesehatan keuangan") || lower.contains("analisa keuangan") ||
            lower.contains("financial health") || lower.contains("how is my financial") || lower.contains("apakah bulan ini boros")
        ) {
            return QueryTopic.FINANCIAL_HEALTH_SUMMARY
        }

        return null
    }

    private fun isCommandCatalogIntent(lower: String): Boolean {
        return lower.contains("katalog") || lower.contains("command catalog") ||
                lower.contains("daftar perintah") || lower.contains("bantuan perintah") ||
                lower.contains("menu perintah") || lower.contains("menu bantuan") ||
                lower.contains("bisa apa") || lower.contains("perintah apa") ||
                lower == "commands" || lower == "help" || lower == "help catalog" ||
                lower.contains("list commands") || lower.contains("list of commands") ||
                lower.contains("help commands")
    }

    private fun isCancelIntent(lower: String): Boolean {
        return lower == "batal" || lower == "batalkan" || lower == "cancel" ||
                lower.startsWith("batal ") || lower.contains("jangan simpan") ||
                lower.contains("tidak jadi") || lower.contains("hapus draft") || lower.contains("abort")
    }

    private fun detectConversationIntent(lower: String, isEn: Boolean): String? {
        val trimmed = lower.trim()

        // Sapaan / Salam
        if (trimmed == "halo" || trimmed == "hai" || trimmed == "hei" || trimmed == "hey" ||
            trimmed == "hi" || trimmed == "hello" || trimmed.startsWith("halo ") || trimmed.startsWith("hi ") ||
            trimmed.contains("selamat pagi") || trimmed.contains("good morning") ||
            trimmed.contains("selamat siang") || trimmed.contains("good afternoon") ||
            trimmed.contains("selamat malam") || trimmed.contains("good evening") ||
            trimmed.contains("assalamualaikum")
        ) {
            return if (isEn)
                "Hello! I am your DompetNalar Financial Assistant. Would you like to log an expense, create a wallet, or check your financial health?"
            else
                "Halo! Saya Asisten DompetNalar. Ada pengeluaran, pemasukan, buat dompet, atau pertanyaan keuangan yang ingin Anda tanyakan?"
        }

        // Ucapan Terima Kasih
        if (trimmed.startsWith("terima kasih") || trimmed.startsWith("makasih") ||
            trimmed.startsWith("thanks") || trimmed.startsWith("thank you") ||
            trimmed == "ok" || trimmed == "oke" || trimmed == "siap"
        ) {
            return if (isEn) "You're welcome! Happy to assist you manage your finances wisely."
            else "Sama-sama! Senang bisa membantu Anda mengelola keuangan dengan bijak dan teratur."
        }

        // Bantuan
        if (trimmed.contains("bantu saya") || trimmed.contains("help") || trimmed.contains("cara pakai") || trimmed == "bantuan") {
            return if (isEn)
                "You can type commands like:\n• 'Lunch 35,000 Cash'\n• 'Create wallet Dana opening balance 100k'\n• 'How is my financial health this month?'\n• 'Which bills are due this week?'"
            else
                "Anda dapat mengetik perintah seperti:\n• 'Makan siang 35.000 Tunai'\n• 'Buat dompet Dana saldo awal 100rb'\n• 'Bagaimana kondisi keuanganku bulan ini?'\n• 'Tagihan apa yang jatuh tempo minggu ini?'"
        }

        return null
    }

    private fun parseTransferCommand(
        userMessage: String,
        cleanedText: String,
        amount: Double?,
        extractedAmount: AmountNormalizer.ExtractedAmount?,
        availableWallets: List<Wallet>,
        dateResult: com.example.util.ParsedDateResult
    ): ParsedFinancialCommand {
        val isEn = LocaleHelper.isEnglish()
        val lower = cleanedText.lowercase(Locale.ROOT)
        var sourceWallet: Wallet? = null
        var targetWallet: Wallet? = null

        // Pola transfer: "transfer [amt] dari [A] ke [B]" atau "pindahkan [amt] [A] ke [B]"
        val transferKePattern = Pattern.compile("(?i)(?:dari\\s+([a-zA-Z0-9_\\s]+?)\\s+)?ke\\s+([a-zA-Z0-9_\\s]+)")
        val matcher = transferKePattern.matcher(lower)

        if (matcher.find()) {
            val fromText = matcher.group(1)?.trim()
            val toText = matcher.group(2)?.trim()

            if (!fromText.isNullOrBlank()) {
                val fromRes = WalletResolver.resolveWalletFromText(fromText, availableWallets)
                if (fromRes is WalletResolver.Result.ExactMatch) {
                    sourceWallet = fromRes.wallet
                }
            }
            if (!toText.isNullOrBlank()) {
                val toRes = WalletResolver.resolveWalletFromText(toText, availableWallets)
                if (toRes is WalletResolver.Result.ExactMatch) {
                    targetWallet = toRes.wallet
                }
            }
        }

        if (sourceWallet == null) {
            val wordsBeforeKe = lower.split("ke")[0]
            val fromRes = WalletResolver.resolveWalletFromText(wordsBeforeKe, availableWallets)
            if (fromRes is WalletResolver.Result.ExactMatch) {
                sourceWallet = fromRes.wallet
            }
        }

        if (sourceWallet == null) {
            sourceWallet = availableWallets.firstOrNull { it.isDefault } ?: availableWallets.firstOrNull()
        }

        val status: ParseStatus
        val msg: String

        if (amount == null || amount <= 0.0) {
            status = ParseStatus.MISSING_AMOUNT
            msg = if (isEn) "Transfer amount not specified. How much would you like to transfer?" else "Nominal transfer belum disebutkan. Berapa yang ingin Anda transfer?"
        } else if (sourceWallet == null) {
            status = ParseStatus.MISSING_WALLET
            msg = if (isEn) "Source wallet not available. Please add a wallet first." else "Dompet sumber transfer belum tersedia. Silakan tambahkan dompet terlebih dahulu."
        } else if (targetWallet == null) {
            status = ParseStatus.INVALID_TRANSFER_MISSING_TARGET
            msg = if (isEn) "Target wallet not specified. Which wallet do you want to transfer to?" else "Dompet tujuan transfer belum ditentukan. Ingin dipindahkan ke dompet apa?"
        } else if (sourceWallet.id == targetWallet.id) {
            status = ParseStatus.INVALID_TRANSFER_SAME_WALLET
            msg = if (isEn) "Source and target wallets cannot be the same (${sourceWallet.name})." else "Dompet sumber dan dompet tujuan tidak boleh sama (${sourceWallet.name})."
        } else {
            status = ParseStatus.READY_FOR_DRAFT
            msg = if (isEn) "Draft Transfer ready: Move ${formatCurrencyStr(amount)} from ${sourceWallet.name} to ${targetWallet.name}." else "Draft Transfer siap: Pindahkan ${formatCurrencyStr(amount)} dari ${sourceWallet.name} ke ${targetWallet.name}."
        }

        return ParsedFinancialCommand(
            intentType = IntentType.CREATE_TRANSFER,
            status = status,
            transactionType = TransactionType.TRANSFER,
            amount = amount,
            title = "Transfer Saldo",
            categoryName = "Transfer Saldo",
            categoryIcon = "swap_horiz",
            sourceWallet = sourceWallet,
            targetWallet = targetWallet,
            timestamp = dateResult.timestamp,
            detectedDateText = dateResult.detectedDateText,
            rawInput = userMessage,
            responseMessage = msg,
            currencyCode = extractedAmount?.detectedCurrencyCode
        )
    }

    private fun resolveCategory(
        lower: String,
        type: TransactionType,
        categories: List<CategoryItem>
    ): Pair<String, String> {
        for (cat in categories) {
            val catLower = cat.name.lowercase(Locale.ROOT)
            if (WalletResolver.containsWordOrPhrase(lower, catLower)) {
                return Pair(cat.name, cat.iconName.ifBlank { "category" })
            }
        }

        return when {
            type == TransactionType.TRANSFER -> Pair("Transfer Saldo", "swap_horiz")
            lower.contains("makan") || lower.contains("minum") || lower.contains("kopi") ||
                    lower.contains("bakso") || lower.contains("nasi") || lower.contains("resto") ||
                    lower.contains("cafe") || lower.contains("snack") || lower.contains("lunch") ||
                    lower.contains("dinner") || lower.contains("food") -> Pair("Makanan & Minuman", "restaurant")
            lower.contains("bensin") || lower.contains("pertalite") || lower.contains("pertamax") ||
                    lower.contains("parkir") || lower.contains("ojek") || lower.contains("gojek") ||
                    lower.contains("grab") || lower.contains("tol") || lower.contains("fuel") ||
                    lower.contains("transport") -> Pair("Transportasi & Bensin", "commute")
            lower.contains("belanja") || lower.contains("beli") || lower.contains("indomaret") ||
                    lower.contains("alfamart") || lower.contains("supermarket") || lower.contains("mall") ||
                    lower.contains("shopping") || lower.contains("groceries") -> Pair("Belanja & Hobi", "shopping_bag")
            lower.contains("listrik") || lower.contains("pln") || lower.contains("pulsa") ||
                    lower.contains("paket data") || lower.contains("wifi") || lower.contains("indihome") ||
                    lower.contains("air") || lower.contains("pdam") || lower.contains("utilities") ||
                    lower.contains("bill") -> Pair("Tagihan & Utilitas", "receipt")
            lower.contains("obat") || lower.contains("dokter") || lower.contains("apotek") ||
                    lower.contains("klinik") || lower.contains("health") || lower.contains("medical") -> Pair("Kesehatan", "medical_services")
            lower.contains("gaji") || lower.contains("salary") -> Pair("Gaji Pokok", "payments")
            lower.contains("bonus") || lower.contains("thr") -> Pair("Bonus & Tunjangan", "card_giftcard")
            lower.contains("investasi") || lower.contains("reksadana") || lower.contains("saham") ||
                    lower.contains("crypto") || lower.contains("investment") -> Pair("Investasi & Dividen", "trending_up")
            lower.contains("jual") || lower.contains("omset") || lower.contains("toko") -> Pair("Penjualan / Omset", "store")
            else -> if (type == TransactionType.INCOME) Pair("Pemasukan Lain", "payments") else Pair("Lain-lain", "category")
        }
    }

    private fun cleanTitle(
        text: String,
        amount: AmountNormalizer.ExtractedAmount?,
        wallet: Wallet?
    ): String {
        var clean = text
        if (amount != null) {
            clean = clean.replace(amount.rawMatchedText, " ")
        }
        if (wallet != null) {
            clean = clean.replace(Regex("(?i)\\b${Pattern.quote(wallet.name)}\\b"), " ")
        }
        clean = clean.replace(Regex("(?i)\\b(?:beli|bayar|makan|minum|catat|pengeluaran|pemasukan|pakai|menggunakan|dari|via|in|at|for)\\b"), " ")
        clean = clean.replace(Regex("[#@][a-zA-Z0-9_]+"), " ")
        clean = clean.replace(Regex("\\s+"), " ").trim()
        return clean.ifBlank { "Transaksi" }.replaceFirstChar { it.uppercase() }
    }

    private fun formatCurrencyStr(amount: Double): String {
        return CurrencyFormatter.formatCurrency(amount)
    }
}
