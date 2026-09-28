# Product Requirement Document (PRD) - FinSmart Android App
**Versi Dokumen:** v1.0.6  
**Status:** Disetujui (Approved)  
**Pemilik Produk:** Technical Product Manager  
**Terakhir Diperbarui:** 02 November 2023  

---

## 1. Overview & Vision

### 1.1 Ringkasan Produk
FinSmart adalah aplikasi manajemen keuangan pribadi berbasis Android yang dirancang untuk membantu pengguna mencatat, memonitor, dan menganalisis kondisi finansial harian secara intuitif, aman, dan efisien. Aplikasi mengadopsi pendekatan *offline-first* dengan sinkronisasi cloud terenkripsi agar pengguna dapat mengakses dan mengelola data finansial kapan saja tanpa hambatan konektivitas.

### 1.2 Visi Produk
Memberdayakan masyarakat urban dan melek digital untuk mencapai kemandirian finansial melalui pelacakan anggaran yang disiplin, analitik pengeluaran yang bermakna, dan otomatisasi tata kelola portofolio keuangan mikro.

### 1.3 Sasaran Strategis (Strategic Goals)
* Mencapai retensi hari ke-30 (D30 Retention) sebesar ≥ 35%.
* Menjaga tingkat *crash-free sessions* di atas 99,8%.
* Menghadirkan waktu pencatatan transaksi di bawah 5 detik (*quick logging*).
* Menjaga kepatuhan tata kelola dokumentasi dan CI/CD melalui sistem otomatisasi internal rilis berkelanjutan.

---

## 2. Target Audience & User Personas

### 2.1 Segmen Pengguna
1. **Young Professionals (Usia 22–32 tahun):** Memiliki penghasilan bulanan rutin, membutuhkan pembagian alokasi gaji, dana darurat, dan investasi.
2. **Mahasiswa / Fresh Graduates (Usia 18–24 tahun):** Membutuhkan kontrol pengeluaran harian dan pengelolaan uang saku/beasiswa yang ketat.
3. **Pekerja Lepas / Freelancer (Usia 23–40 tahun):** Memiliki arus kas tidak tetap, membutuhkan fleksibilitas pelacakan multi-rekening dan kategori kustom.

### 2.2 Persona Pengguna

#### Persona 1: Rian (First Jobber, 24 Tahun)
* **Tujuan:** Menghentikan kebiasaan impulsif dan menabung 25% dari gaji bulanan.
* **Tantangan:** Sering lupa mencatat pengeluaran kecil (kopi, parkir, ojek online); UI aplikasi perbankan konvensional terlalu kaku dan lambat dibuka.
* **Kebutuhan FinSmart:** Pencatatan cepat (*1-click transaction*), widget di homescreen Android, dan notifikasi peringatan saat anggaran kategori melampaui 80%.

#### Persona 2: Maya (Freelance Content Creator, 29 Tahun)
* **Tujuan:** Memisahkan rekening operasional kerja dan kebutuhan rumah tangga.
* **Tantangan:** Pendapatan masuk pada tanggal acak; kesulitan melihat laporan laba-rugi riil setiap bulan.
* **Kebutuhan FinSmart:** Multi-dompet/rekening, ekspor laporan berkala ke CSV/PDF, dan visualisasi grafik yang detail.

---

## 3. User Stories & Use Cases

### 3.1 User Stories

| ID | Persona | Narasi Kebutuhan | Kriteria Keberhasilan (Acceptance Criteria) |
|---|---|---|---|
| **US-01** | Pengguna Aktif | Sebagai pengguna, saya ingin mencatat pengeluaran dalam hitungan detik agar saya tidak menunda pencatatan. | Transaksi tersimpan ke basis data lokal < 300ms setelah menekan tombol "Simpan". |
| **US-02** | Pengguna Aktif | Sebagai pengguna, saya ingin menetapkan limit anggaran per kategori setiap bulan agar tidak melebihi alokasi dana. | Muncul indikator progres visual dan push notification saat alokasi mencapai 80% dan 100%. |
| **US-03** | Pengguna Privasi | Sebagai pengguna, saya ingin mengunci aplikasi dengan Biometrik/PIN agar data finansial tidak dibaca orang lain. | Aplikasi meminta autentikasi sidik jari/wajah saat dibuka atau setelah *background time-out* 60 detik. |
| **US-04** | Pengguna Analitik | Sebagai pengguna, saya ingin melihat grafik distribusi pengeluaran agar mengetahui pos pengeluaran terbesar. | Visualisasi diagram lingkaran/batang interaktif yang dapat difilter per minggu/bulan/tahun. |
| **US-05** | Tech Team / PM | Sebagai TPM, saya ingin pembaruan PRD berjalan secara otomatis melalui script CI/CD (`update_prd.js`) agar dokumentasi selalu sinkron dengan kode. | Script memproses pembaruan commit (`Update6`), memvalidasi dokumen, dan memperbarui berkas markdown tanpa distorsi struktur. |

