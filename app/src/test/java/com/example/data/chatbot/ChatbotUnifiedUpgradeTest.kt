package com.example.data.chatbot

import com.example.data.chatbot.parser.AmountNormalizer
import com.example.data.chatbot.parser.LocalFinancialIntentParser
import com.example.data.chatbot.parser.WalletDraftManager
import com.example.data.model.CategoryItem
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatbotUnifiedUpgradeTest {

    private val testWallets = listOf(
        Wallet(id = 1, name = "Bank BCA", balance = 5_000_000.0, iconName = "account_balance", colorHex = "#005696", type = WalletType.BANK),
        Wallet(id = 2, name = "GoPay", balance = 250_000.0, iconName = "account_balance_wallet", colorHex = "#00AED6", type = WalletType.EWALLET),
        Wallet(id = 3, name = "Tunai", balance = 100_000.0, iconName = "payments", colorHex = "#4CAF50", type = WalletType.CASH)
    )

    private val testCategories = listOf(
        CategoryItem(id = 1, name = "Makanan & Minuman", iconName = "restaurant", type = TransactionType.EXPENSE),
        CategoryItem(id = 2, name = "Transportasi", iconName = "commute", type = TransactionType.EXPENSE),
        CategoryItem(id = 3, name = "Gaji & Pendapatan", iconName = "payments", type = TransactionType.INCOME),
        CategoryItem(id = 4, name = "Transfer", iconName = "swap_horiz", type = TransactionType.TRANSFER)
    )

    @Before
    fun setUp() {
        WalletDraftManager.clearAllForTesting()
    }

    // ==========================================
    // 1. Natural Language Intent Parsing for Wallet Creation
    // ==========================================

    @Test
    fun testIntentParser_CreateWallet_Basic() {
        val command = LocalFinancialIntentParser.parse(
            userMessage = "buat dompet Dana jenis ewallet",
            availableWallets = testWallets,
            availableCategories = testCategories
        )

        assertEquals(LocalFinancialIntentParser.IntentType.CREATE_WALLET, command.intentType)
        assertEquals("Dana", command.targetWalletNameStr)
        assertEquals(WalletType.EWALLET, command.walletType)
        assertNull(command.amount)
    }

    @Test
    fun testIntentParser_CreateWallet_WithOpeningBalance() {
        val command = LocalFinancialIntentParser.parse(
            userMessage = "bikin dompet Tabungan Haji saldo awal 2.500.000 jenis bank",
            availableWallets = testWallets,
            availableCategories = testCategories
        )

        assertEquals(LocalFinancialIntentParser.IntentType.CREATE_WALLET_WITH_OPENING_BALANCE, command.intentType)
        assertEquals("Tabungan Haji", command.targetWalletNameStr)
        assertEquals(WalletType.BANK, command.walletType)
        assertEquals(2_500_000.0, command.initialBalance ?: 0.0, 0.01)
    }

    @Test
    fun testIntentParser_CreateWallet_WithIncome() {
        val command = LocalFinancialIntentParser.parse(
            userMessage = "buat dompet Mandiri dari gaji 5 juta",
            availableWallets = testWallets,
            availableCategories = testCategories
        )

        assertEquals(LocalFinancialIntentParser.IntentType.CREATE_WALLET_WITH_INCOME, command.intentType)
        assertTrue(command.targetWalletNameStr?.contains("Mandiri", ignoreCase = true) == true)
        assertEquals(5_000_000.0, command.incomeAmount ?: 0.0, 0.01)
    }

    @Test
    fun testIntentParser_CreateWallet_English() {
        val command = LocalFinancialIntentParser.parse(
            userMessage = "create wallet Cash Box with initial balance $50",
            availableWallets = testWallets,
            availableCategories = testCategories
        )

        assertEquals(LocalFinancialIntentParser.IntentType.CREATE_WALLET_WITH_OPENING_BALANCE, command.intentType)
        assertTrue(command.targetWalletNameStr?.contains("Cash Box", ignoreCase = true) == true)
        assertEquals(50.0, command.initialBalance ?: 0.0, 0.01)
    }

    // ==========================================
    // 2. Command Catalog Recognition
    // ==========================================

    @Test
    fun testIntentParser_CommandCatalog_Triggers() {
        val triggers = listOf(
            "katalog perintah",
            "menu bantuan",
            "bisa apa saja",
            "perintah apa saja",
            "command catalog",
            "help catalog",
            "list commands"
        )

        for (trigger in triggers) {
            val command = LocalFinancialIntentParser.parse(
                userMessage = trigger,
                availableWallets = testWallets,
                availableCategories = testCategories
            )
            assertEquals("Trigger '$trigger' must resolve to SHOW_COMMAND_CATALOG",
                LocalFinancialIntentParser.IntentType.SHOW_COMMAND_CATALOG, command.intentType)
        }
    }

    // ==========================================
    // 3. WalletDraftManager Lifecycle & Accounting Separation
    // ==========================================

    @Test
    fun testWalletDraftManager_OpeningBalance_ExplicitProperties() = runTest {
        val command = LocalFinancialIntentParser.parse(
            userMessage = "buat dompet Brankas saldo awal 1.000.000 jenis tunai",
            availableWallets = testWallets,
            availableCategories = testCategories
        )

        val draft = WalletDraftManager.createDraft(command, ownerUserId = 1L)

        assertNotNull(draft)
        assertEquals("Brankas", draft?.name)
        assertEquals(WalletType.CASH, draft?.type)
        assertEquals(1_000_000.0, draft?.initialBalance ?: 0.0, 0.01)
        assertNull("Opening balance draft must not have incomeAmount", draft?.incomeAmount)

        val retrieved = WalletDraftManager.getDraft(draft!!.id)
        assertEquals(draft.id, retrieved?.id)

        // Cancel draft
        val cancelled = WalletDraftManager.cancelDraft(draft.id)
        assertTrue(cancelled)
        assertEquals(WalletDraftManager.DraftStatus.CANCELLED, WalletDraftManager.getDraft(draft.id)?.status)
    }

    @Test
    fun testWalletDraftManager_IncomeCreation_ExplicitProperties() = runTest {
        val command = LocalFinancialIntentParser.parse(
            userMessage = "buat dompet Rekening Freelance dari pemasukan 3.500.000 jenis bank",
            availableWallets = testWallets,
            availableCategories = testCategories
        )

        val draft = WalletDraftManager.createDraft(command, ownerUserId = 1L)

        assertNotNull(draft)
        assertTrue(draft?.name?.contains("Freelance") == true)
        assertEquals(WalletType.BANK, draft?.type)
        assertEquals(0.0, draft?.initialBalance ?: 0.0, 0.01)
        assertEquals(3_500_000.0, draft?.incomeAmount ?: 0.0, 0.01)
    }

    @Test
    fun testWalletDraftManager_InvalidDraftId_ReturnsNull() = runTest {
        val retrieved = WalletDraftManager.getDraft("non-existent-uuid")
        assertNull(retrieved)

        val cancelResult = WalletDraftManager.cancelDraft("non-existent-uuid")
        assertFalse(cancelResult)
    }
}
