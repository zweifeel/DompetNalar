# Plan: Audit dan Penuntasan Lokalisasi Dwibahasa End-to-End DompetNalar

## Summary
Audit menyeluruh dari hulu ke hilir terhadap seluruh sistem aplikasi DompetNalar membuktikan adanya 8 kluster lokalisasi yang menyebabkan aplikasi tetap berbahasa Indonesia di HP berbahasa asing (Realme GT Neo 3T) dan teks tidak tersinkronisasi otomatis saat bahasa diganti. Rencana ini menuntaskan akar penyebab level OS Android 13/14 (LocaleManager), 7 formatter tanggal/angka berkode keras `Locale("id", "ID")`, 47 notifikasi sistem, respons Chatbot AI lokal, laporan ekspor PDF/CSV/struk, validasi ViewModel/Toast, serta seluruh teks antarmuka pada 37+ dialog dan layar Compose secara bertahap menggunakan alur 3-Lapis (Data-First -> Headless Logic -> UI-Last).

## User Story
Sebagai pengguna perangkat Android dengan setelan bahasa sistem non-Indonesia (misal: English),
Saya ingin aplikasi DompetNalar otomatis tampil dalam Bahasa Inggris penuh sejak pertama kali dibuka dan seluruh teks (menu, form, chatbot, notifikasi, toast, struk, laporan) otomatis berganti secara instan saat preferensi bahasa diubah,
Agar pengalaman penggunaan pembukuan keuangan nyaman, konsisten, dan bebas dari teks campuran yang membingungkan.

## Problem → Solution
- **Kondisi Saat Ini**:
  1. Pada Android 13+ (API 33+), Realme UI/ColorOS mengunci per-app language `LocaleManager` ke `id` jika pernah tersetel sebelumnya, dan `LocaleHelper` tidak meresetnya ke `LocaleList.getEmptyLocaleList()` saat mode `SYSTEM` aktif, sehingga HP berbahasa Inggris tetap membuka aplikasi dalam Bahasa Indonesia.
  2. Sebanyak 7 tempat pemformatan tanggal dan angka (`SimpleDateFormat` dan `NumberFormat`) mengunci permanen `Locale("id", "ID")`.
  3. Terdapat 1.243 kemunculan string Bahasa Indonesia di 89 file Kotlin yang belum tersinkronisasi dwibahasa, mencakup notifikasi sistem, laporan ekspor, receipt digital, chatbot NLP, model data kategori/badge, toast validasi, dan layar UI.
- **Solusi Tuntas**:
  1. **Lapis 1 (Data-First & Engine Foundation)**:
     - Perbaiki `LocaleHelper.kt` agar menyelaraskan `LocaleManager` Android 13+ (mengosongkan ke `LocaleList.getEmptyLocaleList()` pada mode `SYSTEM`, menyetel `en` atau `id` saat pengguna memilih manual).
     - Perluas kamus `AppStrings.kt` secara komprehensif untuk menampung seluruh teks notifikasi, chatbot, laporan, debt planner, form, dan validasi toast.
     - Perbarui model data (`AssetCategory` di `AssetItem.kt`, `FinancialPersonality` di `GamificationModels.kt`, badge di `GamificationManager.kt`) agar menyediakan teks dwibahasa terintegrasi.
     - Standarisasi seluruh `SimpleDateFormat` dan `NumberFormat` ke `LocaleHelper.getActiveLocale()`.
  2. **Lapis 2 (Headless Logic & CLI Unit Tests)**:
     - Tulis unit test komprehensif di `LocalizationBilingualTest.kt` yang menguji: deteksi sistem Locale US vs ID, konsistensi kamus `AppStrings.t()`, penerjemahan kategori, formatter tanggal dinamis, dan kalkulator struk/WhatsApp summary.
     - Eksekusi `./gradlew testDebugUnitTest` di terminal hingga 100% PASS (995+ tests).
  3. **Lapis 3 (UI-Last / Visual & ViewModel Integration)**:
     - Terapkan dwibahasa pada Chatbot NLP (`LocalFinancialIntentParser.kt`, `FinancialQueryEngine.kt`, `WalletDraftManager.kt`, `TransactionDraftManager.kt`).
     - Terapkan dwibahasa pada Notifikasi & Background Worker (`NotificationDataHelper.kt`, `RecurringTransactionWorker.kt`).
     - Terapkan dwibahasa pada Ekspor & Struk (`ReportExporter.kt`, `ReceiptFormatter.kt`, `SplitBillCalculator.kt`, `GoogleDriveService.kt`).
     - Terapkan dwibahasa pada Validasi ViewModel (`AuthViewModel.kt`, `FinanceViewModel.kt`, `FeedbackViewModel.kt`).
     - Terapkan dwibahasa pada seluruh Layar, Dialog, Modal, dan Bottom Sheet (37+ file UI).

