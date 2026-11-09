package com.example.util

/**
 * DompetNalar Bilingual Translation Engine (Bahasa Indonesia & English).
 * Menyediakan teks resmi terjemahan keuangan yang konsisten, akurat, dan reaktif terhadap LocaleHelper.
 */
object AppStrings {

    fun t(id: String, en: String): String {
        return if (LocaleHelper.isEnglish()) en else id
    }

    // --- Bottom Navigation Tabs ---
    val navHome: String get() = t("Beranda", "Home")
    val navTransactions: String get() = t("Mutasi", "Transactions")
    val navWallets: String get() = t("Dompet", "Wallets")
    val navAnalytics: String get() = t("Analisis", "Analytics")
    val navSettings: String get() = t("Pengaturan", "Settings")
    val navScanReceipt: String get() = t("Scan Nota", "Scan Receipt")
    val navSplitBill: String get() = t("Split Bill", "Split Bill")
    val navBudget: String get() = t("Anggaran", "Budget")
    val navExportReport: String get() = t("Export Laporan", "Export Report")
    val navFeedback: String get() = t("Feedback", "Feedback")

    // --- Common Actions & Buttons ---
    val save: String get() = t("Simpan", "Save")
    val cancel: String get() = t("Batal", "Cancel")
    val close: String get() = t("Tutup", "Close")
    val delete: String get() = t("Hapus", "Delete")
    val edit: String get() = t("Ubah", "Edit")
    val add: String get() = t("Tambah", "Add")
    val confirm: String get() = t("Konfirmasi", "Confirm")
    val back: String get() = t("Kembali", "Back")
    val loading: String get() = t("Memuat...", "Loading...")
    val success: String get() = t("Berhasil", "Success")
    val error: String get() = t("Terjadi Kesalahan", "An Error Occurred")
    val retry: String get() = t("Coba Lagi", "Try Again")
    val viewAll: String get() = t("Lihat Semua", "View All")
    val filter: String get() = t("Filter", "Filter")
    val search: String get() = t("Cari...", "Search...")

    // --- Dashboard & Summary Cards ---
    val totalBalance: String get() = t("Total Saldo DompetNalar", "DompetNalar Total Balance")
    val netWorth: String get() = t("Kekayaan Bersih (Net Worth)", "Total Net Worth")
    val income: String get() = t("Pemasukan", "Income")
    val expense: String get() = t("Pengeluaran", "Expense")
    val transfer: String get() = t("Transfer", "Transfer")
    val personalMode: String get() = t("Mode Pribadi", "Personal Mode")
    val businessMode: String get() = t("Mode Bisnis", "Business Mode")
    val showBalance: String get() = t("Tampilkan Saldo", "Show Balance")
    val hideBalance: String get() = t("Sembunyikan Saldo", "Hide Balance")
    val quickAddIncome: String get() = t("Catat Pemasukan", "Add Income")
    val quickAddExpense: String get() = t("Catat Pengeluaran", "Add Expense")
    val quickTransfer: String get() = t("Transfer Antar Dompet", "Transfer Funds")
    val recentTransactions: String get() = t("Transaksi Terakhir", "Recent Transactions")
    val noTransactionsYet: String get() = t("Belum ada transaksi yang dicatat", "No transactions recorded yet")
    val monthlyCashflow: String get() = t("Arus Kas Bulan Ini", "Monthly Cashflow")
    val financialHealthScore: String get() = t("Skor Kesehatan Finansial", "Financial Health Score")

    // --- Transactions & Mutation ---
    val allTransactions: String get() = t("Semua Transaksi", "All Transactions")
    val transactionDetails: String get() = t("Detail Transaksi", "Transaction Details")
    val category: String get() = t("Kategori", "Category")
    val amount: String get() = t("Nominal", "Amount")
    val date: String get() = t("Tanggal", "Date")
    val wallet: String get() = t("Dompet / Akun", "Wallet / Account")
    val notes: String get() = t("Catatan (Opsional)", "Notes (Optional)")
    val deleteTransactionConfirm: String get() = t("Apakah Anda yakin ingin menghapus transaksi ini?", "Are you sure you want to delete this transaction?")

    // --- Wallets ---
    val walletsTitle: String get() = t("Kelola Dompet & Rekening", "Manage Wallets & Accounts")
    val addWallet: String get() = t("Tambah Dompet Baru", "Add New Wallet")
    val walletName: String get() = t("Nama Dompet", "Wallet Name")
    val walletType: String get() = t("Tipe Dompet", "Wallet Type")
    val initialBalance: String get() = t("Saldo Awal", "Initial Balance")
    val walletCash: String get() = t("Uang Tunai (Cash)", "Cash")
    val walletBank: String get() = t("Rekening Bank", "Bank Account")
    val walletEWallet: String get() = t("Dompet Digital (E-Wallet)", "E-Wallet")

    // --- Budgeting ---
    val budgetTitle: String get() = t("Anggaran Bulanan", "Monthly Budget")
    val addBudget: String get() = t("Buat Target Anggaran", "Set Budget Target")
    val budgetRemaining: String get() = t("Sisa Anggaran", "Remaining Budget")
    val budgetSpent: String get() = t("Terpakai", "Spent")
    val overBudget: String get() = t("Melebihi Anggaran!", "Over Budget!")

