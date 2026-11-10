# RENCANA IMPLEMENTASI: PERBAIKAN 4 DEFECT UJI FISIK HP REALME GT NEO 3T

## [Goal Description]
Menyelesaikan 4 defect nyata yang ditemukan Owner pada pengujian fisik di perangkat **Realme GT Neo 3T** (Android 13/14, Realme UI):
1. **Crash Pergantian Bahasa:** Pemilihan bahasa di Pengaturan atau Dialog Onboarding menyebabkan aplikasi langsung menutup (*force close*).
2. **Teks Hardcoded (Bilingual Localization):** Banyak teks antarmuka yang masih tercampur antara Bahasa Indonesia dan Bahasa Inggris pada layar *Perencana Bebas Utang (Debt Planner)*, *Impor Tagihan PayLater*, dan *Anggaran (Smart Budget)*.
3. **Bug UI Tombol Terpotong:** Tombol "Bagikan ke WhatsApp" / "Share to WhatsApp" dan "Salin Teks" / "Copy Text" di layar *Split Bill* terpotong secara vertikal di bagian bawah.
4. **Crash Pop-up Awal Instalasi / Update:** Pop-up pemilihan mata uang dan bahasa pada pemasangan pertama / reset mengalami crash saat tombol "Simpan & Mulai" diklik dan pop-up kembali muncul saat dibuka ulang.

---

## User Review Required
> [!IMPORTANT]
> **PENGHENTIAN RECREATE ACTIVITY & PENGGUNAAN REAKTIF JETPACK COMPOSE:**
> Karena DompetNalar dibangun 100% berbasis **Jetpack Compose**, seluruh teks antarmuka dikontrol secara dinamis oleh `LocaleHelper.currentLanguage` (`StateFlow`) dan `AppStrings.t()`.
> Pemanggilan sistem Android lama `activity.recreate()` dan mutasi `LocaleManager.applicationLocales` terbukti merusak susunan task Android yang menggunakan `<activity-alias>` (`MainActivityDefault` untuk dynamic launcher icon) pada HP Realme GT Neo 3T (Realme UI / ColorOS).
> **Solusi Permanen:** Pergantian bahasa akan sepenuhnya dikendalikan secara in-memory reaktif oleh Compose snapshot tanpa me-restart Activity. Layar akan berganti bahasa secara instan (*smooth zero-recreate*), tanpa kedipan layar, dan 100% bebas dari risiko force close.

---

## Open Questions
Tidak ada pertanyaan pemblokir. Pendekatan perbaikan sepenuhnya memanfaatkan arsitektur reaktif Jetpack Compose yang sudah ada tanpa menambah dependensi baru (Mandat Anggaran Nol Rupiah / 100% FOSS).

---

## Proposed Changes

### Lapis 1 & 2: Fondasi Data & Logika Bahasa (LocaleHelper & AppStrings)

#### [MODIFY] `app/src/main/java/com/example/util/LocaleHelper.kt`
- Hapus pemanggilan `localeManager.applicationLocales` dan `activity.recreate()`.
- Mutasi `_currentLanguage.value = language`, simpan ke `SharedPreferences`, dan set `Locale.setDefault(locale)` untuk format angka/tanggal.
- Perbarui konfigurasi Context Resources secara aman tanpa mengganggu Activity lifecycle.
- Hapus pembungkusan `createConfigurationContext` di `wrapContext` agar context Activity tidak kehilangan window token.

#### [MODIFY] `app/src/main/java/com/example/MainActivity.kt`
- Sederhanakan `attachBaseContext` agar meneruskan `newBase` secara langsung tanpa pembungkus buatan yang merusak window token Compose.

#### [MODIFY] `app/src/main/java/com/example/util/AppStrings.kt`
- Tambahkan kamus terjemahan untuk kategori tagihan PayLater (`translatePayLaterCategory`).
- Tambahkan kamus terjemahan untuk kategori anggaran (`translateCategory`).
- Tambahkan kamus terjemahan untuk judul strategi pelunasan utang (`translatePayoffStrategy`).
- Tambahkan string dwibahasa yang sebelumnya belum terakomodasi di dialog dan kartu hutang/anggaran.

---

### Lapis 3: Antarmuka Visual & Dialog (UI-Last)