### 3.2 Use Case: Pencatatan Transaksi Cepat
1. **Aktor:** Pengguna Terdaftar.
2. **Kondisi Awal:** Aplikasi berada di dashboard utama atau dibuka melalui Quick Action Widget.
3. **Alur Utama:**
   1. Pengguna menekan tombol Floating Action Button (FAB) `+`.
   2. Sistem menampilkan *bottom sheet modal* dengan keypad angka interaktif.
   3. Pengguna memasukkan nominal (misal: Rp50.000).
   4. Pengguna memilih kategori (misal: "Makanan & Minuman").
   5. Pengguna menekan tombol `Simpan`.
   6. Sistem menyimpan data ke Room DB lokal dan menambahkan item ke antrean sinkronisasi background (*WorkManager*).
   7. UI menampilkan snackbar sukses dan memperbarui saldo dompet secara real-time.
4. **Kondisi Akhir:** Saldo terpotong dan riwayat transaksi teratas terupdate.

---

## 4. Functional Requirements

### 4.1 Modul Autentikasi & Akun
* **FR-01:** Mendukung autentikasi via Email/Password dan Google Single Sign-On (SSO).
* **FR-02:** Mendukung mode Tamu (*Guest Mode/Offline-first*) tanpa registrasi awal, dengan opsi migrasi akun nanti.
* **FR-03:** Keamanan biometrik (Fingerprint & Face Unlock) memanfaatkan Android BiometricPrompt API.

### 4.2 Manajemen Dompet (Wallet Management)
* **FR-04:** Pembuatan multi-dompet (Kas Tunai, Rekening Bank, E-Wallet).
* **FR-05:** Transfer saldo antar-dompet dengan pencatatan ganda otomatis (debit-kredit).
* **FR-06:** Penyesuaian saldo manual (*reconcile*) disertai catatan alasan selisih.

### 4.3 Manajemen Transaksi
* **FR-07:** Input transaksi (Pemasukan, Pengeluaran, Transfer) dengan atribut: Nominal, Tanggal & Waktu, Kategori, Dompet, Tag, Catatan, Lampiran Foto Struk.
* **FR-08:** Dukungan transaksi berulang (harian, mingguan, bulanan) dengan eksekusi background scheduling.
* **FR-09:** Fitur pencarian dan filter transaksi berdasarkan rentang tanggal, kategori, tag, dan rentang nominal.

### 4.4 Anggaran & Target Finansial (Budgeting & Goals)
* **FR-10:** Penetapan anggaran bulanan per kategori atau anggaran global.
* **FR-11:** Sistem peringatan otomatis ketika pengeluaran mencapai 80%, 90%, dan 100% dari limit pagu.
* **FR-12:** Modul "Celengan Impian" (Saving Goals) dengan kalkulator estimasi penyelesaian target.

### 4.5 Laporan & Analitik
* **FR-13:** Diagram visual interaktif (Pie Chart, Bar Chart, Cashflow Line Chart).
* **FR-14:** Ringkasan bulanan: Rasio Tabungan (*Savings Rate*), Kategori Terbesar, dan Rata-rata Pengeluaran Harian.
* **FR-15:** Ekspor data ke format CSV dan PDF dengan rentang kustom.

### 4.6 Internal Automation Tooling (`update_prd.js`)
* **FR-16:** Pembaruan script automasi rilis dokumentasi `update_prd.js` (Iterasi `Update6`).
* **FR-17:** Skrip bertanggung jawab memvalidasi keselarasan struktur 14 bab wajib, parsing metadata Git commit secara headless, dan merefleksikan perubahan teknis ke changelog secara otomatis.

---

## 5. Non-Functional Requirements

### 5.1 Performa (Performance)
* **NFR-01 (Cold Startup):** Waktu peluncuran dingin (Cold Start) aplikasi tidak melebihi 1,5 detik pada perangkat Snapdragon seri 600 atau setara.
* **NFR-02 (Render Smoothness):** Antarmuka wajib mempertahankan frame rate stabil 60 fps (16,6ms per frame) tanpa jank pada operasi scroll daftar transaksi.
* **NFR-03 (Footprint):** Ukuran berkas APK akhir di bawah 15 MB melalui konfigurasi ProGuard/R8 dan WebP asset compression.