    // --- Debt Planner & PayLater ---
    val debtTitle: String get() = t("Perencana Bebas Utang & PayLater", "Debt & PayLater Freedom Planner")
    val totalDebt: String get() = t("Total Kewajiban Utang", "Total Debt Liabilities")
    val addDebt: String get() = t("Tambah Tagihan / Utang", "Add Debt / Bill")
    val installmentPlans: String get() = t("Rencana Cicilan Aktif", "Active Installment Plans")
    val snowballMethod: String get() = t("Metode Snowball (Nominal Terkecil)", "Snowball Method (Lowest Balance)")
    val avalancheMethod: String get() = t("Metode Avalanche (Bunga Tertinggi)", "Avalanche Method (Highest Interest)")
    val dueDate: String get() = t("Jatuh Tempo", "Due Date")
    val paidOff: String get() = t("Lunas", "Paid Off")

    // --- Smart Master Paywall & Subscription ---
    val smartMasterTitle: String get() = t("Upgrade ke Smart Master", "Upgrade to Smart Master")
    val smartMasterSubtitle: String get() = t("Buka seluruh potensi kecerdasan finansial Anda tanpa batas", "Unlock your full financial potential with unlimited power")
    val activeMemberStatus: String get() = t("Status Keanggotaan:", "Membership Status:")
    val smartMasterBadge: String get() = t("SMART MASTER (PREMIUM)", "SMART MASTER (PREMIUM)")
    val freeMemberBadge: String get() = t("ANGGOTA GRATIS", "FREE MEMBER")
    val monthlyPlan: String get() = t("Langganan Bulanan", "Monthly Subscription")
    val yearlyPlan: String get() = t("Langganan Tahunan", "Yearly Subscription")
    val bestValueBadge: String get() = t("PALING HEMAT", "BEST VALUE")
    val restorePurchases: String get() = t("Pulihkan Pembelian", "Restore Purchases")
    val trialBadge: String get() = t("Akses Dasar", "Basic Access")
    val watchAdButton: String get() = t("Tonton Iklan (1 Jam Akses)", "Watch Ad (1 Hour Access)")
    val subscribeNow: String get() = t("Langganan Sekarang via Google Play", "Subscribe Now via Google Play")

    // --- Settings Screen ---
    val settingsTitle: String get() = t("Pengaturan & Preferensi", "Settings & Preferences")
    val accountSection: String get() = t("Akun & Profil", "Account & Profile")
    val appPreferencesSection: String get() = t("Preferensi Aplikasi", "App Preferences")
    val languageSetting: String get() = t("Bahasa Aplikasi", "App Language")
    val currencySetting: String get() = t("Mata Uang (Currency)", "Currency")
    val appearanceSetting: String get() = t("Tema & Tampilan", "Theme & Appearance")
    val duressPinSetting: String get() = t("Duress PIN & Keamanan", "Duress PIN & Security")
    val cloudBackupSetting: String get() = t("Backup & Sinkronisasi Cloud", "Cloud Backup & Sync")
    val privacyPolicy: String get() = t("Kebijakan Privasi", "Privacy Policy")
    val termsOfService: String get() = t("Ketentuan Layanan", "Terms of Service")
    val appVersion: String get() = t("Versi Aplikasi", "App Version")
    val logout: String get() = t("Keluar dari Akun", "Log Out")

    // --- Relative Date & Time ---
    val today: String get() = t("Hari Ini", "Today")
    val yesterday: String get() = t("Kemarin", "Yesterday")

    // --- Chatbot & Financial Assistant ---
    val chatTitle: String get() = t("DompetNalar AI Assistant", "DompetNalar AI Assistant")
    val chatGreeting: String get() = t(
        "Halo! Saya Asisten AI Finansial DompetNalar.\nKetik transaksi, buat dompet, atau tanyakan kondisi keuangan Anda.\n\nContoh:\n• 'Makan siang 35.000 pakai GoPay'\n• 'Buat dompet Dana saldo awal 100 ribu'\n• 'Bagaimana kondisi keuanganku bulan ini?'\n• 'Tagihan apa yang jatuh tempo minggu ini?'",
        "Hello! I am DompetNalar AI Financial Assistant.\nLog transactions, create wallets, or ask about your finances.\n\nExamples:\n• 'Lunch 35,000 with GoPay'\n• 'Create wallet Dana opening balance 100,000'\n• 'How is my financial health this month?'\n• 'Which bills are due this week?'"
    )
    val commandCatalogTitle: String get() = t("Katalog Perintah Pintar", "Smart Command Catalog")
    val commandCatalogSubtitle: String get() = t("Pilih perintah untuk langsung dijalankan atau disalin ke kolom input", "Select a command to execute or insert into input")
    val catQuickLog: String get() = t("Catat Cepat", "Quick Log")
    val catWalletManage: String get() = t("Kelola Dompet", "Manage Wallets")
    val catFinancialAnalysis: String get() = t("Analisis Keuangan", "Financial Analysis")
    val catBillsAndDebts: String get() = t("Tagihan & Cicilan", "Bills & Installments")
    val viewFullAnalytics: String get() = t("Lihat Rincian Analitik Lengkap", "View Full Analytics Details")
    val confirmCreateWallet: String get() = t("Konfirmasi Buat Dompet", "Confirm Create Wallet")
    val confirmSaveTransaction: String get() = t("Simpan Transaksi", "Save Transaction")
    val walletCreatedSuccess: String get() = t("Dompet berhasil dibuat!", "Wallet successfully created!")
    val openingBalanceLabel: String get() = t("Saldo Awal Murni", "Opening Balance Only")
    val incomeRecordLabel: String get() = t("Catat Pemasukan Baru", "Record New Income")
    val upcomingBillsTitle: String get() = t("Tagihan & Cicilan Terdekat", "Upcoming Bills & Installments")
    val noUpcomingBills: String get() = t("Tidak ada tagihan atau cicilan aktif yang tercatat.", "No active bills or installment plans found.")
    val financialHealthAnalytics: String get() = t("Analitik Kesehatan Finansial", "Financial Health Analytics")
}
