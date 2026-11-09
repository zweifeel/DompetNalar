import os, sys

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

filepath = r'app\src\main\java\com\example\ui\components\DebtPlannerModalDialog.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

replacements = [
    # Locale replacements
    ('val dateFormatter = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))', 'val dateFormatter = SimpleDateFormat("MMMM yyyy", com.example.util.LocaleHelper.getActiveLocale())'),
    ('val currentMonthName = SimpleDateFormat("MMMM yyyy", Locale("id", "ID")).format(currentCal.time)', 'val currentMonthName = SimpleDateFormat("MMMM yyyy", com.example.util.LocaleHelper.getActiveLocale()).format(currentCal.time)'),
    ('val nextMonthName = SimpleDateFormat("MMMM yyyy", Locale("id", "ID")).format(nextCal.time)', 'val nextMonthName = SimpleDateFormat("MMMM yyyy", com.example.util.LocaleHelper.getActiveLocale()).format(nextCal.time)'),
    ('val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(plan.lastPaymentDate))', 'val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", com.example.util.LocaleHelper.getActiveLocale()).format(Date(plan.lastPaymentDate))'),
    ('val txDate = SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID")).format(Date(tx.timestamp))', 'val txDate = SimpleDateFormat("dd/MM/yyyy", com.example.util.LocaleHelper.getActiveLocale()).format(Date(tx.timestamp))'),

    # UI text replacements
    ('text = { Text("Hutang & Pinjaman (${debtList.size})"', 'text = { Text(AppStrings.t("Hutang & Pinjaman", "Debts & Loans") + " (${debtList.size})"'),
    ('text = "Kewajiban Pembayaran Bulan Berjalan",', 'text = AppStrings.t("Kewajiban Pembayaran Bulan Berjalan", "Current Month Payment Obligations"),'),
    ('text = "Batas Anggaran Cicilan / Tagihan:",', 'text = AppStrings.t("Batas Anggaran Cicilan / Tagihan:", "Installment / Bill Budget Limit:"),'),
    ('"Belum diatur"', 'AppStrings.t("Belum diatur", "Not set")'),
    ('text = "Status Terhadap Anggaran:",', 'text = AppStrings.t("Status Terhadap Anggaran:", "Budget Status:"),'),
    ('text = "Sesuaikan di menu Anggaran",', 'text = AppStrings.t("Sesuaikan di menu Anggaran", "Adjust in Budget menu"),'),
    ('text = "Jadwal Pembayaran Bulanan",', 'text = AppStrings.t("Jadwal Pembayaran Bulanan", "Monthly Payment Schedule"),'),
    ('text = "Belum ada jadwal pembayaran bulan ini.",', 'text = AppStrings.t("Belum ada jadwal pembayaran bulan ini.", "No payments scheduled for this month."),'),
    ('Text(text = "Sumber: Catatan Manual",', 'Text(text = AppStrings.t("Sumber: Catatan Manual", "Source: Manual Entry"),'),
    ('text = "Daftar & Prioritas Pelunasan AI (${debtList.size}):",', 'text = AppStrings.t("Daftar & Prioritas Pelunasan AI", "AI Repayment Priorities") + " (${debtList.size}):",'),
    ('text = "Belum ada catatan pinjaman/hutang. Gunakan form di bawah untuk menambah.",', 'text = AppStrings.t("Belum ada catatan pinjaman/hutang. Gunakan form di bawah untuk menambah.", "No debt/loan records yet. Use the form below to add one."),'),
    ('text = "Kategori Pinjaman:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant', 'text = AppStrings.t("Kategori Pinjaman:", "Loan Category:"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant'),
    ('text = "Total Kewajiban Angsuran Bulanan",', 'text = AppStrings.t("Total Kewajiban Angsuran Bulanan", "Total Monthly Installment Obligation"),'),
    ('text = "Belum Ada Rencana Cicilan Berjangka",', 'text = AppStrings.t("Belum Ada Rencana Cicilan Berjangka", "No Installment Plans Yet"),'),
    ('text = "Catat cicilan motor, mobil, barang elektronik, atau pinjaman dengan tenor tetap agar progres pembayaran dan tanggal jatuh tempo bulanan terpantau rapi tanpa membuat tagihan manual tiap bulan.",', 'text = AppStrings.t("Catat cicilan motor, mobil, barang elektronik, atau pinjaman dengan tenor tetap agar progres pembayaran dan tanggal jatuh tempo bulanan terpantau rapi tanpa membuat tagihan manual tiap bulan.", "Record vehicle, electronics, or fixed-term loans to track repayment progress and monthly due dates automatically."),'),
    ('text = "Pembayaran Terakhir: $dateStr",', 'text = AppStrings.t("Pembayaran Terakhir: ", "Last Payment: ") + dateStr,'),
    ('text = "Histori Transaksi Terkait (${linkedPayments.size}):",', 'text = AppStrings.t("Histori Transaksi Terkait", "Related Transaction History") + " (${linkedPayments.size}):",'),
    ('text = "Bayar Angsuran ke-${plan.currentPeriod}",', 'text = AppStrings.t("Bayar Angsuran ke-", "Pay Installment #") + "${plan.currentPeriod}",'),
    ('Text("Apakah Anda yakin ingin menghapus seluruh catatan pinjaman/hutang dan rencana cicilan ($totalAffectedCount item: ${debtList.size} pinjaman, ${activeInstallmentPlans.size} rencana cicilan)?\\n\\nTransaksi buku kas (wallet) dan riwayat pengeluaran umum Anda akan tetap aman. Tindakan ini tidak dapat dibatalkan.")', 'Text(AppStrings.t("Apakah Anda yakin ingin menghapus seluruh catatan pinjaman/hutang dan rencana cicilan ($totalAffectedCount item)?\\n\\nTransaksi dompet dan riwayat pengeluaran umum Anda akan tetap aman. Tindakan ini tidak dapat dibatalkan.", "Are you sure you want to delete all loan/debt records and installment plans ($totalAffectedCount items)?\\n\\nYour wallets and general expense records will remain safe. This action cannot be undone."))'),
    ('Text("Hapus", fontSize = 10.sp, color = Color.White)', 'Text(AppStrings.t("Hapus", "Delete"), fontSize = 10.sp, color = Color.White)'),
    ('Text("Hapus", color = Color.White)\n                }\n            },\n            dismissButton = {', 'Text(AppStrings.t("Hapus", "Delete"), color = Color.White)\n                }\n            },\n            dismissButton = {'),
    ('text = "Pilih contoh tagihan untuk mencoba fitur tanpa screenshot fisik:",', 'text = AppStrings.t("Pilih contoh tagihan untuk mencoba fitur tanpa screenshot fisik:", "Select sample bill to test feature without physical screenshot:"),'),
    ('label = { Text("Sudah Bayar") },', 'label = { Text(AppStrings.t("Sudah Bayar", "Paid")) },'),
    ('text = "Pembayaran Angsuran ke-${plan.currentPeriod} dari ${plan.totalTenor}",', 'text = AppStrings.t("Pembayaran Angsuran ke-", "Payment of Installment #") + "${plan.currentPeriod} " + AppStrings.t("dari", "of") + " ${plan.totalTenor}",'),
    ('text = "Nominal: ${com.example.util.CurrencyFormatter.formatRupiah(plan.installmentAmount)}",', 'text = AppStrings.t("Nominal: ", "Amount: ") + com.example.util.CurrencyFormatter.formatRupiah(plan.installmentAmount),'),
    ('text = "Jatuh Tempo: ${plan.nextDueDate}",', 'text = AppStrings.t("Jatuh Tempo: ", "Due Date: ") + plan.nextDueDate,'),
    ('text = "Pilih Dompet Sumber (Opsional):",', 'text = AppStrings.t("Pilih Dompet Sumber (Opsional):", "Select Source Wallet (Optional):"),'),
    ('Text("Apakah Anda yakin ingin menghapus rencana cicilan \'${plan.name}\'? Catatan progres (${plan.paidCount}/${plan.totalTenor}) akan dihapus.")', 'Text(AppStrings.t("Apakah Anda yakin ingin menghapus rencana cicilan", "Are you sure you want to delete installment plan") + " \'${plan.name}\'? " + AppStrings.t("Catatan progres", "Progress records") + " (${plan.paidCount}/${plan.totalTenor}) " + AppStrings.t("akan dihapus.", "will be deleted."))'),
]

initial = content
for old, new in replacements:
    if old not in content:
        print(f"WARNING: Target not found: '{old[:50]}...'")
    else:
        content = content.replace(old, new)
        print(f"SUCCESS: Replaced: '{old[:40]}...'")

if content != initial:
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)
    print("Updated DebtPlannerModalDialog.kt")
else:
    print("No changes in DebtPlannerModalDialog.kt")