## Metadata
- **Complexity**: XL (Cross-cutting architectural & comprehensive UI audit across 89 files)
- **Source PRD**: N/A (Defect Remediation & Whole-System Localization Audit)
- **PRD Phase**: Phase 1 (Audit & Comprehensive Remediation)
- **Estimated Files**: ~45 files directly updated, ~1000 lines touched

---

## Analisis Risiko & Tabrakan Fitur (Bahasa Awam Non-Coder)

### 1. Dampak ke Fitur Lain
- **Perubahan Bahasa OS (LocaleManager Android 13+)**:
  *Fitur yang disentuh*: Cara aplikasi membaca bahasa bawaan HP Android 13/14 (ColorOS/Realme UI).
  *Dampak*: Aman. Pembaruan ini hanya memberitahu Android OS untuk tidak memaksakan Bahasa Indonesia jika HP disetel Bahasa Inggris. Pengguna yang memilih manual "Bahasa Indonesia" di dalam aplikasi tetap akan tersimpan dengan rapi.
- **Kategori & Basis Data (Room SQLite v11)**:
  *Fitur yang disentuh*: Nama kategori transaksi dan aset ("Makanan & Minuman", "Gaji", "Elektronik / Gadget").
  *Dampak*: Aman 100%. Data di dalam database tetap disimpan dalam bentuk aslinya (tidak mengubah skema Room SQLite v11), tetapi saat disajikan ke layar atau laporan pengguna, sistem menggunakan penerjemah `AppStrings.translateCategory(...)` sehingga tampil dalam bahasa Inggris yang tepat.
- **Kalkulasi Keuangan, Saldo, dan Arus Kas**:
  *Fitur yang disentuh*: Format angka dan mata uang.
  *Dampak*: Nilai mata uang utama tetap Rupiah (Rp) atau mata uang pilihan pengguna, tanpa mengubah kalkulasi matematika satu sen pun.

### 2. Potensi Risiko
- **Analogi Kehidupan Nyata**:
  Ibarat rumah besar yang memiliki papan penunjuk jalan di setiap ruangan, jika kita hanya mengganti papan petunjuk di ruang tamu namun membiarkan papan petunjuk di dapur, gudang, dan kamar tidur tetap berbahasa lama, penghuni rumah yang berbahasa asing akan bingung.
  Risiko teknis yang dicegah:
  1. *Double Recreate Crash*: Pada HP Realme GT Neo 3T, memanggil restart activity saat bahasa diganti bisa memicu crash jika tidak ditangani dengan Compose StateFlow re-composition. Solusi: Menggunakan re-composition murni pada Compose dan `LocaleManager` aman pada API 33+.
  2. *Fake Translation*: Teks yang lupa diterjemahkan di dalam Toast atau dialog konfirmasi darurat (Duress PIN). Solusi: Seluruh error string di ViewModel dipetakan ke kamus `AppStrings`.

