# 1. ExportFormatManagementDialog.kt
path1 = 'app/src/main/java/com/example/ui/components/ExportFormatManagementDialog.kt'
with open(path1, 'r', encoding='utf-8') as f:
    c1 = f.read()
c1 = c1.replace('Text("Kelola Format Ekspor")', 'Text(com.example.util.AppStrings.t("Kelola Format Ekspor", "Manage Export Formats"))')
c1 = c1.replace('Text("Tambah Format Kustom Baru")', 'Text(com.example.util.AppStrings.t("Tambah Format Kustom Baru", "Add New Custom Format"))')
c1 = c1.replace('Text("Pilih Kolom Data:")', 'Text(com.example.util.AppStrings.t("Pilih Kolom Data:", "Select Data Columns:"))')
with open(path1, 'w', encoding='utf-8') as f:
    f.write(c1)
print('ExportFormatManagementDialog.kt updated.')

# 2. ReceiptGeneratorModalDialog.kt
path2 = 'app/src/main/java/com/example/ui/components/ReceiptGeneratorModalDialog.kt'
with open(path2, 'r', encoding='utf-8') as f:
    c2 = f.read()
c2 = c2.replace('Text("No. Bukti:")', 'Text(com.example.util.AppStrings.t("No. Bukti:", "Receipt No:"))')
c2 = c2.replace('Text("Tanggal:")', 'Text(com.example.util.AppStrings.t("Tanggal:", "Date:"))')
c2 = c2.replace('Text("Jenis:")', 'Text(com.example.util.AppStrings.t("Jenis:", "Type:"))')
c2 = c2.replace('Text("Deskripsi:")', 'Text(com.example.util.AppStrings.t("Deskripsi:", "Description:"))')
c2 = c2.replace('Text("Kategori:")', 'Text(com.example.util.AppStrings.t("Kategori:", "Category:"))')
c2 = c2.replace('Text("Dari:")', 'Text(com.example.util.AppStrings.t("Dari:", "From:"))')
c2 = c2.replace('Text("Ke:")', 'Text(com.example.util.AppStrings.t("Ke:", "To:"))')
c2 = c2.replace('Text("Sumber Dana:")', 'Text(com.example.util.AppStrings.t("Sumber Dana:", "Source:"))')
c2 = c2.replace('Text("Catatan:")', 'Text(com.example.util.AppStrings.t("Catatan:", "Notes:"))')
c2 = c2.replace('Text("Bukti Transaksi DompetNalar")', 'Text(com.example.util.AppStrings.t("Bukti Transaksi DompetNalar", "DompetNalar Transaction Receipt"))')
c2 = c2.replace('Text("Salin Teks")', 'Text(com.example.util.AppStrings.t("Salin Teks", "Copy Text"))')
c2 = c2.replace('Text("Bagikan")', 'Text(com.example.util.AppStrings.t("Bagikan", "Share"))')
with open(path2, 'w', encoding='utf-8') as f:
    f.write(c2)
print('ReceiptGeneratorModalDialog.kt updated.')

# 3. SplitBillScreen.kt
path3 = 'app/src/main/java/com/example/ui/screens/SplitBillScreen.kt'
with open(path3, 'r', encoding='utf-8') as f:
    c3 = f.read()
c3 = c3.replace('Text("+ Tambah Dompet Baru")', 'Text("+ " + com.example.util.AppStrings.t("Tambah Dompet Baru", "Add New Wallet"))')
c3 = c3.replace('Text("Bagikan")', 'Text(com.example.util.AppStrings.t("Bagikan", "Share"))')
c3 = c3.replace('Text("Salin")', 'Text(com.example.util.AppStrings.t("Salin", "Copy"))')
c3 = c3.replace('placeholder = { Text("Contoh: Rian, Dita, Maya, dll.") }', 'placeholder = { Text(com.example.util.AppStrings.t("Contoh: Rian, Dita, Maya, dll.", "e.g. John, Alice, Bob, etc.")) }')
c3 = c3.replace('Text("Tambah", fontSize = 12.sp, fontWeight = FontWeight.Bold)', 'Text(com.example.util.AppStrings.add, fontSize = 12.sp, fontWeight = FontWeight.Bold)')
with open(path3, 'w', encoding='utf-8') as f:
    f.write(c3)
print('SplitBillScreen.kt updated.')

# 4. DevFeedbackViewerScreen.kt
path4 = 'app/src/main/java/com/example/ui/screens/DevFeedbackViewerScreen.kt'
with open(path4, 'r', encoding='utf-8') as f:
    c4 = f.read()
c4 = c4.replace('Text("Akses Ditolak")', 'Text(com.example.util.AppStrings.t("Akses Ditolak", "Access Denied"))')
c4 = c4.replace('Text("Akses Terbatas: Master Developer")', 'Text(com.example.util.AppStrings.t("Akses Terbatas: Master Developer", "Restricted Access: Master Developer"))')
c4 = c4.replace('Text("Kembali ke Pengaturan")', 'Text(com.example.util.AppStrings.t("Kembali ke Pengaturan", "Back to Settings"))')
c4 = c4.replace('Text("Menghubungkan Akun Google...")', 'Text(com.example.util.AppStrings.t("Menghubungkan Akun Google...", "Connecting Google Account..."))')
c4 = c4.replace('Text("Hubungkan Akun Google Developer")', 'Text(com.example.util.AppStrings.t("Hubungkan Akun Google Developer", "Connect Developer Google Account"))')
c4 = c4.replace('Text("Sinkronkan")', 'Text(com.example.util.AppStrings.t("Sinkronkan", "Sync"))')
c4 = c4.replace('Text("Semua (${combinedFeedbacks.size})")', 'Text("${com.example.util.AppStrings.t(\\"Semua\\", \\"All\\")} (${combinedFeedbacks.size})")')
with open(path4, 'w', encoding='utf-8') as f:
    f.write(c4)