### 5.2 Keandalan & Ketersediaan (Reliability & Availability)
* **NFR-04:** Arsitektur *offline-first* memastikan 100% fungsionalitas pencatatan tetap beroperasi tanpa koneksi internet.
* **NFR-05:** Mekanisme *retry* sinkronisasi otomatis menggunakan exponential backoff strategy saat koneksi terpulihkan.

### 5.3 Keamanan (Security)
* **NFR-06:** Enkripsi basis data lokal menggunakan SQLCipher (AES-256).
* **NFR-07:** Penyimpanan kredensial token dan kunci privat dalam Android Keystore melalui `EncryptedSharedPreferences`.
* **NFR-08:** Seluruh komunikasi jaringan menggunakan TLS 1.3 dengan teknik *certificate pinning*.

### 5.4 Aksesibilitas & Kompatibilitas (Accessibility & Compatibility)
* **NFR-09:** Minimal SDK: Android 8.0 (API Level 26); Target SDK: Android 14 (API Level 34).
* **NFR-10:** Kepatuhan WCAG 2.1 Level AA untuk rasio kontras teks (minimal 4.5:1) dan dukungan screen-reader TalkBack.

---

## 6. System Architecture & Tech Stack

### 6.1 Pola Arsitektur
Aplikasi menerapkan **Clean Architecture** yang dipadukan dengan pola desain **MVVM (Model-View-ViewModel)** serta prinsip UDF (*Unidirectional Data Flow*).

```
[ UI Layer (Jetpack Compose) ] 
            ↓ 
[ ViewModel Layer (StateFlow & SharedFlow) ]
            ↓ 
[ Domain Layer (Use Cases & Business Logic) ]
            ↓ 
[ Data Layer (Repository Pattern) ]
     ↙                        ↘
[ Local DataSource (Room) ]   [ Remote DataSource (Retrofit/Ktor) ]
```

### 6.2 Tech Stack
* **Bahasa:** Kotlin 1.9.x
* **UI Toolkit:** Jetpack Compose (Material 3)
* **Asynchronous:** Kotlin Coroutines & Flow
* **Dependency Injection:** Hilt / Dagger
* **Basis Data Lokal:** Room Persistence Library + SQLCipher
* **Jaringan:** Retrofit2, OkHttp3, Moshi JSON Converter
* **Background Tasks:** Android Jetpack WorkManager
* **Telemetry & Cloud:** Firebase Auth, Firestore, Crashlytics, Performance Monitoring
* **Build & Automation:** Gradle Kotlin DSL, Node.js (`update_prd.js` v6) untuk CI/CD pipeline dokumentasi.

---

## 7. UI/UX Design Guidelines & User Flow

### 7.1 Panduan Desain (Material 3)
* **Color Palette:**
  * Primary: `#1E88E5` (FinSmart Blue - melambangkan stabilitas finansial)
  * Secondary / Accent: `#00C853` (Success/Income Green)
  * Error: `#E53935` (Expense Alert Red)
  * Surface Dark: `#121212` / Surface Light: `#FAFAFA`
* **Tipografi:** Google Font Inter (Titling: Bold 20sp, Subtitle: Medium 16sp, Body: Regular 14sp, Caption: 12sp).
* **Dark Mode:** Wajib mendukung Dynamic Theme (Material You) berbasis wallpaper perangkat di Android 12+.

### 7.2 Diagram Alur Pengguna (User Flow)

```
[Buka Aplikasi]
       |
       v
[Validasi Biometrik / PIN] -> (Gagal) -> [Kunci Akses / Retry]
       | (Sukses)
       v
[Dashboard Utama]
       |
       +---> [Klik Tombol Transaksi (+)] 
       |           |
       |           v
       |     [Input Nominal & Kategori] 
       |           |
       |           v
       |     [Klik Simpan] ---> [Update Room DB] ---> [Antrean WorkManager Sync]
       |
       +---> [Tab Laporan] ---> [Filter Rentang Tanggal] ---> [Grafik & Ringkasan]
       |
       +---> [Tab Anggaran] ---> [Cek Status Pagu & Set Limit Baru]
       |
       +---> [Tab Pengaturan] ---> [Ekspor Data / Ubah Preferensi Keamanan]
```

---

## 8. Data Models & Database Schema

### 8.1 Skema Entity Basis Data Lokal (Room)

