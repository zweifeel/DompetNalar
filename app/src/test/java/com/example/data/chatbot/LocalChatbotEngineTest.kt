package com.example.data.chatbot

import com.example.data.ai.AdvisorAiService
import com.example.data.chatbot.parser.AmountNormalizer
import com.example.data.chatbot.parser.FinancialQueryEngine
import com.example.data.chatbot.parser.LocalFinancialIntentParser
import com.example.data.chatbot.parser.TransactionDraftManager
import com.example.data.chatbot.parser.WalletResolver
import com.example.data.model.BudgetLimitItem
import com.example.data.model.CategoryItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalChatbotEngineTest {

    private val testWallets = listOf(
        Wallet(id = 1, name = "Bank BCA", balance = 5_000_000.0, iconName = "account_balance", colorHex = "#005696"),
        Wallet(id = 2, name = "GoPay", balance = 250_000.0, iconName = "account_balance_wallet", colorHex = "#00AED6"),
        Wallet(id = 3, name = "Tunai / Cash", balance = 100_000.0, iconName = "payments", colorHex = "#4CAF50")
    )

    private val testCategories = listOf(
        CategoryItem(id = 1, name = "Makanan & Minuman", iconName = "restaurant", type = TransactionType.EXPENSE),
        CategoryItem(id = 2, name = "Transportasi", iconName = "commute", type = TransactionType.EXPENSE),
        CategoryItem(id = 3, name = "Gaji & Pendapatan", iconName = "payments", type = TransactionType.INCOME),
        CategoryItem(id = 4, name = "Transfer", iconName = "swap_horiz", type = TransactionType.TRANSFER)
    )

    // ==========================================
    // 1. AmountNormalizer Tests
    // ==========================================

    @Test
    fun testAmountNormalizer_IndonesianExpressions() {
        assertEquals(5_000.0, AmountNormalizer.extractAmount("Beli es teh 5k")?.value ?: 0.0, 0.01)
        assertEquals(50_000.0, AmountNormalizer.extractAmount("Makan siang 50rb")?.value ?: 0.0, 0.01)
        assertEquals(1_500_000.0, AmountNormalizer.extractAmount("Beli sparepart 1.5jt")?.value ?: 0.0, 0.01)
        assertEquals(2_000_000.0, AmountNormalizer.extractAmount("Gaji 2 juta")?.value ?: 0.0, 0.01)
        assertEquals(250_000.0, AmountNormalizer.extractAmount("Belanja Rp 250.000 di supermarket")?.value ?: 0.0, 0.01)
        assertEquals(150_000.0, AmountNormalizer.extractAmount("Total 150000")?.value ?: 0.0, 0.01)
    }

    @Test
    fun testAmountNormalizer_InvalidAndBoundaryValues() {
        // No amount mentioned
        assertNull(AmountNormalizer.extractAmount("Halo apa kabar"))
        // Reject negative values safely
        assertNull(AmountNormalizer.extractAmount("Pengeluaran minus -50000"))
        // Reject extreme overflow values safely
        assertNull(AmountNormalizer.extractAmount("Transfer uang 9999999999999999999"))
    }

    // ==========================================
    // 2. WalletResolver Tests
    // ==========================================

    @Test
    fun testWalletResolver_CaseInsensitiveAndAliases() {
        val bcaResult = WalletResolver.resolveWalletFromText("Beli makan pakai bca", testWallets)
        assertTrue(bcaResult is WalletResolver.Result.ExactMatch)
        assertEquals("Bank BCA", (bcaResult as WalletResolver.Result.ExactMatch).wallet.name)

        val gopayResult = WalletResolver.resolveWalletFromText("Bayar parkir pakai gopay", testWallets)
        assertTrue(gopayResult is WalletResolver.Result.ExactMatch)
        assertEquals("GoPay", (gopayResult as WalletResolver.Result.ExactMatch).wallet.name)

        val cashResult = WalletResolver.resolveWalletFromText("Beli gorengan tunai", testWallets)
        assertTrue(cashResult is WalletResolver.Result.ExactMatch)
        assertEquals("Tunai / Cash", (cashResult as WalletResolver.Result.ExactMatch).wallet.name)
    }

    @Test
    fun testWalletResolver_AntiFirstOrNullRule() {
        // If user specifies a non-existent wallet, NEVER arbitrarily pick wallets.first()
        val invalidResult = WalletResolver.resolveWalletFromText("Bayar pake crypto bitcoin", testWallets)
        assertTrue("WalletResolver must return NotFound rather than fallback to firstOrNull", invalidResult is WalletResolver.Result.NotFound)
    }

    // ==========================================
    // 3. LocalFinancialIntentParser Tests
    // ==========================================

    @Test
    fun testIntentParser_CreateExpense() {
        val command = LocalFinancialIntentParser.parse(
            userMessage = "Beli kopi susu 25rb pakai BCA",
            availableWallets = testWallets,
            availableCategories = testCategories
        )

        assertEquals(LocalFinancialIntentParser.IntentType.CREATE_EXPENSE, command.intentType)
        assertEquals(25_000.0, command.amount ?: 0.0, 0.01)
        assertEquals("Makanan & Minuman", command.categoryName)
        assertEquals("Bank BCA", command.sourceWallet?.name)
        assertNull("Harus null jika tidak menyebut kemarin/lusa", command.detectedDateText)
    }

    @Test
    fun testIntentParser_CreateIncome() {
        val command = LocalFinancialIntentParser.parse(
            userMessage = "Dapat bonus 1.5jt ke BCA",
            availableWallets = testWallets,
            availableCategories = testCategories
        )

        assertEquals(LocalFinancialIntentParser.IntentType.CREATE_INCOME, command.intentType)
        assertEquals(1_500_000.0, command.amount ?: 0.0, 0.01)
        assertEquals("Bank BCA", command.sourceWallet?.name)
    }

    @Test
    fun testIntentParser_CreateTransfer() {
        val command = LocalFinancialIntentParser.parse(
            userMessage = "Transfer 100rb dari BCA ke GoPay",
            availableWallets = testWallets,
            availableCategories = testCategories
        )

        assertEquals(LocalFinancialIntentParser.IntentType.CREATE_TRANSFER, command.intentType)
        assertEquals(100_000.0, command.amount ?: 0.0, 0.01)
        assertEquals("Bank BCA", command.sourceWallet?.name)
        assertEquals("GoPay", command.targetWallet?.name)
    }

    @Test
    fun testIntentParser_BackdatedDate() {
        val command = LocalFinancialIntentParser.parse(
            userMessage = "Kemarin beli bensin 30rb pakai tunai",
            availableWallets = testWallets,
            availableCategories = testCategories
        )

        assertEquals(LocalFinancialIntentParser.IntentType.CREATE_EXPENSE, command.intentType)
        assertEquals(30_000.0, command.amount ?: 0.0, 0.01)
        assertNotNull("Date text kemarin harus terdeteksi", command.detectedDateText)
        assertTrue("Timestamp transaksi kemarin harus sebelum waktu sekarang", command.timestamp < System.currentTimeMillis())
    }

    // ==========================================
    // 4. TransactionDraftManager Tests
    // ==========================================

    @Test
    fun testDraftManager_LifecycleAndCancellation() {
        val command = LocalFinancialIntentParser.parse(
            userMessage = "Beli buku 75rb pakai BCA",
            availableWallets = testWallets,
            availableCategories = testCategories
        )

        val draft = TransactionDraftManager.createDraft(command, ownerUserId = 1L)
        assertNotNull("Draft should be created from valid command", draft)
        assertEquals(TransactionDraftManager.DraftStatus.PENDING, draft!!.status)
        assertEquals(75_000.0, draft.amount, 0.01)
        assertEquals("Bank BCA", draft.sourceWallet.name)

        // Test cancel lifecycle
        val cancelled = TransactionDraftManager.cancelDraft(draft.id)
        assertTrue("Draft cancellation should succeed", cancelled)
        assertEquals(TransactionDraftManager.DraftStatus.CANCELLED, draft.status)
    }

    // ==========================================
    // 5. FinancialQueryEngine & AdvisorAiService Tests
    // ==========================================

    @Test
    fun testFinancialQueryEngine_DeterministicAnswers() = runTest {
        val transactions = listOf(
            TransactionItem(
                id = 1,
                userId = 1L,
                title = "Gaji Bulanan",
                amount = 10_000_000.0,
                type = TransactionType.INCOME,
                category = "Gaji",
                categoryIcon = "payments",
                walletId = 1,
                walletName = "Bank BCA",
                timestamp = System.currentTimeMillis()
            ),
            TransactionItem(
                id = 2,
                userId = 1L,
                title = "Belanja Bulanan Supermarket",
                amount = 2_500_000.0,
                type = TransactionType.EXPENSE,
                category = "Belanja",
                categoryIcon = "shopping_cart",
                walletId = 1,
                walletName = "Bank BCA",
                timestamp = System.currentTimeMillis()
            ),
            TransactionItem(
                id = 3,
                userId = 1L,
                title = "Beli Pulsa & Kuota",
                amount = 150_000.0,
                type = TransactionType.EXPENSE,
                category = "Tagihan",
                categoryIcon = "receipt",
                walletId = 2,
                walletName = "GoPay",
                timestamp = System.currentTimeMillis()
            )
        )

        val budgetLimits = listOf(
            BudgetLimitItem(id = "Belanja", limitAmount = 3_000_000.0, isCategory = true, userId = 1L)
        )

        // Query Saldo BCA
        val saldoResponse = AdvisorAiService.getAdvisorResponse(
            userMessage = "Berapa saldo BCA saya?",
            transactions = transactions,
            budgetLimits = budgetLimits,
            wallets = testWallets
        )
        assertTrue("Harus menyebut saldo Bank BCA", saldoResponse.contains("Bank BCA") && saldoResponse.contains("5.000.000"))

        // Query Pengeluaran Terbesar
        val largestResponse = AdvisorAiService.getAdvisorResponse(
            userMessage = "Apa pengeluaran terbesar saya?",
            transactions = transactions,
            budgetLimits = budgetLimits,
            wallets = testWallets
        )
        assertTrue("Harus menyebut Belanja Bulanan Supermarket", largestResponse.contains("Belanja Bulanan Supermarket") && largestResponse.contains("2.500.000"))

        // Query Evaluasi Kesehatan Keuangan
        val healthResponse = AdvisorAiService.getAdvisorResponse(
            userMessage = "Bagaimana kesehatan keuangan saya?",
            transactions = transactions,
            budgetLimits = budgetLimits,
            wallets = testWallets
        )
        assertTrue("Harus menghitung rasio secara matematis", healthResponse.contains("Rasio Tabungan") || healthResponse.contains("Kesehatan Keuangan"))
    }
    // ==========================================
    // 6. Non-Transaction, Greeting, Query & Integrity Tests (BUG-01, BUG-02, BUG-03)
    // ==========================================

    @Test
    fun testIntentParser_GreetingsDoNotCreateDrafts() {
        val greetings = listOf("Halo", "Selamat pagi", "hai", "assalamualaikum")
        for (g in greetings) {
            val cmd = LocalFinancialIntentParser.parse(g, testWallets, testCategories)
            assertEquals("Greeting harus terklasifikasi sebagai CONVERSATION", LocalFinancialIntentParser.IntentType.CONVERSATION, cmd.intentType)
            assertEquals(LocalFinancialIntentParser.ParseStatus.CONVERSATION_ONLY, cmd.status)
            assertNull("Nominal harus null untuk pesan sapaan", cmd.amount)
            assertTrue("Pesan respons harus ramah dan tidak kosong", cmd.responseMessage.isNotBlank())
            assertNull("Pesan sapaan tidak boleh membuat draft", TransactionDraftManager.createDraft(cmd))
        }
    }

    @Test
    fun testIntentParser_Mode0QueryFinance_ReturnsGuidingMessage_NotEmpty() {
        val queries = listOf("Berapa saldo saya?", "pengeluaran bulan ini", "apa pengeluaran terbesar saya")
        for (q in queries) {
            val cmd = LocalFinancialIntentParser.parse(q, testWallets, testCategories)
            assertEquals("Kueri analitik harus terdeteksi sebagai QUERY_FINANCE", LocalFinancialIntentParser.IntentType.QUERY_FINANCE, cmd.intentType)
            assertTrue("Pesan respons kueri di parser tidak boleh kosong", cmd.responseMessage.isNotBlank())
            assertTrue("Pesan respons harus memandu pengguna ke tab Konsultasi", cmd.responseMessage.contains("Mode Konsultasi"))
            assertNull("Kueri finansial tidak boleh menghasilkan draft transaksi", TransactionDraftManager.createDraft(cmd))
        }
    }

    @Test
    fun testIntentParser_UnclearMessage_DoesNotCreateTransaction() {
        val unclearMessages = listOf("kamu lucu sekali", "asdfghjk", "tolong bantu saya", "terima kasih")
        for (msg in unclearMessages) {
            val cmd = LocalFinancialIntentParser.parse(msg, testWallets, testCategories)
            assertNull("Pesan tidak jelas atau percakapan tidak boleh memiliki nominal", cmd.amount)
            assertNull("Pesan tidak jelas tidak boleh membuat draft", TransactionDraftManager.createDraft(cmd))
        }
    }

    @Test
    fun testTransferIntegrity_RejectsSameSourceAndTarget() {
        val cmd = LocalFinancialIntentParser.parse(
            userMessage = "Transfer 50rb dari BCA ke BCA",
            availableWallets = testWallets,
            availableCategories = testCategories
        )
        assertEquals(LocalFinancialIntentParser.IntentType.CREATE_TRANSFER, cmd.intentType)
        assertNull("Transfer ke dompet yang sama tidak boleh menghasilkan draft", TransactionDraftManager.createDraft(cmd))
    }

    @Test
    fun testDraftManager_ZeroOrNegativeAmount_CannotCreateDraft() {
        val zeroCmd = LocalFinancialIntentParser.ParsedFinancialCommand(
            intentType = LocalFinancialIntentParser.IntentType.CREATE_EXPENSE,
            status = LocalFinancialIntentParser.ParseStatus.READY_FOR_DRAFT,
            transactionType = TransactionType.EXPENSE,
            amount = 0.0,
            title = "Makan",
            categoryName = "Makanan",
            categoryIcon = "restaurant",
            sourceWallet = testWallets[0],
            targetWallet = null,
            timestamp = System.currentTimeMillis(),
            detectedDateText = null,
            rawInput = "Makan 0",
            responseMessage = ""
        )
        assertNull("Draft dengan nominal 0.0 harus ditolak", TransactionDraftManager.createDraft(zeroCmd))
    }
}