# Plan: Eksekusi Fase 1 & Penyelesaian Laporan Bug UI / Onboarding

**Dokumen Rujukan**: `ARCHITECTURE_MAP.md`, `CURRENT_CHECKPOINT.md`, `ROADMAP.md`  
**Target Utama**: Fase 1 (Crash Bahasa & Sistem HP) + Penanganan Laporan Bug Baru (Split Bill Vertikal, Badge Pengaturan, 0 Dompet Awal, & Onboarding)  
**Estimasi Kompleksitas**: Sedang (Medium)  

---

## 🛡️ ANALISIS RISIKO & TABRAKAN FITUR (PROTOKOL 1 ANTIGRAVITY)

### 1. Dampak ke Fitur Lain
- **Modul Bahasa (`LocaleHelper.kt`, `MainActivity.kt`):**
  Perubahan ini berdampak langsung pada siklus hidup Activity saat pengguna berganti bahasa. Kita memastikan pergantian konteks mengikuti API resmi Android 13/14 (`LocaleManager`) sehingga tidak merusak *Window Token* Jetpack Compose dan tidak memengaruhi data keuangan di Room DB.
- **Modul Tampilan (`SplitBillScreen.kt`, `SettingsScreen.kt`):**
  Perubahan hanya memodifikasi bobot layout (`Modifier.weight`), pembatasan baris (`maxLines = 1, softWrap = false`), dan posisi penempatan badge profil di antarmuka. Logika perhitungan patungan (`SplitBillCalculator.kt`) dan database tidak disentuh sama sekali.
- **Modul Onboarding & Saldo Awal (`FirstInstallSetupDialog.kt`, `FinanceRepository.kt`):**
  Tidak ada penambahan skema tabel database (tetap Room v11 utuh). Flag onboarding disimpan aman di SharedPreferences terpisah (`finsmart_onboarding_prefs`).

### 2. Potensi Risiko (Analogi Kehidupan Nyata)
> *Analogi:* Mengganti bahasa aplikasi saat ini seperti **mengganti bola lampu saat saklar listrik utama sedang dinyalakan**—aliran listrik mendadak terputus sehingga sekring rumah turun (*crash*).  
> Solusi kita adalah **mematikan saklar lokal secara teratur sebelum memasang lampu baru** (menggunakan jalur resmi Android 14 tanpa memaksakan modifikasi konfigurasi manual).  
> Untuk bug teks vertikal di Split Bill: ini seperti **memaksa meja kasir yang sempit dijejali dua layar monitor tanpa sekat pembatas**, sehingga salah satu monitor terpaksa diposisikan miring/terbalik ke bawah. Dengan memberikan pembatas ukuran yang adil (*weight* dan *softWrap*), kedua monitor akan berdiri berdampingan secara rapi.

### 3. Rekomendasi Aman
1. **Fokus Tunggal & Bertahap:** Tuntaskan dan buktikan **Fase 1 (Anti-Crash Bahasa)** terlebih dahulu hingga pengujian otomatis di terminal lulus 100%.
2. **Kemandirian Modul UI:** Modifikasi layout antarmuka di `SplitBillScreen.kt` dan `SettingsScreen.kt` dilakukan tanpa mengubah alur data atau status ViewModel.
3. **Nol Modifikasi Skema Database:** Tidak mengubah Room Database (tetap versi 11 aman) sehingga data pengguna tidak ada yang terhapus.

---

## 🎯 RINGKASAN PERSYARATAN & FITUR BARU

1. **Perbaikan Crash Bahasa Android 14 (Fase 1):**
   - Hapus pemanggilan usang `res.updateConfiguration` di `LocaleHelper.kt`.
   - Rapikan `attachBaseContext` di `MainActivity.kt` agar tidak membungkus `createConfigurationContext` pada Android 13+ (API 33+).
2. **Lokalisasi Sistem HP yang Nyata & Hapus "(Otomatis)":**
   - Hapus kata "(Otomatis)" pada teks pilihan bahasa sistem HP.
   - Deteksi bahasa sistem diperluas: jika bahasa HP adalah Indonesia (`id` / `in`), gunakan Bahasa Indonesia; jika bahasa internasional lainnya (Inggris, Spanyol, Prancis, dll.), gunakan Bahasa Inggris untuk kamus aplikasi, namun format tanggal/angka tetap mengikuti sistem HP pengguna.
3. **Penginstalan Pertama Bersih Tanpa Dompet Bawaan (0 Dompet):**
   - Akun tamu atau pengguna baru yang baru pertama kali dipasang tidak memunculkan atau membuat dompet bawaan apa pun secara paksa. Pengguna diberikan kendali penuh untuk menambahkan dompet mereka sendiri.
4. **Pop-up Onboarding Pertama Kali Install:**
   - Munculkan dialog selamat datang saat aplikasi pertama kali dibuka (setelah instalasi baru) untuk memilih:
     - Bahasa Aplikasi (Ikuti Sistem HP, Bahasa Indonesia, English).
     - Mata Uang Utama (Ikuti Wilayah HP, IDR, USD, dll.).
