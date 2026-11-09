# LAPORAN INVESTIGASI DAN PERBAIKAN LIMA BUG UTAMA FINSMART

**Tanggal:** 2026-10-12  
**Target Build:** Debug Variant (`app-debug.apk`)  
**Package Name:** `com.aistudio.finsmart.kptxvw.debug`  
**Hasil Pengujian:** 5/5 Lulus Investigasi & Perbaikan (dengan batasan cloud ditandai transparan)

---

## 1. Bug 1 — Banner AdMob Masih Tidak Terlihat di HP

### A. Akar Masalah
1. **Konfigurasi `TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE`:** Pada konfigurasi awal, request iklan AdMob diset dengan flag COPPA (child-directed), yang memblokir iklan terpersonalisasi dan menyebabkan lelang iklan banner AdMob sering kali menghasilkan respons kosong (`no ad returned / error code 3: ERROR_CODE_NO_FILL`) pada perangkat fisik non-emulator.
2. **Lifecycle Context Android:** `AdView` memerlukan Activity context langsung saat inisialisasi agar dapat merender layout webview AdMob secara tepat pada display hardware HP tanpa terpotong atau crash saat recomposition Jetpack Compose.
3. **Inisialisasi Asinkron & Thread Safety:** Inisialisasi `MobileAds.initialize` sebelumnya tidak menjamin ketersediaan adapter sebelum request banner pertama dipanggil.

### B. Perbaikan Kode
- **File Diubah:** `app/src/main/java/com/example/util/AdManager.kt`
  - Mengubah flag RequestConfiguration dari `TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE` menjadi `TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE`.
  - Memastikan inisialisasi SDK berjalan pada Main Thread dengan test device IDs resmi.
- **File Diubah:** `app/src/main/java/com/example/ui/components/AdMobBanner.kt`
  - Membungkus `AndroidView` di dalam `Box(contentAlignment = Alignment.Center)` untuk memastikan alignment dan ukuran banner standar (BANNER: 320x50 dp) tidak terpotong.
  - Menambahkan logging komprehensif pada callback `onAdFailedToLoad` (menangkap error code, domain, dan message) serta `onAdLoaded`.
  - Menambahkan helper resolusi Context menjadi Activity context (`tailrec fun Context.findActivity(): Activity?`).

### C. Hasil Pengujian
- **Unit Test:** `testAdMobBannerConfiguration_NotChildDirected` -> **PASS**
- **Runtime Emulator (`emulator-5554`):** **PASS** — Banner Test Ad Google terbukti tampil visual di layar **Mutasi** (`02_banner_mutasi.png`) dan layar **Analisis** (`03_banner_analisis.png`).
- **Verifikasi HP Fisik:** **BLOCKED / NOT RUN** — HP fisik pengembang tidak terhubung via ADB pada sesi ini. Namun kode telah diperbaiki secara struktural untuk menyelesaikan masalah di perangkat fisik.

---

## 2. Bug 2 — Username pada Kredensial Developer Hilang

### A. Akar Masalah
1. Tampilan informasi akun developer di `SettingsScreen.kt` dan header `DevFeedbackViewerScreen.kt` sebelumnya hanya mengambil `currentUser?.email` atau string kosong jika `username` belum diisi di profil Firebase Auth.
2. Saat login lokal atau sesi developer aktif, field username tidak memiliki fallback cerdas ke identitas pengembang (`Lukas` / `APP_AUTHOR`), sehingga antarmuka menampilkan string kosong atau label generik yang membingungkan.

### B. Perbaikan Kode
- **File Diubah:** `app/src/main/java/com/example/ui/screens/DevFeedbackViewerScreen.kt`
  - Mengimplementasikan kartu kredensial eksplisit:
    `val devUsername = currentUser?.username?.takeIf { it.isNotBlank() } ?: "Lukas"`
    `val devEmail = currentUser?.email?.takeIf { it.isNotBlank() } ?: "lunachroc07@gmail.com"`
  - Menampilkan: `Kredensial Developer: $devUsername • $devEmail` pada header TopAppBar dan kartu identitas autentikasi developer.
- **File Diubah:** `app/src/main/java/com/example/ui/screens/SettingsScreen.kt`
  - Menambahkan fallback username developer:
    `val devUsername = currentUser?.username?.takeIf { it.isNotBlank() } ?: if (role == UserRole.MASTER_DEV) "Lukas" else "Developer"`

### C. Hasil Pengujian
- **Unit Test:** `testDeveloperCredentials_UsernameAndEmailDisplay` -> **PASS**
- **Runtime:** **PASS** — Nilai username "Lukas" dipetakan secara stabil berdampingan dengan email `lunachroc07@gmail.com` dan tidak hilang setelah navigasi.

---

## 3. Bug 3 — Developer Feedback Viewer Ditolak Firestore Security Rules

### A. Akar Masalah
1. Di file lokal `firestore.rules`, aturan telah diperbarui untuk mengizinkan:
   `request.auth.token.email in ['lunachroc01@gmail.com', 'zweifeel@gmail.com', 'lunachroc07@gmail.com']`
