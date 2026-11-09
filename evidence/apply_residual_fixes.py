import os, sys

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

def replace_in_file(filepath, replacements):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    initial = content
    for old, new in replacements:
        if old not in content:
            print(f"WARNING: Target not found in {os.path.basename(filepath)}:\n  '{old[:50]}...'")
        else:
            content = content.replace(old, new)
            print(f"SUCCESS: Replaced in {os.path.basename(filepath)}: '{old[:40]}...'")
            
    if content != initial:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Updated {filepath}")
    else:
        print(f"No changes in {filepath}")

# 1. AddTransactionSheet.kt
replace_in_file(
    r'app\src\main\java\com\example\ui\components\AddTransactionSheet.kt',
    [
        ('text = "Catat Transaksi",', 'text = AppStrings.t("Catat Transaksi", "Record Transaction"),'),
        ('Icon(Icons.Default.Close, contentDescription = "Tutup")', 'Icon(Icons.Default.Close, contentDescription = AppStrings.t("Tutup", "Close"))'),
        ('text = "Nominal Transaksi",', 'text = AppStrings.t("Nominal Transaksi", "Transaction Amount"),'),
        ('text = "Kategori Transaksi:",', 'text = AppStrings.t("Kategori Transaksi:", "Transaction Category:"),'),
    ]
)

# 2. AssetTrackerModalDialog.kt
replace_in_file(
    r'app\src\main\java\com\example\ui\components\AssetTrackerModalDialog.kt',
    [
        ('text = "Daftar Aset Fisik & Estimasi Nilai (${assetList.size}):",', 'text = AppStrings.t("Daftar Aset Fisik & Estimasi Nilai", "Physical Assets & Valuation") + " (${assetList.size}):",'),
        ('text = "Belum ada aset fisik yang dicatat. Tambahkan melalui form di bawah.",', 'text = AppStrings.t("Belum ada aset fisik yang dicatat. Tambahkan melalui form di bawah.", "No physical assets recorded yet. Add one using the form below."),'),
        ('label = { Text("Nama Aset (Misal: Laptop Asus / Honda Vario)") },', 'label = { Text(AppStrings.t("Nama Aset (Misal: Laptop Asus / Honda Vario)", "Asset Name (e.g. Asus Laptop / Honda Vario)")) },'),
        ('Text("Hapus", fontSize = 10.sp, color = Color.White)', 'Text(AppStrings.t("Hapus", "Delete"), fontSize = 10.sp, color = Color.White)'),
        ('Text("Hapus", color = Color.White)\n                }', 'Text(AppStrings.t("Hapus", "Delete"), color = Color.White)\n                }'),
    ]
)

# 3. AuthModalDialog.kt
replace_in_file(
    r'app\src\main\java\com\example\ui\components\AuthModalDialog.kt',
    [
        ('Text(\n                            "Masuk (Login)",', 'Text(\n                            AppStrings.t("Masuk (Login)", "Log In"),'),
        ('Text(\n                            "Daftar Akun",', 'Text(\n                            AppStrings.t("Daftar Akun", "Register"),'),
    ]
)

# 4. BudgetScreen.kt
replace_in_file(
    r'app\src\main\java\com\example\ui\screens\BudgetScreen.kt',
    [
        ('text = "+ Klik untuk Atur Limit Anggaran Bulanan",', 'text = AppStrings.t("+ Klik untuk Atur Limit Anggaran Bulanan", "+ Tap to Set Monthly Budget Limit"),'),
        ('Text("Limit Kategori: $selectedCategoryName", fontWeight = FontWeight.Bold, fontSize = 16.sp)', 'Text(AppStrings.t("Limit Kategori: ", "Category Limit: ") + selectedCategoryName, fontWeight = FontWeight.Bold, fontSize = 16.sp)'),
    ]
)

