# Product Requirement Document (PRD) — FinSmart Android App

**Dokumen:** Product Requirement Document (PRD)
**Produk:** FinSmart Android Application
**Versi Dokumen:** v2.0.0
**Status Baseline:** Disetujui (Approved Baseline)
**Pemilik Produk:** Technical Product Management & Engineering
**Checkpoint Git:** `00315157098d58bcccdc5b592fc2f2c417b72044` (`0031515`)
**Basis Pemulihan:** Strategy B (Restore Historical Context & Synchronize with Current Verified Baseline)
**Referensi Arsitektur:** `FINSMART-ARCH-REV-001`
**Tanggal Efektif:** 04 Oktober 2026

---

## 1. Overview & Vision

### 1.1 Definisi & Ringkasan Produk
FinSmart adalah aplikasi manajemen keuangan pribadi (*personal finance management*) berbasis sistem operasi Android yang dirancang dengan arsitektur **offline-first** dan diperkaya dengan kapabilitas kecerdasan buatan (**cloud AI enhancement**) menggunakan model Google Gemini 2.0 Flash.

Aplikasi ini bertujuan untuk mentransformasi pengalaman pencatatan keuangan konvensional yang kaku dan merepotkan menjadi interaksi yang sangat intuitif, cepat, dan cerdas—semudah mengirimkan pesan percakapan singkat atau memotret bukti pembayaran nota belanja.

FinSmart beroperasi secara independen tanpa ketergantungan pada server backend kustom terpusat. Seluruh penyimpanan data keuangan, kalkulasi anggaran, manajemen multi-dompet, dan riwayat transaksi tersimpan secara persisten pada basis data lokal SQLite (Room Database Version 5) di perangkat pengguna demi menjamin privasi finansial mutlak. Fitur kecerdasan buatan beroperasi secara hibrida: logika finansial inti dan tokenizer aturan selalu tersedia saat perangkat offline, sedangkan inferensi tingkat lanjut (Natural Language Understanding dan Vision OCR Struk) memanfaatkan koneksi cloud terenkripsi ke Google Gemini API Gateway.

### 1.2 Visi & Filosofi Produk
1. **Pencatatan Semudah Chatting:** Mengeliminasi friksi pencatatan manual dengan memungkinkan pengguna mengetik atau mengucapkan pengeluaran dalam bahasa alami sehari-hari (misalnya: *"makan siang bakso 25rb bayar gopay"*).
2. **Kedaulatan Data & Privasi Finansial (Privacy-First):** Data keuangan adalah ranah paling intim bagi pengguna. FinSmart memegang prinsip bahwa data pengguna sepenuhnya menjadi milik pengguna; tidak ada pelacakan pihak ketiga (*zero third-party telemetry*), tidak ada penjualan data analitik, dan seluruh catatan sensitif dapat dihapus secara permanen dalam sekali ketukan.
3. **Pemberdayaan Finansial Mandiri:** Menolong individu muda dan keluarga urban dalam membangun kebiasaan finansial yang sehat, terhindar dari perilaku impulsif, dan memahami arus kas riil melalui analisis visual serta konsultasi berbasis AI Advisor yang objektif.

### 1.3 Batasan Ruang Lingkup Mutlak (Negative Scope Declaration)
* **FinSmart MURNI Aplikasi Keuangan Pribadi (Personal Finance):** FinSmart **BUKAN** aplikasi manajemen stok, pergudangan (*warehouse*), inventaris dagang, atau kasir POS UMKM.
* **Tidak Ada Entitas Inventaris:** Di dalam sistem FinSmart **TIDAK ADA** dan **DILARANG MENGADAKAN** modul atau entitas seperti `ProductItem`, `StockMovement`, `InventoryScreen`, `InventoryRepository`, manajemen katalog barang, atau API sinkronisasi stok.
* **Klarifikasi Terminologi "Item":** Setiap kemunculan istilah "item" di dalam dokumen ini atau pada modul pemindaian nota (OCR) **hanya merujuk pada rincian baris bukti transaksi pengeluaran konsumsi pribadi** (*itemized receipt expense line*), bukan barang dagangan atau stok inventaris.

### 1.4 Sasaran Strategis (Strategic Goals & SLA)
* **Crash-Free Sessions:** Mempertahankan rasio sesi bebas gangguan $\ge 99,8\%$.
* **Quick Logging Speed:** Waktu pencatatan transaksi manual standar $< 5\text{ detik}$.
* **Target Latensi AI OCR:** Pemrosesan nota struk rata-rata $\le 3\text{ detik}$ pada kondisi jaringan 4G/Wi-Fi stabil (dengan timeout jaringan 30 detik).
* **Cold App Startup:** Inisialisasi awal aplikasi hingga dashboard interaktif $< 1,5\text{ detik}$.
* **Integritas CI/CD Dokumentasi:** Menjaga keutuhan struktur 14 bab wajib pada pipeline otomatisasi `scripts/update_prd.py`.

---

## 2. Target Audience & User Personas

### 2.1 Segmen Pengguna Sasaran
1. **Young Professionals & First Jobbers (Usia 21–28 tahun):** Karyawan baru yang mulai menerima penghasilan rutin bulanan, sering bersosialisasi makan bersama rekan kerja, dan berjuang membangun dana darurat pertama.
2. **Pekerja Lepas / Freelancers (Usia 23–38 tahun):** Individu dengan pendapatan fluktuatif dari berbagai klien yang membutuhkan pemisahan ketat antara dompet operasional kerja dan dompet kebutuhan pribadi.
3. **Pengelola Keuangan Rumah Tangga (Usia 28–45 tahun):** Pengguna yang rutin berbelanja kebutuhan pokok di supermarket/pasar tradisional dan membutuhkan pencatatan struk belanjaan fisik secara otomatis tanpa harus mengetik satu per satu.

### 2.2 Persona Pengguna

#### Persona 1: Rian (24 Tahun, First Jobber / Junior Software Engineer)
* **Karakteristik & Kebiasaan:** Aktif beraktivitas di kawasan perkantoran urban, sering makan siang dan ngopi bersama rekan satu tim, bertransaksi harian mayoritas menggunakan dompet digital (GoPay, OVO, QRIS).
* **Titik Masalah (Pain Points):**
  * Sering lupa mencatat pengeluaran mikro (parkir, kopi, camilan sore).
  * Repot menghitung pembagian tagihan makan bersama (*split bill*) yang memiliki rincian pajak (PB1) dan service charge berbeda-beda.
  * Aplikasi perbankan resmi terlalu lambat dibuka dan memiliki antarmuka yang membingungkan untuk tracking harian.
* **Kebutuhan FinSmart:**
  * Pencatatan kilat berbasis perintah teks alami atau suara (*AI Quick Input*).
  * Kalkulator *Split Bill* instan yang dapat langsung diekspor dan dibagikan ke grup WhatsApp lengkap dengan rincian per orang.
  * Notifikasi batas anggaran saat pengeluaran kategori "Hiburan & Kafe" mendekati 80%.

#### Persona 2: Maya (32 Tahun, Freelance Designer & Ibu Rumah Tangga)
* **Karakteristik & Kebiasaan:** Memiliki arus kas masuk pada tanggal tidak tentu dari proyek desain, sekaligus memegang kendali atas anggaran belanja bulanan keluarga.
* **Titik Masalah (Pain Points):**
  * Struk belanja mingguan dari minimarket/supermarket menumpuk di dompet dan memakan waktu lama jika disalin manual.
  * Sulit melihat apakah pengeluaran rumah tangga bulan ini surplus atau defisit dibandingkan pemasukan proyek freelance.
  * Menuntut privasi tinggi agar rincian saldo rekening tidak mudah diintip saat ponsel dipinjam anak atau orang lain.
* **Kebutuhan FinSmart:**
  * Pemindai struk fisik otomatis (*AI Vision OCR*) yang langsung memetakan total biaya, tanggal, dan nama toko ke dalam transaksi.
  * Manajemen Multi-Dompet untuk memisahkan "Rekening Operasional Bisnis", "Rekening Tabungan Keluarga", dan "Uang Kas Belanja Pasar" melalui *Personal vs Business Mode*.
  * Fitur Keamanan *Stealth Mode* (antarmuka kalkulator penyamar) dan *Duress PIN* untuk perlindungan data finansial keluarga.

---

## 3. User Stories & Use Cases

### 3.1 Daftar User Stories

