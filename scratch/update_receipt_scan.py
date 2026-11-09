receipt_path = r"app/src/main/java/com/example/ui/screens/ReceiptScanScreen.kt"
with open(receipt_path, "r", encoding="utf-8") as f:
    c = f.read()

c_norm = c.replace("\r\n", "\n")

# 1. Update Sample Presets (Cafe/Kopi, Indomaret, Momoyo)
old_preset_row = """                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val preset = ReceiptAiOcrScanner.generateRealisticReceiptFallback(presetType = 0)
                                    syncScanResultToForm(preset)
                                }
                                .testTag("btn_preset_cafe"),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocalCafe, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cafe/Kopi", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val preset = ReceiptAiOcrScanner.generateRealisticReceiptFallback(presetType = 1)
                                    syncScanResultToForm(preset)
                                }
                                .testTag("btn_preset_mart"),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Indomaret", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val preset = ReceiptAiOcrScanner.generateRealisticReceiptFallback(presetType = 2)
                                    syncScanResultToForm(preset)
                                }
                                .testTag("btn_preset_restaurant"),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Restaurant, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Momoyo", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }"""

new_preset_row = """                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1.05f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val preset = ReceiptAiOcrScanner.generateRealisticReceiptFallback(presetType = 0)
                                    syncScanResultToForm(preset)
                                }
                                .testTag("btn_preset_cafe"),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.LocalCafe, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Cafe/Kopi", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1.18f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val preset = ReceiptAiOcrScanner.generateRealisticReceiptFallback(presetType = 1)
                                    syncScanResultToForm(preset)
                                }
                                .testTag("btn_preset_mart"),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Indomaret", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(0.77f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val preset = ReceiptAiOcrScanner.generateRealisticReceiptFallback(presetType = 2)
                                    syncScanResultToForm(preset)
                                }
                                .testTag("btn_preset_restaurant"),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Restaurant, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Momoyo", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                            }
                        }
                    }"""

o_preset = old_preset_row.replace("\r\n", "\n")
n_preset = new_preset_row.replace("\r\n", "\n")

if o_preset in c_norm:
    c_norm = c_norm.replace(o_preset, n_preset)
    print("SUCCESS_PRESETS")
else:
    print("NOT_FOUND_PRESETS")

# 2. Update Source Wallet Selection Row to LazyRow / FlowRow
old_wallet_row = """                            // Source Wallet Selection
                            Text(AppStrings.t("Pilih Sumber Dana (Dompet):", "Select Source Wallet:"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                wallets.forEach { w ->
                                    FilterChip(
                                        selected = selectedWallet?.id == w.id,
                                        onClick = { selectedWallet = w },
                                        label = { Text(w.name, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = FinSmartPrimary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }"""

new_wallet_row = """                            // Source Wallet Selection
                            Text(AppStrings.t("Pilih Sumber Dana (Dompet):", "Select Source Wallet:"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            androidx.compose.foundation.lazy.LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(wallets.size) { index ->
                                    val w = wallets[index]
                                    FilterChip(
                                        selected = selectedWallet?.id == w.id,
                                        onClick = { selectedWallet = w },
                                        label = { Text(w.name, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = FinSmartPrimary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }"""

o_wallet = old_wallet_row.replace("\r\n", "\n")
n_wallet = new_wallet_row.replace("\r\n", "\n")

if o_wallet in c_norm:
    c_norm = c_norm.replace(o_wallet, n_wallet)
    print("SUCCESS_WALLET_ROW")
else:
    print("NOT_FOUND_WALLET_ROW")

with open(receipt_path, "w", encoding="utf-8") as f:
    f.write(c_norm)
