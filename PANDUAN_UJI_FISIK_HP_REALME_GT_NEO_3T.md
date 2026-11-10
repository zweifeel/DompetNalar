# PANDUAN UJI COBA FISIK APLIKASI DOMPETNALAR
## Khusus Perangkat: Realme GT Neo 3T (Android 13 / 14 - realme UI)

> [!NOTE]
> Dokumen ini disusun khusus dalam bahasa awam yang ramah untuk Owner (Non-Coder).
> Seluruh perbaikan dari **Fase 1 hingga Fase 5** telah selesai dan lulus 995 unit test di sistem. Sekarang saatnya menguji kenyamanan dan kehandalan aplikasi langsung di genggaman tangan Anda!

---

## 📦 1. Lokasi Berkas APK & Cara Pemasangan di HP

### A. Lokasi File APK di Komputer
Berkas installer APK yang baru saja diterbitkan berada di:
`E:\Antigravity Project\FinSmart\app\build\outputs\apk\debug\app-debug.apk`
- **Ukuran Berkas:** ~39.5 MB
- **Nama Berkas:** `app-debug.apk`
- **Waktu Pembuatan:** 10 November 2026

### B. Cara Mengirim ke HP Realme GT Neo 3T (Pilih Salah Satu yang Paling Mudah)
1. **Opsi 1 (Kabel Data USB):**
   - Hubungkan HP ke laptop/PC dengan kabel data, pilih mode **"Transfer File (MTP)"** di HP.
   - Buka Windows Explorer, salin (copy) file `app-debug.apk` ke folder `Download` di HP Anda.
   - Buka aplikasi **Pengelola File (File Manager)** bawaan Realme, buka folder `Download`, lalu ketuk file `app-debug.apk` untuk menginstal.
2. **Opsi 2 (Kirim via WhatsApp / Telegram Sendiri):**
   - Buka WhatsApp Web atau Telegram Web di browser laptop, kirim file `app-debug.apk` ke chat pribadi Anda / Saved Messages.
   - Buka aplikasi di HP, unduh file, dan ketuk untuk menginstal.
3. **Catatan Izin Instalasi Realme:**
   - Jika muncul peringatan *"Demi keamanan, ponsel Anda tidak diizinkan memasang aplikasi yang tidak dikenal dari sumber ini"*, ketuk **Setelan (Settings)** lalu geser tombol **"Izinkan dari sumber ini"** menjadi AKTIF.

---

## 🧪 2. Empat Skenario Uji Fisik Utama

---

### 🟢 UJI SKENARIO 1: Tes Anti-Crash Pergantian Bahasa (Fase 1)
**Tujuan:** Memastikan aplikasi TIDAK PERNAH mati mendadak (*crash*) saat Anda mengganti bahasa bolak-balik.

* **Langkah-langkah Pengujian:**
  1. Buka aplikasi **DompetNalar** di HP Anda.
  2. Masuk ke tab **Pengaturan** (ikon roda gigi di pojok kanan bawah).
  3. Ketuk opsi **"Bahasa / Language"**.
  4. Pilih **"English"** -> Perhatikan seluruh teks antarmuka berubah menjadi Bahasa Inggris seketika tanpa layar berkedip hitam atau aplikasi tertutup sendiri.
  5. Ketuk kembali **"Language"**, lalu ganti ke **"Bahasa Indonesia"** -> Periksa bahwa bahasa kembali ke Bahasa Indonesia dengan mulus.
  6. Terakhir, pilih opsi **"Ikuti Sistem HP (Follow System)"** -> Pastikan aplikasi menyesuaikan bahasa sistem perangkat Realme Anda tanpa kata `(Otomatis)` yang mengganggu.

> **Tanda Lolos (PASS):** Aplikasi tetap terbuka dengan stabil, pergantian bahasa berlangsung sekejap tanpa aplikasi tertutup sendiri (0 crash).

---

### 🟢 UJI SKENARIO 2: Tes Pop-up Onboarding Pertama Buka & 0 Dompet Awal (Fase 2)
**Tujuan:** Memastikan pengguna baru disambut pop-up penyiapan bahasa & mata uang, serta akun dimulai dari 0 dompet bersih (bebas atur sendiri).

* **Langkah-langkah Pengujian:**
  1. Di layar utama HP Realme Anda, tekan dan tahan ikon aplikasi **DompetNalar** -> pilih **Info Aplikasi (App Info)**.
  2. Masuk ke menu **Penggunaan Penyimpanan (Storage usage)** -> ketuk **Hapus Data (Clear Data)**. *(Ini membuat aplikasi seperti baru diinstal pertama kali)*.
  3. Buka kembali aplikasi **DompetNalar**.
  4. **Periksa Pop-up Onboarding:**
     - Anda akan langsung disambut dialog elegan: *"Selamat Datang di DompetNalar / Welcome to DompetNalar"*.
     - Pilih Bahasa yang diinginkan dan Mata Uang utama (misalnya IDR - Rupiah).
     - Ketuk tombol **"Mulai Aplikasi / Start Application"**.
  5. **Periksa Layar Dasbor (0 Dompet):**
     - Setelah masuk, perhatikan bagian Dompet: **TIDAK ADA** dompet bawaan atau dompet palsu.
     - Tampil ilustrasi/kartu ramah: *"Belum ada dompet aktif"* dengan tombol jelas **"+ Tambah Dompet"**.
     - Coba ketuk "+ Tambah Dompet" dan buat dompet pertama Anda (misal: "BCA" atau "Tunai") -> Saldo tersimpan dengan benar.

