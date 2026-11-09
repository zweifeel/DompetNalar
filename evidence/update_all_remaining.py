import os, re

def update_file(path, pairs):
    with open(path, 'r', encoding='utf-8') as f:
        c = f.read()
    orig = c
    for old, new in pairs:
        c = c.replace(old, new)
    if c != orig:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(c)
        print(f"Updated {os.path.basename(path)}")
    else:
        print(f"No changes in {os.path.basename(path)}")

# AssetTrackerModalDialog.kt
update_file('app/src/main/java/com/example/ui/components/AssetTrackerModalDialog.kt', [
    ('Text("Reset Semua Aset?",', 'Text(com.example.util.AppStrings.t("Reset Semua Aset?", "Reset All Assets?"),'),
    ('Text("Reset Semua")', 'Text(com.example.util.AppStrings.t("Reset Semua", "Reset All"))'),
    ('Text("Batal")', 'Text(com.example.util.AppStrings.cancel)'),
    ('Text("Hapus Kategori Aset",', 'Text(com.example.util.AppStrings.t("Hapus Kategori Aset", "Delete Asset Category"),'),
    ('Text("Tidak ada kategori aset aktif.",', 'Text(com.example.util.AppStrings.t("Tidak ada kategori aset aktif.", "No active asset categories."),'),
    ('Text("Hapus")', 'Text(com.example.util.AppStrings.delete)'),
    ('Text("Hapus Aset?",', 'Text(com.example.util.AppStrings.t("Hapus Aset?", "Delete Asset?"),'),
    ('Text("Hapus aset \'$', 'Text(com.example.util.AppStrings.t("Hapus aset", "Delete asset") + " \'$')
])

# ChangePasswordDialog.kt
update_file('app/src/main/java/com/example/ui/components/ChangePasswordDialog.kt', [
    ('Text("Simpan Kata Sandi",', 'Text(com.example.util.AppStrings.t("Simpan Kata Sandi", "Save Password"),'),
    ('Text("Batal")', 'Text(com.example.util.AppStrings.cancel)')
])

# ChangelogBottomSheet.kt
update_file('app/src/main/java/com/example/ui/components/ChangelogBottomSheet.kt', [
    ('Text("Tutup Catatan Rilis")', 'Text(com.example.util.AppStrings.t("Tutup Catatan Rilis", "Close Release Notes"))')
])

# DeleteAccountDialog.kt
update_file('app/src/main/java/com/example/ui/components/DeleteAccountDialog.kt', [
    ('Text("Hapus Permanen"', 'Text(com.example.util.AppStrings.t("Hapus Permanen", "Delete Permanently")'),
    ('Text("Batal")', 'Text(com.example.util.AppStrings.cancel)')
])

# DuressPinModalDialog.kt
update_file('app/src/main/java/com/example/ui/components/DuressPinModalDialog.kt', [
    ('Text("Simpan Pengaturan PIN"', 'Text(com.example.util.AppStrings.t("Simpan Pengaturan PIN", "Save PIN Settings")')
])

# InitialMasterSetupDialog.kt
update_file('app/src/main/java/com/example/ui/components/InitialMasterSetupDialog.kt', [
    ('label = { Text("Konfirmasi Kata Sandi") }', 'label = { Text(com.example.util.AppStrings.t("Konfirmasi Kata Sandi", "Confirm Password")) }'),
    ('Text("Simpan & Masuk"', 'Text(com.example.util.AppStrings.t("Simpan & Masuk", "Save & Sign In")'),
    ('Text("Batal")', 'Text(com.example.util.AppStrings.cancel)')
])

