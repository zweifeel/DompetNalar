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
    val done: String get() = t("Selesai", "Done")
    val previous: String get() = t("Sebelumnya", "Previous")
    val next: String get() = t("Selanjutnya", "Next")
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

    // --- Notifications & Reminders ---
    val notifZeroBalanceTitle: String get() = t("Saldo Habis", "Zero Balance")
    val notifLowBalanceTitle: String get() = t("Peringatan Saldo Rendah", "Low Balance Alert")
    val notifDueTodayTitle: String get() = t("Jatuh Tempo Hari Ini", "Due Today")
    val notifDebtReminderTitle: String get() = t("Pengingat Hutang", "Debt Reminder")
    val notifOverdueTitle: String get() = t("Tagihan Terlewat", "Overdue Bill")
    val notifInstallmentDueTodayTitle: String get() = t("Jatuh Tempo Cicilan Hari Ini", "Installment Due Today")
    val notifInstallmentReminderTitle: String get() = t("Pengingat Cicilan", "Installment Reminder")
    val notifInstallmentOverdueTitle: String get() = t("Cicilan Terlewat", "Overdue Installment")
    val notifOverbudgetTitle: String get() = t("Overbudget Anggaran Bulanan!", "Monthly Budget Overspent!")
    val notifBudgetWarningTitle: String get() = t("Peringatan Anggaran Bulanan", "Monthly Budget Warning")
    val notifApproachingLimitTitle: String get() = t("Mendekati Limit", "Approaching Limit")
    val notifRecurringRecordedTitle: String get() = t("Transaksi Berulang Tercatat", "Recurring Transaction Recorded")
    val notifRecurringChannelName: String get() = t("DompetNalar Transaksi Berulang", "DompetNalar Recurring Transactions")
    val notifRecurringChannelDesc: String get() = t("Notifikasi transaksi berulang otomatis (Gaji / Rutin)", "Automatic recurring transaction notifications (Salary / Routine)")
    val notifRecurringDisabledTitle: String get() = t("Jadwal Berulang Dinonaktifkan", "Recurring Schedules Disabled")

    // --- Debt Planner & Bill Management ---
    val newContract: String get() = t("Tambah Kontrak Baru", "Add New Contract")
    val creditorName: String get() = t("Nama Kreditur / Platform", "Creditor / Platform Name")
    val remainingPrincipal: String get() = t("Sisa Pokok Utang", "Remaining Principal")
    val interestRate: String get() = t("Suku Bunga (% / Bulan)", "Interest Rate (% / Month)")
    val tenureMonths: String get() = t("Sisa Tenor (Bulan)", "Remaining Tenure (Months)")
    val monthlyInstallment: String get() = t("Cicilan per Bulan", "Monthly Installment")
    val simulatedPayoff: String get() = t("Simulasi Pelunasan Cepat", "Accelerated Payoff Simulation")
    val payoffStrategies: String get() = t("Strategi Bebas Utang", "Debt Freedom Strategies")
    val contractsCount: String get() = t("Kontrak", "Contracts")

    // --- Digital Receipts & Reports ---
    val digitalReceiptHeader: String get() = t("BUKTI TRANSAKSI DIGITAL", "DIGITAL TRANSACTION RECEIPT")
    val receiptSubtitle: String get() = t("DOMPETNALAR PERSONAL FINANCE", "DOMPETNALAR PERSONAL FINANCE")
    val receiptNo: String get() = t("No. Bukti", "Receipt No")
    val receiptDate: String get() = t("Tanggal", "Date")
    val receiptType: String get() = t("Jenis", "Type")
    val receiptDesc: String get() = t("Deskripsi", "Description")
    val receiptCategory: String get() = t("Kategori", "Category")
    val receiptFrom: String get() = t("Dari", "From")
    val receiptTo: String get() = t("Ke", "To")
    val receiptSource: String get() = t("Sumber Dana", "Source Account")
    val receiptNote: String get() = t("Catatan", "Notes")
    val receiptTotalExpense: String get() = t("TOTAL KELUAR", "TOTAL EXPENSE")
    val receiptTotalIncome: String get() = t("TOTAL MASUK", "TOTAL INCOME")
    val receiptTotalTransfer: String get() = t("TOTAL TRANSFER", "TOTAL TRANSFER")
    val receiptFooter1: String get() = t("Simpan bukti ini sebagai referensi", "Keep this receipt as a reference")
    val receiptFooter2: String get() = t("pencatatan keuangan Anda.", "for your financial records.")

    // --- Report Exporter PDF & CSV ---
    val pdfReportTitle: String get() = t("Laporan Keuangan DompetNalar", "DompetNalar Financial Report")
    val pdfReportSubtitle: String get() = t("Konsultan Finansial & Manajemen Mutasi Mandiri", "Personal Financial Advisor & Mutation Management")
    val colDate: String get() = t("TANGGAL", "DATE")
    val colTitle: String get() = t("JUDUL / DESKRIPSI", "TITLE / DESCRIPTION")
    val colCategory: String get() = t("KATEGORI", "CATEGORY")
    val colWallet: String get() = t("DOMPET", "WALLET")
    val colAmount: String get() = t("NOMINAL", "AMOUNT")
    val totalIncomeHeader: String get() = t("TOTAL PEMASUKAN", "TOTAL INCOME")
    val totalExpenseHeader: String get() = t("TOTAL PENGELUARAN", "TOTAL EXPENSE")
    val netBalanceHeader: String get() = t("SELISIH (NET WORTH)", "NET BALANCE")

    // --- Split Bill WhatsApp Summary ---
    val splitBillHeader: String get() = t("RINCIAN SPLIT BILL", "SPLIT BILL BREAKDOWN")
    val splitBillDate: String get() = t("Tanggal", "Date")
    val splitBillUnassigned: String get() = t("Item Belum Dibagi", "Unassigned Items")
    val splitBillGrandTotal: String get() = t("Total Tagihan Keseluruhan", "Grand Total Bill")
    val splitBillPayTo: String get() = t("Transfer Pembayaran ke Penyelenggara", "Payment Transfer to Organizer")
    val splitBillFooter: String get() = t("Dihitung & Dibagi Otomatis via DompetNalar AI", "Automatically calculated & split via DompetNalar AI")

    // --- Validation & Error Messages ---
    val errPasswordEmpty: String get() = t("Kata sandi tidak boleh kosong.", "Password cannot be empty.")
    val errPasswordMin6: String get() = t("Kata sandi minimal 6 karakter.", "Password must be at least 6 characters.")
    val errPasswordMismatch: String get() = t("Konfirmasi kata sandi tidak cocok.", "Password confirmation does not match.")
    val errSessionInactive: String get() = t("Sesi login tidak aktif. Silakan masuk akun terlebih dahulu.", "Login session inactive. Please log in first.")
    val errAmountZero: String get() = t("Nominal transaksi harus lebih dari Rp 0.", "Transaction amount must be greater than 0.")
    val errTargetWalletRequired: String get() = t("Dompet tujuan transfer harus dipilih.", "Target wallet must be selected.")
    val errSameWalletTransfer: String get() = t("Dompet sumber dan dompet tujuan transfer tidak boleh sama.", "Source and target wallets cannot be the same.")
    val errSmartMasterRequiredWallet: String get() = t("Akses SmartMaster diperlukan untuk membuat dompet via AI.", "SmartMaster access required to create wallets via AI.")
    val errFailedDeleteWallet: String get() = t("Gagal menghapus dompet.", "Failed to delete wallet.")
    val errFailedCreateWallet: String get() = t("Gagal membuat dompet.", "Failed to create wallet.")

    // --- Dynamic Category & Entity Translation Helpers ---

    /**
     * Menerjemahkan kategori anggaran dan pengeluaran sistem ke Bahasa Inggris secara dinamis
     * saat mode bahasa Inggris aktif, tanpa merusak nilai asli di database Room.
     */
    fun translateCategory(rawCategory: String): String {
        if (!LocaleHelper.isEnglish()) return rawCategory
        val normalized = rawCategory.trim().lowercase()
        return when {
            normalized == "belanja & hobi" || normalized == "shopping & hobbies" -> "Shopping & Hobbies"
            normalized == "hiburan & langganan" || normalized == "entertainment & subscriptions" -> "Entertainment & Subscriptions"
            normalized.contains("makanan") || normalized.contains("food") -> "Food & Drinks"
            normalized.contains("transport") -> "Transportation"
            normalized.contains("sehat") || normalized.contains("health") -> "Health"
            normalized.contains("didik") || normalized.contains("education") -> "Education"
            normalized.contains("gaji") || normalized.contains("salary") -> "Salary & Income"
            normalized.contains("invest") -> "Investment"
            normalized.contains("bonus") -> "Bonus"
            normalized.contains("tagihan") || normalized.contains("bill") || normalized.contains("utilitas") -> "Bills & Utilities"
            normalized.contains("donasi") || normalized.contains("charity") || normalized.contains("amal") -> "Donation & Charity"
            normalized.contains("elektronik") || normalized.contains("gadget") -> "Electronics / Gadgets"
            normalized.contains("kendaraan") || normalized.contains("vehicle") -> "Vehicles (Car/Motorcycle)"
            normalized.contains("properti") || normalized.contains("rumah") || normalized.contains("property") -> "Property / Real Estate"
            normalized.contains("emas") || normalized.contains("gold") -> "Gold & Savings"
            normalized.contains("pemasukan lain") || normalized.contains("other income") -> "Other Income"
            normalized == "lain-lain" || normalized == "lainnya" || normalized == "others" -> "Others"
            else -> rawCategory
        }
    }

    /**
     * Menerjemahkan kategori tagihan PayLater ke Bahasa Inggris untuk UI chips.
     */
    fun translatePayLaterCategory(rawCategory: String): String {
        if (!LocaleHelper.isEnglish()) return rawCategory
        return when (rawCategory.trim()) {
            "Pinjaman Online" -> "Online Loan"
            "Kartu Kredit" -> "Credit Card"
            "Cicilan Kendaraan" -> "Vehicle Installment"
            "Pinjaman Bank" -> "Bank Loan"
            "Lain-lain" -> "Others"
            else -> rawCategory
        }
    }

    /**
     * Menerjemahkan judul strategi pelunasan utang.
     */
    fun translatePayoffStrategyTitle(rawTitle: String): String {
        if (!LocaleHelper.isEnglish()) return rawTitle
        return when {
            rawTitle.contains("Tenggat Terdekat") -> "Earliest Due Date ⏰"
            rawTitle.contains("Bunga Tertinggi") -> "Highest Interest ⚡"
            rawTitle.contains("Nominal Terkecil") -> "Lowest Balance ⛄"
            else -> rawTitle
        }
    }

    /**
     * Menerjemahkan deskripsi strategi pelunasan utang.
     */
    fun translatePayoffStrategyDesc(rawDesc: String): String {
        if (!LocaleHelper.isEnglish()) return rawDesc
        return when {
            rawDesc.contains("jatuh tempo paling dekat") -> "Orders debts by nearest due date to furthest."
            rawDesc.contains("persentase bunga tertinggi") -> "Orders debts by highest interest rate for financial efficiency."
            rawDesc.contains("sisa pokok hutang terkecil") -> "Orders debts by lowest remaining balance for psychological momentum."
            else -> rawDesc
        }
    }
}
