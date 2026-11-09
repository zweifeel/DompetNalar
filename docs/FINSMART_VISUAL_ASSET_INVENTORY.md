# FINSMART — INVENTARIS VISUAL ASSET (VISUAL ASSET INVENTORY)
**Dokumen:** `docs/FINSMART_VISUAL_ASSET_INVENTORY.md`  
**Versi:** 1.0.0  
**Tanggal:** 10 Oktober 2026  
**Status Audit:** COMPREHENSIVE VISUAL PIPELINE AUDIT COMPLETED

---

## 1. Ringkasan Status Visual Pipeline

FinSmart saat ini mengadopsi arsitektur Jetpack Compose modern di mana sebagian besar elemen UI, ikon navigasi, dan indikator status dirender secara prosedural menggunakan Material3 Vector Icons dan Canvas primitives. Resource raster bawaan proyek saat ini sangat minimal (`finsmart_logo_*.jpg` dan adaptive launcher vector XML).

Untuk menghadirkan pengalaman visual kelas satu (*premier financial experience*), diperlukan pembuatan asset visual berkualitas tinggi untuk ilustrasi empty state, ikon launcher adaptif, badge gamifikasi, dan branding onboarding.

---

## 2. Tabel Inventaris Visual Asset FinSmart

| ID | Fitur | Nama Asset | Format Target | Ukuran / Rasio | Status Aktual | Lokasi Penggunaan | Prioritas |
|:---|:---|:---|:---:|:---:|:---:|:---|:---:|
| **AST-ICN-01** | App Icon | `ic_launcher_foreground.xml` / PNG | Vector XML / PNG | 108dp (432x432 px) | **Sudah tersedia dan digunakan** | Launcher Android (`res/mipmap/`, `res/drawable/`) | P0 (Kritis) |
| **AST-ICN-02** | App Icon | `ic_launcher_background.xml` | Vector XML / Solid Color | 108dp (432x432 px) | **Sudah tersedia dan digunakan** | Background icon launcher | P0 (Kritis) |
| **AST-ICN-03** | App Icon | `ic_launcher_monochrome.xml` | Vector XML (Monokrom) | 108dp (1:1) | **Perlu dibuat** | Android 13+ Themed Icons | P1 (Tinggi) |
| **AST-ICN-04** | Dynamic Launcher | Icon Edisi Gold / Smart Master | Vector / WebP | 108dp | **Sudah tersedia (Background XML)** | `AppIconManager`, Dynamic Alias | P2 (Sedang) |
| **AST-SPL-01** | Splash Screen | `splash_logo_branding.xml` | Vector Drawable | 288x288 px (1:1) | **Perlu diperbaiki** | `MainActivity`, Android 12+ Splash API | P0 (Kritis) |
| **AST-ONB-01** | Onboarding | `ill_onboarding_privacy.png` | WebP Lossless | 1080x1080 px (1:1) | **Perlu dibuat** | Slide 1 Onboarding: Privacy-First & Offline SQLite | P1 (Tinggi) |
| **AST-ONB-02** | Onboarding | `ill_onboarding_ai_ocr.png` | WebP Lossless | 1080x1080 px (1:1) | **Perlu dibuat** | Slide 2 Onboarding: Scan Struk Nota Cerdas | P1 (Tinggi) |
| **AST-ONB-03** | Onboarding | `ill_onboarding_freedom.png` | WebP Lossless | 1080x1080 px (1:1) | **Perlu dibuat** | Slide 3 Onboarding: Rencana Bebas Utang | P1 (Tinggi) |
| **AST-EMP-01** | Dashboard | `ill_empty_transactions.png` | WebP Transparan | 600x600 px (1:1) | **Perlu dibuat** | Empty State mutasi belum ada transaksi | P1 (Tinggi) |
| **AST-EMP-02** | Dompet | `ill_empty_wallets.png` | WebP Transparan | 600x600 px (1:1) | **Perlu dibuat** | Empty State daftar dompet kosong | P2 (Sedang) |
| **AST-EMP-03** | Utang & Tagihan | `ill_empty_debts.png` | WebP Transparan | 600x600 px (1:1) | **Perlu dibuat** | Empty State "Selamat, Anda Bebas Utang!" | P1 (Tinggi) |
| **AST-EMP-04** | Notifikasi | `ill_empty_notifications.png` | WebP Transparan | 600x600 px (1:1) | **Perlu dibuat** | `NotificationCenterDialog` kosong | P2 (Sedang) |
| **AST-GAM-01** | Gamifikasi | `badge_streak_7_rookie.png` | PNG Transparan | 256x256 px (1:1) | **Perlu dibuat** | Badge Rookie Planner (Streak 7 Hari) | P2 (Sedang) |
| **AST-GAM-02** | Gamifikasi | `badge_streak_30_master.png` | PNG Transparan | 256x256 px (1:1) | **Perlu dibuat** | Badge Budget Master (Streak 30 Hari) | P1 (Tinggi) |
| **AST-GAM-03** | Gamifikasi | `badge_streak_90_guru.png` | PNG Transparan | 256x256 px (1:1) | **Perlu dibuat** | Badge Financial Guru (Streak 90 Hari) | P2 (Sedang) |
| **AST-GAM-04** | Gamifikasi | `badge_streak_365_centurion.png` | PNG Transparan | 256x256 px (1:1) | **Perlu dibuat** | Badge Centurion FinSmart (Streak 365 Hari) | P2 (Sedang) |
| **AST-WLP-01** | Wallpaper Dasbor | Wallpaper Bawaan Galeri | JPEG / PNG | Resolusi Layar (9:16) | **Sudah tersedia (Photo Picker)** | `ThemeEngine`, Dasbor FinSmart | P1 (Tinggi) |
| **AST-AI-01** | AI Chat Advisor | `avatar_gemini_advisor.png` | PNG Transparan | 128x128 px (1:1) | **Perlu dibuat** | Avatar asisten AI FinSmart di `AiChatScreen` | P1 (Tinggi) |
| **AST-PAY-01** | Paywall Dialog | `banner_smart_master_gold.png`| WebP | 800x400 px (2:1) | **Perlu dibuat** | Header banner di `SmartMasterPaywallDialog` | P1 (Tinggi) |

