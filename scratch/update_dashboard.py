dashboard_path = r"app/src/main/java/com/example/ui/screens/DashboardScreen.kt"
with open(dashboard_path, "r", encoding="utf-8") as f:
    c = f.read()

c = c.replace('text = "Scan Nota",', 'text = com.example.util.AppStrings.t("Scan Nota", "Scan Receipt"),')
c = c.replace('text = "Anggaran",', 'text = com.example.util.AppStrings.t("Anggaran", "Budget"),')

with open(dashboard_path, "w", encoding="utf-8") as f:
    f.write(c)

print("SUCCESS_DASHBOARD")