# 5. ChangePasswordDialog.kt
replace_in_file(
    r'app\src\main\java\com\example\ui\components\ChangePasswordDialog.kt',
    [
        ('text = "Ubah Kata Sandi",', 'text = AppStrings.t("Ubah Kata Sandi", "Change Password"),'),
        ('text = "🔒 Keamanan Kredensial Akun:",', 'text = AppStrings.t("🔒 Keamanan Kredensial Akun:", "🔒 Account Credential Security:"),'),
        ('text = "Masukkan kata sandi saat ini untuk verifikasi kepemilikan, lalu buat kata sandi baru minimal 6 karakter. Kata sandi di-hash menggunakan algoritma PBKDF2.",', 'text = AppStrings.t("Masukkan kata sandi saat ini untuk verifikasi kepemilikan, lalu buat kata sandi baru minimal 6 karakter. Kata sandi di-hash menggunakan algoritma PBKDF2.", "Enter your current password to verify ownership, then create a new password of at least 6 characters. Passwords are hashed using PBKDF2."),'),
    ]
)

# 6. ChangelogBottomSheet.kt
replace_in_file(
    r'app\src\main\java\com\example\ui\components\ChangelogBottomSheet.kt',
    [
        ('text = "Catatan Rilis (Changelog)",', 'text = AppStrings.t("Catatan Rilis (Changelog)", "Release Notes (Changelog)"),'),
        ('text = "DompetNalar v1.0.3 Siap Digunakan",', 'text = AppStrings.t("DompetNalar v1.0.3 Siap Digunakan", "DompetNalar v1.0.3 Ready to Use"),'),
        ('text = "Pembaruan ini mencakup perbaikan AI Date Parsing, Selector Mata Uang Global, Manajemen Hutang & Aset, dan optimasi UI.",', 'text = AppStrings.t("Pembaruan ini mencakup perbaikan AI Date Parsing, Selector Mata Uang Global, Manajemen Hutang & Aset, dan optimasi UI.", "This update includes fixes for AI Date Parsing, Global Currency Selector, Debt & Asset Management, and UI optimizations."),'),
        ('Text("Tutup Catatan Rilis", fontWeight = FontWeight.Bold, fontSize = 13.sp)', 'Text(AppStrings.t("Tutup Catatan Rilis", "Close Release Notes"), fontWeight = FontWeight.Bold, fontSize = 13.sp)'),
    ]
)

# 7. DashboardScreen.kt
replace_in_file(
    r'app\src\main\java\com\example\ui\screens\DashboardScreen.kt',
    [
        ('contentDescription = "Peringatan",', 'contentDescription = AppStrings.t("Peringatan", "Warning"),'),
        ('text = "Peringatan Overbudget Cerdas!",', 'text = AppStrings.t("Peringatan Overbudget Cerdas!", "Smart Overbudget Warning!"),'),
        ('if (overbudgetAlerts.size > 1) " (+${overbudgetAlerts.size - 1} lainnya)" else ""', 'if (overbudgetAlerts.size > 1) (if (LocaleHelper.isEnglish()) " (+${overbudgetAlerts.size - 1} others)" else " (+${overbudgetAlerts.size - 1} lainnya)") else ""'),
    ]
)