---

## 3. Klarifikasi & Keputusan Teknis Penting

### G1. Dynamic Launcher vs Adaptive Icon vs Themed Icon vs Widgets
1. **Adaptive Launcher Icon (Aktif & Wajib):**
   - Menggunakan layer terpisah `ic_launcher_background` dan `ic_launcher_foreground` di `res/mipmap-anydpi-v26/`.
   - Wajib untuk memenuhi standar Google Play Store dan adaptif di berbagai OEM launcher (lingkaran, squircles, rounded square).
2. **Themed Icons Android 13+ (Pekerjaan Tertunda):**
   - Memerlukan aset monokrom tunggal (`<monochrome android:drawable="..." />` di XML launcher) agar warna ikon dapat beradaptasi otomatis dengan tema *Material You Dynamic Color* ponsel pengguna.
3. **Dynamic Launcher Icon Aliases (Edisi Khusus):**
   - FinSmart memiliki implementasi `AppIconManager` yang mengaktifkan/menonaktifkan `<activity-alias>` di AndroidManifest untuk edisi Gold, Cyberpunk, Dark, dan MasterDev.
   - **Catatan Teknis:** Mengganti activity-alias saat runtime dapat memicu restart singkat pada launcher Android di beberapa perangkat vendor (Samsung OneUI, Xiaomi MIUI). Fitur ini aman dipertahankan sebagai opsi kosmetik opsional bagi pengguna dengan streak tinggi.
4. **Widget Layar Utama:**
   - Belum diimplementasikan (*Negative Scope* pada rilis ini) demi menjaga fokus performa rilis pertama.

### G2. Keputusan Integrasi: Hadiah Streak 30 Hari vs Dashboard Wallpaper
* **Temuan Audit:**
  - Sebelumnya terdapat duplikasi konseptual: di `GamificationHubDialog` disebut "Custom Background Galeri (Streak 30+ Hari)", sedangkan di `SettingsScreen` disebut "Latar Belakang Dasbor (Wallpaper)".
  - Keduanya sama-sama memanggil backend penyimpanan yang identik: `ThemeEngine.setCustomWallpaper(context, uri, opacity)`.
* **Keputusan Arsitektur Final:**
  - **Satu Sistem Terpadu (Unified Architecture):** Tidak boleh ada dua engine wallpaper atau penyimpanan foto terpisah.
  - Fitur kustomisasi wallpaper dasbor adalah **hadiah kosmetik eksklusif** yang terbuka secara resmi setelah pengguna mencapai **Streak 30 Hari** di modul gamifikasi, atau memiliki status **Smart Master 👑** / **MasterDev 👑**.
  - Pada `SettingsScreen`, jika streak pengguna $< 30$ hari dan bukan Smart Master, opsi tombol galeri dinonaktifkan dengan label jelas: *"Terkunci: Membutuhkan Streak 30 Hari di Gamifikasi atau Smart Master 👑"*.
  - Ketika streak mencapai $\ge 30$ hari, opsi terbuka penuh baik dari menu Pengaturan maupun Hub Gamifikasi.

---
*Inventaris asset ini menjadi acuan spesifikasi pembuatan prompt AI pada dokumen pendamping.*
