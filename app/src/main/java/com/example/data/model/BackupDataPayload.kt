package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BackupDataPayload(
    val version: Int = 1,
    val wallets: List<Wallet> = emptyList(),
    val transactions: List<TransactionItem> = emptyList(),
    val categories: List<CategoryItem> = emptyList(),
    val users: List<User> = emptyList(),
    val splitBillHistory: List<SplitBillHistoryItem> = emptyList(),
    val budgetLimits: List<BudgetLimitItem> = emptyList(),
    val exportedAt: Long = System.currentTimeMillis()
)