### 3. Rekomendasi Aman
Lakukan pengerjaan secara bertahap mengikuti urutan 3-Lapis yang ketat:
Pondasi data dan engine bahasa diperbaiki terlebih dahulu (Lapis 1), diverifikasi dengan tes logika di terminal (Lapis 2), baru kemudian memperbarui seluruh antarmuka visual (Lapis 3).

---

## UX Design

### Before
```
┌────────────────────────────────────────────────────────┐
│ HP Set to English (US)                                │
│ App opens in: Bahasa Indonesia (Per-app lock stuck)    │
│                                                        │
│ Dashboard:                                             │
│ [🔴 Anggaran bulanan telah melampaui 85%!]             │
│ Recent: "Makanan & Minuman"                            │
│                                                        │
│ Debt Planner Dialog:                                   │
│ "Tambah Kontrak Baru", "Sisa Pokok", "Tenor"           │
│                                                        │
│ Notification Status Bar:                               │
│ "Peringatan Saldo Rendah: Saldo dompet menipis!"       │
│                                                        │
│ Exported PDF:                                          │
│ Columns: [TANGGAL | JUDUL | KATEGORI | NOMINAL]        │
└────────────────────────────────────────────────────────┘
```

### After
```
┌────────────────────────────────────────────────────────┐
│ HP Set to English (US)                                │
│ App opens in: English (US) Automatically               │
│                                                        │
│ Dashboard:                                             │
│ [🔴 Combined monthly budget has exceeded 85%!]         │
│ Recent: "Food & Drinks"                                │
│                                                        │
│ Debt Planner Dialog:                                   │
│ "Add New Contract", "Remaining Principal", "Tenure"    │
│                                                        │
│ Notification Status Bar:                               │
│ "Low Balance Alert: Your wallet balance is running low!│
│                                                        │
│ Exported PDF:                                          │
│ Columns: [DATE | TITLE | CATEGORY | AMOUNT]            │
└────────────────────────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Fresh Launch on Non-ID Phone | Stuck in Indonesian | Opens cleanly in English | LocaleManager reset to empty on SYSTEM mode |
| Switch Language in Settings | Partial change; dozens of screens stay Indonesian | 100% of screens, dialogs, toasts, chatbot, and notifications switch instantly | Recompose triggered via StateFlow + AppStrings |
| System Notifications | 47 alerts hardcoded in Indonesian | Dynamic bilingual notification based on active app language | Localized in NotificationDataHelper & Workers |
| PDF/CSV Export & Struk | Indonesian columns & titles | Dynamic English/Indonesian columns & titles | Localized in ReportExporter & ReceiptFormatter |
| Chatbot Assistance | Half Indonesian / English prompts | Fully coherent English or Indonesian conversation | Localized in intent parser & query engine |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 (critical) | `app/src/main/java/com/example/util/LocaleHelper.kt` | 1-209 | Core Android 13+ LocaleManager & language state management |
| P0 (critical) | `app/src/main/java/com/example/util/AppStrings.kt` | 1-250 | Central translation dictionary |
| P1 (important) | `app/src/main/java/com/example/util/NotificationDataHelper.kt` | 1-250 | 47 notification templates needing bilingual support |
| P1 (important) | `app/src/main/java/com/example/ui/components/DebtPlannerModalDialog.kt` | 1-500 | 72 untranslated strings in debt planner modal |
| P1 (important) | `app/src/main/java/com/example/data/chatbot/parser/LocalFinancialIntentParser.kt` | 1-300 | 103 untranslated strings in Chatbot NLP |
| P2 (reference) | `app/src/main/java/com/example/util/ReportExporter.kt` | 1-200 | Table headers and metadata in PDF/CSV exports |
| P2 (reference) | `app/src/main/java/com/example/util/ReceiptFormatter.kt` | 1-150 | Digital receipt headers and formatting |

---

## Patterns to Mirror

### CENTRAL_TRANSLATION_PATTERN
// SOURCE: `app/src/main/java/com/example/util/AppStrings.kt:18`
```kotlin
fun t(id: String, en: String): String {
    return if (LocaleHelper.isEnglish()) en else id
}
```

### DYNAMIC_LOCALE_DATE_FORMATTER_PATTERN
// SOURCE: `app/src/main/java/com/example/util/Formatters.kt:216`
```kotlin
val sdf = SimpleDateFormat(pattern, LocaleHelper.getActiveLocale())
```

### CATEGORY_TRANSLATION_PATTERN
// SOURCE: `app/src/main/java/com/example/util/AppStrings.kt:173`
```kotlin
fun translateCategory(cat: String): String {
    if (!LocaleHelper.isEnglish()) return cat
    return when (cat.trim().lowercase()) {
        "makanan & minuman", "makanan dan minuman", "food & drinks" -> "Food & Drinks"
        "gaji & penghasilan", "gaji", "salary" -> "Salary & Income"
        // ... mapped thoroughly
        else -> cat
    }
}
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `app/src/main/java/com/example/util/LocaleHelper.kt` | UPDATE | Fix Android 13/14 LocaleManager clearing & system locale resolution |
| `app/src/main/java/com/example/util/AppStrings.kt` | UPDATE | Add all missing dictionary entries across domains |
| `app/src/main/java/com/example/data/model/AssetItem.kt` | UPDATE | Add bilingual display names to `AssetCategory` |
| `app/src/main/java/com/example/data/model/GamificationModels.kt` | UPDATE | Add bilingual fields to `FinancialPersonality` |
| `app/src/main/java/com/example/util/GamificationManager.kt` | UPDATE | Localize badge titles and descriptions |
| `app/src/main/java/com/example/util/NotificationDataHelper.kt` | UPDATE | Localize all 47 notification templates |
| `app/src/main/java/com/example/util/RecurringTransactionWorker.kt` | UPDATE | Localize notification channel name & recurring alert |
| `app/src/main/java/com/example/util/ReportExporter.kt` | UPDATE | Localize PDF/CSV report headers, titles, and dates |
| `app/src/main/java/com/example/util/ReceiptFormatter.kt` | UPDATE | Localize digital ASCII receipt headers and labels |
| `app/src/main/java/com/example/util/SplitBillCalculator.kt` | UPDATE | Localize WhatsApp share summary text |
| `app/src/main/java/com/example/util/GoogleDriveService.kt` | UPDATE | Localize backup status strings & date formatters |
| `app/src/main/java/com/example/util/DebtPriorityEngine.kt` | UPDATE | Localize debt advice, strategy titles, and monthly anchor day strings |
| `app/src/main/java/com/example/util/DebtFreedomPlanner.kt` | UPDATE | Localize payoff strategies and freedom date strings |
| `app/src/main/java/com/example/ui/components/DebtPlannerModalDialog.kt` | UPDATE | Localize all 72 debt planning strings and forms |
| `app/src/main/java/com/example/ui/components/PayLaterBillReviewDialog.kt` | UPDATE | Localize 13 bill review strings & number formatter |
| `app/src/main/java/com/example/ui/components/AssetTrackerModalDialog.kt` | UPDATE | Localize 16 asset tracker strings and form labels |
| `app/src/main/java/com/example/ui/components/AddTransactionSheet.kt` | UPDATE | Localize 15 transaction creation strings and category chips |
| `app/src/main/java/com/example/ui/components/AuthModalDialog.kt` | UPDATE | Localize authentication dialog labels and titles |
| `app/src/main/java/com/example/ui/components/ChangePasswordDialog.kt` | UPDATE | Localize password change modal labels |
| `app/src/main/java/com/example/ui/components/DeleteAccountDialog.kt` | UPDATE | Localize account deletion warnings and actions |
| `app/src/main/java/com/example/ui/components/DuressPinModalDialog.kt` | UPDATE | Localize panic pin & biometric settings strings |
| `app/src/main/java/com/example/ui/components/ProminentDisclosureDialog.kt` | UPDATE | Localize permission transparency disclosures |
| `app/src/main/java/com/example/ui/components/InitialMasterSetupDialog.kt` | UPDATE | Localize master setup wizard strings |
| `app/src/main/java/com/example/ui/components/SmartMasterPaywallDialog.kt` | UPDATE | Localize paywall features and upgrade buttons |
| `app/src/main/java/com/example/ui/components/GamificationHubDialog.kt` | UPDATE | Localize gamification ranks and badge popups |
| `app/src/main/java/com/example/ui/components/ExportFormatManagementDialog.kt` | UPDATE | Localize format configuration strings |
| `app/src/main/java/com/example/ui/components/PublishUpdateBottomSheet.kt` | UPDATE | Localize app update publishing strings |
| `app/src/main/java/com/example/ui/components/ReceiptGeneratorModalDialog.kt` | UPDATE | Localize receipt generation strings |
| `app/src/main/java/com/example/ui/components/SummaryCards.kt` | UPDATE | Localize card metrics and labels |
| `app/src/main/java/com/example/ui/components/WalletComponents.kt` | UPDATE | Localize wallet card labels and archive buttons |
| `app/src/main/java/com/example/ui/components/MasterDevPanelSheet.kt` | UPDATE | Localize developer panel strings & fix hardcoded date formatter |
| `app/src/main/java/com/example/ui/screens/DashboardScreen.kt` | UPDATE | Localize overbudget alert texts and action buttons |
| `app/src/main/java/com/example/ui/screens/BudgetScreen.kt` | UPDATE | Localize dynamic overbudget alerts (70%, 85%, 100%) |
| `app/src/main/java/com/example/ui/screens/TransactionsScreen.kt` | UPDATE | Localize category filter chips and search placeholder |
| `app/src/main/java/com/example/ui/screens/SplitBillScreen.kt` | UPDATE | Localize participant placeholder and action buttons |
| `app/src/main/java/com/example/ui/screens/AiChatScreen.kt` | UPDATE | Localize prompt chips, placeholders, and action feedback |
| `app/src/main/java/com/example/ui/screens/ReceiptScanScreen.kt` | UPDATE | Localize OCR scan error messages and labels |
| `app/src/main/java/com/example/ui/screens/SettingsScreen.kt` | UPDATE | Localize guest mode status cards and dialogs |
| `app/src/main/java/com/example/ui/screens/FeedbackScreen.kt` | UPDATE | Localize feedback submission form and status |
| `app/src/main/java/com/example/ui/screens/DevFeedbackViewerScreen.kt` | UPDATE | Localize feedback viewer labels and fix date formatter |
| `app/src/main/java/com/example/ui/FinSmartApp.kt` | UPDATE | Localize navigation items and FAB descriptions |
| `app/src/main/java/com/example/data/chatbot/parser/LocalFinancialIntentParser.kt` | UPDATE | Localize 103 bot guidance prompts and messages |
| `app/src/main/java/com/example/data/chatbot/parser/FinancialQueryEngine.kt` | UPDATE | Localize financial query answers |
| `app/src/main/java/com/example/data/chatbot/parser/WalletDraftManager.kt` | UPDATE | Localize wallet draft interactive prompts |
| `app/src/main/java/com/example/data/chatbot/parser/TransactionDraftManager.kt` | UPDATE | Localize transaction draft interactive prompts |
| `app/src/main/java/com/example/ui/viewmodel/AuthViewModel.kt` | UPDATE | Localize 85 error validation strings and toasts |
| `app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt` | UPDATE | Localize 36 validation callbacks and toasts |
| `app/src/main/java/com/example/ui/viewmodel/FeedbackViewModel.kt` | UPDATE | Localize feedback validation errors |
| `app/src/test/java/com/example/LocalizationBilingualTest.kt` | CREATE | Comprehensive unit tests for bilingual synchronization |

