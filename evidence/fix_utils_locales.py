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

# 1. EntitlementManager.kt
update_file(
    r'app\src\main\java\com\example\util\EntitlementManager.kt',
    [
        ('val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))', 'val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", LocaleHelper.getActiveLocale())'),
        ('effectiveRole == UserRole.MASTER_DEV -> "Permanen (Master Developer)"', 'effectiveRole == UserRole.MASTER_DEV -> (if (LocaleHelper.isEnglish()) "Permanent (Master Developer)" else "Permanen (Master Developer)")'),
        ('expiresAt == null -> "Tidak Terbatas"', 'expiresAt == null -> (if (LocaleHelper.isEnglish()) "Unlimited" else "Tidak Terbatas")'),
    ]
)

# 2. ReportExporter.kt
update_file(
    r'app\src\main\java\com\example\util\ReportExporter.kt',
    [
        ('val printDateStr = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("id", "ID")).format(Date())', 'val printDateStr = SimpleDateFormat("dd MMMM yyyy HH:mm", LocaleHelper.getActiveLocale()).format(Date())'),
        ('"Dokumen laporan resmi diekspor otomatis via DompetNalar pada $printDateStr."', '(if (LocaleHelper.isEnglish()) "Official report document automatically exported via DompetNalar on $printDateStr." else "Dokumen laporan resmi diekspor otomatis via DompetNalar pada $printDateStr.")'),
    ]
)

# 3. DebtFreedomPlanner.kt
update_file(
    r'app\src\main\java\com\example\util\DebtFreedomPlanner.kt',
    [
        ('val sdf = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))', 'val sdf = SimpleDateFormat("MMMM yyyy", LocaleHelper.getActiveLocale())'),
    ]
)
