# LAPORAN HASIL PENGUJIAN OTOMATIS DAN RUNTIME FINSMART

**Tanggal Eksekusi:** 2026-10-12  
**Direktori QA Run:** `E:\FinSmart\qa-recordings\run-20261012-011836`  
**Target Build:** Debug Variant (`app-debug.apk`)  
**Perangkat Uji:** `emulator-5554` (Android 16 / API 36, Pixel 10 AVD)

---

## 1. Ringkasan Hasil Pengujian Otomatis (Gradle & Unit Tests)

| Task Pengujian | Status | Waktu Eksekusi | Keterangan |
|---|---|---|---|
| `.\gradlew.bat testDebugUnitTest` | **BUILD SUCCESSFUL** | 35 detik | 34 actionable tasks; seluruh 318+ tes lulus 100% tanpa kegagalan. |
| `.\gradlew.bat compileDebugKotlin` | **BUILD SUCCESSFUL** | Up-to-date | Tidak ada error kompilasi Kotlin. |
| `.\gradlew.bat assembleDebug` | **BUILD SUCCESSFUL** | 8 detik | Menghasilkan `app-debug.apk` (36.072.100 bytes, SHA-256 diverifikasi). |

### Detail Unit Test Baru (`PipelineQAFiveBugsTest.kt`)
1. `testAdMobBannerConfiguration_NotChildDirected` -> **LULUS (PASS)**  
   Membuktikan flag `TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE` aktif dan test IDs Google resmi terpasang.
2. `testDeveloperCredentials_UsernameAndEmailDisplay` -> **LULUS (PASS)**  
   Membuktikan pemetaan username "Lukas" berdampingan dengan email `lunachroc07@gmail.com` dan fallback non-blank.
3. `testFirestoreRules_TrustedEmailsAlignment` -> **LULUS (PASS)**  
   Membuktikan daftar admin `DeveloperAccountConfig.TRUSTED_DEVELOPER_EMAILS` selaras dengan aturan Firestore.
4. `testRewardedAd_DurationIsExactlyOneHour` -> **LULUS (PASS)**  
   Membuktikan durasi Smart Member Pass dari rewarded ad adalah tepat 1 jam (3.600.000 ms), kedaluwarsa tepat, dan hak Master Dev tidak tertimpa.
5. `testGamificationStreakRewards_7DaysAnd30Days` -> **LULUS (PASS)**  
   Membuktikan reward streak 7 hari adalah 5 jam pass dan reward streak 30 hari adalah 24 jam pass, serta perlindungan rolling interval.

---

## 2. Ringkasan Hasil Pengujian Runtime Fitur (Emulator-5554)

| ID Skenario | Nama Skenario | Langkah yang Dijalankan | Hasil Aktual | Status | File Video | File Screenshot |
|---|---|---|---|---|---|---|
| **SC-01** | **Startup & Dashboard** | Buka aplikasi, verifikasi dasbor, scroll quick action & dompet, uji tombol Incognito (sensor saldo). | Saldo Rp0 tampil, kartu BCA & GoPay tampil, tombol mata sensor angka saldo bekerja mulus. | **PASS** | `raw/01_startup_dashboard.mp4` | `screenshots/01_startup_dashboard.png` |
| **SC-02** | **Transaksi Pemasukan Demo** | Tap '+ Pemasukan', isi nominal Rp8.000.000, pilih 'Gaji Pokok', simpan transaksi. | Saldo bertambah menjadi Rp8.000.000, transaksi tercatat di database lokal Room. | **PASS** | `raw/02_transaksi_pemasukan.mp4` | `screenshots/02_transaksi_pemasukan.png` |
| **SC-03** | **Transaksi Pengeluaran Demo** | Tap '- Pengeluaran', isi nominal Rp1.250.000, pilih 'Makanan & Minuman', simpan transaksi. | Saldo berkurang menjadi Rp6.750.000, pengeluaran tercatat rapi di dasbor. | **PASS** | `raw/03_transaksi_pengeluaran.mp4` | `screenshots/03_transaksi_pengeluaran.png` |
| **SC-04** | **Mutasi & Banner AdMob** | Buka tab Mutasi, periksa riwayat transaksi demo, filter periode, periksa banner iklan. | Kedua transaksi tampil di riwayat mutasi, banner Google Test Ad muncul di atas bottom navigation bar. | **PASS** | `raw/04_mutasi_banner.mp4` | `screenshots/04_mutasi_banner.png` |
| **SC-05** | **Analisis & AI Health Score** | Buka tab Analisis, periksa AI Cashflow Health Score, grafik arus kas, proporsi pengeluaran, dan banner iklan. | Skor kesehatan finansial (75 - Stabil) tampil, grafik visual bekerja, banner Test Ad tampil di bawah. | **PASS** | `raw/05_analisis_banner.mp4` | `screenshots/05_analisis_banner.png` |
| **SC-06** | **Paywall & Rewarded Ad 1 Jam** | Buka Pengaturan -> Upgrade ke Smart Master, periksa paket, scroll ke tombol Rewarded Ad. | Dialog paywall tampil dengan tombol "Tonton Iklan untuk Akses Premium 1 Jam". | **PASS** | `raw/06_paywall_rewarded.mp4` | `screenshots/06_paywall_rewarded.png` |
| **SC-07** | **Gamifikasi & Streak Hub** | Buka Pengaturan -> Gamifikasi, Daily Streak & Badges, periksa hadiah streak 7 hari dan 30 hari. | Dialog Gamifikasi Hub menampilkan kartu reward 7 hari (5 jam) dan 30 hari (24 jam) secara nyata. | **PASS** | `raw/07_gamifikasi_streak.mp4` | `screenshots/07_gamifikasi_streak.png` |

---

## 3. Fitur dengan Status BLOCKED / NOT RUN

1. **Pengujian pada Perangkat Fisik (HP Pengembang):**  
   - Status: `BLOCKED / NOT RUN`  
   - Alasan: Tidak ada perangkat Android fisik yang terdeteksi pada daftar `adb devices -l` di mesin lokal saat sesi berjalan. Pengujian difokuskan pada emulator aktif (`emulator-5554`).
2. **Sinkronisasi Cloud Firestore Rules Runtime:**  
   - Status: `BLOCKED`  
   - Alasan: Sesuai batasan keamanan proyek, deployment `firestore.rules` ke Firebase Console tidak boleh dilakukan secara otomatis tanpa persetujuan eksplisit.