## NOT Building
- Tidak memodifikasi skema database SQLite Room v11 (mencegah risiko migrasi data).
- Tidak mengubah mata uang dasar (Rupiah Rp tetap berfungsi sebagai basis nominal keuangan pengguna).
- Tidak menggunakan library penerjemah eksternal berbayar (100% kamus in-memory open-source bebas biaya).

---

## Step-by-Step Tasks

### Task 1: Pondasi Level OS & LocaleManager (Lapis 1 - Data-First)
- **ACTION**: Perbaiki `app/src/main/java/com/example/util/LocaleHelper.kt`.
- **IMPLEMENT**:
  1. Pada Android 13+ (API 33+), saat `AppLanguage.SYSTEM` aktif, panggil `localeManager.applicationLocales = LocaleList.getEmptyLocaleList()` agar OS Realme/ColorOS tidak memaksa per-app locale lama.
  2. Saat `AppLanguage.INDONESIAN` dipilih pengguna, set `localeManager.applicationLocales = LocaleList(Locale("id", "ID"))`.
  3. Saat `AppLanguage.ENGLISH` dipilih pengguna, set `localeManager.applicationLocales = LocaleList(Locale.US)`.
  4. Dalam `isEnglish()`, pastikan membaca `getSystemLocale()` dengan fallback bersih ke `Locale.getDefault()` dan `Resources.getSystem()`.
