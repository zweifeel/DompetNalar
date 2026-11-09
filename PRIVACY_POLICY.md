# Kebijakan Privasi Aplikasi DompetNalar

*Terakhir diperbarui: 10 Oktober 2026*

Aplikasi **DompetNalar** dikembangkan oleh **SleepyKoala**. Dokumen ini menjelaskan bagaimana data pengguna dikelola, disimpan, diproses, dan dilindungi saat menggunakan aplikasi DompetNalar. Kami berkomitmen untuk menyajikan informasi yang transparan, akurat, dan sesuai dengan implementasi teknis aktual aplikasi. DompetNalar tidak menjual data pribadi pengguna, dan pengiriman data ke layanan pihak ketiga hanya berlangsung sesuai fitur yang Anda gunakan.

---

## 1. Identitas Pengembang & Aplikasi

- **Nama Aplikasi:** DompetNalar
- **Pengembang:** SleepyKoala
- **Email Dukungan:** lunachroc01@gmail.com
- **Status Dokumen:** Dokumen Kebijakan Privasi Aplikasi (Offline-First Architecture)

---

## 2. Penyimpanan & Keamanan Data Lokal

1. **Penyimpanan Privat Aplikasi (*App-Private Storage*):** Semua data keuangan pengguna (saldo akun/dompet, catatan transaksi pemasukan dan pengeluaran, kategori, anggaran, daftar hutang/piutang, aset, dan riwayat split bill) disimpan secara lokal di dalam basis data SQLite (Room Database) pada penyimpanan privat perangkat pengguna.
2. **Perlindungan Sandbox Sistem Operasi:** Data lokal tersebut dilindungi oleh arsitektur keamanan *sandbox* sistem operasi Android (*Linux Application UID isolation*), yang membatasi akses sehingga aplikasi lain pada perangkat tidak dapat membaca basis data privat DompetNalar secara langsung.
3. **Klarifikasi Enkripsi Database:** Basis data aktif yang beroperasi di perangkat tidak menggunakan enkripsi database saat istirahat (*at-rest database encryption* seperti SQLCipher), melainkan mengandalkan perlindungan penyimpanan privat sistem operasi Android.
4. **Enkripsi Berkas Cadangan (AES-256-GCM):** Standar enkripsi tingkat tinggi **AES-256-GCM** terautentikasi (Format v2) diterapkan secara spesifik pada pembuatan berkas cadangan (*backup file*) dan ekspor data, baik saat disimpan sebagai berkas portabel lokal maupun saat diunggah ke Google Drive pengguna.
5. **Kedaulatan & Pengendalian Data:** DompetNalar tidak menjual data pribadi pengguna kepada pihak mana pun. Pengembang tidak mengoperasikan server basis data terpusat untuk menampung data keuangan pengguna. Data keuangan Anda secara bawaan tersimpan di perangkat lokal, dan data tertentu hanya dikirim ke layanan pihak ketiga (seperti Google Drive atau Google AdMob) ketika Anda menggunakan fitur terkait sebagaimana diuraikan dalam Bagian 5.

---

## 3. Fitur Pencadangan & Google Drive

1. **Pencadangan Mandiri:** DompetNalar menyediakan fitur pencadangan (*backup*) dan pemulihan (*restore*) data yang dijalankan atas inisiatif pengguna atau melalui jadwal pengingat lokal yang dikonfigurasi pengguna. Aplikasi tidak menyinkronkan data keuangan secara otomatis atau *real-time* ke server cloud pihak ketiga.
2. **Enkripsi Sebelum Pengunggahan:** Seluruh data yang dicadangkan dienkripsi terlebih dahulu di perangkat menggunakan algoritma AES-256-GCM terautentikasi sebelum berkas cadangan dibuat atau diunggah.
3. **Akses Google Drive (OAuth Least-Privilege):** Jika pengguna memilih untuk menghubungkan Google Drive, aplikasi meminta izin akses terbatas (*scope*) `https://www.googleapis.com/auth/drive.file`. DompetNalar hanya memiliki akses ke berkas dan folder yang dibuatnya sendiri di akun Google Drive pribadi pengguna, yaitu pada folder bernama **`FinSmart_Backups`**. DompetNalar tidak dapat melihat atau membaca berkas lain di Google Drive pengguna.

---

## 4. Izin Akses Perangkat (Device Permissions)

DompetNalar meminta izin perangkat hanya saat fitur terkait digunakan secara langsung oleh pengguna:

1. **Kamera (`android.permission.CAMERA`):** Digunakan saat pengguna mengaktifkan fitur pemindaian struk belanja fisik (Scan Nota) untuk mengambil foto nota yang akan dianalisis.
2. **Mikrofon (`android.permission.RECORD_AUDIO`):** Digunakan saat pengguna menekan tombol mikrofon pada fitur pencatatan pengeluaran berbasis suara (*Voice-to-Text Expense Logging*). Fitur ini memanfaatkan antarmuka pengenalan suara (*SpeechRecognizer*) pada sistem operasi Android. Pemrosesan suara bergantung pada konfigurasi mesin pengenalan suara yang aktif di perangkat pengguna (layanan sistem atau layanan ucapan Google).
3. **Notifikasi (`android.permission.POST_NOTIFICATIONS`):** Digunakan untuk menampilkan pengingat jatuh tempo tagihan, pengingat pencatatan, dan peringatan batas anggaran secara lokal di perangkat.
4. **Transparansi Fitur SMS:** Aplikasi DompetNalar saat ini **tidak** menggunakan atau meminta izin pembacaan SMS (`RECEIVE_SMS`, `READ_SMS`) maupun layanan pendengar notifikasi sistem (*Notification Listener Service*).

---

## 5. Layanan Pihak Ketiga & Transmisi Data

DompetNalar berinteraksi dengan layanan pihak ketiga resmi berikut untuk mendukung fungsionalitas tertentu. Data tertentu hanya dikirimkan atau diproses oleh pihak ketiga ketika fitur terkait digunakan:

1. **Pemrosesan Kecerdasan Buatan & OCR Lokal di Perangkat (On-Device Local AI & ML Kit OCR):**
   - **Kondisi Penggunaan:** Digunakan saat pengguna memanfaatkan fitur cerdas, yaitu:
     - Foto struk belanja fisik (pada fitur Scan Nota OCR).
     - Tangkapan layar (*screenshot*) tagihan PayLater (pada fitur Scan Tagihan PayLater).
     - Teks pesan input transaksi pengguna (pada fitur Pencatatan Transaksi Chatbot).
     - Ringkasan analitik data keuangan (total pemasukan, pengeluaran, rincian per kategori, batas limit anggaran bulanan) beserta pertanyaan pengguna (pada fitur Konsultasi Keuangan Chatbot).
   - **Tujuan Pemrosesan:** Untuk mengekstrak nominal, tanggal, dan kategori transaksi secara deterministik, serta menghitung analisis kesehatan keuangan personal.
   - **Pemrosesan On-Device & Tanpa Transmisi Cloud:** Seluruh pemrosesan teks percakapan chatbot dan kueri analitik keuangan dilakukan 100% secara lokal di perangkat pengguna. Pemindaian OCR nota dan tagihan menggunakan model pengenalan teks Google ML Kit yang berjalan langsung di perangkat (*on-device*). Gambar nota, tagihan, teks percakapan, dan data keuangan Anda **TIDAK** pernah dikirimkan ke Google Gemini API (`generativelanguage.googleapis.com`) atau server AI eksternal manapun.
   - **Komponen Model:** Pustaka Google ML Kit Text Recognition memanfaatkan modul pengenalan teks lokal yang dikelola secara aman oleh Google Play Services di perangkat tanpa memerlukan pengunggahan data gambar pengguna ke internet.

2. **Google Play Billing:**
   - Digunakan untuk memproses transaksi pembelian produk digital atau langganan dalam aplikasi (*In-App Purchases*) seperti Smart Master.
   - Seluruh transaksi diproses langsung oleh sistem Google Play Store. Pengembang tidak mengumpulkan atau menyimpan detail metode pembayaran, kartu kredit, atau informasi perbankan pengguna.