#### Table: `wallets`
| Column Name | Data Type | Constraint | Keterangan |
|---|---|---|---|
| `id` | TEXT (UUID) | PRIMARY KEY | Pengenal unik dompet |
| `name` | TEXT | NOT NULL | Nama dompet (misal: BCA, Dompet Fisik) |
| `type` | TEXT | NOT NULL | Kategori (CASH, BANK, EWALLET) |
| `balance` | REAL | NOT NULL DEFAULT 0.0 | Saldo berjalan |
| `currency` | TEXT | NOT NULL DEFAULT 'IDR' | Kode mata uang |
| `is_deleted` | INTEGER | NOT NULL DEFAULT 0 | Soft delete flag |
| `updated_at` | INTEGER | NOT NULL | Unix epoch timestamp |

#### Table: `categories`
| Column Name | Data Type | Constraint | Keterangan |
|---|---|---|---|
| `id` | TEXT (UUID) | PRIMARY KEY | Pengenal unik kategori |
| `name` | TEXT | NOT NULL | Nama kategori |
| `type` | TEXT | NOT NULL | EXPENSE atau INCOME |
| `icon_key` | TEXT | NOT NULL | Resource identifier icon |
| `color_hex` | TEXT | NOT NULL | Kode warna visual |

#### Table: `transactions`
| Column Name | Data Type | Constraint | Keterangan |
|---|---|---|---|
| `id` | TEXT (UUID) | PRIMARY KEY | Pengenal unik transaksi |
| `wallet_id` | TEXT | FOREIGN KEY (`wallets.id`) | Relasi dompet asal |
| `category_id`| TEXT | FOREIGN KEY (`categories.id`)| Relasi kategori |
| `type` | TEXT | NOT NULL | EXPENSE, INCOME, TRANSFER |
| `amount` | REAL | NOT NULL | Nominal transaksi |
| `timestamp` | INTEGER | NOT NULL | Waktu kejadian transaksi |
| `notes` | TEXT | NULLABLE | Catatan opsional |
| `receipt_url`| TEXT | NULLABLE | Path berkas lokal / URL bukti transaksi |
| `sync_status`| TEXT | NOT NULL DEFAULT 'PENDING' | PENDING, SYNCED, ERROR |

#### Table: `budgets`
| Column Name | Data Type | Constraint | Keterangan |
|---|---|---|---|
| `id` | TEXT (UUID) | PRIMARY KEY | Pengenal unik anggaran |
| `category_id`| TEXT | FOREIGN KEY (`categories.id`)| Kategori alokasi pagu |
| `limit_amount`| REAL | NOT NULL | Batas anggaran |
| `period_month`| INTEGER | NOT NULL | Format: YYYYMM |

---

## 9. API & Third-Party Integration

### 9.1 Endpoints Backend (RESTful Architecture)
* `POST /v1/auth/sync-session`: Pertukaran token Firebase ke server session.
* `POST /v1/transactions/batch`: Sinkronisasi kumpulan (*batch*) transaksi lokal yang berstatus `PENDING`.
* `GET /v1/reports/monthly?period=YYYYMM`: Pengambilan kalkulasi komparasi historis antar-bulan dari backend.
* `POST /v1/backup/export`: Permintaan pembuatan payload cadangan cloud terenkripsi.

### 9.2 Integrasi Layanan Pihak Ketiga (Third-Party SDKs)
1. **Firebase Authentication:** Manajemen identitas pengguna dan federated login Google.
2. **Firebase Cloud Firestore:** Sinkronisasi cloud no-SQL cadangan multi-device.
3. **Firebase Cloud Messaging (FCM):** Notifikasi pengingat malam hari dan alarm pagu anggaran.
4. **Google Drive API (Opsional Pengguna):** Fasilitas pencadangan berkas basis data SQLite mentah secara mandiri.

---

## 10. Analytics & Telemetry Metrics

### 10.1 Key Performance Indicators (KPI)
* **DAU / MAU Ratio:** Target target > 0.4 (menandakan penggunaan rutin harian).
* **Average Time to Add Transaction:** Ditargetkan < 5 detik.
* **Crash-Free User Rate:** Minimal 99,8% di platform Firebase Crashlytics.
* **Monthly Active Saving Goals:** Persentase pengguna yang berhasil menyelesaikan minimal 1 saving goal dalam 6 bulan.

### 10.2 Logging Event Telemetri
* `transaction_created`: Properti mencakup `type`, `category_id`, `has_attachment (boolean)`, `duration_ms`.
* `budget_exceeded`: Properti mencakup `category_id`, `ratio_exceeded`.
* `backup_executed`: Properti mencakup `sync_type (auto/manual)`, `payload_size_kb`, `status`.
* `biometric_auth_failed`: Properti mencakup `failure_reason`.

---