# 8. DeleteAccountDialog.kt
replace_in_file(
    r'app\src\main\java\com\example\ui\components\DeleteAccountDialog.kt',
    [
        ('text = "Hapus Akun & Data Permanen",', 'text = AppStrings.t("Hapus Akun & Data Permanen", "Delete Account & Data Permanently"),'),
        ('text = "⚠️ PERINGATAN (Play Store Deletion Policy):",', 'text = AppStrings.t("⚠️ PERINGATAN (Play Store Deletion Policy):", "⚠️ WARNING (Play Store Deletion Policy):"),'),
        ('text = "Sesi Mode Tamu tidak memerlukan kata sandi. Mengonfirmasi tindakan ini akan langsung menghapus seluruh riwayat mutasi, dompet, dan data lokal secara permanen dari perangkat.",', 'text = AppStrings.t("Sesi Mode Tamu tidak memerlukan kata sandi. Mengonfirmasi tindakan ini akan langsung menghapus seluruh riwayat mutasi, dompet, dan data lokal secara permanen dari perangkat.", "Guest mode session does not require a password. Confirming will permanently delete all transaction history, wallets, and local data from this device."),'),
        ('text = "Akun Anda terhubung melalui Google Sign-In (${currentUser.email}). Tidak ada kata sandi lokal yang diperlukan. Mengonfirmasi tindakan ini akan menghapus akun Google Anda dari DompetNalar dan server cloud secara permanen beserta seluruh data keuangan lokal Anda.",', 'text = AppStrings.t("Akun Anda terhubung melalui Google Sign-In (${currentUser.email}). Tidak ada kata sandi lokal yang diperlukan. Mengonfirmasi tindakan ini akan menghapus akun Google Anda dari DompetNalar dan server cloud secara permanen beserta seluruh data keuangan lokal Anda.", "Your account is connected via Google Sign-In (${currentUser.email}). No local password required. Confirming will permanently delete your Google account and all local financial data from DompetNalar."),'),
        ('text = "Konfirmasi Kata Sandi Akun:",', 'text = AppStrings.t("Konfirmasi Kata Sandi Akun:", "Confirm Account Password:"),'),
        ('label = { Text("Masukkan kata sandi akun") },', 'label = { Text(AppStrings.t("Masukkan kata sandi akun", "Enter account password")) },'),
    ]
)

# 9. DuressPinModalDialog.kt
replace_in_file(
    r'app\src\main\java\com\example\ui\components\DuressPinModalDialog.kt',
    [
        ('label = { Text("PIN Utama (Akun Asli)") },', 'label = { Text(AppStrings.t("PIN Utama (Akun Asli)", "Main PIN (Real Account)")) },'),
        ('placeholder = { Text("Masukkan PIN...") },', 'placeholder = { Text(AppStrings.t("Masukkan PIN...", "Enter PIN...")) },'),
    ]
)

# 10. ExportFormatManagementDialog.kt
replace_in_file(
    r'app\src\main\java\com\example\ui\components\ExportFormatManagementDialog.kt',
    [
        ('Text("Kelola Format Ekspor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)', 'Text(AppStrings.t("Kelola Format Ekspor", "Manage Export Formats"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)'),
        ('Text("Atur format bawaan & kustom laporan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)', 'Text(AppStrings.t("Atur format bawaan & kustom laporan", "Configure default & custom report formats"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)'),
        ('Text("Tambah Format Kustom Baru", fontWeight = FontWeight.Bold, fontSize = 13.sp)', 'Text(AppStrings.t("Tambah Format Kustom Baru", "Add New Custom Format"), fontWeight = FontWeight.Bold, fontSize = 13.sp)'),
        ('placeholder = { Text("Contoh: CSV Analisis Akuntansi") },', 'placeholder = { Text(AppStrings.t("Contoh: CSV Analisis Akuntansi", "Example: Accounting Analysis CSV")) },'),
        ('Text("Pilih Kolom Data:", fontWeight = FontWeight.Bold, fontSize = 12.sp)', 'Text(AppStrings.t("Pilih Kolom Data:", "Select Data Columns:"), fontWeight = FontWeight.Bold, fontSize = 12.sp)'),
        ('Text("Batal")\n                    }\n                    Button(', 'Text(AppStrings.t("Batal", "Cancel"))\n                    }\n                    Button('),
        ('Text("Simpan", fontWeight = FontWeight.Bold)\n                }\n            }', 'Text(AppStrings.t("Simpan", "Save"), fontWeight = FontWeight.Bold)\n                }\n            }'),
    ]
)

# 11. GamificationHubDialog.kt
replace_in_file(
    r'app\src\main\java\com\example\ui\components\GamificationHubDialog.kt',
    [
        ('text = "Tantangan 24 jam tanpa transaksi keluar. Dapatkan Streak Multiplier 2x!",', 'text = AppStrings.t("Tantangan 24 jam tanpa transaksi keluar. Dapatkan Streak Multiplier 2x!", "24-hour challenge without spending. Earn a 2x Streak Multiplier!"),'),
    ]
)

