import os

files_to_check = [
    r'app\src\main\java\com\example\ui\components\AddTransactionSheet.kt',
    r'app\src\main\java\com\example\ui\components\AssetTrackerModalDialog.kt',
    r'app\src\main\java\com\example\ui\components\AuthModalDialog.kt',
    r'app\src\main\java\com\example\ui\components\ChangePasswordDialog.kt',
    r'app\src\main\java\com\example\ui\components\ChangelogBottomSheet.kt',
    r'app\src\main\java\com\example\ui\components\DebtPlannerModalDialog.kt',
    r'app\src\main\java\com\example\ui\components\DeleteAccountDialog.kt',
    r'app\src\main\java\com\example\ui\components\DuressPinModalDialog.kt',
    r'app\src\main\java\com\example\ui\components\ExportFormatManagementDialog.kt',
    r'app\src\main\java\com\example\ui\components\GamificationHubDialog.kt',
    r'app\src\main\java\com\example\ui\components\PayLaterBillReviewDialog.kt',
    r'app\src\main\java\com\example\ui\components\ReceiptGeneratorModalDialog.kt',
    r'app\src\main\java\com\example\ui\components\SleepyKoalaStartupScreen.kt',
    r'app\src\main\java\com\example\ui\components\SmartMasterPaywallDialog.kt',
    r'app\src\main\java\com\example\ui\components\UpdateAvailableBottomSheet.kt',
    r'app\src\main\java\com\example\ui\screens\BudgetScreen.kt',
    r'app\src\main\java\com\example\ui\screens\DashboardScreen.kt',
    r'app\src\main\java\com\example\ui\screens\ReceiptScanScreen.kt',
    r'app\src\main\java\com\example\ui\screens\SettingsScreen.kt',
    r'app\src\main\java\com\example\ui\screens\SplitBillScreen.kt'
]

for p in files_to_check:
    with open(p, 'r', encoding='utf-8') as f:
        c = f.read()
    
    needed_imports = []
    if 'AppStrings.' in c and 'import com.example.util.AppStrings' not in c:
        needed_imports.append('import com.example.util.AppStrings')
    if 'LocaleHelper.' in c and 'import com.example.util.LocaleHelper' not in c:
        needed_imports.append('import com.example.util.LocaleHelper')
        
    if needed_imports:
        # insert after package statement
        lines = c.split('\n')
        pkg_idx = -1
        for idx, line in enumerate(lines):
            if line.startswith('package '):
                pkg_idx = idx
                break
        if pkg_idx != -1:
            for imp in needed_imports:
                lines.insert(pkg_idx + 1, imp)
            with open(p, 'w', encoding='utf-8') as f:
                f.write('\n'.join(lines))
            print(f"Added imports {needed_imports} to {os.path.basename(p)}")
        else:
            print(f"Could not find package in {p}")
    else:
        print(f"No missing imports in {os.path.basename(p)}")