| ID | Kategori | Sebagai | Saya Ingin | Agar | Kriteria Penerimaan (Acceptance Criteria) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **US-01** | Transaksi | Pengguna Harian | Mencatat transaksi mutasi pengeluaran dalam hitungan detik | Saya tidak menunda pencatatan dan saldo dompet langsung terpotong | Transaksi tersimpan ke Room DB lokal $< 300\text{ms}$ setelah tombol simpan ditekan. |
| **US-02** | Mode Akun | Pengguna Multitasking | Beralih antara mode Pribadi (*Personal*) dan Bisnis (*Business*) | Arus kas usaha mikro saya tidak tercampur dengan pengeluaran pribadi | Switcher pill di top bar langsung memfilter dompet dan mutasi transaksi sesuai mode terpilih. |
| **US-03** | AI Input | Pengguna Sibuk | Mengetik pesan alami seperti *"Beli bensin 30rb pakai BCA"* | Sistem otomatis mendeteksi nominal, kategori Transportasi, dan dompet BCA | Form otomatis terisi nominal Rp30.000, kategori dan dompet sesuai, dengan tombol konfirmasi satu klik. |
| **US-04** | AI OCR | Pengguna Belanja | Memotret struk belanja fisik dari kamera ponsel | Rincian nominal total, nama toko, dan tanggal otomatis terekstraksi | Citra diproses oleh Gemini 2.0 Flash Vision; form terpopulasi otomatis; jika offline, fallback parsing regex aktif. |
| **US-05** | Voice Input | Pengguna Mobilitas | Berbicara langsung di depan ponsel untuk mendikte pengeluaran | Saya tetap dapat mencatat pengeluaran saat sedang berjalan atau berkendara | Suara dikonversi via SpeechRecognizer lokal dan diproses oleh parser transaksi teks. |
| **US-06** | Split Bill | Pengguna Sosial | Membagi tagihan makan bersama dengan pajak dan diskon | Setiap teman membayar sesuai porsinya tanpa salah hitung | Sistem menghasilkan kalkulasi proporsional dan teks ringkasan rapi siap kirim ke WhatsApp. |
| **US-07** | Anggaran | Pengguna Hemat | Menetapkan batas anggaran per kategori bulanan | Saya mendapat peringatan visual sebelum overspending | Progress bar berubah warna (hijau $\rightarrow$ kuning $\rightarrow$ merah) saat belanja mencapai 80% dan 100% pagu. |
| **US-08** | Laporan | Pengguna Teliti | Mengekspor riwayat transaksi ke berkas PDF dan CSV | Saya memiliki salinan arsip fisik untuk laporan keuangan bulanan | Sistem menghasilkan dokumen PDF bertata letak rapi dan file CSV tabular yang dapat dibagikan. |
| **US-09** | AI Advisor | Pengguna Pemula | Berkonsultasi mengenai strategi keuangan saya | Saya mendapat rekomendasi cerdas berbasis kondisi saldo riil | Chatbot Gemini menganalisis konteks saldo lokal dan memberikan saran penghematan konkret. |
| **US-10** | Keamanan Ekstrem | Pengguna Terancam | Memasukkan PIN palsu (*Duress PIN*) saat dipaksa pihak lain | Aplikasi menampilkan data tiruan tanpa membongkar data asli | Sistem mendeteksi PIN darurat dan beralih ke profil dompet dummy dengan saldo rekayasa. |
| **US-11** | Backup | Pengguna Teratur | Mencadangkan dan memulihkan data keuangan ke file terenkripsi | Data aman saat berganti perangkat | Tersedia ekspor file JSON terenkripsi AES-256 dan simulasi pencadangan sandbox lokal. |
| **US-12** | Monitisasi | Pengguna Bebas Iklan | Membuka fitur Smart Master via pembayaran mandiri | Saya mendapatkan akses fitur premium tanpa batas iklan | Tersedia opsi transfer DANA manual (build mandiri) atau status IAP (build Play Store). |
| **US-13** | Privasi | Pengguna Regulasi | Menghapus seluruh akun dan data secara tuntas | Riwayat finansial saya hilang tanpa meninggalkan jejak di perangkat | Dialog konfirmasi menghapus seluruh 7 tabel Room Database dan SharedPreferences seketika. |

### 3.2 Use Case Spesifik: Pemindaian Struk Belanja dengan Gemini OCR
1. **Aktor Utama:** Pengguna (Maya).
2. **Kondisi Awal:** Pengguna berada di layar dashboard dan memegang struk fisik dari minimarket.
3. **Alur Peristiwa Utama:**
   1. Pengguna membuka Speed-Dial FAB dan memilih opsi **Scan Struk (OCR)**.
   2. Sistem membuka layar `ReceiptScanScreen` dan memeriksa izin kamera (didahului dialog pengungkapan izin jika belum diberikan).
   3. Pengguna memotret struk belanjaan atau memilih gambar nota dari galeri.
   4. Pengguna menekan tombol **Proses Nota**.
   5. Sistem mengompresi citra ke format JPEG/Base64 dan mengirimkan payload multipart ke endpoint Google Gemini 2.0 Flash REST API.
   6. Gemini mem-parsing teks gambar dan mengembalikan JSON terstruktur: Nama Merchant, Tanggal, Nominal Total, dan Daftar Baris Bukti Transaksi Pengeluaran Konsumsi.
   7. Sistem memetakan nilai tersebut ke formulir transaksi dan memilih kategori default yang paling relevan.
   8. Pengguna memverifikasi data dan menekan tombol **Simpan Transaksi**.
   9. Transaksi tersimpan ke Room DB Version 5 dan saldo dompet terpilih berkurang secara atomik.
4. **Alur Alternatif (Koneksi Internet Putus / Offline):**
   * Pada langkah 5, jika panggilan jaringan gagal/timeout, sistem secara otomatis mengaktifkan preset nota realistis / modul parsing pola regex lokal untuk mengenali pola nota standar atau meminta input manual nominal dengan gambar tetap tersimpan sebagai referensi lokal.

---

## 4. Functional Requirements

Berikut adalah daftar lengkap 36 fitur resmi (**FEAT-01 sampai FEAT-36**) yang divalidasi langsung terhadap baseline arsitektur resmi **`FINSMART-ARCH-REV-001`**:

### FEAT-01: Multi-Wallet Balance & Accounts
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `DashboardScreen.kt`, `WalletsScreen.kt`
* **ViewModel / Handler:** `FinanceViewModel`
* **Persistensi / API:** Room Database `wallets` table
* **Deskripsi:** Pengelolaan multi-rekening/dompet (Kas Tunai, Rekening Bank, Dompet Digital, Tabungan Investasi) dengan pencatatan saldo independen, visualisasi warna heksadesimal dan ikon, penandaan dompet default, serta rekonsiliasi saldo.

### FEAT-02: Personal vs Business Mode
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** Top Header Pill Switcher (`DashboardScreen.kt`, `WalletsScreen.kt`)
* **ViewModel / Handler:** `FinanceViewModel`
* **Persistensi / API:** Kolom `accountMode` pada tabel `wallets` dan `transactions`
* **Deskripsi:** Pemisahan konteks pencatatan keuangan antara akun Pribadi (*Personal*) dan akun Usaha (*Business*) secara instan dengan satu ketukan toggle pill, memastikan arus kas usaha mikro tidak tercampur dengan belanja rumah tangga.

### FEAT-03: Mutasi & Transaction Logging
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `AddTransactionSheet.kt`, FAB Speed-Dial, `TransactionsScreen.kt`
* **ViewModel / Handler:** `FinanceViewModel`
* **Persistensi / API:** Room Database `transactions` table
* **Deskripsi:** Pencatatan mutasi transaksi keuangan komprehensif (Pemasukan / *Income*, Pengeluaran / *Expense*, dan Transfer) dengan metadata lengkap: judul, nominal, tanggal, kategori, dompet asal, catatan, dan tag.

### FEAT-04: Automatic Wallet Balance Adjustment
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `FinanceRepository.kt`
* **ViewModel / Handler:** `FinanceViewModel`
* **Persistensi / API:** Operasi penyesuaian saldo Room atomik
* **Deskripsi:** Rekonsiliasi dan pembaruan saldo dompet secara otomatis dan atomik di basis data saat transaksi baru dibuat, diedit nominalnya, atau dihapus dari riwayat mutasi.

### FEAT-05: Double-Entry Wallet Transfer
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `AddTransactionSheet.kt` (Mode Transfer)
* **ViewModel / Handler:** `FinanceViewModel`
* **Persistensi / API:** Kolom `targetWalletId` & `targetWalletName` pada tabel `transactions`
* **Deskripsi:** Fasilitas transfer dana antar-dompet dengan sistem pencatatan ganda (*double-entry bookkeeping*) yang memotong saldo dompet sumber dan menambahkan saldo ke dompet tujuan dalam satu transaksi atomik.

### FEAT-06: Interactive Cashflow Chart
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `DashboardScreen.kt`, `AnalyticsScreen.kt`, `CashflowChart.kt`
* **ViewModel / Handler:** `FinanceViewModel`
* **Persistensi / API:** In-memory aggregations data Room
* **Deskripsi:** Visualisasi grafik arus kas interaktif (tren pemasukan vs pengeluaran) dengan kurva visual halus, rincian proporsi pengeluaran, dan penanda titik data per tanggal.

