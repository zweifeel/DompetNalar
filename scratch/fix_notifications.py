with open('app/src/main/java/com/example/ui/components/NotificationCenterBottomSheet.kt', 'r', encoding='utf-8') as f:
    text = f.read()

old_row = """                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (item.isWarning) ExpenseRed else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = item.timeAgo,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }"""

new_row = """                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = item.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (item.isWarning) ExpenseRed else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = item.timeAgo,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }"""

text = text.replace('\r\n', '\n')
old_row = old_row.replace('\r\n', '\n')
new_row = new_row.replace('\r\n', '\n')

assert old_row in text, "old_row not found in NotificationCenterBottomSheet.kt"
text = text.replace(old_row, new_row)

with open('app/src/main/java/com/example/ui/components/NotificationCenterBottomSheet.kt', 'w', encoding='utf-8') as f:
    f.write(text.replace('\n', '\r\n'))

print("NotificationCenterBottomSheet.kt updated successfully")
