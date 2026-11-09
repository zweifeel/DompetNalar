# FinSmart Developer Admin Panel (Fase 2: Web Foundation & Auth)

Panel administrasi pengembang berbasis web untuk FinSmart (SleepyKoala).

---

## 1. Prasyarat Lingkungan
- **Node.js**: Versi 18+ (Telah terverifikasi dengan Node.js v24.21.0 & npm v11.19.0).
- **Proyek Firebase Resmi**: `finsmart-47fdd` (Project Number: `1029968996578`).

---

## 2. Cara Mendapatkan Konfigurasi Firebase Web SDK
File `google-services.json` di aplikasi Android diperuntukkan khusus bagi aplikasi mobile Android dan **tidak memuat Web App ID** (`1:1029968996578:web:...`).

Ikuti langkah resmi berikut untuk mendapatkan konfigurasi Web:
1. Masuk ke [Firebase Console](https://console.firebase.google.com/).
2. Buka proyek **`finsmart-47fdd`**.
3. Klik ikon gerigi (Project Settings) $\rightarrow$ tab **General**.
4. Gulir ke bawah ke bagian **Your apps**.
5. Jika belum ada aplikasi Web, klik tombol **Add app** $\rightarrow$ pilih ikon Web (`</>`), beri nama `"FinSmart Admin Panel"`.
6. Salin objek `firebaseConfig` yang ditampilkan ke berkas `.env` lokal di direktori `web-admin/`.

---

## 3. Langkah Menjalankan di Komputer Lokal

```bash
# 1. Masuk ke direktori web-admin
cd web-admin

# 2. Salin template lingkungan
cp .env.example .env

# 3. Isi variabel di dalam .env sesuai kredensial Web App dari Firebase Console
# (Pastikan VITE_FIREBASE_PROJECT_ID tetap "finsmart-47fdd")

# 4. Instal dependensi
npm install

# 5. Jalankan server pengembang lokal
npm run dev
```

Buka peramban di `http://localhost:3000`.

---

## 4. Keamanan Autentikasi Admin
- **Hanya Akun Google Terdaftar:** Sesi masuk via Google dibatasi secara ketat untuk email pengembang tepercaya (`lunachroc01@gmail.com`, `zweifeel@gmail.com`).
- **Zero Secret in Code:** Kunci privat service-account tidak pernah dimasukkan ke dalam source code peramban web.
- **Isolasi Data Finansial:** Data transaksi, saldo, dan utang pengguna disimpan secara privat di HP klien (SQLite Room) dan **tidak dapat diakses** oleh Web Admin Panel untuk menjaga privasi mutlak pengguna.