### FEAT-07: Timeframe Filtering
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** Filter Chips pada layar analitik dan transaksi
* **ViewModel / Handler:** `FinanceViewModel` (`filteredTransactions` StateFlow)
* **Persistensi / API:** Query filter lokal Room
* **Deskripsi:** Penyaringan data transaksi dan visualisasi analitik berdasarkan jendela waktu dinamis: Harian (*Day*), Mingguan (*Week*), Bulanan (*Month*), dan Tahunan (*Year*).

### FEAT-08: Natural Language AI Expense Input
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `AiChatScreen.kt` (Tab 1)
* **ViewModel / Handler:** `FinanceViewModel`, `ExpenseAiParser`
* **Persistensi / API:** Google Gemini 2.0 Flash REST API
* **Deskripsi:** Ekstraksi transaksi pintar dari kalimat percakapan bebas (misalnya: *"Makan siang mie ayam 25000 pakai gopay"*); model Gemini memetakan nominal, kategori, dan dompet secara cerdas ke formulir transaksi.

### FEAT-09: Offline Indonesian NLP Parser
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `AiChatScreen.kt` (Fallback saat offline)
* **ViewModel / Handler:** `ExpenseAiParser`
* **Persistensi / API:** Engine regex lokal & kamus kata kunci bahasa Indonesia
* **Deskripsi:** Mesin pengurai transaksi cadangan berbasis ekspresi reguler deterministik dan kamus kata kunci bahasa Indonesia yang mengenali pola transaksi umum secara instan tanpa koneksi internet.

### FEAT-10: Voice Input Recognition
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `AiChatScreen.kt` (Tombol mikrofon)
* **ViewModel / Handler:** `VoiceRecognitionManager`
* **Persistensi / API:** Android `SpeechRecognizer` API lokal
* **Deskripsi:** Perekaman input suara bebas tangan (*hands-free*) yang mengonversi dikte vokal pengguna menjadi teks lalu meneruskannya ke mesin parser transaksi NLP.

### FEAT-11: AI Financial Health Advisor
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `AiChatScreen.kt` (Tab 2)
* **ViewModel / Handler:** `AdvisorAiService`
* **Persistensi / API:** Gemini 2.0 Flash API + `OfflineAdvisorEngine`
* **Deskripsi:** Asisten konsultan perencanaan keuangan interaktif yang membaca konteks kesehatan finansial pengguna (arus kas dan pagu anggaran) serta memberikan rekomendasi penghematan cerdas, dengan fallback aturan deterministik saat offline.

### FEAT-12: Receipt OCR Scan
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `ReceiptScanScreen.kt`
* **ViewModel / Handler:** `ReceiptAiOcrScanner`
* **Persistensi / API:** Google Gemini 2.0 Flash Vision API
* **Deskripsi:** Pemindaian bukti nota fisik melalui kamera atau berkas galeri menggunakan model multimodal `gemini-2.0-flash` untuk mendeteksi merchant, tanggal transaksi, total belanja, dan baris rincian bukti transaksi konsumsi.

### FEAT-13: Proportional Split-Bill Calculator
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `SplitBillScreen.kt`
* **ViewModel / Handler:** `SplitBillCalculator`
* **Persistensi / API:** Room Database `split_bill_history` table
* **Deskripsi:** Kalkulator pembagian tagihan makan bersama multi-partisipan dengan alokasi pajak persentase, service charge, diskon kustom, dan pembagian item spesifik per individu.

### FEAT-14: WhatsApp Bill Summary Export
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `SplitBillScreen.kt`
* **ViewModel / Handler:** `SplitBillCalculator`
* **Persistensi / API:** Android Clipboard & Share Intent (`ACTION_SEND`)
* **Deskripsi:** Generator teks ringkasan tagihan terstruktur yang menyusun rincian tagihan per individu dan membuka Android Share Sheet secara langsung ke WhatsApp atau aplikasi perpesanan lain.

### FEAT-15: Budget Limit & Overbudget Alerts
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `BudgetScreen.kt`
* **ViewModel / Handler:** `FinanceViewModel`
* **Persistensi / API:** Room Database `budget_limits` table
* **Deskripsi:** Penetapan pagu batas pengeluaran bulanan per kategori dengan bilah progres dinamis dan peringatan dini saat penggunaan dana mendekati limit pagu.

### FEAT-16: PDF & CSV Financial Export
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `ExportScreen.kt`
* **ViewModel / Handler:** `ReportExporter`
* **Persistensi / API:** Android `PdfDocument`, CSV writer
* **Deskripsi:** Pembuatan dan pengunduhan laporan keuangan formal dalam format dokumen PDF bertata letak rapi dan file lembar data CSV tabular untuk keperluan audit pribadi atau pelaporan.

### FEAT-17: Dynamic App Icon Switcher
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `SettingsScreen.kt`, `GamificationHubDialog.kt`
* **ViewModel / Handler:** `AppIconManager`
* **Persistensi / API:** Android `activity-alias` PackageManager toggle
* **Deskripsi:** Penggantian ikon launcher aplikasi secara dinamis (Default, Gold Master, Cyberpunk Edition) sebagai bentuk apresiasi dan penghargaan pencapaian level gamifikasi pengguna.

### FEAT-18: Gamification
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `GamificationHubDialog.kt`
* **ViewModel / Handler:** `AuthViewModel`
* **Persistensi / API:** SharedPreferences state
* **Deskripsi:** Sistem gamifikasi lengkap: pelacakan streak pencatatan harian berturut-turut, perolehan XP, kenaikan level pengguna, skor kesehatan finansial (0–100), dan penganugerahan lencana prestasi (*badges* seperti "Budget Saver", "Master Tracker").

### FEAT-19: Financial Personality Quiz
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `PersonalityQuizModalDialog.kt`
* **ViewModel / Handler:** `GamificationManager`
* **Persistensi / API:** SharedPreferences state
* **Deskripsi:** Kuis kepribadian finansial interaktif yang menganalisis profil dan kecenderungan psikologis pengguna dalam mengelola uang (misal: "Tipe Penghemat Waspada", "Tipe Investor Berani").

### FEAT-20: Duress PIN & Stealth Security
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `DuressPinModalDialog.kt`, `SecuritySettingsDialog.kt`
* **ViewModel / Handler:** `AuthViewModel`
* **Persistensi / API:** SharedPreferences state
* **Deskripsi:** Sistem keamanan ganda tingkat tinggi: PIN Darurat (*Duress PIN*) yang membuka profil dompet tiruan bersaldo minim saat pengguna di bawah paksaan, serta *Stealth Mode* yang menyamarkan antarmuka menjadi kalkulator standar fungsional.

### FEAT-21: Theme Engine
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `SettingsScreen.kt`
* **ViewModel / Handler:** `ThemeEngine`
* **Persistensi / API:** SharedPreferences + Jetpack Compose Theme
* **Deskripsi:** Mesin pengatur tema antarmuka visual dinamis yang mendukung Mode Terang (*Light*), Mode Gelap (*Dark*), dan Tema Khusus Cyberpunk Neon Edition.

### FEAT-22: Custom Wallpaper & Opacity
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `SettingsScreen.kt`
* **ViewModel / Handler:** `ThemeEngine`
* **Persistensi / API:** SharedPreferences + Coil Image Loader
* **Deskripsi:** Kustomisasi gambar latar belakang (*wallpaper*) antarmuka aplikasi dari galeri pengguna dengan pengaturan tingkat transparansi opasitas (*alpha*) agar teks tetap kontras dan mudah dibaca.

### FEAT-23: Multi-Currency Formatter
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `SettingsScreen.kt`
* **ViewModel / Handler:** `CurrencyFormatter`
* **Persistensi / API:** `AppCurrency` (IDR Rupiah default, USD, EUR, SGD, MYR)
* **Deskripsi:** Pemformatan simbol mata uang dan pemisah ribuan otomatis yang beradaptasi dengan preferensi pengguna pada seluruh saldo dan mutasi transaksi.

### FEAT-24: Auto Recurring Transactions
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `SettingsScreen.kt`
* **ViewModel / Handler:** `RecurringTransactionWorker`
* **Persistensi / API:** AndroidX WorkManager + Room DB
* **Deskripsi:** Penjadwalan transaksi berulang otomatis (harian, mingguan, bulanan) di latar belakang untuk pengeluaran rutin terencana seperti tagihan listrik, internet, dan biaya langganan.

