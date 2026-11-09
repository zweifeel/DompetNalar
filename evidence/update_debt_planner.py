path = 'app/src/main/java/com/example/ui/components/DebtPlannerModalDialog.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

replacements = [
    ('Text("Hutang & Pinjaman (${debtList.size})")', 'Text("${com.example.util.AppStrings.t(\\"Hutang & Pinjaman\\", \\"Debts & Loans\\")} (${debtList.size})")'),
    ('Text("Cicilan Berjangka (${installmentPlans.size})")', 'Text("${com.example.util.AppStrings.t(\\"Cicilan Berjangka\\", \\"Term Installments\\")} (${installmentPlans.size})")'),
    ('Text("Kelola Kategori")', 'Text(com.example.util.AppStrings.t("Kelola Kategori", "Manage Categories"))'),
    ('Text("Kategori Pinjaman:")', 'Text(com.example.util.AppStrings.t("Kategori Pinjaman:", "Loan Category:"))'),
    ('Text("Reset Semua")', 'Text(com.example.util.AppStrings.t("Reset Semua", "Reset All"))'),
    ('Text("Hapus Kategori Hutang")', 'Text(com.example.util.AppStrings.t("Hapus Kategori Hutang", "Delete Debt Category"))'),
    ('Text("Pilih kategori yang ingin dihapus seluruh itemnya dari planner:")', 'Text(com.example.util.AppStrings.t("Pilih kategori yang ingin dihapus seluruh itemnya dari planner:", "Select category to delete all its items from planner:"))'),
    ('Text("Tidak ada kategori hutang aktif.")', 'Text(com.example.util.AppStrings.t("Tidak ada kategori hutang aktif.", "No active debt categories."))'),
    ('Text("Hapus Pinjaman?")', 'Text(com.example.util.AppStrings.t("Hapus Pinjaman?", "Delete Loan?"))'),
    ('Text("Memproses Tagihan AI")', 'Text(com.example.util.AppStrings.t("Memproses Tagihan AI", "Processing AI Bill"))'),
    ('Text("Membaca screenshot tagihan...")', 'Text(com.example.util.AppStrings.t("Membaca screenshot tagihan...", "Reading bill screenshot..."))'),
    ('Text("Gagal Scan Tagihan")', 'Text(com.example.util.AppStrings.t("Gagal Scan Tagihan", "Bill Scan Failed"))'),
    ('Text("Pilih Contoh Tagihan")', 'Text(com.example.util.AppStrings.t("Pilih Contoh Tagihan", "Select Sample Bill"))'),
    ('label = { Text("Nama Cicilan (wajib)") }', 'label = { Text(com.example.util.AppStrings.t("Nama Cicilan (wajib)", "Installment Name (Required)")) }'),
    ('placeholder = { Text("Contoh: Cicilan Motor Vario") }', 'placeholder = { Text(com.example.util.AppStrings.t("Contoh: Cicilan Motor Vario", "e.g. Motorcycle Installment")) }'),
    ('label = { Text("Angsuran per Bulan (Rp)") }', 'label = { Text(com.example.util.AppStrings.t("Angsuran per Bulan (Rp)", "Installment per Month (Rp)")) }'),
    ('label = { Text("Tanggal Jatuh Tempo (1-31)") }', 'label = { Text(com.example.util.AppStrings.t("Tanggal Jatuh Tempo (1-31)", "Due Date (1-31)")) }'),
    ('label = { Text("Catatan (opsional)") }', 'label = { Text(com.example.util.AppStrings.t("Catatan (opsional)", "Notes (Optional)")) }'),
    ('Text("Konfirmasi Bayar Angsuran")', 'Text(com.example.util.AppStrings.t("Konfirmasi Bayar Angsuran", "Confirm Installment Payment"))'),
    ('Text("Tanpa potong saldo dompet")', 'Text(com.example.util.AppStrings.t("Tanpa potong saldo dompet", "Without deducting wallet balance"))'),
    ('Text("Konfirmasi Bayar")', 'Text(com.example.util.AppStrings.t("Konfirmasi Bayar", "Confirm Payment"))'),
    ('Text("Hapus Rencana Cicilan?")', 'Text(com.example.util.AppStrings.t("Hapus Rencana Cicilan?", "Delete Installment Plan?"))')
]

for old, new in replacements:
    if old in c:
        c = c.replace(old, new)

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

print('DebtPlannerModalDialog.kt updated successfully.')
