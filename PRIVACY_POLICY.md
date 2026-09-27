# Kebijakan Privasi Aplikasi FinSmart

*Terakhir diperbarui: 27 September 2026*

Aplikasi **FinSmart** (Cross-Platform / Android Financial Management) dikembangkan oleh **Lukas**. Kami berkomitmen penuh untuk melindungi privasi data finansial Anda dengan arsitektur *Offline-First* dan enkripsi tingkat tinggi (AES-256).

---

## 1. Identitas Pengembang & Aplikasi
- **Nama Aplikasi:** FinSmart
- **Pengembang:** Lukas
- **Email Dukungan:** lunachroc07@gmail.com
- **Versi:** 1.0.0 (Dual-Mode Architecture)

---

## 2. Pengumpulan & Penggunaan Data
1. **Penyimpanan Lokal & Privat:** Semua data transaksi, saldo dompet/bank, anggaran, dan target tabungan disimpan di database lokal perangkat pengguna.
2. **Enkripsi AES-256:** Kami menerapkan enkripsi database lokal untuk mencegah akses tidak sah.
3. **Kedaulatan Data:** Tidak ada data keuangan yang dijual, dilacak, atau dibagikan ke server pihak ketiga untuk periklanan.

---

## 3. Akses Izin Sensitif (Prominent Disclosures)
- **Auto-Read Notification Listener & SMS:** Digunakan **HANYA** untuk membaca notifikasi transaksi SMS/Bank lokal di HP pengguna agar dapat mengekstrak nominal secara otomatis tanpa izin OJK atau API Bank. Data notifikasi **TIDAK PERNAH** dikirim keluar dari perangkat.
- **Kamera (Camera Access):** Digunakan semata-mata untuk mengambil foto nota belanja fisik pada fitur Scan Nota AI Gemini OCR.
- **Mikrofon (Record Audio):** Digunakan semata-mata saat pengguna mendiktekan transaksi pada fitur Voice-to-Text Expense Logging.
- **Google Drive Sync (OAuth):** Digunakan untuk mencadangkan database terenkripsi ke folder pribadi pengguna (`FinSmart/Backup Data`).

---

## 4. Layanan Pihak Ketiga
- **Google Gemini API:** Pemrosesan AI untuk ekstraksi nota dan input percakapan.
- **Google Play Billing:** Pembayaran langganan resmi saat mode Play Store aktif.
- **Google AdMob:** Ditampilkan hanya pada akun gratis, dan **100% Bebas Iklan** untuk pengguna Smart Master 👑.

---

## 5. Penghapusan Akun & Data (Play Store Compliance)
Pengguna dapat menghapus seluruh akun, data, dan riwayat transaksi secara permanen kapan saja melalui menu **Pengaturan & Akun &rarr; Hapus Akun & Data Permanen** dengan konfirmasi keamanan PIN/Password.

---

## 6. Kontak Pengembang
Untuk pertanyaan, klaim privasi, atau permohonan bantuan:
- **Email:** lunachroc07@gmail.com
- **Developer:** Lukas
- &copy; 2026 Lukas. All Rights Reserved.