- **MIRROR**: Pola LocaleManager standar Android API 33+.
- **GOTCHA**: Jangan memanggil `activity.recreate()` manual di dalam event loop Compose; biarkan StateFlow `currentLanguage` memicu re-komposisi instan tanpa restart activity.
- **VALIDATE**: Unit test `LocaleHelperTest` memverifikasi `isEnglish()` bernilai benar saat disimulasikan `Locale.US` vs `Locale("id", "ID")`.

### Task 2: Ekspansi Kamus Global & Model Data (Lapis 1 - Data-First)
- **ACTION**: Perbarui `AppStrings.kt`, `AssetItem.kt`, dan `GamificationModels.kt`.
- **IMPLEMENT**:
  1. Tambahkan seluruh kamus terjemahan untuk notifikasi (47 template), chatbot responses, report headers, form placeholders, debt terms, dan toast validation ke `AppStrings.kt`.
  2. Perluas `translateCategory(...)` untuk memetakan seluruh kategori pengeluaran dan pemasukan.
  3. Tambahkan `enDisplayName` pada `AssetCategory` di `AssetItem.kt`.
  4. Tambahkan `localizedTitle` dan `localizedDescription` pada `FinancialPersonality` di `GamificationModels.kt`.
  5. Perbarui `GamificationManager.kt` agar badge title & description menggunakan `AppStrings.t(...)`.
