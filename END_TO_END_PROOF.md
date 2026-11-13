# END_TO_END_PROOF.md
# BUKTI 4 PILAR END-TO-END: PEMENUHAN GOOGLE PLAY TARGET SDK 36 (ANDROID 16) & RILIS BUNDLE AAB v1.0.17 (BUILD 17)

## Pilar 1: Input / Pemicu
- **Penaikan Target SDK & Versi Gradle (`app/build.gradle.kts`):**
  - `targetSdk` dinaikkan ke `36` (Android 16) memenuhi syarat wajib Google Play Console.
  - `versionCode` dinaikkan ke `17`.
  - `versionName` dinaikkan ke `"1.0.17"`.
- **Pembaruan In-App Changelog (`ChangelogBottomSheet.kt`):**
  - Header versi diselaraskan ke `Versi Terbaru v1.0.17 (Build 17)`.
  - Penambahan deskripsi kepatuhan standar Google Play API Level 36 dan perbaikan UI/crash bahasa.
- **Penerbitan Dokumen Changelog Resmi:**
  - Pembuatan `CHANGELOG_VERSION_1.0.17.txt` dan arsip di `Artifact/`.
  - Memuat Bagian A (audit teknis API 36) dan Bagian B (catatan rilis ringkasan Play Console ID & EN).
- **Perintah Eksekusi Kompilasi Resmi:**
  - `./gradlew bundleRelease -PplayStoreBuild=true`

## Pilar 2: Logika Proses & Rilis Produksi
- **Kompilasi & Optimasi Kode Produksi:**
  - Eksekusi kompilasi Kotlin/Java dengan target Android SDK 36.
  - Optimasi ProGuard/R8 aktif (`isMinifyEnabled = true`, `isShrinkResources = true`).
  - Flag produksi Google Play Store aktif (`IS_PLAY_STORE_BUILD = true`).
- **Penandatanganan Digital (Keystore Signing):**
  - Ditandatangani otomatis oleh Gradle menggunakan kunci rilis resmi `my-release-key.jks`.
  - Alias: `kodegawa` (RSA 2048-bit, berlaku hingga 25 September 2051).

## Pilar 3: Penyimpanan / Output
- **Berkas Rilis Android App Bundle (.aab):**
  - Berkas Utama: `app/build/outputs/bundle/release/app-release.aab`
  - Berkas Ekspor Terstandar: `DompetNalar-v1.0.17-versionCode17-release.aab` dan `Artifact/DompetNalar-v1.0.17-versionCode17-release.aab`
  - Ukuran Berkas: **22.293.098 bytes (~21,26 MB)**
  - Waktu Terbit: 13 November 2026, 20:43 WIB
- **Validasi Integritas Tanda Tangan:**
  - Hasil `jarsigner -verify`: **`jar verified.`** (Sertifikat valid & utuh).

## Pilar 4: Uji Regresi & Detektor Kebohongan
- **Pengujian Unit Otomatis (Full Regression Suite):** `./gradlew testDebugUnitTest`
  - Hasil: **BUILD SUCCESSFUL, 998 Unit Tests LULUS 100% (0 failure, 0 error)**.
  - `AdMobIntegrationSecurityTest` terverifikasi menyelaraskan `versionCode = 17` dan `versionName = "1.0.17"` pada `AppConfig`.
- **Audit Detektor Kebohongan (Fake-Pass Audit):** `python auto_sync.py audit`
  - Hasil: **100% BERSIH** dari kata kunci TODO, Dummy, Placeholder, dan Mock.
