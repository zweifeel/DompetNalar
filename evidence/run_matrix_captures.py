import time, subprocess
from capture_all_matrices import capture_suite, screencap, tap, swipe, shell

print("=== 1. CAPTURING MATRIX A (ENGLISH) ===")
capture_suite('english')
screencap("evidence/localization_audit/system_language_matrix/sys_en_app_en.png")

print("\n=== 2. SWITCHING TO BAHASA INDONESIA ===")
tap(1001, 2182) # Settings
swipe(540, 1600, 540, 600, 250)
swipe(540, 1600, 540, 600, 250)
tap(430, 726) # App Language row
time.sleep(1)
screencap("evidence/localization_audit/english/14_language_dialog.png")
tap(326, 1105) # Bahasa Indonesia
time.sleep(2)

print("\n=== 3. CAPTURING MATRIX B (INDONESIAN) ===")
capture_suite('indonesian')
screencap("evidence/localization_audit/system_language_matrix/sys_en_app_id.png")

print("\n=== 4. TESTING RESTART PERSISTENCE ===")
shell("am force-stop com.aistudio.finsmart.kptxvw.debug")
time.sleep(2)
shell("am start -n com.aistudio.finsmart.kptxvw.debug/com.example.MainActivityDefault")
time.sleep(4)
screencap("evidence/localization_audit/system_language_matrix/post_restart_indonesian.png")

print("\nALL MATRICES CAPTURED SUCCESSFULLY!")
