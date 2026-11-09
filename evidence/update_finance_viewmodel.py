path = 'app/src/main/java/com/example/ui/viewmodel/FinanceViewModel.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# Replace hardcoded "Hari Ini"
c = c.replace(
    'result.add(CashflowDataPoint("Hari Ini", inc, exp, start))',
    'result.add(CashflowDataPoint(if (com.example.util.LocaleHelper.isEnglish()) "Today" else "Hari Ini", inc, exp, start))'
)

# Replace Locale("id", "ID") in dayFormat
c = c.replace(
    'val dayFormat = java.text.SimpleDateFormat("EEE", java.util.Locale("id", "ID"))',
    'val dayFormat = java.text.SimpleDateFormat("EEE", com.example.util.LocaleHelper.getActiveLocale())'
)

# Replace Locale("id", "ID") in monthName
c = c.replace(
    'val monthName = java.text.SimpleDateFormat("MMM", java.util.Locale("id", "ID")).format(cal.time)',
    'val monthName = java.text.SimpleDateFormat("MMM", com.example.util.LocaleHelper.getActiveLocale()).format(cal.time)'
)

# Replace "M$week" with localized prefix
c = c.replace(
    'label = "M$week $monthName",',
    'label = "${if (com.example.util.LocaleHelper.isEnglish()) \\"W\\" else \\"M\\"}$week $monthName",'
)

# Replace Locale("id", "ID") in monthFormat for Yearly
c = c.replace(
    'val monthFormat = java.text.SimpleDateFormat("MMM", java.util.Locale("id", "ID"))',
    'val monthFormat = java.text.SimpleDateFormat("MMM", com.example.util.LocaleHelper.getActiveLocale())'
)

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

print('FinanceViewModel.kt updated successfully.')