- **VALIDATE**: Unit test memverifikasi seluruh fungsi kamus menghasilkan terjemahan valid tanpa fallback kosong.

### Task 3: Headless Logic & CLI Unit Tests (Lapis 2 - Headless Test)
- **ACTION**: Buat file `app/src/test/java/com/example/LocalizationBilingualTest.kt`.
- **IMPLEMENT**:
  - Test 1: Verifikasi `LocaleHelper.isEnglish()` pada mode SYSTEM dengan variasi Locale sistem (US, UK, ID).
  - Test 2: Verifikasi `AppStrings.t()` mengembalikan teks yang tepat sesuai status `isEnglish()`.
  - Test 3: Verifikasi `AppStrings.translateCategory()` memetakan seluruh kategori default ke Bahasa Inggris saat English aktif.
  - Test 4: Verifikasi `ReportExporter` menghasilkan header kolom bahasa Inggris saat English aktif.
  - Test 5: Verifikasi `ReceiptFormatter` menghasilkan struk bahasa Inggris saat English aktif.
  - Test 6: Verifikasi `SplitBillCalculator` menghasilkan ringkasan WhatsApp bahasa Inggris saat English aktif.
  - Test 7: Verifikasi seluruh 47 notifikasi di `NotificationDataHelper` memiliki teks bahasa Inggris yang valid.
