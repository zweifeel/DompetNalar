import os

wallets_path = r"app/src/main/java/com/example/ui/screens/WalletsScreen.kt"
with open(wallets_path, "r", encoding="utf-8") as f:
    content = f.read()

# Replace button row in WalletsScreen
old_wallets_block = """                    Row(

                        modifier = Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.spacedBy(10.dp)

                    ) {

                        Button(

                            onClick = onAddWalletClick,

                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),

                            modifier = Modifier.weight(1f).testTag("btn_add_wallet_screen")

                        ) {

                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(com.example.util.AppStrings.t("Tambah Dompet", "Add Wallet"), maxLines = 1, softWrap = false, fontSize = 12.sp)

                        }

                        Button(

                            onClick = onTransferClick,

                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),

                            modifier = Modifier.weight(1f).testTag("btn_transfer_screen")

                        ) {

                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(com.example.util.AppStrings.t("Transfer", "Transfer"), maxLines = 1, softWrap = false, fontSize = 13.sp)

                        }

                    }"""

new_wallets_block = """                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onAddWalletClick,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1.15f).testTag("btn_add_wallet_screen")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(com.example.util.AppStrings.t("Tambah Dompet", "Add Wallet"), maxLines = 1, softWrap = false, fontSize = 11.5.sp)
                        }
                        Button(
                            onClick = onTransferClick,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            modifier = Modifier.weight(0.85f).testTag("btn_transfer_screen")
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(com.example.util.AppStrings.t("Transfer", "Transfer"), maxLines = 1, softWrap = false, fontSize = 12.sp)
                        }
                    }"""

# Normalize CRLF/LF
c_norm = content.replace("\r\n", "\n")
o_norm = old_wallets_block.replace("\r\n", "\n")
n_norm = new_wallets_block.replace("\r\n", "\n")

if o_norm in c_norm:
    c_norm = c_norm.replace(o_norm, n_norm)
    with open(wallets_path, "w", encoding="utf-8") as f:
        f.write(c_norm)
    print("SUCCESS_WALLETS")
else:
    print("NOT_FOUND_WALLETS")
