# Audit Lingkungan (Environment Audit)

**Waktu Audit:** 2026-10-12 01:18:36 WIB  
**Direktori Proyek:** `E:\FinSmart`  
**Host OS:** Windows 10/11 x64  
**Java Version:** Eclipse Adoptium JDK 17.0.20.101 (`C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`)

---

## 1. Alat Android & SDK
- **Android SDK Directory:** `E:\AndroidStudioSDK`
- **ADB Path:** `E:\AndroidStudioSDK\platform-tools\adb.exe`
- **Emulator Path:** `E:\AndroidStudioSDK\emulator\emulator.exe`
- **Build Tools Version:** `36.0.0` (`E:\AndroidStudioSDK\build-tools\36.0.0`)
- **AVD Directory:** `E:\Android\avd`
- **Daftar AVD Terdeteksi:** `Pixel_10_2` (`Pixel_10_2.avd`)

---

## 2. Perangkat Terhubung (ADB Devices)
```
emulator-5554 device product:sdk_gphone16k_x86_64 model:sdk_gphone16k_x86_64 device:emu64xa16k transport_id:1
```
- **Emulator:** `emulator-5554` aktif dan siap (`device`).
- **HP Fisik:** Tidak terdeteksi via ADB saat audit. Sesuai instruksi, pengujian pada HP fisik ditandai `NOT RUN / BLOCKED (Hardware not attached via ADB)`, dan pengujian runtime difokuskan pada emulator aktif dengan status jelas.

---

## 3. Alat Multimedia & Video
- **FFmpeg Path:** `C:\Program Files\Softdeluxe\Free Download Manager\ffmpeg.exe`
- **FFmpeg Version:** 4.3.1 (Tersedia dan berfungsi).
- **Alat Rekam Layar:** `adb shell screenrecord` didukung oleh Android emulator.

---

## 4. Keamanan & Konfigurasi
- **Keystore / Local Properties:** Ditemukan di `local.properties` (lokal, diabaikan Git).
- **Target Build:** Hanya Debug (`assembleDebug`). Release dan AAB tidak akan dibangun.