- **VALIDATE**: Jalankan `./gradlew testDebugUnitTest` di terminal hingga 100% PASS.

### Task 4: Standardisasi Seluruh Date & Number Formatters (Lapis 2 - Headless Logic)
- **ACTION**: Perbarui 7 file yang memiliki hardcoded `Locale("id", "ID")`.
- **IMPLEMENT**:
  1. `MasterDevPanelSheet.kt:403`: Ubah ke `SimpleDateFormat("dd MMMM yyyy, HH:mm", LocaleHelper.getActiveLocale())`.
  2. `DevFeedbackViewerScreen.kt:617`: Ubah ke `SimpleDateFormat("dd MMM yyyy • HH:mm", LocaleHelper.getActiveLocale())`.
  3. `EntitlementManager.kt:45`: Ubah ke `SimpleDateFormat("dd MMM yyyy, HH:mm", LocaleHelper.getActiveLocale())`.
  4. `GoogleDriveService.kt:92, 118`: Ubah ke `SimpleDateFormat("dd MMM yyyy, HH:mm z", LocaleHelper.getActiveLocale())`.
  5. `InstallmentDateHelper.kt:307`: Ubah ke `SimpleDateFormat(pattern, LocaleHelper.getActiveLocale())`.
  6. `PayLaterBillReviewDialog.kt:116, 120`: Ubah ke `NumberFormat.getNumberInstance(LocaleHelper.getActiveLocale())`.
  7. `ReceiptFormatter.kt:15`: Gunakan `LocaleHelper.getActiveLocale()`.
- **VALIDATE**: `./gradlew testDebugUnitTest` tetap 100% green.

### Task 5: Penuntasan Chatbot NLP, Notifikasi & Laporan (Lapis 3 - Logic & Service Hook)
- **ACTION**: Perbarui service dan parser berikut:
  - `NotificationDataHelper.kt` & `RecurringTransactionWorker.kt`
  - `ReportExporter.kt`, `ReceiptFormatter.kt`, `SplitBillCalculator.kt`
  - `LocalFinancialIntentParser.kt`, `FinancialQueryEngine.kt`, `WalletDraftManager.kt`, `TransactionDraftManager.kt`
  - `DebtPriorityEngine.kt`, `DebtFreedomPlanner.kt`
- **IMPLEMENT**:
  - Ganti seluruh string literal statis Bahasa Indonesia dengan pemanggilan `AppStrings.t(...)`.
- **VALIDATE**: CLI unit test memverifikasi string yang dihasilkan berubah sesuai toggle bahasa.

### Task 6: Penuntasan Validasi ViewModel & Toasts (Lapis 3 - ViewModel Hook)
- **ACTION**: Perbarui `AuthViewModel.kt`, `FinanceViewModel.kt`, dan `FeedbackViewModel.kt`.
- **IMPLEMENT**:
  - Bungkus seluruh error messages, snackbar, dan toast dengan `AppStrings.t(...)` (atau helper dwibahasa).
- **VALIDATE**: Kompilasi bersih dan unit test ViewModel 100% PASS.

### Task 7: Penuntasan Seluruh Layar & Dialog UI (Lapis 3 - UI-Last)
- **ACTION**: Perbarui 37+ file Compose (layar, dialog, modal, bottom sheet) yang teridentifikasi dalam audit:
  - 11 Layar: `DashboardScreen.kt`, `BudgetScreen.kt`, `TransactionsScreen.kt`, `SplitBillScreen.kt`, `AiChatScreen.kt`, `ReceiptScanScreen.kt`, `SettingsScreen.kt`, `FeedbackScreen.kt`, `DevFeedbackViewerScreen.kt`, `FinSmartApp.kt`.
  - 16 Dialog/Modal: `DebtPlannerModalDialog.kt`, `PayLaterBillReviewDialog.kt`, `AssetTrackerModalDialog.kt`, `AddTransactionSheet.kt`, `AuthModalDialog.kt`, `ChangePasswordDialog.kt`, `DeleteAccountDialog.kt`, `DuressPinModalDialog.kt`, `ProminentDisclosureDialog.kt`, `InitialMasterSetupDialog.kt`, `SmartMasterPaywallDialog.kt`, `GamificationHubDialog.kt`, `ExportFormatManagementDialog.kt`, `PublishUpdateBottomSheet.kt`, `ReceiptGeneratorModalDialog.kt`, `SummaryCards.kt`, `WalletComponents.kt`.
