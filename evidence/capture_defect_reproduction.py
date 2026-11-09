import time, os
from device_nav import click_text, take_screenshot, dump_ui, find_node_by_text, click_node, adb_shell

print("1. Capturing current screen (Analytics English)...")
click_text(r"^(Analytics|Analisis)$")
time.sleep(2)
take_screenshot("evidence/localization_audit/analysis_year_fix/before_english_analysis.png")

print("2. Navigating to Settings to switch language to Bahasa Indonesia...")
click_text(r"^(Settings|Pengaturan)$")
time.sleep(2)
take_screenshot("evidence/localization_audit/settings_screen.png")

# Scroll down to find App Language / Bahasa Aplikasi
adb_shell(["input", "swipe", "540", "1600", "540", "600", "300"])
time.sleep(1)

# Click Language item
if click_text(r"^(App Language|Bahasa Aplikasi)$"):
    time.sleep(1)
    take_screenshot("evidence/localization_audit/language_dialog.png")
    # Click Bahasa Indonesia
    click_text(r"^Bahasa Indonesia$")
    time.sleep(2)
else:
    # Try clicking language row directly
    click_text(r"(Indonesian|English|Ikuti Sistem)")
    time.sleep(1)
    click_text(r"^Bahasa Indonesia$")
    time.sleep(2)

print("3. Navigating to Analisis in Indonesian...")
click_text(r"^(Analisis|Analytics)$")
time.sleep(2)
take_screenshot("evidence/localization_audit/analysis_year_fix/before_indonesian_analysis.png")
print("Defect reproduction captured.")