# 12. PayLaterBillReviewDialog.kt
replace_in_file(
    r'app\src\main\java\com\example\ui\components\PayLaterBillReviewDialog.kt',
    [
        ('text = "Saya sudah memeriksa dan mengonfirmasi bahwa hari, bulan, dan tahun jatuh tempo di atas sudah benar.",', 'text = AppStrings.t("Saya sudah memeriksa dan mengonfirmasi bahwa hari, bulan, dan tahun jatuh tempo di atas sudah benar.", "I have verified and confirmed that the due day, month, and year above are correct."),'),
        ('text = "Tanggal Jatuh Tempo Kalender (Hari / Bulan / Tahun):",', 'text = AppStrings.t("Tanggal Jatuh Tempo Kalender (Hari / Bulan / Tahun):", "Calendar Due Date (Day / Month / Year):"),'),
        ('text = "Format tersimpan: $formattedIsoDate",', 'text = AppStrings.t("Format tersimpan: ", "Stored format: ") + formattedIsoDate,'),
        ('text = "Kategori Tagihan:",', 'text = AppStrings.t("Kategori Tagihan:", "Bill Category:"),'),
        ('Text("Simpan sbg Rencana Cicilan ($totInstallment x)", fontWeight = FontWeight.Bold)', 'Text(AppStrings.t("Simpan sbg Rencana Cicilan", "Save as Installment Plan") + " ($totInstallment x)", fontWeight = FontWeight.Bold)'),
        ('Text("Batal")\n                }\n                Spacer(modifier = Modifier.width(8.dp))\n                Button(', 'Text(AppStrings.t("Batal", "Cancel"))\n                }\n                Spacer(modifier = Modifier.width(8.dp))\n                Button('),
        ('Text("Simpan Semua (${itemsList.size} Tagihan)", fontWeight = FontWeight.Bold)', 'Text(AppStrings.t("Simpan Semua", "Save All") + " (${itemsList.size} " + AppStrings.t("Tagihan", "Bills") + ")", fontWeight = FontWeight.Bold)'),
    ]
)

# 13. ReceiptGeneratorModalDialog.kt
replace_in_file(
    r'app\src\main\java\com\example\ui\components\ReceiptGeneratorModalDialog.kt',
    [
        ('text = "BUKTI TRANSAKSI DIGITAL",\n                            fontWeight = FontWeight.Bold,', 'text = AppStrings.t("BUKTI TRANSAKSI DIGITAL", "DIGITAL TRANSACTION RECEIPT"),\n                            fontWeight = FontWeight.Bold,'),
        ('text = "BUKTI TRANSAKSI DIGITAL",\n                        fontWeight = FontWeight.ExtraBold,', 'text = AppStrings.t("BUKTI TRANSAKSI DIGITAL", "DIGITAL TRANSACTION RECEIPT"),\n                        fontWeight = FontWeight.ExtraBold,'),
        ('Text(text = "Deskripsi:", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.DarkGray)', 'Text(text = AppStrings.t("Deskripsi:", "Description:"), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.DarkGray)'),
        ('Text(text = "Kategori:", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.DarkGray)', 'Text(text = AppStrings.t("Kategori:", "Category:"), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.DarkGray)'),
        ('Text(text = "Catatan:", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.DarkGray)', 'Text(text = AppStrings.t("Catatan:", "Notes:"), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.DarkGray)'),
        ('text = "Simpan bukti ini sebagai referensi\\npencatatan keuangan pribadi Anda.\\n--- DompetNalar Personal Finance ---",', 'text = AppStrings.t("Simpan bukti ini sebagai referensi\\npencatatan keuangan pribadi Anda.\\n--- DompetNalar Personal Finance ---", "Save this receipt for your\\npersonal finance records.\\n--- DompetNalar Personal Finance ---"),'),
        ('val clip = ClipData.newPlainText("Bukti Transaksi DompetNalar", shareText)', 'val clip = ClipData.newPlainText(AppStrings.t("Bukti Transaksi DompetNalar", "DompetNalar Transaction Receipt"), shareText)'),
        ('Toast.makeText(context, "Teks bukti transaksi berhasil disalin!", Toast.LENGTH_SHORT).show()', 'Toast.makeText(context, AppStrings.t("Teks bukti transaksi berhasil disalin!", "Receipt text copied successfully!"), Toast.LENGTH_SHORT).show()'),
    ]
)

