# 1. WalletsScreen.kt
path1 = 'app/src/main/java/com/example/ui/screens/WalletsScreen.kt'
with open(path1, 'r', encoding='utf-8') as f:
    c1 = f.read()
c1 = c1.replace('Text("Asset Tracker", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)', 'Text(com.example.util.AppStrings.t("Pelacak Aset", "Asset Tracker"), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)')
c1 = c1.replace('Text("AI Debt Planner", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)', 'Text(com.example.util.AppStrings.t("Perencana Hutang AI", "AI Debt Planner"), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)')
with open(path1, 'w', encoding='utf-8') as f:
    f.write(c1)
print('WalletsScreen.kt updated.')

# 2. BudgetScreen.kt
path2 = 'app/src/main/java/com/example/ui/screens/BudgetScreen.kt'
with open(path2, 'r', encoding='utf-8') as f:
    c2 = f.read()
c2 = c2.replace('Text("Limit Kategori: $selectedCategoryName")', 'Text("${com.example.util.AppStrings.t(\\"Limit Kategori\\", \\"Category Limit\\")}: $selectedCategoryName")')
with open(path2, 'w', encoding='utf-8') as f:
    f.write(c2)
print('BudgetScreen.kt updated.')

# 3. ReceiptScanScreen.kt
path3 = 'app/src/main/java/com/example/ui/screens/ReceiptScanScreen.kt'
with open(path3, 'r', encoding='utf-8') as f:
    c3 = f.read()
c3 = c3.replace('Text("Salin Teks")', 'Text(com.example.util.AppStrings.t("Salin Teks", "Copy Text"))')
c3 = c3.replace('Text("Pajak Termasuk (Inklusif)")', 'Text(com.example.util.AppStrings.t("Pajak Termasuk (Inklusif)", "Tax Included (Inclusive)"))')
c3 = c3.replace('Text("Pajak Tambahan (Eksklusif)")', 'Text(com.example.util.AppStrings.t("Pajak Tambahan (Eksklusif)", "Additional Tax (Exclusive)"))')
with open(path3, 'w', encoding='utf-8') as f:
    f.write(c3)
print('ReceiptScanScreen.kt updated.')
