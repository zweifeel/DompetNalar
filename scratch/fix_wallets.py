with open('app/src/main/java/com/example/ui/screens/WalletsScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

old_buttons = """                        Button(
                            onClick = onAddWalletClick,
                            modifier = Modifier.weight(1f).testTag("btn_add_wallet_screen")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tambah Dompet")
                        }
                        Button(
                            onClick = onTransferClick,
                            modifier = Modifier.weight(1f).testTag("btn_transfer_screen")
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Transfer")
                        }"""

new_buttons = """                        Button(
                            onClick = onAddWalletClick,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).testTag("btn_add_wallet_screen")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tambah Dompet", maxLines = 1, softWrap = false, fontSize = 12.sp)
                        }
                        Button(
                            onClick = onTransferClick,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).testTag("btn_transfer_screen")
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Transfer", maxLines = 1, softWrap = false, fontSize = 13.sp)
                        }"""

text = text.replace('\r\n', '\n')
old_buttons = old_buttons.replace('\r\n', '\n')
new_buttons = new_buttons.replace('\r\n', '\n')

assert old_buttons in text, "old_buttons not found in WalletsScreen.kt"
text = text.replace(old_buttons, new_buttons)

with open('app/src/main/java/com/example/ui/screens/WalletsScreen.kt', 'w', encoding='utf-8') as f:
    f.write(text.replace('\n', '\r\n'))

print("WalletsScreen.kt updated successfully")
