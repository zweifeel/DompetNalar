# DAFTAR HAMBATAN DAN CATATAN TEKNIS (KNOWN ISSUES)

**Aplikasi:** FinSmart Debug v1.0.3  
**Tanggal:** 2026-10-12  

---

## 1. Firestore Security Rules Belum Di-deploy ke Cloud

- **Gejala:** Developer Feedback Viewer menampilkan error akses cloud ditolak (`PERMISSION_DENIED`) jika dibuka saat cloud rules di server masih versi lama.
- **Penyebab:** File `E:\FinSmart\firestore.rules` telah diperbarui secara lokal untuk mendukung 3 admin (`lunachroc01@gmail.com`, `zweifeel@gmail.com`, `lunachroc07@gmail.com`), namun belum di-deploy ke Firebase Console sesuai batasan keamanan.
- **Tindakan Manual yang Diperlukan:** Pengembang dapat membuka [Firebase Console](https://console.firebase.google.com/) -> Firestore Database -> Rules -> Salin aturan dari `firestore.rules` -> Klik **Publish**.

---

## 2. Pengujian pada HP Fisik Tidak Dapat Dilakukan di Sesi Ini

- **Gejala:** Verifikasi banner AdMob pada HP fisik ditandai `BLOCKED / NOT RUN`.
- **Penyebab:** Perangkat fisik tidak terdeteksi pada koneksi ADB (`adb devices -l` hanya mendeteksi `emulator-5554`).
- **Mitigasi Teknis:** Perbaikan akar masalah (menonaktifkan COPPA flag `TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE` dan wrapping Activity context) telah diuji pada emulator dan unit test. Pengembang cukup memasang APK Debug terbaru (`app-debug.apk`) ke HP melalui kabel USB atau transfer file.

---

## 3. Fitur Cloud Eksternal Memerlukan Akun Google Aktif

- **Gejala:** Google Drive Backup dan Google Sign-In tidak dapat diuji otomatis secara headless tanpa akun Google nyata.
- **Penyebab:** Arsitektur FinSmart menggunakan Android Credential Manager dan OAuth 2.0 yang membutuhkan sesi login pengguna interaktif di Google Play Services.
- **Mitigasi:** Telah disediakan pengujian terisolasi (`GoogleAuthAndDriveCloudTest.kt`) dan opsi backup lokal offline terenkripsi (`ExportImportManager.kt`) yang 100% berfungsi mandiri.
