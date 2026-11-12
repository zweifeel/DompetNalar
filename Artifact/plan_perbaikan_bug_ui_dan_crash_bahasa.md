# Implementation Plan: Perbaikan Bug UI (Mutasi & Debt Planner) & Penuntasan Bug Crash Pemilihan Bahasa End-to-End

## [Goal Description]
Menuntaskan investigasi akar masalah (*root cause analysis*) dan merancang perbaikan menyeluruh (end-to-end) untuk tiga masalah yang dilaporkan oleh Owner:
1. **Analisis Screenshot 1 (`TransactionsScreen.kt` - Riwayat Transaksi / Mutasi):**
   - **Teks Ringkasan Terpotong:** Judul kartu arus kas menampilkan `"Cashflow Recapitulation (Mont...  Net: +Rp 0"` dengan teks terpotong elipsis jelek karena string terlalu panjang dan dipaksa satu baris (`maxLines = 1`) di samping badge Net.
   - **Teks Campur (Dwibahasa):** Chip filter kategori masih memunculkan Bahasa Indonesia `"Belanja &"` (terpotong di tepi layar) saat bahasa aplikasi adalah Bahasa Inggris. Nilai mentah dari database Room (`"Belanja & Hobi"`) belum dilewatkan ke kamus terjemahan dinamis `AppStrings.translateCategory()`. Begitu pula pada subtitle item transaksi di `TransactionItemRow.kt`.
2. **Analisis Screenshot 2 (`DebtPlannerModalDialog.kt` - Tambah Pinjaman / Utang):**
   - **Tombol Preset Cacat:** Deretan tombol pintasan tanggal di bawah *Due Date* memunculkan teks cacat: `[ Day ] [ Day ] [ Day ] [ Day ] [ End ]` (angka 5, 10, 15, 20 dan kata "of Month" hilang). Penyebabnya adalah `TextButton` Material Design 3 memiliki bantalan internal (*contentPadding*) bawaan sebesar 12dp kiri dan 12dp kanan (total 24dp). Pada baris 5 kolom berbobot setara `weight(1f)`, sisa lebar efektif hanya ~35dp sehingga spasi dan angka dipaksa pindah ke baris kedua yang tersembunyi karena pembatas `maxLines = 1`.
   - **Nilai Awal Jatuh Tempo Berbahasa Indonesia:** Nilai isian awal pada input *Due Date* masih tertulis `"Tgl 15 tiap bulan"` di mode Bahasa Inggris karena inisialisasi state tidak memeriksa `LocaleHelper.isEnglish()`.
3. **Penyelidikan End-to-End Bug Crash Pergantian Bahasa (Android 13/14, Realme GT Neo 3T):**
   - **Akar Masalah Nyata:** Pada commit `5de4872`, pemanggilan mutasi OS `localeManager.applicationLocales = targetLocales` tidak sengaja dimasukkan kembali ke dalam `LocaleHelper.initialize()` dan `LocaleHelper.setLanguage()`.
   - Pada HP Realme GT Neo 3T (Realme UI / ColorOS berbasis Android 13/14), memutasi `applicationLocales` membuat sistem Android memicu *Configuration Change* level OS yang memaksa Activity untuk dihancurkan dan dibuat ulang (*restart/recreate*).
   - Karena aplikasi menggunakan `<activity-alias>` (`MainActivityDefault` untuk ikon dinamis) dengan target `.MainActivity` yang berstatus `android:exported="false"`, proses *task recreation* ColorOS mengalami kegagalan resolusi komponen atau memicu *BadTokenException* pada dialog Jetpack Compose yang sedang menutup.
   - Terlebih lagi, `MainActivity.attachBaseContext` memanggil `LocaleHelper.initialize(newBase)` sebelum context Activity terikat sempurna, sehingga mutasi locale di sini sangat berisiko fatal.
   - **Solusi Permanen:** DompetNalar dibangun 100% dengan **Jetpack Compose** reaktif. Menghapus total mutasi `localeManager.applicationLocales` dan mengandalkan `LocaleHelper.currentLanguage` (`StateFlow`), `Locale.setDefault()`, dan `Resources.updateConfiguration()` non-destruktif membuat bahasa berganti seketika secara in-memory, sangat mulus (*zero-recreate*), tanpa kedipan layar, dan 100% bebas dari risiko crash. Ditambah penambahan guard `android:configChanges="locale|layoutDirection"` pada `AndroidManifest.xml` sebagai pelindung ganda.