- **IMPLEMENT**:
  - Sambungkan seluruh teks yang masih berkode keras Bahasa Indonesia ke `AppStrings.t("Teks ID", "English Text")` atau `AppStrings.translateCategory(...)`.
- **VALIDATE**: Semua string antarmuka terbukti dinamis mengikuti StateFlow bahasa.

### Task 8: Verifikasi Menyeluruh & Build Release APK (Anti-Fake-Pass)
- **ACTION**:
  1. Jalankan `python auto_sync.py audit` untuk memastikan 0 TODO, 0 Dummy, 0 Mock data.
  2. Jalankan `./gradlew testDebugUnitTest` untuk memastikan seluruh 995+ unit test PASS.
  3. Jalankan `./gradlew assembleDebug` untuk mengompilasi APK terbaru.
  4. Perbarui `CURRENT_CHECKPOINT.md` dan `END_TO_END_PROOF.md`.
  5. Commit dan simpan via `python auto_sync.py save "Tuntas Lokalisasi Dwibahasa End-to-End Seluruh Sistem DompetNalar"`.

---

## Testing Strategy

### Unit Tests
| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| Mode SYSTEM pada HP US | Locale.US | `isEnglish() == true`, Teks UI tampil Bahasa Inggris | Yes (HP non-ID) |
| Mode SYSTEM pada HP ID | Locale("id", "ID") | `isEnglish() == false`, Teks UI tampil Bahasa Indonesia | Standard |
| Mode Manual English | AppLanguage.ENGLISH | `isEnglish() == true` terlepas dari Locale HP | Yes (Override) |
| Mode Manual Indonesia | AppLanguage.INDONESIAN | `isEnglish() == false` terlepas dari Locale HP | Yes (Override) |
| Kategori "Makanan & Minuman" | Locale.US | "Food & Drinks" | Yes (DB Seed Translate) |
| Notifikasi Saldo Rendah | Locale.US | "Low Balance Alert" | Yes (Background notification) |
| Struk PDF Kolom | Locale.US | DATE, TITLE, CATEGORY, AMOUNT | Yes (Exporter) |

---

## Validation Commands

### Static Analysis & Audit
```bash
python auto_sync.py audit
```
EXPECT: Zero TODO, zero dummy, zero mock, clean audit.

### Unit Tests
```bash
./gradlew testDebugUnitTest
```
EXPECT: All 995+ tests PASS.

### Build Verification
```bash
./gradlew assembleDebug
```
EXPECT: BUILD SUCCESSFUL in < 1 minute, APK created at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Acceptance Criteria
- [ ] HP berbahasa non-Indonesia (English) membuka DompetNalar otomatis dalam Bahasa Inggris 100%.
- [ ] Penggantian bahasa di Pengaturan langsung mengubah seluruh teks tanpa perlu restart aplikasi atau mengalami crash.
- [ ] Tidak ada teks Bahasa Indonesia yang tertinggal saat Bahasa Inggris dipilih di seluruh 89 file yang diaudit.
- [ ] Laporan PDF, CSV, struk digital, WhatsApp split bill, notifikasi sistem, dan chatbot merespons dalam bahasa yang dipilih pengguna.
- [ ] Semua unit test (995+) lulus 100% di terminal CLI.
- [ ] APK debug terkompilasi sukses dan siap diuji fisik ulang di HP Realme GT Neo 3T.