# MasterDevPanelSheet.kt
update_file('app/src/main/java/com/example/ui/components/MasterDevPanelSheet.kt', [
    ('Text("Semua")', 'Text(com.example.util.AppStrings.t("Semua", "All"))'),
    ('Text("Pilih Durasi Smart Master")', 'Text(com.example.util.AppStrings.t("Pilih Durasi Smart Master", "Select Smart Master Duration"))'),
    ('Text("Lanjutkan ke Konfirmasi"', 'Text(com.example.util.AppStrings.t("Lanjutkan ke Konfirmasi", "Proceed to Confirmation")'),
    ('Text("Konfirmasi Pemberian Membership"', 'Text(com.example.util.AppStrings.t("Konfirmasi Pemberian Membership", "Confirm Membership Grant")'),
    ('Text("Konfirmasi & Simpan"', 'Text(com.example.util.AppStrings.t("Konfirmasi & Simpan", "Confirm & Save")'),
    ('Text("Konfirmasi Cabut Membership"', 'Text(com.example.util.AppStrings.t("Konfirmasi Cabut Membership", "Confirm Membership Revocation")'),
    ('Text("Batal")', 'Text(com.example.util.AppStrings.cancel)')
])

# SmartMasterPaywallDialog.kt
update_file('app/src/main/java/com/example/ui/components/SmartMasterPaywallDialog.kt', [
    ('Text("Memproses Transaksi..."', 'Text(com.example.util.AppStrings.t("Memproses Transaksi...", "Processing Transaction...")')
])

# DebtPlannerModalDialog.kt
update_file('app/src/main/java/com/example/ui/components/DebtPlannerModalDialog.kt', [
    ('Text("Cicilan Berjangka (${installmentPlans.size})",', 'Text("${com.example.util.AppStrings.t(\\"Cicilan Berjangka\\", \\"Term Installments\\")} (${installmentPlans.size})",'),
    ('Text("Kelola Kategori",', 'Text(com.example.util.AppStrings.t("Kelola Kategori", "Manage Categories"),'),
    ('Text("Kategori Pinjaman:",', 'Text(com.example.util.AppStrings.t("Kategori Pinjaman:", "Loan Category:"),'),
    ('Text("Reset Semua",', 'Text(com.example.util.AppStrings.t("Reset Semua", "Reset All"),'),
    ('Text("Batal")', 'Text(com.example.util.AppStrings.cancel)'),
    ('Text("Hapus Kategori Hutang",', 'Text(com.example.util.AppStrings.t("Hapus Kategori Hutang", "Delete Debt Category"),'),
    ('Text("Tidak ada kategori hutang aktif.",', 'Text(com.example.util.AppStrings.t("Tidak ada kategori hutang aktif.", "No active debt categories."),'),
    ('Text("Hapus")', 'Text(com.example.util.AppStrings.delete)'),
    ('Text("Hapus Pinjaman?",', 'Text(com.example.util.AppStrings.t("Hapus Pinjaman?", "Delete Loan?"),'),
    ('Text("Hapus pinjaman \'$', 'Text(com.example.util.AppStrings.t("Hapus pinjaman", "Delete loan") + " \'$'),
    ('Text("Memproses Tagihan AI",', 'Text(com.example.util.AppStrings.t("Memproses Tagihan AI", "Processing AI Bill"),'),
    ('Text("Membaca screenshot tagihan...",', 'Text(com.example.util.AppStrings.t("Membaca screenshot tagihan...", "Reading bill screenshot..."),'),
    ('Text("Gagal Scan Tagihan",', 'Text(com.example.util.AppStrings.t("Gagal Scan Tagihan", "Bill Scan Failed"),'),
    ('Text("Pilih Contoh Tagihan",', 'Text(com.example.util.AppStrings.t("Pilih Contoh Tagihan", "Select Sample Bill"),'),
    ('Text("Konfirmasi Bayar Angsuran",', 'Text(com.example.util.AppStrings.t("Konfirmasi Bayar Angsuran", "Confirm Installment Payment"),'),
    ('Text("Tanpa potong saldo dompet",', 'Text(com.example.util.AppStrings.t("Tanpa potong saldo dompet", "Without deducting wallet balance"),'),
    ('Text("Hapus Rencana Cicilan?",', 'Text(com.example.util.AppStrings.t("Hapus Rencana Cicilan?", "Delete Installment Plan?"),')
])