# 14. ReceiptScanScreen.kt
replace_in_file(
    r'app\src\main\java\com\example\ui\screens\ReceiptScanScreen.kt',
    [
        ('label = { Text("Pajak Termasuk (Inklusif)", fontSize = 10.sp) },', 'label = { Text(AppStrings.t("Pajak Termasuk (Inklusif)", "Tax Included (Inclusive)"), fontSize = 10.sp) },'),
        ('label = { Text("Pajak Tambahan (Eksklusif)", fontSize = 10.sp) },', 'label = { Text(AppStrings.t("Pajak Tambahan (Eksklusif)", "Additional Tax (Exclusive)"), fontSize = 10.sp) },'),
    ]
)

# 15. SettingsScreen.kt
replace_in_file(
    r'app\src\main\java\com\example\ui\screens\SettingsScreen.kt',
    [
        ('title = "Mode Samarkan Saldo (Incognito)",\n                        subtitle = "Sembunyikan angka saldo di tempat umum",', 'title = AppStrings.t("Mode Samarkan Saldo (Incognito)", "Hide Balance Mode (Incognito)"),\n                        subtitle = AppStrings.t("Sembunyikan angka saldo di tempat umum", "Hide balance numbers in public spaces"),'),
        ('text = "Silakan mendaftar atau masuk dengan Akun Google resmi untuk menggunakan fitur Backup & Restore Google Drive.",', 'text = AppStrings.t("Silakan mendaftar atau masuk dengan Akun Google resmi untuk menggunakan fitur Backup & Restore Google Drive.", "Please register or sign in with an official Google Account to use Google Drive Backup & Restore."),'),
        ('title = "Ekspor Laporan Keuangan",\n                        subtitle = "PDF formal atau Excel Spreadsheet (.csv)",', 'title = AppStrings.t("Ekspor Laporan Keuangan", "Export Financial Report"),\n                        subtitle = AppStrings.t("PDF formal atau Excel Spreadsheet (.csv)", "Formal PDF or Excel Spreadsheet (.csv)"),'),
        ('title = "Kelola Format Ekspor",\n                        subtitle = "Atur kolom kustom, pemisah CSV, dan format laporan",', 'title = AppStrings.t("Kelola Format Ekspor", "Manage Export Formats"),\n                        subtitle = AppStrings.t("Atur kolom kustom, pemisah CSV, dan format laporan", "Configure custom columns, CSV delimiter, and report format"),'),
        ('subtitle = "Ganti kata sandi akun ${currentUser.username}",', 'subtitle = AppStrings.t("Ganti kata sandi akun ", "Change password for ") + currentUser.username,'),
        ('text = "Pilih aksi pintas saat perangkat digoyangkan secara mantap (> 2.5g):",', 'text = AppStrings.t("Pilih aksi pintas saat perangkat digoyangkan secara mantap (> 2.5g):", "Choose shortcut action when device is shaken firmly (> 2.5g):"),'),
        ('text = "Pilih skema warna utama untuk tombol, highlight, dan indikator tab:",', 'text = AppStrings.t("Pilih skema warna utama untuk tombol, highlight, dan indikator tab:", "Choose primary color scheme for buttons, highlights, and tab indicators:"),'),
        ('text = "Ubah ikon aplikasi di homescreen HP secara dinamis. Streak harian saat ini: $dailyStreak Hari",', 'text = AppStrings.t("Ubah ikon aplikasi di homescreen HP secara dinamis. Streak harian saat ini: ", "Dynamically change app home screen icon. Current daily streak: ") + "$dailyStreak " + AppStrings.t("Hari", "Days"),'),
        ('text = "Pilih metode pencadangan yang sesuai dengan kebutuhan Anda:",', 'text = AppStrings.t("Pilih metode pencadangan yang sesuai dengan kebutuhan Anda:", "Choose the backup method that suits your needs:"),'),
        ('text = "Catatan: Jangan lupakan kata sandi ini. DompetNalar tidak menyimpannya di server demi keamanan privasi Anda.",', 'text = AppStrings.t("Catatan: Jangan lupakan kata sandi ini. DompetNalar tidak menyimpannya di server demi keamanan privasi Anda.", "Note: Do not forget this password. DompetNalar does not store it on servers for your privacy."),'),
        ('text = "Pilih sumber data cadangan yang ingin dipulihkan ke aplikasi ini:",', 'text = AppStrings.t("Pilih sumber data cadangan yang ingin dipulihkan ke aplikasi ini:", "Select the backup data source you want to restore to this app:"),'),
        ('Text(\n                                    "Pilih file cadangan (.json) dan dekripsi menggunakan kata sandi Anda.",', 'Text(\n                                    AppStrings.t("Pilih file cadangan (.json) dan dekripsi menggunakan kata sandi Anda.", "Select the backup file (.json) and decrypt using your password."),'),
        ('text = "Pilih file cadangan dan masukkan kata sandi yang digunakan saat pencadangan:",', 'text = AppStrings.t("Pilih file cadangan dan masukkan kata sandi yang digunakan saat pencadangan:", "Select the backup file and enter the password used during backup:"),'),
    ]
)

