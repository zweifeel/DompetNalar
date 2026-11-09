# GEMINI.md — ATURAN DISIPLIN SISTEM ANTI-ASAL-BAPAK-SENANG (TBO & TEMPLATE PROYEK AMAN)

> [!IMPORTANT]
> **PROFIL PENGGUNA:**
> Owner proyek ini adalah seorang **Non-Coder** (tidak mengerti teknis sintaks, arsitektur rumit, atau trik kode).
> DILARANG membingungkan Owner dengan jargon teknis tanpa penjelasan awam.
> DILARANG mengklaim pekerjaan sudah "Selesai" jika belum teruji secara nyata dari hulu ke hilir.

---

## 🛡️ TIGA PROTOKOL MUTLAK AGEN ANTIGRAVITY

### 1. PROTOKOL 1: ANALISIS RISIKO & TABRAKAN FITUR (SEBELUM MENYENTUH KODE)
Setiap kali Owner meminta penambahan fitur baru atau perubahan sistem:
- **JANGAN LANGSUNG CODING!**
- Buka dan periksa `ARCHITECTURE_MAP.md`.
- Sampaikan analisis risiko dalam bahasa awam dengan format baku:
  * **Dampak ke Fitur Lain:** Sebutkan fitur apa saja yang disentuh dan apakah fitur yang sudah stabil berpotensi terganggu.
  * **Potensi Risiko:** Jelaskan potensi malfungsi dengan analogi kehidupan nyata (misal: "seperti menambahkan tingkat pada rumah yang pondasinya belum diperkuat").
  * **Rekomendasi Aman:** Berikan opsi arsitektur terbaik yang meminimalisir risiko kerusakan.
- **Interupsi Notepad:** Jika perubahan berdampak besar pada sistem yang sudah stabil, jalankan:
  ```bash
  python auto_sync.py need-decision "<Penjelasan risiko dalam bahasa awam>"
  ```
  Ini akan membunyikan alarm suara dan memunculkan jendela Notepad di layar komputer Owner. Tunggu keputusan Owner!

---

### 2. PROTOKOL 2: DEFINISI SELESAI WAJIB END-TO-END (ANTI FAKE-PASS)
Pekerjaan Anda HANYA dianggap "Selesai" jika telah melalui 4 Pilar Bukti:
1. **Input / Pemicu:** Antarmuka, tombol, atau event benar-benar ada dan bisa diklik/dipicu.
2. **Logika Proses:** Kode pemroses berjalan secara riil (DILARANG menggunakan `TODO`, `dummy`, `mock_data`, `pass #`, atau fungsi pura-pura).
3. **Penyimpanan / Output:** Hasil pemrosesan benar-benar tersimpan ke database/storage dan terlihat perubahannya pada antarmuka/layar tanpa error.
4. **Tes Regresi:** Fitur-fitur lama yang terhubung dipastikan tetap berjalan normal.

**Langkah Wajib Penyelesaian:**
- Tuliskan bukti 4 pilar tersebut ke dalam `END_TO_END_PROOF.md`.
- Jalankan verifikasi otomatis dan simpan:
  ```bash
  python auto_sync.py save "<Ringkasan Fitur>"
  ```
  *Catatan: Skrip `auto_sync.py` akan otomatis memindai seluruh kode. Jika ditemukan `TODO`, `dummy`, `mock_data`, atau fungsi belum jadi, proses simpan akan DIBATALKAN dan alarm kegagalan akan berbunyi!*

---

### 3. PROTOKOL 3: SIKLUS PERCAKAPAN BARU (NEW CHAT) ANTI-HALUSINASI
- **Saat Memulai Sesi/New Chat:**
  Wajib jalankan atau baca:
  ```bash
  python auto_sync.py status
  ```
  Baca `CURRENT_CHECKPOINT.md` dan `ARCHITECTURE_MAP.md` untuk memulihkan konteks tanpa menebak-nebak.
- **Jika Error 3x Berturut-Turut:**
  Hentikan pengulangan coba-coba. Jalankan `python auto_sync.py need-decision` dan sarankan Owner untuk membuka New Chat demi menyegarkan kembali ruang memori AI.
- **Prinsip Vertikal Tunggal:**
  Fokus pada SATU fitur sampai tuntas 100% sebelum berpindah ke fitur lainnya.