## 11. Security & Compliance

### 11.1 Perlindungan Data Pribadi (Data Privacy)
* Kepatuhan terhadap UU Perlindungan Data Pribadi (UU PDP Indonesia) dan standar GDPR:
  * Tidak mengumpulkan identitas sensitif finansial seperti nomor kartu kredit penuh, CVV, atau PIN perbankan.
  * Opsi "Hapus Akun & Data Selamanya" (*Right to Erasure*) yang menghapus seluruh data lokal dan server dalam sekali tekan.

### 11.2 Kriptografi & Penyimpanan
* **At Rest:** Database SQLite dilindungi pustaka SQLCipher dengan kunci acak turunan (PBKDF2) yang tersimpan aman di Android KeyStore Hardware-backed.
* **In Transit:** Komunikasi HTTPS dengan Certificate Transparency checks dan TLS v1.3 pinning.

---

## 12. Release Strategy & Rollout Plan

### 12.1 Strategi Rilis Aplikasi (Google Play Console)
* **Alpha Internal Track:** Pengujian QA internal dan automated testing (Espresso + JUnit).
* **Beta Track Terbuka:** Rollout ke basis pengguna terdaftar (1.000 pengguna pertama).
* **Staged Rollout Produksi:**
  * Tahap 1: 10% dari total pengguna (Monitoring Crashlytics selama 48 jam).
  * Tahap 2: 25% jika crash rate < 0,2%.
  * Tahap 3: 50% peluncuran masal.
  * Tahap 4: 100% rilis global.

### 12.2 Pipeline Otomatisasi Dokumentasi
* Integrasi skrip `update_prd.js` pada alur CI/CD GitHub Actions:
  * Mengeksekusi verifikasi otomatis kelengkapan PRD pada setiap merge pull request ke branch `main`.
  * Memastikan metadata versi, tanggal rilis, dan integritas isi Bab 1 hingga Bab 14 tidak mengalami regresi struktural.

---

## 13. Risks, Assumptions & Dependencies

### 13.1 Risiko & Mitigasi (Risks & Mitigation)
* **Risiko 1 (Kehilangan Data Lokal):** Pengguna mengganti perangkat saat sinkronisasi cloud belum rampung.
  * *Mitigasi:* Peringatan proaktif di layar utama jika terdapat data yang berstatus belum tersinkronisasi (*uncommitted sync*).
* **Risiko 2 (OOM/Out-of-Memory karena Lampiran Foto):** Lampiran struk kamera resolusi tinggi membebani memori.
  * *Mitigasi:* Kompresi otomatis gambar ke resolusi maksimal 1080p WebP sebelum disimpan ke direktori lokal internal.

### 13.2 Asumsi (Assumptions)
* Mayoritas pengguna melakukan pencatatan transaksi secara manual tanpa integrasi otomatis Open Banking (API perbankan Indonesia yang berbiaya tinggi untuk fase MVP).
* Sistem operasi perangkat target telah memiliki dukungan Google Play Services.

### 13.3 Dependensi (Dependencies)
* Ketersediaan layanan Firebase Authentication & Firestore (SLA 99,95%).
* Stabilitas skrip tooling internal `update_prd.js` untuk menjaga kepatuhan dokumentasi lintas siklus rilis.

---

## 14. Appendix & Changelog

### 14.1 Glosarium
* **Clean Architecture:** Pola arsitektur perangkat lunak yang memisahkan tanggung jawab kode ke dalam lapisan presentation, domain, dan data.
* **UDF (Unidirectional Data Flow):** Pola di mana status data mengalir satu arah dan peristiwa mengalir ke arah sebaliknya.
* **SQLCipher:** Ekstensi open-source SQLite yang menyediakan enkripsi transparan 256-bit AES.

### 14.2 Riwayat Perubahan (Changelog)

| Versi | Tanggal | Penulis | Deskripsi Perubahan |
|---|---|---|---|
| **v1.0.4** | 14 Oktober 2023 | Technical PM | Versi dasar dokumentasi perancangan sistem dan fungsionalitas aplikasi FinSmart. |
| **v1.0.5** | 24 Oktober 2023 | Technical PM | Pembaruan script otomatisasi dokumentasi dan integrasi pipeline (`2update_prd.js`). |
| **v1.0.6** | 02 November 2023 | Technical PM | **Refinement Update6:** Pembaruan dan stabilisasi menyeluruh pada skrip automasi internal `update_prd.js`, standardisasi struktur 14 bab wajib PRD, penambahan tata kelola pipeline dokumentasi CI/CD, serta pemutakhiran metrik telemetri. |