> **Tanda Lolos (PASS):** Pop-up setup muncul hanya saat awal instalasi, dan ruang dompet bersih memberi otonomi penuh pada pengguna untuk menambah rekeningnya sendiri.

---

### 🟢 UJI SKENARIO 3: Tes Bebas Tulisan Vertikal di Split Bill / Patungan (Fase 4)
**Tujuan:** Memastikan nominal angka Rupiah tidak terpotong atau terlipat vertikal per huruf ke bawah saat nama item atau nama teman panjang.

* **Langkah-langkah Pengujian:**
  1. Di menu aplikasi, buka fitur **"Split Bill (Patungan)"**.
  2. Buat patungan baru: Masukkan nama tempat belanja yang panjang (misal: *"Restoran Seafood Bakar Nusantara Jaya"*).
  3. Masukkan item belanja dengan nominal besar (misal: *"Paket Ikan Gurame Bakar & Es Kelapa"* seharga `Rp 1.450.000`).
  4. Tambahkan 2 atau 3 nama teman (misal: *"Budi Santoso"* dan *"Siti Rahmawati"*).
  5. Masukkan persentase pajak restoran (10%) dan biaya layanan (5%).
  6. **Periksa Layar Hasil:**
     - Perhatikan kartu ringkasan total tagihan.
     - Perhatikan rincian bayar per orang (*breakdown per person*).
     - Periksa baris nominal Rupiah di sebelah kanan.

> **Tanda Lolos (PASS):** Tulisan nominal seperti `Rp 1.450.000` dan rincian bagian per orang tertulis rapi mendatar dalam **satu baris utuh**! Tidak ada lagi teks yang terlipat vertikal huruf-per-huruf.

---

### 🟢 UJI SKENARIO 4: Tes Kerapian Lencana Profil Pengaturan & Animasi (Fase 4)
**Tujuan:** Memastikan kartu profil pengguna di halaman pengaturan terlihat lega, modern, dan dialog dapat di-scroll lancar.

* **Langkah-langkah Pengujian:**
  1. Masuk ke tab **Pengaturan**.
  2. Perhatikan kartu profil di bagian paling atas:
     - Nama Anda berada di atas.
     - Lencana keanggotaan (badge **"Smart Member"** atau **"Tamu Terdaftar"**) sekarang berada tepat di bawah nama dengan jarak yang rapi, bukan menempel sempit di samping.
  3. Coba ketuk salah satu pengaturan yang memunculkan pop-up/dialog (seperti Ganti Kata Sandi, Preferensi Ekspor, atau Atur Mata Uang).
  4. Coba gulir (*scroll*) dialog tersebut ke atas dan ke bawah:
     - Seluruh tombol ("Tutup" dan "Simpan") tetap mudah dijangkau dan tidak pernah terpotong oleh batas layar bawah.
  5. Coba berpindah-pindah antar tab menu bawah (Dasbor -> Transaksi -> Dompet -> Anggaran -> Pengaturan):
     - Perpindahan layar kini menggunakan transisi halus (*smooth animated fade*), bukan loncatan kasar.

> **Tanda Lolos (PASS):** Tata letak kartu profil terasa lega dan profesional, seluruh pop-up dialog bisa di-scroll tanpa ada tombol tersembunyi, dan navigasi menu terasa lincah.

---

## 📋 3. Tabel Checklist Hasil Uji Coba

Owner cukup mencentang checklist di bawah ini setelah mencoba langsung di HP:

| No | Skenario Pengujian | Hasil Pengamatan di HP Realme | Status |
| :---: | :--- | :--- | :---: |
| 1 | Ganti Bahasa bolak-balik (ID <-> EN <-> Sistem) | Aplikasi lancar, tidak ada crash / hang | [ ] PASS |
| 2 | Buka pertama kali / Hapus data | Pop-up setup muncul, dompet awal bersih 0 | [ ] PASS |
| 3 | Perhitungan Patungan (Split Bill) | Teks nominal Rupiah mendatar rapi 1 baris | [ ] PASS |
| 4 | Kartu Pengaturan & Animasi Navigasi | Badge profil rapi di bawah nama, scroll dialog lancar | [ ] PASS |

---

*Selamat menguji aplikasi DompetNalar! Jika ada catatan atau penyesuaian yang diinginkan, kabari Antigravity kapan saja.*
