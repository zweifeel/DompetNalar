path = 'app/src/main/java/com/example/ui/screens/AnalyticsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# Add AppStrings import if not present
if 'import com.example.util.AppStrings' not in c:
    c = c.replace('import com.example.util.CurrencyFormatter', 'import com.example.util.AppStrings\nimport com.example.util.CurrencyFormatter')

# Title & Subtitle
c = c.replace('text = "Analitik & Kesehatan Finansial",', 'text = AppStrings.t("Analitik & Kesehatan Finansial", "Analytics & Financial Health"),')
c = c.replace('text = "Laporan wawasan cerdas arus kas (${accountMode.displayName})",', 'text = "${AppStrings.t(\\"Laporan wawasan cerdas arus kas\\", \\"Smart cashflow insight report\\")} (${accountMode.displayName})",')

# Health status
old_health = '''                        Text(
                            text = when {
                                healthScore >= 85 -> "Keuangan Sangat Sehat (Optimal)"
                                healthScore >= 70 -> "Keuangan Stabil & Terkendali"
                                else -> "Perlu Perhatian pada Pengeluaran"
                            },'''

new_health = '''                        Text(
                            text = when {
                                healthScore >= 85 -> AppStrings.t("Keuangan Sangat Sehat (Optimal)", "Very Healthy Finances (Optimal)")
                                healthScore >= 70 -> AppStrings.t("Keuangan Stabil & Terkendali", "Finances Stable & Controlled")
                                else -> AppStrings.t("Perlu Perhatian pada Pengeluaran", "Needs Attention on Expenses")
                            },'''

c = c.replace(old_health, new_health)
if 'Very Healthy Finances' not in c:
    c = c.replace(old_health.replace('\n', '\r\n'), new_health.replace('\n', '\r\n'))

# Savings ratio
c = c.replace('text = "Rasio tabungan: ${savingsRate.roundToInt()}% dari total pemasukan.",', 'text = "${AppStrings.t(\\"Rasio tabungan\\", \\"Savings ratio\\")}: ${savingsRate.roundToInt()}% ${AppStrings.t(\\"dari total pemasukan.\\", \\"of total income.\\")}",')

# Proporsi Pengeluaran
c = c.replace('text = "Proporsi Pengeluaran",', 'text = AppStrings.t("Proporsi Pengeluaran", "Expense Proportion"),')
c = c.replace('text = "Belum ada data pengeluaran untuk dianalisis.",', 'text = AppStrings.t("Belum ada data pengeluaran untuk dianalisis.", "No expense data to analyze yet."),')
c = c.replace('Text("Total", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)', 'Text(AppStrings.t("Total", "Total"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)')

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

print('AnalyticsScreen.kt updated successfully.')
