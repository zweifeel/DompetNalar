# LAPORAN AUDIT DAN PEMASANGAN APK FINSMART

**Tanggal & Waktu:** 2026-10-12 01:35:38 WIB  
**Direktori QA Run:** `E:\FinSmart\qa-recordings\run-20261012-011836`  
**Target Build:** Debug Variant (`assembleDebug`)  
**Status Instalasi:** SUCCESS (Berhasil dipasang tanpa menghapus data)

---

## 1. Audit Kandidat APK

| Lokasi File | Ukuran (Bytes) | Waktu Pembuatan | Package ID | Version | Build Variant | Status |
|---|---|---|---|---|---|---|
| `app\build\outputs\apk\debug\app-debug.apk` | 36,072,100 (~34.4 MB) | 2026-10-12 01:35:08 | `com.aistudio.finsmart.kptxvw.debug` | v1.0.3 (code 3) | Debug | **DIPILIH & TERBARU** |
| `store-assets\app-debug.apk` | Lama (pra-perbaikan) | Arsip lama | `com.aistudio.finsmart.kptxvw.debug` | v1.0.x | Debug | Dipertahankan (tidak disentuh) |

---

## 2. Identitas Lengkap APK Terpilih

- **Path Sumber:** `E:\FinSmart\app\build\outputs\apk\debug\app-debug.apk`
- **Path Salinan QA:** `E:\FinSmart\qa-recordings\run-20261012-011836\app-debug.apk`
- **Ukuran File:** 36,072,100 bytes
- **SHA-256 Checksum:** `C57B5A6AA018A39B2AE125EDF0CC75839AC994FBD1CF343DB774F55461067125`
- **Package Name:** `com.aistudio.finsmart.kptxvw.debug`
- **Version Code:** `3`
- **Version Name:** `1.0.3`
- **Compile SDK:** 36 (Android 16 preview)
- **Signature:** Android Debug Key (keystore debug standar Gradle)

---

## 3. Audit Perangkat dan Hasil Pemasangan

- **Perangkat Emulator:** `emulator-5554`
  - Model: `sdk_gphone16k_x86_64`
  - Status ADB: `device`
  - Transport ID: 1
- **Perangkat Fisik (HP Pengembang):**
  - Status: `BLOCKED / NOT RUN` (Perangkat fisik tidak terdeteksi pada daftar `adb devices -l` saat sesi pengujian berjalan).
- **Perintah Eksekusi:**  
  `adb -s emulator-5554 install -r E:\FinSmart\qa-recordings\run-20261012-011836\app-debug.apk`
- **Hasil ADB:** `Performing Streamed Install -> Success`
- **Integritas Data:** Data aplikasi, preferensi, dan database lokal sebelumnya dipertahankan secara utuh (tidak ada uninstall atau clear-data).