### FEAT-25: Self-Hosted In-App APK Updates
* **Status Implementasi:** **EXISTING (VERIFIED - Sideload Build Flow)**
* **UI Entry Point:** `SettingsScreen.kt`, `UpdateAvailableBottomSheet.kt`
* **ViewModel / Handler:** `UpdateViewModel`
* **Persistensi / API:** `DriveUpdateRepository`, OkHttp, Android FileProvider
* **Deskripsi:** Pengecekan versi rilis APK terbaru dari manifest Drive publik dan pembaruan in-app mandiri via Android FileProvider tanpa ketergantungan pada Google Play Store.

### FEAT-26: Master Dev APK Publisher
* **Status Implementasi:** **PARTIAL (VERIFIED - Sandbox Mode)**
* **UI Entry Point:** `MasterDevPanelSheet.kt`, `PublishUpdateBottomSheet.kt`
* **ViewModel / Handler:** `MasterDevViewModel`, `GoogleDrivePublisherEngine`
* **Persistensi / API:** Local sandbox emulation
* **Deskripsi:** Panel utilitas khusus pengembang untuk mengunggah berkas APK baru dan menerbitkan pembaruan rilis pada lingkungan penyimpanan simulasi lokal.

### FEAT-27: Google Drive Cloud Backup/Restore
* **Status Implementasi:** **PARTIAL (Local Sandbox Simulation)**
* **UI Entry Point:** `SettingsScreen.kt`
* **ViewModel / Handler:** `GoogleDriveService`
* **Persistensi / API:** Local sandbox filesystem (`context.filesDir/Google_Drive_Root/FinSmart_Backups`)
* **Deskripsi:** Pencadangan dan pemulihan data aplikasi berarsitektur Dual-Format v1/v2 (AES-GCM-256 terautentikasi): mendukung Cadangan Perangkat Lokal via AndroidKeyStore serta Cadangan Portabel lintas-perangkat terproteksi kata sandi kustom pengguna (PBKDF2), dengan simulasi penyimpanan pada direktori sandbox lokal sebagai validasi arsitektur sebelum integrasi Google Drive REST API OAuth riil di masa depan.

### FEAT-28: Multi-Tier Subscriptions & Paywall
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `SmartMasterPaywallDialog.kt`
* **ViewModel / Handler:** `AuthViewModel`
* **Persistensi / API:** Local Role / Trial validation SharedPreferences (`IS_PLAY_STORE_BUILD` toggle)
* **Deskripsi:** Antarmuka penawaran langganan fitur premium Smart Master dengan adaptasi otomatis: alur transfer DANA manual pada build distribusi mandiri dan slot UI siap Google Play Billing pada build Play Store.

### FEAT-29: AdMob Banner & Rewarded Pass
* **Status Implementasi:** **EXISTING (TEST MODE ONLY)**
* **UI Entry Point:** `AdMobBanner.kt`, `SmartMasterPaywallDialog.kt`
* **ViewModel / Handler:** `AdManager`
* **Persistensi / API:** Google Play Services Ads SDK 23.4.0 (Google Official Test Unit IDs)
* **Deskripsi:** Penayangan iklan banner dan rewarded video pass untuk menambah kuota harian inferensi AI bagi pengguna gratis menggunakan unit ID uji resmi Google.

### FEAT-30: Account Deletion
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `DeleteAccountDialog.kt`
* **ViewModel / Handler:** `AuthViewModel`
* **Persistensi / API:** Room Database wipe & SharedPreferences clear
* **Deskripsi:** Mekanisme pembersihan data tuntas (*Right to Erasure*) yang menghapus seluruh catatan di 7 tabel Room Database serta mereset seluruh SharedPreferences dalam satu ketukan.

### FEAT-31: User Feedback & Dev Viewer
* **Status Implementasi:** **EXISTING (VERIFIED)**
* **UI Entry Point:** `FeedbackScreen.kt`, `DevFeedbackViewerScreen.kt`
* **ViewModel / Handler:** `FeedbackViewModel`
* **Persistensi / API:** Room Database `feedbacks` table
* **Deskripsi:** Modul pengiriman ulasan, rating bintang, dan laporan kendala teknis pengguna serta layar penampil riwayat umpan balik lokal bagi pengembang.

### FEAT-32: AI Debt Payoff Strategy Planner
* **Status Implementasi:** **PARTIAL (In-Memory State)**
* **UI Entry Point:** `DebtPlannerModalDialog.kt`
* **ViewModel / Handler:** `DebtPlannerViewModel`
* **Persistensi / API:** In-memory state `_debtList` (Belum terhubung ke tabel entitas Room terpisah)
* **Deskripsi:** Perencana pelunasan utang cerdas berbasis strategi Bola Salju (*Debt Snowball*) dan Longsoran Salju (*Debt Avalanche*) dengan kalkulator simulasi jadwal bebas utang.

### FEAT-33: Asset & Depreciation Tracker
* **Status Implementasi:** **PARTIAL (In-Memory State)**
* **UI Entry Point:** `AssetTrackerModalDialog.kt`
* **ViewModel / Handler:** Compose local state (`AssetItem`, `AssetCategory`)
* **Persistensi / API:** In-memory state (Belum terhubung ke tabel entitas Room terpisah)
* **Deskripsi:** Pencatatan aset kepemilikan bernilai tinggi (elektronik, kendaraan, perhiasan) dan estimasi penyusutan nilai tahunan (*depreciation rate*).

### FEAT-34: Thermal Merchant Receipt Generator
* **Status Implementasi:** **PARTIAL (Monospace Simulation)**
* **UI Entry Point:** `ReceiptGeneratorModalDialog.kt`
* **ViewModel / Handler:** Compose local state
* **Persistensi / API:** Monospace UI styling + simulasi koneksi Bluetooth thermal printer
* **Deskripsi:** Generator cetak struk nota belanja bertampilan tipografi monospace untuk pencetakan bukti transaksi via printer thermal portabel.

### FEAT-35: Notification Center
* **Status Implementasi:** **PARTIAL (Component Available)**
* **UI Entry Point:** `NotificationCenterBottomSheet.kt`
* **ViewModel / Handler:** `AuthViewModel`
* **Persistensi / API:** Komponen UI tersedia dalam basis kode, pemanggilan aktif dalam `FinSmartApp.kt` belum sepenuhnya di-render
* **Deskripsi:** Lembar pusat notifikasi terpadu untuk menampilkan riwayat pengingat transaksi, peringatan anggaran, dan pengumuman pembaruan aplikasi.

