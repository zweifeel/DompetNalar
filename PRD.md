# PRD FinSmart App v1.0.5

## Riwayat Perubahan (Changelog)

| Versi | Tanggal | Penulis | Deskripsi Perubahan |
|---|---|---|---|
| v1.0.4 | 14 Oktober 2023 | Technical PM | Versi dasar dokumentasi aplikasi FinSmart |
| v1.0.5 | 24 Oktober 2023 | Technical PM | Pembaruan script otomatisasi dokumentasi dan integrasi pipeline (`2update_prd.js`) |

---

## 1. Overview
Aplikasi Manajemen Keuangan FinSmart adalah platform Android untuk membantu pengguna mengelola transaksi harian, membuat anggaran bulanan, dan melacak pertumbuhan portofolio finansial secara personal.

---

## 2. Pembaruan Teknis & Internal Tooling (v1.0.5)

### 2.1 Pembaruan Script Automasi Dokumentasi (`2update_prd.js`)
* **Latar Belakang:** Diperlukan pemeliharaan dan standardisasi alur pembaruan dokumen PRD agar sinkron dengan rilis kode terbaru.
* **Perubahan:**
  * Pembaruan logic pada skrip internal `2update_prd.js` untuk parsing metadata commit dan update changelog otomatis.
  * Peningkatan keandalan integrasi tooling CI/CD dokumen agar proses rilis versi dokumentasi teknis berjalan lebih konsisten.
* **Dampak:** Tidak ada perubahan langsung pada antarmuka pengguna (UI/UX) akhir aplikasi, perubahan difokuskan pada infrastruktur dan tooling developer/PM.