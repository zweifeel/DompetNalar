path = 'app/src/main/java/com/example/ui/components/CashflowChart.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# 1. Imports
old_imp = 'import androidx.compose.foundation.shape.CircleShape'
new_imp = 'import androidx.compose.foundation.horizontalScroll\nimport androidx.compose.foundation.rememberScrollState\nimport com.example.util.AppStrings\nimport androidx.compose.foundation.shape.CircleShape'
assert old_imp in c, 'old_imp not found'
c = c.replace(old_imp, new_imp, 1)

# 2. Header
c = c.replace('text = "Analisis Arus Kas",', 'text = AppStrings.t("Analisis Arus Kas", "Cashflow Analysis"),', 1)
c = c.replace('text = "Grafik Pemasukan vs Pengeluaran",', 'text = AppStrings.t("Grafik Pemasukan vs Pengeluaran", "Income vs Expense Chart"),', 1)

# 3. Timeframe Filter Chips
old_chips = """            // Timeframe Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    TimeframeFilter.DAILY,
                    TimeframeFilter.WEEKLY,
                    TimeframeFilter.MONTHLY,
                    TimeframeFilter.YEARLY
                ).forEach { filter ->
                    val isSelected = selectedTimeframe == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedPointIndex = null
                            onTimeframeSelected(filter)
                        },
                        label = { Text(filter.label, fontSize = 12.sp) },"""

new_chips = """            // Timeframe Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    TimeframeFilter.DAILY,
                    TimeframeFilter.WEEKLY,
                    TimeframeFilter.MONTHLY,
                    TimeframeFilter.YEARLY
                ).forEach { filter ->
                    val isSelected = selectedTimeframe == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedPointIndex = null
                            onTimeframeSelected(filter)
                        },
                        label = {
                            Text(
                                text = filter.label,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        },"""

c = c.replace(old_chips, new_chips)
if 'rememberScrollState' not in c:
    c = c.replace(old_chips.replace('\n', '\r\n'), new_chips.replace('\n', '\r\n'))

# 4. Tooltip
c = c.replace('text = "Masuk: ${CurrencyFormatter.formatRupiah(point.income)}",', 'text = "${AppStrings.t(\\"Masuk\\", \\"Income\\")}: ${CurrencyFormatter.formatRupiah(point.income)}",', 1)
c = c.replace('text = "Keluar: ${CurrencyFormatter.formatRupiah(point.expense)}",', 'text = "${AppStrings.t(\\"Keluar\\", \\"Expense\\")}: ${CurrencyFormatter.formatRupiah(point.expense)}",', 1)

# 5. Legend
c = c.replace('Text("Pemasukan", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)', 'Text(AppStrings.income, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)', 1)
c = c.replace('Text("Pengeluaran", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)', 'Text(AppStrings.expense, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)', 1)

# 6. Empty states
c = c.replace('text = "Belum ada transaksi di periode ini.\\nTekan + Pemasukan / + Pengeluaran untuk memulai.",', 'text = AppStrings.t("Belum ada transaksi di periode ini.\\nTekan + Pemasukan / + Pengeluaran untuk memulai.", "No transactions in this period.\\nTap + Income / + Expense to start."),', 1)
c = c.replace('text = "Belum ada nilai arus kas pada periode ini.",', 'text = AppStrings.t("Belum ada nilai arus kas pada periode ini.", "No cashflow data in this period."),', 1)

# 7. Surplus / Deficit & Pie percentages
c = c.replace('text = if (diff >= 0) "Surplus" else "Defisit",', 'text = if (diff >= 0) AppStrings.t("Surplus", "Surplus") else AppStrings.t("Defisit", "Deficit"),', 1)
c = c.replace('Text(text = "Pemasukan ($incomePercent%)",', 'Text(text = "${AppStrings.income} ($incomePercent%)",', 1)
c = c.replace('Text(text = "Pengeluaran ($expensePercent%)",', 'Text(text = "${AppStrings.expense} ($expensePercent%)",', 1)

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)
print('CashflowChart.kt updated successfully.')
