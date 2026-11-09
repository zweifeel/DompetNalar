path = 'app/src/main/java/com/example/ui/components/AuthModalDialog.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

replacements = [
    ('label = { Text("Email atau Username") }', 'label = { Text(com.example.util.AppStrings.t("Email atau Username", "Email or Username")) }'),
    ('placeholder = { Text("misal: user123 atau nama@email.com") }', 'placeholder = { Text(com.example.util.AppStrings.t("misal: user123 atau nama@email.com", "e.g. user123 or name@email.com")) }'),
    ('label = { Text("Kata Sandi") }', 'label = { Text(com.example.util.AppStrings.t("Kata Sandi", "Password")) }'),
    ('Text("Masuk Sekarang", fontWeight = FontWeight.Bold, fontSize = 15.sp)', 'Text(com.example.util.AppStrings.t("Masuk Sekarang", "Sign In Now"), fontWeight = FontWeight.Bold, fontSize = 15.sp)'),
    ('label = { Text("Nama Lengkap / Panggilan") }', 'label = { Text(com.example.util.AppStrings.t("Nama Lengkap / Panggilan", "Full Name / Nickname")) }'),
    ('placeholder = { Text("misal: Nama Lengkap") }', 'placeholder = { Text(com.example.util.AppStrings.t("misal: Nama Lengkap", "e.g. Full Name")) }'),
    ('label = { Text("Alamat Email") }', 'label = { Text(com.example.util.AppStrings.t("Alamat Email", "Email Address")) }'),
    ('label = { Text("Buat Kata Sandi") }', 'label = { Text(com.example.util.AppStrings.t("Buat Kata Sandi", "Create Password")) }'),
    ('Text("Kirim Kode OTP Verifikasi", fontWeight = FontWeight.Bold, fontSize = 14.sp)', 'Text(com.example.util.AppStrings.t("Kirim Kode OTP Verifikasi", "Send Verification OTP Code"), fontWeight = FontWeight.Bold, fontSize = 14.sp)'),
    ('label = { Text("Masukkan 6-Digit Kode OTP") }', 'label = { Text(com.example.util.AppStrings.t("Masukkan 6-Digit Kode OTP", "Enter 6-Digit OTP Code")) }'),
    ('Text("Verifikasi & Aktifkan Akun", fontWeight = FontWeight.Bold, fontSize = 14.sp)', 'Text(com.example.util.AppStrings.t("Verifikasi & Aktifkan Akun", "Verify & Activate Account"), fontWeight = FontWeight.Bold, fontSize = 14.sp)'),
    ('Text("← Ubah Email / Data", fontSize = 12.sp)', 'Text(com.example.util.AppStrings.t("← Ubah Email / Data", "← Change Email / Data"), fontSize = 12.sp)'),
    ('Text("Menghubungkan Akun Google..."', 'Text(com.example.util.AppStrings.t("Menghubungkan Akun Google...", "Connecting Google Account...")'),
    ('Text("Masuk dengan Akun Google"', 'Text(com.example.util.AppStrings.t("Masuk dengan Akun Google", "Sign In with Google Account")')
]

for old, new in replacements:
    if old in c:
        c = c.replace(old, new)

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

print('AuthModalDialog.kt updated successfully.')
