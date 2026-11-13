# Rencana Status & Pengujian Berkas APK Debug Terbaru (DompetNalar)

## 📌 Status Terkini: Apakah APK Debug Terbaru Sudah Dibuat?

> [!IMPORTANT]
> **JAWABAN LANGSUNG:**
> **YA, SUDAH DIBUAT!**
> Berkas APK debug terbaru yang memuat seluruh perbaikan bug (UI Mutasi, Debt Planner, dan Anti-Crash Bahasa) telah berhasil dikompilasi dan siap diuji.

### Bukti Fisik Berkas APK:
- **Lokasi Berkas:** `E:\Antigravity Project\FinSmart\app\build\outputs\apk\debug\app-debug.apk`
- **Waktu Pembuatan / Modifikasi Terakhir:** 13 November 2026, pukul 06:32:40 WIB
- **Ukuran Berkas:** `41.493.310 bytes` (~39,5 MB)
- **Status Metadata Gradle:** Sesuai (`output-metadata.json`, versionCode 15, versionName 1.0.15)
- **Status Unit Test:** 998 Unit Test LULUS 100% (0 Failure, 0 Fake-Pass)

---

## 🔍 User Review Required (Pilihan Langkah Selanjutnya)

Mohon konfirmasi pilihan langkah yang ingin Bos ambil:

1. **Langkah A (Rekomendasi Utama - Uji Fisik Langsung):**
   - Menggunakan berkas APK yang sudah ada (`app-debug.apk`) untuk dipindahkan/diinstal ke HP fisik (Realme GT Neo 3T).
   - Mengikuti 3 skenario uji visual dan fungsional.
2. **Langkah B (Kompilasi Ulang / Fresh Build):**
   - Menjalankan perintah `./gradlew assembleDebug` sekali lagi jika ingin memastikan proses build biner ulang secara absolut dari nol (*clean & build*).
3. **Langkah C (Lanjut ke Paket Rilis Play Store / AAB):**
   - Jika pengujian lokal di HP sudah tuntas dan tidak ada keluhan, kita bisa melangkah ke pembuatan bundle rilis resmi (`./gradlew bundleRelease`) bertanda tangan *keystore* untuk Google Play Store.

---

## 🛠️ Rencana Eksekusi Pengujian Fisik (Bila Memilih Langkah A)

Berikut adalah ringkasan yang harus diperiksa di HP saat APK terpasang:

### 1. Uji Anti-Crash Ganti Bahasa
- Buka **Pengaturan (Settings)** -> **Pilih Bahasa Aplikasi**.
- Ganti ke **English**: Antarmuka langsung beralih instan tanpa kedipan dan tanpa force close.
- Ganti ke **Bahasa Indonesia** atau **Ikuti Sistem**: Berjalan mulus 100%.

### 2. Uji Layar Riwayat Transaksi (Mutasi)
- Atur bahasa ke **English**, lalu buka tab **Transactions**.
- Judul kartu ringkasan kini terbaca rapi: **"Cashflow Summary (Monthly)"** berdampingan dengan badge Net **"Net: +Rp 0"** (tidak ada teks terpotong `Cashflow Recapitulation (Mont...`).
- Chip filter kategori menampilkan nama terjemahan, misalnya **"Shopping & Hobbies"**.

### 3. Uji Modal Tambah Pinjaman (Debt Planner)
- Buka tab Saldo/Hutang -> **Tambah Pinjaman / Hutang Baru**.
- Kolom *Due Date* otomatis terisi **"Day 15 each month"** (mode EN) atau **"Tgl 15 tiap bulan"** (mode ID).
- 5 tombol pintasan tanggal di bawahnya tampil proporsional dan utuh: `[ Day 5 ] [ Day 10 ] [ Day 15 ] [ Day 20 ] [ End ]`.

---

## 🧪 Verification Plan

### Automated Verification
```powershell
# Memeriksa kembali eksistensi dan timestamp berkas APK
Get-ChildItem -Path "app\build\outputs\apk\debug\app-debug.apk" | Select-Object FullName, Length, LastWriteTime
```

### Manual Verification
1. Salin `app-debug.apk` ke HP via kabel USB / Google Drive / WhatsApp Document.
2. Pasang (*Install / Update*) di HP Realme GT Neo 3T.
3. Jalankan pengujian sesuai 3 skenario di atas.
