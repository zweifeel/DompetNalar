path = 'app/src/main/java/com/example/ui/components/AssetTrackerModalDialog.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

replacements = [
    ('Text("Estimasi Total Kekayaan Bersih (Net Worth)"', 'Text(com.example.util.AppStrings.t("Estimasi Total Kekayaan Bersih (Net Worth)", "Estimated Total Net Worth")'),
    ('Text("Saldo Kas"', 'Text(com.example.util.AppStrings.t("Saldo Kas", "Cash Balance")'),
    ('Text("Aset Fisik"', 'Text(com.example.util.AppStrings.t("Aset Fisik", "Physical Assets")'),
    ('Text("Beban Hutang"', 'Text(com.example.util.AppStrings.t("Beban Hutang", "Debt Liabilities")'),
    ('Text("Kelola Kategori"', 'Text(com.example.util.AppStrings.t("Kelola Kategori", "Manage Categories")'),
    ('placeholder = { Text("Nama Aset (Misal: Laptop Asus / Honda Vario)") }', 'placeholder = { Text(com.example.util.AppStrings.t("Nama Aset (Misal: Laptop Asus / Honda Vario)", "Asset Name (e.g. Laptop / Vehicle)")) }'),
    ('label = { Text("Harga Beli (Rp)") }', 'label = { Text(com.example.util.AppStrings.t("Harga Beli (Rp)", "Purchase Price (Rp)")) }'),
    ('label = { Text("Tahun Beli") }', 'label = { Text(com.example.util.AppStrings.t("Tahun Beli", "Purchase Year")) }'),
    ('Text("Batal Edit")', 'Text(com.example.util.AppStrings.t("Batal Edit", "Cancel Edit"))'),
    ('Text("Reset Semua Aset?")', 'Text(com.example.util.AppStrings.t("Reset Semua Aset?", "Reset All Assets?"))'),
    ('Text("Apakah Anda yakin ingin menghapus seluruh catatan aset fisik (Fresh Start)? Tindakan ini tidak dapat dibatalkan.")', 'Text(com.example.util.AppStrings.t("Apakah Anda yakin ingin menghapus seluruh catatan aset fisik (Fresh Start)? Tindakan ini tidak dapat dibatalkan.", "Are you sure you want to delete all physical asset records (Fresh Start)? This action cannot be undone."))'),
    ('Text("Reset Semua")', 'Text(com.example.util.AppStrings.t("Reset Semua", "Reset All"))'),
    ('Text("Hapus Kategori Aset")', 'Text(com.example.util.AppStrings.t("Hapus Kategori Aset", "Delete Asset Category"))'),
    ('Text("Pilih kategori yang ingin dihapus seluruh aset fisiknya:")', 'Text(com.example.util.AppStrings.t("Pilih kategori yang ingin dihapus seluruh aset fisiknya:", "Select category to delete all its physical assets:"))'),
    ('Text("Tidak ada kategori aset aktif.")', 'Text(com.example.util.AppStrings.t("Tidak ada kategori aset aktif.", "No active asset categories."))'),
    ('Text("Hapus Aset?")', 'Text(com.example.util.AppStrings.t("Hapus Aset?", "Delete Asset?"))')
]

for old, new in replacements:
    if old in c:
        c = c.replace(old, new)

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

print('AssetTrackerModalDialog.kt updated successfully.')
