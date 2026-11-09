# 📖 PANDUAN PENGGUNAAN: TEMPLATE PROYEK AMAN
### Sistem Proteksi Anti-Fake-Pass, Konsultan Risiko & Interupsi Notepad untuk Owner Non-Coder

Halo Owner! Panduan ini ditulis khusus dengan bahasa sehari-hari tanpa istilah koding rumit. 
Tujuan dari template ini adalah agar **Anda memegang kendali penuh atas proyek**, AI tidak bisa berbohong bilang fitur "sudah selesai", dan AI tidak bisa merusak fitur lama secara sembunyi-sembunyi.

---

## 🗂️ Apa Saja Isi Folder Ini?

1. **`auto_sync.py`**  
   Skrip penjaga otomatis (detektor kebohongan kode, alarm suara, pembuka Notepad, dan pencatat kemajuan ke Git).
2. **`.antigravityrules` & `GEMINI.md`**  
   "Buku Peraturan Mutlak" yang dibaca oleh AI Antigravity agar bersikap seperti konsultan arsitektur yang hati-hati, bukan developer asal cepat.
3. **`ARCHITECTURE_MAP.md`**  
   Peta hubungan antar fitur agar AI tahu mana fitur kritis yang dilarang dirusak.
4. **`CURRENT_CHECKPOINT.md`**  
   Buku catatan status proyek terkini untuk mencegah AI halusinasi atau lupa progres saat ganti chat baru.
5. **`ROADMAP.md`**  
   Daftar antrean pengerjaan fitur satu demi satu.
6. **`END_TO_END_PROOF.md`**  
   Lembar bukti pengujian nyata yang wajib diisi AI sebelum mengklaim selesai.

---

## 🚀 Cara Memakai di Proyek Baru atau Proyek yang Sudah Jalan

Anda tidak perlu mengerti koding. Cukup ikuti 3 langkah mudah ini:

### Langkah 1: Salin (Copy-Paste) Seluruh Isi Folder Ini
Salin semua file di dalam folder `template-proyek-aman` ini langsung ke **folder utama (akar/root) proyek Anda** yang ingin diamankan.

### Langkah 2: Inisialisasi Proyek (Hanya 1 Kali di Awal)
Buka terminal (Command Prompt / PowerShell) di folder proyek Anda, lalu ketik:
```bash
python auto_sync.py init "Nama Proyek Anda"
```
*(Ganti "Nama Proyek Anda" dengan nama game atau aplikasi Anda)*.

### Langkah 3: Beri Tahu AI Antigravity
Di awal setiap percakapan dengan Antigravity, cukup katakan:
> *"Tolong baca file CURRENT_CHECKPOINT.md dan ARCHITECTURE_MAP.md. Ikuti semua aturan di .antigravityrules. Fitur yang ingin saya buat hari ini adalah: [Nama Fitur]."*

---

## 🛑 Bagaimana Cara Sistem Ini Melindungi Anda?

### 1. AI Dilarang Asal Ngoding (Konsultan Risiko)
Sebelum menyentuh kode satu baris pun, AI diwajibkan menjelaskan:
- Apakah fitur baru ini bisa membuat fitur lama mogok?
- Apa risikonya jika diteruskan?
- Pilihan solusi yang aman.

### 2. Alarm Suara & Notepad Otomatis (`need-decision`)
Jika AI menemukan risiko berbahaya atau mengalami error 3x berturut-turut, AI akan:
1. Membunyikan alarm suara peringatan di speaker komputer Anda (`BEEP! BEEP!`).
2. Menuliskan masalahnya dalam bahasa awam ke file `BUTUH_KEPUTUSAN_OWNER.txt`.
3. Otomatis membuka aplikasi **Notepad** di layar laptop/PC Anda!
4. Berhenti bekerja sampai Anda membaca Notepad tersebut dan memberikan keputusan di chat.

### 3. Detektor Kebohongan Kode (`audit` & `save`)
AI sering kali merasa sudah selesai hanya karena layarnya sudah bagus, padahal tombolnya belum berfungsi atau fungsinya masih tiruan (`dummy`, `TODO`, `mock`).
- Ketika AI hendak menyimpan pekerjaan dengan:
  ```bash
  python auto_sync.py save "Nama Fitur"
  ```
- Sistem ini akan memindai seluruh file kode Anda.
- Jika ditemukan kata `TODO`, `dummy`, `placeholder`, atau `mock_data`, **penyimpanan akan DITOLAK seketika** dan layar terminal akan menampilkan tanda merah:
  `❌ AUDIT GAGAL! Ditemukan kode pura-pura/dummy. Kerjakan sampai End-to-End!`

---

## 💡 Kapan Waktunya Membuka Percakapan Baru (New Chat)?

Semakin panjang obrolan di satu jendela chat, AI akan semakin pelupa dan rentan berhalusinasi.
**Waktu terbaik membuka New Chat:**
1. **Setiap kali 1 fitur selesai dan lolos simpan** (`✅ LOLOS AUDIT END-TO-END & TERSIMPAN!`).
2. **Saat AI mulai bingung atau mengalami error berulang kali.**

Saat membuka New Chat, Anda tidak perlu mengetik panjang lebar lagi. Cukup ketik:
```bash
python auto_sync.py status
```
Lalu minta AI melanjutkan fitur berikutnya dari `ROADMAP.md`. Proyek Anda akan tetap aman, rapi, dan terkontrol!
