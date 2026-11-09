filepath = r'app\src\main\java\com\example\ui\components\DebtPlannerModalDialog.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    c = f.read()
target = 'text = "Sumber: Catatan Manual",'
replacement = 'text = AppStrings.t("Sumber: Catatan Manual", "Source: Manual Entry"),'
if target in c:
    c = c.replace(target, replacement)
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(c)
    print("Success fixing Sumber: Catatan Manual")
else:
    print("Target not found")