5. **Solusi Bug Penulisan Vertikal di Split Bill & Audit UI Kaku:**
   - Berikan `weight(1f, fill = false)` pada kolom kiri dan `maxLines = 1, softWrap = false` pada nominal rupiah di `SplitBillScreen.kt` (ringkasan total tagihan, rincian per orang, item pesanan, dan riwayat).
   - Audit dan amankan komponen lain yang berpotensi serupa.
6. **Perapian Badge Smart Member di Pengaturan:**
   - Pindahkan badge role/member dari sebelah nama pengguna menjadi tepat di bawah nama pengguna di `SettingsScreen.kt` agar lebih rapi dan tidak sesak.

---

## 🏗️ TAHAPAN IMPLEMENTASI TERSTRUKTUR

### Lapis 1: Persiapan Preferensi & Status Onboarding (Data-First)
- Buat penanda preferensi `has_completed_first_run_setup` di `OnboardingManager.kt` / SharedPreferences.
- Pastikan akun baru tidak menginjeksi dompet awal sintetis ke dalam Room DB.

### Lapis 2: Refaktor Logika Bahasa & Locale (Logic-Second)
- Perbarui `LocaleHelper.kt`:
  - Ubah `AppLanguage.SYSTEM` menjadi `"Ikuti Sistem HP"` dan `"Follow System"`.
  - Sempurnakan deteksi `isEnglish()` dan `getActiveLocale()` untuk menangani variasi bahasa global.
  - Hilangkan pemanggilan konfigurasi yang menyebabkan *crash* di Android 14.
- Perbarui `MainActivity.kt`:
  - Selaraskan `attachBaseContext` dengan standar resmi Android 13+.
- Jalankan uji otomatis di terminal:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.example.util.GlobalLocalizationAndCurrencyTest"
  ```

### Lapis 3: Pembaruan Antarmuka Visual & Audit Layout Kaku (UI-Last)
- Buat dialog `FirstInstallSetupDialog.kt` untuk pemilihan bahasa dan mata uang saat pertama kali dipasang.
- Hubungkan dialog onboarding ke `FinSmartApp.kt` agar tampil sebelum pengguna masuk ke Dasbor utama.
- Perbaiki tata letak di `SplitBillScreen.kt`:
  - Tambahkan `Modifier.weight(1f, fill = false)` pada kolom deskripsi kiri.
  - Tambahkan `maxLines = 1, softWrap = false` pada nominal rupiah di kanan.
- Perbaiki penempatan badge profil di `SettingsScreen.kt`:
  - Pindahkan badge ke bawah nama pengguna dengan spasi yang lega.

---

## 📂 BERKAS YANG AKAN DIUBAH

| Berkas | Aksi | Alasan Perubahan |
| :--- | :--- | :--- |
| `app/src/main/java/com/example/util/LocaleHelper.kt` | UPDATE | Mengatasi crash Android 14, menghapus kata "(Otomatis)", dan menyelaraskan deteksi bahasa sistem. |
| `app/src/main/java/com/example/MainActivity.kt` | UPDATE | Mengamankan `attachBaseContext` agar Window Token tidak rusak saat Activity dimuat ulang. |
| `app/src/main/java/com/example/ui/screens/SplitBillScreen.kt` | UPDATE | Mengatasi bug nominal/teks terlipat vertikal per huruf dan merapikan layout kaku. |
| `app/src/main/java/com/example/ui/screens/SettingsScreen.kt` | UPDATE | Memindahkan badge status Smart Member ke bawah nama user agar lebih rapi. |
| `app/src/main/java/com/example/ui/components/FirstInstallSetupDialog.kt` | CREATE | Pop-up onboarding awal instalasi untuk memilih bahasa dan mata uang. |
| `app/src/main/java/com/example/ui/FinSmartApp.kt` | UPDATE | Mengintegrasikan dialog onboarding awal instalasi secara mulus. |
| `app/src/main/java/com/example/util/OnboardingManager.kt` | CREATE | Mengelola status preferensi apakah onboarding awal sudah selesai dilakukan. |

---

## 🧪 VALIDASI & PENGUJIAN AKHIR
```bash
# 1. Validasi Unit Test Terkait Lokalisasi & Mata Uang
./gradlew testDebugUnitTest --tests "com.example.util.GlobalLocalizationAndCurrencyTest"

# 2. Validasi Seluruh Unit Test Proyek (Anti-Regresi)
./gradlew testDebugUnitTest

# 3. Validasi Integritas Kode (Anti-Dummy / Anti-Fake-Pass)
python auto_sync.py status
```

---

## ✋ MENUNGGU KONFIRMASI OWNER
Sesuai protokol `.antigravityrules` dan panduan `/plan`, **kode belum disentuh sedikit pun**.  
Mohon berikan konfirmasi apakah rencana tahapan kerja di atas sudah sesuai dan disetujui untuk dieksekusi.
