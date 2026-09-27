# Panduan Verifikasi Kepemilikan AdMob (app-ads.txt) FinSmart

Panduan ini menjelaskan cara mempublikasikan dan mengonfigurasi file `app-ads.txt` untuk verifikasi kepemilikan Google AdMob pada distribusi APK non-Play Store maupun Play Store.

---

## 1. Format File `app-ads.txt`
File `app-ads.txt` harus memuat satu baris standar resmi dari Google AdMob berikut:

```text
google.com, pub-XXXXXXXXXXXXXXXX, DIRECT, f08c47fec0942fa0
```

> **Catatan:** Ganti `pub-XXXXXXXXXXXXXXXX` dengan **AdMob Publisher ID** milik Anda (dapat ditemukan di konsol AdMob pada menu *Pengaturan Akun / Account Settings*).

---

## 2. Cara Penempatan di Repositori GitHub & Hosting

### Opsi A: Menggunakan GitHub Pages (Direkomendasikan)
1. Letakkan file `app-ads.txt` di **root repositori GitHub** Anda (misalnya: `https://github.com/username/FinSmart/app-ads.txt`).
2. Buka menu **Settings** repositori GitHub -> **Pages**.
3. Pada bagian **Source**, pilih branch `main` (atau `master`) dan folder `/ (root)`. Klik **Save**.
4. GitHub Pages akan mempublikasikan situs web Anda pada URL: `https://username.github.io/app-ads.txt`.

### Opsi B: Domain Pribadi atau Custom Domain
Jika Anda memiliki domain sendiri (misalnya `finsmart.id`), unggah file `app-ads.txt` ke root direktori web server Anda sehingga dapat diakses secara publik di:
`https://finsmart.id/app-ads.txt`

---

## 3. Menghubungkan Developer Website di Google Play Console / AdMob
1. Masuk ke **Google AdMob Console** -> menu **Apps** -> **All Apps** -> **app-ads.txt**.
2. Masukkan URL situs web pengembang Anda (misalnya `https://username.github.io` atau `https://finsmart.id`).
3. AdMob Crawler akan secara otomatis memindai URL `https://domain-anda/app-ads.txt` dalam kurun waktu 24 jam.
4. Setelah terverifikasi, status pada konsol AdMob akan berubah menjadi **Green / Authorized**.

---

## 4. Konfigurasi AdMob Unit ID di Kode Aplikasi (`AppConfig.kt`)
Untuk mengubah ID Iklan Uji Coba (Test Ad Unit IDs) ke ID Iklan Produksi milik Anda:
1. Buka berkas `app/src/main/java/com/example/config/AppConfig.kt`.
2. Perbarui konstanta berikut dengan ID asli dari Konsol AdMob Anda:
   ```kotlin
   const val ADMOB_APP_ID = "ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY"
   const val ADMOB_BANNER_AD_UNIT_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ"
   const val ADMOB_REWARDED_AD_UNIT_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/WWWWWWWWWW"
   ```