# 16. SleepyKoalaStartupScreen.kt
replace_in_file(
    r'app\src\main\java\com\example\ui\components\SleepyKoalaStartupScreen.kt',
    [
        ('text = "Menyiapkan DompetNalar...",', 'text = AppStrings.t("Menyiapkan DompetNalar...", "Setting up DompetNalar..."),'),
    ]
)

# 17. SmartMasterPaywallDialog.kt
replace_in_file(
    r'app\src\main\java\com\example\ui\components\SmartMasterPaywallDialog.kt',
    [
        ('text = "Pilih Paket Berlangganan:",', 'text = AppStrings.t("Pilih Paket Berlangganan:", "Select Subscription Plan:"),'),
        ('text = "👑 Smart Master — Tahunan",', 'text = AppStrings.t("👑 Smart Master — Tahunan", "👑 Smart Master — Annual"),'),
        ('text = "⭐ Smart Master — Bulanan",', 'text = AppStrings.t("⭐ Smart Master — Bulanan", "⭐ Smart Master — Monthly"),'),
    ]
)

# 18. SplitBillScreen.kt
replace_in_file(
    r'app\src\main\java\com\example\ui\screens\SplitBillScreen.kt',
    [
        ('Text("+ Tambah Dompet Baru", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FinSmartPrimary)', 'Text(AppStrings.t("+ Tambah Dompet Baru", "+ Add New Wallet"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FinSmartPrimary)'),
        ('text = "Belum ada riwayat patungan",', 'text = AppStrings.t("Belum ada riwayat patungan", "No split bill history yet"),'),
        ('text = "Gunakan kalkulator untuk membagi bill & simpan ke sini.",', 'text = AppStrings.t("Gunakan kalkulator untuk membagi bill & simpan ke sini.", "Use calculator to split bill & save here."),'),
        ('Text("Tambah")\n                }\n            },\n            dismissButton = {', 'Text(AppStrings.t("Tambah", "Add"))\n                }\n            },\n            dismissButton = {'),
    ]
)

# 19. UpdateAvailableBottomSheet.kt
replace_in_file(
    r'app\src\main\java\com\example\ui\components\UpdateAvailableBottomSheet.kt',
    [
        ('text = "Catatan Rilis / Pembaruan Fitur:",', 'text = AppStrings.t("Catatan Rilis / Pembaruan Fitur:", "Release Notes / Feature Updates:"),'),
    ]
)