---

## 🖼️ Bukti Tangkapan Layar Masalah yang Dianalisis

| Screenshot 1: Riwayat Transaksi (Mutasi) | Screenshot 2: Tambah Pinjaman (Debt Planner) |
| :---: | :---: |
| ![Mutasi Screen](file:///C:/Users/User/.gemini/antigravity/brain/5b102e2a-b058-4701-886c-4993cc492724/bug_screenshot_transactions.jpg) | ![Debt Planner Modal](file:///C:/Users/User/.gemini/antigravity/brain/5b102e2a-b058-4701-886c-4993cc492724/bug_screenshot_debt_planner.jpg) |
| *Masalah: Judul terpotong `(Mont...` & filter chip berbahasa Indonesia `Belanja &`* | *Masalah: Preset tombol terpotong `Day Day Day Day End` & default input `Tgl 15 tiap bulan`* |

---

## User Review Required

> [!IMPORTANT]
> ### 🛡️ Analisis Risiko & Tabrakan Fitur (Protokol 1 GEMINI.md)
> - **Dampak ke Fitur Lain:**
>   * Berkas yang akan disentuh:
>     1. `app/src/main/java/com/example/util/LocaleHelper.kt` (Pembersihan mutasi LocaleManager berbahaya).
>     2. `app/src/main/java/com/example/MainActivity.kt` (Pembersihan attachBaseContext dari inisialisasi dini).
>     3. `app/src/main/AndroidManifest.xml` (Penambahan `android:configChanges="locale|layoutDirection"` pada `MainActivity`).
>     4. `app/src/main/java/com/example/ui/screens/TransactionsScreen.kt` (Perapian judul rekapitulasi & lokalisasi chip kategori).
>     5. `app/src/main/java/com/example/ui/components/TransactionItemRow.kt` (Lokalisasi subtitle kategori & fallback dompet).
>     6. `app/src/main/java/com/example/ui/components/DebtPlannerModalDialog.kt` (Perbaikan padding tombol preset tanggal & default input due date).
>   * Fitur stabil yang terhubung: Room Database v11, kalkulasi saldo dan mutasi, modul PIN, serta sistem lisensi sama sekali **TIDAK TERGANGGU** karena tidak ada skema database atau rumus keuangan yang diubah.
> - **Potensi Risiko:**
>   * **Analogi Kehidupan Nyata:** *"Seperti mengganti papan nama petunjuk arah dan merapikan ukuran kursi di lobi agar pengunjung tidak tersandung. Jalur kereta api, brankas penyimpanan uang, dan pondasi bangunan sama sekali tidak disentuh."*
> - **Rekomendasi Aman:**
>   * Gunakan mekanisme reaktif murni Jetpack Compose (`StateFlow`) tanpa me-restart Activity untuk pergantian bahasa instan yang stabil di seluruh tipe HP Android (termasuk ColorOS / Realme UI / HyperOS).
>   * Gunakan helper `AppStrings.translateCategory()` yang sudah teruji aman agar kategori tetap tersimpan rapi di Room Database tanpa merusak kompatibilitas data lama.
>   * Ganti `TextButton` pada preset tanggal dengan `Surface` klikable ber-padding mikro (`contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)`) atau layout adaptif sehingga teks `"Day 5"`, `"Day 10"`, `"Day 15"`, `"Day 20"`, dan `"End of Mo"` tampil utuh dan jelas.

---

## Open Questions

> [!NOTE]
> Tidak ada pertanyaan yang memblokir. Pendekatan perbaikan sepenuhnya memanfaatkan arsitektur reaktif Jetpack Compose yang sudah ada di dalam kode proyek tanpa menambah pustaka pihak ketiga berbayar (memenuhi mandat 100% Bebas Biaya / FOSS).

---

## Proposed Changes

Grouped by component layer:

### 1. Logika & Konfigurasi Sistem (Anti-Crash Bahasa)

#### [MODIFY] `app/src/main/java/com/example/util/LocaleHelper.kt`
- Hapus total mutasi `localeManager.applicationLocales = targetLocales` dari metode `initialize()` dan `setLanguage()`.
- Pertahankan pembaruan in-memory reaktif:
  1. `prefs?.edit()?.putString(KEY_LANGUAGE, language.code)?.commit()`
  2. `_currentLanguage.value = language`
  3. `Locale.setDefault(locale)`
  4. `applyLocaleInternal(context.applicationContext, language, isUserExplicitChange = true)` untuk konfigurasi `Resources` non-destruktif.
- Pembacaan sistem `getSystemLocale()` tetap aman membaca `lm?.systemLocales` secara pasif tanpa mengubah state sistem operasi.

```kotlin
// Perubahan pada setLanguage():
fun setLanguage(context: Context, language: AppLanguage) {
    appContext = context.applicationContext
    if (prefs == null) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    prefs?.edit()?.putString(KEY_LANGUAGE, language.code)?.commit()
    _currentLanguage.value = language

    // Terapkan locale ke Locale Java default dan Resources secara non-destruktif (Zero Activity Recreate)
    applyLocaleInternal(context.applicationContext, language, isUserExplicitChange = true)
}
```

#### [MODIFY] `app/src/main/java/com/example/MainActivity.kt`
- Bersihkan `attachBaseContext(newBase: Context)` agar tidak menjalankan `LocaleHelper.initialize(newBase)` di tahap awal yang rawan memicu inisialisasi context belum matang.
- Inisialisasi `LocaleHelper.initialize(this)` dijalankan secara aman dan lengkap pada siklus `onCreate()`.

```kotlin
override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(LocaleHelper.wrapContext(newBase))
}
```

#### [MODIFY] `app/src/main/AndroidManifest.xml`
- Tambahkan deklarasi atribut proteksi konfigurasi pada `MainActivity`:
```xml
<activity
    android:name=".MainActivity"
    android:exported="false"
    android:configChanges="locale|layoutDirection|orientation|screenSize"
    android:label="@string/app_name"
    android:theme="@style/Theme.MyApplication" />
```
- Hal ini mencegah Android OS mematikan dan me-recreate `MainActivity` secara sepihak saat terjadi perubahan locale di level perangkat.

---

### 2. Tampilan Riwayat Transaksi / Mutasi (Screenshot 1)

#### [MODIFY] `app/src/main/java/com/example/ui/screens/TransactionsScreen.kt`
1. **Perbaikan Judul Kartu Ringkasan (Bebas Ellipsis Truncation):**
   - Ubah string judul rekapitulasi dari format panjang yang memakan tempat menjadi format yang pas dan elegan:
     * Bahasa Indonesia: `AppStrings.t("Rekap Arus Kas (${selectedTimeframe.label})", "Cashflow Summary (${selectedTimeframe.label})")`
   - Berikan kelenturan pada teks agar jika nominal "Net" sangat besar, teks judul dapat menyusut secara proporsional atau turun baris secara halus tanpa terpotong elipsis kaku.
2. **Lokalisasi Chip Filter Kategori:**
   - Ubah tampilan label chip kategori di baris 232:
```kotlin
// Dari:
label = { Text(cat.name, fontSize = 11.sp) }

// Menjadi:
label = { Text(com.example.util.AppStrings.translateCategory(cat.name), fontSize = 11.sp) }
```
   - Dengan ini, kategori sistem seperti "Belanja & Hobi" akan tampil sebagai "Shopping & Hobbies" saat mode Bahasa Inggris aktif.

#### [MODIFY] `app/src/main/java/com/example/ui/components/TransactionItemRow.kt`
- Bungkus `transaction.category` dengan `AppStrings.translateCategory(transaction.category)` pada baris subtitle (baris 118) dan pada `contentDescription` ikon.
- Ganti string fallback `"Dompet"` pada baris 116 dengan `AppStrings.t("Dompet", "Wallet")`.

---

### 3. Tampilan Perencana Hutang / Debt Planner (Screenshot 2)

#### [MODIFY] `app/src/main/java/com/example/ui/components/DebtPlannerModalDialog.kt`
1. **Perbaikan State Kolom Input Due Date:**
   - Perbaiki inisialisasi nilai awal di baris 228 agar responsif terhadap bahasa aktif:
```kotlin
// Dari:
var inputDebtDueDate by remember { mutableStateOf("Tgl 15 tiap bulan") }

// Menjadi:
var inputDebtDueDate by remember {
    mutableStateOf(if (com.example.util.LocaleHelper.isEnglish()) "Day 15 each month" else "Tgl 15 tiap bulan")
}
```
2. **Perbaikan Deretan Tombol Preset Tanggal (Bebas Teks Terpotong):**
   - Masalah pemotongan kata terjadi karena `TextButton` memiliki `contentPadding` bawaan Material Design 3 sebesar 12dp horizontal di kiri dan kanan (total 24dp).
   - Solusi: Gunakan `clickable` langsung pada `Surface` atau berikan `contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)` pada `TextButton`, dan sesuaikan label preset agar tidak terpotong:
```kotlin
val datePresets = listOf(
    ("Tgl 5" to "Day 5"),
    ("Tgl 10" to "Day 10"),
    ("Tgl 15" to "Day 15"),
    ("Tgl 20" to "Day 20"),
    ("Akhir Bln" to "End of Mo")
)
```
   - Tombol-tombol ini akan menampilkan teks utuh yang jelas: `Day 5`, `Day 10`, `Day 15`, `Day 20`, dan `End of Mo` (di Bahasa Inggris) atau `Tgl 5`, `Tgl 10`, `Tgl 15`, `Tgl 20`, dan `Akhir Bln` (di Bahasa Indonesia).
   - Pengguna yang menekan tombol tetap mengisi kalimat lengkap ke dalam kolom input: `"Day 5 each month"` / `"Tgl 5 tiap bulan"` atau `"End of Month"` / `"Akhir Bulan"`.

---

## Verification Plan

### Automated Tests
1. **Unit Test Regresi Penuh:**
   ```bash
   ./gradlew testDebugUnitTest
   ```
   - Memastikan seluruh tes (995+ unit test) lulus 100% tanpa ada kesalahan.
2. **Unit Test Khusus Lokalisasi & LocaleHelper:**
   ```bash
   ./gradlew testDebugUnitTest --tests "com.example.LocalizationBilingualTest"
   ./gradlew testDebugUnitTest --tests "com.example.util.GlobalLocalizationAndCurrencyTest"
   ./gradlew testDebugUnitTest --tests "com.example.FeedbackAndPolicyFirstRunRegressionTest"
   ```
   - Memverifikasi bahwa pergantian bahasa via `LocaleHelper.setLanguage()` dan inisialisasi via `LocaleHelper.initialize()` berjalan mulus dan persisten tanpa crash.
3. **Audit Bebas Kebohongan (Fake-Pass Audit):**
   ```bash
   python auto_sync.py audit
   ```
   - Memastikan kode 100% bebas dari `TODO`, `dummy`, `mock_data`, atau fungsi pura-pura.
4. **Kompilasi APK Debug Baru:**
   ```bash
   ./gradlew assembleDebug
   ```
   - Memastikan berkas APK biner terbaru berhasil diterbitkan untuk uji fisik langsung di HP Realme GT Neo 3T Owner.

### Manual Verification (Uji Fisik di HP Realme GT Neo 3T)
1. **Verifikasi Bebas Crash Bahasa:**
   - Buka menu Pengaturan (Settings) -> Pilih Bahasa Aplikasi.
   - Ganti dari Bahasa Indonesia ke English, lalu ganti kembali ke Bahasa Indonesia.
   - Amati: Aplikasi berganti bahasa seketika secara reaktif tanpa layar berkedip hitam dan tanpa ada penutupan paksa (*zero force close*).
2. **Verifikasi Layar Riwayat Transaksi (Mutasi):**
   - Atur bahasa ke English.
   - Buka tab "Transactions".
   - Periksa kartu ringkasan: teks harus menampilkan `"Cashflow Summary (Monthly)"` dan `"Net: +Rp 0"` dengan utuh tanpa pemotongan elipsis jelek `(Mont...`.
   - Periksa baris chip kategori: kategori "Belanja & Hobi" harus tampil sebagai `"Shopping & Hobbies"`.
3. **Verifikasi Dialog Perencana Utang (Debt Planner):**
   - Buka menu Tambah Pinjaman / Utang Baru.
   - Periksa kolom "Due Date": saat bahasa Inggris, nilai bawaan harus tertulis `"Day 15 each month"`.
   - Periksa deretan 5 tombol di bawahnya: tombol harus terbaca jelas `"Day 5"`, `"Day 10"`, `"Day 15"`, `"Day 20"`, dan `"End of Mo"` (angka dan teks terlihat utuh, tidak lagi hanya tulisan `Day`).
   - Klik salah satu tombol (misal `Day 10`): teks pada input Due Date harus otomatis terisi `"Day 10 each month"`.
