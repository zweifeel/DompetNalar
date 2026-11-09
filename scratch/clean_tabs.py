with open('app/src/main/java/com/example/ui/components/AddTransactionSheet.kt', 'r', encoding='utf-8') as f:
    text = f.read()

bad_block = """                        text = {
                            Text(
                                text = when (tabType) {
                                    TransactionType.EXPENSE -> "Pengeluaran"
                                    TransactionType.INCOME -> "Pemasukan"
                                    TransactionType.TRANSFER -> "Transfer"
                                },
                                maxLines = 1,
                                softWrap = false,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) {
                                    when (tabType) {
                                        TransactionType.EXPENSE -> ExpenseRedDark
                                        TransactionType.INCOME -> IncomeGreenDark
                                        TransactionType.TRANSFER -> FinSmartPrimary
                                    }
                                } else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }"""

text = text.replace('\r\n', '\n')
bad_block = bad_block.replace('\r\n', '\n')
if bad_block in text:
    text = text.replace(bad_block, '')
    print('Removed duplicate text block')
else:
    print('bad_block not found')

with open('app/src/main/java/com/example/ui/components/AddTransactionSheet.kt', 'w', encoding='utf-8') as f:
    f.write(text.replace('\n', '\r\n'))
