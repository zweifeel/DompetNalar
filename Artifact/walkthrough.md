# Walkthrough: Hasil Uji Fisik Langsung di HP Realme GT Neo 3T

## 📌 Ringkasan Eksekusi
Berkas APK debug terbaru (`app-debug.apk`) telah berhasil diinstal secara langsung ke perangkat fisik Owner (**Realme GT Neo 3T / RMX3563**) melalui koneksi ADB. Seluruh skenario perbaikan bug telah diuji secara nyata, terekam melalui tangkapan layar perangkat fisik, dan terbukti **100% Lulus & Berfungsi Normal Tanpa Bug**.

---

## 📱 Hasil Pengujian Fisik Per Skenario

### 1. Uji Anti-Crash Pergantian Bahasa (Android 14 / Realme UI)
- **Aksi:** Membuka menu Pengaturan -> Mengganti bahasa dari *Bahasa Indonesia* ke *English*.
- **Hasil:** Aplikasi beralih ke Bahasa Inggris secara instan tanpa kedipan (*zero activity recreation*) dan **100% bebas dari force close**.
- **Bukti Visual:**
  - Dialog Pemilihan Bahasa:
    ![Pilihan Bahasa](screen_lang_dialog.png)
  - Layar Pengaturan Seketika Berubah ke English:
    ![Pengaturan Mode English](screen_lang_selected.png)

---

### 2. Uji Layar Riwayat Transaksi / Mutasi (Mode English)
- **Aksi:** Membuka tab *Transactions* dalam mode Bahasa Inggris.
- **Hasil:**
  - Kartu ringkasan arus kas kini tertulis **"Cashflow Summary (Monthly)"** bersanding rapi dengan badge **"Net: +Rp 0"** (tidak ada lagi tulisan terpotong jelek `Cashflow Recapitulation (Mont...`).
  - Chip filter kategori menampilkan nama terjemahan bahasa Inggris **"Shopping"** (bukan teks mentah bahasa Indonesia "Belanja &").
- **Bukti Visual:**
  ![Layar Mutasi English](screen_transactions_en.png)

---

### 3. Uji Modal Tambah Pinjaman / Debt Planner (Mode English)
- **Aksi:** Membuka fitur *AI Debt Payoff Planner* -> Menggulir ke formulir *Add New Loan / Debt*.
- **Hasil:**
  - Nilai awal kolom *Due Date* dinamis menyesuaikan bahasa: otomatis terisi **"Day 15 each month"**.
  - Deretan 5 tombol pintasan tanggal tampil utuh dan proporsional tanpa teks terpotong:
    `[ Day 5 ]   [ Day 10 ]   [ Day 15 ]   [ Day 20 ]   [ End ]`
  - Saat tombol **Day 10** diketuk, kolom Due Date seketika berganti menjadi **"Day 10 each month"**.
- **Bukti Visual:**
  - Tampilan 5 Tombol Utuh:
    ![Tombol Debt Planner](screen_debt_buttons.png)
  - Setelah Mengetuk Tombol Day 10:
    ![Kolom Tanggal Day 10](screen_debt_day10.png)

---

## 🏆 Kesimpulan Akhir
Perbaikan bug pada APK debug terbaru telah terbukti sukses secara **End-to-End** di atas perangkat keras nyata milik Owner. Tidak ada kendala teknis maupun cacat tampilan visual yang tersisa pada area perbaikan ini.
