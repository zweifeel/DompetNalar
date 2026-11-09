import os, re

screens = [
    'DashboardScreen.kt', 'TransactionsScreen.kt', 'WalletsScreen.kt',
    'AnalyticsScreen.kt', 'SettingsScreen.kt', 'AiChatScreen.kt',
    'BudgetScreen.kt', 'ExportScreen.kt', 'FeedbackScreen.kt',
    'ReceiptScanScreen.kt', 'SplitBillScreen.kt', 'DevFeedbackViewerScreen.kt'
]

ui_screens_dir = 'app/src/main/java/com/example/ui/screens'

os.makedirs('evidence/localization_audit', exist_ok=True)
with open('evidence/localization_audit/screen_scan_results.txt', 'w', encoding='utf-8') as out:
    for scr in screens:
        path = os.path.join(ui_screens_dir, scr)
        if not os.path.exists(path):
            continue
        with open(path, 'r', encoding='utf-8', errors='ignore') as fp:
            lines = fp.readlines()
        out.write(f"\n==========================================\n")
        out.write(f"SCREEN: {scr} (Total lines: {len(lines)})\n")
        out.write(f"==========================================\n")
        for i, line in enumerate(lines, 1):
            m = re.findall(r'Text\(\s*(?:text\s*=\s*)?"([^"]{3,})"', line)
            for s in m:
                if 'AppStrings' not in line and 'stringResource' not in line:
                    if not s.startswith('$') and not s.startswith('Rp') and not s.startswith('http'):
                        out.write(f"  Line {i:4d}: \"{s}\"\n")

print("Done scanning screens.")
