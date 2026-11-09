# CURRENT_CHECKPOINT.md
# STATUS TERAKHIR PROYEK: DOMPETNALAR (FINSMART)

> [!NOTE]
> File ini adalah memori utama proyek Anda. Setiap kali membuka **New Chat** di Antigravity,
> AI wajib membaca file ini untuk mengetahui status terakhir tanpa halusinasi.

---

## 📌 1. Identitas & Status Proyek Detik Ini
- **Nama Aplikasi:** DompetNalar (Sebelumnya: FinSmart)
- **Paket Aplikasi:** `com.aistudio.finsmart.kptxvw.debug`
- **Versi Rilis Terakhir:** v1.0.15 (versionCode 15)
- **Status Database:** Room v11 (Utuh, Aman, 0 modifikasi manual)
- **Status Git:** Branch `main` (Semua perubahan ter-commit rapi)
- **Status Proteksi:** Template Proyek Aman aktif (`auto_sync.py`, `.antigravityrules`, `GEMINI.md`)
- **Status Fase Terakhir:** **Fase 2: Pengaturan Awal Instalasi (Nol Dompet & Pop-up Onboarding Bahasa/Mata Uang) — SELESAI 100% (PASS 983 Unit Test, Verified E2E)**

---

## 🔒 2. Keputusan Mutlak yang Dikunci (Locked System Protocol)
> *"Kalau ada bug, cari sumbernya ada di mana lalu perbaiki. Setelah itu cek apakah bug masih ada atau tidak. Kalau ada perbaiki lagi, setelah itu cek lagi. Kalau sudah diperbaiki baru buat laporan, kalau belum diperbaiki diagnosa lagi."*
- Alur ini adalah protokol wajib anti-asal-bapak-senang yang mengikat seluruh pengerjaan bug di proyek ini.

---

## 🔍 3. Rincian Penyelesaian Fase 2
1. **Jaminan Instalasi Bersih 0 Dompet (User Autonomy):**
   - Pengguna baru (baik mode tamu maupun akun terdaftar) dijamin memulai dengan 0 dompet tanpa dompet tiruan bawaan.
   - `provisionDefaultWalletsIfEmpty` terbukti mengembalikan `emptyList()`.
   - Dasbor menampilkan kartu *Empty State* yang elegan dengan tombol **"+ Tambah Dompet Pertama"**.
   - Layar Manajemen Dompet menampilkan kartu *Empty State* dengan tombol **"+ Tambah Dompet"**.
2. **Pop-up Onboarding Pertama Kali Buka (`FirstRunOnboardingDialog`):**
   - Menggunakan `OnboardingManager` berbasis preferensi lokal privat (`finsmart_onboarding_prefs`) tanpa mengirim data keluar (Zero-Cost FOSS).
   - Menyediakan pilihan:
     * **Bahasa Antarmuka:** Ikuti Sistem HP (`SYSTEM`), Bahasa Indonesia (`INDONESIAN`), atau English (`ENGLISH`).
     * **Mata Uang Utama:** Ikuti Wilayah HP (`FOLLOW_DEVICE`) dengan teks deteksi otomatis, atau pilihan manual IDR, USD, EUR, SGD, MYR, dsb.
   - Pilihan bahasa dan mata uang langsung terkunci dan aktif seketika di seluruh antarmuka aplikasi.
   - Alur pembuka tersusun sekuensial: Onboarding Setup -> Kebijakan Privasi (Prominent Disclosure) dalam bahasa terpilih -> Dasbor Utama.
3. **Verifikasi End-to-End di Terminal:**
   - `Phase2FirstRunOnboardingAndZeroWalletsTest` -> LULUS 100% (10 skenario pengujian).
   - `Phase1gUiLocalizationAndAccountRemovalTest` -> LULUS 100% (Regresi Aman).
   - `PayLaterImportTest` -> LULUS 100% (Regresi Aman).
   - Seluruh 983 unit test proyek -> LULUS 100% (BUILD SUCCESSFUL).
   - `python auto_sync.py audit` -> LULUS (0 TODO, 0 dummy, 0 mock).

---

## 🚀 4. Tugas Berikutnya: Fase 3 (Penuntasan Teks Dwibahasa & Pembersihan Jejak Nama FinSmart)
- **3.1. Audit & Penyelarasan Menu `SettingsScreen.kt`:**
  Ubah seluruh judul dan subjudul yang masih berbahasa Indonesia saat mode Inggris aktif (Gamifikasi, Tes Profil Finansial AI, Mata Uang, Duress PIN, Aksi Goyang HP, Google Drive Sync), serta tombol "Tutup" dwibahasa.
- **3.2. Audit Dialog & Komponen Pendukung:**
  Periksa dan selaraskan teks pada `WalletComponents.kt`, `DebtPlannerModalDialog.kt`, `PayLaterBillReviewDialog.kt`, dan `ExportFormatManagementDialog.kt`.
- **3.3. Pembersihan Nama Merek Visual (FinSmart -> DompetNalar):**
  Ganti sisa teks merek `FinSmart` di antarmuka menjadi `DompetNalar`.

---

## 📜 5. Log Riwayat Selesai (Verified E2E)
- [2026-11-10 05:10:45] Berhasil menyelesaikan Fase 2: Pengaturan Awal Instalasi (Nol Dompet & Onboarding Dialog). Seluruh 983 unit test lulus 100% (Verified E2E).
- [2026-11-10 01:55:00] Berhasil menyelesaikan Fase 1 (Anti-Crash Bahasa Android 14, Hapus "(Otomatis)", & Penyelarasan Sistem HP Internasional). Seluruh unit test 970+ lulus 100%.
- [2026-11-10 00:50:00] Pembaruan `ROADMAP.md` dengan memasukkan seluruh laporan bug UI (Split Bill vertikal, badge pengaturan, 0 dompet awal, & pop-up onboarding).
- [2026-11-10 00:26:00] Audit mendalam crash bahasa berdasarkan bukti rekaman video.
