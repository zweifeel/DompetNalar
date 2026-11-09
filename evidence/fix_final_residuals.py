import os

def update_file(path, replacements):
    with open(path, 'r', encoding='utf-8') as f:
        c = f.read()
    init = c
    for old, new in replacements:
        if old in c:
            c = c.replace(old, new)
            print(f"Replaced in {os.path.basename(path)}")
        else:
            print(f"Target not found in {os.path.basename(path)}: {old[:40]}")
    if c != init:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(c)
        print(f"Saved {path}")

# 1. ExportFormatManagementDialog.kt
update_file(
    r'app\src\main\java\com\example\ui\components\ExportFormatManagementDialog.kt',
    [
        ('Text("Simpan", fontWeight = FontWeight.Bold)', 'Text(AppStrings.t("Simpan", "Save"), fontWeight = FontWeight.Bold)'),
    ]
)

# 2. PayLaterBillReviewDialog.kt
update_file(
    r'app\src\main\java\com\example\ui\components\PayLaterBillReviewDialog.kt',
    [
        ('OutlinedButton(\n                    onClick = onDismiss,\n                    shape = RoundedCornerShape(12.dp),\n                    modifier = Modifier.weight(1f)\n                ) {\n                    Text("Batal")\n                }', 'OutlinedButton(\n                    onClick = onDismiss,\n                    shape = RoundedCornerShape(12.dp),\n                    modifier = Modifier.weight(1f)\n                ) {\n                    Text(AppStrings.t("Batal", "Cancel"))\n                }'),
    ]
)

# 3. NotificationCenterBottomSheet.kt
update_file(
    r'app\src\main\java\com\example\ui\components\NotificationCenterBottomSheet.kt',
    [
        ('text = "Riwayat peringatan & pengingat otomatis",', 'text = AppStrings.t("Riwayat peringatan & pengingat otomatis", "History of automated alerts & reminders"),'),
    ]
)

# 4. ChangelogBottomSheet.kt
update_file(
    r'app\src\main\java\com\example\ui\components\ChangelogBottomSheet.kt',
    [
        ('title = "Otomatisasi Bahasa & Multi-Currency Selector",', 'title = AppStrings.t("Otomatisasi Bahasa & Multi-Currency Selector", "Language Automation & Multi-Currency Selector"),'),
        ('title = "Manajemen Hutang & Tanggal Jatuh Tempo",', 'title = AppStrings.t("Manajemen Hutang & Tanggal Jatuh Tempo", "Debt Management & Due Dates"),'),
        ('title = "Asset & Depreciating Asset Tracker Fleksibel",', 'title = AppStrings.t("Asset & Depreciating Asset Tracker Fleksibel", "Flexible Asset & Depreciation Tracker"),'),
        ('title = "Split Bill Ringkas & Cepat",', 'title = AppStrings.t("Split Bill Ringkas & Cepat", "Quick & Compact Split Bill"),'),
        ('title = "Visualisasi Pie / Donut Chart di Analisis",', 'title = AppStrings.t("Visualisasi Pie / Donut Chart di Analisis", "Pie / Donut Chart Visualization in Analysis"),'),
        ('title = "Pembersihan Pengaturan & Cloud Sync",', 'title = AppStrings.t("Pembersihan Pengaturan & Cloud Sync", "Settings Cleanup & Cloud Sync"),'),
    ]
)
