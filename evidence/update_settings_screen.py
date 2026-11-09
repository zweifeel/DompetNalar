path = 'app/src/main/java/com/example/ui/screens/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

replacements = [
    ('Text("Panel Kelola Akun Master Dev"', 'Text(com.example.util.AppStrings.t("Panel Kelola Akun Master Dev", "Master Dev Account Manager")'),
    ('Text("Upgrade ke Smart Master 👑 (Mulai Rp12.400/bln)"', 'Text(com.example.util.AppStrings.t("Upgrade ke Smart Master 👑 (Mulai Rp12.400/bln)", "Upgrade to Smart Master 👑 (From Rp12.400/mo)")'),
    ('Text("Masuk / Daftar Akun Google"', 'Text(com.example.util.AppStrings.t("Masuk / Daftar Akun Google", "Sign In / Register Google Account")'),
    ('Text("Cadangkan"', 'Text(com.example.util.AppStrings.t("Cadangkan", "Backup")'),
    ('Text("Pulihkan Data"', 'Text(com.example.util.AppStrings.t("Pulihkan Data", "Restore Data")'),
    ('Text("Hubungkan ke Google Drive"', 'Text(com.example.util.AppStrings.t("Hubungkan ke Google Drive", "Connect to Google Drive")'),
    ('Text("Aksi Goyang HP (Shake Gesture)"', 'Text(com.example.util.AppStrings.t("Aksi Goyang HP (Shake Gesture)", "Shake Phone Action (Shake Gesture)")'),
    ('Text("Pilih Tema Tampilan"', 'Text(com.example.util.AppStrings.t("Pilih Tema Tampilan", "Select Theme")'),
    ('Text("Warna Aksen Aplikasi"', 'Text(com.example.util.AppStrings.t("Warna Aksen Aplikasi", "App Accent Color")'),
    ('Text("Latar Belakang Galeri"', 'Text(com.example.util.AppStrings.t("Latar Belakang Galeri", "Gallery Background")'),
    ('Text("Pilihan Pencadangan Data"', 'Text(com.example.util.AppStrings.t("Pilihan Pencadangan Data", "Data Backup Options")'),
    ('Text("Cadangan Perangkat (Otomatis)"', 'Text(com.example.util.AppStrings.t("Cadangan Perangkat (Otomatis)", "Device Backup (Automatic)")'),
    ('Text("Cadangan Portabel (Kata Sandi)"', 'Text(com.example.util.AppStrings.t("Cadangan Portabel (Kata Sandi)", "Portable Backup (Password)")'),
    ('Text("Buat Cadangan Portabel"', 'Text(com.example.util.AppStrings.t("Buat Cadangan Portabel", "Create Portable Backup")'),
    ('Text("Kata Sandi Cadangan"', 'Text(com.example.util.AppStrings.t("Kata Sandi Cadangan", "Backup Password")'),
    ('Text("Minimal 6 karakter"', 'Text(com.example.util.AppStrings.t("Minimal 6 karakter", "Minimum 6 characters")'),
    ('Text("Konfirmasi Kata Sandi"', 'Text(com.example.util.AppStrings.t("Konfirmasi Kata Sandi", "Confirm Password")'),
    ('Text("Ketik ulang kata sandi"', 'Text(com.example.util.AppStrings.t("Ketik ulang kata sandi", "Retype password")'),
    ('Text("Pilihan Pemulihan Data"', 'Text(com.example.util.AppStrings.t("Pilihan Pemulihan Data", "Data Restore Options")'),
    ('Text("Pulihkan Cadangan Perangkat"', 'Text(com.example.util.AppStrings.t("Pulihkan Cadangan Perangkat", "Restore Device Backup")'),
    ('Text("Pulihkan Cadangan Portabel (Kata Sandi)"', 'Text(com.example.util.AppStrings.t("Pulihkan Cadangan Portabel (Kata Sandi)", "Restore Portable Backup (Password)")'),
    ('Text("Pulihkan Cadangan Portabel"', 'Text(com.example.util.AppStrings.t("Pulihkan Cadangan Portabel", "Restore Portable Backup")'),
    ('Text("Pilih File Cadangan Lain (.json)"', 'Text(com.example.util.AppStrings.t("Pilih File Cadangan Lain (.json)", "Select Another Backup File (.json)")'),
    ('Text("Masukkan kata sandi"', 'Text(com.example.util.AppStrings.t("Masukkan kata sandi", "Enter password")')
]

for old, new in replacements:
    if old in c:
        c = c.replace(old, new)
        print(f"Replaced: {old[:30]}...")
    else:
        print(f"WARNING: Not found: {old[:30]}")

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

print('SettingsScreen.kt updated successfully.')