### FEAT-36: Shake Gesture Detector
* **Status Implementasi:** **PLANNED (Helper Function Available)**
* **UI Entry Point:** `rememberShakeDetector` di [`ShakeDetector.kt`](file:///e:/FinSmart/app/src/main/java/com/example/util/ShakeDetector.kt)
* **ViewModel / Handler:** Helper sensor akselerometer Compose
* **Persistensi / API:** SensorManager Android
* **Deskripsi:** Pendeteksi goyangan fisik perangkat (*shake gesture*) menggunakan sensor akselerometer perangkat untuk memicu aksi kilat seperti membuka lembar transaksi baru atau mengaktifkan Stealth Mode seketika.

---

## 5. Non-Functional Requirements

### 5.1 Performa & Efisiensi Sistem (Performance Targets)
* **Ketersediaan Offline (Offline Availability):** Seluruh operasi transaksi lokal, manajemen dompet, perhitungan anggaran, kalkulasi split bill, dan pemindaian regex wajib beroperasi 100% tanpa internet.
* **Target Latensi OCR (SLA Target):** Pemrosesan nota struk ditargetkan selesai rata-rata $\le 3\text{ detik}$ pada kondisi koneksi 4G/Wi-Fi yang stabil. Batas maksimal pembacaan jaringan (*read timeout*) dikunci pada 30 detik untuk mencegah proses menggantung.
* **Alokasi Memori (RAM Footprint):** Penggunaan memori operasional aplikasi dijaga $< 150\text{ MB}$ saat idle dan $< 250\text{ MB}$ saat memproses citra kamera beresolusi tinggi (melalui kompresi bitmap otomatis sebelum pengiriman API).
* **Waktu Startup Aplikasi:** Waktu inisialisasi dingin (*cold start*) $< 1,5\text{ detik}$ pada perangkat kelas menengah (*mid-range Android*).

### 5.2 Keandalan & Integritas Data (Reliability)
* **Integritas Transaksional Basis Data:** Seluruh operasi majemuk (seperti transfer saldo antar-dompet yang melibatkan pemotongan dompet A dan penambahan dompet B) wajib dijalankan di dalam blok transaksi Room `@Transaction` atomic.
* **Crash-Free Ratio Target:** Menjaga tingkat sesi bebas error $\ge 99,8\%$.
* **Pencegahan Kehilangan Data Migrasi:** Menghapus seluruh opsi migrasi destruktif (`fallbackToDestructiveMigration()`) untuk melindungi data pengguna dari penghapusan massal saat terjadi peningkatan versi skema di masa depan.

### 5.3 Keamanan & Kriptografi (Security Benchmarks)
* **Hashing Kredensial:** Algoritma hashing sandi dan PIN wajib menggunakan `PBKDF2WithHmacSHA256` dengan konfigurasi:
  * Jumlah Iterasi: 12.000 iterasi.
  * Panjang Kunci: 256 bit.
  * Panjang Salt: 16 byte cryptographically secure random.
* **Enkripsi Data Sensitif:** Seluruh data cadangan menggunakan arsitektur Dual-Format v1/v2. Format v2 terenkripsi secara terautentikasi menggunakan `AES/GCM/NoPadding` (256-bit, nonce acak 12-byte, tag otentikasi 128-bit) dengan kunci `AndroidKeyStore` untuk Cadangan Perangkat Lokal dan `PBKDF2WithHmacSHA256` (100.000 iterasi, salt 16-byte) untuk Cadangan Portabel yang dilindungi kata sandi pengguna, serta tetap menyediakan pemulihan backward-compatible bagi cadangan warisan Format v1 (`AES/CBC/PKCS5Padding`).
* **Segregasi Kredensial:** Dilarang keras menempatkan API key atau rahasia produksi secara hardcoded di dalam repositori source code Kotlin/Java.

### 5.4 Aksesibilitas & Kompatibilitas Sistem
* **Kompatibilitas Sistem Operasi:** Mendukung sistem operasi mulai dari Android 7.0 (Nougat / API Level 24) hingga Android 16 (Vanilla Ice Cream / API Level 36).
* **Aksesibilitas Visual:** Menyediakan pengaturan skala ukuran teks dinamis serta rasio kontras warna memadai pada antarmuka Light, Dark, dan Cyberpunk untuk memenuhi target kemudahan keterbacaan.

---

## 6. System Architecture & Tech Stack

### 6.1 Diagram Arsitektur Aplikasi
FinSmart mengadopsi pola arsitektur **Offline-First Clean Architecture** yang dipadukan dengan **Model-View-ViewModel (MVVM)** dan **Unidirectional Data Flow (UDF)**:

```
+-------------------------------------------------------------------------+
|                           PRESENTATION LAYER                            |
|  - Jetpack Compose UI (12 Screens, 17 Modals, Material 3 Design)        |
|  - Theme System (Light, Dark, Cyberpunk) & Speed-Dial Navigation        |
+------------------------------------+------------------------------------+
                                     | (UI Events / User Intents)
                                     v
+-------------------------------------------------------------------------+
|                             VIEWMODEL LAYER                             |
|  - AndroidX Lifecycle ViewModels (StateFlow / SharedFlow)               |
|  - Manual ViewModel Factories & Singleton Service Locators              |
+------------------------------------+------------------------------------+
                                     | (Repository Abstractions)
                                     v
+-------------------------------------------------------------------------+
|                                DATA LAYER                               |
|  [Local Persistence]                       [Remote AI & AdMob Gateway]  |
|  - Room Database v5 (SQLite)               - Retrofit 2.11.0            |
|  - 7 Entities & DAOs                       - Moshi JSON Converter       |
|  - Encrypted SharedPreferences             - Google Gemini 2.0 Flash    |
|  - WorkManager 2.9.1 (Reminders)           - AdMob SDK 23.4.0 (Test)    |
|  - Local Sandbox Storage                   - Android SpeechRecognizer   |
+-------------------------------------------------------------------------+
```

### 6.2 Teknologi & Pustaka Terverifikasi (Verified Tech Stack)

| Komponen Teknis | Pustaka / Versi Terverifikasi | Bukti Fisik Repositori | Keterangan & Catatan Arsitektur |
| :--- | :--- | :--- | :--- |
| **Bahasa Pemrograman** | Kotlin 2.0.21 | [`build.gradle.kts:L3`](file:///e:/FinSmart/build.gradle.kts#L3) | Kotlin modern dengan Compose Compiler terintegrasi. |
| **Target & Compile SDK** | Android SDK 36 (Android 16) | [`app/build.gradle.kts:L12`](file:///e:/FinSmart/app/build.gradle.kts#L12) | Kompatibel penuh dengan spesifikasi Android 16 terkini. |
| **Minimum SDK** | Android SDK 24 (Android 7.0) | [`app/build.gradle.kts:L14`](file:///e:/FinSmart/app/build.gradle.kts#L14) | Menjangkau $\ge 95\%$ perangkat aktif di Indonesia. |
| **UI Framework** | Jetpack Compose BOM 2024.10.01 | [`app/build.gradle.kts:L81`](file:///e:/FinSmart/app/build.gradle.kts#L81) | Komponen UI deklaratif Material 3 modern. |
| **Navigasi** | Navigation Compose 2.8.3 | [`app/build.gradle.kts:L78`](file:///e:/FinSmart/app/build.gradle.kts#L78) | Navigasi Single Activity berbasis rute string bertipe aman. |
| **State Management** | Lifecycle ViewModel 2.8.6 | [`app/build.gradle.kts:L73`](file:///e:/FinSmart/app/build.gradle.kts#L73) | UDF dengan Kotlin Coroutines & StateFlow. |
| **Basis Data Lokal** | Room Database 2.6.1 | [`app/build.gradle.kts:L94`](file:///e:/FinSmart/app/build.gradle.kts#L94) | SQLite ORM dengan `exportSchema = true` dan schema v5. |
| **Jaringan & REST API** | Retrofit 2.11.0 + OkHttp 4.12.0 | [`app/build.gradle.kts:L100`](file:///e:/FinSmart/app/build.gradle.kts#L100) | Klien HTTP untuk komunikasi dengan Google Gemini API. |
| **Serialisasi JSON** | Moshi 1.15.1 (via KSP 2.0.21) | [`app/build.gradle.kts:L102`](file:///e:/FinSmart/app/build.gradle.kts#L102) | Parsing payload JSON berkecepatan tinggi tanpa refleksi. |
| **Background Scheduling** | AndroidX WorkManager 2.9.1 | [`app/build.gradle.kts:L107`](file:///e:/FinSmart/app/build.gradle.kts#L107) | Penjadwalan pengingat transaksi berulang dan backup. |
| **Model AI Multimodal** | Google Gemini 2.0 Flash | [`GeminiApiService.kt:L20`](file:///e:/FinSmart/app/src/main/java/com/example/data/remote/gemini/GeminiApiService.kt#L20) | Endpoint model `gemini-2.0-flash` untuk NLP dan Vision OCR. |
| **SDK Iklan Monitisasi** | Google Mobile Ads (AdMob) 23.4.0 | [`app/build.gradle.kts:L109`](file:///e:/FinSmart/app/build.gradle.kts#L109) | Dikonfigurasi dalam Test Mode menggunakan ID resmi Google. |
| **Dependency Injection** | Manual Factory / Service Locator | Repositori Aktual | Menggunakan injeksi manual tanpa overhead refleksi Hilt/Dagger. |

### 6.3 Klarifikasi Teknologi Usang (Obsolete Architecture Cleanup)
* Repositori **TIDAK MENGGUNAKAN** dan telah mengeliminasi ketergantungan pada:
  1. *Hilt / Dagger:* FinSmart menggunakan pola ViewModel Factory manual yang ringan.
  2. *SQLCipher:* Basis data dikelola langsung oleh Room SQLite lokal dengan pengamanan enkripsi tingkat data via `EncryptionUtils`.
  3. *Custom Backend REST API:* Tidak ada server backend kustom `/v1/auth` atau `/v1/sync`; FinSmart adalah aplikasi terdistribusi mandiri (*standalone offline-first*).

---

## 7. UI/UX Design Guidelines & User Flow

### 7.1 Daftar Layar Resmi (12 Verified Screens)
Sistem antarmuka FinSmart terdiri dari tepat **12 layar Compose** yang terletak pada direktori [`app/src/main/java/com/example/ui/screens/`](file:///e:/FinSmart/app/src/main/java/com/example/ui/screens/):

1. **`DashboardScreen`:** Dashboard utama ringkasan saldo total, mutasi terkini, grafik arus kas, dan pill switcher mode Personal/Business.
2. **`TransactionsScreen`:** Daftar riwayat mutasi transaksi lengkap dengan filter kategori, rentang waktu, dan mode akun.
3. **`WalletsScreen`:** Manajemen akun dompet multi-currency, saldo independen, dan formulir transfer dana antar-dompet.
4. **`AiChatScreen`:** Antarmuka percakapan hibrida dua tab: Tab 1 untuk Natural Language AI Expense Input dan Tab 2 untuk AI Financial Health Advisor.
5. **`ReceiptScanScreen`:** Antarmuka pemindaian kamera dan pratinjau bukti nota fisik dengan ekstraksi Gemini 2.0 Flash Vision OCR.
6. **`AnalyticsScreen`:** Visualisasi analisis keuangan mendalam, diagram arus kas, dan perincian kategori pengeluaran.
7. **`BudgetScreen`:** Pengelolaan pagu anggaran per kategori dengan indikator visual dan peringatan overbudget.
8. **`SplitBillScreen`:** Kalkulator pembagian tagihan proporsional multi-partisipan lengkap dengan ekspor ringkasan ke WhatsApp.
9. **`ExportScreen`:** Modul ekspor laporan keuangan ke format dokumen PDF dan CSV.
10. **`FeedbackScreen`:** Formulir pengiriman ulasan, rating bintang, dan laporan kendala teknis pengguna.
11. **`DevFeedbackViewerScreen`:** Layar peninjau daftar umpan balik dan laporan bug bagi pengembang lokal.
12. **`SettingsScreen`:** Pusat konfigurasi: tema visual, wallpaper & opasitas, mata uang, PIN/Biometrik, backup sandbox, update APK, dan reset akun.

### 7.2 Daftar Modal Dialog & Bottom Sheets Resmi (17 Verified Modals)
Terdapat tepat **17 komponen dialog interaktif** yang terletak pada direktori [`app/src/main/java/com/example/ui/components/`](file:///e:/FinSmart/app/src/main/java/com/example/ui/components/):

1. **`InitialMasterSetupDialog`:** Wizard inisialisasi awal saat aplikasi pertama kali dijalankan (Master Setup).
2. **`AddTransactionSheet`:** Bottom sheet formulir penambahan dan pengeditan transaksi/transfer dana.
3. **`AuthModalDialog`:** Dialog otentikasi login pengguna dan pemilihan mode tamu.
4. **`DuressPinModalDialog`:** Panel konfigurasi dan aktivasi PIN darurat (*Duress PIN*).
5. **`ChangePasswordDialog`:** Formulir penggantian kata sandi akun pengguna.
6. **`DebtPlannerModalDialog`:** Dialog perencanaan strategi pelunasan utang cerdas (*Snowball / Avalanche*).
7. **`AssetTrackerModalDialog`:** Dialog pencatatan aset bernilai tinggi dan kalkulasi depresiasi.
8. **`ReceiptGeneratorModalDialog`:** Dialog pembuat struk nota belanja bertampilan thermal monospace.
9. **`SplitBillDialog`:** Formulir rincian pembagian tagihan per anggota dan pembagian pajak.
10. **`SmartMasterPaywallDialog`:** Dialog penawaran langganan fitur premium (Smart Master).
11. **`GamificationHubDialog`:** Hub pusat lencana prestasi (*badges*), streak harian, dan level XP.
12. **`PersonalityQuizModalDialog`:** Dialog kuis kepribadian finansial interaktif.
13. **`NotificationCenterBottomSheet`:** Bottom sheet pusat notifikasi dan riwayat pemberitahuan.
14. **`MasterDevPanelSheet`:** Panel utilitas pengembang untuk simulasi pengujian dan publikasi APK.
15. **`PublishUpdateBottomSheet`:** Lembar dialog konfirmasi publikasi versi rilis pembaruan APK.
16. **`ProminentDisclosureDialog`:** Dialog pengungkapan wajib izin kamera & notifikasi sesuai standar Google Play.
17. **`DeleteAccountDialog`:** Dialog konfirmasi penghapusan seluruh data akun secara permanen (GDPR Wipe).

### 7.3 Sistem Desain Visual & Navigasi
* **Material 3 Tokens:** Menggunakan palet dinamis Material 3 dengan kurva sudut membulat modern (*rounded corners 16dp–24dp*) dan elevasi berbasis tonal color.
* **Sistem Tema:**
  * *Light Theme:* Latar putih bersih dengan kontras teks gelap tajam.
  * *Dark Theme:* Latar abu-abu gelap amoled ramah baterai dan nyaman di mata saat malam hari.
  * *Cyberpunk Theme:* Aksen neon magenta dan cyan berlatar hitam pekat untuk estetika futuristik.
* **Speed-Dial Floating Action Button (FAB):** Tombol aksi mengambang utama di pojok kanan bawah yang saat diketuk akan menganimasikan 4 sub-tombol pintasan kilat:
  1. *Catat Transaksi Manual* (Ikon Edit)
  2. *Scan Struk Belanja OCR* (Ikon Kamera)
  3. *Input Suara / NLP* (Ikon Mic)
  4. *Konsultasi AI Advisor* (Ikon Robot)

---

## 8. Data Models & Database Schema

### 8.1 Spesifikasi Room Database Version 5
* **Basis Data:** Room Database
* **Versi Skema:** `5`
* **Schema Identity Hash:** `75cddc91b1ebddcdeb8aea344f7996bf`
* **Berkas Sumber Skema Terkunci:** [`app/schemas/com.example.data.local.FinSmartDatabase/5.json`](file:///e:/FinSmart/app/schemas/com.example.data.local.FinSmartDatabase/5.json)
* **Status Ekspor Skema:** `exportSchema = true` aktif di `app/build.gradle.kts`.
* **Kebijakan Migrasi:** Destructive migration fallback telah **DIHAPUS**. Registri migrasi eksplisit (`MIGRATIONS = emptyArray()`) disiapkan untuk menampung migrasi teruji saat versi basis data dinaikkan di masa depan.

### 8.2 Definisi 7 Tabel Entitas Resmi (100% Sesuai `5.json`)

#### Tabel 1: `wallets`
Menyimpan data dompet dan rekening pengguna.
```sql
CREATE TABLE IF NOT EXISTS `wallets` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `name` TEXT NOT NULL,
  `type` TEXT NOT NULL,
  `accountNumber` TEXT NOT NULL,
  `balance` REAL NOT NULL,
  `colorHex` TEXT NOT NULL,
  `iconName` TEXT NOT NULL,
  `accountMode` TEXT NOT NULL,
  `isDefault` INTEGER NOT NULL,
  `createdAt` INTEGER NOT NULL
);
```

#### Tabel 2: `transactions`
Menyimpan data transaksi pemasukan, pengeluaran, dan transfer.
```sql
CREATE TABLE IF NOT EXISTS `transactions` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `title` TEXT NOT NULL,
  `amount` REAL NOT NULL,
  `type` TEXT NOT NULL,
  `category` TEXT NOT NULL,
  `categoryIcon` TEXT NOT NULL,
  `walletId` INTEGER NOT NULL,
  `walletName` TEXT NOT NULL,
  `targetWalletId` INTEGER,
  `targetWalletName` TEXT,
  `timestamp` INTEGER NOT NULL,
  `note` TEXT NOT NULL,
  `tags` TEXT NOT NULL,
  `accountMode` TEXT NOT NULL
);
```

#### Tabel 3: `categories`
Menyimpan daftar kategori transaksi (36 kategori bawaan dan kategori kustom).
```sql
CREATE TABLE IF NOT EXISTS `categories` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `name` TEXT NOT NULL,
  `type` TEXT NOT NULL,
  `iconName` TEXT NOT NULL,
  `colorHex` TEXT NOT NULL
);
```

#### Tabel 4: `users`
Menyimpan data profil akun pengguna lokal dan status langganan.
```sql
CREATE TABLE IF NOT EXISTS `users` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `username` TEXT NOT NULL,
  `email` TEXT NOT NULL,
  `passwordHash` TEXT NOT NULL,
  `role` TEXT NOT NULL,
  `createdAt` INTEGER NOT NULL,
  `trialStartDate` INTEGER NOT NULL,
  `subscriptionExpiresAt` INTEGER,
  `isApproved` INTEGER NOT NULL,
  `isGuest` INTEGER NOT NULL
);
```

#### Tabel 5: `split_bill_history`
Menyimpan riwayat pembagian tagihan makan/belanja bersama.
```sql
CREATE TABLE IF NOT EXISTS `split_bill_history` (
  `id` TEXT NOT NULL,
  `merchantName` TEXT NOT NULL,
  `date` TEXT NOT NULL,
  `totalAmount` REAL NOT NULL,
  `formattedText` TEXT NOT NULL,
  `timestamp` INTEGER NOT NULL,
  PRIMARY KEY(`id`)
);
```

#### Tabel 6: `budget_limits`
Menyimpan batasan pagu anggaran bulanan per kategori.
```sql
CREATE TABLE IF NOT EXISTS `budget_limits` (
  `id` TEXT NOT NULL,
  `limitAmount` REAL NOT NULL,
  `isCategory` INTEGER NOT NULL,
  PRIMARY KEY(`id`)
);
```

#### Tabel 7: `feedbacks`
Menyimpan ulasan, rating, dan laporan kendala teknis pengguna secara lokal.
```sql
CREATE TABLE IF NOT EXISTS `feedbacks` (
  `id` TEXT NOT NULL,
  `userName` TEXT NOT NULL,
  `userEmail` TEXT NOT NULL,
  `rating` INTEGER NOT NULL,
  `category` TEXT NOT NULL,
  `message` TEXT NOT NULL,
  `timestamp` INTEGER NOT NULL,
  PRIMARY KEY(`id`)
);
```

---

## 9. API & Third-Party Integration

### 9.1 Integrasi Google Gemini 2.0 Flash (REST API)
* **Model:** `gemini-2.0-flash`
* **Penyedia Layanan:** Google AI (Generative Language API Gateway).
* **Endpoint URL:** `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent`
* **Metode HTTP:** `POST` via Retrofit 2.11.0.
* **Format Kredensial:** Otentikasi query parameter `?key=GEMINI_API_KEY`.
* **Arsitektur Aliran Kredensial yang Aman:**
  ```
  .env lokal (GEMINI_API_KEY=...)
      | (Diabaikan oleh .gitignore)
      v
  secrets-gradle-plugin (Membaca file konfigurasi saat build)
      |
      v
  BuildConfig.GEMINI_API_KEY (Konstanta bytecode terisolasi)
      |
      v
  GeminiClient.kt / GeminiApiService.kt (Runtime Retrofit Client)
  ```
* **Kasus Penggunaan:**
  1. *Natural Language Parsing:* Mengonversi kalimat percakapan bebas menjadi payload data transaksi terstruktur (`FEAT-08`).
  2. *Multimodal Vision OCR:* Mengekstraksi nota fisik struk belanja (Merchant, Tanggal, Total, Rincian Baris Bukti Transaksi) dari gambar Base64 (`FEAT-12`).
  3. *Financial Advisor Chatbot:* Memberikan analisis finansial interaktif berbasis konteks riwayat saldo pengguna (`FEAT-11`).

### 9.2 Integrasi Iklan Google Mobile Ads (AdMob)
* **Status Lingkungan:** **TEST / DEVELOPMENT MODE (`FEAT-29`).**
* **ID Unit Iklan Resmi:** Menggunakan Google Official Sample Test Ad Unit IDs:
  * Banner Test Ad: `ca-app-pub-3940256099942544/6300978111`
  * Interstitial Test Ad: `ca-app-pub-3940256099942544/1033173712`
  * Rewarded Video Test Ad: `ca-app-pub-3940256099942544/5224354917`
* **Kebijakan Monitisasi:** Penayangan iklan dibatasi agar tidak mengganggu alur pencatatan pengguna. Pengguna dapat menonton Rewarded Video untuk menambah kuota prompt Gemini harian. Tidak ada akun publisher produksi yang diklaim aktif pada baseline saat ini.

### 9.3 Integrasi Google Drive (Local Sandbox Simulation)
* **Status Implementasi:** **SIMULASI SANDBOX LOKAL (BUKAN REAL OAUTH) (`FEAT-27`).**
* **Mekanisme:** Komponen [`GoogleDriveBackupRepository.kt`](file:///e:/FinSmart/app/src/main/java/com/example/data/repository/GoogleDriveBackupRepository.kt) menulis dan membaca berkas cadangan ke direktori internal terisolasi `context.filesDir/Google_Drive_Root/FinSmart_Backups/`.
* **Rencana Masa Depan:** Mengganti repositori simulasi ini dengan Google Drive REST API OAuth riil saat integrasi cloud penuh dieksekusi pada rilis mendatang.

### 9.4 Integrasi Google Sign-In (Mock Simulation)
* **Status Implementasi:** **SIMULASI OFFLINE MOCK.**
* **Mekanisme:** Komponen [`MockGoogleAuthManager.kt`](file:///e:/FinSmart/app/src/main/java/com/example/util/MockGoogleAuthManager.kt) mensimulasikan status akun terautentikasi untuk kebutuhan pengujian antarmuka tanpa menyematkan pustaka Google Identity Services atau Firebase Auth.

### 9.5 Kesiapan In-App Purchase (IAP Architectural Readiness)
* **Status Implementasi:** **KESIAPAN ARSITEKTUR & PEMISAHAN UI (BELUM MENGHUBUNGKAN BILLING SDK) (`FEAT-28`).**
* **Mekanisme:** Variabel konfigurasi `AppConfig.IS_PLAY_STORE_BUILD` memisahkan alur penawaran langganan: jika bernilai `true`, antarmuka menyiapkan slot untuk Google Play Billing; jika bernilai `false`, antarmuka menampilkan instruksi pembayaran mandiri (DANA manual). Pustaka `com.android.billingclient:billing` belum diikutsertakan dalam `app/build.gradle.kts`.

---

## 10. Analytics & Telemetry Metrics

### 10.1 Kebijakan Tanpa Pelacakan Eksternal (Zero Third-Party Telemetry)
Demi melindungi kedaulatan data dan privasi mutlak finansial pengguna:
* FinSmart **TIDAK MENGGUNAKAN** dan **TIDAK MENGHUBUNGKAN** SDK pelacakan perilaku pihak ketiga seperti Google Analytics for Firebase, Mixpanel, AppsFlyer, atau Facebook App Events.
* Tidak ada pengiriman data telemetri penggunaan aplikasi, lokasi GPS, kontak telepon, atau rincian saldo ke server analitik manapun.

### 10.2 Metrik Kinerja Finansial Internal Pengguna
Aplikasi menghitung metrik internal secara lokal untuk membantu pengguna memonitor kebiasaan keuangannya sendiri:
1. **Gamification Health Score (0–100):** Dihitung berdasarkan kepatuhan pada batas anggaran, rasio tabungan $\ge 20\%$, dan streak pencatatan aktif (`FEAT-18`).
2. **Savings Rate Metric:** Persentase sisa dana bersih bulanan terhadap total pemasukan:
   $$\text{Savings Rate} = \frac{\text{Total Pemasukan} - \text{Total Pengeluaran}}{\text{Total Pemasukan}} \times 100\%$$
3. **Local Feedback Store:** Ulasan dan keluhan yang dikirimkan melalui dialog bantuan disimpan secara lokal di tabel `feedbacks` untuk keperluan audit internal atau diagnostik pengembang (`FEAT-31`).

---

## 11. Security & Compliance

### 11.1 Hashing Sandi & Kredensial (PBKDF2 Engine)
* **Algoritma:** `PBKDF2WithHmacSHA256`
* **Jumlah Iterasi:** **12.000 iterasi** (diterapkan pada [`PasswordHasher.kt:L12`](file:///e:/FinSmart/app/src/main/java/com/example/util/PasswordHasher.kt#L12)).
* **Ukuran Kunci:** 256 bit.
* **Garam Kriptografi (Salt):** 16 byte acak per pengguna yang dihasilkan oleh `SecureRandom`.
* **Ketahanan:** Mengamankan PIN Master dan kata sandi dari serangan berbasis *rainbow table* dan komputasi GPU berkecepatan tinggi.

### 11.2 Enkripsi Data Simetris (AES-256 Engine)
* **Algoritma:** `AES/CBC/PKCS5Padding`
* **Derivasi Kunci:** SHA-256 menghasilkan kunci enkripsi simetris 256-bit.
* **Penerapan:** Mengamankan konten berkas ekspor cadangan (*Encrypted Backup*) sebelum disimpan ke penyimpanan eksternal atau dipindahkan antar-perangkat.

### 11.3 Mekanisme Keamanan Operasional & Penyamaran
1. **BiometricPrompt API:** Otentikasi sidik jari atau pemindai wajah berbasis subsistem biometrik resmi Android yang tidak menyimpan citra biometrik di level aplikasi.
2. **Stealth Mode (Kalkulator Desepsi):** Mengubah seluruh antarmuka depan menjadi kalkulator aritmatika fungsional. Pengguna yang tidak mengetahui kode PIN rahasia hanya akan melihat aplikasi kalkulator biasa (`FEAT-20`).
3. **Duress PIN Defense System:** Jika pengguna dipaksa oleh pihak lain di bawah ancaman untuk membuka aplikasi, pengguna dapat memasukkan PIN Darurat sekunder yang secara otomatis memuat data dompet dummy dengan saldo kecil (`FEAT-20`).

### 11.4 Kepatuhan Privasi Data & Regulasi Platform
* **Mekanisme Hak Penghapusan (Right to Erasure / Account Deletion):** Dialog `DeleteAccountDialog` menyediakan satu tombol eksekusi yang langsung menghapus seluruh catatan di 7 tabel Room Database serta mereset SharedPreferences secara permanen (`FEAT-30`).
* **Pengungkapan Izin Transparan (Prominent Disclosures):** Izin kamera untuk OCR dan izin notifikasi untuk pengingat diawali dengan dialog pengungkapan eksplisit yang menerangkan mengapa izin tersebut dibutuhkan, sebelum memicu prompt perizinan runtime sistem Android.
* **Rencana Penguatan Masa Depan (Planned Hardening):**
  * Migrasi kunci enkripsi master ke Android Hardware-backed Keystore pada Stage 3.
  * Penerapan Certificate Pinning pada koneksi Retrofit ke endpoint Google Gemini API.

---

## 12. Release Strategy & Rollout Plan

### 12.1 Strategi Distribusi Ganda (Dual-Track Build System)
FinSmart mendukung dua jalur distribusi aplikasi yang dikendalikan melalui flag konfigurasi arsitektur:

1. **Jalur 1: Google Play Store Release (`AppConfig.IS_PLAY_STORE_BUILD = true`)**
   * *Target Audiens:* Pengguna umum ekosistem resmi Google Play.
   * *Fitur Khusus:* Kepatuhan penuh terhadap kebijakan Google Play Developer Policy, pengungkapan izin runtime, pembaruan aplikasi melalui Google Play In-App Updates, dan slot arsitektur langganan disiapkan untuk Google Play Billing.
   * *Pembayaran:* Alur pembayaran mandiri DANA disembunyikan secara otomatis.

2. **Jalur 2: Direct APK Sideload Release (`AppConfig.IS_PLAY_STORE_BUILD = false`)**
   * *Target Audiens:* Pengguna mandiri, komunitas tester, dan distribusi langsung tanpa perantara store.
   * *Fitur Khusus:* Modul pembaruan mandiri terintegrasi (*Self-Hosted In-App Updater* / `FEAT-25`) via `ApkInstallHelper` yang memanfaatkan Android `FileProvider` untuk mengunduh dan memasang pembaruan APK secara langsung.
   * *Pembayaran:* Menampilkan alur aktivasi fitur premium Smart Master melalui transfer manual dompet digital DANA (`FEAT-28`).

### 12.2 Alur Pembaruan APK Mandiri (Self-Hosted Updater Flow)
```
Pengguna membuka Settings / Notifikasi Rilis
               |
               v
UpdateViewModel mengecek berkas manifest rilis di server/drive publik
               |
               v
Ditemukan versi lebih baru dari BuildConfig.VERSION_CODE
               |
               v
Aplikasi mengunduh file update.apk ke direktori cache internal
               |
               v
ApkInstallHelper memicu Intent ACTION_VIEW via FileProvider
               |
               v
Android Package Installer memasang pembaruan aplikasi
```

---

## 13. Risks, Assumptions & Dependencies

### 13.1 Analisis Risiko & Strategi Mitigasi

| ID Risiko | Deskripsi Risiko | Dampak | Tingkat Risiko | Strategi Mitigasi Terverifikasi |
| :--- | :--- | :--- | :--- | :--- |
| **RSK-01** | **Ketergantungan API Gemini:** Kuota gratis habis atau server Google mengalami lonjakan traffic (HTTP 429/503). | Fitur NLP dan Vision OCR tidak merespon sementara waktu. | SEDANG | Otomatis beralih ke `ExpenseAiParser` (regex lokal / `FEAT-09`) dan `OfflineAdvisorEngine` tanpa menyebabkan crash aplikasi. |
| **RSK-02** | **Konektivitas Jaringan Lambat:** Pemrosesan gambar nota struk berukuran besar memicu timeout pada koneksi seluler lemah. | Pengguna menunggu lama saat memindai nota. | SEDANG | Kompresi resolusi gambar otomatis ke format WebP/JPEG sebelum transmisi; pembatasan timeout jaringan 30 detik. |
| **RSK-03** | **Keterbatasan Backup Sandbox:** Pengguna mengira data telah tersimpan di cloud Google Drive riil padahal masih di folder sandbox lokal (`FEAT-27`). | Data bisa hilang jika pengguna melakukan format pabrik ponsel. | TINGGI | Memberikan edukasi transparan pada UI bahwa backup lokal perlu diekspor ke file eksternal via tombol "Export File". |
| **RSK-04** | **Eksposur Kredensial Sisi Klien:** Risiko ekstraksi API key dari APK terkompilasi melalui dekompilasi reverse engineering. | Penyalahgunaan kuota API Gemini oleh pihak tak berwenang. | SEDANG | Pemisahan kredensial via `secrets-gradle-plugin` + `.env`; rekomendasi Stage 3 untuk membatasi kuota API di Google Cloud Console. |
| **RSK-05** | **Manajemen Kunci Enkripsi AES:** Kunci derivasi AES masih dikelola di lapisan utilitas aplikasi tanpa Keystore hardware. | Kunci berpotensi diekstraksi jika perangkat di-root. | RENDAH | Merencanakan migrasi master key ke Android Hardware-backed Keystore pada Stage 3. |

### 13.2 Asumsi Perancangan Sistem (Assumptions)
1. Perangkat pengguna memiliki kamera fungsional dengan resolusi minimal 5 MP untuk menghasilkan citra nota yang terbaca oleh model Vision OCR.
2. Pengguna memahami bahwa fitur kecerdasan buatan memerlukan akses data internet aktif, sedangkan seluruh fitur pembukuan transaksi tetap berfungsi saat offline.
3. Repositori mempertahankan skema Room v5 dan tidak melakukan perubahan skema tanpa migrasi eksplisit.

### 13.3 Dependensi Eksternal (Dependencies)
* Ketersediaan endpoint Google Generative Language API (`gemini-2.0-flash`).
* Ketersediaan server Google AdMob untuk penayangan iklan reward.
* Sistem operasi Android menyediakan provider `SpeechRecognizer` dan `FileProvider` standar.

---

## 14. Appendix & Changelog

### 14.1 Glosarium Istilah Teknis
* **Offline-First:** Filosofi arsitektur di mana aplikasi mengutamakan penyimpanan dan pembacaan data secara lokal di perangkat, memperlakukan jaringan internet sebagai fasilitas pelengkap.
* **UDF (Unidirectional Data Flow):** Pola aliran data searah di mana UI memancarkan event ke ViewModel, dan ViewModel memancarkan StateFlow baru ke UI.
* **PBKDF2 (Password-Based Key Derivation Function 2):** Standar kriptografi yang menerapkan fungsi pseudorandom berulang-ulang untuk memperlambat serangan brute-force terhadap sandi.
* **Room Schema Identity Hash:** Hash kriptografis unik yang dihitung oleh compiler Room untuk memverifikasi bahwa entitas kode Kotlin identik dengan skema SQLite fisik.
* **Receipt Itemized Line:** Baris rincian bukti transaksi pengeluaran konsumsi pribadi pada struk belanja (bukan stok inventaris dagang).
* **Stealth Mode:** Mekanisme penyamaran visual yang menyembunyikan fungsi asli aplikasi di balik antarmuka kalkulator standar.

### 14.2 Riwayat Perubahan Dokumen (Changelog)

| Versi | Tanggal | Penulis | Deskripsi Perubahan Dokumen |
| :--- | :--- | :--- | :--- |
| **v1.0.4** | 14 Oktober 2023 | Technical PM | Versi dasar perancangan sistem dan fungsionalitas aplikasi FinSmart awal. |
| **v1.0.5** | 24 Oktober 2023 | Technical PM | Pembaruan integrasi pipeline otomatisasi dokumentasi CI/CD. |
| **v1.0.6** | 02 November 2023 | Technical PM | Refinement Update: Standardisasi 14 bab wajib dan penyelarasan metrik retensi. |
| **v2.0.0** | 04 Oktober 2026 | Technical PM & Lead Architect | **Stage 1 PRD Baseline Finalization (Strategy B Hybrid Restore & FINSMART-ARCH-REV-001 Traceability):**<br>• Memulihkan konteks visi, persona (Rian & Maya), dan user stories berharga dari PRD historis.<br>• Mengeliminasi total klaim teknis usang (Hilt, SQLCipher, REST backend kustom).<br>• Mengunci arsitektur aktual: Room Database Version 5 (7 tabel entitas resmi sesuai `5.json`), Kotlin 2.0.21, Compile SDK 36, Compose BOM 2024.10.01, Retrofit 2.11.0, Moshi 1.15.1, WorkManager 2.9.1.<br>• Melakukan sinkronisasi ketat terhadap 36 fitur resmi (`FEAT-01` s/d `FEAT-36`) dari baseline arsitektur `FINSMART-ARCH-REV-001`.<br>• Mendokumentasikan 12 layar Compose aktual dan 17 modal dialog terverifikasi.<br>• Mengintegrasikan model kecerdasan buatan Google Gemini 2.0 Flash (NLP & Vision OCR) via `.env` + `secrets-gradle-plugin`.<br>• Mengoreksi fakta keamanan: PBKDF2WithHmacSHA256 menggunakan 12.000 iterasi aktual.<br>• Mengunci batasan ruang lingkup negatif mutlak: 0% fitur inventaris/stok UMKM.<br>• Menegaskan status simulasi lokal untuk Google Drive (sandbox) dan Google Sign-In (mock). |
