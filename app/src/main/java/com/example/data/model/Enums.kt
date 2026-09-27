package com.example.data.model

enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER
}

enum class WalletType(val displayName: String) {
    BANK("Bank"),
    EWALLET("E-Wallet"),
    CASH("Tunai / Cash"),
    INVESTMENT("Investasi"),
    OTHER("Lainnya")
}

enum class AccountMode(val displayName: String) {
    PERSONAL("Pribadi"),
    BUSINESS("Bisnis / Toko")
}
