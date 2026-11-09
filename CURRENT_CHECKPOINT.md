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
- **Status Git:** Branch `main`
- **Status Proteksi:** Template Proyek Aman aktif (`auto_sync.py`, `.antigravityrules`, `GEMINI.md`)
- **Status Fase Terakhir:** **Fase 1: Perbaikan Bug Kritis Crash Bahasa & Penyelarasan Sistem HP — SELESAI 100% (PASS 970+ Unit Test)**

---

## 🔒 2. Keputusan Mutlak yang Dikunci (Locked System Protocol)
> *"Kalau ada bug, cari sumbernya ada di mana lalu perbaiki. Setelah itu cek apakah bug masih ada atau tidak. Kalau ada perbaiki lagi, setelah itu cek lagi. Kalau sudah diperbaiki baru buat laporan, kalau belum diperbaiki diagnosa lagi."*
- Alur ini adalah protokol wajib anti-asal-bapak-senang yang mengikat seluruh pengerjaan bug di proyek ini.

---

## 🔍 3. Rincian Penyelesaian Fase 1
1. **Perbaikan Crash Pergantian Bahasa Android 14:**
   - Telah menghapus pemaksaan usang `res.updateConfiguration` pada Android 13+ (API 33+) di `LocaleHelper.kt`.
   - Menggunakan mekanisme resmi `LocaleManager.setApplicationLocales` secara murni.
   - Mengamankan `MainActivity.attachBaseContext` dan `LocaleHelper.wrapContext` agar tidak membungkus `createConfigurationContext` pada Android 13+, sehingga *Window Token* Activity Jetpack Compose tetap utuh dan aplikasi tidak mati saat bahasa diganti.
2. **Penghapusan Tulisan "(Otomatis)":**
   - Teks pilihan bahasa sistem HP resmi diubah menjadi:
     * Mode Indonesia: **"Ikuti Sistem HP"**
     * Mode English: **"Follow System"**
3. **Penyelarasan Deteksi Bahasa Sistem HP yang Riil:**
   - Logika deteksi `LocaleHelper.isEnglish()` kini mengenali bahasa Indonesia (`id` / `in`) secara akurat.
   - Jika sistem HP menggunakan bahasa internasional lainnya (Inggris, Spanyol, Prancis, Jepang, dsb.), kamus aplikasi menyesuaikan ke Bahasa Inggris sebagai standar global, sementara format tanggal, angka, dan mata uang tetap mengikuti sistem HP pengguna.
4. **Verifikasi End-to-End di Terminal:**
   - `LocalizationBilingualTest` -> LULUS (PASS 100%).
   - `GlobalLocalizationAndCurrencyTest` -> LULUS (PASS 100%).
   - `Phase1gUiLocalizationAndAccountRemovalTest` -> LULUS (PASS 100%).
   - Seluruh suite unit test proyek (970+ tes) -> LULUS (BUILD SUCCESSFUL).

---

## 🚀 4. Tugas Berikutnya: Fase 2 (Pengaturan Awal Instalasi)
- **2.1. Jaminan Instalasi Bersih 0 Dompet:**
  Memastikan pengguna baru atau tamu pada penginstalan pertama tidak memiliki dompet tiruan bawaan (ruang dompet kosong agar pengguna bebas menambahkan sendiri).
- **2.2. Pop-up Onboarding Pertama Kali Buka (First-Run Setup Dialog):**
  Membangun dialog selamat datang saat instalasi baru untuk langsung memilih bahasa dan mata uang utama aplikasi.

---

## 📜 5. Log Riwayat Selesai (Verified E2E)
- [2026-11-10 01:55:00] Berhasil menyelesaikan Fase 1 (Anti-Crash Bahasa Android 14, Hapus "(Otomatis)", & Penyelarasan Sistem HP Internasional). Seluruh unit test 970+ lulus 100%.
- [2026-11-10 00:50:00] Pembaruan `ROADMAP.md` dengan memasukkan seluruh laporan bug UI (Split Bill vertikal, badge pengaturan, 0 dompet awal, & pop-up onboarding).
- [2026-11-10 00:26:00] Audit mendalam crash bahasa berdasarkan bukti rekaman video.

## 📜 Log Riwayat Selesai (Verified E2E)
- [2026-11-10 05:10:45] Verified E2E: Selesaikan Fase 2: Pengaturan Awal Instalasi (Nol Dompet & Onboarding Dialog)
