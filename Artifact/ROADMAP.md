# ROADMAP.md
# RENCANA TAHAPAN KERJA & ROADMAP RESMI: DOMPETNALAR (FINSMART)

> [!TIP]
> **ATURAN KERJA BERTAHAP:**
> Selesaikan pekerjaan secara berurutan dari **Fase 1 sampai Fase 5**.
> Setiap fase wajib diuji tuntas di terminal sebelum beralih ke fase berikutnya.

---

## 👥 TIM PERSONA SPESIALIS YANG DITUGASKAN

Untuk menjamin kualitas dan keamanan proyek, pengembangan ini dikawal oleh 4 persona ahli:

1. **Android Core Systems & Localization Specialist**
   * *Tugas Inti:* Memperbaiki bug fatal crash pergantian bahasa di Android 14 (`LocaleHelper` & `MainActivity`), membersihkan hardcode teks, menghapus kata "(Otomatis)" pada opsi sistem, dan memastikan deteksi bahasa sistem perangkat beroperasi akurat.
2. **Lead Android & UI/UX Experience Engineer**
   * *Tugas Inti:* Mengeliminasi tampilan kaku (*rigid*), mengatasi bug tulisan vertikal di Split Bill dan kartu lainnya, memindahkan badge Smart Member ke bawah nama user di Pengaturan, membangun Pop-up Onboarding pilihan bahasa & mata uang saat instalasi pertama, serta menyempurnakan transisi antar tab.
3. **QA Verification Auditor & Fake-Pass Hunter**
   * *Tugas Inti:* Menjalankan audit menyeluruh tanpa data tiruan (*anti-fake-pass*), memastikan akun awal instalasi benar-benar bersih 0 dompet (user menambahkan sendiri), dan mengawal 970+ tes otomatis di terminal.
4. **Studio Producer / Scope Protector**
   * *Tugas Inti:* Menjaga fokus kerja agar tidak melebar, menjelaskan status teknis ke dalam bahasa awam yang ramah untuk Owner, dan mengawal Mandat Anggaran Nol Rupiah (100% FOSS).

---

## 🔒 KEPUTUSAN MUTLAK SISTEM (LOCKED PROTOCOL)
> *"Kalau ada bug, cari sumbernya ada di mana lalu perbaiki. Setelah itu cek apakah bug masih ada atau tidak. Kalau ada perbaiki lagi, setelah itu cek lagi. Kalau sudah diperbaiki baru buat laporan, kalau belum diperbaiki diagnosa lagi."*

---

## 🏁 URUTAN PENGERJAAN FITUR & PERBAIKAN

### 🔴 Fase 1: Perbaikan Bug Kritis Crash Bahasa & Penyelarasan Sistem HP (Prioritas P0 - Mendasar) — [SELESAI 100% PASS]
* [x] **1.1. Refaktor `LocaleHelper.kt` (Anti-Crash Android 14):**
  * Gunakan mekanisme resmi Android 13/14 `LocaleManager.setApplicationLocales` secara murni tanpa memaksakan manipulasi manual yang membentur memori tampilan.
  * Hapus pemanggilan `res.updateConfiguration` yang memicu tabrakan saat Activity dimuat ulang.
  * Hapus tulisan "(Otomatis)" pada pilihan bahasa sistem HP (menjadi "Ikuti Sistem HP" / "Follow System").
  * Perluas deteksi bahasa sistem: jika bahasa perangkat adalah Indonesia (`id` / `in`), gunakan Bahasa Indonesia; jika bahasa internasional lainnya (Inggris, Spanyol, Prancis, dsb.), gunakan kamus bahasa internasional (English) secara konsisten dan format angka/tanggal sesuai sistem HP.
* [x] **1.2. Refaktor `MainActivity.kt`:**
  * Rapikan `attachBaseContext` agar tidak membungkus konteks dengan `createConfigurationContext` pada Android 13+ (API 33+) sehingga *Window Token* tetap utuh dan aplikasi tidak mati mendadak saat bahasa diganti.
* [x] **1.3. Verifikasi Logika Terminal:**
  * Lulus pengujian otomatis `LocalizationBilingualTest`, `GlobalLocalizationAndCurrencyTest`, `Phase1gUiLocalizationAndAccountRemovalTest`, dan 970+ tes proyek.

---

### 🟡 Fase 2: Pengaturan Awal Instalasi (Nol Dompet & Pop-up Onboarding Bahasa/Mata Uang) (Prioritas P1) — [SELESAI 100% PASS]
* [x] **2.1. Jaminan Instalasi Bersih 0 Dompet (User Autonomy):**
  * Pastikan pada penginstalan pertama kali (akun baru / tamu baru), aplikasi tidak memunculkan atau membuat dompet bawaan apa pun.
  * Ruang dompet dibiarkan kosong (0 dompet) agar pengguna bebas menambahkan dompetnya sendiri.
  * Tampilkan *Empty State* yang ramah dan elegan pada Dasbor & Layar Dompet dengan tombol "+ Tambah Dompet".
