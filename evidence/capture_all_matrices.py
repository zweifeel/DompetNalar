import subprocess, time, os

ADB = r'E:\AndroidStudioSDK\platform-tools\adb.exe'
SERIAL = 'PZGMWGKZAA5XJZTW'
PKG = 'com.aistudio.finsmart.kptxvw.debug'

def shell(cmd):
    return subprocess.run([ADB, '-s', SERIAL, 'shell'] + cmd.split(), capture_output=True, text=True).stdout

def tap(x, y, wait=1.2):
    shell(f'input tap {x} {y}')
    time.sleep(wait)

def keyevent(code, wait=1.0):
    shell(f'input keyevent {code}')
    time.sleep(wait)

def swipe(x1, y1, x2, y2, dur=300, wait=1.0):
    shell(f'input swipe {x1} {y1} {x2} {y2} {dur}')
    time.sleep(wait)

def screencap(dest):
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, 'wb') as f:
        subprocess.run([ADB, '-s', SERIAL, 'exec-out', 'screencap', '-p'], stdout=f, check=True)
    print(f"Captured: {dest} ({os.path.getsize(dest)} bytes)")

def capture_suite(lang_prefix):
    # Ensure on Dashboard
    tap(78, 2182) # Home / Dasbor
    screencap(f"evidence/localization_audit/{lang_prefix}/01_dashboard.png")

    tap(262, 2182) # AI Chat / Input AI
    screencap(f"evidence/localization_audit/{lang_prefix}/02_ai_chat.png")

    tap(446, 2182) # Transactions / Mutasi
    screencap(f"evidence/localization_audit/{lang_prefix}/03_transactions.png")

    tap(630, 2182) # Wallets / Dompet
    screencap(f"evidence/localization_audit/{lang_prefix}/04_wallets.png")

    # In Wallets, open Asset Tracker
    tap(270, 715) # Asset Tracker button
    screencap(f"evidence/localization_audit/{lang_prefix}/12_asset_tracker_modal.png")
    keyevent(4) # Dismiss modal

    # In Wallets, open Debt Planner
    tap(810, 715) # Debt Planner button
    screencap(f"evidence/localization_audit/{lang_prefix}/13_debt_planner_modal.png")
    keyevent(4) # Dismiss modal

    tap(816, 2182) # Analytics / Analisis
    screencap(f"evidence/localization_audit/{lang_prefix}/05_analytics.png")

    tap(1001, 2182) # Settings / Pengaturan
    screencap(f"evidence/localization_audit/{lang_prefix}/06_settings.png")

    # In Settings, open Paywall
    tap(584, 1100) # Upgrade to Smart Master banner/button
    screencap(f"evidence/localization_audit/{lang_prefix}/16_paywall_dialog.png")
    keyevent(4) # Dismiss paywall

    # Go back to Dashboard to access smart feature cards (Scan Nota, Split Bill, Anggaran)
    tap(78, 2182)
    swipe(540, 1600, 540, 900, 300) # Scroll to smart cards

    # Open Scan Nota
    tap(200, 1100)
    screencap(f"evidence/localization_audit/{lang_prefix}/07_scan_receipt.png")
    keyevent(4) # Back to Dashboard

    # Open Split Bill
    tap(540, 1100)
    screencap(f"evidence/localization_audit/{lang_prefix}/08_split_bill.png")
    keyevent(4) # Back to Dashboard

    # Open Budget
    tap(880, 1100)
    screencap(f"evidence/localization_audit/{lang_prefix}/09_budget.png")
    keyevent(4) # Back to Dashboard

    # Open Add Transaction FAB
    tap(950, 1950) # FAB
    screencap(f"evidence/localization_audit/{lang_prefix}/11_add_transaction_modal.png")
    keyevent(4) # Close sheet

    # Open Export from Transactions screen
    tap(446, 2182)
    # Scroll or find Export
    tap(990, 200) # Export icon on Transactions top bar
    screencap(f"evidence/localization_audit/{lang_prefix}/10_export.png")
    keyevent(4) # Back to Transactions

print("Suite capture function ready.")