#### [MODIFY] `app/src/main/java/com/example/ui/components/FirstRunOnboardingDialog.kt`
- Balik urutan eksekusi tombol "Simpan & Mulai":
  1. Simpan preferensi onboarding (`OnboardingManager.completeOnboarding(context)`) terlebih dahulu.
  2. Terapkan preferensi mata uang (`CurrencyFormatter`).
  3. Terapkan bahasa (`LocaleHelper.setLanguage`).
  4. Jalankan callback `onCompleted()`.
- Memastikan onboarding tersimpan permanen dan dialog menutup mulus tanpa crash.

#### [MODIFY] `app/src/main/java/com/example/ui/screens/SplitBillScreen.kt`
- Hapus `.height(48.dp)` kaku pada tombol "Bagikan ke WhatsApp" dan "Salin Teks".
- Berikan `.defaultMinSize(minHeight = 44.dp)` dengan `contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)`.
- Atur `maxLines = 1` dengan `overflow = TextOverflow.Ellipsis` atau izinkan penyesuaian tinggi otomatis agar teks baris kedua tidak terpotong vertikal.

#### [MODIFY] `app/src/main/java/com/example/ui/screens/BudgetScreen.kt`
- Perbaiki teks tombol FAB: ubah `Text(AppStrings.t("+ Kategori", "+ Category"))` menjadi `Text(AppStrings.t("Kategori", "Category"))` agar tidak menghasilkan double plus `+ + Category`.
- Gunakan `AppStrings.translateCategory(category.name)` pada daftar anggaran per kategori sehingga saat bahasa Inggris aktif, kategori seperti "Belanja & Hobi" tampil sebagai "Shopping & Hobbies".

#### [MODIFY] `app/src/main/java/com/example/ui/components/DebtPlannerModalDialog.kt`
- Ubah seluruh teks hardcoded bahasa Indonesia ke format dwibahasa:
  - "Bulan Berjalan" -> `AppStrings.t("Bulan Berjalan", "Current Month")`
  - "Estimasi" -> `AppStrings.t("Estimasi", "Estimated")`
  - "Bulan Berikutnya" -> `AppStrings.t("Bulan Berikutnya", "Next Month")`
  - "Rincian kewajiban..." -> `AppStrings.t(...)`
  - "Tidak ada tagihan yang dijadwalkan untuk bulan berikutnya." -> `AppStrings.t(...)`
  - "Impor Tagihan dari Screenshot" -> `AppStrings.t(...)`
  - "Scan otomatis Shopee PayLater..." -> `AppStrings.t(...)`
  - Filter chip strategi pelunasan -> gunakan judul terjemahan dwibahasa.

#### [MODIFY] `app/src/main/java/com/example/ui/components/PayLaterBillReviewDialog.kt`
- Tombol simpan: ubah `"Simpan Item Ini"` / `"Simpan Tagihan"` menjadi `AppStrings.t("Simpan Item Ini", "Save This Item")` / `AppStrings.t("Simpan Tagihan", "Save Bill")`.
- Chip kategori: gunakan label terjemahan dwibahasa untuk "Pinjaman Online", "Kartu Kredit", "Cicilan Kendaraan", dan "Lain-lain".
- Dialog notifikasi dan konfirmasi diselaraskan dwibahasa secara tuntas.

---

## Verification Plan

### Automated Tests (Terminal Headless)
Jalankan verifikasi regresi penuh di terminal:
```bash
./gradlew testDebugUnitTest
```
- Seluruh 995+ unit test wajib **LULUS 100% (PASS, 0 failure, 0 error)**.
- Tambahkan unit test baru di `LocalizationBilingualTest.kt` untuk memvalidasi:
  1. `LocaleHelper.setLanguage()` berjalan mulus tanpa memicu `recreate` atau `LocaleManager` exception.
  2. Dialog onboarding menyimpan status `isOnboardingCompleted == true` sebelum memicu pergantian bahasa.
  3. Terjemahan kategori PayLater dan strategi pelunasan utang berfungsi presisi pada kedua bahasa.

### Audit Detektor Kebohongan (Fake-Pass Audit)
```bash
python auto_sync.py audit
```
- Memastikan tidak ada kode `TODO`, `dummy`, `mock_data`, atau fungsi pura-pura.

### Build APK Debug Baru untuk Pengujian Fisik Ulang
```bash
./gradlew assembleDebug
```
- Menghasilkan berkas `app-debug.apk` baru untuk dipasang langsung ke HP Realme GT Neo 3T Owner guna memverifikasi bahwa crash hilang dan tampilan tombol rapi sempurna.