* [x] **2.2. Pop-up Onboarding Pertama Kali Buka (First-Run Setup Dialog):**
  * Bangun dialog pop-up pembuka yang muncul saat aplikasi pertama kali dibuka setelah instalasi baru.
  * Memberi pilihan langsung kepada pengguna untuk menentukan:
    1. **Bahasa Antarmuka:** Ikuti Sistem HP, Bahasa Indonesia, atau English.
    2. **Mata Uang Utama:** Ikuti Wilayah HP, IDR (Rupiah), USD (Dollar), EUR, SGD, MYR, dll.
  * Pilihan langsung tersimpan secara permanen ke preferensi sistem sebelum pengguna masuk ke Dasbor utama.

---

### 🌟 Fase 3: Penuntasan Teks Dwibahasa & Pembersihan Jejak Nama FinSmart (Prioritas P1) — [SELESAI 100% PASS]
* [x] **3.1. Audit & Penyelarasan Menu `SettingsScreen.kt`:**
  * Ubah seluruh judul dan subjudul yang masih berbahasa Indonesia saat mode Inggris aktif (Gamifikasi, Tes Profil Finansial AI, Mata Uang, Duress PIN, Aksi Goyang HP, Google Drive Sync).
  * Terjemahkan istilah bahasa Inggris yang belum di-Indonesiakan di mode Indonesia.
  * Ganti seluruh tombol **"Tutup"** menjadi bilingual (`AppStrings.t("Tutup", "Close")`).
* [x] **3.2. Audit Dialog & Komponen Pendukung:**
  * Periksa dan selaraskan teks pada `WalletComponents.kt`, `DebtPlannerModalDialog.kt`, `PayLaterBillReviewDialog.kt`, dan `ExportFormatManagementDialog.kt`.
* [x] **3.3. Pembersihan Nama Merek Visual (FinSmart -> DompetNalar):**
  * Ganti semua sisa teks `FinSmart` yang tampak di layar menjadi `DompetNalar`.

---

### 🌟 Fase 4: Solusi Bug Tulisan Vertikal, Audit UI Kaku & Perapian Badge Pengaturan (Prioritas P2) — [SELESAI 100% PASS]
* [x] **4.1. Perbaikan Bug Tulisan Vertikal di Split Bill (`SplitBillScreen.kt`):**
  * Berikan `Modifier.weight(1f, fill = false)` pada kolom kiri dan `maxLines = 1, softWrap = false` pada nominal rupiah di kanan pada:
    1. Kartu ringkasan total tagihan (nama toko vs total harga).
    2. Kartu rincian tagihan per orang (nama teman vs total bayar).
    3. Daftar item pesanan dan baris porsi pajak/service.
    4. Kartu riwayat split bill.
* [x] **4.2. Audit Menyeluruh UI Kaku & Perapian Antarmuka Lainnya:**
  * Periksa seluruh layar yang memiliki pola teks bersampingan tanpa bobot (seperti `TransactionItemCard`, `WalletsSection`, `DebtPlannerModalDialog`, `BudgetScreen`, dan `AssetTrackerModalDialog`).
  * Tambahkan `softWrap = false` dan penanganan elipsis agar nominal uang tidak pernah terlipat vertikal per huruf.
* [x] **4.3. Perapian Badge Smart Member di Pengaturan (`SettingsScreen.kt`):**
  * Pindahkan badge status keanggotaan dari posisi samping nama ke posisi bawah nama user di dalam `Column`.
  * Beri jarak dan padding yang proporsional sehingga kartu profil pengguna tampak rapi, modern, dan tidak sesak.
* [x] **4.4. Peningkatan Responsivitas & Animasi Halus Antar Tab:**
  * Pasang transisi lembut antar tab utama di `FinSmartApp.kt` (`AnimatedContent`) dan pastikan seluruh dialog memiliki `verticalScroll(rememberScrollState())`.

---

### 🔵 Fase 5: Pengujian Akhir End-to-End, Build APK & Penyerahan ke Owner (Prioritas P0) — [SELESAI 100% PASS]
* [x] **5.1. Uji Regresi Menyeluruh di Terminal:**
  * Seluruh suite pengujian otomatis (`./gradlew testDebugUnitTest`) lulus 100% (995 unit test PASS, 0 gagal, 0 error).
* [x] **5.2. Audit Detektor Kebohongan (`python auto_sync.py audit`):**
  * Terverifikasi 100% bersih dari fungsi tiruan atau stub (0 TODO, 0 Dummy, 0 Placeholder, 0 Mock).
* [x] **5.3. Pembuatan Berkas APK Baru:**
  * Kompilasi build APK debug terbaru (`./gradlew assembleDebug`) berhasil terbit di `app/build/outputs/apk/debug/app-debug.apk` (41.427.313 bytes).
* [x] **5.4. Panduan Uji Fisik di HP Owner:**
  * Panduan langkah pengujian fisik ramah Non-Coder telah disusun di `PANDUAN_UJI_FISIK_HP_REALME_GT_NEO_3T.md` untuk menguji ke-4 skenario langsung di HP Realme GT Neo 3T.
