package com.example.ui.screens



import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.AccountBalanceWallet

import androidx.compose.material.icons.filled.Add

import androidx.compose.material.icons.filled.SwapHoriz

import androidx.compose.material3.Button

import androidx.compose.material3.Card

import androidx.compose.material3.CardDefaults

import androidx.compose.material3.FloatingActionButton

import androidx.compose.material3.Icon

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Surface

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.platform.testTag

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import com.example.data.model.AccountMode

import com.example.data.model.Wallet

import com.example.data.model.WalletType

import com.example.ui.components.WalletCard

import com.example.ui.theme.FinSmartPrimary

import com.example.util.CurrencyFormatter



import androidx.compose.material.icons.filled.CorporateFare

import androidx.compose.material.icons.filled.TrendingDown

import androidx.compose.material3.OutlinedButton



@Composable

fun WalletsScreen(

    wallets: List<Wallet>,

    accountMode: AccountMode,

    isPrivacyMode: Boolean,

    onAddWalletClick: () -> Unit,

    onEditWallet: (Wallet) -> Unit,

    onDeleteWallet: (Wallet) -> Unit,

    onTransferClick: () -> Unit,

    onOpenAssetTracker: () -> Unit = {},

    onOpenDebtPlanner: () -> Unit = {},

    modifier: Modifier = Modifier

) {

    val totalWalletBalance = wallets.sumOf { it.balance }



    LazyColumn(

        modifier = modifier

            .fillMaxSize()

            .background(MaterialTheme.colorScheme.background)

            .testTag("wallets_screen"),

        verticalArrangement = Arrangement.spacedBy(16.dp),

        contentPadding = androidx.compose.foundation.layout.PaddingValues(

            start = 16.dp,

            end = 16.dp,

            top = 16.dp,

            bottom = 100.dp

        )

    ) {

        // Screen Header

        item {

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceBetween,

                verticalAlignment = Alignment.CenterVertically

            ) {

                Column {

                    Text(

                        text = com.example.util.AppStrings.t("Manajemen Dompet & Bank", "Wallet & Bank Management"),

                        style = MaterialTheme.typography.titleLarge,

                        fontWeight = FontWeight.Bold,

                        color = MaterialTheme.colorScheme.onBackground

                    )

                    Text(

                        text = com.example.util.AppStrings.t("Kelola seluruh akun Bank, E-Wallet & Kas (${accountMode.displayName})", "Manage all Bank, E-Wallet & Cash accounts (${accountMode.displayName})"),

                        style = MaterialTheme.typography.bodySmall,

                        color = MaterialTheme.colorScheme.onSurfaceVariant

                    )

                }

            }

        }



        // Net Worth Card

        item {

            Card(

                modifier = Modifier.fillMaxWidth(),

                shape = RoundedCornerShape(20.dp),

                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))

            ) {

                Column(modifier = Modifier.padding(18.dp)) {

                    Row(

                        modifier = Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.SpaceBetween,

                        verticalAlignment = Alignment.CenterVertically

                    ) {

                        Text(
                            text = com.example.util.AppStrings.t("Total Saldo Akun Terdaftar", "Total Balance of Registered Accounts"),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${wallets.size} ${com.example.util.AppStrings.t("Akun", "Accounts")}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(

                        text = if (isPrivacyMode) "Rp •••••••" else CurrencyFormatter.formatRupiah(totalWalletBalance),

                        fontSize = 26.sp,

                        fontWeight = FontWeight.Bold,

                        color = MaterialTheme.colorScheme.onSurface

                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
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
                    }

                }

            }

        }



        // Bab 12 Action Cards: Asset Tracker & AI Debt Planner

        item {

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.spacedBy(10.dp)

            ) {

                OutlinedButton(
                    onClick = onOpenAssetTracker,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f).testTag("btn_asset_tracker_wallets")
                ) {
                    Icon(Icons.Default.CorporateFare, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(com.example.util.AppStrings.t("Pelacak Aset", "Asset Tracker"), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }

                OutlinedButton(
                    onClick = onOpenDebtPlanner,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f).testTag("btn_debt_planner_wallets")
                ) {
                    Icon(Icons.Default.TrendingDown, contentDescription = null, tint = com.example.ui.theme.ExpenseRed, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(com.example.util.AppStrings.t("Perencana Hutang AI", "AI Debt Planner"), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }

            }

        }



        // Wallets by Category

        val groupedWallets = wallets.groupBy { it.type }



        groupedWallets.forEach { (type, typeWallets) ->

            item {

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = type.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPrivacyMode) "Rp •••" else CurrencyFormatter.formatRupiah(typeWallets.sumOf { it.balance }),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }

            }



            items(typeWallets, key = { it.id }) { wallet ->

                WalletCard(

                    wallet = wallet,

                    isPrivacyMode = isPrivacyMode,

                    onEdit = onEditWallet,

                    onDelete = onDeleteWallet,

                    modifier = Modifier.fillMaxWidth().height(140.dp)

                )

            }

        }



        if (wallets.isEmpty()) {

            item {

                Card(

                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),

                    shape = RoundedCornerShape(16.dp),

                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)

                ) {

                    Column(

                        modifier = Modifier.fillMaxWidth().padding(24.dp),

                        horizontalAlignment = Alignment.CenterHorizontally

                    ) {

                        Icon(

                            imageVector = Icons.Default.AccountBalanceWallet,

                            contentDescription = null,

                            tint = MaterialTheme.colorScheme.primary,

                            modifier = Modifier.size(40.dp)

                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(

                            text = com.example.util.AppStrings.t("Belum Ada Dompet di Mode Ini", "No Wallets in This Mode"),

                            fontWeight = FontWeight.Bold,

                            fontSize = 15.sp

                        )

                        Text(

                            text = com.example.util.AppStrings.t("Tambahkan akun rekening bank atau e-wallet untuk mulai mengelola keuangan.", "Add a bank account or e-wallet to start managing your finances."),

                            fontSize = 12.sp,

                            color = MaterialTheme.colorScheme.onSurfaceVariant,

                            textAlign = androidx.compose.ui.text.style.TextAlign.Center

                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onAddWalletClick,
                            modifier = Modifier.testTag("btn_add_wallet_empty_screen"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(com.example.util.AppStrings.t("Tambah Dompet", "Add Wallet"), fontSize = 13.sp)
                        }

                    }

                }

            }

        }

    }

}