print('DevFeedbackViewerScreen.kt updated.')

# 5. PayLaterBillReviewDialog.kt
path5 = 'app/src/main/java/com/example/ui/components/PayLaterBillReviewDialog.kt'
with open(path5, 'r', encoding='utf-8') as f:
    c5 = f.read()
c5 = c5.replace('Text("Sebelumnya")', 'Text(com.example.util.AppStrings.t("Sebelumnya", "Previous"))')
c5 = c5.replace('Text("Selanjutnya")', 'Text(com.example.util.AppStrings.t("Selanjutnya", "Next"))')
c5 = c5.replace('label = { Text("Penyedia Layanan / Platform *") }', 'label = { Text(com.example.util.AppStrings.t("Penyedia Layanan / Platform *", "Service Provider / Platform *")) }')
c5 = c5.replace('placeholder = { Text("misal: GoPay Pinjam, DANA Cicil") }', 'placeholder = { Text(com.example.util.AppStrings.t("misal: GoPay Pinjam, DANA Cicil", "e.g. Loan Provider, PayLater")) }')
c5 = c5.replace('label = { Text("Nama Produk / Kontrak") }', 'label = { Text(com.example.util.AppStrings.t("Nama Produk / Kontrak", "Product / Contract Name")) }')
c5 = c5.replace('placeholder = { Text("misal: Pinjaman Tunai #1, Cicilan 4x") }', 'placeholder = { Text(com.example.util.AppStrings.t("misal: Pinjaman Tunai #1, Cicilan 4x", "e.g. Cash Loan #1, 4x Installments")) }')
c5 = c5.replace('label = { Text("Cicilan Ke") }', 'label = { Text(com.example.util.AppStrings.t("Cicilan Ke", "Installment No.")) }')
c5 = c5.replace('label = { Text("Total Tenor") }', 'label = { Text(com.example.util.AppStrings.t("Total Tenor", "Total Tenor")) }')
c5 = c5.replace('label = { Text("Tgl (1-31)") }', 'label = { Text(com.example.util.AppStrings.t("Tgl (1-31)", "Day (1-31)")) }')
c5 = c5.replace('label = { Text("Bln (1-12)") }', 'label = { Text(com.example.util.AppStrings.t("Bln (1-12)", "Month (1-12)")) }')
c5 = c5.replace('label = { Text("Tahun") }', 'label = { Text(com.example.util.AppStrings.t("Tahun", "Year")) }')
c5 = c5.replace('Text("Simpan sbg Rencana Cicilan ($totInstallment x)")', 'Text("${com.example.util.AppStrings.t(\\"Simpan sbg Rencana Cicilan\\", \\"Save as Installment Plan\\")} ($totInstallment x)")')
c5 = c5.replace('Text("Simpan Semua (${itemsList.size} Tagihan)")', 'Text("${com.example.util.AppStrings.t(\\"Simpan Semua\\", \\"Save All\\")} (${itemsList.size} ${com.example.util.AppStrings.t(\\"Tagihan\\", \\"Bills\\")})")')
with open(path5, 'w', encoding='utf-8') as f:
    f.write(c5)
print('PayLaterBillReviewDialog.kt updated.')

# 6. ChangePasswordDialog.kt
path6 = 'app/src/main/java/com/example/ui/components/ChangePasswordDialog.kt'
with open(path6, 'r', encoding='utf-8') as f:
    c6 = f.read()
c6 = c6.replace('label = { Text("Kata Sandi Saat Ini") }', 'label = { Text(com.example.util.AppStrings.t("Kata Sandi Saat Ini", "Current Password")) }')
c6 = c6.replace('placeholder = { Text("Masukkan kata sandi lama") }', 'placeholder = { Text(com.example.util.AppStrings.t("Masukkan kata sandi lama", "Enter current password")) }')
c6 = c6.replace('label = { Text("Kata Sandi Baru") }', 'label = { Text(com.example.util.AppStrings.t("Kata Sandi Baru", "New Password")) }')
c6 = c6.replace('placeholder = { Text("Minimal 6 karakter") }', 'placeholder = { Text(com.example.util.AppStrings.t("Minimal 6 karakter", "Minimum 6 characters")) }')
c6 = c6.replace('label = { Text("Konfirmasi Kata Sandi Baru") }', 'label = { Text(com.example.util.AppStrings.t("Konfirmasi Kata Sandi Baru", "Confirm New Password")) }')
c6 = c6.replace('placeholder = { Text("Ulangi kata sandi baru") }', 'placeholder = { Text(com.example.util.AppStrings.t("Ulangi kata sandi baru", "Repeat new password")) }')
c6 = c6.replace('Text("Simpan Kata Sandi", fontWeight = FontWeight.Bold)', 'Text(com.example.util.AppStrings.t("Simpan Kata Sandi", "Save Password"), fontWeight = FontWeight.Bold)')
with open(path6, 'w', encoding='utf-8') as f:
    f.write(c6)
print('ChangePasswordDialog.kt updated.')
