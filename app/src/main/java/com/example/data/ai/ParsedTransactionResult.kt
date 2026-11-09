package com.example.data.ai

import com.example.data.model.WalletType

data class ParsedTransactionResult(
    val title: String,
    val amount: Double,
    val type: String, // "EXPENSE", "INCOME", "TRANSFER", "CREATE_WALLET"
    val category: String,
    val categoryIcon: String,
    val suggestedWalletName: String,
    val suggestedTargetWalletName: String?,
    val note: String,
    val tags: String,
    val aiExplanation: String,
    val timestamp: Long = System.currentTimeMillis(),
    val detectedDateText: String? = null,
    val isValid: Boolean = true,
    val draftId: String? = null,
    val isWalletCreation: Boolean = false,
    val walletDraftId: String? = null,
    val walletType: WalletType? = null,
    val openingBalance: Double = 0.0,
    val incomeAmount: Double? = null
)
