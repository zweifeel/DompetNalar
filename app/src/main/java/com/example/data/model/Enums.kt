package com.example.data.model

enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER
}

enum class WalletType(val idName: String, val enName: String) {
    BANK("Bank", "Bank"),
    EWALLET("E-Wallet", "E-Wallet"),
    CASH("Tunai / Cash", "Cash"),
    INVESTMENT("Investasi", "Investment"),
    OTHER("Lainnya", "Other");

    val displayName: String
        get() = if (com.example.util.LocaleHelper.isEnglish()) enName else idName
}

enum class AccountMode(val idName: String, val enName: String) {
    PERSONAL("Pribadi", "Personal"),
    BUSINESS("Bisnis / Toko", "Business / Store");

    val displayName: String
        get() = if (com.example.util.LocaleHelper.isEnglish()) enName else idName
}