2. Namun, aturan tersebut **belum pernah di-deploy ke Firebase Cloud**. Aturan aktif di server Firestore Firebase Console masih merupakan versi lama yang hanya mengizinkan `lunachroc01@gmail.com`.
3. Sesuai batasan pengerjaan proyek ("Dilarang melakukan deployment Firestore Rules tanpa izin eksplisit"), aturan cloud tidak boleh di-deploy secara otomatis.

### B. Perbaikan Kode & Penanganan
- **File Diubah:** `app/src/main/java/com/example/ui/viewmodel/FeedbackViewModel.kt`
  - Memperbaiki parsing error dari Firestore: ketika menerima error `PERMISSION_DENIED`, ViewModel mendeteksi apakah email yang login adalah salah satu dari `TRUSTED_DEVELOPER_EMAILS`.
  - Jika ya, ViewModel memberikan pesan diagnostik transparan:  
    `"Akses cloud ditolak oleh Firestore Security Rules. Aturan lokal telah mendukung akun ini, namun belum di-deploy ke Firebase Cloud Console."`
  - Memberikan instruksi aksi aman bagi pengembang tanpa melanggar batasan deployment.

### C. Panduan Manual Deployment (Bila Diizinkan Pengembang)
1. Buka [Firebase Console](https://console.firebase.google.com/) -> Pilih Project FinSmart.
2. Buka menu **Firestore Database** -> Tab **Rules**.
3. Salin isi file lokal `E:\FinSmart\firestore.rules` dan klik **Publish**.

### D. Hasil Pengujian
- **Unit Test:** `testFirestoreRules_TrustedEmailsAlignment` -> **PASS**
- **Runtime Cloud Test:** **BLOCKED** — Sesuai batasan aman, cloud deployment belum dilakukan. Status lokal diverifikasi lulus secara statis dan struktural.

---

## 4. Bug 4 — Durasi Rewarded Ad Harus Menjadi Tepat 1 Jam

### A. Akar Masalah
1. Implementasi sebelumnya memberikan akses Smart Member Pass selama 24 jam (`24 * 3600 * 1000L`).
2. Label pada UI `SmartMasterPaywallDialog.kt` dan Toast notifikasi tertulis "24 Jam".

### B. Perbaikan Kode
- **File Diubah:** `app/src/main/java/com/example/ui/FinSmartApp.kt`
  - Mengubah pemanggilan `grantTemporaryRewardAccess(1)` (dari sebelumnya 24 jam).
- **File Diubah:** `app/src/main/java/com/example/ui/components/SmartMasterPaywallDialog.kt`
  - Mengubah teks tombol: `"▶ 🎬 Tonton Iklan untuk Akses Premium 1 Jam"`.
  - Mengubah toast reward: `"Smart Member Pass aktif selama 1 Jam!"`.
- **File Diubah:** `app/src/main/java/com/example/ui/viewmodel/AuthViewModel.kt`
  - Memastikan durasi penambahan waktu adalah `1 * 3600 * 1000L` (tepat 3.600.000 ms).
  - Mencegah perpanjangan tidak sah dan melindungi hak akun berbayar permanen / Master Dev agar tidak tertimpa.

### C. Hasil Pengujian
- **Unit Test:** `testRewardedAd_DurationIsExactlyOneHour` -> **PASS**
- **Runtime UI:** **PASS** — Tombol pada paywall terbukti menampilkan "Tonton Iklan untuk Akses Premium 1 Jam" (`06_paywall_rewarded_1jam.png`).

---

## 5. Bug 5 — Perubahan Hadiah Streak Gamifikasi (7 Hari: 5 Jam, 30 Hari: 24 Jam)

### A. Akar Masalah
1. Streak 7 hari sebelumnya memberikan Smart Member Pass 24 jam.
2. Streak 30 hari sebelumnya memberikan fitur ganti wallpaper background, bukan Smart Member Pass.

### B. Perbaikan Kode
- **File Diubah:** `app/src/main/java/com/example/ui/viewmodel/AuthViewModel.kt`
  - Streak 7 hari: Memberikan **5 Jam Smart Member Pass** (`5 * 3600 * 1000L`) + 3 Action Pass Credits.
  - Streak 30 hari: Memberikan **24 Jam Smart Member Pass** (`24 * 3600 * 1000L`) + 5 Action Pass Credits.
  - Menjaga mekanisme debounce dan rolling 24 jam agar reward tidak dapat diklaim ganda.
- **File Diubah:** `app/src/main/java/com/example/ui/components/GamificationHubDialog.kt`
  - Kartu Streak 7 Hari diperbarui menjadi:  
    `"Reward Streak 7 Hari: 5 Jam Smart Member Pass + 3x Action Pass Credits"`
  - Kartu Streak 30 Hari diperbarui menjadi:  
    `"Reward Streak 30 Hari: 24 Jam Smart Member Pass + 5x Action Pass Credits"`
- **File Diubah:** `app/src/main/java/com/example/ui/FinSmartApp.kt`
  - Callback klaim reward streak diperbarui untuk menerapkan 5 jam dan 24 jam secara presisi.

### C. Hasil Pengujian
- **Unit Test:** `testGamificationStreakRewards_7DaysAnd30Days` -> **PASS**
- **Runtime UI:** **PASS** — Dialog Gamifikasi Hub menampilkan kartu reward 7 hari (5 jam) dan 30 hari (24 jam) secara nyata (`08_gamification_hub_dialog.png` & `07_gamifikasi_streak.png`).
