wallets_path = r"app/src/main/java/com/example/ui/screens/WalletsScreen.kt"
with open(wallets_path, "r", encoding="utf-8") as f:
    c = f.read()

c_norm = c.replace("\r\n", "\n")

old_asset_block = """                OutlinedButton(

                    onClick = onOpenAssetTracker,

                    shape = RoundedCornerShape(14.dp),

                    modifier = Modifier.weight(1f).testTag("btn_asset_tracker_wallets")

                ) {

                    Icon(Icons.Default.CorporateFare, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))

                    Spacer(modifier = Modifier.width(6.dp))

                    Text("Asset Tracker", fontSize = 11.sp, fontWeight = FontWeight.Bold)

                }



                OutlinedButton(

                    onClick = onOpenDebtPlanner,

                    shape = RoundedCornerShape(14.dp),

                    modifier = Modifier.weight(1f).testTag("btn_debt_planner_wallets")

                ) {

                    Icon(Icons.Default.TrendingDown, contentDescription = null, tint = com.example.ui.theme.ExpenseRed, modifier = Modifier.size(16.dp))

                    Spacer(modifier = Modifier.width(6.dp))

                    Text("AI Debt Planner", fontSize = 11.sp, fontWeight = FontWeight.Bold)

                }"""

new_asset_block = """                OutlinedButton(
                    onClick = onOpenAssetTracker,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f).testTag("btn_asset_tracker_wallets")
                ) {
                    Icon(Icons.Default.CorporateFare, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Asset Tracker", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }

                OutlinedButton(
                    onClick = onOpenDebtPlanner,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f).testTag("btn_debt_planner_wallets")
                ) {
                    Icon(Icons.Default.TrendingDown, contentDescription = null, tint = com.example.ui.theme.ExpenseRed, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AI Debt Planner", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }"""

o_norm = old_asset_block.replace("\r\n", "\n")
n_norm = new_asset_block.replace("\r\n", "\n")

if o_norm in c_norm:
    c_norm = c_norm.replace(o_norm, n_norm)
    with open(wallets_path, "w", encoding="utf-8") as f:
        f.write(c_norm)
    print("SUCCESS_ASSET_DEBT")
else:
    print("NOT_FOUND_ASSET_DEBT")
