with open('app/src/main/java/com/example/ui/components/AddTransactionSheet.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
i = 0
replaced = False
while i < len(lines):
    line = lines[i]
    if 'modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),' in line and not replaced:
        new_lines.append('                        modifier = Modifier.padding(vertical = 2.dp),\n')
        new_lines.append('                        content = {\n')
        new_lines.append('                            Box(\n')
        new_lines.append('                                modifier = Modifier\n')
        new_lines.append('                                    .fillMaxWidth()\n')
        new_lines.append('                                    .padding(horizontal = 4.dp, vertical = 10.dp),\n')
        new_lines.append('                                contentAlignment = Alignment.Center\n')
        new_lines.append('                            ) {\n')
        new_lines.append('                                Text(\n')
        new_lines.append('                                    text = when (tabType) {\n')
        new_lines.append('                                        TransactionType.EXPENSE -> "Pengeluaran"\n')
        new_lines.append('                                        TransactionType.INCOME -> "Pemasukan"\n')
        new_lines.append('                                        TransactionType.TRANSFER -> "Transfer"\n')
        new_lines.append('                                    },\n')
        new_lines.append('                                    maxLines = 1,\n')
        new_lines.append('                                    softWrap = false,\n')
        new_lines.append('                                    fontSize = 12.5.sp,\n')
        new_lines.append('                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,\n')
        new_lines.append('                                    color = if (isSelected) {\n')
        new_lines.append('                                        when (tabType) {\n')
        new_lines.append('                                            TransactionType.EXPENSE -> ExpenseRedDark\n')
        new_lines.append('                                            TransactionType.INCOME -> IncomeGreenDark\n')
        new_lines.append('                                            TransactionType.TRANSFER -> FinSmartPrimary\n')
        new_lines.append('                                        }\n')
        new_lines.append('                                    } else MaterialTheme.colorScheme.onSurfaceVariant\n')
        new_lines.append('                                )\n')
        new_lines.append('                            }\n')
        new_lines.append('                        }\n')
        # Skip until the closing of Tab text block
        # line 197 was "text = {"
        # so skip lines until we pass the matching closing parenthesis of Tab
        while i < len(lines) and ')' not in lines[i]:
            i += 1
        replaced = True
    else:
        new_lines.append(line)
    i += 1

with open('app/src/main/java/com/example/ui/components/AddTransactionSheet.kt', 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
print('Replacement complete! Replaced:', replaced)