3. **Google AdMob (Google Mobile Ads SDK):**
   - Ditampilkan pada versi gratis aplikasi. SDK pihak ketiga Google Mobile Ads dapat memproses data teknis (seperti pengenal perangkat semu/Advertising ID sesuai konfigurasi perangkat, alamat IP, dan data diagnostik) untuk penayangan iklan, pengukuran kinerja, keamanan, serta pencegahan penipuan (*fraud prevention*).
   - Kebijakan Privasi Google AdMob dapat dipelajari di [https://policies.google.com/privacy](https://policies.google.com/privacy).
   - Penayangan iklan dinonaktifkan untuk pelanggan paket Smart Master hanya apabila hak kepemilikan langganan (*valid entitlement*) terdeteksi oleh aplikasi.

4. **Firebase Authentication & Cloud Firestore:**
   - **Firebase Authentication:** Digunakan untuk otentikasi identitas akun Google secara aman melalui Android Credential Manager / Google Sign-In. Pengembang hanya menerima identitas dasar terverifikasi (ID unik akun Google, alamat email, dan nama tampilan) untuk mengasosiasikan sesi pengguna.
   - **Cloud Firestore:** Digunakan secara terbatas dan selektif hanya untuk:
     1. Pengiriman umpan balik pengguna (*Feedback/Rating*) yang dikirimkan secara eksplisit oleh pengguna, tanpa menyertakan riwayat transaksi keuangan privat.
     2. Verifikasi status hak akses pengujian (*Remote Tester / Developer Grant*) oleh administrator resmi.
   - Kebijakan Privasi Firebase tunduk pada ketentuan Google Firebase di [https://firebase.google.com/support/privacy](https://firebase.google.com/support/privacy).

5. **Google Drive (Cadangan Data Terenkripsi):**
   - Fitur pencadangan awan (*Cloud Backup*) bersifat sepenuhnya opsional dan hanya berjalan atas perintah eksplisit pengguna.
   - Seluruh arsip data keuangan dienkripsi secara ketat di perangkat sebelum dikirim menggunakan algoritma standar industri AES-256-GCM dengan kunci turunan unik berbasis sandi pengguna (PBKDF2).
   - File arsip terenkripsi disimpan langsung di folder penyimpanan privat akun Google Drive pengguna sendiri (`appDataFolder`) dan tidak dapat diakses atau diintip oleh pengembang maupun pihak ketiga lainnya.

---

## 6. Hak Pengguna & Penghapusan Akun/Data (Account & Data Deletion)

### A. Penghapusan Melalui Aplikasi (*In-App Deletion*)
Pengguna dapat menghapus akun dan seluruh data lokal secara permanen kapan saja langsung dari dalam aplikasi:
1. Buka menu **Pengaturan & Akun** di DompetNalar.
2. Pilih menu **Hapus Akun & Data Permanen**.
3. Masukkan kata sandi akun untuk verifikasi (atau konfirmasi langsung bagi sesi Akun Tamu).
4. Setelah dikonfirmasi, seluruh catatan transaksi, dompet, hutang, anggaran, dan data profil pengguna akan segera dimusnahkan secara permanen dari basis data lokal perangkat, tugas terjadwal *WorkManager* dibatalkan, dan koneksi sesi diputuskan.

### B. Permintaan Bantuan Penghapusan Eksternal (*External Deletion Request*)
Bagian ini disediakan bagi pengguna yang ingin mengajukan permohonan bantuan penghapusan atau yang tidak lagi memiliki akses ke aplikasi:
- **Karakteristik Data:** Karena DompetNalar beroperasi dengan arsitektur *offline-first* dan pengembang tidak menyimpan salinan data keuangan Anda di server cloud milik pengembang, data yang tersimpan di dalam perangkat Anda berada di bawah kendali fisik Anda sendiri dan **tidak dapat dihapus secara jarak jauh oleh pengembang**.
- **Penghapusan Mandiri di Perangkat:** Menghapus data aplikasi melalui menu pengaturan sistem Android atau menghapus instalasi (*uninstall*) aplikasi DompetNalar akan menghapus seluruh data lokal yang ada pada perangkat Anda.
- **Berkas Cadangan di Google Drive:** Berkas cadangan yang pernah diunggah ke Google Drive tersimpan di penyimpanan cloud pribadi Anda pada folder `FinSmart_Backups`. Anda dapat menghapus berkas cadangan tersebut secara mandiri kapan saja melalui aplikasi atau situs Google Drive. Pengembang tidak dapat menghapus berkas di Google Drive pribadi Anda dari jarak jauh.
- **Pengajuan Bantuan Melalui Email Dukungan:** Pengguna dapat mengirimkan surat elektronik ke email dukungan kami:
  - **Alamat Email:** lunachroc01@gmail.com
  - **Subjek Email:** `Permintaan Penghapusan Akun DompetNalar`
  - **Pemberitahuan Keamanan Penting:** Jangan pernah menyertakan kata sandi, PIN, API Key, atau informasi sensitif lainnya dalam email Anda. Tim dukungan kami akan memandu langkah-langkah penghapusan data atau membantu memastikan bahwa data sesi Anda telah bersih.

---

## 7. Kontak Pengembang

Jika Anda memiliki pertanyaan mengenai dokumen kebijakan privasi ini atau membutuhkan bantuan terkait data Anda, hubungi kami melalui:

- **Nama Pengembang:** SleepyKoala
- **Email Dukungan:** lunachroc01@gmail.com
- &copy; 2026 SleepyKoala. All Rights Reserved.
